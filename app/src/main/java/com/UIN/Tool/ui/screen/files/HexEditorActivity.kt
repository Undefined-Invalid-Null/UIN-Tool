// app/src/main/java/com/UIN/Tool/ui/screen/files/HexEditorActivity.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.NeuDefaults
import com.UIN.Tool.ui.components.unified.neuRaised
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

/**
 * 内置十六进制编辑器（6.3.0）。
 *
 * 每行 16 字节：左侧固定 8 位偏移，中间十六进制，右侧 ASCII。
 * 三处刻意的设计：
 *
 *  1. **改字节不换数组** —— 编辑写进 [overrides] 这张 `SnapshotStateMap`，原字节数组
 *     只当底稿。Compose 靠快照读自动重绘被改的那一格（改 1 格只重绘 1 格），
 *     而不是靠版本号把整屏 32MB 的行全部重算。
 *  2. **保存走临时文件 + rename** —— 直接 `writeBytes` 覆盖时若中途崩掉，文件就废了。
 *     Android 是 Linux，同目录 `rename(2)` 原子替换，读者永远看不到写了一半的文件。
 *  3. **分页读取，不再整读进内存**（n.txt 第 14 条）—— 底稿由 [HexDocument] 按 1MB
 *     一页从磁盘取，页面缓存只留最近几页。GB 级文件也能打开、滚动、查找、保存，
 *     堆占用与文件大小无关；与代码编辑器的窗口化文档同一思路（按需取页）。
 *
 * 只读写「用户点开的那一个文件」，不做任何目录遍历或批量操作。
 */
class HexEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            UINToolTheme {
                HexEditorRoot(
                    path = path,
                    onExit = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"

        fun start(context: Context, path: String) {
            context.startActivity(
                Intent(context, HexEditorActivity::class.java).apply {
                    putExtra(EXTRA_PATH, path)
                }
            )
        }
    }
}

// ==================== 读取 ====================

/**
 * 每行字节数（用户反馈 d7「16进制与字符要分别放左右」）。
 *
 * 分左右两栏后，**十六进制只剩半屏宽**：偏移 56dp + 8 格 ≈ 190dp，正好落进
 * 320dp 屏的左栏（`weight(2f)`）；再排 16 格就只有 8dp 一格，两个字符都塞不下。
 * 8 字节/行是「左右分栏 + 每格字号不缩」能同时成立的唯一档位 ——
 * 每格分到的宽度与分栏前的 16 格/行**完全相同**，所以字号、可点区域都不退化。
 */
private const val BYTES_PER_ROW = 8

/** [hex2] 用的字符表：`0123456789abcdef`。 */
private val HEX_DIGITS = CharArray(16) { "0123456789abcdef"[it] }

/**
 * 两位十六进制小写串（`%02x` 的等价物）。
 *
 * f12「滑动卡顿」：一屏几十行 × 每行 16 格，`"%02x".format(value)` 每格都要走一遍
 * `java.util.Formatter` —— 分配、区域设置查找、格式串解析，滚动时新进屏的行全在算它。
 * 查表拼字符数组：两次取下标 + 一次拷贝，没有格式化机器，快一个量级，
 * 输出与 `format` 完全一致（小写、补零）。
 */
private fun hex2(value: Int): String =
    charArrayOf(HEX_DIGITS[(value shr 4) and 0xF], HEX_DIGITS[value and 0xF]).concatToString()

/** n.txt 第 14 条：分页大小与页缓存数量（1MB × 6 ≈ 6MB 常驻，与文件大小无关）。 */
private const val HEX_PAGE_SIZE = 1024 * 1024
private const val HEX_PAGE_CACHE = 6

/** 全文查找每次顺序读入的块大小（1MB）。 */
private const val HEX_SCAN_CHUNK = 1024 * 1024

/** 偏移列宽：8 位十六进制 @10sp ≈ 48dp，加左内边距正好落在这。 */
private val OFFSET_WIDTH = 56.dp

/** 偏移字号。 */
private val OFFSET_SP = 10.sp

/**
 * 单元格字号。11sp 下两位十六进制约 13dp；左右分栏后左栏 `weight(2f)`，
 * 320dp 屏上 ≈189dp，扣掉 56dp 偏移分给 8 格 ≈16.6dp/格 —— 装得下 13dp 的两位数。
 * 「每行不超出屏幕」这条反馈能成立就靠这几个数字，改动前先算一遍。
 */
private val CELL_SP = 11.sp

/** 撤销栈上限。超出就丢最老的一条 —— 与主流编辑器一致，也顺带兜住内存。 */
private const val MAX_UNDO = 500

/** 复制上限：剪贴板塞不下几百 MB，超了直接说清楚，不硬塞。 */
private const val MAX_COPY_BYTES = 64 * 1024

/** 填充上限。每个字节在 `overrides` 里是一条 map 记录，几百万条会把内存吃光。 */
private const val MAX_FILL_BYTES = 64 * 1024

/**
 * 文本查找可选的编码（d7「MP 的十六进制编辑器有 selectable encodings」）。
 * 名字直接交给 `Charset.forName`，这五个都是 Android 自带的，不用额外带表。
 */
private val SEARCH_ENCODINGS = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "GBK", "ISO-8859-1")

/**
 * 命中数封顶。几 MB 的文件里同一个词能出现几十万次，那种规模下
 * 「第 N / M 处」既算不动也没人读 —— 到顶就停，够用来跳着看。
 */
private const val MAX_MATCH_COUNT = 999

/**
 * 滚动条三段（头/拇指/尾）的 `weight` 下限。
 *
 * Compose 的 `weight` 要求严格大于 0，而「所有行都在屏幕内」时拇指占满整列、
 * 头尾正好算成 0 —— 那就是崩溃日志里 `invalid weight; must be greater than zero` 的来源。
 */
private const val MIN_SCROLL_WEIGHT = 0.001f

/** 一次字节写入的撤销记录：[old] 为 null 表示改之前这一格**没被改过**（撤销要 remove）。 */
private data class ByteEdit(val index: Long, val old: Byte?)

private sealed interface HexLoad {
    class Ready(val doc: HexDocument) : HexLoad
    object Empty : HexLoad
    object Failed : HexLoad
}

/**
 * n.txt 第 14 条：**按页读取的十六进制底稿**。
 *
 * 旧实现把整份文件读进 `ByteArray`，32MB 以上直接拒绝打开。这里与代码编辑器的
 * 窗口化文档同口径 —— 打开只记文件长度，真正读某一行时才从磁盘取那一页：
 *  - 页大小 1MB，LRU 只留 [HEX_PAGE_CACHE] 页，堆占用恒定（≈6MB），GB 级文件也能开；
 *  - 读取走 `RandomAccessFile.seek + readFully`，与页缓存同一把锁，读到的页不会被踩；
 *  - 保存不经过这个对象，见 [writeAtomic] 的流式改写（边读原文件边写临时文件）。
 *
 * 页面按需读取意味着滚动时可能有极少量磁盘 IO，但一页 1MB 足够覆盖几十屏，实际
 * 只会在跨页时读一次。
 */
private class HexDocument private constructor(
    private val raf: RandomAccessFile,
    val size: Long
) {
    private val lock = Any()

    private val pages = object : LinkedHashMap<Long, ByteArray>(HEX_PAGE_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean =
            size > HEX_PAGE_CACHE
    }

    /** 取任意偏移的一字节（越界返回 0）。可被组合线程与扫描线程并发调用。 */
    fun get(index: Long): Byte {
        if (index < 0L || index >= size) return 0
        val pageIndex = index / HEX_PAGE_SIZE
        val offset = (index % HEX_PAGE_SIZE).toInt()
        val page = synchronized(lock) {
            pages[pageIndex] ?: loadPageLocked(pageIndex).also { pages[pageIndex] = it }
        }
        return page[offset]
    }

    /** 整段读入内存（调用方保证长度可控：复制/填充最多 64KB）。 */
    fun read(start: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        var done = 0
        while (done < length) {
            val at = start + done
            if (at < 0L || at >= size) break
            val pageIndex = at / HEX_PAGE_SIZE
            val offset = (at % HEX_PAGE_SIZE).toInt()
            val page = synchronized(lock) {
                pages[pageIndex] ?: loadPageLocked(pageIndex).also { pages[pageIndex] = it }
            }
            val n = minOf(length - done, page.size - offset)
            System.arraycopy(page, offset, out, done, n)
            done += n
        }
        return out
    }

    /** 顺序块读取（全文查找用）：返回实际读取的字节数，-1 = 到末尾。 */
    fun readChunk(start: Long, buffer: ByteArray): Int {
        if (start < 0L || start >= size) return -1
        val len = minOf(buffer.size.toLong(), size - start).toInt()
        synchronized(lock) {
            raf.seek(start)
            raf.readFully(buffer, 0, len)
        }
        return len
    }

    private fun loadPageLocked(pageIndex: Long): ByteArray {
        val start = pageIndex * HEX_PAGE_SIZE
        val len = minOf(HEX_PAGE_SIZE.toLong(), size - start).toInt().coerceAtLeast(0)
        val page = ByteArray(len)
        raf.seek(start)
        if (len > 0) raf.readFully(page, 0, len)
        return page
    }

    fun close() {
        runCatching { raf.close() }
    }

    companion object {
        fun open(file: File): HexDocument {
            val raf = RandomAccessFile(file, "r")
            return HexDocument(raf, raf.length())
        }
    }
}

/**
 * 打开文件：只建页读取器，不读内容 —— 长度 0 才算空，读不动才报失败。
 * n.txt 第 14 条去掉了「文件过大」这一档：大小不再限制能否打开。
 */
private fun loadHexFile(file: File): HexLoad {
    return try {
        val doc = HexDocument.open(file)
        if (doc.size <= 0L) {
            doc.close()
            HexLoad.Empty
        } else {
            HexLoad.Ready(doc)
        }
    } catch (e: Throwable) {
        AppLog.w("HexEditor", "open failed: ${file.absolutePath} (${e.message})")
        HexLoad.Failed
    }
}

// ==================== 写回 ====================

/**
 * 原子写回。
 *
 * 同目录建临时文件 → 写满 → `renameTo` 覆盖目标。Linux 的 `rename(2)` 是原子的，
 * 要么整个是新内容、要么还是老内容，不存在「写到一半」。
 *
 * 顺带保住可执行位：rename 出来的新文件是按 umask 新建的，`./script.sh` 这类
 * 文件会被抹掉 `+x`。读写位不用管 —— 能编辑就说明本来就可写。
 */
private fun writeAtomic(target: File, overrides: Map<Long, Byte>): Boolean {
    val dir = target.parentFile
    val keepExec = target.canExecute()
    val tmp = File(dir ?: return false, ".${target.name}.hexedit")
    // n.txt 第 14 条：**流式改写**，不再把整份文件复制进堆（大文件那份副本本身就是
    // 一次 OOM）。改动按偏移排序后逐块贴回读到的缓冲区，内存只有一个 1MB 块。
    return try {
        val keys = overrides.keys.sorted()
        var ki = 0
        FileInputStream(target).use { input ->
            tmp.outputStream().use { out ->
                val buffer = ByteArray(HEX_PAGE_SIZE)
                var position = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    while (ki < keys.size && keys[ki] < position) ki++
                    var kj = ki
                    while (kj < keys.size && keys[kj] < position + n) {
                        val k = keys[kj]
                        buffer[(k - position).toInt()] = overrides.getValue(k)
                        kj++
                    }
                    out.write(buffer, 0, n)
                    position += n
                }
            }
        }
        val renamed = tmp.renameTo(target)
        if (!renamed) {
            // 个别文件系统 rename 不覆盖目标，退回覆盖写（此时临时文件已完整）。
            val ok = runCatching {
                tmp.inputStream().use { ins -> target.outputStream().use { os -> ins.copyTo(os) } }
            }.isSuccess
            tmp.delete()
            if (ok && keepExec) target.setExecutable(true)
            ok
        } else {
            if (keepExec) target.setExecutable(true)
            true
        }
    } catch (e: Throwable) {
        AppLog.w("HexEditor", "save failed: ${target.absolutePath} (${e.message})")
        tmp.delete()
        false
    }
}

// ==================== 界面 ====================

@Composable
private fun HexEditorRoot(path: String, onExit: () -> Unit) {
    val file = remember(path) { File(path) }
    // LocalContext 必须在 composable 作用域里读；塞进 onClick 里读会拿不到正确值。
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var load by remember { mutableStateOf<HexLoad?>(null) }
    // 偏移 → 新值。只有改过的字节才在里面，save 时再合回底稿。
    // n.txt 14：偏移改成 Long —— 分页底稿不再受 2GB 的 Int 上限约束。
    val overrides = remember { mutableStateMapOf<Long, Byte>() }
    // 保存后要换一份新的页读取器（旧句柄指向被 rename 替换掉的旧 inode）。
    val docRef = remember { mutableStateOf<HexDocument?>(null) }

    LaunchedEffect(path) {
        val result = withContext(Dispatchers.IO) { loadHexFile(file) }
        docRef.value?.close()
        docRef.value = (result as? HexLoad.Ready)?.doc
        load = result
    }
    DisposableEffect(Unit) {
        onDispose { docRef.value?.close() }
    }

    val ready = load as? HexLoad.Ready
    val dirty = ready != null && overrides.isNotEmpty()

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = file.name,
                onBack = onExit,
                actions = {
                    if (dirty) {
                        UnifiedIconButton(
                            icon = Icons.Default.Save,
                            onClick = {
                                scope.launch {
                                    val snapshot = HashMap(overrides)
                                    val ok = withContext(Dispatchers.IO) {
                                        writeAtomic(file, snapshot)
                                    }
                                    if (ok) {
                                        // 保存成功：改动已落盘，重开一份页读取器再把 overlay 清掉，
                                        // 否则界面会继续显示旧 inode 的内容（改过的字节像是弹回去了）。
                                        val reloaded = withContext(Dispatchers.IO) { loadHexFile(file) }
                                        docRef.value?.close()
                                        docRef.value = (reloaded as? HexLoad.Ready)?.doc
                                        load = reloaded
                                        overrides.clear()
                                        AppToast.success(context, Str.get(R.string.saved))
                                    } else {
                                        AppToast.error(context, Str.get(R.string.hex_save_failed))
                                    }
                                }
                            },
                            contentDescription = Str.get(R.string.save),
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (val current = load) {
            null -> CenteredNote(Str.get(R.string.loading), padding, loading = true)

            HexLoad.Empty -> CenteredNote(Str.get(R.string.hex_empty), padding)
            HexLoad.Failed -> CenteredNote(Str.get(R.string.hex_read_failed), padding)

            is HexLoad.Ready -> HexEditorBody(
                doc = current.doc,
                overrides = overrides,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

/**
 * 居中提示。`loading = true` 时上方补一个**真在转的圈**（用户反馈 d9）：
 * 「还在读文件」是进行时，静止的文字看着像已经卡死了。
 */
@Composable
private fun CenteredNote(text: String, padding: PaddingValues, loading: Boolean = false) {
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

@Composable
private fun HexEditorBody(
    doc: HexDocument,
    overrides: SnapshotStateMap<Long, Byte>,
    modifier: Modifier = Modifier
) {
    // LocalContext 必须在组合作用域里读；塞进 onClick 里读会拿不到正确值。
    val context = LocalContext.current
    // h.txt 第 14 条：列宽只量一次。放进每行会让几十个可见行各建一个 TextMeasurer，
    // 快滚时开销比排版本身还大。
    val hexColumnWidth = rememberHexColumnWidth()
    // i.txt 第 9 条：字符栏的字距按屏幕剩余宽度撑开 —— 否则 8 个字符只占左半截，
    // 右缘留出一大片空白。只算一次（配置/列宽变化才重算），不进入每行的布局。
    val asciiLetterSpacing = rememberAsciiLetterSpacing(hexColumnWidth)
    var selected by remember { mutableStateOf<Long?>(null) }
    var byteInput by remember { mutableStateOf("") }
    var jumpInput by remember { mutableStateOf("") }

    // 用户反馈：多选。锚点 + 游标构成一个**连续选区** —— 十六进制编辑器里真正的
    // 需求是「选一段拿去复制 / 填充」，连续区间的内存是 O(1)；逐字节点选在大文件上
    // 会把选集本身撑爆（每字节在 overrides 里是一条 map 记录）。
    var anchor by remember { mutableStateOf<Long?>(null) }
    var multiMode by remember { mutableStateOf(false) }
    // 撤销栈用 SnapshotStateList 而不是 ArrayDeque：`undoStack.isNotEmpty()` 要能驱动
    // 按钮的 enabled，普通容器不是快照状态，改了页面不会重画。
    val undoStack = remember { mutableStateListOf<ByteEdit>() }
    var fillOpen by remember { mutableStateOf(false) }
    var fillInput by remember { mutableStateOf("") }

    // ── 查找 / 替换（d7：MP 的十六进制编辑器有 search + selectable encodings + replace）──
    var searchOpen by remember { mutableStateOf(false) }
    /** true = 按十六进制字节串找；false = 按文本找（用 [searchEnc] 编码成字节再比）。 */
    var searchHex by remember { mutableStateOf(true) }
    var searchEnc by remember { mutableStateOf(SEARCH_ENCODINGS.first()) }
    var searchInput by remember { mutableStateOf("") }
    var replaceInput by remember { mutableStateOf("") }
    /** 当前命中的起点；null = 还没找到。 */
    var matchAt by remember { mutableStateOf<Long?>(null) }
    /** 当前是第几处 / 一共有几处（封顶 [MAX_MATCH_COUNT]）。 */
    var matchNo by remember { mutableStateOf(0) }
    var matchTotal by remember { mutableStateOf(0) }
    /**
     * 当前模式的全部命中位置与对应模式串。**模式没变就复用** ——
     * 全表扫一次是 O(文件大小)，每敲一个字都扫一遍会让输入框卡住。
     */
    var matchHits by remember { mutableStateOf<List<Long>?>(null) }
    var matchPattern by remember { mutableStateOf<ByteArray?>(null) }
    /** 正在扫描全文（n.txt 14：大文件查找挪到 IO 线程，扫描期间给按钮一个禁用态）。 */
    var searchBusy by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val byteCount = doc.size
    val rowCount = ((byteCount + BYTES_PER_ROW - 1) / BYTES_PER_ROW).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    // o.txt 第 14 条：分页切换。一「页」= 当前屏幕上能看到的行数 —— 这正是手机上
    // 「翻一页」的自然含义，也不依赖固定行数（横竖屏、字号变化后依然对得上）。
    // 页码由列表首行实时推导：手指滚动时页码跟着走，不需要另存一份会过期的状态。
    var pageJumpOpen by remember { mutableStateOf(false) }
    var pageJumpInput by remember { mutableStateOf("") }
    var pageJumpError by remember { mutableStateOf(false) }
    val pageRows by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) }
    }
    val firstRow by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceAtLeast(0) }
    }
    val totalPages = ((rowCount + pageRows - 1) / pageRows).coerceAtLeast(1)
    val currentPage = (firstRow / pageRows + 1).coerceIn(1, totalPages)

    fun goPage(page: Int) {
        if (rowCount <= 0) return
        val target = ((page - 1) * pageRows).coerceIn(0, rowCount - 1)
        scope.launch { listState.animateScrollToItem(target) }
    }

    fun currentByte(index: Long): Byte = overrides[index] ?: doc.get(index)

    /** 当前选区；没选返回 null。多选关着时退化成单字节。 */
    fun selRange(): LongRange? {
        val a = anchor
        val s = selected
        if (a != null && s != null) return minOf(a, s)..maxOf(a, s)
        return s?.let { it..it }
    }

    fun setByte(index: Long, value: Byte) {
        if (index < 0L || index >= byteCount) return
        val old = overrides[index]
        if (old == value) return
        undoStack.add(ByteEdit(index, old))
        while (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        overrides[index] = value
    }

    fun undoOne() {
        if (undoStack.isEmpty()) return
        val e = undoStack.removeAt(undoStack.size - 1)
        if (e.old == null) overrides.remove(e.index) else overrides[e.index] = e.old
    }

    fun select(index: Long) {
        if (index !in 0 until byteCount) return
        if (multiMode) {
            // 多选开着：第一次点设锚点，之后每次点只挪游标，区间自己跟着长
            if (anchor == null) anchor = index
            selected = index
        } else {
            anchor = null
            selected = index
        }
        byteInput = ""
        // 从 ASCII 列点进来的目标行可能刚好在视口外。
        val row = (index / BYTES_PER_ROW).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == row }
        if (!visible) scope.launch { listState.animateScrollToItem(row) }
    }

    fun stepSelection(delta: Long) {
        val current = selected ?: return
        select((current + delta).coerceIn(0L, byteCount - 1))
    }

    fun jump(raw: String) {
        val text = raw.trim().removePrefix("0x").removePrefix("0X")
        if (text.isEmpty()) return
        // 十六进制编辑器里偏移一律按十六进制读；越界不报错，夹到最近的合法字节。
        // n.txt 14：偏移用 Long 解析 —— 大于 2GB 的文件也能直接跳过去。
        val value = text.toLongOrNull(16)?.coerceIn(0L, byteCount - 1) ?: return
        jumpInput = ""
        select(value)
        scope.launch {
            listState.animateScrollToItem(
                (value / BYTES_PER_ROW).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        }
    }

    fun copyToClipboard(text: String) {
        clipboard.setText(AnnotatedString(text))
        AppToast.success(context, Str.get(R.string.copied_to_clipboard))
    }

    /**
     * 复制 / 填充共用的前置检查：没选给提示，选区超限也给提示 ——
     * **不硬塞**，剪贴板和 overrides 都扛不住几百万条。
     * n.txt 14：区间用 LongRange（分页底稿的偏移不再受 Int 限制）。
     */
    fun guardRange(actionRes: Int, limit: Int): LongRange? {
        val r = selRange()
        if (r == null) {
            AppToast.warning(context, Str.get(R.string.hex_no_selection))
            return null
        }
        if (r.last - r.first + 1 > limit) {
            AppToast.warning(context, Str.get(actionRes, limit))
            return null
        }
        return r
    }

    fun copyHex() {
        val r = guardRange(R.string.hex_copy_too_large, MAX_COPY_BYTES) ?: return
        val sb = StringBuilder((r.last - r.first + 1).toInt() * 3)
        for (i in r) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(hex2(currentByte(i).toInt() and 0xFF))
        }
        copyToClipboard(sb.toString())
    }

    fun copyText() {
        val r = guardRange(R.string.hex_copy_too_large, MAX_COPY_BYTES) ?: return
        val sb = StringBuilder((r.last - r.first + 1).toInt())
        for (i in r) {
            val v = currentByte(i).toInt() and 0xFF
            sb.append(if (v in 0x20..0x7E) v.toChar() else '.')
        }
        copyToClipboard(sb.toString())
    }

    fun applyFill() {
        val r = guardRange(R.string.hex_fill_too_large, MAX_FILL_BYTES) ?: return
        val v = fillInput.toIntOrNull(16)
        if (v == null) {
            AppToast.warning(context, Str.get(R.string.hex_byte_hint))
            return
        }
        for (i in r) setByte(i, v.toByte())
        fillOpen = false
        fillInput = ""
    }

    // ==================== 查找 / 替换 ====================
    // n.txt 第 14 条：查找不再「整份复制进 ByteArray」当底稿 —— 分页底稿上按块扫描
    // （1MB + 模式串重叠），未保存改动按偏移叠进块内；扫描放 IO 线程，主线程只收结果。
    // 旧实现找 100MB 文件会先复制 100MB，再在主线程空转几秒，界面必然像卡死。

    /** 解析查找/替换串；解析不了返回 null（调用方给提示）。替换串走**同一套口径**。 */
    fun parsePattern(raw: String): ByteArray? {
        if (searchHex) {
            // 空格 / 逗号 / 冒号 / 连字符都当分隔符：`48 65 6c` 与 `48656c` 等价
            val cleaned = raw.replace(Regex("[\\s,:-]"), "")
            if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
            if (!cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            return ByteArray(cleaned.length / 2) {
                cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
        }
        if (raw.isEmpty()) return null
        return runCatching { raw.toByteArray(Charset.forName(searchEnc)) }.getOrNull()
    }

    fun matchesAt(buf: ByteArray, pat: ByteArray, at: Int): Boolean {
        for (j in pat.indices) if (buf[at + j] != pat[j]) return false
        return true
    }

    /**
     * 按块扫描全文（n.txt 14）。
     *
     * 每块与上一块的尾巴重叠 `pattern.size - 1` 字节，跨块命中不会漏；未保存改动按
     * 排序后的偏移贴回块缓冲，查找看到的是「改完之后」的字节。命中数封顶
     * [MAX_MATCH_COUNT]，与旧行为一致。**内部切到 IO**，调用方在协程里用。
     */
    suspend fun scanMatches(pattern: ByteArray, snapshot: Map<Long, Byte>): List<Long> =
        withContext(Dispatchers.IO) {
        val out = ArrayList<Long>()
        val n = pattern.size
        if (n == 0 || byteCount < n) return@withContext out
        val keys = snapshot.keys.toLongArray()
        keys.sort()
        var ki = 0
        val buffer = ByteArray(HEX_SCAN_CHUNK)
        var prevTail = ByteArray(0)
        var pos = 0L
        while (pos < byteCount) {
            ensureActive()
            val len = doc.readChunk(pos, buffer)
            if (len <= 0) break
            // 叠加本块范围内的未保存改动（keys 已排序，沿用指针顺序推进）。
            while (ki < keys.size && keys[ki] < pos) ki++
            var kj = ki
            while (kj < keys.size && keys[kj] < pos + len) {
                val k = keys[kj]
                buffer[(k - pos).toInt()] = snapshot.getValue(k)
                kj++
            }
            val base = pos - prevTail.size
            val combined = if (prevTail.isEmpty()) {
                buffer.copyOf(len)
            } else {
                ByteArray(prevTail.size + len).also {
                    System.arraycopy(prevTail, 0, it, 0, prevTail.size)
                    System.arraycopy(buffer, 0, it, prevTail.size, len)
                }
            }
            var i = 0
            val last = combined.size - n
            while (i <= last) {
                if (combined[i] == pattern[0] && matchesAt(combined, pattern, i)) {
                    out.add(base + i)
                    if (out.size >= MAX_MATCH_COUNT) return@withContext out
                }
                i++
            }
            prevTail = if (n <= 1) {
                ByteArray(0)
            } else {
                val take = minOf(n - 1, combined.size)
                combined.copyOfRange(combined.size - take, combined.size)
            }
            pos += len
        }
        out
    }

    /** 让命中表过期：模式或数据变了就该重扫。 */
    fun invalidateMatches() {
        matchHits = null
        matchPattern = null
        matchAt = null
        matchNo = 0
        matchTotal = 0
    }

    /** 拿到（并在需要时重算）命中表；模式非法时提示并返回 null。扫描在 IO 线程。 */
    suspend fun matches(): List<Long>? {
        val pat = parsePattern(searchInput)
        if (pat == null) {
            AppToast.warning(context, Str.get(R.string.hex_search_invalid))
            return null
        }
        val cached = matchHits
        if (cached != null && matchPattern?.contentEquals(pat) == true) return cached
        searchBusy = true
        // 快照在主线程拍好：扫描期间用户改动不该让 IO 线程去碰 Compose 快照状态。
        val snapshot = HashMap(overrides)
        val hits = try {
            scanMatches(pat, snapshot)
        } finally {
            searchBusy = false
        }
        matchPattern = pat
        matchHits = hits
        return hits
    }

    /** 把命中摆成选区并滚到那一行（左右两栏同时亮）。 */
    fun applyMatch(at: Long, len: Int) {
        // 只摆锚点与游标，不动 multiMode —— 多选开关是用户自己的选择，
        // 查一次不该顺手替他开/关（关着时同样能靠 selRange() 画出这段区间）。
        anchor = at
        selected = at + len - 1
        matchAt = at
        byteInput = ""
        val row = (at / BYTES_PER_ROW).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == row }) {
            scope.launch { listState.animateScrollToItem(row) }
        }
    }

    /** 上一处 / 下一处。没有当前命中时从头（或从尾）起步，到头绕回。 */
    fun startSearch(back: Boolean) {
        if (searchBusy) return
        scope.launch {
            val hits = matches() ?: return@launch
            val pat = matchPattern ?: return@launch
            if (hits.isEmpty()) {
                AppToast.info(context, Str.get(R.string.hex_search_not_found))
                return@launch
            }
            val cur = matchAt
            val idx = when {
                cur == null -> if (back) hits.size - 1 else 0
                back -> hits.indexOfLast { it < cur }.let { if (it >= 0) it else hits.size - 1 }
                else -> hits.indexOfFirst { it > cur }.let { if (it >= 0) it else 0 }
            }
            matchNo = idx + 1
            matchTotal = hits.size
            applyMatch(hits[idx], pat.size)
        }
    }

    /**
     * 替换命中（[all] = 全部）。
     *
     * **定长替换**：这份编辑器的底稿是定长文件，改长度等于改文件大小，而「插入 /
     * 删除字节」会把后面每一个偏移都推走 —— 没实现就明说（提示长度要一致），
     * 不做「看起来改了其实没改」的半截事。
     */
    fun startReplace(all: Boolean) {
        if (searchBusy) return
        scope.launch {
            val pat = parsePattern(searchInput)
            val rep = parsePattern(replaceInput)
            if (pat == null || rep == null) {
                AppToast.warning(context, Str.get(R.string.hex_search_invalid))
                return@launch
            }
            if (rep.size != pat.size) {
                AppToast.warning(context, Str.get(R.string.hex_replace_len))
                return@launch
            }
            val targets = if (all) {
                matches() ?: return@launch
            } else {
                var m = matchAt
                if (m == null) {
                    // 没有当前命中就先找一处 —— 免得「替换」点了没反应
                    val hits = matches() ?: return@launch
                    if (hits.isEmpty()) {
                        AppToast.info(context, Str.get(R.string.hex_search_not_found))
                        return@launch
                    }
                    m = hits.first()
                    matchNo = 1
                    matchTotal = hits.size
                    applyMatch(m, pat.size)
                }
                listOf(m)
            }
            if (targets.isEmpty()) {
                AppToast.info(context, Str.get(R.string.hex_search_not_found))
                return@launch
            }
            for (at in targets) for (j in pat.indices) setByte(at + j, rep[j])
            AppToast.success(context, Str.get(R.string.hex_replaced, targets.size))
            // 底稿与命中表都过期了：替换后的内容得重扫才对得上。
            invalidateMatches()
        }
    }

    fun openSearch() {
        invalidateMatches()
        searchOpen = true
    }

    // 选区在组合作用域里算好 —— 塞进 items{} 或 onClick 里读状态拿不到重组。
    val range = selRange()
    val selStart: Long = range?.first ?: -1L
    val selEnd: Long = range?.last ?: -1L
    val selIndex = selected

    Column(modifier.fillMaxSize()) {

        // ── 跳转 + 查找替换入口 ──
        // 同一行是刻意的：两者都是「按位置/内容移动视线」，分开两行只会多占一截。
        //
        // e9 修的误会：**跳转框右边原先只有一颗放大镜，点下去打开的却是「查找替换」**。
        // 敲完偏移，最近的那颗按钮看着就是「确认跳过去」，按下去却是另一回事；
        // 键盘上的动作键也是放大镜，更坐实了这个误会。现在：
        //   ① 跳转框自己带一颗「跳过去」的箭头，键盘动作键换成 Go（前往）；
        //   ② 查找替换单独一颗，图标换成它真正的样子（FindReplace），不再冒充搜索。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UnifiedTextField(
                value = jumpInput,
                onValueChange = { jumpInput = it },
                modifier = Modifier.weight(1f),
                placeholder = Str.get(R.string.hex_jump_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    // Go 的动作键是「前往」，Search 是放大镜 —— 这里是跳偏移，不是搜索
                    imeAction = ImeAction.Go
                ),
                // onGo 才是 Go 对应的回调；顺带留一份 onSearch，免得哪天动作键被改回去就失效
                keyboardActions = KeyboardActions(
                    onGo = { jump(jumpInput) },
                    onSearch = { jump(jumpInput) }
                )
            )
            // 跳转：敲完偏移按这里（键盘上的「前往」也是它）
            UnifiedIconButton(
                icon = Icons.Default.ArrowForward,
                onClick = { jump(jumpInput) },
                contentDescription = Str.get(R.string.hex_jump_hint),
                tint = AppColors.textSecondary(),
            )
            // 查找替换：图标与它的名字（hex_search = 查找替换）对上
            UnifiedIconButton(
                icon = Icons.Default.FindReplace,
                onClick = { openSearch() },
                contentDescription = Str.get(R.string.hex_search),
                tint = AppColors.textSecondary(),
            )
        }

        // ── 分页切换（o.txt 第 14 条）──
        // 上一页 / 页码（点击输入页码）/ 下一页。页码始终由首行推导，滚动时同步变化，
        // 不会出现「翻过了却还显示第 1 页」。
        if (rowCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                UnifiedIconButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    onClick = { goPage(currentPage - 1) },
                    contentDescription = Str.get(R.string.hex_page_prev),
                    tint = AppColors.textSecondary(),
                    enabled = currentPage > 1,
                    iconSize = 20.dp
                )
                Text(
                    text = Str.get(R.string.hex_page_indicator, currentPage, totalPages),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.textSecondary(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            pageJumpInput = currentPage.toString()
                            pageJumpError = false
                            pageJumpOpen = true
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                UnifiedIconButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    onClick = { goPage(currentPage + 1) },
                    contentDescription = Str.get(R.string.hex_page_next),
                    tint = AppColors.textSecondary(),
                    enabled = currentPage < totalPages,
                    iconSize = 20.dp
                )
            }
        }

        // ── 字节网格（右侧带滚动条）──
        // 用户反馈：加滑条。文件动辄几十万行，没有滑条只能一下下翻。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                // 右侧留出滚动条的位置，免得最后一列被拇指压住
                contentPadding = PaddingValues(end = 10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 行内容型一致：给 LazyColumn 一个复用提示，快速拖动时少重建节点
                items(rowCount, key = { it }, contentType = { "hexrow" }) { row ->
                    HexRow(
                        rowStart = row.toLong() * BYTES_PER_ROW,
                        doc = doc,
                        overrides = overrides,
                        hexColumnWidth = hexColumnWidth,
                        asciiLetterSpacing = asciiLetterSpacing,
                        cursor = selIndex ?: -1L,
                        selStart = selStart,
                        selEnd = selEnd,
                        onSelect = { select(it) }
                    )
                }
            }
            HexScrollBar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // ── 信息行：偏移 / 值 / 选区长度 ──
        // 用户反馈：底部输入框被压缩了。原来「偏移 116dp + 值 72dp + 输入框 + 两颗
        // 方向键」全塞一行，360dp 屏上输入框只剩 68dp。信息拆到上一行，
        // 下一行只留输入框和步进键，输入框宽度翻了三倍多。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface())
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UnifiedCaptionText(
                text = Str.get(R.string.hex_offset) + " " +
                    (selIndex?.toString(16)?.uppercase()?.padStart(8, '0') ?: "--"),
                modifier = Modifier.weight(1f)
            )
            UnifiedCaptionText(
                text = Str.get(R.string.hex_value) + " " +
                    (selIndex?.let { "%02x".format(currentByte(it)) } ?: "--"),
                modifier = Modifier.weight(1f),
                color = AppColors.textSecondary()
            )
            UnifiedCaptionText(
                text = Str.get(R.string.hex_selection) + " " +
                    (range?.let { it.last - it.first + 1 } ?: 0),
                modifier = Modifier.weight(1f),
                color = AppColors.textSecondary()
            )
        }

        // ── 编辑行：输入 + 步进 ──
        if (selIndex != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.surface())
                    .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UnifiedTextField(
                    value = byteInput,
                    onValueChange = { raw ->
                        val filtered = raw
                            .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                            .take(2)
                        byteInput = filtered
                        // 凑满两位立刻写入并清空，可以连续敲；**不自动跳下一字节** ——
                        // 编辑器里误覆盖比多按一次方向键糟得多。
                        if (filtered.length == 2) {
                            filtered.toIntOrNull(16)?.let { value ->
                                setByte(selIndex, value.toByte())
                            }
                            byteInput = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = Str.get(R.string.hex_byte_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { byteInput = "" })
                )
                UnifiedIconButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    onClick = { stepSelection(-1) },
                    contentDescription = null,
                    iconSize = 20.dp,
                )
                UnifiedIconButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    onClick = { stepSelection(+1) },
                    contentDescription = null,
                    iconSize = 20.dp,
                )
            }
        }

        // ── 动作行（两排，避免文字按钮挤成一团）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface())
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UnifiedButton(
                text = Str.get(R.string.hex_multi_select),
                onClick = {
                    multiMode = !multiMode
                    anchor = if (multiMode) selected else null
                },
                modifier = Modifier.weight(1f),
                variant = if (multiMode) ButtonVariant.Primary else ButtonVariant.Outlined,
                size = ButtonSize.Small
            )
            UnifiedButton(
                text = Str.get(R.string.select_all),
                onClick = {
                    multiMode = true
                    anchor = 0L
                    selected = byteCount - 1
                    scope.launch { listState.scrollToItem(0) }
                },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Outlined,
                size = ButtonSize.Small,
                enabled = byteCount > 0
            )
            UnifiedButton(
                text = Str.get(R.string.hex_copy_hex),
                onClick = { copyHex() },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Outlined,
                size = ButtonSize.Small,
                enabled = range != null
            )
            UnifiedButton(
                text = Str.get(R.string.hex_copy_text),
                onClick = { copyText() },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Outlined,
                size = ButtonSize.Small,
                enabled = range != null
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UnifiedButton(
                text = Str.get(R.string.hex_fill),
                onClick = { fillOpen = true },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Outlined,
                size = ButtonSize.Small,
                enabled = range != null
            )
            UnifiedButton(
                text = Str.get(R.string.undo),
                onClick = { undoOne() },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Outlined,
                size = ButtonSize.Small,
                enabled = undoStack.isNotEmpty()
            )
            UnifiedButton(
                text = Str.get(R.string.hex_discard_edits),
                onClick = {
                    overrides.clear()
                    undoStack.clear()
                },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Text,
                size = ButtonSize.Small,
                enabled = overrides.isNotEmpty()
            )
        }
    }

    // ── 跳转页码对话框（o.txt 第 14 条）──
    if (pageJumpOpen) {
        UnifiedDialog(
            onDismissRequest = { pageJumpOpen = false },
            title = Str.get(R.string.hex_page_jump_title),
            content = {
                UnifiedTextField(
                    value = pageJumpInput,
                    onValueChange = { raw ->
                        pageJumpInput = raw.filter { it.isDigit() }.take(9)
                        pageJumpError = false
                    },
                    label = Str.get(R.string.editor_page_input_hint, totalPages),
                    error = if (pageJumpError) {
                        Str.get(R.string.editor_page_input_error, totalPages)
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val page = pageJumpInput.toIntOrNull()
                            if (page == null || page < 1 || page > totalPages) {
                                pageJumpError = true
                            } else {
                                goPage(page)
                                pageJumpOpen = false
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.confirm),
                    onClick = {
                        val page = pageJumpInput.toIntOrNull()
                        if (page == null || page < 1 || page > totalPages) {
                            pageJumpError = true
                        } else {
                            goPage(page)
                            pageJumpOpen = false
                        }
                    },
                    variant = ButtonVariant.Primary
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { pageJumpOpen = false },
                    variant = ButtonVariant.Text
                )
            }
        )
    }

    // ── 填充对话框 ──
    if (fillOpen) {
        UnifiedDialog(
            onDismissRequest = { fillOpen = false },
            title = Str.get(R.string.hex_fill),
            content = {
                UnifiedTextField(
                    value = fillInput,
                    onValueChange = { raw ->
                        fillInput = raw
                            .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                            .take(2)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = Str.get(R.string.hex_byte_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { applyFill() })
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.confirm),
                    onClick = { applyFill() },
                    variant = ButtonVariant.Primary
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { fillOpen = false },
                    variant = ButtonVariant.Text
                )
            }
        )
    }

    // ── 查找 / 替换 ──
    // 用户反馈 d7：MP 的十六进制编辑器带查找、可选编码、替换。这里是**等长替换**版：
    // 见 [startReplace] 的说明（改长度会推走后面所有偏移，没实现就明说）。
    if (searchOpen) {
        val at = matchAt
        UnifiedDialog(
            onDismissRequest = { searchOpen = false },
            title = Str.get(R.string.hex_search),
            // 正文自己排版（里面有 `weight` 分行）**也自己滚**（下面那个 Column 的
            // verticalScroll）。传 `false` 让 UnifiedDialog 别再套一层滚动 ——
            // 两层竖滚 = 内层被量到无界高度 → 打开即崩（f12「hex 替换闪退」）。
            scrollContent = false,
            content = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 模式：十六进制 / 文本。一次只有一种口径，查找串与替换串共用。
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HexChip(
                            label = Str.get(R.string.hex_search_hex),
                            selected = searchHex,
                            onClick = {
                                searchHex = true
                                invalidateMatches()
                            }
                        )
                        HexChip(
                            label = Str.get(R.string.hex_search_text),
                            selected = !searchHex,
                            onClick = {
                                searchHex = false
                                invalidateMatches()
                            }
                        )
                    }

                    // 文本模式才要挑编码 —— 十六进制串本身就是字节，没有编码可言
                    if (!searchHex) {
                        Text(
                            text = Str.get(R.string.hex_encoding),
                            color = AppColors.textSecondary(),
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SEARCH_ENCODINGS.forEach { enc ->
                                HexChip(
                                    label = enc,
                                    selected = searchEnc == enc,
                                    onClick = {
                                        searchEnc = enc
                                        invalidateMatches()
                                    }
                                )
                            }
                        }
                    }

                    UnifiedTextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            invalidateMatches()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = Str.get(R.string.hex_search_hint),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (searchHex) KeyboardType.Ascii else KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { startSearch(false) })
                    )

                    UnifiedTextField(
                        value = replaceInput,
                        onValueChange = { replaceInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = Str.get(R.string.hex_replace_hint),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (searchHex) KeyboardType.Ascii else KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { startReplace(false) })
                    )

                    // n.txt 14：扫描在 IO 线程，按钮禁用之外再给一行状态 ——
                    // 大文件扫一遍要几秒，没有反馈会被当成没反应。
                    if (searchBusy) {
                        UnifiedCaptionText(
                            text = Str.get(R.string.loading),
                            textAlign = TextAlign.Start
                        )
                    }

                    // 命中位置：给「现在翻到哪」一个落点，也顺带说明第几处
                    UnifiedCaptionText(
                        text = if (at == null) {
                            ""
                        } else {
                            val head = Str.get(R.string.hex_search_at, "0x" + at.toString(16).uppercase())
                            if (matchTotal > 0) {
                                head + "  ·  " + Str.get(R.string.hex_search_pos, matchNo, matchTotal)
                            } else {
                                head
                            }
                        },
                        textAlign = TextAlign.Start
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        UnifiedButton(
                            text = Str.get(R.string.hex_search_prev),
                            onClick = { startSearch(true) },
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Outlined,
                            size = ButtonSize.Small,
                            enabled = searchInput.isNotEmpty() && !searchBusy
                        )
                        UnifiedButton(
                            text = Str.get(R.string.hex_replace),
                            onClick = { startReplace(false) },
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Outlined,
                            size = ButtonSize.Small,
                            enabled = searchInput.isNotEmpty() && !searchBusy
                        )
                    }

                    UnifiedButton(
                        text = Str.get(R.string.hex_replace_all),
                        onClick = { startReplace(true) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Outlined,
                        size = ButtonSize.Small,
                        enabled = searchInput.isNotEmpty() && !searchBusy
                    )
                }
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.hex_search_next),
                    onClick = { startSearch(false) },
                    variant = ButtonVariant.Primary,
                    enabled = searchInput.isNotEmpty() && !searchBusy
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { searchOpen = false },
                    variant = ButtonVariant.Text
                )
            }
        )
    }
}

/**
 * 查找弹窗里的小胶囊（模式 / 编码选择）。
 *
 * 用胶囊不用下拉框：选项只有 2~5 个，下拉要多点一次才看得见有哪些，胶囊一次
 * 全摊开，当前选中哪个也一眼可见。选中态走 primary描边 + 淡底，与整套主题一致。
 */
@Composable
private fun HexChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) AppColors.primary() else AppColors.outlineVariant()
    val textColor = if (selected) AppColors.primary() else AppColors.textSecondary()
    val shape = RoundedCornerShape(50)
    // v.txt 第 8 条（用户）：与新拟态标签统一 —— 未选中也浮起，选中保持描边。
    val neu = UIConfig.isNeumorphismEnabled()
    Box(
        modifier = Modifier
            .then(
                if (neu && !selected) {
                    Modifier.neuRaised(
                        shape,
                        UIConfig.shouldUseDarkTheme(),
                        NeuDefaults.Intensity.LIGHT,
                        cornerRadius = 50.dp,
                        backgroundColor = Color.Transparent,
                        offset = 3f
                    )
                } else Modifier
            )
            .clip(shape)
            // j.txt 第 3 条：选中态只留描边 + 文字，不再垫一层淡色底。
            .background(AppColors.dialogBackground())
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp
        )
    }
}

// ==================== 行渲染 ====================

/**
 * 量一次十六进制栏的宽度（h.txt 第 11 条）。
 *
 * 每行 8 字节 = 23 个等宽字符，字符栏 = 15 个。原先按 2 : 1 分，十六进制一侧
 * 多出一截空白、字符栏紧贴分隔线；这里按同一字体/字号（含 letterSpacing）
 * 实测「00 00 … 00」的宽度，左栏只占它真正需要的部分。
 */
@Composable
private fun rememberHexColumnWidth(): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = CELL_SP
    )
    return remember(density, style) {
        val sample = "00 ".repeat(BYTES_PER_ROW).trimEnd()
        with(density) {
            OFFSET_WIDTH + measurer.measure(
                text = sample,
                style = style,
                maxLines = 1,
                softWrap = false
            ).size.width.toDp() + 2.dp
        }
    }
}

/**
 * 字符栏的字距（i.txt 第 9 条）。
 *
 * 字符栏是 `weight(1f)`，会把整行剩余宽度都占掉；但 8 个字符本身只画一小段，
 * 右边就空出一大片。这里按「屏幕宽 − 左边距×2 − 滚动条 − 栏间距 − 十六进制列」
 * 量出字符栏真正可用的宽度，把差额均摊成字距 —— 文字会铺满右栏，阅读时也更容易
 * 与左侧十六进制对齐扫视。不改字号，避免把行高一起放大。
 */
@Composable
private fun rememberAsciiLetterSpacing(hexColumnWidth: Dp): TextUnit {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val style = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = CELL_SP
    )
    return remember(density, style, hexColumnWidth, screenWidth) {
        with(density) {
            val natural = measurer.measure(
                text = "0 ".repeat(BYTES_PER_ROW).trimEnd(),
                style = style,
                maxLines = 1,
                softWrap = false
            ).size.width.toDp()
            // 横向：内容左右各 12dp、列表右侧滚动条 10dp、字符栏左侧 8dp。
            val available = screenWidth - 24.dp - 10.dp - 8.dp - hexColumnWidth
            val extra = (available - natural).coerceAtLeast(0.dp)
            (extra.toPx() / BYTES_PER_ROW).toSp()
        }
    }
}

/**
 * 一行 8 字节，**左右两栏**：左栏 = 偏移 + 十六进制，右栏 = 对应字符
 * （用户反馈 d7「16进制与字符要分别放左右」）。
 *
 * 演进：最早是「偏移 + 16 格 hex + 16 格 ASCII」挤在一条 Row（实测 ≈800dp，
 * 手机屏只有 320~411dp，只能横向滚）；上一轮反馈「不要横滑」把它拆成**上下两条**；
 * 这一轮要的是**并排看** —— 拆上下只解决了宽度，没解决左右对照的阅读习惯。
 * 并排后左栏只剩半屏宽，排不下 16 格，于是每行改 8 字节（见 [BYTES_PER_ROW]：
 * 每格分到的宽度与拆分前**完全相同**，字号不缩、格子不变小）。
 *
 * 两栏各自按 `weight` 均分本栏宽度，左 2 : 右 1 —— 十六进制「XX」比字符宽一倍，
 * 按内容比例分才不会一边挤一边空；整行宽度恒等于容器宽度，屏再窄也不溢出。
 *
 * 每格单独可点：比「整段文字 + 坐标反推第几字节」可靠得多，后者要拿布局结果
 * 去算列号，遇到分组空格就得打补丁。
 */
@Composable
private fun HexRow(
    rowStart: Long,
    doc: HexDocument,
    overrides: SnapshotStateMap<Long, Byte>,
    hexColumnWidth: Dp,
    /** 字符栏的额外字距（i.txt 第 9 条：把右缘空白摊进字与字之间）。 */
    asciiLetterSpacing: TextUnit,
    cursor: Long,
    selStart: Long,
    selEnd: Long,
    onSelect: (Long) -> Unit
) {
    val count = minOf(BYTES_PER_ROW.toLong(), doc.size - rowStart).toInt()
    if (count <= 0) return

    val activeColor = AppColors.primary()
    val normalColor = AppColors.textPrimary()
    val dimColor = AppColors.textSecondary()
    val dividerColor = AppColors.outlineVariant()

    /** 0 = 普通，1 = 选区内，2 = 游标（当前正在编辑的那一格）。 */
    fun cellState(index: Long): Int = when {
        index == cursor -> 2
        index in selStart..selEnd -> 1
        else -> 0
    }

    fun bgOf(state: Int): Color = when (state) {
        2 -> activeColor.copy(alpha = 0.34f)
        1 -> activeColor.copy(alpha = 0.16f)
        else -> Color.Transparent
    }

    // f12「滑动很卡」的真正大头：原先**每个字节格是一个 Text + clickable** ——
    // 一行 32 个（hex 16 + ASCII 16），一屏三十行就是近千个布局节点，
    // 快滚时每帧都在组合/布局这些节点。这里改成**每行两块 Text**：
    //   · 文字用 AnnotatedString 一次性拼好，选中/游标只改 SpanStyle 的背景色；
    //   · 点击不再挂在格子上，整块文字按等宽字符位置反推第几格。
    // 视觉与行为不变（选中仍然逐格亮），节点数从 ~1000 降到 ~60。
    //
    // h.txt 第 14 条（大内容拖滑条卡）：绝大多数格子是「普通态」，原先也给每格套一个
    // SpanStyle（一行 16 个、一屏几百个对象）。现在普通格子直接 append 纯文本、颜色
    // 由 Text 的基准色给，只有游标/选中的少数格子才建 SpanStyle —— 快滚时几乎零 span。
    val hasOverrides = overrides.isNotEmpty()
    fun byteAt(index: Long): Int =
        ((if (hasOverrides) overrides[index] else null) ?: doc.get(index)).toInt() and 0xFF

    val hexText = buildAnnotatedString {
        for (i in 0 until BYTES_PER_ROW) {
            if (i > 0) append(' ')
            if (i < count) {
                val index = rowStart + i
                val st = cellState(index)
                val token = hex2(byteAt(index))
                if (st == 0) append(token) else withStyle(
                    SpanStyle(color = activeColor, background = bgOf(st))
                ) { append(token) }
            } else {
                append("  ")
            }
        }
    }
    val asciiText = buildAnnotatedString {
        for (i in 0 until BYTES_PER_ROW) {
            if (i > 0) append(' ')
            if (i < count) {
                val index = rowStart + i
                val value = byteAt(index)
                val st = cellState(index)
                val token = if (value in 0x20..0x7E) value.toChar() else '.'
                if (st == 0) append(token) else withStyle(
                    SpanStyle(color = activeColor, background = bgOf(st))
                ) { append(token) }
            } else {
                append(' ')
            }
        }
    }

    // h.txt 第 14 条：排版结果只给点击反推列号用，**不该是 snapshot state** ——
    // 写进 mutableStateOf 会让每个新行在排版后再重组一次，快滚时翻倍。
    val hexLayout = remember { AtomicReference<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val asciiLayout = remember { AtomicReference<androidx.compose.ui.text.TextLayoutResult?>(null) }

    // h.txt 第 11 条：左右栏宽度由 [rememberHexColumnWidth] 统一量出来。
    val cellStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = CELL_SP
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // ── 左栏：偏移 + 十六进制（d7「16进制与字符要分别放左右」）──
        Row(
            modifier = Modifier
                .width(hexColumnWidth)
                .padding(end = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                // 8 位是 4GB 内的偏移；更大的文件自然变长，不截断。
                text = rowStart.toString(16).uppercase().padStart(8, '0'),
                modifier = Modifier
                    .width(OFFSET_WIDTH)
                    .padding(top = 3.dp, start = 4.dp),
                fontSize = OFFSET_SP,
                fontFamily = FontFamily.Monospace,
                color = dimColor,
                maxLines = 1
            )
            Text(
                text = hexText,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
                    .pointerInput(rowStart, count) {
                        detectTapGestures { pos ->
                            // 每格 = "XX "（3 个等宽字符）。落在分隔空格上也归前一格。
                            val cell = (hexLayout.get()?.getOffsetForPosition(pos) ?: 0) / 3
                            onSelect(rowStart + cell.coerceIn(0, count - 1))
                        }
                    },
                onTextLayout = { hexLayout.set(it) },
                style = cellStyle,
                color = normalColor,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Start
            )
        }

        // ── 右栏：字符（逐格与左栏同一个字节下标，选中态两边同步亮）──
        Text(
            text = asciiText,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                .pointerInput(rowStart, count) {
                    detectTapGestures { pos ->
                        // ASCII 每格 = "c "（2 个等宽字符）
                        val cell = (asciiLayout.get()?.getOffsetForPosition(pos) ?: 0) / 2
                        onSelect(rowStart + cell.coerceIn(0, count - 1))
                    }
                }
                // 栏间分隔线画在这一块的实际尺寸上（LazyColumn 行内拿不到无限高）
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            onTextLayout = { asciiLayout.set(it) },
            style = cellStyle.copy(letterSpacing = asciiLetterSpacing),
            color = dimColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ==================== 滚动条 ====================

/**
 * 右侧滚动条（用户反馈：加滑条）。
 *
 * 不量像素 —— 用 `weight` 把「头 / 拇指 / 尾」三段按比例分掉整列高度，
 * 既省一次测量，也天然跟着容器高度走。行数不足以滚动时整根不画，
 * 免得在短文件上挂一根永远不动的死条。
 *
 * **e9「拖不动」的根因**：这根条原先**只画不给摸** —— 三段画得再准，一个指针处理
 * 都没有，手指按上去纹丝不动。现在补上与文件列表滚动条同一套手势（见下面的
 * `pointerInput`）：拖动期间每帧用 `dispatchRawDelta`（**非挂起**）直接推一发位移，
 * 不走 `scrollToItem` —— 那条路每次拖动都要起协程，快速拖会堆积协程、松手后还在追。
 */
/** 滚动条一帧要用的全部几何量。放在 derivedStateOf 里算，避免每帧重组整根条。 */
private data class HexBarInfo(
    val total: Int,
    val first: Int,
    val shown: Int,
    val viewportPx: Float,
    val contentPx: Float,
    val thumbPx: Float,
    val trackPx: Float,
    val head: Float,
    val thumb: Float,
    val tail: Float
)

@Composable
private fun HexScrollBar(state: LazyListState, modifier: Modifier = Modifier) {
    // f12：原先 `state.layoutInfo` 直接写在组合里 —— 每一帧滚动都会让整根滚动条重组。
    // 现在把「首行 / 可见行数 / 视口」这些只在**跨行**时才变的值收进 derivedStateOf，
    // 手指滑动过程中多数帧拿到的是同一个 BarInfo，组合直接跳过。
    val bar by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total <= 1 || visible.isEmpty()) return@derivedStateOf null
            val first = visible.first().index
            val shown = (visible.last().index - first + 1).coerceAtLeast(1)
            // 一屏装得下 = 没得滚，不画（文件小到一屏放得下时正是拖不动的观感）
            if (total <= shown) return@derivedStateOf null
            val viewportPx = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            if (viewportPx <= 0f) return@derivedStateOf null
            val contentPx = viewportPx * total / shown
            val thumbPx = (viewportPx * shown / total)
                .coerceAtLeast(1f)
                .coerceAtMost(viewportPx)
            val trackPx = (viewportPx - thumbPx).coerceAtLeast(1f)
            val thumb = (shown.toFloat() / total).coerceIn(0.05f, 1f)
            val head = (first.toFloat() / total).coerceIn(0f, 1f - thumb)
            val tail = (1f - head - thumb).coerceAtLeast(0f)
            HexBarInfo(total, first, shown, viewportPx, contentPx, thumbPx, trackPx, head, thumb, tail)
        }
    }
    val b = bar ?: return
    // weight 要求 > 0，任一段算成 0 都会抛 invalid weight
    val wHead = b.head.coerceAtLeast(MIN_SCROLL_WEIGHT)
    val wThumb = b.thumb.coerceAtLeast(MIN_SCROLL_WEIGHT)
    val wTail = b.tail.coerceAtLeast(MIN_SCROLL_WEIGHT)
    // m.txt 第 11 条：与字体查看器同一条修法 —— 拖动按位置**绝对跳转**，
    // 不再用放大的 dispatchRawDelta 逐帧甩动。十六进制视图动辄几十万行，
    // 旧算法拖 1px 就滚过上千行，每一帧都在整屏重排，正是拖动卡死的来源。
    // 现在一次拖动最多保留一个 scrollToItem，目标行没变直接跳过。
    val scope = rememberCoroutineScope()
    val barNow by rememberUpdatedState(b)
    val jumpJobRef = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val lastJumpIndex = remember { intArrayOf(-1) }

    fun jumpTo(y: Float, height: Float) {
        val info = barNow
        if (height <= 0f || info.total <= 0) return
        val track = (height - info.thumb * height).coerceAtLeast(1f)
        val frac = ((y - info.thumb * height / 2f) / track).coerceIn(0f, 1f)
        val target = (frac * (info.total - info.shown)).toInt()
            .coerceIn(0, (info.total - 1).coerceAtLeast(0))
        if (target == lastJumpIndex[0]) return
        lastJumpIndex[0] = target
        jumpJobRef[0]?.cancel()
        jumpJobRef[0] = scope.launch { state.scrollToItem(target) }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(18.dp)
            // 放在 padding 之前：整条 18dp 宽、整列高都是热区
            .pointerInput(b.total) {
                detectDragGestures(
                    onDragStart = { jumpTo(it.y, size.height.toFloat()) },
                    onDrag = { change, _ ->
                        change.consume()
                        jumpTo(change.position.y, size.height.toFloat())
                    },
                    onDragEnd = { lastJumpIndex[0] = -1 },
                    onDragCancel = { lastJumpIndex[0] = -1 }
                )
            }
            // 点轨道任意位置直接跳到那一档 —— 大文件里点按比拖更准
            .pointerInput(b.total) {
                detectTapGestures { offset -> jumpTo(offset.y, size.height.toFloat()) }
            }
            .padding(vertical = 4.dp)
    ) {
        Spacer(modifier = Modifier.weight(wHead))
        Box(
            modifier = Modifier
                .weight(wThumb)
                .fillMaxWidth(0.6f)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.textHint().copy(alpha = 0.55f))
        )
        Spacer(modifier = Modifier.weight(wTail))
    }
}
