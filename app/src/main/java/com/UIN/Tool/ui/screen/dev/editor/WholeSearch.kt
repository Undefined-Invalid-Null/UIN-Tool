// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/WholeSearch.kt
package com.UIN.Tool.ui.screen.dev.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * 6.2.0 批次47：分页窗口模式的**全文搜索 / 全文替换**引擎。
 *
 * 整档不在内存（见 [WindowedDocument]），搜索/替换只能**流式逐页**进行：
 *  1. 每页文本由供给器给出（页号 0 起）—— ViewModel 按「内存改动页 → 磁盘 spool → 原盘」
 *     的顺序供水，替换产生的改动页先落磁盘 spool，GB 级替换内存占用恒定 = 一页；
 *  2. 向后多取一页做**重叠**：跨页界的匹配由此能被识别（计入 [ScanResult.boundary]），
 *     搜索可报告、替换**绝不盲替**（跨 2000 行页界的匹配原样留着并计数上报）；
 *  3. 匹配语义对齐 sora：正则（替换时支持 `$1` 反向引用）> 全词 > 字面量，大小写开关贯穿。
 *     sora 的页内搜索仍是常规文档的首选；本引擎只服务窗口模式的整档诉求。
 *
 * 6.2.0 批次52（提速，调研依据：ripgrep 的两大加速点是「并行分块扫描」与「消除冗余工作」；
 * 而 JVM/ART 的 String.indexOf 已是高度优化的内建实现，自写 Boyer-Moore 不能反超）：
 *  1. **每页只读一次** —— 旧实现每页被取两遍（前一轮的 next + 本轮的 cur），磁盘读与
 *     字符集解码整整翻倍；现在 next 复用为下一轮的 cur（实测路径省一半 IO/解码）；
 *  2. **并行分块** —— 页与页互相独立，页数超过 [PARALLEL_MIN_PAGES] 时把页区间切成
 *     ≤[PARALLEL_MAX_WORKERS] 个连续块交给 IO 协程并行扫（手机多核吃满，量级 ~3-4×）；
 *     各块内匹配天然有序，按块序合并即全局页序；进度用原子计数 + 单调闸门回报；
 *  3. 替换并行时**块边界页先取原文快照**（启动前顺序预读）—— 跨缝判定必须以原文为准，
 *     否则相邻块先落 spool 会把判定引向替换稿，与串行语义分叉；spool 写出经 [Mutex] 串行。
 *
 * 结果行号一律**绝对口径**（1 起），配合翻页 + 待定位光标即可跳转任意匹配。
 */
object WholeSearch {

    /** 结果收集上限：GB 级文件可能有百万匹配，面板列表只保留前 N 条带预览的行（总数照常全量统计）。 */
    const val MAX_RESULTS = 500

    /** 批次53：**有界**收集窗口的并行护栏 —— workers×窗口上限超过该值就退回串行，防有界
     *  窗口被各块冗余收集撑爆（紧凑匹配 ≈ 40 字节/条）。批次57（用户）起结果页改为一趟
     *  无界全量收集 —— 各块只收自己分块、合并恰为全局总数、无冗余，不触发本护栏（见 scan）。 */
    private const val FULL_PARALLEL_MAX_ITEMS = 1_500_000

    /** 批次52：并行阈值 —— 页数不超过它就单线程扫（小文件开协程不值当）。 */
    private const val PARALLEL_MIN_PAGES = 64

    /** 批次52：并行度上限 —— 手机大核有限，4 路已能吃满 IO/解码/CPU 重叠。 */
    private const val PARALLEL_MAX_WORKERS = 4

    /** 一处匹配（行/列均为跳转定位口径：页码 1 起、页内行 0 起、列 0 起、绝对行 1 起）。 */
    data class WholeMatch(
        val page: Int,
        val inPageLine: Int,
        val absLine: Int,
        val column: Int,
        /** 选中长度（跨行匹配已夹到本行内；0 = 只定位不选中）。 */
        val length: Int,
        /** 行内上下文预览（控制字符压成空格，单行）。 */
        val preview: String
    )

    /** 扫描结果：matches 仅在 collect=true 时收集；boundary = 跨页界匹配数（不进列表）。 */
    data class ScanResult(
        val matches: List<WholeMatch>,
        val total: Int,
        val truncated: Boolean,
        val boundary: Int,
        /** 批次53：全量结果窗口的紧凑匹配（[WholeMatch.preview] 留空；fullLimit=0 时恒空）。 */
        val full: List<WholeMatch> = emptyList()
    )

    /** 替换结果：replaced = 实际替换处数；modifiedPages = 落 spool 的页数；boundary 同上。 */
    data class ReplaceOutcome(
        val replaced: Int,
        val modifiedPages: Int,
        val boundary: Int
    )

    /** 批次52：单个并行块的扫描分量（matches 已按页序排列）。 */
    private data class RangeScan(
        val matches: List<WholeMatch>,
        val total: Int,
        val boundary: Int,
        /** 批次53：本块的全量收集分量（同样按页序；每块各收前 fullCap 条，合并后开窗）。 */
        val full: List<WholeMatch> = emptyList()
    )

    /** 批次52：单个并行块的替换分量。 */
    private data class RangeReplace(
        val replaced: Int,
        val modifiedPages: Int,
        val boundary: Int
    )

    /** 批次52：并行度 —— 小文档单线程；大文档 2..4 路（视核心数）。 */
    private fun workerCount(pageCount: Int): Int {
        if (pageCount <= PARALLEL_MIN_PAGES) return 1
        return minOf(PARALLEL_MAX_WORKERS, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
    }

    /** 编译检索式；正则非法时异常抛给调用方（UI 显示「检索式无效」）。 */
    private fun compile(
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        regex: Boolean
    ): Regex? {
        // 批次53 复查：MULTILINE 对齐 sora 原检索语义（Pattern.MULTILINE）——
        // `^`/`$` 匹配**每行**首尾而非仅整档首尾；分页本身按行对齐，页首/页尾即行首尾，
        // 分页扫描与内存整写口径一致（否则 `^foo` 只命中档首、`^` 还会在每页页首
        // 产生幻影命中）。
        val opts = mutableSetOf(RegexOption.MULTILINE).apply {
            if (!caseSensitive) add(RegexOption.IGNORE_CASE)
        }
        return when {
            regex -> Regex(query, opts)
            wholeWord -> Regex("\\b" + Regex.escape(query) + "\\b", opts)
            else -> null // 字面量走 indexOf，不编译
        }
    }

    /**
     * 流式扫描全文。
     *
     * @param pageCount 页数；[pageText] 以 0 起页号取页文本（不得返回 null —— 无改动页请回原盘）。
     *   批次52 起可能被**多协程并发调用** —— 供给器必须线程安全（VM 侧以启动快照供水）。
     * @param collect false = 只计数（如替换前的确认统计），不产列表、不做行内回溯。
     * @param onProgress (百分比 0..100, 已找到总数) —— 每页最多回调一次、百分比变化才触发。
     * @param previewLimit 面板预览列表的收集上限（默认 [MAX_RESULTS]）。
     * @param fullFrom 全量结果窗口的起点（0 起）；fullLimit=0 时不收集。
     * @param fullLimit 全量结果窗口的条数上限（紧凑匹配，预览留空按需生成）。
     *   批次57（用户）：结果页改为一趟**全量**收集 —— 传 [Int.MAX_VALUE] 即无界（各块全收、
     *   合并不截、并行护栏不触发）；有界值仅保留窗口语义。
     */
    suspend fun scan(
        pageCount: Int,
        pageText: suspend (Int) -> String,
        // 批次56：行号基址 —— 分页行数不再恒等页宽（字节上限先到即断页），绝对行
        // 必须按真实前缀和算；缺省回退旧「页号×页宽」（整读单页场景两者等价）。
        lineBase: (Int) -> Int = { it * FileAccess.WINDOW_PAGE_LINES },
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
        collect: Boolean,
        onProgress: (Int, Int) -> Unit,
        previewLimit: Int = MAX_RESULTS,
        fullFrom: Int = 0,
        fullLimit: Int = 0
    ): ScanResult {
        if (query.isEmpty() || pageCount <= 0) return ScanResult(emptyList(), 0, false, 0)
        val rx = compile(query, caseSensitive, wholeWord, regex)
        val literal = rx == null
        // 批次53：全量收集开启时，各块按「本块前 fullCap 条」收集，合并后开窗取 [from,from+limit)。
        // 批次57：护栏只对**有界**窗口生效 —— 有界时各块可能各自重复收集前 fullCap 条
        //（workers×上限放大）；无界全量收集（fullLimit=MAX）各块只收自己分块、合并总和
        // 恰为全局总数，无冗余 —— 保持并行（退单线程会让 GB 级检索从 4 路掉到 1 路）。
        val requested = workerCount(pageCount)
        val boundedWindow = fullLimit in 1 until Int.MAX_VALUE
        val workers = if (boundedWindow && requested > 1 &&
            requested.toLong() * (fullFrom.toLong() + fullLimit) > FULL_PARALLEL_MAX_ITEMS
        ) 1 else requested
        // 上限饱和到 Int.MAX_VALUE：无界收集（批次57 常态）时 fullCap 即 MAX，永不撞顶。
        val fullCap = if (fullLimit > 0)
            (fullFrom.toLong() + fullLimit.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else 0
        val parts = if (workers <= 1) {
            // 单线程路径：进度口径与旧版一致（每页最多一次、百分比变化才触发）。
            var lastPercent = -1
            var acc = 0
            listOf(
                scanRange(0, pageCount, pageCount, pageText, lineBase, query, caseSensitive, literal, rx, collect, previewLimit, fullCap) { done, found ->
                    acc += found
                    val pct = done * 100 / pageCount
                    if (pct != lastPercent) {
                        lastPercent = pct
                        onProgress(pct, acc)
                    }
                }
            )
        } else {
            val chunk = (pageCount + workers - 1) / workers
            val pagesDone = AtomicInteger(0)
            val foundSoFar = AtomicInteger(0)
            val lock = Any()
            var lastPercent = -1 // 只在 lock 内读写 —— 保证百分比单调不回跳
            coroutineScope {
                (0 until workers).map { w ->
                    val from = w * chunk
                    val to = minOf(pageCount, from + chunk)
                    if (from >= to) null
                    else async(Dispatchers.IO) {
                        scanRange(from, to, pageCount, pageText, lineBase, query, caseSensitive, literal, rx, collect, previewLimit, fullCap) { _, found ->
                            val done = pagesDone.incrementAndGet()
                            val sum = foundSoFar.addAndGet(found)
                            val pct = done * 100 / pageCount
                            synchronized(lock) {
                                if (pct != lastPercent) {
                                    lastPercent = pct
                                    onProgress(pct, sum)
                                }
                            }
                        }
                    }
                }.filterNotNull().awaitAll()
            }
        }
        // 按块序合并（awaitAll 保序）= 全局页序；每块各收前 N 条，合并后全局截断/开窗。
        val merged = ArrayList<WholeMatch>()
        for (p in parts) merged.addAll(p.matches)
        val fullMerged = ArrayList<WholeMatch>()
        if (fullLimit > 0) for (p in parts) fullMerged.addAll(p.full)
        val total = parts.sumOf { it.total }
        val result = merged.take(previewLimit)
        // 批次57：窗口覆盖全部（无界收集常态）直接复用合并结果 —— drop/take 会再复制两份，
        // 百万级列表上是白白翻三倍的瞬时堆。
        val fullResult = if (fullLimit <= 0) emptyList()
        else if (fullFrom == 0 && fullLimit >= fullMerged.size) fullMerged
        else fullMerged.drop(fullFrom).take(fullLimit)
        return ScanResult(result, total, total > result.size, parts.sumOf { it.boundary }, fullResult)
    }

    /**
     * 批次52：扫描一个连续页块 [from, to) —— 串行/并行共用的核心。
     *
     * **每页只取一次**：next 复用为下一轮的 cur（旧实现每页读两遍、解码两遍）。
     * [onPageDone] 在每页扫完后回调 (全局已完页数, 本页新命中数)。
     *
     * 批次53：同时维护两个收集窗口 —— 面板预览（前 previewLimit 条带预览）与全量紧凑
     * （前 fullCap 条 preview 留空）；行内回溯游标两者共用，只前进一次。
     */
    private suspend fun scanRange(
        from: Int,
        to: Int,
        pageCount: Int,
        pageText: suspend (Int) -> String,
        lineBase: (Int) -> Int,
        query: String,
        caseSensitive: Boolean,
        literal: Boolean,
        rx: Regex?,
        collect: Boolean,
        previewLimit: Int,
        fullCap: Int,
        onPageDone: (Int, Int) -> Unit
    ): RangeScan {
        val matches = ArrayList<WholeMatch>()
        val full = ArrayList<WholeMatch>()
        var total = 0
        var boundary = 0
        var cur = pageText(from)
        for (i in from until to) {
            currentCoroutineContext().ensureActive()
            val pageBefore = total
            val next = if (i + 1 < pageCount) pageText(i + 1) else ""
            // 页内行号回溯游标（只前进：匹配按起点有序）。
            var walkPos = 0
            var walkLine = 0
            var lineStart = 0
            fun advanceTo(pos: Int) {
                while (walkPos < pos) {
                    val c = cur[walkPos]
                    if (c == '\r') {
                        walkLine++
                        if (walkPos + 1 < cur.length && cur[walkPos + 1] == '\n') walkPos++
                        lineStart = walkPos + 1
                        walkPos++
                    } else if (c == '\n') {
                        walkLine++
                        lineStart = walkPos + 1
                        walkPos++
                    } else walkPos++
                }
            }
            // 统一口径：扩展串里「起点落在本页」的匹配都归属本页（与 replace 一致）。
            // 终点出页 = 跨页匹配，计入 total 与 boundary，但不进可点击列表（起点在页内、
            // 无法单页定位高亮），由全文替换分段完整处理。
            val extended = if (next.isEmpty()) cur else cur + next
            if (literal) {
                var idx = extended.indexOf(query, 0, !caseSensitive)
                while (idx >= 0) {
                    if (idx < cur.length) {
                        val end = idx + query.length
                        if (end > cur.length) {
                            total++; boundary++
                        } else {
                            total++
                            if (collect && (matches.size < previewLimit || full.size < fullCap)) {
                                advanceTo(idx)
                                if (matches.size < previewLimit) {
                                    matches.add(makeMatch(cur, i, walkLine, idx, lineStart, query.length, lineBase = lineBase))
                                }
                                if (full.size < fullCap) {
                                    full.add(makeMatch(cur, i, walkLine, idx, lineStart, query.length, withPreview = false, lineBase = lineBase))
                                }
                            }
                        }
                    }
                    idx = extended.indexOf(query, idx + query.length.coerceAtLeast(1), !caseSensitive)
                }
            } else {
                for (m in rx!!.findAll(extended)) {
                    val start = m.range.first
                    if (start >= cur.length) break // 后续起点都在下一页，由下一轮负责
                    val end = m.range.last + 1
                    if (end > cur.length) {
                        total++; boundary++
                        continue
                    }
                    total++
                    if (collect && (matches.size < previewLimit || full.size < fullCap)) {
                        advanceTo(start)
                        val len = inlineLength(cur, start, end)
                        if (matches.size < previewLimit) {
                            matches.add(makeMatch(cur, i, walkLine, start, lineStart, len, lineBase = lineBase))
                        }
                        if (full.size < fullCap) {
                            full.add(makeMatch(cur, i, walkLine, start, lineStart, len, withPreview = false, lineBase = lineBase))
                        }
                    }
                }
            }
            onPageDone(i + 1, total - pageBefore)
            cur = next // 批次52：next 复用为下一轮 cur —— 每页只读盘/解码一次
        }
        return RangeScan(matches, total, boundary, full)
    }

    /**
     * 流式全文替换（**跨页完整替换**）：每页读入「本页 + 下一页」，匹配按**起点落在本页**归属
     * （与扫描口径一致）。终点也∈本页的匹配整段改写；**跨缝匹配**（起点在本页、终点伸进下一页）
     * 在本页只把「本页可见部分」换成 replacement，其「下页剩余部分」记为下一页的**头部待删段**
     * （carry；同一缝最多一个跨缝匹配 —— 命中互不重叠），下一轮开头先删该段再继续。
     * 由此跨页界的匹配被**分段完整替换**（不再跳过、也不再残留半截），且绝不产出跨缝的错位改写：
     * [ReplaceOutcome.boundary] 语义 = 跨页匹配数，供 UI 展示「含 N 处跨页」。
     *
     * 批次53 修「替换失败 StringIndexOutOfBoundsException」：页边界随改写位移 —— 新页长 =
     * 删段后页长 + 净变化（delta），落盘边界取 sb[0, 旧页长 + delta)，该式在任何 replacement
     * 长度组合下都 ≤ sb.length（旧实现固定取旧页长，replacement 更短且下页为空（末页）时必然越界）。
     *
     * 批次52：页数超阈值时**并行分块**改写（与扫描同款切法）；块边界页在启动前顺序预读原文
     * 快照（跨缝判定必须看原文，见类注释 3），块缝 carry 一并用原文预计算（并行下无法顺序传递，
     * 块内缝 carry 在串行轮次里自然传递）；[onWritePage] 经内部 [Mutex] 串行 —— spool
     * 簿记（页号集合/目录懒建）保持单写者，内存占用恒定 = 每块一页。
     *
     * @param onWritePage (0 起页号 → 该页替换后全文)。每页只写一次、内容自洽；末页无下页，天然收尾。
     */
    suspend fun replace(
        pageCount: Int,
        pageText: suspend (Int) -> String,
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
        replacement: String,
        onProgress: (Int) -> Unit,
        onWritePage: suspend (Int, String) -> Unit
    ): ReplaceOutcome {
        if (query.isEmpty() || pageCount <= 0) return ReplaceOutcome(0, 0, 0)
        val rx = compile(query, caseSensitive, wholeWord, regex)
        val literal = rx == null
        val workers = workerCount(pageCount)
        if (workers <= 1) {
            var lastPercent = -1
            var done = 0
            val r = replaceRange(
                0, pageCount, pageCount, pageText, query, caseSensitive, literal, rx, replacement,
                null, onWritePage, initialCarry = 0
            ) {
                done++
                val pct = done * 100 / pageCount
                if (pct != lastPercent) {
                    lastPercent = pct
                    onProgress(pct)
                }
            }
            return ReplaceOutcome(r.replaced, r.modifiedPages, r.boundary)
        }
        val chunk = (pageCount + workers - 1) / workers
        // 块边界页（各块首页）先取原文快照 —— 相邻块并行落 spool 后这些页可能已是替换稿。
        // 批次53：同时用原文预计算块缝 (b-1, b) 的跨缝「下页头部待删段」长度（carry）——
        // 并行下块 w+1 无法等块 w 跑完再拿（顺序化会毁掉并行），只能原文预计算。
        // 块内页缝的 carry 在各块串行轮次里自然传递，无需预计算。
        val overrides = HashMap<Int, String>()
        val seamCarry = HashMap<Int, Int>()
        for (w in 1 until workers) {
            val b = w * chunk
            if (b in 1 until pageCount) {
                overrides[b] = pageText(b)
                val prev = pageText(b - 1)
                seamCarry[b] = seamCarryLen(prev, overrides[b]!!, query, caseSensitive, literal, rx)
            }
        }
        val provider: suspend (Int) -> String = { p -> overrides[p] ?: pageText(p) }
        val mutex = Mutex()
        val pagesDone = AtomicInteger(0)
        val lock = Any()
        var lastPercent = -1 // 只在 lock 内读写
        val parts = coroutineScope {
            (0 until workers).map { w ->
                val from = w * chunk
                val to = minOf(pageCount, from + chunk)
                if (from >= to) null
                else async(Dispatchers.IO) {
                    replaceRange(
                        from, to, pageCount, provider, query, caseSensitive, literal, rx, replacement,
                        mutex, onWritePage, initialCarry = seamCarry[from] ?: 0
                    ) {
                        val done = pagesDone.incrementAndGet()
                        val pct = done * 100 / pageCount
                        synchronized(lock) {
                            if (pct != lastPercent) {
                                lastPercent = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }.filterNotNull().awaitAll()
        }
        return ReplaceOutcome(
            parts.sumOf { it.replaced },
            parts.sumOf { it.modifiedPages },
            parts.sumOf { it.boundary }
        )
    }

    /**
     * 批次52：替换一个连续页块 [from, to) —— 串行/并行共用的核心。
     * [writeMutex] 非空时写出经其串行（并行模式）；串行模式传 null，零开销。
     * [initialCarry] 批次53：块首页需先删除的头部前缀长（上一块末页跨缝命中的剩余部分，
     * 并行下无法顺序传递，由 [replace] 用原文快照预计算）。
     */
    private suspend fun replaceRange(
        from: Int,
        to: Int,
        pageCount: Int,
        pageText: suspend (Int) -> String,
        query: String,
        caseSensitive: Boolean,
        literal: Boolean,
        rx: Regex?,
        replacement: String,
        writeMutex: Mutex?,
        onWritePage: suspend (Int, String) -> Unit,
        initialCarry: Int,
        onPageDone: () -> Unit
    ): RangeReplace {
        var replaced = 0
        var pages = 0
        var boundary = 0
        // 批次53：跨缝接续 —— 上一页跨缝命中的「下页剩余部分」位于本页头部，长度 carry。
        var carry = initialCarry
        var cur = pageText(from)
        for (i in from until to) {
            currentCoroutineContext().ensureActive()
            val next = if (i + 1 < pageCount) pageText(i + 1) else ""
            val raw = cur
            if (carry > 0) {
                // carry ≤ 原文长（跨缝匹配终点不会超过 extended 末尾）；防御性 clamp 兜底。
                cur = if (carry >= raw.length) "" else raw.substring(carry.coerceAtMost(raw.length))
                carry = 0
            }
            // extended 供跨缝匹配被发现；改写时只作用于落在本页内的部分。
            val extended = if (next.isEmpty()) cur else cur + next
            // 收集（起点∈本页）的匹配 (start, endInExtended)。
            val hits = collectHits(extended, cur.length, query, caseSensitive, literal, rx)
            var newCur = cur
            if (hits.isNotEmpty()) {
                // 从右往左改写（高位先改，低位区间不受位移影响）。
                val sb = StringBuilder(extended)
                var delta = 0 // 本页全部改写带来的净长度变化（replacement 与 query 不等长时非 0）
                var cross = 0
                for (k in hits.indices.reversed()) {
                    val (s, e) = hits[k]
                    val isCross = e > cur.length
                    // 跨缝匹配只替换到本页边界；剩余部分（下页头部前缀）由下一轮开头删除。
                    val writeEnd = if (isCross) cur.length else e
                    // 反向引用在**原文 extended** 里展开 —— sb 已从右往左改写过，
                    // 在 sb 上重查会让回溯断言看到改写后的上下文（取错捕获组/定位漂移）。
                    val rep = expandReplacement(rx, replacement, s, extended)
                    sb.replace(s, writeEnd, rep)
                    delta += rep.length - (writeEnd - s)
                    if (isCross) {
                        cross++
                        // 同缝最多一个跨缝匹配（命中互不重叠 ⇒ 第二个起点 ≥ 前一个终点 >
                        // 本页长）；maxOf 仅为防御性兜底。
                        carry = maxOf(carry, e - cur.length)
                    }
                }
                // 本页边界随改写位移：新边界 = 本页长 + 净变化。sb = 本页(+下页原文) + 净变化
                // ⇒ 0 ≤ 新边界 ≤ sb.length 恒成立。修「替换失败 StringIndexOutOfBounds」：
                // 旧实现固定取 cur.length，replacement 比 query 短且下页为空（末页）/
                // 缩短量超过下页长时必然越界。
                newCur = sb.substring(0, cur.length + delta)
                replaced += hits.size
                boundary += cross
            }
            // 与**原文**比较决定是否落盘：跨缝删除也会改写本页（哪怕本轮没有新命中）。
            if (newCur != raw) {
                if (writeMutex != null) writeMutex.withLock { onWritePage(i, newCur) }
                else onWritePage(i, newCur)
                pages++
            }
            onPageDone()
            cur = next // 批次52：next 复用为下一轮 cur —— 每页只读盘/解码一次
        }
        return RangeReplace(replaced, pages, boundary)
    }

    /**
     * 收集 extended 中**起点落在前 [curLen] 字符内**的匹配 (start, end)（replace/块缝预计算
     * 共用；scan 因需行号簿记单独实现，口径一致）。end 可超过 [curLen]（跨缝匹配）。
     */
    private fun collectHits(
        extended: String,
        curLen: Int,
        query: String,
        caseSensitive: Boolean,
        literal: Boolean,
        rx: Regex?
    ): ArrayList<Pair<Int, Int>> {
        val hits = ArrayList<Pair<Int, Int>>()
        if (literal) {
            var idx = extended.indexOf(query, 0, !caseSensitive)
            while (idx >= 0) {
                if (idx < curLen) hits.add(idx to (idx + query.length))
                idx = extended.indexOf(query, idx + query.length.coerceAtLeast(1), !caseSensitive)
            }
        } else {
            for (m in rx!!.findAll(extended)) {
                val start = m.range.first
                if (start >= curLen) break // 起点在下页，下一轮负责
                hits.add(start to (m.range.last + 1))
            }
        }
        return hits
    }

    /**
     * 批次53：并行块缝预计算 —— 原文 (prev, cur) 接缝处跨缝命中的「cur 页头部待删前缀长」，
     * 与 [replaceRange] 内部同口径（[collectHits]）；无跨缝匹配时为 0。
     */
    private fun seamCarryLen(
        prev: String, cur: String, query: String,
        caseSensitive: Boolean, literal: Boolean, rx: Regex?
    ): Int {
        if (prev.isEmpty() || cur.isEmpty()) return 0
        val hits = collectHits(prev + cur, prev.length, query, caseSensitive, literal, rx)
        var cut = 0
        for ((_, e) in hits) if (e > prev.length) cut = maxOf(cut, e - prev.length)
        return cut
    }

    /**
     * 展开替换文本里的 `$1`/`${1}` 反向引用（仅正则模式）。字面量模式直接回显 [replacement]。
     *
     * 以**原文 [extended]** 重新定位匹配：[replaceRange] 从右往左改写，[sb] 里已有部分
     * 区间被改写过 —— 在 sb 上重查会让回溯断言（`\b`、lookbehind、`$`）看到改写后的
     * 上下文，可能取错捕获组甚至定位漂移。[startInExtended] 起 sb 与 extended 的该匹配
     * 文本相同（改写都在更高位），故原文定位等价且上下文完整。
     *
     * 组索引越界（如 `$9` 而只有 3 个组）、组未参与匹配、`$` 后不是数字 —— 一律按字面量
     * 回显（绝不抛错、绝不写坏文档；修「`$9` 越界 IndexOutOfBounds 中断窗口替换」）。
     */
    private fun expandReplacement(
        rx: Regex?,
        replacement: String,
        startInExtended: Int,
        extended: String
    ): String {
        if (rx == null) return replacement
        val m = rx.find(extended, startInExtended) ?: return replacement
        return replacement.replace(BACKREF) { dm ->
            val g = dm.groupValues
            val idxStr = if (g[1].isNotEmpty()) g[1] else g[2]
            val idx = idxStr.toIntOrNull()
            // 组未参与匹配（m.groups[idx] == null）也走字面量回显 —— 与 KDoc/CHANGELOG
            // 的承诺一致（orEmpty 会把 `$1` 静默换成空串，用户看不到自己写了什么）。
            if (idx == null || idx < 0 || idx >= m.groups.size) dm.value
            else m.groups[idx]?.value ?: dm.value
        }
    }

    /**
     * 批次53：非窗口（完整加载）文档的**整档内存替换** —— 与流式替换同一套语义
     *（compile 编译 + `$1` 反向引用展开；字面量经 Regex.escape 走同一引擎，与扫描的
     * indexOf 口径一致）。整串单遍处理，只多产出一份新串（峰值约 2× 内容 —— 仅用于
     * 本就已整读进内存的文档，确认弹窗对大文件有内存提示）。
     *
     * @return 新全文 to 替换数；query 为空返回 null。正则非法异常抛给调用方。
     */
    fun replaceInString(
        text: String, query: String,
        caseSensitive: Boolean, wholeWord: Boolean, regex: Boolean, replacement: String
    ): Pair<String, Int>? {
        if (query.isEmpty()) return null
        val rx = compile(query, caseSensitive, wholeWord, regex)
            ?: Regex(Regex.escape(query), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        var count = 0
        val out = rx.replace(text) { m ->
            count++
            replacement.replace(BACKREF) { dm ->
                val g = dm.groupValues
                val idxStr = if (g[1].isNotEmpty()) g[1] else g[2]
                val idx = idxStr.toIntOrNull()
                if (idx == null || idx < 0 || idx >= m.groups.size) dm.value
                else m.groups[idx]?.value ?: dm.value // 未参与的组字面量回显，口径同 expandReplacement
            }
        }
        return out to count
    }

    // `$1` 或 `${1}`（与窗口流式替换共用同一展开口径，见 expandReplacement）。
    private val BACKREF = Regex("""\$(\d+)|\$\{(\d+)\}""")

    /** 跨行匹配夹到本行内（选中高亮不能跨行；计数仍按完整匹配）。 */
    private fun inlineLength(cur: String, start: Int, end: Int): Int {
        var e = start
        while (e < end && cur[e] != '\n' && cur[e] != '\r') e++
        return e - start
    }

    /** 生成结果项：行内预览 = 匹配前后各 ≤24 字符，控制字符压成空格，截断侧补省略号。
     *  批次53：[withPreview]=false 时预览留空（全量窗口的紧凑项，预览由结果页按需 [previewFor] 生成）。 */
    private fun makeMatch(
        cur: String,
        page0: Int,
        inPageLine: Int,
        start: Int,
        lineStart: Int,
        length: Int,
        withPreview: Boolean = true,
        lineBase: (Int) -> Int
    ): WholeMatch {
        return WholeMatch(
            page = page0 + 1,
            inPageLine = inPageLine,
            // 批次56：绝对行按真实页基址（分页行数不等后不能再用 页号×页宽）。
            absLine = lineBase(page0) + inPageLine + 1,
            column = start - lineStart,
            length = length,
            preview = if (withPreview) buildPreview(cur, lineStart, start, length) else ""
        )
    }

    /**
     * 批次53：按「页内行 0 起 + 列 0 起 + 长度」就地生成一条行内预览 —— 全量结果页对
     * 紧凑项按可见范围懒加载（供给器给的是搜索时点的页快照，时点差由夹取兜底）。
     *
     * 防御式：行号超出文本行数、列越界一律返回空串/夹回行内，绝不抛错。
     */
    fun previewFor(text: String, inPageLine: Int, column: Int, length: Int): String {
        if (text.isEmpty() || inPageLine < 0 || column < 0) return ""
        // 走到第 inPageLine 行的行首。
        var line = 0
        var lineStart = 0
        var i = 0
        while (line < inPageLine && i < text.length) {
            val c = text[i]
            if (c == '\r') {
                line++
                if (i + 1 < text.length && text[i + 1] == '\n') i++
                lineStart = i + 1
                i++
            } else if (c == '\n') {
                line++
                lineStart = i + 1
                i++
            } else i++
        }
        if (line < inPageLine) return "" // 文档已变小，行不存在
        var lineEnd = lineStart
        while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
        val start = (lineStart + column).coerceAtMost(lineEnd)
        val len = length.coerceIn(0, lineEnd - start)
        return buildPreview(text, lineStart, start, len)
    }

    /** 行内预览的共用构建（匹配前后各 ≤24 字符，控制字符压空格，截断侧补省略号）。 */
    private fun buildPreview(cur: String, lineStart: Int, start: Int, length: Int): String {
        val pre = cur.substring(lineStart, start).takeLast(24)
        var postEnd = start + length
        val postCap = postEnd + 24
        while (postEnd < cur.length && postEnd < postCap &&
            cur[postEnd] != '\n' && cur[postEnd] != '\r'
        ) postEnd++
        val post = cur.substring(start + length, postEnd)
        val matched = cur.substring(start, start + length)
        return buildString {
            if (lineStart < start - pre.length) append('…')
            append(pre.replace(CONTROL, " "))
            append(matched.replace(CONTROL, " "))
            append(post.replace(CONTROL, " "))
            if (postEnd < start + length + 24 && postEnd < cur.length) append('…')
        }
    }

    private val CONTROL = Regex("""[\p{Cntrl}&&[^\t]]""")
}
