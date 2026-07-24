// plugin/PluginInterface.kt
package com.UIN.Tool.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup

/**
 * 插件接口 - 所有原生插件必须实现此接口
 */
interface PluginInterface {

    // ==================== 生命周期方法 ====================

    /**
     * 创建插件视图
     * @param context 插件上下文 (PluginContext)
     * @param container 父容器
     * @param savedInstanceState 保存的状态
     * @return 插件视图
     */
    fun onCreateView(context: Context, container: ViewGroup?, savedInstanceState: Bundle?): View?

    /**
     * 插件恢复
     */
    fun onResume()

    /**
     * 插件暂停
     */
    fun onPause()

    /**
     * 插件销毁
     */
    fun onDestroy()

    /**
     * 返回键处理
     * @return true 表示已处理，false 表示未处理
     */
    fun onBackPressed(): Boolean

    /**
     * 保存状态
     */
    fun onSaveInstanceState(): Bundle?

    /**
     * Activity 结果回调
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}

    /**
     * 权限请求结果回调
     */
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {}

    // ==================== 宿主集成 ====================

    /**
     * 获取插件标题（用于显示在 Toolbar）
     */
    fun getPluginTitle(): String? = null

    /**
     * 获取插件菜单项（用于添加到 Toolbar 菜单）
     */
    fun getPluginMenuItems(): List<PluginMenuItem>? = null

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

    // ==================== Termux 后端执行接口 ====================

    /**
     * 检查后端是否正在运行
     * 默认返回 false，由宿主注入实际实现
     */
    fun isBackendRunning(): Boolean = false

    /**
     * 获取后端端口
     * 默认返回 0，由宿主注入实际端口
     */
    fun getBackendPort(): Int = 0

    /**
     * 通过 HTTP 调用后端 API
     * 默认空实现，由宿主注入实际调用逻辑
     */
    fun callBackendApi(
        path: String,
        method: String = "GET",
        body: String? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        // 默认空实现
    }

    /**
     * 执行后端任务（通过 WebSocket）
     * 默认空实现，由宿主注入实际执行逻辑
     */
    fun executeBackendTask(
        taskType: String,
        payload: Map<String, Any>,
        callback: (Map<String, Any>?) -> Unit
    ) {
        // 默认空实现
    }
}

/**
 * 插件菜单项数据类
 */
data class PluginMenuItem(
    val id: Int,
    val title: String,
    val icon: Int? = null,
    val onClick: () -> Unit
)