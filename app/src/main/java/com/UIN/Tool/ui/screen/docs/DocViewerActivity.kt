// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocViewerActivity.kt
package com.UIN.Tool.ui.screen.docs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class DocViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                DocViewerScreen()
            }
        }
    }
}