// app/src/main/java/com/UIN/Tool/ui/screen/manage/GitHubMirrorActivity.kt
package com.UIN.Tool.ui.screen.manage

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class GitHubMirrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                GitHubMirrorScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}