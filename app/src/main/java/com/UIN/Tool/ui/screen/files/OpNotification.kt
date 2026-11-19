// app/src/main/java/com/UIN/Tool/ui/screen/files/OpNotification.kt
package com.UIN.Tool.ui.screen.files

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.UIN.Tool.MainActivity
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.Str

/**
 * 文件操作的**常驻进度通知**（用户反馈 e15 **P0-4**：操作进度被弹窗挡住，回不了文件管理器）。
 *
 * 弹窗那一层照旧（`OpUiState.Running` 不隐藏就显示），这条通知补的是**离开弹窗之后**：
 * 把进度钉在状态栏上，用户去翻别的目录、甚至切出应用，都还能看见它跑到哪儿了、
 * 什么时候结束。点一下回到应用。
 *
 * ## 几个刻意的取舍
 *
 * - **不是前台服务**：普通通知就够，操作本来就在 `viewModelScope` 里跑。
 *   起服务要处理 Android 12+ 的后台启动限制，收益只是「进程优先级」，不值当。
 * - **低优先级 + 不发声**：进度是会连续刷新的，任何一次都响一下会把通知栏刷成噪声。
 * - **节流 500ms**：进度回调每写一块字节就来一次，通知栏刷新是有开销的（binder 调用）。
 * - **`setOngoing(true)`**：进行中的操作不能被用户随手划掉 —— 划掉了进度就没了，
 *   而操作还在跑，那是个骗人的状态。
 * - **收口在一条线上**：调用方用 `state.map { it.op }` 收集，离开 Running 就取消。
 *   不散在各个收尾点里，是因为状态里有好几处直接置 `Idle`，漏一个通知就挂那儿不消失。
 *
 * 通知号 1004 —— 1001 更新 / 1002 远程 / 1003 保活，别撞。
 */
object OpNotification {

    private const val ID = 1004
    private const val CHANNEL_ID = "file_operation"

    /** 同一毫秒连发几十次进度也只落一次通知。 */
    private const val MIN_INTERVAL_MS = 500L

    @Volatile
    private var lastAt = 0L

    /** 发（或更新）一条进度通知。任何失败都静默 —— 通知是**附加**能力，不该拖垮操作本身。 */
    fun show(context: Context, p: OpProgress) {
        val now = SystemClock.elapsedRealtime()
        // lastAt == 0 是「这一轮还没发过」，头一条必须立刻上屏
        if (lastAt != 0L && now - lastAt < MIN_INTERVAL_MS) return
        lastAt = now
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(context, ensureChannel(context, nm))
                // 标题与进度弹窗、胶囊**同一句**（progressTitle 就在同包里），免得三处措辞漂移
                .setContentTitle(progressTitle(p))
                .setContentIntent(openAppIntent(context))
                .setSmallIcon(R.drawable.ic_document)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true)

            val name = p.currentName.trim()
            if (name.isNotEmpty()) {
                builder.setContentText(name)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(name))
            }
            // 总量拿得到就给确定进度（按百分比缩放，避免 >2GB 时 Int 溢出）；拿不到就转圈
            if (p.totalBytes > 0) {
                val pct = ((p.doneBytes.coerceIn(0L, p.totalBytes) * 1000) / p.totalBytes).toInt()
                builder.setProgress(1000, pct, false)
            }
            nm.notify(ID, builder.build())
        } catch (t: Throwable) {
            Logger.w("OpNotification", "show failed: ${t.message}")
        }
    }

    /** 收掉。操作回到 Idle / 转进撞名询问时调用 —— 后者需要的是用户注意，不是状态栏。 */
    fun cancel(context: Context) {
        lastAt = 0L
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ID)
        } catch (t: Throwable) {
            Logger.w("OpNotification", "cancel failed: ${t.message}")
        }
    }

    private fun ensureChannel(context: Context, nm: NotificationManager): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    Str.get(R.string.files_op_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = Str.get(R.string.files_op_channel_desc)
                    setShowBadge(false)
                }
            )
        }
        return CHANNEL_ID
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
