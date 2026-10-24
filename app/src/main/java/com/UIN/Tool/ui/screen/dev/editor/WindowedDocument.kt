// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/WindowedDocument.kt
package com.UIN.Tool.ui.screen.dev.editor

import com.UIN.Tool.utils.AppLog
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * 6.2.0 批次46：**窗口模式**的磁盘文档 —— EmEditor「磁盘溢写 + 分区窗口」的 Android 缩配。
 *
 * 背景：内存预检失败的超大文件此前只能报「文件过大」。窗口模式给它们一条活路：
 *  1. **一遍字节级流式扫描**建立「每页首字节偏移 + 总行数」的小索引（10GB 文件约几十 KB），
 *     索引只驻留偏移数组，**不把正文装进堆**；
 *  2. 编辑器每次只载入**当前页**（[com.UIN.Tool.ui.screen.dev.editor.FileAccess.WINDOW_PAGE_LINES]
 *     行）文本，翻页时换页、改动进 overlays（spool）；
 *  3. 保存时把原文件逐页流式写出（未改动页**原样字节拷贝**、改动页按当前编码重编码），
 *     写到同目录临时文件后原子替换 —— 与 EmEditor「保存时整份重写回一个文件」同构。
 *
 * 为什么是字节扫描而不是解码扫描：白名单编码（UTF-8/GB18030/GBK/Big5/SJIS/EUC/ISO-8859/
 * windows-125x/UTF-16/UTF-32 …）的行终止符 0x0A/0x0D **不可能**出现在多字节序列的
 * 非首字节里（各编码的尾字节范围都不含 0x0A/0x0D），UTF-16/32 按 2/4 字节单元对齐判定 ——
 * 于是无需解码即可数出行界并拿到精确字节偏移。不在白名单的编码（如 ISO-2022-JP）
 * 不做此假设，[build] 直接返回 null，调用方回落原「文件过大」行为，绝不错切。
 *
 * 行计数口径与 sora 一致：LF、CR、CRLF 都各算**一个**行界；文件末尾无行界时补最后一行。
 */
class WindowedDocument(
    /** 批次55：改为可变 —— 文件树重命名时与 tab.file 同步迁移，否则保存仍写旧路径（复活旧文件）。 */
    var file: File,
    /** 解码/编码本页文本所用编码（= 打开时实际采用的编码）。 */
    val charset: Charset,
    /** 每页行数上限（= [FileAccess.WINDOW_PAGE_LINES]；批次56 起字节上限先到即提前断页，实际每页行数不等）。 */
    val pageSize: Int,
    /** [i] = 第 i 页（0 起）首字节绝对位置；末页终点另见 [pageEnd]。 */
    private val pageOffsets: LongArray,
    /** 批次56：[i] = 第 i 页的**行数**（保存快速重建校验与总行数算术用；与 pageOffsets 等长）。 */
    private val pageLines: IntArray,
    /** 全文行数（字节级扫描所得）。 */
    val totalLines: Int,
    private val fileLength: Long
) {
    /** 页数 = 偏移条目数（每条即一页的起点；末页终点 = 文件长度）。 */
    val totalPages: Int get() = pageOffsets.size

    /**
     * 批次56：行前缀和（[i] = 前 i 页的行数总和 = 第 i 页首行的 0 基绝对行）。
     * 分页行数不再恒等 [pageSize]（1MB 字节上限先到即断页）—— 行号↔页码换算
     * 一律走 [lineBase] / [pageForLine]，不得再用「页号 × 页宽」。惰性建一次，线程安全。
     */
    private val lineBasePrefix: IntArray by lazy {
        val acc = IntArray(pageLines.size + 1)
        for (i in pageLines.indices) acc[i + 1] = acc[i] + pageLines[i]
        acc
    }

    /** 批次56：第 [page0] 页首行的 0 基绝对行基址（O(1)；越界夹到边界）。 */
    fun lineBase(page0: Int): Int {
        val p = page0.coerceIn(0, totalPages)
        return lineBasePrefix[p]
    }

    /**
     * 批次56：1 基绝对行 → 1 基页号（二分，O(log 页数)）。行号超出全文时夹到末页；
     * 空档（空文件）恒返回 1。
     */
    fun pageForLine(absLine: Int): Int {
        val prefix = lineBasePrefix
        if (totalPages <= 1) return 1
        val target = absLine.coerceAtLeast(1)
        var lo = 0
        var hi = totalPages - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (prefix[mid] < target) lo = mid else hi = mid - 1
        }
        return lo + 1
    }

    /**
     * 批次53：一次 [writeWithOverlays] 的**簿记** —— 只记录改动页的算术特征，
     * 供 [reindexAfterWrite] 免整档重扫地平移索引（2GB/16858 页实测省 ~26s）。
     *
     * @param pages 改动页号（0 起，升序）。
     * @param deltas 与 [pages] 逐项对应：新字节数 − 旧字节数。
     * @param midValid 每个**非末尾**改动页都「行界数 == 该页原行数 且以 LF 收尾」——
     *   页仍行数不变、仍终止于行首，其后所有页偏移只做平移即可；
     *   否则（行数变化 / 以 CR 或半截多字节收尾）后续页起点失去「行首」语义 → 必须全量重建。
     * @param lastChanged 末页（最后一页）是否改动。
     * @param lastBoundaries 末页若改动：其新字节的行界数；未改动为 0（配合 [lastChanged] 区分）。
     *   新总行数 = newLength==0 ? 0 : lastBoundaries + 1（口径与 [build] 一致：末尾空行也算一行）。
     */
    data class WriteResult(
        val pages: IntArray,
        val deltas: LongArray,
        val midValid: Boolean,
        val lastChanged: Boolean,
        val lastBoundaries: Int
    )

    /** 第 [index] 页（0 起）的字节终点 = 下一页起点，末页 = 文件长度。 */
    fun pageEnd(index: Int): Long =
        if (index + 1 < pageOffsets.size) pageOffsets[index + 1] else fileLength

    /**
     * 读出第 [index] 页（0 起）的文本。
     * 页边界总是**行首**、因而总在字符边界上，按页整段解码不会切断多字节字符。
     */
    fun readPage(index: Int): String {
        if (index !in 0 until totalPages) return ""
        val start = pageOffsets[index]
        val end = pageEnd(index).coerceAtMost(fileLength)
        if (end <= start) return ""
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val size = end - start
            if (size > Int.MAX_VALUE) return "" // 单页超 2GB 属病态文件，宁空勿 OOM
            val buf = ByteArray(size.toInt())
            raf.readFully(buf)
            return String(buf, charset)
        }
    }

    /**
     * 6.2.0 批次46/47 写回：原文件 + 改动页流式拼接成新文件。
     * [overlayFor] = 页号（0 起）→ 改动后全文；返回 null = 该页未改动、走**原样字节拷贝**
     * （不经过解码，外部字节原封不动）。供给器可同时覆盖内存改动页与磁盘 spool
     * （批次47 全文替换的改动页落在磁盘 spool，GB 级替换不占堆）。
     * 先写同目录隐藏临时文件 `.name.uin_tmp`，成功后原子替换；失败删除临时文件、原文件不动。
     *
     * @param onProgress 批次51：写盘进度（0..100，按页数折算，变化才回调）—— GB 级写盘
     *   需数十秒，调用方据此在底部状态栏显示「保存中… N%」，否则用户全程无反馈会连点保存。
     * @return 批次53：成功返回簿记 [WriteResult]（失败 null）—— 调用方凭它走
     *   [reindexAfterWrite] 快速重建索引，免去保存后 ~26s 的整档重扫；**不要**在成功时返回 Boolean 语义。
     */
    fun writeWithOverlays(
        overlayFor: (Int) -> String?,
        onProgress: ((Int) -> Unit)? = null
    ): WriteResult? {
        val tmp = File(file.parentFile, ".${file.name}.uin_tmp")
        val changedPages = ArrayList<Int>()
        val deltas = ArrayList<Long>()
        var midValid = true
        var lastChanged = false
        var lastBoundaries = 0
        try {
            tmp.delete()
            RandomAccessFile(file, "r").use { raf ->
                FileOutputStream(tmp).use { out ->
                    // 批次53：拷贝缓冲 64KB → 1MB —— GB 级保存的 syscall/循环次数降一个量级。
                    val buf = ByteArray(WRITE_BUF)
                    val unit = unitSize(charset)
                    val littleEndian = detectLittleEndian(raf, charset)
                    var lastPercent = -1
                    for (i in 0 until totalPages) {
                        val overlay = overlayFor(i)
                        if (overlay != null) {
                            val bytes = overlay.toByteArray(charset)
                            out.write(bytes)
                            // 批次53：簿记 —— 字节增量 + 行界特征（快速重建索引的全部依据）。
                            val oldLen = pageEnd(i).coerceAtMost(fileLength) - pageOffsets[i]
                            changedPages.add(i)
                            deltas.add(bytes.size.toLong() - oldLen)
                            val (bounds, endsLf) = countBoundaries(bytes, unit, littleEndian)
                            if (i == totalPages - 1) {
                                lastChanged = true
                                lastBoundaries = bounds
                            } else if (bounds != pageLines[i] || !endsLf) {
                                // 批次56：每页行数不再恒为 pageSize（1MB 字节上限先到即断页）
                                // —— 与该页**原行数**比对；行数变化/非行首收尾都回退全量重建。
                                midValid = false
                            }
                        } else {
                            val start = pageOffsets[i]
                            val end = pageEnd(i).coerceAtMost(fileLength)
                            raf.seek(start)
                            var remaining = end - start
                            while (remaining > 0) {
                                val n = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                remaining -= n
                            }
                        }
                        if (onProgress != null) {
                            val pct = ((i + 1) * 100 / totalPages)
                            if (pct != lastPercent) {
                                lastPercent = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(file)) {
                // 极少数文件系统不支持覆盖式改名：回退为拷贝覆盖（仍先有完整临时文件）。
                tmp.inputStream().use { input ->
                    FileOutputStream(file).use { input.copyTo(it) }
                }
                tmp.delete()
            }
            return WriteResult(
                changedPages.toIntArray(),
                deltas.toLongArray(),
                midValid,
                lastChanged,
                lastBoundaries
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "writeWithOverlays failed: ${file.absolutePath}", e)
            tmp.delete()
            return null
        } catch (e: OutOfMemoryError) {
            AppLog.e(TAG, "writeWithOverlays OOM: ${file.absolutePath}")
            tmp.delete()
            return null
        }
    }

    /**
     * 批次53：写回成功后的**快速索引重建** —— 用 [WriteResult] 的簿记做一遍 O(页数) 前缀和平移，
     * 免去整档字节重扫（2GB/16858 页实测 ~26s → 毫秒级）。
     *
     * 快速路径成立的条件（任一不满足返回 null，调用方回落 [build] 全量重建，绝不产错索引）：
     *  1. 每个非末尾改动页 [WriteResult.midValid]：新行界数 == 该页原行数 且以 LF 收尾 ——
     *     该页行数不变、仍终止于行首，其后所有页偏移只受字节增量平移；
     *  2. 新文件长度 ≥ 0（防御，理论恒成立）。
     *
     * 新偏移[i] = 旧偏移[i] + 页 i 之前所有改动页的增量之和；总行数只可能因**末页**变化
     * （非末页行数逐页守恒）：newLines = newLength==0 ? 0 : lastBoundaries + 1。
     */
    fun reindexAfterWrite(result: WriteResult): WindowedDocument? {
        val n = totalPages
        if (result.pages.size != result.deltas.size) return null
        if (result.pages.isEmpty()) return this // 无改动页：文件逐字节等价，索引原样可用
        if (!result.midValid) return null
        val changed = HashMap<Int, Long>(result.pages.size * 2)
        for (k in result.pages.indices) changed[result.pages[k]] = result.deltas[k]
        val newOffsets = LongArray(n)
        var shift = 0L
        for (i in 0 until n) {
            newOffsets[i] = pageOffsets[i] + shift
            shift += changed[i] ?: 0L
        }
        val newLength = fileLength + shift
        if (newLength < 0) return null
        val newTotalLines = if (result.lastChanged) {
            if (newLength == 0L) 0
            else {
                // 批次56：页行数不再恒等 pageSize（1MB 字节上限先到即断页）——
                // 末页旧行数 = 总行数 − 前 n-1 页的**实际**行数之和。
                var oldLastLines = totalLines.toLong()
                for (k in 0 until n - 1) oldLastLines -= pageLines[k]
                if (oldLastLines < 0) return null
                // 两支必须同为 Int：末页旧行数(Long 差值)扣除后加新末页行数，收敛回 Int 行数口径。
                (totalLines - oldLastLines + result.lastBoundaries + 1L).toInt()
            }
        } else totalLines
        AppLog.d(
            TAG,
            "reindex fast path: pages=$n lines=$newTotalLines len=$newLength " +
                "changed=${result.pages.size} lastChanged=${result.lastChanged}"
        )
        // 批次56 复查修：末页行数已随重写变化 —— 同步进新数组（lineBase/前缀和语义要求
        // pageLines[i] 恒为该页**当前**行数，复用旧数组会让 lineBase(totalPages) 拿到陈旧值）；
        // 空档（保存后文件为空）退化为 [0]，与 totalLines=0 对齐。
        val newPageLines = when {
            newLength == 0L -> intArrayOf(0)
            result.lastChanged ->
                pageLines.copyOf().also { it[n - 1] = result.lastBoundaries + 1 }
            else -> pageLines
        }
        return WindowedDocument(
            file = file,
            charset = charset,
            pageSize = pageSize,
            pageOffsets = newOffsets,
            pageLines = newPageLines,
            totalLines = newTotalLines,
            fileLength = newLength
        )
    }

    companion object {
        private const val TAG = "WindowedDoc"

        /** 流式扫描的读缓冲（1MB —— 批次50 由 256KB 上调，减少 GB 级文件的 FUSE syscall 次数）。 */
        private const val SCAN_BUF = 1024 * 1024

        /** 批次53：写回的拷贝缓冲（64KB → 1MB —— GB 级保存的读循环/ syscall 次数降一个量级）。 */
        private const val WRITE_BUF = 1024 * 1024

        /** 批次50：快速路径用的行界字节常量（单字节编码 = ASCII 兼容，CR/LF 即 0x0D/0x0A）。 */
        private const val CR: Byte = 0x0D
        private const val LF: Byte = 0x0A

        /**
         * 字节级扫描**安全**的编码白名单 —— 判据：行终止符 0x0A/0x0D 不可能落在
         * 多字节序列的非首字节（尾字节范围不含 0x0A/0x0D），UTF-16/32 按对齐单元判定。
         * 不在名单（如 ISO-2022-JP、未知自定义编码）→ [build] 返回 null，回落「文件过大」。
         */
        private val BYTE_SCAN_SAFE = setOf(
            "UTF-8", "GB18030", "GBK", "GB2312", "Big5", "Big5-HKSCS",
            "Shift_JIS", "EUC-JP", "EUC-KR", "KOI8-R", "TIS-620", "US-ASCII",
            "ISO-8859-1", "ISO-8859-2", "ISO-8859-15",
            "windows-1250", "windows-1251", "windows-1252",
            "UTF-16", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE"
        )

        /**
         * 流式建立页索引。整趟只做 IO + 整数扫描，堆占用 = 一个 1MB 缓冲 + 偏移数组。
         *
         * @param pageSize 每页行数；必须 > 0。
         * @param onProgress 可选进度回调（0..100，变化才触发）—— GB 级扫描在手机上需数十秒，
         *   调用方据此显示「正在建立索引… N%」，避免用户以为卡死/失败。
         * @return 索引；编码不在白名单 / IO 失败 / 参数非法时返回 null（调用方报「文件过大」）。
         */
        fun build(
            file: File,
            charset: Charset,
            pageSize: Int,
            onProgress: ((Int) -> Unit)? = null
        ): WindowedDocument? {
            if (pageSize <= 0 || !file.isFile) return null
            val unit = unitSize(charset)
            if (unit == 0) {
                AppLog.w(TAG, "build: charset not byte-scan safe: ${charset.name()}")
                return null
            }
            return try {
                val offsets = ArrayList<Long>(16)
                offsets.add(0L)
                // 批次56：与 offsets 一一对应的每页行数 + 分页累计器（行数/字节先到者断页）。
                val pageLines = ArrayList<Int>(16)
                var lineCount = 0
                var curPageLines = 0
                var pageBytes = 0L
                var skipLf = false      // 上一单元是 CR，遇 LF 吞掉（CRLF 只算一个行界）
                var pendingRec = false  // 刚越过页界行界，等下一行首单元落偏移
                RandomAccessFile(file, "r").use { raf ->
                    // UTF-16/32 需要按端序判单元；通用 UTF-16 档由 BOM 决定（无 BOM 按
                    // Java 的大端默认，与 String(bytes, UTF-16) 解码口径一致）。
                    val littleEndian = detectLittleEndian(raf, charset)
                    val buf = ByteArray(SCAN_BUF)
                    var carry = ByteArray(0)   // UTF-16/32 对齐残余（< unit 字节）
                    var filePos = 0L           // 已读到的文件位置
                    val totalBytes = file.length()
                    var lastPercent = -1
                    while (true) {
                        val n = raf.read(buf)
                        if (n < 0) break
                        val chunkStart = filePos
                        filePos += n
                        if (unit == 1) {
                            // 批次50 快速路径：单字节编码（ASCII 兼容占绝大多数）逐字节**内联**
                            // 判定 —— 不再每字节两次函数分派（isCrUnit/isLfUnit），也无需
                            // buf.copyOf（carry 恒空）。GB 级扫描由分钟级降到秒级。
                            var i = 0
                            while (i < n) {
                                val b = buf[i]
                                pageBytes++
                                if (skipLf) {
                                    skipLf = false
                                    if (b == LF) { i++; continue } // CRLF 后半
                                    if (pendingRec) { offsets.add(chunkStart + i); pendingRec = false }
                                } else if (pendingRec) {
                                    offsets.add(chunkStart + i)
                                    pendingRec = false
                                }
                                if (b == CR) {
                                    lineCount++; curPageLines++
                                    skipLf = true
                                    // 批次56：行数到 pageSize 或字节过 1MB —— 先到者在行界断页。
                                    if (lineCount % pageSize == 0 || pageBytes >= FileAccess.WINDOW_PAGE_BYTES) {
                                        pendingRec = true
                                        pageLines.add(curPageLines)
                                        curPageLines = 0
                                        pageBytes = 0
                                    }
                                } else if (b == LF) {
                                    lineCount++; curPageLines++
                                    if (lineCount % pageSize == 0 || pageBytes >= FileAccess.WINDOW_PAGE_BYTES) {
                                        pendingRec = true
                                        pageLines.add(curPageLines)
                                        curPageLines = 0
                                        pageBytes = 0
                                    }
                                }
                                i++
                            }
                        } else {
                            val chunk = if (carry.isEmpty()) {
                                if (n == buf.size) buf.copyOf() else buf.copyOf(n)
                            } else {
                                ByteArray(carry.size + n).also {
                                    carry.copyInto(it, 0)
                                    buf.copyInto(it, carry.size, 0, n)
                                }
                            }
                            val consumedUnits = (chunk.size / unit) * unit
                            // carry = 尾部不足一个单元的字节（下一轮拼上再判）
                            carry = if (consumedUnits < chunk.size) {
                                chunk.copyOfRange(consumedUnits, chunk.size)
                            } else ByteArray(0)
                            var i = 0
                            while (i < consumedUnits) {
                                val pos = filePos - chunk.size + i  // 单元在文件中的绝对起点
                                val isCr = isCrUnit(chunk, i, unit, littleEndian)
                                val isLf = isLfUnit(chunk, i, unit, littleEndian)
                                pageBytes += unit
                                if (skipLf) {
                                    skipLf = false
                                    if (isLf) {
                                        // CRLF 的后半：不计行、不落偏移，pending 留给再下一行首。
                                        // 注意必须推进 i —— 否则下一圈 skipLf 已复位，这个 LF
                                        // 会被当普通行界再计一次（CRLF 文件行数翻倍）。
                                        i += unit
                                        continue
                                    }
                                    if (pendingRec) { offsets.add(pos); pendingRec = false }
                                } else if (pendingRec) {
                                    // 页界行的下一行（可能自身就是空行 = 直接以行界开头）
                                    offsets.add(pos)
                                    pendingRec = false
                                }
                                when {
                                    isCr -> {
                                        lineCount++; curPageLines++
                                        skipLf = true
                                        // 批次56：行数到 pageSize 或字节过 1MB —— 先到者断页。
                                        if (lineCount % pageSize == 0 ||
                                            pageBytes >= FileAccess.WINDOW_PAGE_BYTES
                                        ) {
                                            pendingRec = true
                                            pageLines.add(curPageLines)
                                            curPageLines = 0
                                            pageBytes = 0
                                        }
                                    }
                                    isLf -> {
                                        lineCount++; curPageLines++
                                        if (lineCount % pageSize == 0 ||
                                            pageBytes >= FileAccess.WINDOW_PAGE_BYTES
                                        ) {
                                            pendingRec = true
                                            pageLines.add(curPageLines)
                                            curPageLines = 0
                                            pageBytes = 0
                                        }
                                    }
                                }
                                i += unit
                            }
                        }
                        // 批次50：进度按已读字节占比上报（变化才回调）——UI 显示「扫描中 N%」。
                        if (onProgress != null && totalBytes > 0) {
                            val percent = (filePos * 100 / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    // 行数口径与 sora 一致：行界数 + 1 —— 末尾**空行也算一行**
                    // （"a\n" 在 sora 里是 2 行）。空文件没有行（页数仍为 1，读出空串）。
                    if (filePos > 0) {
                        lineCount += 1
                        curPageLines += 1
                    }
                    val length = filePos
                    // 悬空页界：文件恰好以页界行的行界收尾（下一行 = 末尾空行，起点即文件尾）
                    // —— 补记该页起点，页数与整读模式 ceil(总行数/页宽) 对齐。
                    if (pendingRec && filePos > 0) offsets.add(filePos)
                    // 批次56：补齐末页行数 —— pageLines 必须与 offsets 一一对应。
                    if (pageLines.size < offsets.size) pageLines.add(curPageLines)
                    AppLog.d(
                        TAG,
                        "build ok: lines=$lineCount pages=${offsets.size} " +
                            "charset=${charset.name()} size=$length"
                    )
                    WindowedDocument(
                        file = file,
                        charset = charset,
                        pageSize = pageSize,
                        pageOffsets = offsets.toLongArray(),
                        pageLines = pageLines.toIntArray(),
                        totalLines = lineCount,
                        fileLength = length
                    )
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "build failed: ${file.absolutePath}", e)
                null
            } catch (e: OutOfMemoryError) {
                AppLog.e(TAG, "build OOM: ${file.absolutePath}")
                null
            }
        }

        /** 1/2/4 = 变宽 ASCII 兼容 / UTF-16 / UTF-32；0 = 白名单外，不猜。 */
        private fun unitSize(charset: Charset): Int = when (val n = charset.name()) {
            "UTF-16", "UTF-16LE", "UTF-16BE" -> 2
            "UTF-32LE", "UTF-32BE" -> 4
            else -> if (n in BYTE_SCAN_SAFE) 1 else 0
        }

        /** 批次53：按编码名/BOM 判端序（[build] 与 [writeWithOverlays] 的簿记共用，避免口径漂移）。 */
        private fun detectLittleEndian(raf: RandomAccessFile, charset: Charset): Boolean =
            when (charset.name()) {
                "UTF-16LE", "UTF-32LE" -> true
                "UTF-16BE", "UTF-32BE" -> false
                "UTF-16" -> {
                    val head = ByteArray(2)
                    val got = raf.read(head, 0, 2)
                    raf.seek(0)
                    !(got == 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte())
                }
                else -> false
            }

        /**
         * 批次53：统计一页新字节的行界数与「是否以 LF 单元收尾」—— 口径与 [build] 逐条对齐
         * （CR/LF/CRLF 各算一个行界；UTF-16/32 按端序对齐单元判定；不足一个单元的尾字节忽略，
         * String.getBytes 对白名单编码恒产完整单元）。
         */
        private fun countBoundaries(b: ByteArray, unit: Int, le: Boolean): Pair<Int, Boolean> {
            if (b.isEmpty() || unit <= 0) return 0 to false
            var bounds = 0
            var skipLf = false
            var lastIsLf = false
            var i = 0
            val limit = (b.size / unit) * unit
            while (i < limit) {
                val isCr = isCrUnit(b, i, unit, le)
                val isLf = isLfUnit(b, i, unit, le)
                if (skipLf) {
                    skipLf = false
                    if (isLf) {
                        lastIsLf = true // CRLF 后半：边界已随 CR 计过
                    } else if (isCr) {
                        bounds++; skipLf = true; lastIsLf = false
                    } else {
                        lastIsLf = false
                    }
                } else when {
                    isCr -> { bounds++; skipLf = true; lastIsLf = false }
                    isLf -> { bounds++; lastIsLf = true }
                    else -> lastIsLf = false
                }
                i += unit
            }
            return bounds to lastIsLf
        }

        /**
         * 单元判定按**端序**分发：UTF-16LE 的 U+0D00 字符恰好是字节 [00 0D]，若不分端序
         * 会被误判成 UTF-16BE 的 CR —— 必须严格按编码端序比对，绝不双认。
         */
        private fun isCrUnit(b: ByteArray, i: Int, unit: Int, le: Boolean): Boolean = when (unit) {
            1 -> b[i] == 0x0D.toByte()
            2 -> if (le) b[i] == 0x0D.toByte() && b[i + 1] == 0x00.toByte()
            else b[i] == 0x00.toByte() && b[i + 1] == 0x0D.toByte()
            else -> if (le) {
                b[i] == 0x0D.toByte() && b[i + 1] == 0x00.toByte() &&
                    b[i + 2] == 0x00.toByte() && b[i + 3] == 0x00.toByte()
            } else {
                b[i] == 0x00.toByte() && b[i + 1] == 0x00.toByte() &&
                    b[i + 2] == 0x00.toByte() && b[i + 3] == 0x0D.toByte()
            }
        }

        private fun isLfUnit(b: ByteArray, i: Int, unit: Int, le: Boolean): Boolean = when (unit) {
            1 -> b[i] == 0x0A.toByte()
            2 -> if (le) b[i] == 0x0A.toByte() && b[i + 1] == 0x00.toByte()
            else b[i] == 0x00.toByte() && b[i + 1] == 0x0A.toByte()
            else -> if (le) {
                b[i] == 0x0A.toByte() && b[i + 1] == 0x00.toByte() &&
                    b[i + 2] == 0x00.toByte() && b[i + 3] == 0x00.toByte()
            } else {
                b[i] == 0x00.toByte() && b[i + 1] == 0x00.toByte() &&
                    b[i + 2] == 0x00.toByte() && b[i + 3] == 0x0A.toByte()
            }
        }
    }
}
