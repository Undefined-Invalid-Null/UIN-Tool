package com.UIN.Tool.data.remote

import com.UIN.Tool.domain.model.RepoPluginInfo
import com.UIN.Tool.log.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubApiService(
    private val client: OkHttpClient
) {
    
    companion object {
        private const val TAG = "GitHubApiService"
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val GITHUB_ORG = "UIN-Tool-Plugins"
    }
    
    suspend fun fetchOrgRepos(): List<RepoPluginInfo> {
        val result = mutableListOf<RepoPluginInfo>()
        val url = "$GITHUB_API_BASE/orgs/$GITHUB_ORG/repos?per_page=100"
        
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "UIN-Tool-Android")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return emptyList()
                    val repos = JSONArray(json)
                    
                    for (i in 0 until repos.length()) {
                        val repo = repos.getJSONObject(i)
                        val repoName = repo.getString("name")
                        
                        if (repoName == ".github" || repoName == "Docs" || repoName == "docs") {
                            continue
                        }
                        
                        val description = repo.optString("description", "")
                        val plugin = fetchLatestRelease(repoName)
                        plugin?.let {
                            it.name = if (description.isNotEmpty()) description else repoName
                            result.add(it)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "获取仓库列表失败", e)
        }
        
        return result
    }
    
    suspend fun fetchLatestRelease(repoName: String): RepoPluginInfo? {
        val url = "$GITHUB_API_BASE/repos/$GITHUB_ORG/$repoName/releases/latest"
        
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "UIN-Tool-Android")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return null
                    parseRelease(repoName, json)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "获取Release失败: $repoName", e)
            null
        }
    }
    
    private fun parseRelease(repoName: String, json: String): RepoPluginInfo? {
        return try {
            val release = JSONObject(json)
            val plugin = RepoPluginInfo()
            plugin.pluginId = repoName
            
            val tagName = release.optString("tag_name", "")
            if (tagName.contains("-")) {
                val parts = tagName.split("-", limit = 2)
                plugin.version = parts[0]
                plugin.versionName = if (parts.size > 1) parts[1] else parts[0]
            } else {
                plugin.version = "1"
                plugin.versionName = if (tagName.isNotEmpty()) tagName else "1.0.0"
            }
            
            plugin.updateLog = release.optString("body", "")
            plugin.lastUpdate = release.optString("published_at", "")
            plugin.repositoryUrl = release.optString("html_url", "")
            
            val assets = release.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".tpk")) {
                    plugin.downloadUrl = asset.getString("browser_download_url")
                    plugin.size = asset.getLong("size")
                    break
                }
            }
            
            if (plugin.downloadUrl.isEmpty()) {
                return null
            }
            
            plugin.author = "UIN 社区"
            plugin.description = "UIN Tool 插件"
            plugin
        } catch (e: Exception) {
            Logger.e(TAG, "解析Release失败", e)
            null
        }
    }
}