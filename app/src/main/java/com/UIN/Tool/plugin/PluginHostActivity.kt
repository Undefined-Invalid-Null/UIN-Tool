// app/src/main/java/com/UIN/Tool/plugin/PluginHostActivity.kt
package com.UIN.Tool.plugin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.PermissionUtils
import java.io.File

class PluginHostActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val TAG = "PluginHostActivity"
        private const val KEY_PLUGIN_ID = "plugin_id"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var container: FrameLayout
    private var currentPluginId: String = ""
    private var webView: WebView? = null
    private var pluginManager: PluginManager? = null
    private var pluginInfo: PluginInfo? = null
    private var currentTitle: String = ""
    private var isDestroyed = false
    private var isPermissionRequesting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = FrameLayout(this)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(container)

        // 从 Intent 或 savedInstanceState 获取插件ID
        if (savedInstanceState != null) {
            currentPluginId = savedInstanceState.getString(KEY_PLUGIN_ID) ?: ""
        }

        if (currentPluginId.isEmpty()) {
            currentPluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: ""
        }

        Logger.i(TAG, "========================================")
        Logger.i(TAG, "onCreate: 插件ID = $currentPluginId")
        Logger.i(TAG, "========================================")

        if (currentPluginId.isEmpty()) {
            Logger.e(TAG, "插件ID为空")
            Toast.makeText(this, "插件ID不能为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        pluginManager = ServiceLocator.getPluginManager()
        pluginInfo = pluginManager?.getPluginInfo(currentPluginId)

        if (pluginInfo == null) {
            Logger.e(TAG, "插件不存在: $currentPluginId")
            Toast.makeText(this, "插件不存在: $currentPluginId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ✅ 检查并请求权限
        checkAndRequestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        val newPluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: ""
        
        Logger.i(TAG, "========================================")
        Logger.i(TAG, "onNewIntent: 新插件ID = $newPluginId, 当前插件ID = $currentPluginId")
        Logger.i(TAG, "========================================")
        
        if (newPluginId.isEmpty()) {
            Logger.w(TAG, "onNewIntent: 插件ID为空，忽略")
            return
        }
        
        if (newPluginId != currentPluginId) {
            Logger.i(TAG, "onNewIntent: 检测到不同插件，重新加载")
            
            // 清理旧插件
            clearPlugin()
            
            // 更新当前插件ID
            currentPluginId = newPluginId
            setIntent(intent)
            
            // 重新获取插件信息
            pluginInfo = pluginManager?.getPluginInfo(currentPluginId)
            if (pluginInfo == null) {
                Logger.e(TAG, "插件不存在: $currentPluginId")
                Toast.makeText(this, "插件不存在: $currentPluginId", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // 重新检查权限并加载
            checkAndRequestPermissions()
        } else {
            Logger.d(TAG, "onNewIntent: 相同插件，忽略")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PLUGIN_ID, currentPluginId)

        webView?.let {
            val bundle = Bundle()
            it.saveState(bundle)
            outState.putBundle(KEY_WEBVIEW_STATE, bundle)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val webViewState = savedInstanceState.getBundle(KEY_WEBVIEW_STATE)
        webViewState?.let {
            webView?.restoreState(it)
        }
    }

    // ==================== 权限检查与请求 ====================

    /**
     * ✅ 检查并请求插件权限
     */
    private fun checkAndRequestPermissions() {
        Logger.enter(TAG, "checkAndRequestPermissions")
        Logger.param(TAG, "pluginId", currentPluginId)

        val plugin = pluginInfo ?: run {
            Logger.e(TAG, "插件信息为空")
            finish()
            return
        }

        if (plugin.permissions.isEmpty()) {
            Logger.d(TAG, "插件没有声明权限，直接加载")
            loadPlugin()
            return
        }

        // 检查权限状态
        val missingPermissions = pluginManager?.getPluginMissingPermissions(currentPluginId) ?: emptyList()
        
        if (missingPermissions.isEmpty()) {
            Logger.success(TAG, "所有权限已授予，加载插件")
            loadPlugin()
            return
        }

        Logger.w(TAG, "插件缺少 ${missingPermissions.size} 个权限: ${missingPermissions.joinToString()}")

        // 分离普通权限和特殊权限
        val normalPermissions = missingPermissions.filter { 
            !PluginPermissionManager.isSpecialPermission(it) 
        }
        val specialPermissions = missingPermissions.filter { 
            PluginPermissionManager.isSpecialPermission(it) 
        }

        // 显示权限说明对话框
        showPermissionExplanationDialog(normalPermissions, specialPermissions)
    }

    /**
     * ✅ 显示权限说明对话框
     */
    private fun showPermissionExplanationDialog(
        normalPermissions: List<String>,
        specialPermissions: List<String>
    ) {
        val plugin = pluginInfo ?: return

        val message = buildString {
            append("插件 \"${plugin.name}\" 需要以下权限才能正常运行：\n\n")
            
            if (normalPermissions.isNotEmpty()) {
                append("📱 普通权限：\n")
                normalPermissions.forEach { permission ->
                    append("  • ${PluginPermissionManager.getPermissionDisplayName(permission)}\n")
                    append("    ${PluginPermissionManager.getPermissionDescription(permission)}\n")
                }
                append("\n")
            }
            
            if (specialPermissions.isNotEmpty()) {
                append("⚙️ 特殊权限（需在系统设置中手动开启）：\n")
                specialPermissions.forEach { permission ->
                    append("  • ${PluginPermissionManager.getPermissionDisplayName(permission)}\n")
                    append("    ${PluginPermissionManager.getPermissionDescription(permission)}\n")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("权限请求")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("授权") { _, _ ->
                requestPermissionsInternal(normalPermissions, specialPermissions)
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .setNeutralButton("权限说明") { _, _ ->
                // 显示详细权限说明
                showDetailedPermissionInfo()
            }
            .show()
    }

    /**
     * ✅ 显示详细权限信息
     */
    private fun showDetailedPermissionInfo() {
        val plugin = pluginInfo ?: return
        val missingPermissions = pluginManager?.getPluginMissingPermissions(currentPluginId) ?: emptyList()

        val message = buildString {
            append("📋 权限详细说明\n\n")
            missingPermissions.forEach { permission ->
                append("━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📌 ${PluginPermissionManager.getPermissionDisplayName(permission)}\n")
                append("📝 ${PluginPermissionManager.getPermissionDescription(permission)}\n")
                if (PluginPermissionManager.isSpecialPermission(permission)) {
                    append("⚠️ 特殊权限：需要手动在系统设置中开启\n")
                }
                append("\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("权限详情")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .setNegativeButton("返回") { _, _ ->
                checkAndRequestPermissions()
            }
            .show()
    }

    /**
     * ✅ 内部权限请求
     */
    private fun requestPermissionsInternal(
        normalPermissions: List<String>,
        specialPermissions: List<String>
    ) {
        if (isPermissionRequesting) {
            Logger.d(TAG, "权限请求正在进行中")
            return
        }

        isPermissionRequesting = true

        // 1. 请求普通权限
        if (normalPermissions.isNotEmpty()) {
            Logger.d(TAG, "请求普通权限: ${normalPermissions.joinToString()}")
            requestPermissions(normalPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            return
        }

        // 2. 如果没有普通权限，直接处理特殊权限
        if (specialPermissions.isNotEmpty()) {
            requestSpecialPermissions(specialPermissions)
        } else {
            // 所有权限都已授予
            isPermissionRequesting = false
            loadPlugin()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // ✅ 处理普通权限结果
            val plugin = pluginInfo ?: return
            
            // 检查所有权限是否都已授予
            val allGranted = permissions.indices.all { 
                grantResults[it] == android.content.pm.PackageManager.PERMISSION_GRANTED 
            }

            if (allGranted) {
                Logger.success(TAG, "✅ 所有普通权限已授予")
                // 检查是否有特殊权限需要请求
                val missingSpecial = pluginManager?.getPluginMissingPermissions(currentPluginId)
                    ?.filter { PluginPermissionManager.isSpecialPermission(it) } ?: emptyList()
                
                if (missingSpecial.isNotEmpty()) {
                    // 请求特殊权限
                    requestSpecialPermissions(missingSpecial)
                } else {
                    // 所有权限都已授予
                    isPermissionRequesting = false
                    loadPlugin()
                }
            } else {
                // 部分权限被拒绝
                Logger.w(TAG, "⚠️ 部分权限被拒绝")
                isPermissionRequesting = false
                
                // 检查是否还有权限被拒绝
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                
                AlertDialog.Builder(this)
                    .setTitle("权限被拒绝")
                    .setMessage("插件 \"${plugin.name}\" 需要以下权限才能正常运行：\n\n${deniedPermissions.joinToString("\n") { "• ${PluginPermissionManager.getPermissionDisplayName(it)}" }}\n\n是否重新请求？")
                    .setPositiveButton("重新请求") { _, _ ->
                        requestPermissions(deniedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
                    }
                    .setNegativeButton("退出") { _, _ ->
                        finish()
                    }
                    .setNeutralButton("去设置") { _, _ ->
                        openAppSettings()
                    }
                    .show()
            }
        }
    }

    /**
     * ✅ 请求特殊权限
     */
    private fun requestSpecialPermissions(specialPermissions: List<String>) {
        Logger.d(TAG, "请求特殊权限: ${specialPermissions.joinToString()}")

        // 检查哪些特殊权限已授予
        val missing = specialPermissions.filter { 
            !PermissionUtils.hasSpecialPermission(this, it) 
        }

        if (missing.isEmpty()) {
            Logger.success(TAG, "所有特殊权限已授予")
            isPermissionRequesting = false
            loadPlugin()
            return
        }

        // 构建特殊权限提示
        val message = buildString {
            append("以下特殊权限需要在系统设置中手动开启：\n\n")
            missing.forEach { permission ->
                append("• ${PluginPermissionManager.getPermissionDisplayName(permission)}\n")
                append("  ${PluginPermissionManager.getPermissionDescription(permission)}\n")
            }
            append("\n点击「去设置」打开系统设置页面。")
        }

        AlertDialog.Builder(this)
            .setTitle("特殊权限")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                openSpecialPermissionSettings(missing)
            }
            .setNegativeButton("取消") { _, _ ->
                isPermissionRequesting = false
                finish()
            }
            .show()
    }

    /**
     * ✅ 打开特殊权限设置
     */
    private fun openSpecialPermissionSettings(permissions: List<String>) {
        // 如果有多个特殊权限，打开应用详情页
        if (permissions.size > 1) {
            openAppSettings()
            return
        }

        val permission = permissions.firstOrNull() ?: return
        val intent = when (permission) {
            "MANAGE_EXTERNAL_STORAGE" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                } else null
            }
            "SYSTEM_ALERT_WINDOW" -> {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            "WRITE_SETTINGS" -> {
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            "REQUEST_INSTALL_PACKAGES" -> {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            "PACKAGE_USAGE_STATS" -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
            "ACCESSIBILITY" -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            else -> null
        }

        if (intent != null) {
            try {
                startActivity(intent)
                // 用户从设置返回后检查权限
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    checkSpecialPermissionsAfterSettings()
                }, 1000)
            } catch (e: Exception) {
                Logger.e(TAG, "打开特殊权限设置失败: ${e.message}", e)
                openAppSettings()
            }
        } else {
            openAppSettings()
        }
    }

    /**
     * ✅ 从设置返回后检查特殊权限
     */
    private fun checkSpecialPermissionsAfterSettings() {
        val plugin = pluginInfo ?: return
        val missingPermissions = pluginManager?.getPluginMissingPermissions(currentPluginId) ?: emptyList()
        val missingSpecial = missingPermissions.filter { 
            PluginPermissionManager.isSpecialPermission(it) 
        }

        if (missingSpecial.isEmpty()) {
            // 所有特殊权限已授予
            Logger.success(TAG, "所有特殊权限已授予")
            isPermissionRequesting = false
            loadPlugin()
        } else {
            // 仍有特殊权限未授予
            Logger.w(TAG, "仍有特殊权限未授予: ${missingSpecial.joinToString()}")
            
            AlertDialog.Builder(this)
                .setTitle("权限未完全授予")
                .setMessage("部分特殊权限尚未授予，插件可能无法正常运行。\n\n缺少：\n${missingSpecial.joinToString("\n") { "• ${PluginPermissionManager.getPermissionDisplayName(it)}" }}")
                .setPositiveButton("继续") { _, _ ->
                    isPermissionRequesting = false
                    loadPlugin() // 即使权限不全，也尝试加载
                }
                .setNegativeButton("退出") { _, _ ->
                    finish()
                }
                .show()
        }
    }

    /**
     * ✅ 打开应用设置
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "打开应用设置失败: ${e.message}", e)
        }
    }

    // ==================== 插件加载 ====================

    private fun loadPlugin() {
        Logger.enter(TAG, "loadPlugin")
        
        val plugin = pluginInfo ?: run {
            Logger.e(TAG, "插件信息为空")
            finish()
            return
        }

        Logger.i(TAG, "加载插件: ${plugin.name} (${plugin.uiType})")

        // 设置标题
        supportActionBar?.title = plugin.name
        currentTitle = plugin.name

        // 根据UI类型加载
        when (plugin.uiType) {
            "web" -> loadWebPlugin(plugin)
            else -> loadNativePlugin(plugin)
        }
        
        Logger.exit(TAG, "loadPlugin", System.currentTimeMillis())
    }

    private fun clearPlugin() {
        Logger.d(TAG, "clearPlugin: 清理旧插件 $currentPluginId")
        
        webView?.let {
            it.loadUrl("about:blank")
            it.clearHistory()
            it.clearCache(true)
            it.clearFormData()
            it.destroy()
        }
        webView = null
        
        container.removeAllViews()
        pluginManager?.onPluginDestroy(currentPluginId)
        PluginManager.removePluginWebView(currentPluginId)
        
        Logger.success(TAG, "clearPlugin: 清理完成")
    }

    private fun loadNativePlugin(info: PluginInfo) {
        Logger.d(TAG, "加载原生插件: ${info.name}")

        try {
            val view = pluginManager?.getPluginViewSync(currentPluginId, this, container)

            if (view != null) {
                container.addView(view)
                Logger.success(TAG, "原生插件加载成功: ${info.name}")
            } else {
                Logger.e(TAG, "加载原生插件失败")
                Toast.makeText(this, "加载原生插件失败", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "加载原生插件异常", e)
            Toast.makeText(this, "插件加载异常: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadWebPlugin(info: PluginInfo) {
        Logger.d(TAG, "加载Web插件: ${info.name}")

        val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
        if (!pluginDir.exists()) {
            Logger.e(TAG, "插件目录不存在")
            Toast.makeText(this, "插件目录不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        createWebView(pluginDir, info)
    }

    private fun createWebView(pluginDir: File, info: PluginInfo) {
        webView?.let {
            it.loadUrl("about:blank")
            it.clearHistory()
            it.clearCache(true)
            it.destroy()
        }
        webView = null

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                defaultTextEncodingName = "UTF-8"
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult
                ): Boolean {
                    AlertDialog.Builder(this@PluginHostActivity)
                        .setTitle("提示")
                        .setMessage(message)
                        .setPositiveButton("确定") { _, _ -> result.confirm() }
                        .setCancelable(false)
                        .show()
                    result.confirm()
                    return true
                }

                override fun onJsConfirm(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult
                ): Boolean {
                    AlertDialog.Builder(this@PluginHostActivity)
                        .setTitle("确认")
                        .setMessage(message)
                        .setPositiveButton("确定") { _, _ -> result.confirm() }
                        .setNegativeButton("取消") { _, _ -> result.cancel() }
                        .show()
                    return true
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Logger.d("WebView", "Console: ${consoleMessage.message()} (line ${consoleMessage.lineNumber()})")
                    return true
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress == 100) {
                        Logger.success(TAG, "WebView加载完成: ${info.name}")
                    }
                }

                override fun onReceivedTitle(view: WebView, title: String) {
                    super.onReceivedTitle(view, title)
                    supportActionBar?.title = title
                    currentTitle = title
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()

                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Logger.e(TAG, "打开外部链接失败", e)
                            Toast.makeText(this@PluginHostActivity, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }

                    view.loadUrl(url)
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Logger.success(TAG, "页面加载完成: $url")
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Logger.e(TAG, "WebView加载错误: $description (code: $errorCode)")

                    val errorHtml = """
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: sans-serif; padding: 40px 20px; text-align: center; background: #f5f5f5; margin: 0; }
                                .error-card { max-width: 400px; margin: 40px auto; background: white; border-radius: 16px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                                h1 { color: #e74c3c; margin-bottom: 16px; }
                                p { color: #666; margin-bottom: 8px; }
                                .url { font-size: 12px; color: #999; word-break: break-all; }
                            </style>
                        </head>
                        <body>
                            <div class="error-card">
                                <h1>⚠️ 加载失败</h1>
                                <p>$description</p>
                                <p class="url">$failingUrl</p>
                                <p style="margin-top:16px;font-size:12px;color:#999;">错误代码: $errorCode</p>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
                }
            }

            val jsInterface = PluginJSInterface(this@PluginHostActivity, currentPluginId, info)
            addJavascriptInterface(jsInterface, "UINPlugin")

            val entryPath = if (info.entry.isNotEmpty()) info.entry else Constants.PLUGIN_WEB_INDEX
            val indexPath = "${pluginDir.absolutePath}/$entryPath"
            val indexFile = File(indexPath)

            if (indexFile.exists()) {
                loadUrl("file://$indexPath")
                Logger.i(TAG, "加载Web插件入口: $indexPath")
            } else {
                val defaultHtml = createDefaultWebPage(info)
                loadDataWithBaseURL(
                    "file://${pluginDir.absolutePath}/",
                    defaultHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
                Logger.w(TAG, "入口文件不存在，使用默认页面: $indexPath")
            }
        }

        container.addView(webView)
        PluginManager.putPluginWebView(currentPluginId, webView)
        Logger.success(TAG, "Web插件已加载: ${info.name}")
    }

    private fun createDefaultWebPage(info: PluginInfo): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${info.name}</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; text-align: center; background: #f5f5f5; margin: 0; }
                    .card { max-width: 400px; margin: 40px auto; background: white; border-radius: 16px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    h1 { color: #37474F; margin-bottom: 16px; }
                    p { color: #666; margin-bottom: 24px; line-height: 1.6; }
                    button { background: #37474F; color: white; border: none; padding: 12px 24px; border-radius: 8px; margin: 8px; cursor: pointer; font-size: 14px; transition: background 0.2s; }
                    button:hover { background: #263238; }
                    .info { background: #f8f9fa; padding: 12px; border-radius: 8px; margin-top: 16px; text-align: left; font-size: 12px; color: #666; }
                    .info strong { color: #37474F; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>${info.name}</h1>
                    <p>${info.description ?: "Web插件"}</p>
                    <button onclick="UINPlugin.callHost('toast', 'Hello from ${info.name}!')">📱 点我</button>
                    <button onclick="UINPlugin.callHost('finish', '')">❌ 关闭</button>
                    <div class="info">
                        <strong>插件信息</strong><br>
                        版本: ${info.versionName}<br>
                        作者: ${info.author ?: "未知"}<br>
                        ID: ${info.pluginId}<br>
                        类型: Web插件
                    </div>
                </div>
                <script>
                    console.log('Web插件已加载: ${info.name}');
                    console.log('插件信息:', JSON.parse(UINPlugin.getPluginInfo()));
                    console.log('设备信息:', JSON.parse(UINPlugin.getDeviceInfo()));
                    window.addEventListener('resume', function() { console.log('插件恢复'); });
                    window.addEventListener('pause', function() { console.log('插件暂停'); });
                    window.addEventListener('destroy', function() { console.log('插件销毁'); });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    override fun onResume() {
        super.onResume()
        if (!isDestroyed) {
            pluginManager?.onPluginResume(currentPluginId)
            
            // ✅ 从设置返回后检查权限状态
            if (isPermissionRequesting) {
                checkSpecialPermissionsAfterSettings()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isDestroyed) {
            pluginManager?.onPluginPause(currentPluginId)
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        super.onDestroy()
        pluginManager?.onPluginDestroy(currentPluginId)
        isPermissionRequesting = false
    }

    override fun onBackPressed() {
        if (pluginManager?.onPluginBackPressed(currentPluginId) == true) {
            return
        }

        webView?.let {
            if (it.canGoBack()) {
                it.goBack()
                return
            }
        }

        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val plugin = pluginManager?.getPluginInstance(currentPluginId)
        plugin?.onActivityResult(requestCode, resultCode, data)

        webView?.let {
            val js = """
                if(window.onActivityResult) {
                    window.onActivityResult($requestCode, $resultCode, ${data != null});
                }
            """.trimIndent()
            it.evaluateJavascript(js, null)
        }
    }

    fun setPluginTitle(title: String) {
        currentTitle = title
        runOnUiThread {
            supportActionBar?.title = title
        }
    }

    fun evaluateJavascript(script: String) {
        webView?.evaluateJavascript(script, null)
    }
}