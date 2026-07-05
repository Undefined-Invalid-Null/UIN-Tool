package com.UIN.Tool.domain.model

/**
 * 仓库插件信息
 * 从GitHub获取的插件信息
 */
data class RepoPluginInfo(
    var pluginId: String = "",
    var name: String = "",
    var author: String = "",
    var description: String = "",
    var version: String = "",
    var versionName: String = "",
    var downloadUrl: String = "",
    var iconUrl: String = "",
    var updateLog: String = "",
    var size: Long = 0,
    var lastUpdate: String = "",
    var repositoryUrl: String = "",
    var isInstalled: Boolean = false
) {
    
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        }
    }
    
    fun getFormattedDate(): String {
        return if (lastUpdate.isNotEmpty()) {
            try {
                lastUpdate.substring(0, 10)
            } catch (e: Exception) {
                lastUpdate
            }
        } else ""
    }
}