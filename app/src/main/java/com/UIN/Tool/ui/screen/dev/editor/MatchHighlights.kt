package com.UIN.Tool.ui.screen.dev.editor

import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.color.EditorColor
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.lang.ref.WeakReference

/**
 * 6.2.0 批次57（用户）：**搜索结果在编辑器内高亮** —— 把全文命中的区间涂上 sora 主题的
 * `editor.findMatchBackground`（[EditorColorScheme.MATCHED_TEXT_BACKGROUND]，
 * github-dark-dimmed = `#966600`，主题未定义时回退 sora 默认黄），前景文字仍由 sora 后画、
 * 压在色块之上（同 [HexColorHints] 的绘制管线，无需注册渲染器）。
 *
 * 与 [HexColorHints] 共用 sora 唯一的 `setHighlightTexts` 通道，采用「**颜色类型判归属 +
 * 互相带载**」合并，双方任一时刻重写容器都把对方的存量条目挑回来，互不覆盖：
 *  - 本类条目背景恒为 [EditorColor]（主题键），hex 条目恒为 [ConstColor]（文本抠出的色值）；
 *  - 归并经 [mergeSorted]：两份都按 (行, 列) 排好再喂 `container.add` —— 它是二分插入，
 *    全程追加才不会退化成中段搬移的 O(n²)（十万级条目上是几十毫秒的主线程卡顿）。
 *
 * 编辑期间**不重算**：sora 自带 `updateOnInsertion/updateOnDeletion` 会把容器内的条目随
 * 插入/删除自动平移（含撤销/重做），搜索高亮跟着内容走、不回跳。因此 Compose 侧的
 * 应用效果**刻意不键入 contentRev** —— 新查询/换页/切标签才换一批，编辑只由 sora 平移。
 *
 * 生命周期：EditorCanvas factory 创建、[com.UIN.Tool.ui.screen.dev.EditorController.detach]
 * 释放；对 editor 仅持弱引用。所有方法只在主线程调用。
 */
class MatchHighlights(editor: CodeEditor) {

    private val editorRef = WeakReference(editor)

    /** 最近一次由本类写入的条目（[reapply] 用；仅主线程读写）。 */
    private var owned: List<HighlightTextContainer.HighlightText> = emptyList()

    /** 套用一组新结果（Compose 搜索状态变化时调用）。 */
    fun apply(items: List<HighlightTextContainer.HighlightText>) {
        owned = items
        write(items)
    }

    /** 移除搜索高亮、保留十六进制色块（搜索取消/切到别的标签）。 */
    fun clear() {
        if (owned.isEmpty()) return // 没画过就不动容器，免得白白失效重绘
        owned = emptyList()
        write(emptyList())
    }

    /**
     * `setEditorLanguage` 内部会 `setHighlightTexts(null)` 清掉整个容器 —— 重设语言后用
     * owned 存量原样补写（hex 部分由其 refresh 自行重建；两来源在 restoreDecorations 里
     * 成对出现，先后无关：后写的会把先写的按颜色类型带载回来）。
     */
    fun reapply() {
        if (owned.isEmpty()) return
        write(owned)
    }

    fun detach() {
        owned = emptyList()
    }

    /** 主线程：新容器 = hex 存量（ConstColor）+ 本类条目（EditorColor），归并后一次写回。 */
    private fun write(items: List<HighlightTextContainer.HighlightText>) {
        val e = editorRef.get() ?: return
        if (e.isReleased) return
        val hexCarry = e.highlightTexts?.asList()?.filter { it.color is ConstColor }.orEmpty()
        val container = HighlightTextContainer()
        container.addAll(mergeSorted(hexCarry, items))
        e.setHighlightTexts(container)
    }

    companion object {
        /** 单文件搜索高亮条数上限 —— 再多人眼也分辨不过来，也把写入/绘制成本封顶。 */
        const val MAX_ITEMS = 10_000

        /**
         * 构造一条搜索命中：单行区间、背景 = 主题 findMatch 色、无描边（border=0 →
         * sora 早退只填色块，同 [HexColorHints] 的取舍）。列越界由 sora 绘制时自行夹住。
         */
        fun matchItem(line: Int, column: Int, length: Int): HighlightTextContainer.HighlightText =
            HighlightTextContainer.HighlightText(
                line, column, line, column + length,
                EditorColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND),
                ConstColor(0)
            )

        /** (行, 列) 升序，与 sora 容器内部的 highlightComparator 同口径。 */
        private val POS_COMPARATOR =
            Comparator<HighlightTextContainer.HighlightText> { a, b ->
                if (a.startLine != b.startLine) a.startLine - b.startLine
                else if (a.startColumn != b.startColumn) a.startColumn - b.startColumn
                else if (a.endLine != b.endLine) a.endLine - b.endLine
                else a.endColumn - b.endColumn
            }

        /**
         * 归并两份（各自内部可能乱序 —— 经 sora 平移后 owned 列表顺序不再可靠）的条目为
         * 一份全局有序列表。`container.add` 是二分插入：只有输入全程递增才会退化成尾部追加。
         */
        fun mergeSorted(
            a: List<HighlightTextContainer.HighlightText>,
            b: List<HighlightTextContainer.HighlightText>
        ): List<HighlightTextContainer.HighlightText> {
            if (a.isEmpty()) return b.sortedWith(POS_COMPARATOR)
            if (b.isEmpty()) return a.sortedWith(POS_COMPARATOR)
            val x = a.sortedWith(POS_COMPARATOR)
            val y = b.sortedWith(POS_COMPARATOR)
            val out = ArrayList<HighlightTextContainer.HighlightText>(x.size + y.size)
            var i = 0
            var j = 0
            while (i < x.size && j < y.size) {
                if (POS_COMPARATOR.compare(x[i], y[j]) <= 0) out.add(x[i++]) else out.add(y[j++])
            }
            while (i < x.size) out.add(x[i++])
            while (j < y.size) out.add(y[j++])
            return out
        }
    }
}
