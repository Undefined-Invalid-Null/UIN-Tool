package com.UIN.Tool.utils

import android.content.Context
import android.os.Build
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashLogUtils {

    private const val TAG = "CrashLogUtils"

    /**
     * 记录异常到文件
     */
    fun logException(context: Context, throwable: Throwable, tag: String = "Exception") {
        try {
            val logDir = File(Constants.LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "crash_$date.log")

            val sb = StringBuilder()
            sb.append("\n")
            sb.append("================== 异常报告 ==================\n")
            sb.append("时间: ${getCurrentTime()}\n")
            sb.append("标签: $tag\n")
            sb.append("设备: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            sb.append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            sb.append("异常类型: ${throwable.javaClass.name}\n")
            sb.append("异常信息: ${throwable.message}\n")
            sb.append("堆栈信息:\n")
            sb.append(throwable.stackTraceToString())
            sb.append("================== 异常报告结束 ==================\n")

            FileWriter(logFile, true).use { writer ->
                writer.write(sb.toString())
            }

            // 同时记录到 Logcat
            Logger.e(tag, "异常已记录: ${throwable.message}", throwable)

        } catch (e: Exception) {
            Logger.e(TAG, "记录异常失败", e)
        }
    }

    /**
     * 记录异常并标记需要跳转
     */
    fun logExceptionAndNavigate(context: Context, throwable: Throwable, tag: String = "Exception") {
        logException(context, throwable, tag)

        // 标记需要跳转到日志页面
        val prefs = context.getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.KEY_JUST_CRASHED, true).apply()
    }

    /**
     * 检查是否需要跳转到日志页面
     */
    fun shouldNavigateToLogs(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.KEY_JUST_CRASHED, false)
    }

    /**
     * 清除跳转标记
     */
    fun clearNavigateFlag(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.KEY_JUST_CRASHED, false).apply()
    }

    /**
     * 获取当前时间字符串
     */
    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }
}