// app/src/main/java/com/UIN/Tool/ui/screen/dev/DevDocActivity.kt
package com.UIN.Tool.ui.screen.dev

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class DevDocActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                com.UIN.Tool.ui.screen.docs.DocViewerScreen()
            }
        }
    }
}