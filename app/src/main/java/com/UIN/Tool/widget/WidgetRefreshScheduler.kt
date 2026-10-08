package com.UIN.Tool.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.UIConfig
import java.util.concurrent.TimeUnit

/**
 * G-28：小部件后台周期刷新。
 * 原状 updatePeriodMillis=0 + 仅应用内广播，应用死后小部件永不更新；
 * 现由 WorkManager 周期任务兜底（≥15min 系统下限，Doze 下延后但会执行），
 * 开关存 UIConfig.widget.autoRefresh（默认开），应用启动与开关变更时幂等应用。
 */
object WidgetRefreshScheduler {
    private const val TAG = "WidgetRefreshScheduler"
    private const val WORK_NAME = "uin.widget.autoRefresh"

    /** WorkManager 周期任务的系统下限 */
    const val INTERVAL_MINUTES = 15L

    /** 幂等应用当前开关：开 → enqueueUniquePeriodicWork(KEEP)，关 → cancelUniqueWork。 */
    fun apply(context: Context) {
        try {
            val wm = WorkManager.getInstance(context)
            if (UIConfig.isWidgetAutoRefreshEnabled()) {
                val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                    INTERVAL_MINUTES, TimeUnit.MINUTES
                ).build()
                wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
                Logger.d(TAG, "widget auto-refresh scheduled (${INTERVAL_MINUTES}min, KEEP)")
            } else {
                wm.cancelUniqueWork(WORK_NAME)
                Logger.d(TAG, "widget auto-refresh cancelled")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "apply failed: ${e.message}", e)
        }
    }
}

/** 周期任务：与应用内同一渲染路径 forceRefreshAllWidgets。 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        WidgetProvider.forceRefreshAllWidgets(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Logger.e("WidgetRefreshWorker", "periodic widget refresh failed: ${e.message}", e)
        Result.retry()
    }
}
