// app/src/main/java/com/UIN/Tool/ui/screen/permission/PluginPermissionActivity.kt
package com.UIN.Tool.ui.screen.permission

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class PluginPermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        setContent {
            UINToolTheme {
                PluginPermissionScreen(initialPluginId = pluginId)
            }
        }
    }

    companion object {
        const val EXTRA_PLUGIN_ID = "extra_plugin_id"
    }
}