// ui/screen/manage/BackendSettingsActivity.kt
package com.UIN.Tool.ui.screen.manage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class BackendSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                BackendSettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
