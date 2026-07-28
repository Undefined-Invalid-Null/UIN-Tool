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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

enum class PluginUiState {
    LOADING,
    PERMISSION_DIALOG,
    SPECIAL_PERMISSION_GUIDE,
    PLUGIN_VIEW
}

class PluginHostActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PluginHostActivity"
        const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val BACKEND_START_TIMEOUT = 15000L
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }

    private lateinit var pluginManager: PluginManager
    private var pluginInfo: PluginInfo? = null
    private var currentPluginId: String = ""
    private lateinit var pluginContext: PluginContext

    private var isBackendReady = false
    private var backendPort = 0
    private var isBackendStarting = false
    private var backendTimeoutHandler: Handler? = null
    private var backendTimeoutRunnable: Runnable? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var uiState by mutableStateOf(PluginUiState.LOADING)
    private var isPendingPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            currentPluginId = savedInstanceState.getString("plugin_id") ?: ""
            uiState = PluginUiState.values()[savedInstanceState.getInt("ui_state", 0)]
            isPendingPermission = savedInstanceState.getBoolean("is_pending", false)
        }

        if (currentPluginId.isEmpty()) {
            currentPluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: ""
        }

        if (currentPluginId.isEmpty()) {
            Toast.makeText(this, "插件ID不能为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        pluginManager = ServiceLocator.getPluginManager()
        pluginInfo = pluginManager.getPluginInfo(currentPluginId)
        if (pluginInfo == null) {
            Toast.makeText(this, "插件不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val pluginDir = File(Constants.PLUGIN_DIR, currentPluginId)
        pluginContext = PluginContext(this, pluginDir.absolutePath)

        // ============ 权限状态管理 ============
        // 状态 0：未授权，显示弹窗
        // 状态 1：已处理（无论授权还是拒绝），不再弹窗
        // 状态 2：用户主动取消，下次仍弹窗

        val savedState = pluginContext.getPermissionState()
        Logger.d(TAG, "权限状态: $savedState")

        if (savedState == 1) {
            // 已处理，直接进入
            uiState = PluginUiState.PLUGIN_VIEW
            Logger.d(TAG, "✅ 已处理，直接进入")
        } else {
            // 状态 0 或 2：检查实际权限
            val missing = PluginPermissionManager.getMissingPermissions(this, currentPluginId)

            if (missing.isEmpty()) {
                // 权限已授予，设为1
                setPermissionState(1)
                uiState = PluginUiState.PLUGIN_VIEW
                Logger.d(TAG, "✅ 权限已授予，直接进入")
            } else {
                // 有缺失权限，显示弹窗
                val hasNormal = missing.any { !PluginPermissionManager.isSpecialPermission(it) }
                uiState = if (hasNormal) PluginUiState.PERMISSION_DIALOG else PluginUiState.SPECIAL_PERMISSION_GUIDE
                Logger.d(TAG, "❌ 显示权限弹窗")
            }
        }

        setContent {
            UINToolTheme {
                PluginHostScreen(
                    pluginId = currentPluginId,
                    pluginInfo = pluginInfo!!,
                    uiState = uiState,
                    onRequestPermissions = { perms ->
                        isPendingPermission = true
                        ActivityCompat.requestPermissions(this@PluginHostActivity, perms, REQUEST_CODE_PERMISSIONS)
                    },
                    onOpenSettings = { openAppSettings() },
                    onPermissionsGranted = {
                        // ✅ 用户点击授权 -> 设为1，不再弹窗
                        setPermissionState(1)
                        uiState = PluginUiState.PLUGIN_VIEW
                        isPendingPermission = false
                        Toast.makeText(this, "权限已处理", Toast.LENGTH_SHORT).show()
                        startBackendIfNeeded()
                    },
                    onPermissionsDenied = {
                        // ✅ 用户点击取消 -> 状态设为2，下次仍弹窗
                        setPermissionState(2)
                        isPendingPermission = false
                        Toast.makeText(this, "已取消权限请求", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onBackendReady = { port ->
                        backendPort = port
                        isBackendReady = true
                    }
                )
            }
        }

        if (uiState == PluginUiState.PLUGIN_VIEW) {
            startBackendIfNeeded()
        }
    }

    private fun setPermissionState(state: Int) {
        pluginContext.setPermissionState(state)
        Logger.d(TAG, "权限状态已写入: $state")
    }

    private fun startBackendIfNeeded() {
        if (pluginInfo!!.hasBackend() && !isBackendReady) {
            isBackendStarting = true
            pluginManager.startBackend(currentPluginId) { success, port, error ->
                isBackendStarting = false
                if (success) {
                    backendPort = port
                    isBackendReady = true
                    Logger.success(TAG, "后端就绪，端口: $port")
                } else {
                    Logger.e(TAG, "后端启动失败: $error")
                }
            }
            setupBackendTimeout()
        }
    }

    private fun setupBackendTimeout() {
        backendTimeoutHandler = Handler(Looper.getMainLooper())
        backendTimeoutRunnable = Runnable {
            if (isBackendStarting) {
                Logger.w(TAG, "后端启动超时")
                isBackendStarting = false
            }
        }
        backendTimeoutHandler?.postDelayed(backendTimeoutRunnable!!, BACKEND_START_TIMEOUT)
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 权限请求结果 ====================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_PERMISSIONS) return

        isPendingPermission = false

        val allGranted = permissions.indices.all { grantResults[it] == PackageManager.PERMISSION_GRANTED }

        // ✅ 不管是否全部授予，都设为1，不再弹窗
        setPermissionState(1)

        if (allGranted) {
            Toast.makeText(this, "✅ 所有权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            val denied = permissions.filterIndexed { index, _ -> grantResults[index] != PackageManager.PERMISSION_GRANTED }
            val deniedNames = denied.joinToString { PluginPermissionManager.getPermissionDisplayName(it) }
            Toast.makeText(this, "⚠️ 权限被拒绝: $deniedNames，部分功能可能不可用", Toast.LENGTH_LONG).show()
        }

        uiState = PluginUiState.PLUGIN_VIEW
        startBackendIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (isPendingPermission) {
            val missing = PluginPermissionManager.getMissingPermissions(this, currentPluginId)
            if (missing.isEmpty()) {
                isPendingPermission = false
                setPermissionState(1)
                uiState = PluginUiState.PLUGIN_VIEW
                Toast.makeText(this, "✅ 权限已授予", Toast.LENGTH_SHORT).show()
                startBackendIfNeeded()
            }
        }
    }

    // ==================== 对外接口 ====================

    fun evaluateJavascript(script: String) {}
    fun setPluginTitle(title: String) { supportActionBar?.title = title }
    fun getBackendPort(): Int = if (isBackendReady) backendPort else 0
    fun isBackendReady(): Boolean = isBackendReady

    fun callBackendApi(path: String, method: String = "GET", body: String? = null, callback: (Boolean, String?) -> Unit) {
        if (!isBackendReady) {
            callback(false, "后端未就绪")
            return
        }
        Thread {
            try {
                val url = "http://127.0.0.1:$backendPort$path"
                val builder = okhttp3.Request.Builder().url(url)
                when (method.uppercase()) {
                    "GET" -> builder.get()
                    "POST" -> {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = (body ?: "{}").toRequestBody(mediaType)
                        builder.post(requestBody)
                    }
                    else -> builder.get()
                }
                val response = okHttpClient.newCall(builder.build()).execute()
                val responseBody = response.body?.string()
                response.close()
                if (response.isSuccessful) {
                    callback(true, responseBody)
                } else {
                    callback(false, "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                callback(false, e.message)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        backendTimeoutHandler?.removeCallbacks(backendTimeoutRunnable!!)
        backendTimeoutHandler = null
        if (!(pluginInfo?.backendKeepAlive ?: false) && isBackendReady) {
            pluginManager.stopBackend(currentPluginId)
        }
        pluginManager.onPluginDestroy(currentPluginId)
    }

    override fun onBackPressed() {
        if (pluginManager.onPluginBackPressed(currentPluginId)) return
        super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("plugin_id", currentPluginId)
        outState.putInt("ui_state", uiState.ordinal)
        outState.putBoolean("is_pending", isPendingPermission)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentPluginId = savedInstanceState.getString("plugin_id", currentPluginId)
        uiState = PluginUiState.values()[savedInstanceState.getInt("ui_state", 0)]
        isPendingPermission = savedInstanceState.getBoolean("is_pending", false)
    }
}

// ==================== Compose UI ====================

@Composable
fun PluginHostScreen(
    pluginId: String,
    pluginInfo: PluginInfo,
    uiState: PluginUiState,
    onRequestPermissions: (Array<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit,
    onBackendReady: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    when (uiState) {
        PluginUiState.PLUGIN_VIEW -> {
            PluginContentView(pluginId, pluginInfo, context as? PluginHostActivity)
        }
        PluginUiState.PERMISSION_DIALOG -> {
            val missing = getMissingPermissions(context, pluginId)
            val normal = missing.filter { !PluginPermissionManager.isSpecialPermission(it) }
            val special = missing.filter { PluginPermissionManager.isSpecialPermission(it) }

            PermissionRequestDialog(
                pluginName = pluginInfo.name,
                normalPermissions = normal,
                specialPermissions = special,
                onAuthorize = {
                    if (normal.isNotEmpty()) {
                        onRequestPermissions(normal.toTypedArray())
                    } else if (special.isNotEmpty()) {
                        onOpenSettings()
                    }
                },
                onOpenSettings = onOpenSettings,
                onDismiss = onPermissionsDenied
            )
        }
        PluginUiState.SPECIAL_PERMISSION_GUIDE -> {
            val missing = getMissingPermissions(context, pluginId)
            val special = missing.filter { PluginPermissionManager.isSpecialPermission(it) }

            SpecialPermissionGuideDialog(
                permissions = special,
                onOpenSettings = onOpenSettings,
                onDismiss = onPermissionsDenied
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("加载中...", modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
fun PluginContentView(
    pluginId: String,
    pluginInfo: PluginInfo,
    hostActivity: PluginHostActivity?
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    if (pluginInfo.uiType == "web") {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
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
                    val jsInterface = PluginJSInterface(ctx, pluginId, pluginInfo)
                    addJavascriptInterface(jsInterface, "UINPlugin")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            hostActivity?.evaluateJavascript("if(window._onUINPluginReady) window._onUINPluginReady();")
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                            android.app.AlertDialog.Builder(ctx)
                                .setTitle("提示")
                                .setMessage(message)
                                .setPositiveButton("确定") { _, _ -> result?.confirm() }
                                .show()
                            return true
                        }
                    }
                    val entryPath = if (pluginInfo.entry.isNotEmpty()) pluginInfo.entry else "web/index.html"
                    val indexPath = File(Constants.PLUGIN_DIR, pluginId).absolutePath + "/$entryPath"
                    if (File(indexPath).exists()) {
                        loadUrl("file://$indexPath")
                    } else {
                        val defaultHtml = createDefaultHtml(pluginInfo)
                        loadDataWithBaseURL("file://${Constants.PLUGIN_DIR}/$pluginId/", defaultHtml, "text/html", "UTF-8", null)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        AndroidView(
            factory = { ctx ->
                val pluginManager = ServiceLocator.getPluginManager()
                val view = pluginManager.getPluginViewSync(pluginId, ctx, null)
                view ?: android.widget.TextView(ctx).apply {
                    text = "原生插件加载失败"
                    setTextColor(android.graphics.Color.RED)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ==================== 弹窗组件 ====================

@Composable
fun PermissionRequestDialog(
    pluginName: String,
    normalPermissions: List<String>,
    specialPermissions: List<String>,
    onAuthorize: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("权限说明", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("插件「$pluginName」需要以下权限：", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    normalPermissions.forEach { perm ->
                        PermissionItemRow(
                            icon = Icons.Default.CheckCircle,
                            text = PluginPermissionManager.getPermissionDisplayName(perm),
                            description = PluginPermissionManager.getPermissionDescription(perm),
                            isNormal = true
                        )
                    }
                    specialPermissions.forEach { perm ->
                        PermissionItemRow(
                            icon = Icons.Default.Settings,
                            text = PluginPermissionManager.getPermissionDisplayName(perm),
                            description = "⚠️ 特殊权限：${PluginPermissionManager.getPermissionDescription(perm)}",
                            isNormal = false
                        )
                    }
                }
                if (specialPermissions.isNotEmpty()) {
                    Text("⚠️ 特殊权限需在系统设置中手动开启", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    }

                    Button(
                        onClick = onAuthorize,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            if (normalPermissions.isNotEmpty()) Icons.Default.Check else Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (normalPermissions.isNotEmpty()) "授权" else "去设置",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpecialPermissionGuideDialog(
    permissions: List<String>,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFFFEBEE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("需要特殊权限", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("以下权限需在系统设置中手动开启：", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    permissions.forEach { perm ->
                        PermissionItemRow(
                            icon = Icons.Default.Settings,
                            text = PluginPermissionManager.getPermissionDisplayName(perm),
                            description = PluginPermissionManager.getPermissionDescription(perm),
                            isNormal = false
                        )
                    }
                }
                Text("点击「去设置」打开应用设置页面", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    }

                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "去设置",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    description: String,
    isNormal: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isNormal) MaterialTheme.colorScheme.primary else Color(0xFFFF9800)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

// ==================== 辅助函数 ====================

private fun getMissingPermissions(context: android.content.Context, pluginId: String): List<String> {
    val plugin = ServiceLocator.getPluginManager().getPluginInfo(pluginId) ?: return emptyList()
    return plugin.permissions.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }
}

private fun createDefaultHtml(info: PluginInfo): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${info.name}</title>
            <style>
                body{font-family:sans-serif;padding:20px;text-align:center;background:#f5f5f5;}
                .card{max-width:400px;margin:40px auto;background:white;border-radius:16px;padding:32px;box-shadow:0 4px 12px rgba(0,0,0,0.1);}
                button{background:#37474F;color:white;border:none;padding:12px 24px;border-radius:8px;margin:8px;cursor:pointer;font-size:14px;}
                button:hover{background:#263238;}
            </style>
        </head>
        <body>
            <div class="card">
                <h1>${info.name}</h1>
                <p>${info.description ?: "Web插件"}</p>
                <button onclick="UINPlugin.callHost('toast','Hello from Web Plugin!')">点我</button>
                <button onclick="UINPlugin.callHost('finish','')">关闭</button>
            </div>
        </body>
        </html>
    """.trimIndent()
}