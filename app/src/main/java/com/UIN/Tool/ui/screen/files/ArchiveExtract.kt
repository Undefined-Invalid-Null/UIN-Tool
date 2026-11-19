// app/src/main/java/com/UIN/Tool/ui/screen/files/ArchiveExtract.kt
package com.UIN.Tool.ui.screen.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 归档内条目**解出**到真实目录（§4.15 末条）。
 *
 * > 归档内选中条目复制/移动到另一窗格 = **解出到该窗格**（`复制 →` 语义）
 *
 * 也就是说：按「复制」还是「移动」，走到这里都是同一件事 —— 把条目从归档里解到目标目录。
 * 归档在 M4a 一律只读，源条目**不会**被删，所以调用方一律按 [OpKind.COPY] 进来；
 * 报成「正在移动」而源条目原封不动，是撒谎。
 *
 * 与 [FileOps.executeCopyOrMove] 的分工（用户在归档内外做同一件事，手感必须一致）：
 * - **撞名一次列全问一次**（§4.4 多项形态）、**进度七层**、**取消清半成品** —— 三者口径完全一致；
 * - 差别只在字节从哪来：那边读 `FileInputStream`，这边从归档流里解出来。
 *
 * 撞名为什么**一次问全**而不是懒问：目标的**顶层**落点在动手前就全部已知
 * （根 A 没有落点，A 下面的东西也必然没有），所以首批清单里已经含全部冲突项；
 * 此时 [ConflictDecision.applyToAll] 是空操作 —— 清单里没有「剩余」可谈。
 * 这与 `FileOps.policyFor(index=0)` 的实际行为是同一件事。目录内部合并时的同名一律按
 * REPLACE（§4.4：嵌套冲突不单问），也与那边一致。
 *
 * 一次**顺序扫描**完成解出：zip 走中央目录随机定位（不解压非目标条目）；
 * 7z/tar 没有随机访问，只能顺序过一遍，非目标条目按流跳过 —— 全程不把条目正文装进内存。
 */
object ArchiveExtract {

    private const val BUFFER = 64 * 1024
    private const val REPORT_INTERVAL_MS = 60L

    /**
     * @param sources 虚拟路径（`x.zip!/src/Main.kt`）包成的 [File]；只取其 path 字符串用。
     * @param kind 由调用方给（归档内恒为 [OpKind.COPY]，见类注释）。
     * @param onConflict 撞名询问；返回 null = 用户点了外面 = 整批取消。
     * @throws IOException 归档打不开 / 格式不支持 / 建不了目录
     */
    suspend fun execute(
        kind: OpKind,
        sources: List<File>,
        targetDir: File,
        /**
         * 是否把归档里记的时间戳还原到落盘的文件上（解压框里的复选，用户反馈⑪）。
         *
         * 关掉 = 落盘时间就是**解压那一刻**，与「保留原样」是两种都正当的选择，
         * 由用户在弹窗里挑，不进设置。
         */
        restoreTimestamps: Boolean = true,
        /**
         * 解压密码（b10）。空 = 这个包没有密码。
         *
         * commons-compress 的 `ZipFile` **不解任何密** —— ZipCrypto 与 AES 一律抛
         * `UnsupportedZipFeatureException`，所以「列得出来、解不出来」。给了密码就换 zip4j
         * 流式读（与写侧 [ZipWrite] 同一个引擎），没给密码又遇到加密条目则**在动手之前**
         * 就拒绝（见下面的 fail-fast），不让半截产物留在目标目录里。
         */
        password: String? = null,
        onProgress: (OpProgress) -> Unit,
        onConflict: suspend (List<ConflictItem>) -> ConflictDecision?
    ): OpResult {
        if (sources.isEmpty()) return OpResult(false, 0, 0, null)

        // ── 归一：全部来源必须落在同一个归档里。混选（真实文件 + 归档条目）不静默吞 ──
        val archivePath = sources.firstNotNullOfOrNull { VirtualPath.archiveOf(it.path) }
        if (archivePath == null || sources.any { VirtualPath.archiveOf(it.path) != archivePath }) {
            return OpResult(false, 0, sources.size, "mixed selection is not extractable")
        }
        val roots = sources
            .mapNotNull { VirtualPath.internalOf(it.path) }
            // 归档内路径是**别人给的**：`../../x` 这类要在这里掐掉，否则 `destRoot`
            // 会落到 targetDir 外面（zip-slip）。规范化后再 distinct ——
            // `a/../b` 与 `b` 本就是同一个落点，不去重会问两次撞名。
            .mapNotNull { safeInternalPath(it) }
            .distinct()
            .map { Root(it, File(targetDir, it.substringAfterLast('/'))) }
        if (roots.isEmpty()) return OpResult(false, 0, 0, null)

        val archiveFile = File(archivePath)
        val created = mutableListOf<File>()

        // ── 加密包 fail-fast（b10）──
        // 没给密码、包里却有加密条目：**一个字节都别往目标目录写**再报错。等解到那一条
        // 才发现解不动的话，前面已经落了一批文件，用户看到的是「半截解压 + 一句报错」，
        // 哪边是真的哪边是失败都分不清。给过密码就交给下面的解出过程自己验（密码错要
        // 由 zip4j 说，而不是在这里一句笼统的「加密」把「密码打错了」也盖掉）。
        // 判定读的是包尾的中央目录 —— 切到 IO 线程，调用方两个入口都在主线程协程里。
        val blocked = withContext(Dispatchers.IO) {
            if (!password.isNullOrEmpty()) {
                false
            } else when (detectArchiveFormat(archiveFile)) {
                // 7z 也在这儿拦（e15 P1-12）：头被加密的连条目表都读不出来，
                // 不拦就一路掉进「已损坏」那句，而包其实好好的
                // RAR 也在这儿拦（同 e15 P1-12 的 7z）：头被加密的连条目表都读不出来，
                // 不拦就一路掉进「已损坏」那句，而包其实好好的
                ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.RAR ->
                    ArchiveEncoding.hasEncryptedEntry(archiveFile)
                else -> false
            }
        }
        if (blocked) {
            return OpResult(false, 0, sources.size, Str.get(R.string.files_archive_encrypted))
        }

        return try {
            withContext(Dispatchers.IO) {
                // ── 1. 先读条目表 ──
                // 一举两得：算得出总字节（进度条第 5 层的分母），也量得准源大小 ——
                // 撞名框里那句「已存在且相同，跳过？」靠的就是 sourceSize/sourceModified，
                // 动手前不先量一遍就只能瞎猜。
                // 条目名同样过一遍 safeInternalPath：能规范化的换成规范形，逃逸的整条丢掉 ——
                // `e.path` 下面要同时当「映射的键」和「拼落点的原料」，两处必须同形。
                val all = ArrayList<ArchiveEntry>()
                for (e in readArchiveEntries(archiveFile)) {
                    val p = safeInternalPath(e.path) ?: continue
                    all.add(if (p == e.path) e else e.copy(path = p))
                }
                if (all.size > FileOps.MAX_ENTRIES) throw FileOps.TooLargeException(FileOps.MAX_ENTRIES)

                // 每个根：体积、修改时间（单文件时才有意义）、是不是目录根
                val rootSize = HashMap<String, Long>()
                val rootModified = HashMap<String, Long>()
                val rootIsDir = HashMap<String, Boolean>()
                val rootEntryCount = HashMap<String, Int>()
                for (r in roots) {
                    var bytes = 0L
                    var files = 0
                    var mod = 0L
                    var dirLike = false
                    for (e in all) {
                        if (!e.path.matches(r.internalRoot)) continue
                        if (e.path == r.internalRoot && e.isDir) dirLike = true
                        if (e.path != r.internalRoot && e.path.startsWith("${r.internalRoot}/")) dirLike = true
                        if (e.isDir) continue
                        bytes += e.size
                        files++
                        // 只有恰好一个文件时，这个根才谈得上「和目标是同一份」
                        mod = if (files == 1) e.modified else 0L
                    }
                    rootSize[r.internalRoot] = bytes
                    rootModified[r.internalRoot] = mod
                    rootIsDir[r.internalRoot] = dirLike
                    rootEntryCount[r.internalRoot] = files
                }

                // ── 2. 撞名：把所有已存在的落点列成一张清单，一次问完（§4.4 多项形态）──
                val conflicting = roots.filter { it.destRoot.exists() }
                if (conflicting.isNotEmpty()) {
                    val decision = onConflict(
                        conflicting.map { r ->
                            ConflictItem(
                                sourcePath = VirtualPath.join(archivePath, r.internalRoot),
                                targetPath = r.destRoot.path,
                                sourceSize = rootSize[r.internalRoot] ?: -1L,
                                targetSize = if (r.destRoot.isFile) r.destRoot.length() else -1L,
                                sourceModified = rootModified[r.internalRoot] ?: 0L,
                                targetModified = r.destRoot.lastModified()
                            )
                        }
                    )
                    if (decision == null) {
                        // 点外面 = 整批取消。此刻一个字节都没写、一个目录都没建 → 无须清理。
                        onProgress(
                            OpProgress(
                                kind = kind,
                                totalCount = sources.size,
                                totalBytes = -1L,
                                sourcePath = archivePath,
                                targetPath = targetDir.path,
                                finished = true,
                                cancelled = true
                            )
                        )
                        return@withContext OpResult(true, 0, 0, null)
                    }
                    for (r in roots) {
                        if (!r.destRoot.exists()) continue
                        when (decision.policy) {
                            ConflictPolicy.SKIP -> r.skip = true
                            // 「保留两份」不是加前缀，而是整个改名，同目录复制的那套规则
                            ConflictPolicy.KEEP_BOTH -> r.destRoot = FileOps.uniqueSibling(r.destRoot)
                            ConflictPolicy.REPLACE -> Unit // 就地覆盖；目录则是合并
                        }
                    }
                }

                // ── 3. 建目录根 + 建立「条目 → 落点」映射 ──
                val wanted = LinkedHashMap<String, File>()
                val rootOf = LinkedHashMap<String, Root>()
                for (r in roots) {
                    if (r.skip) continue
                    if (rootIsDir[r.internalRoot] == true) ensureDir(r.destRoot, created)
                }
                for (e in all) {
                    if (e.isDir) continue
                    val r = roots.firstOrNull { !it.skip && e.path.matches(it.internalRoot) } ?: continue
                    wanted[e.path] = if (e.path == r.internalRoot) {
                        // 根本身就是个文件：落点的文件名 = 目标目录里那个名字
                        r.destRoot
                    } else {
                        File(r.destRoot, e.path.removePrefix("${r.internalRoot}/"))
                    }
                    rootOf[e.path] = r
                }
                // 目录条目单独收一份：原来只建「根是目录」那一个，嵌套目录全靠
                // `ensureParent` 在解文件时顺带建 —— **空目录**没有任何文件可顺带，
                // 解完就凭空消失（用户反馈的「空文件夹没了」）。
                val dirs = LinkedHashMap<String, File>()
                for (e in all) {
                    if (!e.isDir) continue
                    val r = roots.firstOrNull { !it.skip && e.path.matches(it.internalRoot) } ?: continue
                    if (e.path == r.internalRoot) continue
                    dirs[e.path] = File(r.destRoot, e.path.removePrefix("${r.internalRoot}/"))
                    rootOf[e.path] = r
                }

                val activeRoots = roots.filter { !it.skip }
                val bytesTotal = all.filter { it.path in wanted }.sumOf { it.size.coerceAtLeast(0L) }
                val rootDone = HashMap<String, Int>()
                activeRoots.forEach { rootDone[it.internalRoot] = 0 }

                // ── 4. 顺序解出 ──
                var doneBytes = 0L
                var lastReport = 0L
                var firstError: String? = null
                val rootOk = HashMap<String, Boolean>()
                activeRoots.forEach { rootOk[it.internalRoot] = true }
                /** 真正落盘成功的条目；对账时用来找「映射里有、却没解出来」的那几个。 */
                val written = HashSet<String>()

                /** 条目模式的分母是 0 字节（只有空目录）→ 退化成条目计数，别显示成「100%」。 */
                val progressTotal = if (bytesTotal > 0) bytesTotal else -1L

                fun report(currentName: String, finished: Boolean = false) {
                    val now = System.currentTimeMillis()
                    if (!finished && now - lastReport < REPORT_INTERVAL_MS) return
                    lastReport = now
                    val doneRoots = activeRoots.count { r ->
                        (rootDone[r.internalRoot] ?: 0) >= (rootEntryCount[r.internalRoot] ?: 0)
                    }
                    onProgress(
                        OpProgress(
                            kind = kind,
                            totalCount = sources.size,
                            doneCount = if (finished) activeRoots.size else doneRoots,
                            totalBytes = progressTotal,
                            doneBytes = doneBytes,
                            currentName = if (finished) "" else currentName,
                            sourcePath = archivePath,
                            targetPath = targetDir.path,
                            finished = finished,
                            successCount = activeRoots.count { rootOk[it.internalRoot] == true },
                            failedCount = activeRoots.size - activeRoots.count { rootOk[it.internalRoot] == true },
                            firstError = firstError
                        )
                    )
                }

                report("")

                extractPass(archiveFile, password) { name, modified, data ->
                    // 目录条目（data == null）也要真的建出来，否则空目录会丢。
                    // 非空目录这里先建好，后面 ensureParent 直接复用，不会多跑一次 mkdir。
                    if (data == null) {
                        val dir = dirs[name] ?: return@extractPass
                        val r = rootOf[name] ?: return@extractPass
                        try {
                            ensureDir(dir, created)
                            report(name)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            rootOk[r.internalRoot] = false
                            if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                        }
                        return@extractPass
                    }
                    val dst = wanted[name] ?: return@extractPass
                    val r = rootOf[name] ?: return@extractPass
                    try {
                        ensureParent(dst, created)
                        // 用户反馈 e15 P0-1：同 FileOps —— 就地覆盖之前先把旧文件的副本
                        // 送进回收站。原文件仍在原地，下面那条记账照常按「是否已存在」判。
                        if (dst.exists()) RecycleBin.preserve(dst)
                        // 只给**本次新建**的文件记账：覆盖掉的那几个要是也记了，
                        // 取消时会连用户原有的数据一起删 —— 那是拿「取消」毁文件。
                        if (!dst.exists()) created.add(dst)
                        FileOutputStream(dst).use { outs ->
                            data.use { ins ->
                                val buf = ByteArray(BUFFER)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = ins.read(buf)
                                    if (n < 0) break
                                    outs.write(buf, 0, n)
                                    doneBytes += n
                                    report(name)
                                }
                                outs.fd.sync()
                            }
                        }
                        if (restoreTimestamps && modified > 0) dst.setLastModified(modified)
                        written += name
                        rootDone[r.internalRoot] = (rootDone[r.internalRoot] ?: 0) + 1
                        report(name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 单条失败不中断整批，但**这条根算失败**，收尾必须报出来 —— 不做静默失败
                        rootOk[r.internalRoot] = false
                        if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                    }
                }

                // ── 5. 对账：映射里有、扫描里却没解出来（名字对不上 / 半路被跳过）→ 该根失败 ──
                for (name in wanted.keys) {
                    if (name in written) continue
                    val r = rootOf[name] ?: continue
                    rootOk[r.internalRoot] = false
                    if (firstError == null) firstError = "not found in archive: $name"
                }

                val okCount = activeRoots.count { rootOk[it.internalRoot] == true }
                report("", finished = true)
                OpResult(false, okCount, activeRoots.size - okCount, firstError)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                cleanup(created)
                onProgress(
                    OpProgress(
                        kind = kind,
                        totalCount = sources.size,
                        totalBytes = -1L,
                        sourcePath = archivePath,
                        targetPath = targetDir.path,
                        finished = true,
                        cancelled = true
                    )
                )
            }
            OpResult(true, 0, 0, null)
        }
    }

    // ==================== 内部 ====================

    /** 一个被选中的归档内根，及其在目标目录里的落点。[destRoot] 可因「保留两份」被改名。 */
    private class Root(
        val internalRoot: String,
        var destRoot: File,
        var skip: Boolean = false
    )

    private fun String.matches(root: String): Boolean =
        this == root || startsWith("$root/")

    /**
     * 顺序扫一遍归档，把**每个文件条目**的解压流交给 [body]。
     *
     * 三种格式的流生命周期不同，这里统一成「条目前开、body 返回后关」：
     * - zip：`getInputStream` 只定位不解压，关掉也便宜；
     * - 7z：关掉 = **把剩余字节读完**（`SevenZInput.close`），否则下一条 `getNextEntry()` 位置不对；
     * - tar：传的是归档流本身，**关它就等于关掉整个归档** → 用 [NoCloseStream] 挡一下。
     *
     * 列目录走的也是同一套 [readArchiveEntries] / 同一条流构造 —— GBK 名字
     * 「列得出来、取不出来」就是两条路各解各的造成的。
     */
    private suspend fun extractPass(
        archiveFile: File,
        password: String? = null,
        body: suspend (name: String, modified: Long, data: InputStream?) -> Unit
    ) {
        // 归档里读出来的是**原样名**，而 execute 那边的 `wanted` 键、进度记账用的都是
        // 过完 safeInternalPath 的规范形。两边不同形就对不上号 —— `tar cf x.tar .` 打出来
        // 的包条目全是 `./` 开头，拿原样名去查一条都命中不了，表现成「解压完一个文件都没有」。
        // 统一在这一个口子换掉，五个分支就都不用各自记得处理；逃逸的（`../x`）直接丢。
        val emit: suspend (String, Long, InputStream?) -> Unit = { n, m, d ->
            val norm = safeInternalPath(n)
            if (norm != null) body(norm, m, d)
        }
        when (detectArchiveFormat(archiveFile)) {
            // 编码与列目录同源（ArchiveEncoding），否则同一个包会「列得出来、取不出来」
            ArchiveFormat.ZIP -> if (password.isNullOrEmpty()) {
                ArchiveEncoding.openZipArchive(archiveFile).use { zip ->
                    val en = zip.entries
                    while (en.hasMoreElements()) {
                        currentCoroutineContext().ensureActive()
                        val e = en.nextElement()
                        val name = e.name.removeSuffix("/")
                        if (name.isEmpty()) continue
                        val mod = runCatching { e.lastModifiedTime.toMillis() }.getOrDefault(0L)
                        val stream = if (e.isDirectory) null else zip.getInputStream(e)
                        try {
                            emit(name, mod, stream)
                        } finally {
                            stream?.close()
                        }
                    }
                }
            } else {
                // 有密码就换 zip4j 流式读：commons-compress 对任何加密都直接抛
                // UnsupportedZipFeatureException（见 execute 的 password 注释）。
                // 传 emit 而不是 body：那条路是它自己逐条回调的，规范化得跟着下去
                extractEncryptedZip(archiveFile, password, emit)
            }

            // 密码走 openSevenZ（e15 P1-12）：没显式给就用用户之前输过的那份缓存 ——
            // 「包内打开文件」那条路没有地方让他再输一次。
            ArchiveFormat.SEVEN_Z -> try {
                openSevenZ(archiveFile, password).use { z ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val e = z.getNextEntry() ?: break
                        val name = e.name.removeSuffix("/")
                        if (name.isEmpty()) continue
                        val mod = if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L
                        val stream = if (e.isDirectory) null else SevenZInput(z, e.size)
                        try {
                            emit(name, mod, stream)
                        } finally {
                            stream?.close()
                        }
                    }
                }
            } catch (e: PasswordRequiredException) {
                // 兜底：正常情况下上面的 fail-fast 已经先问过密码了。真走到这儿说明
                // 漏了一条路 —— 报成人话，别把库那句英文（"Cannot read encrypted
                // content from …"）甩给用户：看不懂，也搜不到。
                throw IOException(Str.get(R.string.files_archive_password_required))
            }

            ArchiveFormat.TAR -> {
                val inner = openTarStream(archiveFile)
                if (inner == null) {
                    // 单文件压缩（`a.log.gz`）：整包就一条内容。**名字必须与列目录用同一个
                    // plainInnerName** —— 一边剥后缀一边不剥，就会「列得出来、取不出来」。
                    val plain = openPlainStream(archiveFile)
                    try {
                        emit(
                            plainInnerName(archiveFile),
                            archiveFile.lastModified(),
                            plain
                        )
                    } finally {
                        plain.close()
                    }
                } else {
                    TarArchiveInputStream(inner).use { tar ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val e = tar.nextTarEntry ?: break
                            val name = e.name.removeSuffix("/")
                            if (name.isEmpty()) continue
                            val mod = e.modTime?.time ?: 0L
                            val stream = if (e.isDirectory) null else NoCloseStream(tar)
                            try {
                                emit(name, mod, stream)
                            } finally {
                                // NoCloseStream.close 是空实现，关它不会关掉归档流本身
                                stream?.close()
                            }
                        }
                    }
                }
            }

            // RAR 与列目录同源（同一个 openRar、同一份缓存密码）—— 两条路各开各的包，
            // 就会变成「列得出来、取不出来」
            ArchiveFormat.RAR -> try {
                openRar(archiveFile, password).use { rar ->
                    for (fh in rar.getFileHeaders()) {
                        currentCoroutineContext().ensureActive()
                        val name = fh.getFileName()?.trimEnd('/', '\\') ?: continue
                        if (name.isEmpty()) continue
                        val mod = fh.getMTime()?.time ?: 0L
                        // getInputStream 自己管 solid 与顺序解压 —— 必须按头链顺序读，
                        // 而 emit 的调用方本来就是按这个顺序遍历的
                        val stream = if (fh.isDirectory) null else rar.getInputStream(fh)
                        try {
                            emit(name, mod, stream)
                        } finally {
                            stream?.close()
                        }
                    }
                }
            } catch (e: PasswordRequiredException) {
                // 兜底：正常情况上面的 fail-fast 已经先问过密码了。真走到这儿说明漏了一条路，
                // 报成人话，别把库那句英文甩给用户 —— 看不懂，也搜不到
                throw IOException(Str.get(R.string.files_archive_password_required))
            }

            ArchiveFormat.UNKNOWN ->
                throw IOException("unsupported archive format: ${archiveFile.path}")
        }
    }

    /**
     * [SevenZFile] 不是 [InputStream]，而 7z 只能顺序读 —— 这层把它包成
     * 「只读当前条目前 [size] 字节」的流。
     *
     * **close 必须把没读完的读完**：7z 是一条压缩字节流，不读干就跳到下一条，
     * 后面所有条目都会错位（表现成解出来的内容张冠李戴）。
     */
    private class SevenZInput(private val f: SevenZFile, private val size: Long) : InputStream() {

        private var pos = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= size) return -1
            if (len <= 0) return 0
            val n = f.read(b, off, minOf(len.toLong(), size - pos).toInt())
            if (n <= 0) return -1
            pos += n
            return n
        }

        override fun close() {
            // 不看协程状态、不抛异常 —— 清理阶段必须走完
            val buf = ByteArray(BUFFER)
            while (pos < size) {
                val n = try {
                    f.read(buf, 0, minOf(buf.size.toLong(), size - pos).toInt())
                } catch (_: Exception) {
                    -1
                }
                if (n <= 0) break
                pos += n
            }
        }
    }

    /** 关掉时不透传给底层 —— tar 的「流」就是整个归档，关不得。 */
    private class NoCloseStream(ins: InputStream) : FilterInputStream(ins) {
        override fun close() = Unit
    }

    /**
     * 加密 zip 的解出（b10）—— `password` 有值时的专用通路。
     *
     * commons-compress 的 `ZipFile` 不解任何密（ZipCrypto/AES 都抛
     * `UnsupportedZipFeatureException`），所以这里改用 zip4j 流式读：与写侧 [ZipWrite]
     * 是同一个引擎，条目名编码也取同一个判定（[ArchiveEncoding.pickCharset]），
     * 不会出现「列出来的名字和取出来的名字对不上」。
     *
     * 与通用分支的两处差别，都是为了**守住 `extractPass` 的契约**：
     * - 整包共用**一条**流，所以喂给 `body` 的是 [NoCloseStream] —— 让它关掉一个条目
     *   就把整个归档关了，剩下的一半必然报「已关闭」；
     * - `body` 可能没读完就返回（撞名选了跳过/覆盖前的分支），走之前**主动排空**到本条
     *   末尾，否则下一个 `getNextEntry()` 会把半截正文当成条目头来解，后面全错位。
     *
     * 密码错、CRC 不符、包损坏在这条路上都表现为 [ZipException] —— 统一换成一句人话；
     * `body` 自己写的那些 IOException（磁盘满之类）**不在此列**，原样往上抛。
     */
    private suspend fun extractEncryptedZip(
        archiveFile: File,
        password: String,
        body: suspend (name: String, modified: Long, data: InputStream?) -> Unit
    ) {
        val zis = ZipInputStream(
            archiveFile.inputStream().buffered(),
            password.toCharArray(),
            ArchiveEncoding.pickCharset(archiveFile)
        )
        try {
            zis.use { z ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val lfh = z.getNextEntry() ?: break
                    val rawName = lfh.fileName ?: break
                    val name = rawName.removeSuffix("/")
                    if (name.isEmpty()) continue
                    // 必须走 Epoch 那个 getter：zip4j 在 header 里存的是**原始 DOS 时间**
                    // （读进来直接塞进去，没换算过），直接拿会得到一个 1980 年附近的「毫秒数」，
                    // 还原时间戳时整批文件的日期就全歪了。≤0 的由 body 那侧的 `> 0` 挡掉。
                    val mod = lfh.lastModifiedTimeEpoch
                    if (rawName.endsWith("/")) {
                        try {
                            body(name, mod, null)
                        } finally {
                            drainQuietly(z)
                        }
                        continue
                    }
                    // ok 才去强排空：body 已经抛出时，排空路上的异常会**盖掉**原始那一个
                    var ok = false
                    try {
                        body(name, mod, NoCloseStream(z))
                        ok = true
                    } finally {
                        if (ok) drain(z) else drainQuietly(z)
                    }
                }
            }
        } catch (_: ZipException) {
            // 换成人话：zip4j 的原话是英文的 "wrong password" / "CRC mismatch"，
            // 直接甩给用户等于没说；这两种原因用户要做的事一样 —— 换个密码或换个包
            throw IOException(Str.get(R.string.files_archive_bad_password))
        }
    }

    /** 读到本条末尾；`body` 已经读完了就立刻返回（`read` 第一下就是 -1）。 */
    private fun drain(z: InputStream) {
        val buf = ByteArray(64 * 1024)
        while (z.read(buf) >= 0) {
            // 只为给下一条让位，字节本身不要
        }
    }

    /** [drain] 的不抛版本 —— 只用在「已经有更值得报的异常在往上抛」的那条退路上。 */
    private fun drainQuietly(z: InputStream) {
        runCatching { drain(z) }
    }

    /** 逐层建目录并记账（只记本次真正建出来的，取消时据此清半成品）。 */
    private fun ensureDir(d: File, created: MutableList<File>) {
        if (d.isDirectory) return
        if (d.exists()) throw IOException("target exists and is not a directory: ${d.path}")
        d.parentFile?.let { ensureDir(it, created) }
        if (!d.mkdirs()) throw IOException("cannot create directory: ${d.path}")
        created.add(d)
    }

    /** 落点的父目录 —— 归档内的深层路径在目标侧可能是一整串全新的目录。 */
    private fun ensureParent(dst: File, created: MutableList<File>) {
        dst.parentFile?.let { ensureDir(it, created) }
    }

    /** 取消后的半成品清理：先文件后目录、倒序，避开「父目录还没空就删不掉」。 */
    private fun cleanup(created: List<File>) {
        for (f in created.asReversed()) {
            try {
                if (f.exists()) f.delete()
            } catch (_: Exception) {
                // 清理尽力而为，失败不影响「已取消」这个结论
            }
        }
    }
}
