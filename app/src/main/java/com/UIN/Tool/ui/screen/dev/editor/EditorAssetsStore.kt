package com.UIN.Tool.ui.screen.dev.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.UIN.Tool.UinApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 6.2.0 批次66追加：**语法 / 主题文件管理**的存储层（用户要求「可以新增或者删除」）。
 *
 * 内置文件在 APK 的 assets 里只读，用户导入的文件写进应用私有目录、可随时删除：
 * ```
 * filesDir/textmate/themes/<名称>.json              ← 私有主题
 * filesDir/textmate/<语法目录>/syntaxes/<原文件名>   ← 私有语法（与内置同构）
 * ```
 *
 * 关键约束（决定了这里的行为设计）：
 *  - **导入即热生效**：主题/语法当场注册进 sora 注册表，选得到、用得上；
 *  - **删除只删文件**：sora 的注册表没有热移除接口，已在内存里的实例等重启才消失
 *    （登记会先摘掉，重启后不再加载），删除确认里如实告知；
 *  - **查重**：主题按 stem 与 JSON `name` 双键查重（撞 JSON name 会让 sora 走
 *    「已存在 → setTheme(旧主题)」分支，既加不进来还会切走当前主题）；语法按
 *    scopeName 查重（重复 scope 会让注册表里的 scope→definition 映射被覆盖）。
 */
object EditorAssetsStore {

    private const val ROOT = "textmate"
    private const val THEMES_DIR = "$ROOT/themes"

    /** 导入失败原因；UI 层据此翻译成用户可读文案。 */
    enum class ImportError {
        /** 文件类型不支持（既不是主题也不是语法） */
        UNSUPPORTED,

        /** 读取失败 / 写入失败 */
        READ_FAILED,

        /** 内容非法（不是合法 JSON 主题、或语法解析不到 scopeName） */
        INVALID,

        /** scopeName 与现有语法重复 */
        SCOPE_DUP,

        /** 主题名与现有主题重复 */
        THEME_DUP
    }

    /** 一次批量导入的结果：已生效 / 已写入待重启 / 失败原因。 */
    data class ImportOutcome(
        val imported: List<String>,
        val deferred: List<String>,
        val failed: List<Pair<String, ImportError>>
    )

    /** 主题列表项。[builtIn] 为 true 表示随包内置（只读，不可删除）。 */
    data class ThemeEntry(
        val name: String,
        val builtIn: Boolean,
        val file: File?,
        /** 用户导入主题的 JSON `name` 字段（展示用；内置项为 null） */
        val rawName: String? = null
    )

    /** 语法列表项；[name] 为文件 stem（用户项）或原文件名（内置项）。 */
    data class GrammarEntry(
        val name: String,
        val builtIn: Boolean,
        val file: File?,
        val scope: String? = null
    )

    fun userRoot(context: Context): File = File(context.filesDir, ROOT)

    fun userThemesDir(context: Context): File = File(context.filesDir, THEMES_DIR)

    /** 全部主题（内置 + 用户），按名称排序。 */
    fun listThemes(context: Context): List<ThemeEntry> {
        val builtIn = (context.assets.list(THEMES_DIR) ?: emptyArray())
            .filter { it.endsWith(".json") }
            .map { ThemeEntry(it.removeSuffix(".json"), builtIn = true, file = null) }
        val user = userThemesDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map {
                ThemeEntry(
                    name = it.name.removeSuffix(".json"),
                    builtIn = false,
                    file = it,
                    rawName = runCatching {
                        JSONObject(TextMateThemes.sanitizeJsonc(it.readText(Charsets.UTF_8)))
                            .optString("name")
                    }.getOrDefault("")
                )
            } ?: emptyList()
        return (builtIn + user).sortedBy { it.name }
    }

    /** 全部语法（内置 + 用户）；内置项 [name] 用原文件名，用户项 [name] 用 stem + scope。 */
    fun listGrammars(context: Context): List<GrammarEntry> {
        val builtIn = ArrayList<GrammarEntry>()
        for (dir in context.assets.list(ROOT) ?: emptyArray()) {
            if (dir == "themes" || dir.contains('.')) continue
            val files = context.assets.list("$ROOT/$dir/syntaxes") ?: continue
            for (f in files) {
                if (!isValidGrammarFileName(f)) continue
                builtIn.add(GrammarEntry(f, builtIn = true, file = null))
            }
        }
        val userScopes = UinApplication.userGrammars().associateBy { it.path }
        val user = ArrayList<GrammarEntry>()
        val root = userRoot(context)
        val dirs = root.listFiles { f -> f.isDirectory && f.name != "themes" } ?: emptyArray()
        for (dir in dirs) {
            val files = File(dir, "syntaxes").listFiles { f -> f.isFile } ?: continue
            for (f in files) {
                val info = userScopes[f.absolutePath]
                user.add(
                    GrammarEntry(
                        name = f.nameWithoutExtension,
                        builtIn = false,
                        file = f,
                        scope = info?.scope
                    )
                )
            }
        }
        return (builtIn + user).sortedBy { it.name }
    }

    /**
     * 批量导入（SAF 多选）：逐个按**内容**判定是主题还是语法并分别校验，非法文件单独记失败、
     * 不影响同批其余文件。全程 IO 线程。
     */
    suspend fun import(context: Context, uris: List<Uri>): ImportOutcome =
        withContext(Dispatchers.IO) {
            val imported = ArrayList<String>()
            val deferred = ArrayList<String>()
            val failed = ArrayList<Pair<String, ImportError>>()
            for (uri in uris) {
                val label = displayName(context, uri)
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null || bytes.isEmpty()) {
                    failed.add(label to ImportError.READ_FAILED)
                    continue
                }
                val text = bytes.decodeToString()
                val scope = parseScopeName(text)
                if (scope != null) {
                    importGrammar(context, label, bytes, scope, imported, deferred, failed)
                } else {
                    importTheme(context, label, text, imported, deferred, failed)
                }
            }
            ImportOutcome(imported, deferred, failed)
        }

    /** 删除一个**用户导入**的文件（内置只读）。返回是否真的删掉了。 */
    fun deleteTheme(entry: ThemeEntry): Boolean {
        val file = entry.file ?: return false
        if (entry.builtIn) return false
        val ok = runCatching { file.delete() }.getOrDefault(false)
        if (ok) TextMateThemes.invalidateCache()
        return ok
    }

    fun deleteGrammar(entry: GrammarEntry): Boolean {
        val file = entry.file ?: return false
        if (entry.builtIn) return false
        val ok = runCatching { file.delete() }.getOrDefault(false)
        if (ok) {
            // 注册表没有热移除接口：摘掉登记（选择器不再出现），内存里的实例等重启释放
            val path = file.absolutePath
            val scope = UinApplication.grammarAssetPaths.entries
                .firstOrNull { it.value == path }?.key
            if (scope != null) UinApplication.forgetUserGrammar(scope)
        }
        return ok
    }

    // ==================== 导入分支 ====================

    private fun importTheme(
        context: Context,
        label: String,
        text: String,
        imported: MutableList<String>,
        deferred: MutableList<String>,
        failed: MutableList<Pair<String, ImportError>>
    ) {
        val cleaned = TextMateThemes.sanitizeJsonc(text)
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
        // 主题要能提供配色：VS Code 的 colors / tokenColors，或 TextMate 的 settings
        // （内置就有一份 settings 结构的 darcula.json，用户导入同类文件不该被挡）
        if (json == null || (!json.has("colors") && !json.has("tokenColors") &&
                !json.has("settings"))
        ) {
            // 扩展名就不在主题/语法的可用范围里 → 说「类型不支持」比「内容非法」更准确
            val lower = label.lowercase()
            val supported = lower.endsWith(".json") || lower.endsWith(".tmlanguage") ||
                lower.endsWith(".plist")
            failed.add(label to if (supported) ImportError.INVALID else ImportError.UNSUPPORTED)
            return
        }
        val rawName = json.optString("name")
        if (rawName.isNotEmpty() && rawName in UinApplication.themeRawNames()) {
            failed.add(label to ImportError.THEME_DUP)
            return
        }
        val dir = userThemesDir(context).apply { mkdirs() }
        var stem = sanitizeStem(File(label).nameWithoutExtension).ifEmpty { "theme" }
        val own = File(dir, "$stem.json")
        // 撞已加载主题名（内置等）且不是覆盖自己的文件 → 自动加序号，避免重名
        if (!own.exists() && stem in UinApplication.themeStems()) {
            var i = 2
            while (File(dir, "$stem-$i.json").exists()) i++
            stem = "$stem-$i"
        }
        val target = File(dir, "$stem.json")
        runCatching { target.writeText(text, Charsets.UTF_8) }.onFailure {
            failed.add(label to ImportError.READ_FAILED)
            return
        }
        TextMateThemes.invalidateCache()
        val wasLoaded = stem in UinApplication.themeStems()
        val ok = UinApplication.getInstance().registerUserTheme(stem, target)
        when {
            ok -> imported.add(stem)
            wasLoaded -> deferred.add(stem) // 覆盖自己此前导入过的同名主题 → 重启生效
            else -> {
                // 注册失败（内容不被 sora 接受等）→ 清掉刚写入的文件，不留垃圾
                runCatching { target.delete() }
                TextMateThemes.invalidateCache()
                failed.add(label to ImportError.INVALID)
            }
        }
    }

    private fun importGrammar(
        context: Context,
        label: String,
        bytes: ByteArray,
        scope: String,
        imported: MutableList<String>,
        deferred: MutableList<String>,
        failed: MutableList<Pair<String, ImportError>>
    ) {
        val fileName = sanitizeStem(File(label).name).ifEmpty { "grammar.tmLanguage.json" }
        val stem = sanitizeStem(File(label).nameWithoutExtension).ifEmpty { "grammar" }
        val root = userRoot(context).apply { mkdirs() }

        // 1) 该 scope 已注册：只有「覆盖自己导入过的同一文件」才允许，否则会覆盖
        //    注册表里的 scope→definition 映射（sora 无热替换 → 只能等重启）。
        val existingPath = UinApplication.grammarAssetPaths[scope]
        if (existingPath != null) {
            val underUser = existingPath.startsWith(root.absolutePath + File.separator)
            if (underUser && File(existingPath).name == fileName) {
                runCatching { File(existingPath).writeBytes(bytes) }.onFailure {
                    failed.add(label to ImportError.READ_FAILED)
                    return
                }
                deferred.add(File(existingPath).nameWithoutExtension)
                return
            }
            failed.add(label to ImportError.SCOPE_DUP)
            return
        }

        // 2) 新 scope：同名目录已存在就换序号目录，绝不覆盖别的语法文件
        var dirName = stem
        if (File(root, dirName).exists()) {
            var i = 2
            while (File(root, "$stem-$i").exists()) i++
            dirName = "$stem-$i"
        }
        val syntaxes = File(File(root, dirName), "syntaxes").apply { mkdirs() }
        val target = File(syntaxes, fileName)
        runCatching { target.writeBytes(bytes) }.onFailure {
            failed.add(label to ImportError.READ_FAILED)
            return
        }
        val registered = UinApplication.getInstance().registerUserGrammar(target)
        if (registered == null) {
            runCatching { target.delete() } // 注册失败把半成品清掉，不留垃圾目录
            failed.add(label to ImportError.INVALID)
            return
        }
        imported.add(target.nameWithoutExtension)
    }

    // ==================== 小工具 ====================

    /** 与内置加载同口径的文件名规则（.json / .tmLanguage / .plist）。 */
    private fun isValidGrammarFileName(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".json") || lower.endsWith(".tmlanguage") || lower.endsWith(".plist")
    }

    /** 只保留安全字符，去掉首尾点 —— 目录名不能含点（内置加载器按「含点即文件」跳过）。 */
    private fun sanitizeStem(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.')

    /** 从文件内容里读 scopeName（JSON 语法 / plist XML 语法），读不出返回 null。 */
    private fun parseScopeName(text: String): String? = when {
        text.trimStart().startsWith("{") -> runCatching {
            JSONObject(text).optString("scopeName").takeIf { it.isNotEmpty() }
        }.getOrNull()
        text.contains("<plist") || text.contains("<?xml") ->
            Regex("""<key>\s*scopeName\s*</key>\s*<string>\s*([^<]+?)\s*</string>""")
                .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        else -> null
    }

    /** SAF 展示名（导入提示里用它指代文件）。 */
    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        }.getOrNull()
            ?: uri.lastPathSegment
            ?: uri.toString()
}
