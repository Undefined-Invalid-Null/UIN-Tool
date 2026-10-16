// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/EditorModels.kt
package com.UIN.Tool.ui.screen.dev.editor

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 6.2.0 编辑器数据模型。
 * 取代旧版「内存 HashMap + Serializable」方案，改为以标签页为中心的文档模型。
 */

/**
 * 6.2.0 批次26：TextMate 语法是否已加载完毕。
 * 语法在后台 StartupTask 里加载（耗时 1.8~4s）。外部 ACTION_VIEW 在启动瞬间打开文件时，
 * `TextMateLanguage.create(scope)` 因语法未注册而失败 → 回退 EmptyLanguage（无高亮）。
 * 加载完成后把此标志置 true，EditorCanvas 观察到翻转后对当前 tab 重设一次语言即可补上高亮。
 */
object TextMateInitState {
    val grammarsReady = MutableStateFlow(false)
}

/** 一个打开的文档（标签页）。 */
data class EditorTab(
    val id: String,
    /** 显示名（文件名或临时名）。 */
    val name: String,
    /** 真实文件后端；null = 未落盘的内存文档（插件向导模式 / 新建未保存）。 */
    val file: File? = null,
    /** 无法解析成真实路径的 content:// 后端。 */
    val uri: String? = null,
    /** 磁盘/来源上的内容。 */
    val savedContent: String = "",
    /** 当前编辑器中的内容。 */
    var content: String = savedContent,
    /**
     * 6.2.0 批次16：用户手动指定的语法高亮 scope（如 "source.python"）。
     * null = 按文件后缀名自动推断（[getLanguageScopeName]）。
     * 非空时优先于后缀推断，用户可在右上角语法选择器里覆盖。
     */
    val languageScope: String? = null,
    /**
     * 6.2.0 批次39（SE 审计 T3-19）：**显式脏标记**，取代原先的
     * `content != savedContent` 全量字符串比较。
     *
     * 原来 `isModified` 是个 `get() = content != savedContent`，而标签栏、关闭确认、
     * `hasUnsaved()` 都会读它；列表参数在 Compose 里被视为不稳定，光标每动一下
     * （`cursorLine` 变化）标签行就会重组一次 → **每个标签对整份文本做一遍 O(n) 比较**。
     * 现在改为 O(1) 布尔，只在 `updateContent`（变脏）、保存成功 / 撤销改动（变干净）时维护。
     */
    val edited: Boolean = false
) {
    val isModified: Boolean get() = edited
    val isBackedByFile: Boolean get() = file != null || uri != null

    /** 路径或 URI 的唯一标识（用于去重与重开）。 */
    val identity: String get() = file?.absolutePath ?: uri ?: id
}

/** 文件树节点。 */
sealed class FileNode {
    abstract val name: String
    abstract val path: String
    abstract val file: File

    data class DirNode(
        override val file: File,
        val children: List<FileNode> = emptyList(),
        val isExpanded: Boolean = false
    ) : FileNode() {
        override val name: String get() = file.name.ifEmpty { file.absolutePath }
        override val path: String get() = file.absolutePath
    }

    data class FileLeaf(
        override val file: File
    ) : FileNode() {
        override val name: String get() = file.name
        override val path: String get() = file.absolutePath
    }
}

/** 编辑器外观与行为设置。 */
data class EditorSettings(
    /** 6.2.0：默认主题固定为 dracula。原为空串=跟随系统深浅色，但系统并未定义「代码编辑器主题」，
     *  跟随系统实际只是按深浅色在 one-dark-pro / github-light 间切换，语义含糊，已移除该概念。 */
    val theme: String = "dracula",
    val fontSize: Float = 14f,
    val tabWidth: Int = 4,
    val fontFamily: String = "",
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val showNonPrintable: Boolean = false,
    val bracketMatching: Boolean = true,
    val stickyScroll: Boolean = true,
    val pinchScalable: Boolean = true,
    val autoComplete: Boolean = true,
    val symbolBar: Boolean = false,
    val lineDivider: Boolean = true,
    val currentLine: Boolean = true,
    // 6.2.0 批次39（SE 审计 T1-9）：此处**删掉了 autoSave 字段** —— 它只有 UI + 持久化、
    // 全工程零消费者，是个假开关；旧版本写下的 "auto_save" key 被自然忽略。
    /** 6.2.0 批次32：缩进层级参考线（sora `setBlockLineEnabled`）。 */
    val blockLine: Boolean = true,
    /** 6.2.0 批次32：浮动「行:列」位置面板（sora `setDisplayLnPanel`）。 */
    val lnPanel: Boolean = true,
    /** 6.2.0 批次32：连字（Fira Code 等字体的 -> ⇥ 等合字；sora `setLigatureEnabled`）。 */
    val ligature: Boolean = true,
    /** 6.2.0 批次32：光标闪烁（sora `setCursorBlinkPeriod`）。 */
    val cursorBlink: Boolean = true,
    /** 6.2.0 批次32：选中文字后弹出「剪切/复制/粘贴」悬浮窗（sora `EditorTextActionWindow`）。 */
    val textActionPopup: Boolean = true,
    /** 6.2.0 批次34：行距倍数（sora `setLineSpacingMultiplier`）。 */
    val lineSpacing: Float = 1f,
    /** 6.2.0 批次34：字宽（横向缩放，sora `setTextScaleX`）。 */
    val textScaleX: Float = 1f,
    /** 6.2.0 批次34：浮动「行:列」面板位置（sora `LineInfoPanelPosition`：1左/2上/4右/8下/15中）。 */
    val lnPanelPosition: Int = 15,
    /** 6.2.0 批次34：**新行**的换行符（0 LF / 1 CRLF / 2 CR，sora `LineSeparator`）。
     *  只影响此后从输入法插入的换行，不会改写文档里已有的行尾。 */
    val lineSeparator: Int = 0,
    /** 6.2.0 批次34：高亮当前代码块（sora `setHighlightCurrentBlock`）。 */
    val highlightBlock: Boolean = true,
    /** 6.2.0 批次34：显示 void/null 等函数占位字符（sora `setRenderFunctionCharacters`）。 */
    val renderFunctionChars: Boolean = true,
    /** 6.2.0 批次39（SE 审计 T2-16）：长按拖选时的放大镜（sora `widget/component/Magnifier`）。
     *  6.2.0 之前从未读写过，等于「默认开了但既无法感知也无法关闭」。 */
    val showMagnifier: Boolean = true
)
