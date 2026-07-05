package com.UIN.Tool.core.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup

/**
 * 插件接口
 * 所有原生插件必须实现此接口
 */
interface PluginInterface {
    
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
     * @return true表示已处理，false表示未处理
     */
    fun onBackPressed(): Boolean
    
    /**
     * 保存状态
     */
    fun onSaveInstanceState(): Bundle?
    
    /**
     * Activity结果回调
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}
    
    /**
     * 权限请求结果回调
     */
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {}
}