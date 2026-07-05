// app/src/main/java/com/UIN/Tool/ui/screen/onboarding/OnboardingActivity.kt
package com.UIN.Tool.ui.screen.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.MainActivity
import com.UIN.Tool.ui.theme.UINToolTheme

class OnboardingActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isVersionUpdate = intent.getBooleanExtra("is_version_update", false)
        val versionName = intent.getStringExtra("version_name") ?: "4.0.0"
        
        setContent {
            UINToolTheme {
                OnboardingScreen(
                    isVersionUpdate = isVersionUpdate,
                    versionName = versionName,
                    onNavigateToMain = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}