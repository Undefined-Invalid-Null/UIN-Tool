// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/EditorViewModel.kt
package com.UIN.Tool.ui.screen.dev.editor

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 6.2.0 代码编辑器 ViewModel。
 *
 * 取代旧实现的三个结构性缺陷：
 *  1) 文件内容存在普通 remember 里 —— 旋转/进程回收即丢失 → 改由 VM 持有
 *  2) 用 HashMap<String,String> + Serializable 传给 Activity —— 有 TransactionTooLarge 风险
 *     → 改为只传路径/URI，内容按需读取
 *  3) 无保存语义 —— hasChanges 永不为 true → 改为 savedContent/content 对比
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "EditorVM"
        /** 文件树单目录最多列出的条目。 */
        const val MAX_DIR_ENTRIES = 500

        /** 批次54：超此大小的文件一律分页窗口打开（真分页；整读档编辑稳态内存不划算）。 */
        private const val WINDOW_OPEN_BYTES = 16L * 1024 * 1024
    }

    // ==================== 标签页 ====================
    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    /**
     * 6.2.0 批次46：分页窗口模式的**每标签页簿记**（不在 StateFlow 里 —— UI 信息
     * 全部经 [EditorTab.window] 暴露，这里只放重对象与 spool）。
     *  - [doc]：磁盘页索引（只读）；
     *  - [overlays]：改动页 spool（页号 0 起 → 该页改后全文）；
     *  - [baselines]：本会话已载入页的基线指纹（[pageBaselineFp]，8B/页；批次55 前存
     *    整页文本 —— 连续翻几百页即驻留上百 MB，在 512MB 堆机上必然 OOM）。
     */
    private class WindowState(
        val doc: WindowedDocument,
        // 复查三修：搜索供给（IO 线程）对 overlays 做整表复制、保存线程 clear、主线程读写
        // 三方并发 —— 普通 LinkedHashMap 会 CME/撕裂。与 baselines 同款并发容器。
        val overlays: MutableMap<Int, String> = ConcurrentHashMap(),
        // 批次55：finishWholeReplaceView 在 IO 线程写基线、主线程 flushWindowPage 并发读 —— 并发容器。
        val baselines: MutableMap<Int, Long> = ConcurrentHashMap(),
        /** 批次47：全文替换的磁盘 spool 目录（懒建；null = 尚未产生过替换）。 */
        var spoolDir: File? = null,
        /** spool 里已落盘的页号（0 起）—— 脏标记与清理用，避免每次遍历目录。 */
        val spoolPages: MutableSet<Int> = mutableSetOf()
    ) {
        /** 是否有未保存改动（手改页 + 全文替换页）。 */
        fun isDirty(): Boolean = overlays.isNotEmpty() || spoolPages.isNotEmpty()
    }

    /**
     * 批次55：页基线指纹 = 长度(32b) + hashCode(32b) 拼成 64 位 —— 脏判定与存整页
     * 基线文本语义一致（长度与哈希同时碰撞才会误判，实际不可达），内存 8B/页。
     */
    private fun pageBaselineFp(text: String): Long =
        (text.length.toLong() shl 32) or (text.hashCode().toLong() and 0xffffffffL)

    // 保存在裸后台线程写回，会与主线程的翻页/再保存并发触碰这两张表 —— 用并发容器。
    // （WindowState.baselines 为 ConcurrentHashMap：替换收尾在 IO 线程写、主线程 flush 读；
    //  overlays 仍只在主线程改，保存线程只读 spawn 前的快照；跨线程 _tabs 改写一律 .update。）
    private val windowState = ConcurrentHashMap<String, WindowState>()

    /** 批次46：正在后台建索引的路径 —— 防止连点同一文件开出重复窗口标签（只在主线程增删）。 */
    private val pendingWindowOpens = mutableSetOf<String>()

    /** 批次46：正在后台写回的标签 —— 防止双击保存起两个写线程抢临时文件。 */
    private val savingWindows: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 批次47：正在全文替换的标签 —— 与保存/翻页互斥（三者都会触碰页供给与 spool）。 */
    private val replacingWindows: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 批次47：全文搜索状态。批次53 起为**唯一**搜索路径（所有文件形态共用：
     *  窗口标签逐页扫，非窗口标签以整读内容单页扫）。 */
    data class WholeSearchUi(
        val query: String,
        val running: Boolean = false,
        val progress: Int = 0,
        val total: Int = 0,
        val truncated: Boolean = false,
        val boundary: Int = 0,
        val matches: List<WholeSearch.WholeMatch> = emptyList(),
        val error: Boolean = false,
        /** 批次53 复查：文档被编辑后结果的行/列可能已过期 —— 面板提示重新搜索
         *  （不自动重扫：GB 级文档每键一扫不可行；跳转侧另有夹界防越界）。 */
        val stale: Boolean = false
    )

    data class WholeReplaceUi(
        val running: Boolean = false,
        val progress: Int = 0,
        val replaced: Int = 0,
        val pages: Int = 0,
        val boundary: Int = 0,
        val done: Boolean = false,
        val error: String? = null,
        /** 批次53：非窗口（完整加载）文档的内存整档替换 —— 无「页」概念，完成文案不提页数。 */
        val inMemory: Boolean = false
    )

    /**
     * 批次53：**全量结果页**的状态 —— 与面板的前 500 条分开存（大列表不塞进面板
     * StateFlow，避免面板重组携带全量列表）；行内预览按需 [requestWholePreviews] 懒生成。
     * 批次57（用户）：搜索一趟**全量**收集、不再「加载更多」分批续扫 —— loading/progress/
     * error 三个续扫态字段随按钮一并移除。
     */
    data class WholeFullUi(
        val matches: List<WholeSearch.WholeMatch> = emptyList(),
        /** 匹配总数（与 [WholeSearchUi.total] 同源口径）。 */
        val total: Int = 0
    )

    /** 批次50：窗口渐进式打开的索引进度（tabId → 0..100）—— 底部左下角显示「扫描 N%」。 */
    private val _windowScanProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val windowScanProgress: StateFlow<Map<String, Int>> = _windowScanProgress.asStateFlow()

    /** 批次51：窗口标签的保存进度（tabId → 0..100；写盘映射 0..80、索引重建 80..100，完成即移除）。
     *  底部状态栏据此显示「保存中 N%」—— GB 级保存数十秒无反馈会被连点、被误判卡死。 */
    private val _windowSaveProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val windowSaveProgress: StateFlow<Map<String, Int>> = _windowSaveProgress.asStateFlow()

    private val _wholeSearch = MutableStateFlow<WholeSearchUi?>(null)
    val wholeSearch: StateFlow<WholeSearchUi?> = _wholeSearch.asStateFlow()

    private val _wholeReplace = MutableStateFlow<WholeReplaceUi?>(null)
    val wholeReplace: StateFlow<WholeReplaceUi?> = _wholeReplace.asStateFlow()

    /** 批次53：全量结果页（点击「全部结果」的对话框数据源）。 */
    private val _wholeFull = MutableStateFlow<WholeFullUi?>(null)
    val wholeFull: StateFlow<WholeFullUi?> = _wholeFull.asStateFlow()

    /** 批次53：全量结果页的行内预览缓存（列表下标 → 预览；可见范围懒加载，溢出整表清一次）。 */
    private val _wholePreviews = MutableStateFlow<Map<Int, String>>(emptyMap())
    val wholePreviews: StateFlow<Map<Int, String>> = _wholePreviews.asStateFlow()

    private var wholeSearchJob: kotlinx.coroutines.Job? = null
    private var wholeReplaceJob: kotlinx.coroutines.Job? = null
    /** 全文任务归属标签 —— 关/切标签时按归属精确取消，不误伤别的窗口标签。 */
    private var wholeSearchTabId: String? = null
    private var wholeReplaceTabId: String? = null
    // 批次53 复查：面板按**当前标签**过滤结果展示（whole 状态是全局单例，切标签后
    // 旧标签的结果不能再显示/跳转），对外只读暴露这两个簿记。
    /** 最近一次整档搜索所属标签（取消/新搜时同步改写）。 */
    val wholeSearchOwner: String? get() = wholeSearchTabId
    /** 最近一次整档替换所属标签（完成/失败后保留，用于面板过滤）。 */
    val wholeReplaceOwner: String? get() = wholeReplaceTabId

    /** 批次53：全量结果页的**代际闸门** —— 新搜索/取消即 +1，在途扫描/预览回调作废
     *（批次57 移除「加载更多」后，续扫 Job 不复存在）。 */
    @Volatile private var wholeGen = 0
    /** 批次55：替换代际 —— cancel/新发起即推进，旧协程的收尾横幅写入代际不匹配即丢弃。 */
    @Volatile private var wholeReplaceGen = 0
    /** 批次53：上一次全文检索的参数快照 —— 按需预览据此取页（跨线程读，全 volatile）。 */
    @Volatile private var wholeQuery = ""
    @Volatile private var wholeCase = false
    @Volatile private var wholeWord = false
    @Volatile private var wholeRegex = false
    @Volatile private var wholePageCount = 0
    @Volatile private var wholePageProvider: (suspend (Int) -> String)? = null
    /** 批次56：与 [wholePageProvider] 成对的绝对行基址（重建索引后随之换新，防「加载更多」行号错位）。 */
    @Volatile private var wholePageLineBase: ((Int) -> Int)? = null
    /** 批次53：按需预览的在途下标（并发集 —— 请求在主线程加、IO 协程 finally 移）。 */
    private val wholePreviewInFlight: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /**
     * 批次47：统一的页文本供给 —— 内存改动页 → 磁盘 spool → 原盘。
     * 搜索/替换/翻页/写回全部经此取页，保证「未保存的改动对全文可见」。
     */
    private fun windowPageText(st: WindowState, page0: Int): String {
        st.overlays[page0]?.let { return it }
        val dir = st.spoolDir
        if (dir != null && page0 in st.spoolPages) {
            try {
                return File(dir, "page_%06d.txt".format(page0)).readText(st.doc.charset)
            } catch (e: Exception) {
                AppLog.w(TAG, "spool read failed, falling back to disk: page=$page0 (${e.message})")
            }
        }
        return st.doc.readPage(page0)
    }

    /** 全文替换的一页改动落磁盘 spool（GB 级替换不进堆）；spool 版本是最新的，撤掉旧内存 overlay 防遮蔽。 */
    private fun windowWriteSpool(st: WindowState, tabId: String, page0: Int, text: String) {
        val dir = st.spoolDir
            ?: File(getApplication<Application>().cacheDir, "uin_spool/$tabId")
                .also { it.mkdirs(); st.spoolDir = it }
        File(dir, "page_%06d.txt".format(page0)).writeText(text, st.doc.charset)
        st.spoolPages.add(page0)
        st.overlays.remove(page0)
    }

    /** 删除 spool 目录并清空页号集合（保存成功 / 丢弃修改 / 关标签时调用）。 */
    private fun clearSpool(st: WindowState) {
        st.spoolDir?.let { dir ->
            try {
                dir.deleteRecursively()
            } catch (e: Exception) {
                AppLog.w(TAG, "spool cleanup failed: $dir (${e.message})")
            }
        }
        st.spoolDir = null
        st.spoolPages.clear()
    }

    /**
     * 批次53：清空全文附属状态（全量结果页 + 预览缓存 + 续扫任务），并推进代际使在途
     * 在途扫描/预览回调作废。任何让既有结果作废的路径（新搜索/替换/取消）都须调用。
     */
    private fun clearWholeExtras() {
        wholeGen++
        _wholeFull.value = null
        _wholePreviews.value = emptyMap()
        wholePreviewInFlight.clear()
    }

    /** 批次47：全文搜索 —— 窗口模式走磁盘分页；非窗口大文件走**单页整读**扫描。 */
    fun startWholeSearch(
        tabId: String, query: String,
        caseSensitive: Boolean, wholeWord: Boolean, regex: Boolean
    ) {
        val st = windowState[tabId]
        // 批次53：**空查询最先处理** —— 搜索一律整档后面板一打开就会送来空串清结果；
        // 若让它先过分页/内容守卫，空查询也会误报「建立分页索引」或「无匹配」。
        // 取消在途扫描 + 清结果即可，不需要任何文档簿记。
        if (query.isEmpty()) {
            wholeSearchJob?.cancel()
            _wholeSearch.value = null
            // 复查：空查询也撤下已完成/失败的替换横幅（进行中的不动 —— 运行指示不能凭空消失）。
            if (wholeReplaceJob?.isActive != true) _wholeReplace.value = null
            clearWholeExtras()
            return
        }
        // 批次54：大文档编辑期间 content 不逐键复制 —— 进搜索前先回灌一次最新文本
        //（空查询已早退，清空搜索框不再白付一次拷贝）。回灌会更新 _tabs，故在其后取 tab。
        refreshStaleContent(tabId)
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        // 复查：早退分支也要先掐掉在途扫描并清附属状态 —— 否则旧任务的结果稍后会盖过来
        //（残留结果 + 行号越界是崩溃链的一环）。
        wholeSearchJob?.cancel()
        // 复查修「早退状态被标签过滤藏住」：归属标签在这里就绑定 —— 面板按标签展示结果，
        // 不绑标签的早退状态（空文档「无匹配」等）会被调用点永远过滤掉。
        wholeSearchTabId = tabId
        // 窗口模式需有窗口簿记；非窗口需有整读内容 —— 两者都没有则无从扫描。
        // 批次53：搜索一律整档后小文件也走这里，空文档要给出「无匹配」而非静默无反馈。
        if (st == null && tab.content.isEmpty()) {
            clearWholeExtras()
            _wholeSearch.value = WholeSearchUi(query, total = 0)
            return
        }
        // 批次50：渐进式打开期间（标签已显示首页、索引仍在后台扫）页偏移还不准 —— 拒搜。
        // 复查修「拒搜状态误导」：不再复用 error=true（那会显示「搜索表达式无效」）——
        // 清空结果 + toast 说明原因即可（面板留「搜索」按钮可稍后重跑，见动作行逻辑）。
        if (tab.window != null && st == null) {
            clearWholeExtras()
            _wholeSearch.value = null
            emit("正在建立分页索引…请稍候再搜索")
            return
        }
        if (savingWindows.contains(tabId) || replacingWindows.contains(tabId)) {
            // 保存/替换进行中页供给在换代 —— 结果不可信，直接拒。复查补清附属状态 +
            // toast（原先静默 + 误导文案），同样留「搜索」按钮稍后重跑。
            clearWholeExtras()
            _wholeSearch.value = null
            emit("正在保存/替换…请稍候再搜索")
            return
        }
        _wholeSearch.value = WholeSearchUi(query, running = true)
        // 批次53 复查修「跨标签搜索清掉运行中替换状态」：旧替换**横幅**（已完成/失败）
        // 随新搜索作废 —— 但**运行中**的替换状态绝不能碰：A 标签替换没完、切到 B 标签
        // 搜索，会把 B 侧看到的 running 清掉，回到 A 只读锁随之失效，替换期间的编辑会被
        // 结果静默覆盖。运行中的替换横幅由调用点按标签过滤，B 标签本来也看不见。
        if (wholeReplaceJob?.isActive != true) _wholeReplace.value = null
        clearWholeExtras()
        val gen = wholeGen
        wholeQuery = query
        wholeCase = caseSensitive
        this.wholeWord = wholeWord
        this.wholeRegex = regex
        // 冻结内容快照 —— 非窗口大文件的整读扫描直接用它当单页（避免 sora 页内检索复制整档 OOM）。
        val snap = tab.content
        wholeSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val win = st
                // 非窗口大文件：整读 String 本就在内存，**单页扫描**最省 —— 零子串拷贝、
                // 零跨页界误判、absLine 天然正确（makeMatch 里 page0=0）。
                val pageCount = if (win != null) win.doc.totalPages else 1
                // 批次56：绝对行基址 —— 窗口档按真实行前缀和（分页行数不等后不可
                // 再用 页号×页宽）；整读单页 page0 恒为 0，两者等价。
                val lineBase: (Int) -> Int = if (win != null) {
                    win.doc::lineBase
                } else { p -> p * FileAccess.WINDOW_PAGE_LINES }
                // 批次52：并行分块扫描多协程并发取页 —— 供给改用**启动快照**（overlay/spool
                // 页号表是普通 HashMap/HashSet，主程翻页 flush 会并发写，活读即 UB）。
                // 快照口径 = 搜索启动时刻的文档状态，结果自洽；替换/保存本就与搜索互斥。
                val pageText: suspend (Int) -> String = if (win != null) {
                    val doc = win.doc
                    val overlays = HashMap(win.overlays)
                    val spoolPages = HashSet(win.spoolPages)
                    val spoolDir = win.spoolDir
                    { p ->
                        overlays[p]
                            ?: if (p in spoolPages && spoolDir != null) {
                                try {
                                    File(spoolDir, "page_%06d.txt".format(p)).readText(doc.charset)
                                } catch (e: Exception) {
                                    AppLog.w(TAG, "spool read failed, fallback disk: page=$p (${e.message})")
                                    doc.readPage(p)
                                }
                            } else doc.readPage(p)
                    }
                } else {
                    { snap }
                }
                wholePageProvider = pageText // 批次53：「加载更多」/按需预览复用同一快照供给器
                wholePageCount = pageCount
                wholePageLineBase = lineBase // 批次56：加载更多与首扫用同一行基址
                val res = WholeSearch.scan(
                    pageCount = pageCount,
                    pageText = pageText,
                    lineBase = lineBase,
                    query = query,
                    caseSensitive = caseSensitive,
                    wholeWord = wholeWord,
                    regex = regex,
                    collect = true,
                    onProgress = { pct, found ->
                        // 批次53 复查：代际守卫 —— 被取消的旧扫描回调不得再刷新搜索的状态。
                        if (gen == wholeGen) _wholeSearch.update {
                            it?.copy(running = true, progress = pct, total = found)
                        }
                    },
                    // 批次57（用户）：一趟**全量**收集紧凑匹配（去掉「加载更多」分批与
                    // 10 万批次/50 万硬上限）—— 预览留空，结果页按需生成。
                    fullFrom = 0,
                    fullLimit = Int.MAX_VALUE
                )
                if (gen != wholeGen) return@launch // 扫描期间已被更新的搜索/取消接管
                // 复查修「完成态抹掉过期标记」：扫描期间文档若被编辑（updateContent 置
                // stale），完成写入必须保留 —— 否则扫描横跨一次编辑的结果会显示成新鲜的。
                val wasStale = _wholeSearch.value?.stale == true
                _wholeSearch.value = WholeSearchUi(
                    query = query,
                    progress = 100,
                    total = res.total,
                    truncated = res.truncated,
                    boundary = res.boundary,
                    matches = res.matches,
                    stale = wasStale
                )
                _wholeFull.value = WholeFullUi(matches = res.full, total = res.total)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "whole search failed: $query", e)
                // 批次55：异常收尾核对代际 —— 被取消的旧扫描（如页文件被删）抛错时，
                // 不得把新搜索/其他标签的结果盖成「表达式无效」。
                if (gen == wholeGen) _wholeSearch.value = WholeSearchUi(query, error = true)
            }
        }
    }

    fun cancelWholeSearch() {
        wholeSearchJob?.cancel()
        wholeSearchJob = null
        wholeSearchTabId = null
        _wholeSearch.value = null
        clearWholeExtras()
    }

    /**
     * 批次53：全量结果页的**按需预览** —— 对下标 [from, from+count) 内尚未缓存的紧凑项，
     * 取页快照就地生成行内预览（[WholeSearch.previewFor]）。可见范围由列表滚动回调驱动，
     * 百万级结果不预生成预览；供给器线程安全（搜索启动快照），预览与结果同一时点口径。
     */
    fun requestWholePreviews(from: Int, count: Int) {
        val provider = wholePageProvider ?: return
        val cur = _wholeFull.value ?: return
        val gen = wholeGen
        val need = ArrayList<Pair<Int, WholeSearch.WholeMatch>>()
        val start = from.coerceAtLeast(0)
        val end = (from + count).coerceAtMost(cur.matches.size)
        for (i in start until end) {
            if (!_wholePreviews.value.containsKey(i) && wholePreviewInFlight.add(i)) {
                need.add(i to cur.matches[i])
            }
        }
        if (need.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val batch = HashMap<Int, String>(need.size * 2)
                for ((i, m) in need) {
                    currentCoroutineContext().ensureActive()
                    val text = try {
                        provider(m.page - 1)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.w(TAG, "preview page read failed: page=${m.page} (${e.message})")
                        ""
                    }
                    batch[i] = WholeSearch.previewFor(text, m.inPageLine, m.column, m.length)
                }
                if (gen != wholeGen) return@launch
                _wholePreviews.update { old ->
                    // 缓存溢出整表清一次（只留本批）——来回滚动 GB 级文档也不无界增长。
                    if (old.size > 3000) batch else old + batch
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                need.forEach { wholePreviewInFlight.remove(it.first) }
            }
        }
    }

    /**
     * 批次47：全文替换（窗口模式）—— 逐页改写落**磁盘 spool**，保存时才真正写文件；
     * 与保存/翻页互斥；跨页界匹配跳过并上报。取消后已完成的页保持一致状态（脏标记照常）。
     */
    fun startWholeReplace(
        tabId: String, query: String,
        caseSensitive: Boolean, wholeWord: Boolean, regex: Boolean,
        replacement: String
    ) {
        if (query.isEmpty()) return
        // 复查六修（死页锁绕过·替换侧）：死签的 doc/基线是新布局而 content 是旧布局 ——
        // 替换启动的 flush 会把旧布局当前页写进 overlays，finish 再拼接即坏文件；
        // rebuilt==null 死签更会拿旧偏移扫描已重写的新文件（整篇错位）。逐签硬拦，
        // 与「任意 openFile 解锁全局只读」脱钩（与 saveWindowed/jumpWindowPage 同款）。
        if (tabId in layoutDeadTabs) {
            emit("分页索引已失效，请关闭该标签后重新打开文件")
            return
        }
        // 批次53 复查：整档替换**全局单实例** —— 切标签不再取消替换后，可能对另一标签
        // 再发起一次；两个任务并行会互相踩 _wholeReplace 横幅/进度。等上一个结束再来。
        if (wholeReplaceJob?.isActive == true) {
            emit("正在全文替换…请稍候")
            return
        }
        // 批次55（对齐搜索侧）：空查询/单实例已先早退 —— 大文档回灌只在真的要替换时付费。
        refreshStaleContent(tabId)
        // 批次55：替换代际在发起时刻取号 —— 下面协程收尾写横幅前都要核对。
        val rGen = ++wholeReplaceGen
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val st = windowState[tabId]
        val win = tab.window
        if (win == null || st == null) {
            if (tab.window != null) {
                // 窗口标签但索引未就绪（批次50 渐进式打开中）—— 与搜索同款拒绝。
                emit("正在建立分页索引…请稍候再替换")
                return
            }
            // ---- 批次53：非窗口（完整加载）文档的**整档内存替换**（此前一律不提供，
            // 提示语里的「窗口模式」术语用户完全看不懂）：内容本就在 tab.content，
            // [WholeSearch.replaceInString] 单遍换新，经 contentRev 换新由编辑器重设文本。
            if (!replacingWindows.add(tabId)) {
                emit("正在全文替换…请稍候")
                return
            }
            cancelWholeSearch()
            _wholeReplace.value = WholeReplaceUi(running = true, inMemory = true)
            wholeReplaceTabId = tabId
            val snap = tab.content
            wholeReplaceJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val res = WholeSearch.replaceInString(
                        snap, query, caseSensitive, wholeWord, regex, replacement
                    ) ?: (snap to 0)
                    withContext(Dispatchers.Main) {
                        // 批次55：0 命中不置脏不 bump（内容没变，白给标签挂未保存点）；
                        // IO 协程回写改原子 update，防与主线程按键编辑交错吞输入。
                        if (res.second > 0) _tabs.update { tabs ->
                            tabs.map { t ->
                                if (t.id == tabId) {
                                    t.copy(
                                        content = res.first,
                                        edited = true,
                                        contentRev = t.contentRev + 1
                                    )
                                } else t
                            }
                        }
                    }
                    // 批次55：横幅写入核对代际 —— 本任务被取消后新的替换可能已接管横幅。
                    if (rGen == wholeReplaceGen) _wholeReplace.value = WholeReplaceUi(
                        replaced = res.second, done = true, progress = 100, inMemory = true
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    replacingWindows.remove(tabId)
                    if (rGen == wholeReplaceGen) _wholeReplace.value = null
                    throw e
                } catch (e: Exception) {
                    AppLog.e(TAG, "whole replace failed: $query", e)
                    replacingWindows.remove(tabId)
                    if (rGen == wholeReplaceGen) _wholeReplace.value = WholeReplaceUi(
                        done = true, inMemory = true, error = "替换失败: ${e.message ?: "IO 错误"}"
                    )
                } finally {
                    replacingWindows.remove(tabId)
                }
            }
            return
        }
        if (tabId in _rebuildingCharset.value) {
            emit("正在重新载入编码…请稍候再替换")
            return
        }
        if (savingWindows.contains(tabId)) {
            emit("正在保存…请稍候再替换")
            return
        }
        if (!replacingWindows.add(tabId)) {
            emit("正在全文替换…请稍候")
            return
        }
        // 批次53：替换会改写全文 —— 旧搜索结果/全量页/预览先行作废（替换后想看新结果就
        // 手动再搜，不再自动重扫「显示成功」刷屏）。
        cancelWholeSearch()
        val curPage0 = win.page - 1
        _wholeReplace.value = WholeReplaceUi(running = true)
        wholeReplaceTabId = tabId
        wholeReplaceJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 手改先行归位：当前页未刷改动 flush 进 overlay，再连同历史 overlay 一并
                // **迁进磁盘 spool** —— 替换扫描必须以「用户看到的文本」为准；落 spool 后
                // windowWriteSpool 会撤掉内存 overlay，旧版本不遮替换成果。
                flushWindowPage(tab, st)
                st.overlays.keys.toList().forEach { p ->
                    st.overlays[p]?.let { windowWriteSpool(st, tabId, p, it) }
                }
                // 批次52：并行分块改写多协程并发取页 —— 供给改用**启动快照**（spool 页号表是
                // 普通 HashSet，写出线程经 Mutex 加 add、读者不加锁并发遍历即 UB）。
                // 块边界页已在引擎内预读原文，快照口径与扫描启动时刻一致，跨缝判定不受写出影响。
                val snapDoc = st.doc
                val snapOverlays = HashMap(st.overlays)
                val snapSpoolPages = HashSet(st.spoolPages)
                val snapSpoolDir = st.spoolDir
                val snapText: suspend (Int) -> String = { p ->
                    snapOverlays[p]
                        ?: if (p in snapSpoolPages && snapSpoolDir != null) {
                            try {
                                File(snapSpoolDir, "page_%06d.txt".format(p)).readText(snapDoc.charset)
                            } catch (e: Exception) {
                                AppLog.w(TAG, "spool read failed, fallback disk: page=$p (${e.message})")
                                snapDoc.readPage(p)
                            }
                        } else snapDoc.readPage(p)
                }
                val res = WholeSearch.replace(
                    pageCount = st.doc.totalPages,
                    pageText = snapText,
                    query = query,
                    caseSensitive = caseSensitive,
                    wholeWord = wholeWord,
                    regex = regex,
                    replacement = replacement,
                    onProgress = { pct -> _wholeReplace.update { it?.copy(progress = pct) } },
                    onWritePage = { p, text -> windowWriteSpool(st, tabId, p, text) }
                )
                finishWholeReplaceView(tabId, st, curPage0)
                replacingWindows.remove(tabId)
                // 批次55：横幅写入核对代际 —— 被取消后新的替换可能已接管横幅。
                if (rGen == wholeReplaceGen) _wholeReplace.value = WholeReplaceUi(
                    replaced = res.replaced,
                    pages = res.modifiedPages,
                    boundary = res.boundary,
                    done = true,
                    progress = 100
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 取消留下的是「逐页一致」的半程状态 —— 已替换页在 spool、未处理页在原盘。
                runCatching { finishWholeReplaceView(tabId, st, curPage0) }
                    .onFailure { AppLog.e(TAG, "replace view reset failed: ${it.message}") }
                if (rGen == wholeReplaceGen) _wholeReplace.value = null
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "whole replace failed: $query", e)
                runCatching { finishWholeReplaceView(tabId, st, curPage0) }
                    .onFailure { AppLog.e(TAG, "replace view reset failed: ${it.message}") }
                if (rGen == wholeReplaceGen) _wholeReplace.value = WholeReplaceUi(
                    done = true, error = "替换失败: ${e.message ?: "IO 错误"}"
                )
            } finally {
                // 复查修「视图归位再抛异常时 replacingWindows 永久卡死该 tab」——
                // 保存/翻页/搜索/替换全被这个锁挡住。锁必须在 finally 里释放。
                replacingWindows.remove(tabId)
                // 批次53 复查修「关标签时替换仍在跑」的 spool 泄漏：closeTab 的 clearSpool
                // 先删、而取消是异步的 —— 取消标记生效前可能再写一轮把目录建回来（Linux
                // 允许对已 unlink 的句柄继续写，不报错）。标签已不在就按确定性路径兜底
                // 再删一次（原文件从未被替换触碰，泄漏的只会是 spool 目录）。
                if (_tabs.value.none { it.id == tabId }) {
                    val dir = st.spoolDir
                        ?: java.io.File(getApplication<Application>().cacheDir, "uin_spool/$tabId")
                    runCatching { dir.deleteRecursively() }
                        .onFailure { AppLog.w(TAG, "spool leak cleanup failed: $dir (${it.message})") }
                }
            }
        }
    }

    fun cancelWholeReplace() {
        // 批次55：取消方推进代际并**同步**收尾横幅 —— 被取消协程的收尾写入会被代际
        // 挡掉，修「关 A 标签的替换把 B 标签刚发的 running 横幅清掉 → B 失去只读保护」。
        wholeReplaceGen++
        wholeReplaceJob?.cancel()
        wholeReplaceJob = null
        wholeReplaceTabId = null
        _wholeReplace.value = null
    }

    /** 替换结束（含取消/失败）后的视图归位：当前页若被改写则换 spool 文本并 bump 版本。 */
    private fun finishWholeReplaceView(tabId: String, st: WindowState, curPage0: Int) {
        val currentRewritten = curPage0 in st.spoolPages
        val text = if (currentRewritten) windowPageText(st, curPage0) else null
        if (text != null) {
            // 基线跟进新真相 —— 后续 flush 与「放弃修改」都以替换后的文本为参照，
            // 不把整页白送进内存 overlay（批次55：基线指纹化，只存 8B 指纹）。
            st.baselines[curPage0] = pageBaselineFp(text)
        }
        // 批次55：本函数也从取消/异常路径（IO 协程）调用 —— RMW 用原子 update，
        // 与主线程按键编辑交错不再吞输入/脏标记（此函数里别用 _tabs.value = map）。
        _tabs.update { tabs ->
            tabs.map { t ->
                if (t.id == tabId) {
                    t.copy(
                        content = text ?: t.content,
                        edited = st.isDirty(),
                        contentRev = if (currentRewritten) t.contentRev + 1 else t.contentRev
                    )
                } else t
            }
        }
    }

    /**
     * 6.2.0 修「大文件不可编辑」（粘性只读）：只读标记原先由「>32MB 且省耗开启」置位、
     * 仅在另存为时复位 —— 一旦打开过一个超大文件，之后**打开任何文件都保持只读**。
     * 现在大文件一律可编辑（装不装得下由 [FileAccess] 的内存预检判断，装不下给
     * 「文件过大」明确文案），每次打开文档时归零此标记；后续若有真正的只读场景
     * （如权限只读）再按文档置位。
     */
    private val _readOnly = MutableStateFlow(false)
    val readOnly: StateFlow<Boolean> = _readOnly.asStateFlow()

    /**
     * 批次55：正在按新编码重建分页索引的标签（Main 线程增删）。重建期间旧页偏移与
     * 文件字节正在换代 —— 编辑（经 [readOnlyEff]）/翻页/替换/保存都必须互斥，
     * 否则重建完成时会把这期间的编辑与替换成果连同旧 spool 一并静默抹掉。
     */
    private val _rebuildingCharset = MutableStateFlow<Set<String>>(emptySet())
    val rebuildingCharset: StateFlow<Set<String>> = _rebuildingCharset.asStateFlow()

    // ==================== 批次56：会话缓存（关闭软件后恢复标签与未保存修改）====================

    private val sessionStore by lazy {
        EditorSessionStore(File(getApplication<Application>().filesDir, "editor_session"))
    }

    /** 文件路径 → 待恢复会话：窗口档索引在 [finishWindowedOpen] 就绪后消费（补挂改动页 + 跳回页码）。 */
    private val pendingRestores = ConcurrentHashMap<String, EditorSessionStore.Loaded>()

    /**
     * 恢复完成前禁止快照落盘 —— 启动瞬间标签流先是空的，防抖一到就会把上一份
     * session.json 覆写成空清单，恢复流程还在读就没了。
     */
    @Volatile private var sessionPersistReady = false

    /** 上一份快照的内容指纹 —— 相同即跳过整轮 IO。初值必须不与「空清单」的 key(0) 相等，
     *  否则启动即关全部标签时空快照被指纹跳过，旧会话跨重启复活（复查修）。 */
    @Volatile private var lastSnapshotKey = Int.MIN_VALUE

    /** 防抖的快照任务：标签 / 内容 / 页码任一变化 → 静默 1.2s 后落盘。 */
    private var sessionSaveJob: Job? = null

    /** 快照写盘序号（Main 单调递增）—— 多份快照的 IO 协程乱序落锁时，旧的直接丢弃，
     *  session.json 永不回退到旧内容（复查修）。 */
    private var sessionSeq = 0
    private var lastWrittenSeq = 0
    private val sessionWriteLock = Any()

    init {
        // 批次56：节流快照。collect 收到新标签状态就重启计时 —— 用户停手 1.2s 才写盘，
        // 进程被系统杀掉时的最大丢改动窗口 ≈ 1.2s（退出路径另有 onCleared 兜底）。
        viewModelScope.launch {
            _tabs.collect {
                sessionSaveJob?.cancel()
                sessionSaveJob = launch {
                    delay(1200)
                    if (sessionPersistReady) snapshotSession()
                }
            }
        }
        // 复查二修：纯切标签只改 _activeTabId、不走 _tabs —— 也纳入触发源，重启才记得
        // 停在哪个标签（配合 buildSessionSnapshot 里 activeId 进指纹）。
        viewModelScope.launch {
            _activeTabId.collect {
                sessionSaveJob?.cancel()
                sessionSaveJob = launch {
                    delay(1200)
                    if (sessionPersistReady) snapshotSession()
                }
            }
        }
        // 批次56：启动恢复（尽力而为 —— 读失败只记日志，绝不能挡住编辑器打开）。
        viewModelScope.launch { restoreSession() }
    }

    // ==================== 文件树 ====================
    private val _projectRoot = MutableStateFlow<File?>(null)
    val projectRoot: StateFlow<File?> = _projectRoot.asStateFlow()

    private val _expandedDirs = MutableStateFlow<Set<String>>(emptySet())
    val expandedDirs: StateFlow<Set<String>> = _expandedDirs.asStateFlow()

    /**
     * 6.2.0 批次16：当前「选中」的目录（点目录时高亮标记）。
     * 新建文件/文件夹默认落在这里；null 时回落到 projectRoot。
     */
    private val _selectedDir = MutableStateFlow<File?>(null)
    val selectedDir: StateFlow<File?> = _selectedDir.asStateFlow()

    /**
     * 6.2.0 批次16：文件树版本号。
     *
     * 旧的 refreshTree() 靠「把根目录从 expandedDirs 里移出再加回」来触发重组，但一出一进后
     * Set 与原来完全相同 → StateFlow 对相等值去重、不发射 → FileTreePanel 的
     * remember(root, expanded) 键没变 → buildTree() 不重算 → 重命名/删除/新建后树不刷新。
     * 改为单调递增的版本号，任何文件系统变更都 +1 并作为 remember 键，保证必然重建。
     */
    private val _treeVersion = MutableStateFlow(0)
    val treeVersion: StateFlow<Int> = _treeVersion.asStateFlow()

    // ==================== 设置 ====================
    private val _settings = MutableStateFlow(EditorSettingsStore.load(app))
    val settings: StateFlow<EditorSettings> = _settings.asStateFlow()

    // ==================== 事件 ====================
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // ==================== 另存为（无写入权限时） ====================
    /**
     * 6.2.0 批次21：待另存的标签页。当保存失败（无存储权限 / 原位置不可写）时置位，
     * Activity 观察到非空后弹出 SAF 目录选择器，选完回调 [completeSaveAs]。
     */
    private val _saveAsRequest = MutableStateFlow<EditorTab?>(null)
    val saveAsRequest: StateFlow<EditorTab?> = _saveAsRequest.asStateFlow()

    fun clearSaveAsRequest() {
        // 批次55：SAF 目录选择器被取消 —— 撤销挂起的「保存并关闭」，标签保留（改动不丢）。
        _saveAsRequest.value?.id?.let { pendingCloseAfterSave.remove(it) }
        _saveAsRequest.value = null
    }

    /**
     * 6.2.0：长按「保存」图标触发的系统级「另存为」（ACTION_CREATE_DOCUMENT）。
     * 与 [saveAsRequest]（写入失败后兜底、只能选目录）并行，用户**主动**另存时走这条，
     * 可以同时指定文件名和位置。Activity 观察到非空后弹文件选择器，回调 [completeSaveAsDoc]。
     */
    private val _saveAsDocRequest = MutableStateFlow<EditorTab?>(null)
    val saveAsDocRequest: StateFlow<EditorTab?> = _saveAsDocRequest.asStateFlow()

    fun clearSaveAsDocRequest() {
        // 批次55：CreateDocument 选择器被取消/启动失败 —— 撤销挂起的「保存并关闭」。
        _saveAsDocRequest.value?.id?.let { pendingCloseAfterSave.remove(it) }
        _saveAsDocRequest.value = null
    }

    /** 置位待另存标签（长按保存图标时调用），交由 Activity 弹 CreateDocument 文件选择器。 */
    fun requestSaveAsDoc() {
        val tab = activeTab ?: return
        // 批次46：窗口标签的整档不在内存，另存为只能写出**当前页** —— 静默截断不可接受，直接挡住。
        if (tab.windowed) {
            emit("窗口模式暂不支持另存为，请直接保存到原文件")
            return
        }
        _saveAsDocRequest.value = tab
    }

    /**
     * 6.2.0 批次27：最近一次打开失败的原因（如 Android/data 系统保护目录）。
     * 非空时 CodeEditorScreen 的空状态用它替换「未打开文件」，让失败原因常驻可见，
     * 而不是只有一闪而过的 toast。成功打开文件/项目时清空。
     */
    private val _lastOpenError = MutableStateFlow<String?>(null)
    val lastOpenError: StateFlow<String?> = _lastOpenError.asStateFlow()

    /** 插件向导模式：退出时把内存文档回传给调用方。 */
    var onMemoryDocumentsCommitted: ((List<Pair<String, String>>) -> Unit)? = null

    val activeTab: EditorTab?
        get() = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun emit(message: String) {
        _events.tryEmit(message)
    }

    // ==================== 打开 ====================

    /** 打开本地真实文件（D2 主路径：直接 IO）。 */
    fun openFile(file: File, asNew: Boolean = false) {
        val path = file.absolutePath
        // 6.2.0：每个新文档都从「可编辑」开始，杜绝上一个文档的只读标记粘住本次打开。
        _readOnly.value = false
        _tabs.value.firstOrNull { it.file?.absolutePath == path }?.let {
            activateTab(it.id) // 批次55：统一走切活入口（切走前回灌脏大文档）
            return
        }
        if (!file.exists()) {
            emit("文件不存在: ${file.name}")
            return
        }
        if (!file.isFile) {
            emit("不是文件: ${file.name}")
            return
        }
        if (!FileAccess.isTextFile(file.name)) {
            emit("暂不支持打开非文本文件: ${file.name}")
            return
        }
        // 6.2.0：原先这里对 >32MB（且省耗开启）的文件强制只读 —— 已移除：
        // 该护栏既被用户点名「大文件不可编辑」，又会粘住后续打开。大文件能否打开
        // 由下方 readText 的内存预检判断（装不下 → TooLarge 明确报「文件过大」）。
        // 6.2.0 批次40：区分「文件过大」与「读取失败」—— 以前 OOM 也报「读取失败」，
        // 用户完全看不出真实原因，还会误以为是权限问题。
        // 批次54（用户反馈「看似分页，实则并未分页」+ 当晚 30MB 档编辑 OOM 实测）：
        // 超过 16MB 的文件一律走分页窗口模式 —— 整读只是「当下装得下」，编辑稳态还要付
        // sora Content + 基线快照多份拷贝（30MB 档当晚堆 531/537MB 顶穿），且整读档的
        // n/N 分页控件只是光标位置换算（假分页）。窗口模式 = 真分页 + 每页仅 2000 行。
        if (file.length() > WINDOW_OPEN_BYTES) {
            startWindowedOpen(file, file.length())
            return
        }
        // 6.2.0：读取时带编码 —— 自动档由 FileAccess 判定 Unicode/GBK 等，实际采用的编码
        // 随 ReadResult.Ok 回传，落进 tab.effectiveCharset 供状态栏显示、保存时复用。
        val r = FileAccess.readText(file)
        val content = when (r) {
            is FileAccess.ReadResult.Ok -> r.text
            is FileAccess.ReadResult.TooLarge -> {
                // 6.2.0 批次46：装不下不再直接拒开 —— 转入**分页窗口模式**（后台建索引、
                // 只装当前页）。索引也建不起来（如非白名单编码）时才回落原「文件过大」文案。
                startWindowedOpen(file, r.bytes)
                return
            }
            FileAccess.ReadResult.Unreadable -> {
                val msg = "读取失败: ${file.name}"
                _lastOpenError.value = msg
                emit(msg)
                return
            }
        }
        val effective = if (r is FileAccess.ReadResult.Ok) r.charset else "UTF-8"
        addTab(
            EditorTab(
                id = newId(),
                name = file.name,
                file = file,
                savedContent = if (asNew) "" else content,
                content = content,
                charset = FileAccess.CHARSET_AUTO,
                effectiveCharset = effective,
                // 6.2.0 批次39：asNew = 以空基线打开、正文已被写入 → 一打开就是「未保存」。
                edited = asNew && content.isNotEmpty()
            )
        )
        // 6.2.0：记下最后打开的文件，供进程被回收后从多任务回来时恢复（见 CodeEditorActivity）。
        // 批次57②（用户指定）：插件向导草稿**不记「上次打开」** —— 否则向导结束后进程被
        // 系统回收、从多任务回来，会把插件草稿当成上次文档复活进编辑器（插件代码不落缓存）。
        if (_wizardDraftPrefix.value?.let { path.startsWith(it) } != true) {
            EditorSettingsStore.rememberLastOpened(getApplication(), path, null)
        }
    }

    /**
     * 6.2.0 批次46：**分页窗口模式**的异步打开入口。
     *
     * 整文件装不下堆（内存预检失败 / 整读 OOM）时走这里。批次50 改为**渐进式**：
     *  1. 先快读文件头（≤4MB，[FileAccess.readHeadPage]）解出首页文本，**立刻开标签显示** ——
     *     不再让用户盯空页面等 GB 级索引（此前 2GB 文件要数分钟无反馈）；
     *  2. 页索引随后在后台流式建立（[WindowedDocument.build] 快速路径），期间底部左下角
     *     显示「扫描 N%」；翻页/保存/全文检索在索引就绪前一律被拒（页偏移还不准）；
     *  3. 索引完成：登记窗口簿记、用精确页文本校正首页（超长单行档的头部截断在此修正）。
     * 索引失败（编码不适合字节级扫描 / IO 失败）关闭该标签并回落「文件过大」文案。
     */
    private fun startWindowedOpen(file: File, bytes: Long) {
        val path = file.absolutePath
        _tabs.value.firstOrNull { it.file?.absolutePath == path }?.let {
            activateTab(it.id) // 批次55：统一走切活入口（切走前回灌脏大文档）
            return
        }
        if (!pendingWindowOpens.add(path)) return  // 已有一次建索引在途
        viewModelScope.launch(Dispatchers.IO) {
            AppLog.d(TAG, "windowed open start: $path")
            // 批次50（用户反馈）：大文件**本来**就以分页窗口模式打开，不再说「内存不足，改用…」。
            emit("大文件分页打开：${file.name}（先显示首页，索引后台建立中）…")
            val head = FileAccess.readHeadPage(file)
            if (head == null) {
                withContext(Dispatchers.Main) {
                    pendingWindowOpens.remove(path)
                    // 复查三修：没建成临时签的失败也要终结会话恢复项（prune 不再剪缺席项）。
                    pendingRestores.remove(path)
                    val msg = "文件过大（${FileAccess.formatSize(bytes)}），内存不足，无法打开"
                    _lastOpenError.value = msg
                    emit(msg)
                }
                return@launch
            }
            val tabId = withContext(Dispatchers.Main) {
                pendingWindowOpens.remove(path)
                addProvisionalWindowTab(file, head)
            }
            // 批次50：后台建索引（进度经 _windowScanProgress 供底部状态栏显示）。
            val doc = WindowedDocument.build(
                file, FileAccess.charsetForName(head.charset), FileAccess.WINDOW_PAGE_LINES
            ) { pct -> _windowScanProgress.update { m -> m + (tabId to pct) } }
            withContext(Dispatchers.Main) {
                _windowScanProgress.update { it - tabId }
                finishWindowedOpen(tabId, file, head, doc)
            }
        }
    }

    /** 批次50：以头部快读结果先开标签（窗口元信息的总行/总页 = 0，索引完成后校正）。 */
    private fun addProvisionalWindowTab(file: File, head: FileAccess.HeadPage): String {
        val tab = EditorTab(
            id = newId(),
            name = file.name,
            file = file,
            savedContent = head.text,
            content = head.text,
            charset = FileAccess.CHARSET_AUTO,
            effectiveCharset = head.charset,
            window = WindowInfo(page = 1, totalLines = 0, totalPages = 0)
        )
        // 复查三修：该文件是会话恢复目标时临时签不抢焦点（消费点按 entry.active 兑付），
        // 否则头读慢于 restoreSession 收尾校正会把活动标签顶成错误值并被补拍固化。
        // 复查四修：`!in` 对具体 CHM 会命中继承的 contains=containsValue（K2 下编译错误
        // KT-18053）—— 必须显式 containsKey。
        addTab(tab, activate = !pendingRestores.containsKey(file.absolutePath))
        EditorSettingsStore.rememberLastOpened(getApplication(), file.absolutePath, null)
        return tab.id
    }

    /** 批次50：索引完成（或失败）的收尾 —— 登记簿记 / 校正首页文本 / 失败关标签。 */
    private fun finishWindowedOpen(
        tabId: String,
        file: File,
        head: FileAccess.HeadPage,
        doc: WindowedDocument?
    ) {
        if (doc == null) {
            // 索引失败：标签虽已显示首页，但无法翻页/保存 —— 关掉并明确报因。
            closeTab(tabId, force = true)
            val msg = "文件过大（${FileAccess.formatSize(file.length())}），无法建立分页索引"
            _lastOpenError.value = msg
            emit(msg)
            return
        }
        // 扫描期间标签被用户关掉：不登记簿记（防僵尸条目），索引结果丢弃。
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val exact = doc.readPage(0)
        // 扫描期间用户可能编辑过首页：以「与快读文本是否一致」判定。
        val edited = tab.content != head.text
        // 复查三修：baselines 与其它登记点统一用并发容器（finishWholeReplaceView 在 IO 写）。
        windowState[tabId] = WindowState(
            doc,
            baselines = ConcurrentHashMap<Int, Long>().apply { put(0, pageBaselineFp(exact)) }
        )
        // 批次55：本函数跑在索引协程（IO）上 —— RMW 用原子 update 防与主线程按键编辑交错。
        _tabs.update { tabs ->
            tabs.map { t ->
                if (t.id == tabId) {
                    t.copy(
                        // 未编辑 → 换精确页文本（修正头部截断）；已编辑 → 保留用户文本（脏），
                        // 基线 = 精确原文，后续 flush 会把它记进 overlay。
                        content = if (edited) t.content else exact,
                        savedContent = exact,
                        window = t.window?.copy(totalLines = doc.totalLines, totalPages = doc.totalPages),
                        edited = edited,
                        contentRev = if (edited) t.contentRev else t.contentRev + 1
                    )
                } else t
            }
        }
        // 批次56：会话恢复 —— 索引刚就绪，消费待恢复项（补挂改动页 + 跳回原页）。
        pendingRestores.remove(file.absolutePath)?.let { p ->
            viewModelScope.launch(Dispatchers.Main) {
                applyWindowedRestore(tabId, p)
                // 复查二修：索引慢于 restoreSession 的收尾校正时，条目自身的 active
                // 在此兑付（恢复期 openFile 未返回拿不到 tid 的场景就靠这里补）。
                if (p.entry.active) activateTab(tabId)
            }
        }
        AppLog.d(TAG, "windowed open ok: ${file.absolutePath} pages=${doc.totalPages}")
        emit("${file.name}：分页就绪，共 ${doc.totalPages} 页")
    }

    /**
     * 打开来自外部 ACTION_VIEW 的 URI。
     * 6.2.0 批次30：直接打开、立即显示文本，不再等待 TextMate 就绪（不显示「正在打开…」占位页）。
     * 冷启动时若语法未就绪，文件先以 EmptyLanguage 打开，待就绪后由 EditorCanvas 补设语言。
     * 6.2.0 批次24：content:// 一律直接走 ContentResolver（分享方已临时授权），
     * 不再先解析成真实路径 —— android/data/、android/obb/ 被系统硬拦，解析成路径后
     * 用 java.io.File 读必然失败。file:// 或能直读的真实路径才走 java.io.File。
     */
    fun openUri(uri: Uri) {
        // 6.2.0：与 openFile 一致，新文档从「可编辑」开始（file:// 分支最终也走 openFile）。
        _readOnly.value = false
        // 6.2.0 批次25：先尝试固化 URI 读写授权（分享方若带了 PERSISTABLE flag 才会成功，
        // 普通临时授权会抛 SecurityException，属正常，忽略即可）。个别文件管理器只给
        // 临时授权且稍纵即逝，固化后 openInputStream/openFileDescriptor 更稳。
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        // file:// 直接走真实路径。
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                val f = File(path)
                if (f.isFile && f.canRead()) {
                    openFile(f)
                    return
                }
            }
        }
        // content://（或 file:// 直读失败）：查重后用 ContentResolver 读。
        val identity = uri.toString()
        _tabs.value.firstOrNull { it.uri == identity }?.let {
            activateTab(it.id) // 批次55：统一走切活入口（切走前回灌脏大文档）
            return
        }
        // 批次54：content:// 也必须过 openFile 的 16MB 分页门槛 —— 否则文件管理器「打开
        // 方式」进来的 30MB 档仍整读（当晚 OOM 链的原路径）。能解析成可读真实路径的
        //（绝大多数 /storage 下的文件）优先走 openFile；android/data 等被系统硬拦的目录
        // canRead()=false，自然落回下面的 ContentResolver 读取（批次24 语义不变）。
        if (uri.scheme != "file") {
            val realPath = FileAccess.resolveRealPath(getApplication(), uri)
            if (realPath != null) {
                val f = File(realPath)
                if (f.isFile && f.canRead()) {
                    openFile(f)
                    return
                }
            }
        }
        val uriRead = FileAccess.readUri(getApplication(), uri)
        if (uriRead == null) {
            // 兜底：尝试按解析出的真实路径直读（个别 provider 的 content:// 其实可映射到可读文件）。
            val realPath = FileAccess.resolveRealPath(getApplication(), uri)
            if (realPath != null) {
                val f = File(realPath)
                if (f.isFile && f.canRead()) {
                    openFile(f)
                    return
                }
            }
            // 6.2.0 批次26：Android 11+ 对其他应用的 Android/data|obb 是系统硬拦截（即便
            // 授予「所有文件访问」也 EACCES），直接 IO 打不开。给出明确提示，而不是含糊的
            // 「无法读取该文件/未打开文件」，引导用户改用系统文件选择器（SAF 可访问该目录）。
            val blocked = uri.path?.let(FileAccess::isProtectedPath) == true ||
                realPath?.let(FileAccess::isProtectedPath) == true
            if (blocked) {
                AppLog.e(TAG, "openUri blocked by scoped storage: $uri")
                val msg = "无法读取：该文件位于系统保护目录（Android/data），请改用系统文件选择器打开"
                _lastOpenError.value = msg
                emit(msg)
            } else {
                AppLog.e(TAG, "openUri read failed, no fallback: $uri")
                val msg = "无法读取该文件（可能过大、编码不受支持，或分享方已回收授权）"
                _lastOpenError.value = msg
                emit(msg)
            }
            return
        }
        _lastOpenError.value = null
        addTab(
            EditorTab(
                id = newId(),
                name = displayNameOf(uri),
                uri = identity,
                savedContent = uriRead.text,
                content = uriRead.text,
                charset = FileAccess.CHARSET_AUTO,
                effectiveCharset = uriRead.charset
            )
        )
        EditorSettingsStore.rememberLastOpened(getApplication(), null, identity)
    }

    private fun displayNameOf(uri: Uri): String {
        try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) return c.getString(i) ?: uri.lastPathSegment ?: "未命名"
                    }
                }
        } catch (e: Exception) {
            AppLog.w(TAG, "displayNameOf failed for $uri")
        }
        return uri.lastPathSegment ?: "未命名"
    }

    /** 内存文档（插件向导模式 / 新建未保存）。 */
    fun openMemory(name: String, content: String) {
        val existing = _tabs.value.firstOrNull { it.name == name && it.file == null && it.uri == null }
        if (existing != null) {
            activateTab(existing.id) // 批次55：统一走切活入口
            return
        }
        addTab(
            EditorTab(id = newId(), name = name, savedContent = content, content = content)
        )
    }

    /**
     * 6.2.0：**切换当前文档的编码**（底部状态栏「编码」按钮 → 选择器）。
     *
     * 换编码意味着「用另一种字节解释磁盘上的同一份字节」，所以从来源**重新读取并重解**
     * —— 乱码纠正只对未保存的重解有效，无法凭已解出的 String 反推原字节。
     * 重解会覆盖当前编辑内容，故基线 savedContent 一并更新、编辑态置为「未修改」，
     * 并明确提示用户这是重新载入。
     */
    fun setTabCharset(id: String, name: String) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        if (tab.charset == name) return
        val f = tab.file
        val uri = tab.uri
        // 内存文档没有来源字节可重解 —— 只记录选择（真正落盘时才用它编码）。
        if (f == null && uri == null) {
            _tabs.value = _tabs.value.map {
                if (it.id == id) it.copy(charset = name, effectiveCharset = name) else it
            }
            emit("编码: ${charsetLabel(name)}")
            return
        }
        // 6.2.0 批次46：窗口标签换编码 = 用新编码**重建页索引**并重读首页。
        // 后台线程流式扫描（GB 级同步做会 ANR）；与整读换档一致，重解覆盖当前编辑。
        if (tab.windowed && f != null) {
            val tabId = id
            // 批次47：保存/全文替换正持有页供给与 spool 簿记 —— 换编码会让整套索引作废，互斥。
            if (savingWindows.contains(tabId)) {
                emit("正在保存…请稍候再换编码")
                return
            }
            if (replacingWindows.contains(tabId)) {
                emit("正在全文替换…请稍候再换编码")
                return
            }
            if (tabId in _rebuildingCharset.value) {
                emit("正在重新载入编码…请稍候")
                return
            }
            // 批次55：挂上重建互斥标记 —— 期间编辑（readOnlyEff）/翻页/替换/保存全挡，
            // 否则完成时清 spool + edited=false 会把这几十秒内的改动静默抹掉。
            _rebuildingCharset.update { it + tabId }
            viewModelScope.launch(Dispatchers.IO) {
                AppLog.d(TAG, "windowed re-charset: $tabId $name")
                emit("正在按 ${charsetLabel(name)} 重新载入 ${tab.name}…")
                try {
                    val r = FileAccess.openWindowed(f, name)
                    withContext(Dispatchers.Main) {
                        if (r == null) {
                            emit("无法按 ${charsetLabel(name)} 重新读取 ${tab.name}")
                            return@withContext
                        }
                        // 批次47：旧窗口的改动页（内存 overlay 与磁盘 spool）随重建一并作废清理。
                        windowState.remove(tabId)?.let { clearSpool(it) }
                        _tabs.value = _tabs.value.map { t ->
                            if (t.id == tabId) {
                                t.copy(
                                    charset = name,
                                    effectiveCharset = r.charset,
                                    savedContent = r.text,
                                    content = r.text,
                                    window = WindowInfo(1, r.doc.totalLines, r.doc.totalPages),
                                    contentRev = t.contentRev + 1,
                                    edited = false
                                )
                            } else t
                        }
                        // 标签在扫描期间被关掉则不再登记簿记（防僵尸条目）。
                        if (_tabs.value.any { it.id == tabId }) {
                            windowState[tabId] =
                                WindowState(r.doc, baselines = ConcurrentHashMap<Int, Long>().apply {
                                    put(0, pageBaselineFp(r.text))
                                })
                        }
                        // 复查六修（小·终审建议）：换编码重建已用新盘文本 + 新索引把标签完全
                        // 重置（content/基线/簿记全部对齐磁盘）—— 该签若曾是死签，此刻已被
                        // 真正「治愈」，解除死标；否则三门禁继续误拦（方向安全但纯属误报）。
                        layoutDeadTabs.remove(tabId)
                        // 复查修「换编码不清检索结果」：重建期间可能已有新搜索在跑（扫的还是
                        // 旧索引），换档即作废 —— 否则结果指向旧文本，点跳只能夹到错误位置。
                        cancelWholeSearch()
                        emit("编码: ${charsetLabel(name)}（已重新载入 ${tab.name}）")
                    }
                } finally {
                    // 批次55：所有退出路径（含异常/协程取消）都必须释放互斥标记。
                    _rebuildingCharset.update { it - tabId }
                }
            }
            return
        }
        // 一次读取同时拿到「按所选解出的文本」和「实际采用的编码」。
        val decoded: String
        val effective: String
        if (f != null) {
            val r = FileAccess.readText(f, name)
            if (r !is FileAccess.ReadResult.Ok) {
                emit("无法按 ${charsetLabel(name)} 重新读取 ${tab.name}")
                return
            }
            decoded = r.text
            effective = r.charset
        } else {
            val r = FileAccess.readUri(getApplication(), Uri.parse(uri), name)
            if (r == null) {
                emit("无法按 ${charsetLabel(name)} 重新读取 ${tab.name}")
                return
            }
            decoded = r.text
            effective = r.charset
        }
        _tabs.value = _tabs.value.map {
            if (it.id == id) {
                it.copy(
                    charset = name,
                    effectiveCharset = effective,
                    savedContent = decoded,
                    content = decoded,
                    edited = false
                )
            } else it
        }
        // 复查修「换编码不清检索结果」：重解后文本与行位全变，旧结果立即作废
        //（窗口路径在重建完成回调里取消，覆盖重建期间新发起的搜索）。
        cancelWholeSearch()
        emit("编码: ${charsetLabel(name)}（已重新载入 ${tab.name}）")
    }

    /** 选择器/提示里显示的名字（自动档显示本地化文案，编码名本身语言无关）。 */
    private fun charsetLabel(name: String): String =
        if (name == FileAccess.CHARSET_AUTO) "自动检测" else name

    /** 批量载入插件向导的文件集合。 */
    fun loadFromEntries(entries: List<Pair<String, String>>) {
        entries.forEach { (name, content) -> openMemory(name, content) }
        if (_tabs.value.isNotEmpty() && _activeTabId.value == null) {
            activateTab(_tabs.value.first().id)
        }
    }

    /**
     * @param activate 复查三修：会话恢复期分页临时签（头读完成晚于收尾校正）不得抢焦点 ——
     *   该条目若非恢复目标，抢了就再没人纠正（active 在消费点只兑付 entry.active=true 的条目）。
     */
    private fun addTab(tab: EditorTab, activate: Boolean = true) {
        _lastOpenError.value = null
        _tabs.value = _tabs.value + tab
        if (activate) activateTab(tab.id) // 批次55：统一走切活入口（切走前回灌脏大文档）
        // 6.2.0 批次23：外部打开文件后，让文件树立刻可见并尽量定位到该文件。
        ensureTreeRootForFile(tab.file)
    }

    // ==================== 项目 ====================

    // ---- 批次57②（用户指定）：插件向导草稿模式 ----

    /**
     * 向导草稿目录前缀（非空 = 草稿模式）：标签栏只显示该目录下的签（用户没关的设备
     * 文件**隐藏而非关闭**，向导结束编辑器关闭后随会话恢复原样回来）；会话缓存与
     * 「上次打开」按此前缀剔除（插件代码不落编辑器缓存）。主线程读写。
     */
    private val _wizardDraftPrefix = MutableStateFlow<String?>(null)
    val wizardDraftPrefix: StateFlow<String?> = _wizardDraftPrefix.asStateFlow()

    /**
     * 进入新一轮向导草稿模式。上一轮向导自己的遗留草稿签直接撤掉（向导已读走结果，
     * 可静默丢弃）；用户标签只隐藏不关。
     */
    fun enterWizardMode(draftDir: File) {
        val prefix = draftDir.absolutePath + File.separator
        val prev = _wizardDraftPrefix.value
        _wizardDraftPrefix.value = prefix
        if (prev == null || prev == prefix) return
        var searchOwnerDropped = false
        var activeDropped = false
        _tabs.update { list ->
            list.filterNot { t ->
                val drop = t.file?.absolutePath?.startsWith(prev) == true
                if (drop) {
                    if (t.id == wholeSearchTabId) searchOwnerDropped = true
                    if (t.id == _activeTabId.value) activeDropped = true
                }
                drop
            }
        }
        if (searchOwnerDropped) cancelWholeSearch()
        if (activeDropped) {
            // 激活第一枚新草稿签；随后 handleIntent 的 openFile 也会切活（兜底空态）。
            _tabs.value.firstOrNull { it.file?.absolutePath?.startsWith(prefix) == true }
                ?.let { activateTab(it.id) }
        }
    }

    fun openProject(dir: File) {
        if (!dir.isDirectory || !dir.canRead()) {
            emit("无法读取目录: ${dir.name}")
            return
        }
        _projectRoot.value = dir
        _expandedDirs.value = setOf(dir.absolutePath)
        _selectedDir.value = dir
    }

    /**
     * 6.2.0 批次40：**启动即建树根**。
     *
     * 应用一启动就申请存储权限，权限到手后目录树本就该有内容 —— 不该再让用户先
     * 「打开一个文件夹」才能看到文件。这里把树根默认设为外部存储根，
     * 前置条件与 [ensureTreeRootForFile] 完全一致（有权限 + 根目录可读）。
     *
     * 只在树根尚未设置时生效，用户自己 [openProject] 选的目录不会被覆盖。
     */
    fun ensureDefaultRoot() {
        if (_projectRoot.value != null) return
        if (!FileAccess.hasStorageAccess(getApplication())) return
        val storageRoot = Environment.getExternalStorageDirectory()
        if (!storageRoot.isDirectory || !storageRoot.canRead()) return
        openProject(storageRoot)
    }

    fun clearProject() {
        _projectRoot.value = null
        _expandedDirs.value = emptySet()
        _selectedDir.value = null
    }

    /**
     * 6.2.0 修「退出后从多任务窗口回来显示『未打开文件』」：
     * 进程被系统回收后，回来的是全新 Activity + 空 Intent（没有 ACTION_VIEW、没有 project_dir），
     * 旧实现直接落进空态，用户看着像刚编辑的内容全没了。
     *
     * 这里把 [EditorSettingsStore] 记下的上次文档重新打开。已删除 / 不可读的静默跳过，
     * 不在启动瞬间弹错误提示。
     */
    fun restoreLastOpenedIfAny() {
        if (_tabs.value.isNotEmpty()) return
        val (file, uri) = EditorSettingsStore.lastOpened(getApplication())
        // 6.2.0 修「从多任务回来文件又没了」时查无对证：恢复路径原先零日志，
        // 设备日志里既看不到它跑没跑、也看不到它为什么没恢复，这里补齐。
        AppLog.d(TAG, "restoreLastOpened: file=$file uri=$uri")
        try {
            if (file != null) {
                val f = File(file)
                if (f.isFile && f.canRead() && FileAccess.isTextFile(f.name)) {
                    openFile(f)
                    val outcome = if (_tabs.value.isNotEmpty()) "restored" else "open failed"
                    AppLog.d(TAG, "restoreLastOpened: file $outcome: $file")
                    return
                }
                AppLog.w(TAG, "restoreLastOpened: skip missing/unreadable: $file")
            }
            if (uri != null) {
                openUri(Uri.parse(uri))
                val outcome = if (_tabs.value.isNotEmpty()) "restored" else "open failed"
                AppLog.d(TAG, "restoreLastOpened: uri $outcome: $uri")
            } else if (file == null) {
                AppLog.d(TAG, "restoreLastOpened: nothing remembered")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "restoreLastOpened failed: $e")
        }
    }

    fun toggleDir(path: String) {
        _expandedDirs.value = _expandedDirs.value.let {
            if (path in it) it - path else it + path
        }
    }

    /** 标记某个目录为「选中」（点目录时高亮；新建文件/文件夹默认落在这里）。 */
    fun selectDir(file: File) {
        _selectedDir.value = file
    }

    /**
     * 6.2.0 批次16：把 [file] 的所有祖先目录（含自身，直到 projectRoot）标记为展开，
     * 保证新建/重命名后的节点在树里可见。
     */
    private fun expandAncestors(file: File) {
        val root = _projectRoot.value ?: return
        val rootPath = root.absolutePath
        val toExpand = mutableSetOf<String>()
        var current: File? = file
        while (current != null) {
            val p = current.absolutePath
            if (!p.startsWith(rootPath)) break
            toExpand.add(p)
            if (p == rootPath) break
            current = current.parentFile
        }
        _expandedDirs.value = _expandedDirs.value + toExpand
    }

    /**
     * 6.2.0 批次23/24：打开文件（含外部 ACTION_VIEW）后，仅在**能定位**该文件时才建树根：
     *  - 必须有真实路径（content:// 打开的 tab.file 为 null，无法定位）；
     *  - 必须有「所有文件访问」权限（否则 listFiles 读不到内部存储）。
     * 能定位 → 树根设为内部存储根、展开祖先并高亮父目录，用户点目录树即可看到当前文件。
     * 不能定位 → 不建树根（projectRoot 保持 null）→ 目录树与目录树开关图标一并隐藏，
     * 直接显示文件本身，也不再弹「授予权限」页。
     */
    private fun ensureTreeRootForFile(file: File?) {
        if (file == null) return                       // content:// 无法定位 → 不建树
        if (!FileAccess.hasStorageAccess(getApplication())) return  // 无权限 → 不建树
        val storageRoot = Environment.getExternalStorageDirectory()
        if (!storageRoot.isDirectory || !storageRoot.canRead()) return
        if (_projectRoot.value == null) {
            _projectRoot.value = storageRoot
            _expandedDirs.value = setOf(storageRoot.absolutePath)
            _selectedDir.value = storageRoot
        }
        val root = _projectRoot.value ?: return
        if (file.absolutePath.startsWith(root.absolutePath)) {
            expandAncestors(file)
            _selectedDir.value = file.parentFile ?: root
        }
    }

    /** 文件系统发生变更后调用：版本号 +1，强制文件树重建。 */
    fun refreshTree() {
        _treeVersion.value = _treeVersion.value + 1
    }

    /** 构建当前展开状态下的文件树。 */
    fun buildTree(): List<FileNode> {
        val root = _projectRoot.value ?: return emptyList()
        return listOf(buildDirNode(root, depth = 0))
    }

    private fun buildDirNode(dir: File, depth: Int): FileNode.DirNode {
        val expanded = dir.absolutePath in _expandedDirs.value
        val children: List<FileNode> = if (!expanded || depth > 12) {
            emptyList()
        } else {
            FileAccess.listProjectChildren(dir).take(MAX_DIR_ENTRIES).map { child ->
                if (child.isDirectory) buildDirNode(child, depth + 1)
                else FileNode.FileLeaf(child)
            }
        }
        return FileNode.DirNode(file = dir, children = children, isExpanded = expanded)
    }

    // ==================== 编辑 / 保存 ====================

    fun setActive(id: String) {
        if (_activeTabId.value != id && _tabs.value.any { it.id == id }) {
            // 批次53 复查修「替换中途切标签被静默取消」：**搜索**结果绑定旧标签的页供给
            // —— 换标签一律作废；**替换**是自带簿记的写操作（逐页落 spool / 内存整写，
            // 完成后按 tabId 更新数据层），切走让它在后台跑完即可 —— 静默取消会让「全部
            // 替换」无声无息少改一批，比跑完更糟。面板按标签过滤展示：期间新标签不受影响
            //（只读/横幅只对被替换的标签生效），完成后回原标签能看到结果横幅。
            cancelWholeSearch()
            if (wholeReplaceJob?.isActive == true && wholeReplaceTabId != null) {
                emit("替换继续在后台进行，完成后回原标签可查看结果")
            }
            // 批次54：切走前把脏大文档的最新文本回灌一次（大文档编辑期间不再逐键复制）。
            // 不回灌的话，切回来时 EditorCanvas 会按过期 content 重设文本，编辑就"消失"了。
            _activeTabId.value.let { old -> if (old != null) refreshStaleContent(old) }
            _activeTabId.value = id
        }
    }

    fun updateContent(id: String, newContent: String) {
        contentStaleTabs.remove(id) // 批次54：内容已回灌/同步，不再是「待回灌」态
        // 6.2.0 批次39（SE 审计 T3-19）：顺带置脏标记，使 isModified 变成 O(1)。
        _tabs.value = _tabs.value.map { t ->
            if (t.id == id) t.copy(content = newContent, edited = true) else t
        }
        // 批次53 复查：内容一变，既有整档结果的行号/列号即失效（点旧结果会跳错位）——
        // 标记过期，面板提示重新搜索。旧 sora 页内检索靠内容事件自动重搜，统一整档后
        // 需要这个显式信号兜住（跳转侧另有夹界防越界崩溃）。
        if (wholeSearchTabId == id && wholeQuery.isNotEmpty()) {
            _wholeSearch.update { it?.copy(stale = true) }
        }
    }

    /**
     * 批次54：大文档（内容超 200 万字符）编辑时**不再逐键复制全文** —— 2026-10-21 22:26
     * 实测：30MB 文档每键 Content.toString() 一份 57MB 字符数组，堆 531/537MB 顶穿 OOM。
     * 置脏后 content 视为「待回灌」：由界面层 contentRefresher 在保存/搜索/替换/切走前
     * 统一取编辑器最新文本回灌（updateContent 同时清标记）。小文档仍每键同步，实时性不变。
     */
    // 复查五修（小）：并发容器 —— 保存完成簿记在写盘线程 `in` 读本集合，普通 HashSet
    // 并发读写无保证（读到旧值=退回保守保脏、方向安全，但不给撕裂留机会）。
    private val contentStaleTabs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /**
     * 复查五修（写坏文件）：「死页」——保存全量重建后 tab.content 仍是旧分页布局、又因
     * 保存期间键入/陈旧无法安全重设的标签（含索引重建失败的旧簿记）。其内容/基线与已
     * 落盘的新文件对不上，一旦进快照就会以「新文件 size/mtime + 旧布局页」的组合被会话
     * 恢复挂到新文件上，再保存即拼接出重复/丢失行 —— 捕获与快照前 flush 一律跳过；
     * 保存已被只读门禁拦住，重开文件即除名（id 全局唯一，随签消亡）。
     */
    private val layoutDeadTabs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 界面层实现：把编辑器当前文本回灌进指定标签（仅在文本确实超大时才复制）。 */
    var contentRefresher: ((String) -> Unit)? = null

    /** 大文档按键只置脏 —— 内容不逐键复制；已有整档结果同样标过期。 */
    fun markContentEdited(id: String) {
        _tabs.value = _tabs.value.map { t -> if (t.id == id) t.copy(edited = true) else t }
        contentStaleTabs.add(id)
        if (wholeSearchTabId == id && wholeQuery.isNotEmpty()) {
            _wholeSearch.update { it?.copy(stale = true) }
        }
    }

    /** 待回灌标签在快照消费前回灌一次最新文本（干净/小文档为空操作）。 */
    private fun refreshStaleContent(tabId: String) {
        if (tabId !in contentStaleTabs) return
        // 批次55 纵深防御：按设计「脏 ⇒ 活动标签」（切走必经 activateTab 回灌）。
        // 命中非活动说明该不变量被破坏 —— 告警让它可观测（回灌依赖活动编辑器）。
        if (tabId != _activeTabId.value) {
            AppLog.w(TAG, "stale tab not active during refresh, content may be stale: $tabId")
        }
        contentRefresher?.invoke(tabId)
    }

    // ==================== 分页窗口模式：翻页 / 保存 ====================

    /**
     * 6.2.0 批次46：窗口标签翻页 —— 先把当前页改动刷进 spool，再从磁盘读出目标页，
     * 以 [EditorTab.contentRev] +1 触发 EditorCanvas 重设 sora 文本（内容整体换页）。
     *
     * @return true = 成功（或无需翻）；false = 目标页读取失败（调用方清掉待定位光标）。
     */
    fun jumpWindowPage(tabId: String, page: Int): Boolean {
        // 复查六修（死页锁绕过·翻页侧）：死签的 doc/基线是新布局而 content 是旧布局，
        // 放行即两条坏路 —— ① flush 把旧布局文本写进 overlays，此后任何入口一保存就拼坏
        // 文件；② rebuilt==null 死签拿旧偏移读新文件（错位/乱码页展示）。逐签硬拦，
        // 与「任意 openFile 解锁全局只读」脱钩。页码非法/重建中等判定仍保留在下方。
        if (tabId in layoutDeadTabs) {
            emit("分页索引已失效，请关闭该标签后重新打开文件")
            return false
        }
        // 批次54：超大单页（>200 万字符）编辑后翻页 —— 先回灌最新文本，否则下面
        // flushWindowPage 会把过期文本写进 overlay，该页编辑被静默丢弃。
        refreshStaleContent(tabId)
        val st = windowState[tabId] ?: return false
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return false
        val win = tab.window ?: return false
        // 批次55：渐进式打开/换编码重建期 totalLines/totalPages==0 —— coerceIn(1, 0) 会抛
        // IllegalArgumentException；此时没有可用页偏移，直接拒翻。
        if (win.totalPages <= 0 || win.totalLines <= 0) return false
        if (tabId in rebuildingCharset.value) {
            emit("正在重新载入编码…请稍候再翻页")
            return false
        }
        // 保存写盘期间文件正被整体替换、页偏移即将作废 —— 此时翻页读到的是错位字节。
        if (savingWindows.contains(tabId)) {
            emit("正在保存…请稍候再翻页")
            return false
        }
        // 全文替换期间页供给正在换代（磁盘 spool 逐页增长）—— 同样拒翻。
        if (replacingWindows.contains(tabId)) {
            emit("正在全文替换…请稍候再翻页")
            return false
        }
        val target = page.coerceIn(1, win.totalPages)
        if (target == win.page) return true
        flushWindowPage(tab, st)
        // 批次47：经统一供给取页 —— 目标页若已被全文替换改写，看到的是 spool 版本而非原盘。
        val text = try {
            windowPageText(st, target - 1)
        } catch (e: Exception) {
            AppLog.e(TAG, "readPage ${target - 1} failed: ${tab.file}", e)
            emit("读取第 $target 页失败")
            return false
        }
        // 批次47：savedContent 恒为**盘上原文**（不含 spool/overlay）——「放弃修改」=回到上次
        // 落盘状态；若拿改动版当基线，丢弃清空 spool 后内容仍留着替换文本，脏标记会把它复活。
        var diskTextOk = true // 复查二修：读不出原盘时 fallback 已是改动版，不得污染基线
        val diskText = if ((target - 1) in st.spoolPages || (target - 1) in st.overlays) {
            try {
                st.doc.readPage(target - 1)
            } catch (e: Exception) {
                diskTextOk = false
                text // 盘上原文读不出来时退回供给文本（罕见；至少不崩、内容仍正确）
            }
        } else text
        // 批次56 复查修「复访已改页被静默丢弃」：flush 的脏判定基线必须是**无手改时的
        // 供给版**（spool 版优先 → 原盘），绝不能用 overlay 版 —— 否则复访一个已改页会把
        // 基线污染成改动版指纹，下一次 flush 判「内容==基线」直接 overlays.remove，改动
        // 静默消失且标签显示干净；undo 回干净版时也靠同一基线正确清除 overlay。
        // 复查二修：供给版整个读不出来（原盘失败且 spool 也失败）→ **不写基线**，保留旧值
        // 或 null —— null 在 flush 里判脏只会多留改动，方向安全；写改动版才真丢数据。
        val p0 = target - 1
        val baselineText: String? = if (p0 in st.overlays) {
            val dir = st.spoolDir
            if (dir != null && p0 in st.spoolPages) {
                try {
                    File(dir, "page_%06d.txt".format(p0)).readText(st.doc.charset)
                } catch (e: Exception) {
                    if (diskTextOk) diskText else null
                }
            } else {
                if (diskTextOk) diskText else null
            }
        } else text
        baselineText?.let { st.baselines[target - 1] = pageBaselineFp(it) }
        _tabs.value = _tabs.value.map { t ->
            if (t.id == tabId) {
                t.copy(
                    content = text,
                    savedContent = diskText,
                    window = t.window?.copy(page = target),
                    edited = st.isDirty(),
                    contentRev = t.contentRev + 1
                )
            } else t
        }
        return true
    }

    /**
     * 把当前页的编辑内容按「是否与基线一致」归位进 spool：一致 → 从 spool 移除
     * （保存时该页走**原样字节拷贝**，不重编码、不惊动原字节），不一致 → 记入。
     */
    private fun flushWindowPage(tab: EditorTab, st: WindowState) {
        val page = tab.window?.page ?: return
        val key = page - 1
        // 批次55：基线存指纹（8B/页）而非整页文本 —— 脏判定语义不变（长度+哈希同碰撞才误判）。
        val baseline = st.baselines[key]
        val fp = pageBaselineFp(tab.content)
        if (baseline == null || baseline != fp) st.overlays[key] = tab.content
        else st.overlays.remove(key)
    }

    /**
     * 保存到已知后端（文件 / content:// / 内存文档）。 */
    fun saveActive(): Boolean {
        val tab = activeTab ?: return false
        return saveTab(tab)
    }

    /** 批次55：待「保存完成后关闭」的标签（窗口档异步写 / SAF 选择器完成时兑现）。 */
    private val pendingCloseAfterSave = mutableSetOf<String>()

    /**
     * 批次55：退出确认的「保存」—— 保存**所有**脏标签。原 saveActive 只存活动标签，
     * 多标签同时改脏时其余标签的改动随 Activity 销毁静默丢失。
     */
    fun saveAllDirty() {
        _tabs.value.filter { it.isModified }.forEach { saveTab(it) }
    }

    /** 批次56：关闭全部文件（顶栏「全部」下拉；有脏标签时调用方先弹丢弃确认）。 */
    fun closeAllTabs() {
        _tabs.value.map { it.id }.forEach { closeTab(it, force = true) }
        // 复查五修（沿报小项）：恢复期「关闭全部」还要清掉尚未建签的待恢复项 —— 否则
        // 头读慢于本轮关闭时，索引完成会把用户刚清掉的脏档又挂回来（复活已关标签）。
        // 清除后在途头读最多再弹出一个**干净**签（无法取消已发起的打开，属已知残余）。
        pendingRestores.clear()
    }

    // ==================== 批次56：会话恢复 / 快照 ====================

    /** 主线程组装出的一份会话快照（条目 + 草稿 + 改动页 + 写盘序号）。 */
    private class SessionSnapshot(
        val entries: List<EditorSessionStore.Entry>,
        val drafts: Map<String, String>,
        val overlays: Map<String, Map<Int, String>>,
        val seq: Int
    )

    /**
     * 批次56：启动恢复 —— 整读档带草稿直接重建标签；窗口档先开档（后台索引照常跑），
     * 改动页与页码等 [finishWindowedOpen] 索引就绪后经 [pendingRestores] 补挂。
     * 编码口径：整读档按存下的编码强解；窗口档回落自动识别 —— 强解要重建索引，
     * 会把刚补挂的改动页连锅端，恢复流程不代劳（需要时用户可手动重选编码）。
     * URI 标签只恢复「打开」本身（草稿不跨 content:// —— 授权时效不在本流程兜）。
     */
    private fun restoreSession() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { sessionStore.load() }
                    .onFailure { AppLog.e(TAG, "session restore load failed", it) }
                    .getOrDefault(emptyList())
            }
            if (loaded.isEmpty()) {
                sessionPersistReady = true
                return@launch
            }
            var activeId: String? = null
            loaded.forEach { l ->
                val e = l.entry
                if (e.windowed) {
                    val f = e.file?.let { File(it) } ?: return@forEach
                    if (!f.isFile || !FileAccess.isTextFile(f.name)) return@forEach
                    pendingRestores[f.absolutePath] = l
                    openFile(f)
                    val tid = _tabs.value.firstOrNull { it.file?.absolutePath == f.absolutePath }?.id
                    // 复查三修：openFile 同步失败（读取失败等，既无标签也无在途索引）——
                    // 立即终结恢复项，否则 prune 按「头读在途」保留它会永久卡住快照门禁。
                    if (tid == null && f.absolutePath !in pendingWindowOpens) {
                        pendingRestores.remove(f.absolutePath)
                    }
                    if (tid != null) {
                        if (windowState[tid] != null) {
                            // 去重命中了已完全打开的标签（启动自动恢复与会话恢复赛跑）——
                            // finishWindowedOpen 不会再来，索引已就绪就地补挂。
                            pendingRestores.remove(f.absolutePath)
                            applyWindowedRestore(tid, l)
                        }
                        if (e.active) activeId = tid
                    }
                } else if (e.file == null) {
                    // 无真实路径的条目：只剩 content:// —— 干净重开，不带草稿。
                    e.uri?.let { uri ->
                        runCatching { openUri(Uri.parse(uri)) }
                        if (e.active) activeId = _tabs.value.lastOrNull()?.id ?: activeId
                    }
                } else {
                    val f = File(e.file)
                    var detected = "UTF-8"
                    val disk = if (f.isFile) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val r = FileAccess.readText(f, e.charset)
                                if (r is FileAccess.ReadResult.Ok) {
                                    detected = r.charset
                                    r.text
                                } else null
                            }.getOrNull()
                        }
                    } else null
                    val draft = l.draft
                    // 文件没了但草稿还在 → 仍按内存档恢复（保存时重建该文件）。
                    if (disk == null && draft == null) return@forEach
                    // 批次56 复查修「冷启动双标签」：Activity 的 restoreLastOpenedIfAny 在本
                    // 恢复之前同步开过同一文件（冷启动必然先跑）—— 命中就地并入（草稿覆盖
                    // 内容），不再 addTab，否则同文件两份缓冲、后保存者覆盖前保存者。
                    _tabs.value.firstOrNull { it.file?.absolutePath == f.absolutePath }?.let { ex ->
                        // 复查二修：只并入**整读**标签 —— 文件跨运行长大已开成分页标签时，
                        // 整份旧草稿灌进 content 会被 finishWindowedOpen 判成「用户已编辑」
                        // 挂成第 0 页改动，保存即用旧文本盖写新文件（丢文件）。
                        if (draft != null && draft != ex.content && ex.window == null) {
                            _tabs.update { tabs -> tabs.map { t ->
                                // contentRev 必须 bump —— 编辑器只在 id 变化或 rev 变化时
                                // 重设文本；不 bump 则界面仍显示盘文本，第一下按键就用盘文本
                                // 回写 VM 把草稿静默覆盖丢失（复查二修·大）。
                                if (t.id == ex.id) {
                                    t.copy(content = draft, edited = true, contentRev = t.contentRev + 1)
                                } else t
                            } }
                        } else if (draft != null && ex.window != null) {
                            // 复查三修：草稿确实存在但按上注释无法安全并入 —— 明示丢弃，
                            // 免得收尾「已恢复」提示与实际不符。
                            emit("文件已变大为分页档，上一会话的未保存文本未恢复")
                        }
                        if (e.active) activeId = ex.id
                        return@forEach
                    }
                    val content = draft ?: disk!!
                    val tab = EditorTab(
                        id = newId(),
                        name = if (f.isFile) f.name else e.name,
                        file = f,
                        savedContent = disk ?: content,
                        content = content,
                        charset = e.charset,
                        effectiveCharset = if (disk != null) detected else "UTF-8",
                        edited = draft != null
                    )
                    addTab(tab)
                    if (e.active) activeId = tab.id
                }
            }
            // 恢复过程中逐个 addTab/openFile 把焦点挪到了最后一项 —— 最后校正回原焦点。
            // 批次57②：向导草稿模式下「原焦点」是被隐藏的用户签 —— 不能抢回去（内容区
            // 会空态、草稿签亮在栏里没人看），落到首枚**可见**草稿签上。
            val wiz = _wizardDraftPrefix.value
            val target = activeId?.takeIf { id ->
                wiz == null || _tabs.value.firstOrNull { it.id == id }
                    ?.file?.absolutePath?.startsWith(wiz) != true
            } ?: _tabs.value.firstOrNull {
                wiz == null || it.file?.absolutePath?.startsWith(wiz) == true
            }?.id
            if (target != null) activateTab(target)
            sessionPersistReady = true
            // 复查二修：防抖可能已在 pendingRestores 非空期间烧完 —— 收尾主动补拍。
            scheduleSessionSnapshot()
            if (_tabs.value.isNotEmpty()) emit("已恢复上次打开的标签与未保存修改")
        }
    }

    /**
     * 批次56：把会话里的改动页补挂到**索引已就绪**的窗口标签并跳回原页（主线程）。
     *
     * 顺序是命门：必须先把首页显示文本换成改动版再补挂 —— 否则 [jumpWindowPage] 的
     * [flushWindowPage] 会拿「内容 == 基线」判定把刚补挂的 overlays[0] 直接 remove 掉
     *（磁盘版文本与基线指纹相同），改动页静默丢失。
     */
    private fun applyWindowedRestore(tabId: String, l: EditorSessionStore.Loaded) {
        val st = windowState[tabId] ?: return
        val ov = l.overlays
        val tab = _tabs.value.firstOrNull { it.id == tabId }
        if (ov != null && ov.isNotEmpty() && tab?.file != null) {
            // 批次56 复查修：两次运行之间文件被外部改过（大小/修改时间不符）→ 改动页
            // 按旧页码挂上会写坏文件 —— 整体放弃补挂，保留磁盘现状并提示。
            val f = tab.file
            val changed = (l.entry.size >= 0 && f.length() != l.entry.size) ||
                (l.entry.mtime >= 0 && f.lastModified() != l.entry.mtime)
            if (changed) {
                AppLog.w(TAG, "session restore skipped: file changed externally ${f.absolutePath}")
                emit("文件已被外部修改，未恢复未保存改动")
                return
            }
            // 1)+2) 全部改动页补挂进统一供给（搜索/替换/翻页/保存都看得见）；首页改动
            // 同时覆盖 tab.content —— 顺序命门：flush 的输入必须与基线（原盘指纹）不同，
            // 否则下一次 flush 判「内容==基线」会把刚补挂的 overlays[0] 直接 remove 掉。
            val ov0 = ov[0]
            ov.forEach { (pg, text) -> st.overlays[pg] = text }
            _tabs.update { tabs -> tabs.map { t ->
                if (t.id != tabId) t
                else t.copy(
                    content = ov0 ?: t.content,
                    contentRev = if (ov0 != null) t.contentRev + 1 else t.contentRev,
                    edited = true
                )
            } }
        }
        // 3) 跳回原页（页相同则不动 —— 首页显示已在步骤 1 处理）。
        val page = l.entry.page
        if (page > 1) jumpWindowPage(tabId, page)
        // 复查二修：干净且停在首页的恢复不动 _tabs —— 补拍防抖让状态尽快固化。
        scheduleSessionSnapshot()
    }

    /**
     * 批次56：窗口档绝对行基址（UI 信息栏/跳转到行专用）—— 分页行数不等后
     * 不得再用「页号 × 页宽」换算。非窗口/索引未就绪返回 null，调用方回退旧算式
     *（此类场景只可能是第 1 页，旧算式给出 0，与真实值一致）。
     */
    fun absLineBase(tabId: String, page0: Int): Int? =
        windowState[tabId]?.doc?.lineBase(page0)

    /** 批次56：1 基绝对行 → 1 基页号（前缀和二分）；非窗口/无索引返回 null。 */
    fun pageForAbsLine(tabId: String, absLine: Int): Int? =
        windowState[tabId]?.doc?.pageForLine(absLine)

    /**
     * 批次56：抓一份会话快照并异步落盘。**必须在主线程调用** —— 先把活动脏大文档的
     * 最新文本回灌进 VM（防写旧快照），再取 overlays 副本；IO 交给 [Dispatchers.IO]。
     * 指纹相同则整轮跳过（每轮要拼 JSON + 可能写十几 MB 草稿）。
     */
    private fun snapshotSession() {
        val snap = buildSessionSnapshot() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // 批次56 复查修「乱序覆盖」：按序号单向写 —— 旧快照的 IO 协程若晚于
                // 新快照落锁，直接跳过，session.json 永不回退。
                synchronized(sessionWriteLock) {
                    if (snap.seq <= lastWrittenSeq) return@synchronized
                    lastWrittenSeq = snap.seq
                    sessionStore.save(snap.entries, snap.drafts, snap.overlays)
                }
            }.onFailure { AppLog.e(TAG, "session save failed", it) }
        }
    }

    /**
     * 复查二修：主动排一次防抖快照 —— 恢复收尾/补挂之后「状态已变但 _tabs 可能没变」
     *（如恢复的窗口档干净且停在首页，apply 一个字段都不动），没人触发就会一直等下一拍
     * 输入才固化；这里补一枪（指纹相同自然跳过，重复无害）。
     */
    private fun scheduleSessionSnapshot() {
        sessionSaveJob?.cancel()
        sessionSaveJob = viewModelScope.launch {
            delay(1200)
            if (sessionPersistReady) snapshotSession()
        }
    }

    /**
     * 批次56 复查修「恢复期中间态快照误清草稿」：还有**存活**的待恢复项（窗口档索引
     * 在建、finishWindowedOpen 未消费）时整体跳过落盘 —— 否则快照把临时标签记成
     * 「干净」，孤儿清理会把上一会话的 ov/draft 草稿文件全删，索引期间进程被杀即永久丢改动。
     */
    private fun prunePendingRestores() {
        if (pendingRestores.isEmpty()) return
        // 失效项丢弃：标签已关（如索引失败即关签），或最终以整读形态打开
        //（文件被外部改小、窗口分支不成立，finishWindowedOpen 不会再来）。
        val downgraded = HashSet<String>()
        pendingRestores.entries.retainAll { e ->
            val t = _tabs.value.firstOrNull { it.file?.absolutePath == e.key }
            // 复查三修：t == null 大多是**头读在途**（临时签还没建）—— 按「标签不在」剪掉
            // 会在慢存储上误杀还没消费的恢复项。终局移除一律走显式路径：关签 closeTab、
            // 索引失败（经 closeTab）、头读失败（startWindowedOpen）、openFile 即时失败
            //（restoreSession 队列后自检）。只有「开着但已是整读档」才按失效剪。
            val keep = t == null || t.window != null
            if (t != null && t.window == null) downgraded.add(e.key)
            keep
        }
        // 复查二修：文件变小降级整读 → 改动页随后的孤儿清理**永久**删除 —— 明示用户；
        // 标签已关的场景（用户主动关 / 索引失败已提示）不重复打扰。
        if (downgraded.isNotEmpty()) emit("文件已缩小，未保存的分页改动未能恢复")
    }

    /**
     * 主线程组装快照；返回 null = 不该写或无需写（存在待恢复项 / 指纹相同）。
     * @param refreshEditor false = 退出兜底路径：编辑器视图可能已拆除，跳过回灌直接取。
     */
    private fun buildSessionSnapshot(refreshEditor: Boolean = true): SessionSnapshot? {
        prunePendingRestores()
        if (pendingRestores.isNotEmpty()) return null
        // 复查三修（大）：保存写盘窗口内**整轮不落快照** —— 若只跳过捕获 overlays，快照
        // 照常写盘会把上一份已持久化的 ov_*.txt 当孤儿删掉；随后写盘中被杀 / 保存失败
        //（失败路径无 _tabs 变化不再触发）→ 上一会话的未保存改动永久消失。整轮跳过则旧
        // ov 文件原样保留：成功路径的 _tabs 完成簿记会触发下一拍补写（此时改动已落盘，
        // 删孤儿才是对的）；写盘中被杀则旧快照 + 旧 ov 文件指向未改动的原文件，恢复一致。
        if (savingWindows.isNotEmpty()) return null
        val activeId = _activeTabId.value
        // 脏大文档的最新文本还在编辑器里 —— 回灌后再取，防快照写旧内容。
        if (refreshEditor && activeId != null) refreshStaleContent(activeId)
        // 复查三修（中）→ 四修补漏：不止活动标签 —— 非活动窗口档切走时只回灌不 flush，
        // 当前页键入同样只停在 tab.content，kill 时进不了快照、丢失窗口可达整个停留期。
        // 统一 flush 一遍：各页到达必种基线（所有 set window.page 的路径都会种），干净页
        // fp==基线无副作用；rebuild/替换不掺和（其开始阶段已各自 flush 过），保存窗口
        // 整轮已在上方 return。onCleared 路径也跑 —— flush 只读写 VM 状态、不碰编辑器视图。
        _tabs.value.forEach { t ->
            val st = windowState[t.id]
            if (t.window != null && st != null && t.edited &&
                t.id !in rebuildingCharset.value && t.id !in replacingWindows &&
                t.id !in savingWindows &&  // 恒真（上方门禁）——留作二道保险
                t.id !in layoutDeadTabs  // 复查五修：死页的旧布局 content 一 flush 就成假 overlay
            ) {
                flushWindowPage(t, st)
            }
        }
        // 批次57②（用户指定）：插件向导草稿**不进会话缓存**（插件代码不落编辑器缓存）
        // —— 在建快照的唯一串行点剔除，防抖/退出兜底/指纹三条路径同时生效；隐藏中的
        // 用户标签照常持久化（向导期间它们没被动过，照旧保存不丢）。
        val wizPrefix = _wizardDraftPrefix.value
        val tabs = _tabs.value.filterNot {
            wizPrefix != null && it.file?.absolutePath?.startsWith(wizPrefix) == true
        }
        val entries = ArrayList<EditorSessionStore.Entry>(tabs.size)
        val drafts = HashMap<String, String>()
        val overlays = HashMap<String, Map<Int, String>>()
        // 草稿上限（字符数）：超大整读档的整份正文不进快照（打开时回盘读原文件即可，只是改动丢）。
        val draftMax = 4_000_000
        var key = tabs.size
        tabs.forEach { t ->
            val win = t.window
            entries.add(
                EditorSessionStore.Entry(
                    id = t.id,
                    file = t.file?.absolutePath,
                    uri = t.uri,
                    name = t.name,
                    charset = t.charset,
                    windowed = win != null,
                    // 批次57②：向导期间活动签是草稿（已被剔除）→ 让首枚留存标签顶上活动
                    // 位，避免会话落成「无活动标签」、下次恢复不知开哪一页。
                    active = t.id == activeId ||
                        (tabs.none { it.id == activeId } && t.id == tabs.firstOrNull()?.id),
                    page = win?.page ?: 1,
                    edited = t.edited,
                    // 批次56 复查修：文件指纹 —— 两次运行间文件被外部改过时，窗口档改动页
                    // 按旧页码挂上会写坏文件，恢复侧按此比对放弃补挂。
                    size = t.file?.length() ?: 0L,
                    mtime = t.file?.lastModified() ?: 0L
                )
            )
            // 指纹要覆盖全部会话语义字段 —— 只混草稿/页码时，重命名、换编码、脏标记翻转
            // 都不触发落盘，重启后按旧状态恢复（复查修）。
            key = key * 31 + t.id.hashCode()
            key = key * 31 + (t.file?.absolutePath?.hashCode() ?: 0)
            key = key * 31 + (t.uri?.hashCode() ?: 0)
            key = key * 31 + t.name.hashCode()
            key = key * 31 + t.charset.hashCode()
            key = key * 31 + if (t.edited) 1 else 0
            key = key * 31 + (win?.page ?: 0)
            // 草稿只给「有真实文件的整读档」——纯内存档/uri 档恢复路径不消费草稿，不白存。
            if (win == null && t.edited && t.file != null && t.uri == null &&
                t.content.length <= draftMax
            ) {
                drafts[t.id] = t.content
                key = key * 31 + t.content.length + t.content.hashCode()
            }
            // 复查二修：保存写盘窗口内 overlays 可能正被保存线程 clear —— 跳过捕获，
            // 与保存侧的 st.overlays.clear() 双保险，防「新文件指纹 + 旧页码内容」快照。
            // 复查五修：死页（旧布局 content 对不上已落盘新文件）同样跳过 —— 它们的
            // overlay 若进快照，恢复后按旧布局挂页，再保存即拼接坏文件。
            if (win != null && t.edited && t.id !in savingWindows &&
                t.id !in layoutDeadTabs
            ) {
                windowState[t.id]?.takeIf { it.overlays.isNotEmpty() }?.let { st ->
                    // overlays 只在主线程写读 —— 取副本后再交给 IO 协程。
                    val ov = HashMap(st.overlays)
                    overlays[t.id] = ov
                    key = key * 31 + ov.size
                    ov.forEach { (p, txt) -> key = key * 31 + p * 31 + txt.hashCode() }
                }
            }
        }
        // 复查二修：活动标签也进指纹（配合 _activeTabId 的防抖触发源），纯切标签才落盘。
        key = key * 31 + (activeId?.hashCode() ?: 0)
        if (key == lastSnapshotKey) return null
        lastSnapshotKey = key
        return SessionSnapshot(entries, drafts, overlays, ++sessionSeq)
    }

    /**
     * 批次56：退出兜底 —— 防抖计时可能还没到点。同步抓最后一份（上限 1.5s，
     * 指纹相同时瞬时跳过）；不能用 [viewModelScope]（onCleared 时已取消）。
     */
    override fun onCleared() {
        super.onCleared()
        runCatching {
            if (!sessionPersistReady) return
            // refreshEditor=false：编辑器视图可能已随 Activity 拆除，跳过回灌（最后一拍
            // 输入由 1.2s 防抖快照覆盖，这里只兜已回灌过的内容）。
            val snap = buildSessionSnapshot(refreshEditor = false) ?: return
            runBlocking {
                withTimeoutOrNull(1500) {
                    withContext(Dispatchers.IO) {
                        synchronized(sessionWriteLock) {
                            if (snap.seq > lastWrittenSeq) {
                                lastWrittenSeq = snap.seq
                                sessionStore.save(snap.entries, snap.drafts, snap.overlays)
                            }
                        }
                    }
                }
            }
        }.onFailure { AppLog.w(TAG, "session final save failed: $it") }
    }

    /**
     * 批次55：「保存并关闭」—— 同步保存成功立即关；窗口档（异步写盘）与写失败转入
     * SAF 的情形把关闭**推迟**到完成回调兑现，写失败绝不关标签（原实现不看保存结果，
     * 失败即丢改动 —— 改动只剩 cache spool 孤儿副本，界面再无入口）。
     * 返回 false = 保存被拒（只读/索引中/写失败未进 SAF），标签保留。
     */
    fun saveAndClose(id: String): Boolean {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return false
        val ok = saveTab(tab)
        return when {
            ok && tab.windowed -> {
                // 窗口档保存入队即返回 —— 关标签推迟到保存线程的成功/失败回调。
                pendingCloseAfterSave.add(id)
                true
            }
            ok -> {
                closeTab(id, force = true)
                true
            }
            _saveAsRequest.value?.id == id -> {
                // 写失败已转入 SAF 目录选择器 —— 关标签推迟到 completeSaveAs 成功回调。
                pendingCloseAfterSave.add(id)
                true
            }
            else -> false
        }
    }

    /** 批次55：保存完成回调 —— 兑现「保存并关闭」；失败则保留标签（既有失败提示已醒目）。 */
    private fun closeIfPendingAfterSave(id: String, success: Boolean) {
        // 保存线程（IO）也会调到这里 —— 关签牵动回灌/Compose 状态且集合非并发容器，
        // 统一切回主线程执行。
        viewModelScope.launch(Dispatchers.Main) {
            if (!pendingCloseAfterSave.remove(id)) return@launch
            if (success && _tabs.value.any { it.id == id }) closeTab(id, force = true)
        }
    }

    fun saveTab(tab: EditorTab): Boolean {
        // 批次55：Ctrl+S / 退出保存等入口绕过 UI 只读置灰直达这里 —— 只读锁必须在入口拦：
        // 索引重建失败后 windowState 还是废偏移，再存一次就会把文件切碎。
        if (readOnly.value) {
            emit("文件已临时只读（分页索引重建失败），请重新打开文件后再保存")
            return false
        }
        // 批次54：大文档编辑不逐键复制 —— 落盘前先回灌一次最新文本（脏标记驱动，干净文档零开销）。
        refreshStaleContent(tab.id)
        val fresh = _tabs.value.firstOrNull { it.id == tab.id } ?: tab
        if (fresh.windowed) return saveWindowed(fresh)
        val content = fresh.content
        when {
            tab.file != null -> {
                // 6.2.0 批次21：写入失败（含无存储权限）时不再直接报错，改为引导「另存为」。
                // 6.2.0：按 tab.writeCharset 写回 —— 自动档保持原编码，不让保存悄悄改文件编码。
                if (readOnly.value || !FileAccess.writeText(tab.file, content, tab.writeCharset)) {
                    requestSaveAs(tab)
                    emit("无法写入原文件，请选择另存目录")
                    return false
                }
            }
            tab.uri != null -> {
                if (!FileAccess.writeUri(
                        getApplication(), Uri.parse(tab.uri), content, tab.writeCharset
                    )
                ) {
                    requestSaveAs(tab)
                    emit("无法写入原位置，请选择另存目录")
                    return false
                }
            }
            else -> {
                // 内存文档：仅更新已保存基线，回传给调用方
                commitMemoryDocuments()
            }
        }
        _tabs.value = _tabs.value.map { t ->
            if (t.id == tab.id) t.copy(savedContent = content, edited = false) else t
        }
        emit("已保存 ${tab.name}")
        return true
    }

    /**
     * 6.2.0 批次46：窗口标签的保存 —— 原文件 + spool 逐页流式拼接，先写同目录临时文件、
     * 成功后原子替换；随后**重建页索引**（文件字节布局变了，旧偏移全部作废）。
     *
     * GB 级写盘需数十秒，放在**非 viewModelScope 的裸线程**：退出编辑器（VM 清场）也不中断，
     * 「保存并退出」不丢写；写失败删临时文件、原文件分毫不动。
     */
    private fun saveWindowed(tab: EditorTab): Boolean {
        // 复查六修（写坏文件·死页锁绕过）：全局 _readOnly 会被之后任何一次 openFile
        // 无条件解锁（:880 置 false 在去重早退**之前**，经文件树重开同一文件同样触发）——
        // 死签因此能再次走到写盘：旧布局 content 按新布局基线判脏，整页拼进新文件 =
        // 行重复/丢行（第六轮 JVM 实测 C 组复现）。逐签硬门禁与全局只读解耦：死签只有
        // 关签（closeTab 除名）后重新打开才能再保存。
        if (tab.id in layoutDeadTabs) {
            emit("分页索引已失效（保存期间有新输入），请关闭该标签后重新打开文件")
            return false
        }
        val st = windowState[tab.id]
        if (st == null) {
            // 批次50：渐进式打开中（首页已显示、索引在后台扫）—— 页偏移未就绪，不能写盘。
            emit("正在建立分页索引…请稍候再保存")
            return false
        }
        val target = tab.file ?: return false
        // 复查三修：>2M 字符页的键入只置脏不回传内容（contentStaleTabs）—— 完成簿记拿
        // content 比对会误判「无编辑」清掉脏标记，界面显示干净而编辑器里还有未保存文本。
        // 入口（主线程）先记陈旧态，写盘线程经闭包读到的即此刻快照。
        val staleOnEntry = tab.id in contentStaleTabs
        if (replacingWindows.contains(tab.id)) {
            emit("正在全文替换…请稍候再保存")
            return false
        }
        if (tab.id in rebuildingCharset.value) {
            // 批次55：按新编码重建期间旧索引即将作废 —— 用它写盘会错位（与替换/保存互斥同款）。
            emit("正在重新载入编码…请稍候再保存")
            return false
        }
        if (!savingWindows.add(tab.id)) {
            emit("正在保存…")
            return true
        }
        // 批次51：首击立刻给反馈 —— GB 级写盘数十秒，之前第一击全程无提示（用户以为没点上，
        // 连点出的三条「正在保存」全是重复点击的拦截图），退出编辑器后完成提示还会丢。
        emit("正在保存…")
        // 复查三修：搜索入口只单向校验保存态（搜→存拦得住，存→搜拦不住）—— 在途扫描仍握
        // 旧 doc 引用，rename 后页偏移失效，扫到后半段全是错位字节、行号错乱。主动取消
        //（结果区随之清空，保存后重新搜索即为新索引口径）。
        cancelWholeSearch()
        // 快照：写线程期间的后续编辑不影响本次写盘（编辑态照常保持未保存）。
        flushWindowPage(tab, st)
        val doc = st.doc
        val overlays = HashMap(st.overlays)
        val spoolDir = st.spoolDir
        val spoolPages = HashSet(st.spoolPages)
        val snapContent = tab.content
        val tabId = tab.id
        Thread {
            try {
                AppLog.d(
                    TAG, "windowed save start: $tabId " +
                        "overlays=${overlays.size} spool=${spoolPages.size}"
                )
                // 批次53：无改动（打开后未编辑/改动已撤销回基线）—— 跳过整份重写与索引重建，
                // 直接走成功簿记。GB 级文件点保存从 ~79 秒降到瞬时。
                if (overlays.isEmpty() && spoolPages.isEmpty()) {
                    savingWindows.remove(tabId)
                    _windowSaveProgress.update { it - tabId }
                    // 批次55：后台线程 RMW 改原子 update —— 与主线程按键编辑交错会吞输入/脏标记。
                    _tabs.update { tabs ->
                        tabs.map { t ->
                            if (t.id == tabId) t.copy(savedContent = snapContent, edited = false) else t
                        }
                    }
                    closeIfPendingAfterSave(tabId, success = true)
                    AppToast.info(getApplication(), "已保存 ${tab.name}")
                    return@Thread
                }
                // 批次47：供给器统一两种改动页 —— 手改（内存 overlay）与全文替换（磁盘 spool）。
                // spool 读失败**不降级**：异常冒泡让整次保存失败（原文件不动），绝不悄悄写回旧页。
                // 批次51：onProgress 0..100 → 底部状态栏「保存中 N%」（写盘段映射到 0..79）。
                // 批次53：返回值改为簿记 WriteResult（null = 失败）。
                val wr = doc.writeWithOverlays(
                    overlayFor = { p ->
                        overlays[p]
                            ?: if (p in spoolPages && spoolDir != null) {
                                File(spoolDir, "page_%06d.txt".format(p)).readText(doc.charset)
                            } else null
                    },
                    onProgress = { pct -> _windowSaveProgress.update { m -> m + (tabId to (pct * 8 / 10)) } }
                )
                if (wr == null) {
                    savingWindows.remove(tabId)
                    _windowSaveProgress.update { it - tabId }
                    // 批次51：完成/失败提示改走 AppToast 直发（application context）——
                    // events 流的收集器随 Activity 重建即亡（23:28 日志实证：保存成功但
                    // 「已保存」进了无人接收的 flow，用户从未看到）。AppToast 线程安全。
                    closeIfPendingAfterSave(tabId, success = false)
                    // 复查六修（小·终审建议）：保存失败 + 期间关签的组合 —— closeTab 因
                    // savingWindows 在场跳过 clearSpool（留给保存线程），失败路径又不再碰
                    // st → cache 下 spool 目录成孤儿。标签已关才清（还开着要留着重试）；
                    // 成功路径已 clearSpool 置空 spoolDir，二次清幂等跳过（同替换侧兜底）。
                    if (_tabs.value.none { it.id == tabId }) st.spoolDir?.deleteRecursively()
                    // 复查三修：失败路径无 _tabs 变化、不会自然触发快照 —— 主动排一拍，
                    // 把保存期间的新键入也固化（本线程不能直接碰 sessionSaveJob，回 Main）。
                    viewModelScope.launch(Dispatchers.Main) { scheduleSessionSnapshot() }
                    AppToast.info(getApplication(), "保存失败：无法写入原文件（${tab.name}）")
                    return@Thread
                }
                // 文件已完整写入 —— spool 使命完成，先清磁盘（重建索引要数秒，不占着临时文件）。
                clearSpool(st)
                // 复查二修「保存窗口快照 TOCTOU」：改动页已随 wr 落盘 —— 立刻清内存 overlay。
                // 否则 rename 完成到主线程换 WindowState 之间（rebuilt==null 分支则**永不换**）的
                // 防抖快照会把「新文件 size/mtime + 旧页码旧内容」一起存进 session.json，重启按
                // 新布局挂旧页、再保存即写坏文件。此时 savingWindows 未撤，翻页 flush 全被拦
                //（:1551 拒翻、saveWindowed 入口先 flush），与快照侧跳过捕获（buildSessionSnapshot）
                // 双保险；写盘期间的新键入只在 tab.content，下次 flush/保存自然接管。
                st.overlays.clear()
                // 批次51：写盘 0..79 之后进入索引重建段 —— 映射 80..99，保存全程有进度。
                _windowSaveProgress.update { m -> m + (tabId to 80) }
                // 批次53：先试**算术平移**快速重建（改动页行数守恒时毫秒级，2GB 省 ~26s）；
                // 条件不满足回落整档重扫（进度照常 80..99）。
                // 复查五修（写坏文件）：记下走的哪条重建路 —— 快速平移**布局不变**；全量
                // 重扫按全局行号重切页边界，非末页行数变化后编辑器里的旧布局 content 与
                // 新布局磁盘/基线永不相等（指纹恒异 → 下次 flush 把旧布局文本固化成假
                // overlay → 再保存 = 新布局邻居页 + 旧布局本页拼接 → 行重复/丢行，JVM 实测必然）。
                val fastRebuilt = doc.reindexAfterWrite(wr)
                val rebuilt = fastRebuilt ?: WindowedDocument.build(
                    target, doc.charset, doc.pageSize,
                    onProgress = { pct -> _windowSaveProgress.update { m -> m + (tabId to (80 + pct / 5)) } }
                )
                // savingWindows 不在此撤 —— 撤点下移到两分支簿记完成之后：让「锁在场 +
                // WS 还是旧的 + overlays 已 clear」这个组合状态不暴露给快照的全标签 flush
                // 循环（否则会把旧内容重灌进空 overlays，随新文件指纹落盘，恢复即坏）。
                if (rebuilt == null) {
                    // 复查六修（小·终审建议）：本分支簿记同样上主线程 —— 与 else 块同构，
                    //「查存活」到「置只读/种死标」之间不再有写盘线程 vs Main 关签的
                    // check-then-act 纳秒窗口；closeIfPendingAfterSave 触碰的也是 Main 专属
                    // 状态（pendingCloseAfterSave/_tabs），顺带归位到正确线程。
                    runBlocking(Dispatchers.Main) {
                        if (_tabs.value.any { it.id == tabId }) {
                            // 文件本身已写好，但旧页偏移作废 —— 锁只读，防止按废偏移再存一次切碎文件。
                            _readOnly.value = true
                            // 复查五修：盘上已是新文件而簿记还是旧布局 —— 标死页，内容禁入快照
                            //（防「新 size/mtime + 旧布局页」经会话恢复后再保存拼坏文件）。
                            layoutDeadTabs.add(tabId)
                        }
                        savingWindows.remove(tabId)
                        _windowSaveProgress.update { it - tabId }
                        closeIfPendingAfterSave(tabId, success = true) // 文件已完整写入，兑现关闭
                    }
                    // 复查三修：本分支不动 _tabs，同样主动补拍（改动已落盘，拍下「干净」态）。
                    viewModelScope.launch(Dispatchers.Main) { scheduleSessionSnapshot() }
                    AppToast.info(
                        getApplication(),
                        "已保存 ${tab.name}，但分页索引重建失败 —— 已临时只读，请重新打开文件"
                    )
                } else {
                    // 复查六修（中·TOCTOU）：判定 + 簿记**整体上主线程**执行 —— 按键回灌
                    //（updateContent/markContentEdited）全在 Main 串行，「算 canReset」与
                    //「执行 update」之间零交错窗口：readPage IO 期间的一次键入不会再被
                    // resetTo 静默覆盖（那条路丢键且无死签保护，比 mustLock 更糟）。
                    // 页号稳定性：savingWindows 仍锁着，翻页/换编码/替换全被拦，page 不变；
                    // base 的磁盘 IO 留在本线程算好，Main 块内纯内存、无挂起点（整块原子）。
                    val curPage0 = ((_tabs.value.firstOrNull { it.id == tabId }?.window?.page ?: 1) - 1)
                        .coerceIn(0, rebuilt.totalPages - 1)
                    val base = runCatching { rebuilt.readPage(curPage0) }.getOrNull()
                    runBlocking(Dispatchers.Main) {
                        // 批次55：主线程上 RMW 改原子 update（与按键编辑交错会吞输入/脏标记）。
                        val curTab = _tabs.value.firstOrNull { it.id == tabId }
                        // 仅**全量重建**（布局可能重切）且新布局该页文本 ≠ 编辑器现文本才需
                        // 重对齐；快速平移布局不变，content != base 只是保存中键入 —— 走保脏路径。
                        val needRealign = fastRebuilt == null && base != null && curTab != null &&
                            curTab.content != base
                        // 保存期间没键入也没陈旧（content == 入口快照）→ content 重设为新布局
                        // 该页文本（rev+1 让编辑器重载），视图/基线/磁盘归一，flush 即判干净。
                        val canReset = needRealign && curTab?.content == snapContent &&
                            !staleOnEntry && tabId !in contentStaleTabs
                        // 保存中有键入/陈旧 → 键入基于旧布局、无法安全落盘 —— 临时只读 + 提示
                        // 重开（同 rebuilt==null 口径，屏上文本不动），并标死页阻断快照捕获。
                        val mustLock = needRealign && !canReset
                        val resetTo = if (canReset) base else null // 非 null ⟺ 需要且可安全重设
                        // 复查六修（小）：置只读/死标前复查存活 —— 保存期间 force 关签后不给
                        // 已关签种僵尸死标，也不让全局只读误拦其它无关标签的保存。
                        if (mustLock && _tabs.value.any { it.id == tabId }) {
                            layoutDeadTabs.add(tabId)
                            _readOnly.value = true
                        }
                        _tabs.update { tabs ->
                            tabs.map { t ->
                                if (t.id == tabId) {
                                    t.copy(
                                        // 复查五修：重设后 savedContent 同步为新盘该页文本 ——
                                        // 内容比较型脏判定与磁盘口径一致，不残留假脏。
                                        savedContent = resetTo ?: snapContent,
                                        content = resetTo ?: t.content,
                                        contentRev = if (resetTo != null) t.contentRev + 1 else t.contentRev,
                                        // 保存期间若有新编辑，脏标记保持（文件里还没有那部分）。
                                        // 复查三修→四修补漏：陈旧页（>2M 键入不回传）content 比不出
                                        // 差异 —— 入口快照 + 此刻在线集合**双取**，保守保脏。
                                        // 复查五修：重设归一后必干净；其余原式。
                                        edited = resetTo == null &&
                                            (t.content != snapContent || staleOnEntry ||
                                                tabId in contentStaleTabs),
                                        window = t.window?.copy(
                                            totalLines = rebuilt.totalLines,
                                            totalPages = rebuilt.totalPages
                                        )
                                    )
                                } else t
                            }
                        }
                        // 标签在写盘期间被关掉则不再登记簿记 —— 本块在 Main 上无挂起点，
                        // 关签也在 Main，curTab 判定与 put 之间插不进关闭（复查六修化简，
                        // 原「readPage IO 后复查 + put 后撤僵尸」随 IO 外移一并退役）。
                        if (curTab != null) {
                            // 复查二修：重建后 baselines 全空 —— 不种当前页基线，flush 判
                            // null=脏，用户不编辑直接翻页也会写「与盘上完全一致」的假 overlay
                            // → 假脏标记 → 下一次保存把整档重写一遍。种入无手改供给版
                            //（= 新盘该页文本），口径同 finishWindowedOpen/换编码处。
                            windowState[tabId] = WindowState(
                                rebuilt,
                                baselines = ConcurrentHashMap<Int, Long>().apply {
                                    if (base != null) put(curPage0, pageBaselineFp(base))
                                }
                            )
                        }
                        // 复查五修：保存锁 + 进度条撤于 WS 换装之后（本块原子，锁窗闭合）。
                        savingWindows.remove(tabId)
                        _windowSaveProgress.update { it - tabId }
                        // 批次53：全文结果还开着则把页供给器指向重建后的文档 —— 保存后 overlay/
                        // spool 已清空，新文件就是唯一真源，按需预览读到的是保存后的文本。
                        if (wholeSearchTabId == tabId && _wholeFull.value != null) {
                            wholePageProvider = { p -> rebuilt.readPage(p) }
                            // 复查：重建索引后总页数可能变化 —— 供给与页数必须成对更新，否则
                            //「加载更多」按旧页数越界扫描，异常后才进 error（走重试也要再撞）。
                            wholePageCount = rebuilt.totalPages
                            wholePageLineBase = rebuilt::lineBase // 批次56：保存后结构可变，行基址同换
                        }
                        closeIfPendingAfterSave(tabId, success = true)
                        if (mustLock) {
                            AppToast.info(
                                getApplication(),
                                "已保存 ${tab.name}；保存期间的新输入基于旧分页、无法写入，请重新打开文件"
                            )
                        } else {
                            AppToast.info(getApplication(), "已保存 ${tab.name}")
                        }
                    }
                }
            } catch (e: Throwable) {
                // 复查六修（小）：扩到 Throwable —— reindexAfterWrite 段的 OOM（Error）不被
                // Exception 捕获会直接杀死线程，savingWindows 永不撤 → 全项目快照门禁与该签
                // 保存永久卡死。先撤锁再记日志（OOM 下日志本身也可能再抛，锁已脱手即安全）。
                savingWindows.remove(tabId)
                _windowSaveProgress.update { it - tabId }
                AppLog.e(TAG, "windowed save failed: $tabId", e)
                closeIfPendingAfterSave(tabId, success = false)
                // 复查六修（小·终审建议）：同 wr==null 失败分支的兜底 —— 异常期间标签被关的
                // 组合下 closeTab 跳过 clearSpool（savingWindows 在场时留给保存线程），此处
                // 补清防 cache 孤儿目录；已关才清，成功已置空的 spoolDir 幂等跳过。
                if (_tabs.value.none { it.id == tabId }) st.spoolDir?.deleteRecursively()
                // 复查四修：通用 catch 与两个显式失败分支对称补拍（本线程回 Main 再碰 job）。
                viewModelScope.launch(Dispatchers.Main) { scheduleSessionSnapshot() }
                AppToast.info(getApplication(), "保存失败: ${e.message ?: "IO 错误"}")
            }
        }.start()
        return true
    }

    /** 置位待另存标签，交由 Activity 弹 SAF 目录选择器。 */
    private fun requestSaveAs(tab: EditorTab) {
        _saveAsRequest.value = tab
    }

    /**
     * 6.2.0 批次21：用户经 SAF 选好目录后，把待另存标签的内容写进该目录的新文档，
     * 并把标签改指向新 Uri（后续保存直接走 ContentResolver，不再受存储权限限制）。
     */
    fun completeSaveAs(treeUri: Uri) {
        val requested = _saveAsRequest.value ?: return
        _saveAsRequest.value = null
        if (requested.windowed) {
            emit("窗口模式暂不支持另存为，请直接保存到原文件")
            closeIfPendingAfterSave(requested.id, success = false)
            return
        }
        // 批次55：大文档脏内容先回灌，再取**当前**文本另存 —— 原实现用置位时的快照，
        // 选择期间的编辑既写不进新文件、脏标记还会被抹成「已保存」（静默丢改动）。
        refreshStaleContent(requested.id)
        val tab = _tabs.value.firstOrNull { it.id == requested.id } ?: requested
        val newUri = FileAccess.saveAsToTree(
            getApplication(), treeUri, tab.name, tab.content
        )
        if (newUri != null) {
            _tabs.update { tabs ->
                tabs.map { t ->
                    if (t.id == tab.id) {
                        t.copy(
                            file = null,
                            uri = newUri.toString(),
                            savedContent = t.content,
                            edited = false
                        )
                    } else t
                }
            }
            _readOnly.value = false
            closeIfPendingAfterSave(tab.id, success = true)
            emit("已另存: ${tab.name}")
        } else {
            closeIfPendingAfterSave(tab.id, success = false)
            emit("另存失败")
        }
    }

    /**
     * 6.2.0：系统「另存为」文件选择器返回 Uri 后，把标签内容写入该 Uri 并改指向它
     * （后续保存直接走 ContentResolver，不再受存储权限限制）。
     * 用**当前**内容而非置位时的快照，避免选择期间的编辑丢失。
     */
    fun completeSaveAsDoc(uri: Uri) {
        val requested = _saveAsDocRequest.value ?: return
        _saveAsDocRequest.value = null
        if (requested.windowed) {
            emit("窗口模式暂不支持另存为，请直接保存到原文件")
            closeIfPendingAfterSave(requested.id, success = false)
            return
        }
        // 批次55：大文档脏内容先回灌再取当前文本（否则写出的是编辑前快照）。
        refreshStaleContent(requested.id)
        val current = _tabs.value.firstOrNull { it.id == requested.id } ?: requested
        if (!FileAccess.writeUri(getApplication(), uri, current.content, current.writeCharset)) {
            closeIfPendingAfterSave(current.id, success = false)
            emit("另存失败")
            return
        }
        _tabs.update { tabs ->
            tabs.map { t ->
                if (t.id == current.id) {
                    t.copy(
                        file = null,
                        uri = uri.toString(),
                        savedContent = t.content,
                        edited = false
                    )
                } else t
            }
        }
        _readOnly.value = false
        closeIfPendingAfterSave(current.id, success = true)
        emit("已另存: ${current.name}")
    }

    /** 把所有内存文档回传给调用方（插件向导回调）。 */
    fun commitMemoryDocuments() {
        val docs = _tabs.value.filter { it.file == null && it.uri == null }
            .map { it.name to it.content }
        if (docs.isNotEmpty()) onMemoryDocumentsCommitted?.invoke(docs)
    }

    /**
     * 批次55：统一的「切活」入口 —— 切走前回灌脏大文档（与 setActive 同款）。
     * 绕过这里直接写 _activeTabId 会让脏标签带着过期 content 静默切走，
     * 切回时 EditorCanvas 按过期文本重设编辑器，编辑就"消失"了。
     */
    private fun activateTab(id: String?) {
        _activeTabId.value.let { old -> if (old != null && old != id) refreshStaleContent(old) }
        _activeTabId.value = id
    }

    fun closeTab(id: String, force: Boolean = false): Boolean {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return false
        if (tab.isModified && !force) return false
        // 批次55：标签簿记随关签清理 —— 挂起的「保存并关闭」与脏标记都不该跨签存活。
        // 先撤脏标记，下面 activateTab 的回灌即成空操作（内容本就要丢弃/已兑现）。
        pendingCloseAfterSave.remove(id)
        contentStaleTabs.remove(id)
        layoutDeadTabs.remove(id) // 复查五修：死页标记随签消亡，防集合慢性膨胀
        val idx = _tabs.value.indexOfFirst { it.id == id }
        val remaining = _tabs.value.filterNot { it.id == id }
        _tabs.value = remaining
        // 批次46：窗口簿记随标签清理（后台保存线程持有自己的快照，不受影响）。
        // 批次47：磁盘 spool 一并删除；保存进行中则留给保存线程完成后清（它正读着这些文件）。
        windowState.remove(id)?.let { st ->
            if (!savingWindows.contains(id)) clearSpool(st)
        }
        // 复查三修：待恢复项随关签终结 —— 否则 t==null 的终局项没人移除，会把快照门禁
        // 永久卡死（prune 现按「头读在途」保留缺席项，终局一律靠这里的显式移除）。
        tab.file?.absolutePath?.let { pendingRestores.remove(it) }
        if (wholeSearchTabId == id) cancelWholeSearch()
        if (wholeReplaceTabId == id) cancelWholeReplace()
        if (_activeTabId.value == id) {
            activateTab(remaining.getOrNull(idx)?.id ?: remaining.lastOrNull()?.id)
        }
        return true
    }

    fun hasUnsaved(): Boolean = _tabs.value.any { it.isModified }

    // ==================== 文件树操作 ====================

    /** 新建文件的默认落点：优先当前选中目录，否则项目根。 */
    private fun creationBase(): File? =
        _selectedDir.value?.takeIf { it.isDirectory } ?: _projectRoot.value

    fun newFile(name: String, content: String = ""): File? {
        val dir = creationBase() ?: return null
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) {
            emit("名称不能为空")
            return null
        }
        val target = File(dir, clean)
        if (target.exists()) {
            emit("同名文件已存在: $clean")
            return null
        }
        target.parentFile?.mkdirs()
        if (!FileAccess.writeText(target, content)) {
            emit("创建失败: $clean")
            return null
        }
        expandAncestors(target)
        refreshTree()
        openFile(target)
        return target
    }

    /**
     * 6.2.0 批次16：在当前选中目录（或根）下新建文件夹。
     * 支持形如 `a/b` 的名字 —— mkdirs() 会一并建出中间目录 a 及其下的 b。
     */
    fun newFolder(name: String): File? {
        val base = creationBase() ?: return null
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) {
            emit("名称不能为空")
            return null
        }
        val target = File(base, clean)
        if (target.exists()) {
            emit("同名目录已存在: $clean")
            return null
        }
        // mkdirs 递归建出 a/b（含中间的 a）；任一层已存在同名文件时会失败
        if (!target.mkdirs()) {
            emit("创建目录失败: $clean")
            return null
        }
        expandAncestors(target)
        refreshTree()
        emit("已创建目录 $clean")
        return target
    }

    fun deleteFile(file: File): Boolean {
        // 6.2.0 批次12：File.delete() 对**非空目录**永远返回 false。
        // 打开侧栏文件树上的 ⋮ 删除一个文件夹时，实际从未删掉过。这里改为递归删除。
        // 批次55：命中的打开标签里有**未保存改动**时先拦下 —— 原实现删除后 force 关签，
        // 未保存的编辑连同标签一起静默消失（确认框只问「删除 xxx？」，没问丢不丢改动）。
        val matched = _tabs.value.filter {
            it.file != null &&
                (it.file!!.absolutePath == file.absolutePath ||
                    it.file!!.absolutePath.startsWith(file.absolutePath + File.separator))
        }
        matched.firstOrNull { it.isModified }?.let {
            emit("「${it.name}」有未保存修改 —— 请先在编辑器里保存或放弃，再删除")
            return false
        }
        val ok = try {
            deleteRecursively(file)
        } catch (e: Exception) {
            AppLog.e(TAG, "deleteFile failed: ${file.absolutePath}", e)
            false
        }
        if (ok) {
            matched.forEach { closeTab(it.id, force = true) }
            refreshTree()
            emit("已删除 ${file.name}")
        } else {
            emit("删除失败: ${file.name}")
        }
        return ok
    }

    /** 递归删除文件或目录；返回 false 表示有任何一项未能删除。 */
    private fun deleteRecursively(f: File): Boolean {
        if (f.isDirectory) {
            var ok = true
            f.listFiles()?.forEach { ok = deleteRecursively(it) && ok }
            // 目录内容删干净后才能删掉目录本身
            return f.delete() && ok
        }
        return f.delete()
    }

    fun renameFile(file: File, newName: String): Boolean {
        if (newName.isBlank()) {
            emit("名称不能为空")
            return false
        }
        val target = File(file.parentFile, newName)
        if (target.exists()) {
            emit("同名文件已存在: $newName")
            return false
        }
        // 批次55：命中正在保存/替换/重建编码的打开标签先拦下 —— 这些流程持着旧路径/旧索引
        // 快照，此刻改名会把数据写到旧路径（复活改名前的文件）或按废偏移读写。
        val affectedIds = _tabs.value.filter { t ->
            val p = t.file?.absolutePath
            p != null && (p == file.absolutePath ||
                p.startsWith(file.absolutePath + File.separator))
        }.map { it.id }
        if (affectedIds.any {
                it in savingWindows || it in replacingWindows || it in _rebuildingCharset.value
            }
        ) {
            emit("正在保存/替换/重新载入…请稍候再重命名")
            return false
        }
        if (!file.renameTo(target)) {
            emit("重命名失败: ${file.name}")
            return false
        }
        // 6.2.0 批次12：重命名**目录**时，要把它下面所有已打开标签的路径一并迁移，
        // 否则标签仍指向旧路径，保存会失败（旧代码只匹配了被重命名的那一项自身）。
        val oldPath = file.absolutePath
        val newPath = target.absolutePath
        _tabs.value = _tabs.value.map { t ->
            val p = t.file?.absolutePath
            when {
                p == null -> t
                p == oldPath -> t.copy(file = target, name = newName)
                p.startsWith(oldPath + File.separator) -> {
                    // 父目录改名不改变子文件自己的 basename，标签名保持不变，只迁移路径
                    t.copy(file = File(newPath + p.removePrefix(oldPath)))
                }
                else -> t
            }
        }
        // 批次55：窗口档内部 doc.file 仍指旧路径 —— 不迁移的话翻页读旧路径失败、
        // 保存写旧路径（目录里凭空"复活"一份改名前的文件）。按迁移后的 tab.file 同步。
        affectedIds.forEach { tid ->
            val newFile = _tabs.value.firstOrNull { it.id == tid }?.file ?: return@forEach
            windowState[tid]?.doc?.file = newFile
        }
        // 选中目录若在被重命名的子树内，一并迁移，避免选中态指向已不存在的旧路径
        _selectedDir.value?.absolutePath?.let { sp ->
            when {
                sp == oldPath -> _selectedDir.value = target
                sp.startsWith(oldPath + File.separator) ->
                    _selectedDir.value = File(newPath + sp.removePrefix(oldPath))
            }
        }
        // 展开集合同步迁移旧路径前缀，避免展开状态丢失
        val migrated = _expandedDirs.value.map { ep ->
            when {
                ep == oldPath -> newPath
                ep.startsWith(oldPath + File.separator) -> newPath + ep.removePrefix(oldPath)
                else -> ep
            }
        }.toSet()
        _expandedDirs.value = migrated
        expandAncestors(target)
        refreshTree()
        emit("已重命名为 $newName")
        return true
    }

    /**
     * 6.2.0 批次16：手动为当前标签页指定语法高亮 scope（null = 恢复按后缀自动推断）。
     * 右上角语法选择器调用；改的是 tab.languageScope，EditorCanvas 会据此重设语言。
     */
    fun setActiveLanguageScope(scope: String?) {
        val id = _activeTabId.value ?: return
        _tabs.value = _tabs.value.map { t ->
            if (t.id == id) t.copy(languageScope = scope) else t
        }
    }

    // ==================== 设置 ====================

    fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        EditorSettingsStore.save(getApplication(), next)
    }

    private fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
}
