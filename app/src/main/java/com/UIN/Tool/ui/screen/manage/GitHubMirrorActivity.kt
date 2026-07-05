// app/src/main/java/com/UIN/Tool/ui/screen/manage/GitHubMirrorActivity.kt
package com.UIN.Tool.ui.screen.manage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.ui.theme.UINToolTheme

class GitHubMirrorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                GitHubMirrorScreen(
                    navController = rememberNavController()
                )
            }
        }
    }
}