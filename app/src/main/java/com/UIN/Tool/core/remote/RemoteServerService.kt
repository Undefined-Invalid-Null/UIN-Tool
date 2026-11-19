package com.UIN.Tool.core.remote

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
import com.UIN.Tool.ui.screen.files.RemoteManager
import com.UIN.Tool.ui.screen.files.lanIpv4
import com.UIN.Tool.utils.Str

/**
 * 远程服务（FTP / HTTP）的前台通知（用户答复 4：**加前台通知 + 停止入口**）。
 *
 * ## 为什么需要它
 *
 * 两个服务端刻意活在 [RemoteManager] 单例上 —— 起服务是为了让电脑那头连过来，人多半
 * 切回文件页继续干活，页面一关就掐 socket 等于功能残废。可「脱离页面继续跑」也意味着
 * **脱离任何界面持有者**：用户切到后台后进程随时可能被回收，服务静默消失，而电脑那头
 * 只会看到「连接被拒」，回来一看却不知道它是什么时候没的。
 *
 * 这里只负责「持有前台优先级 + 把状态摆出来」，起停仍归远程管理页：
 * - 页面按 [RemoteManager] 的 `state` 变化调 [start] / [stop]，重复 start 只刷新内容；
 * - 通知正文就是**当前在听的地址**（`ftp://192.168.1.5:2121`），照抄下来就能在电脑上用；
 * - 通知栏「停止」直投本服务（与更新通知的「取消」同一套路），把两个服务端一起收掉。
 *
 * 通知 ID 用 1002：1001 是更新下载的进度通知，撞了号会互相覆盖。
 */
class RemoteServerService : Service() {

    companion object {
        private const val TAG = "RemoteServerService"
        private const val CHANNEL_ID = "remote_server_channel"
        private const val NOTIFICATION_ID = 1002
        private const val EXTRA_STOP = "stop"

        fun start(context: Context) {
            try {
                val intent = Intent(context, RemoteServerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // 起不来（Android 12+ 后台启动受限等）不阻断功能：服务照跑，只是没有前台保护
                Logger.w(TAG, "start foreground service failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RemoteServerService::class.java))
            } catch (e: Exception) {
                Logger.w(TAG, "stop foreground service failed: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏「停止」：PendingIntent 直投本服务，把两个服务端一起收掉再收自己
        if (intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            Logger.i(TAG, "stop requested from notification")
            RemoteManager.ftp.stop()
            RemoteManager.http.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RemoteServerService::class.java).putExtra(EXTRA_STOP, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = summary()
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setContentTitle(Str.get(R.string.remote_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                Str.get(R.string.remote_stop),
                stopIntent
            )
            .build()
        // 必须尽快进入前台，否则 startForegroundService 的 5s 限制会让服务被系统杀掉
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    /**
     * 通知正文 = **当前在听的那几个地址**，一行一个 —— 通知本身就该把有用信息给全，
     * 用户不必为了看一眼端口专门切回应用。都不在跑（本该被收掉的那一刻）退回「已停止」。
     */
    private fun summary(): String {
        val ip = lanIpv4()
        val parts = mutableListOf<String>()
        if (ip != null) {
            val ftp = RemoteManager.ftp.state.value
            val http = RemoteManager.http.state.value
            if (ftp.running && ftp.port > 0) parts += "ftp://$ip:${ftp.port}"
            if (http.running && http.port > 0) parts += "http://$ip:${http.port}"
        }
        return parts.joinToString("\n").ifEmpty { Str.get(R.string.remote_stopped) }
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
                        Str.get(R.string.remote_title),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = Str.get(R.string.remote_notification_title)
                        setShowBadge(false)
                    }
                )
            }
        }
        return CHANNEL_ID
    }
}
