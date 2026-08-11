package com.UIN.Tool.plugin

import com.UIN.Tool.utils.Str
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import com.UIN.Tool.R
import com.UIN.Tool.data.local.FileManager
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.SecurityUtils
import dalvik.system.DexClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.lang.ref.WeakReference

class PluginManager private constructor(
    private val context: Context,
    private val fileManager: FileManager,
    private val preferenceManager: PreferenceManager
) {

    companion object {
        private const val TAG = "PluginManager"
        private var instance: PluginManager? = null
        private var ignoreSignatureWarning = false

        /**
         * WebView 缓存，键为「插件实例键」（pluginId[:instanceId]），
         * 一个插件可在后台同时存活多个实例（多开）。
         */
        private val webViewCache = mutableMapOf<String, WebView?>()
        private val classLoaders = mutableMapOf<String, DexClassLoader>()
        private val pluginInstances = mutableMapOf<String, WeakReference<PluginInterface>>()

        @JvmStatic
        fun getInstance(context: Context): PluginManager {
            if (instance == null) {
                instance = PluginManager(
                    context.applicationContext,
                    FileManager(context.applicationContext),
                    PreferenceManager(context.applicationContext)
                )
            }
            return instance!!
        }

        @JvmStatic
        fun setIgnoreSignatureWarning(ignore: Boolean) {
            ignoreSignatureWarning = ignore
            Logger.i(TAG, Str.get(R.string.signature_verification_ignored_ignor, ignore))
        }

        @JvmStatic
        fun isIgnoreSignatureWarning(): Boolean = ignoreSignatureWarning

        @JvmStatic
        fun putPluginWebView(instanceKey: String, webView: WebView?) {
            webViewCache[instanceKey] = webView
        }

        @JvmStatic
        fun getPluginWebView(instanceKey: String): WebView? {
            return webViewCache[instanceKey]
        }

        @JvmStatic
        fun removePluginWebView(instanceKey: String) {
            webViewCache.remove(instanceKey)
        }

        /**
         * 共享端口模式的「保留会话」WebView 缓存，键为插件 ID。
         * 关闭插件（最后一个实例）时若开启保留会话，WebView 不销毁而是移入此缓存；
         * 重开同一插件时直接取出复用，页面与会话状态得以保留。
         */
        private val retainedWebViews = mutableMapOf<String, WebView?>()

        @JvmStatic
        fun retainSharedWebView(pluginId: String, webView: WebView?) {
            retainedWebViews[pluginId] = webView
        }

        @JvmStatic
        fun takeRetainedSharedWebView(pluginId: String): WebView? {
            return retainedWebViews.remove(pluginId)
        }

        @JvmStatic
        fun hasRetainedSharedWebView(pluginId: String): Boolean =
            retainedWebViews.containsKey(pluginId) && retainedWebViews[pluginId] != null

        @JvmStatic
        fun clearRetainedWebViews() {
            retainedWebViews.values.forEach { it?.destroy() }
            retainedWebViews.clear()
        }

        /** 移除某插件（pluginId）关联的全部实例 WebView，用于卸载/清理 */
        @JvmStatic
        fun removePluginWebViewsOf(pluginId: String) {
            webViewCache.keys
                .filter { it == pluginId || it.startsWith("$pluginId:") }
                .forEach { webViewCache.remove(it) }
            retainedWebViews.remove(pluginId)?.destroy()
        }

        @JvmStatic
        fun clearWebViewCache() {
            webViewCache.values.forEach { it?.destroy() }
            webViewCache.clear()
        }

        /** 从实例键中解析插件 ID（无冒号时本身即插件 ID） */
        @JvmStatic
        fun pluginIdOfInstance(instanceKey: String): String =
            instanceKey.substringBefore(":")
    }

    // ==================== 状态流 ====================

    /** 原生插件当前存活宿主 Activity 的实例，用于默认单实例去重（pluginId -> instanceKey） */
    private val activeNativeInstances = mutableMapOf<String, String>()

    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins.asStateFlow()

    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshPlugins()
    }

    // ==================== 插件列表管理 ====================

    fun refreshPlugins() {
        Logger.enter(TAG, "refreshPlugins")
        _isLoading.value = true
        _error.value = null

        try {
            val pluginList = mutableListOf<PluginInfo>()
            val pluginDir = File(Constants.PLUGIN_DIR)

            if (pluginDir.exists() && pluginDir.isDirectory) {
                pluginDir.listFiles()?.forEach { dir ->
                    if (dir.isDirectory && !dir.name.startsWith(".")) {
                        val jsonFile = File(dir, Constants.PLUGIN_CONFIG_FILE)
                        if (jsonFile.exists()) {
                            val json = try { jsonFile.readText() } catch (e: Exception) { null }
                            json?.let {
                                val info = PluginInfo.fromJson(it)
                                info?.let { pluginInfo ->
                                    if (pluginInfo.pluginId.isEmpty()) {
                                        pluginInfo.pluginId = dir.name
                                    }
                                    pluginList.add(pluginInfo)
                                }
                            }
                        }
                    }
                }
            }

            _plugins.value = pluginList
            _installedPluginIds.value = pluginList.map { it.pluginId }.toSet()
            refreshAllPluginShortcuts()

            Logger.i(TAG, Str.get(R.string.loaded_pluginlist_size_plugin_s, pluginList.size))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_refresh_plugin_list), e)
            _error.value = e.message
        } finally {
            _isLoading.value = false
            Logger.exit(TAG, "refreshPlugins", System.currentTimeMillis())
        }
    }

    fun getPluginInfo(pluginId: String): PluginInfo? {
        return _plugins.value.find { it.pluginId == pluginId }
    }

    fun getInstalledPluginIds(): List<String> = _plugins.value.map { it.pluginId }
    fun getPluginCount(): Int = _plugins.value.size
    fun isPluginInstalled(pluginId: String): Boolean = _installedPluginIds.value.contains(pluginId)

    // ==================== 插件安装 ====================

    private suspend fun installPluginInternal(file: File, fileName: String): PluginInfo? {
        val tempDir = File(Constants.TEMP_DIR, "plugin_install_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        return try {
            if (!fileManager.unzipFile(file, tempDir)) {
                Logger.e(TAG, Str.get(R.string.extraction_failed))
                return null
            }

            val jsonFile = File(tempDir, Constants.PLUGIN_CONFIG_FILE)
            if (!jsonFile.exists()) {
                Logger.e(TAG, Str.get(R.string.missing_plugin_json))
                return null
            }

            val jsonContent = jsonFile.readText()
            val info = PluginInfo.fromJson(jsonContent)
            if (info == null || info.pluginId.isEmpty()) {
                Logger.e(TAG, Str.get(R.string.invalid_plugin_json))
                return null
            }

            if (info.minHostVersion > android.os.Build.VERSION.SDK_INT) {
                Logger.e(TAG, Str.get(R.string.android_version_too_old_info_minhost, info.minHostVersion, android.os.Build.VERSION.SDK_INT))
                return null
            }

            if (info.apiLevel > android.os.Build.VERSION.SDK_INT) {
                Logger.e(TAG, Str.get(R.string.android_api_level_too_low_info_apil, info.apiLevel, android.os.Build.VERSION.SDK_INT))
                return null
            }

            if (info.uiType == "native") {
                val dexFile = File(tempDir, Constants.PLUGIN_DEX_FILE)
                if (!dexFile.exists()) {
                    Logger.e(TAG, Str.get(R.string.native_plugin_missing_plugin_dex))
                    return null
                }
            }

            val pluginDir = File(Constants.PLUGIN_DIR, info.pluginId)

            var dataBackup: File? = null
            if (pluginDir.exists()) {
                val userDataDir = File(pluginDir, "data")
                if (userDataDir.exists()) {
                    dataBackup = File(Constants.TEMP_DIR, "data_backup_${info.pluginId}_${System.currentTimeMillis()}")
                    dataBackup.mkdirs()
                    fileManager.copyDirectory(userDataDir, dataBackup)
                    Logger.d(TAG, Str.get(R.string.user_data_backed_up_userdatadir_abso, userDataDir.absolutePath))
                }
                fileManager.deleteRecursively(pluginDir)
                Logger.i(TAG, Str.get(R.string.removing_old_plugin_info_pluginid, info.pluginId))
            }

            pluginDir.mkdirs()
            fileManager.copyDirectory(tempDir, pluginDir)

            if (dataBackup != null && dataBackup.exists()) {
                val userDataDir = File(pluginDir, "data")
                fileManager.copyDirectory(dataBackup, userDataDir)
                fileManager.deleteRecursively(dataBackup)
                Logger.success(TAG, Str.get(R.string.user_data_restored))
            }

            migratePluginDataVersion(info)

            SecurityUtils.savePluginSignature(info.pluginId, file, preferenceManager)

            Logger.success(TAG, Str.get(R.string.installed_info_name_info_pluginid, info.name, info.pluginId))
            info

        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.install_failed_e_message, e.message), e)
            null
        } finally {
            fileManager.deleteRecursively(tempDir)
        }
    }

    private fun migratePluginDataVersion(info: PluginInfo) {
        try {
            val pluginDir = File(Constants.PLUGIN_DIR, info.pluginId)
            val pluginContext = PluginContext(context, pluginDir.absolutePath)

            val currentVersion = pluginContext.getDataVersion()
            if (currentVersion == 0) {
                pluginContext.setDataVersion(info.version)
                Logger.d(TAG, Str.get(R.string.first_install_data_version_info_vers, info.version))
            } else if (currentVersion < info.version) {
                Logger.i(TAG, Str.get(R.string.data_migration_currentversion_info_v, currentVersion, info.version))
                pluginContext.setDataVersion(info.version)
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.data_version_check_failed), e)
        }
    }

    suspend fun installPlugin(file: File, fileName: String): PluginInfo? {
        Logger.enter(TAG, "installPlugin")
        Logger.param(TAG, Str.get(R.string.file), file.absolutePath)

        return try {
            if (!SecurityUtils.verifyFileSignature(file, preferenceManager)) {
                Logger.e(TAG, Str.get(R.string.plugin_signature_verification_failed))
                _error.value = Str.get(R.string.plugin_signature_verification_failed_2)
                return null
            }

            val info = installPluginInternal(file, fileName)

            if (info != null) {
                checkPluginDependencies(info)
                refreshPlugins()
                createPluginDynamicShortcut(info)
                notifyWidgetsRefresh()
                Logger.success(TAG, Str.get(R.string.plugin_installed_info_name, info.name))
            }

            info
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.plugin_install_error), e)
            _error.value = e.message
            null
        } finally {
            Logger.exit(TAG, "installPlugin", System.currentTimeMillis())
        }
    }

    private fun checkPluginDependencies(info: PluginInfo) {
        if (info.dependencies.isNotEmpty()) {
            Logger.i(TAG, Str.get(R.string.plugin_info_name_dependencies_info_d, info.name, info.dependencies.joinToString()))
            val installedIds = _installedPluginIds.value
            val missingDeps = info.dependencies.filter { !installedIds.contains(it) }
            if (missingDeps.isNotEmpty()) {
                Logger.w(TAG, Str.get(R.string.missing_dependencies_missingdeps_joi, missingDeps.joinToString()))
            }
        }
    }

    suspend fun installPluginsBatch(files: List<File>): List<PluginInfo> {
        val installed = mutableListOf<PluginInfo>()
        files.forEach { file ->
            installPlugin(file, file.name)?.let { installed.add(it) }
        }
        return installed
    }

    suspend fun installPluginFromFile(filePath: String): PluginInfo? {
        val file = File(filePath)
        if (!file.exists()) {
            Logger.e(TAG, Str.get(R.string.file_not_found_filepath, filePath))
            return null
        }
        return installPlugin(file, file.name)
    }

    // ==================== 插件卸载 ====================

    suspend fun uninstallPlugin(pluginId: String): Boolean {
        Logger.action(TAG, Str.get(R.string.uninstall_plugin), pluginId)

        if (isBackendRunning(pluginId)) {
            stopBackend(pluginId)
        }

        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val optDir = File(context.codeCacheDir, "opt/$pluginId")

            try {
                val pluginContext = PluginContext(context, pluginDir.absolutePath)
                pluginContext.deleteAllPluginData()
                Logger.d(TAG, Str.get(R.string.plugin_data_cleared_pluginid, pluginId))
            } catch (e: Exception) {
                Logger.w(TAG, Str.get(R.string.failed_to_clear_data_e_message, e.message))
            }

            val result = fileManager.deleteRecursively(pluginDir)
            fileManager.deleteRecursively(optDir)

            if (result) {
                classLoaders.remove(pluginId)
                pluginInstances.remove(pluginId)
                removePluginWebViewsOf(pluginId)
                removePluginDynamicShortcut(pluginId)
                preferenceManager.removePluginSignature(pluginId)
                refreshPlugins()
                notifyWidgetsRefresh()
                Logger.success(TAG, Str.get(R.string.plugin_uninstalled_pluginid, pluginId))
            }
            result
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.plugin_uninstall_failed), e)
            _error.value = e.message
            false
        }
    }

    suspend fun uninstallPluginsBatch(pluginIds: List<String>): List<String> {
        val successList = mutableListOf<String>()
        pluginIds.forEach { pluginId ->
            if (uninstallPlugin(pluginId)) {
                successList.add(pluginId)
            }
        }
        return successList
    }

    // ==================== 插件更新 ====================

    suspend fun updatePlugin(pluginId: String, newPluginFile: File): Boolean {
        if (!uninstallPlugin(pluginId)) {
            Logger.e(TAG, Str.get(R.string.failed_to_uninstall_old_version))
            return false
        }
        val info = installPlugin(newPluginFile, newPluginFile.name)
        return info != null
    }

    fun hasPluginUpdate(pluginId: String, newVersion: Int): Boolean {
        val info = getPluginInfo(pluginId)
        return info != null && newVersion > info.version
    }

    // ==================== 插件导出 ====================

    suspend fun exportPlugin(pluginId: String, destFile: File): Boolean {
        val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
        if (!pluginDir.exists()) {
            Logger.e(TAG, Str.get(R.string.plugin_dir_not_found))
            return false
        }
        return fileManager.zipDirectory(pluginDir, destFile)
    }

    suspend fun exportPluginsBatch(pluginIds: List<String>, destDir: File): List<String> {
        val successList = mutableListOf<String>()
        destDir.mkdirs()
        pluginIds.forEach { pluginId ->
            val destFile = File(destDir, "$pluginId.tpk")
            if (exportPlugin(pluginId, destFile)) {
                successList.add(pluginId)
            }
        }
        return successList
    }

    // ==================== 插件运行 ====================

    fun openPlugin(pluginId: String, context: Context) {
        Logger.action(TAG, Str.get(R.string.open_plugin), pluginId)
        val info = getPluginInfo(pluginId)
        if (info == null) {
            Toast.makeText(context, Str.get(R.string.plugin_not_found_pluginid, pluginId), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, PluginHostActivity::class.java)
        intent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ==================== 插件视图加载（原生插件） ====================

    fun getPluginViewSync(pluginId: String, context: Context, container: ViewGroup?, forceNewInstance: Boolean = false): View? {
        Logger.enter(TAG, "getPluginViewSync")
        Logger.param(TAG, "pluginId", pluginId)

        val info = getPluginInfo(pluginId) ?: return null

        if (info.uiType == "web") {
            Logger.d(TAG, Str.get(R.string.web_plugins_handled_by_pluginhostact))
            return null
        }

        try {
            var classLoader = classLoaders[pluginId]

            if (classLoader == null) {
                val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
                val dexFile = File(pluginDir, Constants.PLUGIN_DEX_FILE)
                if (!dexFile.exists()) {
                    Logger.e(TAG, Str.get(R.string.dex_file_not_found_dexfile_absolutep, dexFile.absolutePath))
                    return null
                }

                val optDir = File(context.codeCacheDir, "opt/$pluginId")
                optDir.mkdirs()

                classLoader = DexClassLoader(
                    dexFile.absolutePath,
                    optDir.absolutePath,
                    null,
                    context.classLoader
                )
                classLoaders[pluginId] = classLoader
                Logger.success(TAG, Str.get(R.string.dexclassloader_created))
            }

            var plugin = pluginInstances[pluginId]?.get()

            if (forceNewInstance) {
                // 开发者选项开启的「原生插件多开」：每次新建 PluginInterface 实例。
                // classLoader 保持共享，实例与 View 相互独立。
                val clazz = classLoader.loadClass(info.mainClass)
                plugin = clazz.newInstance() as PluginInterface
            } else if (plugin == null) {
                val clazz = classLoader.loadClass(info.mainClass)
                plugin = clazz.newInstance() as PluginInterface
                pluginInstances[pluginId] = WeakReference(plugin)
                Logger.success(TAG, Str.get(R.string.plugin_instantiated))
            }

            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val pluginContext = PluginContext(context, pluginDir.absolutePath)

            val view = plugin.onCreateView(pluginContext, container, null)

            if (view != null) {
                Logger.success(TAG, Str.get(R.string.plugin_view_created))
            } else {
                Logger.e(TAG, Str.get(R.string.plugin_view_creation_failed))
            }

            return view

        } catch (e: ClassNotFoundException) {
            Logger.e(TAG, Str.get(R.string.main_class_not_found_info_mainclass, info.mainClass), e)
            return null
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.native_plugin_load_error_2), e)
            return null
        } finally {
            Logger.exit(TAG, "getPluginViewSync", System.currentTimeMillis())
        }
    }

    fun getPluginInstance(pluginId: String): PluginInterface? {
        return pluginInstances[pluginId]?.get()
    }

    fun getPluginDirFile(pluginId: String): File? {
        return File(Constants.PLUGIN_DIR, pluginId).takeIf { it.exists() }
    }

    // ==================== 获取插件数据存储 ====================

    fun getPluginDataContext(pluginId: String): PluginContext? {
        if (!isPluginInstalled(pluginId)) {
            Logger.w(TAG, Str.get(R.string.plugin_not_installed_pluginid, pluginId))
            return null
        }
        val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
        return PluginContext(context, pluginDir.absolutePath)
    }

    fun getPluginDataVersion(pluginId: String): Int {
        val pctx = getPluginDataContext(pluginId) ?: return 0
        return pctx.getDataVersion()
    }

    fun getPluginStorageStats(pluginId: String): PluginContext.StorageStats? {
        val pctx = getPluginDataContext(pluginId) ?: return null
        return pctx.getStorageStats()
    }

    // ==================== 搜索和分类 ====================

    fun searchPlugins(keyword: String): List<PluginInfo> {
        if (keyword.isEmpty()) return _plugins.value
        val lower = keyword.lowercase()
        return _plugins.value.filter {
            it.name.lowercase().contains(lower) ||
            it.pluginId.lowercase().contains(lower) ||
            it.description.lowercase().contains(lower) ||
            it.author.lowercase().contains(lower) ||
            it.category.lowercase().contains(lower)
        }
    }

    fun getPluginsByCategory(category: String): List<PluginInfo> {
        return if (category == Str.get(R.string.all)) _plugins.value else _plugins.value.filter { it.category == category }
    }

    fun getAllCategories(): List<String> {
        val categories = mutableSetOf(Str.get(R.string.all), Str.get(R.string.uncategorized))
        _plugins.value.forEach { categories.add(it.category.ifEmpty { Str.get(R.string.uncategorized) }) }
        return categories.toList()
    }

    fun updatePluginCategory(pluginId: String, newCategory: String): Boolean {
        val plugin = getPluginInfo(pluginId) ?: return false
        val updated = plugin.copy(category = newCategory)
        val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
        val jsonFile = File(pluginDir, Constants.PLUGIN_CONFIG_FILE)
        return try {
            jsonFile.writeText(updated.toJson())
            refreshPlugins()
            true
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_update_category), e)
            false
        }
    }

    // ==================== 动态快捷方式 ====================

    @Suppress("DEPRECATION")
    private fun createPluginDynamicShortcut(plugin: PluginInfo): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
                val dynamicShortcuts = shortcutManager.dynamicShortcuts
                if (dynamicShortcuts.size >= shortcutManager.maxShortcutCountPerActivity) {
                    dynamicShortcuts.firstOrNull { it.id.startsWith("plugin_") }?.let {
                        shortcutManager.removeDynamicShortcuts(listOf(it.id))
                    }
                }

                val intent = Intent(context, PluginHostActivity::class.java).apply {
                    putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId)
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                val icon = getPluginIconForShortcut(plugin)
                if (icon != null) {
                    val shortcut = ShortcutInfo.Builder(context, "plugin_${plugin.pluginId}")
                        .setShortLabel(plugin.name)
                        .setLongLabel(plugin.description.ifEmpty { plugin.name })
                        .setIcon(icon)
                        .setIntent(intent)
                        .build()
                    shortcutManager.addDynamicShortcuts(listOf(shortcut))
                    Logger.success(TAG, Str.get(R.string.creating_dynamic_shortcut_plugin_nam, plugin.name))
                    return true
                }
            } else {
                createShortcutForOldVersions(plugin)
                return true
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_create_shortcut), e)
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun createShortcutForOldVersions(plugin: PluginInfo) {
        try {
            val shortcutIntent = Intent(context, PluginHostActivity::class.java).apply {
                putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId)
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val addIntent = Intent().apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, plugin.name)
                action = "com.android.launcher.action.INSTALL_SHORTCUT"
            }

            val bitmap = getPluginIconBitmap(plugin)
            if (bitmap != null) {
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
            } else {
                addIntent.putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_extension)
                )
            }
            context.sendBroadcast(addIntent)
            Logger.success(TAG, Str.get(R.string.creating_legacy_shortcut_plugin_name, plugin.name))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_create_legacy_shortcut), e)
        }
    }

    private fun removePluginDynamicShortcut(pluginId: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                shortcutManager?.removeDynamicShortcuts(listOf("plugin_$pluginId"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_remove_shortcut), e)
        }
    }

    private fun refreshAllPluginShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
                val pluginShortcutIds = shortcutManager.dynamicShortcuts
                    .filter { it.id.startsWith("plugin_") }
                    .map { it.id }
                if (pluginShortcutIds.isNotEmpty()) {
                    shortcutManager.removeDynamicShortcuts(pluginShortcutIds)
                }
                val max = shortcutManager.maxShortcutCountPerActivity
                var added = 0
                for (plugin in _plugins.value) {
                    if (added >= max) break
                    if (createPluginDynamicShortcut(plugin)) added++
                    else break
                }
                Logger.i(TAG, Str.get(R.string.dynamic_shortcuts_refreshed))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_refresh_shortcuts), e)
            }
        }
    }

    private fun getPluginIconForShortcut(plugin: PluginInfo): Icon? {
        val bitmap = getPluginIconBitmap(plugin)
        return if (bitmap != null) {
            val scaled = Bitmap.createScaledBitmap(bitmap, 72, 72, true)
            Icon.createWithBitmap(scaled)
        } else {
            Icon.createWithResource(context, R.drawable.ic_extension)
        }
    }

    private fun getPluginIconBitmap(plugin: PluginInfo): Bitmap? {
        try {
            val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
            if (pluginDir.exists()) {
                val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                val iconFile = File(pluginDir, iconPath)
                if (iconFile.exists()) {
                    return BitmapFactory.decodeFile(iconFile.absolutePath)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_get_plugin_icon), e)
        }
        return null
    }

    // ==================== 小部件刷新 ====================

    private fun notifyWidgetsRefresh() {
        try {
            val intent = Intent("com.UIN.Tool.REFRESH_WIDGET").setPackage(context.packageName)
            context.sendBroadcast(intent)
            val intent1x1 = Intent("com.UIN.Tool.REFRESH_WIDGET_1x1").setPackage(context.packageName)
            context.sendBroadcast(intent1x1)
            Logger.i(TAG, Str.get(R.string.widget_refresh_notification_sent))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_notify_widget_refresh), e)
        }
    }

    fun refreshAndNotifyWidgets() {
        refreshPlugins()
        notifyWidgetsRefresh()
    }

    // ==================== 生命周期管理 ====================

    /**
     * 生命周期回调，均按「实例键」隔离（多开支持）。
     * 原生插件实例仍以插件 ID 共享（默认单实例；开发者选项开启后由宿主自行创建新实例）。
     */
    fun onPluginResume(instanceKey: String) {
        val pluginId = pluginIdOfInstance(instanceKey)
        getPluginInstance(pluginId)?.onResume()
        getPluginWebView(instanceKey)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('resume'));", null)
            it.onResume()
            it.resumeTimers()
        }
    }

    fun onPluginPause(instanceKey: String) {
        val pluginId = pluginIdOfInstance(instanceKey)
        getPluginInstance(pluginId)?.onPause()
        getPluginWebView(instanceKey)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('pause'));", null)
            it.onPause()
            it.pauseTimers()
        }
    }

    fun onPluginDestroy(instanceKey: String) {
        val pluginId = pluginIdOfInstance(instanceKey)
        getPluginInstance(pluginId)?.onDestroy()
        getPluginWebView(instanceKey)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('destroy'));", null)
            it.loadUrl("about:blank")
            it.clearHistory()
            it.clearCache(true)
            it.destroy()
        }
        removePluginWebView(instanceKey)
        pluginInstances.remove(pluginId)
        classLoaders.remove(pluginId)
    }

    fun onPluginBackPressed(instanceKey: String): Boolean {
        val pluginId = pluginIdOfInstance(instanceKey)
        val plugin = getPluginInstance(pluginId)
        if (plugin?.onBackPressed() == true) return true
        getPluginWebView(instanceKey)?.let {
            if (it.canGoBack()) {
                it.goBack()
                return true
            }
        }
        return false
    }

    // ==================== 插件权限管理（代理） ====================

    fun getPluginDeclaredPermissions(pluginId: String): List<String> {
        return PluginPermissionManager.getPluginDeclaredPermissions(context, pluginId)
    }

    fun getPluginPermissionStatus(pluginId: String): Map<String, Boolean> {
        return PluginPermissionManager.getPluginPermissionStatus(context, pluginId)
    }

    fun arePluginPermissionsGranted(pluginId: String): Boolean {
        return PluginPermissionManager.areAllPermissionsGranted(context, pluginId)
    }

    fun getPluginMissingPermissions(pluginId: String): List<String> {
        return PluginPermissionManager.getMissingPermissions(context, pluginId)
    }

    fun getPluginPermissionSummary(pluginId: String): PluginPermissionManager.PermissionStatusSummary {
        return PluginPermissionManager.getPermissionStatusSummary(context, pluginId)
    }

    fun getPermissionState(pluginId: String): Int {
        return PluginPermissionManager.getPermissionState(context, pluginId)
    }

    fun setPermissionState(pluginId: String, state: Int) {
        PluginPermissionManager.setPermissionState(context, pluginId, state)
    }

    fun shouldShowPermissionDialog(pluginId: String): Boolean {
        return PluginPermissionManager.shouldShowPermissionDialog(context, pluginId)
    }

    fun requestPluginPermissions(pluginId: String, onResult: (Boolean) -> Unit) {
        val activity = context as? android.app.Activity
        if (activity == null) {
            Logger.e(TAG, Str.get(R.string.context_is_not_an_activity_cannot_re))
            onResult(false)
            return
        }
        val permissions = getPluginMissingPermissions(pluginId)
        if (permissions.isEmpty()) {
            onResult(true)
            return
        }
        val requestCode = 2000 + pluginId.hashCode() % 1000
        PluginPermissionManager.requestPermissions(
            activity,
            permissions.toTypedArray(),
            requestCode
        )
        onResult(true)
    }

    fun requestPluginPermissionsByGroups(
        pluginId: String,
        onProgress: ((String, Int, Int) -> Unit)? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val activity = context as? android.app.Activity
        if (activity == null) {
            Logger.e(TAG, Str.get(R.string.context_is_not_an_activity_cannot_re))
            onComplete(false)
            return
        }
        
        val permissions = getPluginMissingPermissions(pluginId)
        if (permissions.isEmpty()) {
            onComplete(true)
            return
        }
        
        val normal = permissions.filter { !PluginPermissionManager.isSpecialPermission(it) }
        val special = permissions.filter { PluginPermissionManager.isSpecialPermission(it) }
        
        if (normal.isNotEmpty()) {
            onProgress?.invoke(Str.get(R.string.normal_permissions), 0, 2)
            val requestCode = 3000 + pluginId.hashCode() % 1000
            PluginPermissionManager.requestPermissions(
                activity,
                normal.toTypedArray(),
                requestCode
            )
            onProgress?.invoke(Str.get(R.string.normal_permissions_granted), 1, 2)
        }
        
        if (special.isNotEmpty()) {
            onProgress?.invoke(Str.get(R.string.special_permissions), 1, 2)
            PluginPermissionManager.openAppSettings(activity)
            onProgress?.invoke(Str.get(R.string.please_enable_special_permissions_ma), 2, 2)
            onComplete(false)
        } else {
            onComplete(true)
        }
    }

    fun showPermissionGuidance(pluginId: String, onReRequest: () -> Unit, onOpenSettings: () -> Unit) {
        onReRequest()
    }

    fun savePluginPermissionConfig(pluginId: String, permission: String, granted: Boolean) {
        val prefs = context.getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$pluginId", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("perm_$permission", granted).apply()
        Logger.d(TAG, Str.get(R.string.saving_permission_config_pluginid_pe, pluginId, permission, granted))
    }

    fun clearPluginPermissionConfigs(pluginId: String) {
        val prefs = context.getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$pluginId", Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith("perm_") }
        keys.forEach { prefs.edit().remove(it).apply() }
        Logger.d(TAG, Str.get(R.string.clearing_permission_config_pluginid, pluginId))
    }

    // ==================== Termux 后端管理 ====================

    fun hasBackend(pluginId: String): Boolean {
        val info = getPluginInfo(pluginId)
        val result = info?.hasBackend() == true
        Logger.d(TAG, "hasBackend($pluginId) = $result")
        return result
    }

    fun getBackendType(pluginId: String): String {
        return getPluginInfo(pluginId)?.backend ?: ""
    }

    fun isBackendRunning(pluginId: String): Boolean {
        return PluginBackendManager.isRunning(pluginId)
    }

    fun getBackendPort(pluginId: String): Int {
        return PluginBackendManager.getPort(pluginId)
    }

    // ==================== ✅ startBackend 带详细日志 ====================

    fun startBackend(pluginId: String, callback: (Boolean, Int, String?) -> Unit) {
        Logger.d(TAG, "========================================")
        Logger.d(TAG, Str.get(R.string.pluginmanager_startbackend_called))
        Logger.d(TAG, "📦 pluginId: $pluginId")
        Logger.d(TAG, Str.get(R.string.time_system_currenttimemillis, System.currentTimeMillis()))
        
        val info = getPluginInfo(pluginId)
        Logger.d(TAG, "📌 info == null: ${info == null}")
        
        if (info == null) {
            Logger.e(TAG, Str.get(R.string.plugin_not_found_pluginid_2, pluginId))
            callback(false, 0, Str.get(R.string.plugin_does_not_exist))
            return
        }
        
        Logger.d(TAG, "📌 info.name: ${info.name}")
        Logger.d(TAG, "📌 info.hasBackend(): ${info.hasBackend()}")
        Logger.d(TAG, "📌 info.backend: '${info.backend}'")
        Logger.d(TAG, "📌 info.backendEntry: '${info.backendEntry}'")
        Logger.d(TAG, "📌 info.backendPort: ${info.backendPort}")
        Logger.d(TAG, "📌 info.backendAutoStart: ${info.backendAutoStart}")
        Logger.d(TAG, "========================================")
        
        if (!info.hasBackend()) {
            Logger.w(TAG, Str.get(R.string.plugin_has_no_backend_configured))
            callback(false, 0, Str.get(R.string.plugin_has_no_backend_configured_2))
            return
        }

        Logger.i(TAG, Str.get(R.string.conditions_met_calling_pluginbackend))
        
        Thread {
            try {
                Logger.d(TAG, Str.get(R.string.calling_pluginbackendmanager_startba))
                val startTime = System.currentTimeMillis()
                val success = PluginBackendManager.startBackend(context, info)
                val elapsed = System.currentTimeMillis() - startTime
                val port = if (success) PluginBackendManager.getPort(pluginId) else 0
                val error = if (success) null else Str.get(R.string.start_failed)
                
                Logger.d(TAG, Str.get(R.string.start_result_success_success_port_po, success, port, error, elapsed))
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(success, port, error)
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.backend_start_error_e_message, e.message), e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(false, 0, e.message)
                }
            }
        }.start()
    }

    /**
     * 按后端实例键启动后端：
     * key 由调用方（宿主 Activity）根据共享/独立端口模式决定——
     * 共享端口模式传插件 ID（多实例共享同一端口/进程）；
     * 独立端口模式传实例键（各实例独立端口/进程）。
     */
    fun startBackendFor(pluginId: String, key: String, callback: (Boolean, Int, String?) -> Unit) {
        Logger.d(TAG, "========================================")
        Logger.d(TAG, Str.get(R.string.pluginmanager_startbackend_called))
        Logger.d(TAG, "📦 pluginId: $pluginId")
        Logger.d(TAG, "🔑 backendKey: $key")

        val info = getPluginInfo(pluginId)
        Logger.d(TAG, "📌 info == null: ${info == null}")

        if (info == null) {
            Logger.e(TAG, Str.get(R.string.plugin_not_found_pluginid_2, pluginId))
            callback(false, 0, Str.get(R.string.plugin_does_not_exist))
            return
        }

        if (!info.hasBackend()) {
            Logger.w(TAG, Str.get(R.string.plugin_has_no_backend_configured))
            callback(false, 0, Str.get(R.string.plugin_has_no_backend_configured_2))
            return
        }

        Logger.i(TAG, Str.get(R.string.conditions_met_calling_pluginbackend))

        Thread {
            try {
                Logger.d(TAG, Str.get(R.string.calling_pluginbackendmanager_startba))
                val startTime = System.currentTimeMillis()
                val success = PluginBackendManager.startBackendInstance(context, info, key)
                val elapsed = System.currentTimeMillis() - startTime
                val port = if (success) PluginBackendManager.getPort(key) else 0
                val error = if (success) null else Str.get(R.string.start_failed)

                Logger.d(TAG, Str.get(R.string.start_result_success_success_port_po, success, port, error, elapsed))

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(success, port, error)
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.backend_start_error_e_message, e.message), e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(false, 0, e.message)
                }
            }
        }.start()
    }

    fun stopBackend(pluginId: String) {
        PluginBackendManager.stopBackend(pluginId)
    }

    /** 共享端口模式：插件实例持有共享后端（每打开一个带后端的插件实例调用一次） */
    fun acquireBackend(pluginId: String) {
        PluginBackendManager.acquireHold(pluginId)
    }

    /** 共享端口模式：插件实例释放共享后端（最后一个实例关闭时停止后端） */
    fun releaseBackend(pluginId: String) {
        PluginBackendManager.releaseHold(pluginId)
    }

    fun stopAllBackends() {
        PluginBackendManager.stopAllBackends()
    }

    fun callBackendApi(
        pluginId: String,
        path: String,
        method: String = "GET",
        body: String? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        PluginBackendManager.callApi(pluginId, path, method, body, callback)
    }

    // ==================== 插件说明通知管理 ====================

    fun isPluginNoticeIgnored(pluginId: String): Boolean {
        val prefs = context.getSharedPreferences("plugin_notices", Context.MODE_PRIVATE)
        return prefs.getBoolean("notice_$pluginId", false)
    }

    fun setPluginNoticeIgnored(pluginId: String, ignored: Boolean) {
        val prefs = context.getSharedPreferences("plugin_notices", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notice_$pluginId", ignored).apply()
    }

    fun clearAllPluginNoticeIgnored() {
        val prefs = context.getSharedPreferences("plugin_notices", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // ==================== 清理 ====================

    fun clearAllPlugins() {
        _plugins.value.forEach { plugin ->
            onPluginDestroy(plugin.pluginId)
            stopBackend(plugin.pluginId)
        }
        _plugins.value = emptyList()
        _installedPluginIds.value = emptySet()
        Logger.i(TAG, Str.get(R.string.all_plugins_cleaned))
    }

    fun refreshWorkFolder() {
        Logger.i(TAG, Str.get(R.string.refreshing_work_dir_preferencemanage, preferenceManager.getWorkFolder()))
        refreshPlugins()
    }

    // ==================== 多开设置 ====================

    fun isNativeMultiInstanceEnabled(): Boolean = preferenceManager.isNativeMultiInstanceEnabled()

    fun setNativeMultiInstanceEnabled(enabled: Boolean) {
        preferenceManager.setNativeMultiInstanceEnabled(enabled)
    }

    fun isBackendMultiModeIndependent(): Boolean = preferenceManager.isBackendMultiModeIndependent()

    fun getBackendMultiMode(): String = preferenceManager.getBackendMultiMode()

    fun setBackendMultiMode(mode: String) {
        preferenceManager.setBackendMultiMode(mode)
    }

    fun isSharedSessionRetainEnabled(): Boolean = preferenceManager.isSharedSessionRetainEnabled()

    fun setSharedSessionRetainEnabled(enabled: Boolean) {
        preferenceManager.setSharedSessionRetainEnabled(enabled)
    }

    // ==================== 原生插件活动实例追踪 ====================

    /**
     * 记录某个原生插件正在宿主 Activity 中运行（默认单实例时由此判断是否“已在运行”，
     * 从而让第二次打开直接结束，避免重复挂载同一个 View）。
     */
    fun onNativePluginStarted(pluginId: String, instanceKey: String) {
        activeNativeInstances[pluginId] = instanceKey
    }

    fun onNativePluginStopped(instanceKey: String) {
        activeNativeInstances.entries.removeAll { it.value == instanceKey }
    }

    /** 该原生插件当前是否已有存活实例（默认单实例时用于去重） */
    fun isNativePluginActive(pluginId: String): Boolean {
        return activeNativeInstances.containsKey(pluginId)
    }
}