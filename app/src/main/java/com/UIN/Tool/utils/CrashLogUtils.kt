package com.UIN.Tool.utils

import com.UIN.Tool.R
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.UIN.Tool.constants.AppConstants as Constants
import android.os.Build
import com.UIN.Tool.log.Logger
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashLogUtils {

    private const val TAG = "CrashLogUtils"

    /** [scheduleLogPageFallback] 的 PendingIntent 请求码 —— 全应用唯一，别与别处撞号。 */
    private const val REQ_FALLBACK = 0x0C7A

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
     * 用 commit() 同步写盘：主线程崩溃后进程随即被杀，apply() 的异步写可能来不及落盘
     */
    fun logExceptionAndNavigate(context: Context, throwable: Throwable, tag: String = "Exception") {
        logException(context, throwable, tag)

        // 标记需要跳转到日志页面
        val prefs = context.getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.KEY_JUST_CRASHED, true).commit()
    }

    /**
     * 检查是否需要跳转到日志页面
     */
    fun shouldNavigateToLogs(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREF_CRASH, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.KEY_JUST_CRASHED, false)
    }

    /**
     * 崩溃后的**兜底**拉起：主路径是崩溃处理里那次 `startActivity`，可进程随即便会被杀，
     * 那次启动有被系统丢掉的风险（实测丢过 —— 崩溃后没有任何自动重启的痕迹）。
     *
     * AlarmManager 持有的 PendingIntent **不随本进程死**，到点由系统另起进程执行
     * [CrashRestartReceiver]。接收器会复查跳转标记：日志页已经打开过、标记已被清掉，
     * 它就安静返回，所以主路径成功时这条兜底**自动变哑**，不会把页面再弹一次。
     */
    fun scheduleLogPageFallback(context: Context, delayMs: Long = 2500L) {
        try {
            val intent = Intent(context, CrashRestartReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                REQ_FALLBACK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, System.currentTimeMillis() + delayMs, pending)
        } catch (e: Exception) {
            Logger.e(TAG, "scheduleLogPageFallback failed", e)
        }
    }

    /**
     * 往当日崩溃报告**同步追加一行**诊断记录。
     *
     * 崩溃后主线程随即便 killProcess，Logger 的写线程来不及 flush，那些行会丢 ——
     * 只有紧跟写报告、同样走 FileWriter 同步落盘的追加才留得下来。用它回答
     * 「崩溃之后到底有没有去请日志页」：报告末尾有这行 = 请求已经交出去。
     * 报告正文本就是英文堆栈，诊断行保持英文同风格，不进 strings。
     */
    fun appendNote(note: String) {
        try {
            val logDir = File(Constants.LOG_DIR)
            if (!logDir.exists()) logDir.mkdirs()
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            FileWriter(File(logDir, "crash_$date.log"), true).use { w ->
                w.write("[auto-nav ${getCurrentTime()}] $note\n")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "appendNote failed", e)
        }
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