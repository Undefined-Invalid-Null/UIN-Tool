// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocBrowserActivity.kt
package com.UIN.Tool.ui.screen.docs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class DocBrowserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                DocBrowserScreen()
            }
        }
    }
}