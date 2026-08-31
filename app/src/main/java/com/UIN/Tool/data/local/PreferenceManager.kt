package com.UIN.Tool.data.local

import android.content.Context
import android.content.SharedPreferences
import com.UIN.Tool.constants.AppConstants as Constants

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

    // ==================== 更新辅助 ====================

    /** 最近一次检测到的新版本变更日志（Markdown），供开屏页展示。 */
    fun getLastChangelog(): String {
        return prefs.getString(Constants.KEY_LAST_CHANGELOG, "") ?: ""
    }

    fun setLastChangelog(notes: String) {
        prefs.edit().putString(Constants.KEY_LAST_CHANGELOG, notes).apply()
    }

    /** 最近一次静默更新检测所在的天（epoch day = 距今天数）。无记录返回 -1。 */
    fun getLastUpdateCheckDay(): Long {
        return prefs.getLong(Constants.KEY_LAST_UPDATE_CHECK, -1L)
    }

    fun setLastUpdateCheckDay(epochDay: Long) {
        prefs.edit().putLong(Constants.KEY_LAST_UPDATE_CHECK, epochDay).apply()
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

    // ==================== 插件多开 ====================

    /** 原生插件是否允许多开（内置单实例限制，开发者选项可开启实验性支持） */
    fun isNativeMultiInstanceEnabled(): Boolean {
        return prefs.getBoolean(Constants.KEY_NATIVE_MULTI_INSTANCE, false)
    }

    fun setNativeMultiInstanceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_NATIVE_MULTI_INSTANCE, enabled).apply()
    }

    /** 共享端口模式下关闭插件时是否保留页面/后端会话（重开直接复用，不重新走环境流水线）。
     * 默认开启：默认启用「关闭时保留会话」（同一 Web 插件只保留一个后台窗口，不默认多开）；
     * 用户显式关闭后才允许多开。 */
    fun isSharedSessionRetainEnabled(): Boolean {
        return prefs.getBoolean(Constants.KEY_SHARED_SESSION_RETAIN, true)
    }

    fun setSharedSessionRetainEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_SHARED_SESSION_RETAIN, enabled).apply()
    }

    /** 开发者选项：忽略签名验证（默认关闭；开启后安装/更新插件跳过签名校验） */
    fun isDevIgnoreSignatureEnabled(): Boolean {
        return prefs.getBoolean(Constants.KEY_DEV_IGNORE_SIGNATURE, false)
    }

    fun setDevIgnoreSignatureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_DEV_IGNORE_SIGNATURE, enabled).apply()
    }

    // ==================== 源管理 ====================

    /** 获取已保存的源列表 JSON 字符串 */
    fun getSourcesJson(): String {
        return prefs.getString("sources_json", "") ?: ""
    }

    /** 保存源列表 JSON 字符串 */
    fun setSourcesJson(json: String) {
        prefs.edit().putString("sources_json", json).apply()
    }

    /** 获取已启用的源 ID 列表 */
    fun getEnabledSourceIds(): List<String> {
        val str = prefs.getString("enabled_source_ids", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",").filter { it.isNotEmpty() }
    }

    /** 保存已启用的源 ID 列表 */
    fun setEnabledSourceIds(ids: List<String>) {
        prefs.edit().putString("enabled_source_ids", ids.joinToString(",")).apply()
    }

    /** 获取上次刷新时间 */
    fun getLastRepoRefreshTime(): Long {
        return prefs.getLong("last_repo_refresh_time", 0L)
    }

    /** 保存上次刷新时间 */
    fun setLastRepoRefreshTime(time: Long) {
        prefs.edit().putLong("last_repo_refresh_time", time).apply()
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