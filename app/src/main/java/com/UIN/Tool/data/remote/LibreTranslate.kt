package com.UIN.Tool.data.remote

import com.UIN.Tool.core.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 6.2.0 批次66追加：**自定义翻译接口（LibreTranslate 兼容）**。
 *
 * 「翻译接口管理」里用户可添加任意 LibreTranslate 兼容实例（自建或自己能访问的公网
 * 实例；内置三个通道是写死的免费匿名接口，这里补上可扩展的一条）。协议按 LibreTranslate
 * 官方规范：
 * ```
 * POST <baseUrl>/translate
 * {"q": …, "source": "auto", "target": "zh", "format": "text", "apiKey"?: …}
 * → {"translatedText": "…"}   失败时 {"error": "…"}
 * ```
 * 具体能否连通取决于用户填的地址，管理页提供「测试」按钮就地验证。
 */
object LibreTranslate {

    private const val TAG = "LibreTranslate"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * 目标语言 chip（与内置通道同款式）。[codeOf] 把 chip 代码翻成 LibreTranslate 的
     * 语言码：简体 `zh-CN` → `zh`；繁体 `zh-TW` 原样传（实例不支持时会回 error，前端可见）。
     */
    val TARGET_LANGS: List<Pair<String, String>> = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "ru" to "Русский",
        "pt" to "Português",
        "it" to "Italiano",
        "ar" to "العربية",
        "vi" to "Tiếng Việt",
        "th" to "ไทย",
        "id" to "Bahasa Indonesia",
        "tr" to "Türkçe",
        "hi" to "हिन्दी",
        "nl" to "Nederlands"
    )

    private fun codeOf(chip: String): String = if (chip == "zh-CN") "zh" else chip

    /** 把 [text] 翻译成语言 [to]；失败抛异常，message 直接给结果页展示。 */
    suspend fun translate(
        baseUrl: String,
        apiKey: String?,
        text: String,
        to: String
    ): String = call(baseUrl, apiKey, text, codeOf(to))

    /** 连通性测试：翻一个词回简体中文，成功返回译文。 */
    suspend fun test(baseUrl: String, apiKey: String?): String =
        call(baseUrl, apiKey, "hello", "zh")

    private suspend fun call(
        baseUrl: String,
        apiKey: String?,
        text: String,
        target: String
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("q", text)
            .put("source", "auto")
            .put("target", target)
            .put("format", "text")
        if (!apiKey.isNullOrEmpty()) body.put("apiKey", apiKey)

        val url = baseUrl.trim().trimEnd('/') + "/translate"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val respBody = ServiceLocator.getOkHttpClient().newCall(req).execute().use { resp ->
            val b = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            b
        }
        val json = JSONObject(respBody)
        // LibreTranslate 的错误体：{"error": "..."}（4xx 时也常是它）
        val err = json.optString("error")
        if (err.isNotEmpty()) throw Exception(err)
        json.optString("translatedText").takeIf { it.isNotEmpty() }
            ?: throw Exception("empty translation")
    }
}
