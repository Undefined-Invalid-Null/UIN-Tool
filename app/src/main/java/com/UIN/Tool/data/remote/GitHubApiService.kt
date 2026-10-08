package com.UIN.Tool.data.remote

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.domain.model.RepoPluginInfo
import com.UIN.Tool.domain.model.SourceInfo
import com.UIN.Tool.log.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubApiService(
    private val client: OkHttpClient
) {
    
    companion object {
        private const val TAG = "GitHubApiService"
    }
    
    private val indexFetcher = SourceIndexFetcher(client)

    /** D-31：最近一次 fetchAllSourcesPlugins 中失败的源（「显示名: 原因」），供 UI 上浮 */
    @Volatile
    var lastFetchFailures: List<String> = emptyList()
        private set

    /**
     * 获取所有启用源的插件列表
     */
    suspend fun fetchAllSourcesPlugins(sources: List<SourceInfo>): List<RepoPluginInfo> {
        val allPlugins = mutableListOf<RepoPluginInfo>()
        val failures = mutableListOf<String>()

        for (source in sources.filter { it.enabled }) {
            try {
                val plugins = fetchSourcePlugins(source)
                allPlugins.addAll(plugins)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to fetch plugins from source: ${source.sourceId}", e)
                // D-31①：单源失败原先只进日志，用户看到空/缺列表却毫无提示
                failures.add("${source.getDisplayName()}: ${e.message ?: e.toString()}")
            }
        }

        lastFetchFailures = failures
        return allPlugins
    }

    /**
     * 获取单个源的插件列表
     */
    suspend fun fetchSourcePlugins(source: SourceInfo): List<RepoPluginInfo> {
        val result = mutableListOf<RepoPluginInfo>()

        val pluginsResult = indexFetcher.fetchSourcePlugins(source)
        if (pluginsResult.isFailure) {
            Logger.e(TAG, "Failed to fetch source index: ${source.sourceId}")
            // D-31①：原先 return emptyList() 吞掉原因，调用方无从区分「源里没插件」与「源挂了」
            throw pluginsResult.exceptionOrNull()
                ?: java.io.IOException(Str.get(R.string.failed_to_load_plugins))
        }
        
        val plugins = pluginsResult.getOrNull() ?: return emptyList()
        val baseUrl = source.getDownloadBaseUrl()
        
        for (pluginMap in plugins) {
            try {
                val pluginId = pluginMap["pluginId"] as? String ?: ""
                val tpkPath = pluginMap["tpkPath"] as? String ?: ""
                val iconPath = pluginMap["iconPath"] as? String ?: ""
                // D-42A2：原「先 no-arg 构造、后逐字段赋值」收敛为构造器实参（模型全 val）
                val plugin = RepoPluginInfo(
                    pluginId = pluginId,
                    name = pluginMap["name"] as? String ?: "",
                    author = pluginMap["author"] as? String ?: "",
                    description = pluginMap["description"] as? String ?: "",
                    version = pluginMap["version"] as? String ?: "",
                    versionName = pluginMap["versionName"] as? String ?: "",
                    updateLog = pluginMap["updateLog"] as? String ?: "",
                    size = (pluginMap["size"] as? Number)?.toLong() ?: 0,
                    lastUpdate = pluginMap["lastUpdate"] as? String ?: "",
                    repositoryUrl = pluginMap["repositoryUrl"] as? String ?: "",
                    uiType = pluginMap["uiType"] as? String ?: "",
                    downloadUrl = if (tpkPath.isNotEmpty()) "$baseUrl/$tpkPath" else "",
                    tpkPath = tpkPath,
                    iconUrl = if (iconPath.isNotEmpty()) "$baseUrl/$iconPath" else "$baseUrl/plugins/$pluginId/icon.png",
                    iconPath = iconPath,
                    sourceId = source.sourceId,
                    sourceName = source.getDisplayName()
                )

                result.add(plugin)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to parse plugin from source: ${source.sourceId}", e)
            }
        }
        
        return result
    }
    
    /**
     * 清除所有缓存
     */
    fun clearCache() {
        indexFetcher.clearCache()
    }
}