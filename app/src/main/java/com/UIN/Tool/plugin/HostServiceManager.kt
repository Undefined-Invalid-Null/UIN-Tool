// app/src/main/java/com/UIN/Tool/plugin/HostServiceManager.kt
package com.UIN.Tool.plugin

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.UIN.Tool.MainActivity
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.ProgressDialog
import com.UIN.Tool.utils.Constants

class HostServiceManager(private val context: Context) : HostService {
    
    companion object {
        private const val TAG = "HostServiceManager"
    }
    
    private var currentDialog: AlertDialog? = null
    private var currentProgressDialog: ProgressDialog? = null
    
    override fun showToast(message: String, duration: Int) {
        Toast.makeText(context, message, duration).show()
    }
    
    override fun showLoading(message: String): HostLoadingDialog {
        val dialog = object : HostLoadingDialog {
            override fun setMessage(message: String) {
                currentProgressDialog?.setMessage(message)
            }
            
            override fun setProgress(progress: Int) {
                currentProgressDialog?.setProgress(progress)
            }
            
            override fun dismiss() {
                currentProgressDialog?.dismiss()
                currentProgressDialog = null
            }
        }
        
        // 如果当前是 FragmentActivity，使用 Compose 对话框
        if (context is FragmentActivity) {
            // 使用 Compose 对话框
            currentProgressDialog = ProgressDialog(context).apply {
                setMessage(message)
                show()
            }
        }
        
        return dialog
    }
    
    override fun hideLoading() {
        currentProgressDialog?.dismiss()
        currentProgressDialog = null
    }
    
    override fun showDialog(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String?,
        onPositive: () -> Unit,
        onNegative: (() -> Unit)?
    ) {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onPositive() }
        
        if (negativeText != null && onNegative != null) {
            builder.setNegativeButton(negativeText) { _, _ -> onNegative() }
        }
        
        currentDialog = builder.show()
    }
    
    override fun getWorkFolder(): String = Constants.WORK_DIR
    
    override fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "打开 URL 失败: ${e.message}", e)
            showToast("无法打开链接", Toast.LENGTH_SHORT)
        }
    }
    
    override fun shareText(text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享"))
        } catch (e: Exception) {
            Logger.e(TAG, "分享失败: ${e.message}", e)
            showToast("分享失败", Toast.LENGTH_SHORT)
        }
    }
    
    override fun copyToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("plugin_text", text)
            clipboard.setPrimaryClip(clip)
            showToast("已复制到剪贴板", Toast.LENGTH_SHORT)
        } catch (e: Exception) {
            Logger.e(TAG, "复制失败: ${e.message}", e)
        }
    }
    
    override fun getPluginManager(): PluginManager = PluginManager.getInstance(context)
}

// ==================== ProgressDialog（Compose 版本） ====================

class ProgressDialog(private val context: Context) {
    private var dialog: android.app.AlertDialog? = null
    private var message: String = ""
    
    fun setMessage(message: String) {
        this.message = message
        dialog?.setMessage(message)
    }
    
    fun setProgress(progress: Int) {
        // 更新进度
    }
    
    fun show() {
        dialog = android.app.AlertDialog.Builder(context)
            .setMessage(message)
            .setCancelable(false)
            .create()
        dialog?.show()
    }
    
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}