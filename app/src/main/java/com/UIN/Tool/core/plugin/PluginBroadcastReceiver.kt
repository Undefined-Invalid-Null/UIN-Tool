package com.UIN.Tool.core.plugin

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.UIN.Tool.log.Logger

/**
 * 插件系统广播接收器
 * 用于接收系统广播并转发给插件
 */
object PluginBroadcastReceiver {
    
    private const val TAG = "PluginBroadcastReceiver"
    private const val ACTION_PLUGIN_BROADCAST = "com.UIN.Tool.PLUGIN_BROADCAST"
    
    private var registered = false
    private lateinit var context: Context
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == null || !action.startsWith("com.UIN.Tool.")) return
            
            val pluginId = intent.getStringExtra("plugin_id")
            val eventType = intent.getStringExtra("event_type")
            val data = intent.getSerializableExtra("data") as? Map<String, Any>
            
            if (pluginId != null) {
                // 发给指定插件
                PluginEventBus.sendToPlugin(pluginId, eventType ?: "broadcast", data)
            } else {
                // 广播给所有插件
                PluginEventBus.broadcastToPlugins(eventType ?: "broadcast", data)
            }
        }
    }
    
    fun init(context: Context) {
        if (registered) return
        this.context = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(ACTION_PLUGIN_BROADCAST)
            // 也可以添加系统广播
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        // 仅接收本应用广播，不向外部应用暴露（Android 13+ 需显式 NOT_EXPORTED）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
        Logger.d(TAG, Str.get(R.string.broadcast_receiver_registered))
    }
    
    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
            registered = false
            Logger.d(TAG, Str.get(R.string.broadcast_receiver_unregistered))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_unregister_broadcast_recei), e)
        }
    }
    
    /**
     * 发送广播给插件
     */
    fun sendBroadcast(pluginId: String? = null, eventType: String, data: Map<String, Any>? = null) {
        val intent = Intent(ACTION_PLUGIN_BROADCAST).apply {
            setPackage(context.packageName)
            pluginId?.let { putExtra("plugin_id", it) }
            putExtra("event_type", eventType)
            data?.let {
                putExtra("data", HashMap(it) as java.io.Serializable)
            }
        }
        context.sendBroadcast(intent)
    }
}