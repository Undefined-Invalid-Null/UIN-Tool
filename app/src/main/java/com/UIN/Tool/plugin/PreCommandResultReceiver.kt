// plugin/PreCommandResultReceiver.kt
package com.UIN.Tool.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.UIN.Tool.constants.AppConstants as Constants

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
        Logger.d(TAG, Str.get(R.string.received_pre_command_result_callback))
        val pluginId = intent.getStringExtra(PluginHostActivity.EXTRA_PLUGIN_ID)
        if (pluginId.isNullOrEmpty()) {
            Logger.e(TAG, Str.get(R.string.plugin_id_empty_ignoring_result))
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
            Logger.success(TAG, Str.get(R.string.pre_command_succeeded_marking_pre_cm))

            // 后台启动后端（阻塞，避免占用主线程）
            Thread {
                try {
                    val info = getPluginInfo(pluginId)
                    if (info != null) {
                        val started = PluginBackendManager.startBackend(context.applicationContext, info)
                        Logger.d(TAG, Str.get(R.string.backend_start_result_started, started))
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, Str.get(R.string.backend_start_error_e_message_2, e.message), e)
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
            Logger.e(TAG, Str.get(R.string.failed_to_get_plugin_info_e_message, e.message), e)
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
            Logger.e(TAG, Str.get(R.string.failed_to_open_host_e_message, e.message), e)
        }
    }
}
