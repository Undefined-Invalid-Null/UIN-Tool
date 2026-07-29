package com.UIN.Tool.plugin

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
        private const val REQUEST_CODE_PERMISSIONS = 1001
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

        // ✅ 调用 startBackendIfNeeded
        android.util.Log.e(TAG, "📞 调用 startBackendIfNeeded()")
        startBackendIfNeeded()

        // 加载插件视图
        loadPlugin()
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
            android.util.Log.e(TAG, "📌 pluginInfo.backendAutoStart: ${pluginInfo!!.backendAutoStart}")
            android.util.Log.e(TAG, "📌 pluginInfo.hasBackend(): ${pluginInfo!!.hasBackend()}")
            android.util.Log.e(TAG, "📌 isBackendReady: $isBackendReady")
        } else {
            android.util.Log.e(TAG, "❌ pluginInfo 为 null，无法检查后端")
        }
        android.util.Log.e(TAG, "========================================")
        
        if (pluginInfo != null && pluginInfo!!.hasBackend() && !isBackendReady) {
            android.util.Log.e(TAG, "✅ 条件满足，调用 PluginManager.startBackend()")
            isBackendStarting = true
            pluginManager.startBackend(currentPluginId) { success, port, error ->
                isBackendStarting = false
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
            setupBackendTimeout()
        } else {
            android.util.Log.e(TAG, "❌ 条件不满足，跳过启动后端")
            if (pluginInfo == null) {
                android.util.Log.e(TAG, "   -> pluginInfo 为 null")
            }
            if (pluginInfo != null && !pluginInfo!!.hasBackend()) {
                android.util.Log.e(TAG, "   -> hasBackend() 返回 false")
            }
            if (isBackendReady) {
                android.util.Log.e(TAG, "   -> isBackendReady 为 true")
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
        backendTimeoutHandler?.postDelayed(backendTimeoutRunnable!!, BACKEND_START_TIMEOUT)
    }

    // ============================================================
    // 插件说明弹窗
    // ============================================================

    private fun showPluginNoticeIfNeeded() {
        val info = pluginInfo ?: return
        if (!info.hasNotice()) return
        if (pluginManager.isPluginNoticeIgnored(currentPluginId)) return

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

        if (info.uiType == "web") {
            android.util.Log.e(TAG, "✅ 走 Web 插件分支")
            loadWebPlugin()
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