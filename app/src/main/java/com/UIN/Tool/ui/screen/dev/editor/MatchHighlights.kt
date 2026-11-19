package com.UIN.Tool.ui.screen.dev.editor

import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.HighlightTextProvider
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
 * 6.3.0 起改用 sora 0.24.7 的注册式通道：本类持有**只含搜索命中**的镜像容器，构造时注册
 * [HighlightTextProvider]，sora 需要时把镜像复制进自己的合并容器。与 [HexColorHints]
 * 各注册一个 provider，两份来源天然独立 —— 不再需要旧版的「颜色类型判归属 + 互相带载」，
 * 也不会出现一方重写容器把另一方覆盖掉的问题。
 *
 * 编辑期间**不重算**：本类在 [onContentChanged] 里用容器自带的 `updateOnInsertion` /
 * `updateOnDeletion` 平移镜像（与 sora 对合并容器的处理同口径，含撤销/重做），搜索高亮
 * 跟着内容走、不回跳。因此 Compose 侧的应用效果**刻意不键入 contentRev** —— 新查询/
 * 换页/切标签才换一批，编辑只做平移。
 *
 * 生命周期：EditorCanvas factory 创建、[com.UIN.Tool.ui.screen.dev.EditorController.detach]
 * 释放；对 editor 仅持弱引用。所有方法只在主线程调用。
 */
class MatchHighlights(editor: CodeEditor) {

    private val editorRef = WeakReference(editor)

    /** 本类独有的镜像容器（只装搜索命中）。 */
    private val highlights = HighlightTextContainer()

    /**
     * 供数回调：逐条**复制**进 sora 的合并容器。必须复制 —— sora 会在插入/删除时原地
     * 改写合并容器里的条目对象；共用引用会让镜像被重复平移，整段删除时对象从合并容器
     * 摘掉而镜像仍保留，导致幽灵高亮。
     */
    private val provider = HighlightTextProvider { target ->
        for (h in highlights.asList()) {
            target.add(
                HighlightTextContainer.HighlightText(
                    h.startLine, h.startColumn, h.endLine, h.endColumn, h.color, h.borderColor
                )
            )
        }
    }

    init {
        editor.registerHighlightTextProvider(provider)
    }

    /** 套用一组新结果（Compose 搜索状态变化时调用）。 */
    fun apply(items: List<HighlightTextContainer.HighlightText>) {
        val e = editorRef.get() ?: return
        if (e.isReleased) return
        highlights.clear()
        highlights.addAll(items)
        e.invalidateHighlightTexts()
    }

    /** 移除搜索高亮（搜索取消/切到别的标签）；hex 色块由自己的 provider 保留。 */
    fun clear() {
        if (highlights.isEmpty()) return // 没画过就不动容器，免得白白失效重绘
        highlights.clear()
        val e = editorRef.get() ?: return
        if (e.isReleased) return
        e.invalidateHighlightTexts()
    }

    /**
     * `setEditorLanguage` 内部会 `internalSetHighlightTexts(null)` 清掉合并容器（但不会
     * 动已注册的 provider），重设语言后让 sora 用镜像重新合并即可。
     */
    fun reapply() {
        if (highlights.isEmpty()) return
        val e = editorRef.get() ?: return
        if (e.isReleased) return
        e.invalidateHighlightTexts()
    }

    fun detach() {
        val e = editorRef.get()
        if (e != null && !e.isReleased) e.unregisterHighlightTextProvider(provider)
        highlights.clear()
    }

    /**
     * 批次57②复查（用户反馈「命中前打的空格也被高亮」）：sora 的 `updateOnInsertion` 对
     * 起点用**严格小于**才平移 —— 插入点恰等于条目起点时终点平移、起点不动，条目向左扩
     * 一格，把刚敲进去的字符吃进高亮。删除侧语义正确（整段覆盖摘条目、部分覆盖夹位、
     * 撞尾平移），无需逐条跟进。
     *
     * `ContentChangeEvent` 在 sora 平移合并容器**之后**派发，事件带的就是同一次插入的
     * (start,end) —— 这里先在镜像上做同口径平移，再把起点撞在插入点上的条目**起点**补到
     * 插入终点（终点已平移正确），最后重合并一次。集合**以镜像为准**：选中整段命中后
     * 直接打字是「删 + 插」两连事件，前一删已把该条从镜像摘出，这里不会再把它带回来
     * （否则会在已被删掉的位置画出幽灵高亮）。没撞上就不重合并：普通敲字只付一次镜像平移。
     */
    fun onContentChanged(
        action: Int,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int
    ) {
        if (highlights.isEmpty()) return
        val e = editorRef.get() ?: return
        if (e.isReleased) return
        when (action) {
            ContentChangeEvent.ACTION_INSERT -> {
                highlights.updateOnInsertion(startLine, startColumn, endLine, endColumn)
                var hit = false
                for (item in highlights.asList()) {
                    if (item.startLine == startLine && item.startColumn == startColumn) {
                        item.startLine = endLine
                        item.startColumn = endColumn
                        hit = true
                    }
                }
                if (hit) e.invalidateHighlightTexts()
            }
            ContentChangeEvent.ACTION_DELETE ->
                highlights.updateOnDeletion(startLine, startColumn, endLine, endColumn)
        }
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
    }
}
