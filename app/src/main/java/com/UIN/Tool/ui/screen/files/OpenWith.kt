package com.UIN.Tool.ui.screen.files

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginBackendPaths
import com.UIN.Tool.plugin.PluginHostActivity
import com.UIN.Tool.ui.screen.dev.CodeEditorActivity
import com.UIN.Tool.ui.screen.docs.DocViewerActivity
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "OpenWith"

// ==================== 目标描述（跨版本持久化，改动要向后兼容） ====================

/** 内置文本编辑器。 */
const val TARGET_EDITOR = "builtin:editor"

/** 内置图片查看器。 */
const val TARGET_VIEWER = "builtin:viewer"

/** 内置 APK 信息页。 */
const val TARGET_APK_INFO = "builtin:apkinfo"

/** 内置字体查看页（ttf/otf/ttc）。 */
const val TARGET_FONT = "builtin:font"

/** 内置十六进制编辑页。 */
const val TARGET_HEX = "builtin:hex"

/** g10：在文件页里挂载并浏览压缩包（不是把它解压到磁盘）。 */
const val TARGET_ARCHIVE = "builtin:archive"

/**
 * 内置 Markdown **阅读**页（`.md` / `.markdown`）。
 *
 * 与 [TARGET_EDITOR] 是**两个并列的目标**，不是先后关系：同一个 `.md`，
 * 阅读页渲染成网页、编辑器给纯文本，两者都摆在「打开方式」网格里，
 * 谁当默认由用户在管理页挑 —— 所以存串也要分开，否则没法各存各的默认。
 */
const val TARGET_MD = "builtin:markdown"

/**
 * m.txt 第 9 条：内置密钥库查看 / 转换页（JKS / PKCS12 / BKS）。
 *
 * 与编辑器、图片查看器一样是**独立目标**，可以单独设成 `.jks` 的默认打开方式。
 */
const val TARGET_KEYSTORE = "builtin:keystore"

// ==================== 数据形状 ====================

/**
 * 「打开方式」网格三档列表的哪一档（弹窗左下角「类型」下拉切换）。
 *
 * 顺序即下拉里的顺序：先给自家能接的，再给系统推荐的，最后才是全量。
 */
enum class OpenWithMode { BUILT_IN, PLUGINS, SUGGESTED, ALL }

/** [OpenWithMode] → 下拉项文案。 */
fun modeLabel(mode: OpenWithMode): String = when (mode) {
    OpenWithMode.BUILT_IN -> Str.get(R.string.files_open_builtin)
    // s.txt 第 1 条（用户）：插件不再挂在「内置」下方，而是和内置 / 系统建议 / 全部
    // 并列的独立一档 —— 下拉里四项，各看各的，不互相挤占高度。
    OpenWithMode.PLUGINS -> Str.get(R.string.files_open_plugins)
    OpenWithMode.SUGGESTED -> Str.get(R.string.files_open_suggested)
    OpenWithMode.ALL -> Str.get(R.string.files_open_all)
}

/** 格子里的图标：内置能力给矢量图，系统应用给它自己的位图图标。 */
sealed interface OpenWithIcon {
    data class Vector(val value: ImageVector) : OpenWithIcon
    data class Raster(val value: ImageBitmap) : OpenWithIcon
}

/**
 * 打开方式网格里的一格。
 *
 * [target] 是**存进管理页的那串描述**：`builtin:<id>` 或 `包名/类名`。
 * [launch] 是点了以后真正起目标的那一下 —— 管理页只挑不启，会把它整个丢掉。
 */
class OpenWithItem(
    val label: String,
    val icon: OpenWithIcon,
    val target: String,
    val launch: () -> Unit
)

// ==================== 内置能力 ====================

/**
 * 这个文件能用的**内置**能力。与 [openEntry] 的分流是**超集**关系：
 * 那边先到先得（点一下直接开），这边**全摆出来**让用户挑 —— 图片的格子里除了
 * 「查看器」也照样有「编辑器」，因为 e23 要求**所有文件都能选文本编辑器**。
 *
 * 返回空表示内置一个都接不住，调用方据此「没有内置就直接给系统的」。
 * 判定只看文件名，所以传个假路径也能用（管理页按后缀挑目标时就是这么调的）。
 */
fun builtInOpsFor(
    context: Context,
    path: String,
    /**
     * g10：true = **完整内置**。「打开方式」弹窗用这一档：不管后缀认不认识，
     * 编辑器 / 十六进制 / 查看图片 / 安装包信息 / 字体查看器 / 浏览压缩包全部摆出来 ——
     * 用户想强行用某个工具开，是他自己的判断；这里不替他把入口藏掉。
     * 后缀 → 默认打开方式的绑定仍用 false（那里只该列出真能接住的后缀）。
     */
    includeAll: Boolean = false,
    /**
     * 「浏览压缩包」的落点。由文件页传入（在当前窗格里挂载该包）；管理页没有
     * 浏览上下文，传 null 时不摆这一格。
     */
    onBrowseArchive: ((String) -> Unit)? = null
): List<OpenWithItem> {
    val file = File(path)
    val ops = mutableListOf<OpenWithItem>()
    // 这个文件能用哪些内置能力**由 `assets/config/file_types.json` 决定**（m.txt 第 3 条 /
    // 用户「打开方式不要硬编码」）。Kotlin 这边只负责把能力名映射成真正能启动的闭包。
    val caps = FileTypes.builtIn(path)

    // Markdown 排在**最前**：`.md` 的默认打开方式就是渲染后的阅读页（openEntry 那边同序），
    // 网格顺序与真实分流一致，用户才不会「点了第一个却是另一个东西」。
    // 它也**故意同时**跟编辑器一起给 —— 两者的取舍属于用户，不属于这里。
    if ("markdown" in caps) {
        ops += OpenWithItem(
            label = Str.get(R.string.files_open_markdown),
            icon = OpenWithIcon.Vector(Icons.Default.Article),
            target = TARGET_MD
        ) {
            try {
                DocViewerActivity.start(context, file.absolutePath, file.name)
            } catch (e: Exception) {
                AppLog.w(TAG, "open markdown viewer failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }

    // 编辑器（e23「**所有文件**的打开方式都可选文本编辑器，不论是否是文本」）——
    // **不再设任何后缀门槛**，一视同仁地摆出来。
    //
    // 以前用 `TEXT_EXTENSIONS` 那份窄白名单卡着，于是 .dat/.nfo/.dump/.tsv/无后缀…
    // 这一大批「后缀不认识」的文件在打开方式里**根本没有编辑器这一格**，可它们点开
    // 其实完全没问题（正是你之前点名要的「未知后缀一律按 txt 打开」那条规则）。
    // 现在连已知二进制（png/mp4/zip/apk…）也给 —— 能不能看、值不值得看由你判断，
    // 不该由这里替你决定。真正的大二进制在编辑器里另有**尺寸**护栏（见 openFile）。
    ops += OpenWithItem(
        label = Str.get(R.string.files_drawer_editor),
        icon = OpenWithIcon.Vector(Icons.Default.Edit),
        target = TARGET_EDITOR
    ) {
        try {
            context.startActivity(
                Intent(context, CodeEditorActivity::class.java).apply {
                    putExtra("file_path", file.absolutePath)
                }
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "open editor failed: ${e.message}")
            AppToast.warning(context, Str.get(R.string.files_open_failed))
        }
    }

    if (includeAll || "viewer" in caps) {
        ops += OpenWithItem(
            label = Str.get(R.string.files_open_viewer),
            icon = OpenWithIcon.Vector(Icons.Default.Image),
            target = TARGET_VIEWER
        ) {
            try {
                ImagePagerActivity.start(context, file.absolutePath, null)
            } catch (e: Exception) {
                AppLog.w(TAG, "open viewer failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }

    if (includeAll || "apkinfo" in caps) {
        ops += OpenWithItem(
            label = Str.get(R.string.files_open_apk_info),
            icon = OpenWithIcon.Vector(Icons.Default.Android),
            target = TARGET_APK_INFO
        ) {
            try {
                ApkInfoActivity.start(context, file.absolutePath)
            } catch (e: Exception) {
                AppLog.w(TAG, "open apk info failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }

    // 字体（ttf/otf/ttc/otc）→ 内置查看器：试看 + 码位表 + name 表里的字体族名。
    // 与图片、APK 同一条「起失败就退系统分流」的路子，不让用户点了没反应。
    if (includeAll || "font" in caps) {
        ops += OpenWithItem(
            label = Str.get(R.string.files_open_font),
            icon = OpenWithIcon.Vector(Icons.Default.TextFields),
            target = TARGET_FONT
        ) {
            try {
                FontViewerActivity.start(context, file.absolutePath)
            } catch (e: Exception) {
                AppLog.w(TAG, "open font viewer failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }

    // 浏览压缩包（g10）：包内结构也能从「打开方式」进，不必先点开再退出来。
    // 动作由文件页给 —— 它知道哪个窗格该挂载；管理页没有浏览上下文时不摆。
    if (onBrowseArchive != null && !file.isDirectory &&
        (includeAll || "archive" in caps)
    ) {
        ops += OpenWithItem(
            label = Str.get(R.string.files_open_archive),
            icon = OpenWithIcon.Vector(Icons.Default.Unarchive),
            target = TARGET_ARCHIVE
        ) {
            try {
                onBrowseArchive(file.absolutePath)
            } catch (e: Exception) {
                AppLog.w(TAG, "browse archive failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }

    // m.txt 第 9 条：密钥库（JKS / PKCS12 / BKS）→ 内置查看 / 转换页。
    // 与图片、字体同一条「起失败就退提示」的路子。
    if (includeAll || "keystore" in caps) {
        ops += OpenWithItem(
            label = Str.get(R.string.files_open_keystore),
            icon = OpenWithIcon.Vector(Icons.Default.VpnKey),
            target = TARGET_KEYSTORE
        ) {
            try {
                KeystoreViewerActivity.start(context, file.absolutePath)
            } catch (e: Exception) {
                AppLog.w(TAG, "open keystore viewer failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }

    // 十六进制（6.3.0）：**不按后缀分流** —— 后缀说明不了内容，恰恰是那些未知后缀的
    // 二进制最需要看原始字节；能进这个列表的本来就是文件，只把目录排掉。
    if (!file.isDirectory) {
        ops += OpenWithItem(
            label = Str.get(R.string.hex_open),
            icon = OpenWithIcon.Vector(Icons.Default.Code),
            target = TARGET_HEX
        ) {
            try {
                HexEditorActivity.start(context, file.absolutePath)
            } catch (e: Exception) {
                AppLog.w(TAG, "open hex editor failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }

    return ops
}

// ==================== 系统应用 ====================

/**
 * 「打开方式」列表里图标统一转成这么大 —— 自适应图标的 intrinsic 能到 400px 以上，
 * 按原尺寸转是白烧内存（列表里一格才 26dp，128px 已经两倍于它了）。
 */
private const val OPEN_ICON_PX = 128

/**
 * 组件 → 「名称 + 图标」的进程内缓存（用户反馈 c17「加载还是太慢」）。
 *
 * 换个后缀再查一遍，命中的多半还是同一批应用。没有这层缓存，每查一次后缀就要给几十个
 * 应用各做一遍 `loadLabel`（一次 IPC）+ `loadIcon`（图标解码）—— 候选列表转圈的主体耗时
 * 就在这里，缓存之后第二次往后基本是瞬时的。
 *
 * 上限 256 条：一台机器上的组件量级远小于这个数，超出丢最旧的那条。
 */
private val openerMetaCache = LinkedHashMap<String, Pair<String, OpenWithIcon>>()

private fun openerMeta(
    component: String,
    compute: () -> Pair<String, OpenWithIcon>
): Pair<String, OpenWithIcon> {
    val cached = synchronized(openerMetaCache) { openerMetaCache[component] }
    if (cached != null) return cached
    val meta = compute()
    synchronized(openerMetaCache) {
        if (openerMetaCache.size >= 256) {
            // g10 崩溃：`iterator.remove()` 必须在 `next()` 之后调用，否则
            // LinkedHashMap 直接抛 IllegalStateException（crash_2026-11-08 就是这么来的）。
            // 丢最旧的一条用 keySet 首键即可。
            openerMetaCache.keys.firstOrNull()?.let { openerMetaCache.remove(it) }
        }
        openerMetaCache[component] = meta
    }
    return meta
}

/**
 * 按 MIME 查系统里能打开它的应用（「系统建议」/「全部」两档）。
 *
 * - [OpenWithMode.SUGGESTED]：`MATCH_DEFAULT_ONLY` —— 只取声明了 `DEFAULT` 类别的匹配，
 *   也就是 Android 平时会摆到你面前的那一档；
 * - [OpenWithMode.ALL]：再并上 MIME 通配的匹配，那些「什么都能开」的应用也算进来，
 *   相当于系统「打开方式」列表拉到底的那一屏。
 *   （具体查的是哪个通配串，写在函数里那行 `//` 注释 —— 这里不能直接写出来，
 *   星号斜杠星号会把本注释提前闭合。）
 *
 * 顺序原样保留系统给的：默认应用、用户置顶这些优先级是系统算出来的，自己重排只会排错。
 * 用 [PackageManager.MATCH_DEFAULT_ONLY] 而不加 `MATCH_ALL`，是为了让「建议」与「全部」
 * 真的有差别 —— 全量匹配会把一堆只声明了自定义 scheme 的应用也捞进来。
 *
 * [uri] 只用来参与 scheme 匹配：有真实文件时传 FileProvider 签出的那个（与真正启动时
 * 同一个 URI，才不会「列得出来点不动」），按后缀挑目标时传一个假 content URI 即可。
 * targetSdk 停在 28，不受 Android 11 的包可见性过滤影响，查询不需要 `<queries>`。
 */
fun systemAppsFor(
    context: Context,
    mime: String,
    uri: Uri,
    mode: OpenWithMode
): List<OpenWithItem> {
    val pm = context.packageManager

    fun query(type: String): List<ResolveInfo> = try {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, type),
            PackageManager.MATCH_DEFAULT_ONLY
        )
    } catch (e: Exception) {
        AppLog.w(TAG, "queryIntentActivities failed: ${e.message}")
        emptyList()
    }

    val hits = query(mime).toMutableList()
    if (mode == OpenWithMode.ALL && mime != "*/*") {
        val seen = hits.mapNotNull { it.activityInfo }
            .map { it.packageName to it.name }.toSet()
        // 通配串就是这个（星号斜杠星号）；写成行注释而不是 KDoc —— 块注释里它会提前闭合
        for (ri in query("*/*")) {
            val ai = ri.activityInfo ?: continue
            if ((ai.packageName to ai.name) !in seen) hits += ri
        }
    }

    return hits.mapNotNull { ri ->
        val ai = ri.activityInfo ?: return@mapNotNull null
        val component = "${ai.packageName}/${ai.name}"
        // 名称与图标按**组件**缓存（见 [openerMeta]）—— 这两样是候选列表转圈的主体耗时
        val (label, icon) = openerMeta(component) {
            val l = ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: ai.packageName
            // 图标读不出来就退回通用图标：应用本身能开，不能因为一张图把它从列表里抹掉
            val ic = try {
                ri.loadIcon(pm)?.toBitmap(OPEN_ICON_PX, OPEN_ICON_PX)
                    ?.let { OpenWithIcon.Raster(it.asImageBitmap()) }
            } catch (e: Exception) {
                AppLog.w(TAG, "loadIcon failed: ${e.message}")
                null
            } ?: OpenWithIcon.Vector(Icons.Default.OpenInNew)
            l to ic
        }

        OpenWithItem(
            label = label,
            icon = icon,
            target = component
        ) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // 必须带接收者：同作用域里还有个 `val component: String`（上面那串
                    // 包名/类名），裸写会解析到它 —— 报「val 不能重新赋值」。
                    this.component = ComponentName(ai.packageName, ai.name)
                })
            } catch (e: Exception) {
                AppLog.w(TAG, "open with $component failed: ${e.message}")
                AppToast.warning(context, Str.get(R.string.files_open_failed))
            }
        }
    }
}

/** 按后缀挑系统打开方式（管理页没有真实文件，只能从 MIME 反查）。 */
fun systemAppsForSuffix(context: Context, suffix: String, mode: OpenWithMode): List<OpenWithItem> {
    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(suffix.trim().trimStart('.').lowercase()) ?: "*/*"
    val uri = Uri.parse("content://${context.packageName}.openwith/$suffix")
    return systemAppsFor(context, mime, uri, mode)
}

/**
 * **系统应用**那一档的候选缓存（用户反馈㉚ + c17）。
 *
 * [systemAppsForSuffix] 要问 PackageManager **两次**、再给每个命中项 `loadIcon` + `loadLabel` ——
 * 一台机器几十个命中项，原先这段跑在主线程的 `remember` 里，点「切换」会卡到秒级、
 * 看着就像没反应。同一个后缀的候选在一次会话里不会变，缓存后第二次直接命中。
 *
 * **只缓存系统这一档**：内置能力是本地拼几个闭包，几微秒的事，缓存它反倒多一张表。
 * 拆开的另一个好处是调用方能**先摆内置的、系统的一到再接上** —— c17「加载还是太慢」
 * 的体感就是被这一档拖住的：原先一转圈到底，用户等的是他压根不看的那一档。
 *
 * 上限 16 条：来回试后缀时不至于把内存堆高，超出丢最旧的那条。
 */
private val systemChoiceCache = LinkedHashMap<String, List<OpenWithItem>>()

/** 系统应用那一档，带缓存。[suffix] 允许带前导点或大小写混杂，内部统一。 */
fun systemChoicesCached(context: Context, suffix: String): List<OpenWithItem> {
    val key = suffix.trim().trimStart('.').lowercase()
    if (key.isEmpty()) return emptyList()
    synchronized(systemChoiceCache) { systemChoiceCache[key]?.let { return it } }
    val list = systemAppsForSuffix(context, key, OpenWithMode.ALL)
    synchronized(systemChoiceCache) {
        if (systemChoiceCache.size >= 16) {
            // 同上：先 next 再 remove 的规则不适用于「只丢一条」，直接删首键。
            systemChoiceCache.keys.firstOrNull()?.let { systemChoiceCache.remove(it) }
        }
        systemChoiceCache[key] = list
    }
    return list
}

/**
 * 启动时要预热的后缀（e5）—— 清单来自 `assets/config/file_types.json` 的 `warmOpenWith`
 * （m.txt 第 3 条：后缀不要散落在 Kotlin 里），这里只负责逐个查一遍。
 */
fun warmOpenWithChoices(context: Context) {
    val app = context.applicationContext
    for (suffix in FileTypes.WARM_OPEN_WITH) {
        try {
            systemChoicesCached(app, suffix)
        } catch (e: Exception) {
            AppLog.w(TAG, "warm open-with $suffix failed: ${e.message}")
        }
    }
}

// ==================== 按存储串启动 ====================

/**
 * 按存储串起一个打开方式，返回是否真的起动了。
 *
 * 存储串有两种：`builtin:<id>` 与 `包名/类名`。认不出来 / 起失败一律返回 false，
 * 让调用方退回原有分流 —— 这串是**跨版本持久化**的，将来内置能力改名、
 * 目标应用被卸载，都不能把打开文件这件事卡死。
 */
fun launchTarget(context: Context, target: String, file: File): Boolean = try {
    when {
        target == TARGET_EDITOR -> {
            context.startActivity(
                Intent(context, CodeEditorActivity::class.java).apply {
                    putExtra("file_path", file.absolutePath)
                }
            )
            true
        }

        target == TARGET_VIEWER -> {
            ImagePagerActivity.start(context, file.absolutePath, null)
            true
        }

        target == TARGET_APK_INFO -> {
            ApkInfoActivity.start(context, file.absolutePath)
            true
        }

        target == TARGET_FONT -> {
            FontViewerActivity.start(context, file.absolutePath)
            true
        }

        target == TARGET_HEX -> {
            HexEditorActivity.start(context, file.absolutePath)
            true
        }

        // 「浏览压缩包」需要文件页的窗格上下文，不在这里起 Activity；
        // 从「打开方式」调用时走 onBrowseArchive，从默认绑定调用时提示走文件页。
        target == TARGET_ARCHIVE -> {
            AppToast.info(context, Str.get(R.string.files_open_archive_hint))
            true
        }
        target == TARGET_MD -> {
            DocViewerActivity.start(context, file.absolutePath, file.name)
            true
        }

        // m.txt 第 9 条：密钥库查看 / 转换页也可以被设成某个后缀的默认打开方式。
        target == TARGET_KEYSTORE -> {
            KeystoreViewerActivity.start(context, file.absolutePath)
            true
        }

        '/' in target -> {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val mime = if (file.isDirectory) "resource/folder"
            else MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                component = ComponentName(
                    target.substringBefore('/'),
                    target.substringAfter('/')
                )
            })
            true
        }

        else -> false
    }
} catch (e: Exception) {
    AppLog.w(TAG, "launch target $target failed: ${e.message}")
    false
}

/**
 * 存储串 → 「名称 + 图标」，管理页卡片左侧那一块用。
 *
 * 目标应用被卸载 / 组件被删掉时返回 null —— 那条绑定已经没意义，
 * 让管理页把它标成失效比画一个问号更诚实。
 */
fun openerOf(context: Context, target: String): Pair<String, OpenWithIcon>? {
    if (target.startsWith("builtin:")) {
        // w.txt 第 4 条（用户）：管理页里「浏览压缩包」这一格原先不出现 ——
        // 候选是按 `sample.$suffix` 且没传 onBrowseArchive 拼的，归档格被跳过，
        // 用户只能绑到编辑器；绑上以后打开文件自然就进了编辑器。
        // 这里同样用「完整内置」档并给一个空实现，只为拿到它的名称与图标。
        val builtIn = builtInOpsFor(
            context,
            when (target) {
                TARGET_EDITOR -> "x.txt"
                TARGET_VIEWER -> "x.png"
                TARGET_MD -> "x.md"
                TARGET_ARCHIVE -> "x.zip"
                else -> "x.apk"
            },
            includeAll = true,
            onBrowseArchive = {}
        ).firstOrNull { it.target == target } ?: return null
        return builtIn.label to builtIn.icon
    }
    if ('/' !in target) return null
    return try {
        val pm = context.packageManager
        val cn = ComponentName(target.substringBefore('/'), target.substringAfter('/'))
        val label = pm.getActivityInfo(cn, 0).loadLabel(pm).toString()
            .takeIf { it.isNotBlank() } ?: cn.packageName
        val icon = try {
            OpenWithIcon.Raster(pm.getApplicationIcon(cn.packageName).toBitmap().asImageBitmap())
        } catch (e: Exception) {
            OpenWithIcon.Vector(Icons.Default.OpenInNew)
        }
        label to icon
    } catch (e: Exception) {
        // 组件查不到 = 应用被卸载或类被改名，交给调用方当失效项处理
        AppLog.w(TAG, "openerOf $target failed: ${e.message}")
        null
    }
}

// ==================== 插件打开本地文件（r.txt 第 1 条）====================

/** 文件 → MIME，与「打开方式」弹窗查询系统候选时同一口径。 */
fun mimeOf(file: File): String =
    if (file.isDirectory) "resource/folder"
    else MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

/**
 * 把当前文件直接交给某个已安装插件打开。
 *
 * t.txt 第 1 条（用户）：用压缩解压插件打开文件时它报「未接收到文件」。
 *
 * 原因不在插件：插件的后端跑在 **proot 容器**里，原先只拿到 `filePath`（本机绝对
 * 路径），容器内并不存在这个路径 —— 它要的是 `containerPath`（容器内路径）。
 *
 * u.txt 第 1 条（用户）：不要复制进插件目录，让插件直接改**原文件**，且不限大小。
 * 内置 proot 默认把共享存储按原路径挂进容器，所以 [PluginBackendPaths.containerPath]
 * 能直通时直接写原路径；只有后端确实看不到（私有目录 / 关掉了共享存储挂载）才
 * 退回复制进 `.incoming/`。最后都补一份单元素 `files` 数组（有的插件只遍历数组）。
 */
fun openFileWithPlugin(
    context: Context,
    plugin: PluginInfo,
    file: File,
    mime: String = mimeOf(file)
): Boolean {
    return try {
        val data = JSONObject().apply {
            put("when", System.currentTimeMillis())
            put("kind", "file")
            put("type", "file")
            put("uri", Uri.fromFile(file).toString())
            put("name", file.name)
            put("mime", mime)
            put("filePath", file.absolutePath)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // u.txt 第 1 条（用户）：优先让插件直接操作**原文件** ——
            // 内置 proot 默认按原路径挂载共享存储，容器内路径就是宿主绝对路径，
            // 于是既不复制、也不限大小，插件改的就是原文件。
            val direct = PluginBackendPaths.containerPath(context, file)
            if (direct != null) {
                data.put("filePath", direct)
                data.put("containerPath", direct)
                data.put("incomingName", file.name)
            } else {
                // 后端看不到（私有目录 / 关掉了共享存储挂载）：才退回复制进 .incoming/。
                // 只有这条兜底路径会真的搬字节，大文件才需要提示。
                if (file.length() > 2L * 1024 * 1024) {
                    withContext(Dispatchers.Main) {
                        AppToast.info(context, Str.get(R.string.open_with_preparing_files))
                    }
                }
                val staged = runCatching { stageForPlugin(plugin, file) }.getOrNull()
                if (staged != null) {
                    data.put("filePath", staged.absolutePath)
                    data.put("incomingName", staged.name)
                    data.put("containerPath", "/plugins/${plugin.pluginId}/.incoming/${staged.name}")
                }
            }
            // 单文件也补一份数组：只认 `files` 的插件同样能看见这一条。
            data.put("files", JSONArray().apply { put(JSONObject(data.toString())) })
            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(
                        Intent(context, PluginHostActivity::class.java).apply {
                            putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId)
                            putExtra(PluginHostActivity.EXTRA_OPEN_DATA, data.toString())
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (e: Exception) {
                    AppLog.w(TAG, "launch plugin ${plugin.pluginId} failed: ${e.message}")
                    AppToast.warning(context, Str.get(R.string.files_open_failed))
                }
            }
        }
        true
    } catch (e: Exception) {
        AppLog.w(TAG, "open file with plugin ${plugin.pluginId} failed: ${e.message}")
        AppToast.warning(context, Str.get(R.string.files_open_failed))
        false
    }
}

/**
 * 把文件复制进插件的 `.incoming/`，返回副本；失败返回 null（调用方退回原路径）。
 * 已经在该插件的 `.incoming/` 里就直接复用，不做第二次拷贝。
 *
 * u.txt 第 1 条（用户）：**不再限制体积** —— 只在这条兜底路径上才会复制
 * （共享存储里的文件走 [PluginBackendPaths.containerPath] 直通原路径）。
 */
private fun stageForPlugin(plugin: PluginInfo, file: File): File? {
    if (!file.isFile) return null
    val dir = File(File(Constants.PLUGIN_DIR, plugin.pluginId), ".incoming")
    // 已经在这个插件的 .incoming 里（例如从插件自己的目录里再打开一次）就别重复拷。
    val alreadyStaged = runCatching {
        file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
    }.getOrDefault(false)
    if (alreadyStaged) return file
    if (!dir.exists() && !dir.mkdirs()) return null
    val safe = file.name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim()
        .ifEmpty { "file" }.take(120)
    val dest = File(dir, "${System.currentTimeMillis()}_$safe")
    file.copyTo(dest, overwrite = true)
    return dest
}
