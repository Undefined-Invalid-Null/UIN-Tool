// app/src/main/java/com/UIN/Tool/ui/screen/dev/DevDocActivity.kt
package com.UIN.Tool.ui.screen.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class DevDocActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                DevDocScreen()
            }
        }
    }
}