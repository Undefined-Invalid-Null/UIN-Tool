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

    /**
     * 获取所有启用源的插件列表
     */
    suspend fun fetchAllSourcesPlugins(sources: List<SourceInfo>): List<RepoPluginInfo> {
        val allPlugins = mutableListOf<RepoPluginInfo>()
        
        for (source in sources.filter { it.enabled }) {
            try {
                val plugins = fetchSourcePlugins(source)
                allPlugins.addAll(plugins)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to fetch plugins from source: ${source.sourceId}", e)
            }
        }
        
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
            return emptyList()
        }
        
        val plugins = pluginsResult.getOrNull() ?: return emptyList()
        val baseUrl = source.getDownloadBaseUrl()
        
        for (pluginMap in plugins) {
            try {
                val plugin = RepoPluginInfo()
                
                plugin.pluginId = pluginMap["pluginId"] as? String ?: ""
                plugin.name = pluginMap["name"] as? String ?: ""
                plugin.author = pluginMap["author"] as? String ?: ""
                plugin.description = pluginMap["description"] as? String ?: ""
                plugin.version = pluginMap["version"] as? String ?: ""
                plugin.versionName = pluginMap["versionName"] as? String ?: ""
                plugin.updateLog = pluginMap["updateLog"] as? String ?: ""
                plugin.size = (pluginMap["size"] as? Number)?.toLong() ?: 0
                plugin.lastUpdate = pluginMap["lastUpdate"] as? String ?: ""
                plugin.repositoryUrl = pluginMap["repositoryUrl"] as? String ?: ""
                plugin.uiType = pluginMap["uiType"] as? String ?: ""
                
                // 设置源信息
                plugin.sourceId = source.sourceId
                plugin.sourceName = source.getDisplayName()
                
                // 构建下载 URL
                val tpkPath = pluginMap["tpkPath"] as? String ?: ""
                if (tpkPath.isNotEmpty()) {
                    plugin.downloadUrl = "$baseUrl/$tpkPath"
                    plugin.tpkPath = tpkPath
                }
                
                // 构建图标 URL
                val iconPath = pluginMap["iconPath"] as? String ?: ""
                if (iconPath.isNotEmpty()) {
                    plugin.iconUrl = "$baseUrl/$iconPath"
                    plugin.iconPath = iconPath
                } else {
                    // 默认图标路径
                    val pluginDir = pluginMap["pluginId"] as? String ?: ""
                    plugin.iconUrl = "$baseUrl/plugins/$pluginDir/icon.png"
                }
                
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