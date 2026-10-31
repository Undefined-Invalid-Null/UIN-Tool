package com.UIN.Tool.data.remote

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * 6.2.0 批次66：**Google 翻译（gtx 免费匿名接口）** —— 新增的在线翻译方式之一。
 *
 * 与必应路线完全独立（用户要求：新接口写新类，不复用 BingTranslate），只共用结果页 UI。
 * 端点 `translate.googleapis.com/translate_a/single?client=gtx` 是 google-translate-api ★3159、
 * py-googletrans ★4291 等开源实现通用的网页免费通道，无需 key / 签名：
 *  - `sl=auto` 自动检测源语言，`tl` 目标语言，`dt=t` 只要正文译文；
 *  - 响应是嵌套数组 `[[["译文","原文",...], ...], null, "zh-CN", ...]`：
 *    根 `[0]` 为句子片段数组，每片 `[0]` 即该片译文，按顺序拼接。
 *
 * 【为什么单独建客户端】2026-10-31 本机实测：同一 URL、同一 UA，
 *  HTTP/2 请求被风控返回 "Sorry..." 错误页，`--http1.1` 稳定返回 200 JSON。
 *  因此这里强制 `Protocol.HTTP_1_1`（共享 OkHttp 客户端默认 h2，见 D-29 注释的取舍）。
 *  国内网络访问该域名可能不通，失败会在结果页显示 HTTP 错误信息，可改用其他通道。
 */
object GoogleTranslate {

    private const val TAG = "GoogleTranslate"

    private const val ENDPOINT =
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** gtx 通道接受的 BCP-47 目标语言代码（简繁用 zh-CN / zh-TW）。 */
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

    /** 强制 HTTP/1.1 的独立客户端（理由见类注释的实测说明）。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 把 [text] 翻译成语言 [to]（如 `zh-CN` / `en`）。
     * 成功返回译文；失败抛异常，`message` 会拼进结果页的「翻译失败：%1$s」。
     */
    suspend fun translate(text: String, to: String): String = withContext(Dispatchers.IO) {
        // HttpUrl 自动做 URL 编码，中文/换行/百分号都安全
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("tl", to)
            .addQueryParameter("q", text)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json,text/plain,*/*")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code}")
            }
            // 风控/超限时 Google 会返回 HTML 的 "Sorry..." 页而不是 JSON
            if (body.contains("Sorry", ignoreCase = true) && !body.startsWith("[")) {
                throw Exception(Str.get(R.string.editor_translate_rate_limited))
            }
            parse(body)
        }
    }

    /** 解析 gtx 嵌套数组：根 [0] 是分句片段，每片 [0] 为该片段译文。 */
    private fun parse(json: String): String {
        val root = JSONArray(json)
        val segments = root.optJSONArray(0)
            ?: throw Exception(Str.get(R.string.editor_translate_failed, "empty segments"))
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            val piece = segments.optJSONArray(i)?.optString(0)
            if (!piece.isNullOrEmpty()) sb.append(piece)
        }
        if (sb.isEmpty()) {
            throw Exception(Str.get(R.string.editor_translate_failed, "empty translation"))
        }
        return sb.toString()
    }
}
