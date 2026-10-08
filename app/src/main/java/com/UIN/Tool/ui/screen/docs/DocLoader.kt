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
}
