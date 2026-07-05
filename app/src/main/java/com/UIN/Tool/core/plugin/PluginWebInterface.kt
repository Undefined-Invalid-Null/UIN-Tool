// app/src/main/java/com/UIN/Tool/core/plugin/PluginWebInterface.kt
package com.UIN.Tool.core.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Vibrator
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.UIN.Tool.log.Logger
import org.json.JSONObject

class PluginWebInterface(
    private val context: Context,
    private val pluginId: String,
    private val proxy: WebPluginProxy?
) {
    
    private val TAG = "PluginWebInterface"
    
    @JavascriptInterface
    fun callPlugin(method: String, params: String?) {
        Logger.i(TAG, "JS 调用插件: $method, 参数: $params")
        proxy?.onJsCall(method, params)
    }
    
    @JavascriptInterface
    fun callHost(action: String, data: String?) {
        Logger.i(TAG, "JS 调用宿主: $action, 数据: $data")
        val params = data ?: ""
        
        when (action) {
            "toast" -> showToast(params)
            "finish" -> (context as? Activity)?.finish()
            "log" -> Logger.i("WebPlugin", params)
            "alert" -> showAlert(params)
            "confirm" -> showConfirm(params)
            "vibrate" -> vibrate(params)
            "copy" -> copyToClipboard(params)
            "openUrl" -> openUrl(params)
            "share" -> share(params)
            else -> Logger.w(TAG, "未知的宿主调用: $action")
        }
    }
    
    @JavascriptInterface
    fun sendEvent(eventName: String, data: String?) {
        Logger.d(TAG, "发送事件: $eventName")
        proxy?.sendEvent(eventName, data)
    }
    
    // ==================== 新增/补全的方法 ====================
    
    private fun showToast(message: String) {
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showAlert(message: String) {
        (context as? Activity)?.runOnUiThread {
            android.app.AlertDialog.Builder(context)
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }
    }
    
    private fun showConfirm(message: String) {
        (context as? Activity)?.runOnUiThread {
            android.app.AlertDialog.Builder(context)
                .setTitle("确认")
                .setMessage(message)
                .setPositiveButton("确定") { _, _ -> }
                .setNegativeButton("取消") { _, _ -> }
                .show()
        }
    }
    
    private fun vibrate(durationMs: String) {
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
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("plugin_text", text)
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
}