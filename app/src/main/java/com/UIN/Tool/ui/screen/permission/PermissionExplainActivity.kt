// app/src/main/java/com/UIN/Tool/ui/screen/permission/PermissionExplainActivity.kt
package com.UIN.Tool.ui.screen.permission

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.ui.theme.UINToolTheme

class PermissionExplainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                PermissionExplainScreen(
                    navController = rememberNavController()
                )
            }
        }
    }
}