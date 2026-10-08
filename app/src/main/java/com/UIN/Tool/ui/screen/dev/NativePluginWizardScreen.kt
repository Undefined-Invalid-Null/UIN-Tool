// app/src/main/java/com/UIN/Tool/ui/screen/dev/NativePluginWizardScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class NativePluginWizardScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uiType = intent.getStringExtra("ui_type") ?: "native"
        setContent {
            UINToolTheme {
                BasePluginWizardScreen(
                    uiType = uiType,
                    onFinish = { finish() }
                )
            }
        }
    }
}