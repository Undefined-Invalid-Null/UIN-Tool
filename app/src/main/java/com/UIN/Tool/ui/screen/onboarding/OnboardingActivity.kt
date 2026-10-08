// OnboardingActivity.kt
package com.UIN.Tool.ui.screen.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.MainActivity
import com.UIN.Tool.ui.theme.UINToolTheme

class OnboardingActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isVersionUpdate = intent.getBooleanExtra("is_version_update", false)
        // G-14：兜底版本串改 BuildConfig，避免与 build.gradle 硬编码漂移
        val versionName = intent.getStringExtra("version_name") ?: com.UIN.Tool.BuildConfig.VERSION_NAME
        val releaseNotes = intent.getStringExtra("release_notes") ?: ""

        // G-15：独立 Activity 的系统栏颜色原先固定走 XML primary_dark，
        // 与 Compose 主题（UIConfig 动态配色）分裂；这里与 MainActivity/SplashActivity 对齐
        if (com.UIN.Tool.utils.UIConfig.isInitialized() &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP
        ) {
            val uiConfig = com.UIN.Tool.utils.UIConfig.getInstance()
            val dark = uiConfig.shouldUseDarkTheme(uiConfig.isSystemDark())
            window.statusBarColor = uiConfig.getColorForMode("status_bar", dark)
            window.navigationBarColor = uiConfig.getColorForMode("navigation_bar", dark)
        }

        setContent {
            UINToolTheme {
                OnboardingScreen(
                    isVersionUpdate = isVersionUpdate,
                    versionName = versionName,
                    releaseNotes = releaseNotes,
                    onNavigateToMain = {
                        // ✅ 只有用户点击按钮才跳转
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}