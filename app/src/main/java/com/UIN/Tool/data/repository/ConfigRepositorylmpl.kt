// app/src/main/java/com/UIN/Tool/data/repository/ConfigRepositoryImpl.kt
package com.UIN.Tool.data.repository

import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.repository.IConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 配置仓库实现类 - 实现 IConfigRepository 接口
 * 使用 PreferenceManager 作为底层数据源
 */
class ConfigRepositoryImpl(
    private val preferenceManager: PreferenceManager
) : IConfigRepository {

    // ==================== 工作目录 ====================
    private val _workFolder = MutableStateFlow(preferenceManager.getWorkFolder())
    override fun getWorkFolder(): StateFlow<String> = _workFolder.asStateFlow()

    override suspend fun setWorkFolder(path: String) {
        preferenceManager.setWorkFolder(path)
        _workFolder.value = path
    }

    // ==================== 视图模式 ====================
    private val _viewMode = MutableStateFlow(preferenceManager.getViewMode())
    override fun getViewMode(): StateFlow<String> = _viewMode.asStateFlow()

    override suspend fun setViewMode(mode: String) {
        preferenceManager.setViewMode(mode)
        _viewMode.value = mode
    }

    // ==================== 图标着色 ====================
    override suspend fun setIconTintEnabled(enabled: Boolean) {
        preferenceManager.setUseCustomIconTint(enabled)
    }

    override fun isIconTintEnabled(): Boolean {
        return preferenceManager.isUseCustomIconTint()
    }

    // ==================== 启动状态 ====================
    override suspend fun setFirstLaunch(completed: Boolean) {
        preferenceManager.setFirstLaunch(completed)
    }

    override fun isFirstLaunch(): Boolean {
        return preferenceManager.isFirstLaunch()
    }

    override suspend fun setLastVersion(version: String) {
        preferenceManager.setLastVersion(version)
    }

    override fun getLastVersion(): String {
        return preferenceManager.getLastVersion()
    }

    // ==================== 更新相关 ====================
    override suspend fun setIgnoredVersion(version: String) {
        preferenceManager.setIgnoredVersion(version)
    }

    override fun getIgnoredVersion(): String {
        return preferenceManager.getIgnoredVersion()
    }

    override suspend fun setForceUpdateIgnored(tag: String) {
        preferenceManager.setForceUpdateIgnored(tag)
    }

    override fun getForceUpdateIgnored(): String {
        return preferenceManager.getForceUpdateIgnored()
    }

    // ==================== 崩溃相关 ====================
    override suspend fun setJustCrashed(crashed: Boolean) {
        preferenceManager.setJustCrashed(crashed)
    }

    override fun hasJustCrashed(): Boolean {
        return preferenceManager.hasJustCrashed()
    }

    // ==================== 插件签名 ====================
    override suspend fun savePluginSignature(pluginName: String, signature: String) {
        preferenceManager.savePluginSignature(pluginName, signature)
    }

    override fun getPluginSignature(pluginName: String): String? {
        return preferenceManager.getPluginSignature(pluginName)
    }

    override suspend fun removePluginSignature(pluginName: String) {
        preferenceManager.removePluginSignature(pluginName)
    }

    // ==================== 插件权限 ====================
    override fun getPluginPermissions(pluginId: String): Map<String, Boolean> {
        return preferenceManager.getPluginPermissions(pluginId)
    }

    override suspend fun savePluginPermission(pluginId: String, permission: String, granted: Boolean) {
        preferenceManager.savePluginPermission(pluginId, permission, granted)
    }

    override suspend fun savePluginPermissions(pluginId: String, permissions: Map<String, Boolean>) {
        preferenceManager.savePluginPermissions(pluginId, permissions)
    }

    override suspend fun clearPluginPermissions(pluginId: String) {
        preferenceManager.clearPluginPermissions(pluginId)
    }

    // ==================== 镜像站 ====================
    override fun getEnabledMirrors(): List<String> {
        return preferenceManager.getEnabledMirrors()
    }

    override suspend fun setEnabledMirrors(mirrors: List<String>) {
        preferenceManager.setEnabledMirrors(mirrors)
    }

    override fun isUseCdn(): Boolean {
        return preferenceManager.isUseCdn()
    }

    override suspend fun setUseCdn(use: Boolean) {
        preferenceManager.setUseCdn(use)
    }

    // ==================== 自定义镜像站 ====================
    override fun getCustomMirrors(): List<String> {
        return preferenceManager.getCustomMirrors()
    }

    override suspend fun setCustomMirrors(mirrors: List<String>) {
        preferenceManager.setCustomMirrors(mirrors)
    }

    // ==================== 主题 ====================
    override fun getCurrentTheme(): String {
        return preferenceManager.getCurrentTheme()
    }

    override suspend fun setCurrentTheme(theme: String) {
        preferenceManager.setCurrentTheme(theme)
    }

    // ==================== UI配置 ====================
    override fun getUiConfig(): String {
        return preferenceManager.getUiConfig()
    }

    override suspend fun setUiConfig(config: String) {
        preferenceManager.setUiConfig(config)
    }
}