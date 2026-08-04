package com.UIN.Tool.utils

import com.UIN.Tool.R
import android.content.Context
import com.UIN.Tool.constants.AppConstants as Constants
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
            sb.append(Str.get(R.string.crash_report_n))
            sb.append(Str.get(R.string.time_getcurrenttime_n, getCurrentTime()))
            sb.append(Str.get(R.string.tag_tag_n, tag))
            sb.append(Str.get(R.string.device_build_manufacturer_build_mode, Build.MANUFACTURER, Build.MODEL))
            sb.append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            sb.append(Str.get(R.string.exception_type_throwable_javaclass_n, throwable.javaClass.name))
            sb.append(Str.get(R.string.exception_message_throwable_message_, throwable.message))
            sb.append(Str.get(R.string.stack_trace_n))
            sb.append(throwable.stackTraceToString())
            sb.append(Str.get(R.string.end_of_crash_report_n))

            FileWriter(logFile, true).use { writer ->
                writer.write(sb.toString())
            }

            // 同时记录到 Logcat
            Logger.e(tag, Str.get(R.string.exception_recorded_throwable_message, throwable.message), throwable)

        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_record_exception), e)
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