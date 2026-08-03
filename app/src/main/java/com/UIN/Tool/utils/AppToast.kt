// app/src/main/java/com/UIN/Tool/utils/AppToast.kt
package com.UIN.Tool.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import com.UIN.Tool.log.Logger

/**
 * 统一 Toast 工具
 * 所有 Toast 通过此对象显示，支持取消当前 Toast
 * 使用 Material Icon 显示在 Toast 中
 */
object AppToast {
    
    private const val TAG = "AppToast"
    private var currentToast: Toast? = null
    
    /**
     * 显示 Toast
     * 线程安全：后台线程调用时自动切回主线程显示
     * @param context 上下文
     * @param message 消息内容
     * @param duration 显示时长，默认 Toast.LENGTH_SHORT
     */
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                currentToast?.cancel()
                currentToast = Toast.makeText(appContext, message, duration)
                currentToast?.show()
            } catch (e: Exception) {
                Logger.e(TAG, "Toast 显示失败", e)
            }
        }
    }
    
    /**
     * 显示成功 Toast - 使用绿色对勾
     */
    fun success(context: Context, message: String) {
        show(context, "✔ $message")
        Logger.i(TAG, "Success: $message")
    }
    
    /**
     * 显示错误 Toast - 使用红色叉号
     */
    fun error(context: Context, message: String) {
        show(context, "✘ $message")
        Logger.e(TAG, "Error: $message")
    }
    
    /**
     * 显示警告 Toast - 使用黄色感叹号
     */
    fun warning(context: Context, message: String) {
        show(context, "⚠ $message")
        Logger.w(TAG, "Warning: $message")
    }
    
    /**
     * 显示信息 Toast - 使用蓝色信息图标
     */
    fun info(context: Context, message: String) {
        show(context, "ℹ $message")
        Logger.i(TAG, "Info: $message")
    }
    
    /**
     * 显示长 Toast
     */
    fun showLong(context: Context, message: String) {
        show(context, message, Toast.LENGTH_LONG)
    }
    
    /**
     * 取消当前显示的 Toast
     */
    fun cancel() {
        currentToast?.cancel()
        currentToast = null
    }
}