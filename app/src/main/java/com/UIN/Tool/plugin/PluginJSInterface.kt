// plugin/PluginJSInterface.kt
package com.UIN.Tool.plugin

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.PermissionUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Web 插件 JS 接口 - 所有方法必须添加 @JavascriptInterface 注解
 */
class PluginJSInterface(
    private val context: Context,
    private val pluginId: String,
    private val pluginInfo: PluginInfo
) {

    companion object {
        private const val TAG = "PluginJSInterface"
    }

    private val pendingPermissionCallbacks = mutableMapOf<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .cache(Cache(File(Constants.CACHE_DIR, "okhttp_cache"), Constants.CACHE_SIZE))
        .build()

    // ============================================================
    // 基础功能
    // ============================================================

    @JavascriptInterface
    fun callHost(action: String, data: String?) {
        Logger.i(TAG, "JS调用: $action -> $data")
        val params = data ?: ""

        when (action) {
            "toast" -> showToast(params)
            "finish" -> closePlugin()
            "log" -> Logger.i("WebPlugin[$pluginId]", params)
            "alert" -> showAlert(params)
            "confirm" -> showConfirm(params)
            "vibrate" -> vibrate(params)
            "copy" -> copyToClipboard(params)
            "openUrl" -> openUrl(params)
            "share" -> share(params)
            "setTitle" -> setTitle(params)
            "setFullscreen" -> setFullscreen(params.toBoolean())
            else -> Logger.w(TAG, "未知调用: $action")
        }
    }

    @JavascriptInterface
    fun callPlugin(method: String, params: String?) {
        Logger.i(TAG, "调用插件方法: $method -> $params")
        // 由 PluginManager 处理
    }

    // ============================================================
    // ✅ 修复：所有 Native 接口必须添加 @JavascriptInterface
    // ============================================================

    @JavascriptInterface
    fun getPluginInfo(): String {
        Logger.d(TAG, "getPluginInfo() 被调用")
        return pluginInfo.toJson()
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        Logger.d(TAG, "getAppVersion() 被调用")
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        Logger.d(TAG, "getDeviceInfo() 被调用")
        val dm = context.resources.displayMetrics
        return JSONObject().apply {
            put("android", Build.VERSION.RELEASE)
            put("api", Build.VERSION.SDK_INT)
            put("device", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("product", Build.PRODUCT)
            put("screenWidth", dm.widthPixels)
            put("screenHeight", dm.heightPixels)
            put("screenDensity", dm.density)
            put("screenDensityDpi", dm.densityDpi)
        }.toString()
    }

    @JavascriptInterface
    fun getNetworkInfo(): String {
        Logger.d(TAG, "getNetworkInfo() 被调用")
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return JSONObject().apply {
            val isConnected = activeNetwork != null && activeNetwork.isConnected
            put("connected", isConnected)
            if (isConnected && activeNetwork != null) {
                put("type", activeNetwork.typeName ?: "")
                activeNetwork.subtypeName?.let { put("subtype", it) }
                put("isWifi", activeNetwork.type == ConnectivityManager.TYPE_WIFI)
                put("isMobile", activeNetwork.type == ConnectivityManager.TYPE_MOBILE)
            }
        }.toString()
    }

    @JavascriptInterface
    fun getBackendStatus(): String {
        Logger.d(TAG, "getBackendStatus() 被调用")
        val activity = context as? PluginHostActivity
        if (activity != null) {
            val port = activity.getBackendPort()
            if (port > 0) {
                return "running:$port"
            } else {
                return "starting"
            }
        }
        return "unknown"
    }

    @JavascriptInterface
    fun getPluginDir(): String {
        Logger.d(TAG, "getPluginDir() 被调用")
        val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
        return pluginDir.absolutePath
    }

    @JavascriptInterface
    fun getCurrentTime(): String {
        Logger.d(TAG, "getCurrentTime() 被调用")
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    @JavascriptInterface
    fun getBatteryInfo(): String {
        Logger.d(TAG, "getBatteryInfo() 被调用")
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)

            JSONObject().apply {
                put("level", level)
                put("isCharging", status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("level", 0)
                put("isCharging", false)
            }.toString()
        }
    }

    // ============================================================
    // Storage 操作
    // ============================================================

    @JavascriptInterface
    fun setStorage(key: String, value: String) {
        Logger.d(TAG, "setStorage() 被调用: $key")
        if (key.isEmpty()) {
            Logger.e(TAG, "Storage key不能为空")
            return
        }
        context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    @JavascriptInterface
    fun getStorage(key: String): String {
        Logger.d(TAG, "getStorage() 被调用: $key")
        if (key.isEmpty()) {
            Logger.e(TAG, "Storage key不能为空")
            return ""
        }
        return context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .getString(key, "") ?: ""
    }

    @JavascriptInterface
    fun removeStorage(key: String) {
        Logger.d(TAG, "removeStorage() 被调用: $key")
        if (key.isEmpty()) return
        context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    @JavascriptInterface
    fun clearStorage() {
        Logger.d(TAG, "clearStorage() 被调用")
        context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    // ============================================================
    // HTTP API 调用（供 JS 使用）
    // ============================================================

    @JavascriptInterface
    fun callBackendApi(path: String, method: String, body: String?, callbackId: String) {
        Logger.d(TAG, "callBackendApi() 被调用: $path")
        val activity = context as? PluginHostActivity
        if (activity == null) {
            sendCallback(callbackId, "{\"error\":\"无法获取Activity\"}")
            return
        }

        activity.callBackendApi(path, method, body) { success, response ->
            val result = JSONObject().apply {
                put("success", success)
                put("data", response ?: "")
            }
            sendCallback(callbackId, result.toString())
        }
    }

    @JavascriptInterface
    fun httpGet(url: String, callbackId: String) {
        Logger.d(TAG, "httpGet() 被调用: $url")
        if (!hasPermission(android.Manifest.permission.INTERNET)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }

        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "UIN-Tool-WebPlugin/$pluginId")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                sendCallback(callbackId, "{\"success\":false,\"error\":\"${e.message}\"}")
                Logger.e(TAG, "GET请求失败", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    val result = JSONObject().apply {
                        put("success", true)
                        put("statusCode", it.code)
                        put("data", body)
                    }
                    sendCallback(callbackId, result.toString())
                }
            }
        })
    }

    @JavascriptInterface
    fun httpPost(url: String, jsonBody: String, callbackId: String) {
        Logger.d(TAG, "httpPost() 被调用: $url")
        if (!hasPermission(android.Manifest.permission.INTERNET)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }

        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", "UIN-Tool-WebPlugin/$pluginId")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                sendCallback(callbackId, "{\"success\":false,\"error\":\"${e.message}\"}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    val result = JSONObject().apply {
                        put("success", true)
                        put("statusCode", it.code)
                        put("data", body)
                    }
                    sendCallback(callbackId, result.toString())
                }
            }
        })
    }

    // ============================================================
    // 文件系统
    // ============================================================

    @JavascriptInterface
    fun writeFile(fileName: String, content: String): Boolean {
        Logger.d(TAG, "writeFile() 被调用: $fileName")
        if (!hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return false
        }

        if (fileName.isEmpty()) {
            Logger.e(TAG, "文件名不能为空")
            return false
        }

        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            if (!pluginDir.exists()) {
                pluginDir.mkdirs()
            }

            val file = File(pluginDir, fileName)
            if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                Logger.e(TAG, "非法的文件路径: $fileName")
                return false
            }

            file.writeText(content)
            Logger.i(TAG, "写入文件成功: $fileName")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "写入文件失败: $fileName", e)
            false
        }
    }

    @JavascriptInterface
    fun readFile(fileName: String): String? {
        Logger.d(TAG, "readFile() 被调用: $fileName")
        if (!hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return null
        }

        if (fileName.isEmpty()) {
            Logger.e(TAG, "文件名不能为空")
            return null
        }

        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val file = File(pluginDir, fileName)

            if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                Logger.e(TAG, "非法的文件路径: $fileName")
                return null
            }

            if (!file.exists()) return null
            file.readText()
        } catch (e: Exception) {
            Logger.e(TAG, "读取文件失败: $fileName", e)
            null
        }
    }

    @JavascriptInterface
    fun deleteFile(fileName: String): Boolean {
        Logger.d(TAG, "deleteFile() 被调用: $fileName")
        if (fileName.isEmpty()) return false

        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val file = File(pluginDir, fileName)

            if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                Logger.e(TAG, "非法的文件路径: $fileName")
                return false
            }

            file.delete()
        } catch (e: Exception) {
            Logger.e(TAG, "删除文件失败: $fileName", e)
            false
        }
    }

    @JavascriptInterface
    fun listFiles(dirPath: String?): Array<String> {
        Logger.d(TAG, "listFiles() 被调用: $dirPath")
        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            val targetDir = if (dirPath.isNullOrEmpty()) pluginDir else File(pluginDir, dirPath)

            if (!targetDir.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                Logger.e(TAG, "非法的目录路径: $dirPath")
                return emptyArray()
            }

            if (!targetDir.exists() || !targetDir.isDirectory) return emptyArray()
            targetDir.listFiles()?.map { it.name }?.toTypedArray() ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    @JavascriptInterface
    fun fileExists(path: String): Boolean {
        Logger.d(TAG, "fileExists() 被调用: $path")
        return try {
            val file = File(path)
            file.exists()
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    // 权限
    // ============================================================

    @JavascriptInterface
    fun checkPermission(permission: String): Boolean {
        Logger.d(TAG, "checkPermission() 被调用: $permission")
        if (permission.isEmpty()) return false
        return hasPermission(permission)
    }

    @JavascriptInterface
    fun requestPermission(permission: String, callbackId: String) {
        Logger.d(TAG, "requestPermission() 被调用: $permission")
        if (permission.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"权限名不能为空\"}")
            return
        }

        if (PermissionUtils.isSpecialPermission(permission)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"特殊权限需要在系统设置中手动开启\"}")
            return
        }

        (context as? Activity)?.let { activity ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingPermissionCallbacks[permission] = callbackId
                activity.requestPermissions(arrayOf(permission), 1002)
                Logger.i(TAG, "请求权限: $permission")
            } else {
                sendCallback(callbackId, "{\"success\":true,\"message\":\"权限已授予\"}")
            }
        } ?: run {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"无法获取Activity上下文\"}")
        }
    }

    // ============================================================
    // 系统操作
    // ============================================================

    @JavascriptInterface
    fun openSettings() {
        Logger.d(TAG, "openSettings() 被调用")
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun openAppSettings() {
        Logger.d(TAG, "openAppSettings() 被调用")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun takeScreenshot() {
        Logger.d(TAG, "takeScreenshot() 被调用")
        if (!hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return
        }

        (context as? Activity)?.let { activity ->
            try {
                val rootView = activity.window.decorView.rootView
                rootView.isDrawingCacheEnabled = true
                val bitmap = rootView.drawingCache

                if (bitmap != null) {
                    val dir = File(Constants.DOWNLOAD_DIR, "screenshots")
                    if (!dir.exists()) dir.mkdirs()

                    val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
                    java.io.FileOutputStream(file).use { fos ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                    }

                    showToast("截图已保存: ${file.absolutePath}")
                    Logger.i(TAG, "截图保存成功: ${file.absolutePath}")
                }
                rootView.isDrawingCacheEnabled = false
            } catch (e: Exception) {
                Logger.e(TAG, "截图失败", e)
                showToast("截图失败: ${e.message}")
            }
        }
    }

    // ============================================================
    // 私有方法
    // ============================================================

    private fun hasPermission(permission: String): Boolean {
        return PermissionUtils.hasPermission(context, permission) ||
                PermissionUtils.hasSpecialPermission(context, permission)
    }

    private fun sendCallback(callbackId: String, data: String) {
        if (callbackId.isEmpty()) return

        (context as? Activity)?.runOnUiThread {
            val activity = context
            if (activity is PluginHostActivity) {
                val js = """
                    if(window.UINPluginCallbacks && window.UINPluginCallbacks['$callbackId']) {
                        window.UINPluginCallbacks['$callbackId']($data);
                    }
                """.trimIndent()
                activity.evaluateJavascript(js)
            }
        }
    }

    private fun showToast(message: String) {
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun closePlugin() {
        if (context is Activity) {
            context.finish()
            Logger.i(TAG, "关闭插件")
        }
    }

    private fun showAlert(message: String) {
        (context as? Activity)?.runOnUiThread {
            AlertDialog.Builder(context)
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun showConfirm(message: String) {
        (context as? Activity)?.runOnUiThread {
            AlertDialog.Builder(context)
                .setTitle("确认")
                .setMessage(message)
                .setPositiveButton("确定") { _, _ -> }
                .setNegativeButton("取消") { _, _ -> }
                .show()
        }
    }

    private fun vibrate(durationMs: String) {
        if (!hasPermission(android.Manifest.permission.VIBRATE)) {
            return
        }

        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val duration = durationMs.toLongOrNull() ?: 200

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        duration,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                vibrator.vibrate(duration)
            }
            Logger.i(TAG, "震动: ${duration}ms")
        } catch (e: Exception) {
            Logger.e(TAG, "震动失败: ${e.message}")
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isEmpty()) {
            showToast("内容为空")
            return
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("plugin_text", text)
            clipboard.setPrimaryClip(clip)
            showToast("已复制到剪贴板")
            Logger.i(TAG, "复制到剪贴板: ${text.take(50)}...")
        } catch (e: Exception) {
            Logger.e(TAG, "复制失败", e)
            showToast("复制失败: ${e.message}")
        }
    }

    private fun openUrl(url: String) {
        if (url.isEmpty()) {
            showToast("URL不能为空")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Logger.i(TAG, "打开链接: $url")
        } catch (e: Exception) {
            showToast("无法打开链接")
            Logger.e(TAG, "打开链接失败: ${e.message}")
        }
    }

    private fun share(text: String) {
        if (text.isEmpty()) {
            showToast("内容为空")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享"))
            Logger.i(TAG, "分享: ${text.take(50)}...")
        } catch (e: Exception) {
            Logger.e(TAG, "分享失败", e)
            showToast("分享失败: ${e.message}")
        }
    }

    private fun setTitle(title: String) {
        (context as? Activity)?.runOnUiThread {
            (context as? Activity)?.title = title
            if (context is PluginHostActivity) {
                context.setPluginTitle(title)
            }
        }
    }

    private fun setFullscreen(fullscreen: Boolean) {
        (context as? Activity)?.runOnUiThread {
            if (fullscreen) {
                context.window?.decorView?.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
            } else {
                context.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // ============================================================
    // 权限请求结果处理
    // ============================================================

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == 1002) {
            permissions.forEachIndexed { index, permission ->
                val callbackId = pendingPermissionCallbacks.remove(permission)
                if (callbackId != null) {
                    val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
                    sendCallback(
                        callbackId,
                        "{\"success\":$granted,\"message\":\"${if (granted) "权限已授予" else "权限被拒绝"}\"}"
                    )
                    Logger.i(TAG, "权限请求结果: $permission -> ${if (granted) "已授予" else "被拒绝"}")
                }
            }
        }
    }
}