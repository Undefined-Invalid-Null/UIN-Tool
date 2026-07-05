package com.UIN.Tool.core.plugin

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import java.io.File

/**
 * Web 插件代理类 - 实现 PluginInterface 接口，内部使用 WebView 渲染
 */
class WebPluginProxy(
    private val pluginId: String,
    private val pluginDir: String,
    private val pluginInfo: PluginInfo
) : PluginInterface {
    
    private val TAG = "WebPluginProxy"
    private var context: Context? = null
    private var webView: WebView? = null
    private var isWebViewReady = false
    private val entryFile: String = pluginInfo.entry.ifEmpty { Constants.PLUGIN_WEB_INDEX }
    
    override fun onCreateView(
        context: Context,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        this.context = context
        
        webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                defaultTextEncodingName = "UTF-8"
            }
            
            // 设置 WebViewClient
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    isWebViewReady = true
                    Logger.success(TAG, "WebView 加载完成: ${pluginInfo.name}")
                }
                
                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    Logger.e(TAG, "WebView 加载错误: $description")
                    val errorHtml = """
                        <html><body style='padding:20px;text-align:center;font-family:sans-serif'>
                        <h3>插件加载失败</h3>
                        <p>$description</p>
                        <p style='font-size:12px;color:#999'>$failingUrl</p>
                        </body></html>
                    """.trimIndent()
                    view.loadData(errorHtml, "text/html", "UTF-8")
                }
            }
            
            // 设置 JS 接口
            val proxy = this@WebPluginProxy
            val jsInterface = PluginWebInterface(context, pluginId, proxy)
            addJavascriptInterface(jsInterface, "UINPlugin")
            
            // 加载本地 HTML 文件
            val indexPath = "$pluginDir/$entryFile"
            val indexFile = File(indexPath)
            
            if (indexFile.exists()) {
                loadUrl("file://$indexPath")
                Logger.i(TAG, "加载 Web 插件: $indexPath")
            } else {
                val defaultHtml = getDefaultHtml()
                loadDataWithBaseURL("file://$pluginDir/web/", defaultHtml, "text/html", "UTF-8", null)
                Logger.w(TAG, "入口文件不存在，使用默认页面: $indexPath")
            }
        }
        
        return webView
    }
    
    /**
     * JS 调用插件方法
     */
    fun onJsCall(method: String, params: String?) {
        when (method) {
            "getPluginInfo" -> sendEvent("pluginInfo", pluginInfo.toJson())
            "getDeviceInfo" -> {
                val dm = context?.resources?.displayMetrics
                val info = org.json.JSONObject().apply {
                    put("model", android.os.Build.MODEL)
                    put("manufacturer", android.os.Build.MANUFACTURER)
                    put("android", android.os.Build.VERSION.RELEASE)
                    put("api", android.os.Build.VERSION.SDK_INT)
                    dm?.let {
                        put("screenWidth", it.widthPixels)
                        put("screenHeight", it.heightPixels)
                        put("density", it.density)
                    }
                }
                sendEvent("deviceInfo", info.toString())
            }
            else -> Logger.d(TAG, "未处理的方法: $method")
        }
    }
    
    /**
     * 向 Web 端发送事件
     */
    fun sendEvent(eventName: String, data: String?) {
        if (webView != null && isWebViewReady) {
            val js = """
                if (window.dispatchEvent && window.dispatchEvent instanceof Function) {
                    window.dispatchEvent(new CustomEvent('$eventName', { detail: ${data ?: "null"} }));
                } else if (window.on$eventName) {
                    window.on$eventName(${data ?: "null"});
                }
            """.trimIndent()
            webView?.evaluateJavascript(js, null)
        }
    }
    
    override fun onResume() {
        webView?.let {
            it.onResume()
            it.resumeTimers()
        }
        sendEvent("resume", "{}")
    }
    
    override fun onPause() {
        webView?.let {
            it.onPause()
            it.pauseTimers()
        }
        sendEvent("pause", "{}")
    }
    
    override fun onDestroy() {
        sendEvent("destroy", "{}")
        webView?.let {
            it.loadUrl("about:blank")
            it.clearHistory()
            it.clearCache(true)
            it.clearFormData()
            it.destroy()
        }
        webView = null
    }
    
    override fun onBackPressed(): Boolean {
        webView?.let {
            if (it.canGoBack()) {
                it.goBack()
                return true
            }
        }
        return false
    }
    
    override fun onSaveInstanceState(): Bundle? {
        return Bundle().apply {
            webView?.saveState(this)
        }
    }
    
    private fun getDefaultHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${pluginInfo.name}</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; text-align: center; }
                    button { background: #37474F; color: white; border: none; padding: 12px 24px; border-radius: 8px; margin: 10px; }
                    .info { background: #f0f0f0; padding: 16px; border-radius: 12px; margin-top: 20px; text-align: left; }
                </style>
            </head>
            <body>
                <h1>${pluginInfo.name}</h1>
                <p>${pluginInfo.description ?: ""}</p>
                <button onclick="UINPlugin.callHost('toast', 'Hello from Web Plugin!')">点我</button>
                <button onclick="UINPlugin.callHost('finish', '')">关闭</button>
                <div class="info">
                    <strong>插件信息</strong><br>
                    版本: ${pluginInfo.versionName}<br>
                    作者: ${pluginInfo.author ?: "未知"}<br>
                    ID: $pluginId
                </div>
                <script>
                    window.addEventListener('resume', function(e) { console.log('插件恢复', e.detail); });
                    window.addEventListener('pause', function(e) { console.log('插件暂停', e.detail); });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}