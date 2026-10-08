package com.UIN.Tool.cache

import com.UIN.Tool.constants.AppConstants
import com.UIN.Tool.log.Logger
import java.io.File
import java.security.MessageDigest

/**
 * 磁盘缓存管理器
 * - 文件名: URL 的 SHA-256 哈希
 * - 元数据: {hash}.meta (etag, lastModified, timestamp)
 * - 支持 TTL 过期清理
 */
object DiskCache {

    private const val TAG = "DiskCache"
    private const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L // 1 天

    private const val MEMORY_CACHE_MAX_BYTES = 8L * 1024 * 1024 // B-73：内存缓存字节上限
    private const val DISK_CACHE_MAX_BYTES = 64L * 1024 * 1024 // B-74：磁盘缓存总容量上限

    // B-73：原先是无上限 ConcurrentHashMap，图标全量常驻内存；
    // 改为按访问顺序的 LinkedHashMap + 字节上限，超出即淘汰最久未访问项
    private val memoryLock = Any()
    private val memoryCache = LinkedHashMap<String, ByteArray>(64, 0.75f, true)
    private var memoryBytes = 0L
    private var defaultTtlMs = DEFAULT_TTL_MS

    private fun memGet(hash: String): ByteArray? = synchronized(memoryLock) { memoryCache[hash] }

    private fun memPut(hash: String, data: ByteArray) = synchronized(memoryLock) {
        if (data.size > MEMORY_CACHE_MAX_BYTES) return
        memoryCache[hash]?.let { memoryBytes -= it.size }
        memoryCache[hash] = data
        memoryBytes += data.size
        val it = memoryCache.entries.iterator()
        while (memoryBytes > MEMORY_CACHE_MAX_BYTES && it.hasNext()) {
            val eldest = it.next()
            memoryBytes -= eldest.value.size
            it.remove()
        }
    }

    private fun memRemove(hash: String) = synchronized(memoryLock) {
        memoryCache.remove(hash)?.let { memoryBytes -= it.size }
    }

    private fun memClear() = synchronized(memoryLock) {
        memoryCache.clear()
        memoryBytes = 0L
    }

    /**
     * 获取缓存目录
     */
    fun getCacheDir(subDir: String = "disk_cache"): File {
        return File(AppConstants.CACHE_DIR, subDir).also { it.mkdirs() }
    }

    /**
     * 获取缓存文件（内存 → 磁盘）
     */
    fun get(url: String, subDir: String = "disk_cache"): ByteArray? {
        val hash = sha256(url)
        val dir = getCacheDir(subDir)
        val file = File(dir, hash)

        // 1. 内存缓存
        memGet(hash)?.let { return it }

        // 2. 磁盘缓存
        if (!file.exists()) return null

        // 检查 TTL
        val meta = readMeta(dir, hash)
        if (meta != null && System.currentTimeMillis() - meta.timestamp > defaultTtlMs) {
            Logger.d(TAG, "Cache expired for: $url")
            file.delete()
            File(dir, "$hash.meta").delete()
            return null
        }

        val data = file.readBytes()
        memPut(hash, data)
        return data
    }

    /**
     * 写入缓存
     */
    fun put(url: String, data: ByteArray, etag: String? = null, lastModified: String? = null, subDir: String = "disk_cache") {
        val hash = sha256(url)
        val dir = getCacheDir(subDir)
        val file = File(dir, hash)

        // B-75：先写临时文件再 rename，避免进程被杀/写失败留下半截缓存字节
        val tmp = File(dir, "$hash.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.delete()
                Logger.w(TAG, "atomic move failed for: $url")
            }
        }
        memPut(hash, data)

        // 写入元数据
        val meta = CacheMeta(
            timestamp = System.currentTimeMillis(),
            etag = etag,
            lastModified = lastModified
        )
        writeMeta(dir, hash, meta)

        Logger.d(TAG, "Cached ${data.size} bytes for: $url")
    }

    /**
     * 获取缓存的 ETag（用于条件请求）
     */
    fun getEtag(url: String, subDir: String = "disk_cache"): String? {
        val hash = sha256(url)
        val dir = getCacheDir(subDir)
        return readMeta(dir, hash)?.etag
    }

    /**
     * 获取缓存的 Last-Modified（用于条件请求）
     */
    fun getLastModified(url: String, subDir: String = "disk_cache"): String? {
        val hash = sha256(url)
        val dir = getCacheDir(subDir)
        return readMeta(dir, hash)?.lastModified
    }

    /**
     * 检查缓存是否存在且未过期
     */
    fun exists(url: String, subDir: String = "disk_cache"): Boolean {
        val hash = sha256(url)
        val dir = getCacheDir(subDir)
        val file = File(dir, hash)
        if (!file.exists()) return false

        val meta = readMeta(dir, hash)
        return meta == null || System.currentTimeMillis() - meta.timestamp <= defaultTtlMs
    }

    /**
     * 清理过期缓存
     */
    fun cleanup(subDir: String = "disk_cache") {
        val dir = getCacheDir(subDir)
        val now = System.currentTimeMillis()
        var cleaned = 0

        // B-74：原先只遍历 .meta——缺 meta 的数据文件永远不会被清（磁盘无限增长）。
        // 改为遍历数据文件本身，时间戳优先取 meta、缺 meta 时回退文件 mtime。
        val dataFiles = dir.listFiles()?.filter { it.isFile && it.extension != "meta" } ?: emptyList()

        for (file in dataFiles) {
            val meta = readMeta(dir, file.name)
            val ts = meta?.timestamp ?: file.lastModified()
            if (now - ts > defaultTtlMs) {
                file.delete()
                File(dir, "${file.name}.meta").delete()
                File(dir, "${file.name}.tmp").delete()
                memRemove(file.name)
                cleaned++
            }
        }

        // 孤儿 meta（数据文件已被删）一并清掉
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.extension == "meta" && !File(dir, f.nameWithoutExtension).exists()) {
                f.delete()
            }
        }

        // B-74：总容量上限，超出按最旧优先淘汰
        enforceDiskLimit(dir)

        if (cleaned > 0) {
            Logger.i(TAG, "Cleaned $cleaned expired cache entries")
        }
    }

    /** B-74：磁盘缓存总大小超过上限时，按 lastModified 从旧到新删除，直到回到上限内 */
    private fun enforceDiskLimit(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && it.extension != "meta" } ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_CACHE_MAX_BYTES) return

        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_CACHE_MAX_BYTES) break
            val len = file.length()
            if (file.delete()) {
                total -= len
                File(dir, "${file.name}.meta").delete()
                memRemove(file.name)
            }
        }
        Logger.i(TAG, "Disk cache trimmed, now ${total / 1024} KB")
    }

    /**
     * 清理所有缓存
     */
    fun clearAll(subDir: String = "disk_cache") {
        val dir = getCacheDir(subDir)
        dir.deleteRecursively()
        dir.mkdirs()
        memClear()
        Logger.i(TAG, "Cleared all disk cache")
    }

    /**
     * 设置 TTL
     */
    fun setTtl(ttlMs: Long) {
        defaultTtlMs = ttlMs
    }

    // ==================== 内部方法 ====================

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun readMeta(dir: File, hash: String): CacheMeta? {
        val metaFile = File(dir, "$hash.meta")
        return readMetaFromFile(metaFile)
    }

    private fun readMetaFromFile(metaFile: File): CacheMeta? {
        if (!metaFile.exists()) return null
        return try {
            val lines = metaFile.readLines()
            CacheMeta(
                timestamp = lines.getOrNull(0)?.toLongOrNull() ?: return null,
                etag = lines.getOrNull(1)?.ifEmpty { null },
                lastModified = lines.getOrNull(2)?.ifEmpty { null }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun writeMeta(dir: File, hash: String, meta: CacheMeta) {
        val metaFile = File(dir, "$hash.meta")
        val tmp = File(dir, "$hash.meta.tmp")
        tmp.writeText(buildString {
            appendLine(meta.timestamp)
            appendLine(meta.etag ?: "")
            appendLine(meta.lastModified ?: "")
        })
        if (!tmp.renameTo(metaFile)) {
            metaFile.delete()
            if (!tmp.renameTo(metaFile)) tmp.delete()
        }
    }

    private data class CacheMeta(
        val timestamp: Long,
        val etag: String?,
        val lastModified: String?
    )
}
