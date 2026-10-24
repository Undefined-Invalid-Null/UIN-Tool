// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/EditorSettingsStore.kt
package com.UIN.Tool.ui.screen.dev.editor

import android.content.Context
import android.content.SharedPreferences

/**
 * 编辑器设置持久化（沿用旧版 editor_prefs，新增 6.2.0 键）。
 * 兼容：旧版只写过 selected_theme，其余取默认值。
 */
object EditorSettingsStore {

    private const val PREFS = "editor_prefs"

    /** theme 取该值时跟随系统深浅色。 */
    const val THEME_FOLLOW_SYSTEM = ""
    private const val K_THEME = "selected_theme"
    private const val K_FONT_SIZE = "font_size"
    private const val K_TAB_WIDTH = "tab_width"
    private const val K_FONT_FAMILY = "font_family"
    private const val K_WORD_WRAP = "word_wrap"
    private const val K_LINE_NUMBERS = "line_numbers"
    private const val K_NON_PRINTABLE = "non_printable"
    private const val K_BRACKET_MATCH = "bracket_match"
    private const val K_STICKY_SCROLL = "sticky_scroll"
    private const val K_PINCH_SCALABLE = "pinch_scalable"
    private const val K_AUTO_COMPLETE = "auto_complete"
    private const val K_SYMBOL_BAR = "symbol_bar"
    private const val K_LINE_DIVIDER = "line_divider"
    private const val K_CURRENT_LINE = "current_line"
    // 6.2.0 批次39：K_AUTO_SAVE("auto_save") 已随死开关移除，旧 key 不再读写。
    private const val K_BLOCK_LINE = "block_line"
    private const val K_LN_PANEL = "ln_panel"
    private const val K_LIGATURE = "ligature"
    private const val K_CURSOR_BLINK = "cursor_blink"
    private const val K_TEXT_ACTION_POPUP = "text_action_popup"
    private const val K_LINE_SPACING = "line_spacing"
    private const val K_TEXT_SCALE_X = "text_scale_x"
    private const val K_LN_PANEL_POSITION = "ln_panel_position"
    private const val K_LINE_SEPARATOR = "line_separator"
    private const val K_HIGHLIGHT_BLOCK = "highlight_block"
    private const val K_RENDER_FN_CHARS = "render_function_characters"
    private const val K_SHOW_MAGNIFIER = "show_magnifier"
    private const val K_LARGE_FILE_MODE = "large_file_mode"
    private const val K_FILE_TREE_MULTI_LINE = "file_tree_multi_line"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): EditorSettings {
        val p = prefs(context)
        val defaults = EditorSettings()
        return EditorSettings(
            theme = p.getString(K_THEME, defaults.theme) ?: defaults.theme,
            fontSize = p.getFloat(K_FONT_SIZE, defaults.fontSize),
            tabWidth = p.getInt(K_TAB_WIDTH, defaults.tabWidth),
            fontFamily = p.getString(K_FONT_FAMILY, defaults.fontFamily) ?: defaults.fontFamily,
            wordWrap = p.getBoolean(K_WORD_WRAP, defaults.wordWrap),
            showLineNumbers = p.getBoolean(K_LINE_NUMBERS, defaults.showLineNumbers),
            showNonPrintable = p.getBoolean(K_NON_PRINTABLE, defaults.showNonPrintable),
            bracketMatching = p.getBoolean(K_BRACKET_MATCH, defaults.bracketMatching),
            stickyScroll = p.getBoolean(K_STICKY_SCROLL, defaults.stickyScroll),
            pinchScalable = p.getBoolean(K_PINCH_SCALABLE, defaults.pinchScalable),
            autoComplete = p.getBoolean(K_AUTO_COMPLETE, defaults.autoComplete),
            symbolBar = p.getBoolean(K_SYMBOL_BAR, defaults.symbolBar),
            lineDivider = p.getBoolean(K_LINE_DIVIDER, defaults.lineDivider),
            currentLine = p.getBoolean(K_CURRENT_LINE, defaults.currentLine),
            blockLine = p.getBoolean(K_BLOCK_LINE, defaults.blockLine),
            lnPanel = p.getBoolean(K_LN_PANEL, defaults.lnPanel),
            ligature = p.getBoolean(K_LIGATURE, defaults.ligature),
            cursorBlink = p.getBoolean(K_CURSOR_BLINK, defaults.cursorBlink),
            textActionPopup = p.getBoolean(K_TEXT_ACTION_POPUP, defaults.textActionPopup),
            lineSpacing = p.getFloat(K_LINE_SPACING, defaults.lineSpacing),
            textScaleX = p.getFloat(K_TEXT_SCALE_X, defaults.textScaleX),
            lnPanelPosition = p.getInt(K_LN_PANEL_POSITION, defaults.lnPanelPosition),
            lineSeparator = p.getInt(K_LINE_SEPARATOR, defaults.lineSeparator),
            highlightBlock = p.getBoolean(K_HIGHLIGHT_BLOCK, defaults.highlightBlock),
            renderFunctionChars = p.getBoolean(K_RENDER_FN_CHARS, defaults.renderFunctionChars),
            showMagnifier = p.getBoolean(K_SHOW_MAGNIFIER, defaults.showMagnifier),
            largeFileMode = p.getBoolean(K_LARGE_FILE_MODE, defaults.largeFileMode),
            fileTreeMultiLine = p.getBoolean(K_FILE_TREE_MULTI_LINE, defaults.fileTreeMultiLine)
        )
    }

    fun save(context: Context, s: EditorSettings) {
        prefs(context).edit()
            .putString(K_THEME, s.theme)
            .putFloat(K_FONT_SIZE, s.fontSize)
            .putInt(K_TAB_WIDTH, s.tabWidth)
            .putString(K_FONT_FAMILY, s.fontFamily)
            .putBoolean(K_WORD_WRAP, s.wordWrap)
            .putBoolean(K_LINE_NUMBERS, s.showLineNumbers)
            .putBoolean(K_NON_PRINTABLE, s.showNonPrintable)
            .putBoolean(K_BRACKET_MATCH, s.bracketMatching)
            .putBoolean(K_STICKY_SCROLL, s.stickyScroll)
            .putBoolean(K_PINCH_SCALABLE, s.pinchScalable)
            .putBoolean(K_AUTO_COMPLETE, s.autoComplete)
            .putBoolean(K_SYMBOL_BAR, s.symbolBar)
            .putBoolean(K_LINE_DIVIDER, s.lineDivider)
            .putBoolean(K_CURRENT_LINE, s.currentLine)
            .putBoolean(K_BLOCK_LINE, s.blockLine)
            .putBoolean(K_LN_PANEL, s.lnPanel)
            .putBoolean(K_LIGATURE, s.ligature)
            .putBoolean(K_CURSOR_BLINK, s.cursorBlink)
            .putBoolean(K_TEXT_ACTION_POPUP, s.textActionPopup)
            .putFloat(K_LINE_SPACING, s.lineSpacing)
            .putFloat(K_TEXT_SCALE_X, s.textScaleX)
            .putInt(K_LN_PANEL_POSITION, s.lnPanelPosition)
            .putInt(K_LINE_SEPARATOR, s.lineSeparator)
            .putBoolean(K_HIGHLIGHT_BLOCK, s.highlightBlock)
            .putBoolean(K_RENDER_FN_CHARS, s.renderFunctionChars)
            .putBoolean(K_SHOW_MAGNIFIER, s.showMagnifier)
            .putBoolean(K_LARGE_FILE_MODE, s.largeFileMode)
            .putBoolean(K_FILE_TREE_MULTI_LINE, s.fileTreeMultiLine)
            .apply()
    }

    // ==================== 6.2.0：上次打开的文档（会话恢复） ====================

    private const val K_LAST_FILE = "last_open_file"
    private const val K_LAST_URI = "last_open_uri"

    /**
     * 记住最后一次打开的后端：[file] 为真实路径，[uri] 为 content:// 二选一（传 null 表示清掉另一个）。
     *
     * 用途：进程被系统回收后从**多任务窗口**回来，Activity 是全新的、Intent 里没有文件，
     * 原先会直接落进「未打开文件」空态，看着像内容全没了。这里记下路径供重建时恢复。
     */
    fun rememberLastOpened(context: Context, file: String?, uri: String?) {
        prefs(context).edit()
            .putString(K_LAST_FILE, file)
            .putString(K_LAST_URI, uri)
            .apply()
    }

    /** @return Pair(上次文件路径, 上次 content:// URI)，两者都可能为 null。 */
    fun lastOpened(context: Context): Pair<String?, String?> {
        val p = prefs(context)
        return p.getString(K_LAST_FILE, null) to p.getString(K_LAST_URI, null)
    }
}
