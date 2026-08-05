package com.UIN.Tool.core.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.constants.AppConstants as Constants
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
                    if (com.UIN.Tool.utils.UIConfig.isInitialized()) {
                        view.evaluateJavascript(com.UIN.Tool.utils.UIConfig.getThemeCssInjectionScript(), null)
                    }
                    Logger.success(TAG, Str.get(R.string.webview_load_complete_plugininfo_nam, pluginInfo.name))
                }
                
                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    Logger.e(TAG, Str.get(R.string.webview_load_error_description, description))
                    val errorHtml = """
                        <html><body style='padding:20px;text-align:center;font-family:sans-serif'>
                        <h3>${Str.get(R.string.plugin_load_failed_h3)}</h3>
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
                Logger.i(TAG, Str.get(R.string.loading_web_plugin_indexpath, indexPath))
            } else {
                val defaultHtml = getDefaultHtml()
                loadDataWithBaseURL("file://$pluginDir/web/", defaultHtml, "text/html", "UTF-8", null)
                Logger.w(TAG, Str.get(R.string.entry_file_not_found_using_default_p, indexPath))
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
            else -> Logger.d(TAG, Str.get(R.string.unhandled_method_method, method))
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
                <button onclick="UINPlugin.callHost('toast', 'Hello from Web Plugin!')">${Str.get(R.string.click_me)}</button>
                <button onclick="UINPlugin.callHost('finish', '')">${Str.get(R.string.close)}</button>
                <div class="info">
                    <strong>${Str.get(R.string.plugin_info)}</strong><br>
                    ${Str.get(R.string.web_plugin_version, pluginInfo.versionName)}
                    ${Str.get(R.string.web_plugin_author, pluginInfo.author ?: Str.get(R.string.unknown))}
                    ID: $pluginId
                </div>
                <script>
                    window.addEventListener('resume', function(e) { console.log('${Str.get(R.string.web_plugin_resumed)}', e.detail); });
                    window.addEventListener('pause', function(e) { console.log('${Str.get(R.string.web_plugin_paused)}', e.detail); });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}