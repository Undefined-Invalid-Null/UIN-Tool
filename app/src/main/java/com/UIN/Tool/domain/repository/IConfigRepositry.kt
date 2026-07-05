// app/src/main/java/com/UIN/Tool/domain/repository/IConfigRepository.kt
package com.UIN.Tool.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * 配置仓库接口 - 前后端分离的抽象层
 * 定义所有配置相关的操作
 */
interface IConfigRepository {
    
    // ==================== 工作目录 ====================
    fun getWorkFolder(): StateFlow<String>
    suspend fun setWorkFolder(path: String)
    
    // ==================== 视图模式 ====================
    fun getViewMode(): StateFlow<String>
    suspend fun setViewMode(mode: String)
    
    // ==================== 图标着色 ====================
    suspend fun setIconTintEnabled(enabled: Boolean)
    fun isIconTintEnabled(): Boolean
    
    // ==================== 启动状态 ====================
    suspend fun setFirstLaunch(completed: Boolean)
    fun isFirstLaunch(): Boolean
    
    suspend fun setLastVersion(version: String)
    fun getLastVersion(): String
    
    // ==================== 更新相关 ====================
    suspend fun setIgnoredVersion(version: String)
    fun getIgnoredVersion(): String
    
    suspend fun setForceUpdateIgnored(tag: String)
    fun getForceUpdateIgnored(): String
    
    // ==================== 崩溃相关 ====================
    suspend fun setJustCrashed(crashed: Boolean)
    fun hasJustCrashed(): Boolean
    
    // ==================== 插件签名 ====================
    suspend fun savePluginSignature(pluginName: String, signature: String)
    fun getPluginSignature(pluginName: String): String?
    suspend fun removePluginSignature(pluginName: String)
    
    // ==================== 插件权限 ====================
    fun getPluginPermissions(pluginId: String): Map<String, Boolean>
    suspend fun savePluginPermission(pluginId: String, permission: String, granted: Boolean)
    suspend fun savePluginPermissions(pluginId: String, permissions: Map<String, Boolean>)
    suspend fun clearPluginPermissions(pluginId: String)
    
    // ==================== 镜像站 ====================
    fun getEnabledMirrors(): List<String>
    suspend fun setEnabledMirrors(mirrors: List<String>)
    fun isUseCdn(): Boolean
    suspend fun setUseCdn(use: Boolean)
    
    // ==================== 自定义镜像站 ====================
    fun getCustomMirrors(): List<String>
    suspend fun setCustomMirrors(mirrors: List<String>)
    
    // ==================== 主题 ====================
    fun getCurrentTheme(): String
    suspend fun setCurrentTheme(theme: String)
    
    // ==================== UI配置 ====================
    fun getUiConfig(): String
    suspend fun setUiConfig(config: String)
}