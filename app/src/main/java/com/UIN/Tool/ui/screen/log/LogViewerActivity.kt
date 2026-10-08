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

        val autoOpen = intent.getBooleanExtra("auto_open", false)
        if (autoOpen) {
            CrashLogUtils.clearNavigateFlag(this)
        }

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