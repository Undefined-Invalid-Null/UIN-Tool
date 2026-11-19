// app/src/main/java/com/UIN/Tool/ui/screen/files/CompareActivity.kt
package com.UIN.Tool.ui.screen.files

import com.UIN.Tool.ui.components.unified.UnifiedDropdownMenu
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * 并排文件对比（6.3.0）。
 *
 * 入口是多选两条后的长按菜单（[ContextAction.CONTRAST]）—— 对比天然要两个文件，
 * 所以不走单文件的「打开方式」。
 *
 * diff 是自己写的行级算法，分三段：
 *  1. **剥公共前后缀** —— 两份文件绝大多数行是一样的，剥完中间只剩真正有差异的那段，
 *     后面的开销直接降几个数量级；
 *  2. 中间段小（乘积 ≤ [MAX_LCS_CELLS]）走 **LCS 动态规划**，结果最优；
 *  3. 中间段大则退到 **唯一行锚点**：只在两边各出现一次的行才当锚点，按 A 的顺序扫、
 *     要求 B 侧位置单调递增。O(n log n)、不吃内存，大文件也能秒出。
 *
 * 三个直接拒绝的入口，都是「不该让用户试了才知道」：二进制（NUL 判定，比了也是乱码）、
 * 超大（行数/字节都封顶）、读不出来。
 */
class CompareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val left = intent.getStringExtra(EXTRA_LEFT)
        val right = intent.getStringExtra(EXTRA_RIGHT)
        if (left.isNullOrBlank() || right.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            UINToolTheme {
                CompareRoot(
                    leftPath = left,
                    rightPath = right,
                    onExit = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_LEFT = "left"
        const val EXTRA_RIGHT = "right"

        fun start(context: Context, leftPath: String, rightPath: String) {
            context.startActivity(
                Intent(context, CompareActivity::class.java).apply {
                    putExtra(EXTRA_LEFT, leftPath)
                    putExtra(EXTRA_RIGHT, rightPath)
                }
            )
        }
    }
}

// ==================== 对比深度 ====================

/**
 * 对比深度三档 —— 用户建议的「让用户选快速 / 普通 / 精确，最高级别就是解压后对比」。
 *
 * - [FAST] **只看元数据**：比「条目 + 大小」，一个字节正文都不解。零成本、无碰撞
 *   （大小不同**必然**不同），代价是「长度没变、内容变了」看不见；
 * - [NORMAL] **再加包里记着的 CRC32**（[ArchiveEntry.crc]）：仍然只读头索引，
 *   能抓住上面那种改动。CRC 有 2^-32 的碰撞率，比大小准、比解压便宜；
 * - [EXACT] **逐条解压出来算 SHA-256** —— 这才叫「解压后对比」，也是唯一一条
 *   无碰撞的判据。代价是两个包各整解一遍，条目数与总字节各有一道上限。
 *
 * **按格式收档位**（`modeOptionsFor`）：tar 的条目头里根本没有 CRC，[NORMAL] 对它
 * 无意义，那一行就只摆 [FAST] 和 [EXACT]。
 */
enum class CompareMode { FAST, NORMAL, EXACT }

/**
 * 当前这两个对象能摆出哪几档。
 *
 * @return 空表 = **不摆模式行**。文本对比没有中间档可言 —— 16MB 读进来就是十几毫秒，
 *   「快」快不出来；而它本来就把正文按行铺满了，也没有更准的一档可去。
 */
internal fun modeOptionsFor(left: File, right: File): List<CompareMode> {
    // 两个目录：三档照样成立 —— 快速只看相对路径与大小，普通 / 精确得现读正文算
    // （归档那边「包里记着 CRC」的那份便宜这里没有）
    if (left.isDirectory && right.isDirectory) {
        return listOf(CompareMode.FAST, CompareMode.NORMAL, CompareMode.EXACT)
    }
    val lf = detectArchiveFormat(left)
    val rf = detectArchiveFormat(right)
    if (lf == ArchiveFormat.UNKNOWN || rf == ArchiveFormat.UNKNOWN) return emptyList()
    return if (lf.hasEntryCrc && rf.hasEntryCrc) {
        listOf(CompareMode.FAST, CompareMode.NORMAL, CompareMode.EXACT)
    } else {
        // tar / 单文件压缩：没有条目级 CRC，摆了「普通」也只会跟「快速」一模一样
        listOf(CompareMode.FAST, CompareMode.EXACT)
    }
}

/**
 * 对比**超出上限**的专用信号 —— 上层据此回「文件过大」，而不是含糊的「读不出来」。
 *
 * 两个来源：精确档解出来的字节超了 [MAX_EXACT_BYTES]；文件夹递归数出的文件超了
 * [MAX_COMPARE_ENTRIES]。对用户来说是同一句话（都该停下），所以共用一个信号。
 */
private class CompareTooLargeException : Exception("compare over budget")

/**
 * 把对比途中抛出的东西翻成人话，三处入口共用（归档 / 文件夹 / 文本）。
 *
 * **取消要原样抛出去**：吞成 Failed 的话，用户切档位时旧请求会把自己当成一次
 * 「读取失败」写进 `load`，把新结果盖掉 —— 现象就是「点了没反应」。
 */
private fun mapCompareError(e: Throwable): CompareLoad {
    if (e is CancellationException) throw e
    return when {
        e is CompareTooLargeException -> CompareLoad.TooLarge
        isPasswordRequired(e) -> CompareLoad.Encrypted
        else -> CompareLoad.Failed
    }
}

// ==================== 门槛 ====================

/**
 * 单侧字节上限。超过就直接拒 —— 先看长度再读，免得把进程撑爆。
 *
 * `internal`：属性框挑第二个文件时要**同一个闸**先量一遍（不然拷完进来也只会撞上
 * 「文件过大」），两处各写一个 16MB 日后必然漂移。
 */
internal const val MAX_COMPARE_BYTES = 16L * 1024 * 1024

/** 单侧行数上限。再往上并排渲染也没有可读性了。 */
private const val MAX_COMPARE_LINES = 50_000

/** 单侧归档条目数上限（e15 P2-14）。与行数闸同级：再往上只是把列表拖长。 */
private const val MAX_COMPARE_ENTRIES = 200_000

/** LCS 表的格数上限（≈16MB int 数组）。超过就改走锚点法。 */
private const val MAX_LCS_CELLS = 4_000_000L

/**
 * 精确档单侧**解压出来**的总字节上限。
 *
 * 只封条目数封不住体积 —— 一个 tar 里塞 20GB 也才几万条。破了就退成
 * 「文件过大，无法对比」，与另两道闸同一句话，不为它单开一种说法。
 */
private const val MAX_EXACT_BYTES = 1L * 1024 * 1024 * 1024

/** 判二进制只嗅探开头这一段，与 git 的做法一致。 */
private const val BINARY_SNIFF_BYTES = 8192

/** 被比的是哪一类对象（见 [CompareLoad.Ready]）。 */
private enum class CompareKind { TEXT, ARCHIVE, DIR }

private sealed interface CompareLoad {
    /**
     * [kind] 决定**这一屏怎么说自己、摆哪几排开关**：
     *
     * - [CompareKind.TEXT] 报「行」，摆口径开关（忽略大小写 / 忽略空白 / 只看差异）；
     * - [CompareKind.ARCHIVE] 报「条目」、[CompareKind.DIR] 报「文件」，两者**都不摆
     *   口径开关**（那三颗对一张表毫无意义，e15 P2-14 的同一条理由），改摆对比深度三档。
     *
     * [commonCount] = **两边都有的**条目数（只列差异的那两种表里没有 SAME 行，
     * 汇总那行的「相同 N 个」只能这么补回来）。文本表自带 SAME 行，给 -1 走默认。
     */
    class Ready(
        val rows: List<DiffRow>,
        val kind: CompareKind,
        val commonCount: Int = -1
    ) : CompareLoad
    object TooLarge : CompareLoad
    object Binary : CompareLoad
    object Failed : CompareLoad

    /** 两个包里至少有一个加密，而用户还没为它输过密码（e15 P1-12）。 */
    object Encrypted : CompareLoad
}

private fun looksBinary(head: ByteArray): Boolean {
    val limit = minOf(head.size, BINARY_SNIFF_BYTES)
    for (i in 0 until limit) if (head[i].toInt() == 0) return true
    return false
}

/**
 * 切行。
 *
 * - 空文件是 **0 行**，不是 1 个空行（`"".split('\n')` 给的是 `[""]`，得自己兜）；
 * - CRLF 归一成 LF，否则 Windows 文件每行都拖个幽灵 `\r`，两边一比整篇全红；
 * - 末尾换行会 split 出一个空尾巴，那是「结尾标记」不是一行内容。
 */
private fun splitLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val raw = text.split('\n')
    val out = ArrayList<String>(raw.size)
    for (line in raw) {
        out.add(if (line.endsWith("\r")) line.dropLast(1) else line)
    }
    if (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
    return out
}

/**
 * 「忽略空白 / 忽略大小写」的归一化（用户反馈 c16）。
 *
 * 只给 diff **判定**用，显示永远是原文 —— 归一化不改变行数，算出来的 ops 与原文数组
 * 的下标一一对得上，所以 [buildDiffRows] 才敢拿两套数组各用各的。
 */
private fun normalizeForDiff(line: String, ignoreCase: Boolean, ignoreSpace: Boolean): String {
    var t = if (ignoreCase) line.lowercase() else line
    if (ignoreSpace) t = t.replace(WHITESPACE_RUN, " ").trim()
    return t
}

private val WHITESPACE_RUN = Regex("\\s+")

private suspend fun loadCompare(
    left: File,
    right: File,
    mode: CompareMode,
    ignoreCase: Boolean,
    ignoreSpace: Boolean
): CompareLoad {
    // 归档对比（e15 **P2-14**）：两边都是认得出的包 → 比**条目表**，不比字节。
    // 这一支必须排在 16MB 字节闸**之前** —— 一个 50MB 的 zip，索引往往只有几十 KB，
    // 拿文件体积去卡它，等于把最该比的那种包挡在门外。
    // 格式不同（zip vs 7z）也照比：比的是「里面有什么」，不是「怎么装的」。
    val leftFormat = detectArchiveFormat(left)
    val rightFormat = detectArchiveFormat(right)
    if (leftFormat != ArchiveFormat.UNKNOWN && rightFormat != ArchiveFormat.UNKNOWN) {
        return runCatching { loadArchiveCompare(left, right, mode) }
            .getOrElse { e -> mapCompareError(e) }
    }

    // 文件夹对文件夹 → 递归比两棵树。**排在归档那支之后**没关系（目录的
    // detectArchiveFormat 恒为 UNKNOWN），但必须排在 16MB 字节闸**之前**：
    // 目录没有可比的 length()，读它只会换来一句含糊的「读不出来」。
    if (left.isDirectory || right.isDirectory) {
        // 混着来（一个文件 / 一个目录）没有可比的东西 —— 菜单那儿只卡了「恰好两条」，
        // 这里补上「同一种」
        if (!left.isDirectory || !right.isDirectory) return CompareLoad.Failed
        return runCatching { loadDirCompare(left, right, mode) }
            .getOrElse { e -> mapCompareError(e) }
    }

    if (left.length() > MAX_COMPARE_BYTES || right.length() > MAX_COMPARE_BYTES) {
        return CompareLoad.TooLarge
    }
    val leftBytes = runCatching { left.readBytes() }.getOrNull() ?: return CompareLoad.Failed
    val rightBytes = runCatching { right.readBytes() }.getOrNull() ?: return CompareLoad.Failed

    if (looksBinary(leftBytes) || looksBinary(rightBytes)) return CompareLoad.Binary

    val a = splitLines(String(leftBytes, Charsets.UTF_8))
    val b = splitLines(String(rightBytes, Charsets.UTF_8))
    if (a.size > MAX_COMPARE_LINES || b.size > MAX_COMPARE_LINES) return CompareLoad.TooLarge

    return CompareLoad.Ready(buildDiffRows(a, b, ignoreCase, ignoreSpace), CompareKind.TEXT)
}

/**
 * 一条待铺出来的差异 —— 归档对比与**文件夹对比**共用（两者都是「一张
 * 「相对路径 → 大小」的表，只列差异、目录不进表」）。
 *
 * [lTag] / [rTag] 是**大小相同**时补在后面的一句：没有它，「改了但长度没变」的两栏
 * 会一字不差，看着像没算完（见 `cmp_same_size`）。
 */
private class Pending(
    val path: String,
    val kind: RowKind,
    val l: Long?,
    val r: Long?,
    val lTag: String = "",
    val rTag: String = ""
)

/**
 * [Pending] → 行。**先按路径排、再编号**：两趟扫出来的是「左边全在前、右边全在后」，
 * 同一个目录的条目被劈到两头，看着像两个毫不相干的包。
 */
private fun pendingToRows(pending: List<Pending>): List<DiffRow> {
    val sorted = pending.sortedBy { it.path }
    val rows = ArrayList<DiffRow>(sorted.size)
    for ((i, p) in sorted.withIndex()) {
        val n = i + 1
        val leftText = p.l?.let { "${p.path}    ${formatFileSize(it)}${p.lTag}" }
        val rightText = p.r?.let { "${p.path}    ${formatFileSize(it)}${p.rTag}" }
        rows.add(
            when (p.kind) {
                RowKind.ONLY_LEFT -> DiffRow(RowKind.ONLY_LEFT, n, leftText, 0, null)
                RowKind.ONLY_RIGHT -> DiffRow(RowKind.ONLY_RIGHT, 0, null, n, rightText)
                else -> DiffRow(RowKind.CHANGED, n, leftText, n, rightText)
            }
        )
    }
    return rows
}

/** 汇总那行的量词随被比对象走：文本报「行」、归档报「条目」、文件夹报「文件」。 */
private fun countRes(kind: CompareKind, text: Int, arc: Int, dir: Int): Int = when (kind) {
    CompareKind.TEXT -> text
    CompareKind.ARCHIVE -> arc
    CompareKind.DIR -> dir
}

/**
 * 两个压缩包的**条目对比**（e15 **P2-14**）：列出只在左边 / 只在右边 / 两边都有但不一样。
 *
 * 三条取舍：
 * - **只列差异**：几千条一模一样的条目摆出来只是噪音，汇总那行已经把「相同」的条数报了；
 * - **只比文件**，目录不进表：新目录里的文件会自己冒出来，目录那条是重复信息；
 * - 「不同」的判据**随档位走**（[CompareMode]）。判据升级靠 [ArchiveEntry.crc] ——
 *   它是**一趟扫索引顺手带出来的**，不是 `archiveEntryCrc32` 那样为一条重开一次包
 *   （那条对 N 个条目就是 O(N^2)）。精确档再往下走一层，直接解压正文算 SHA-256。
 */
private suspend fun loadArchiveCompare(
    left: File,
    right: File,
    mode: CompareMode
): CompareLoad {
    val leftByPath = readArchiveEntries(left).filter { !it.isDir }
        .associateBy({ it.path }, { it })
    val rightByPath = readArchiveEntries(right).filter { !it.isDir }
        .associateBy({ it.path }, { it })
    if (leftByPath.size > MAX_COMPARE_ENTRIES || rightByPath.size > MAX_COMPARE_ENTRIES) {
        return CompareLoad.TooLarge
    }
    // 两边都有的条目数：汇总那行「相同 N 个」的分子（见 [CompareLoad.Ready.commonCount]）
    val commonCount = leftByPath.keys.count { it in rightByPath }

    // 精确档要两份「路径 → 正文 SHA-256」；另两档一个字节正文都不解
    val leftHash: Map<String, String>? =
        if (mode == CompareMode.EXACT) archiveContentHashes(left) else null
    val rightHash: Map<String, String>? =
        if (mode == CompareMode.EXACT) archiveContentHashes(right) else null
    // 大小相同时要补的一句：没有它，「改了但长度没变」的两栏会一字不差，看着像没算完
    val sameSizeNote = Str.get(R.string.cmp_same_size)

    val pending = ArrayList<Pending>()
    for ((path, lf) in leftByPath) {
        val rf = rightByPath[path]
        when {
            rf == null -> pending.add(Pending(path, RowKind.ONLY_LEFT, lf.size, null))
            rf.size != lf.size -> pending.add(Pending(path, RowKind.CHANGED, lf.size, rf.size))
            // 大小已经一样了，往下按档位加码；每一档都是「找到理由说不同才列出来」
            mode == CompareMode.FAST -> Unit // 快速档到此为止
            mode == CompareMode.NORMAL ->
                if (lf.crc != null && rf.crc != null && lf.crc != rf.crc) {
                    pending.add(
                        Pending(path, RowKind.CHANGED, lf.size, rf.size, sameSizeNote, sameSizeNote)
                    )
                }
            else -> {
                val lh = leftHash?.get(path)
                val rh = rightHash?.get(path)
                if (lh == null || rh == null || lh != rh) {
                    pending.add(
                        Pending(path, RowKind.CHANGED, lf.size, rf.size, sameSizeNote, sameSizeNote)
                    )
                }
            }
        }
    }
    for ((path, rf) in rightByPath) {
        if (path !in leftByPath) pending.add(Pending(path, RowKind.ONLY_RIGHT, null, rf.size))
    }
    return CompareLoad.Ready(pendingToRows(pending), CompareKind.ARCHIVE, commonCount)
}

/**
 * 把 [input] 读干算 SHA-256；返回「十六进制摘要 to 读了多少字节」。
 *
 * **不关流** —— tar 那条路传进来的是整条归档流，关了下一条就没了；谁开的谁关。
 */
private fun sha256Of(input: InputStream): Pair<String, Long> {
    val d = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        if (n > 0) {
            d.update(buf, 0, n)
            total += n
        }
    }
    return d.digest().joinToString("") { "%02x".format(it) } to total
}

/**
 * 逐条**解压** [file]、算出「包内路径 → 正文 SHA-256」（[CompareMode.EXACT]）。
 *
 * 每种格式**只走一遍流**：zip/7z/rar 是一边读条目一边解，tar 本来就是顺序流。
 * 落成临时文件再哈希也行，但那要多写一遍盘还得记着删 —— 正文一边流一边喂进
 * `MessageDigest`，峰值内存只有一个缓冲区。
 *
 * 这就是「解压后对比」的全部代价所在：两个包各整解一遍，见 [MAX_EXACT_BYTES]。
 *
 * 每 64 条 [yield] 一次 —— 精确档是这一屏最慢的动作，用户切了档位或直接退出时
 * 得能停得下来（[loadCompare] 那层会把取消原样抛出去）。
 *
 * @throws CompareTooLargeException 解出来的字节超了上限
 * @throws org.apache.commons.compress.PasswordRequiredException 加密包没密码
 * @throws java.io.IOException 包损坏 / 解不出来
 */
private suspend fun archiveContentHashes(file: File): Map<String, String> {
    // 加密的 zip 只在这一支拦：commons-compress **只读索引、不解密**，正文根本拿不到。
    // 放它去试只会换来一句含糊的「读不出来」，不如用既有那句话把用户支去
    // 「先打开它输一次密码」（7z / rar 走的是各自唯一入口，密码缓存接得住，不拦）。
    if (detectArchiveFormat(file) == ArchiveFormat.ZIP && ArchiveEncoding.hasEncryptedEntry(file)) {
        throw PasswordRequiredException("zip encrypted")
    }

    val out = HashMap<String, String>()
    var bytes = 0L
    var seen = 0

    fun record(path: String, input: InputStream) {
        val (hex, n) = sha256Of(input)
        bytes += n
        if (bytes > MAX_EXACT_BYTES) throw CompareTooLargeException()
        out[path] = hex
    }

    when (detectArchiveFormat(file)) {
        ArchiveFormat.ZIP -> ArchiveEncoding.openZipArchive(file).use { zip ->
            val en = zip.entries
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                if (e.isDirectory) continue
                val name = safeInternalPath(e.name) ?: continue
                zip.getInputStream(e).use { record(name, it) }
                if (++seen and 0x3F == 0) yield()
            }
        }

        ArchiveFormat.SEVEN_Z -> openSevenZ(file).use { seven ->
            for (e in seven.entries) {
                if (e.isDirectory) continue
                val name = safeInternalPath(e.name) ?: continue
                seven.getInputStream(e).use { record(name, it) }
                if (++seen and 0x3F == 0) yield()
            }
        }

        ArchiveFormat.RAR -> openRar(file).use { rar ->
            for (fh in rar.getFileHeaders()) {
                if (fh.isDirectory) continue
                val name = fh.getFileName()?.trimEnd('/', '\\') ?: continue
                val norm = safeInternalPath(name) ?: continue
                rar.getInputStream(fh).use { record(norm, it) }
                if (++seen and 0x3F == 0) yield()
            }
        }

        ArchiveFormat.TAR -> {
            // 剥掉外层之后里面不是 tar → `a.log.gz` 这类「只压了一下的一整个文件」，
            // 整包就是一条内容（与 scanPlain 同一条路；openTarStream 返回 null 时
            // 它自己已经把那条半途流关了，所以这里重开一条）
            val inner = openTarStream(file)
            if (inner == null) {
                openPlainStream(file).use { record(plainInnerName(file), it) }
            } else {
                TarArchiveInputStream(inner).use { tar ->
                    while (true) {
                        val e = tar.nextTarEntry ?: break
                        if (e.isDirectory) continue
                        val name = safeInternalPath(e.name) ?: continue
                        // 传整条 tar 流进去：read 会在当前条目边界自己停住，
                        // 也正因为不关流，才能连着往下读（见 sha256Of 的注释）
                        record(name, tar)
                        if (++seen and 0x3F == 0) yield()
                    }
                }
            }
        }

        ArchiveFormat.UNKNOWN -> Unit
    }
    return out
}

/** 递归深度上限：`File.isDirectory` 会跟着符号链接走，没有兜底就可能绕圈。 */
private const val MAX_DIR_DEPTH = 64

/**
 * 递归量出 [root] 下所有文件：「相对路径 → 字节数」。**只走元数据，不读正文** ——
 * 快速档到这就够了，另两档也先拿它把候选缩到「大小相同」那一小批。
 *
 * - **不过滤隐藏文件**：按「列表里看不看得见」来筛，结果会随设置变，而对比要比的
 *   正是两棵树的实际差异；
 * - 读不动的子目录（外部存储上很常见）整支跳过，不中断整棵树；
 * - 每数到 256 个文件 [yield] 一次：几万条的树在 IO 线程上也要能被切走。
 */
private suspend fun walkDir(
    root: File,
    prefix: String,
    out: MutableMap<String, Long>,
    depth: Int = 0
) {
    if (depth > MAX_DIR_DEPTH) return
    val children = root.listFiles() ?: return
    for (f in children) {
        if (out.size >= MAX_COMPARE_ENTRIES) throw CompareTooLargeException()
        val rel = if (prefix.isEmpty()) f.name else prefix + "/" + f.name
        if (f.isDirectory) {
            walkDir(f, rel, out, depth + 1)
        } else if (f.isFile) {
            out[rel] = f.length()
            if (out.size and 0xFF == 0) yield()
        }
    }
}

/**
 * 一个文件按档位算出来的「指纹」：普通给 CRC32（8 位十六进制），精确给 SHA-256。
 *
 * @return 「指纹 to 读了多少字节」—— 调用方拿后者记账（见 [CompareTooLargeException]）
 */
private fun fingerprintOf(f: File, mode: CompareMode): Pair<String, Long> {
    val crc = if (mode == CompareMode.NORMAL) CRC32() else null
    val md = if (mode == CompareMode.EXACT) MessageDigest.getInstance("SHA-256") else null
    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    f.inputStream().buffered().use { ins ->
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            if (n > 0) {
                crc?.update(buf, 0, n)
                md?.update(buf, 0, n)
                total += n
            }
        }
    }
    val hex = if (md != null) md.digest().joinToString("") { "%02x".format(it) }
    else "%08x".format(crc!!.value)
    return hex to total
}

/**
 * 两个**文件夹**的递归对比（多选两个文件夹 → 长按「对比」）。
 *
 * 形状与归档对比一致：一张「相对路径 → 大小」的表，**只列差异、目录不进表**（新目录里
 * 的文件会自己冒出来，目录那条是重复信息）。路径按各自的根算**相对形**，这样两个文件夹
 * 名字不同也不会每行都算「不同」。
 *
 * 判据同样随档位走，但 CRC 的**来源**不同：归档的 CRC 是包里记着的（扫头索引就带出来，
 * 零成本），普通文件得现读正文算。所以这里先只量大小，**只对「大小相同」的那批**才去读 ——
 * 大小不同的必然不同，读了也是白读，而那一批通常很小。
 */
private suspend fun loadDirCompare(left: File, right: File, mode: CompareMode): CompareLoad {
    val leftByPath = HashMap<String, Long>()
    val rightByPath = HashMap<String, Long>()
    walkDir(left, "", leftByPath)
    walkDir(right, "", rightByPath)

    val sameSizeNote = Str.get(R.string.cmp_same_size)
    val pending = ArrayList<Pending>()
    // 两边都在、**大小还一样**的那批 —— 快速档直接结案，另两档才需要往下读正文
    val sameSize = ArrayList<Pair<String, Long>>()

    for ((path, ls) in leftByPath) {
        val rs = rightByPath[path]
        when {
            rs == null -> pending.add(Pending(path, RowKind.ONLY_LEFT, ls, null))
            ls != rs -> pending.add(Pending(path, RowKind.CHANGED, ls, rs))
            else -> sameSize.add(path to ls)
        }
    }
    for ((path, rs) in rightByPath) {
        if (path !in leftByPath) pending.add(Pending(path, RowKind.ONLY_RIGHT, null, rs))
    }

    if (mode != CompareMode.FAST) {
        var bytes = 0L
        var done = 0
        for ((path, size) in sameSize) {
            val lf = File(left, path)
            val rf = File(right, path)
            // 中途被删 / 对不上的符号链接 → 当它没变（少了证据，不等于有证据说不同）
            if (!lf.isFile || !rf.isFile) continue
            val (lh, ln) = fingerprintOf(lf, mode)
            val (rh, rn) = fingerprintOf(rf, mode)
            bytes += ln + rn
            if (bytes > MAX_EXACT_BYTES) throw CompareTooLargeException()
            if (lh != rh) {
                pending.add(
                    Pending(path, RowKind.CHANGED, size, size, sameSizeNote, sameSizeNote)
                )
            }
            // 按**遍历数**让出，不能拿 pending.size 当节拍 —— 全都没变时它一动不动，
            // 整轮循环就一次都切不出去（用户退出也停不下来）
            if (++done and 0xFF == 0) yield()
        }
    }

    val commonCount = leftByPath.keys.count { it in rightByPath }
    return CompareLoad.Ready(pendingToRows(pending), CompareKind.DIR, commonCount)
}

// ==================== diff ====================

private enum class Op { SAME, DELETE, INSERT }

private enum class RowKind {
    /** 两边同内容。 */
    SAME,

    /** 左删右增配成一对（行号各自连续）。 */
    CHANGED,

    /** 只有左边有。 */
    ONLY_LEFT,

    /** 只有右边有。 */
    ONLY_RIGHT
}

private data class DiffRow(
    val kind: RowKind,
    val leftNo: Int,
    val left: String?,
    val rightNo: Int,
    val right: String?
)

private fun buildDiffRows(
    a: List<String>,
    b: List<String>,
    ignoreCase: Boolean = false,
    ignoreSpace: Boolean = false
): List<DiffRow> {
    // 判定用归一化的行、显示用原文：[normalizeForDiff] 不改变行数，两套数组下标一一对得上
    val ops = if (ignoreCase || ignoreSpace) {
        diffSequences(
            a.map { normalizeForDiff(it, ignoreCase, ignoreSpace) },
            b.map { normalizeForDiff(it, ignoreCase, ignoreSpace) }
        )
    } else {
        diffSequences(a, b)
    }
    val rows = ArrayList<DiffRow>(ops.size)
    var i = 0
    var j = 0
    var k = 0
    while (k < ops.size) {
        if (ops[k] == Op.SAME) {
            rows.add(DiffRow(RowKind.SAME, i + 1, a[i], j + 1, b[j]))
            i++
            j++
            k++
            continue
        }
        // 一整段删除/插入：先各自计数，再左右配对成「改动行」，多出来的落单
        val delStart = i
        val insStart = j
        var delCount = 0
        var insCount = 0
        while (k < ops.size && ops[k] != Op.SAME) {
            if (ops[k] == Op.DELETE) {
                delCount++
                i++
            } else {
                insCount++
                j++
            }
            k++
        }
        val paired = minOf(delCount, insCount)
        for (t in 0 until paired) {
            rows.add(
                DiffRow(
                    RowKind.CHANGED,
                    delStart + t + 1, a[delStart + t],
                    insStart + t + 1, b[insStart + t]
                )
            )
        }
        for (t in paired until delCount) {
            rows.add(DiffRow(RowKind.ONLY_LEFT, delStart + t + 1, a[delStart + t], 0, null))
        }
        for (t in paired until insCount) {
            rows.add(DiffRow(RowKind.ONLY_RIGHT, 0, null, insStart + t + 1, b[insStart + t]))
        }
    }
    return rows
}

private fun diffSequences(a: List<String>, b: List<String>): List<Op> {
    // 1) 剥公共前后缀
    val maxCommon = minOf(a.size, b.size)
    var prefix = 0
    while (prefix < maxCommon && a[prefix] == b[prefix]) prefix++

    var suffix = 0
    while (suffix < maxCommon - prefix &&
        a[a.size - 1 - suffix] == b[b.size - 1 - suffix]
    ) {
        suffix++
    }

    val midA = a.subList(prefix, a.size - suffix)
    val midB = b.subList(prefix, b.size - suffix)

    val midOps = when {
        midA.isEmpty() && midB.isEmpty() -> emptyList()
        midA.isEmpty() -> List(midB.size) { Op.INSERT }
        midB.isEmpty() -> List(midA.size) { Op.DELETE }
        midA.size.toLong() * midB.size <= MAX_LCS_CELLS -> lcsOps(midA, midB)
        else -> anchorOps(midA, midB)
    }

    return buildList {
        repeat(prefix) { add(Op.SAME) }
        addAll(midOps)
        repeat(suffix) { add(Op.SAME) }
    }
}

/** 标准 LCS 回溯。表是 (m+1)×(n+1) 的 int，调用方已按 [MAX_LCS_CELLS] 卡过规模。 */
private fun lcsOps(a: List<String>, b: List<String>): List<Op> {
    val m = a.size
    val n = b.size
    val width = n + 1
    val dp = IntArray((m + 1) * width)

    for (i in m - 1 downTo 0) {
        val row = i * width
        val nextRow = (i + 1) * width
        val ai = a[i]
        for (j in n - 1 downTo 0) {
            dp[row + j] = if (ai == b[j]) {
                dp[nextRow + j + 1] + 1
            } else {
                maxOf(dp[nextRow + j], dp[row + j + 1])
            }
        }
    }

    val ops = ArrayList<Op>(m + n)
    var i = 0
    var j = 0
    while (i < m && j < n) {
        when {
            a[i] == b[j] -> {
                ops.add(Op.SAME)
                i++
                j++
            }
            // 等长时优先删，保证结果稳定、可复现
            dp[(i + 1) * width + j] >= dp[i * width + j + 1] -> {
                ops.add(Op.DELETE)
                i++
            }
            else -> {
                ops.add(Op.INSERT)
                j++
            }
        }
    }
    while (i < m) {
        ops.add(Op.DELETE)
        i++
    }
    while (j < n) {
        ops.add(Op.INSERT)
        j++
    }
    return ops
}

/**
 * 超大中间段的兜底：唯一行锚点。
 *
 * 只有「在 A 和 B 里各出现且仅出现一次」的行才配当锚点 —— 重复行（空行、缩进相同的行）
 * 无法确定对应关系，拿去配对只会把差异切碎。锚点按 A 的顺序扫，且要求 B 侧位置
 * **严格递增**，这样配对天然单调，不需要再解一遍最长递增子序列。
 */
private fun anchorOps(a: List<String>, b: List<String>): List<Op> {
    val countA = HashMap<String, Int>()
    for (line in a) countA[line] = (countA[line] ?: 0) + 1
    val countB = HashMap<String, Int>()
    for (line in b) countB[line] = (countB[line] ?: 0) + 1

    val uniqueB = HashMap<String, Int>()
    for (j in b.indices) if (countB[b[j]] == 1) uniqueB[b[j]] = j

    val anchors = ArrayList<Pair<Int, Int>>()
    var lastB = -1
    for (i in a.indices) {
        val line = a[i]
        if (countA[line] != 1) continue
        val j = uniqueB[line] ?: continue
        if (j <= lastB) continue
        anchors.add(i to j)
        lastB = j
    }

    val ops = ArrayList<Op>(a.size + b.size)
    var ai = 0
    var bi = 0
    for ((i, j) in anchors) {
        while (ai < i) {
            ops.add(Op.DELETE)
            ai++
        }
        while (bi < j) {
            ops.add(Op.INSERT)
            bi++
        }
        ops.add(Op.SAME)
        ai++
        bi++
    }
    while (ai < a.size) {
        ops.add(Op.DELETE)
        ai++
    }
    while (bi < b.size) {
        ops.add(Op.INSERT)
        bi++
    }
    return ops
}

// ==================== 界面 ====================

@Composable
private fun CompareRoot(leftPath: String, rightPath: String, onExit: () -> Unit) {
    val left = remember(leftPath) { File(leftPath) }
    val right = remember(rightPath) { File(rightPath) }

    var load by remember { mutableStateOf<CompareLoad?>(null) }
    // 口径重算期间的「还在算」标记 —— 配合下面**不再清空 load** 的做法用
    var recomputing by remember { mutableStateOf(false) }
    // 三个口径开关（用户反馈 c16「功能还不够多」）。前两个要重算 diff（判定按口径走、
    // 显示仍用原文），第三个只影响显示；任一变化都得从头来一遍，所以一起进 LaunchedEffect 的键。
    var ignoreCase by rememberSaveable { mutableStateOf(false) }
    var ignoreSpace by rememberSaveable { mutableStateOf(false) }
    var onlyDiff by rememberSaveable { mutableStateOf(false) }
    // 对比深度三档。存的是 ordinal —— enum 本身塞 Bundle 不是零风险，省得转个屏炸掉；
    // 选项外的值（换了对象、档位在这个格式下不存在）一律落回 FAST，见 effectiveMode
    var modeOrdinal by rememberSaveable { mutableStateOf(CompareMode.FAST.ordinal) }
    val modeOptions = remember(leftPath, rightPath) { modeOptionsFor(left, right) }
    val mode = CompareMode.entries.getOrElse(modeOrdinal) { CompareMode.FAST }
    val effectiveMode = if (mode in modeOptions) mode else CompareMode.FAST
    // 查看模式（e11「尤其是查看模式」）：**并排** = 左右两栏对照（改前的样子）；
    // **单栏** = 把改动合并成一栏，改过的那行红绿上下各摆一条 —— 窄屏上并排两边
    // 各只剩半行宽，长行被截得看不出改了哪儿，单栏一行就是一整行。
    var unified by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(leftPath, rightPath, effectiveMode, ignoreCase, ignoreSpace) {
        // **不要**先 `load = null`：那会把 CompareBody 整个卸载，`rememberLazyListState`
        // 随之销毁 —— 滚动位置归零、屏幕先白一下，大文件还要从头读两个 16MB。
        // 用户看到的就是反馈里的「点了没反应 / 点一下跳回顶部」（d18）。
        // 旧结果留着继续显示，旁边压一条细进度条说明还在算。
        recomputing = true
        val next = withContext(Dispatchers.IO) {
            loadCompare(left, right, effectiveMode, ignoreCase, ignoreSpace)
        }
        load = next
        recomputing = false
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = Str.get(
                    when ((load as? CompareLoad.Ready)?.kind) {
                        CompareKind.ARCHIVE -> R.string.cmp_archive_title
                        CompareKind.DIR -> R.string.cmp_dir_title
                        else -> R.string.cmp_title
                    }
                ),
                onBack = onExit,
                // e11：功能菜单挪到右上角三个点（原先对比深度、口径开关各自占一行摆在
                // 正文顶上，标题/汇总/跳转/两排胶囊/列表挤在一屏，列表只剩半截）
                actions = {
                    CompareMenu(
                        kind = (load as? CompareLoad.Ready)?.kind ?: CompareKind.TEXT,
                        mode = effectiveMode,
                        modeOptions = modeOptions,
                        onModeChange = { modeOrdinal = it.ordinal },
                        unified = unified,
                        onUnifiedChange = { unified = it },
                        onlyDiff = onlyDiff,
                        onOnlyDiffChange = { onlyDiff = it },
                        ignoreCase = ignoreCase,
                        onIgnoreCaseChange = { ignoreCase = it },
                        ignoreSpace = ignoreSpace,
                        onIgnoreSpaceChange = { ignoreSpace = it }
                    )
                }
            )
        }
    ) { padding ->
        when (val current = load) {
            null -> CompareNote(Str.get(R.string.loading), padding, loading = true)
            CompareLoad.TooLarge -> CompareNote(Str.get(R.string.cmp_too_large), padding)
            CompareLoad.Binary -> CompareNote(Str.get(R.string.cmp_binary), padding)
            CompareLoad.Failed -> CompareNote(Str.get(R.string.hex_read_failed), padding)
            CompareLoad.Encrypted -> CompareNote(Str.get(R.string.cmp_archive_encrypted), padding)
            is CompareLoad.Ready -> CompareBody(
                left = left,
                right = right,
                rows = current.rows,
                kind = current.kind,
                commonCount = current.commonCount,
                onlyDiff = onlyDiff,
                unified = unified,
                recomputing = recomputing,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

/**
 * 居中提示。`loading = true` 时上方补一个**真在转的圈**（用户反馈 d9）：
 * 「还在读两个文件」是进行时，静止的一行字看着像已经卡死了。
 */
@Composable
private fun CompareNote(text: String, padding: PaddingValues, loading: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(12.dp))
            }
            UnifiedCaptionText(text = text, textAlign = TextAlign.Center)
        }
    }
}

/** 一次统计的结果 —— 放进 remember，别让每次滚动都把 5 万行重新数一遍。 */
private data class CmpCounts(
    val same: Int,
    val changed: Int,
    val onlyLeft: Int,
    val onlyRight: Int
)

// ==================== 右上角功能菜单（e11） ====================

/** 对比深度三档的标签资源。 */
private fun modeLabelRes(mode: CompareMode): Int = when (mode) {
    CompareMode.FAST -> R.string.cmp_mode_fast
    CompareMode.NORMAL -> R.string.cmp_mode_normal
    CompareMode.EXACT -> R.string.cmp_mode_exact
}

/** 档位说明（「精确」比「普通」强在哪儿，光看词猜不出来）。 */
private fun modeHintRes(mode: CompareMode): Int = when (mode) {
    CompareMode.FAST -> R.string.cmp_mode_fast_hint
    CompareMode.NORMAL -> R.string.cmp_mode_normal_hint
    CompareMode.EXACT -> R.string.cmp_mode_exact_hint
}

/**
 * 右上角「⋮」功能菜单（e11：把这些开关从正文顶上挪进来）。
 *
 * 原先对比深度三档、口径三开关各自占一行摆在正文顶上 —— 标题、汇总、跳转条、
 * 两排胶囊、列表挤在一屏，列表只剩半截。收进来之后正文只剩
 * 「左右文件名 + 汇总 + 跳转 + 列表」，跳转条与列表都宽了一截。
 *
 * 菜单分三段，段标题是**不可点**的项（`enabled = false`），只为分组 ——
 * 没有分组的话「并排 / 快速 / 只看差异」排在一起，谁跟谁是一组看不出来。
 *
 * 单选项（查看模式、对比深度）选完**关菜单**；复选项（口径）**保持展开** ——
 * 想连着关掉两个开关时，每点一次都弹回去一次是纯打断。
 *
 * 勾选态一律用 `Check`，未选中时留一个**等宽占位** —— 不留的话每次选中/取消，
 * 整列文字都会左右跳一下。
 */
@Composable
private fun CompareMenu(
    kind: CompareKind,
    mode: CompareMode,
    modeOptions: List<CompareMode>,
    onModeChange: (CompareMode) -> Unit,
    unified: Boolean,
    onUnifiedChange: (Boolean) -> Unit,
    onlyDiff: Boolean,
    onOnlyDiffChange: (Boolean) -> Unit,
    ignoreCase: Boolean,
    onIgnoreCaseChange: (Boolean) -> Unit,
    ignoreSpace: Boolean,
    onIgnoreSpaceChange: (Boolean) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val tick = AppColors.textPrimary()
    val hintColor = AppColors.textHint()

    UnifiedIconButton(
        icon = Icons.Default.MoreVert,
        onClick = { open = true },
        contentDescription = Str.get(R.string.more),
        tint = tick,
    )

    // 每条都**内联**写：DropdownMenuItem 的接收者类型随 material3 版本在
    // 「顶层函数 / DropdownMenuScope 成员」之间变过，抽成 helper 反而可能拿不到接收者。
    UnifiedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(Str.get(R.string.cmp_view_mode), color = hintColor) },
            enabled = false,
            onClick = {}
        )
        DropdownMenuItem(
            text = { Text(Str.get(R.string.cmp_view_side)) },
            leadingIcon = {
                if (!unified) Icon(Icons.Default.Check, null, tint = tick)
                else Spacer(Modifier.width(24.dp))
            },
            onClick = {
                onUnifiedChange(false)
                open = false
            }
        )
        DropdownMenuItem(
            text = { Text(Str.get(R.string.cmp_view_unified)) },
            leadingIcon = {
                if (unified) Icon(Icons.Default.Check, null, tint = tick)
                else Spacer(Modifier.width(24.dp))
            },
            onClick = {
                onUnifiedChange(true)
                open = false
            }
        )

        // 对比深度：归档 / 文件夹才有档可选（文本与单文件不摆，见 modeOptionsFor）
        if (modeOptions.isNotEmpty()) {
            DropdownMenuItem(
                text = { Text(Str.get(R.string.cmp_mode_title), color = hintColor) },
                enabled = false,
                onClick = {}
            )
            modeOptions.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(Str.get(modeLabelRes(opt))) },
                    leadingIcon = {
                        if (mode == opt) Icon(Icons.Default.Check, null, tint = tick)
                        else Spacer(Modifier.width(24.dp))
                    },
                    onClick = {
                        onModeChange(opt)
                        open = false
                    }
                )
            }
            // 档位是干什么的，得有人说明 —— 收进菜单里也得看得见
            DropdownMenuItem(
                text = {
                    UnifiedCaptionText(
                        text = Str.get(modeHintRes(mode)),
                        color = hintColor
                    )
                },
                enabled = false,
                onClick = {}
            )
        }

        // 文本口径：只有文本对比有意义（e15 P2-14）—— 归档/文件夹那边
        // 「只看差异」本来就是默认，「忽略大小写 / 空白」比的是文本行
        if (kind == CompareKind.TEXT) {
            DropdownMenuItem(
                text = { Text(Str.get(R.string.cmp_options_title), color = hintColor) },
                enabled = false,
                onClick = {}
            )
            DropdownMenuItem(
                text = { Text(Str.get(R.string.cmp_only_diff)) },
                leadingIcon = {
                    if (onlyDiff) Icon(Icons.Default.Check, null, tint = tick)
                    else Spacer(Modifier.width(24.dp))
                },
                onClick = { onOnlyDiffChange(!onlyDiff) }
            )
            DropdownMenuItem(
                text = { Text(Str.get(R.string.cmp_ignore_case)) },
                leadingIcon = {
                    if (ignoreCase) Icon(Icons.Default.Check, null, tint = tick)
                    else Spacer(Modifier.width(24.dp))
                },
                onClick = { onIgnoreCaseChange(!ignoreCase) }
            )
            DropdownMenuItem(
                text = { Text(Str.get(R.string.cmp_ignore_space)) },
                leadingIcon = {
                    if (ignoreSpace) Icon(Icons.Default.Check, null, tint = tick)
                    else Spacer(Modifier.width(24.dp))
                },
                onClick = { onIgnoreSpaceChange(!ignoreSpace) }
            )
        }
    }
}

@Composable
private fun CompareBody(
    left: File,
    right: File,
    rows: List<DiffRow>,
    kind: CompareKind,
    commonCount: Int,
    onlyDiff: Boolean,
    /** 查看模式（e11）：`false` = 并排两栏，`true` = 单栏（改动红绿上下各一条）。 */
    unified: Boolean,
    recomputing: Boolean,
    modifier: Modifier = Modifier
) {
    val raw = remember(rows) {
        CmpCounts(
            same = rows.count { it.kind == RowKind.SAME },
            changed = rows.count { it.kind == RowKind.CHANGED },
            onlyLeft = rows.count { it.kind == RowKind.ONLY_LEFT },
            onlyRight = rows.count { it.kind == RowKind.ONLY_RIGHT }
        )
    }
    // 「相同」在**只列差异**的那两种表里数不出来 —— 表里压根没有 SAME 行，只能拿
    // 「两边都有的」减掉「不同的」补回来。归档对比上线时这里恒报 0（汇总那行写着
    // 「0 个条目相同」），加文件夹对比时一并修掉。
    val counts = if (commonCount >= 0) {
        raw.copy(same = (commonCount - raw.changed).coerceAtLeast(0))
    } else {
        raw
    }
    // 全相同 = 一个「不同」、一边倒的都没有。**别写成 counts.same == rows.size**：
    // 只列差异的那两种表 rows.size 就是差异数，补出来的「相同」一比就反了
    val identical = counts.changed == 0 && counts.onlyLeft == 0 && counts.onlyRight == 0
    // 「只看差异」筛过的行。原样时直接引用原列表，不另拷一份 —— 5 万行的 list 不该白拷
    val shown = remember(rows, onlyDiff) {
        if (onlyDiff) rows.filter { it.kind != RowKind.SAME } else rows
    }
    // 「上一处 / 下一处」跳的是**差异行**在 shown 里的下标（只看差异时就是全部行）
    val diffIndexes = remember(shown) {
        shown.indices.filter { shown[it].kind != RowKind.SAME }
    }
    // e11「跳转后要高亮」：点上一处/下一处之后落在哪一行，就把那一行标出来 ——
    // 列表滚了一屏，不标的话用户得自己数行数才知道停在了第几处。
    // **只在跳转时写**（不跟着滚动写），所以不会把这里拖成每帧重组；
    // 换数据或换「只看差异」就清空，免得留一个指向旧行的高亮。
    var jumpTo by remember(rows, onlyDiff) { mutableStateOf(-1) }

    // 左右两列共用一个横向滚动状态，跟十六进制页同理：各滚各的立刻对不齐
    val sharedScroll = rememberScrollState()
    val titleColor = AppColors.textPrimary()

    val listState = rememberLazyListState()

    /**
     * 全表最宽的正文像素宽（d18）。
     *
     * 共用一个 `ScrollState` 意味着**每个可见行都在往同一个 `maxValue` 上写** ——
     * 那是 measure 期间写的，最后测到的那行说了算：各行宽度不一致就互相覆盖，
     * 末尾恰是短行时 `maxValue` 被写成 0，**整屏横向锁死**（长行被裁 + 划不动）。
     * 把所有行统一到最宽那一行，大家写的值就相同，顺带得到编辑器的常规语义：
     * 横滚是全局的，短行右边露出的是空白，而不是各滚各的。
     * 只增不减，收敛在几次重排之内。
     */
    var widestPx by remember(rows) { mutableStateOf(0) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Str.get(R.string.cmp_left) + " · " + left.name,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = Str.get(R.string.cmp_right) + " · " + right.name,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 汇总从两档拆成四档（c16）：新增与删除原先混在「不同」里，看不出改动的方向
        // 归档模式换「条目」口径（e15 P2-14）：报「3 行不同」会让人去找那三行文本，
        // 而它其实是三个条目
        val sameRes = countRes(kind, R.string.cmp_same, R.string.cmp_arc_same, R.string.cmp_dir_same)
        val changedRes =
            countRes(kind, R.string.cmp_changed, R.string.cmp_arc_changed, R.string.cmp_dir_changed)
        val addedRes =
            countRes(kind, R.string.cmp_added, R.string.cmp_arc_added, R.string.cmp_dir_added)
        val removedRes =
            countRes(kind, R.string.cmp_removed, R.string.cmp_arc_removed, R.string.cmp_dir_removed)
        UnifiedCaptionText(
            text = Str.get(sameRes, counts.same) + " · " +
                Str.get(changedRes, counts.changed) + " · " +
                Str.get(addedRes, counts.onlyRight) + " · " +
                Str.get(removedRes, counts.onlyLeft),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textAlign = TextAlign.Center
        )

        // 跳转：上一处 / 当前第几处 / 下一处（c16）。逻辑与「滚动时的重组」都关在
        // [JumpBar] 自己屋里，别让每次滚动都拖着整个 CompareBody 一起重组。
        JumpBar(
            listState = listState,
            diffIndexes = diffIndexes,
            titleColor = titleColor,
            onJumped = { jumpTo = it }
        )

        // 对比深度三档与口径三开关**已挪进右上角「⋮」菜单**（e11）：原先这两排胶囊
        // 各占一行摆在正文顶上，标题 / 汇总 / 跳转条 / 两排胶囊 / 列表挤在一屏，
        // 列表只剩半截 —— MP 那边这类功能也是收进右上角菜单的。现在正文只剩
        // 「左右文件名 + 汇总 + 跳转 + 列表」，跳转条与列表都宽了一截。

        // 口径重算期间不换页（见 CompareRoot）：旧结果留着，只压一条细进度条。
        // 没有它的话，开关拨完列表纹丝不动，用户同样会当成「点了没反应」。
        if (recomputing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = AppColors.outlineVariant()
            )
        }

        if (identical) {
            Text(
                text = Str.get(
                    when (kind) {
                        CompareKind.ARCHIVE -> R.string.cmp_arc_identical
                        CompareKind.DIR -> R.string.cmp_dir_identical
                        CompareKind.TEXT -> R.string.cmp_identical
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = AppColors.success(),
                textAlign = TextAlign.Center
            )
        }

        if (shown.isEmpty()) {
            // 只看差异时一条不剩（= 两份相同）：给句人话，别留一片空白
            if (!identical) {
                Text(
                    text = Str.get(R.string.cmp_no_diff),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 18.dp),
                    fontSize = 12.sp,
                    color = AppColors.textHint(),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // f13：竖条 + 横条。竖条直接复用文件列表那根（同一套手势与口径，
            // 省得再写一份、再修一遍）；横条是给 `sharedScroll` 用的 —— 一行能横滚
            // 出去好几屏，原先只能盲划，既不知道还能往右多少，也没法快速定位。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    // 补偿固定头部与屏幕底边的贴边感（首行贴着开关行、末行贴着屏幕底）
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shown.size) { index ->
                        DiffRowView(
                            row = shown[index],
                            scroll = sharedScroll,
                            unified = unified,
                            highlighted = index == jumpTo,
                            minBodyPx = widestPx,
                            onBodyWidth = { w -> if (w > widestPx) widestPx = w }
                        )
                    }
                }
                FastScrollBar(
                    state = listState,
                    interactive = true,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            CompareHScrollBar(
                state = sharedScroll,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== 横向滚动条（f13） ====================

/**
 * 正文的横向滚动条。
 *
 * 「文件对比」一行动辄几百个字符，横着能滚出好几屏；原先只有手势能划 ——
 * 既不知道右边还有多少，也没法快速定位。竖条那边复用文件列表那根，这根是它的横向
 * 对照物，口径完全一样：`maxValue` 是内容比视口多出来的像素，于是
 * 「拇指长 / 轨道长 = 视口 / 内容」，位置按 `value / 内容` 归一化。
 *
 * 没得横滚时整根不画（连高度都不占）；拖动与点按两套手势并存，
 * 与十六进制页一致 —— 拖不准就点，按哪儿到哪儿。
 */
@Composable
private fun CompareHScrollBar(state: ScrollState, modifier: Modifier = Modifier) {
    val content = state.maxValue + state.viewportSize
    if (state.maxValue <= 0 || state.viewportSize <= 0 || content <= 0) return

    val thumb = (state.viewportSize.toFloat() / content).coerceIn(0.08f, 1f)
    val head = (state.value.toFloat() / content).coerceIn(0f, 1f - thumb)
    val tail = (1f - head - thumb).coerceAtLeast(0f)
    // `weight` 必须 > 0：一屏装得下时拇指占满整条、头尾正好是 0，
    // 直接抛 `invalid weight; must be greater than zero`（竖条同款坑，见 HexScrollBar）
    val wHead = head.coerceAtLeast(0.001f)
    val wThumb = thumb.coerceAtLeast(0.001f)
    val wTail = tail.coerceAtLeast(0.001f)

    var barWidth by remember { mutableStateOf(0) }
    // 拖 1px 内容滚多少 px：拇指能走的距离 = 轨道 × (1 - 拇指占比)，
    // 对应内容能走的 maxValue。`rememberUpdatedState` 是因为手势 key 只写了 Unit。
    val dragScale by rememberUpdatedState(
        if (barWidth > 0) {
            state.maxValue.toFloat() / (barWidth * (1f - thumb)).coerceAtLeast(1f)
        } else 0f
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .onSizeChanged { barWidth = it.width }
            // 放在 padding 之前：整条 14dp 高都是热区，不只是中间那 4dp 细条
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    state.dispatchRawDelta(dragAmount.x * dragScale)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    if (barWidth <= 0) return@detectTapGestures
                    val frac = (at.x / barWidth).coerceIn(0f, 1f)
                    state.dispatchRawDelta(frac * state.maxValue - state.value)
                }
            }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(wHead))
        Box(
            modifier = Modifier
                .weight(wThumb)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.textHint().copy(alpha = 0.55f))
        )
        Spacer(Modifier.weight(wTail))
    }
}

/**
 * 「上一处 / 第几处 / 下一处」跳转条（d18 → e11）。
 *
 * 独立成一个 composable：它要读 `listState.firstVisibleItemIndex`，滚动时必然重组 ——
 * 关在自己屋里只重排这三颗按钮，不必把整个 CompareBody（四行头部 + 列表外框）拖下水。
 *
 * 「当前第几处」按**视口顶之上最近的一处差异**算（-1 = 还没看到第一处）。原先取的是
 * 「视口**前方**最近的一处」，两处会出错：
 *  · 还没到第一处时它给 0，点「下一处」直接跳到第二处 —— **第一处被跳过**；
 *  · 视口越过最后一处时它给 size-1，「下一处」提前变灰，而用户明明还在中间。
 * 还有更隐蔽的一种：视口停在两处差异之间时，按 clamp 算「下一处」等于原地不动，
 * 按钮却亮着 —— 正是反馈里的「点了没反应」。现在直接算**目标下标**，越界即没有，
 * 按钮灰掉，亮着就一定动。
 *
 * **e11 两处改动**：
 *  · **首尾绕回**：已是最后一处再点「下一处」→ 绕回**第一处**；已是第一处点「上一处」→
 *    绕回**最后一处**。原先越界就把按钮按灰，用户停在末尾时「下一处」看着像坏了。
 *    现在只有**一处差异都没有**时按钮才按不得（按了也是原地不动）。
 *  · **落点高亮**：跳到哪一处就把哪一行报给 [onJumped]，由调用方把那一行标出来 ——
 *    列表滚了一屏，不标的话用户得自己数行数才知道停在了第几处。
 */
@Composable
private fun JumpBar(
    listState: LazyListState,
    diffIndexes: List<Int>,
    titleColor: Color,
    /** 跳到了 [diffIndexes] 里的哪个**行下标**（不是第几处），由调用方高亮。 */
    onJumped: (Int) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // 视口**底边**落在第几行。
    //
    // f13「只能跳到差异 165/167」的根因就在这里：原先「当前第几处」只看视口**顶**，
    // 而列表末尾那几行永远推不到顶边（滚到底就没有空间再把最后一行顶上去了），
    // 于是计数停在倒数第几处，「下一处」也一直在同一个目标上打转 —— 跳过去还是同一屏，
    // 看着像坏了。改成同时参考底边，「已经看到哪儿」才说得清。
    val lastVisibleRow by remember(diffIndexes) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: listState.firstVisibleItemIndex
        }
    }
    // g13：「当前第几处」= 视口里**第一处**差异。
    //
    // 原先取的是视口里**最后一处**。跳到第 6 处后，一屏里往往同时能看到 6、7、8，
    // 计数立刻变成 8 —— 用户按一次「下一处」却从 6 跳到 8，看起来就是「突变好几个数」。
    // 改成第一处之后，跳过去显示的就是 6；再按一次只前进到 7。
    //
    // 视口里一处差异都没有时，取「视口上方最近的一处」当当前位（还没看到下一处）；
    // 还没到第一处则为 -1。
    //
    // h.txt 第 13 条「最后一个差异跳不过去」：差异密集时（一屏里同时有好几处），
    // 只看「视口第一处」会让当前位**卡在**第一处 —— 跳到第二处后它仍然可见，
    // 再按「下一处」又回到第二处，永远走不到最后。跳转成功后把目标**钉住**，
    // 直到它滚出视口（用户自己滚远了）才退回按视口计算。
    var jumpPos by remember(diffIndexes) { mutableStateOf(-1) }
    val currentIndex by remember(diffIndexes) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val lastVisible = lastVisibleRow
            val pinned = jumpPos
            if (pinned in diffIndexes.indices &&
                diffIndexes[pinned] in firstVisible..lastVisible
            ) {
                pinned
            } else {
                val firstInView = diffIndexes.indexOfFirst { it in firstVisible..lastVisible }
                if (firstInView >= 0) firstInView
                else diffIndexes.indexOfLast { it < firstVisible }
            }
        }
    }
    // 「上一处 / 下一处」严格按当前位的 **±1** 走，不再按视口边缘找 —— 一次只动一处。
    val prevTarget by remember(diffIndexes) {
        derivedStateOf {
            when {
                diffIndexes.isEmpty() -> -1
                currentIndex <= 0 -> diffIndexes.lastIndex   // 首尾绕回
                else -> currentIndex - 1
            }
        }
    }
    val nextTarget by remember(diffIndexes) {
        derivedStateOf {
            when {
                diffIndexes.isEmpty() -> -1
                currentIndex < 0 -> 0                        // 还没到第一处
                else -> (currentIndex + 1) % diffIndexes.size // 首尾绕回
            }
        }
    }

    fun jump(to: Int) {
        if (to !in diffIndexes.indices) return
        jumpPos = to
        val index = diffIndexes[to]
        onJumped(index)
        scope.launch { listState.animateScrollToItem(index) }
    }

    // 一处差异都没有时才按不得；其余情况两端都绕回，按钮永远有用
    val canJump = diffIndexes.isNotEmpty()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        UnifiedIconButton(
            icon = Icons.Default.NavigateBefore,
            onClick = { jump(prevTarget) },
            contentDescription = Str.get(R.string.cmp_prev_diff),
            tint = titleColor,
            enabled = canJump,
        )
        Text(
            text = if (diffIndexes.isEmpty()) Str.get(R.string.cmp_no_diff)
            else Str.get(R.string.cmp_diff_pos, (currentIndex + 1).coerceAtLeast(1), diffIndexes.size),
            fontSize = 12.sp,
            color = titleColor
        )
        UnifiedIconButton(
            icon = Icons.Default.NavigateNext,
            onClick = { jump(nextTarget) },
            contentDescription = Str.get(R.string.cmp_next_diff),
            tint = titleColor,
            enabled = canJump,
        )
    }
}

@Composable
private fun DiffRowView(
    row: DiffRow,
    scroll: ScrollState,
    /** 全表最宽正文像素宽（见 CompareBody 的 widestPx）。 */
    minBodyPx: Int,
    /** 上报本行正文宽度，由调用方取最大。 */
    onBodyWidth: (Int) -> Unit,
    /** 查看模式（e11）：`false` = 并排两栏；`true` = 单栏，改动红绿上下各一条。 */
    unified: Boolean = false,
    /** 跳转落点（e11「跳转后要高亮」）：这一行就是刚跳过来的那一处差异。 */
    highlighted: Boolean = false
) {
    val errorTint = AppColors.error().copy(alpha = 0.14f)
    val successTint = AppColors.success().copy(alpha = 0.16f)

    // 落点高亮（e11）：整行垫一层主题色，把「刚跳到这一处」标出来。
    // **叠在左右底色之上**而不是替换它 —— 只看差异时一行本来就有底色，
    // 替换掉就看不出「我跳到的到底是哪一处」了。只在 highlighted 时才加这个
    // modifier，其余行一个透明底色的 draw 都不必要。
    val highlightTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    val leftTint = when (row.kind) {
        RowKind.CHANGED, RowKind.ONLY_LEFT -> errorTint
        RowKind.SAME, RowKind.ONLY_RIGHT -> Color.Transparent
    }
    val rightTint = when (row.kind) {
        RowKind.CHANGED, RowKind.ONLY_RIGHT -> successTint
        RowKind.SAME, RowKind.ONLY_LEFT -> Color.Transparent
    }

    // 行内差异（c16）：只对「左右都改了」的行算，把中间那一段单独加深色 ——
    // 整行只给底色时，一行几十个字里到底改了哪个字还是得自己逐字比
    val leftMark = if (row.kind == RowKind.CHANGED) row.right else null
    val rightMark = if (row.kind == RowKind.CHANGED) row.left else null
    val leftChange = AppColors.error().copy(alpha = 0.45f)
    val rightChange = AppColors.success().copy(alpha = 0.45f)
    val dividerColor = AppColors.outlineVariant()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 落点高亮垫在最底下。放在 drawBehind **之前**：background 先画、
            // 分隔线后画，线仍压在高亮之上（顺序反了高亮会把线糊掉）
            .then(if (highlighted) Modifier.background(highlightTint) else Modifier)
            // 栏间分隔线。**不能用 fillMaxHeight 的 Box**：lazy item 在滚动方向拿到的是
            // 无限高约束，fillMaxHeight 撑不起来、Box 高度恒为 0，线根本画不出来
            // （与十六进制页同款结论）。画在整行自己的实际尺寸上才画得出来。
            // **单栏模式不画**：只有左右两栏时中间那条才有意义，单栏是上下堆叠。
            .drawBehind {
                if (!unified) {
                    val x = size.width / 2f
                    drawLine(
                        color = dividerColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
    ) {
        if (unified) {
            // 单栏（e11 查看模式）：一行改动拆成上下两条 —— 红在上（左/删）、
            // 绿在下（右/增），行号各自跟自己那条走。相同行只画一条。
            // 宽屏上并排更好对照，但窄屏左右各只剩半行，长行被截得看不出改了哪儿；
            // 单栏一行就是一整行，配合行内差异段反而更好读。
            when (row.kind) {
                RowKind.SAME -> LineCell(
                    no = row.leftNo,
                    text = row.left,
                    tint = leftTint,
                    scroll = scroll,
                    minBodyPx = minBodyPx,
                    onBodyWidth = onBodyWidth,
                    modifier = Modifier.fillMaxWidth()
                )

                RowKind.CHANGED -> {
                    LineCell(
                        no = row.leftNo,
                        text = row.left,
                        tint = leftTint,
                        mark = leftMark,
                        changeTint = leftChange,
                        scroll = scroll,
                        minBodyPx = minBodyPx,
                        onBodyWidth = onBodyWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LineCell(
                        no = row.rightNo,
                        text = row.right,
                        tint = rightTint,
                        mark = rightMark,
                        changeTint = rightChange,
                        scroll = scroll,
                        minBodyPx = minBodyPx,
                        onBodyWidth = onBodyWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                RowKind.ONLY_LEFT -> LineCell(
                    no = row.leftNo,
                    text = row.left,
                    tint = leftTint,
                    scroll = scroll,
                    minBodyPx = minBodyPx,
                    onBodyWidth = onBodyWidth,
                    modifier = Modifier.fillMaxWidth()
                )

                RowKind.ONLY_RIGHT -> LineCell(
                    no = row.rightNo,
                    text = row.right,
                    tint = rightTint,
                    scroll = scroll,
                    minBodyPx = minBodyPx,
                    onBodyWidth = onBodyWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            LineCell(
                no = row.leftNo,
                text = row.left,
                tint = leftTint,
                mark = leftMark,
                changeTint = leftChange,
                scroll = scroll,
                minBodyPx = minBodyPx,
                onBodyWidth = onBodyWidth,
                modifier = Modifier.weight(1f)
            )
            LineCell(
                no = row.rightNo,
                text = row.right,
                tint = rightTint,
                mark = rightMark,
                changeTint = rightChange,
                scroll = scroll,
                minBodyPx = minBodyPx,
                onBodyWidth = onBodyWidth,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 行内差异段：把 [text] 切成「与对方相同的前缀 + 中间不同的那段 + 相同的后缀」。
 *
 * 前后缀用逐字比对就能切出来，不必再跑一次字符级 LCS —— 行级 diff 已经把「哪两行是一对」
 * 定死了，这里只回答「这一对里改了哪个字」，剥公共前后缀正是这个问题的标准解法，
 * 而且是 O(行长)，跟着每行渲染跑都不心疼。
 *
 * 返回三段（或更少）：相同段 `false`、不同段 `true`。相同段不着色，只有中间那段上底色。
 */
private fun lineSegments(text: String, other: String): List<Pair<String, Boolean>> {
    val max = minOf(text.length, other.length)
    var prefix = 0
    while (prefix < max && text[prefix] == other[prefix]) prefix++
    var suffix = 0
    while (suffix < max - prefix &&
        text[text.length - 1 - suffix] == other[other.length - 1 - suffix]
    ) {
        suffix++
    }
    val midEnd = text.length - suffix
    val segs = ArrayList<Pair<String, Boolean>>(3)
    if (prefix > 0) segs.add(text.substring(0, prefix) to false)
    if (midEnd > prefix) segs.add(text.substring(prefix, midEnd) to true)
    if (suffix > 0) segs.add(text.substring(midEnd) to false)
    return segs
}

/**
 * 半边：行号钉死不参与横滚，只有正文跟着 [scroll] 走 —— 行号滚没了就没法定位了。
 *
 * 正文用 `maxLines = 1` 而不是换行：左右两列必须**行行等高**，一旦某边折行，
 * 两列从那一行起就全错位了。
 */
@Composable
private fun LineCell(
    no: Int,
    text: String?,
    tint: Color,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    /** 对面那一行的原文；非 null 时算行内差异并把改动段上色（c16）。 */
    mark: String? = null,
    /** 改动段的底色，由调用方按左右侧给（红/绿）。 */
    changeTint: Color = Color.Transparent,
    /** 全表最宽正文像素宽：把本行撑到这个宽度，各行写的 maxValue 才一致（d18）。 */
    minBodyPx: Int = 0,
    /** 上报本行正文的实际宽度，由调用方取最大值。 */
    onBodyWidth: (Int) -> Unit = {}
) {
    val numberColor = AppColors.textHint()
    val bodyColor = AppColors.textPrimary()
    val density = LocalDensity.current

    // 正文统一按 AnnotatedString 拼：没有 mark 时等价于纯文本，有 mark 时把
    // lineSegments 切出来的「不同段」上一层底色。记得住的键是内容本身 ——
    // 行没变就不必每次滚动都重切一遍
    val body = remember(text, mark, changeTint) {
        val raw = text ?: ""
        buildAnnotatedString {
            if (mark == null || raw.isEmpty()) {
                append(raw)
            } else {
                for ((seg, changed) in lineSegments(raw, mark)) {
                    if (changed && seg.isNotEmpty()) {
                        withStyle(SpanStyle(background = changeTint)) { append(seg) }
                    } else {
                        append(seg)
                    }
                }
            }
        }
    }

    Row(
        modifier = modifier
            .background(tint)
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = if (text == null) "" else no.toString(),
            modifier = Modifier
                .width(38.dp)
                .padding(start = 4.dp),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = numberColor,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        Row(modifier = Modifier.horizontalScroll(scroll)) {
            Text(
                text = body,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = if (text == null) Color.Transparent else bodyColor,
                maxLines = 1,
                // 把实测宽度报出去，CompareBody 取全表最大值再回填给 minBodyPx ——
                // 所有行宽度一致，共用 ScrollState 才不会互相覆盖 maxValue（d18）
                onTextLayout = { result -> onBodyWidth(result.size.width) },
                modifier = Modifier.widthIn(
                    min = with(density) { minBodyPx.toDp() }
                )
            )
        }
    }
}
