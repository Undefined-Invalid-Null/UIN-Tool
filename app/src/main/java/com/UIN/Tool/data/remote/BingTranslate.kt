// app/src/main/java/com/UIN/Tool/data/remote/BingTranslate.kt
package com.UIN.Tool.data.remote

import androidx.annotation.StringRes
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 批次63（用户指定库）：**必应翻译** —— 把
 * [bing-translate-api](https://github.com/plainheart/bing-translate-api) 的流程原样移植成
 * Kotlin（那是 Node 库，Android 里跑不了 JS，因此按它的 `src/index.js` 逐段翻译）：
 *
 *  1. GET `bing.com/translator` 抓全局凭据 —— 页面里的 `IG:"…"`、`data-iid="…"` 与
 *     `params_AbusePreventionHelper = [key, token, 过期毫秒]`（防滥用 token，同库逻辑）；
 *  2. POST `{host}/ttranslatev3?isVertical=1&IG=…&IID=…`（表单：fromLang/text/token/key/to），
 *     返回 JSON 数组 `[{translations:[{text,to}], detectedLanguage:{language,score}}]`；
 *  3. **主机要选对**（真机报 `unexpected response` 的根因，实测补的一步）——两条网络事实：
 *     ① 国内网络下 `www.bing.com` 整站 302 到 `cn.bing.com`，而 OkHttp 跟 302 时会把 POST
 *        降级成 GET、拿到的只是一段空 HTML（实测：POST www → 302 → GET cn → 空体）；
 *     ② 有些网络（不分地域）`www` / 裸域名的 POST 干脆回 200 + 空体，同一份凭据打
 *        `cn.bing.com` 却正常返回 JSON（实测对照）。
 *     库里靠 `replaceSubdomain(重定向后的主机)` 解决第 ① 条，这里两条都兜住：**凭据页
 *     最终 URL 的主机**起步；POST 完若被 302 换了主机就改写 [Cfg.host] 重发，若没换主机
 *     响应却不是 JSON，就换到另一台主机（`www ↔ cn`）再发一发；
 *  4. 凭据过期或被限流 → 重新抓凭据重试一次（同库的 token 刷新语义）。
 *
 * EPT（库 v3.0 起的加速端点参数 `SFX/ref/edgepdftranslator`）也按库的判定：两端语言都在
 * `eptLangs` 白名单且长度 ≤3000 才启用；否则老老实实走基础端点、长度上限 1000。
 *
 * 一切网络都在 IO 线程（`Dispatchers.IO`），失败抛 [TranslateException]，UI 直接按
 * [TranslateException.res] 出本地化文案。
 */
object BingTranslate {

    private const val TAG = "BingTranslate"

    /** 翻译结果。[from] 为必应检测出的源语言代码（如 `zh-Hans`），[to] 为目标语言。 */
    data class Result(val text: String, val from: String, val to: String, val translation: String)

    /** 带本地化文案的失败：UI 侧 `Str.get(res, *args)` 即为用户可读消息。 */
    class TranslateException(@StringRes val res: Int, val args: Array<out Any?>) :
        Exception(Str.get(res, *args))

    private fun fail(@StringRes res: Int, vararg args: Any?): Nothing =
        throw TranslateException(res, args)

    /** 入口页固定从 www 起步，最终主机以 302 之后的 URL 为准（见 [fetchCfg]）。 */
    private const val WEBSITE = "https://www.bing.com/translator"

    /** 翻译端点路径：主机部分按 [Cfg.host] 拼接，不能写死（国内会换到 cn）。 */
    private const val API_PATH = "/ttranslatev3?isVertical=1"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 非 EPT 模式的文本长度上限（同库 `config.maxTextLen`）。 */
    private const val MAX_LEN = 1000

    /** cn 主机放宽后的上限（同库 `config.maxTextLenCN`，库对 cn 主机用 5000）。 */
    private const val MAX_LEN_CN = 5000

    /** EPT 模式与绝对长度上限（同库 `config.maxEPTTextLen`）。 */
    private const val MAX_EPT_LEN = 3000

    /** 目标语言：`code to 母语展示名`（展示名不随界面语言变，翻译场景母语名更好认）。 */
    val TARGET_LANGS: List<Pair<String, String>> = listOf(
        "zh-Hans" to "简体中文",
        "zh-Hant" to "繁體中文",
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

    /**
     * EPT 白名单（照抄库 `config.eptLangs`）：只有源/目标语言都在表内（或源为
     * `auto-detect`）时才带 `SFX/ref/edgepdftranslator` 参数走加速端点。
     */
    private val EPT_LANGS = setOf(
        "af", "sq", "am", "ar", "hy", "az", "bn", "bs", "bg", "ca", "zh-Hans", "zh-Hant",
        "hr", "cs", "da", "prs", "nl", "en", "et", "fil", "fi", "fr", "de", "el", "gu",
        "ht", "he", "hi", "hu", "is", "id", "iu", "ga", "it", "ja", "kn", "kk", "km",
        "ko", "ku", "lo", "lv", "lt", "mg", "ms", "ml", "mt", "mr", "my", "mi", "ne",
        "nb", "or", "ps", "fa", "pl", "pt", "pt-PT", "pa", "ro", "ru", "sm", "sr-Cyrl",
        "sr-Latn", "sk", "sl", "es", "sw", "sv", "ta", "te", "th", "to", "tr", "uk",
        "ur", "vi", "cy"
    )

    /**
     * 全局凭据（同库 GlobalConfig）。[count] 是 EPT 的 `SFX` 递增序号、[host] 是 302 之后
     * 的实际主机（`cn.bing.com` / `www.bing.com`…），两者都可能在会话中变化，故为 `var`。
     */
    private data class Cfg(
        var host: String,
        val ig: String,
        val iid: String,
        val key: Long,
        val token: String,
        val ts: Long,
        val expiry: Long,
        var count: Int
    )

    @Volatile
    private var cfg: Cfg? = null

    /**
     * 翻译一段文本（源语言自动检测）。
     *
     * @param text 待译文本（会 trim）
     * @param to   目标语言代码，见 [TARGET_LANGS]
     */
    suspend fun translate(text: String, to: String): Result =
        withContext(Dispatchers.IO) { doTranslate(text, to) }

    private fun doTranslate(text: String, to: String): Result {
        val t = text.trim()
        if (t.isEmpty()) fail(R.string.editor_select_action_no_selection)

        var last: TranslateException? = null
        repeat(2) { attempt ->
            try {
                val c = cfg?.takeIf { System.currentTimeMillis() - it.ts <= it.expiry }
                    ?: fetchCfg().also { cfg = it }
                // 同库 canUseEPT：长度 ≤3000 且源/目标语言都在白名单。源语言恒为 auto-detect，
                // 库对 auto-detect 一概放行 —— 所以这里只看目标语言是否在 EPT 表内。
                // 上限判定放在拿到凭据之后：长度取决于主机（cn 主机同库放宽到 5000）。
                val useEpt = to in EPT_LANGS
                if (t.length > MAX_EPT_LEN) fail(R.string.editor_translate_too_long, MAX_EPT_LEN)
                if (!useEpt) {
                    val max = if (c.host.startsWith("cn.")) MAX_LEN_CN else MAX_LEN
                    if (t.length > max) fail(R.string.editor_translate_too_long, max)
                }
                return postTranslate(c, t, to, useEpt)
            } catch (e: TranslateException) {
                // 凭据过期 / 被限流这类「换一套凭据可能就好了」的失败才重试；
                // 其余（文本过长等）直接抛。
                if (e.res != R.string.editor_translate_limited && e.res != R.string.editor_translate_failed) throw e
                cfg = null
                last = e
                if (attempt == 0) {
                    com.UIN.Tool.utils.AppLog.d(TAG, "attempt 1 failed, refetch config and retry")
                }
            }
        }
        throw last ?: fail(R.string.editor_translate_failed, "unknown")
    }

    /**
     * 1) 抓全局凭据（IG / IID / 防滥用 token）。
     *
     * 顺带定下 [Cfg.host]：OkHttp 默认跟完 302，`resp.request.url` 就是**最终 URL** ——
     * 国内网络这里会是 `cn.bing.com`（库的 `redirectUrl.match(...)` 同款信息）。
     */
    private fun fetchCfg(): Cfg {
        val req = Request.Builder().url(WEBSITE).header("User-Agent", UA).build()
        val client = ServiceLocator.getOkHttpClient()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) fail(R.string.editor_translate_limited)
            val host = resp.request.url.host
            if (host != "bing.com" && !host.endsWith(".bing.com")) {
                fail(R.string.editor_translate_failed, "host $host")
            }
            val body = resp.body?.string() ?: fail(R.string.editor_translate_failed, "empty body")
            // 与库同一套正则：IG、data-iid、防滥用三元组 [key, token, 过期毫秒]。
            val ig = Regex("IG:\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: fail(R.string.editor_translate_failed, "IG")
            val iid = Regex("data-iid=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: fail(R.string.editor_translate_failed, "iid")
            val helper = Regex("params_AbusePreventionHelper\\s*=\\s*(\\[[^\\]]+])")
                .find(body)?.groupValues?.get(1)
                ?: fail(R.string.editor_translate_failed, "token")
            val arr = JSONArray(helper)
            val key = arr.optLong(0)
            val token = arr.optString(1)
            val expiry = arr.optLong(2)
            if (key == 0L || token.isEmpty() || expiry <= 0L) {
                fail(R.string.editor_translate_failed, "bad token")
            }
            com.UIN.Tool.utils.AppLog.d(TAG, "config host=$host ig=$ig")
            return Cfg(host, ig, iid, key, token, System.currentTimeMillis(), expiry, 0)
        }
    }

    /**
     * 2) POST 翻译请求。
     *
     * 最多三发、逐发纠偏（两种网络坑实测得到）：
     *  1. 被 302 换了主机（`www` → `cn`，POST 被降级成 GET、只回空 HTML）→ 改写 [Cfg.host]
     *     用新主机重发；
     *  2. 没换主机但响应不是 JSON（部分网络上 `www` 直接回 200 空体）→ 换另一台主机重发；
     *  3. 第三发才是终局：正常解析，解析不了就带主机/状态码的归因抛出。
     */
    private fun postTranslate(c: Cfg, text: String, to: String, useEpt: Boolean): Result {
        val client = ServiceLocator.getOkHttpClient()
        var attempt = 0
        while (attempt < 3) {
            attempt++
            val url = buildString {
                append("https://").append(c.host).append(API_PATH)
                append("&IG=").append(c.ig)
                append("&IID=").append(c.iid)
                if (useEpt) {
                    // EPT 端点参数（库 makeRequestURL）：SFX 是每请求递增序号。
                    append("&SFX=").append(++c.count)
                    append("&ref=TThis")
                    append("&edgepdftranslator=1")
                }
            }
            val form = FormBody.Builder()
                .add("fromLang", "auto-detect")
                .add("text", text)
                .add("token", c.token)
                .add("key", c.key.toString())
                .add("to", to)
                .add("tryFetchingGenderDebiasedTranslations", "true")
                .build()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", "https://${c.host}/translator")
                .post(form)
                .build()
            client.newCall(req).execute().use { resp ->
                // 纠偏①：收尾主机 ≠ 请求主机 = 被 302 换过（POST→GET 降级，body 是空 HTML）。
                val finalHost = resp.request.url.host
                if (finalHost != c.host) {
                    com.UIN.Tool.utils.AppLog.d(TAG, "redirected to $finalHost, resend there")
                    c.host = finalHost
                    return@use
                }
                val body = resp.body?.string() ?: fail(R.string.editor_translate_failed, "empty body")
                val contentType = resp.header("Content-Type") ?: ""
                when {
                    resp.code == 401 || resp.code == 429 ->
                        fail(R.string.editor_translate_limited)
                    !resp.isSuccessful -> fail(R.string.editor_translate_failed, resp.code)
                    contentType.contains("application/json") ->
                        return parseJsonResult(body, text, to)
                    // 性别平权译文：库在首包非 JSON 且带该响应头时补发一次请求取正文。
                    resp.header("isgenderdebiasedtranslation") != null ->
                        return parseGenderDebiased(c, url, text, to)
                    body.contains("ShowCaptcha") -> fail(R.string.editor_translate_limited)
                    // 纠偏②：没换主机却不是 JSON（典型 www 回 200 空体）→ 换台主机再发。
                    attempt < 3 -> {
                        val alt = if (c.host.startsWith("cn.")) "www.bing.com" else "cn.bing.com"
                        com.UIN.Tool.utils.AppLog.d(
                            TAG, "empty response on ${c.host} (HTTP ${resp.code} ${contentType.ifEmpty { "no-ct" }}), resend via $alt"
                        )
                        c.host = alt
                        return@use
                    }
                    else -> fail(
                        R.string.editor_translate_failed,
                        "HTTP ${resp.code} ${contentType.ifEmpty { "no-ct" }} host=${c.host}"
                    )
                }
            }
        }
        fail(R.string.editor_translate_failed, "no host worked")
    }

    /** 2a) 标准 JSON：`[{translations:[{text,to}], detectedLanguage:{language}}]`。 */
    private fun parseJsonResult(body: String, text: String, to: String): Result {
        val arr = JSONArray(body)
        if (arr.length() == 0) fail(R.string.editor_translate_failed, "empty result")
        val obj = arr.getJSONObject(0)
        if (obj.has("ShowCaptcha")) fail(R.string.editor_translate_limited)
        val tr = obj.optJSONArray("translations")?.optJSONObject(0)
            ?: fail(R.string.editor_translate_failed, "no translation")
        val detected = obj.optJSONObject("detectedLanguage")?.optString("language")
            ?: "auto-detect"
        return Result(text, detected, tr.optString("to", to), tr.optString("text"))
    }

    /** 2b) 性别平权译文分支：补 `isGenderDebiasViewPresent=true` 再取正文。 */
    private fun parseGenderDebiased(c: Cfg, url: String, text: String, to: String): Result {
        val form = FormBody.Builder()
            .add("fromLang", "auto-detect")
            .add("text", text)
            .add("token", c.token)
            .add("key", c.key.toString())
            .add("to", to)
            .add("tryFetchingGenderDebiasedTranslations", "true")
            .add("isGenderDebiasViewPresent", "true")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://${c.host}/translator")
            .post(form)
            .build()
        val client = ServiceLocator.getOkHttpClient()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: fail(R.string.editor_translate_failed, "empty body")
            if (!resp.isSuccessful) fail(R.string.editor_translate_failed, resp.code)
            val obj = JSONObject(body)
            // 库把 masculineTranslation 当作默认译文（res.translation = body.masculineTranslation）。
            val translation = obj.optString("masculineTranslation")
                .ifEmpty { obj.optString("translation") }
            if (translation.isEmpty()) fail(R.string.editor_translate_failed, "no translation")
            val from = resp.header("detectedlanguage") ?: "auto-detect"
            return Result(text, from, to, translation)
        }
    }
}
