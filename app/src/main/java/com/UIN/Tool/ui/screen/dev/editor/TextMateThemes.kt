package com.UIN.Tool.ui.screen.dev.editor

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Collections

/**
 * 6.2.0 批次66：TextMate 主题清单的**动态检测**（用户要求「不要硬编码，可以动态检测」）。
 *
 * 主题选择器原先在 CodeEditorScreen 里维护了 THEMES / DARK_THEMES / LIGHT_THEMES 三份硬编码
 * 名单，UinApplication 里还另有一份 isDarkTheme 名单 —— assets/textmate/themes 下漏登记的主题
 * （例如 nord.json）选择器根本列不出来，深浅色也判定错。这里统一改成从 assets 现读：
 *
 *  1. 主题名单 = 枚举 `assets/textmate/themes` 下的 .json 文件（与 loadThemes 的加载口径一致：
 *     选择器列出来的就是真正加载了的）**并合并用户导入的私有目录**
 *     （`filesDir/textmate/themes`，见批次66追加的「语法/主题文件管理」）；
 *  2. 深浅色 = 读主题文件自身的 `"type": "dark" | "light"`（VS Code 官方主题的通行写法）；
 *     没有 type 的（hc_light / hc_black）退回 `colors.editor.background` 亮度判定；
 *     两者都没有才按文件名兜底（含 "light" → 浅色）；
 *  3. 同名时**用户导入的优先**（私有目录文件先读），导入/删除后调用 [invalidateCache] 刷新。
 *
 * 主题文件随 APK 打包后不再变化，故名单与深浅色结果进程内各缓存一次。
 */
object TextMateThemes {

    private const val THEME_DIR = "textmate/themes"

    /** 用户导入的私有主题目录（`filesDir/textmate/themes`）。 */
    fun userThemesDir(context: Context): File = File(context.filesDir, THEME_DIR)

    @Volatile
    private var namesCache: List<String>? = null

    /** 主题名 → 是否深色；读一次文件后常驻。 */
    private val darkCache: MutableMap<String, Boolean> =
        Collections.synchronizedMap(HashMap())

    /** 批次66追加：主题导入/删除后清空缓存，让选择器与深浅色立即反映最新状态。 */
    fun invalidateCache() {
        namesCache = null
        darkCache.clear()
    }

    /** 全部主题名（内置 + 用户导入，文件名去 .json），按名称排序；读取失败返回空列表。 */
    fun names(context: Context): List<String> {
        namesCache?.let { return it }
        val fromAssets = (context.assets.list(THEME_DIR) ?: emptyArray())
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
        val fromUser = userThemesDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?: emptyList()
        val list = (fromAssets + fromUser).distinct().sorted()
        namesCache = list
        return list
    }

    /**
     * [name] 是否深色主题（带缓存）。同名时**优先读用户导入的那份**。
     * 文件读不到时按文件名兜底 —— 与旧硬编码名单一致：未知主题不会让选择器崩，深浅判定
     * 只影响配色圆点与默认兜底主题的选择。
     */
    fun isDark(context: Context, name: String): Boolean {
        darkCache[name]?.let { return it }
        val userFile = File(userThemesDir(context), "$name.json")
        val raw = if (userFile.isFile) {
            runCatching { userFile.readText(Charsets.UTF_8) }.getOrNull()
        } else {
            runCatching {
                context.assets.open("$THEME_DIR/$name.json")
                    .use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
        }
        val dark = if (raw != null) detect(sanitizeJsonc(raw), name)
        else !name.contains("light", ignoreCase = true)
        darkCache[name] = dark
        return dark
    }

    /**
     * 从**已清洗为严格 JSON** 的主题文本判定深浅色。
     *
     * 供 UinApplication.loadThemes 复用：它本来就要 sanitize 出同一份 cleaned 文本给 sora 解析，
     * 这里直接拿现成的文本判定，避免重复读文件。
     */
    fun detect(cleanedJson: String, name: String): Boolean {
        val json = runCatching { JSONObject(cleanedJson) }.getOrNull()
        if (json != null) {
            // 1) 官方主题都带 "type": "dark" | "light"
            when (json.optString("type").lowercase()) {
                "dark" -> return true
                "light" -> return false
            }
            // 2) 缺 type 的（hc_black 等）按编辑器背景亮度判定
            val bg = json.optJSONObject("colors")?.optString("editor.background")
            if (!bg.isNullOrEmpty()) {
                isDarkColorOrNull(bg)?.let { return it }
            }
        }
        // 3) 都没有（hc_light：无 type、无 editor.background）→ 文件名兜底
        return !name.contains("light", ignoreCase = true)
    }

    /**
     * 把 JSONC（允许行注释、块注释、尾逗号）清洗成严格 JSON，供 org.json / Gson 解析。
     * 注释剥离用逐字符扫描以避开字符串字面量里的斜杠斜杠（如 https 里的双斜杠）；
     * 尾逗号用正则移除（主题文件里不会出现形如「逗号+空格+右花括号」的字符串内容）。
     * （原先在 UinApplication 里是私有实现，批次66 移到这里与主题检测共用一份。）
     */
    fun sanitizeJsonc(raw: String): String {
        val noComments = StringBuilder(raw.length)
        var i = 0
        var inString = false
        var escape = false
        while (i < raw.length) {
            val c = raw[i]
            if (inString) {
                noComments.append(c)
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when {
                c == '"' -> { inString = true; noComments.append(c); i++ }
                c == '/' && i + 1 < raw.length && raw[i + 1] == '/' -> {
                    while (i < raw.length && raw[i] != '\n') i++
                }
                c == '/' && i + 1 < raw.length && raw[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < raw.length && !(raw[i] == '*' && raw[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(raw.length)
                }
                else -> { noComments.append(c); i++ }
            }
        }
        return Regex(",\\s*([}\\]])").replace(noComments.toString(), "$1")
    }

    /**
     * `#RGB` / `#RRGGBB` / `#RRGGBBAA` → 是否深色（Y = 0.299R + 0.587G + 0.114B，Y < 128 深色）。
     * 解析不了返回 null，交由调用方走下一级兜底。
     */
    private fun isDarkColorOrNull(hex: String): Boolean? {
        val h = hex.removePrefix("#")
        val full = when (h.length) {
            3 -> h.map { "${it}${it}" }.joinToString("")
            6 -> h
            8 -> h.substring(0, 6) // 带 alpha 的取 RGB 部分
            else -> return null
        }
        val v = full.toLongOrNull(16) ?: return null
        val r = (v shr 16) and 0xFF
        val g = (v shr 8) and 0xFF
        val b = v and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) < 128
    }
}
