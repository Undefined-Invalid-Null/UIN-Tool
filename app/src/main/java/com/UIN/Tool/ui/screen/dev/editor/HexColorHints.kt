package com.UIN.Tool.ui.screen.dev.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorMotionEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.color.EditorColor
import io.github.rosemoe.sora.widget.CodeEditor
import java.lang.ref.WeakReference

/**
 * 6.2.0 批次31：十六进制颜色**背景色**（VS Code 同款 color decorator）。
 *
 * 扫描文本里的 `#RGB` / `#RRGGBB` / `#RRGGBBAA`，把**色值文本本身所在的区间**涂成对应颜色
 * （前景文字仍保留语法高亮色），而不是在旁边插入色块。
 *
 * 实现完全基于 sora 0.24.4 自带能力，不自定义控件：
 *  - 绘制：用 [HighlightTextContainer] / [HighlightTextContainer.HighlightText]，sora 会把该区间
 *    画成一个填充 + 描边的背景矩形（`computeHighlightPositions` → `highlight.getColor()`），
 *    无需注册任何渲染器（区别于 inlay hint 的 ColorInlayHintRenderer）。
 *  - 刷新：订阅 [ContentChangeEvent]，文本变化后延迟重扫（合并连续输入，避免逐键全量扫描）。
 *  - 交互：订阅 [ClickEvent]，点中色值即**复制到剪贴板并选中该区间**（6.2.0 批次40，
 *    SE 审计 T2-A）——此前只是画了背景，点上去毫无反应，是「看得见点不着」。
 *
 * 生命周期：由 [attach] 挂到 editor，随 editor 一起回收；对 editor 仅持弱引用，不阻止其释放。
 * 6.2.0 批次41：[detach] 会置起 [cancelled]，工作线程每 [CANCEL_CHECK_MASK]+1 行检查一次，
 * 编辑器一关扫描立刻中止 —— 此前扫描线程会拿着整份旧文档引用扫满 22~64 秒，把已销毁
 * 编辑器的内容钉在堆里，直接导致多任务回来后的恢复打开被内存预检误判「文件过大」。
 */
class HexColorHints(editor: CodeEditor) {

    private val editorRef = WeakReference(editor)

    // 文本变化后延迟重扫，合并连续输入，避免每次按键都全量正则扫描。
    private val rescanRunnable = Runnable { refresh() }

    // 6.2.0 批次41：detach（编辑器释放）后置真，工作线程据此中止，不再钉住旧文档。
    @Volatile private var cancelled = false

    // 6.2.0 批次39（SE 审计 T1-1）：留住订阅回执，供 [detach] 解绑 ——
    // 此前 attach() 订阅后从不解绑，随编辑器重建会持续累积回调。
    private var receipt: SubscriptionReceipt<ContentChangeEvent>? = null

    /** 6.2.0 批次40：点击色值复制。与背景扫描同样受 [detach] 管控，不遗留回调。 */
    private var clickReceipt: SubscriptionReceipt<ClickEvent>? = null

    // ---- 6.2.0 批次39（SE 审计 T3-20）：并发扫描控制 ----
    /** 是否正有扫描在跑（工作线程完成时复位）。 */
    private val scanning = java.util.concurrent.atomic.AtomicBoolean(false)
    /** 扫描在跑期间又有一次请求 → 扫完后补扫一次。 */
    @Volatile private var rerunRequested = false

    /**
     * 6.2.0 大文件省耗：扫描行数上限（超它直接清空背景、不开线程）。
     * 由 [com.UIN.Tool.ui.screen.dev.CodeEditorScreen] 按「大文件省耗」开关写入 ——
     * 关掉开关即置 [Int.MAX_VALUE]（不做省耗，全量扫描），符合「所有损耗措施可由用户关闭」。
     * 主线程写、工作线程读，故需 @Volatile。
     */
    @Volatile var maxLines: Int = MAX_LINES

    /** 挂载：订阅内容变化 + 对已有内容产出首屏背景。 */
    fun attach() {
        cancelled = false
        val editor = editorRef.get() ?: return
        // 内容增删（含 setText 换标签）后延迟重扫；不区分 action，任何变化都重扫。
        receipt?.unsubscribe()
        receipt = editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ -> scheduleRescan() }
        clickReceipt?.unsubscribe()
        clickReceipt = editor.subscribeEvent(ClickEvent::class.java) { event, _ -> handleTap(event) }
        // 首屏：attach 在 setText 之后调用，对当前内容产出背景（现在是后台线程，异步落地）。
        refresh()
    }

    /** 解绑订阅并取消挂起的重扫；随 [CodeEditor.release] 一起调用。6.2.0 批次41：同时中止在跑的扫描线程。 */
    fun detach() {
        cancelled = true
        editorRef.get()?.removeCallbacks(rescanRunnable)
        receipt?.unsubscribe()
        receipt = null
        clickReceipt?.unsubscribe()
        clickReceipt = null
    }

    /**
     * 6.2.0 批次40（T2-A）：点中色值 → 复制 + 选中。
     *
     * **刻意不用 `getWordRange()`**：`#` 在 ICU 眼里不是词字符，光标落在 `#` 上时
     * 拿到的词区间要么是空、要么只含后面的十六进制位，复制出来会缺 `#`。
     * 这里改为**复用画背景时的同一个正则**在被点的那一行上找，保证「看到的是什么、
     * 复制到的就是什么」，与背景区间严格一一对应。
     *
     * 只在文本区（[EditorMotionEvent.REGION_TEXT]）响应 —— 点行号、侧图标等区域不动作，
     * 免得与它们各自的行为抢事件。
     */
    private fun handleTap(event: ClickEvent) {
        val editor = editorRef.get() ?: return
        if (event.motionRegion != EditorMotionEvent.REGION_TEXT) return
        val text = editor.text ?: return
        val line = event.line
        if (line < 0 || line >= text.lineCount) return
        val column = event.column
        val lineText = text.getLine(line)
        for (m in HEX_COLOR_REGEX.findAll(lineText)) {
            val startCol = m.range.first
            val endCol = startCol + m.value.length
            if (column < startCol || column >= endCol) continue
            copyToClipboard(editor, m.value)
            // 顺手选中该区间：用户能立刻看见「复制的是哪一段」，误触时也能一眼发现。
            runCatching { editor.setSelectionRegion(line, startCol, line, endCol) }
            return
        }
    }

    private fun copyToClipboard(editor: CodeEditor, token: String) {
        val context = editor.context ?: return
        runCatching {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            clipboard.setPrimaryClip(ClipData.newPlainText("color", token))
            // 批次45（审计 F2）：裸 Toast 改 AppToast —— 连点多个色值时系统 Toast 会排队，
            // AppToast 走 Snackbar 且先撤当前，最新一条立即显示。
            com.UIN.Tool.utils.AppToast.info(
                context,
                Str.get(R.string.editor_color_copied, token)
            )
        }.onFailure {
            android.util.Log.w(TAG, "copy color failed: ${it.message}")
        }
    }

    private fun scheduleRescan() {
        val editor = editorRef.get() ?: return
        editor.removeCallbacks(rescanRunnable)
        // 用 postDelayedInLifecycle 而非 postDelayed：编辑器 release() 后该回调会被丢弃，
        // 否则延迟 150ms 的重扫会撞上已被置空的 styles。
        editor.postDelayedInLifecycle(rescanRunnable, RESCAN_DELAY_MS)
    }

    /**
     * 重新扫描并产出背景。必须由调用方在 **setEditorLanguage 之后**再调一次：
     * sora 的 `setEditorLanguage` 内部会 `setHighlightTexts(null)` 清掉背景，而它不派发
     * ContentChangeEvent，故语法就绪补设/手动切语法等场景不会自动恢复，需显式重建。
     *
     * 6.2.0 批次39（SE 审计 T3-20）：**修 UI 卡顿** —— 此前整段正则扫描在**主线程**同步跑完
     * 才 `setHighlightTexts`，且是在 ContentChange 后 150ms 触发，大文件上表现为
     * 「打完一行字就顿一下」。现在整段扫描放到后台线程，跑完经
     * [CodeEditor.postInLifecycle] 回主线程写入，并比对 `documentVersion`：
     * 扫描期间文档变过就丢弃结果、重排一次扫描，避免把过期位置写进编辑器。
     */
    fun refresh() {
        if (cancelled) return
        val editor = editorRef.get() ?: return
        if (editor.isReleased) return
        val text = editor.text
        if (text == null) {
            editor.setHighlightTexts(HighlightTextContainer())
            return
        }
        val lineCount = text.lineCount
        // 超上限文件不产出 hex 背景（防扫描压满线程），但**搜索高亮照带** ——
        // 它是 O(结果数) 的、与内容行数无关，大文件正是最需要看到命中的地方。
        // 判归属：搜索条目恒 EditorColor、hex 恒 ConstColor（见 MatchHighlights KDoc）。
        if (lineCount > maxLines) {
            val searchCarry =
                editor.highlightTexts?.asList()?.filter { it.color is EditorColor }.orEmpty()
            val carry = HighlightTextContainer()
            carry.addAll(MatchHighlights.mergeSorted(emptyList(), searchCarry))
            editor.setHighlightTexts(carry)
            return
        }
        // 重入保护：上一次扫描还在跑时只记一笔，扫完后再补一次，避免连开线程。
        if (!scanning.compareAndSet(false, true)) {
            rerunRequested = true
            return
        }
        rerunRequested = false
        val startVersion = text.documentVersion
        val startedAt = System.currentTimeMillis()
        val worker = Thread({
            val container = runCatching { scanLines(lineCount) }.getOrNull()
            // `scanning` 必须在**工作线程上**复位：一回到主线程（postInLifecycle）
            // 编辑器可能已被 release，那条回调会被静默丢弃，标志就再也复位不了。
            scanning.set(false)
            val e = editorRef.get()
            // 先判空再进 postInLifecycle：写成 `e?.postInLifecycle { e.isReleased … }` 的话，
            // Kotlin 不会在 lambda 体内把 e 智能转换成非空（它无法据此推断 e 已非空）。
            if (e != null) {
                e.postInLifecycle {
                    if (e.isReleased) return@postInLifecycle
                    // 过期判定放在**写入前**的主线程上，比在工作线程上判断更准确：
                    //  - current !== text：切标签会整体换掉 Content 对象（换了一个之后
                    //    旧对象的版本号就不会再变，只比版本号根本判断不出来）；
                    //  - documentVersion != startVersion：扫描期间在编辑。
                    val current = e.text
                    val invalid =
                        container == null || current !== text || text.documentVersion != startVersion
                    if (invalid) {
                        // 过期/失败 → 丢弃并重排一次。
                        e.removeCallbacks(rescanRunnable)
                        e.postDelayedInLifecycle(rescanRunnable, RESCAN_DELAY_MS)
                    } else {
                        // 批次57：与搜索高亮共用通道 —— 把旧容器里搜索方的条目（EditorColor）
                        // 归并进新容器一并写回，双方互不覆盖（判归属见 MatchHighlights KDoc）。
                        // 归并保序：container.add 是二分插入，中段插入会退化成 O(n²) 搬移。
                        val searchCarry =
                            e.highlightTexts?.asList()?.filter { it.color is EditorColor }.orEmpty()
                        val merged = HighlightTextContainer()
                        merged.addAll(MatchHighlights.mergeSorted(container.asList(), searchCarry))
                        e.setHighlightTexts(merged)
                    }
                    if (rerunRequested) {
                        rerunRequested = false
                        e.removeCallbacks(rescanRunnable)
                        e.postDelayedInLifecycle(rescanRunnable, RESCAN_DELAY_MS)
                    }
                }
            }
            android.util.Log.d(
                TAG,
                "scan took ${System.currentTimeMillis() - startedAt}ms lines=$lineCount " +
                    "result=${if (container == null) "abort" else "ok"}"
            )
        }, "hex-color-hints")
        worker.isDaemon = true
        worker.start()
    }

    /**
     * 逐行扫描，为每个色值区间添加彩色背景（前景文字不受影响）。**仅工作线程调用。**
     *
     * 行数在扫描过程中可能被编辑改变，故每轮都重新读取 `lineCount` 并夹住上界：
     * `Content.getLine()` 内部有读锁，越界会抛 IndexOutOfBounds，由 [refresh] 的
     * `runCatching` 兜住并转成「重排一次」。
     *
     * 6.2.0 批次41：**修「大文件打开后卡到没法输入 / 反复 GC / 恢复打开被判文件过大」**。
     * 设备日志实测（2026-10-17，200 万行文件）：全量正则单轮扫 22~64 秒，每秒堆里冒出
     * 约 45MB 的正则中间对象，GC 全程连轴转 —— 界面卡顿、输入无响应，堆长期顶在 400MB；
     * 且扫描线程在编辑器销毁后仍持有整份旧文档，多任务回来重建时旧内容钉在堆里，
     * 恢复打开被预检连判两次「文件过大」。故：
     *  1) 先做一次**零分配的 `#` 预筛**（[containsHash]，ContentLine.charAt 是字节直读），
     *     没有 `#` 的行直接跳过 —— 绝大多数真实文件根本进不了正则；
     *  2) 每 [CANCEL_CHECK_MASK]+1 行检查 [cancelled] / 编辑器存活，`detach()` 后立即中止；
     *  3) 单文件最多产出 [MAX_HIGHLIGHTS] 处背景即停 —— 再多人眼也分辨不出来，
     *     顺带兜住「每一行都是 # 注释」这类必然要碰正则的文件。
     *
     * @return null = 被取消/编辑器已释放（结果作废，调用方丢弃）；非空 = 正常产出
     */
    private fun scanLines(expectedLines: Int): HighlightTextContainer? {
        val container = HighlightTextContainer()
        val editor = editorRef.get() ?: return null
        if (cancelled || editor.isReleased) return null
        val text = editor.text ?: return container
        var line = 0
        var added = 0
        while (line < expectedLines && line < text.lineCount) {
            if ((line and CANCEL_CHECK_MASK) == 0) {
                if (cancelled) return null
                val e = editorRef.get()
                if (e == null || e.isReleased) return null
            }
            val lineText = text.getLine(line)
            if (containsHash(lineText)) {
                for (m in HEX_COLOR_REGEX.findAll(lineText)) {
                    val argb = parseHexColor(m.value) ?: continue
                    val startCol = m.range.first
                    // HighlightText 的 end 为**开区间**（computeHighlightPositions 要求 start < end）。
                    val endCol = startCol + m.value.length
                    container.add(
                        HighlightTextContainer.HighlightText(
                            line, startCol, line, endCol,
                            ConstColor(argb),
                            ConstColor(BORDER_ARGB)
                        )
                    )
                    added++
                    if (added >= MAX_HIGHLIGHTS) return container
                }
            }
            line++
        }
        return container
    }

    /**
     * 6.2.0 批次41：零分配预筛 —— 该行有没有可能构成色值的 `#`。
     * `ContentLine.charAt` 直接读内部 byte[]，逐字符检查不产生任何垃圾；
     * 没有 `#` 的行连正则都不用碰（200 万行实测从 22~64 秒降到亚秒级）。
     */
    private fun containsHash(line: CharSequence): Boolean {
        val n = line.length
        for (i in 0 until n) {
            if (line[i] == '#') return true
        }
        return false
    }

    companion object {
        private const val TAG = "HexColorHints"

        /** 仅当 # 后是 3/6/8 位十六进制、且前后都不是词字符时才算颜色，避免误伤 #include、#define、# 标题等。 */
        private val HEX_COLOR_REGEX =
            Regex("""(?<![0-9A-Za-z_])#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})(?![0-9A-Za-z_])""")

        private const val RESCAN_DELAY_MS = 150L
        /** 默认扫描行数上限；[maxLines] 的初值，供外部按省耗开关改写。 */
        const val MAX_LINES = 10000

        /**
         * 6.2.0 批次41：扫描中止检查间隔（行）——每 4096 行看一眼 [cancelled] / 编辑器存活。
         * 粒度足够细（毫秒级内中止），又不至于每行都读两个 volatile/弱引用。
         */
        private const val CANCEL_CHECK_MASK = 4095

        /**
         * 6.2.0 批次41：单文件色值背景处数上限，超出即停。再多人眼也分辨不过来；
         * 也顺带把「整份文件每行都是 # 注释」这类必然全量碰正则的文件的扫描量封了顶。
         */
        const val MAX_HIGHLIGHTS = 50_000

        /**
         * 背景 = **色值本身**（不透明），不与主题背景混色。sora 先按 SRC_OVER 画背景、再在其上画
         * 语法文字，故文字颜色仍由主题前景色决定、始终压在色块之上（不会被盖住）。
         * `#RRGGBBAA` 的自带 alpha（末尾）仍会保留 —— 语义上那本来就是「半透明色」。
         */
        private const val DECORATOR_ALPHA = 0xFF

        /**
         * 描边色 = 0 → `drawRowBackgroundRectWithBorder` 命中 `if (borderColor == 0) return`，
         * 只画背景色块、不描边。6.2.0 之前用 `0x6E404040` 半透明灰描边，而描边宽度取的是
         * `editor.getTextBorderWidth()`（我们设成 4dp 正文内边距），描边粗得喧宾夺主。
         * 现在只留色块本身（浅色块在浅主题上仍靠色块边缘区分，足够）。
         */
        private const val BORDER_ARGB = 0

        /**
         * 解析色值为 Android ARGB。遵循 CSS 约定：3 位补全成 6 位；8 位是 `RRGGBBAA`（alpha 在**末尾**），
         * 需转换成 Android 的 `AARRGGBB`。
         */
        private fun parseHexColor(token: String): Int? {
            val h = token.substring(1)
            return try {
                when (h.length) {
                    3 -> {
                        val r = h[0].digitToInt(16) * 17
                        val g = h[1].digitToInt(16) * 17
                        val b = h[2].digitToInt(16) * 17
                        // 0xF -> 0xFF：v * 17 == (v shl 4) or v
                        Color.argb(DECORATOR_ALPHA, r, g, b)
                    }
                    6 -> Color.argb(
                        DECORATOR_ALPHA,
                        h.substring(0, 2).toInt(16),
                        h.substring(2, 4).toInt(16),
                        h.substring(4, 6).toInt(16)
                    )
                    8 -> {
                        // 色值自带 alpha（末尾）再乘一层装饰透明度。
                        val a = h.substring(6, 8).toInt(16)
                        Color.argb(
                            DECORATOR_ALPHA * a / 255,
                            h.substring(0, 2).toInt(16),
                            h.substring(2, 4).toInt(16),
                            h.substring(4, 6).toInt(16)
                        )
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
