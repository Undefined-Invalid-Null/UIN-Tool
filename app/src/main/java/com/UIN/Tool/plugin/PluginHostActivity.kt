package com.UIN.Tool.plugin

import com.UIN.Tool.R
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
        private const val KEY_PLUGIN_ID = "plugin_id"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val BACKEND_START_TIMEOUT = 15000L
        private const val BACKEND_START_TIMEOUT_PROOT = 90000L
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }

    private lateinit var container: FrameLayout
    private var currentPluginId: String = ""
    private var webView: WebView? = null
    private lateinit var pluginManager: PluginManager
    private var pluginInfo: PluginInfo? = null
    private var isDestroyed = false

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

        if (savedInstanceState != null) {
            currentPluginId = savedInstanceState.getString(KEY_PLUGIN_ID) ?: ""
        }
        if (currentPluginId.isEmpty()) {
            currentPluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: ""
        }

        Logger.i(TAG, "📦 currentPluginId: $currentPluginId")

        if (currentPluginId.isEmpty()) {
            Logger.e(TAG, Str.get(R.string.plugin_id_is_empty))
            Toast.makeText(this, Str.get(R.string.plugin_id_must_not_be_empty), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

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

        // 显示插件说明
        showPluginNoticeIfNeeded()

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
            Logger.e(TAG, Str.get(R.string.conditions_not_met_skipping_backend_))
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
        pluginManager.startBackend(currentPluginId) { success, port, error ->
            isBackendStarting = false
            dismissEnvProgress()
            Logger.i(TAG, Str.get(R.string.backend_start_callback_success_succe, success, port, error))
            if (success) {
                backendPort = port
                isBackendReady = true
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
        pluginManager.startBackend(currentPluginId) { success, port, error ->
            isBackendStarting = false
            dismissEnvProgress()
            Logger.i(TAG, Str.get(R.string.env_pipeline_backend_callback_succes, success, port, error))
            if (success) {
                backendPort = port
                isBackendReady = true
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
        dialogHost = ComposeView(this).apply {
            setContent {
                UINToolTheme(fillBackground = false) {
                    PluginDialogHost()
                }
            }
        }

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                defaultTextEncodingName = "UTF-8"
                loadWithOverviewMode = true
                useWideViewPort = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            val jsInterface = PluginJSInterface(this@PluginHostActivity, currentPluginId, pluginInfo!!)
            addJavascriptInterface(jsInterface, "UINPlugin")
            Logger.success(TAG, Str.get(R.string.uinplugin_js_interface_injected))

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Logger.success(TAG, Str.get(R.string.webview_load_complete_url, url))
                    injectJSInterface()
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

            webChromeClient = object : WebChromeClient() {
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

            val entryPath = if (pluginInfo!!.entry.isNotEmpty()) pluginInfo!!.entry else "web/index.html"
            val indexPath = "$pluginDir/$entryPath"
            Logger.i(TAG, Str.get(R.string.entry_path_indexpath, indexPath))
            Logger.i(TAG, Str.get(R.string.file_exists_file_indexpath_exists, File(indexPath).exists()))

            if (File(indexPath).exists()) {
                loadUrl("file://$indexPath")
                Logger.success(TAG, Str.get(R.string.loading_web_plugin_indexpath_2, indexPath))
            } else {
                val defaultHtml = createDefaultHtml(pluginInfo!!)
                loadDataWithBaseURL("file://$pluginDir/", defaultHtml, "text/html", "UTF-8", null)
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
        PluginManager.putPluginWebView(currentPluginId, webView)
        Logger.separator(TAG, Str.get(R.string.loadwebplugin_complete))
    }

    private fun loadNativePlugin() {
        Logger.separator(TAG, "loadNativePlugin")
        Logger.w(TAG, Str.get(R.string.loading_native_plugin_currentplugini, currentPluginId))

        try {
            val view = pluginManager.getPluginViewSync(currentPluginId, this, container)
            if (view != null) {
                container.removeAllViews()
                container.addView(view)
                Logger.success(TAG, Str.get(R.string.native_plugin_loaded_plugininfo_name, pluginInfo?.name))
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
            RealTermuxRuntime.buildRunCommandIntent(
                commandPath = RealTermuxRuntime.PROOT_DISTRO_PATH,
                arguments = buildList {
                    add("login"); add(container)
                    add("--bind"); add(bind)
                    add("--"); add("sh")
                    if (preCmd.isNotEmpty()) {
                        add("-lc"); add(preCmd)
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

    private fun injectJSInterface() {
        webView?.let {
            it.removeJavascriptInterface("UINPlugin")
            val jsInterface = PluginJSInterface(this@PluginHostActivity, currentPluginId, pluginInfo!!)
            it.addJavascriptInterface(jsInterface, "UINPlugin")
            Logger.success(TAG, Str.get(R.string.uinplugin_js_interface_re_injected))
        }
    }

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
        PluginBackendManager.markActivity(currentPluginId)

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

        val keepAlive = pluginInfo?.backendKeepAlive ?: false
        if (!keepAlive && isBackendReady) {
            pluginManager.stopBackend(currentPluginId)
        }

        webView?.let {
            it.loadUrl("about:blank")
            it.clearHistory()
            it.clearCache(true)
            it.destroy()
        }
        webView = null
        PluginManager.removePluginWebView(currentPluginId)
        pluginManager.onPluginDestroy(currentPluginId)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (pluginManager.onPluginBackPressed(currentPluginId)) {
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
        val plugin = pluginManager.getPluginInstance(currentPluginId)
        plugin?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val plugin = pluginManager.getPluginInstance(currentPluginId)
        plugin?.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            val jsInterface = PluginJSInterface(this, currentPluginId, pluginInfo!!)
            jsInterface.onRequestPermissionsResult(requestCode, permissions, grantResults)
        } catch (e: Exception) {
            // 忽略
        }
    }
}