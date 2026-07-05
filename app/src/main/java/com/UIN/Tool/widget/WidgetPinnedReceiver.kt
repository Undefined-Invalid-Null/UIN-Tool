// app/src/main/java/com/UIN/Tool/widget/WidgetPinnedReceiver.kt
package com.UIN.Tool.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.UIN.Tool.log.Logger

class WidgetPinnedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "WidgetPinnedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.i(TAG, "收到小部件固定广播: ${intent.action}")
        
        when (intent.action) {
            "com.UIN.Tool.WIDGET_PINNED" -> {
                // 3x3 小部件固定成功
                Toast.makeText(context, "3x3 小部件已添加成功！", Toast.LENGTH_SHORT).show()
                Logger.success(TAG, "3x3 小部件固定成功")
                
                // 刷新所有小部件
                WidgetProvider.forceRefreshAllWidgets(context)
            }
            "com.UIN.Tool.WIDGET_1X1_PINNED" -> {
                // 1x1 快捷方式固定成功
                Toast.makeText(context, "快捷方式已添加动成功！", Toast.LENGTH_SHORT).show()
                Logger.success(TAG, "1x1 快捷方式固定成功")
                
                // 刷新所有1x1小部件
                Widget1x1Provider.refresh1x1Widgets(context)
            }
        }
    }
}