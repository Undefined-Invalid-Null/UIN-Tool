// app/src/main/java/com/UIN/Tool/ui/screen/manage/UIConfigActivity.kt
package com.UIN.Tool.ui.screen.manage

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.UIConfig

class UIConfigActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UIConfig.init(this)
        // u.txt 第 4 条（用户）：切换深浅色 / 语言会重建本 Activity —— 先铺一层
        // 当前主题背景色，避免重建瞬间露出一块与页面不一致的底色（深色下像黑屏）。
        com.UIN.Tool.ui.theme.LaunchScreenBackground.applyPlainBackground(this)

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