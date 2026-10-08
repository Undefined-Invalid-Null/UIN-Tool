package com.UIN.Tool.ui.screen.proot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

class ContainerManagementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                ContainerManagementScreen()
            }
        }
    }

}
