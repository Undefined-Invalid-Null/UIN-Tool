// app/src/main/java/com/UIN/Tool/plugin/PluginInterface.kt
package com.UIN.Tool.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner

/**
 * 插件接口 - 所有插件必须实现
 */
interface PluginInterface {

    // ==================== 生命周期 ====================
    
    fun onCreateView(context: Context, container: ViewGroup?, savedInstanceState: Bundle?): View?

    fun onResume()
    fun onPause()
    fun onDestroy()
    fun onBackPressed(): Boolean
    fun onSaveInstanceState(): Bundle?
    
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {}
    
    // ==================== 宿主 UI 集成 ====================
    
    /**
     * 获取插件标题（用于显示在 Toolbar）
     */
    fun getPluginTitle(): String? = null
    
    /**
     * 获取插件菜单项（用于添加到 Toolbar 菜单）
     */
    fun getPluginMenuItems(): List<PluginMenuItem>? = null
    
    /**
     * 获取插件 Fragment（用于嵌入宿主 Fragment 容器）
     */
    fun getPluginFragment(): Fragment? = null
    
    /**
     * 获取插件视图（用于嵌入宿主 ViewGroup）
     */
    fun getPluginView(context: Context, container: ViewGroup?): View? = null
    
    /**
     * 获取插件配置视图（用于插件设置页面）
     */
    fun getConfigView(context: Context): View? = null
    
    /**
     * 处理宿主发送的事件
     */
    fun onHostEvent(event: String, data: Bundle?) {}
    
    /**
     * 发送事件到宿主
     */
    fun sendHostEvent(event: String, data: Bundle?) {}
    
    /**
     * 获取宿主提供的服务
     */
    fun <T> getHostService(serviceClass: Class<T>): T? = null
}

// ==================== 插件菜单项 ====================

data class PluginMenuItem(
    val id: Int,
    val title: String,
    val icon: Int? = null,
    val onClick: () -> Unit
)

// ==================== 宿主服务接口 ====================

interface HostService {
    fun showToast(message: String, duration: Int = android.widget.Toast.LENGTH_SHORT)
    fun showLoading(message: String): HostLoadingDialog
    fun hideLoading()
    fun showDialog(title: String, message: String, positiveText: String, negativeText: String? = null, onPositive: () -> Unit, onNegative: (() -> Unit)? = null)
    fun getWorkFolder(): String
    fun openUrl(url: String)
    fun shareText(text: String)
    fun copyToClipboard(text: String)
    fun getPluginManager(): PluginManager
}

// ==================== 加载对话框接口 ====================

interface HostLoadingDialog {
    fun setMessage(message: String)
    fun setProgress(progress: Int)
    fun dismiss()
}