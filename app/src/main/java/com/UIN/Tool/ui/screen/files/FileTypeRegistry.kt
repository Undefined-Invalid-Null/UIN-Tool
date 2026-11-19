// app/src/main/java/com/UIN/Tool/ui/screen/files/FileTypeRegistry.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.UIN.Tool.UinApplication
import compose.icons.Octicons
import compose.icons.octicons.Markdown16
import com.UIN.Tool.ui.screen.dev.editor.FileAccess
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import org.json.JSONObject

/**
 * 一种文件类型：后缀、图标、颜色、内置打开能力。
 *
 * 数据本体**不在 Kotlin 里**（m.txt 第 3 条 / 用户：「文件后缀，打开方式，图标，
 * 不要硬编码，单独写个文件」），而是放在 `assets/config/file_types.json`。这个类只是
 * 解析后的内存形态。
 *
 * @param id       JSON 里的类型名（image / markdown / keystore …），也用于分流判断；
 * @param builtIn  该类型能被本应用哪些内置查看器打开：markdown / viewer / apkinfo /
 *                 font / archive / keystore。编辑器与十六进制是**所有文件**都给，
 *                 所以不写进这里。
 */
data class FileType(
    val id: String,
    val icon: ImageVector,
    /** 文件图标固定色（不跟主题语义色走，避免改主题把类型色一起带偏）。 */
    val tint: Color,
    val extensions: Set<String>,
    val builtIn: Set<String>
)

/**
 * 文件类型注册表 —— 解析 `assets/config/file_types.json` 并对外提供查询。
 *
 * **只改 JSON，不改这里**：新增后缀、换图标、换颜色、调整内置打开能力，都在
 * `file_types.json` 里完成。Kotlin 侧只保留 [ICONS] 这张「JSON 里的图标名 →
 * Compose ImageVector」映射（ImageVector 没法序列化，必须落在代码里）。
 *
 * 解析失败时退回一张最小表（仅 txt/md/png/apk/zip 等常见后缀），保证文件页不会
 * 因为一个坏 JSON 直接崩掉；同时打一条日志，方便定位。
 */
object FileTypeRegistry {

    private const val TAG = "FileTypeRegistry"
    private const val ASSET_NAME = "config/file_types.json"

    /** JSON `icon` 字段 → Compose 图标。新增图标名时两边一起加。 */
    private val ICONS: Map<String, ImageVector> = mapOf(
        "image" to Icons.Default.Image,
        "video" to Icons.Default.VideoFile,
        "audio" to Icons.Default.AudioFile,
        "archive" to Icons.Default.FolderZip,
        "archive_stream" to Icons.Default.Archive,
        // m.txt 第 3 条：归档的三个子类图标各不相同 —— 同色同时只换 tint 时，
        // 小尺寸下几乎分不出「能解的 gz」「解不开的 cpio」和「光盘镜像」。
        "archive_off" to Icons.Default.Block,
        "disk_image" to Icons.Default.Album,
        "apk" to Icons.Default.Android,
        "installer" to Icons.Default.Apps,
        "font" to Icons.Default.FontDownload,
        "webfont" to Icons.Default.TextFields,
        "markdown" to Octicons.Markdown16,
        "pdf" to Icons.Default.PictureAsPdf,
        "ebook" to Icons.Default.MenuBook,
        "spreadsheet" to Icons.Default.TableChart,
        "presentation" to Icons.Default.Slideshow,
        "document" to Icons.Default.Article,
        "text" to Icons.Default.Description,
        "subtitle" to Icons.Default.Subtitles,
        "data" to Icons.Default.DataObject,
        "config" to Icons.Default.Settings,
        "java" to Icons.Default.Coffee,
        "code" to Icons.Default.Code,
        "javascript" to Icons.Default.Javascript,
        // q.txt 第 4 条（用户）：两种图标各归其位 —— 单页 HTML 用 Html 标，
        // 组件式 Web 工程（vue/svelte/astro）用通用语言图标。
        "web" to Icons.Default.Language,
        "html" to Icons.Default.Html,
        "style" to Icons.Default.Palette,
        "shell" to Icons.Default.Terminal,
        "notebook" to Icons.Default.Science,
        "native" to Icons.Default.Memory,
        "database" to Icons.Default.Storage,
        "certificate" to Icons.Default.Key,
        "keystore" to Icons.Default.VpnKey,
        "diff" to Icons.Default.Compare,
        "torrent" to Icons.Default.Download,
        // q.txt 第 4 条：补齐常用大类，避免它们落到「未知文件」的通用图标。
        "password" to Icons.Default.Lock,
        "design" to Icons.Default.Brush,
        "model3d" to Icons.Default.ViewInAr,
        "cad" to Icons.Default.Build,
        "email" to Icons.Default.Email,
        "game" to Icons.Default.SportsEsports,
        "template" to Icons.Default.Web
    )

    /**
     * 认不出类型时的兜底。
     *
     * m.txt 第 3 条：不再用灰色 —— 文件列表里满屏灰图标正是用户点名的问题。
     * r.txt 第 2 条：避免紫红/靛紫系列，改用蓝灰 —— 深浅色底上都够醒目，
     * 又能与各具体类型的固有色区分开。
     */
    private val UNKNOWN = FileType(
        id = "unknown",
        icon = Icons.Default.InsertDriveFile,
        tint = Color(0xFF546E7A),
        extensions = emptySet(),
        builtIn = emptySet()
    )

    /** 目录图标（颜色由调用方决定，通常跟主题色）。 */
    val directoryIcon: ImageVector = Icons.Default.Folder

    private class Parsed(
        val types: List<FileType>,
        val byId: Map<String, FileType>,
        val byExtension: Map<String, FileType>,
        val byName: Map<String, FileType>,
        /** 含点号的复合后缀（tar.gz 等），按长度倒序，先匹配长的。 */
        val compoundExtensions: List<Pair<String, FileType>>,
        val warmOpenWith: List<String>,
        /** 编辑器**不能**按文本打开的二进制后缀（JSON 顶层 `binaryExtensions`）。 */
        val binaryExtensions: Set<String>,
        /** 编辑器语法/文本统计认得的文本后缀（JSON 顶层 `textExtensions`）。 */
        val textExtensions: Set<String>
    )

    private val parsed: Parsed by lazy { load() }

    val types: List<FileType> get() = parsed.types

    val warmOpenWith: List<String> get() = parsed.warmOpenWith

    /** 文本后缀清单 —— 文件页分流、属性框行数统计、编辑器语法识别共用这一份。 */
    val textExtensions: Set<String> get() = parsed.textExtensions

    /**
     * q.txt 第 4 条：后缀是不是二进制（编辑器不能按文本打开）。
     *
     * **唯一来源是 `file_types.json` 的顶层 `binaryExtensions`** —— Kotlin 里不再留
     * 第二份后缀表。`FileAccess.isTextFile` 直接调这里，避免「JSON 认得出类型、
     * 编辑器却按另一份名单拒开/乱开」。
     */
    fun isBinaryExtension(extension: String): Boolean =
        parsed.binaryExtensions.contains(extension.lowercase().removePrefix("."))

    fun typeById(id: String): FileType? = parsed.byId[id]

    /** 某个后缀属于哪种类型；未知返回 null。 */
    fun typeOfExtension(extension: String): FileType? =
        parsed.byExtension[extension.lowercase().removePrefix(".")]

    /**
     * 文件名 → 类型。顺序：整名匹配（makefile/readme…）→ 复合后缀（tar.gz）→
     * 普通后缀。认不出返回 null —— 让调用方决定用兜底图标还是按文本处理。
     */
    fun typeOf(fileName: String): FileType? {
        val lower = fileName.lowercase()
        parsed.byName[lower]?.let { return it }
        for ((suffix, type) in parsed.compoundExtensions) {
            if (lower.endsWith(".$suffix")) return type
        }
        // 注意是**文件名本身**去取后缀：`typeOfExtension` 自己会 lowercase + 去点，
        // 这里不用先剥一层（`lower` 已经小写，两者等价）。
        return typeOfExtension(lower.substringAfterLast('.', ""))
    }

    /** 某类型的内置打开能力（markdown / viewer / apkinfo / font / archive / keystore）。 */
    fun builtInFor(fileName: String): Set<String> =
        typeOf(fileName)?.builtIn ?: emptySet()

    /** 所有带某个内置能力的后缀 —— 例如「哪些后缀能用内置 keystore 打开」。 */
    fun extensionsWithBuiltIn(target: String): Set<String> =
        parsed.types.filter { target in it.builtIn }.flatMap { it.extensions }.toSet()

    // ---------------- 解析 ----------------

    private fun load(): Parsed {
        val json = try {
            UinApplication.getInstance().assets.open(ASSET_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (e: Exception) {
            AppLog.w(TAG, "read $ASSET_NAME failed: ${e.message}")
            return fallback()
        }
        return try {
            parse(JSONObject(json))
        } catch (e: Exception) {
            // JSON 写坏了也不能让文件页起不来：退回最小表并留下日志。
            AppLog.w(TAG, "parse $ASSET_NAME failed: ${e.message}")
            fallback()
        }
    }

    private fun parse(root: JSONObject): Parsed {
        val types = ArrayList<FileType>()
        val byId = LinkedHashMap<String, FileType>()
        val byExtension = LinkedHashMap<String, FileType>()
        val byName = LinkedHashMap<String, FileType>()
        val compound = ArrayList<Pair<String, FileType>>()

        val array = root.optJSONArray("types")
        if (array != null) {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").trim().lowercase()
                if (id.isEmpty()) continue
                val icon = ICONS[o.optString("icon").trim().lowercase()]
                    ?: UNKNOWN.icon
                val tint = parseColor(o.optString("tint"), UNKNOWN.tint)
                val exts = o.optJSONArray("extensions").toStringSet()
                val builtIn = o.optJSONArray("builtIn").toStringSet()
                val type = FileType(id, icon, tint, exts, builtIn)
                types += type
                byId[id] = type
                for (raw in exts) {
                    val ext = raw.removePrefix(".")
                    if (ext.isEmpty()) continue
                    if (!byExtension.containsKey(ext)) byExtension[ext] = type
                    if (ext.contains('.')) compound += ext to type
                }
            }
        }

        root.optJSONObject("byName")?.let { names ->
            for (key in names.keys()) {
                val id = names.optString(key).lowercase()
                byId[id]?.let { byName[key.lowercase()] = it }
            }
        }
        // 长后缀优先，tar.gz 不会被 gz 抢走。
        compound.sortByDescending { it.first.length }

        val warm = root.optJSONArray("warmOpenWith").toStringList()
        val binary = root.optJSONArray("binaryExtensions").toStringSet()
        val text = root.optJSONArray("textExtensions").toStringSet()
        return Parsed(types, byId, byExtension, byName, compound, warm, binary, text)
    }

    private fun org.json.JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        val out = LinkedHashSet<String>()
        for (i in 0 until length()) {
            val s = optString(i).trim().lowercase()
            if (s.isNotEmpty()) out += s
        }
        return out
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotEmpty()) out += s
        }
        return out
    }

    private fun parseColor(raw: String, fallback: Color): Color {
        val s = raw.trim().removePrefix("#")
        if (s.isEmpty()) return fallback
        return try {
            val argb = when (s.length) {
                6 -> 0xFF000000L or s.toLong(16)
                8 -> s.toLong(16)
                3 -> {
                    // #RGB → #RRGGBB
                    val r = s[0].toString().repeat(2)
                    val g = s[1].toString().repeat(2)
                    val b = s[2].toString().repeat(2)
                    0xFF000000L or (r + g + b).toLong(16)
                }
                else -> return fallback
            }
            Color(argb.toInt())
        } catch (e: Exception) {
            fallback
        }
    }

    /**
     * JSON 缺失/损坏时的最小可用表。
     *
     * 不是「第二份后缀清单」—— 正常路径永远走 JSON；这里只保证极端情况下列表
     * 仍能显示图标而不是一片空白，数量刻意压到最少，避免形成第二份真源。
     */
    private fun fallback(): Parsed {
        val map = linkedMapOf(
            "text" to (Icons.Default.Description to Color(0xFF3860AF)),
            "markdown" to (Octicons.Markdown16 to Color(0xFF00838F)),
            "image" to (Icons.Default.Image to Color(0xFF43A047)),
            "archive" to (Icons.Default.FolderZip to Color(0xFF795548)),
            "apk" to (Icons.Default.Android to Color(0xFF2E7D32)),
            "font" to (Icons.Default.FontDownload to Color(0xFF6D4C41)),
            "keystore" to (Icons.Default.VpnKey to Color(0xFF8D6E63))
        )
        val exts = mapOf(
            "text" to setOf("txt", "log"),
            "markdown" to setOf("md", "markdown"),
            "image" to setOf("png", "jpg", "jpeg", "gif", "webp"),
            "archive" to setOf("zip", "jar", "tar", "gz"),
            "apk" to setOf("apk", "aab"),
            "font" to setOf("ttf", "otf", "ttc"),
            "keystore" to setOf("jks", "keystore", "bks", "p12", "pfx")
        )
        val builtIn = mapOf(
            "markdown" to setOf("markdown"),
            "image" to setOf("viewer"),
            "archive" to setOf("archive"),
            "apk" to setOf("apkinfo"),
            "font" to setOf("font"),
            "keystore" to setOf("keystore")
        )
        val types = map.keys.map { id ->
            val (icon, tint) = map.getValue(id)
            FileType(id, icon, tint, exts[id].orEmpty(), builtIn[id].orEmpty())
        }
        return Parsed(
            types = types,
            byId = types.associateBy { it.id },
            byExtension = buildMap {
                types.forEach { t -> t.extensions.forEach { ext -> if (!containsKey(ext)) put(ext, t) } }
            },
            byName = emptyMap(),
            compoundExtensions = emptyList(),
            warmOpenWith = listOf("txt", "png", "apk", "zip", "md", "jks"),
            // 兜底同样只留「最小可用」—— 正常路径永远读 JSON。
            binaryExtensions = setOf(
                "png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "zip", "apk",
                "pdf", "ttf", "otf", "jks", "keystore", "so", "dex"
            ),
            textExtensions = setOf("txt", "log", "md", "markdown")
        )
    }
}

/**
 * 文件后缀 / 打开方式 / 图标的上层门面。
 *
 * 所有判断都回到 [FileTypeRegistry]（也就是 `file_types.json`），调用点不要再写
 * `setOf("md", …)`。文本能否按编辑器打开仍由 [FileAccess.isTextFile] 负责 ——
 * 那是「编辑器接不接得住」的问题，不是「文件是什么类型」的问题。
 */
object FileTypes {

    fun extensionsOf(id: String): Set<String> =
        FileTypeRegistry.typeById(id)?.extensions ?: emptySet()

    fun isType(fileName: String, vararg ids: String): Boolean {
        val id = FileTypeRegistry.typeOf(fileName)?.id ?: return false
        return id in ids
    }

    /** 后缀在注册表里认得出来吗（决定「打开方式」默认摆在哪个页签，见 m.txt 第 10 条）。 */
    fun isKnown(fileName: String): Boolean = FileTypeRegistry.typeOf(fileName) != null

    val MARKDOWN: Set<String> get() = extensionsOf("markdown")
    val IMAGE: Set<String> get() = extensionsOf("image")
    val FONT: Set<String> get() = extensionsOf("font")
    val ARCHIVE: Set<String> get() = extensionsOf("archive") + extensionsOf("archive_stream")
    val KEYSTORE: Set<String> get() = extensionsOf("keystore")
    val CERTIFICATE: Set<String> get() = extensionsOf("certificate")
    val APK: Set<String> get() = extensionsOf("apk")

    val WARM_OPEN_WITH: List<String> get() = FileTypeRegistry.warmOpenWith

    /** 文本后缀清单（JSON 顶层 `textExtensions`）—— 文件页分流与属性框统计共用。 */
    val TEXT_EXTENSIONS: Set<String> get() = FileTypeRegistry.textExtensions

    /**
     * 「文件页点开进编辑器」的类型 id —— 与 `file_types.json` 的 id 对齐。
     *
     * 只列**编辑得来**的类型：图片/视频/音频/归档/安装包/字体/密钥库…不在其中，
     * 它们各有专用查看器；`database` 在里面但 `.db`/`.sqlite` 会被
     * [FileAccess.isTextFile] 的黑名单挡住，只有 `.sql` 这类真文本进来。
     */
    private val TEXT_TYPE_IDS = setOf(
        "text", "markdown", "subtitle", "data", "config", "java", "code",
        "javascript", "web", "html", "style", "shell", "notebook", "native",
        "database", "diff", "spreadsheet",
        // q.txt 第 4 条：这些大类里混着真文本格式（tex/rtf、gltf/dae/step、dxf）——
        // 二进制成员由 FileAccess 的黑名单挡掉，文本成员继续进编辑器。
        "document", "model3d", "cad"
    )

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    fun isMarkdown(fileName: String): Boolean = isType(fileName, "markdown")
    fun isImage(fileName: String): Boolean = isType(fileName, "image")
    fun isFont(fileName: String): Boolean = isType(fileName, "font")
    fun isArchive(fileName: String): Boolean =
        isType(fileName, "archive", "archive_stream")
    fun isKeystore(fileName: String): Boolean = isType(fileName, "keystore")
    fun isApk(fileName: String): Boolean = isType(fileName, "apk")
    fun isText(fileName: String): Boolean = FileAccess.isTextFile(fileName)

    /**
     * 文件页「点一下该进编辑器吗」—— 与 [isText] 不是一回事。
     *
     * [isText] 是[黑名单制][FileAccess.isTextFile]（未知后缀也算文本，编辑器自己接得住）；
     * 但文件页要的是**分流**：后缀认得出是文本 → 直接进编辑器；后缀本应用完全不认识
     * → 摆出「打开方式」让用户挑（m.txt 第 10 条）。
     *
     * 判定 = 注册表里属于文本类 且 不在二进制黑名单里，另加编辑器自己的白名单
     * （那份清单里有 `.hbs`/`.glsl` 等注册表还没单列的工程后缀）。
     */
    fun isEditableText(fileName: String): Boolean =
        FileAccess.isTextFile(fileName) && (
            extensionOf(fileName) in TEXT_EXTENSIONS ||
                isType(fileName, *TEXT_TYPE_IDS.toTypedArray())
            )

    /** 该文件能被哪些内置查看器打开（见 `file_types.json` 的 `builtIn`）。 */
    fun builtIn(fileName: String): Set<String> = FileTypeRegistry.builtInFor(fileName)
}

/** 文件名 → 类型；认不出返回 unknown。 */
fun fileKindOf(fileName: String): FileType =
    FileTypeRegistry.typeOf(fileName) ?: FileType(
        id = "unknown",
        icon = Icons.Default.InsertDriveFile,
        // m.txt 第 3 条：与 [FileTypeRegistry] 的兜底色保持一致，不再发灰。
        // r.txt 第 2 条：同步改为蓝灰，避免靛紫。
        tint = Color(0xFF546E7A),
        extensions = emptySet(),
        builtIn = emptySet()
    )

/** 文件列表 / 编辑器文件树共用的类型图标。 */
fun fileIconFor(fileName: String): ImageVector = fileKindOf(fileName).icon

/** 文件列表 / 回收站共用的类型颜色；目录仍跟主题色走。 */
@Composable
fun fileIconTintFor(fileName: String, isDir: Boolean = false): Color =
    if (isDir) AppColors.primary() else fileKindOf(fileName).tint