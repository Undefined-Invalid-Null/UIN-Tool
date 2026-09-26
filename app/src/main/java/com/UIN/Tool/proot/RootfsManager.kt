package com.UIN.Tool.proot

import android.content.Context
import android.system.Os
import com.UIN.Tool.constants.AppConstants
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
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

    private const val TAG = "RootfsManager"
    private const val CONTAINERS_DIR = "containers"
    private const val SETUP_MARKER = ".setup-complete"

    private val installingLocks = ConcurrentHashMap<String, Any>()

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
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isDirectory }?.map { d ->
            val name = d.name
            val rootfs = File(d, "rootfs")
            val ready = isContainerReady(context, name)
            val size = if (rootfs.exists()) calculateDirSize(rootfs) else 0L
            val fileCount = if (rootfs.exists()) countFiles(rootfs) else 0
            ContainerInfo(name, ready, size, d.lastModified(), fileCount, d.absolutePath)
        }?.sortedBy { it.name } ?: emptyList()
    }

    suspend fun installFromAsset(
        context: Context,
        containerName: String,
        assetName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val lock = installingLocks.getOrPut(containerName) { Any() }
        synchronized(lock) {
            if (isContainerReady(context, containerName)) {
                Logger.i(TAG, "RootfsManager: 容器 '$containerName' 已就绪，跳过安装")
                return@withContext Result.success(Unit)
            }
            Logger.i(TAG, "RootfsManager: 开始从 asset 安装容器 '$containerName' (asset=$assetName)")
            try {
                val destDir = containerDir(context, containerName)
                val stagingDir = File(destDir.parent, "${containerName}.staging")
                if (stagingDir.exists()) {
                    Logger.i(TAG, "RootfsManager: 清理残留 staging 目录")
                    stagingDir.deleteRecursively()
                }
                stagingDir.mkdirs()

                val rootfsDest = File(stagingDir, "rootfs")
                rootfsDest.mkdirs()

                Logger.i(TAG, "RootfsManager: 正在解压 rootfs...")
                context.assets.open(assetName).use { rawInput ->
                    val inputStream = when {
                        assetName.endsWith(".xz") -> {
                            Logger.i(TAG, "RootfsManager: 检测到 XZ 压缩，先解压 XZ 层")
                            XZCompressorInputStream(rawInput)
                        }
                        assetName.endsWith(".gz") || assetName.endsWith(".tgz") -> {
                            Logger.i(TAG, "RootfsManager: 检测到 GZIP 压缩，先解压 GZIP 层")
                            GZIPInputStream(rawInput)
                        }
                        assetName.endsWith(".zst") -> {
                            Logger.i(TAG, "RootfsManager: 检测到 ZSTD 压缩，先解压 ZSTD 层")
                            ZstdInputStream(rawInput)
                        }
                        else -> rawInput
                    }
                    inputStream.use { extractTar(it, rootfsDest) }
                }

                Logger.i(TAG, "RootfsManager: 生成 resolv.conf 和 hosts")
                generateResolvConf(rootfsDest)
                generateEtcHosts(rootfsDest)
                generateAptConfig(rootfsDest)
                generateDpkgConfig(rootfsDest)

                // 确保 proot 所需的关键目录存在并设置权限
                val tmpDir = File(rootfsDest, "tmp")
                tmpDir.mkdirs()
                runCatching { Os.chmod(tmpDir.absolutePath, 511) } // 0777
                val varTmpDir = File(rootfsDest, "var/tmp")
                varTmpDir.mkdirs()
                runCatching { Os.chmod(varTmpDir.absolutePath, 511) } // 0777
                val runtimeDir = File(tmpDir, "runtime-root")
                runtimeDir.mkdirs()
                runCatching { Os.chmod(runtimeDir.absolutePath, 448) } // 0700
                val rootDir = File(rootfsDest, "root")
                if (!rootDir.exists()) {
                    rootDir.mkdirs()
                    Logger.i(TAG, "RootfsManager: 创建 /root 目录")
                }

                if (destDir.exists()) destDir.deleteRecursively()
                stagingDir.renameTo(destDir)

                File(destDir, SETUP_MARKER).createNewFile()
                Logger.i(TAG, "RootfsManager: 容器 '$containerName' 安装完成 (asset=$assetName)")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e(TAG, "RootfsManager: 容器 '$containerName' 安装失败", e)
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
                val inputStream = when {
                    tarFile.name.endsWith(".tar.xz") -> XZCompressorInputStream(fis)
                    tarFile.name.endsWith(".tar.gz") || tarFile.name.endsWith(".tgz") -> GZIPInputStream(fis)
                    tarFile.name.endsWith(".tar.zst") || tarFile.name.endsWith(".zst") -> ZstdInputStream(fis)
                    else -> fis
                }
                TarArchiveInputStream(inputStream).use { tar ->
                    while (tar.nextTarEntry != null) count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    suspend fun installFromFile(
        context: Context,
        containerName: String,
        tarFile: File,
        onProgress: ((entryCount: Int, entryName: String, bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val lock = installingLocks.getOrPut(containerName) { Any() }
        synchronized(lock) {
            try {
                val destDir = containerDir(context, containerName)
                val stagingDir = File(destDir.parent, "${containerName}.staging")
                if (stagingDir.exists()) stagingDir.deleteRecursively()
                stagingDir.mkdirs()

                val rootfsDest = File(stagingDir, "rootfs")
                rootfsDest.mkdirs()

                val totalBytes = tarFile.length()
                var entryCount = 0

                FileInputStream(tarFile).use { fis ->
                    val inputStream = when {
                        tarFile.name.endsWith(".tar.xz") -> XZCompressorInputStream(fis)
                        tarFile.name.endsWith(".tar.gz") || tarFile.name.endsWith(".tgz") -> GZIPInputStream(fis)
                        tarFile.name.endsWith(".tar.zst") || tarFile.name.endsWith(".zst") -> ZstdInputStream(fis)
                        else -> fis
                    }
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

                if (destDir.exists()) destDir.deleteRecursively()
                stagingDir.renameTo(destDir)

                File(destDir, SETUP_MARKER).createNewFile()
                Logger.i(TAG, "Container '$containerName' installed from file: ${tarFile.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to install container '$containerName' from file", e)
                Result.failure(e)
            }
        }
    }

    fun deleteContainer(context: Context, name: String): Boolean {
        val dir = containerDir(context, name)
        if (!dir.exists()) return false
        Logger.i(TAG, "RootfsManager: 删除容器 '$name'")
        val deleted = dir.deleteRecursively()
        if (deleted) Logger.i(TAG, "RootfsManager: 容器 '$name' 已删除")
        return deleted
    }

    suspend fun resetContainer(context: Context, name: String): Result<Unit> {
        Logger.i(TAG, "RootfsManager: 重置容器 '$name'")
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
                Logger.w(TAG, "RootfsManager: 压缩级别从 $compressionLevel 降至 $safeLevel（可用内存不足）")
            }

            val rootfs = rootfsDir(context, name)
            if (!rootfs.exists() || !rootfs.isDirectory) {
                return@withContext Result.failure(IllegalStateException("Rootfs not found for '$name'"))
            }
            outputDir.mkdirs()
            val outFile = File(outputDir, "${name}-rootfs.tar.zst")

            val excludeDirs = setOf("tmp", "proc", "sys", "dev", "run")
            val excludeFiles = setOf(".setup-complete")

            Logger.i(TAG, "RootfsManager: 开始导出容器 '$name' → ${outFile.name}")
            val startTime = System.currentTimeMillis()

            // 先统计文件数和总大小
            val allFiles = mutableListOf<File>()
            var totalBytes = 0L
            rootfs.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val relPath = file.toRelativeString(rootfs)
                if (relPath.isBlank()) return@forEach
                val topDir = relPath.split("/").firstOrNull() ?: return@forEach
                if (topDir in excludeDirs) return@forEach
                if (file.name in excludeFiles) return@forEach
                allFiles.add(file)
                totalBytes += file.length()
            }
            val totalFiles = allFiles.size
            var fileCount = 0
            var bytesWritten = 0L

            FileOutputStream(outFile).use { fos ->
                ZstdOutputStream(fos, safeLevel).use { zstOut ->
                    TarArchiveOutputStream(zstOut).use { tar ->
                        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                        allFiles.forEach { file ->
                            fileCount++
                            val relPath = file.toRelativeString(rootfs)
                            onProgress?.invoke(fileCount, totalFiles, bytesWritten, totalBytes, relPath)

                            val entry = TarArchiveEntry(file, relPath)
                            entry.size = file.length()

                            try {
                                val st = android.system.Os.stat(file.absolutePath)
                                entry.mode = st.st_mode
                            } catch (_: Exception) {}

                            tar.putArchiveEntry(entry)
                            FileInputStream(file).use { input ->
                                input.copyTo(tar, bufferSize = DEFAULT_BUFFER_SIZE)
                            }
                            bytesWritten += file.length()
                            tar.closeArchiveEntry()
                        }
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            val sizeMB = outFile.length() / (1024.0 * 1024.0)
            Logger.i(TAG, "RootfsManager: 导出完成 '$name' → %.1f MB, 耗时 %dms".format(sizeMB, elapsed))
            Result.success(outFile)
        } catch (e: Exception) {
            Logger.e(TAG, "RootfsManager: 导出容器 '$name' 失败", e)
            Result.failure(e)
        }
    }

    private fun extractTar(inputStream: java.io.InputStream, destDir: File, onEntry: ((String) -> Unit)? = null) {
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
                if (!outFile.canonicalFile.path.startsWith(rootCanonical.path)) {
                    Logger.w(TAG, "RootfsManager: 拒绝路径穿越: $name")
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
                            Logger.w(TAG, "RootfsManager: 无法创建 symlink $normalized: ${ex.message}")
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
                                    Logger.w(TAG, "RootfsManager: hard link target not found: $normalizedTarget for $normalized")
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
                        Logger.d(TAG, "RootfsManager: 跳过特殊类型: $normalized")
                    }
                }
            }
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
        var permissions = mode and 0b111_111_111
        if (permissions <= 0) return
        permissions = if (file.isDirectory) permissions or 448 else permissions or 384
        try { Os.chmod(file.absolutePath, permissions) } catch (_: Exception) {}
    }

    private fun generateResolvConf(rootfs: File) {
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText(
            """
            |nameserver 8.8.8.8
            |nameserver 1.1.1.1
            |nameserver 223.5.5.5
            """.trimMargin()
        )
    }

    private fun generateEtcHosts(rootfs: File) {
        val hosts = File(rootfs, "etc/hosts")
        hosts.parentFile?.mkdirs()
        hosts.writeText("127.0.0.1 localhost\n")
    }

    private fun generateAptConfig(rootfs: File) {
        val aptConf = File(rootfs, "etc/apt/apt.conf.d/99proot-nosandbox")
        aptConf.parentFile?.mkdirs()
        aptConf.writeText("APT::Sandbox::User \"root\";\n")
    }

    private fun generateDpkgConfig(rootfs: File) {
        val dpkgConf = File(rootfs, "etc/dpkg/dpkg.cfg.d/force-unsafe-io")
        dpkgConf.parentFile?.mkdirs()
        dpkgConf.writeText("force-unsafe-io\n")
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
                return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(sizeBytes / 1024.0)
            }
    }
}
