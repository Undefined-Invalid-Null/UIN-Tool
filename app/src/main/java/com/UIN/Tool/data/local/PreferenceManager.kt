package com.UIN.Tool.data.local

import android.content.Context
import android.content.SharedPreferences
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceManager(private val context: Context) {

    // D-27：改为 lazy —— 原先构造即打开 4 个 SP，首次访问在主线程触发磁盘加载
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE) }
    private val signaturePrefs: SharedPreferences by lazy { context.getSharedPreferences(Constants.PREF_PLUGIN_SIGNATURES, Context.MODE_PRIVATE) }
    private val crashPrefs: SharedPreferences by lazy { context.getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE) }
    private val mirrorPrefs: SharedPreferences by lazy { context.getSharedPreferences(Constants.PREF_GITHUB_MIRROR, Context.MODE_PRIVATE) }

    // D-27⑤：类型安全读取 —— 历史上同 key 可能写过别的类型，直接 getString 会抛 ClassCastException
    private fun getStringSafe(sp: SharedPreferences, key: String, def: String?): String? =
        try { sp.getString(key, def) } catch (e: ClassCastException) { def }

    private fun getBooleanSafe(sp: SharedPreferences, key: String, def: Boolean): Boolean =
        try { sp.getBoolean(key, def) } catch (e: ClassCastException) { def }

    private fun getLongSafe(sp: SharedPreferences, key: String, def: Long): Long =
        try { sp.getLong(key, def) } catch (e: ClassCastException) { def }

    private fun getIntSafe(sp: SharedPreferences, key: String, def: Int): Int =
        try { sp.getInt(key, def) } catch (e: ClassCastException) { def }

    // ==================== 工作目录 ====================

    // D-28：workFolder / viewMode 的唯一 StateFlow 源。
    // 本类是两个值的唯一写入口，因此 MainActivity / SplashActivity / SettingsViewModel /
    // ConfigRepository 等任何写入都会同步到这两个 Flow，消除「仓库缓存 StateFlow
    // 永不更新」的双写源问题；仓库只负责转发。

    fun getWorkFolder(): String {
        return getStringSafe(prefs, Constants.KEY_WORK_FOLDER, Constants.WORK_DIR) ?: Constants.WORK_DIR
    }

    fun setWorkFolder(path: String) {
        prefs.edit().putString(Constants.KEY_WORK_FOLDER, path).apply()
        _workFolderFlow.value = path
    }

    private val _workFolderFlow = MutableStateFlow(getWorkFolder())
    fun workFolderFlow(): StateFlow<String> = _workFolderFlow.asStateFlow()

    // ==================== 视图模式 ====================

    fun getViewMode(): String {
        return getStringSafe(prefs, Constants.KEY_VIEW_MODE, "list") ?: "list"
    }

    fun setViewMode(mode: String) {
        prefs.edit().putString(Constants.KEY_VIEW_MODE, mode).apply()
        _viewModeFlow.value = mode
    }

    private val _viewModeFlow = MutableStateFlow(getViewMode())
    fun viewModeFlow(): StateFlow<String> = _viewModeFlow.asStateFlow()

    // ==================== 图标着色 ====================

    fun isUseCustomIconTint(): Boolean {
        return getBooleanSafe(prefs, Constants.KEY_USE_CUSTOM_ICON_TINT, true)
    }

    fun setUseCustomIconTint(use: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_USE_CUSTOM_ICON_TINT, use).apply()
    }

    // ==================== 启动状态 ====================

    fun isFirstLaunch(): Boolean {
        return getBooleanSafe(prefs, Constants.KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunch(completed: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_FIRST_LAUNCH, completed).apply()
    }

    fun getLastVersion(): String {
        return getStringSafe(prefs, Constants.KEY_LAST_VERSION, "") ?: ""
    }

    fun setLastVersion(version: String) {
        prefs.edit().putString(Constants.KEY_LAST_VERSION, version).apply()
    }

    // ==================== 更新相关 ====================

    fun getIgnoredVersion(): String {
        return getStringSafe(prefs, Constants.KEY_IGNORE_VERSION, "") ?: ""
    }

    fun setIgnoredVersion(version: String) {
        prefs.edit().putString(Constants.KEY_IGNORE_VERSION, version).apply()
    }

    fun getForceUpdateIgnored(): String {
        return getStringSafe(prefs, Constants.KEY_FORCE_UPDATE_IGNORE, "") ?: ""
    }

    fun setForceUpdateIgnored(tag: String) {
        prefs.edit().putString(Constants.KEY_FORCE_UPDATE_IGNORE, tag).apply()
    }

    // ==================== 更新辅助 ====================

    /** 最近一次检测到的新版本变更日志（Markdown），供开屏页展示。 */
    fun getLastChangelog(): String {
        return getStringSafe(prefs, Constants.KEY_LAST_CHANGELOG, "") ?: ""
    }

    fun setLastChangelog(notes: String) {
        prefs.edit().putString(Constants.KEY_LAST_CHANGELOG, notes).apply()
    }

    /** 最近一次静默更新检测所在的天（epoch day = 距今天数）。无记录返回 -1。 */
    fun getLastUpdateCheckDay(): Long {
        return getLongSafe(prefs, Constants.KEY_LAST_UPDATE_CHECK, -1L)
    }

    fun setLastUpdateCheckDay(epochDay: Long) {
        prefs.edit().putLong(Constants.KEY_LAST_UPDATE_CHECK, epochDay).apply()
    }

    // ==================== 崩溃相关 ====================

    fun hasJustCrashed(): Boolean {
        return getBooleanSafe(crashPrefs, Constants.KEY_JUST_CRASHED, false)
    }

    fun setJustCrashed(crashed: Boolean) {
        crashPrefs.edit().putBoolean(Constants.KEY_JUST_CRASHED, crashed).apply()
    }

    // ==================== 插件签名 ====================

    fun getPluginSignature(pluginName: String): String? {
        return getStringSafe(signaturePrefs, pluginName, null)
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
        // D-27：原先每个 key 单独 apply()（N 次磁盘写），改为一次 edit 批量提交
        val editor = prefs.edit()
        permissions.forEach { (key, value) -> editor.putBoolean(key, value) }
        editor.apply()
    }

    fun clearPluginPermissions(pluginId: String) {
        val prefs = context.getSharedPreferences("${Constants.KEY_PLUGIN_PERMISSIONS}$pluginId", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // ==================== 镜像站 ====================

    fun getEnabledMirrors(): List<String> {
        val str = getStringSafe(mirrorPrefs, Constants.KEY_ENABLED_MIRRORS, "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",").filter { it.isNotEmpty() }
    }

    fun setEnabledMirrors(mirrors: List<String>) {
        mirrorPrefs.edit().putString(Constants.KEY_ENABLED_MIRRORS, mirrors.joinToString(",")).apply()
    }

    fun isUseCdn(): Boolean {
        return getBooleanSafe(mirrorPrefs, Constants.KEY_USE_CDN, true)
    }

    fun setUseCdn(use: Boolean) {
        mirrorPrefs.edit().putBoolean(Constants.KEY_USE_CDN, use).apply()
    }

    // ==================== 自定义镜像站 ====================

    fun getCustomMirrors(): List<String> {
        val str = getStringSafe(prefs, "custom_mirrors", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split("\n").filter { it.isNotEmpty() }
    }

    fun setCustomMirrors(mirrors: List<String>) {
        prefs.edit().putString("custom_mirrors", mirrors.joinToString("\n")).apply()
    }

    // ==================== 主题 ====================

    fun getCurrentTheme(): String {
        return getStringSafe(prefs, Constants.KEY_CURRENT_THEME, "default") ?: "default"
    }

    fun setCurrentTheme(theme: String) {
        prefs.edit().putString(Constants.KEY_CURRENT_THEME, theme).apply()
    }

    // ==================== 插件多开 ====================

    /** 原生插件是否允许多开（内置单实例限制，开发者选项可开启实验性支持） */
    fun isNativeMultiInstanceEnabled(): Boolean {
        return getBooleanSafe(prefs, Constants.KEY_NATIVE_MULTI_INSTANCE, false)
    }

    fun setNativeMultiInstanceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_NATIVE_MULTI_INSTANCE, enabled).apply()
    }

    /** 共享端口模式下关闭插件时是否保留页面/后端会话（重开直接复用，不重新走环境流水线）。
     * 默认开启：默认启用「关闭时保留会话」（同一 Web 插件只保留一个后台窗口，不默认多开）；
     * 用户显式关闭后才允许多开。 */
    fun isSharedSessionRetainEnabled(): Boolean {
        return getBooleanSafe(prefs, Constants.KEY_SHARED_SESSION_RETAIN, true)
    }

    fun setSharedSessionRetainEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_SHARED_SESSION_RETAIN, enabled).apply()
    }

    /** D-33：是否把宿主共享存储 (/storage/emulated/0) bind 进容器，默认开（保持旧行为） */
    fun isProotBindSharedStorage(): Boolean {
        return getBooleanSafe(prefs, Constants.KEY_PROOT_BIND_SHARED_STORAGE, true)
    }

    fun setProotBindSharedStorage(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_PROOT_BIND_SHARED_STORAGE, enabled).apply()
    }

    /** 开发者选项：忽略签名验证（默认关闭；开启后安装/更新插件跳过签名校验） */
    fun isDevIgnoreSignatureEnabled(): Boolean {
        return getBooleanSafe(prefs, Constants.KEY_DEV_IGNORE_SIGNATURE, false)
    }

    fun setDevIgnoreSignatureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_DEV_IGNORE_SIGNATURE, enabled).apply()
    }

    // ==================== 源管理 ====================

    /** 获取已保存的源列表 JSON 字符串 */
    fun getSourcesJson(): String {
        return getStringSafe(prefs, Constants.KEY_SOURCES_JSON, "") ?: ""
    }

    /** 保存源列表 JSON 字符串 */
    fun setSourcesJson(json: String) {
        prefs.edit().putString(Constants.KEY_SOURCES_JSON, json).apply()
    }

    /** 获取已启用的源 ID 列表 */
    fun getEnabledSourceIds(): List<String> {
        val str = getStringSafe(prefs, Constants.KEY_ENABLED_SOURCE_IDS, "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",").filter { it.isNotEmpty() }
    }

    /** 保存已启用的源 ID 列表 */
    fun setEnabledSourceIds(ids: List<String>) {
        prefs.edit().putString(Constants.KEY_ENABLED_SOURCE_IDS, ids.joinToString(",")).apply()
    }

    /** 获取上次刷新时间 */
    fun getLastRepoRefreshTime(): Long {
        return getLongSafe(prefs, Constants.KEY_LAST_REPO_REFRESH_TIME, 0L)
    }

    /** 保存上次刷新时间 */
    fun setLastRepoRefreshTime(time: Long) {
        prefs.edit().putLong(Constants.KEY_LAST_REPO_REFRESH_TIME, time).apply()
    }

    // ==================== UI配置 ====================

    fun getUiConfig(): String {
        return getStringSafe(prefs, "ui_config", "") ?: ""
    }

    fun setUiConfig(config: String) {
        prefs.edit().putString("ui_config", config).apply()
    }
}