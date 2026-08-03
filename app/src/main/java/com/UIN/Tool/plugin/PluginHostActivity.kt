package com.UIN.Tool.plugin

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedInfoDialog
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Constants
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

        // ===== 启动前命令（pre-command）相关 =====
        const val KEY_PRE_CMD_DONE = "pre_cmd_done"
        const val EXTRA_PRE_COMMAND_FINISHED = "pre_command_finished"
        const val EXTRA_PRE_COMMAND_SUCCESS = "pre_command_success"
        const val EXTRA_PRE_COMMAND_EXIT_CODE = "pre_command_exit_code"
        const val EXTRA_PRE_COMMAND_ERRMSG = "pre_command_errmsg"
        private const val PRE_COMMAND_REQUEST_CODE = 0x5043
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

        android.util.Log.e(TAG, "========================================")
        android.util.Log.e(TAG, "🚀 PluginHostActivity.onCreate() 被调用")
        android.util.Log.e(TAG, "========================================")

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

        android.util.Log.e(TAG, "📦 currentPluginId: $currentPluginId")

        if (currentPluginId.isEmpty()) {
            android.util.Log.e(TAG, "❌ 插件ID为空")
            Toast.makeText(this, "插件ID不能为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        pluginManager = ServiceLocator.getPluginManager()
        pluginInfo = pluginManager.getPluginInfo(currentPluginId)

        // ========== ✅ 详细日志 ==========
        android.util.Log.e(TAG, "========================================")
        android.util.Log.e(TAG, "📌 pluginInfo == null: ${pluginInfo == null}")
        
        if (pluginInfo != null) {
            android.util.Log.e(TAG, "✅ pluginInfo 存在")
            android.util.Log.e(TAG, "   📌 pluginId: ${pluginInfo!!.pluginId}")
            android.util.Log.e(TAG, "   📌 name: ${pluginInfo!!.name}")
            android.util.Log.e(TAG, "   📌 uiType: '${pluginInfo!!.uiType}'")
            android.util.Log.e(TAG, "   📌 backend: '${pluginInfo!!.backend}'")
            android.util.Log.e(TAG, "   📌 backendAutoStart: ${pluginInfo!!.backendAutoStart}")
            android.util.Log.e(TAG, "   📌 backendEntry: '${pluginInfo!!.backendEntry}'")
            android.util.Log.e(TAG, "   📌 backendPort: ${pluginInfo!!.backendPort}")
            android.util.Log.e(TAG, "   📌 hasBackend(): ${pluginInfo!!.hasBackend()}")
        } else {
            android.util.Log.e(TAG, "❌ pluginInfo 为 null!")
        }
        android.util.Log.e(TAG, "========================================")

        if (pluginInfo == null) {
            android.util.Log.e(TAG, "❌ 插件不存在: $currentPluginId")
            Toast.makeText(this, "插件不存在: $currentPluginId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 显示插件说明
        showPluginNoticeIfNeeded()

        if (pluginInfo?.isCui() == true) {
            // CUI 插件：在真实全屏终端（TermuxActivity）中运行 pre-command
            android.util.Log.e(TAG, "📞 CUI 插件：加载占位视图")
            loadPlugin()
            runCuiPipeline()
        } else {
            // ✅ 调用 startBackendIfNeeded
            android.util.Log.e(TAG, "📞 调用 startBackendIfNeeded()")
            startBackendIfNeeded()

            // 加载插件视图
            loadPlugin()
        }
    }

    // ============================================================
    // ✅ 带详细日志的 startBackendIfNeeded
    // ============================================================

    private fun startBackendIfNeeded() {
        android.util.Log.e(TAG, "========================================")
        android.util.Log.e(TAG, "🔍 startBackendIfNeeded() 被调用")
        android.util.Log.e(TAG, "📌 pluginInfo == null: ${pluginInfo == null}")
        
        if (pluginInfo != null) {
            android.util.Log.e(TAG, "📌 pluginInfo.pluginId: ${pluginInfo!!.pluginId}")
            android.util.Log.e(TAG, "📌 pluginInfo.backend: '${pluginInfo!!.backend}'")
            android.util.Log.e(TAG, "📌 pluginInfo.backendRuntime: '${pluginInfo!!.backendRuntime}'")
            android.util.Log.e(TAG, "📌 pluginInfo.backendAutoStart: ${pluginInfo!!.backendAutoStart}")
            android.util.Log.e(TAG, "📌 pluginInfo.hasBackend(): ${pluginInfo!!.hasBackend()}")
            android.util.Log.e(TAG, "📌 isBackendReady: $isBackendReady")
        } else {
            android.util.Log.e(TAG, "❌ pluginInfo 为 null，无法检查后端")
        }
        android.util.Log.e(TAG, "========================================")
        
        val info = pluginInfo
        if (info == null || !info.hasBackend() || isBackendReady) {
            android.util.Log.e(TAG, "❌ 条件不满足，跳过启动后端")
            if (info == null) android.util.Log.e(TAG, "   -> pluginInfo 为 null")
            if (info != null && !info.hasBackend()) android.util.Log.e(TAG, "   -> hasBackend() 返回 false")
            if (isBackendReady) android.util.Log.e(TAG, "   -> isBackendReady 为 true")
            return
        }

        android.util.Log.e(TAG, "✅ 条件满足，准备启动后端")
        isBackendStarting = true
        setupBackendTimeout()

        if (info.useProotRuntime() || info.isOtherBackend()) {
            // proot 容器运行时 / other 模式：走环境流水线
            android.util.Log.e(TAG, "🔄 走 proot/other 环境流水线")
            runEnvironmentPipeline()
        } else {
            // 常规 termux 运行时：直接启动后端
            android.util.Log.e(TAG, "🔄 走常规后端启动")
            startBackendInternal()
        }
    }

    private fun startBackendInternal() {
        showEnvProgress("正在启动后端服务（常规运行时）...")
        pluginManager.startBackend(currentPluginId) { success, port, error ->
            isBackendStarting = false
            dismissEnvProgress()
            android.util.Log.e(TAG, "📊 后端启动回调: success=$success, port=$port, error=$error")
            if (success) {
                backendPort = port
                isBackendReady = true
                android.util.Log.e(TAG, "✅ 后端就绪，端口: $port")
                sendBackendReadyToWebView(port)
            } else {
                android.util.Log.e(TAG, "❌ 后端启动失败: $error")
            }
        }
    }

    // ============================================================
    // proot/other 环境流水线
    // ============================================================

    /**
     * ① Termux 基础环境 → ② Alpine 共享容器 → ③ pre-command → ④ 启动后端
     */
    private fun runEnvironmentPipeline() {
        val info = pluginInfo ?: return
        android.util.Log.e(TAG, "🔍 runEnvironmentPipeline()")

        ProotContainerManager.ensureTermux(this, status = { showEnvProgress(it) }) {
            android.util.Log.e(TAG, "✅ Termux 环境就绪，检查 Alpine...")
            ProotContainerManager.ensureAlpine(applicationContext, status = { showEnvProgress(it) }) { ok ->
                if (!ok) {
                    android.util.Log.e(TAG, "❌ Alpine 容器准备失败")
                    isBackendStarting = false
                    dismissEnvProgress()
                    showPluginInfoDialog(
                        "环境准备失败",
                        "Alpine 容器安装失败，请确认 APK 的 assets 目录已包含 Alpine 离线安装包（alpine*）后重试。"
                    )
                    return@ensureAlpine
                }
                android.util.Log.e(TAG, "✅ Alpine 容器就绪，处理 pre-command...")
                handlePreCommand()
            }
        }
    }

    private fun handlePreCommand() {
        val info = pluginInfo ?: return
        val preCmd = info.backendPreCommand.trim()
        android.util.Log.e(TAG, "🔍 handlePreCommand(), preCmd='$preCmd', done=${isPreCommandDone()}")

        if (preCmd.isEmpty() || isPreCommandDone()) {
            android.util.Log.e(TAG, "✅ 无需执行 pre-command，直接启动后端")
            startBackendAfterEnv()
            return
        }

        // 进入交互式询问/终端，先收起环境进度弹窗
        dismissEnvProgress()

        enqueuePluginDialog(
            PluginDialogRequest.PreCommand(
                name = info.name,
                command = preCmd,
                onRunNow = { runPreCommand() },
                onLater = { onPreCommandLater() },
                onCancel = { onPreCommandCancelled() }
            )
        )
    }

    private fun onPreCommandLater() {
        // 「稍后」：本次不执行 pre-command，也不标记 done；下次打开会再次询问
        if (pluginInfo?.isOtherBackend() == true) {
            // other 模式后端由 pre-command 启动，未执行则端口不会就绪
            isBackendStarting = false
            Toast.makeText(this, "已稍后处理，后端未启动（需执行启动前命令）", Toast.LENGTH_LONG).show()
        } else {
            startBackendAfterEnv()
        }
    }

    private fun onPreCommandCancelled() {
        // 「取消」：不执行 pre-command，也不自动启动后端
        isBackendStarting = false
        android.util.Log.e(TAG, "❌ 用户取消 pre-command，不启动后端")
    }

    private fun isPreCommandDone(): Boolean {
        val prefs = getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$currentPluginId", MODE_PRIVATE)
        return prefs.getBoolean(KEY_PRE_CMD_DONE, false)
    }

    private fun markPreCommandDone() {
        getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$currentPluginId", MODE_PRIVATE)
            .edit().putBoolean(KEY_PRE_CMD_DONE, true).apply()
        android.util.Log.e(TAG, "✅ pre-command 已标记为完成")
    }

    private fun startBackendAfterEnv() {
        android.util.Log.e(TAG, "🔍 startBackendAfterEnv()")
        val info = pluginInfo
        if (info?.isOtherBackend() == true) {
            showEnvProgress(if (info.backendPort > 0) "正在等待后端服务端口就绪（最长 90 秒）..." else "正在等待启动前命令会话启动后端...")
        } else {
            val prootMsg = if (ProotContainerManager.isAlpineInstalled()) {
                "正在启动容器后端（请稍候）..."
            } else {
                "正在启动容器后端（首次需初始化容器，约需 1-2 分钟，请耐心等待）..."
            }
            showEnvProgress(if (info?.useProotRuntime() == true) prootMsg else "正在启动后端服务...")
        }
        pluginManager.startBackend(currentPluginId) { success, port, error ->
            isBackendStarting = false
            dismissEnvProgress()
            android.util.Log.e(TAG, "📊 环境流水线后端启动回调: success=$success, port=$port, error=$error")
            if (success) {
                backendPort = port
                isBackendReady = true
                sendBackendReadyToWebView(port)
            } else {
                Toast.makeText(this, "后端启动失败: ${error ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 在 Termux 终端会话中执行 pre-command（bash -lc "<command>"）。
     *
     * @param withResult 是否通过 PendingIntent 接收会话结束结果。
     *                   other 模式 pre-command 为长驻服务启动命令，会话不会退出，故不传结果。
     */
    private fun runPreCommand() {
        val info = pluginInfo ?: return
        val preCmd = info.backendPreCommand.trim()
        android.util.Log.e(TAG, "🔍 runPreCommand(), withResult=${!info.isOtherBackend()}")

        if (preCmd.isEmpty()) {
            startBackendAfterEnv()
            return
        }

        // 启动终端会话，先收起环境进度弹窗
        dismissEnvProgress()

        try {
            val intent = Intent(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND).apply {
                setClass(this@PluginHostActivity, RunCommandService::class.java)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, ProotContainerManager.BASH)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, arrayOf("-lc", preCmd))
                putExtra(RUN_COMMAND_SERVICE.EXTRA_WORKDIR, File(Constants.PLUGIN_DIR, info.pluginId).absolutePath)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_SHELL_NAME, info.name)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "启动前命令")
                // 后台执行（APP_SHELL），不弹终端、不用悬浮窗；CUI 插件不走此路径
                putExtra(RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, true)
            }

            if (!info.isOtherBackend()) {
                val pi = PendingIntent.getBroadcast(
                    this,
                    PRE_COMMAND_REQUEST_CODE,
                    Intent(this, PreCommandResultReceiver::class.java)
                        .setAction(PreCommandResultReceiver.ACTION)
                        .putExtra(EXTRA_PLUGIN_ID, info.pluginId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT, pi)
                android.util.Log.e(TAG, "📬 已附加 PendingIntent 结果回调")
            }

            startService(intent)
            Toast.makeText(this, "已在终端执行启动前命令", Toast.LENGTH_SHORT).show()

            if (info.isOtherBackend()) {
                // other 模式：pre-command 即后端服务，会话不退出，改为轮询端口就绪
                android.util.Log.e(TAG, "🔄 other 模式：开始轮询端口就绪")
                startBackendAfterEnv()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ 启动终端失败: ${e.message}", e)
            dismissEnvProgress()
            Toast.makeText(this, "启动终端失败: ${e.message}", Toast.LENGTH_LONG).show()
            isBackendStarting = false
        }
    }

    /**
     * pre-command 会话结束后刷新后端状态（由 PreCommandResultReceiver 唤起）。
     */
    private fun refreshBackendStateAfterPreCommand() {
        android.util.Log.e(TAG, "🔍 refreshBackendStateAfterPreCommand()")
        if (isBackendReady) return
        val port = PluginBackendManager.getPort(currentPluginId)
        if (PluginBackendManager.isRunning(currentPluginId)) {
            backendPort = port
            isBackendReady = true
            isBackendStarting = false
            android.util.Log.e(TAG, "✅ 后端已在运行，端口: $port")
            sendBackendReadyToWebView(port)
        } else {
            android.util.Log.e(TAG, "🔄 后端未运行，重新启动")
            startBackendAfterEnv()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.e(TAG, "🔍 onNewIntent() 被调用")

        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        if (!pluginId.isNullOrEmpty() && pluginId != currentPluginId) {
            currentPluginId = pluginId
            pluginInfo = pluginManager.getPluginInfo(pluginId)
        }

        if (intent.getBooleanExtra(EXTRA_PRE_COMMAND_FINISHED, false)) {
            val success = intent.getBooleanExtra(EXTRA_PRE_COMMAND_SUCCESS, false)
            if (success) {
                markPreCommandDone()
                refreshBackendStateAfterPreCommand()
            } else {
                val exitCode = intent.getIntExtra(EXTRA_PRE_COMMAND_EXIT_CODE, -1)
                val errmsg = intent.getStringExtra(EXTRA_PRE_COMMAND_ERRMSG) ?: ""
                android.util.Log.e(TAG, "❌ pre-command 执行失败: exitCode=$exitCode, errmsg=$errmsg")
                showPluginInfoDialog(
                    "启动前命令执行失败",
                    "退出码: $exitCode${if (errmsg.isNotEmpty()) "\n\n$errmsg" else ""}"
                )
            }
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
                android.util.Log.e(TAG, "⏰ 后端启动超时")
                isBackendStarting = false
            }
        }
        backendTimeoutHandler?.postDelayed(
            backendTimeoutRunnable!!,
            if (pluginInfo?.useProotRuntime() == true || pluginInfo?.isOtherBackend() == true) {
                BACKEND_START_TIMEOUT_PROOT
            } else {
                BACKEND_START_TIMEOUT
            }
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

        data class PreCommand(
            val name: String,
            val command: String,
            val onRunNow: () -> Unit,
            val onLater: () -> Unit,
            val onCancel: () -> Unit
        ) : PluginDialogRequest
    }

    fun showPluginInfoDialog(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        android.util.Log.d(TAG, "📩 showPluginInfoDialog: $title")
        enqueuePluginDialog(PluginDialogRequest.Info(title, message, onDismiss))
    }

    fun showPluginConfirmDialog(
        title: String,
        message: String,
        confirmText: String = "确定",
        dismissText: String = "取消",
        isDestructive: Boolean = false,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        android.util.Log.d(TAG, "📩 showPluginConfirmDialog: $title")
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
        android.util.Log.d(TAG, "📩 showPluginChoiceDialog: $title")
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
        android.util.Log.d(TAG, "📩 showPluginPromptDialog: $title")
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
                android.util.Log.d(TAG, "🖼 渲染插件弹窗: ${request.javaClass.simpleName}")
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
                    ) { Text("知道了") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onNever()
                            }
                        ) { Text("不再提示") }
                        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                        TextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onLater()
                            }
                        ) { Text("稍后提醒") }
                    }
                }
            )

            is PluginDialogRequest.Info -> UnifiedInfoDialog(
                title = r.title,
                message = r.message,
                buttonText = "确定",
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
                    ) { Text("确定") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onNeutral()
                            }
                        ) { Text("忽略") }
                        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                        TextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onDismiss()
                            }
                        ) { Text("取消") }
                    }
                }
            )

            is PluginDialogRequest.PreCommand -> UnifiedDialog(
                onDismissRequest = {
                    dismissPluginDialog()
                    r.onCancel()
                },
                title = "启动前命令",
                content = {
                    Text(
                        text = "插件「${r.name}」需要在容器环境中先执行启动前命令：\n\n${r.command}\n\n是否现在在终端中运行？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dismissPluginDialog()
                            r.onRunNow()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppDimens.radiusLarge)
                    ) { Text("现在运行") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onLater()
                            }
                        ) { Text("稍后") }
                        Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                        TextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onCancel()
                            }
                        ) { Text("取消") }
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
                        ) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                dismissPluginDialog()
                                r.onDismiss()
                            }
                        ) { Text("取消") }
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
            android.util.Log.d(TAG, "🖼 显示下一个插件弹窗: ${pluginDialogQueue.first().javaClass.simpleName}")
        } else {
            pluginDialogRequest = null
            dialogHost?.visibility = View.GONE
            android.util.Log.d(TAG, "🗑 插件弹窗已关闭，覆盖层隐藏")
        }
    }

    // ============================================================
    // 插件加载
    // ============================================================

    private fun loadPlugin() {
        val info = pluginInfo ?: run {
            android.util.Log.e(TAG, "loadPlugin: pluginInfo is null!")
            finish()
            return
        }

        android.util.Log.e(TAG, "========== loadPlugin ==========")
        android.util.Log.e(TAG, "📌 uiType: '${info.uiType}'")
        android.util.Log.e(TAG, "📌 entry: '${info.entry}'")
        android.util.Log.e(TAG, "=================================")

        if (info.uiType.equals("web", ignoreCase = true)) {
            android.util.Log.e(TAG, "✅ 走 Web 插件分支")
            loadWebPlugin()
        } else if (info.isCui()) {
            android.util.Log.e(TAG, "✅ 走 CUI 插件分支（内嵌终端，非悬浮窗）")
            loadCuiPlugin()
        } else {
            android.util.Log.e(TAG, "❌ 走原生插件分支 (uiType = '${info.uiType}')")
            loadNativePlugin()
        }
    }

    private fun loadWebPlugin() {
        android.util.Log.e(TAG, "========== loadWebPlugin ==========")

        val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
        android.util.Log.e(TAG, "📂 插件目录: ${pluginDir.absolutePath}")
        android.util.Log.e(TAG, "📂 目录是否存在: ${pluginDir.exists()}")

        if (!pluginDir.exists()) {
            android.util.Log.e(TAG, "❌ 插件目录不存在: ${pluginDir.absolutePath}")
            Toast.makeText(this, "插件目录不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        container.removeAllViews()

        // 统一对话框宿主（Compose 覆盖层，置于 WebView 之上；自身透明且不拦截触摸，
        // 弹窗通过独立窗口浮于最上）
        dialogHost = ComposeView(this).apply {
            setContent {
                UINToolTheme {
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
            android.util.Log.e(TAG, "✅ UINPlugin JS 接口已注入")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    android.util.Log.e(TAG, "✅ WebView 加载完成: $url")
                    injectJSInterface()
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
                    android.util.Log.e(TAG, "❌ WebView 加载错误: $description (code: $errorCode), url: $failingUrl")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.d("WebView", "Console: ${consoleMessage.message()}")
                    return true
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    showPluginInfoDialog(
                        title = "提示",
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
                        title = "确认",
                        message = message ?: "",
                        onConfirm = { result?.confirm() },
                        onDismiss = { result?.cancel() }
                    )
                    return true
                }
            }

            val entryPath = if (pluginInfo!!.entry.isNotEmpty()) pluginInfo!!.entry else "web/index.html"
            val indexPath = "$pluginDir/$entryPath"
            android.util.Log.e(TAG, "📄 入口路径: $indexPath")
            android.util.Log.e(TAG, "📄 文件是否存在: ${File(indexPath).exists()}")

            if (File(indexPath).exists()) {
                loadUrl("file://$indexPath")
                android.util.Log.e(TAG, "✅ 加载 Web 插件: $indexPath")
            } else {
                val defaultHtml = createDefaultHtml(pluginInfo!!)
                loadDataWithBaseURL("file://$pluginDir/", defaultHtml, "text/html", "UTF-8", null)
                android.util.Log.e(TAG, "⚠️ 入口文件不存在，使用默认页面")
            }
        }

        container.addView(webView)
        dialogHost?.let { host ->
            container.addView(
                host,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            host.visibility = if (pluginDialogRequest != null) View.VISIBLE else View.GONE
            android.util.Log.e(
                TAG,
                "✅ Compose 对话框覆盖层已添加在 WebView 之上（初始" +
                    (if (pluginDialogRequest != null) "显示" else "隐藏") + "）"
            )
        }
        PluginManager.putPluginWebView(currentPluginId, webView)
        android.util.Log.e(TAG, "========== loadWebPlugin 完成 ==========")
    }

    private fun loadNativePlugin() {
        android.util.Log.e(TAG, "========== loadNativePlugin ==========")
        android.util.Log.e(TAG, "⚠️ 正在加载原生插件: $currentPluginId")

        try {
            val view = pluginManager.getPluginViewSync(currentPluginId, this, container)
            if (view != null) {
                container.removeAllViews()
                container.addView(view)
                android.util.Log.e(TAG, "✅ 原生插件加载成功: ${pluginInfo?.name}")
            } else {
                android.util.Log.e(TAG, "❌ 原生插件加载失败")
                Toast.makeText(this, "原生插件加载失败", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ 原生插件加载异常", e)
            Toast.makeText(this, "插件加载异常: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * CUI 插件：加载占位视图。实际终端会话通过 [startCuiTerminalSession] 在独立
     * TermuxActivity 中打开（页面内嵌终端易出渲染问题，故使用真实全屏终端）。
     */
    private fun loadCuiPlugin() {
        android.util.Log.e(TAG, "========== loadCuiPlugin ==========")
        try {
            container.removeAllViews()
            val textView = android.widget.TextView(this).apply {
                text = "正在打开终端并执行命令...\n\n若未自动弹出，请在通知栏查看 UIN Tool 终端。"
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.BLACK)
                textSize = 16f
            }
            container.addView(
                textView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            android.util.Log.e(TAG, "✅ CUI 占位视图已创建")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ CUI 占位视图创建异常", e)
            Toast.makeText(this, "CUI 插件加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * CUI 环境流水线：确保 Termux 环境（proot/other 需要时再确保 Alpine），
     * 然后启动内嵌终端会话；若插件还声明了后端且非 other 模式，再后台启动后端。
     */
    private fun runCuiPipeline() {
        val info = pluginInfo ?: return
        android.util.Log.e(TAG, "🔍 runCuiPipeline()")
        ProotContainerManager.ensureTermux(this, status = { showEnvProgress(it) }) {
            val start = {
                startCuiTerminalSession()
                if (info.hasBackend() && !info.isOtherBackend()) {
                    startBackendAfterEnv()
                }
            }
            if (info.useProotRuntime() || info.isOtherBackend()) {
                ProotContainerManager.ensureAlpine(applicationContext, status = { showEnvProgress(it) }) { ok ->
                    if (!ok) {
                        isBackendStarting = false
                        dismissEnvProgress()
                        showPluginInfoDialog(
                            "环境准备失败",
                            "Alpine 容器安装失败，请确认 APK 的 assets 目录已包含 Alpine 离线安装包（alpine*）后重试。"
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
     * 通过 RunCommandService 以 TERMINAL_SESSION 方式启动 CUI 会话：
     * 在真实全屏终端（TermuxActivity）中运行 pre-command，无 pre-command 时进入插件目录的交互式 bash。
     */
    private fun startCuiTerminalSession() {
        try {
            val info = pluginInfo ?: return
            val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
            val preCmd = info.backendPreCommand.trim()
            val args = if (preCmd.isNotEmpty()) arrayOf("-lc", preCmd) else arrayOf("-l")
            android.util.Log.e(TAG, "🔍 startCuiTerminalSession(), preCmd='$preCmd'")

            val intent = Intent(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND).apply {
                setClass(this@PluginHostActivity, RunCommandService::class.java)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, ProotContainerManager.BASH)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, args)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_WORKDIR, pluginDir.absolutePath)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_SHELL_NAME, info.name)
                putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "CUI 插件终端")
                // 不设置 EXTRA_BACKGROUND → Runner.TERMINAL_SESSION → 打开真实终端并执行命令
            }
            startService(intent)
            android.util.Log.e(TAG, "✅ 已启动 CUI 终端会话")
        } catch (e: Exception) {
            Logger.e(TAG, "❌ CUI 终端启动异常: ${e.message}", e)
            Toast.makeText(this, "CUI 终端启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ============================================================
    // JS 接口
    // ============================================================

    private fun injectJSInterface() {
        webView?.let {
            it.removeJavascriptInterface("UINPlugin")
            val jsInterface = PluginJSInterface(this@PluginHostActivity, currentPluginId, pluginInfo!!)
            it.addJavascriptInterface(jsInterface, "UINPlugin")
            android.util.Log.e(TAG, "✅ UINPlugin JS 接口已重新注入")
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
            callback(false, "后端未就绪")
            return
        }

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
                android.util.Log.e(TAG, "调用API失败: ${e.message}", e)
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
            "<br>后端: ${info.getBackendDisplayName()}"
        } else {
            ""
        }
        val noticeInfo = if (info.hasNotice()) {
            "<br>包含说明文档"
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
                <p>${info.description ?: "Web插件"}</p>
                <div id="backendStatus" style="margin: 12px 0;">
                    后端: <span class="status-dot offline" id="statusDot"></span>
                    <span id="statusText">检测中...</span>
                </div>
                <button onclick="testBackend()">测试后端</button>
                <button onclick="UINPlugin.callHost('finish','')">关闭</button>
                <div class="info">
                    <strong>插件信息</strong><br>
                    版本: ${info.versionName}<br>
                    作者: ${info.author ?: "未知"}<br>
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
                        text.textContent = '已连接 (端口 ' + port + ')';
                    } else {
                        dot.className = 'status-dot offline';
                        text.textContent = '未连接';
                    }
                };

                window._onBackendProgress = function(progress, message) {
                    var dot = document.getElementById('statusDot');
                    var text = document.getElementById('statusText');
                    dot.className = 'status-dot starting';
                    text.textContent = message || ('启动中 ' + progress + '%');
                };

                function testBackend() {
                    var status = UINPlugin.getBackendStatus();
                    if (status.startsWith('running:')) {
                        alert('后端运行中，端口: ' + status.split(':')[1]);
                    } else {
                        alert('后端未运行');
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