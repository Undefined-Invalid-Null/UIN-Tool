package com.UIN.Tool.cache

import com.UIN.Tool.constants.AppConstants
import com.UIN.Tool.log.Logger
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 磁盘缓存管理器
 * - 文件名: URL 的 SHA-256 哈希
 * - 元数据: {hash}.meta (etag, lastModified, timestamp)
 * - 支持 TTL 过期清理
 */
object DiskCache {

    private const val TAG = "DiskCache"
    private const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L // 1 天

    private val memoryCache = ConcurrentHashMap<String, ByteArray>()
    private var defaultTtlMs = DEFAULT_TTL_MS

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
        memoryCache[hash]?.let { return it }

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
        memoryCache[hash] = data
        return data
    }

    /**
     * 写入缓存
     */
    fun put(url: String, data: ByteArray, etag: String? = null, lastModified: String? = null, subDir: String = "disk_cache") {
        val hash = sha256(url)
        val dir = getCacheDir(subDir)
        val file = File(dir, hash)

        file.writeBytes(data)
        memoryCache[hash] = data

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

        dir.listFiles()?.forEach { file ->
            if (file.extension == "meta") {
                val meta = readMetaFromFile(file)
                if (meta != null && now - meta.timestamp > defaultTtlMs) {
                    val hash = file.nameWithoutExtension
                    File(dir, hash).delete()
                    file.delete()
                    memoryCache.remove(hash)
                    cleaned++
                }
            }
        }

        if (cleaned > 0) {
            Logger.i(TAG, "Cleaned $cleaned expired cache entries")
        }
    }

    /**
     * 清理所有缓存
     */
    fun clearAll(subDir: String = "disk_cache") {
        val dir = getCacheDir(subDir)
        dir.deleteRecursively()
        dir.mkdirs()
        memoryCache.clear()
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
        metaFile.writeText(buildString {
            appendLine(meta.timestamp)
            appendLine(meta.etag ?: "")
            appendLine(meta.lastModified ?: "")
        })
    }

    private data class CacheMeta(
        val timestamp: Long,
        val etag: String?,
        val lastModified: String?
    )
}
