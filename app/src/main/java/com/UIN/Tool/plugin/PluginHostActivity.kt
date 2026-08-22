package com.UIN.Tool.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.PermissionUtils
import com.UIN.Tool.utils.Str
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.UIN.Tool.app.RunCommandService
import com.UIN.Tool.app.TermuxActivity
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE
import com.UIN.Tool.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedInfoDialog
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.ui.screen.permission.PluginPermissionActivity
import com.UIN.Tool.constants.AppConstants as Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.Proxy
import java.util.concurrent.TimeUnit

class PluginHostActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PluginHostActivity"
        const val EXTRA_PLUGIN_ID = "plugin_id"
        const val EXTRA_INSTANCE_ID = "instance_id"
        /** 意图中转（openWith）传入的外部数据 JSON */
        const val EXTRA_OPEN_DATA = "open_data"
        private const val KEY_PLUGIN_ID = "plugin_id"
        private const val KEY_INSTANCE_ID = "instance_id"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val BACKEND_START_TIMEOUT = 15000L
        private const val BACKEND_START_TIMEOUT_PROOT = 90000L
        private const val REQUEST_CODE_PERMISSIONS = 1001

        private var instanceCounter = 0L

        /**
         * 生成一个全局唯一的实例 ID，用于多开隔离。
         * 同一插件可同时存在多个实例键（pluginId:instanceId）。
         */
        private fun generateInstanceId(pluginId: String): String {
            instanceCounter++
            return "inst-${System.currentTimeMillis()}-${instanceCounter}"
        }
    }

    private lateinit var container: FrameLayout
    private var currentPluginId: String = ""
    var currentInstanceId: String = ""
    private var currentInstanceKey: String = ""
    private var webView: WebView? = null
    /** 实际注入到 WebView 的 JS 桥实例（每次注入时更新），权限回调等路由到该实例 */
    private var injectedJsInterface: PluginJSInterface? = null
    private lateinit var pluginManager: PluginManager
    private var pluginInfo: PluginInfo? = null
    private var isDestroyed = false

    /** 意图中转传入的外部数据 JSON（openWith），供 JS / 原生插件读取 */
    private var openDataJson: String? = null

/**
 * 后端实例键：始终使用插件 ID（多实例共享同一后端端口/进程）。
 */
private val backendKey: String
    get() = currentPluginId

    /** backend 实际被当前实例持有时为 true，用于在 destroy 时决定停止/释放 */
    private var backendHeldByThisInstance = false

    private var pluginDialogRequest by mutableStateOf<PluginDialogRequest?>(null)
    private var dialogHost: ComposeView? = null
    private val pluginDialogQueue = ArrayDeque<PluginDialogRequest>()

    private var backendPort = 0
    private var isBackendReady = false
    private var isBackendStarting = false

    private var envProgressDialog: android.app.ProgressDialog? = null

    private var backendTimeoutHandler: Handler? = null
    private var backendTimeoutRunnable: Runnable? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.separator(TAG)
        Logger.i(TAG, Str.get(R.string.pluginhostactivity_oncreate_called))
        Logger.separator(TAG)

        container = FrameLayout(this)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(container)

        // 统一对话框宿主（Compose 覆盖层）：在 onCreate 即创建，
        // 使权限门（加载前弹窗）也能使用宿主统一风格；自身透明且不拦截触摸。
        setupDialogHost()

        if (savedInstanceState != null) {
            currentPluginId = savedInstanceState.getString(KEY_PLUGIN_ID) ?: ""
            currentInstanceId = savedInstanceState.getString(KEY_INSTANCE_ID) ?: ""
        }
        if (currentPluginId.isEmpty()) {
            currentPluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: ""
        }
        if (currentInstanceId.isEmpty()) {
            currentInstanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: ""
        }
        // 意图中转数据（系统「用…打开」传入的文件/文本/URL）
        openDataJson = intent.getStringExtra(EXTRA_OPEN_DATA)

        Logger.i(TAG, "📦 currentPluginId: $currentPluginId")

        if (currentPluginId.isEmpty()) {
            Logger.e(TAG, Str.get(R.string.plugin_id_is_empty))
            Toast.makeText(this, Str.get(R.string.plugin_id_must_not_be_empty), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (currentInstanceId.isEmpty()) {
            currentInstanceId = generateInstanceId(currentPluginId)
        }
        currentInstanceKey = "$currentPluginId:$currentInstanceId"
        Logger.i(TAG, "🔑 currentInstanceKey: $currentInstanceKey")

        pluginManager = ServiceLocator.getPluginManager()
        pluginInfo = pluginManager.getPluginInfo(currentPluginId)

        // ========== ✅ 详细日志 ==========
        Logger.separator(TAG)
        Logger.i(TAG, "📌 pluginInfo == null: ${pluginInfo == null}")
        
        if (pluginInfo != null) {
            Logger.success(TAG, Str.get(R.string.plugininfo_exists))
            Logger.i(TAG, "   📌 pluginId: ${pluginInfo!!.pluginId}")
            Logger.i(TAG, "   📌 name: ${pluginInfo!!.name}")
            Logger.i(TAG, "   📌 uiType: '${pluginInfo!!.uiType}'")
            Logger.i(TAG, "   📌 backend: '${pluginInfo!!.backend}'")
            Logger.i(TAG, "   📌 backendAutoStart: ${pluginInfo!!.backendAutoStart}")
            Logger.i(TAG, "   📌 backendEntry: '${pluginInfo!!.backendEntry}'")
            Logger.i(TAG, "   📌 backendPort: ${pluginInfo!!.backendPort}")
            Logger.i(TAG, "   📌 hasBackend(): ${pluginInfo!!.hasBackend()}")
        } else {
            Logger.e(TAG, Str.get(R.string.plugininfo_is_null))
        }
        Logger.separator(TAG)

        if (pluginInfo == null) {
            Logger.e(TAG, Str.get(R.string.plugin_not_found_currentpluginid, currentPluginId))
            Toast.makeText(this, Str.get(R.string.plugin_not_found_currentpluginid_2, currentPluginId), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ========== 多开保护 ==========
        // 默认：web/cui 插件可多开；原生插件单实例（除非开发工具页开启原生多开）。
        // 若原生插件已有一个存活实例，则不再新建，直接结束本次启动。
        if (pluginInfo?.isNativePlugin() == true && !pluginManager.isNativeMultiInstanceEnabled()) {
            if (pluginManager.isNativePluginActive(currentPluginId)) {
                Logger.w(TAG, Str.get(R.string.native_plugin_already_running_single_ins, pluginInfo?.name))
                Toast.makeText(
                    this,
                    Str.get(R.string.native_plugin_already_running_single_ins_2, pluginInfo?.name),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return
            }
        }

        // 原生插件默认单实例：注册“已运行”标记，供二次打开去重
        if (pluginInfo?.isNativePlugin() == true) {
            pluginManager.onNativePluginStarted(currentPluginId, currentInstanceKey)
        }

        // 保留会话单窗口：共享端口模式 + 保留会话开启 + web 插件时，
        // 同一插件只保留一个后台窗口（多任务仅显示一个）；重复打开时把
        // 已有窗口带到前台并结束本次启动，避免多任务出现多个实例。
        if (shouldRetainSharedSession()) {
            val existing = pluginManager.getLivePluginHost(currentPluginId)
            if (existing != null && existing !== this) {
                Logger.w(TAG, Str.get(R.string.retain_session_single_window_reuse))
                try {
                    val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
                    am?.moveTaskToFront(existing.taskId, 0)
                } catch (e: Exception) {
                    Logger.w(TAG, "bring existing window to front failed: ${e.message}")
                }
                finish()
                return
            }
            pluginManager.registerLivePluginHost(currentPluginId, this)
        }

        // 权限门：加载插件前先弹权限提示（原生=所需权限；web=缺失权限）。
        // 必须先于插件说明入队，确保权限弹窗先显示。
        maybeShowPermissionGateBeforeLoad()

        // 显示插件说明（在权限门之后入队）
        showPluginNoticeIfNeeded()
    }

    /**
     * 权限门：先弹窗再打开插件（使用宿主统一风格弹窗）。
     * - 原生插件：每次打开都提示所需（声明）权限，用户可「确定」或「不再显示」；
     * - web 插件：提示尚未授予的权限，用户可「确定」「不再提示」或「管理权限」（跳转该插件权限管理页）；
     * - CUI 插件：终端场景，跳过权限门。
     */
    private fun maybeShowPermissionGateBeforeLoad() {
        val info = pluginInfo ?: run { proceedToLoadAfterGate(); return }
        if (info.isCui()) {
            proceedToLoadAfterGate()
            return
        }

        val isNative = info.isNativePlugin()
        val declared = PluginPermissionManager.getPluginDeclaredPermissions(this, currentPluginId)
        if (declared.isEmpty()) {
            proceedToLoadAfterGate()
            return
        }

        // web 插件只提示尚未授予的权限；若全部已授权则直接打开
        val shown = if (isNative) {
            declared
        } else {
            PluginPermissionManager.getMissingPermissions(this, currentPluginId).filter { it in declared }
        }
        if (shown.isEmpty()) {
            proceedToLoadAfterGate()
            return
        }
        if (pluginManager.isPluginPermissionPromptIgnored(currentPluginId)) {
            proceedToLoadAfterGate()
            return
        }

        val names = shown.joinToString("\n") { PermissionUtils.getPermissionDisplayName(it) }
        val message = (if (isNative) Str.get(R.string.permission_gate_native_message) else Str.get(R.string.permission_gate_web_message)) +
                "\n\n" + names

        Logger.i(TAG, "permissionGate: showing dialog for pluginId=$currentPluginId uiType=${info.uiType}")
        enqueuePluginDialog(
            PluginDialogRequest.Permission(
                message = message,
                showManage = !isNative,
                onConfirm = { proceedToLoadAfterGate() },
                onManage = {
                    try {
                        startActivity(
                            Intent(this, PluginPermissionActivity::class.java)
                                .putExtra(PluginPermissionActivity.EXTRA_PLUGIN_ID, currentPluginId)
                        )
                    } catch (e: Exception) {
                        Logger.e(TAG, "jump to PluginPermissionActivity failed", e)
                    }
                    finish()
                },
                onNever = {
                    pluginManager.setPluginPermissionPromptIgnored(currentPluginId, true)
                    proceedToLoadAfterGate()
                }
            )
        )
    }

    /**
     * 通过权限门后的真正加载逻辑（保持原 CUI / 普通分支语义）。
     */
    private fun proceedToLoadAfterGate() {
        if (pluginInfo?.isCui() == true) {
            // CUI 插件：在真实全屏终端（TermuxActivity）中运行 pre-command
            Logger.i(TAG, Str.get(R.string.cui_plugin_loading_placeholder_view))
            loadPlugin()
            runCuiPipeline()
        } else {
            // ✅ 调用 startBackendIfNeeded
            Logger.i(TAG, Str.get(R.string.calling_startbackendifneeded))
            startBackendIfNeeded()

            // 加载插件视图
            loadPlugin()
        }
    }

    // ============================================================
    // ✅ 带详细日志的 startBackendIfNeeded
    // ============================================================

    private fun startBackendIfNeeded() {
        Logger.separator(TAG)
        Logger.i(TAG, Str.get(R.string.startbackendifneeded_called))
        Logger.i(TAG, "📌 pluginInfo == null: ${pluginInfo == null}")
        
        if (pluginInfo != null) {
            Logger.i(TAG, "📌 pluginInfo.pluginId: ${pluginInfo!!.pluginId}")
            Logger.i(TAG, "📌 pluginInfo.backend: '${pluginInfo!!.backend}'")
            Logger.i(TAG, "📌 pluginInfo.backendRuntime: '${pluginInfo!!.backendRuntime}'")
            Logger.i(TAG, "📌 pluginInfo.backendAutoStart: ${pluginInfo!!.backendAutoStart}")
            Logger.i(TAG, "📌 pluginInfo.hasBackend(): ${pluginInfo!!.hasBackend()}")
            Logger.i(TAG, "📌 isBackendReady: $isBackendReady")
        } else {
            Logger.e(TAG, Str.get(R.string.plugininfo_is_null_cannot_check_back))
        }
        Logger.separator(TAG)
        
        val info = pluginInfo
        if (info == null || !info.hasBackend() || isBackendReady) {
            Logger.i(TAG, Str.get(R.string.conditions_not_met_skipping_backend_))
            if (info == null) Logger.i(TAG, Str.get(R.string.plugininfo_is_null_2))
            if (info != null && !info.hasBackend()) Logger.i(TAG, Str.get(R.string.hasbackend_returns_false))
            if (isBackendReady) Logger.i(TAG, Str.get(R.string.isbackendready_is_true))
            return
        }

        Logger.success(TAG, Str.get(R.string.conditions_met_starting_backend))
        isBackendStarting = true
        setupBackendTimeout()

        if (BackendConfig.isBuiltin(this)) {
            // 内置 Termux（强制 proot Alpine 容器）：走环境流水线（确保 bootstrap + alpine）
            Logger.i(TAG, Str.get(R.string.going_through_proot_other_environmen))
            runEnvironmentPipeline()
        } else {
            // 实体 Termux：直接启动后端（allow-external-apps 探测与配置引导在失败回调里处理）
            Logger.i(TAG, Str.get(R.string.going_through_normal_backend_start))
            startBackendInternal()
        }
    }

    private fun startBackendInternal() {
        showEnvProgress(Str.get(R.string.starting_backend_service_normal_runt))
        // 后端实例键：共享端口模式按插件共享；独立端口模式按实例隔离
        val key = backendKey
        Logger.i(TAG, "🔑 startBackendInternal backendKey: $key")
        pluginManager.startBackendFor(currentPluginId, key) { success, port, error ->
            isBackendStarting = false
            dismissEnvProgress()
            Logger.i(TAG, Str.get(R.string.backend_start_callback_success_succe, success, port, error))
            if (success) {
                backendPort = port
                backendHeldByThisInstance = true
                isBackendReady = true
                // 共享端口模式：每个实例持有一次共享后端（最后一个实例关闭时后端才停止）
                pluginManager.acquireBackend(currentPluginId)
                Logger.success(TAG, Str.get(R.string.backend_ready_port_port, port))
                sendBackendReadyToWebView(port)
            } else {
                Logger.e(TAG, Str.get(R.string.backend_start_failed_error, error))
                maybeShowRealTermuxGuidance()
            }
        }
    }

    // ============================================================
    // proot/other 环境流水线
    // ============================================================

    /**
     * ① Termux 基础环境 → ② Alpine 共享容器 → ③ 启动后端
     */
    private fun runEnvironmentPipeline() {
        val info = pluginInfo ?: return
        Logger.i(TAG, "🔍 runEnvironmentPipeline()")

        // 若后台正在安装 Alpine，等待完成后再继续
        if (com.UIN.Tool.UinApplication.isEnvironmentInstalling()) {
            Logger.i(TAG, "runEnvironmentPipeline: waiting for background env install...")
            showEnvProgress(Str.get(R.string.initializing_termux_base_environment))
            Thread {
                while (com.UIN.Tool.UinApplication.isEnvironmentInstalling()) {
                    Thread.sleep(300)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runEnvironmentPipeline()
                }
            }.start()
            return
        }

        ProotContainerManager.ensureTermux(this, status = { showEnvProgress(it) }) {
            Logger.success(TAG, Str.get(R.string.termux_ready_checking_alpine))
            ProotContainerManager.ensureAlpine(applicationContext, status = { showEnvProgress(it) }) { ok ->
                if (!ok) {
                    Logger.e(TAG, Str.get(R.string.alpine_container_setup_failed))
                    isBackendStarting = false
                    dismissEnvProgress()
                    showPluginInfoDialog(
                        Str.get(R.string.environment_setup_failed),
                        Str.get(R.string.alpine_container_install_failed_make)
                    )
                    return@ensureAlpine
                }
                Logger.success(TAG, Str.get(R.string.alpine_ready_processing_pre_command))
                startBackendAfterEnv()
            }
        }
    }

    private fun startBackendAfterEnv() {
        Logger.i(TAG, "🔍 startBackendAfterEnv()")
        showEnvProgress(Str.get(R.string.starting_backend_service))
        val key = backendKey
        Logger.i(TAG, "🔑 startBackendAfterEnv backendKey: $key")
        pluginManager.startBackendFor(currentPluginId, key) { success, port, error ->
            isBackendStarting = false
            dismissEnvProgress()
            Logger.i(TAG, Str.get(R.string.env_pipeline_backend_callback_succes, success, port, error))
            if (success) {
                backendPort = port
                backendHeldByThisInstance = true
                isBackendReady = true
                pluginManager.acquireBackend(currentPluginId)
                sendBackendReadyToWebView(port)
            } else {
                Toast.makeText(this, Str.get(R.string.backend_start_failed_error_unknown_e, error ?: Str.get(R.string.unknown_error)), Toast.LENGTH_LONG).show()
                maybeShowRealTermuxGuidance()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Logger.i(TAG, Str.get(R.string.onnewintent_called))

        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        if (!pluginId.isNullOrEmpty() && pluginId != currentPluginId) {
            currentPluginId = pluginId
            pluginInfo = pluginManager.getPluginInfo(pluginId)
            currentInstanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: generateInstanceId(pluginId)
            currentInstanceKey = "$currentPluginId:$currentInstanceId"
            openDataJson = intent.getStringExtra(EXTRA_OPEN_DATA)
            Logger.i(TAG, "🔑 onNewIntent instanceKey: $currentInstanceKey")
        }
    }

    private fun sendBackendReadyToWebView(port: Int) {
        webView?.evaluateJavascript(
            "if (window._onBackendReady) window._onBackendReady($port);",
            null
        )
    }

    private fun setupBackendTimeout() {
        backendTimeoutHandler = Handler(Looper.getMainLooper())
        backendTimeoutRunnable = Runnable {
            if (isBackendStarting) {
                Logger.i(TAG, Str.get(R.string.backend_start_timed_out))
                isBackendStarting = false
            }
        }
        backendTimeoutHandler?.postDelayed(
            backendTimeoutRunnable!!,
            BACKEND_START_TIMEOUT_PROOT
        )
    }

    /**
     * 展示环境准备/后端启动进度弹窗，让用户始终知道当前在做什么。
     */
    private fun showEnvProgress(message: String) {
        if (isDestroyed || isFinishing) return
        if (envProgressDialog == null) {
            envProgressDialog = android.app.ProgressDialog(this).apply {
                setCancelable(false)
                setMessage(message)
                show()
            }
        } else {
            envProgressDialog?.setMessage(message)
        }
    }

    private fun dismissEnvProgress() {
        try {
            envProgressDialog?.dismiss()
        } catch (_: Exception) {
        }
        envProgressDialog = null
    }

    // ============================================================
    // 插件说明弹窗
    // ============================================================

    private fun showPluginNoticeIfNeeded() {
        val info = pluginInfo ?: return
        if (!info.hasNotice()) return
        if (pluginManager.isPluginNoticeIgnored(currentPluginId)) return
        enqueuePluginDialog(
            PluginDialogRequest.Notice(
                name = info.name,
                notice = info.notice,
                onAck = { pluginManager.setPluginNoticeIgnored(currentPluginId, true) },
                onNever = { pluginManager.setPluginNoticeIgnored(currentPluginId, true) },
                onLater = {}
            )
        )
    }

    // ============================================================
    // 统一对话框宿主（使用 ui/components/unified/UnifiedDialogs.kt）
    // ============================================================

    private sealed interface PluginDialogRequest {
        data class Notice(
            val name: String,
            val notice: String,
            val onAck: () -> Unit,
            val onNever: () -> Unit,
            val onLater: () -> Unit
        ) : PluginDialogRequest

        data class Info(
            val title: String,
            val message: String,
            val onDismiss: (() -> Unit)?
        ) : PluginDialogRequest

        data class Confirm(
            val title: String,
            val message: String,
            val confirmText: String,
            val dismissText: String,
            val isDestructive: Boolean,
            val onConfirm: () -> Unit,
            val onDismiss: () -> Unit
        ) : PluginDialogRequest

        data class Choice(
            val title: String,
            val message: String,
            val onConfirm: () -> Unit,
            val onDismiss: () -> Unit,
            val onNeutral: () -> Unit
        ) : PluginDialogRequest

        /**
         * 权限门弹窗：加载插件前提示所需权限。
         * - 原生插件：确认 / 不再显示；
         * - web 插件：确认 / 管理权限 / 不再提示。
         */
        data class Permission(
            val message: String,
            val showManage: Boolean,
            val onConfirm: () -> Unit,
            val onManage: () -> Unit,
            val onNever: () -> Unit
        ) : PluginDialogRequest

        data class Prompt(
            val title: String,
            val hint: String,
            val onConfirm: (String) -> Unit,
            val onDismiss: () -> Unit
        ) : PluginDialogRequest
    }

    fun showPluginInfoDialog(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        Logger.d(TAG, "📩 showPluginInfoDialog: $title")
        enqueuePluginDialog(PluginDialogRequest.Info(title, message, onDismiss))
    }

    // ============================================================
    // 实体 Termux 引导（allow-external-apps / bootstrap / 存储授权）
    // ============================================================

    /**
     * 后端启动失败时检测实体 Termux 是否未就绪；若是，弹出可复制的配置引导。
     */
    private fun maybeShowRealTermuxGuidance() {
        if (!BackendConfig.isRealTermux(this)) return
        Thread {
            val probe = RealTermuxRuntime.probe(this)
            if (probe.ok) return@Thread
            if (probe.requiresRunCommandPermission) {
                // 本应用未获得 com.termux.permission.RUN_COMMAND：
                // 打开本应用 App Info 权限页并弹窗说明。
                runOnUiThread {
                    openAppDetailsSettings()
                    showPluginInfoDialog(
                        Str.get(R.string.real_termux_not_ready),
                        Str.get(R.string.real_termux_run_command_permission_grant_guide)
                    )
                }
                return@Thread
            }
            val code = BackendConfig.buildRealTermuxSetupCode(this)
            copyTextToClipboard(code)
            runOnUiThread {
                showPluginInfoDialog(
                    Str.get(R.string.real_termux_not_ready),
                    probe.error + "\n\n" +
                        Str.get(R.string.setup_code_copied_to_clipboard) + "\n\n" + code
                )
            }
        }.start()
    }

    /** 打开本应用 App Info 页面（权限入口）。 */
    private fun openAppDetailsSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
        }
    }

    private fun copyTextToClipboard(text: String) {
        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UIN_Tool", text))
        } catch (_: Exception) {
        }
    }

    fun showPluginConfirmDialog(
        title: String,
        message: String,
        confirmText: String = Str.get(R.string.ok_2),
        dismissText: String = Str.get(R.string.cancel),
        isDestructive: Boolean = false,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Logger.d(TAG, "📩 showPluginConfirmDialog: $title")
        enqueuePluginDialog(
            PluginDialogRequest.Confirm(
                title, message, confirmText, dismissText, isDestructive, onConfirm, onDismiss
            )
        )
    }

    fun showPluginChoiceDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
        onNeutral: () -> Unit
    ) {
        Logger.d(TAG, "📩 showPluginChoiceDialog: $title")
        enqueuePluginDialog(
            PluginDialogRequest.Choice(title, message, onConfirm, onDismiss, onNeutral)
        )
    }

    fun showPluginPromptDialog(
        title: String,
        hint: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        Logger.d(TAG, "📩 showPluginPromptDialog: $title")
        enqueuePluginDialog(
            PluginDialogRequest.Prompt(title, hint, onConfirm, onDismiss)
        )
    }

    private fun enqueuePluginDialog(request: PluginDialogRequest) {
        runOnUiThread {
            val wasEmpty = pluginDialogQueue.isEmpty()
            pluginDialogQueue.addLast(request)
            if (wasEmpty) {
                dialogHost?.visibility = View.VISIBLE
                pluginDialogRequest = request
            }
        }
    }

    @Composable
    private fun PluginDialogHost() {
        val request = pluginDialogRequest
        LaunchedEffect(request) {
            if (request != null) {
                dialogHost?.visibility = View.VISIBLE
                Logger.d(TAG, Str.get(R.string.rendering_plugin_dialog_request_java, request.javaClass.simpleName))
            }
        }
        when (val r = request) {
            null -> Unit

            is PluginDialogRequest.Notice -> UnifiedDialog(
                onDismissRequest = {
                    dismissPluginDialog()
                    r.onLater()
                },
                title = r.name,
                content = {
                    Text(
                        text = r.notice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dismissPluginDialog()
                            r.onAck()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppDimens.radiusLarge)
                    ) { Text(Str.get(R.string.got_it)) }
                },
                dismissButton = {
                    Row {
                        UnifiedDialogTextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onNever()
                            }
                        ) { Text(Str.get(R.string.don_t_ask_again)) }
                        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                        UnifiedDialogTextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onLater()
                            }
                        ) { Text(Str.get(R.string.remind_me_later)) }
                    }
                }
            )

            is PluginDialogRequest.Info -> UnifiedInfoDialog(
                title = r.title,
                message = r.message,
                buttonText = Str.get(R.string.ok_2),
                onDismiss = {
                    dismissPluginDialog()
                    r.onDismiss?.invoke()
                }
            )

            is PluginDialogRequest.Confirm -> UnifiedConfirmDialog(
                title = r.title,
                message = r.message,
                confirmText = r.confirmText,
                dismissText = r.dismissText,
                isDestructive = r.isDestructive,
                onConfirm = {
                    dismissPluginDialog()
                    r.onConfirm()
                },
                onDismiss = {
                    dismissPluginDialog()
                    r.onDismiss()
                }
            )

            is PluginDialogRequest.Choice -> UnifiedDialog(
                onDismissRequest = {
                    dismissPluginDialog()
                    r.onDismiss()
                },
                title = r.title,
                content = {
                    Text(
                        text = r.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dismissPluginDialog()
                            r.onConfirm()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppDimens.radiusLarge)
                    ) { Text(Str.get(R.string.ok_2)) }
                },
                dismissButton = {
                    Row {
                        UnifiedDialogTextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onNeutral()
                            }
                        ) { Text(Str.get(R.string.ignore)) }
                        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                        UnifiedDialogTextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onDismiss()
                            }
                        ) { Text(Str.get(R.string.cancel)) }
                    }
                }
            )

            is PluginDialogRequest.Permission -> UnifiedDialog(
                onDismissRequest = {
                    dismissPluginDialog()
                    r.onConfirm()
                },
                title = Str.get(R.string.permission_gate_title),
                content = {
                    Text(
                        text = r.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dismissPluginDialog()
                            r.onConfirm()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppDimens.radiusLarge)
                    ) { Text(Str.get(R.string.ok_2)) }
                },
                dismissButton = {
                    Row {
                        if (r.showManage) {
                            UnifiedDialogTextButton(
                                onClick = {
                                    dismissPluginDialog()
                                    r.onManage()
                                }
                            ) { Text(Str.get(R.string.permission_gate_manage)) }
                            Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                        }
                        UnifiedDialogTextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onNever()
                            }
                        ) { Text(if (r.showManage) Str.get(R.string.don_t_ask_again) else Str.get(R.string.permission_gate_never_show)) }
                    }
                }
            )

            is PluginDialogRequest.Prompt -> {
                var value by remember { mutableStateOf("") }
                UnifiedDialog(
                    onDismissRequest = {
                        dismissPluginDialog()
                        r.onDismiss()
                    },
                    title = r.title,
                    content = {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            placeholder = { Text(r.hint) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimens.inputCornerRadius),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                dismissPluginDialog()
                                r.onConfirm(value)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(AppDimens.radiusLarge)
                        ) { Text(Str.get(R.string.ok_2)) }
                    },
                    dismissButton = {
                        UnifiedDialogTextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onDismiss()
                            }
                        ) { Text(Str.get(R.string.cancel)) }
                    }
                )
            }
        }
    }

    private fun dismissPluginDialog() {
        if (pluginDialogQueue.isNotEmpty()) {
            pluginDialogQueue.removeFirst()
        }
        if (pluginDialogQueue.isNotEmpty()) {
            pluginDialogRequest = pluginDialogQueue.first()
            dialogHost?.visibility = View.VISIBLE
            Logger.d(TAG, Str.get(R.string.showing_next_plugin_dialog_plugindia, pluginDialogQueue.first().javaClass.simpleName))
        } else {
            pluginDialogRequest = null
            dialogHost?.visibility = View.GONE
            Logger.d(TAG, Str.get(R.string.plugin_dialog_closed_overlay_hidden))
        }
    }

    // ============================================================
    // 插件加载
    // ============================================================

    private fun loadPlugin() {
        val info = pluginInfo ?: run {
            Logger.i(TAG, "loadPlugin: pluginInfo is null!")
            finish()
            return
        }

        Logger.separator(TAG, "loadPlugin")
        Logger.i(TAG, "📌 uiType: '${info.uiType}'")
        Logger.i(TAG, "📌 entry: '${info.entry}'")
        Logger.separator(TAG)

        if (info.uiType.equals("web", ignoreCase = true)) {
            Logger.success(TAG, Str.get(R.string.going_web_plugin_branch))
            loadWebPlugin()
        } else if (info.isCui()) {
            Logger.success(TAG, Str.get(R.string.going_cui_plugin_branch_embedded_ter))
            loadCuiPlugin()
        } else {
            Logger.i(TAG, Str.get(R.string.going_native_plugin_branch_uitype_in, info.uiType))
            loadNativePlugin()
        }
    }

    /**
     * 创建统一对话框宿主（Compose 覆盖层）并加入 container。
     * 在 onCreate 即创建，使权限门（加载前弹窗）也能使用宿主统一风格；
     * 插件加载流程中若已存在则复用，避免重复创建。
     */
    private fun setupDialogHost() {
        if (dialogHost != null) return
        dialogHost = ComposeView(this).apply {
            setContent {
                UINToolTheme(fillBackground = false) {
                    PluginDialogHost()
                }
            }
        }
        container.addView(
            dialogHost,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        dialogHost?.visibility = View.GONE
    }

    private fun loadWebPlugin() {
        Logger.separator(TAG, "loadWebPlugin")

        val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
        Logger.i(TAG, Str.get(R.string.plugin_dir_plugindir_absolutepath, pluginDir.absolutePath))
        Logger.i(TAG, Str.get(R.string.dir_exists_plugindir_exists, pluginDir.exists()))

        if (!pluginDir.exists()) {
            Logger.e(TAG, Str.get(R.string.plugin_dir_not_found_plugindir_absol, pluginDir.absolutePath))
            Toast.makeText(this, Str.get(R.string.plugin_dir_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        container.removeAllViews()

        // 统一对话框宿主（Compose 覆盖层，置于 WebView 之上；自身透明且不拦截触摸，
        // 弹窗通过独立窗口浮于最上）。fillBackground=false 使覆盖层不再绘制不透明背景，
        // 否则会盖住插件 WebView 内容（背景只剩纯色而无控件）。
        // 已在 onCreate 创建则复用（权限门弹窗依赖它），无需重建。
        if (dialogHost == null) {
            setupDialogHost()
        }

        // 共享端口模式 + 保留会话：复用上次关闭时保留的 WebView（页面/会话不重建）
        val retained = if (shouldRetainSharedSession()) PluginManager.takeRetainedSharedWebView(currentPluginId) else null
        if (retained != null) {
            Logger.success(TAG, Str.get(R.string.retained_session_webview_reused))
            (retained.parent as? ViewGroup)?.removeView(retained)
            webView = retained
            bindWebViewClients(retained)
            injectJSInterface()
            injectOpenData()
            if (isBackendReady) sendBackendReadyToWebView(backendPort)
            Logger.success(TAG, Str.get(R.string.retained_session_rebound_clients))
        } else {
            if (shouldRetainSharedSession() && PluginManager.hasRetainedSharedWebView(currentPluginId)) {
                Logger.w(TAG, Str.get(R.string.retained_session_webview_missing_create_new))
            }
            val wv = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.defaultTextEncodingName = "UTF-8"
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
            }
            webView = wv

            val jsInterface = PluginJSInterface(this@PluginHostActivity, currentPluginId, pluginInfo!!)
            injectedJsInterface = jsInterface
            wv.addJavascriptInterface(jsInterface, "UINPlugin")
            Logger.success(TAG, Str.get(R.string.uinplugin_js_interface_injected))

            bindWebViewClients(wv)

            val entryPath = if (pluginInfo!!.entry.isNotEmpty() && isSafePluginRelativePath(pluginInfo!!.entry)) pluginInfo!!.entry else "web/index.html"
            val indexPath = "$pluginDir/$entryPath"
            Logger.i(TAG, Str.get(R.string.entry_path_indexpath, indexPath))
            Logger.i(TAG, Str.get(R.string.file_exists_file_indexpath_exists, File(indexPath).exists()))

            if (File(indexPath).exists()) {
                wv.loadUrl("file://$indexPath")
                Logger.success(TAG, Str.get(R.string.loading_web_plugin_indexpath_2, indexPath))
            } else {
                val defaultHtml = createDefaultHtml(pluginInfo!!)
                wv.loadDataWithBaseURL("file://$pluginDir/", defaultHtml, "text/html", "UTF-8", null)
                Logger.w(TAG, Str.get(R.string.entry_file_not_found_using_default_p_2))
            }
        }

        container.addView(webView)
        dialogHost?.let { host ->
            container.addView(
                host,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            host.visibility = if (pluginDialogRequest != null) View.VISIBLE else View.GONE
            Logger.success(TAG, Str.get(R.string.compose_dialog_overlay_added_above_w) + (if (pluginDialogRequest != null) Str.get(R.string.show) else Str.get(R.string.hide)) + ")")
        }
        PluginManager.putPluginWebView(currentInstanceKey, webView)
        Logger.separator(TAG, Str.get(R.string.loadwebplugin_complete))
    }

    /**
     * 共享端口模式 + 保留会话开关 + Web 插件时启用会话保留：
     * 关闭插件时 WebView 不销毁，重开时复用页面/会话状态。
     */
    private fun shouldRetainSharedSession(): Boolean =
        pluginManager.isSharedSessionRetainEnabled() &&
            pluginInfo?.isWebPlugin() == true

    /** 绑定 WebViewClient / WebChromeClient（绑定当前 Activity，供新建与复用共用） */
    private fun bindWebViewClients(webView: WebView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (isAllowedPluginUrl(url)) return false
                // 插件自身内容以外的链接（http/https/mailto 等）交给系统浏览器，避免 JS 桥外泄
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Logger.success(TAG, Str.get(R.string.webview_load_complete_url, url))
                if (url == null || isPluginOwnOrigin(url)) {
                    injectJSInterface()
                    injectOpenData()
                }
                if (com.UIN.Tool.utils.UIConfig.isInitialized()) {
                    view?.evaluateJavascript(com.UIN.Tool.utils.UIConfig.getThemeCssInjectionScript(), null)
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isBackendReady) {
                        sendBackendReadyToWebView(backendPort)
                    }
                    webView?.evaluateJavascript(
                        "if (window._onUINPluginReady) window._onUINPluginReady();",
                        null
                    )
                }, 300)
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Logger.e(TAG, Str.get(R.string.webview_load_error_description_code_, description, errorCode, failingUrl))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                Logger.d("WebView", "Console: ${consoleMessage.message()}")
                return true
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                showPluginInfoDialog(
                    title = Str.get(R.string.notice),
                    message = message ?: "",
                    onDismiss = { result?.confirm() }
                )
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                showPluginConfirmDialog(
                    title = Str.get(R.string.confirm_2),
                    message = message ?: "",
                    onConfirm = { result?.confirm() },
                    onDismiss = { result?.cancel() }
                )
                return true
            }
        }
    }

    private fun loadNativePlugin() {
        Logger.separator(TAG, "loadNativePlugin")
        Logger.w(TAG, Str.get(R.string.loading_native_plugin_currentplugini, currentPluginId))

        try {
            // 开发者选项开启原生多开时每次新建实例；否则复用（单实例）
            val forceNewInstance = pluginManager.isNativeMultiInstanceEnabled()
            val view = pluginManager.getPluginViewSync(currentPluginId, this, container, forceNewInstance, currentInstanceKey)
            if (view != null) {
                container.removeAllViews()
                container.addView(view)
                Logger.success(TAG, Str.get(R.string.native_plugin_loaded_plugininfo_name, pluginInfo?.name))

                // 通知原生插件宿主事件：传入实例 ID（多开隔离）与外部打开数据
                val pluginInstance = pluginManager.getPluginInstanceByKey(currentInstanceKey)
                if (pluginInstance != null && pluginImplementsMethod(pluginInstance, "onHostEvent")) {
                    pluginInstance.onHostEvent(
                        "host.open",
                        android.os.Bundle().apply {
                            putString("instanceId", currentInstanceId)
                            putString("openDataJson", openDataJson)
                            putBoolean("multiInstanceEnabled", forceNewInstance)
                        }
                    )
                }
            } else {
                Logger.e(TAG, Str.get(R.string.native_plugin_load_failed))
                Toast.makeText(this, Str.get(R.string.native_plugin_load_failed_2), Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.native_plugin_load_error), e)
            Toast.makeText(this, Str.get(R.string.plugin_load_error_e_message, e.message), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * 反射判断插件实现类是否真正提供了指定接口方法。
     *
     * 兼容旧版编译的插件 dex：它们按早期 PluginInterface 编译，接口中可能没有
     * onHostEvent 等新增默认方法，宿主直接调用会抛 AbstractMethodError。
     * 若插件类未实现该方法（新方法由宿主接口默认实现兜底），则跳过调用，
     * 避免旧插件打开即崩溃。
     */
    private fun pluginImplementsMethod(pluginInstance: Any, methodName: String): Boolean {
        return try {
            val declared = pluginInstance.javaClass.declaredMethods
            declared.any { it.name == methodName }
        } catch (e: Exception) {
            Logger.w(TAG, Str.get(R.string.failed_to_reflect_plugin_method_m_n, methodName, e.message))
            false
        }
    }

    /**
     * CUI 插件：加载占位视图。实际终端会话通过 [startCuiTerminalSession] 在独立
     * TermuxActivity 中打开（页面内嵌终端易出渲染问题，故使用真实全屏终端）。
     */
    private fun loadCuiPlugin() {
        Logger.separator(TAG, "loadCuiPlugin")
        try {
            container.removeAllViews()
            val textView = android.widget.TextView(this).apply {
                text = Str.get(R.string.opening_terminal_and_running_command)
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.BLACK)
                textSize = 16f
            }
            container.addView(
                textView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            Logger.success(TAG, Str.get(R.string.cui_placeholder_view_created))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.cui_placeholder_view_creation_error), e)
            Toast.makeText(this, Str.get(R.string.cui_plugin_load_failed_e_message, e.message), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * CUI 环境流水线：
     * - 内置 Termux：确保 Termux 环境（需要时再确保 Alpine），然后启动全屏终端会话；
     * - 实体 Termux：直接按后端设置（Termux 本机 / Proot 容器）启动 CUI 会话；
     * 若插件还声明了后端，再后台启动后端。
     */
    private fun runCuiPipeline() {
        val info = pluginInfo ?: return
        Logger.i(TAG, "🔍 runCuiPipeline(), backend=${BackendConfig.getImplementation(this)}")

        if (BackendConfig.isRealTermux(this)) {
            if (startCuiTerminalSession()) {
                if (info.hasBackend()) {
                    startBackendAfterEnv()
                }
                finish()
            }
            return
        }

        ProotContainerManager.ensureTermux(this, status = { showEnvProgress(it) }) {
            val start = {
                startCuiTerminalSession()
                if (info.hasBackend()) {
                    startBackendAfterEnv()
                }
                // 纯 CUI：终端会话已启动（全屏 TermuxActivity），关闭占位宿主页面，
                // 避免残留一个看似“悬浮窗/多余窗口”的空白页。
                finish()
            }
            if (info.useProotRuntime() || info.isOtherBackend()) {
                ProotContainerManager.ensureAlpine(applicationContext, status = { showEnvProgress(it) }) { ok ->
                    if (!ok) {
                        isBackendStarting = false
                        dismissEnvProgress()
                        showPluginInfoDialog(
                            Str.get(R.string.environment_setup_failed),
                            Str.get(R.string.alpine_container_install_failed_make)
                        )
                        return@ensureAlpine
                    }
                    start()
                }
            } else {
                start()
            }
        }
    }

    /**
     * 根据全局后端设置启动 CUI 会话：
     * - 内置 Termux：通过 RunCommandService 以 TERMINAL_SESSION 方式启动，前台拉起全屏 TermuxActivity；
     * - 实体 Termux：通过 com.termux RUN_COMMAND 启动前台终端会话。
     * @return 是否成功启动
     */
    private fun startCuiTerminalSession(): Boolean {
        try {
            val info = pluginInfo ?: return false
            val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
            val preCmd = info.backendPreCommand.trim()
            Logger.i(TAG, "🔍 startCuiTerminalSession(), preCmd='$preCmd'")

            if (BackendConfig.isRealTermux(this)) {
                return startCuiInRealTermux(info, pluginDir, preCmd)
            }

            val args = if (preCmd.isNotEmpty()) arrayOf("-lc", preCmd) else arrayOf("-l")
            val intent = Intent(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND).apply {
                setClass(this@PluginHostActivity, RunCommandService::class.java)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, ProotContainerManager.BASH)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, args)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_WORKDIR, pluginDir.absolutePath)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_SHELL_NAME, info.name)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, Str.get(R.string.cui_plugin_terminal))
                // 不设置 EXTRA_BACKGROUND → Runner.TERMINAL_SESSION → 创建真实终端会话并执行命令。
                // 用 DONT_OPEN_ACTIVITY 让服务端只建会话、不开 Activity，避免触发
                // “显示在其他应用上层”权限检查；随后由前台 Activity 直接拉起全屏终端。
                // 注意：RunCommandService 用 getStringExtra 读取该值，必须传字符串。
                putExtra(
                    RUN_COMMAND_SERVICE.EXTRA_SESSION_ACTION,
                    TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY.toString()
                )
            }
            startService(intent)
            // 前台 Activity 直接启动全屏 TermuxActivity（不依赖“显示在其他应用上层”权限）。
            // 会话由上面 RUN_COMMAND 创建，TermuxActivity 绑定后会自动附着到该会话。
            startActivity(TermuxActivity.newInstance(this))
            // 仅对进入的终端做淡入：宿主保持不透明直至被覆盖，避免交叉淡入淡出时
            // 双方同时半透明、露出系统桌面/上一页的“空档期”。
            overridePendingTransition(R.anim.fade_in, 0)
            Logger.success(TAG, Str.get(R.string.cui_terminal_session_started))
            return true
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.cui_terminal_start_error_e_message, e.message), e)
            Toast.makeText(this, Str.get(R.string.cui_terminal_start_failed_e_message, e.message), Toast.LENGTH_LONG).show()
            return false
        }
    }

    /**
     * 实体 Termux 的 CUI 会话：根据后端设置的环境（Termux 本机 / Proot 容器）
     * 以前台终端会话方式发送 RUN_COMMAND，由 com.termux 打开全屏终端。
     */
    private fun startCuiInRealTermux(info: PluginInfo, pluginDir: File, preCmd: String): Boolean {
        if (!RealTermuxRuntime.isRunCommandPermissionGranted(this)) {
            Toast.makeText(
                this,
                Str.get(R.string.real_termux_run_command_permission_denied),
                Toast.LENGTH_LONG
            ).show()
            maybeShowRealTermuxGuidance()
            return false
        }

        val isProot = BackendConfig.getEnvironment(this) == BackendConfig.ENV_PROOT
        val intent = if (isProot) {
            val container = BackendConfig.getContainer(this)
            val bind = "${pluginDir.absolutePath}:/plugins/${info.pluginId}"
            // proot login 会把 cwd 重置为容器内 HOME（/root），相对路径脚本会解析失败，
            // 因此先 cd 到绑定到容器内的插件目录 /plugins/<id> 再执行。
            val inner = if (preCmd.isNotEmpty()) "cd /plugins/${info.pluginId} && $preCmd" else ""
            RealTermuxRuntime.buildRunCommandIntent(
                commandPath = RealTermuxRuntime.PROOT_DISTRO_PATH,
                arguments = buildList {
                    add("login"); add(container)
                    add("--bind"); add(bind)
                    add("--"); add("sh")
                    if (inner.isNotEmpty()) {
                        add("-lc"); add(inner)
                    } else {
                        add("-l")
                    }
                }.toTypedArray(),
                workDir = pluginDir.absolutePath,
                shellName = info.name,
                commandLabel = Str.get(R.string.cui_plugin_terminal),
                background = false,
                sessionAction = RealTermuxRuntime.SESSION_ACTION_DONT_OPEN_ACTIVITY
            )
        } else {
            RealTermuxRuntime.buildRunCommandIntent(
                commandPath = RealTermuxRuntime.BASH_PATH,
                arguments = if (preCmd.isNotEmpty()) arrayOf("-lc", preCmd) else arrayOf("-l"),
                workDir = pluginDir.absolutePath,
                shellName = info.name,
                commandLabel = Str.get(R.string.cui_plugin_terminal),
                background = false,
                sessionAction = RealTermuxRuntime.SESSION_ACTION_DONT_OPEN_ACTIVITY
            )
        }

        if (!RealTermuxRuntime.startRunCommand(this, intent)) {
            Toast.makeText(
                this,
                Str.get(R.string.real_termux_run_command_permission_denied),
                Toast.LENGTH_LONG
            ).show()
            maybeShowRealTermuxGuidance()
            return false
        }
        // 前台 Activity 直接拉起 com.termux 的全屏终端（不依赖其 Draw Over Apps 自动弹出）。
        // 会话由上面 RUN_COMMAND 创建，com.termux 的 TermuxActivity 绑定后会附着到该会话。
        startActivity(RealTermuxRuntime.termuxActivityIntent())
        // 仅淡入新终端，宿主保持不透明，避免交叉淡入淡出露桌面。
        overridePendingTransition(R.anim.fade_in, 0)
        Logger.success(TAG, Str.get(R.string.cui_terminal_session_started))
        return true
    }

    // ============================================================
    // JS 接口
    // ============================================================

    /** 插件自身目录的 file:// 前缀（用于 origin 校验） */
    private fun pluginFileRoot(): String {
        val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
        return "file://${pluginDir.absolutePath}/"
    }

    /** 当前页面是否属于插件自身 file:// 目录 */
    private fun isPluginOwnOrigin(url: String?): Boolean {
        if (url == null) return false
        if (url.startsWith("file://")) {
            return url.startsWith(pluginFileRoot())
        }
        // loadDataWithBaseURL 注入的数据 URL 视作插件自身内容
        return url.startsWith("data:") || url.startsWith("about:blank")
    }

    /** 是否允许在 WebView 内加载（仅插件自身 file:// 内容；其余一律交由外部打开） */
    private fun isAllowedPluginUrl(url: String?): Boolean {
        if (url == null) return false
        if (isPluginOwnOrigin(url)) return true
        // 插件调用 loadUrl 的自身文件（含子资源）放行；其它 scheme 全部拦截
        return false
    }

    /** 校验插件的相对路径（entry/icon 等）不会越出插件目录 */
    private fun isSafePluginRelativePath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains("..")) return false
        if (path.matches(Regex("[A-Za-z]:.*"))) return false
        return true
    }

    private fun injectJSInterface() {
        webView?.let {
            it.removeJavascriptInterface("UINPlugin")
            val jsInterface = PluginJSInterface(this@PluginHostActivity, currentPluginId, pluginInfo!!)
            injectedJsInterface = jsInterface
            it.addJavascriptInterface(jsInterface, "UINPlugin")
            Logger.success(TAG, Str.get(R.string.uinplugin_js_interface_re_injected))
        }
    }

    /**
     * 向 WebView 注入「外部打开数据」（系统「用…打开」传入的文件/文本/URL）。
     * 宿主通过 JS 提供 window.UINOpenData / window.getOpenData() 供插件读取。
     */
    private fun injectOpenData() {
        val data = openDataJson
        if (data == null || data.isEmpty()) {
            // 无数据时也提供空实现，避免插件未做空值判断报错
            webView?.evaluateJavascript(
                "window.getOpenData = window.getOpenData || function(){ return '{}' }; window.UINOpenData = window.UINOpenData || '{}';",
                null
            )
            return
        }
        // 使用 JSONObject.quote 生成合法的 JS 字符串字面量（处理 \、引号、\n、\r 等）。
        // 旧版 Android 的 org.json 不转义 U+2028/U+2029（合法但会让 JS 语法出错），手动补上。
        val json = org.json.JSONObject.quote(data)
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        webView?.evaluateJavascript(
            "window.getOpenData = window.getOpenData || function(){ return $json; }; window.UINOpenData = window.UINOpenData || $json;",
            null
        )
    }

    /** 获取外部打开数据 JSON（WebView 插件可通过 UINPlugin.getOpenData() 读取） */
    fun getOpenDataJson(): String? = openDataJson

    fun evaluateJavascript(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    fun setPluginTitle(title: String) {
        runOnUiThread {
            supportActionBar?.title = title
        }
    }

    // ============================================================
    // HTTP API 调用
    // ============================================================

    fun callBackendApi(
        path: String,
        method: String = "GET",
        body: String? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!isBackendReady) {
            callback(false, Str.get(R.string.backend_not_ready))
            return
        }

        // WebView 直连请求也刷新空闲回收计时
        PluginBackendManager.markActivity(backendKey)

        Thread {
            try {
                val url = "http://127.0.0.1:$backendPort$path"
                val builder = Request.Builder().url(url)

                when (method.uppercase()) {
                    "GET" -> builder.get()
                    "POST" -> {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = (body ?: "{}").toRequestBody(mediaType)
                        builder.post(requestBody)
                    }
                    "PUT" -> {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = (body ?: "{}").toRequestBody(mediaType)
                        builder.put(requestBody)
                    }
                    "DELETE" -> builder.delete()
                    else -> builder.get()
                }

                val response = okHttpClient.newCall(builder.build()).execute()
                val responseBody = response.body?.string()
                response.close()

                if (response.isSuccessful) {
                    callback(true, responseBody)
                } else {
                    callback(false, "HTTP ${response.code}: $responseBody")
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.api_call_failed_e_message, e.message), e)
                callback(false, e.message)
            }
        }.start()
    }

    fun getBackendPort(): Int {
        return if (isBackendReady) backendPort else 0
    }

    fun isBackendReady(): Boolean {
        return isBackendReady
    }

    // ============================================================
    // 默认 HTML 页面
    // ============================================================

    private fun createDefaultHtml(info: PluginInfo): String {
        val backendInfo = if (info.hasBackend()) {
            Str.get(R.string.br_backend_info_getbackenddisplaynam, info.getBackendDisplayName())
        } else {
            ""
        }
        val noticeInfo = if (info.hasNotice()) {
            Str.get(R.string.br_includes_documentation)
        } else {
            ""
        }
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${info.name}</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: -apple-system, sans-serif; padding: 20px; text-align: center; background: #f5f5f5; }
                    .card { max-width: 400px; margin: 40px auto; background: white; border-radius: 16px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    button { background: #37474F; color: white; border: none; padding: 12px 24px; border-radius: 8px; margin: 8px; cursor: pointer; font-size: 14px; }
                    button:hover { background: #263238; }
                    .info { background: #f8f9fa; padding: 12px; border-radius: 8px; margin-top: 16px; text-align: left; font-size: 12px; color: #666; }
                    .status-dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; }
                    .online { background: #4CAF50; }
                    .offline { background: #f44336; }
                    .starting { background: #FFC107; animation: blink 1s infinite; }
                    @keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
                </style>
            </head>
            <body>
            <div class="card">
                <h1>${info.name}</h1>
                <p>${info.description ?: Str.get(R.string.web_plugin)}</p>
                <div id="backendStatus" style="margin: 12px 0;">
                    ${Str.get(R.string.backend_colon)} <span class="status-dot offline" id="statusDot"></span>
                    ${Str.get(R.string.checking_status_dots)}
                </div>
                <button onclick="testBackend()">${Str.get(R.string.test_backend)}</button>
                <button onclick="UINPlugin.callHost('finish','')">${Str.get(R.string.close)}</button>
                <div class="info">
                    <strong>${Str.get(R.string.plugin_info)}</strong><br>
                    ${Str.get(R.string.web_plugin_version, info.versionName)}
                    ${Str.get(R.string.web_plugin_author, info.author ?: Str.get(R.string.unknown))}
                    ID: ${info.pluginId}${backendInfo}${noticeInfo}
                </div>
            </div>
            <script>
                var backendReady = false;
                var backendPort = 0;

                window._onBackendReady = function(port) {
                    var dot = document.getElementById('statusDot');
                    var text = document.getElementById('statusText');
                    if (port) {
                        backendReady = true;
                        backendPort = port;
                        dot.className = 'status-dot online';
                        text.textContent = '${Str.get(R.string.connected_port_js)}' + port + ')';
                    } else {
                        dot.className = 'status-dot offline';
                        text.textContent = '${Str.get(R.string.not_connected_js)}';
                    }
                };

                window._onBackendProgress = function(progress, message) {
                    var dot = document.getElementById('statusDot');
                    var text = document.getElementById('statusText');
                    dot.className = 'status-dot starting';
                    text.textContent = message || ('${Str.get(R.string.starting_progress_js)}' + progress + '%');
                };

                function testBackend() {
                    var status = UINPlugin.getBackendStatus();
                    if (status.startsWith('running:')) {
                        alert('${Str.get(R.string.backend_running_port_js)}' + status.split(':')[1]);
                    } else {
                        alert('${Str.get(R.string.backend_not_running_js)}');
                    }
                }

                setTimeout(function() {
                    try {
                        var status = UINPlugin.getBackendStatus();
                        if (status && status.startsWith('running:')) {
                            var port = parseInt(status.split(':')[1]);
                            window._onBackendReady(port);
                        }
                    } catch(e) {}
                }, 500);
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    // ============================================================
    // 生命周期
    // ============================================================

    override fun onResume() {
        super.onResume()
        if (!isDestroyed) {
            pluginManager.onPluginResume(currentPluginId)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isDestroyed) {
            pluginManager.onPluginPause(currentPluginId)
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        super.onDestroy()

        backendTimeoutHandler?.removeCallbacks(backendTimeoutRunnable!!)
        backendTimeoutHandler = null

        dismissEnvProgress()

        // 原生插件单实例追踪注销
        pluginManager.onNativePluginStopped(currentInstanceKey)

        // 保留会话单窗口：注销存活窗口标记
        if (shouldRetainSharedSession()) {
            pluginManager.unregisterLivePluginHost(currentPluginId, this)
        }

        // 保留会话：共享端口模式 + 保留开关时，WebView 移入缓存（重开复用），不销毁。
        // 必须先于后端释放：releaseHold 检测到存在保留 WebView 时不会停掉共享后端。
        val retainSession = shouldRetainSharedSession() && webView != null
        if (retainSession) {
            webView?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                PluginManager.retainSharedWebView(currentPluginId, it)
                Logger.success(TAG, Str.get(R.string.retained_session_webview_cached))
            }
        }

        // 后端生命周期：只在当前实例“持有”时才停止/释放
        val keepAlive = pluginInfo?.backendKeepAlive ?: false
        if (backendHeldByThisInstance && !keepAlive) {
            // 共享端口模式：释放本实例持有（归零时停止共享后端；保留会话时维持存活）
            pluginManager.releaseBackend(currentPluginId)
            backendHeldByThisInstance = false
        }

        if (!retainSession) {
            webView?.let {
                it.loadUrl("about:blank")
                it.clearHistory()
                it.clearCache(true)
                it.destroy()
            }
        }
        webView = null
        PluginManager.removePluginWebView(currentInstanceKey)
        pluginManager.onPluginDestroy(currentInstanceKey)

        // 注销 JS 桥的传感器监听与挂起回调，防止泄漏
        try {
            injectedJsInterface?.destroy()
            injectedJsInterface = null
        } catch (e: Exception) {
            // 忽略
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (pluginManager.onPluginBackPressed(currentInstanceKey)) {
            return
        }

        webView?.let {
            if (it.canGoBack()) {
                it.goBack()
                return
            }
        }
        super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PLUGIN_ID, currentPluginId)
        outState.putString(KEY_INSTANCE_ID, currentInstanceId)
        webView?.saveState(outState.getBundle(KEY_WEBVIEW_STATE) ?: Bundle().also {
            outState.putBundle(KEY_WEBVIEW_STATE, it)
        })
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val bundle = savedInstanceState.getBundle(KEY_WEBVIEW_STATE)
        bundle?.let { webView?.restoreState(it) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 路由到当前实例键对应的原生插件实例（多开隔离）
        val plugin = pluginManager.getPluginInstanceByKey(currentInstanceKey)
        plugin?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 路由到当前实例键对应的原生插件实例（多开隔离）
        val plugin = pluginManager.getPluginInstanceByKey(currentInstanceKey)
        plugin?.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 路由到实际注入的 JS 桥实例（而非新建），确保 WebView 中同一对象收到回调
        try {
            injectedJsInterface?.onRequestPermissionsResult(requestCode, permissions, grantResults)
        } catch (e: Exception) {
            // 忽略
        }
    }
}