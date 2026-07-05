package com.UIN.Tool.data.local

import android.content.Context
import android.content.SharedPreferences
import com.UIN.Tool.utils.Constants

class PreferenceManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    private val signaturePrefs: SharedPreferences = context.getSharedPreferences(Constants.PREF_PLUGIN_SIGNATURES, Context.MODE_PRIVATE)
    private val crashPrefs: SharedPreferences = context.getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE)
    private val mirrorPrefs: SharedPreferences = context.getSharedPreferences(Constants.PREF_GITHUB_MIRROR, Context.MODE_PRIVATE)

    // ==================== 工作目录 ====================

    fun getWorkFolder(): String {
        return prefs.getString(Constants.KEY_WORK_FOLDER, Constants.WORK_DIR) ?: Constants.WORK_DIR
    }

    fun setWorkFolder(path: String) {
        prefs.edit().putString(Constants.KEY_WORK_FOLDER, path).apply()
    }

    // ==================== 视图模式 ====================

    fun getViewMode(): String {
        return prefs.getString(Constants.KEY_VIEW_MODE, "list") ?: "list"
    }

    fun setViewMode(mode: String) {
        prefs.edit().putString(Constants.KEY_VIEW_MODE, mode).apply()
    }

    // ==================== 图标着色 ====================

    fun isUseCustomIconTint(): Boolean {
        return prefs.getBoolean(Constants.KEY_USE_CUSTOM_ICON_TINT, true)
    }

    fun setUseCustomIconTint(use: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_USE_CUSTOM_ICON_TINT, use).apply()
    }

    // ==================== 启动状态 ====================

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(Constants.KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunch(completed: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_FIRST_LAUNCH, completed).apply()
    }

    fun getLastVersion(): String {
        return prefs.getString(Constants.KEY_LAST_VERSION, "") ?: ""
    }

    fun setLastVersion(version: String) {
        prefs.edit().putString(Constants.KEY_LAST_VERSION, version).apply()
    }

    // ==================== 更新相关 ====================

    fun getIgnoredVersion(): String {
        return prefs.getString(Constants.KEY_IGNORE_VERSION, "") ?: ""
    }

    fun setIgnoredVersion(version: String) {
        prefs.edit().putString(Constants.KEY_IGNORE_VERSION, version).apply()
    }

    fun getForceUpdateIgnored(): String {
        return prefs.getString(Constants.KEY_FORCE_UPDATE_IGNORE, "") ?: ""
    }

    fun setForceUpdateIgnored(tag: String) {
        prefs.edit().putString(Constants.KEY_FORCE_UPDATE_IGNORE, tag).apply()
    }

    // ==================== 崩溃相关 ====================

    fun hasJustCrashed(): Boolean {
        return crashPrefs.getBoolean(Constants.KEY_JUST_CRASHED, false)
    }

    fun setJustCrashed(crashed: Boolean) {
        crashPrefs.edit().putBoolean(Constants.KEY_JUST_CRASHED, crashed).apply()
    }

    // ==================== 插件签名 ====================

    fun getPluginSignature(pluginName: String): String? {
        return signaturePrefs.getString(pluginName, null)
    }

    fun savePluginSignature(pluginName: String, signature: String) {
        signaturePrefs.edit().putString(pluginName, signature).apply()
    }

    fun removePluginSignature(pluginName: String) {
        signaturePrefs.edit().remove(pluginName).apply()
    }

    // ==================== 插件权限 ====================

    fun getPluginPermissions(pluginId: String): Map<String, Boolean> {
        val prefs = context.getSharedPreferences("${Constants.KEY_PLUGIN_PERMISSIONS}$pluginId", Context.MODE_PRIVATE)
        val result = mutableMapOf<String, Boolean>()
        prefs.all.forEach { (key, value) ->
            if (value is Boolean) {
                result[key] = value
            }
        }
        return result
    }

    fun savePluginPermission(pluginId: String, permission: String, granted: Boolean) {
        val prefs = context.getSharedPreferences("${Constants.KEY_PLUGIN_PERMISSIONS}$pluginId", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(permission, granted).apply()
    }

    fun savePluginPermissions(pluginId: String, permissions: Map<String, Boolean>) {
        val prefs = context.getSharedPreferences("${Constants.KEY_PLUGIN_PERMISSIONS}$pluginId", Context.MODE_PRIVATE)
        permissions.forEach { (key, value) ->
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    fun clearPluginPermissions(pluginId: String) {
        val prefs = context.getSharedPreferences("${Constants.KEY_PLUGIN_PERMISSIONS}$pluginId", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // ==================== 镜像站 ====================

    fun getEnabledMirrors(): List<String> {
        val str = mirrorPrefs.getString(Constants.KEY_ENABLED_MIRRORS, "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",").filter { it.isNotEmpty() }
    }

    fun setEnabledMirrors(mirrors: List<String>) {
        mirrorPrefs.edit().putString(Constants.KEY_ENABLED_MIRRORS, mirrors.joinToString(",")).apply()
    }

    fun isUseCdn(): Boolean {
        return mirrorPrefs.getBoolean(Constants.KEY_USE_CDN, true)
    }

    fun setUseCdn(use: Boolean) {
        mirrorPrefs.edit().putBoolean(Constants.KEY_USE_CDN, use).apply()
    }

    // ==================== 自定义镜像站 ====================

    fun getCustomMirrors(): List<String> {
        val str = prefs.getString("custom_mirrors", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split("\n").filter { it.isNotEmpty() }
    }

    fun setCustomMirrors(mirrors: List<String>) {
        prefs.edit().putString("custom_mirrors", mirrors.joinToString("\n")).apply()
    }

    // ==================== 主题 ====================

    fun getCurrentTheme(): String {
        return prefs.getString(Constants.KEY_CURRENT_THEME, "default") ?: "default"
    }

    fun setCurrentTheme(theme: String) {
        prefs.edit().putString(Constants.KEY_CURRENT_THEME, theme).apply()
    }

    // ==================== UI配置 ====================

    fun getUiConfig(): String {
        return prefs.getString("ui_config", "") ?: ""
    }

    fun setUiConfig(config: String) {
        prefs.edit().putString("ui_config", config).apply()
    }
    fun getPrefs(): SharedPreferences {
    return prefs
}
}