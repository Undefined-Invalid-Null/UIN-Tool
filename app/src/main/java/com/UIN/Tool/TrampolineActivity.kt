// app/src/main/java/com/UIN/Tool/TrampolineActivity.kt
package com.UIN.Tool

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginHostActivity
import com.UIN.Tool.plugin.PluginManager

/**
 * 跳板 Activity - 用于处理快捷方式启动
 * 模拟系统启动器行为，正确恢复任务栈
 */
class TrampolineActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "TrampolineActivity"
        const val EXTRA_TARGET = "target"
        const val EXTRA_PLUGIN_ID = "plugin_id"
        const val TARGET_MAIN = "main"
        const val TARGET_PLUGIN = "plugin"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.i(TAG, "========================================")
        Logger.i(TAG, "TrampolineActivity onCreate")
        Logger.i(TAG, "Intent: $intent")
        
        val target = intent.getStringExtra(EXTRA_TARGET) ?: TARGET_PLUGIN
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        val selectedTab = intent.getStringExtra("selected_tab")
        val checkUpdate = intent.getBooleanExtra("check_update", false)
        
        Logger.i(TAG, "目标: $target, 插件ID: $pluginId, selectedTab: $selectedTab")
        
        Handler(Looper.getMainLooper()).postDelayed({
            when (target) {
                TARGET_MAIN -> openMainActivity(selectedTab, checkUpdate)
                else -> openPlugin(pluginId)
            }
        }, 50)
    }
    
    /**
     * 打开主界面
     */
    private fun openMainActivity(selectedTab: String?, checkUpdate: Boolean) {
        Logger.i(TAG, "打开主界面 (模拟系统启动器行为)")
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("selected_tab", selectedTab ?: "tools")
                putExtra("check_update", checkUpdate)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Logger.success(TAG, "主界面打开/恢复成功")
        } catch (e: Exception) {
            Logger.e(TAG, "打开主界面失败: ${e.message}", e)
        } finally {
            finish()
        }
    }
    
    /**
     * 打开插件
     */
    private fun openPlugin(pluginId: String?) {
        Logger.i(TAG, "打开插件: $pluginId")
        
        if (pluginId.isNullOrEmpty()) {
            Logger.w(TAG, "插件ID为空，打开主界面")
            openMainActivity(null, false)
            return
        }
        
        try {
            // ✅ 使用 ServiceLocator 获取 PluginManager
            val pluginManager = ServiceLocator.getPluginManager()
            val info = pluginManager.getPluginInfo(pluginId)
            
            if (info == null) {
                Logger.w(TAG, "插件不存在: $pluginId")
                openMainActivity(null, false)
                return
            }
            
            Logger.i(TAG, "插件存在: ${info.name}")
            
            val intent = Intent(this, PluginHostActivity::class.java).apply {
                putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId)
            }
            startActivity(intent)
            Logger.success(TAG, "插件启动成功: ${info.name}")
            
        } catch (e: Exception) {
            Logger.e(TAG, "启动插件失败: ${e.message}", e)
            openMainActivity(null, false)
        } finally {
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "onDestroy")
        Logger.i(TAG, "========================================")
    }
}