package com.UIN.Tool.domain.model

import org.json.JSONObject

/**
 * 插件信息数据模型
 * 包含插件的完整元数据
 * 
 * 字段说明：
 * - pluginId: 插件唯一标识
 * - version: 版本代码 (整数)
 * - versionName: 版本名称 (如 "1.0.0")
 * - minHostVersion: 最低宿主版本
 * - name: 插件显示名称
 * - author: 作者
 * - description: 描述
 * - icon: 图标文件名
 * - mainClass: 原生插件的主类名
 * - updateUrl: 更新检查URL
 * - apiLevel: 最低API级别
 * - category: 分类
 * - signature: 签名 (自动生成)
 * - uiType: "native" 或 "web"
 * - entry: Web插件的入口文件
 * - permissions: 需要的权限列表
 * - dependencies: 依赖的插件ID列表
 */
data class PluginInfo(
    var pluginId: String = "",
    var version: Int = 1,
    var versionName: String = "1.0.0",
    var minHostVersion: Int = 1,
    var name: String = "",
    var author: String = "",
    var description: String = "",
    var icon: String = "icon.png",
    var mainClass: String = "",
    var updateUrl: String = "",
    var apiLevel: Int = 21,
    var category: String = "未分类",
    var signature: String = "",
    var uiType: String = "native",
    var entry: String = "web/index.html",
    var permissions: List<String> = emptyList(),
    var dependencies: List<String> = emptyList()
) {

    /**
     * 转换为JSON字符串
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("pluginId", pluginId)
            put("version", version)
            put("versionName", versionName)
            put("minHostVersion", minHostVersion)
            put("name", name)
            put("author", author)
            put("description", description)
            put("icon", icon)
            put("mainClass", mainClass)
            put("updateUrl", updateUrl)
            put("apiLevel", apiLevel)
            put("category", category)
            put("signature", signature)
            put("uiType", uiType)
            put("entry", entry)
            put("permissions", permissions.joinToString(","))
            put("dependencies", dependencies.joinToString(","))
        }.toString()
    }

    /**
     * 检查插件是否兼容当前Android版本
     */
    fun isCompatible(): Boolean {
        return minHostVersion <= android.os.Build.VERSION.SDK_INT
    }

    /**
     * 检查是否是Web插件
     */
    fun isWebPlugin(): Boolean = uiType == "web"

    /**
     * 检查是否是原生插件
     */
    fun isNativePlugin(): Boolean = uiType == "native"

    /**
     * 检查是否缺少依赖
     */
    fun getMissingDependencies(installedIds: Set<String>): List<String> {
        return dependencies.filter { !installedIds.contains(it) }
    }

    companion object {
        /**
         * 从JSON字符串解析
         */
        fun fromJson(json: String): PluginInfo? {
            return try {
                val obj = JSONObject(json)
                PluginInfo(
                    pluginId = obj.optString("pluginId", ""),
                    version = obj.optInt("version", 1),
                    versionName = obj.optString("versionName", "1.0.0"),
                    minHostVersion = obj.optInt("minHostVersion", 1),
                    name = obj.optString("name", ""),
                    author = obj.optString("author", ""),
                    description = obj.optString("description", ""),
                    icon = obj.optString("icon", "icon.png"),
                    mainClass = obj.optString("mainClass", ""),
                    updateUrl = obj.optString("updateUrl", ""),
                    apiLevel = obj.optInt("apiLevel", 21),
                    category = obj.optString("category", "未分类"),
                    signature = obj.optString("signature", ""),
                    uiType = obj.optString("uiType", "native"),
                    entry = obj.optString("entry", "web/index.html"),
                    permissions = obj.optString("permissions", "").split(",").filter { it.isNotEmpty() },
                    dependencies = obj.optString("dependencies", "").split(",").filter { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 创建默认插件信息
         */
        fun createDefault(
            pluginId: String,
            name: String,
            author: String = "开发者",
            description: String = "",
            uiType: String = "native"
        ): PluginInfo {
            return PluginInfo(
                pluginId = pluginId,
                name = name,
                author = author,
                description = description,
                uiType = uiType,
                version = 1,
                versionName = "1.0.0"
            )
        }
    }
}