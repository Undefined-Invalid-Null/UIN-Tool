// plugin/PreCommandResultReceiver.kt
package com.UIN.Tool.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.UIN.Tool.utils.Constants

/**
 * 接收 pre-command 终端会话结束结果（通过 RUN_COMMAND intent 的 PendingIntent 回调）。
 *
 * - 退出码 0：标记 pre_cmd_done，后台启动后端，并将宿主带回前台刷新状态。
 * - 退出码非 0：将宿主带回前台并提示失败信息。
 */
class PreCommandResultReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PreCommandResultReceiver"
        const val ACTION = "com.UIN.Tool.PRE_COMMAND_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Logger.d(TAG, "📬 收到 pre-command 结果回调")
        val pluginId = intent.getStringExtra(PluginHostActivity.EXTRA_PLUGIN_ID)
        if (pluginId.isNullOrEmpty()) {
            Logger.e(TAG, "❌ 插件ID为空，忽略结果")
            return
        }

        val resultBundle = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE)
        val exitCode = resultBundle?.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, -1) ?: -1
        val errmsg = resultBundle?.getString(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG)
        Logger.d(TAG, "pluginId=$pluginId, exitCode=$exitCode, errmsg=$errmsg")

        val success = exitCode == 0

        if (success) {
            val prefs = context.getSharedPreferences("${Constants.PREF_PLUGIN_DATA_PREFIX}$pluginId", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PluginHostActivity.KEY_PRE_CMD_DONE, true).apply()
            Logger.success(TAG, "✅ pre-command 执行成功，标记 pre_cmd_done")

            // 后台启动后端（阻塞，避免占用主线程）
            Thread {
                try {
                    val info = getPluginInfo(pluginId)
                    if (info != null) {
                        val started = PluginBackendManager.startBackend(context.applicationContext, info)
                        Logger.d(TAG, "后端启动结果: $started")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "启动后端异常: ${e.message}", e)
                }
                Handler(Looper.getMainLooper()).post {
                    openHost(context, pluginId, success, exitCode, errmsg)
                }
            }.start()
        } else {
            Handler(Looper.getMainLooper()).post {
                openHost(context, pluginId, success, exitCode, errmsg)
            }
        }
    }

    private fun getPluginInfo(pluginId: String): PluginInfo? {
        return try {
            ServiceLocator.getPluginManager().getPluginInfo(pluginId)
        } catch (e: Exception) {
            Logger.e(TAG, "获取插件信息失败: ${e.message}", e)
            null
        }
    }

    private fun openHost(
        context: Context,
        pluginId: String,
        success: Boolean,
        exitCode: Int,
        errmsg: String?
    ) {
        val hostIntent = Intent(context, PluginHostActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
            putExtra(PluginHostActivity.EXTRA_PRE_COMMAND_FINISHED, true)
            putExtra(PluginHostActivity.EXTRA_PRE_COMMAND_SUCCESS, success)
            putExtra(PluginHostActivity.EXTRA_PRE_COMMAND_EXIT_CODE, exitCode)
            putExtra(PluginHostActivity.EXTRA_PRE_COMMAND_ERRMSG, errmsg ?: "")
        }
        try {
            context.startActivity(hostIntent)
        } catch (e: Exception) {
            Logger.e(TAG, "打开宿主失败: ${e.message}", e)
        }
    }
}
