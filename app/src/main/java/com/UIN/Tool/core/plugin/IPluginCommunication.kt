package com.UIN.Tool.core.plugin

/**
 * 插件通信接口
 * 插件实现此接口可接收来自其他插件的调用
 */
interface IPluginCommunication {
    
    /**
     * 被其他插件调用
     * @param method 方法名
     * @param params 参数
     * @return 返回值（可以是任意类型）
     */
    fun onPluginCall(method: String, params: Map<String, Any>?): Any?
    
    /**
     * 接收事件总线消息
     */
    fun onPluginEvent(eventType: String, data: Map<String, Any>?) {}
    
    /**
     * 接收直接消息
     */
    fun onPluginMessage(message: PluginMessage) {}
    
    /**
     * 获取插件对外暴露的方法列表
     */
    fun getExposedMethods(): List<String> = emptyList()
}