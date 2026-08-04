// app/src/main/java/com/UIN/Tool/core/plugin/PluginWebInterface.kt
package com.UIN.Tool.core.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
        Logger.i(TAG, Str.get(R.string.js_call_plugin_method_params_params, method, params))
        proxy?.onJsCall(method, params)
    }
    
    @JavascriptInterface
    fun callHost(action: String, data: String?) {
        Logger.i(TAG, Str.get(R.string.js_call_host_action_data_data, action, data))
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
            else -> Logger.w(TAG, Str.get(R.string.unknown_host_call_action, action))
        }
    }
    
    @JavascriptInterface
    fun sendEvent(eventName: String, data: String?) {
        Logger.d(TAG, Str.get(R.string.sending_event_eventname, eventName))
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
                .setTitle(Str.get(R.string.notice))
                .setMessage(message)
                .setPositiveButton(Str.get(R.string.ok_2), null)
                .show()
        }
    }
    
    private fun showConfirm(message: String) {
        (context as? Activity)?.runOnUiThread {
            android.app.AlertDialog.Builder(context)
                .setTitle(Str.get(R.string.confirm_2))
                .setMessage(message)
                .setPositiveButton(Str.get(R.string.ok_2)) { _, _ -> }
                .setNegativeButton(Str.get(R.string.cancel)) { _, _ -> }
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
            Logger.i(TAG, Str.get(R.string.vibration_duration_ms, duration))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.vibration_failed_e_message, e.message))
        }
    }
    
    private fun copyToClipboard(text: String) {
        if (text.isEmpty()) {
            showToast(Str.get(R.string.content_is_empty))
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("plugin_text", text)
            clipboard.setPrimaryClip(clip)
            showToast(Str.get(R.string.copied_to_clipboard))
            Logger.i(TAG, Str.get(R.string.copied_to_clipboard_text_take_50, text.take(50)))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.copy_failed), e)
            showToast(Str.get(R.string.copy_failed_e_message, e.message))
        }
    }
    
    private fun openUrl(url: String) {
        if (url.isEmpty()) {
            showToast(Str.get(R.string.url_must_not_be_empty))
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Logger.i(TAG, Str.get(R.string.opening_link_url, url))
        } catch (e: Exception) {
            showToast(Str.get(R.string.unable_to_open_link))
            Logger.e(TAG, Str.get(R.string.failed_to_open_link_e_message, e.message))
        }
    }
    
    private fun share(text: String) {
        if (text.isEmpty()) {
            showToast(Str.get(R.string.content_is_empty))
            return
        }
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, Str.get(R.string.share)))
            Logger.i(TAG, Str.get(R.string.sharing_text_take_50, text.take(50)))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.share_failed), e)
            showToast(Str.get(R.string.share_failed_e_message, e.message))
        }
    }
}