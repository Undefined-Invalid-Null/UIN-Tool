// app/src/main/java/com/UIN/Tool/ui/screen/log/LogViewerActivity.kt
package com.UIN.Tool.ui.screen.log

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.MainActivity
import com.UIN.Tool.ui.screen.log.LogViewerScreen
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.CrashLogUtils

class LogViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 6.2.0 批次42：auto_open 不在此清 just_crashed 标记 —— LogViewerScreen 要先读它
        // 才能显示「发生异常」提示条，读完由屏幕的 LaunchedEffect 自行清除；这里提前清
        // 会让提示条永远出不来（历史行为，闪退跳转目标也一直不是本页）。
        setContent {
            UINToolTheme {
                LogViewerScreen(
                    onBack = { goBackToMainPage() }
                )
            }
        }
    }

    /**
     * h.txt 第 8 条：闪退后直接拉起日志页时，崩溃处理里那次「先起 MainActivity」可能
     * 被系统丢掉，任务栈里就只剩日志页 —— 此时返回会直接退出应用。
     * 检测到自己就是根页面时，先补一个主界面再结束，保证返回键/左上角按钮都回到主页。
     */
    private fun goBackToMainPage() {
        if (isTaskRoot) {
            try {
                startActivity(Intent(this, MainActivity::class.java))
            } catch (e: Exception) {
                AppLog.e(TAG, "start main from log page failed", e)
            }
        }
        finish()
    }

    override fun onBackPressed() {
        goBackToMainPage()
        CrashLogUtils.clearNavigateFlag(this)
    }

    companion object {
        private const val TAG = "LogViewerActivity"
    }
}
