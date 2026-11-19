package com.UIN.Tool.core.keepalive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Str

/**
 * 「后台保活」的常驻通知（用户反馈 d15-②：开关写着「配合电池优化豁免、通知栏保活」，
 * 结果两项都没有 —— 描述承诺了就得兑现）。
 *
 * ## 它到底保的是谁
 *
 * 保活的真正对象是**共享调度器**（Termux 侧进程）：`keep_alive` 标记一写，
 * 宿主进程被杀也不会连累它。所以本服务不参与「让后端活着」，它做两件事：
 *
 * 1. **把状态摆出来** —— 没有这条常驻通知，用户根本不知道保活此刻是开是关，
 *    更不会知道它是从什么时候开始的（这正是反馈里「没有通知保活」的意思）；
 * 2. **顺带给宿主进程前台优先级** —— 通知在，进程被回收的概率显著下降，
 *    而宿主活着才写得动 `host_alive`，调度器就不必走「宿主超时自杀」那条分支。
 *
 * 起停归开关：设置页打开开关时 [start]、关掉时 [stop]，App 启动时若开关仍开着也补一次
 * （否则重启应用后通知就没了，看起来像保活失效）。
 *
 * 通知 ID 用 1003：1001 是更新下载，1002 是远程服务，撞了号会互相覆盖。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "keep_alive_channel"
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // 后台启动受限等场景起不来：不阻断开关本身，只是这一轮没有通知
                Logger.w(TAG, "start failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                Logger.w(TAG, "stop failed: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = Str.get(R.string.keep_alive_notif_text)
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setContentTitle(Str.get(R.string.keep_alive_notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            // 系统自带图标：小图标必须是纯剪影，主题里的彩色图标直接摆上去会糊成一块
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // 必须尽快进入前台，否则 startForegroundService 的 5s 限制会把服务杀掉
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    /**
     * 点通知回到应用。直接点名 MainActivity（singleTask）而不是 launcher 入口：
     * h.txt 第 1 条 —— 走 SplashActivity 会在重新进主界面时把 tab 重置成默认页
     * （默认就是插件页），用户上次停在文件页也会被顶走。MainActivity 的
     * onNewIntent 对「纯启动意图」保持当前页，只收掉浮在上面的插件页。
     */
    private fun openAppIntent(): PendingIntent? {
        val intent = Intent(this, com.UIN.Tool.MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // m.txt 第 12 条：告诉 MainActivity「这是保活通知点的」——保持当前页。
            putExtra(com.UIN.Tool.MainActivity.EXTRA_FROM_KEEP_ALIVE, true)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 建通道前先判存在，避免每次 startForeground 都刷一条「channel created」日志。 */
    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        Str.get(R.string.keep_alive_notif_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = Str.get(R.string.keep_alive_notif_title)
                        setShowBadge(false)
                    }
                )
            }
        }
        return CHANNEL_ID
    }
}
