package com.UIN.Tool.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 6.2.0 批次66追加：**翻译接口管理**的持久化（用户要求「启停内置 + 自定义接口」）。
 *
 *  - 内置通道（必应 / Google / MyMemory）各有一个开关，关闭后**不再出现在翻译方式列表**；
 *  - 自定义通道是任意 **LibreTranslate 兼容**实例（自建或自己能访问的公网实例），
 *    记录 {名称, 地址, 可选密钥}，保存在独立 SharedPreferences 里。
 *
 * 与翻译实现（BingTranslate / GoogleTranslate / MyMemoryTranslate / LibreTranslate）解耦：
 * 这里只管「有哪些通道、各自开不开」，取译文的函数由 UI 层按本表装配。
 */
object TranslateApiStore {

    /** 内置通道键（写入 prefs 的开关名）。 */
    const val KEY_BING = "bing"
    const val KEY_GOOGLE = "google"
    const val KEY_MYMEMORY = "mymemory"

    private const val PREFS = "translate_api_prefs"
    private const val KEY_CUSTOMS = "customs"

    /** 一条自定义 LibreTranslate 兼容接口。[apiKey] 可为空（多数公开实例不要求）。 */
    data class CustomApi(
        val id: String,
        val name: String,
        val url: String,
        val apiKey: String
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** [key] 对应的内置通道是否启用（默认启用）。 */
    fun isEnabled(context: Context, key: String): Boolean =
        prefs(context).getBoolean(key, true)

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(key, enabled).apply()
    }

    fun customs(context: Context): List<CustomApi> {
        val raw = prefs(context).getString(KEY_CUSTOMS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (o.optString("name").isEmpty() || url.isEmpty()) return@mapNotNull null
                CustomApi(
                    id = o.optString("id").ifEmpty { url },
                    name = o.optString("name"),
                    url = url,
                    apiKey = o.optString("apiKey")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun addCustom(context: Context, name: String, url: String, apiKey: String): CustomApi {
        val api = CustomApi(
            id = UUID.randomUUID().toString().take(8),
            name = name.trim(),
            url = url.trim().trimEnd('/'),
            apiKey = apiKey.trim()
        )
        save(context, customs(context) + api)
        return api
    }

    fun removeCustom(context: Context, id: String) {
        save(context, customs(context).filterNot { it.id == id })
    }

    private fun save(context: Context, list: List<CustomApi>) {
        val arr = JSONArray()
        list.forEach { api ->
            arr.put(
                JSONObject()
                    .put("id", api.id)
                    .put("name", api.name)
                    .put("url", api.url)
                    .put("apiKey", api.apiKey)
            )
        }
        prefs(context).edit().putString(KEY_CUSTOMS, arr.toString()).apply()
    }
}
