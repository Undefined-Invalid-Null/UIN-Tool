package com.UIN.Tool.plugin

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Constants
import com.UIN.Tool.utils.PermissionUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Web 插件 JS 接口 - 完整数据获取版
 * 只包含插件自身无法获取的设备和系统数据
 */
class PluginJSInterface(
    private val context: Context,
    private val pluginId: String,
    private val pluginInfo: PluginInfo
) {

    companion object {
        private const val TAG = "PluginJSInterface"
        private const val NOTIFICATION_CHANNEL_ID = "plugin_notification_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "插件通知"
    }

    private val pendingPermissionCallbacks = mutableMapOf<String, String>()
    private var isMigrated = false
    private var notificationManager: NotificationManager? = null
    private var activeSensorListener: SensorEventListener? = null
    private var activeSensorType = ""
    private var activeSensorCallbackId = ""

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .cache(Cache(File(Constants.CACHE_DIR, "okhttp_cache"), Constants.CACHE_SIZE))
        .build()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "插件通知"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun getPluginContext(): PluginContext? {
        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
            PluginContext(context, pluginDir.absolutePath)
        } catch (e: Exception) {
            Logger.e(TAG, "获取PluginContext失败", e)
            null
        }
    }

    private fun getActivity(): Activity? = context as? Activity

    private fun ensureMigration() {
        if (isMigrated) return
        try {
            val oldPrefs = context.getSharedPreferences("web_plugin_$pluginId", Context.MODE_PRIVATE)
            val oldData = oldPrefs.all
            if (oldData.isNotEmpty()) {
                Logger.i(TAG, "迁移旧数据: ${oldData.size} 条")
                val pctx = getPluginContext()
                if (pctx != null) {
                    oldData.forEach { (key, value) ->
                        when (value) {
                            is String -> pctx.putString(key, value)
                            is Boolean -> pctx.putBoolean(key, value)
                            is Int -> pctx.putInt(key, value)
                            is Float -> pctx.putFloat(key, value)
                            is Long -> pctx.putLong(key, value)
                            else -> {}
                        }
                    }
                    oldPrefs.edit().clear().apply()
                    Logger.success(TAG, "迁移完成")
                }
            }
            isMigrated = true
        } catch (e: Exception) {
            Logger.e(TAG, "迁移旧数据失败", e)
            isMigrated = true
        }
    }

    // ==================== 基础功能 ====================

    @JavascriptInterface
    fun callHost(action: String, data: String?) {
        Logger.i(TAG, "JS调用: $action -> $data")
        val params = data ?: ""
        when (action) {
            "toast" -> showToast(params)
            "toastLong" -> showToastLong(params)
            "finish" -> closePlugin()
            "log" -> Logger.i("WebPlugin[$pluginId]", params)
            "logError" -> Logger.e("WebPlugin[$pluginId]", params)
            "logWarning" -> Logger.w("WebPlugin[$pluginId]", params)
            "alert" -> showAlert(params)
            "confirm" -> showConfirm(params)
            "vibrate" -> vibrate(params)
            "copy" -> copyToClipboard(params)
            "openUrl" -> openUrl(params)
            "share" -> share(params)
            "setTitle" -> setTitle(params)
            "setFullscreen" -> setFullscreen(params.toBoolean())
            "setKeepScreenOn" -> setKeepScreenOn(params.toBoolean())
            "sendNotification" -> sendNotification(params, "")    
                    "takeScreenshot" -> takeScreenshot()
            else -> Logger.w(TAG, "未知调用: $action")
        }
    }

    @JavascriptInterface
    fun callPlugin(method: String, params: String?) {
        Logger.i(TAG, "调用插件方法: $method -> $params")
    }

    // ==================== 1. 设备标识 ====================

    @JavascriptInterface
    fun getDeviceId(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    tm.deviceId ?: "unknown"
                } else {
                    "permission_denied"
                }
            } else {
                tm.deviceId ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    @JavascriptInterface
    fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    @JavascriptInterface
    fun getSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial() ?: "unknown"
            } else {
                Build.SERIAL ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    @JavascriptInterface
    fun getMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val hardwareAddress = ni.hardwareAddress
                if (hardwareAddress != null && hardwareAddress.isNotEmpty()) {
                    return hardwareAddress.joinToString(":") { "%02X".format(it) }
                }
            }
            "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    @JavascriptInterface
    fun getFingerprint(): String = Build.FINGERPRINT

    @JavascriptInterface
    fun getHardwareInfo(): String {
        return JSONObject().apply {
            put("hardware", Build.HARDWARE)
            put("board", Build.BOARD)
            put("bootloader", Build.BOOTLOADER)
            put("radio", Build.getRadioVersion() ?: "unknown")
            put("cpu_abi", Build.CPU_ABI)
            put("cpu_abi2", Build.CPU_ABI2)
            put("supported_abis", Build.SUPPORTED_ABIS.joinToString(","))
        }.toString()
    }

    @JavascriptInterface
    fun getBootTime(): Long {
        return try {
            File("/proc/stat").readText().lines()
                .firstOrNull { it.startsWith("btime ") }
                ?.substringAfter("btime ")
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    @JavascriptInterface
    fun getUptime(): Long = System.currentTimeMillis() - getBootTime()

    // ==================== 2. 设备信息 ====================

    @JavascriptInterface
    fun getPluginInfo(): String = pluginInfo.toJson()

    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    @JavascriptInterface
    fun getAppVersionCode(): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    @JavascriptInterface
    fun getPluginVersion(): String = pluginInfo.versionName
    @JavascriptInterface
    fun getPluginVersionCode(): Int = pluginInfo.version
    @JavascriptInterface
    fun getHostVersion(): String = getAppVersion()

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
            put("board", Build.BOARD)
            put("hardware", Build.HARDWARE)
            put("screenWidth", dm.widthPixels)
            put("screenHeight", dm.heightPixels)
            put("screenDensity", dm.density)
            put("screenDensityDpi", dm.densityDpi)
            put("packageName", context.packageName)
        }.toString()
    }

    @JavascriptInterface
    fun getDeviceModel(): String = Build.MODEL
    @JavascriptInterface
    fun getAndroidVersion(): String = Build.VERSION.RELEASE
    @JavascriptInterface
    fun getApiLevel(): Int = Build.VERSION.SDK_INT

    @JavascriptInterface
    fun getScreenSize(): String {
        val dm = context.resources.displayMetrics
        return JSONObject().apply {
            put("width", dm.widthPixels)
            put("height", dm.heightPixels)
            put("widthDp", dm.widthPixels / dm.density)
            put("heightDp", dm.heightPixels / dm.density)
        }.toString()
    }

    @JavascriptInterface
    fun getScreenDensity(): Float = context.resources.displayMetrics.density

    @JavascriptInterface
    fun getTotalMemory(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return formatFileSize(mi.totalMem)
    }

    @JavascriptInterface
    fun getFreeMemory(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return formatFileSize(mi.availMem)
    }

    @JavascriptInterface
    fun getMemoryUsage(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val used = mi.totalMem - mi.availMem
        return JSONObject().apply {
            put("total", formatFileSize(mi.totalMem))
            put("used", formatFileSize(used))
            put("free", formatFileSize(mi.availMem))
            put("percentage", (used.toFloat() / mi.totalMem * 100).roundToInt())
        }.toString()
    }

    @JavascriptInterface
    fun getCpuInfo(): String {
        return try {
            val sb = StringBuilder()
            val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            "无法获取CPU信息"
        }
    }

    @JavascriptInterface
    fun getBuildInfo(): String {
        return JSONObject().apply {
            put("brand", Build.BRAND)
            put("device", Build.DEVICE)
            put("model", Build.MODEL)
            put("product", Build.PRODUCT)
            put("manufacturer", Build.MANUFACTURER)
            put("hardware", Build.HARDWARE)
            put("board", Build.BOARD)
            put("host", Build.HOST)
            put("id", Build.ID)
            put("tags", Build.TAGS)
            put("type", Build.TYPE)
            put("user", Build.USER)
            put("display", Build.DISPLAY)
            put("fingerprint", Build.FINGERPRINT)
            put("time", Build.TIME)
            put("radioVersion", Build.getRadioVersion() ?: "unknown")
            put("bootloader", Build.BOOTLOADER)
        }.toString()
    }

    // ==================== 3. 传感器 ====================

    private fun getSensorValue(type: Int, name: String): String {
        return try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(type)
            if (sensor == null) return "{\"error\":\"无${name}传感器\"}"
            // 使用默认值（实际传感器值需要通过监听器获取，这里返回传感器信息）
            JSONObject().apply {
                put("name", sensor.name)
                put("vendor", sensor.vendor)
                put("version", sensor.version)
                put("maxRange", sensor.maximumRange)
                put("resolution", sensor.resolution)
                put("power", sensor.power)
                put("available", true)
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    @JavascriptInterface
    fun getAccelerometer(): String = getSensorValue(Sensor.TYPE_ACCELEROMETER, "加速度计")

    @JavascriptInterface
    fun getGyroscope(): String = getSensorValue(Sensor.TYPE_GYROSCOPE, "陀螺仪")

    @JavascriptInterface
    fun getLightSensor(): String = getSensorValue(Sensor.TYPE_LIGHT, "光线")

    @JavascriptInterface
    fun getProximitySensor(): String = getSensorValue(Sensor.TYPE_PROXIMITY, "距离")

    @JavascriptInterface
    fun getMagneticField(): String = getSensorValue(Sensor.TYPE_MAGNETIC_FIELD, "磁场")

    @JavascriptInterface
    fun getOrientation(): String = getSensorValue(Sensor.TYPE_ORIENTATION, "方向")

    @JavascriptInterface
    fun getPressureSensor(): String = getSensorValue(Sensor.TYPE_PRESSURE, "气压")

    @JavascriptInterface
    fun getTemperatureSensor(): String = getSensorValue(Sensor.TYPE_AMBIENT_TEMPERATURE, "温度")

    @JavascriptInterface
    fun getHumiditySensor(): String = getSensorValue(Sensor.TYPE_RELATIVE_HUMIDITY, "湿度")

    @JavascriptInterface
    fun getAvailableSensors(): String {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return JSONObject().apply {
            put("accelerometer", sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
            put("gyroscope", sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)
            put("magnetic", sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)
            put("light", sm.getDefaultSensor(Sensor.TYPE_LIGHT) != null)
            put("proximity", sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null)
            put("pressure", sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null)
        }.toString()
    }

    @JavascriptInterface
    fun startSensor(type: String, callbackId: String) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stopSensor()
        val sensorType = when (type) {
            "accelerometer" -> Sensor.TYPE_ACCELEROMETER
            "gyroscope" -> Sensor.TYPE_GYROSCOPE
            "magnetic" -> Sensor.TYPE_MAGNETIC_FIELD
            "light" -> Sensor.TYPE_LIGHT
            "proximity" -> Sensor.TYPE_PROXIMITY
            "pressure" -> Sensor.TYPE_PRESSURE
            else -> -1
        }
        if (sensorType < 0) {
            sendCallback(callbackId, errJson("未知传感器类型: $type"))
            return
        }
        val sensor = sm.getDefaultSensor(sensorType) ?: run {
            sendCallback(callbackId, errJson("传感器不可用: $type"))
            return
        }
        activeSensorType = type
        activeSensorCallbackId = callbackId
        activeSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val id = activeSensorCallbackId
                if (id.isEmpty()) return
                sendCallback(id, JSONObject().apply {
                    put("success", true)
                    put("timestamp", event.timestamp)
                    put("accuracy", event.accuracy)
                    when (activeSensorType) {
                        "accelerometer", "gyroscope", "magnetic" -> {
                            put("x", event.values[0])
                            put("y", event.values[1])
                            put("z", event.values[2])
                        }
                        "light" -> put("lux", event.values[0])
                        "proximity" -> put("distance", event.values[0])
                        "pressure" -> put("pressure", event.values[0])
                        else -> {
                            val arr = JSONArray()
                            event.values.forEach { arr.put(it) }
                            put("values", arr)
                        }
                    }
                }.toString())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                val id = activeSensorCallbackId
                if (id.isEmpty()) return
                sendCallback(id, JSONObject().apply {
                    put("type", "accuracy")
                    put("accuracy", accuracy)
                }.toString())
            }
        }
        sm.registerListener(activeSensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sendCallback(callbackId, JSONObject().apply {
            put("success", true)
            put("message", "传感器已启动")
            put("sensor", sensor.name)
        }.toString())
        Logger.i(TAG, "启动传感器: $type")
    }

    @JavascriptInterface
    fun stopSensor() {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        activeSensorListener?.let { sm.unregisterListener(it) }
        activeSensorListener = null
        activeSensorCallbackId = ""
        Logger.i(TAG, "停止传感器")
    }

    // ==================== 4. 位置 ====================

    @JavascriptInterface
    fun getLocation(): String {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var best: Location? = null
            providers.forEach { provider ->
                val location = lm.getLastKnownLocation(provider)
                if (location != null && (best == null || location.accuracy < best.accuracy)) {
                    best = location
                }
            }
            if (best == null) return "{\"error\":\"无法获取位置\"}"
            JSONObject().apply {
                put("latitude", best.latitude)
                put("longitude", best.longitude)
                put("accuracy", best.accuracy)
                put("altitude", best.altitude)
                put("speed", best.speed)
                put("bearing", best.bearing)
                put("provider", best.provider)
                put("time", best.time)
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    @JavascriptInterface
    fun getAddress(lat: Double, lng: Double): String {
        return try {
            val geocoder = android.location.Geocoder(context)
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses == null || addresses.isEmpty()) {
                return "{\"error\":\"无法获取地址\"}"
            }
            val addr = addresses[0]
            JSONObject().apply {
                put("address", addr.getAddressLine(0) ?: "")
                put("country", addr.countryName ?: "")
                put("countryCode", addr.countryCode ?: "")
                put("state", addr.adminArea ?: "")
                put("city", addr.locality ?: "")
                put("district", addr.subLocality ?: "")
                put("postalCode", addr.postalCode ?: "")
                put("street", addr.thoroughfare ?: "")
                put("latitude", addr.latitude)
                put("longitude", addr.longitude)
                put("featureName", addr.featureName ?: "")
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    // ==================== 5. 屏幕/显示 ====================

    @JavascriptInterface
    fun getScreenBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            -1
        }
    }

    @JavascriptInterface
    fun getAutoBrightness(): Boolean {
        return try {
            val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun getDisplayInfo(): String {
        val dm = context.resources.displayMetrics
        return JSONObject().apply {
            put("width", dm.widthPixels)
            put("height", dm.heightPixels)
            put("widthDp", dm.widthPixels / dm.density)
            put("heightDp", dm.heightPixels / dm.density)
            put("density", dm.density)
            put("densityDpi", dm.densityDpi)
            put("scaledDensity", dm.scaledDensity)
            put("xdpi", dm.xdpi)
            put("ydpi", dm.ydpi)
        }.toString()
    }

    @JavascriptInterface
    fun getFontScale(): Float = context.resources.configuration.fontScale

    // ==================== 6. 系统设置状态 ====================

    @JavascriptInterface
    fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isBluetoothOn(): Boolean {
        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            adapter != null && adapter.isEnabled
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isWifiOn(): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isMobileDataOn(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isLocationOn(): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isNfcOn(): Boolean {
        return try {
            val nfcManager = context.getSystemService(Context.NFC_SERVICE) as android.nfc.NfcManager
            val adapter = nfcManager.defaultAdapter
            adapter != null && adapter.isEnabled
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isAutoRotateOn(): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isDndOn(): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 7. 存储信息 ====================

    @JavascriptInterface
    fun getTotalStorage(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.totalBytes
            } else {
                stat.blockCount.toLong() * stat.blockSize
            }
            formatFileSize(total)
        } catch (e: Exception) {
            "未知"
        }
    }

    @JavascriptInterface
    fun getFreeStorage(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val free = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                stat.availableBlocks.toLong() * stat.blockSize
            }
            formatFileSize(free)
        } catch (e: Exception) {
            "未知"
        }
    }

    @JavascriptInterface
    fun getUsedStorage(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.totalBytes
            } else {
                stat.blockCount.toLong() * stat.blockSize
            }
            val free = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                stat.availableBlocks.toLong() * stat.blockSize
            }
            formatFileSize(total - free)
        } catch (e: Exception) {
            "未知"
        }
    }

    @JavascriptInterface
    fun getStoragePercentage(): Int {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.totalBytes
            } else {
                stat.blockCount.toLong() * stat.blockSize
            }
            val free = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                stat.availableBlocks.toLong() * stat.blockSize
            }
            ((total - free).toFloat() / total * 100).roundToInt()
        } catch (e: Exception) {
            0
        }
    }

    // ==================== 8. 网络数据 ====================

    @JavascriptInterface
    fun getNetworkInfo(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
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
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

    @JavascriptInterface
    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected && activeNetwork.type == ConnectivityManager.TYPE_WIFI
    }

    @JavascriptInterface
    fun isMobileConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected && activeNetwork.type == ConnectivityManager.TYPE_MOBILE
    }

    @JavascriptInterface
    fun getIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        if (hostAddress.contains(".")) {
                            return hostAddress
                        }
                    }
                }
            }
            "0.0.0.0"
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }

    @JavascriptInterface
    fun getWifiInfo(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wm.connectionInfo
            if (wifiInfo == null) return "{\"error\":\"无WiFi连接\"}"
            JSONObject().apply {
                put("ssid", wifiInfo.ssid ?: "")
                put("bssid", wifiInfo.bssid ?: "")
                put("rssi", wifiInfo.rssi)
                put("linkSpeed", wifiInfo.linkSpeed)
                put("frequency", wifiInfo.frequency)
                put("ip", wifiInfo.ipAddress?.let { 
                    val ip = it.toInt()
                    String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
                } ?: "")
                put("networkId", wifiInfo.networkId)
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    @JavascriptInterface
    fun getSignalStrength(): Int {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wm.connectionInfo
            wifiInfo?.rssi ?: -100
        } catch (e: Exception) {
            -100
        }
    }

    @JavascriptInterface
    fun getOperatorInfo(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            JSONObject().apply {
                put("networkOperator", tm.networkOperator ?: "")
                put("networkOperatorName", tm.networkOperatorName ?: "")
                put("networkCountry", tm.networkCountryIso ?: "")
                put("simOperator", tm.simOperator ?: "")
                put("simOperatorName", tm.simOperatorName ?: "")
                put("simCountry", tm.simCountryIso ?: "")
                put("phoneType", tm.phoneType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    put("dataState", tm.dataState)
                    put("dataNetworkType", tm.dataNetworkType)
                }
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    // ==================== 9. 电池 ====================

    @JavascriptInterface
    fun getBatteryInfo(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            }
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
            } else {
                val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            }
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            JSONObject().apply {
                put("level", level)
                put("isCharging", isCharging)
                put("status", when (status) {
                    android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    android.os.BatteryManager.BATTERY_STATUS_FULL -> "full"
                    android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                    else -> "unknown"
                })
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    // ==================== 10. 音频 ====================

    @JavascriptInterface
    fun getVolume(): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            0
        }
    }

    @JavascriptInterface
    fun getMaxVolume(): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            0
        }
    }

    @JavascriptInterface
    fun getVolumePercentage(): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max == 0) 0 else (current.toFloat() / max * 100).roundToInt()
        } catch (e: Exception) {
            0
        }
    }

    @JavascriptInterface
    fun isHeadphonesConnected(): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isWiredHeadsetOn || am.isBluetoothA2dpOn
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 11. 时间/日期 ====================

    @JavascriptInterface
    fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    @JavascriptInterface
    fun getCurrentTimestamp(): Long = System.currentTimeMillis()

    @JavascriptInterface
    fun getTimezone(): String = TimeZone.getDefault().id

    @JavascriptInterface
    fun getTimezoneOffset(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000

    @JavascriptInterface
    fun isDaylightSaving(): Boolean = TimeZone.getDefault().inDaylightTime(Date())

    @JavascriptInterface
    fun getSystemTime(): Long = System.currentTimeMillis()

    // ==================== 12. 系统语言 ====================

    @JavascriptInterface
    fun getSystemLanguage(): String = Locale.getDefault().language

    @JavascriptInterface
    fun getSystemCountry(): String = Locale.getDefault().country

    @JavascriptInterface
    fun getLocale(): String = Locale.getDefault().toString()

    @JavascriptInterface
    fun getDisplayLanguage(): String = Locale.getDefault().displayLanguage

    // ==================== 13. 应用管理 ====================

    @JavascriptInterface
    fun isAppInstalled(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JavascriptInterface
    fun getAppName(packageName: String): String {
        if (packageName.isEmpty()) return ""
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    @JavascriptInterface
    fun getAppVersion(packageName: String): String {
        if (packageName.isEmpty()) return ""
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun openApp(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun getAppInfo(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            JSONObject().apply {
                put("packageName", info.packageName)
                put("versionName", info.versionName)
                put("versionCode", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode)
                put("firstInstallTime", info.firstInstallTime)
                put("lastUpdateTime", info.lastUpdateTime)
                put("sharedUserId", info.sharedUserId ?: "")
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    // ==================== 14. 存储 API（插件数据） ====================

    @JavascriptInterface
    fun setStorage(key: String, value: String) {
        if (key.isEmpty()) return
        ensureMigration()
        getPluginContext()?.putString(key, value)
    }

    @JavascriptInterface
    fun getStorage(key: String): String {
        if (key.isEmpty()) return ""
        ensureMigration()
        return getPluginContext()?.getString(key, "") ?: ""
    }

    @JavascriptInterface
    fun setStorageInt(key: String, value: Int) {
        if (key.isEmpty()) return
        ensureMigration()
        getPluginContext()?.putInt(key, value)
    }

    @JavascriptInterface
    fun getStorageInt(key: String, defaultValue: Int): Int {
        if (key.isEmpty()) return defaultValue
        ensureMigration()
        return getPluginContext()?.getInt(key, defaultValue) ?: defaultValue
    }

    @JavascriptInterface
    fun setStorageLong(key: String, value: Long) {
        if (key.isEmpty()) return
        ensureMigration()
        getPluginContext()?.putLong(key, value)
    }

    @JavascriptInterface
    fun getStorageLong(key: String, defaultValue: Long): Long {
        if (key.isEmpty()) return defaultValue
        ensureMigration()
        return getPluginContext()?.getLong(key, defaultValue) ?: defaultValue
    }

    @JavascriptInterface
    fun setStorageBool(key: String, value: Boolean) {
        if (key.isEmpty()) return
        ensureMigration()
        getPluginContext()?.putBoolean(key, value)
    }

    @JavascriptInterface
    fun getStorageBool(key: String, defaultValue: Boolean): Boolean {
        if (key.isEmpty()) return defaultValue
        ensureMigration()
        return getPluginContext()?.getBoolean(key, defaultValue) ?: defaultValue
    }

    @JavascriptInterface
    fun setStorageFloat(key: String, value: Float) {
        if (key.isEmpty()) return
        ensureMigration()
        getPluginContext()?.putFloat(key, value)
    }

    @JavascriptInterface
    fun getStorageFloat(key: String, defaultValue: Float): Float {
        if (key.isEmpty()) return defaultValue
        ensureMigration()
        return getPluginContext()?.getFloat(key, defaultValue) ?: defaultValue
    }

    @JavascriptInterface
    fun setStorageJSON(key: String, json: String) {
        if (key.isEmpty()) return
        ensureMigration()
        try {
            val obj = JSONObject(json)
            getPluginContext()?.putJSON(key, obj)
        } catch (e: Exception) {
            Logger.e(TAG, "JSON格式错误", e)
        }
    }

    @JavascriptInterface
    fun getStorageJSON(key: String): String {
        if (key.isEmpty()) return "{}"
        ensureMigration()
        return getPluginContext()?.getJSON(key)?.toString() ?: "{}"
    }

    @JavascriptInterface
    fun removeStorage(key: String) {
        if (key.isEmpty()) return
        ensureMigration()
        getPluginContext()?.remove(key)
    }

    @JavascriptInterface
    fun clearStorage() {
        ensureMigration()
        getPluginContext()?.clearAll()
    }

    @JavascriptInterface
    fun containsStorageKey(key: String): Boolean {
        if (key.isEmpty()) return false
        ensureMigration()
        return getPluginContext()?.contains(key) ?: false
    }

    @JavascriptInterface
    fun getAllStorage(): String {
        ensureMigration()
        val pctx = getPluginContext() ?: return "{}"
        return try {
            JSONObject(pctx.getAllEntries()).toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun getStorageKeys(): String {
        ensureMigration()
        val pctx = getPluginContext() ?: return "[]"
        return try {
            JSONArray(pctx.getAllKeys()).toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun getAllKeys(): String = getStorageKeys()
    @JavascriptInterface
    fun getAllData(): String = getAllStorage()

    // ==================== 15. 批量操作 ====================

    @JavascriptInterface
    fun setStorageBatch(jsonData: String): Boolean {
        ensureMigration()
        return try {
            val obj = JSONObject(jsonData)
            val pctx = getPluginContext() ?: return false
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.getString(key)
                pctx.putString(key, value)
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "批量存储失败", e)
            false
        }
    }

    @JavascriptInterface
    fun getStorageBatch(keys: String): String {
        ensureMigration()
        return try {
            val keyArray = JSONArray(keys)
            val result = JSONObject()
            val pctx = getPluginContext() ?: return "{}"
            for (i in 0 until keyArray.length()) {
                val key = keyArray.getString(i)
                result.put(key, pctx.getString(key, ""))
            }
            result.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    // ==================== 16. 存储统计 ====================

    @JavascriptInterface
    fun getStorageStats(): String {
        ensureMigration()
        val pctx = getPluginContext() ?: return "{}"
        return try {
            val stats = pctx.getStorageStats()
            JSONObject().apply {
                put("kvCount", stats.kvCount)
                put("fileCount", stats.fileCount)
                put("totalFileSize", stats.totalFileSize)
                put("cacheSize", stats.cacheSize)
                put("dataVersion", pctx.getDataVersion())
                put("totalSize", stats.totalFileSize + stats.cacheSize)
            }.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun getPluginDataSize(): String {
        ensureMigration()
        val pctx = getPluginContext() ?: return "0"
        return try {
            val stats = pctx.getStorageStats()
            formatFileSize(stats.totalFileSize + stats.cacheSize)
        } catch (e: Exception) {
            "0"
        }
    }

    @JavascriptInterface
    fun clearAllPluginData() {
        ensureMigration()
        getPluginContext()?.deleteAllPluginData()
        Logger.i(TAG, "所有插件数据已清除")
    }

    @JavascriptInterface
    fun getDataVersion(): Int {
        ensureMigration()
        return getPluginContext()?.getDataVersion() ?: 0
    }

    @JavascriptInterface
    fun exportData(): String {
        ensureMigration()
        val pctx = getPluginContext() ?: return "{}"
        return try {
            JSONObject().apply {
                put("pluginId", pluginId)
                put("pluginName", pluginInfo.name)
                put("version", pluginInfo.version)
                put("exportTime", System.currentTimeMillis())
                put("data", JSONObject(pctx.getAllEntries()))
            }.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun importData(jsonData: String): Boolean {
        ensureMigration()
        return try {
            val obj = JSONObject(jsonData)
            val data = obj.optJSONObject("data")
            if (data == null) return false
            val pctx = getPluginContext() ?: return false
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = data.getString(key)
                pctx.putString(key, value)
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "导入数据失败", e)
            false
        }
    }

    // ==================== 17. 文件操作 ====================

    private fun getSafeFile(fileName: String): File? {
        if (fileName.contains("..") || fileName.contains("/../") || fileName.startsWith("/")) {
            Logger.w(TAG, "非法的文件路径: $fileName")
            return null
        }
        val pctx = getPluginContext() ?: return null
        val baseDir = pctx.getPluginDataDir()
        val file = File(baseDir, fileName)
        return try {
            if (file.canonicalPath.startsWith(baseDir.canonicalPath)) file else null
        } catch (e: Exception) {
            null
        }
    }

    @JavascriptInterface
    fun writeFile(fileName: String, content: String): Boolean {
        if (fileName.isEmpty()) return false
        ensureMigration()
        return getPluginContext()?.writeFile(fileName, content) ?: false
    }

    @JavascriptInterface
    fun readFile(fileName: String): String? {
        if (fileName.isEmpty()) return null
        ensureMigration()
        return getPluginContext()?.readFile(fileName)
    }

    @JavascriptInterface
    fun deleteFile(fileName: String): Boolean {
        if (fileName.isEmpty()) return false
        ensureMigration()
        return getPluginContext()?.deletePluginFile(fileName) ?: false
    }

    @JavascriptInterface
    fun fileExists(fileName: String): Boolean {
        if (fileName.isEmpty()) return false
        ensureMigration()
        return getPluginContext()?.fileExists(fileName) ?: false
    }

    @JavascriptInterface
    fun listFiles(): String {
        ensureMigration()
        val pctx = getPluginContext() ?: return "[]"
        return try {
            JSONArray(pctx.listPluginFiles()).toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun getFileSize(fileName: String): Long {
        if (fileName.isEmpty()) return 0L
        ensureMigration()
        return getPluginContext()?.getPluginFileSize(fileName) ?: 0L
    }

    @JavascriptInterface
    fun getFileInfo(fileName: String): String {
        if (fileName.isEmpty()) return "{}"
        ensureMigration()
        val file = getSafeFile(fileName) ?: return "{}"
        return try {
            JSONObject().apply {
                put("name", file.name)
                put("path", file.absolutePath)
                put("size", file.length())
                put("isFile", file.isFile)
                put("isDirectory", file.isDirectory)
                put("exists", file.exists())
                put("lastModified", file.lastModified())
                put("canRead", file.canRead())
                put("canWrite", file.canWrite())
                put("canExecute", file.canExecute())
            }.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun getFileList(directory: String): String {
        ensureMigration()
        val pctx = getPluginContext() ?: return "[]"
        val dir = if (directory.isEmpty()) pctx.getPluginDataDir() else getSafeFile(directory)
        if (dir == null || !dir.exists() || !dir.isDirectory) return "[]"
        return try {
            val result = JSONArray()
            dir.listFiles()?.forEach { file ->
                result.put(JSONObject().apply {
                    put("name", file.name)
                    put("path", file.absolutePath)
                    put("size", file.length())
                    put("isFile", file.isFile)
                    put("isDirectory", file.isDirectory)
                    put("lastModified", file.lastModified())
                })
            }
            result.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun createDir(dirName: String): Boolean {
        if (dirName.isEmpty()) return false
        ensureMigration()
        val dir = getSafeFile(dirName) ?: return false
        return try { dir.mkdirs() } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun deleteDir(dirName: String): Boolean {
        if (dirName.isEmpty()) return false
        ensureMigration()
        val dir = getSafeFile(dirName) ?: return false
        return try { dir.deleteRecursively() } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun isDirectory(fileName: String): Boolean {
        if (fileName.isEmpty()) return false
        ensureMigration()
        val file = getSafeFile(fileName) ?: return false
        return file.isDirectory
    }

    @JavascriptInterface
    fun renameFile(oldName: String, newName: String): Boolean {
        if (oldName.isEmpty() || newName.isEmpty()) return false
        ensureMigration()
        val oldFile = getSafeFile(oldName) ?: return false
        val newFile = getSafeFile(newName) ?: return false
        return try { oldFile.renameTo(newFile) } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun copyFile(srcName: String, dstName: String): Boolean {
        if (srcName.isEmpty() || dstName.isEmpty()) return false
        ensureMigration()
        val src = getSafeFile(srcName) ?: return false
        val dst = getSafeFile(dstName) ?: return false
        return try { src.copyTo(dst, overwrite = true); true } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun moveFile(srcName: String, dstName: String): Boolean {
        if (srcName.isEmpty() || dstName.isEmpty()) return false
        ensureMigration()
        val src = getSafeFile(srcName) ?: return false
        val dst = getSafeFile(dstName) ?: return false
        return try { src.renameTo(dst) } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun exists(path: String): Boolean {
        if (path.isEmpty()) return false
        ensureMigration()
        val file = getSafeFile(path) ?: return false
        return file.exists()
    }

    @JavascriptInterface
    fun clearCache() {
        ensureMigration()
        getPluginContext()?.clearPluginCache()
    }

    @JavascriptInterface
    fun clearPluginCache() = clearCache()

    // ==================== 18. HTTP ====================

    @JavascriptInterface
    fun callBackendApi(path: String, method: String, body: String?, callbackId: String) {
        val activity = context as? PluginHostActivity
        if (activity == null) {
            sendCallback(callbackId, "{\"error\":\"无法获取Activity\"}")
            return
        }
        activity.callBackendApi(path, method, body) { success, response ->
            sendCallback(callbackId, JSONObject().apply {
                put("success", success)
                put("data", response ?: "")
            }.toString())
        }
    }

    @JavascriptInterface
    fun httpGet(url: String, callbackId: String) {
        if (!hasPermission(Manifest.permission.INTERNET)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }
        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }
        val request = try {
            Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "UIN-Tool-WebPlugin/$pluginId")
                .build()
        } catch (e: Exception) {
            sendCallback(callbackId, errJson("URL格式错误: ${e.message}"))
            return
        }
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                sendCallback(callbackId, errJson(e.message))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    sendCallback(callbackId, JSONObject().apply {
                        put("success", true)
                        put("statusCode", it.code)
                        put("data", it.body?.string() ?: "")
                    }.toString())
                }
            }
        })
    }

    @JavascriptInterface
    fun httpPost(url: String, jsonBody: String, callbackId: String) {
        if (!hasPermission(Manifest.permission.INTERNET)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }
        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        val request = try {
            Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "UIN-Tool-WebPlugin/$pluginId")
                .build()
        } catch (e: Exception) {
            sendCallback(callbackId, errJson("URL格式错误: ${e.message}"))
            return
        }
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                sendCallback(callbackId, errJson(e.message))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    sendCallback(callbackId, JSONObject().apply {
                        put("success", true)
                        put("statusCode", it.code)
                        put("data", it.body?.string() ?: "")
                    }.toString())
                }
            }
        })
    }

    @JavascriptInterface
    fun httpPut(url: String, jsonBody: String, callbackId: String) {
        if (!hasPermission(Manifest.permission.INTERNET)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }
        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        val request = try {
            Request.Builder()
                .url(url)
                .put(body)
                .header("User-Agent", "UIN-Tool-WebPlugin/$pluginId")
                .build()
        } catch (e: Exception) {
            sendCallback(callbackId, errJson("URL格式错误: ${e.message}"))
            return
        }
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                sendCallback(callbackId, errJson(e.message))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    sendCallback(callbackId, JSONObject().apply {
                        put("success", true)
                        put("statusCode", it.code)
                        put("data", it.body?.string() ?: "")
                    }.toString())
                }
            }
        })
    }

    @JavascriptInterface
    fun httpDelete(url: String, callbackId: String) {
        if (!hasPermission(Manifest.permission.INTERNET)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }
        if (url.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}")
            return
        }
        val request = try {
            Request.Builder()
                .url(url)
                .delete()
                .header("User-Agent", "UIN-Tool-WebPlugin/$pluginId")
                .build()
        } catch (e: Exception) {
            sendCallback(callbackId, errJson("URL格式错误: ${e.message}"))
            return
        }
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                sendCallback(callbackId, errJson(e.message))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    sendCallback(callbackId, JSONObject().apply {
                        put("success", true)
                        put("statusCode", it.code)
                        put("data", it.body?.string() ?: "")
                    }.toString())
                }
            }
        })
    }

    @JavascriptInterface
    fun downloadFile(url: String, fileName: String, callbackId: String) {
        if (!hasPermission(Manifest.permission.INTERNET)) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"缺少网络权限\"}")
            return
        }
        if (url.isEmpty() || fileName.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"URL或文件名不能为空\"}")
            return
        }
        Thread {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "UIN-Tool-WebPlugin/$pluginId")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    sendCallback(callbackId, errJson("HTTP ${response.code}"))
                    return@Thread
                }
                val body = response.body ?: run {
                    sendCallback(callbackId, "{\"success\":false,\"error\":\"响应体为空\"}")
                    return@Thread
                }
                val pctx = getPluginContext()
                if (pctx == null) {
                    sendCallback(callbackId, "{\"success\":false,\"error\":\"无法获取存储\"}")
                    return@Thread
                }
                val safeFile = getSafeFile(fileName)
                if (safeFile == null) {
                    sendCallback(callbackId, "{\"success\":false,\"error\":\"非法文件名\"}")
                    return@Thread
                }
                safeFile.parentFile?.mkdirs()
                FileOutputStream(safeFile).use { fos ->
                    body.byteStream().use { input ->
                        input.copyTo(fos)
                    }
                }
                sendCallback(callbackId, JSONObject().apply {
                    put("success", true)
                    put("file", safeFile.absolutePath)
                    put("size", safeFile.length())
                }.toString())
            } catch (e: Exception) {
                sendCallback(callbackId, errJson(e.message))
            }
        }.start()
    }

    // ==================== 19. 权限 ====================

    @JavascriptInterface
    fun checkPermission(permission: String): Boolean {
        if (permission.isEmpty()) return false
        return PermissionUtils.hasPermission(context, permission) ||
                PermissionUtils.hasSpecialPermission(context, permission)
    }

    @JavascriptInterface
    fun requestPermission(permission: String, callbackId: String) {
        if (permission.isEmpty()) {
            sendCallback(callbackId, "{\"success\":false,\"error\":\"权限名不能为空\"}")
            return
        }
        if (checkPermission(permission)) {
            sendCallback(callbackId, "{\"success\":true,\"message\":\"权限已授予\"}")
            return
        }
        if (PermissionUtils.isSpecialPermission(permission)) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
                sendCallback(callbackId, "{\"success\":false,\"message\":\"特殊权限请在系统设置中手动开启\"}")
            } catch (e: Exception) {
                sendCallback(callbackId, errJson("无法打开设置: ${e.message}"))
            }
            return
        }
        getActivity()?.let { activity ->
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

    @JavascriptInterface
    fun requestPermissions(permissions: String, callbackId: String) {
        try {
            val permArray = JSONArray(permissions)
            val permList = mutableListOf<String>()
            for (i in 0 until permArray.length()) {
                val perm = permArray.getString(i)
                if (!checkPermission(perm)) permList.add(perm)
            }
            if (permList.isEmpty()) {
                sendCallback(callbackId, "{\"success\":true,\"message\":\"所有权限已授予\"}")
                return
            }
            val normalPerms = permList.filter { !PermissionUtils.isSpecialPermission(it) }
            val specialPerms = permList.filter { PermissionUtils.isSpecialPermission(it) }
            if (specialPerms.isNotEmpty()) {
                showSpecialPermissionDialog(specialPerms) {
                    if (normalPerms.isNotEmpty()) {
                        requestNormalPermissions(normalPerms, callbackId)
                    } else {
                        sendCallback(callbackId, "{\"success\":false,\"message\":\"特殊权限需要手动开启\"}")
                    }
                }
                return
            }
            if (normalPerms.isNotEmpty()) {
                requestNormalPermissions(normalPerms, callbackId)
            }
        } catch (e: Exception) {
            sendCallback(callbackId, errJson(e.message))
        }
    }

    private fun requestNormalPermissions(permissions: List<String>, callbackId: String) {
        val activity = getActivity() ?: return
        pendingPermissionCallbacks["bulk_$callbackId"] = callbackId
        activity.requestPermissions(permissions.toTypedArray(), 1003)
        Logger.i(TAG, "批量请求权限: ${permissions.joinToString()}")
    }

    private fun showSpecialPermissionDialog(permissions: List<String>, onComplete: () -> Unit) {
        val activity = getActivity() ?: return
        val message = buildString {
            append("以下权限需要在系统设置中手动开启：\n\n")
            permissions.forEach { perm ->
                append("• ${PermissionUtils.getPermissionDisplayName(perm)}\n")
            }
            append("\n点击「去设置」打开应用设置页面。")
        }
        AlertDialog.Builder(activity)
            .setTitle("需要特殊权限")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onComplete()
                    }, 3000)
                } catch (e: Exception) {
                    onComplete()
                }
            }
            .setNegativeButton("取消") { _, _ -> onComplete() }
            .show()
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Logger.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")
        when (requestCode) {
            1002 -> {
                permissions.forEachIndexed { index, permission ->
                    val callbackId = pendingPermissionCallbacks.remove(permission)
                    if (callbackId != null) {
                        val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
                        sendCallback(
                            callbackId,
                            "{\"success\":$granted,\"message\":\"${if (granted) "权限已授予" else "权限被拒绝"}\"}"
                        )
                    }
                }
            }
            1003 -> {
                var callbackId: String? = null
                val iterator = pendingPermissionCallbacks.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key.startsWith("bulk_")) {
                        callbackId = entry.value
                        iterator.remove()
                        break
                    }
                }
                if (callbackId != null) {
                    val resultMap = permissions.mapIndexed { index, permission ->
                        permission to (grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED)
                    }.toMap()
                    val allGranted = resultMap.values.all { it }
                    sendCallback(
                        callbackId,
                        JSONObject().apply {
                            put("success", allGranted)
                            put("allGranted", allGranted)
                            put("results", JSONObject(resultMap))
                        }.toString()
                    )
                }
            }
        }
    }

    // ==================== 20. UI ====================

    @JavascriptInterface
    fun showLoading(message: String) {
        getActivity()?.runOnUiThread {
            Toast.makeText(context, message ?: "加载中...", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun hideLoading() {
        // Toast 会自动消失
    }

    @JavascriptInterface
    fun showConfirmDialog(title: String, message: String, callbackId: String) {
        val activity = getActivity()
        if (activity == null) {
            sendCallback(callbackId, errJson("无法获取Activity"))
            return
        }
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定") { _, _ ->
                    sendCallback(callbackId, "{\"success\":true,\"confirmed\":true}")
                }
                .setNegativeButton("取消") { _, _ ->
                    sendCallback(callbackId, "{\"success\":false,\"confirmed\":false}")
                }
                .setNeutralButton("忽略") { _, _ ->
                    sendCallback(callbackId, "{\"success\":false,\"confirmed\":false,\"ignored\":true}")
                }
                .show()
        }
    }

    @JavascriptInterface
    fun showPromptDialog(title: String, hint: String, callbackId: String) {
        val activity = getActivity()
        if (activity == null) {
            sendCallback(callbackId, errJson("无法获取Activity"))
            return
        }
        activity.runOnUiThread {
            val input = android.widget.EditText(activity)
            input.hint = hint
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    sendCallback(callbackId, JSONObject().apply {
                        put("success", true)
                        put("confirmed", true)
                        put("value", input.text?.toString() ?: "")
                    }.toString())
                }
                .setNegativeButton("取消") { _, _ ->
                    sendCallback(callbackId, "{\"success\":false,\"confirmed\":false,\"value\":\"\"}")
                }
                .show()
        }
    }

    // ==================== 21. 剪贴板 ====================

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        if (text.isEmpty()) {
            showToast("内容为空")
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("plugin_text", text))
            showToast("已复制到剪贴板")
            Logger.i(TAG, "复制到剪贴板: ${text.take(50)}...")
        } catch (e: Exception) {
            Logger.e(TAG, "复制失败", e)
            showToast("复制失败: ${e.message}")
        }
    }

    @JavascriptInterface
    fun setClipboard(text: String) = copyToClipboard(text)

    @JavascriptInterface
    fun getClipboard(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString() ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun paste(): String = getClipboard()

    @JavascriptInterface
    fun clearClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ==================== 22. Toast ====================

    private fun showToast(message: String) {
        getActivity()?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun showToastLong(message: String) {
        getActivity()?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } ?: Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    // ==================== 23. 振动 ====================

    @JavascriptInterface
    fun vibrate(durationMs: String) {
        if (!hasPermission(Manifest.permission.VIBRATE)) return
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val duration = durationMs.toLongOrNull() ?: 200
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(duration)
            }
            Logger.i(TAG, "震动: ${duration}ms")
        } catch (e: Exception) {
            Logger.e(TAG, "震动失败: ${e.message}")
        }
    }

    @JavascriptInterface
    fun cancelVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.cancel()
        } catch (e: Exception) { }
    }

    // ==================== 24. 通知 ====================

    @JavascriptInterface
    fun sendNotification(title: String, message: String) {
        try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, PluginHostActivity::class.java).apply {
                    putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            notificationManager?.notify(System.currentTimeMillis().toInt(), notification)
            Logger.i(TAG, "发送通知: $title")
        } catch (e: Exception) {
            Logger.e(TAG, "发送通知失败", e)
        }
    }

    @JavascriptInterface
    fun cancelNotification(id: Int) {
        try {
            notificationManager?.cancel(id)
        } catch (e: Exception) { }
    }

    // ==================== 25. 系统操作 ====================

    @JavascriptInterface
    fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun openUrl(url: String) {
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

    @JavascriptInterface
    fun share(text: String) {
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

    @JavascriptInterface
    fun setTitle(title: String) {
        getActivity()?.runOnUiThread {
            getActivity()?.title = title
            if (context is PluginHostActivity) {
                context.setPluginTitle(title)
            }
        }
    }

    @JavascriptInterface
    fun setFullscreen(fullscreen: Boolean) {
        getActivity()?.runOnUiThread {
            if (fullscreen) {
                getActivity()?.window?.decorView?.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
            } else {
                getActivity()?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    @JavascriptInterface
    fun setKeepScreenOn(keepOn: Boolean) {
        getActivity()?.runOnUiThread {
            if (keepOn) {
                getActivity()?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                getActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    @JavascriptInterface
    fun takeScreenshot() {
        if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showToast("需要存储权限才能截图")
            return
        }
        getActivity()?.let { activity ->
            try {
                val rootView = activity.window.decorView.rootView
                rootView.isDrawingCacheEnabled = true
                val bitmap = rootView.drawingCache
                if (bitmap != null) {
                    val dir = File(Constants.DOWNLOAD_DIR, "screenshots")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { fos ->
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

    @JavascriptInterface
    fun getPluginDir(): String {
        val pluginDir = File(Constants.PLUGIN_DIR, pluginId)
        if (!pluginDir.exists()) pluginDir.mkdirs()
        return pluginDir.absolutePath
    }

    @JavascriptInterface
    fun getBackendStatus(): String {
        val activity = context as? PluginHostActivity
        if (activity != null) {
            val port = activity.getBackendPort()
            return if (port > 0) "running:$port" else "starting"
        }
        return "unknown"
    }

    @JavascriptInterface
    fun isDarkMode(): Boolean {
        return try {
            val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 26. 事件 ====================

    @JavascriptInterface
    fun sendEvent(eventName: String, data: String?) {
        Logger.d(TAG, "发送事件: $eventName -> $data")
        val activity = context as? PluginHostActivity
        if (activity != null) {
            val js = """
                if (window.dispatchEvent) {
                    window.dispatchEvent(new CustomEvent('$eventName', { detail: ${data ?: "null"} }));
                }
            """.trimIndent()
            activity.evaluateJavascript(js)
        }
    }

    @JavascriptInterface
    fun addEventListener(eventName: String, callbackId: String) {
        Logger.d(TAG, "添加事件监听: $eventName (callback: $callbackId)")
    }
    
    @JavascriptInterface
fun ping(host: String, callbackId: String) {
    if (host.isEmpty()) {
        sendCallback(callbackId, errJson("主机不能为空"))
        return
    }

    Thread {
        try {
            val startTime = System.currentTimeMillis()
            val ip = try {
                InetAddress.getByName(host).hostAddress ?: host
            } catch (e: Exception) {
                host
            }

            var success: Boolean
            var timeMs = -1L
            try {
                val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "5", host))
                val output = process.inputStream.bufferedReader().use { it.readText() }
                success = process.waitFor() == 0
                Regex("time[=<]\\s*([0-9.]+)").find(output)?.let {
                    timeMs = it.groupValues[1].toFloat().roundToInt().toLong()
                }
            } catch (e: Exception) {
                // ping 二进制不可用时回退到 isReachable
                success = try {
                    InetAddress.getByName(host).isReachable(3000)
                } catch (e2: Exception) {
                    false
                }
            }

            sendCallback(callbackId, JSONObject().apply {
                put("success", success)
                put("reachable", success)
                put("host", host)
                put("ip", ip)
                put("time", if (success && timeMs >= 0) timeMs else System.currentTimeMillis() - startTime)
            }.toString())
        } catch (e: Exception) {
            sendCallback(callbackId, errJson(e.message ?: "ping 失败"))
        }
    }.start()
}

    @JavascriptInterface
fun resolveDns(host: String, callbackId: String) {
    if (host.isEmpty()) {
        sendCallback(callbackId, errJson("主机不能为空"))
        return
    }
    Thread {
        try {
            val all = InetAddress.getAllByName(host)
            val ips = JSONArray()
            all.forEach { ips.put(it.hostAddress ?: "") }
            sendCallback(callbackId, JSONObject().apply {
                put("success", true)
                put("host", host)
                put("ips", ips)
            }.toString())
        } catch (e: Exception) {
            sendCallback(callbackId, errJson(e.message ?: "DNS 解析失败"))
        }
    }.start()
}

    @JavascriptInterface
fun dns(host: String, callbackId: String) = resolveDns(host, callbackId)

    @JavascriptInterface
fun dnsLookup(host: String): String {
    if (host.isEmpty()) return errJson("主机不能为空")
    return try {
        val all = InetAddress.getAllByName(host)
        val ips = JSONArray()
        all.forEach { ips.put(it.hostAddress ?: "") }
        JSONObject().apply {
            put("success", true)
            put("host", host)
            put("ips", ips)
        }.toString()
    } catch (e: Exception) {
        errJson(e.message ?: "DNS 解析失败")
    }
}

    // ==================== 27. 私有方法 ====================

    private fun sendCallback(callbackId: String, data: String) {
        if (callbackId.isEmpty()) return
        val activity = context as? PluginHostActivity ?: return
        activity.runOnUiThread {
            try {
                // 回调约定：response 以 JSON 字符串形式传给 JS（JS 端通过 JSON.parse 解析）
                // 因此这里必须把 callbackId 和 data 都转成合法的 JS 字符串字面量，
                // 既保证 callbackId 安全，也保证 data 中的引号/特殊字符不会破坏 JS。
                val jsId = jsString(callbackId)
                val jsData = jsString(data)
                val script = "if(window.UINPluginCallbacks && window.UINPluginCallbacks[$jsId]){window.UINPluginCallbacks[$jsId]($jsData);}"
                activity.evaluateJavascript(script)
            } catch (e: Exception) {
                Logger.e(TAG, "回调执行失败: $callbackId", e)
            }
        }
    }

    private fun jsString(value: String): String {
        val sb = StringBuilder(value.length + 8)
        sb.append('"')
        value.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun errJson(message: String?, code: String? = null): String {
        return JSONObject().apply {
            put("success", false)
            if (code != null) put("code", code)
            put("error", message ?: "未知错误")
        }.toString()
    }

    private fun closePlugin() {
        if (context is Activity) context.finish()
    }

    private fun showAlert(message: String) {
        getActivity()?.runOnUiThread {
            AlertDialog.Builder(context)
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun showConfirm(message: String) {
        getActivity()?.runOnUiThread {
            AlertDialog.Builder(context)
                .setTitle("确认")
                .setMessage(message)
                .setPositiveButton("确定") { _, _ -> }
                .setNegativeButton("取消") { _, _ -> }
                .show()
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 0 -> "0 B"
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> String.format("%.2fKB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2fMB", size / (1024.0 * 1024.0))
            else -> String.format("%.2fGB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
    

    private fun hasPermission(permission: String): Boolean {
        return PermissionUtils.hasPermission(context, permission) ||
                PermissionUtils.hasSpecialPermission(context, permission)
    }
}