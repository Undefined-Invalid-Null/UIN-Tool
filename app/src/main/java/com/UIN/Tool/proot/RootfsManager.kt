package com.UIN.Tool.proot

import android.content.Context
import android.system.Os
import com.UIN.Tool.constants.AppConstants
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream

object RootfsManager {

    /** D-06：导出条目（区分文件/目录/符号链接/硬链接） */
    private data class ExportItem(val file: File, val relPath: String, val flag: Byte, val linkName: String?)

    private const val TAG = "RootfsManager"
    private const val CONTAINERS_DIR = "containers"
    private const val SETUP_MARKER = ".setup-complete"

    private val installingLocks = ConcurrentHashMap<String, ReentrantLock>()

    /** D-10：per-container 读写互斥锁。原先只有 install 加锁，delete/reset/export 都不加，
     *  可与安装并发（导出读一半的 rootfs、删除正在写入的 staging）。 */
    private fun lockFor(containerName: String): ReentrantLock =
        installingLocks.getOrPut(containerName) { ReentrantLock() }

    // G-21：UI 门禁与底层锁统一读取（原先 isInstalling 是屏幕内单 bool，与锁不联动）
    fun isBusy(containerName: String): Boolean = lockFor(containerName).isLocked

    fun anyBusy(): Boolean = installingLocks.values.any { it.isLocked }

    // D-07：listContainers 结果缓存（按容器目录 mtime 失效），
    // 避免每次刷新都对每个容器做 size+count 全树扫描
    @Volatile
    private var listCache: List<ContainerInfo>? = null
    @Volatile
    private var listCacheKey: Long = Long.MIN_VALUE

    fun containersDir(context: Context): File =
        File(context.filesDir, CONTAINERS_DIR)

    fun containerDir(context: Context, name: String): File =
        File(containersDir(context), name)

    fun rootfsDir(context: Context, name: String): File =
        File(containerDir(context, name), "rootfs")

    fun isContainerReady(context: Context, name: String): Boolean {
        val rootfs = rootfsDir(context, name)
        val marker = File(containerDir(context, name), SETUP_MARKER)
        if (!rootfs.isDirectory || !marker.exists()) return false
        return findShell(rootfs) != null
    }

    fun findShell(rootfs: File): String? {
        val candidates = listOf(
            "/bin/bash", "/usr/bin/bash",
            "/bin/sh", "/bin/ash", "/bin/dash",
            "/usr/bin/sh", "/usr/bin/dash"
        )
        for (path in candidates) {
            val f = File(rootfs, path.removePrefix("/"))
            if (f.exists() && f.isFile) return path
            // Also check that symlinks resolve to existing targets
            try {
                val canonical = f.canonicalFile
                if (canonical.exists() && canonical.isFile) return path
            } catch (_: Exception) {}
        }
        // fallback: scan bin/ for any shell, prefer bash
        val binDir = File(rootfs, "bin")
        if (binDir.isDirectory) {
            binDir.listFiles()?.firstOrNull { it.name == "bash" }?.let {
                return "/bin/${it.name}"
            }
            binDir.listFiles()?.firstOrNull { it.name in listOf("sh", "ash", "dash", "busybox") }?.let {
                return "/bin/${it.name}"
            }
        }
        return null
    }

    fun listContainers(context: Context): List<ContainerInfo> {
        val dir = containersDir(context)
        if (!dir.exists()) {
            listCache = null
            listCacheKey = Long.MIN_VALUE
            return emptyList()
        }
        // D-07：目录 mtime 未变化时直接命中缓存（安装/删除/重置都会改变 mtime）
        val key = containersCacheKey(dir)
        val cached = listCache
        if (cached != null && key == listCacheKey) return cached
        val result = dir.listFiles()
            ?.filter { it.isDirectory && !it.name.endsWith(".staging") && !it.name.startsWith(".") }
            ?.map { d ->
                val name = d.name
                val rootfs = File(d, "rootfs")
                val ready = isContainerReady(context, name)
                val size = if (rootfs.exists()) calculateDirSize(rootfs) else 0L
                val fileCount = if (rootfs.exists()) countFiles(rootfs) else 0
                ContainerInfo(name, ready, size, d.lastModified(), fileCount, d.absolutePath)
            }
            ?.sortedBy { it.name }
            ?: emptyList()
        listCache = result
        listCacheKey = key
        return result
    }

    /** D-07：容器列表缓存键 = 容器目录与其一级子目录的 mtime 组合 */
    private fun containersCacheKey(dir: File): Long {
        var key = dir.lastModified()
        dir.listFiles()?.forEach { key = key * 31 + it.lastModified() }
        return key
    }

    /**
     * D-03/D-04：安装事务收尾。
     * 顺序：删除旧目录 → staging 原子改名 → 全部成功后才写 .setup-complete 标记。
     * 任一步返回 false/抛异常都清理 staging 并返回 failure，
     * 杜绝原先「deleteRecursively/renameTo 返回值被忽略 → 无条件写标记 →
     * 把被部分删除的旧 rootfs 判成就绪」的问题。
     */
    private fun finalizeInstall(stagingDir: File, destDir: File, label: String): Result<Unit> {
        try {
            if (destDir.exists() && !destDir.deleteRecursively()) {
                Logger.e(TAG, "RootfsManager: $label failed to delete old container dir: ${destDir.absolutePath}")
                stagingDir.deleteRecursively()
                return Result.failure(java.io.IOException("failed to clean old container dir: ${destDir.name}"))
            }
            if (!stagingDir.renameTo(destDir)) {
                Logger.e(TAG, "RootfsManager: $label staging rename failed: ${stagingDir.name} -> ${destDir.name}")
                stagingDir.deleteRecursively()
                return Result.failure(java.io.IOException("failed to rename staged container dir: ${destDir.name}"))
            }
            if (!File(destDir, SETUP_MARKER).createNewFile()) {
                Logger.w(TAG, "RootfsManager: $label failed to create ready marker (file may already exist)")
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "RootfsManager: $label finalize failed", e)
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            return Result.failure(e)
        }
    }

    /**
     * D-04：解压前的可用空间预检（解压物约为压缩包的 4 倍，按此估算）。
     * 无法测量时放行，交由解压阶段抛出真实错误。
     */
    private fun hasEnoughSpace(dir: File, requiredBytes: Long): Boolean {
        return try {
            val target = if (dir.exists()) dir else dir.parentFile ?: dir
            val stat = android.os.StatFs(target.absolutePath)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            available >= requiredBytes
        } catch (e: Exception) {
            true
        }
    }

    /**
     * D-15：按**魔数**嗅探压缩格式，文件名后缀只作兜底。
     * 原先三处（asset 安装 / 计数 / 文件安装）都按后缀判断，且 asset 与文件两侧条件还不一致，
     * 改名或后缀缺失的包会直接解压失败或产出乱数据。
     */
    private fun openCompressedStream(raw: java.io.InputStream, nameHint: String): java.io.InputStream {
        val push = java.io.PushbackInputStream(raw, 6)
        val head = ByteArray(6)
        var n = 0
        while (n < 6) {
            val r = push.read(head, n, 6 - n)
            if (r < 0) break
            n += r
        }
        push.unread(head, 0, n)
        val isXz = n >= 6 && head[0] == 0xFD.toByte() && head[1] == 0x37.toByte() &&
            head[2] == 0x7A.toByte() && head[3] == 0x58.toByte() &&
            head[4] == 0x5A.toByte() && head[5] == 0x00.toByte()
        val isGz = n >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()
        val isZst = n >= 4 && head[0] == 0x28.toByte() && head[1] == 0xB5.toByte() &&
            head[2] == 0x2F.toByte() && head[3] == 0xFD.toByte()
        return when {
            isXz -> XZCompressorInputStream(push)
            isGz -> GZIPInputStream(push)
            isZst -> ZstdInputStream(push)
            nameHint.endsWith(".xz") -> XZCompressorInputStream(push)
            nameHint.endsWith(".gz") || nameHint.endsWith(".tgz") -> GZIPInputStream(push)
            nameHint.endsWith(".zst") -> ZstdInputStream(push)
            else -> push
        }
    }

    suspend fun installFromAsset(
        context: Context,
        containerName: String,
        assetName: String,
        onProgress: ((entryCount: Int, entryName: String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val lock = lockFor(containerName)
        lock.withLock {
            if (isContainerReady(context, containerName)) {
                Logger.i(TAG, "RootfsManager: container '$containerName' already ready, skip install")
                return@withContext Result.success(Unit)
            }
            Logger.i(TAG, "RootfsManager: installing container '$containerName' from asset (asset=$assetName)")
            val destDir = containerDir(context, containerName)
            val stagingDir = File(destDir.parent, "${containerName}.staging")
            try {
                // D-04：先清理上次失败残留的 staging（原为仅解压前清理，异常路径会永久残留）
                if (stagingDir.exists()) {
                    Logger.i(TAG, "RootfsManager: cleaning leftover staging dir")
                    stagingDir.deleteRecursively()
                }

                // D-04：解压前预检可用空间，避免解到一半 ENOSPC 留下残缺 rootfs
                val assetSize = try {
                    context.assets.open(assetName).use { it.available().toLong() }
                } catch (e: Exception) { 0L }
                if (assetSize > 0 && !hasEnoughSpace(destDir.parentFile ?: destDir, assetSize * 4)) {
                    return@withContext Result.failure(
                        java.io.IOException("insufficient storage: about ${assetSize * 4 / (1024 * 1024)} MB free space required")
                    )
                }

                if (!stagingDir.mkdirs() && !stagingDir.isDirectory) {
                    return@withContext Result.failure(java.io.IOException("failed to create temp install dir: ${stagingDir.name}"))
                }

                val rootfsDest = File(stagingDir, "rootfs")
                rootfsDest.mkdirs()

                Logger.i(TAG, "RootfsManager: extracting rootfs...")
                var entryCount = 0
                context.assets.open(assetName).use { rawInput ->
                    // D-15：按魔数嗅探（后缀仅兜底）
                    val decompressed = openCompressedStream(rawInput, assetName)
                    decompressed.use { stream ->
                        extractTar(stream, rootfsDest) { entryName ->
                            entryCount++
                            onProgress?.invoke(entryCount, entryName)
                        }
                    }
                }

                Logger.i(TAG, "RootfsManager: generating resolv.conf and hosts")
                generateResolvConf(rootfsDest)
                generateEtcHosts(rootfsDest)
                generateAptConfig(rootfsDest)
                generateDpkgConfig(rootfsDest)

                // 确保 proot 所需的关键目录存在并设置权限
                val tmpDir = File(rootfsDest, "tmp")
                tmpDir.mkdirs()
                // D-13：/tmp 必须是 0o1777（带 sticky），原先 511=0777 丢 sticky 位
                runCatching { Os.chmod(tmpDir.absolutePath, 1023) } // 0o1777
                val varTmpDir = File(rootfsDest, "var/tmp")
                varTmpDir.mkdirs()
                runCatching { Os.chmod(varTmpDir.absolutePath, 1023) } // 0o1777 sticky
                val runtimeDir = File(tmpDir, "runtime-root")
                runtimeDir.mkdirs()
                runCatching { Os.chmod(runtimeDir.absolutePath, 448) } // 0700
                val rootDir = File(rootfsDest, "root")
                if (!rootDir.exists()) {
                    rootDir.mkdirs()
                    Logger.i(TAG, "RootfsManager: creating /root dir")
                }

                val finalize = finalizeInstall(stagingDir, destDir, "容器 '$containerName' 安装")
                if (finalize.isFailure) return@withContext finalize
                Logger.i(TAG, "RootfsManager: container '$containerName' installed (asset=$assetName)")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e(TAG, "RootfsManager: container '$containerName' install failed", e)
                // D-04：失败时清理 staging，避免数百 MB 残留
                runCatching { if (stagingDir.exists()) stagingDir.deleteRecursively() }
                Result.failure(e)
            }
        }
    }

    /**
     * 预扫描 tar 文件统计条目总数（不解压）。
     */
    fun countTarEntries(tarFile: File): Int {
        var count = 0
        try {
            FileInputStream(tarFile).use { fis ->
                val inputStream = openCompressedStream(fis, tarFile.name)
                TarArchiveInputStream(inputStream).use { tar ->
                    while (tar.nextTarEntry != null) count++
                }
            }
        } catch (e: Exception) {
            // D-12：原先 catch 里静默返回 0，调用方据此算进度 → UI 总数恒空
            Logger.e(TAG, "RootfsManager: countTarEntries failed: ${e.message}", e)
        }
        return count
    }

    suspend fun installFromFile(
        context: Context,
        containerName: String,
        tarFile: File,
        onProgress: ((entryCount: Int, entryName: String, bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val lock = lockFor(containerName)
        lock.withLock {
            val destDir = containerDir(context, containerName)
            val stagingDir = File(destDir.parent, "${containerName}.staging")
            try {
                if (stagingDir.exists()) stagingDir.deleteRecursively()

                // D-04：解压前预检可用空间（解压物约为压缩包 4 倍）
                val compressedBytes = tarFile.length()
                if (compressedBytes > 0 && !hasEnoughSpace(destDir.parentFile ?: destDir, compressedBytes * 4)) {
                    return@withContext Result.failure(
                        java.io.IOException("insufficient storage: about ${compressedBytes * 4 / (1024 * 1024)} MB free space required")
                    )
                }

                if (!stagingDir.mkdirs() && !stagingDir.isDirectory) {
                    return@withContext Result.failure(java.io.IOException("failed to create temp install dir: ${stagingDir.name}"))
                }

                val rootfsDest = File(stagingDir, "rootfs")
                rootfsDest.mkdirs()

                val totalBytes = tarFile.length()
                var entryCount = 0

                FileInputStream(tarFile).use { fis ->
                    val inputStream = openCompressedStream(fis, tarFile.name)
                    extractTar(inputStream, rootfsDest) { entryName ->
                        entryCount++
                        onProgress?.invoke(entryCount, entryName, 0L, 0L)
                    }
                }

                generateResolvConf(rootfsDest)
                generateEtcHosts(rootfsDest)
                generateAptConfig(rootfsDest)
                generateDpkgConfig(rootfsDest)

                val tmpDir = File(rootfsDest, "tmp")
                tmpDir.mkdirs()
                runCatching { Os.chmod(tmpDir.absolutePath, 511) }
                val varTmpDir = File(rootfsDest, "var/tmp")
                varTmpDir.mkdirs()
                runCatching { Os.chmod(varTmpDir.absolutePath, 511) }
                val runtimeDir = File(tmpDir, "runtime-root")
                runtimeDir.mkdirs()
                runCatching { Os.chmod(runtimeDir.absolutePath, 448) }
                val rootDir = File(rootfsDest, "root")
                if (!rootDir.exists()) rootDir.mkdirs()

                val finalize = finalizeInstall(stagingDir, destDir, "容器 '$containerName' 导入")
                if (finalize.isFailure) return@withContext finalize
                Logger.i(TAG, "Container '$containerName' installed from file: ${tarFile.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to install container '$containerName' from file", e)
                // D-04：失败时清理 staging，避免数百 MB 残留
                runCatching { if (stagingDir.exists()) stagingDir.deleteRecursively() }
                Result.failure(e)
            }
        }
    }

    fun deleteContainer(context: Context, name: String): Boolean {
        // D-10：与安装/导出互斥，避免删掉正在解压或正在读取的目录
        lockFor(name).withLock {
            val dir = containerDir(context, name)
            if (!dir.exists()) return false
            Logger.i(TAG, "RootfsManager: deleting container '$name'")
            val deleted = dir.deleteRecursively()
            if (deleted) Logger.i(TAG, "RootfsManager: container '$name' deleted")
            return deleted
        }
    }

    suspend fun resetContainer(context: Context, name: String): Result<Unit> {
        Logger.i(TAG, "RootfsManager: resetting container '$name'")
        deleteContainer(context, name)
        val assetName = com.UIN.Tool.plugin.ProotContainerManager.findRootfsAsset(context, name)
            ?: return Result.failure(IllegalStateException("No rootfs asset found for container '$name'"))
        return installFromAsset(context, name, assetName)
    }

    fun getContainerInfo(context: Context, name: String): ContainerInfo? {
        val dir = containerDir(context, name)
        if (!dir.exists()) return null
        val rootfs = File(dir, "rootfs")
        val ready = isContainerReady(context, name)
        val size = if (rootfs.exists()) calculateDirSize(rootfs) else 0L
        val fileCount = if (rootfs.exists()) countFiles(rootfs) else 0
        val lastModified = dir.lastModified()
        return ContainerInfo(name, ready, size, lastModified, fileCount, dir.absolutePath)
    }

    fun setDefaultContainer(context: Context, name: String) {
        context.getSharedPreferences(AppConstants.PREF_PROOT, Context.MODE_PRIVATE)
            .edit().putString(AppConstants.KEY_DEFAULT_CONTAINER, name).apply()
    }

    fun getDefaultContainer(context: Context): String {
        return context.getSharedPreferences(AppConstants.PREF_PROOT, Context.MODE_PRIVATE)
            .getString(AppConstants.KEY_DEFAULT_CONTAINER, BackendConfig.BUILTIN_CONTAINER) ?: BackendConfig.BUILTIN_CONTAINER
    }

    /**
     * 导出容器 rootfs 为 tar.xz 文件。
     * @param onProgress 进度回调 (fileCount, totalFiles, bytesWritten, totalBytes, currentFile)
     * @return Result 包含导出文件路径
     */
    suspend fun exportContainer(
        context: Context,
        name: String,
        outputDir: File,
        compressionLevel: Int = 9,
        onProgress: ((fileCount: Int, totalFiles: Int, bytesWritten: Long, totalBytes: Long, currentFile: String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        // D-10：导出与安装/删除互斥（否则可能导出到一半目录被换掉）
        lockFor(name).withLock {
            try {
                // Cap compression level based on available memory to prevent OOM
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory()
                val safeLevel = when {
                    maxMemory < 128 * 1024 * 1024 -> compressionLevel.coerceAtMost(3)  // <128MB: level 0-3
                    maxMemory < 256 * 1024 * 1024 -> compressionLevel.coerceAtMost(9)  // <256MB: level 0-9
                    else -> compressionLevel.coerceIn(0, 22)  // zstd supports 0-22
                }
                if (safeLevel != compressionLevel) {
                    Logger.w(TAG, "RootfsManager: compression level lowered $compressionLevel -> $safeLevel (not enough memory)")
                }

                val rootfs = rootfsDir(context, name)
                if (!rootfs.exists() || !rootfs.isDirectory) {
                    return@withContext Result.failure(IllegalStateException("Rootfs not found for '$name'"))
                }
                outputDir.mkdirs()
                val outFile = File(outputDir, "${name}-rootfs.tar.zst")

                val excludeDirs = setOf("tmp", "proc", "sys", "dev", "run")
                val excludeFiles = setOf(".setup-complete")

                Logger.i(TAG, "RootfsManager: exporting container '$name' -> ${outFile.name}")
                val startTime = System.currentTimeMillis()

                // D-06：按真实类型收集（文件/目录/符号链接/硬链接）。
                // 原先只收 isFile：符号链接被展开成普通文件、悬挂链接与空目录整体丢失、
                // 硬链接被复制成多份 → 导出→导入往返后结构损坏、体积膨胀。
                val items = mutableListOf<ExportItem>()
                val hardLinkSeen = HashMap<Any, String>()
                var totalBytes = 0L
                rootfs.walkTopDown().forEach { file ->
                    val relPath = file.toRelativeString(rootfs)
                    if (relPath.isBlank()) return@forEach
                    val topDir = relPath.split("/").firstOrNull() ?: return@forEach
                    if (topDir in excludeDirs) return@forEach
                    if (file.name in excludeFiles) return@forEach
                    val attrs = try {
                        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    } catch (ex: Exception) {
                        Logger.w(TAG, "RootfsManager: failed to read attributes $relPath: ${ex.message}")
                        null
                    } ?: return@forEach
                    when {
                        attrs.isSymbolicLink -> {
                            val target = runCatching { Files.readSymbolicLink(file.toPath()).toString() }.getOrNull()
                            items.add(ExportItem(file, relPath, TarArchiveEntry.LF_SYMLINK, target))
                        }
                        attrs.isDirectory -> items.add(ExportItem(file, relPath, TarArchiveEntry.LF_DIR, null))
                        attrs.isRegularFile -> {
                            val key = attrs.fileKey()
                            val first = key?.let { hardLinkSeen[it] }
                            if (first != null) {
                                items.add(ExportItem(file, relPath, TarArchiveEntry.LF_LINK, first))
                            } else {
                                if (key != null) hardLinkSeen[key] = relPath
                                items.add(ExportItem(file, relPath, TarArchiveEntry.LF_NORMAL, null))
                                totalBytes += attrs.size()
                            }
                        }
                        else -> Logger.d(TAG, "RootfsManager: skipping special type $relPath")
                    }
                }
                val totalFiles = items.size
                var fileCount = 0
                var bytesWritten = 0L

                FileOutputStream(outFile).use { fos ->
                    ZstdOutputStream(fos, safeLevel).use { zstOut ->
                        TarArchiveOutputStream(zstOut).use { tar ->
                            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                            items.forEach { item ->
                                fileCount++
                                onProgress?.invoke(fileCount, totalFiles, bytesWritten, totalBytes, item.relPath)

                                val isDir = item.flag == TarArchiveEntry.LF_DIR
                                val entryName =
                                    if (isDir && !item.relPath.endsWith("/")) item.relPath + "/" else item.relPath
                                // 1.26.2 无 setLinkFlag（该方法在公开 API 从未存在），
                                // 用 TarArchiveEntry(name, linkFlag) 构造器写入类型标志。
                                val entry = TarArchiveEntry(entryName, item.flag)
                                if (item.linkName != null) entry.setLinkName(item.linkName)

                                try {
                                    val st = if (item.flag == TarArchiveEntry.LF_SYMLINK) {
                                        android.system.Os.lstat(item.file.absolutePath)
                                    } else {
                                        android.system.Os.stat(item.file.absolutePath)
                                    }
                                    entry.mode = st.st_mode
                                } catch (_: Exception) {}

                                when (item.flag) {
                                    TarArchiveEntry.LF_DIR,
                                    TarArchiveEntry.LF_SYMLINK,
                                    TarArchiveEntry.LF_LINK -> {
                                        entry.size = 0
                                        tar.putArchiveEntry(entry)
                                        tar.closeArchiveEntry()
                                    }
                                    else -> {
                                        val len = item.file.length()
                                        entry.size = len
                                        tar.putArchiveEntry(entry)
                                        FileInputStream(item.file).use { input ->
                                            input.copyTo(tar, bufferSize = DEFAULT_BUFFER_SIZE)
                                        }
                                        bytesWritten += len
                                        tar.closeArchiveEntry()
                                    }
                                }
                            }
                        }
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                val sizeMB = outFile.length() / (1024.0 * 1024.0)
                Logger.i(TAG, "RootfsManager: export done '$name' -> %.1f MB, took %dms".format(java.util.Locale.US, sizeMB, elapsed))
                Result.success(outFile)
            } catch (e: Exception) {
                Logger.e(TAG, "RootfsManager: export container '$name' failed", e)
                Result.failure(e)
            }
            }
    }

    private fun extractTar(inputStream: java.io.InputStream, destDir: File, onEntry: ((String) -> Unit)? = null) {
        // D-05：tar 中硬链接的目标常出现在链接条目**之后**，原先目标不存在就只打 warning 丢弃
        // → 导入的 rootfs 缺文件。先记账，遍历结束后统一补齐。
        val pendingLinks = mutableListOf<Triple<String, String, Int>>() // (entry 路径, 目标路径, mode)
        TarArchiveInputStream(inputStream).use { tar ->
            val rootCanonical = destDir.canonicalFile
            var entry: TarArchiveEntry?
            while (tar.nextTarEntry.also { entry = it } != null) {
                val e = entry ?: continue
                val name = e.name
                if (name.isBlank()) continue

                val normalized = normalizeEntryName(name) ?: continue
                onEntry?.invoke(normalized)
                val outFile = File(destDir, normalized)

                // 安全检查：防止路径穿越
                // D-38：加分隔符，/a/rootfs 才不会放行 /a/rootfs_evil
                if (!outFile.canonicalFile.path.startsWith(rootCanonical.path + File.separator)) {
                    Logger.w(TAG, "RootfsManager: rejected path traversal: $name")
                    continue
                }

                when {
                    e.isDirectory -> {
                        outFile.mkdirs()
                        setFilePermissions(outFile, e.mode)
                    }
                    e.isSymbolicLink -> {
                        outFile.parentFile?.mkdirs()
                        deleteIfExists(outFile)
                        try {
                            val linkName = e.linkName.orEmpty()
                            if (linkName.isNotEmpty()) {
                                Os.symlink(linkName, outFile.absolutePath)
                                Logger.d(TAG, "RootfsManager: symlink: $normalized -> $linkName")
                            }
                        } catch (ex: Exception) {
                            Logger.w(TAG, "RootfsManager: cannot create symlink $normalized: ${ex.message}")
                        }
                    }
                    e.isLink -> {
                        // Hard link → copy file content (Android doesn't support Os.link)
                        // Matches proot-distro behavior: materializes hard links as regular files
                        outFile.parentFile?.mkdirs()
                        deleteIfExists(outFile)
                        try {
                            val linkName = e.linkName.orEmpty()
                            if (linkName.isNotEmpty()) {
                                val normalizedTarget = linkName.removePrefix("/")
                                val targetFile = File(destDir, normalizedTarget)
                                if (targetFile.exists() && targetFile.isFile) {
                                    targetFile.copyTo(outFile, overwrite = true)
                                    // Use target file's permissions if link entry mode is 0
                                    val mode = if (e.mode != 0) e.mode else {
                                        try { Os.stat(targetFile.absolutePath).st_mode } catch (_: Exception) { 0 }
                                    }
                                    setFilePermissions(outFile, mode)
                                    Logger.d(TAG, "RootfsManager: hardlink→copy: $normalized <- $normalizedTarget")
                                } else {
                                    // D-05：目标还没解出来 → 记账，循环结束后补齐
                                    pendingLinks.add(Triple(normalized, normalizedTarget, e.mode))
                                }
                            }
                        } catch (ex: Exception) {
                            Logger.w(TAG, "RootfsManager: failed to copy hardlink $normalized: ${ex.message}")
                        }
                    }
                    e.isFile -> {
                        outFile.parentFile?.mkdirs()
                        if (outFile.exists() && outFile.isDirectory) outFile.deleteRecursively()
                        FileOutputStream(outFile).use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read: Int
                            while (tar.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                            }
                        }
                        setFilePermissions(outFile, e.mode)
                    }
                    else -> {
                        Logger.d(TAG, "RootfsManager: skipping special type: $normalized")
                    }
                }
            }
        }

        // D-05：补齐迟到的硬链接（目标可能本身又是后置链接 → 多轮，无进展即停）
        var pending = pendingLinks
        var guard = pendingLinks.size + 1
        while (pending.isNotEmpty() && guard-- > 0) {
            val unresolved = mutableListOf<Triple<String, String, Int>>()
            for ((entryPath, targetPath, mode) in pending) {
                val targetFile = File(destDir, targetPath.removePrefix("/"))
                if (!targetFile.exists() || !targetFile.isFile) {
                    unresolved.add(Triple(entryPath, targetPath, mode))
                    continue
                }
                try {
                    val outFile = File(destDir, entryPath)
                    outFile.parentFile?.mkdirs()
                    deleteIfExists(outFile)
                    targetFile.copyTo(outFile, overwrite = true)
                    val resolvedMode = if (mode != 0) mode else {
                        try { Os.stat(targetFile.absolutePath).st_mode } catch (_: Exception) { 0 }
                    }
                    setFilePermissions(outFile, resolvedMode)
                    Logger.d(TAG, "RootfsManager: hardlink->copy (deferred): $entryPath <- $targetPath")
                } catch (ex: Exception) {
                    Logger.w(TAG, "RootfsManager: deferred hardlink failed $entryPath: ${ex.message}")
                }
            }
            if (unresolved.size == pending.size) {
                unresolved.forEach { (p, t) ->
                    Logger.w(TAG, "RootfsManager: hard link target not found: $t for $p")
                }
                break
            }
            pending = unresolved
        }
    }

    private fun normalizeEntryName(rawName: String): String? {
        val trimmed = rawName.trim().replace('\\', '/')
        if (trimmed.isBlank() || trimmed.indexOf('\u0000') >= 0) return null
        val noLeadingSlash = trimmed.trimStart('/')
        val withoutDotPrefix = generateSequence(noLeadingSlash) { value ->
            if (value.startsWith("./")) value.removePrefix("./") else null
        }.last()
        if (withoutDotPrefix.isBlank()) return null
        val parts = withoutDotPrefix.split('/').filter { it.isNotBlank() && it != "." }
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun deleteIfExists(file: File) {
        if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    private fun setFilePermissions(file: File, mode: Int) {
        // D-13：原先 mask 0o777 把 setuid/setgid/sticky 全部抹掉（再强制 or 0600/0700），
        // rootfs 里 /tmp 1777、passwd 的 setuid 等特殊权限被改坏。
        // 改为按 0o7777 全集保留，仍然补上属主必备位。
        var permissions = mode and 0b1111_1111_1111 // 0o7777
        if (permissions == 0) return
        permissions = if (file.isDirectory) permissions or 448 else permissions or 384
        try { Os.chmod(file.absolutePath, permissions) } catch (_: Exception) {}
    }

    /**
     * D-14：收尾配置写入统一入口。
     * 原先 resolv.conf/hosts 无条件覆盖，若它们是符号链接（systemd-resolved 常见）
     * 且目标不存在 → FileNotFoundException → 整个安装在 rename 之前失败，只剩 staging 残留。
     * 现在先判 isSymbolicLink（是则删掉链接本身，绝不跟随写到容器外），失败也不影响后续步骤。
     */
    private fun writeHostFile(target: File, content: String) {
        runCatching {
            target.parentFile?.mkdirs()
            if (java.nio.file.Files.isSymbolicLink(target.toPath())) {
                Logger.w(TAG, "RootfsManager: ${target.name} is a symlink, deleting and recreating")
                target.delete()
            }
            target.writeText(content)
        }.onFailure { e ->
            Logger.w(TAG, "RootfsManager: write ${target.name} failed: ${e.message}")
        }
    }

    private fun generateResolvConf(rootfs: File) {
        val resolv = File(rootfs, "etc/resolv.conf")
        writeHostFile(resolv, 
            """
            |nameserver 8.8.8.8
            |nameserver 1.1.1.1
            |nameserver 223.5.5.5
            """.trimMargin()
        )
    }

    private fun generateEtcHosts(rootfs: File) {
        val hosts = File(rootfs, "etc/hosts")
        writeHostFile(hosts, "127.0.0.1 localhost\n")
    }

    private fun generateAptConfig(rootfs: File) {
        val aptConf = File(rootfs, "etc/apt/apt.conf.d/99proot-nosandbox")
        writeHostFile(aptConf, "APT::Sandbox::User \"root\";\n")
    }

    private fun generateDpkgConfig(rootfs: File) {
        val dpkgConf = File(rootfs, "etc/dpkg/dpkg.cfg.d/force-unsafe-io")
        writeHostFile(dpkgConf, "force-unsafe-io\n")
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    private fun countFiles(dir: File): Int {
        var count = 0
        dir.walkTopDown().forEach { if (it.isFile) count++ }
        return count
    }

    data class ContainerInfo(
        val name: String,
        val ready: Boolean,
        val sizeBytes: Long,
        val lastModified: Long = 0L,
        val fileCount: Int = 0,
        val path: String = ""
    ) {
        val sizeDisplay: String
            get() {
                val mb = sizeBytes / (1024.0 * 1024.0)
                return if (mb >= 1.0) "%.1f MB".format(java.util.Locale.US, mb) else "%.0f KB".format(java.util.Locale.US, sizeBytes / 1024.0)
            }
    }
}
