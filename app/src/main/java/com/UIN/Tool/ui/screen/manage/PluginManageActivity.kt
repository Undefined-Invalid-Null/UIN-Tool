// app/src/main/java/com/UIN/Tool/ui/screen/manage/PluginManageActivity.kt
package com.UIN.Tool.ui.screen.manage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.ui.theme.UINToolTheme

class PluginManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                PluginManageScreen(
                    navController = rememberNavController()
                )
            }
        }
    }
}