// app/src/main/java/com/UIN/Tool/ui/screen/log/LogViewerActivity.kt
package com.UIN.Tool.ui.screen.log

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.screen.log.LogViewerScreen
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.CrashLogUtils

class LogViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 6.2.0 批次42：auto_open 不在此清 just_crashed 标记 —— LogViewerScreen 要先读它
        // 才能显示「发生异常」提示条，读完由屏幕的 LaunchedEffect 自行清除；这里提前清
        // 会让提示条永远出不来（历史行为，闪退跳转目标也一直不是本页）。
        setContent {
            UINToolTheme {
                LogViewerScreen(
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        CrashLogUtils.clearNavigateFlag(this)
    }
}