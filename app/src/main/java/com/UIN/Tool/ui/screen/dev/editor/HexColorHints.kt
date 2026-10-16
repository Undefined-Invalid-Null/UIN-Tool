package com.UIN.Tool.ui.screen.dev.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.widget.Toast
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorMotionEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
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
 */
class HexColorHints(editor: CodeEditor) {

    private val editorRef = WeakReference(editor)

    // 文本变化后延迟重扫，合并连续输入，避免每次按键都全量正则扫描。
    private val rescanRunnable = Runnable { refresh() }

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

    /** 挂载：订阅内容变化 + 对已有内容产出首屏背景。 */
    fun attach() {
        val editor = editorRef.get() ?: return
        // 内容增删（含 setText 换标签）后延迟重扫；不区分 action，任何变化都重扫。
        receipt?.unsubscribe()
        receipt = editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ -> scheduleRescan() }
        clickReceipt?.unsubscribe()
        clickReceipt = editor.subscribeEvent(ClickEvent::class.java) { event, _ -> handleTap(event) }
        // 首屏：attach 在 setText 之后调用，对当前内容产出背景（现在是后台线程，异步落地）。
        refresh()
    }

    /** 解绑订阅并取消挂起的重扫；随 [CodeEditor.release] 一起调用。 */
    fun detach() {
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
            Toast.makeText(
                context,
                Str.get(R.string.editor_color_copied, token),
                Toast.LENGTH_SHORT
            ).show()
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
        val editor = editorRef.get() ?: return
        if (editor.isReleased) return
        val text = editor.text
        if (text == null) {
            editor.setHighlightTexts(HighlightTextContainer())
            return
        }
        val lineCount = text.lineCount
        // 超大文件不产出背景（返回空容器以清除旧背景），防止扫描把线程压满。
        if (lineCount > MAX_LINES) {
            editor.setHighlightTexts(HighlightTextContainer())
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
                        e.setHighlightTexts(container)
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
                "scan took ${System.currentTimeMillis() - startedAt}ms lines=$lineCount"
            )
        }, "hex-color-hints")
        worker.isDaemon = true
        worker.start()
    }

    /**
     * 逐行正则扫描，为每个色值区间添加彩色背景（前景文字不受影响）。**仅工作线程调用。**
     *
     * 行数在扫描过程中可能被编辑改变，故每轮都重新读取 `lineCount` 并夹住上界：
     * `Content.getLine()` 内部有读锁，越界会抛 IndexOutOfBounds，由 [refresh] 的
     * `runCatching` 兜住并转成「重排一次」。
     */
    private fun scanLines(expectedLines: Int): HighlightTextContainer {
        val container = HighlightTextContainer()
        val text = editorRef.get()?.text ?: return container
        var line = 0
        while (line < expectedLines && line < text.lineCount) {
            val lineText = text.getLine(line)
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
            }
            line++
        }
        return container
    }

    companion object {
        private const val TAG = "HexColorHints"

        /** 仅当 # 后是 3/6/8 位十六进制、且前后都不是词字符时才算颜色，避免误伤 #include、#define、# 标题等。 */
        private val HEX_COLOR_REGEX =
            Regex("""(?<![0-9A-Za-z_])#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})(?![0-9A-Za-z_])""")

        private const val RESCAN_DELAY_MS = 150L
        private const val MAX_LINES = 10000

        /**
         * 背景 = **色值本身**（不透明），不与主题背景混色。sora 先按 SRC_OVER 画背景、再在其上画
         * 语法文字，故文字颜色仍由主题前景色决定、始终压在色块之上（不会被盖住）。
         * `#RRGGBBAA` 的自带 alpha（末尾）仍会保留 —— 语义上那本来就是「半透明色」。
         */
        private const val DECORATOR_ALPHA = 0xFF

        /** 半透明灰描边：浅色块在浅色主题上也能看出边界，深色主题下不影响观感。 */
        private const val BORDER_ARGB = 0x6E404040

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
