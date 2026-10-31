package com.UIN.Tool.data.remote

import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/**
 * 6.2.0 批次66：**MyMemory 翻译（免费匿名接口）** —— 新增的在线翻译方式之一。
 *
 * 与必应路线完全独立（用户要求：新接口写新类，不复用 BingTranslate），只共用结果页 UI。
 * 端点 `api.mymemory.translated.net/get` 是 MyMemory 官方公开的免费通道，无需 key / 签名：
 *  - `q` 待译文本，`langpair=<源>|<目标>`；源语言可写 `autodetect` 让它自己判；
 *  - 译文来自联合国/欧盟翻译记忆库（TM）与它自带的神经机器翻译，质量好、短句尤其准；
 *  - 匿名额度为**每日 5000 字符**，用尽时 `quotaFinished=true`，结果页会明确提示；
 *  - 返回的是 `responseData.translatedText`，错误详情在 `responseDetails`。
 *
 * 【批次66追加的三处实测修正】（2026-10-31 本机逐条实测）
 *  1. **英 → 中回显原文**：`langpair=autodetect|zh-CN` 拿英文原文会原样回吐
 *     （`translatedText` == 传入文本，`match:0.85`），改用显式 `en|zh-CN` 立刻出中文。
 *     故这里先按 `autodetect` 发一次，**若回显**就用答复里自带的 `detectedLanguage`
 *     重发一次显式语向（中文 → 英文、法 → 中等其它语向实测本就正常，不额外发请求）。
 *     同理：检测出的源语言就是目标语言时直接回原文，不再空跑。
 *  2. **单次 500 字符上限**：超限不但报错，`responseStatus` 还是**字符串** `"403"`，
 *     原先 `optInt` 会把它吞成 200，于是错误文本被当成译文返回 —— 现在按数字/字符串
 *     两种形态都解析，并按行打包把长文本切成 ≤450 字的片段逐片翻译（片段间的换行还原）。
 *  3. `th` 无 TM 内容会原样回显，故不列入目标语言表。
 */
object MyMemoryTranslate {

    private const val TAG = "MyMemoryTranslate"

    private const val ENDPOINT = "https://api.mymemory.translated.net/get"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 单次请求的字符上限（实测 500，留 50 余量 —— 超限会被整条拒绝）。 */
    private const val CHUNK_LIMIT = 450

    /** 语言代码形态：`en` / `zh-CN` 这类；用来挡住答复里的 `false`、空串等脏值。 */
    private val LANG_CODE = Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?")

    /** 一次往返的答复：译文 + 答复顺带给出的源语言（只有 `autodetect` 语向才有）。 */
    private data class Reply(val text: String, val detected: String?)

    /** MyMemory 的 langpair 目标语言代码（ISO-639-1 + 地区，简繁用 zh-CN / zh-TW）。 */
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
        "id" to "Bahasa Indonesia",
        "tr" to "Türkçe",
        "hi" to "हिन्दी",
        "nl" to "Nederlands"
    )

    /**
     * 把 [text] 翻译成语言 [to]（如 `zh-CN` / `en`）。
     * 成功返回译文；失败抛异常，`message` 会拼进结果页的「翻译失败：%1$s」。
     */
    suspend fun translate(text: String, to: String): String = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext text
        val chunks = splitChunks(text)
        val out = StringBuilder()
        var translated = 0
        var echoed = 0
        // 第一块探到的源语言：同一段文本通常同语种，缓存下来后续块直接用显式语向，
        // 免得每块都先走一次 autodetect（英 → 中必回显，那样每块都要两次请求）。
        var detected: String? = null
        chunks.forEach { (chunk, sep) ->
            val useDetect = detected == null
            val pair = if (useDetect) "autodetect|$to" else "$detected|$to"
            val reply = request(chunk, pair)
            if (reply.detected != null) detected = reply.detected
            var body = reply.text
            if (body != chunk) {
                translated++
            } else if (useDetect) {
                val d = reply.detected
                when {
                    // 检测出来源语言就是目标语言 —— 回显即正确结果，整篇不用再翻。
                    d != null && d.equals(to, ignoreCase = true) -> return@withContext text
                    // autodetect 在「英 → 中」语向上会原样回吐，拿它给的语种重发显式语向。
                    d != null -> {
                        body = request(chunk, "$d|$to").text
                        if (body != chunk) translated++ else echoed++
                    }
                    // 既没译出来、也没给检测值 —— 记为回显，最后一并判定。
                    else -> echoed++
                }
            } else {
                // 显式语向仍回显：TM/MT 里没有这句的译文，再试也一样。
                echoed++
            }
            out.append(body)
            // 片段间的衔接照抄原文（整行边界是换行、长行内部是空串），结构逐字还原。
            out.append(sep)
        }
        // 一片都没译出来（且不是同语种空跑）就别把原文当译文还回去 —— 用户会以为翻好了。
        if (translated == 0 && echoed > 0) {
            throw Exception(Str.get(R.string.editor_translate_no_match))
        }
        out.toString()
    }

    /**
     * 发一次请求并解析。失败抛异常（额度用尽 / 语向非法 / 超限 / 空译文）。
     */
    private suspend fun request(q: String, langpair: String): Reply {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("q", q)                     // HttpUrl 自动 URL 编码
            .addQueryParameter("langpair", langpair)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .get()
            .build()
        // 复用 ServiceLocator 的共享客户端（D-29：不另建网络栈）
        val client = ServiceLocator.getOkHttpClient()
        val body = client.newCall(req).execute().use { resp ->
            val b = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            b
        }
        val root = JSONObject(body)
        if (root.optBoolean("quotaFinished", false)) {
            // 匿名额度按日重置（每日 5000 字符）
            throw Exception(Str.get(R.string.editor_translate_quota))
        }
        // responseStatus 实测两种形态都出现过：正常时是数字 200，超限 / 语向非法时是
        // 字符串 "403" —— 只用 optInt 会把字符串吞成默认 200，错误文本于是冒充译文。
        val status = when (val raw = root.opt("responseStatus")) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull() ?: 200
            else -> 200
        }
        val detail = root.optString("responseDetails").trim()
        if (status != 200) {
            throw Exception(if (detail.isNotEmpty()) detail else "HTTP $status")
        }
        val data = root.optJSONObject("responseData")
        val translated = data?.optString("translatedText").orEmpty()
        if (translated.isEmpty()) {
            throw Exception(
                if (detail.isNotEmpty()) detail
                else Str.get(R.string.editor_translate_failed, "empty translation")
            )
        }
        val detected = data?.optString("detectedLanguage")?.takeIf { LANG_CODE.matches(it) }
        return Reply(translated, detected)
    }

    /**
     * 按行打包成 ≤ [CHUNK_LIMIT] 字符的片段：整行塞得下就整行进片，塞不下的长行再按
     * 空白切开。相邻片段之间用什么衔接记在返回的 Pair.second 里（整行边界是 `\n`，
     * 长行内部是空串），调用方照抄即可**逐字还原原文结构**。
     */
    private fun splitChunks(text: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val lines = text.split('\n')
        lines.forEachIndexed { li, line ->
            val lineSep = if (li == lines.lastIndex) "" else "\n"
            val pieces = splitLong(line)
            pieces.forEachIndexed { pi, piece ->
                val sep = if (pi == pieces.lastIndex) lineSep else ""
                val last = out.lastOrNull()
                // 与上一片合得下就合并，省一次请求
                if (last != null && last.first.length + last.second.length + piece.length <= CHUNK_LIMIT) {
                    out[out.lastIndex] = (last.first + last.second + piece) to sep
                } else {
                    out.add(piece to sep)
                }
            }
        }
        return out
    }

    /** 把单行超限的长行切成 ≤ [CHUNK_LIMIT] 的小段（优先在空白处断，其次硬切）。 */
    private fun splitLong(line: String): List<String> {
        if (line.length <= CHUNK_LIMIT) return listOf(line)
        val out = mutableListOf<String>()
        var rest = line
        while (rest.length > CHUNK_LIMIT) {
            // 从限内往后找最后一个空格 —— 断在词边界上，译文不会被拦腰截断。
            var cut = rest.lastIndexOf(' ', CHUNK_LIMIT - 1)
            if (cut <= CHUNK_LIMIT / 2) cut = CHUNK_LIMIT
            else cut += 1 // 空格留在本段末尾，下一段不带前导空格
            out.add(rest.substring(0, cut))
            rest = rest.substring(cut)
        }
        out.add(rest)
        return out
    }
}
