package com.UIN.Tool.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Str

/**
 * B-61：更新下载前台服务。
 *
 * 下载原先只跑在普通 Thread 上，虽然发了进度通知，但通知并不挂在任何服务上：
 * 用户把应用切到后台后，进程随时可能被系统回收，下载**静默中断**（没有取消按钮、
 * 没有失败提示，下次进界面才发现包没了）。
 *
 * 这里只负责「持有前台优先级」，下载逻辑仍在 [UpdateDownloader]：
 * - 通知 ID 与 UpdateDownloader 的进度通知**同为 1001**，`startForeground` 之后
 *   UpdateDownloader 的 `notify(1001, …)` 会直接更新这条前台通知（进度条/文案无缝衔接）；
 * - 下载成功/失败/取消时由 UpdateDownloader 调用 [stop] 收掉服务。
 *
 * 启动失败（如 Android 12+ 后台启动受限）不阻断下载，只是失去前台保护，降级为原行为。
 */
class UpdateDownloadService : Service() {

    companion object {
        private const val TAG = "UpdateDownloadService"
        private const val CHANNEL_ID = "update_download_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_VERSION_NAME = "version_name"
        private const val EXTRA_CANCEL = "cancel"

        /**
         * B-61②：通知栏「取消」动作的回接点。
         * 服务只负责前台与通知，下载状态在 [UpdateDownloader] 实例上；由下载器在
         * 开始下载时注册、结束时反注册，避免服务持有 Activity/单例之外的引用。
         */
        @Volatile
        private var cancelAction: (() -> Unit)? = null

        fun setCancelAction(action: (() -> Unit)?) {
            cancelAction = action
        }

        fun start(context: Context, versionName: String) {
            try {
                val intent = Intent(context, UpdateDownloadService::class.java)
                    .putExtra(EXTRA_VERSION_NAME, versionName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Logger.d(TAG, "foreground service started (version=$versionName)")
            } catch (e: Exception) {
                Logger.w(TAG, "start foreground service failed, download continues without it: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, UpdateDownloadService::class.java))
            } catch (e: Exception) {
                Logger.w(TAG, "stop foreground service failed: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏「取消下载」按钮：由 Pending 直投本服务
        if (intent?.getBooleanExtra(EXTRA_CANCEL, false) == true) {
            Logger.i(TAG, "cancel requested from notification")
            cancelAction?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }

        val versionName = intent?.getStringExtra(EXTRA_VERSION_NAME)
            ?: Str.get(R.string.downloading_update)
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, UpdateDownloadService::class.java).putExtra(EXTRA_CANCEL, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setContentTitle(Str.get(R.string.downloading_uin_tool_versionname, versionName))
            .setContentText(Str.get(R.string.downloading_0))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, 0, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                Str.get(R.string.cancel_download),
                cancelIntent
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

    /** 与 UpdateDownloader 共用同一 channel，创建前先判存在避免重复日志 */
    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        Str.get(R.string.update_download),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = Str.get(R.string.app_update_download_progress)
                        setShowBadge(false)
                    }
                )
            }
        }
        return CHANNEL_ID
    }
}
