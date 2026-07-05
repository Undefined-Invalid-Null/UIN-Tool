// app/src/main/java/com/UIN/Tool/plugin/PluginJSInterface.kt
package com.UIN.Tool.plugin

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PluginJSInterface(
    private val context: Context,
    private val pluginId: String,
    private val pluginInfo: PluginInfo
) {

    private val TAG = "PluginJSInterface"
    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null

    private val pendingPermissionCallbacks = mutableMapOf<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .cache(Cache(File(Constants.CACHE_DIR, "okhttp_cache"), Constants.CACHE_SIZE))
        .build()

    // ==================== 权限检查 ====================

    private fun hasPermission(permission: String): Boolean {
        return PermissionUtils.hasPermission(context, permission) ||
                PermissionUtils.hasSpecialPermission(context, permission)
    }

    private fun checkPermissionAndLog(permission: String, action: String): Boolean {
        val result = hasPermission(permission)
        if (!result) {
            Logger.w(TAG, "插件 $pluginId 缺少权限 $permission，无法执行 $action")
        }
        return result
    }

    // ==================== 基础功能 ====================

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
            else -> Logger.w(TAG, "未知调用: $action")
        }
    }

    @JavascriptInterface
    fun callPlugin(method: String, params: String?) {
        Logger.i(TAG, "调用插件方法: $method -> $params")
        val plugin = PluginManager.getInstance(context).getPluginInstance(pluginId)
        if (plugin != null) {
            Logger.d(TAG, "插件实例存在，转发方法调用: $method")
        } else {
            Logger.w(TAG, "插件实例不存在，无法调用方法: $method")
        }
    }

    // ==================== 网络请求 ====================

    @JavascriptInterface
    fun httpGet(url: String, callbackId: String) {
        if (!checkPermissionAndLog(android.Manifest.permission.INTERNET, "HTTP GET")) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }

        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }

        Logger.i(TAG, "HTTP GET: $url")

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
        if (!checkPermissionAndLog(android.Manifest.permission.INTERNET, "HTTP POST")) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }

        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }

        Logger.i(TAG, "HTTP POST: $url")

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = RequestBody.create(mediaType, jsonBody)

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

    // ==================== 文件系统 ====================

    @JavascriptInterface
    fun writeFile(fileName: String, content: String): Boolean {
        if (!checkPermissionAndLog(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, "写入文件")) {
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
        if (!checkPermissionAndLog(android.Manifest.permission.READ_EXTERNAL_STORAGE, "读取文件")) {
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
        return try {
            val file = File(path)
            file.exists()
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 传感器 ====================

    @JavascriptInterface
    fun startSensor(sensorType: String, callbackId: String) {
        if (!checkPermissionAndLog(android.Manifest.permission.VIBRATE, "传感器")) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少传感器权限\"}")
            return
        }

        if (sensorType.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"传感器类型不能为空\"}")
            return
        }

        if (sensorManager == null) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }

        sensorListener?.let {
            sensorManager?.unregisterListener(it)
        }

        val sensorTypeInt = getSensorType(sensorType)
        if (sensorTypeInt == -1) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"不支持的传感器类型: $sensorType\"}")
            return
        }

        val sensor = sensorManager?.getDefaultSensor(sensorTypeInt)
        if (sensor == null) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"设备不支持该传感器: $sensorType\"}")
            return
        }

        sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val data = JSONObject().apply {
                    put("success", true)
                    put("type", sensorType)
                    put("timestamp", event.timestamp)
                    put("accuracy", event.accuracy)

                    when (sensorTypeInt) {
                        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD -> {
                            put("x", event.values[0])
                            put("y", event.values[1])
                            put("z", event.values[2])
                        }
                        Sensor.TYPE_LIGHT -> put("lux", event.values[0])
                        Sensor.TYPE_PROXIMITY -> put("distance", event.values[0])
                        Sensor.TYPE_PRESSURE -> put("pressure", event.values[0])
                        else -> put("values", event.values)
                    }
                }
                sendCallback(callbackId, data.toString())
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                val data = JSONObject().apply {
                    put("type", "accuracy")
                    put("sensorType", sensorType)
                    put("accuracy", accuracy)
                }
                sendCallback(callbackId, data.toString())
            }
        }

        sensorManager?.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sendCallback(callbackId, "{\"success\":true,\"message\":\"传感器启动成功: $sensorType\"}")
        Logger.i(TAG, "启动传感器: $sensorType")
    }

    @JavascriptInterface
    fun stopSensor() {
        sensorListener?.let {
            sensorManager?.unregisterListener(it)
            sensorListener = null
            Logger.i(TAG, "停止所有传感器")
        }
    }

    @JavascriptInterface
    fun getAvailableSensors(): String {
        if (sensorManager == null) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }

        val result = JSONObject().apply {
            put("accelerometer", sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
            put("gyroscope", sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)
            put("magneticField", sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)
            put("light", sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) != null)
            put("proximity", sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null)
            put("pressure", sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null)
            put("temperature", sensorManager?.getDefaultSensor(Sensor.TYPE_TEMPERATURE) != null)
        }
        return result.toString()
    }

    private fun getSensorType(sensorType: String): Int {
        return when (sensorType.lowercase()) {
            "accelerometer" -> Sensor.TYPE_ACCELEROMETER
            "gyroscope" -> Sensor.TYPE_GYROSCOPE
            "magnetic", "magneticfield" -> Sensor.TYPE_MAGNETIC_FIELD
            "light" -> Sensor.TYPE_LIGHT
            "proximity" -> Sensor.TYPE_PROXIMITY
            "pressure" -> Sensor.TYPE_PRESSURE
            "temperature" -> Sensor.TYPE_TEMPERATURE
            else -> -1
        }
    }

    // ==================== 获取信息 ====================

    @JavascriptInterface
    fun getPluginInfo(): String {
        return pluginInfo.toJson()
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
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
    fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    // ==================== UI操作 ====================

    @JavascriptInterface
    fun setTitle(title: String) {
        Logger.i(TAG, "设置标题: $title")
        (context as? Activity)?.runOnUiThread {
            (context as? Activity)?.title = title
            if (context is PluginHostActivity) {
                context.setPluginTitle(title)
            }
        }
    }

    @JavascriptInterface
    fun setFullscreen(fullscreen: Boolean) {
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

    // ==================== 存储操作 ====================

    @JavascriptInterface
    fun setStorage(key: String, value: String) {
        if (key.isEmpty()) {
            Logger.e(TAG, "Storage key不能为空")
            return
        }
        context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
        Logger.i(TAG, "存储数据: $key = ${value.take(50)}")
    }

    @JavascriptInterface
    fun getStorage(key: String): String {
        if (key.isEmpty()) {
            Logger.e(TAG, "Storage key不能为空")
            return ""
        }
        return context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .getString(key, "") ?: ""
    }

    @JavascriptInterface
    fun removeStorage(key: String) {
        if (key.isEmpty()) return
        context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
        Logger.i(TAG, "删除数据: $key")
    }

    @JavascriptInterface
    fun clearStorage() {
        context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        Logger.i(TAG, "清空所有存储数据")
    }

    // ==================== 系统操作 ====================

    @JavascriptInterface
    fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Logger.i(TAG, "打开系统设置")
    }

    @JavascriptInterface
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Logger.i(TAG, "打开应用设置")
    }

    @JavascriptInterface
    fun checkPermission(permission: String): Boolean {
        if (permission.isEmpty()) return false
        return hasPermission(permission)
    }

    @JavascriptInterface
    fun requestPermission(permission: String, callbackId: String) {
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

    // ==================== 插件目录 ====================

    @JavascriptInterface
    fun getPluginDir(): String {
        val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
        Logger.i(TAG, "插件目录: ${pluginDir.absolutePath}")
        return pluginDir.absolutePath
    }

    // ==================== 高级功能 ====================

    @JavascriptInterface
    fun getBatteryInfo(): String {
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

    @JavascriptInterface
    fun takeScreenshot() {
        if (!checkPermissionAndLog(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, "截图")) {
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

    // ==================== 私有方法 ====================

    private fun sendCallback(callbackId: String, data: String) {
        if (callbackId.isEmpty()) return

        (context as? Activity)?.runOnUiThread {
            if (context is PluginHostActivity) {
                val js = """
                    if(window.UINPluginCallbacks && window.UINPluginCallbacks['$callbackId']) {
                        window.UINPluginCallbacks['$callbackId']($data);
                    }
                """.trimIndent()
                context.evaluateJavascript(js)
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
        if (!checkPermissionAndLog(android.Manifest.permission.VIBRATE, "震动")) {
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

    // ==================== 通过 PluginHostActivity 执行 JS ====================

    private fun evaluateJavascript(script: String) {
        if (context is PluginHostActivity) {
            context.evaluateJavascript(script)
        }
    }
}