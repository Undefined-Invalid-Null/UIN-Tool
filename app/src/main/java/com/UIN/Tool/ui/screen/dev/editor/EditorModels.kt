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
    /** 磁盘/来源上的内容。窗口模式下 = **当前页**的基线文本（随翻页更新）。 */
    val savedContent: String = "",
    /** 当前编辑器中的内容。窗口模式下 = **当前页**文本（整档不在内存）。 */
    var content: String = savedContent,
    /**
     * 6.2.0 批次16：用户手动指定的语法高亮 scope（如 "source.python"）。
     * null = 按文件后缀名自动推断（[getLanguageScopeName]）。
     * 非空时优先于后缀推断，用户可在右上角语法选择器里覆盖。
     */
    val languageScope: String? = null,
    /**
     * 6.2.0：用户选择的编码。[charset] 是**选择**（默认 [FileAccess.CHARSET_AUTO] 自动检测），
     * [effectiveCharset] 是**实际解出这份文本所用的编码**，状态栏「编码」按钮显示它，
     * 覆盖保存时也按它写回。
     *
     * 两者都存是因为：自动档下必须先读过文件才知道具体是 UTF-8 还是 GB18030，
     * 而用户一改选择就要能立刻重新解码、立刻看到状态栏变化。
     */
    val charset: String = FileAccess.CHARSET_AUTO,
    val effectiveCharset: String = "UTF-8",
    /**
     * 6.2.0 批次39（SE 审计 T3-19）：**显式脏标记**，取代原先的
     * `content != savedContent` 全量字符串比较。
     *
     * 原来 `isModified` 是个 `get() = content != savedContent`，而标签栏、关闭确认、
     * `hasUnsaved()` 都会读它；列表参数在 Compose 里被视为不稳定，光标每动一下
     * （`cursorLine` 变化）标签行就会重组一次 → **每个标签对整份文本做一遍 O(n) 比较**。
     * 现在改为 O(1) 布尔，只在 `updateContent`（变脏）、保存成功 / 撤销改动（变干净）时维护。
     */
    val edited: Boolean = false,
    /**
     * 6.2.0 批次46：**分页窗口模式**（超大文件降级打开）的窗口信息；null = 整档模式（常规）。
     * 非空时 [content] 只是当前页文本，总行数/页数以此为准（编辑器的行号是**页内相对行号**）。
     */
    val window: WindowInfo? = null,
    /**
     * 6.2.0 批次46：内容版本号。同标签页内换页时 [content] 被整体替换，而 EditorCanvas
     * 只在 tab.id 变化时重设 sora 文本 —— 这里加一个单调版本号，翻页 +1 触发 setText。
     */
    val contentRev: Int = 0
) {
    val isModified: Boolean get() = edited
    /** 批次46：是否处于分页窗口模式（= [window] 非空）。 */
    val windowed: Boolean get() = window != null
    val isBackedByFile: Boolean get() = file != null || uri != null

    /**
     * 6.2.0：**保存时真正使用的编码**。自动档按读取时判定的编码原样写回 ——
     * 否则一个 GBK 文件读进来（判成 GB18030）、一保存被写成 UTF-8，
     * 等于在用户不知情的情况下换掉了文件编码。
     */
    val writeCharset: String
        get() = if (charset == FileAccess.CHARSET_AUTO) effectiveCharset else charset

    /** 路径或 URI 的唯一标识（用于去重与重开）。 */
    val identity: String get() = file?.absolutePath ?: uri ?: id
}

/**
 * 6.2.0 批次46：分页窗口模式的窗口元信息。
 * 总行数/页数来自磁盘索引（[WindowedDocument]），页码 1 起（与分页控件口径一致）。
 */
data class WindowInfo(
    /** 当前页（1 起）。 */
    val page: Int,
    /** 全文总行数（磁盘索引所得）。 */
    val totalLines: Int,
    /** 全文总页数。 */
    val totalPages: Int
)

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
    /** 6.2.0 批次54（用户指定）：默认主题 github-dark-dimmed（原 dracula）。
     *  仍可在主题选择器里随时切换；已存过主题的老配置不受影响（只改默认值与空串兜底）。 */
    val theme: String = "github-dark-dimmed",
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
    val showMagnifier: Boolean = true,
    /** 6.2.0：大文件省耗 —— 内容超过阈值时自动关掉语法高亮/粘性滚动/缩进线等耗能项。
     *  默认开启；关掉即恢复始终全开。用户仍可在语法选择器里为单个文件手动指定高亮。 */
    val largeFileMode: Boolean = true,
    /** 6.2.0 批次42：文件树名称多行显示 —— 关（默认）单行裁切出省略号、行尾 ⋮ 恒可见；
     *  开则名称换多行显示、行高自适应。 */
    val fileTreeMultiLine: Boolean = false
)
