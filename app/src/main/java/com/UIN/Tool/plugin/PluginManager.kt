// app/src/main/java/com/UIN/Tool/plugin/PluginManager.kt
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
import com.UIN.Tool.utils.PermissionUtils
import com.UIN.Tool.utils.SecurityUtils
import dalvik.system.DexClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.lang.ref.WeakReference

/**
 * 插件管理器 - 集成权限管理
 */
class PluginManager private constructor(
    private val context: Context,
    private val fileManager: FileManager,
    private val preferenceManager: PreferenceManager
) {

    companion object {
        private var instance: PluginManager? = null
        private var ignoreSignatureWarning = false
        private val webViewCache = mutableMapOf<String, WebView?>()
        private const val TAG = "PluginManager"

        private val classLoaders = mutableMapOf<String, DexClassLoader>()
        private val pluginInstances = mutableMapOf<String, WeakReference<PluginInterface>>()

        @JvmStatic
        fun getInstance(context: Context): PluginManager {
            if (instance == null) {
                instance = PluginManager(
                    context,
                    FileManager(context),
                    PreferenceManager(context)
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
            Logger.d(TAG, "缓存WebView: $pluginId")
        }

        @JvmStatic
        fun getPluginWebView(pluginId: String): WebView? {
            return webViewCache[pluginId]
        }

        @JvmStatic
        fun removePluginWebView(pluginId: String) {
            webViewCache.remove(pluginId)
            Logger.d(TAG, "移除WebView缓存: $pluginId")
        }

        @JvmStatic
        fun clearWebViewCache() {
            webViewCache.values.forEach { it?.destroy() }
            webViewCache.clear()
            Logger.i(TAG, "WebView缓存已清理")
        }
    }

    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins.asStateFlow()

    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ==================== 权限管理相关 ====================
    
    /**
     * 获取插件的权限状态
     */
    fun getPluginPermissionStatus(pluginId: String): Map<String, Boolean> {
        return PluginPermissionManager.getPluginPermissionStatus(context, pluginId)
    }
    
    /**
     * 检查插件所有权限是否已授予
     */
    fun arePluginPermissionsGranted(pluginId: String): Boolean {
        return PluginPermissionManager.areAllPermissionsGranted(context, pluginId)
    }
    
    /**
     * 获取插件缺少的权限
     */
    fun getPluginMissingPermissions(pluginId: String): List<String> {
        return PluginPermissionManager.getMissingPermissions(context, pluginId)
    }
    
    /**
     * 请求插件权限（异步）
     */
    fun requestPluginPermissions(
        pluginId: String,
        onResult: (Boolean) -> Unit
    ) {
        PluginPermissionManager.requestPermissions(
            context,
            pluginId,
            onResult
        )
    }
    
    /**
     * 请求插件权限（按分组，带进度）
     */
    fun requestPluginPermissionsByGroups(
        pluginId: String,
        onProgress: ((String, Int, Int) -> Unit)? = null,
        onComplete: (Boolean) -> Unit
    ) {
        PluginPermissionManager.requestPermissionsByGroups(
            context,
            pluginId,
            onProgress,
            onComplete
        )
    }
    
    /**
     * 显示权限引导
     */
    fun showPermissionGuidance(
        pluginId: String,
        onReRequest: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        PluginPermissionManager.showPermissionGuidance(
            context,
            pluginId,
            onReRequest,
            onOpenSettings
        )
    }
    
    /**
     * 获取插件权限摘要
     */
    fun getPluginPermissionSummary(pluginId: String): PluginPermissionManager.PermissionStatusSummary {
        return PluginPermissionManager.getPermissionStatusSummary(context, pluginId)
    }

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

    // ==================== 插件安装 ====================

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
                // 检查依赖
                checkPluginDependencies(info)
                
                // ✅ 安装成功后请求权限
                requestPermissionsAfterInstall(info)
                
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

    /**
     * ✅ 安装后请求权限
     */
    private fun requestPermissionsAfterInstall(info: PluginInfo) {
        if (info.permissions.isEmpty()) {
            Logger.d(TAG, "插件 ${info.name} 没有声明权限")
            return
        }

        Logger.i(TAG, "插件 ${info.name} 声明了 ${info.permissions.size} 个权限，开始请求...")
        
        // 使用分组请求，带进度回调
        requestPluginPermissionsByGroups(
            info.pluginId,
            onProgress = { group, current, total ->
                Logger.d(TAG, "权限请求进度: ($current/$total) $group")
            },
            onComplete = { allGranted ->
                if (allGranted) {
                    Logger.success(TAG, "✅ 插件 ${info.name} 所有权限已授予")
                } else {
                    Logger.w(TAG, "⚠️ 插件 ${info.name} 部分权限被拒绝，可能无法正常运行")
                }
            }
        )
    }

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

            // 检查 Android 版本兼容性
            if (info.minHostVersion > android.os.Build.VERSION.SDK_INT) {
                Logger.e(TAG, "Android 版本过低: ${info.minHostVersion} > ${android.os.Build.VERSION.SDK_INT}")
                return null
            }

            // 验证原生插件的 DEX 文件
            if (info.uiType != "web") {
                val dexFile = File(tempDir, Constants.PLUGIN_DEX_FILE)
                if (!dexFile.exists()) {
                    Logger.e(TAG, "原生插件缺少 plugin.dex")
                    return null
                }
            }

            // 复制到正式目录
            val pluginDir = File(Constants.PLUGIN_DIR, info.pluginId)
            if (pluginDir.exists()) {
                fileManager.deleteRecursively(pluginDir)
                Logger.i(TAG, "删除旧插件: ${info.pluginId}")
            }
            pluginDir.mkdirs()

            fileManager.copyDirectory(tempDir, pluginDir)

            // 保存签名
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

    suspend fun installPluginsBatch(files: List<File>): List<PluginInfo> {
        Logger.enter(TAG, "installPluginsBatch")
        Logger.param(TAG, "文件数量", files.size)

        val installed = mutableListOf<PluginInfo>()
        val failed = mutableListOf<String>()

        files.forEach { file ->
            try {
                val info = installPlugin(file, file.name)
                if (info != null) {
                    installed.add(info)
                } else {
                    failed.add(file.name)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "安装失败: ${file.name}", e)
                failed.add(file.name)
            }
        }

        Logger.i(TAG, "批量安装完成: 成功 ${installed.size} 个, 失败 ${failed.size} 个")
        Logger.exit(TAG, "installPluginsBatch", System.currentTimeMillis())
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

    // ==================== 插件卸载 ====================

    suspend fun uninstallPlugin(pluginId: String): Boolean {
        Logger.action(TAG, "卸载插件", pluginId)

        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val optDir = File(context.codeCacheDir, "opt/$pluginId")

            val result = fileManager.deleteRecursively(pluginDir)
            fileManager.deleteRecursively(optDir)

            if (result) {
                classLoaders.remove(pluginId)
                pluginInstances.remove(pluginId)

                getPluginWebView(pluginId)?.let {
                    it.loadUrl("about:blank")
                    it.clearHistory()
                    it.clearCache(true)
                    it.destroy()
                }
                removePluginWebView(pluginId)
                removePluginDynamicShortcut(pluginId)
                preferenceManager.removePluginSignature(pluginId)
                
                // ✅ 清理权限配置
                PluginPermissionManager.clearPluginPermissionConfigs(context, pluginId)
                
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
        Logger.enter(TAG, "uninstallPluginsBatch")
        val successList = mutableListOf<String>()
        val failList = mutableListOf<String>()

        pluginIds.forEach { pluginId ->
            if (uninstallPlugin(pluginId)) {
                successList.add(pluginId)
            } else {
                failList.add(pluginId)
            }
        }

        Logger.i(TAG, "批量卸载完成: 成功 ${successList.size} 个, 失败 ${failList.size} 个")
        Logger.exit(TAG, "uninstallPluginsBatch", System.currentTimeMillis())
        return successList
    }

    // ==================== 插件更新 ====================

    suspend fun updatePlugin(pluginId: String, newPluginFile: File): Boolean {
        Logger.action(TAG, "更新插件", pluginId)

        if (!uninstallPlugin(pluginId)) {
            Logger.e(TAG, "卸载旧版本失败")
            return false
        }

        val info = installPlugin(newPluginFile, newPluginFile.name)
        if (info == null) {
            Logger.e(TAG, "安装新版本失败")
            return false
        }

        Logger.success(TAG, "插件更新成功: $pluginId -> ${info.versionName}")
        return true
    }

    fun hasPluginUpdate(pluginId: String, newVersion: Int): Boolean {
        val info = getPluginInfo(pluginId)
        return if (info == null) false else newVersion > info.version
    }

    // ==================== 插件导出 ====================

    suspend fun exportPlugin(pluginId: String, destFile: File): Boolean {
        Logger.action(TAG, "导出插件", pluginId)

        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            if (!pluginDir.exists()) {
                Logger.e(TAG, "插件目录不存在")
                return false
            }

            fileManager.zipDirectory(pluginDir, destFile)
            Logger.success(TAG, "插件导出成功: ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "导出插件失败", e)
            false
        }
    }

    suspend fun exportPluginsBatch(pluginIds: List<String>, destDir: File): List<String> {
        Logger.enter(TAG, "exportPluginsBatch")
        val successList = mutableListOf<String>()

        destDir.mkdirs()

        pluginIds.forEach { pluginId ->
            val destFile = File(destDir, "$pluginId.tpk")
            if (exportPlugin(pluginId, destFile)) {
                successList.add(pluginId)
            }
        }

        Logger.i(TAG, "批量导出完成: ${successList.size} 个")
        Logger.exit(TAG, "exportPluginsBatch", System.currentTimeMillis())
        return successList
    }

    // ==================== 插件运行 ====================

    fun openPlugin(pluginId: String, context: Context) {
        Logger.action(TAG, "打开插件", pluginId)

        val info = getPluginInfo(pluginId)
        if (info == null) {
            Logger.e(TAG, "插件不存在: $pluginId")
            Toast.makeText(context, "插件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ 检查权限状态
        if (!arePluginPermissionsGranted(pluginId)) {
            val missingPermissions = getPluginMissingPermissions(pluginId)
            Logger.w(TAG, "插件缺少权限: ${missingPermissions.joinToString()}")
            
            // 显示权限引导
            showPermissionGuidance(
                pluginId,
                onReRequest = {
                    // 重新请求权限
                    requestPluginPermissions(pluginId) { granted ->
                        if (granted) {
                            // 权限授予，打开插件
                            openPluginInternal(pluginId, context, info)
                        } else {
                            Toast.makeText(context, "权限被拒绝，插件无法正常运行", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onOpenSettings = {
                    // 打开系统设置
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }
            )
            return
        }

        // 权限已授予，直接打开
        openPluginInternal(pluginId, context, info)
    }

    private fun openPluginInternal(pluginId: String, context: Context, info: PluginInfo) {
        val intent = Intent(context, PluginHostActivity::class.java)
        intent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
        context.startActivity(intent)
    }

    // ==================== 插件加载核心方法 ====================

    fun getPluginViewSync(pluginId: String, context: Context, container: ViewGroup?): View? {
        Logger.enter(TAG, "getPluginViewSync")
        Logger.i(TAG, "========== 开始获取插件视图 ==========")
        Logger.param(TAG, "pluginId", pluginId)

        val info = getPluginInfo(pluginId)
        if (info == null) {
            Logger.e(TAG, "❌ 插件信息不存在: $pluginId")
            return null
        }
        Logger.param(TAG, "插件名称", info.name)
        Logger.param(TAG, "UI类型", info.uiType)

        // ✅ 检查权限
        if (!arePluginPermissionsGranted(pluginId)) {
            val missing = getPluginMissingPermissions(pluginId)
            Logger.w(TAG, "⚠️ 插件缺少权限: ${missing.joinToString()}")
            // 这里只记录日志，实际权限请求由 PluginHostActivity 处理
        }

        if (info.uiType == "web") {
            Logger.d(TAG, "Web插件由PluginHostActivity处理")
            return null
        }

        try {
            var classLoader = classLoaders[pluginId]

            if (classLoader == null) {
                Logger.d(TAG, "ClassLoader 不存在，创建新的")

                val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
                Logger.param(TAG, "插件目录", pluginDir.absolutePath)
                Logger.param(TAG, "目录是否存在", pluginDir.exists())

                if (!pluginDir.exists()) {
                    Logger.e(TAG, "❌ 插件目录不存在: ${pluginDir.absolutePath}")
                    return null
                }

                val dexFile = File(pluginDir, Constants.PLUGIN_DEX_FILE)
                Logger.param(TAG, "DEX文件路径", dexFile.absolutePath)
                Logger.param(TAG, "DEX文件是否存在", dexFile.exists())
                Logger.param(TAG, "DEX文件大小", "${dexFile.length()} bytes")

                if (!dexFile.exists()) {
                    Logger.e(TAG, "❌ DEX文件不存在: ${dexFile.absolutePath}")
                    return null
                }

                val optDir = File(context.codeCacheDir, "opt/$pluginId")
                Logger.param(TAG, "优化目录", optDir.absolutePath)

                if (!optDir.exists()) {
                    optDir.mkdirs()
                }

                Logger.d(TAG, "创建 DexClassLoader")
                Logger.param(TAG, "dexFile", dexFile.absolutePath)
                Logger.param(TAG, "optDir", optDir.absolutePath)
                Logger.param(TAG, "parentClassLoader", context.classLoader.toString())

                classLoader = DexClassLoader(
                    dexFile.absolutePath,
                    optDir.absolutePath,
                    null,
                    context.classLoader
                )
                classLoaders[pluginId] = classLoader
                Logger.success(TAG, "✅ DexClassLoader 创建成功")
            } else {
                Logger.d(TAG, "✅ ClassLoader 已存在，复用")
            }

            Logger.d(TAG, "尝试获取已有插件实例")
            var plugin = pluginInstances[pluginId]?.get()

            if (plugin == null) {
                Logger.d(TAG, "插件实例不存在，加载新实例")
                Logger.d(TAG, "加载主类")
                Logger.param(TAG, "mainClass", info.mainClass)

                try {
                    val clazz = classLoader.loadClass(info.mainClass)
                    Logger.success(TAG, "✅ 主类加载成功: ${info.mainClass}")
                    Logger.param(TAG, "类对象", clazz.toString())

                    Logger.d(TAG, "实例化插件")
                    plugin = clazz.newInstance() as PluginInterface
                    pluginInstances[pluginId] = WeakReference(plugin)
                    Logger.success(TAG, "✅ 插件实例化成功")
                } catch (e: ClassNotFoundException) {
                    Logger.e(TAG, "❌ 主类未找到: ${info.mainClass}", e)
                    Logger.e(TAG, "请检查:")
                    Logger.e(TAG, "  1. plugin.dex 是否包含类 ${info.mainClass}")
                    Logger.e(TAG, "  2. plugin.json 中的 mainClass 是否正确")
                    Logger.e(TAG, "  3. 包名和类名是否匹配")
                    listDexClasses(pluginId)
                    return null
                } catch (e: ClassCastException) {
                    Logger.e(TAG, "❌ 插件未实现 PluginInterface", e)
                    Logger.e(TAG, "请确保插件的 PluginInterface 包名为 com.UIN.Tool.plugin")
                    return null
                }
            } else {
                Logger.d(TAG, "✅ 插件实例已存在，复用")
            }

            Logger.d(TAG, "创建 PluginContext 并返回视图")
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val pluginContext = PluginContext(context, pluginDir.absolutePath)
            val view = plugin.onCreateView(pluginContext, container, null)

            if (view != null) {
                Logger.success(TAG, "✅ 插件视图创建成功")
            } else {
                Logger.e(TAG, "❌ 插件视图创建失败（返回 null）")
            }

            Logger.i(TAG, "========== 获取插件视图完成 ==========")
            Logger.exit(TAG, "getPluginViewSync", System.currentTimeMillis())
            return view

        } catch (e: ClassNotFoundException) {
            Logger.e(TAG, "❌ ClassNotFoundException: ${e.message}", e)
            Logger.e(TAG, "主类未找到: ${info.mainClass}")
            return null
        } catch (e: ClassCastException) {
            Logger.e(TAG, "❌ ClassCastException: ${e.message}", e)
            Logger.e(TAG, "插件未实现 PluginInterface")
            return null
        } catch (e: Exception) {
            Logger.e(TAG, "❌ 加载原生插件失败: ${e.message}", e)
            return null
        }
    }

    fun getPluginInstance(pluginId: String): PluginInterface? {
        return pluginInstances[pluginId]?.get()
    }

    fun listDexClasses(pluginId: String) {
        try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val dexFile = File(pluginDir, Constants.PLUGIN_DEX_FILE)
            if (!dexFile.exists()) {
                Logger.e(TAG, "DEX文件不存在: ${dexFile.absolutePath}")
                return
            }

            Logger.d(TAG, "========== DEX 类列表 ==========")
            val dexFileClass = Class.forName("dalvik.system.DexFile")
            val dexFileConstructor = dexFileClass.getConstructor(File::class.java)
            val dex = dexFileConstructor.newInstance(dexFile)
            val entriesMethod = dexFileClass.getMethod("entries")
            val entries = entriesMethod.invoke(dex) as java.util.Enumeration<*>
            var count = 0
            while (entries.hasMoreElements()) {
                val className = entries.nextElement().toString()
                Logger.d(TAG, "  $className")
                count++
            }
            Logger.param(TAG, "DEX 中类总数", count)
            Logger.d(TAG, "==================================")
        } catch (e: Exception) {
            Logger.e(TAG, "列出 DEX 类失败: ${e.message}", e)
        }
    }

    fun getPluginDirFile(pluginId: String): File? {
        return File(Constants.PLUGIN_DIR, pluginId).takeIf { it.exists() }
    }

    // ==================== 插件搜索和分类 ====================

    fun searchPlugins(keyword: String): List<PluginInfo> {
        if (keyword.isEmpty()) return _plugins.value

        val lowerKeyword = keyword.lowercase()
        return _plugins.value.filter { plugin ->
            plugin.name.lowercase().contains(lowerKeyword) ||
            plugin.pluginId.lowercase().contains(lowerKeyword) ||
            plugin.description.lowercase().contains(lowerKeyword) ||
            plugin.author.lowercase().contains(lowerKeyword) ||
            plugin.category.lowercase().contains(lowerKeyword)
        }
    }

    fun getPluginsByCategory(category: String): List<PluginInfo> {
        return if (category == "全部") {
            _plugins.value
        } else {
            _plugins.value.filter { it.category == category }
        }
    }

    fun getAllCategories(): List<String> {
        val categories = mutableSetOf("全部", "未分类")
        _plugins.value.forEach { plugin ->
            categories.add(plugin.category.ifEmpty { "未分类" })
        }
        return categories.toList()
    }

    fun getAllCategoriesWithSystem(): List<String> {
        return getAllCategories()
    }

    fun addCategory(categoryName: String): Boolean {
        if (categoryName.isBlank()) {
            Logger.w(TAG, "分类名称不能为空")
            return false
        }

        val trimmed = categoryName.trim()
        val existing = getAllCategories()

        if (existing.contains(trimmed)) {
            Logger.w(TAG, "分类已存在: $trimmed")
            return false
        }

        Logger.action(TAG, "添加分类", trimmed)
        return true
    }

    fun deleteCategory(categoryName: String): Boolean {
        if (categoryName.isBlank()) return false

        if (categoryName == "全部" || categoryName == "未分类") {
            Logger.w(TAG, "不能删除系统分类: $categoryName")
            return false
        }

        Logger.action(TAG, "删除分类", categoryName)

        val pluginsInCategory = getPluginsByCategory(categoryName)
        var success = true

        for (info in pluginsInCategory) {
            if (!updatePluginCategory(info.pluginId, "未分类")) {
                success = false
                Logger.e(TAG, "移动插件失败: ${info.name}")
            }
        }

        if (success) {
            Logger.success(TAG, "分类删除成功: $categoryName")
        }

        return success
    }

    fun updatePluginCategory(pluginId: String, newCategory: String): Boolean {
        Logger.action(TAG, "更新插件分类", "$pluginId -> $newCategory")

        val plugin = getPluginInfo(pluginId) ?: return false
        val updatedPlugin = plugin.copy(category = newCategory)

        val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
        val jsonFile = File(pluginDir, Constants.PLUGIN_CONFIG_FILE)

        return try {
            jsonFile.writeText(updatedPlugin.toJson())
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
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                if (shortcutManager == null) return

                val dynamicShortcuts = shortcutManager.dynamicShortcuts
                val maxCount = shortcutManager.maxShortcutCountPerActivity
                if (dynamicShortcuts.size >= maxCount) {
                    for (info in dynamicShortcuts) {
                        if (info.id.startsWith("plugin_")) {
                            shortcutManager.removeDynamicShortcuts(listOf(info.id))
                            break
                        }
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
                    Logger.success(TAG, "为插件创建动态快捷方式: ${plugin.name}")
                }
            } else {
                createShortcutForOldVersions(plugin)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "创建插件快捷方式失败: ${e.message}")
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

            val iconBitmap = getPluginIconBitmap(plugin)
            if (iconBitmap != null) {
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap)
            } else {
                addIntent.putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_extension)
                )
            }

            context.sendBroadcast(addIntent)
            Logger.success(TAG, "为插件创建旧版快捷方式: ${plugin.name}")
        } catch (e: Exception) {
            Logger.e(TAG, "创建旧版快捷方式失败: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun removePluginDynamicShortcut(pluginId: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                shortcutManager?.removeDynamicShortcuts(listOf("plugin_$pluginId"))
                Logger.i(TAG, "移除插件动态快捷方式: $pluginId")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "移除插件快捷方式失败: ${e.message}")
        }
    }

    fun refreshAllPluginShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                if (shortcutManager == null) return

                val dynamicShortcuts = shortcutManager.dynamicShortcuts
                val pluginShortcutIds = dynamicShortcuts
                    .filter { it.id.startsWith("plugin_") }
                    .map { it.id }
                if (pluginShortcutIds.isNotEmpty()) {
                    shortcutManager.removeDynamicShortcuts(pluginShortcutIds)
                }

                _plugins.value.forEach { plugin ->
                    createPluginDynamicShortcut(plugin)
                }

                Logger.i(TAG, "刷新所有插件动态快捷方式完成")
            } catch (e: Exception) {
                Logger.e(TAG, "刷新插件快捷方式失败: ${e.message}")
            }
        }
    }

    private fun getPluginIconForShortcut(plugin: PluginInfo): Icon? {
        val bitmap = getPluginIconBitmap(plugin)
        return if (bitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val scaled = Bitmap.createScaledBitmap(bitmap, 72, 72, true)
            Icon.createWithBitmap(scaled)
        } else {
            null
        }
    }

    private fun getPluginIconBitmap(plugin: PluginInfo): Bitmap? {
        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
            if (pluginDir.exists()) {
                val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                val iconFile = File(pluginDir, iconPath)
                if (iconFile.exists()) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(iconFile.absolutePath, options)
                } else null
            } else null
        } catch (e: Exception) {
            Logger.e(TAG, "获取插件图标失败", e)
            null
        }
    }

    // ==================== 小部件刷新 ====================

    private fun notifyWidgetsRefresh() {
        try {
            val intent = Intent("com.UIN.Tool.REFRESH_WIDGET")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)

            val intent1x1 = Intent("com.UIN.Tool.REFRESH_WIDGET_1x1")
            intent1x1.setPackage(context.packageName)
            context.sendBroadcast(intent1x1)

            Logger.i(TAG, "小部件刷新通知已发送")
        } catch (e: Exception) {
            Logger.e(TAG, "通知小部件刷新失败: ${e.message}", e)
        }
    }

    fun refreshAndNotifyWidgets() {
        refreshPlugins()
        notifyWidgetsRefresh()
        Logger.i(TAG, "刷新并通知小部件更新")
    }

    fun onPluginsChanged() {
        refreshAndNotifyWidgets()
    }

    // ==================== 生命周期管理 ====================

    fun onPluginResume(pluginId: String) {
        Logger.d(TAG, "插件恢复: $pluginId")
        getPluginInstance(pluginId)?.onResume()
        getPluginWebView(pluginId)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('resume'));", null)
            it.onResume()
            it.resumeTimers()
        }
    }

    fun onPluginPause(pluginId: String) {
        Logger.d(TAG, "插件暂停: $pluginId")
        getPluginInstance(pluginId)?.onPause()
        getPluginWebView(pluginId)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('pause'));", null)
            it.onPause()
            it.pauseTimers()
        }
    }

    fun onPluginDestroy(pluginId: String) {
        Logger.d(TAG, "插件销毁: $pluginId")
        val plugin = pluginInstances[pluginId]?.get()
        plugin?.onDestroy()

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
        if (plugin?.onBackPressed() == true) {
            return true
        }

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

    fun savePluginPermissionConfig(pluginId: String, permission: String, granted: Boolean) {
        PluginPermissionManager.savePluginPermissionConfig(context, pluginId, permission, granted)
    }

    fun savePluginPermissionConfigs(pluginId: String, permissions: Map<String, Boolean>) {
        PluginPermissionManager.savePluginPermissionConfigs(context, pluginId, permissions)
    }

    fun getPluginPermissionConfigs(pluginId: String): Map<String, Boolean> {
        return PluginPermissionManager.getPluginPermissionConfigs(context, pluginId)
    }

    fun clearPluginPermissionConfigs(pluginId: String) {
        PluginPermissionManager.clearPluginPermissionConfigs(context, pluginId)
    }

    // ==================== 工具方法 ====================

    fun getInstalledPluginIds(): List<String> {
        return _plugins.value.map { it.pluginId }
    }

    fun getPluginCount(): Int {
        return _plugins.value.size
    }

    fun isPluginInstalled(pluginId: String): Boolean {
        return _installedPluginIds.value.contains(pluginId)
    }

    fun refreshWorkFolder() {
        val workFolder = preferenceManager.getWorkFolder()
        Logger.i(TAG, "刷新工作目录: $workFolder")
        refreshPlugins()
    }

    fun clearAllPlugins() {
        _plugins.value.forEach { plugin ->
            unloadPluginInternal(plugin.pluginId)
        }
        _plugins.value = emptyList()
        _installedPluginIds.value = emptySet()
        Logger.i(TAG, "已清理所有插件")
    }

    private fun unloadPluginInternal(pluginId: String) {
        classLoaders.remove(pluginId)
        pluginInstances.remove(pluginId)
        getPluginWebView(pluginId)?.destroy()
        removePluginWebView(pluginId)
        removePluginDynamicShortcut(pluginId)
        clearPluginPermissionConfigs(pluginId)
    }
}