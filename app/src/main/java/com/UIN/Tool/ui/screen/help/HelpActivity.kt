// app/src/main/java/com/UIN/Tool/ui/screen/help/HelpActivity.kt
package com.UIN.Tool.ui.screen.help

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                HelpScreen()
            }
        }
    }
}