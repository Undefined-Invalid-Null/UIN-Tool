package com.UIN.Tool.plugin

import com.UIN.Tool.utils.Str
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.UIN.Tool.utils.isValidPluginId
import dalvik.system.DexClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference

class PluginManager private constructor(
    private val context: Context,
    private val fileManager: FileManager,
    private val preferenceManager: PreferenceManager
) {

    companion object {
        private const val TAG = "PluginManager"
        private var instance: PluginManager? = null

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
         *
         * C-12：原为无界 static map —— 宿主 Activity 与其 WebView（数十 MB）每插件常驻；
         * 现限制为 [MAX_RETAINED_WEB_VIEWS] 个，超限时淘汰最早的一份并 destroy。
         * 表中只保存「宿主 Activity 已销毁、尚未被重开取走」的 WebView（take 即移出），
         * 因此淘汰与清理均不会碰到正在显示的 WebView。
         */
        private const val MAX_RETAINED_WEB_VIEWS = 4
        private val retainedWebViews = LinkedHashMap<String, WebView?>()

        @JvmStatic
        fun retainSharedWebView(pluginId: String, webView: WebView?) {
            retainedWebViews.remove(pluginId)
            if (webView == null) return
            retainedWebViews[pluginId] = webView
            while (retainedWebViews.size > MAX_RETAINED_WEB_VIEWS) {
                val iterator = retainedWebViews.entries.iterator()
                if (!iterator.hasNext()) break
                val eldest = iterator.next()
                iterator.remove()
                runCatching { eldest.value?.destroy() }
                    .onFailure { Logger.e(TAG, "retained WebView destroy failed: ${eldest.key}", it) }
            }
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

        /** 从实例键中解析插件 ID（无冒号时本身即插件 ID） */
        @JvmStatic
        fun pluginIdOfInstance(instanceKey: String): String =
            instanceKey.substringBefore(":")
    }

    // ==================== 状态流 ====================

    /**
     * C-03：插件权限请求的待回执表（requestCode -> [宿主 Activity, 回调]）。
     * 系统弹窗返回后由宿主 Activity 调用 [dispatchPermissionResult] 回填真实结果；
     * 宿主销毁时由 [failPendingPermissionRequests] 仅清理自己发起的请求。
     */
    private val pendingPermissionRequests = java.util.concurrent.ConcurrentHashMap<
        Int, Pair<WeakReference<android.app.Activity>, (Boolean) -> Unit>>()

    /** 原生插件当前存活宿主 Activity 的实例，用于默认单实例去重（pluginId -> instanceKey） */
    private val activeNativeInstances = mutableMapOf<String, String>()

    /** 存活（未销毁）的 PluginHostActivity，键为插件 ID。
     *  用于「保留会话开启时同一插件只保留一个窗口（多任务仅显示一个）」的去重。 */
    private val livePluginHosts = mutableMapOf<String, WeakReference<PluginHostActivity>>()

    fun registerLivePluginHost(pluginId: String, activity: PluginHostActivity) {
        livePluginHosts[pluginId] = WeakReference(activity)
    }

    fun unregisterLivePluginHost(pluginId: String, activity: PluginHostActivity) {
        val ref = livePluginHosts[pluginId]
        if (ref?.get() === activity) {
            livePluginHosts.remove(pluginId)
        }
    }

    /** 该插件当前是否已有存活窗口（保留会话单窗口去重）。 */
    fun getLivePluginHost(pluginId: String): PluginHostActivity? {
        val ref = livePluginHosts[pluginId] ?: return null
        val host = ref.get()
        if (host == null || host.isFinishing || host.isDestroyed) {
            livePluginHosts.remove(pluginId)
            return null
        }
        return host
    }

    /** 原生插件实例表（兼容旧字段 pluginInstances）：按「实例键」隔离，多开各实例独立销毁 */
    private val pluginInstancesByKey = mutableMapOf<String, WeakReference<PluginInterface>>()

    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins.asStateFlow()

    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 500L

    // C-26：防抖由「窗口内直接 return 丢弃」改为「pending 标记 + 到期必刷」，
    // 否则安装/卸载后紧跟的那次刷新会被吞掉，列表不更新
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshScheduled = false
    private var refreshPending = false
    private val refreshRunnable = Runnable {
        refreshScheduled = false
        if (refreshPending) {
            refreshPending = false
            lastRefreshTime = System.currentTimeMillis()
            performRefreshPlugins()
        }
    }

    init {
        refreshPlugins()
    }

    // ==================== 插件列表管理 ====================

    fun refreshPlugins() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < refreshDebounceMs) {
            refreshPending = true
            if (!refreshScheduled) {
                refreshScheduled = true
                refreshHandler.postDelayed(refreshRunnable, refreshDebounceMs - (now - lastRefreshTime))
            }
            return
        }
        refreshPending = false
        lastRefreshTime = now
        performRefreshPlugins()
    }

    private fun performRefreshPlugins() {
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
                                PluginInfo.fromJson(it)?.let { pluginInfo ->
                                    // D-42A2：目录名回填改 copy（模型全 val）
                                    pluginList.add(
                                        if (pluginInfo.pluginId.isEmpty()) pluginInfo.copy(pluginId = dir.name)
                                        else pluginInfo
                                    )
                                }
                            }
                        }
                    }
                }
            }

            _plugins.value = pluginList
            _installedPluginIds.value = pluginList.map { it.pluginId }.toSet()

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

    /**
     * 只读读取 tpk 内的 plugin.json，返回 pluginId + version，用于签名校验
     * （按 pluginId 记「版本:哈希」—— 同版本钉哈希、跨版本放行更新，见 SecurityUtils）。
     */
    private fun readPluginMetaFromTpk(file: File): Pair<String, Int>? {
        return try {
            java.util.zip.ZipFile(file).use { zf ->
                val entry = zf.getEntry(Constants.PLUGIN_CONFIG_FILE) ?: return null
                val jsonContent = zf.getInputStream(entry).bufferedReader().use { it.readText() }
                // 编译修（26 复查）：fromJson 可空 —— 先早退收窄，后面 info.version 才有非空智能转换。
                val info = PluginInfo.fromJson(jsonContent) ?: return null
                val pluginId = info.pluginId ?: ""
                if (pluginId.isEmpty() || !pluginId.isValidPluginId()) null
                else pluginId to info.version
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.invalid_plugin_json), e)
            null
        }
    }

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

            if (!info.pluginId.isValidPluginId()) {
                Logger.e(TAG, Str.get(R.string.invalid_plugin_id_format_pluginid, info.pluginId))
                return null
            }

            // C-25：minHostVersion 是「宿主应用版本号」，原先误比 Android API 级别
            // （26>23 会把本该可用的插件全部拒装）。改比 BuildConfig.VERSION_CODE。
            if (info.minHostVersion > com.UIN.Tool.BuildConfig.VERSION_CODE) {
                Logger.e(TAG, Str.get(R.string.android_version_too_old_info_minhost, info.minHostVersion, com.UIN.Tool.BuildConfig.VERSION_CODE))
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

            SecurityUtils.savePluginSignature(info.pluginId, info.version, file, preferenceManager)

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

    // F-15：安装单飞 —— 同一 pluginId 的并发安装只允许一个，后来的直接丢弃
    //（原先 UI 层只是禁用按钮，多入口并发仍可触发两次安装，第二次会覆盖第一次的结果）
    private val installingIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun installPlugin(file: File, fileName: String): PluginInfo? {
        Logger.enter(TAG, "installPlugin")
        Logger.param(TAG, Str.get(R.string.file), file.absolutePath)

        var inFlightId: String? = null
        return try {
            val meta = readPluginMetaFromTpk(file)
            if (meta == null) {
                Logger.e(TAG, Str.get(R.string.invalid_plugin_json))
                _error.value = Str.get(R.string.invalid_plugin_json)
                return null
            }
            val (pluginId, tpkVersion) = meta

            if (!preferenceManager.isDevIgnoreSignatureEnabled() &&
                !SecurityUtils.verifyFileSignature(pluginId, tpkVersion, file, preferenceManager)) {
                Logger.e(TAG, Str.get(R.string.plugin_signature_verification_failed))
                _error.value = Str.get(R.string.plugin_signature_verification_failed_2)
                return null
            }

            if (!installingIds.add(pluginId)) {
                Logger.w(TAG, "F-15: plugin '$pluginId' is already installing, drop duplicate trigger")
                return null
            }
            inFlightId = pluginId

            val info = installPluginInternal(file, fileName)

            if (info != null) {
                checkPluginDependencies(info)
                refreshPlugins()
                notifyWidgetsRefresh()
                Logger.success(TAG, Str.get(R.string.plugin_installed_info_name, info.name))
            }

            info
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.plugin_install_error), e)
            _error.value = e.message
            null
        } finally {
            inFlightId?.let { installingIds.remove(it) }
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

        if (!pluginId.isValidPluginId()) {
            Logger.e(TAG, Str.get(R.string.invalid_plugin_id_format_pluginid, pluginId))
            return false
        }

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
                pluginInstancesByKey.entries.removeAll { pluginIdOfInstance(it.key) == pluginId }
                removePluginWebViewsOf(pluginId)
                // B-50：连带清掉该插件在事件总线上的监听器与粘性事件
                com.UIN.Tool.core.plugin.PluginEventBus.removePlugin(pluginId)
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

    /** 把旧插件目录中的用户数据（data）合并进新目录。 */
    private fun preserveUserData(oldDir: File, newDir: File) {
        try {
            val oldData = File(oldDir, "data")
            val newData = File(newDir, "data")
            if (oldData.exists()) {
                if (!newData.exists()) newData.mkdirs()
                oldData.listFiles()?.forEach { file ->
                    val target = File(newData, file.name)
                    if (file.isDirectory) {
                        fileManager.copyDirectory(file, target)
                    } else {
                        file.copyTo(target, overwrite = true)
                    }
                }
                Logger.d(TAG, Str.get(R.string.user_data_preserved_during_update))
            }
        } catch (e: Exception) {
            Logger.w(TAG, Str.get(R.string.failed_to_preserve_user_data_e_me, e.message))
        }
    }

    // ==================== 插件导出 ====================

    suspend fun exportPlugin(pluginId: String, destFile: File): Boolean {
        if (!pluginId.isValidPluginId()) {
            Logger.e(TAG, Str.get(R.string.invalid_plugin_id_format_pluginid, pluginId))
            return false
        }
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

    fun getPluginViewSync(pluginId: String, context: Context, container: ViewGroup?, forceNewInstance: Boolean = false, instanceKey: String? = null): View? {
        Logger.enter(TAG, "getPluginViewSync")
        Logger.param(TAG, "pluginId", pluginId)

        val info = getPluginInfo(pluginId) ?: return null

        if (info.uiType == "web") {
            Logger.d(TAG, Str.get(R.string.web_plugins_handled_by))
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

            // 多开时每个实例键有独立 PluginInterface；默认单实例复用 pluginId 实例
            val instanceLookupKey = if (instanceKey != null) instanceKey else pluginId
            var plugin = pluginInstancesByKey[instanceLookupKey]?.get()

            if (forceNewInstance || plugin == null) {
                // 开发者选项开启的「原生插件多开」：每次新建 PluginInterface 实例。
                // classLoader 保持共享，实例与 View 相互独立。
                val clazz = classLoader.loadClass(info.mainClass)
                plugin = clazz.newInstance() as PluginInterface
                pluginInstancesByKey[instanceLookupKey] = WeakReference(plugin)
                Logger.success(TAG, Str.get(R.string.plugin_instantiated))
            }
            // 兼容旧调用：无 instanceKey（单实例）时同步更新旧 pluginInstances 表
            if (instanceKey == null) {
                pluginInstances[pluginId] = WeakReference(plugin)
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

    /** 按「实例键」获取原生插件实例（多开隔离）。无实例键时回退到 pluginId 共享实例。 */
    fun getPluginInstanceByKey(instanceKey: String): PluginInterface? {
        return pluginInstancesByKey[instanceKey]?.get() ?: pluginInstances[pluginIdOfInstance(instanceKey)]?.get()
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
        return if (category == Str.get(R.string.all)) _plugins.value else _plugins.value.filter { it.displayCategory() == category }
    }

    fun getAllCategories(): List<String> {
        val categories = mutableSetOf(Str.get(R.string.all), Str.get(R.string.uncategorized))
        _plugins.value.forEach { categories.add(it.displayCategory()) }
        return categories.toList()
    }

    fun updatePluginCategory(pluginId: String, newCategory: String): Boolean {
        val plugin = getPluginInfo(pluginId) ?: return false
        // D-41：本地化「未分类」/空串统一存回稳定 key，避免把 UI 文案持久化成数据
        val normalizedCategory = if (newCategory.isBlank() || newCategory == Str.get(R.string.uncategorized)) {
            com.UIN.Tool.domain.model.PluginInfo.CATEGORY_UNCATEGORIZED
        } else {
            newCategory
        }
        val updated = plugin.copy(category = normalizedCategory)
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

        // ==================== 动态快捷方式已移除，改为 shortcuts.xml 静态快捷方式 ====================

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
        getPluginInstanceByKey(instanceKey)?.onResume()
        getPluginWebView(instanceKey)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('resume'));", null)
            it.onResume()
            it.resumeTimers()
        }
    }

    fun onPluginPause(instanceKey: String) {
        getPluginInstanceByKey(instanceKey)?.onPause()
        getPluginWebView(instanceKey)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('pause'));", null)
            it.onPause()
            it.pauseTimers()
        }
    }

    fun onPluginDestroy(instanceKey: String) {
        val pluginId = pluginIdOfInstance(instanceKey)
        // 仅销毁当前实例键对应的原生实例，不影响同插件其他多开实例
        pluginInstancesByKey.remove(instanceKey)?.get()?.onDestroy()
        if (pluginInstancesByKey.keys.none { pluginIdOfInstance(it) == pluginId }) {
            getPluginInstance(pluginId)?.onDestroy()
            pluginInstances.remove(pluginId)
        }
        getPluginWebView(instanceKey)?.let {
            it.evaluateJavascript("if(window.dispatchEvent) window.dispatchEvent(new Event('destroy'));", null)
            it.loadUrl("about:blank")
            it.clearHistory()
            it.clearCache(true)
            it.destroy()
        }
        removePluginWebView(instanceKey)
        // classLoader 在最后一个实例销毁后才回收（同一 dex 可被多开实例共享）
        if (pluginInstancesByKey.keys.none { pluginIdOfInstance(it) == pluginId }) {
            classLoaders.remove(pluginId)
        }
    }

    fun onPluginBackPressed(instanceKey: String): Boolean {
        val pluginId = pluginIdOfInstance(instanceKey)
        val plugin = getPluginInstanceByKey(instanceKey)
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

    @Deprecated("Deprecated: use per-permission check via PluginPermissionManager instead")
    fun getPermissionState(pluginId: String): Int {
        return PluginPermissionManager.getPermissionState(context, pluginId)
    }

    @Deprecated("Deprecated: use per-permission grant/block via PluginPermissionManager instead")
    fun setPermissionState(pluginId: String, state: Int) {
        PluginPermissionManager.setPermissionState(context, pluginId, state)
    }

    @Deprecated("Deprecated: use per-permission check via PluginPermissionManager instead")
    fun shouldShowPermissionDialog(pluginId: String): Boolean {
        return PluginPermissionManager.shouldShowPermissionDialog(context, pluginId)
    }

    /**
     * C-03：当前发起权限请求的宿主 Activity。
     * PluginManager 构造时持的是 applicationContext（[getInstance]），`context as? Activity`
     * 恒为 null，导致权限弹窗从未真正发起；这里由 MainActivity.onResume/onDestroy 维护弱引用。
     */
    private var hostActivity: WeakReference<android.app.Activity>? = null

    fun onHostActivityResumed(activity: android.app.Activity) {
        hostActivity = WeakReference(activity)
    }

    fun onHostActivityDestroyed(activity: android.app.Activity) {
        if (hostActivity?.get() === activity) hostActivity = null
        failPendingPermissionRequests(activity)
    }

    /** 解析可用于弹系统权限窗的 Activity：优先 applicationContext（若真是 Activity），否则取登记的宿主。 */
    private fun resolveRequestActivity(): android.app.Activity? {
        val fromContext = context as? android.app.Activity
        if (fromContext != null && !fromContext.isFinishing && !fromContext.isDestroyed) {
            return fromContext
        }
        val host = hostActivity?.get()
        return host?.takeUnless { it.isFinishing || it.isDestroyed }
    }

    fun requestPluginPermissions(pluginId: String, onResult: (Boolean) -> Unit) {
        val activity = resolveRequestActivity()
        if (activity == null) {
            Logger.e(TAG, Str.get(R.string.context_is_not_an_activity_cannot))
            onResult(false)
            return
        }
        val permissions = getPluginMissingPermissions(pluginId)
        if (permissions.isEmpty()) {
            onResult(true)
            return
        }
        val requestCode = 2000 + Math.floorMod(pluginId.hashCode(), 1000)
        // C-03：先登记回调，由 Activity.onRequestPermissionsResult 回填真实授权结果
        // （原先请求后立即 onResult(true)，属假异步，调用方以为已授予）。
        pendingPermissionRequests[requestCode] = WeakReference(activity) to onResult
        PluginPermissionManager.requestPermissions(
            activity,
            permissions.toTypedArray(),
            requestCode
        )
    }

    /**
     * C-03：系统权限请求结果回填入口，由宿主 Activity 调用。
     * @return true 表示该 requestCode 由插件权限流程消费（调用方无需再处理）。
     */
    fun dispatchPermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        val (owner, callback) = pendingPermissionRequests.remove(requestCode) ?: return false
        owner.clear()
        val granted = grantResults.isNotEmpty() &&
            grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
        Logger.d(TAG, "permission request #$requestCode finished, granted=$granted")
        callback(granted)
        return true
    }

    /** 宿主 Activity 销毁时，仅中止由它发起的未决请求，避免回调持有 Compose 状态造成泄漏。 */
    fun failPendingPermissionRequests(activity: android.app.Activity) {
        if (pendingPermissionRequests.isEmpty()) return
        pendingPermissionRequests.entries
            .filter { it.value.first.get() === activity }
            .map { it.key }
            .forEach { code ->
                pendingPermissionRequests.remove(code)?.second?.let { cb ->
                    Logger.w(TAG, "pending permission request #$code aborted (activity destroyed)")
                    cb(false)
                }
            }
    }

    fun requestPluginPermissionsByGroups(
        pluginId: String,
        onProgress: ((String, Int, Int) -> Unit)? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val activity = resolveRequestActivity()
        if (activity == null) {
            Logger.e(TAG, Str.get(R.string.context_is_not_an_activity_cannot))
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
        val hasSpecial = special.isNotEmpty()
        
        if (normal.isNotEmpty()) {
            onProgress?.invoke(Str.get(R.string.normal_permissions), 0, 2)
            val requestCode = 3000 + Math.floorMod(pluginId.hashCode(), 1000)
            // C-03：等系统真实回执后再报进度与完成，不再请求即刻报"已授予"
            pendingPermissionRequests[requestCode] = WeakReference(activity) to { granted: Boolean ->
                onProgress?.invoke(Str.get(R.string.normal_permissions_granted), 1, 2)
                // 含特殊权限时必须手动在设置里开启，故整体结果恒为未完成
                onComplete(granted && !hasSpecial)
            }
            PluginPermissionManager.requestPermissions(
                activity,
                normal.toTypedArray(),
                requestCode
            )
        }

        if (hasSpecial) {
            onProgress?.invoke(Str.get(R.string.special_permissions), if (normal.isNotEmpty()) 1 else 0, 2)
            PluginPermissionManager.openAppSettings(activity)
            onProgress?.invoke(Str.get(R.string.please_enable_special_permissions), 2, 2)
            if (normal.isEmpty()) {
                // 只剩特殊权限：无法通过系统弹窗授权，引导用户手动开启，按未完成返回
                onComplete(false)
            }
        } else if (normal.isEmpty()) {
            onComplete(true)
        }
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
        return PluginBackendManager.isAnyBackendRunningForPlugin(pluginId)
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

        Logger.i(TAG, Str.get(R.string.conditions_met_calling))
        
        Thread {
            try {
                Logger.d(TAG, Str.get(R.string.calling_pluginbackendmanager))
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

        Logger.i(TAG, Str.get(R.string.conditions_met_calling))

        Thread {
            try {
                Logger.d(TAG, Str.get(R.string.calling_pluginbackendmanager))
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
        // 独立端口多开：插件级停止 = 停该插件名下全部实例键的后端
        PluginBackendManager.stopBackendsOfPlugin(pluginId)
    }

    /** 独立端口多开：实例持有自己的后端键（每成功启动一个实例后端调用一次） */
    fun acquireBackend(key: String) {
        PluginBackendManager.acquireHold(key)
    }

    /** 共享端口模式：插件实例释放共享后端（最后一个实例关闭时停止后端） */
    fun releaseBackend(key: String) {
        PluginBackendManager.releaseHold(key)
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

    // ==================== 权限提示忽略管理 ====================

    fun isPluginPermissionPromptIgnored(pluginId: String): Boolean {
        val prefs = context.getSharedPreferences("plugin_permission_prompts", Context.MODE_PRIVATE)
        return prefs.getBoolean("prompt_$pluginId", false)
    }

    fun setPluginPermissionPromptIgnored(pluginId: String, ignored: Boolean) {
        val prefs = context.getSharedPreferences("plugin_permission_prompts", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("prompt_$pluginId", ignored).apply()
    }

    // ==================== 清理 ====================

    fun refreshWorkFolder() {
        Logger.i(TAG, Str.get(R.string.refreshing_work_dir_preferencemanage, preferenceManager.getWorkFolder()))
        refreshPlugins()
    }

    // ==================== 多开设置 ====================

    fun isNativeMultiInstanceEnabled(): Boolean = preferenceManager.isNativeMultiInstanceEnabled()

    fun setNativeMultiInstanceEnabled(enabled: Boolean) {
        preferenceManager.setNativeMultiInstanceEnabled(enabled)
    }

    fun isSharedSessionRetainEnabled(): Boolean = preferenceManager.isSharedSessionRetainEnabled()

    fun setSharedSessionRetainEnabled(enabled: Boolean) {
        preferenceManager.setSharedSessionRetainEnabled(enabled)
    }

    fun isDevIgnoreSignatureEnabled(): Boolean = preferenceManager.isDevIgnoreSignatureEnabled()

    fun setDevIgnoreSignatureEnabled(enabled: Boolean) {
        preferenceManager.setDevIgnoreSignatureEnabled(enabled)
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