// app/src/main/java/com/UIN/Tool/ShortcutAppInfoActivity.kt
package com.UIN.Tool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.UIN.Tool.utils.AppLog

/**
 * o.txt 第 15 条：「打开应用信息」快捷方式的中转页。
 *
 * 快捷方式的 intent 必须落在本应用内（静态 XML 用 `targetClass`，动态快捷方式要求
 * 显式 component），而「应用信息」在系统设置那个包里。这里用一帧无界面的 Activity
 * 转一手：拉起系统页后立刻 finish，用户看不到中间这一帧。
 *
 * 非导出：快捷方式由系统 LauncherAppsService 代为启动，不需要 exported（与
 * `shortcuts.xml` 里原有的 DocBrowser / BackendSettings 一致）。
 */
class ShortcutAppInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "open app info failed: ${e.message}")
        }
        finish()
    }

    private companion object {
        const val TAG = "ShortcutAppInfo"
    }
}
