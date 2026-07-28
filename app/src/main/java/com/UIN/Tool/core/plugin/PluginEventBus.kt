package com.UIN.Tool.core.plugin

import android.os.Handler
import android.os.Looper
import com.UIN.Tool.log.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 插件事件总线
 * 支持一对多广播通信
 */
object PluginEventBus {
    
    private const val TAG = "PluginEventBus"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 事件监听器存储：key = eventType, value = 监听器列表
    private val listeners = ConcurrentHashMap<String, MutableList<EventListener>>()
    
    // 插件消息监听器（针对特定插件ID）
    private val pluginListeners = ConcurrentHashMap<String, MutableList<PluginMessageListener>>()
    
    // 是否启用调试日志
    var debugEnabled = false
    
    /**
     * 事件监听器接口
     */
    interface EventListener {
        fun onEvent(eventType: String, data: Map<String, Any>?)
    }
    
    /**
     * 插件消息监听器接口
     */
    interface PluginMessageListener {
        fun onMessage(message: PluginMessage)
    }
    
    /**
     * 注册事件监听器
     * @param eventType 事件类型
     * @param listener 监听器
     * @param sticky 是否粘性（会立即收到最近一次该事件的数据）
     */
    fun register(eventType: String, listener: EventListener, sticky: Boolean = false) {
        listeners.getOrPut(eventType) { CopyOnWriteArrayList() }.add(listener)
        Logger.d(TAG, "注册事件监听: $eventType")
        
        // 粘性事件处理
        if (sticky) {
            val stickyData = getStickyEvent(eventType)
            if (stickyData != null) {
                mainHandler.post {
                    listener.onEvent(eventType, stickyData)
                }
            }
        }
    }
    
    /**
     * 注册插件消息监听器
     * @param pluginId 插件ID
     * @param listener 监听器
     */
    fun registerPluginListener(pluginId: String, listener: PluginMessageListener) {
        pluginListeners.getOrPut(pluginId) { CopyOnWriteArrayList() }.add(listener)
        Logger.d(TAG, "注册插件消息监听: $pluginId")
    }
    
    /**
     * 取消注册事件监听器
     */
    fun unregister(eventType: String, listener: EventListener) {
        listeners[eventType]?.remove(listener)
        Logger.d(TAG, "取消事件监听: $eventType")
    }
    
    /**
     * 取消注册插件消息监听器
     */
    fun unregisterPluginListener(pluginId: String, listener: PluginMessageListener) {
        pluginListeners[pluginId]?.remove(listener)
        Logger.d(TAG, "取消插件消息监听: $pluginId")
    }
    
    /**
     * 发送事件（广播）
     * @param eventType 事件类型
     * @param data 数据
     * @param sticky 是否粘性
     */
    fun postEvent(eventType: String, data: Map<String, Any>? = null, sticky: Boolean = false) {
        if (debugEnabled) {
            Logger.d(TAG, "发送事件: $eventType, data: $data")
        }
        
        // 保存粘性事件
        if (sticky) {
            saveStickyEvent(eventType, data)
        }
        
        val list = listeners[eventType]
        if (list.isNullOrEmpty()) {
            if (debugEnabled) {
                Logger.d(TAG, "没有监听器: $eventType")
            }
            return
        }
        
        // 在主线程分发
        mainHandler.post {
            list.forEach { listener ->
                try {
                    listener.onEvent(eventType, data)
                } catch (e: Exception) {
                    Logger.e(TAG, "事件分发异常: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * 发送插件消息
     * @param message 消息
     */
    fun postMessage(message: PluginMessage) {
        if (debugEnabled) {
            Logger.d(TAG, "发送消息: ${message.action} from ${message.sender} to ${message.target}")
        }
        
        // 如果指定了目标，只发给目标插件
        if (message.target != null) {
            val list = pluginListeners[message.target]
            if (!list.isNullOrEmpty()) {
                mainHandler.post {
                    list.forEach { listener ->
                        try {
                            listener.onMessage(message)
                        } catch (e: Exception) {
                            Logger.e(TAG, "消息分发异常: ${e.message}", e)
                        }
                    }
                }
            }
            return
        }
        
        // 广播给所有插件
        pluginListeners.values.forEach { list ->
            mainHandler.post {
                list.forEach { listener ->
                    try {
                        listener.onMessage(message)
                    } catch (e: Exception) {
                        Logger.e(TAG, "消息分发异常: ${e.message}", e)
                    }
                }
            }
        }
    }
    
    /**
     * 发送给指定插件
     */
    fun sendToPlugin(targetPluginId: String, action: String, data: Map<String, Any>? = null) {
        val message = PluginMessage(
            type = "direct",
            action = action,
            sender = "system",
            target = targetPluginId,
            data = data
        )
        postMessage(message)
    }
    
    /**
     * 广播给所有插件
     */
    fun broadcastToPlugins(action: String, data: Map<String, Any>? = null) {
        val message = PluginMessage(
            type = "broadcast",
            action = action,
            sender = "system",
            target = null,
            data = data
        )
        postMessage(message)
    }
    
    // ==================== 粘性事件存储 ====================
    
    private val stickyEvents = ConcurrentHashMap<String, Map<String, Any>?>()
    
    private fun saveStickyEvent(eventType: String, data: Map<String, Any>?) {
        stickyEvents[eventType] = data
    }
    
    private fun getStickyEvent(eventType: String): Map<String, Any>? {
        return stickyEvents[eventType]
    }
    
    fun clearStickyEvents() {
        stickyEvents.clear()
    }
    
    /**
     * 移除所有监听器（用于清理）
     */
    fun clearAll() {
        listeners.clear()
        pluginListeners.clear()
        stickyEvents.clear()
        Logger.d(TAG, "事件总线已清理")
    }
}