package com.UIN.Tool.domain.model

import com.UIN.Tool.utils.formatFileSize

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
    var isInstalled: Boolean = false,
    var sourceId: String = "",
    var sourceName: String = "",
    var tpkPath: String = "",
    var iconPath: String = "",
    var uiType: String = "",
    var hasUpdate: Boolean = false,
    var installedVersion: Int = 0
) {
    
    fun getFormattedSize(): String {
        return formatFileSize(size)
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