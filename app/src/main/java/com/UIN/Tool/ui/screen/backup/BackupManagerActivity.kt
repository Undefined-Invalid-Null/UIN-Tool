// app/src/main/java/com/UIN/Tool/ui/screen/backup/BackupManagerActivity.kt
package com.UIN.Tool.ui.screen.backup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class BackupManagerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                BackupScreen()
            }
        }
    }
}