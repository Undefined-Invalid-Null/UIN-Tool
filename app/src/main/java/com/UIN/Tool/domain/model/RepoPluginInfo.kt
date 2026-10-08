package com.UIN.Tool.domain.model

import com.UIN.Tool.utils.formatFileSize

/**
 * 仓库插件信息
 * 从GitHub获取的插件信息
 */
data class RepoPluginInfo(
    val pluginId: String = "",
    val name: String = "",
    val author: String = "",
    val description: String = "",
    val version: String = "",
    val versionName: String = "",
    val downloadUrl: String = "",
    val iconUrl: String = "",
    val updateLog: String = "",
    val size: Long = 0,
    val lastUpdate: String = "",
    val repositoryUrl: String = "",
    val sourceId: String = "",
    val sourceName: String = "",
    val tpkPath: String = "",
    val iconPath: String = "",
    val uiType: String = ""
    // D-42①：运行时派生态（isInstalled/installedVersion/hasUpdate）已外移至
    // RepoViewModel.InstalledMeta（按 pluginId 索引），domain model 不再混入状态
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