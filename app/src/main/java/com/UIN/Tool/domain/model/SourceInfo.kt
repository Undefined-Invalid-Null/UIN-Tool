package com.UIN.Tool.domain.model

/**
 * 源信息
 * 从 registry.json 或本地配置解析
 */
data class SourceInfo(
    var sourceId: String = "",
    var name: String = "",
    var owner: String = "",
    var repo: String = "",
    var branch: String = "dist",
    var description: String = "",
    var trustLevel: String = "community",
    var addedAt: String = "",
    var enabled: Boolean = true
) {
    /**
     * 源 index.json 的原始 URL
     */
    fun getRawIndexUrl(): String {
        return "https://raw.githubusercontent.com/$owner/$repo/$branch/source.json"
    }

    /**
     * GitHub API URL（带缓存）
     */
    fun getApiIndexUrl(): String {
        return "https://api.github.com/repos/$owner/$repo/contents/source.json?ref=$branch"
    }

    /**
     * 插件下载基础 URL（dist 分支）
     */
    fun getDownloadBaseUrl(): String {
        return "https://raw.githubusercontent.com/$owner/$repo/$branch"
    }

    /**
     * 仓库页面 URL
     */
    fun getRepoUrl(): String {
        return "https://github.com/$owner/$repo"
    }

    fun getDisplayName(): String {
        return name.ifEmpty { sourceId }
    }

    fun getTrustLabel(): String {
        return when (trustLevel) {
            "official" -> "官方"
            "verified" -> "认证"
            else -> "社区"
        }
    }
}
