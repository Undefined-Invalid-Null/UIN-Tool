// app/src/main/java/com/UIN/Tool/ui/screen/manage/UIConfigActivity.kt
package com.UIN.Tool.ui.screen.manage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.ui.theme.UINToolTheme

class UIConfigActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            UINToolTheme {
                // 调用 UIConfigScreen 中的 UI
                UIConfigScreen(
                    navController = rememberNavController(),
                    onBack = { finish() }
                )
            }
        }
    }
}