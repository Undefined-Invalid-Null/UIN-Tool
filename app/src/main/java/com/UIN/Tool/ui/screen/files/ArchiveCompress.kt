// app/src/main/java/com/UIN/Tool/ui/screen/files/ArchiveCompress.kt
package com.UIN.Tool.ui.screen.files

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IOutCreateCallback
import net.sf.sevenzipjbinding.IOutItem7z
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.InputStreamSequentialInStream
import net.sf.sevenzipjbinding.impl.OutItemFactory
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream

/**
 * 归档**创建**引擎（§4.7「压缩」/ §4.15）。
 *
 * ## 支持面：读写对齐，不给「只读不给写」的半吊子格式
 *
 * | 目标扩展名 | 后端 | 密码 | 分卷 |
 * |---|---|---|---|
 * | `.zip` | zip4j | ✅ AES-256 | ✅ |
 * | `.tar` | commons-compress | — | — |
 * | `.tar.gz` `.tgz` | gzip | — | — |
 * | `.tar.bz2` | bzip2 | — | — |
 * | `.tar.xz` | xz | — | — |
 * | `.tar.zst` | zstd | — | — |
 * | `.7z` | 7-Zip 16.02（sevenzipjbinding） | ✅ AES-256 + 文件名加密 | — |
 * | `.gz` `.bz2` `.xz` | 单文件压缩（整包就一条内容） | — | — |
 *
 * tar 系**不支持密码与分卷**是格式本身的限制，不是我们偷懒 —— tar 里根本没有这个概念；
 * 要加密只有 zip / 7z 两条路（7z 的密码与「加密文件名」见 `Format.supportsPassword`
 * 与压缩框里的那个复选）。
 *
 * **7z 只能新建、不能改**：`ArchiveFormat.SEVEN_Z.writable = false` 说的是「改已有包」那条路
 * （它走 `ZipWrite`，而 ZipWrite 只认 zip），与**新建**是两回事 —— 新建走 7-Zip 引擎。
 * 引擎是 native 的，比原先的纯 Java 后端快，仍每完成一条经 [Reporter] 上报，进度弹窗与
 * 常驻通知（反馈 P0-4）一起给出「到哪了」。
 *
 * 单文件压缩（`.gz`/`.bz2`/`.xz`）**只装一个文件**：多给一条会「看起来压成功了、其实少了
 * 东西」，那比一句报错糟得多 —— 进 [create] 就先拦，且拦在覆盖询问**之前**。
 *
 * ## 与读侧的分工
 *
 * 读走 [ArchiveIndex] / [ArchiveExtract]，写走这里（tar 系）与 [ZipWrite]（zip）。
 * tar 写入刻意**不复用** ZipWrite：两者格式无关，硬凑一个类反而会让「哪些格式可写」
 * 这件事失去唯一出处。
 */
object ArchiveCompress {

    /** 可创建的格式。与 `ArchiveFormat` 分开：那个描述「读得到什么」，这个描述「写得出什么」。 */
    enum class Format(
        val extension: String,
        val supportsPassword: Boolean,
        val supportsSplit: Boolean
    ) {
        ZIP(".zip", supportsPassword = true, supportsSplit = true),
        TAR(".tar", supportsPassword = false, supportsSplit = false),
        TAR_GZ(".tar.gz", supportsPassword = false, supportsSplit = false),
        TAR_BZ2(".tar.bz2", supportsPassword = false, supportsSplit = false),
        TAR_XZ(".tar.xz", supportsPassword = false, supportsSplit = false),
        TAR_ZST(".tar.zst", supportsPassword = false, supportsSplit = false),
        /** 7z：真 7-Zip 引擎（sevenzipjbinding）。密码 / 加密文件名 / 级别都支持。 */
        SEVEN_Z(".7z", supportsPassword = true, supportsSplit = false),
        /** 单文件压缩：整包就是**一条内容**，装不下第二个文件，也装不下文件夹。 */
        GZ(".gz", supportsPassword = false, supportsSplit = false),
        BZ2(".bz2", supportsPassword = false, supportsSplit = false),
        XZ(".xz", supportsPassword = false, supportsSplit = false);

        /** 展示名（去点、大写）；`.tar.gz` 这类要整体保留，不能只取最后一段。 */
        val label: String get() = extension.trimStart('.').uppercase()
    }

    /** 按目标文件名认格式；认不出返回 null（调用方给「不支持的扩展名」提示）。 */
    fun detect(fileName: String): Format? {
        val n = fileName.lowercase()
        return Format.entries.firstOrNull { n.endsWith(it.extension) }
    }

    /**
     * 创建归档：把 [sources] 打进 [dest]（按 [dest] 的扩展名认格式）。
     *
     * @param password zip / 7z 生效；tar 系传了也**静默忽略**（格式本身没有密码概念），
     *                 调用方应先用 [Format.supportsPassword] 拦一道并提示用户。
     * @param splitSize >0 时 zip 按该字节数分卷（`.zip` → `.z01`…`.zip`）。
     * @param keepMeta 是否把源文件的时间戳写进包里（压缩框里的复选，用户反馈⑪）。
     *                 关掉 = 记成打包那一刻，别处不另设开关。
     * @return null = 用户取消整批
     */
    suspend fun create(
        dest: File,
        sources: List<File>,
        password: String? = null,
        splitSize: Long = 0L,
        level: String = FilePrefs.LEVEL_DEFAULT,
        encoding: String = FilePrefs.ENCODING_UTF8,
        keepMeta: Boolean = true,
        /** 7z 专属：是否连**文件名表**一起加密（7-Zip 的 `-mhe=on`）。 */
        encryptHeader: Boolean = false,
        onProgress: (OpProgress) -> Unit,
        onConflict: suspend (List<ConflictItem>) -> ConflictDecision?
    ): OpResult? = withContext(Dispatchers.IO) {
        val format = detect(dest.name) ?: throw IOException2("unsupported: ${dest.name}")
        if (sources.isEmpty()) return@withContext OpResult(false, 0, 0, null)

        // g17：先报一条 0/N，再开始 walk —— 压缩一个大目录时，遍历本身也可能跑几秒，
        // 等它跑完才出现进度框，用户会以为「点了没反应」。此时总数还没量出来，
        // 用顶层条数 + 未知字节先占位；walk 完由下面的 reporter.start() 换成准确值。
        onProgress(
            OpProgress(
                kind = OpKind.COMPRESS,
                totalCount = sources.size,
                totalBytes = -1L,
                doneCount = 0,
                currentName = sources.firstOrNull()?.name.orEmpty(),
                targetPath = dest.path
            )
        )
        val stats = walk(sources)

        // 单文件压缩只能装一个文件（`.gz`/`.bz2`/`.xz`）—— 这是格式的定义，不是我们抠。
        // 挡在**覆盖询问之前**：先弹一个「要不要覆盖」、再告诉你其实装不下，
        // 等于拿两次交互去换一句本可以一开口就说清的话。
        if (format == Format.GZ || format == Format.BZ2 || format == Format.XZ) {
            val hasDir = stats.items.any { it.isDir }
            val files = stats.items.count { !it.isDir }
            if (hasDir || files != 1) {
                return@withContext OpResult(
                    false, 0, 1,
                    com.UIN.Tool.utils.Str.get(
                        com.UIN.Tool.R.string.files_compress_single_file_only
                    )
                )
            }
        }

        val reporter = Reporter(dest, sources.size, stats.totalBytes, onProgress)

        // 已存在的目标文件要问一次（否则一个「确定」就把用户上周的备份盖没了）
        if (dest.exists()) {
            val decision = onConflict(
                listOf(
                    ConflictItem(
                        sourcePath = sources.joinToString { it.name },
                        targetPath = dest.path,
                        sourceSize = stats.totalBytes,
                        targetSize = dest.length(),
                        sourceModified = 0L,
                        targetModified = dest.lastModified()
                    )
                )
            ) ?: return@withContext OpResult(cancelled = true, successCount = 0, failedCount = 0, firstError = null)
            if (decision.policy == ConflictPolicy.SKIP) {
                return@withContext OpResult(false, 0, 0, null)
            }
        }

        // 覆盖前把旧包改名藏起来：写新包期间它不在目标路径上，取消/失败可原样换回。
        // 改名失败就别动目标 —— 继续写下去，取消时删掉的就真是用户的旧包了。
        val hadExisting = dest.exists()
        val backup = hideExisting(dest)
        if (hadExisting && backup == null) {
            return@withContext OpResult(
                false, 0, 1, com.UIN.Tool.utils.Str.get(
                    com.UIN.Tool.R.string.files_compress_backup_failed
                )
            )
        }

        try {
            when (format) {
                Format.ZIP -> createZip(dest, stats, password, splitSize, level, encoding, keepMeta, reporter)
                // 7z 换真 7-Zip 引擎（见 createSevenZBinding）：能出密码 / 级别 / 加密文件名。
                Format.SEVEN_Z -> createSevenZBinding(
                    dest, stats, password, level, encryptHeader, keepMeta, reporter
                )
                // 单文件三种只有一条内容，进不来第二条（上面已拦），这里不必再分支
                Format.GZ, Format.BZ2, Format.XZ -> createPlain(dest, format, stats, level, reporter)
                else -> createTar(dest, format, stats, level, keepMeta, reporter)
            }
            // h.txt 第 15 条：取消发生在最后一条文件上时，循环里已经没有下一个检查点；
            // 这里补一次，保证「点了取消」不会留下一个已经压完的包。
            currentCoroutineContext().ensureActive()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 取消：把写了一半的目标删掉，别留个打不开的残包让用户以为是自己搞坏的。
            // 若目标原先已存在，这样删掉的就是**用户原来的包** —— 所以覆盖前先把它
            // 改名藏起来（见上方 backup），这里再换回去。
            deletePartial(dest)
            restoreBackup(dest, backup)
            throw e
        } catch (e: Exception) {
            deletePartial(dest)
            restoreBackup(dest, backup)
            return@withContext OpResult(false, reporter.success, reporter.failed.coerceAtLeast(1), e.message)
        } catch (e: OutOfMemoryError) {
            // i.txt 第 12 条：内存真的不够时，至少**别闪退** —— 清掉半截包、把旧包换回来，
            // 然后按普通失败报给用户（提示降低级别 / 少选一些文件）。
            deletePartial(dest)
            restoreBackup(dest, backup)
            return@withContext OpResult(
                false,
                reporter.success,
                reporter.failed.coerceAtLeast(1),
                com.UIN.Tool.utils.Str.get(com.UIN.Tool.R.string.files_compress_oom)
            )
        }
        // 新包写完整了才丢弃旧包；删除失败也只是留下一个隐藏备份，不影响结果。
        runCatching { backup?.delete() }
        reporter.finish()
        OpResult(false, reporter.success, reporter.failed, reporter.firstError)
    }

    /**
     * 覆盖已有压缩包时先把旧包改名到 [backup]，成功后才删。
     *
     * 之前是直接往目标路径写：取消/出错时 catch 里的 `dest.delete()` 会把**用户原来
     * 那个包**一起删掉 —— 一次误点取消就永久丢失旧备份。改名而不是复制：同一目录下
     * `renameTo` 是元数据操作，几百 MB 的包也是瞬时的。
     *
     * 返回 null 表示没有旧包（无需恢复）。
     */
    private fun hideExisting(dest: File): File? {
        if (!dest.exists()) return null
        val parent = dest.absoluteFile.parentFile ?: return null
        val backup = File(parent, "${dest.name}.uinbak")
        runCatching { backup.delete() }
        return if (dest.renameTo(backup)) backup else null
    }

    /** 取消/失败后清掉残包；分卷 zip 的 `.z01/.z02…` 一并清掉。 */
    private fun deletePartial(dest: File) {
        runCatching { dest.delete() }
        val parent = dest.absoluteFile.parentFile ?: return
        val name = dest.name
        val stem = name.substringBeforeLast('.', name)
        runCatching {
            parent.listFiles()?.forEach { f ->
                val n = f.name
                if (n != name && n.startsWith("$stem.z")) {
                    val tail = n.removePrefix("$stem.z")
                    if (tail.isNotEmpty() && tail.all { it.isDigit() }) runCatching { f.delete() }
                }
            }
        }
    }

    /** 失败/取消后把旧包换回原位（隐藏备份不存在就什么都不做）。 */
    private fun restoreBackup(dest: File, backup: File?) {
        if (backup == null || !backup.exists()) return
        runCatching { backup.renameTo(dest) }
    }

    // ==================== zip ====================

    /**
     * zip：**分卷与非分卷走两条完全不同的路**，这是 zip4j 的 API 形状决定的，不是我们绕远。
     *
     * - 非分卷：`addStream` 逐条追加 —— 条目名完全由我们定（要支持「保留两者」改名、
     *   要支持目录树落进指定内层目录），且是**追加**不是整包重写。
     * - 分卷：zip4j 只给 `createSplitZipFile(...)` 这一个入口，它是**从零建包**，
     *   且条目名由它自己按源文件路径推（`rootFolderNameInZip` 加前缀）。逐条 `addStream`
     *   建不了分卷包 —— 所以分卷路径**不支持「保留两者」逐条改名**，撞名一律覆盖。
     *   这个差异会如实告诉用户，不做「看起来能改名、其实没改」的假象。
     */
    private suspend fun createZip(
        dest: File,
        stats: Walk,
        password: String?,
        splitSize: Long,
        level: String,
        encoding: String,
        keepMeta: Boolean,
        reporter: Reporter
    ) {
        val hasPassword = !password.isNullOrEmpty()
        // 条目名编码必须与**读侧**钉成同一个值（ArchiveEncoding 读的是同一个 pref），
        // 否则「列得出来、解不出来」。写侧不显式 setCharset 就等于赌两边默认恰好一致。
        val charset = runCatching { Charset.forName(encoding) }
            .getOrElse { StandardCharsets.UTF_8 }
        val zipLevel = zipLevelOf(level)

        // 首个条目可能是一个几百 MB 的大文件：不先报 0/N，进度框要等它压完才出现。
        reporter.start()

        if (splitSize > 0) {
            // 分卷：整批一次建。注意 ZipFile 构造器**没有** (File, long) 重载，
            // splitLength 只能经 createSplitZipFile 传 —— 这一点查过 2.11.6 的 jar。
            val zip = ZipFile(dest).apply { setCharset(charset) }
            if (hasPassword) zip.setPassword(password!!.toCharArray())
            val params = ZipParameters().apply {
                setCompressionMethod(CompressionMethod.DEFLATE)
                setCompressionLevel(zipLevel)
                if (hasPassword) {
                    setEncryptFiles(true)
                    setEncryptionMethod(EncryptionMethod.AES)
                    setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256)
                }
            }
            // 分卷走 createSplitZipFile，条目名由 zip4j 自己推。不设 defaultFolderPath
            // 时它只取 `file.getName()` —— 整个目录树会被拍平（嵌套文件全落到根）。
            // 这里把「所有源的共同上级」交给它，条目名就与 items.entry 同形；空目录也可
            // 直接作为一条目录条目传进去（zip4j 对目录只落一条，不会递归子项）。
            val sourceParent = commonAncestorOf(stats.items.map { it.src })
            if (sourceParent != null) params.setDefaultFolderPath(sourceParent.absolutePath)
            val files = stats.items.map { it.src }
            zip.use { z ->
                z.createSplitZipFile(files, params, true, splitSize)
            }
            for (item in stats.items) {
                if (item.isDir) reporter.onDir(item.src)
                else reporter.onFile(item.src, item.src.length())
            }
            return
        }

        val zip = ZipFile(dest).apply { setCharset(charset) }
        if (hasPassword) zip.setPassword(password!!.toCharArray())
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        try {
            zip.use { z ->
                for (item in stats.items) {
                    currentCoroutineContext().ensureActive()
                    val params = ZipParameters().apply {
                        setFileNameInZip(item.entry)
                        setOverrideExistingFilesInZip(true)
                        if (item.isDir) {
                            // 目录条目必须以 `/` 结尾，且**不压缩**（空内容压了也没意义，
                            // 部分解压工具还会因此判不出它是目录）
                            setFileNameInZip("${item.entry.trimEnd('/')}/")
                            setCompressionMethod(CompressionMethod.STORE)
                        } else {
                            setCompressionMethod(CompressionMethod.DEFLATE)
                            // 用户在压缩框里选的级别（用户反馈⑪）。STORE 分支不给 ——
                            // 没有内容可压，给了也只是个没人读的字段。
                            setCompressionLevel(zipLevel)
                            // 用户在压缩框里选的「保留时间戳」（用户反馈⑪）。
                            // 不保留时**不写这个字段**，zip4j 会记成打包那一刻。
                            if (keepMeta && item.src.lastModified() > 0) {
                                setLastModifiedFileTime(item.src.lastModified())
                            }
                            if (hasPassword) {
                                setEncryptFiles(true)
                                setEncryptionMethod(EncryptionMethod.AES)
                                setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256)
                            }
                        }
                    }
                    if (item.isDir) {
                        z.addStream(java.io.ByteArrayInputStream(ByteArray(0)), params)
                        reporter.onDir(item.src)
                    } else {
                        val counted = ProgressInputStream(
                            inner = item.src.inputStream().buffered(),
                            cancelled = { job?.isActive == false },
                            onBytes = { n ->
                                reporter.addBytes(n)
                                reporter.onBytes(item.src)
                            }
                        )
                        counted.use { ins -> z.addStream(ins, params) }
                        reporter.onFile(item.src, item.src.length(), bytesCounted = true)
                    }
                }
            }
        } finally {
            runCatching { zip.close() }
        }
    }

    // ==================== tar 系 ====================

    /**
     * tar + 可选外层压缩。
     *
     * 外层压缩器必须**先于** TarArchiveOutputStream 的 close 关闭 —— 顺序反了会把
     * 压缩器的收尾字节写进一个已关闭的流，包直接损坏。用一个薄包装把 close 串起来，
     * 而不是靠调用方记得按什么顺序关。
     */
    // suspend：循环体里要用 currentCoroutineContext().ensureActive() 取消（见下方），
    // 而它只能在协程里调。唯一调用点是 suspend 的 create(...)，改签名不影响任何调用方。
    private suspend fun createTar(
        dest: File,
        format: Format,
        stats: Walk,
        level: String,
        keepMeta: Boolean,
        reporter: Reporter
    ) {
        // 首个条目可能很大：先报一条 0/N，别让进度框等到它写完才出现。
        reporter.start()
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        FileOutputStream(dest).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                // 用户在压缩框里选的级别（用户反馈⑪）。四种后端的「级别」不是同一个量纲 ——
                // gzip/xz/zstd 是 Deflater 式的 0-9 档，bzip2 只有 1-9 的块大小 ——
                // 所以换算各自收在下面的映射函数里，别在这里散写数字。
                val compressed: OutputStream = when (format) {
                    Format.TAR -> bos
                    Format.TAR_GZ -> GzipCompressorOutputStream(
                        bos,
                        GzipParameters().apply { compressionLevel = gzipLevelOf(level) }
                    )
                    Format.TAR_BZ2 -> BZip2CompressorOutputStream(bos, bzip2BlockOf(level))
                    Format.TAR_XZ -> XZCompressorOutputStream(bos, xzPresetOf(level))
                    Format.TAR_ZST -> ZstdCompressorOutputStream(bos, zstdLevelOf(level))
                    else -> bos
                }
                // BIG_FILES：单个 >8GB 的文件在 ustar 头里放不下，必须走 POSIX 扩展头，
                // 否则写出来的包「小文件能解、大文件截断」—— 而这恰恰是最难被发现的一类损坏。
                TarArchiveOutputStream(compressed).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    for (item in stats.items) {
                        currentCoroutineContext().ensureActive()
                        val e = TarArchiveEntry(item.src, item.entry.trimEnd('/'))
                        // 「保留时间戳」关掉时记成打包那一刻 —— 留 0 会让解出来的一律
                        // 显示成 1970，看着像包坏了，而不是像「没保留」
                        if (!keepMeta) e.setModTime(System.currentTimeMillis())
                        tar.putArchiveEntry(e)
                        if (!item.isDir) {
                            val counted = ProgressInputStream(
                                inner = item.src.inputStream().buffered(),
                                cancelled = { job?.isActive == false },
                                onBytes = { n ->
                                    reporter.addBytes(n)
                                    reporter.onBytes(item.src)
                                }
                            )
                            counted.use { ins -> ins.copyTo(tar, 64 * 1024) }
                        }
                        tar.closeArchiveEntry()
                        if (item.isDir) reporter.onDir(item.src)
                        else reporter.onFile(item.src, item.src.length(), bytesCounted = true)
                    }
                    tar.finish()
                }
                // 压缩器要跟着关，否则 gzip/zstd 的尾部校验和不会落盘
                if (compressed !== bos) compressed.close()
            }
        }
    }

    // ==================== 7z ====================

    /**
     * 7z 新建：**真 7-Zip 引擎**（`sevenzipjbinding`，用户拍板集成）。
     *
     * 与原先 commons-compress 的 `SevenZOutputFile` 相比，这一条能给出：
     * - 压缩级别（`setLevel`，0-9 → 三档）；
     * - 密码（回调 `ICryptoGetTextPassword`，与属性无关）；
     * - **加密文件名**（`setHeaderEncryption(true)`，即 7z 的 `-mhe=on`）。
     *
     * ## 已知缺陷（上游 issue #77，用户拍板**不修**）
     *
     * 每次 `SetProperties` 都会先 `InitProps()`，所以级别 / 加密文件名 / 线程数
     * **只有最后设置的那一个生效**。这里按优先级落子：
     * - 开了加密文件名 → 最后设它，级别回落引擎默认（界面同步禁掉级别并说明）；
     * - 没开 → 最后设级别。
     * 密码走回调，不是属性，因此两者可以同时存在。
     *
     * ## 取消
     *
     * `createArchive` 是阻塞的 native 调用，协程取消打断不了它；用 `invokeOnCompletion`
     * 置一个标志，回调（`setCompleted` / `getItemInformation`）见到就抛 —— 7-Zip 会
     * 中止并把异常带回 `createArchive`，外层 `create` 负责删掉半截包。
     */
    private suspend fun createSevenZBinding(
        dest: File,
        stats: Walk,
        password: String?,
        level: String,
        encryptHeader: Boolean,
        keepMeta: Boolean,
        reporter: Reporter
    ) {
        val items = stats.items
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        val handle = job?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) cancel.set(true)
        }
        val callback: SevenZOutCallbackBase = if (password.isNullOrEmpty()) {
            SevenZOutCallback(items, keepMeta, cancel, reporter)
        } else {
            SevenZOutCallbackEncrypted(items, keepMeta, cancel, reporter, password)
        }
        // 先落一条 0/N：原生建包是阻塞调用，首个落点若等整个文件压完才来，
        // 大文件期间进度框一直不出现。这一条让 UI 立刻有东西可显示。
        reporter.start()
        try {
            // 空目录包也是合法的：7z 允许只装目录条目。sources 为空在 create() 进口已拦。
            val out = SevenZip.openOutArchive7z()
            try {
                // issue #77：只能留一个属性。加密文件名优先（它决定了「能不能看见目录」，
                // 比压缩率高一点更重要）；没开加密才轮到级别。
                if (encryptHeader && !password.isNullOrEmpty()) {
                    out.setHeaderEncryption(true)
                } else {
                    out.setLevel(sevenZLevelOf(level))
                }
                RandomAccessFile(dest, "rw").use { raf ->
                    RandomAccessFileOutStream(raf).use { stream ->
                        out.createArchive(stream, items.size, callback)
                    }
                }
            } finally {
                runCatching { out.close() }
            }
            if (cancel.get()) throw kotlinx.coroutines.CancellationException("7z cancelled")
            if (callback.failed > 0) {
                throw IOException2("7z: ${callback.failed} item(s) failed")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SevenZipException) {
            if (cancel.get()) throw kotlinx.coroutines.CancellationException("7z cancelled")
            throw e
        } finally {
            handle?.dispose()
        }
    }

    /** 7-Zip 的级别是 0-9；三档取 1 / 5 / 9，与别的格式「快 / 标准 / 最大」同义。 */
    private fun sevenZLevelOf(level: String): Int = when (level) {
        FilePrefs.LEVEL_FAST -> 1
        FilePrefs.LEVEL_BEST -> 9
        else -> 5
    }

    /**
     * 7z 写入回调（共用部分）。
     *
     * **密码只在子类里实现 `ICryptoGetTextPassword`**：按 sevenzipjbinding 的约定，
     * 「回调实现了这个接口」本身就是「要加密」的开关。如果无密码时也实现它并返回
     * null，原生侧可能把它当成空密码去加密 —— 所以这里是两个子类，而不是一个
     * 永远实现接口、靠返回 null 表示不加密。
     *
     * **不要把它改成匿名对象**：R8 会按 `app-keep.pro` 里的整包规则保留
     * `net.sf.sevenzipjbinding`，但这个类是我们自己的，字段被优化掉 native 也不会知道；
     * 命名类 + 只读字段最稳。
     */
    private open class SevenZOutCallbackBase(
        private val items: List<Walk.Item>,
        private val keepMeta: Boolean,
        private val cancel: java.util.concurrent.atomic.AtomicBoolean,
        private val reporter: Reporter
    ) : IOutCreateCallback<IOutItem7z> {

        @Volatile
        var failed = 0
            private set

        /** 已结算的条目下标。7-Zip 按 getItemInformation 的顺序逐条回调，可据此对上源文件。 */
        private var completed = 0

        override fun setTotal(total: Long) = Unit

        override fun setCompleted(complete: Long) {
            if (cancel.get()) throw SevenZipException("cancelled")
        }

        /**
         * g17：每条压缩完就上报一次。
         *
         * 原先是在 createArchive 返回后补一轮 —— 大文件压缩期间进度框根本不出现，
         * 用户只看到包在长、后台任务里什么都没有。改成逐条上报后，进度框/通知/任务
         * 从第一条落点就跟上；Reporter 自己带 60ms 节流，不会把 UI 刷爆。
         */
        override fun setOperationResult(operationResultOk: Boolean) {
            if (!operationResultOk) failed++
            val item = items.getOrNull(completed)
            completed++
            if (operationResultOk && item != null) {
                if (item.isDir) reporter.onDir(item.src)
                else reporter.onFile(item.src, item.src.length())
            }
        }

        override fun getItemInformation(
            index: Int,
            outItemFactory: OutItemFactory<IOutItem7z>
        ): IOutItem7z {
            if (cancel.get()) throw SevenZipException("cancelled")
            val item = items[index]
            val out = outItemFactory.createOutItem()
            // 目录条目不带结尾斜杠（7-Zip 用 IS_FOLDER 属性判断，不靠名字）
            out.setPropertyPath(item.entry.trimEnd('/'))
            out.setPropertyIsDir(item.isDir)
            if (!item.isDir) out.setDataSize(item.src.length())
            // 与 tar 同一条：不保留时间戳时记成打包那一刻 —— 留 0 解出来是 1970
            val stamp = if (keepMeta && item.src.lastModified() > 0) {
                item.src.lastModified()
            } else {
                System.currentTimeMillis()
            }
            out.setPropertyLastModificationTime(Date(stamp))
            return out
        }

        override fun getStream(index: Int): net.sf.sevenzipjbinding.ISequentialInStream? {
            val item = items[index]
            if (item.isDir) return null
            if (cancel.get()) throw SevenZipException("cancelled")
            // h.txt 第 15 条：7-Zip 也只给「一个流」，包一层后同样能显示块级进度、
            // 并在取消标志置位时从读循环里立刻退出（不用等整条文件压完）。
            val counted = ProgressInputStream(
                inner = item.src.inputStream().buffered(),
                cancelled = { cancel.get() },
                onBytes = { n ->
                    reporter.addBytes(n)
                    reporter.onBytes(item.src)
                }
            )
            return InputStreamSequentialInStream(counted)
        }
    }

    /** 无密码：**不实现** `ICryptoGetTextPassword`（见基类说明）。 */
    private class SevenZOutCallback(
        items: List<Walk.Item>,
        keepMeta: Boolean,
        cancel: java.util.concurrent.atomic.AtomicBoolean,
        reporter: Reporter
    ) : SevenZOutCallbackBase(items, keepMeta, cancel, reporter)

    /** 有密码：实现 `ICryptoGetTextPassword`，7-Zip 据此加密内容（勾了加密头则连表一起）。 */
    private class SevenZOutCallbackEncrypted(
        items: List<Walk.Item>,
        keepMeta: Boolean,
        cancel: java.util.concurrent.atomic.AtomicBoolean,
        reporter: Reporter,
        private val password: String
    ) : SevenZOutCallbackBase(items, keepMeta, cancel, reporter), ICryptoGetTextPassword {

        override fun cryptoGetTextPassword(): String = password
    }

    // ==================== 单文件压缩（.gz / .bz2 / .xz） ====================

    /**
     * 单文件压缩：整包就是**一条内容**。多源 / 含目录的情形在 [create] 进口就拦掉了，
     * 所以这里取「那唯一一个文件」即可。
     *
     * 与 tar 系**共用同一批换算函数**（gzip/bzip2/xz 的级别映射）——
     * 用户在压缩框里选的「快 / 标准 / 最大」在两种包里必须是同一个意思。
     */
    private suspend fun createPlain(
        dest: File,
        format: Format,
        stats: Walk,
        level: String,
        reporter: Reporter
    ) {
        val item = stats.items.firstOrNull { !it.isDir }
            ?: throw IOException2("single-file: nothing to compress")
        // 单文件压缩：先报一条 0/N，进度框立刻出现，而不是等整份文件写完。
        reporter.start()
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        FileOutputStream(dest).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                val compressed: OutputStream = when (format) {
                    Format.BZ2 -> BZip2CompressorOutputStream(bos, bzip2BlockOf(level))
                    Format.XZ -> XZCompressorOutputStream(bos, xzPresetOf(level))
                    else -> GzipCompressorOutputStream(
                        bos,
                        GzipParameters().apply { compressionLevel = gzipLevelOf(level) }
                    )
                }
                val counted = ProgressInputStream(
                    inner = item.src.inputStream().buffered(),
                    cancelled = { job?.isActive == false },
                    onBytes = { n ->
                        reporter.addBytes(n)
                        reporter.onBytes(item.src)
                    }
                )
                counted.use { ins -> ins.copyTo(compressed, 64 * 1024) }
                // 压缩器要跟着关，否则 gzip 的尾部校验和不会落盘
                if (compressed !== bos) compressed.close()
            }
        }
        reporter.onFile(item.src, item.src.length(), bytesCounted = true)
    }

    // ==================== 压缩级别换算（用户反馈⑪） ====================

    /**
     * 三档 → zip4j 的十档。用户只该看到「最快 / 标准 / 最大」—— 把 10 个档位摆出来，
     * 每一档的差别肉眼难辨，反而让人怀疑是不是选错了。
     */
    /** zip4j 的档位换算。**公开给 [ZipWrite.addFiles] 复用** ——
     *  两处各写一份的话，「最快」在压缩框里和在「加入已有包」里迟早不是一个意思。 */
    internal fun zipLevelOf(level: String): CompressionLevel = when (level) {
        FilePrefs.LEVEL_FAST -> CompressionLevel.FASTEST
        FilePrefs.LEVEL_BEST -> CompressionLevel.ULTRA
        else -> CompressionLevel.NORMAL
    }

    /** gzip 走 Deflater 的 0-9 档。 */
    private fun gzipLevelOf(level: String): Int = when (level) {
        FilePrefs.LEVEL_FAST -> 1
        FilePrefs.LEVEL_BEST -> 9
        else -> 6
    }

    /**
     * bzip2 **没有「级别」**，只有 1-9 的**块大小**（块越大压得越狠，也越慢越吃内存）。
     * 按「快 / 中 / 慢」的直觉映射成 2 / 5 / 9 即可，语义与另外几档对齐。
     */
    private fun bzip2BlockOf(level: String): Int = when (level) {
        FilePrefs.LEVEL_FAST -> 2
        FilePrefs.LEVEL_BEST -> 9
        else -> 5
    }

    /** xz 的 preset 同样是 0-9。 */
    private fun xzPresetOf(level: String): Int = when (level) {
        FilePrefs.LEVEL_FAST -> 1
        FilePrefs.LEVEL_BEST -> 9
        else -> 6
    }

    /**
     * zstd 的 1-19 都是常规档（20-22 要开 ultra，内存成倍涨，这里不开放）。
     * 它的默认档是 3，所以「标准」也取 3 —— 与别的格式取 6/5 不是同一个量纲。
     */
    private fun zstdLevelOf(level: String): Int = when (level) {
        FilePrefs.LEVEL_FAST -> 1
        FilePrefs.LEVEL_BEST -> 19
        else -> 3
    }

    // ==================== 通用 ====================

    /** 一次遍历的结果：条目清单 + 总字节。遍历在 IO 线程，且每层都检查取消。 */
    private class Walk(val items: List<Item>, val totalBytes: Long) {
        class Item(val src: File, val entry: String, val isDir: Boolean)
    }

    private suspend fun walk(sources: List<File>): Walk {
        val items = ArrayList<Walk.Item>()
        var total = 0L
        fun go(src: File, entry: String) {
            if (src.isDirectory) {
                items += Walk.Item(src, entry, isDir = true)
                val kids = src.listFiles() ?: return
                for (k in kids) go(k, "$entry/${k.name}")
            } else {
                items += Walk.Item(src, entry, isDir = false)
                total += src.length()
            }
        }
        for (s in sources) {
            currentCoroutineContext().ensureActive()
            if (s.name.isEmpty()) continue
            go(s, s.name)
        }
        return Walk(items, total.coerceAtLeast(0L))
    }

    /**
     * 一组文件在磁盘上的共同上级目录。
     *
     * 分卷 zip 只能走 `createSplitZipFile`，条目名由 zip4j 按 `defaultFolderPath`
     * 推相对路径；不给就是 `getName()` —— 目录树被拍平。这里取所有源的共同上级，
     * 让分卷包里的结构与非分卷一致。找不到时返回 null（保持库默认行为）。
     */
    private fun commonAncestorOf(files: List<File>): File? {
        if (files.isEmpty()) return null
        var base: File? = files.first().absoluteFile.parentFile ?: return null
        for (f in files) {
            val path = f.absoluteFile.path
            var cursor = base ?: return null
            while (true) {
                val prefix = if (cursor.path == "/") "/" else cursor.path + File.separator
                if (path == cursor.path || path.startsWith(prefix)) {
                    base = cursor
                    break
                }
                cursor = cursor.parentFile ?: return null
            }
        }
        return base
    }

    /** 进度上报（与 FileOps / ZipWrite 同一套节流节奏）。 */
    /**
     * 带进度与取消检查的输入流（h.txt 第 15 条）。
     *
     * zip4j / tar / gzip 都是「给一个 InputStream 就闷头读」，中间没有任何回调；
     * 包一层之后每读一块就累加 [onBytes]，并检查协程是否已被取消 ——
     * 取消键按下后不必等整个大文件压完。
     */
    private class ProgressInputStream(
        private val inner: java.io.InputStream,
        private val cancelled: () -> Boolean,
        private val onBytes: (Long) -> Unit
    ) : java.io.FilterInputStream(inner) {

        private fun check() {
            if (cancelled()) throw kotlinx.coroutines.CancellationException("compress cancelled")
        }

        override fun read(): Int {
            check()
            val b = super.read()
            if (b >= 0) onBytes(1L)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            check()
            val n = super.read(b, off, len)
            if (n > 0) onBytes(n.toLong())
            return n
        }

        override fun skip(n: Long): Long {
            check()
            return super.skip(n)
        }
    }

    private class Reporter(
        private val dest: File,
        private val totalCount: Int,
        private val totalBytes: Long,
        private val onProgress: (OpProgress) -> Unit
    ) {
        var success = 0
        var failed = 0
        var firstError: String? = null
        private var doneBytes = 0L
        private var last = 0L

        /**
         * g17：压缩一开始先报一条 0/N。
         *
         * 没有这一条，首个落点要等第一条压缩完；单文件大包（几百 MB 的镜像、视频）
         * 压上几分钟，界面全程没有进度框，看起来像「点了没反应」。
         */
        fun start() {
            last = 0L
            emit(File(""))
        }

        fun onDir(f: File) {
            success++
            emit(f)
        }

        /**
         * 已读字节数（h.txt 第 15 条）。压缩大文件时逐个块累加，进度条与已处理体积
         * 才会动；原先只在整条文件压完后一次加完，一个几百 MB 的文件全程显示 0。
         */
        fun addBytes(n: Long) {
            if (n > 0) doneBytes += n
        }

        /** 块级进度上报（节流仍由 [emit] 负责）。 */
        fun onBytes(f: File) = emit(f)

        /**
         * @param bytesCounted 该文件的字节已通过 [addBytes] 累计过 —— 不要重复加，
         *                     否则进度会冲过 100%。
         */
        fun onFile(f: File, bytes: Long, bytesCounted: Boolean = false) {
            if (!bytesCounted) doneBytes += bytes
            success++
            emit(f)
        }

        private fun emit(f: File, finished: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!finished && now - last < 60L) return
            last = now
            onProgress(
                OpProgress(
                    // g17：压缩自己报「正在压缩」。原先这里写死 COPY，压缩框标题、胶囊、
                    // 状态栏通知全都在说「正在复制」—— 操作类型只有调用方知道，不能猜。
                    kind = OpKind.COMPRESS,
                    totalCount = totalCount,
                    doneCount = success + failed,
                    totalBytes = totalBytes,
                    doneBytes = doneBytes,
                    currentName = if (finished) "" else f.name,
                    targetPath = dest.path,
                    finished = finished,
                    successCount = success,
                    failedCount = failed,
                    firstError = firstError
                )
            )
        }

        fun finish() = emit(File(""), finished = true)
    }

    /** 本文件内部用的异常：上层 `catch (e: Exception)` 会把它翻成提示文案。 */
    private class IOException2(msg: String) : java.io.IOException(msg)
}
