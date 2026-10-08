package com.UIN.Tool.data.remote

import com.UIN.Tool.log.Logger
import com.UIN.Tool.domain.model.SourceInfo
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

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

    // D-09④：普通 mutableMap 在并发拉取多源时会互相踩踏 → 换 ConcurrentHashMap
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** D-09①：sourceId -> ETag，配合 If-None-Match 走 304 */
    private val etags = ConcurrentHashMap<String, String>()

    /** D-09②：GitHub API 限流截止时间（epoch ms），到期前直接跳过 API 调用 */
    @Volatile
    private var rateLimitedUntilMs = 0L

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
        // D-09②：命中限流冷却期就不再打 API（未认证仅 60 次/小时）
        if (System.currentTimeMillis() < rateLimitedUntilMs) {
            Logger.d(TAG, "API rate limited, skip for ${source.sourceId}")
            return null
        }
        return try {
            val builder = Request.Builder()
                .url(source.getApiIndexUrl())
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "UIN-Tool-Android")
            // D-09①：注释一直说"带 ETag"，实际从未发送 If-None-Match
            etags[source.sourceId]?.let { builder.header("If-None-Match", it) }

            // D-09③：execute() 是阻塞调用且协程取消时无法中断 → enqueue + 挂起
            executeAsync(apiClient, builder.build()).use { response ->
                when {
                    response.code == 304 -> {
                        Logger.d(TAG, "Not modified (ETag) for ${source.sourceId}")
                        cache[source.sourceId]?.plugins
                    }
                    response.code == 403 || response.code == 429 -> {
                        noteRateLimit(response)
                        null
                    }
                    !response.isSuccessful -> null
                    else -> {
                        response.header("ETag")?.let { etags[source.sourceId] = it }
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
                }
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
            val cached = cache[source.sourceId]
            val builder = Request.Builder()
                .url(source.getRawIndexUrl())
                .header("User-Agent", "UIN-Tool-Android")
            if (cached != null) {
                etags[source.sourceId]?.let { builder.header("If-None-Match", it) }
            }

            executeAsync(rawClient, builder.build()).use { response ->
                when {
                    response.code == 304 && cached != null -> cached.plugins
                    response.code == 403 || response.code == 429 -> {
                        noteRateLimit(response)
                        null
                    }
                    !response.isSuccessful -> null
                    else -> {
                        response.header("ETag")?.let { etags[source.sourceId] = it }
                        val json = response.body?.string() ?: return@use null
                        parseSourceJson(json)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Raw fetch failed for ${source.sourceId}", e)
            null
        }
    }

    /** D-09②：记录限流恢复时间（优先 Retry-After / X-RateLimit-Reset） */
    private fun noteRateLimit(response: Response) {
        var until = System.currentTimeMillis() + 60_000L
        response.header("Retry-After")?.toLongOrNull()?.let { retry ->
            if (retry > 0) until = System.currentTimeMillis() + retry * 1000L
        }
        response.header("X-RateLimit-Reset")?.toLongOrNull()?.let { reset ->
            if (reset > 0) until = maxOf(until, reset * 1000L)
        }
        rateLimitedUntilMs = until
        Logger.w(TAG, "Rate limited (HTTP ${response.code}), backing off until $until")
    }

    /** D-09③：enqueue 封装为可取消的挂起调用 */
    private suspend fun executeAsync(client: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            })
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
