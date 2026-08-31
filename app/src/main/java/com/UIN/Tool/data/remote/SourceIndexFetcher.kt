package com.UIN.Tool.data.remote

import com.UIN.Tool.log.Logger
import com.UIN.Tool.domain.model.SourceInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 源索引获取器
 * 从 GitHub API 或 raw URL 拉取 source.json，支持缓存
 */
class SourceIndexFetcher(
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "SourceIndexFetcher"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 分钟缓存
    }

    private val apiClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val rawClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 内存缓存：sourceId -> (plugins, timestamp)
    private val cache = mutableMapOf<String, CacheEntry>()

    private data class CacheEntry(
        val plugins: List<Map<String, Any>>,
        val timestamp: Long
    )

    /**
     * 获取单个源的插件列表
     * 优先 GitHub API（带 ETag），失败回退 raw URL
     */
    suspend fun fetchSourcePlugins(source: SourceInfo): Result<List<Map<String, Any>>> {
        // 检查缓存
        val cached = cache[source.sourceId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            Logger.d(TAG, "Using cached plugins for source: ${source.sourceId}")
            return Result.success(cached.plugins)
        }

        return try {
            // 优先尝试 GitHub API
            val plugins = fetchViaApi(source)
            if (plugins != null) {
                cache[source.sourceId] = CacheEntry(plugins, System.currentTimeMillis())
                return Result.success(plugins)
            }

            // 回退 raw URL
            val rawPlugins = fetchViaRaw(source)
            if (rawPlugins != null) {
                cache[source.sourceId] = CacheEntry(rawPlugins, System.currentTimeMillis())
                return Result.success(rawPlugins)
            }

            Result.failure(Exception("Failed to fetch source index for ${source.sourceId}"))
        } catch (e: Exception) {
            Logger.e(TAG, "Error fetching source: ${source.sourceId}", e)
            Result.failure(e)
        }
    }

    /**
     * 通过 GitHub API 获取 source.json（带 base64 解码）
     */
    private suspend fun fetchViaApi(source: SourceInfo): List<Map<String, Any>>? {
        return try {
            val request = Request.Builder()
                .url(source.getApiIndexUrl())
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "UIN-Tool-Android")
                .build()

            apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val json = response.body?.string() ?: return@use null
                val obj = JSONObject(json)
                val content = obj.optString("content", "")
                if (content.isEmpty()) return@use null

                // base64 解码
                val decoded = String(android.util.Base64.decode(
                    content.replace("\n", ""),
                    android.util.Base64.DEFAULT
                ))

                parseSourceJson(decoded)
            }
        } catch (e: Exception) {
            Logger.d(TAG, "API fetch failed for ${source.sourceId}, trying raw")
            null
        }
    }

    /**
     * 通过 raw URL 获取 source.json
     */
    private suspend fun fetchViaRaw(source: SourceInfo): List<Map<String, Any>>? {
        return try {
            val url = "${source.getRawIndexUrl()}?t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UIN-Tool-Android")
                .build()

            rawClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val json = response.body?.string() ?: return@use null
                parseSourceJson(json)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Raw fetch failed for ${source.sourceId}", e)
            null
        }
    }

    /**
     * 解析 source.json 内容，提取插件列表
     */
    private fun parseSourceJson(json: String): List<Map<String, Any>> {
        val plugins = mutableListOf<Map<String, Any>>()
        val obj = JSONObject(json)
        val pluginsArray = obj.optJSONArray("plugins") ?: return plugins

        for (i in 0 until pluginsArray.length()) {
            val plugin = pluginsArray.getJSONObject(i)
            val pluginMap = mutableMapOf<String, Any>()

            pluginMap["pluginId"] = plugin.optString("pluginId", "")
            pluginMap["name"] = plugin.optString("name", "")
            pluginMap["author"] = plugin.optString("author", "")
            pluginMap["description"] = plugin.optString("description", "")
            pluginMap["version"] = plugin.optString("version", "")
            pluginMap["versionName"] = plugin.optString("versionName", "")
            pluginMap["tpkPath"] = plugin.optString("tpkPath", "")
            pluginMap["iconPath"] = plugin.optString("iconPath", "")
            pluginMap["uiType"] = plugin.optString("uiType", "")
            pluginMap["updateLog"] = plugin.optString("updateLog", "")
            pluginMap["size"] = plugin.optLong("size", 0)
            pluginMap["lastUpdate"] = plugin.optString("lastUpdate", "")
            pluginMap["repositoryUrl"] = plugin.optString("repositoryUrl", "")

            plugins.add(pluginMap)
        }

        return plugins
    }

    /**
     * 清除所有缓存
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * 清除指定源的缓存
     */
    fun clearCache(sourceId: String) {
        cache.remove(sourceId)
    }
}
