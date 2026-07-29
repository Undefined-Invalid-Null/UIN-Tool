package com.UIN.Tool.plugin

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
import com.UIN.Tool.utils.Constants
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
            Logger.i(TAG, "忽略签名验证: $ignore")
        }

        @JvmStatic
        fun isIgnoreSignatureWarning(): Boolean = ignoreSignatureWarning

        @JvmStatic
        fun putPluginWebView(pluginId: String, webView: WebView?) {
            webViewCache[pluginId] = webView
        }

        @JvmStatic
        fun getPluginWebView(pluginId: String): WebView? {
            return webViewCache[pluginId]
        }

        @JvmStatic
        fun removePluginWebView(pluginId: String) {
            webViewCache.remove(pluginId)
        }

        @JvmStatic
        fun clearWebViewCache() {
            webViewCache.values.forEach { it?.destroy() }
            webViewCache.clear()
        }
    }

    // ==================== 状态流 ====================

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

            Logger.i(TAG, "已加载 ${pluginList.size} 个插件")
        } catch (e: Exception) {
            Logger.e(TAG, "刷新插件列表失败", e)
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
                Logger.e(TAG, "解压失败")
                return null
            }

            val jsonFile = File(tempDir, Constants.PLUGIN_CONFIG_FILE)
            if (!jsonFile.exists()) {
                Logger.e(TAG, "缺少 plugin.json")
                return null
            }

            val jsonContent = jsonFile.readText()
            val info = PluginInfo.fromJson(jsonContent)
            if (info == null || info.pluginId.isEmpty()) {
                Logger.e(TAG, "plugin.json 格式错误")
                return null
            }

            if (info.minHostVersion > android.os.Build.VERSION.SDK_INT) {
                Logger.e(TAG, "Android 版本过低: ${info.minHostVersion} > ${android.os.Build.VERSION.SDK_INT}")
                return null
            }

            if (info.uiType != "web") {
                val dexFile = File(tempDir, Constants.PLUGIN_DEX_FILE)
                if (!dexFile.exists()) {
                    Logger.e(TAG, "原生插件缺少 plugin.dex")
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
                    Logger.d(TAG, "已备份用户数据: ${userDataDir.absolutePath}")
                }
                fileManager.deleteRecursively(pluginDir)
                Logger.i(TAG, "删除旧插件: ${info.pluginId}")
            }

            pluginDir.mkdirs()
            fileManager.copyDirectory(tempDir, pluginDir)

            if (dataBackup != null && dataBackup.exists()) {
                val userDataDir = File(pluginDir, "data")
                fileManager.copyDirectory(dataBackup, userDataDir)
                fileManager.deleteRecursively(dataBackup)
                Logger.success(TAG, "用户数据已恢复")
            }

            migratePluginDataVersion(info)

            SecurityUtils.savePluginSignature(info.pluginId, file, preferenceManager)

            Logger.success(TAG, "安装成功: ${info.name} (${info.pluginId})")
            info

        } catch (e: Exception) {
            Logger.e(TAG, "安装失败: ${e.message}", e)
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
                Logger.d(TAG, "首次安装，数据版本: ${info.version}")
            } else if (currentVersion < info.version) {
                Logger.i(TAG, "数据迁移: $currentVersion -> ${info.version}")
                pluginContext.setDataVersion(info.version)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "数据版本检查失败", e)
        }
    }

    suspend fun installPlugin(file: File, fileName: String): PluginInfo? {
        Logger.enter(TAG, "installPlugin")
        Logger.param(TAG, "文件", file.absolutePath)

        return try {
            if (!SecurityUtils.verifyFileSignature(file, preferenceManager)) {
                Logger.e(TAG, "插件签名验证失败")
                _error.value = "插件签名验证失败，文件可能被篡改"
                return null
            }

            val info = installPluginInternal(file, fileName)

            if (info != null) {
                checkPluginDependencies(info)
                refreshPlugins()
                createPluginDynamicShortcut(info)
                notifyWidgetsRefresh()
                Logger.success(TAG, "插件安装成功: ${info.name}")
            }

            info
        } catch (e: Exception) {
            Logger.e(TAG, "安装插件异常", e)
            _error.value = e.message
            null
        } finally {
            Logger.exit(TAG, "installPlugin", System.currentTimeMillis())
        }
    }

    private fun checkPluginDependencies(info: PluginInfo) {
        if (info.dependencies.isNotEmpty()) {
            Logger.i(TAG, "插件 ${info.name} 依赖: ${info.dependencies.joinToString()}")
            val installedIds = _installedPluginIds.value
            val missingDeps = info.dependencies.filter { !installedIds.contains(it) }
            if (missingDeps.isNotEmpty()) {
                Logger.w(TAG, "缺少依赖: ${missingDeps.joinToString()}")
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
            Logger.e(TAG, "文件不存在: $filePath")
            return null
        }
        return installPlugin(file, file.name)
    }

    // ==================== 插件卸载 ====================

    suspend fun uninstallPlugin(pluginId: String): Boolean {
        Logger.action(TAG, "卸载插件", pluginId)

        if (isBackendRunning(pluginId)) {
            stopBackend(pluginId)
        }

        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val optDir = File(context.codeCacheDir, "opt/$pluginId")

            try {
                val pluginContext = PluginContext(context, pluginDir.absolutePath)
                pluginContext.deleteAllPluginData()
                Logger.d(TAG, "已清理插件数据: $pluginId")
            } catch (e: Exception) {
                Logger.w(TAG, "清理数据失败: ${e.message}")
            }

            val result = fileManager.deleteRecursively(pluginDir)
            fileManager.deleteRecursively(optDir)

            if (result) {
                classLoaders.remove(pluginId)
                pluginInstances.remove(pluginId)
                removePluginWebView(pluginId)
                removePluginDynamicShortcut(pluginId)
                preferenceManager.removePluginSignature(pluginId)
                refreshPlugins()
                notifyWidgetsRefresh()
                Logger.success(TAG, "插件卸载成功: $pluginId")
            }
            result
        } catch (e: Exception) {
            Logger.e(TAG, "卸载插件失败", e)
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
            Logger.e(TAG, "卸载旧版本失败")
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
            Logger.e(TAG, "插件目录不存在")
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
        Logger.action(TAG, "打开插件", pluginId)
        val info = getPluginInfo(pluginId)
        if (info == null) {
            Toast.makeText(context, "插件不存在: $pluginId", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, PluginHostActivity::class.java)
        intent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ==================== 插件视图加载（原生插件） ====================

    fun getPluginViewSync(pluginId: String, context: Context, container: ViewGroup?): View? {
        Logger.enter(TAG, "getPluginViewSync")
        Logger.param(TAG, "pluginId", pluginId)

        val info = getPluginInfo(pluginId) ?: return null

        if (info.uiType == "web") {
            Logger.d(TAG, "Web插件由PluginHostActivity处理")
            return null
        }

        try {
            var classLoader = classLoaders[pluginId]

            if (classLoader == null) {
                val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
                val dexFile = File(pluginDir, Constants.PLUGIN_DEX_FILE)
                if (!dexFile.exists()) {
                    Logger.e(TAG, "DEX文件不存在: ${dexFile.absolutePath}")
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
                Logger.success(TAG, "DexClassLoader 创建成功")
            }

            var plugin = pluginInstances[pluginId]?.get()
            if (plugin == null) {
                val clazz = classLoader.loadClass(info.mainClass)
                plugin = clazz.newInstance() as PluginInterface
                pluginInstances[pluginId] = WeakReference(plugin)
                Logger.success(TAG, "插件实例化成功")
            }

            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val pluginContext = PluginContext(context, pluginDir.absolutePath)

            val view = plugin.onCreateView(pluginContext, container, null)

            if (view != null) {
                Logger.success(TAG, "插件视图创建成功")
            } else {
                Logger.e(TAG, "插件视图创建失败")
            }

            return view

        } catch (e: ClassNotFoundException) {
            Logger.e(TAG, "主类未找到: ${info.mainClass}", e)
            return null
        } catch (e: Exception) {
            Logger.e(TAG, "加载原生插件异常", e)
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
            Logger.w(TAG, "插件未安装: $pluginId")
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
        return if (category == "全部") _plugins.value else _plugins.value.filter { it.category == category }
    }

    fun getAllCategories(): List<String> {
        val categories = mutableSetOf("全部", "未分类")
        _plugins.value.forEach { categories.add(it.category.ifEmpty { "未分类" }) }
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
            Logger.e(TAG, "更新分类失败", e)
            false
        }
    }

    // ==================== 动态快捷方式 ====================

    @Suppress("DEPRECATION")
    private fun createPluginDynamicShortcut(plugin: PluginInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
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
                    Logger.success(TAG, "创建动态快捷方式: ${plugin.name}")
                }
            } else {
                createShortcutForOldVersions(plugin)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "创建快捷方式失败", e)
        }
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
            Logger.success(TAG, "创建旧版快捷方式: ${plugin.name}")
        } catch (e: Exception) {
            Logger.e(TAG, "创建旧版快捷方式失败", e)
        }
    }

    private fun removePluginDynamicShortcut(pluginId: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                shortcutManager?.removeDynamicShortcuts(listOf("plugin_$pluginId"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "移除快捷方式失败", e)
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
                _plugins.value.forEach { createPluginDynamicShortcut(it) }
                Logger.i(TAG, "刷新动态快捷方式完成")
            } catch (e: Exception) {
                Logger.e(TAG, "刷新快捷方式失败", e)
            }
        }
    }

    private fun getPluginIconForShortcut(plugin: PluginInfo): Icon? {
        val bitmap = getPluginIconBitmap(plugin)
        return if (bitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val scaled = Bitmap.createScaledBitmap(bitmap, 72, 72, true)
            Icon.createWithBitmap(scaled)
        } else null
    }

    private fun getPluginIconBitmap(plugin: PluginInfo): Bitmap? {
        try {
            val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
            if (pluginDir.exists()) {
                val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                val iconFile = File(pluginDir, iconPath)
                if (iconFile.exists()) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    return BitmapFactory.decodeFile(iconFile.absolutePath, options)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "获取插件图标失败", e)
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
            Logger.i(TAG, "小部件刷新通知已发送")
        } catch (e: Exception) {
            Logger.e(TAG, "通知小部件刷新失败", e)
        }
    }

    fun refreshAndNotifyWidgets() {
        refreshPlugins()
        notifyWidgetsRefresh()
    }

    // ==================== 生命周期管理 ====================

    fun onPluginResume(pluginId: String) {
        getPluginInstance(pluginId)?.onResume()
        getPluginWebView(pluginId)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('resume'));", null)
            it.onResume()
            it.resumeTimers()
        }
    }

    fun onPluginPause(pluginId: String) {
        getPluginInstance(pluginId)?.onPause()
        getPluginWebView(pluginId)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('pause'));", null)
            it.onPause()
            it.pauseTimers()
        }
    }

    fun onPluginDestroy(pluginId: String) {
        getPluginInstance(pluginId)?.onDestroy()
        getPluginWebView(pluginId)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('destroy'));", null)
            it.loadUrl("about:blank")
            it.clearHistory()
            it.clearCache(true)
            it.destroy()
        }
        removePluginWebView(pluginId)
        pluginInstances.remove(pluginId)
        classLoaders.remove(pluginId)
    }

    fun onPluginBackPressed(pluginId: String): Boolean {
        val plugin = getPluginInstance(pluginId)
        if (plugin?.onBackPressed() == true) return true
        getPluginWebView(pluginId)?.let {
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
            Logger.e(TAG, "Context 不是 Activity，无法请求权限")
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
            Logger.e(TAG, "Context 不是 Activity，无法请求权限")
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
            onProgress?.invoke("普通权限", 0, 2)
            val requestCode = 3000 + pluginId.hashCode() % 1000
            PluginPermissionManager.requestPermissions(
                activity,
                normal.toTypedArray(),
                requestCode
            )
            onProgress?.invoke("普通权限已授予", 1, 2)
        }
        
        if (special.isNotEmpty()) {
            onProgress?.invoke("特殊权限", 1, 2)
            PluginPermissionManager.openAppSettings(activity)
            onProgress?.invoke("请手动开启特殊权限", 2, 2)
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
        Logger.d(TAG, "保存权限配置: $pluginId -> $permission = $granted")
    }

    fun clearPluginPermissionConfigs(pluginId: String) {
        val prefs = context.getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$pluginId", Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith("perm_") }
        keys.forEach { prefs.edit().remove(it).apply() }
        Logger.d(TAG, "清除权限配置: $pluginId")
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
        Logger.d(TAG, "🔍 PluginManager.startBackend() 被调用")
        Logger.d(TAG, "📦 pluginId: $pluginId")
        Logger.d(TAG, "⏰ 时间: ${System.currentTimeMillis()}")
        
        val info = getPluginInfo(pluginId)
        Logger.d(TAG, "📌 info == null: ${info == null}")
        
        if (info == null) {
            Logger.e(TAG, "❌ 插件不存在: $pluginId")
            callback(false, 0, "插件不存在")
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
            Logger.w(TAG, "❌ 插件未配置后端")
            callback(false, 0, "插件未配置后端")
            return
        }

        Logger.i(TAG, "✅ 条件满足，调用 PluginBackendManager.startBackend()")
        
        Thread {
            try {
                Logger.d(TAG, "🔄 调用 PluginBackendManager.startBackend()...")
                val startTime = System.currentTimeMillis()
                val success = PluginBackendManager.startBackend(context, info)
                val elapsed = System.currentTimeMillis() - startTime
                val port = if (success) PluginBackendManager.getPort(pluginId) else 0
                val error = if (success) null else "启动失败"
                
                Logger.d(TAG, "📊 启动结果: success=$success, port=$port, error=$error, 耗时=${elapsed}ms")
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(success, port, error)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "❌ 启动后端异常: ${e.message}", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(false, 0, e.message)
                }
            }
        }.start()
    }

    fun stopBackend(pluginId: String) {
        PluginBackendManager.stopBackend(pluginId)
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
        Logger.i(TAG, "已清理所有插件")
    }

    fun refreshWorkFolder() {
        Logger.i(TAG, "刷新工作目录: ${preferenceManager.getWorkFolder()}")
        refreshPlugins()
    }
}