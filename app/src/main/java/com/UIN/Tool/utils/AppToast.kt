// app/src/main/java/com/UIN/Tool/utils/AppToast.kt
package com.UIN.Tool.utils

import com.UIN.Tool.R
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import com.UIN.Tool.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 统一提示工具
 * 主界面有 SnackbarHost 时优先使用 Material Snackbar（更统一、可排队），
 * 无宿主（后台任务/非 Compose 上下文）时回退到系统 Toast。
 */
object AppToast {

    private const val TAG = "AppToast"
    private var currentToast: Toast? = null

    private var snackbarHost: SnackbarHostState? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 由主题根节点绑定当前前台界面的 Snackbar 宿主
     */
    fun bindHost(host: SnackbarHostState) {
        snackbarHost = host
    }

    /**
     * 解绑宿主；仅当仍指向同一宿主时才清空，避免覆盖后方的界面
     */
    fun unbindHost(host: SnackbarHostState) {
        if (snackbarHost === host) {
            snackbarHost = null
        }
    }

    /**
     * 显示提示
     * 线程安全：后台线程调用时自动切回主线程显示
     * @param context 上下文
     * @param message 消息内容
     * @param duration 显示时长，默认 Toast.LENGTH_SHORT
     */
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val host = snackbarHost
        if (host != null) {
            val snackbarDuration =
                if (duration == Toast.LENGTH_LONG) SnackbarDuration.Long else SnackbarDuration.Short
            scope.launch {
                try {
                    host.showSnackbar(message = message, duration = snackbarDuration)
                } catch (e: Exception) {
                    Logger.e(TAG, Str.get(R.string.toast_failed_to_show), e)
                }
            }
            return
        }

        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                currentToast?.cancel()
                currentToast = Toast.makeText(appContext, message, duration)
                currentToast?.show()
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.toast_failed_to_show), e)
            }
        }
    }

    /**
     * 显示成功提示 - 使用绿色对勾
     */
    fun success(context: Context, message: String) {
        show(context, "✔ $message")
        Logger.i(TAG, "Success: $message")
    }

    /**
     * 显示错误提示 - 使用红色叉号
     */
    fun error(context: Context, message: String) {
        show(context, "✘ $message")
        Logger.e(TAG, "Error: $message")
    }

    /**
     * 显示警告提示 - 使用黄色感叹号
     */
    fun warning(context: Context, message: String) {
        show(context, "⚠ $message")
        Logger.w(TAG, "Warning: $message")
    }

    /**
     * 显示信息提示 - 使用蓝色信息图标
     */
    fun info(context: Context, message: String) {
        show(context, "ℹ $message")
        Logger.i(TAG, "Info: $message")
    }

    /**
     * 显示长提示
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
