package com.UIN.Tool.ui.screen.docs

import com.UIN.Tool.UinApplication
import com.UIN.Tool.R
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig

/**
 * G-10：文档加载单一实现。
 * 原先 `DocViewerScreen.loadDocument` 与 `HelpScreen` 的 LaunchedEffect 各写一份
 * 「语言判定 → 拼 assets 路径 → 读文件」，语言口径与回退行为漂移（Help 版多一层
 * zh-cn 回退、DocViewer 版没有）。收口到 DocLoader 后两处共用同一逻辑：
 * - 语言：UIConfig 显式 en，或 system + 系统 locale 为 en → `docs/en/`，否则 `docs/zh-cn/`；
 * - 回退：目标语言文件缺失时回退 `docs/zh-cn/`（en 目录历史上缺过 CONTRIBUTORS.md）。
 */
object DocLoader {

    /** docType → assets 文件名 */
    fun fileNameFor(docType: String): String = when (docType) {
        "help" -> "Help.md"
        "dev" -> "README.md"
        "changelog" -> "CHANGELOG.md"
        "about" -> "About.md"
        "contributors" -> "CONTRIBUTORS.md"
        else -> "README.md"
    }

    fun isEnglish(): Boolean {
        val language = UIConfig.getLanguage()
        return language == "en" ||
            (language == "system" && java.util.Locale.getDefault().language == "en")
    }

    /**
     * 读取文档正文；目标语言缺失时回退 zh-cn，仍失败则抛出由调用方转成错误文案。
     */
    fun load(fileName: String): String {
        val appContext = UinApplication.getInstance()
        val preferred = if (isEnglish()) "docs/en/$fileName" else "docs/zh-cn/$fileName"
        return try {
            appContext.assets.open(preferred).use { it.reader(Charsets.UTF_8).readText() }
        } catch (e: Exception) {
            if (preferred == "docs/zh-cn/$fileName") throw e
            AppLog.w("DocLoader", "missing $preferred, falling back to zh-cn: ${e.message}")
            appContext.assets.open("docs/zh-cn/$fileName").use { it.reader(Charsets.UTF_8).readText() }
        }
    }

    /** 失败时统一的用户可见错误文案（两处入口共用） */
    fun errorText(e: Exception): String =
        Str.get(R.string.failed_to_load_document_e_message, e.message)

    /**
     * x.txt 第 1 条（用户）：README / About 里的相对链接（如 `CHANGELOG.md`）要能点开。
     *
     * WebView 的 base URL 以前固定写成 `file:///android_asset/`，相对链接会被解析成
     * `file:///android_asset/CHANGELOG.md` —— 根目录下根本没有这份文件，于是报
     * `net::ERR_FILE_NOT_FOUND`。base 必须指向**这份文档真正所在的目录**。
     */
    fun baseUrl(fileName: String): String {
        val dir = if (isEnglish() && assetExists("docs/en/$fileName")) "docs/en" else "docs/zh-cn"
        return "file:///android_asset/$dir/"
    }

    private fun assetExists(path: String): Boolean = try {
        UinApplication.getInstance().assets.open(path).close()
        true
    } catch (_: Exception) {
        false
    }

    /** assets 文档文件名 → `doc_type`（文档之间的相对链接靠它转给内置阅读页）。 */
    fun docTypeForFileName(fileName: String): String? = when (fileName.lowercase()) {
        "readme.md" -> "dev"
        "changelog.md" -> "changelog"
        "help.md" -> "help"
        "about.md" -> "about"
        "contributors.md" -> "contributors"
        else -> null
    }

    /** 文档标题（点链接跳转时给新页面用；不传会退化成「文档」两个字）。 */
    fun titleFor(docType: String): String = Str.get(
        when (docType) {
            "help" -> R.string.help_2
            "dev" -> R.string.development_docs
            "changelog" -> R.string.changelog
            "about" -> R.string.about
            "contributors" -> R.string.contributors
            else -> R.string.docs
        }
    )

    /**
     * 拦截文档里的 `.md` 相对链接，交给内置阅读页渲染。
     *
     * 放行 WebView 自己加载的话：链接对了也只是把 markdown 源码当纯文本显示，
     * 链接错了就是 `ERR_FILE_NOT_FOUND`。返回 true 表示已接管。
     */
    fun openLinkedDoc(context: android.content.Context, url: String?): Boolean {
        if (url == null || !url.startsWith("file://")) return false
        val name = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val docType = docTypeForFileName(name) ?: return false
        DocViewerActivity.startDoc(context, docType, titleFor(docType))
        return true
    }
}
