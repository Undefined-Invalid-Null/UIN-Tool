// ui/screen/dev/BasePluginWizardActivity.kt
package com.UIN.Tool.ui.screen.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class BasePluginWizardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uiType = intent.getStringExtra("ui_type") ?: "native"
        val backendType = intent.getStringExtra("backend_type") ?: ""
        setContent {
            UINToolTheme {
                BasePluginWizardScreen(
                    uiType = uiType,
                    backendType = backendType,
                    onFinish = { finish() }
                )
            }
        }
    }
}