// app/src/main/java/com/UIN/Tool/data/repository/PluginRepositoryImpl.kt
package com.UIN.Tool.data.repository

import com.UIN.Tool.UinApplication
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.domain.repository.IPluginRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import kotlinx.coroutines.flow.StateFlow

class PluginRepositoryImpl(
    private val pluginManager: PluginManager
) : IPluginRepository {

    companion object {
        private const val TAG = "PluginRepositoryImpl"
    }

    override fun getPlugins(): StateFlow<List<PluginInfo>> {
        return pluginManager.plugins
    }

    override suspend fun getPlugin(pluginId: String): PluginInfo? {
        return pluginManager.getPluginInfo(pluginId)
    }

    override suspend fun installPlugin(filePath: String): PluginInfo? {
        return try {
            val file = java.io.File(filePath)
            val info = pluginManager.installPlugin(file, file.name)
            if (info != null) {
                Logger.success(TAG, "安装成功: ${info.name}")
            }
            info
        } catch (e: Exception) {
            Logger.e(TAG, "安装插件失败", e)
            null
        }
    }

    override suspend fun uninstallPlugin(pluginId: String): Boolean {
        return try {
            val result = pluginManager.uninstallPlugin(pluginId)
            if (result) {
                Logger.success(TAG, "卸载成功: $pluginId")
            }
            result
        } catch (e: Exception) {
            Logger.e(TAG, "卸载插件失败", e)
            false
        }
    }

    override suspend fun refreshPlugins() {
        pluginManager.refreshPlugins()
    }

    override fun searchPlugins(keyword: String): List<PluginInfo> {
        return pluginManager.searchPlugins(keyword)
    }

    override fun getPluginsByCategory(category: String): List<PluginInfo> {
        return pluginManager.getPluginsByCategory(category)
    }

    override fun getAllCategories(): List<String> {
        return pluginManager.getAllCategories()
    }

    override suspend fun updatePluginCategory(pluginId: String, category: String): Boolean {
        return pluginManager.updatePluginCategory(pluginId, category)
    }

    override suspend fun openPlugin(pluginId: String) {
        // 使用 UinApplication 获取 Context
        val context = UinApplication.getAppContext()
        pluginManager.openPlugin(pluginId, context)
    }

    override fun getInstalledPluginIds(): List<String> {
        return pluginManager.getInstalledPluginIds()
    }

    override fun getPluginCount(): Int {
        return pluginManager.getPluginCount()
    }

    override fun isPluginInstalled(pluginId: String): Boolean {
        return pluginManager.isPluginInstalled(pluginId)
    }

    override suspend fun onPluginResume(pluginId: String) {
        pluginManager.onPluginResume(pluginId)
    }

    override suspend fun onPluginPause(pluginId: String) {
        pluginManager.onPluginPause(pluginId)
    }

    override suspend fun onPluginDestroy(pluginId: String) {
        pluginManager.onPluginDestroy(pluginId)
    }

    override suspend fun onPluginBackPressed(pluginId: String): Boolean {
        return pluginManager.onPluginBackPressed(pluginId)
    }
}