// app/src/main/java/com/UIN/Tool/domain/repository/IPluginRepository.kt
package com.UIN.Tool.domain.repository

import com.UIN.Tool.domain.model.PluginInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 插件仓库接口 - 前后端分离的抽象层
 * 定义所有插件相关的操作
 */
interface IPluginRepository {
    
    // ==================== 获取插件列表 ====================
    fun getPlugins(): StateFlow<List<PluginInfo>>
    suspend fun getPlugin(pluginId: String): PluginInfo?
    
    // ==================== 安装/卸载 ====================
    suspend fun installPlugin(filePath: String): PluginInfo?
    suspend fun uninstallPlugin(pluginId: String): Boolean
    
    // ==================== 刷新 ====================
    suspend fun refreshPlugins()
    
    // ==================== 搜索和分类 ====================
    fun searchPlugins(keyword: String): List<PluginInfo>
    fun getPluginsByCategory(category: String): List<PluginInfo>
    fun getAllCategories(): List<String>
    suspend fun updatePluginCategory(pluginId: String, category: String): Boolean
    
    // ==================== 运行插件 ====================
    suspend fun openPlugin(pluginId: String)
    
    // ==================== 状态查询 ====================
    fun getInstalledPluginIds(): List<String>
    fun getPluginCount(): Int
    fun isPluginInstalled(pluginId: String): Boolean
    
    // ==================== 插件生命周期 ====================
    suspend fun onPluginResume(pluginId: String)
    suspend fun onPluginPause(pluginId: String)
    suspend fun onPluginDestroy(pluginId: String)
    suspend fun onPluginBackPressed(pluginId: String): Boolean
}