// plugin/PluginHostActivity.kt
package com.UIN.Tool.plugin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class PluginHostActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PluginHostActivity"
        const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val KEY_PLUGIN_ID = "plugin_id"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val BACKEND_START_TIMEOUT = 15000L
    }

    private lateinit var container: FrameLayout
    private var currentPluginId: String = ""
    private var webView: WebView? = null
    private lateinit var pluginManager: PluginManager
    private var pluginInfo: PluginInfo? = null
    private var isDestroyed = false

    private var backendPort = 0
    private var isBackendReady = false
    private var isBackendStarting = false

    private var backendTimeoutHandler: Handler? = null
    private var backendTimeoutRunnable: Runnable? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        if (currentPluginId.isEmpty()) {
            Logger.e(TAG, "插件ID为空")
            Toast.makeText(this, "插件ID不能为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        pluginManager = ServiceLocator.getPluginManager()
        pluginInfo = pluginManager.getPluginInfo(currentPluginId)

        if (pluginInfo == null) {
            Logger.e(TAG, "插件不存在: $currentPluginId")
            Toast.makeText(this, "插件不存在: $currentPluginId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 显示插件说明（使用 Compose 弹窗）
        showPluginNoticeIfNeeded()

        if (pluginInfo!!.hasBackend()) {
            Logger.i(TAG, "插件需要后端: ${pluginInfo!!.backend}")
            isBackendStarting = true
            showLoadingState()
            startBackendAndLoadPlugin()
            setupBackendTimeout()
        } else {
            loadPlugin()
        }
    }

    // ============================================================
    // ✅ 使用 Compose 显示说明弹窗（与项目风格一致，不新建类）
    // ============================================================

    private fun showPluginNoticeIfNeeded() {
        val info = pluginInfo ?: return
        if (!info.hasNotice()) return
        if (pluginManager.isPluginNoticeIgnored(currentPluginId)) return

        // 使用 Compose 显示弹窗
        setContent {
            UINToolTheme {
                var showDialog by remember { mutableStateOf(true) }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showDialog = false
                        },
                        title = { Text(info.name) },
                        text = { Text(info.notice) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDialog = false
                                    pluginManager.setPluginNoticeIgnored(currentPluginId, true)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("知道了")
                            }
                        },
                        dismissButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        showDialog = false
                                        pluginManager.setPluginNoticeIgnored(currentPluginId, true)
                                    }
                                ) {
                                    Text("不再提示")
                                }
                                TextButton(
                                    onClick = {
                                        showDialog = false
                                    }
                                ) {
                                    Text("稍后提醒")
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }

    // ============================================================
    // 加载状态
    // ============================================================

    private fun showLoadingState() {
        val loadingView = WebView(this).apply {
            settings.javaScriptEnabled = true
            loadData(
                """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            height: 100vh;
                            margin: 0;
                            font-family: -apple-system, sans-serif;
                            background: #f5f7fa;
                        }
                        .loader { text-align: center; }
                        .spinner {
                            width: 48px;
                            height: 48px;
                            border: 4px solid #e0e0e0;
                            border-top-color: #1A3A4A;
                            border-radius: 50%;
                            animation: spin 0.8s linear infinite;
                            margin: 0 auto;
                        }
                        @keyframes spin { to { transform: rotate(360deg); } }
                        .title { margin-top: 20px; font-size: 16px; font-weight: 600; color: #1A3A4A; }
                        .subtitle { margin-top: 8px; font-size: 13px; color: #888; }
                        .progress-bar {
                            margin-top: 16px;
                            width: 200px;
                            height: 4px;
                            background: #e0e0e0;
                            border-radius: 2px;
                            overflow: hidden;
                            margin: 16px auto 0;
                        }
                        .progress-fill {
                            height: 100%;
                            width: 0%;
                            background: linear-gradient(90deg, #1A3A4A, #4A8A9E);
                            border-radius: 2px;
                            transition: width 0.3s ease;
                        }
                    </style>
                </head>
                <body>
                    <div class="loader">
                        <div class="spinner"></div>
                        <div class="title" id="loaderTitle">正在启动后端服务</div>
                        <div class="subtitle" id="loaderSubtitle">请稍候...</div>
                        <div class="progress-bar">
                            <div class="progress-fill" id="loaderProgress"></div>
                        </div>
                    </div>
                    <script>
                        window._onBackendProgress = function(progress, message) {
                            document.getElementById('loaderProgress').style.width = Math.min(progress, 100) + '%';
                            if (message) document.getElementById('loaderSubtitle').textContent = message;
                            if (progress >= 100) {
                                document.getElementById('loaderTitle').textContent = '后端就绪';
                            }
                        };
                        window._onBackendReady = function(port) {
                            if (port) {
                                document.getElementById('loaderTitle').textContent = '后端就绪 (端口 ' + port + ')';
                                document.getElementById('loaderProgress').style.width = '100%';
                            } else {
                                document.getElementById('loaderTitle').textContent = '后端启动失败';
                                document.getElementById('loaderSubtitle').textContent = '将使用降级模式';
                            }
                            setTimeout(function() {
                                document.body.innerHTML = '<div style="text-align:center;padding:40px;color:#666;">加载完成，即将显示内容...</div>';
                            }, 500);
                        };
                    </script>
                </body>
                </html>
                """.trimIndent(),
                "text/html",
                "UTF-8"
            )
        }
        container.addView(loadingView)
    }

    private fun setupBackendTimeout() {
        backendTimeoutHandler = Handler(Looper.getMainLooper())
        backendTimeoutRunnable = Runnable {
            if (isBackendStarting) {
                Logger.w(TAG, "后端启动超时，继续加载插件")
                isBackendStarting = false
                sendBackendProgress(0, "启动超时，继续加载")
                loadPlugin()
            }
        }
        backendTimeoutHandler?.postDelayed(backendTimeoutRunnable!!, BACKEND_START_TIMEOUT)
    }

    private fun startBackendAndLoadPlugin() {
        sendBackendProgress(10, "正在检查后端环境...")

        pluginManager.startBackend(currentPluginId) { success, port, error ->
            backendTimeoutHandler?.removeCallbacks(backendTimeoutRunnable!!)

            isBackendStarting = false
            if (success) {
                backendPort = port
                isBackendReady = true
                sendBackendProgress(100, "后端就绪 (端口 $port)")
                Logger.success(TAG, "后端就绪，端口: $port")
            } else {
                sendBackendProgress(0, "后端启动失败: ${error ?: "未知错误"}")
                Logger.e(TAG, "后端启动失败: $error")
            }
            loadPlugin()
        }
    }

    private fun sendBackendProgress(progress: Int, message: String) {
        webView?.evaluateJavascript(
            "if (window._onBackendProgress) window._onBackendProgress($progress, '$message');",
            null
        )
        if (progress < 100) {
            Logger.d(TAG, "后端进度: $progress% - $message")
        }
    }

    // ============================================================
    // 插件加载
    // ============================================================

    private fun loadPlugin() {
        val info = pluginInfo ?: run { finish(); return }

        if (info.uiType == "web") {
            loadWebPlugin()
        } else {
            loadNativePlugin()
        }
    }

    private fun loadWebPlugin() {
        val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
        if (!pluginDir.exists()) {
            Logger.e(TAG, "插件目录不存在: ${pluginDir.absolutePath}")
            Toast.makeText(this, "插件目录不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        container.removeAllViews()

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
            Logger.success(TAG, "UINPlugin JS 接口已注入 (加载前)")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Logger.success(TAG, "WebView 加载完成")
                    injectJSInterface()
                    Handler(Looper.getMainLooper()).postDelayed({
                        notifyBackendStatus()
                        webView?.evaluateJavascript(
                            "if (window._onUINPluginReady) window._onUINPluginReady();",
                            null
                        )
                    }, 300)
                    if (isBackendStarting) {
                        sendBackendProgress(30, "正在启动后端服务...")
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Logger.e(TAG, "WebView 加载错误: $description (code: $errorCode)")
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
                    android.app.AlertDialog.Builder(this@PluginHostActivity)
                        .setTitle("提示")
                        .setMessage(message)
                        .setPositiveButton("确定") { _, _ -> result?.confirm() }
                        .show()
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    android.app.AlertDialog.Builder(this@PluginHostActivity)
                        .setTitle("确认")
                        .setMessage(message)
                        .setPositiveButton("确定") { _, _ -> result?.confirm() }
                        .setNegativeButton("取消") { _, _ -> result?.cancel() }
                        .show()
                    return true
                }
            }

            val entryPath = if (pluginInfo!!.entry.isNotEmpty()) pluginInfo!!.entry else "web/index.html"
            val indexPath = "$pluginDir/$entryPath"
            if (File(indexPath).exists()) {
                loadUrl("file://$indexPath")
                Logger.i(TAG, "加载 Web 插件: $indexPath")
            } else {
                val defaultHtml = createDefaultHtml(pluginInfo!!)
                loadDataWithBaseURL("file://$pluginDir/", defaultHtml, "text/html", "UTF-8", null)
                Logger.w(TAG, "入口文件不存在，使用默认页面")
            }
        }

        container.addView(webView)
        PluginManager.putPluginWebView(currentPluginId, webView)
    }

    private fun loadNativePlugin() {
        try {
            val view = pluginManager.getPluginViewSync(currentPluginId, this, container)
            if (view != null) {
                container.removeAllViews()
                container.addView(view)
                Logger.success(TAG, "原生插件加载成功: ${pluginInfo?.name}")
            } else {
                Logger.e(TAG, "原生插件加载失败")
                Toast.makeText(this, "原生插件加载失败", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "原生插件加载异常", e)
            Toast.makeText(this, "插件加载异常: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
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
            Logger.success(TAG, "UINPlugin JS 接口已重新注入")
        }
    }

    private fun notifyBackendStatus() {
        if (isBackendReady) {
            webView?.evaluateJavascript(
                "if (window._onBackendReady) window._onBackendReady($backendPort);",
                null
            )
        } else if (isBackendStarting) {
            webView?.evaluateJavascript(
                "if (window._onBackendProgress) window._onBackendProgress(20, '正在启动后端...');",
                null
            )
        } else {
            webView?.evaluateJavascript(
                "if (window._onBackendReady) window._onBackendReady(null);",
                null
            )
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
                Logger.e(TAG, "调用API失败: ${e.message}", e)
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