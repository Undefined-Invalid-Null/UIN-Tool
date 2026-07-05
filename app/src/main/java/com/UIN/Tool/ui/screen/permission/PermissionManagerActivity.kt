// app/src/main/java/com/UIN/Tool/ui/screen/permission/PermissionManagerActivity.kt
package com.UIN.Tool.ui.screen.permission

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class PermissionManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                PermissionManagerScreen()
            }
        }
    }
}