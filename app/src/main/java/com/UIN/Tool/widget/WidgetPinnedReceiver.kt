// app/src/main/java/com/UIN/Tool/widget/WidgetPinnedReceiver.kt
package com.UIN.Tool.widget

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.UIN.Tool.log.Logger
import com.UIN.Tool.utils.PluginShortcutHelper

class WidgetPinnedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "WidgetPinnedReceiver"
        const val EXTRA_PLUGIN_ID = "extra_plugin_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.i(TAG, Str.get(R.string.got_widget_pin_broadcast_intent_acti, intent.action))
        
        when (intent.action) {
            "com.UIN.Tool.WIDGET_PINNED" -> {
                // 3x3 小部件固定成功
                Toast.makeText(context, Str.get(R.string.widget_3x3_widget_added_successfully), Toast.LENGTH_SHORT).show()
                Logger.success(TAG, Str.get(R.string.widget_3x3_widget_pinned_successfully))
                
                // 刷新所有小部件
                WidgetProvider.forceRefreshAllWidgets(context)
            }
            PluginShortcutHelper.ACTION_WIDGET_1X1_PINNED -> {
                // 1x1 快捷方式固定成功，绑定插件
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !pluginId.isNullOrEmpty()) {
                    Widget1x1Config(pluginId).save(context, widgetId)
                    Logger.i(TAG, "已绑定 1x1 小部件: widgetId=$widgetId pluginId=$pluginId")
                } else {
                    Logger.w(TAG, "1x1 小部件固定成功但缺少插件信息: widgetId=$widgetId pluginId=$pluginId")
                }
                Toast.makeText(context, Str.get(R.string.shortcut_added_successfully), Toast.LENGTH_SHORT).show()
                Logger.success(TAG, Str.get(R.string.widget_1x1_shortcut_pinned_successfully))
                
                // 刷新所有1x1小部件
                Widget1x1Provider.refresh1x1Widgets(context)
            }
        }
    }
}