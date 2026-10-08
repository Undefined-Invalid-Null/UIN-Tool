package com.UIN.Tool.domain.model

/**
 * 源信息
 * 从 registry.json 或本地配置解析
 */
data class SourceInfo(
    val sourceId: String = "",
    val name: String = "",
    val owner: String = "",
    val repo: String = "",
    val branch: String = "dist",
    val description: String = "",
    val trustLevel: String = "community",
    val addedAt: String = "",
    val enabled: Boolean = true
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
            "official" -> com.UIN.Tool.utils.Str.get(com.UIN.Tool.R.string.trust_official)
            "verified" -> com.UIN.Tool.utils.Str.get(com.UIN.Tool.R.string.trust_verified)
            else -> com.UIN.Tool.utils.Str.get(com.UIN.Tool.R.string.trust_community)
        }
    }
}
