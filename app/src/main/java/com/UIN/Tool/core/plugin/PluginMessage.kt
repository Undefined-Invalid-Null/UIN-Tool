package com.UIN.Tool.core.plugin

import org.json.JSONObject

/**
 * 插件消息数据类
 * 用于插件间通信的消息载体
 */
data class PluginMessage(
    val type: String,           // 消息类型，如 "data", "command", "event"
    val action: String,         // 动作名称
    val sender: String,         // 发送者插件ID
    val target: String? = null, // 目标插件ID（null表示广播）
    val data: Map<String, Any>? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", type)
        obj.put("action", action)
        obj.put("sender", sender)
        obj.put("target", target ?: "")
        obj.put("timestamp", timestamp)
        data?.let {
            val dataObj = JSONObject()
            it.forEach { (key, value) ->
                dataObj.put(key, value)
            }
            obj.put("data", dataObj)
        }
        return obj.toString()
    }
    
    companion object {
        fun fromJson(json: String): PluginMessage? {
            return try {
                val obj = JSONObject(json)
                val dataObj = obj.optJSONObject("data")
                val data = dataObj?.let {
                    val map = mutableMapOf<String, Any>()
                    it.keys().forEach { key ->
                        map[key] = it.get(key)
                    }
                    map
                }
                PluginMessage(
                    type = obj.getString("type"),
                    action = obj.getString("action"),
                    sender = obj.getString("sender"),
                    target = obj.optString("target", null),
                    data = data,
                    timestamp = obj.getLong("timestamp")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}