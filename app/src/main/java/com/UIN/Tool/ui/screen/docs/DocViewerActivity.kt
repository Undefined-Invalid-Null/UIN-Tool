// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocViewerActivity.kt
package com.UIN.Tool.ui.screen.docs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class DocViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                DocViewerScreen()
            }
        }
    }
}