package com.UIN.Tool.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.screen.log.LogViewerActivity

/**
 * 崩溃后的兜底拉起，由 [CrashLogUtils.scheduleLogPageFallback] 排期。
 *
 * 为什么需要它：崩溃处理里那次 `startActivity` 是**紧接着 killProcess** 发出的，
 * 进程立刻死掉时系统有可能把这次启动一并丢掉 —— 用户看到的就是「闪了一下什么都没有」。
 * AlarmManager 的 PendingIntent 存在系统进程里，不随我们死，到点才执行。
 *
 * 动手前先复查跳转标记：日志页若已经被拉起来，它读出标记后会自行清掉，
 * 这里查到已清就安静返回，**不会**把页面再弹第二遍。
 */
class CrashRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!CrashLogUtils.shouldNavigateToLogs(context)) return
        val app = context.applicationContext
        runCatching {
            app.startActivity(
                Intent(app, LogViewerActivity::class.java)
                    // SINGLE_TOP：主路径可能已经把日志页顶上去了，别叠第二个
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("auto_open", true)
            )
            // 同步落进崩溃报告（Logger 那边不一定来得及），下次一看就知道兜底确实响过
            CrashLogUtils.appendNote("fallback alarm fired -> log page requested")
        }.onFailure {
            // 兜底这条路也失败就真没路了 —— 记下来，下次好知道该往哪查
            Logger.e(TAG, "fallback start of log page failed", it)
            CrashLogUtils.appendNote(
                "fallback alarm FAILED - ${it.message ?: it.javaClass.simpleName}"
            )
        }
    }

    companion object {
        private const val TAG = "CrashRestart"
    }
}
