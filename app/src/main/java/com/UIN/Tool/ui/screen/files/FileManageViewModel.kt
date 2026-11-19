// app/src/main/java/com/UIN/Tool/ui/screen/files/FileManageViewModel.kt
package com.UIN.Tool.ui.screen.files

import androidx.annotation.StringRes
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ==================== M3 数据件（4.3–4.5） ====================

/** 长按上下文菜单的目标条目（4.3）。 */
data class ContextMenuEntry(
    val side: Side,
    val path: String,
    val name: String,
    val isDir: Boolean,
    /** 菜单动作作用于**整批**选中项（长按的这条已在选中集里且不止一项）。 */
    val batch: Boolean,
    /** 整批时选中集的条数 —— 「对比」按恰好 2 条门控用。 */
    val selectionSize: Int
)

/**
 * 删除确认框的数据（4.5）。
 *
 * [measuring] = true 时正文显示「统计中…」，体积算完后由 [FileManageViewModel.onDeleteMeasured]
 * 回填 [totalBytes] / [entryCount]，对话框随即换成「8.26 MB · 3 项」。
 */
data class DeleteRequest(
    val paths: List<String>,
    val names: List<String>,
    val measuring: Boolean = true,
    val totalBytes: Long = -1L,
    val entryCount: Int = -1,
    /** 关键系统目录（/system、/data…）：再叠一层危险提示（4.5）。 */
    val dangerous: Boolean = false
)

/** 重命名框的数据（4.5）。撞名时用 [suggestedName] 给建议名。 */
data class RenameRequest(
    val side: Side,
    val path: String,
    /** 打开即选中的主干名（不含扩展名）—— MP 式行为，省掉一次「删后缀再重打」。 */
    val initialName: String,
    /** 打开后要把整个主干名选中的标记位，选完即清。 */
    val selectStem: Boolean = true,
    /** 目标名已存在时的建议名（`name (1).ext`），对话框直接显示出来。 */
    val suggestedName: String = ""
)

/** 批量重命名框的数据。[paths] 是进入多选后被选中的那批源路径。 */
data class BatchRenameRequest(
    val side: Side,
    val paths: List<String>
)

/** 新建框的数据（4.5）。一个对话框三个键：取消 / 文件夹 / 文件。 */
data class CreateRequest(
    val side: Side,
    val dirPath: String,
    val initialName: String = "",
    /** 非空 = 当前输入名已存在，直接把建议名摆出来而不是等按下才报错。 */
    val suggestedName: String = ""
)

/**
 * 压缩框的数据（§4.7「压缩」）。
 *
 * [sources] 是**已选好的真实文件**（归档内条目不给压缩 —— 那等于要先解出来，语义不清，
 * 宁可让用户先解压再压）。[dirPath] 是落点目录，包会建在那儿。
 *
 * [initialName] 由调用方按「第一个源的名字去后缀」给，用户不必从空框开始打。
 */
data class CompressRequest(
    val side: Side,
    val dirPath: String,
    val sources: List<String>,
    val initialName: String,
    /** 目标名已存在时的建议名（`name (1).zip`）。 */
    val suggestedName: String = ""
)

/**
 * 输出名撞车的**三选**（反馈 e17：「新增输出名冲突三选」）。
 *
 * 原先 `performCompress` 一上来就 `uniqueSibling()` 把名字静默改成 `x (1).zip` ——
 * 压完才发现包名跟自己打的不一样，而且**永远走不到** `ArchiveCompress.create`
 * 里那个「目标已存在」的询问（目标被它提前挪走了），更没有「并进已有包」这条路。
 *
 * 现在撞了先停下来问：追加进已有包 / 换个名字新建 / 取消。
 *
 * @param existing 已存在的那个包（绝对路径）
 * @param suggested 换名后的建议文件名（`x (1).zip`）
 */
data class CompressConflict(
    val existing: String,
    val suggested: String,
    val side: Side,
    val sources: List<String>,
    val format: ArchiveCompress.Format,
    val password: String?,
    val level: String,
    val encoding: String,
    val keepMeta: Boolean,
    /** 分卷字节数；0 = 不分卷（用户反馈 e15 **P1-9**「分卷给入口」）。 */
    val splitSize: Long = 0L,
    /** 7z 专属：连文件名表一起加密。 */
    val encryptHeader: Boolean = false
)

/**
 * 整包解压框的数据（用户反馈⑪：**解压时弹窗选落点**，不再往设置里塞）。
 *
 * [archivePath] 是真实归档的绝对路径（菜单门控已挡掉归档内条目与目录）。
 * [cwd] 是按下菜单时窗格所在的目录 —— 它可能是归档内的虚拟路径，
 * 那种情况下「当前目录」根本落不了地，[initialTarget] 已经替用户改选成「归档旁」。
 *
 * [initialTarget] 由调用方按 [FilePrefs.extractTarget()] 与 [cwd] 校正后给，
 * 用户改过的目标目录存在 [FilePrefs.customExtractTarget()] 里，框自己去读。
 */
data class ExtractRequest(
    val side: Side,
    val archivePath: String,
    val cwd: String,
    val initialTarget: String
)

/**
 * 归档内条目复制/移动到另一窗格时撞上加密包，先要个密码再动手（b10 的补口）。
 *
 * 引擎在**动手之前**就会拒绝没有密码的加密包 —— 可那条路上原本没有给密码的地方：
 * 报一句「请在解压时输入密码」，用户却无处可输，等于让他自己换条路走。
 * [sources] / [targetDir] 就是被打断的那一次拷贝，拿到密码后原样重跑。
 */
data class ArchivePasswordRequest(
    val sources: List<String>,
    val targetDir: String,
    /**
     * 非 null = 这次是**浏览**被打断（e15 **P1-12**）：7z 的头被加密，
     * 连文件名都读不出来。[browseSide] / [browseArchive] 是被打断时的窗格与包 ——
     * 拿到密码后原样重开。与 [targetDir] 那套「解压落点」互不相干，两条路共用一个框。
     */
    val browseSide: Side? = null,
    val browseArchive: String? = null
)

/**
 * 一次文件操作的对话框状态机。
 *
 * `Idle → Running → Idle` 是主干；撞名时插一个 `Conflict`，用户答复后回到 `Running`。
 * [Running.hidden] = 用户按了「隐藏」——对话框收起但**操作没停**，靠 `Hidden` 那个小胶囊唤回。
 */
sealed interface OpUiState {
    /** 没有进行中的操作。 */
    data object Idle : OpUiState

    /** 运行中。[progress] 是七层对话框的数据源。 */
    /**
     * [cancelling] = 用户已经按过取消，正等底层把手头这一段写完。
     *
     * f19「需要多次点击取消才能取消」：取消是协程级的，而压缩/复制在**单个大文件**里
     * 是一整段阻塞 IO，插不进取消点；原先按一下没有任何可见反馈，用户只能反复点。
     * 现在按第一下就把状态置成「取消中」并禁用按钮，剩下交给底层在下一个检查点收尾。
     */
    data class Running(
        val progress: OpProgress,
        val hidden: Boolean = false,
        val cancelling: Boolean = false
    ) : OpUiState

    /** 撞名，等用户三选（4.4）。 */
    data class Conflict(val items: List<ConflictItem>) : OpUiState
}

/**
 * 一次性提示事件（VM 不持有 Context，字符串与 toast 由 UI 层落）。
 *
 * 用 `SharedFlow(extraBufferCapacity = 1)` 而不是塞进 [FileManageUiState] ——
 * 状态流里放「已消费完的一次性事件」会重复弹。
 */
sealed interface NoticeEvent {
    /**
     * 操作收尾汇总（4.4 第 4 条：成功 n / 失败 m / 首个错误）。
     *
     * [doneRes] 是成功时的**专用文案**（带一个「成功条数」参数）：删除进回收站要说
     * 「已移入回收站 · 共 N 项」，而不是笼统的「删除完成」。null = 用通用文案。
     */
    data class OpDone(
        val kind: OpKind,
        val result: OpResult,
        val totalItems: Int,
        @StringRes val doneRes: Int? = null
    ) : NoticeEvent

    /** 直接给字符串资源 + 一个参数。 */
    data class Text(@StringRes val res: Int, val arg1: String = "") : NoticeEvent

    /**
     * 解压跑完了、**落点在 [dir]**，供 UI 弹一个带「定位」的完成框（e15 **P2-16**）。
     *
     * 单独成一条而不是塞进 [OpDone]：完成框是解压**独有**的呈现，复制/移动/删除
     * 仍然是收尾 toast —— 给它们也弹一框，日常小操作会被打扰。
     *
     * @param highlight 落点里要选中的那一条（解出来的根）。`navigateTo` 会把
     *                  `highlightPath` 清成 null，所以必须**先跳再高亮**，
     *   两个值一起交给 UI 才好按那个顺序做。
     */
    data class ExtractLocated(val dir: String, val highlight: String?) : NoticeEvent
}

/** 双窗格整页状态（MT.md §5.2）。 */
/**
 * 单窗口下的「先拿起、后粘贴」（用户反馈㉒）。
 *
 * 双窗格时代复制/移动的目标是**另一栏**，点一下就落到对面；单窗口没有第二栏，
 * 目标目录只能由用户**之后**走到 —— 所以先把源记在这儿，走到哪儿粘到哪儿。
 * 关闭按钮丢弃这次拿起，不做任何文件操作。
 */
data class PendingTransfer(val kind: OpKind, val sources: List<String>)

data class FileManageUiState(
    val left: PaneState = PaneState(),
    val right: PaneState = PaneState(),
    val active: Side = Side.LEFT,
    /** 多选态：底栏整排替换为「全选 · 反选 · 退出 · 同类型」+ 常驻「已选 N 项」（4.1/4.3） */
    val multiSelect: Boolean = false,
    /** 设置里的「默认单/双窗格」，改完立即生效（4.17） */
    val dualEnabled: Boolean = true,
    /** ⋮ → 过滤：独立过滤条是否展开（不替换路径行，4.8） */
    val filterBarVisible: Boolean = false,
    /**
     * 长按弹出的上下文菜单目标（4.3）。null = 关着。
     *
     * 放在整页状态里而不是行内局部状态，是为了让**返回键**能关掉它（4.2 的优先级链），
     * 也让「点菜单时窗格已切活动」这件事有处可查。
     */
    val contextMenu: ContextMenuEntry? = null,
    /** 删除确认框（4.5）。非空 = 弹框；体积在 [DeleteRequest.totalBytes] 里异步补上。 */
    val deleteRequest: DeleteRequest? = null,
    /** 重命名框（4.5）。 */
    val renameRequest: RenameRequest? = null,
    /** 批量重命名框。非空 = 弹框；与单条的 [renameRequest] 互斥（单条语义 vs 批量语义）。 */
    val batchRename: BatchRenameRequest? = null,
    /** 新建框（4.5）。 */
    val createRequest: CreateRequest? = null,
    val compressRequest: CompressRequest? = null,
    /** 输出名撞车的三选（反馈 e17）；非空时压在 `compressRequest` 之上再摆一层框。 */
    val compressConflict: CompressConflict? = null,
    /** 整包解压框（用户反馈⑪）。非空 = 弹框选目标目录。 */
    val extractRequest: ExtractRequest? = null,
    /** 归档内复制到另一窗格撞上加密包 → 先弹密码框（非空 = 弹框）。 */
    val archivePassword: ArchivePasswordRequest? = null,
    /**
     * 属性框（4.5）。非空 = 弹框显示属性。
     *
     * n.txt 第 1 条：列表改成**列表**而不是单条 —— 多选后长按「属性」原先只取
     * `targets.firstOrNull()`，用户看到的是第一项的属性，与「我已选 5 个」的预期
     * 完全不符。现在整批一起传进来：单条走原来的文件属性框，多条走「整体属性」
     * （合计大小 / 类型统计 / 最新修改时间 / 公共权限）。
     */
    val propertiesPaths: List<String> = emptyList(),
    /** 进行中的文件操作（4.12 七层进度对话框）。 */
    val op: OpUiState = OpUiState.Idle,
    /**
     * 单窗口「先拿起、后粘贴」（用户反馈㉒）。非 null = 有一批源文件正等着粘到某个目录，
     * 此时列表右下角挂着悬浮的粘贴/关闭按钮。
     */
    val pendingTransfer: PendingTransfer? = null,
    /**
     * 设置改动计数位。
     *
     * `MutableStateFlow` 会**丢弃与当前值相等的新状态**，而行密度这类设置只存在 [FilePrefs] 里、
     * 不在 [PaneState] 上——`fit()` 全程 `copy()` 出来的值会 equals → 不发射 → 页面不重组 →
     * 改了等于没改。每次 [FileManageViewModel.applySettings] 自增一位，保证设置改动一定重画。
     */
    val settingsTick: Int = 0
) {
    operator fun get(side: Side): PaneState = if (side == Side.LEFT) left else right

    val activePane: PaneState get() = get(active)

    val otherSide: Side get() = if (active == Side.LEFT) Side.RIGHT else Side.LEFT

    val otherPane: PaneState get() = get(otherSide)

    val selectedCount: Int get() = activePane.selection.size

    fun mutate(side: Side, transform: (PaneState) -> PaneState): FileManageUiState =
        if (side == Side.LEFT) copy(left = transform(left)) else copy(right = transform(right))
}

/**
 * 「已定位」高亮保留多久（用户要求：显示 5 秒就消失）。
 */
private const val HIGHLIGHT_LINGER_MS = 5_000L

/** l.txt 第 3 条：逐条 stat 深检的间隔（目录指纹轮询之外的那一档）。 */
private const val AUTO_DEEP_INTERVAL_MS = 15_000L

/**
 * 双窗格文件管理器状态机：**两窗独立导航、历史独立、选择集独立**（4.2/4.3）。
 *
 * - 目录扫描一律 `Dispatchers.IO`，UI 只订阅 [StateFlow]（5.3/5.4：主线程零 IO）；
 * - 加载结果按 `cwd` 对账，用户已经走掉的过期结果直接丢弃，杜绝「旧目录刷进新目录」；
 * - 设置读写走 [FilePrefs]，[applySettings] 把「改动即时生效」（4.17）收敛成一个入口。
 *
 * 旋转由 ViewModel 天然扛住（5.2）；进程重建由 [FilePrefs.sessionJson] 的会话快照在 init 时恢复（[loadSession]）。
 */
class FileManageViewModel : ViewModel() {

    private val _state = MutableStateFlow(FileManageUiState())
    val state: StateFlow<FileManageUiState> = _state.asStateFlow()

    /**
     * 两窗各自的**首个可见项下标**（e12「滑动列表卡顿」的根因之一）。
     *
     * 故意**不放进 [_state]**：`setScroll` 在滚动时一秒能来十几趟，写进状态就等于让
     * FilesScreen → FileListPane → **每一行**都跟着重组一遍。`PaneState` 里有 `List`/`Map`，
     * Compose 判它是不稳定类型，行闭包里捕获的 `pane` 只能按**引用**比 —— 引用一变，
     * 整批可见行全部重来，目录越大越明显。
     *
     * 这里是个没人订阅的普通字段，写多少次 UI 都听不见；换目录时由 navigateTo /
     * goBack / goForward 三处把它并进 `scrolls`（见 [PaneState.memoScroll]）。
     */
    private val liveScroll = IntArray(2)

    // ---- l.txt 第 3 条：自动刷新的指纹簿 ----
    /** 每栏上次的目录指纹（lastModified + 条目数）；null = 还没建立基线。 */
    private val autoCheap = arrayOfNulls<String>(2)
    /** 每栏上次的逐条指纹（名字/大小/时间）；只有深检档更新它。 */
    private val autoDeep = arrayOfNulls<String>(2)
    /** 每栏是否有一轮检测正在跑（IO stat 未回）—— 防止轮询叠罗汉。 */
    private val autoBusy = BooleanArray(2)
    /** 上次深检的时刻（elapsedRealtime）。 */
    private var lastAutoDeepAt = 0L

    /**
     * 临时挂载的包 → 它来自哪个目录（包中包）。
     *
     * 值可能是虚拟路径（父包内目录）。只增不减会慢慢变多，所以 [mountArchive] 卡了 64 条。
     */
    private val mountedArchiveOrigins = LinkedHashMap<String, String>()

    /** 一次性提示（toast 用）。放 SharedFlow 不放 StateFlow —— 状态流会重放，toast 会连弹。 */
    private val _notices = MutableSharedFlow<NoticeEvent>(extraBufferCapacity = 4)
    val notices: SharedFlow<NoticeEvent> = _notices.asSharedFlow()

    /** 当前进行中的操作任务；起新操作前先取消旧的，避免两个写操作同时跑。 */
    private var opJob: kotlinx.coroutines.Job? = null

    /** 撞名对话框的挂起点：用户一答复就 complete，引擎从 await 处继续。 */
    private var conflictWaiter: CompletableDeferred<ConflictDecision?>? = null

    /**
     * 「已定位」自动退场任务，左右各一个（用户要求：只显示 5 秒）。
     *
     * 新定位会先取消旧任务，到点后再确认一次仍是**同一条**路径才清空 —— 否则用户在
     * 5 秒内又定位了别的条目，旧任务会把后一次高亮误清掉。
     */
    private val highlightClearJobs = arrayOfNulls<kotlinx.coroutines.Job>(2)

    /**
     * 历史变更信号（§4.9 底部抽屉）。
     *
     * 历史本身存在 `FilePlaces` 的 SharedPreferences 里（不在 UI 状态里），这里只负责「变了没有」。
     * 用**自增的 StateFlow**：StateFlow 会丢弃与当前值相等的新值，所以必须让值一定不同 ——
     * 自增恰好保证这一点，抽屉那边 `collect` 就能每次都收到。
     */
    private val _historyTick = MutableStateFlow(0L)
    val historyTick: StateFlow<Long> = _historyTick.asStateFlow()

    init {
        applySettings(firstLaunch = true)

        // e15 **P1-11**：写进包里的任何一条改名 / 删除 / 加条目 / 编辑器写回，
        // 都会让 APK 的签名当场失效，而用户多半是**改完才发现装不上**的。
        // 这儿只负责把话说了；修的那条路在 APK 信息页（「重新签名」）。
        ZipWrite.onArchiveWritten = { archive ->
            // f11：写完立刻作废旧条目缓存 —— 同一毫秒内改完且大小恰好不变时，
            // 「路径 + 大小 + mtime」这个键不会变，列表会看不到刚加进去的条目。
            invalidateArchiveEntries(archive)
            if (archive.name.endsWith(".apk", ignoreCase = true)) {
                _notices.tryEmit(NoticeEvent.Text(R.string.apk_signature_stale))
            }
        }
        // 会话快照落盘：只有「位置/历史/活动窗格」变化才写（distinctUntilChanged 挡掉
        // loading、选择集这类高频噪声，避免每次重组都写一次 prefs）
        viewModelScope.launch {
            state
                .map { sessionSnapshot(it) }
                .distinctUntilChanged()
                .collect { json -> if (json.isNotEmpty()) FilePrefs.setSessionJson(json) }
        }

        // 用户反馈 e15 P0-4：操作跑着的时候给一条**常驻进度通知** —— 弹窗挡着时照样能回
        // 文件管理器，进度在状态栏里看得见。收在一条线上（离开 Running 就取消）而不是散在
        // 各个收尾点：状态里有好几处直接置 Idle，漏一个通知就挂那儿不消失。
        // 撞名询问也走「取消」那边 —— 那时需要的是用户注意，不是状态栏里的一行字。
        viewModelScope.launch {
            state.map { it.op }.distinctUntilChanged().collect { op ->
                val app = com.UIN.Tool.UinApplication.getAppContext()
                if (op is OpUiState.Running) OpNotification.show(app, op.progress)
                else OpNotification.cancel(app)
            }
        }
    }

    // ==================== 目录扫描 ====================

    private data class ScanResult(val items: List<FileEntry>, val errorRes: Int?)

    /** 扫一个目录。异常路径都折成错误码，调用方不会因此崩溃（5.4）。 */
    private fun scan(dir: File): ScanResult = try {
        when {
            !dir.exists() -> ScanResult(emptyList(), R.string.files_path_missing)
            !dir.isDirectory -> ScanResult(emptyList(), R.string.files_not_a_directory)
            !dir.canRead() -> ScanResult(emptyList(), R.string.files_no_access)
            else -> {
                val children = dir.listFiles()
                    ?: return ScanResult(emptyList(), R.string.files_no_access)
                ScanResult(
                    items = children.map { f ->
                        // 一次 `stat(2)` 同时拿类型 / 大小 / 时间。原先 `isDirectory` +
                        // `isFile` + `length()` + `lastModified()` 是 3～4 次系统调用，
                        // 上万条的目录光 stat 就要好几秒（i.txt 第 11 条「大量内容加载卡」）。
                        val path = f.absolutePath
                        val st = runCatching { android.system.Os.stat(path) }.getOrNull()
                        val isDir = st != null && (st.st_mode and android.system.OsConstants.S_IFMT) ==
                            android.system.OsConstants.S_IFDIR
                        FileEntry(
                            name = f.name,
                            path = path,
                            isDir = isDir,
                            size = if (isDir) 0L else (st?.st_size ?: 0L),
                            // Os.stat 的 st_mtime 是**秒**，File.lastModified() 是毫秒 ——
                            // 不乘 1000 会让所有文件都显示成 1970 年。
                            modified = (st?.st_mtime ?: 0L) * 1000L
                        )
                    },
                    errorRes = null
                )
            }
        }
    } catch (e: Exception) {
        ScanResult(emptyList(), R.string.files_no_access)
    }

    private fun load(side: Side) {
        val target = _state.value[side].cwd
        if (target.isEmpty()) return
        // l.txt 第 3 条：换目录/主动刷新后重新建立自动刷新基线 —— 否则下一轮轮询
        // 会把「刚载入的目录」误判成变化再扫一遍。
        autoCheap[side.ordinal] = null
        autoDeep[side.ordinal] = null
        // 归档内的 cwd 是虚拟路径（`x.zip!/src`），拿它去 `File(...)` 会当成不存在的目录
        val split = VirtualPath.split(target)
        _state.update { it.mutate(side) { p -> p.copy(loading = true, errorRes = null, indexingCount = null) } }
        viewModelScope.launch {
            if (split != null) {
                loadArchive(side, target, split.first, split.second)
                return@launch
            }
            val dir = File(target)
            val result = withContext(Dispatchers.IO) { scan(dir) }
            // 反馈 e8「为什么会出现路径不存在」：这四个字是**空态文案**，用户只看得到它，
            // 我们这边却一点线索都没有。把 cwd、split 结果与真实存在性一并记进日志，
            // 下次复现直接对着日志定位，不靠猜。
            if (result.errorRes == R.string.files_path_missing) {
                Logger.w(
                    "FileManageVM",
                    "path missing: cwd=$target split=$split exists=${dir.exists()} " +
                        "isDir=${dir.isDirectory} canRead=${dir.canRead()}"
                )
            }
            val readOnly = withContext(Dispatchers.IO) { dir.isDirectory && !dir.canWrite() }
            _state.update { st ->
                // 加载期间用户已经导航走 → 丢弃过期结果
                if (st[side].cwd != target) st
                else st.mutate(side) { p ->
                    p.copy(
                        items = result.items,
                        loading = false,
                        errorRes = result.errorRes,
                        readOnly = readOnly
                    )
                }
            }
        }
    }

    /**
     * 载入归档内目录（4.15）。
     *
     * 条目转成 [FileEntry] 时 [FileEntry.path] 一律是**虚拟路径**，所以往下
     * （`visible()` / 行渲染 / 选中集 / 菜单）全都不必知道自己在归档里。
     *
     * 归档内的 [PaneState.readOnly] 按**格式**分级（4.15）：zip 可写、7z/tar 只读。
     * （M4a 时一概只读，M4b 起 zip 开写。）
     */
    private suspend fun loadArchive(
        side: Side,
        target: String,
        archivePath: String,
        internalDir: String
    ) {
        val archiveFile = File(archivePath)
        val format = detectArchiveFormat(archiveFile)
        val result = withContext(Dispatchers.IO) {
            try {
                if (!archiveFile.isFile) {
                    // 反馈 e8：包打不开时报的也是这四个字，同样没线索 —— 记下归档位、
                    // 内部目录与磁盘实况（多半是包被删/改名，或归档位被拼成了虚拟路径）
                    Logger.w(
                        "FileManageVM",
                        "archive not a file: archive=$archivePath internal=$internalDir " +
                            "exists=${archiveFile.exists()} isFile=${archiveFile.isFile} " +
                            "isDir=${archiveFile.isDirectory}"
                    )
                    ScanResult(emptyList(), R.string.files_path_missing)
                } else {
                    // 7z 要建索引、tar 系压根没有索引，大包得整包扫一遍（4.15「索引中…」）。
                    // 把已扫条目数报给 UI，否则用户对着空列表只会以为卡死。
                    // 节流在扫描函数里（每 1024 条一次），这里不必再掐。
                    val children = listArchiveChildren(archiveFile, internalDir) { count ->
                        _state.update { st ->
                            // 扫描期间用户已经导航走 → 丢弃过期进度，别把别的目录闪成「索引中」
                            if (st[side].cwd != target) st
                            else st.mutate(side) { p -> p.copy(indexingCount = count) }
                        }
                    }
                    // f11 复现线索：包读不出时，日志里要能看到「用的哪个读器、认成什么格式、
                    // 内部目录、条目数」。这三个数一摆，UNKNOWN / 读到 0 条 / 格式认错
                    // 三种情形当场分开，不用再猜。
                    Logger.i(
                        "FileManageVM",
                        "archive ok: archive=$archivePath fmt=$format dir='$internalDir' entries=${children.size}"
                    )
                    ScanResult(
                        items = children.map { e ->
                            FileEntry(
                                name = e.name,
                                path = VirtualPath.join(archivePath, e.path),
                                isDir = e.isDir,
                                // 归档里的目录行带**聚合**大小（e15 P2-18，由 directChildren 算好）；
                                // 真实目录那条路是另一处构造、恒 0，不扫盘。
                                size = e.size,
                                modified = e.modified
                            )
                        },
                        errorRes = null
                    )
                }
            } catch (e: Exception) {
                // 7z 的**头被加密**（e15 P1-12）：连文件名都读不出来，必须先要密码。
                // 这条必须排在「已损坏」前面 —— 报「压缩包已损坏」，用户只会以为包坏了，
                // 而它其实好好的，只是没给密码；他下一步多半是去重下这个包。
                if (isPasswordRequired(e)) {
                    _state.update { st ->
                        // 扫描期间用户已经导航走 → 别把密码框弹到别人头上
                        if (st[side].cwd != target) st
                        else st.copy(
                            archivePassword = ArchivePasswordRequest(
                                sources = listOf(archivePath),
                                targetDir = internalDir,
                                browseSide = side,
                                browseArchive = archivePath
                            )
                        )
                    }
                    ScanResult(emptyList(), null)
                } else {
                    // 归档损坏 / 格式认错 —— 都落到这一句，不崩、给原因。
                    // f11：异常本身也记下来（原先完全吞掉，日志里只剩一句「已损坏」，
                    // 到底是 UNKNOWN、读器不认、还是真损坏，事后无从分辨）。
                    Logger.w(
                        "FileManageVM",
                        "archive scan failed: archive=$archivePath internal='$internalDir' " +
                            "fmt=$format ${e.javaClass.simpleName}: ${e.message}"
                    )
                    ScanResult(emptyList(), R.string.files_archive_broken)
                }
            }
        }
        _state.update { st ->
            if (st[side].cwd != target) st
            else st.mutate(side) { p ->
                p.copy(
                    items = result.items,
                    loading = false,
                    errorRes = result.errorRes,
                    indexingCount = null,
                    // 写能力按格式分级：zip 可写（M4b），7z/tar 保持只读。
                    // 给错写能力比不给更危险 —— 用户以为改了，其实没落盘（§4.15）
                    readOnly = !format.writable,
                    archive = ArchiveState(archivePath, format, internalDir)
                )
            }
        }
    }

    // ==================== 导航（4.2：每窗独立 back/forward） ====================

    /** 进入目录。[push] = false 时只换位置、不记历史（暂未用到，供 4.9 历史跳转用）。 */
    /**
     * 跳到 [path]。
     *
     * @param recordHistory false = **不记历史**。底部抽屉（§4.9）里点书签/历史时必须传 false：
     *   点历史是为了「回到那儿继续看」，若点一次就把那条顶到最前，用户想回的那条反被挤走 ——
     *   那等于历史没法用。
     */
    fun navigateTo(side: Side, path: String, push: Boolean = true, recordHistory: Boolean = true) {
        // 虚拟路径不是 File，`File(x.zip!/src).absolutePath` 只是碰巧没坏 —— 显式分开走（4.15）
        val target = if (VirtualPath.isInArchive(path)) path else File(path).absolutePath
        _state.update { st ->
            val cur = st[side]
            if (target == cur.cwd) return@update st
            st.mutate(side) { p ->
                p.copy(
                    cwd = target,
                    // 进/出归档都要把 archive 跟着换：出归档时 split 为 null → 自动清掉
                    archive = VirtualPath.split(target)?.let { (arc, internal) ->
                        ArchiveState(arc, detectArchiveFormat(File(arc)), internal)
                    },
                    back = if (push) p.back + p.cwd else p.back,
                    forward = if (push) emptyList() else p.forward,
                    items = emptyList(),
                    // 用户反馈⑩「仅用于此文件夹」：进这个目录先看有没有**专属排序**，
                    // 有就用它、没有才沿用上一格的（也就是全局）设定。
                    sort = FilePrefs.folderSort(target) ?: p.sort,
                    // 换目录就清过滤词：否则「怎么什么都没有」全是过滤词造成的错觉
                    filter = "",
                    selection = emptySet(),
                    // 换目录 = 全新的列表 = 全新的选择状态：锚点也一并作废。
                    // 留着的话，上个目录那一笔会在新目录里被当成「第二笔」，
                    // 一横滑就把两处不相干的条目连成一段。
                    rangeAnchor = null,
                    // 用户反馈 c11：回父目录/上一格要**接上上次的位置** —— 进去前先把
                    // 当前目录的下标记进 scrolls，再按目标目录查回来（没记过就是 0）
                    scrollIndex = PaneState.scrollOf(p.scrolls, target),
                    scrolls = PaneState.memoScroll(p.scrolls, p.cwd, liveScroll[side.ordinal]),
                    errorRes = null,
                    // 落点高亮是**一次性**的（4.5）：换目录就作废，免得留在列表里一直亮着
                    highlightPath = null
                )
            }
        }
        // 真换了目录才把实时滚动位对齐到**目标目录**已存的位置（e12）。同目录早退时
        // `scrollIndex` 还是老值，照抄会把这一格刚滚到的位置抹掉。
        // 必须放在上面那句 **之后** —— `memoScroll` 还要读到「上个目录」的位置。
        if (_state.value[side].cwd != target) {
            liveScroll[side.ordinal] = _state.value[side].scrollIndex
        }
        // 历史也记**包内位置**（e15 **P1-8**）。原先显式排除，理由是「虚拟路径既不能
        // 从别处直接跳进来、记了也是噪声」—— 可 `navigateTo` 本身就认虚拟路径
        // （split → loadArchive），历史抽屉点一下完全回得去；排除掉等于**后退链条**
        // 和**历史**两套口径：在包里翻过的地方，前进后退记得、历史里一律查不到。
        // `recordHistory=false` 是底部抽屉点选时传的，理由见 navigateTo 的 KDoc。
        if (recordHistory) {
            val sp = VirtualPath.split(target)
            // 主标题不能用 `File(target).name`：它对 `x.zip!/src` 只会给 "src"，
            // 看着跟真实目录没两样 —— 历史行是「名字 + 完整路径」两行，名字得先说清
            // 这是**哪个包里**的位置。
            val name = if (sp != null) {
                File(sp.first).name + if (sp.second.isEmpty()) "" else "/${sp.second}"
            } else {
                File(target).name.ifBlank { target }
            }
            if (FilePlaces.addHistory(target, name)) _historyTick.value += 1
        }
        load(side)
    }

    fun goBack(side: Side) {
        val cur = _state.value[side]
        if (!cur.canGoBack) return
        _state.update { st ->
            st.mutate(side) { p ->
                val target = p.back.last()
                p.copy(
                    cwd = target,
                    back = p.back.dropLast(1),
                    forward = listOf(p.cwd) + p.forward,
                    // e15 **P1-8**：后退会跨回**包内**位置，`archive` 必须跟着换 ——
                    // 与 navigateTo 同一条算式。不换的话，loadArchive 落地前的那一小段里
                    // `goUp` / 归档内菜单仍照着**上一个**位置算（包根 → 真实父目录 →
                    // 直接把你扔出包外），loadArchive 失败时更是永久停在错的那格。
                    archive = VirtualPath.split(target)?.let { (arc, internal) ->
                        ArchiveState(arc, detectArchiveFormat(File(arc)), internal)
                    },
                    items = emptyList(),
                    filter = "",
                    selection = emptySet(),
                    // 换目录 = 全新的列表 = 全新的选择状态：锚点也一并作废。
                    // 留着的话，上个目录那一笔会在新目录里被当成「第二笔」，
                    // 一横滑就把两处不相干的条目连成一段。
                    rangeAnchor = null,
                    // c11：退回去的那一格接上它自己的位置（同 navigateTo）
                    scrollIndex = PaneState.scrollOf(p.scrolls, target),
                    scrolls = PaneState.memoScroll(p.scrolls, p.cwd, liveScroll[side.ordinal]),
                    errorRes = null,
                    highlightPath = null
                )
            }
        }
        // 同 navigateTo（e12）：这一格走了，实时滚动位归到目标目录已存的位置上
        liveScroll[side.ordinal] = _state.value[side].scrollIndex
        load(side)
    }

    fun goForward(side: Side) {
        val cur = _state.value[side]
        if (!cur.canGoForward) return
        _state.update { st ->
            st.mutate(side) { p ->
                val target = p.forward.first()
                p.copy(
                    cwd = target,
                    back = p.back + p.cwd,
                    forward = p.forward.drop(1),
                    // 同 goBack（e15 P1-8）：前进同样可能跨回包内
                    archive = VirtualPath.split(target)?.let { (arc, internal) ->
                        ArchiveState(arc, detectArchiveFormat(File(arc)), internal)
                    },
                    items = emptyList(),
                    filter = "",
                    selection = emptySet(),
                    // 换目录 = 全新的列表 = 全新的选择状态：锚点也一并作废。
                    // 留着的话，上个目录那一笔会在新目录里被当成「第二笔」，
                    // 一横滑就把两处不相干的条目连成一段。
                    rangeAnchor = null,
                    // c11：前进也走同一条恢复路径 —— 同一个目录两处入口，一个记得一个不记得更怪
                    scrollIndex = PaneState.scrollOf(p.scrolls, target),
                    scrolls = PaneState.memoScroll(p.scrolls, p.cwd, liveScroll[side.ordinal]),
                    errorRes = null,
                    highlightPath = null
                )
            }
        }
        // 同 navigateTo（e12）：这一格走了，实时滚动位归到目标目录已存的位置上
        liveScroll[side.ordinal] = _state.value[side].scrollIndex
        load(side)
    }

    /** 上一级；**已到根则不动**（4.17：不越出根）。 */
    fun goUp(side: Side) {
        val cur = _state.value[side]
        if (!cur.canGoUp) return
        // 归档内（4.15）：上一层 = 归档内的父目录；已经在归档根 → 退出归档，回到它所在的那个真实目录
        val arch = cur.archive
        if (arch != null) {
            val parentInternal = VirtualPath.parentInternal(arch.internalDir)
            if (parentInternal != null) {
                navigateTo(side, VirtualPath.join(arch.archivePath, parentInternal), push = true)
            } else {
                // 包中包：这份包是从父包某处解到缓存的，「上一级」要回父包里的那个目录，
                // 不能真的往上走到 cache 目录 —— 那里对用户是**不存在的地方**。
                val origin = mountedArchiveOrigins[arch.archivePath]
                navigateTo(
                    side,
                    origin ?: File(arch.archivePath).parent ?: cur.root,
                    push = true
                )
            }
            return
        }
        val parent = File(cur.cwd).parent ?: cur.root
        navigateTo(side, parent, push = true)
    }

    /**
     * 记录「这个临时包是从哪个目录解出来的」（包中包）。
     *
     * 包里的包不能原地浏览（归档位必须是真实文件），所以先解一份到私有缓存再进。
     * 这份副本在缓存里，`File(parent)` 指向 `/data/.../cache/...` —— 用户在那里按「上一级」
     * 会掉进一个从没见过的目录。记下来源，[goUp] 就能把他送回父包里的原位置。
     */
    fun mountArchive(archivePath: String, origin: String) {
        if (origin.isEmpty() || VirtualPath.isInArchive(archivePath)) return
        mountedArchiveOrigins[archivePath] = origin
        while (mountedArchiveOrigins.size > 64) {
            val first = mountedArchiveOrigins.keys.firstOrNull() ?: break
            mountedArchiveOrigins.remove(first)
        }
    }

    /** 同步：把**活动窗格**路径复制到另一窗格（不是互换，4.1）。同目录时 UI 已置灰。 */
    fun sync() {
        val st = _state.value
        if (st.activePane.cwd == st.otherPane.cwd) return
        navigateTo(st.otherSide, st.activePane.cwd, push = true)
    }

    /** 交换两窗（⋮ 里的另一项）：整份 PaneState 对调，连列表/排序/选择集一起换，无需重新扫盘。 */
    /**
     * 互换两窗（g16：位置也要跟着走）。
     *
     * 光把两个 [PaneState] 对调不够 —— `LazyListState` 是按 `cwd` 重建的，而实时滚动位
     * 只存在 [liveScroll]（写状态会让整页重组，见 e12），状态里的 `scrollIndex` 往往是
     * 上一次导航留下的旧值。结果是互换后两栏都跳回顶部。
     *
     * 这里先把「当前目录 → 实时下标」分别记进各自的 `scrolls`，交换后再按新 cwd 取回来；
     * `liveScroll` 也跟着对调，后续 flush / 再次互换才能接得上。
     */
    fun swapPanes() {
        val leftLive = liveScroll[Side.LEFT.ordinal].coerceAtLeast(0)
        val rightLive = liveScroll[Side.RIGHT.ordinal].coerceAtLeast(0)
        _state.update { st ->
            val leftScrolls = PaneState.memoScroll(st.left.scrolls, st.left.cwd, leftLive)
            val rightScrolls = PaneState.memoScroll(st.right.scrolls, st.right.cwd, rightLive)
            val newLeft = st.right.copy(
                scrolls = rightScrolls,
                scrollIndex = PaneState.scrollOf(rightScrolls, st.right.cwd)
            )
            val newRight = st.left.copy(
                scrolls = leftScrolls,
                scrollIndex = PaneState.scrollOf(leftScrolls, st.left.cwd)
            )
            st.copy(left = newLeft, right = newRight)
        }
        liveScroll[Side.LEFT.ordinal] = rightLive
        liveScroll[Side.RIGHT.ordinal] = leftLive
    }

    fun setActive(side: Side) {
        if (_state.value.active != side) _state.update { it.copy(active = side) }
    }

    fun refresh(side: Side) {
        load(side)
        // 4.13：两窗同目录时刷新联动
        val st = _state.value
        if (st[st.otherSide].cwd == st[side].cwd) load(st.otherSide)
    }

    /**
     * l.txt 第 3 条：文件/文件夹列表**持续自动刷新** —— 别的应用改了文件（改名、增删、
     * 覆盖写入）之后，列表里的大小 / 时间 / 条目不该继续显示旧值。
     *
     * 两档检测，兼顾实时与开销：
     * - **每轮**读一次目录指纹（`lastModified` + 条目数）—— 增删/改名会改变目录 mtime，
     *   这一档就能发现；
     * - **每 15s** 才做一次逐条 stat 指纹（名字 + 大小 + mtime）—— 覆盖「文件内容被改写、
     *   但目录本身没动」这种只看目录看不出来的修改。上万条的目录也不会每 2.5s stat 一遍。
     *
     * 由 [com.UIN.Tool.ui.screen.files.FilesScreen] 在 RESUMED 期间轮询调用；
     * 归档内的虚拟目录不参与（归档没变就无需重扫，变了由用户刷新/重进触发）。
     */
    fun autoRefresh() {
        val state = _state.value
        val now = SystemClock.elapsedRealtime()
        val wantDeep = now - lastAutoDeepAt >= AUTO_DEEP_INTERVAL_MS
        if (wantDeep) lastAutoDeepAt = now
        for (side in Side.entries) {
            val pane = state[side]
            if (pane.cwd.isEmpty() || pane.loading) continue
            if (VirtualPath.split(pane.cwd) != null) continue
            val idx = side.ordinal
            if (autoBusy[idx]) continue
            autoBusy[idx] = true
            viewModelScope.launch {
                val snapshot: Pair<String, String?>? = withContext(Dispatchers.IO) {
                    val dir = File(pane.cwd)
                    if (!dir.isDirectory) return@withContext null
                    val cheap = runCatching {
                        "${dir.lastModified()}:${dir.list()?.size ?: -1}"
                    }.getOrNull() ?: return@withContext null
                    // 只在「目录指纹没变」且轮到深检时才逐条 stat —— 目录刚变过
                    // 反正要重扫，深检是白做。
                    val deep = if (wantDeep && cheap == autoCheap[idx]) deepStamp(dir) else null
                    cheap to deep
                }
                autoBusy[idx] = false
                if (snapshot == null) return@launch
                val (cheap, deep) = snapshot
                val prevCheap = autoCheap[idx]
                val prevDeep = autoDeep[idx]
                autoCheap[idx] = cheap
                if (deep != null) autoDeep[idx] = deep
                // 检测期间用户已经导航走 → 丢弃这次结果
                if (_state.value[side].cwd != pane.cwd) return@launch
                val changed = prevCheap != null && (
                    prevCheap != cheap ||
                        (deep != null && prevDeep != null && prevDeep != deep)
                    )
                if (changed) load(side)
            }
        }
    }

    /** 逐条指纹：名字 + 大小 + 修改时间（秒级，与 [scan] 同口径）。 */
    private fun deepStamp(dir: File): String? = try {
        val sb = StringBuilder()
        dir.listFiles()?.forEach { f ->
            val st = runCatching { android.system.Os.stat(f.absolutePath) }.getOrNull()
            sb.append(f.name).append('|')
                .append(st?.st_size ?: -1L).append('|')
                .append(st?.st_mtime ?: 0L).append('\n')
        }
        sb.toString()
    } catch (_: Exception) {
        null
    }

    /**
     * 记录首个可见项下标：切 tab 回来时列表能接上原来的位置（验收 15）。
     *
     * **只写 [liveScroll]，不写 [_state]**（e12 滑动卡顿的根因）。这个回调挂在
     * `firstVisibleItemIndex` 上，滚动时每滚过一行高就来一次；原先每次都要
     * `_state.update` → 整页状态换新 → 可见行**全部**重组一次。修法见 [liveScroll]。
     */
    fun setScroll(side: Side, index: Int) {
        if (index < 0) return
        liveScroll[side.ordinal] = index
    }

    /**
     * 离场（切 tab / 退出文件页）时把两窗的实时滚动位**立刻**落进状态（e12）。
     *
     * 平时 [setScroll] 只写没人订阅的 [liveScroll]，滚动期间整页一次都不重组；
     * 这一笔是给「切 tab 回来还得接上原位置」补的 —— 那时 `LazyListState` 是重建的，
     * 初值取的正是状态里这份 `scrollIndex`。
     */
    fun flushScrolls() {
        for (side in Side.entries) {
            val idx = liveScroll[side.ordinal]
            if (idx < 0 || _state.value[side].scrollIndex == idx) continue
            _state.update { it.mutate(side) { p -> p.copy(scrollIndex = idx) } }
        }
    }

    // ==================== 排序 / 过滤 / 隐藏（4.8） ====================

    /** 同键切换升降序；换键则给该键的自然顺序（日期默认新在前）。 */
    /**
     * @param onlyThisFolder 用户反馈⑩「仅用于此文件夹」：除全局默认外，再往**当前目录**写一条
     *   专属排序，之后进这个目录就按它来（[navigateTo] 会读）。不勾则只改全局默认。
     */
    fun setSort(side: Side, key: SortKey, onlyThisFolder: Boolean = false) {
        _state.update { st ->
            val cur = st[side]
            val spec = if (cur.sort.key == key) {
                cur.sort.copy(ascending = !cur.sort.ascending)
            } else {
                SortSpec(
                    key = key,
                    ascending = key != SortKey.DATE,
                    dirsFirst = cur.sort.dirsFirst
                )
            }
            st.mutate(side) { it.copy(sort = spec) }
        }
        // ⋮ 的排序与设置页的「默认排序」是同一个开关（N12：改动一致）
        val spec = _state.value[side].sort
        FilePrefs.setSortKey(prefKeyOf(spec.key))
        FilePrefs.setSortAscending(spec.ascending)
        if (onlyThisFolder) FilePrefs.setFolderSort(_state.value[side].cwd, spec)
    }

    /** 直接指定升降序（菜单里「升序 / 降序」两个勾选项，已是该状态再点不产生抖动）。 */
    fun setSortAscending(side: Side, ascending: Boolean, onlyThisFolder: Boolean = false) {
        _state.update { st ->
            st.mutate(side) { it.copy(sort = it.sort.copy(ascending = ascending)) }
        }
        FilePrefs.setSortAscending(ascending)
        if (onlyThisFolder) FilePrefs.setFolderSort(_state.value[side].cwd, _state.value[side].sort)
    }

    fun toggleDirsFirst(side: Side, onlyThisFolder: Boolean = false) {
        _state.update { st ->
            st.mutate(side) { it.copy(sort = it.sort.copy(dirsFirst = !it.sort.dirsFirst)) }
        }
        FilePrefs.setDirsFirst(_state.value[side].sort.dirsFirst)
        if (onlyThisFolder) FilePrefs.setFolderSort(_state.value[side].cwd, _state.value[side].sort)
    }

    /**
     * 用户反馈⑩：勾上/取消「仅用于此文件夹」。
     *
     * 勾上 = 把**当前**排序写成该目录的专属条目（内容不变，只是从此不再跟随全局）；
     * 取消 = 删掉这条专属记录，回落到全局默认。
     */
    fun setSortScopeThisFolder(side: Side, enabled: Boolean) {
        val st = _state.value
        if (enabled) FilePrefs.setFolderSort(st[side].cwd, st[side].sort)
        else FilePrefs.setFolderSort(st[side].cwd, null)
    }

    /** [SortKey] → [FilePrefs] 里的持久化键（与 [defaultSortSpec] 反向对应）。 */
    private fun prefKeyOf(key: SortKey): String = when (key) {
        SortKey.SIZE -> FilePrefs.SORT_SIZE
        SortKey.DATE -> FilePrefs.SORT_DATE
        SortKey.TYPE -> FilePrefs.SORT_TYPE
        else -> FilePrefs.SORT_NAME
    }

    /**
     * 隐藏开关写回 [FilePrefs]：⋮ 与设置页说的是**同一个开关**，
     * 天然一致（避免「设置里开了、页面里还是旧的」），两窗一起变也符合 4.13 的一致性要求。
     */
    fun setShowSystemHidden(v: Boolean) {
        FilePrefs.setShowSystemHidden(v)
        _state.update { st ->
            st.copy(
                left = st.left.copy(showSystemHidden = v),
                right = st.right.copy(showSystemHidden = v)
            )
        }
    }

    /**
     * 「隐藏 / 取消隐藏」选中项（MT.md §4.8 手动隐藏集）。
     *
     * 写完偏好**立刻把两窗格状态里的 manualHidden 集换掉** —— `PaneState.visible()` 的
     * 隐藏判定读的是状态里这一份（纯函数、不读盘），不跟着更新的话，藏完那一行还在原地，
     * 用户只会以为按钮没反应（用户反馈 b28「类比排查」要找的正是这类「点了没动静」）。
     *
     * **不顺手改显示开关**：藏起来正是本意，若这时把「手动隐藏」打开，文件刚消失就冒出来，
     * 等于什么都没做；要找回来得由用户自己去设置里打开那个开关。
     */
    fun setManualHidden(paths: List<String>, hidden: Boolean) {
        if (paths.isEmpty()) return
        FilePrefs.setManualHiddenAll(paths, hidden)
        val all = FilePrefs.manualHidden()
        _state.update { st ->
            st.copy(
                left = st.left.copy(manualHidden = all),
                right = st.right.copy(manualHidden = all)
            )
        }
    }

    fun setFilter(side: Side, text: String) {
        // 过滤词一变列表按新键重建（FileListPane），下标同步归零，避免重建后停在旧位置
        liveScroll[side.ordinal] = 0
        _state.update { it.mutate(side) { p -> p.copy(filter = text, scrollIndex = 0) } }
    }

    fun toggleFilterBar() {
        _state.update { st ->
            val show = !st.filterBarVisible
            // 收起时一并清词，避免「收了过滤条却还在过滤」的假空列表
            if (!show) { liveScroll[0] = 0; liveScroll[1] = 0 }
            st.copy(
                filterBarVisible = show,
                left = if (show) st.left else st.left.copy(filter = "", scrollIndex = 0),
                right = if (show) st.right else st.right.copy(filter = "", scrollIndex = 0)
            )
        }
    }

    // ==================== 多选（4.3） ====================

    /**
     * 长按条目 → 该栏变活动 + 进多选并选中该项。
     * 4.3 的上下文菜单（复制/剪切/删除…）随 M3 落地，届时这里改为先弹菜单、由菜单里的
     * 「选择」触发；M2 先把「长按 = 开始选择」这条最常用的路径打通。
     */
    /**
     * 长按菜单里的「选择」→ 该栏变活动 + 进多选并选中该项，同时把它定为**区间选择的锚点**
     * （4.3：进多选后从 A 拖到 B 选中 A..B）。
     */
    fun startSelect(side: Side, path: String) {
        _state.update { st ->
            st.mutate(side) { p -> p.copy(selection = setOf(path), rangeAnchor = path) }
                .copy(active = side, multiSelect = true)
        }
    }

    fun toggleSelect(side: Side, path: String) {
        _state.update { st ->
            var out = st.mutate(side) { p ->
                val next = if (path in p.selection) p.selection - path else p.selection + path
                p.copy(
                    selection = next,
                    // f7「手势多选还是错位」：锚点原先只在「下一笔横滑 / 退出多选 / 换目录」
                    // 时才清。把锚点自己那一条点掉、选中集清空退出多选之后，锚点还留着 ——
                    // 下一笔横滑于是走「有锚点」分支，把**旧锚点到落点整段**并进来，
                    // 屏幕上亮起一片跟这一笔毫无关系的行。锚点不在选中集里就没有存在的意义。
                    rangeAnchor = p.rangeAnchor?.takeIf { it in next }
                )
            }
            // 减到 0 自动退出多选（4.3）。
            // D7：判据必须是**活动栏**，不能是「两栏都空」—— 另一栏可以保留上一轮的勾
            // （按设计不显示）。要求两栏都空的话，活动栏清空后底栏会停在「已选 0 项」，
            // 全选/反选还会作用在空栏上，看起来像退不出多选。
            if (out[out.active].selection.isEmpty()) {
                out = out.copy(multiSelect = false)
            }
            out
        }
    }

    fun selectAll(side: Side) {
        _state.update { st ->
            val out = st.mutate(side) { p ->
                val items = p.visible().items
                val next = items.mapTo(LinkedHashSet()) { it.path }
                p.copy(
                    // f7：不主动改锚点（用户反馈④），但锚点被这次选择挤出去后必须清掉 ——
                    // 否则下一笔横滑仍会从「已经不在选中集里的旧锚点」整段并入。
                    selection = next,
                    rangeAnchor = p.rangeAnchor?.takeIf { it in next }
                )
            }
            // D7 同一口径：空目录里点「全选」不该把底栏留在「已选 0 项」。
            out.copy(multiSelect = out[side].selection.isNotEmpty())
        }
    }

    /** 反选：全集 = 当前**可见**条目（被过滤掉的不参与，所见即所选）。不碰锚点。 */
    fun invertSelection(side: Side) {
        _state.update { st ->
            val out = st.mutate(side) { p ->
                val visible = p.visible().items.map { it.path }.toSet()
                val next = (visible - p.selection) + (p.selection - visible)
                p.copy(selection = next, rangeAnchor = p.rangeAnchor?.takeIf { it in next })
            }
            // D7：反选后可能一个都不剩（全选再反选）。此时必须退出多选，
            // 否则底栏停在「已选 0 项」，全选/反选继续作用在空集上。
            out.copy(multiSelect = out[side].selection.isNotEmpty())
        }
    }

    /**
     * 同类型（用户反馈④）：**先清空再选**，不是往里并。
     *
     * 参照物 = 可见列表里**位置最靠前**的那条选中项（选中集本身是无序的，
     * 直接 `firstOrNull` 会随插入顺序飘）。参照是文件夹 → 只选全部文件夹；
     * 参照是文件 → 只选**同扩展名**（忽略大小写）的文件，文件夹一概不掺。
     * 两者从不混在一起。选中集为空就什么都不做。
     */
    fun selectSameType(side: Side) {
        _state.update { st ->
            st.mutate(side) { p ->
                val items = p.visible().items
                val ref = items.firstOrNull { it.path in p.selection } ?: return@mutate p
                val targets = if (ref.isDir) {
                    items.filter { it.isDir }
                } else {
                    val ext = ref.name.substringAfterLast('.', "").lowercase()
                    items.filter {
                        !it.isDir && it.name.substringAfterLast('.', "").lowercase() == ext
                    }
                }
                val next = targets.mapTo(LinkedHashSet()) { it.path }
                p.copy(selection = next, rangeAnchor = p.rangeAnchor?.takeIf { it in next })
            }.copy(multiSelect = true)
        }
    }

    fun exitMultiSelect() {
        _state.update { st ->
            st.copy(
                multiSelect = false,
                left = st.left.copy(selection = emptySet(), rangeAnchor = null),
                right = st.right.copy(selection = emptySet(), rangeAnchor = null)
            )
        }
    }

    /**
     * 退出多选但**只清活动栏**（用户答复：另一栏的勾选保留，「焦点在哪就退哪」）。
     *
     * 用在两处「退出」入口 —— 硬件返回键与多选条上的关闭按钮；操作完成
     * （复制/移动/删除那一串）仍然走 [exitMultiSelect] 一次清干净两边：那时候保留没有
     * 任何意义，只会留下半截选中态。
     *
     * 保留下来那栏的勾**不显示**（列表行的高亮按 `multiSelect` 门控），也不会被长按菜单
     * 当成整批 —— `showContextMenu` 同样按 `multiSelect` 门控，否则就是「菜单按 5 项算、
     * 屏幕上一项都没勾」。重新进多选时它们原样回来，这正是「保留」的意思。
     */
    fun exitMultiSelectKeepingOtherPane() {
        _state.update { st ->
            st.copy(multiSelect = false).mutate(st.active) { p ->
                p.copy(selection = emptySet(), rangeAnchor = null)
            }
        }
    }

    // ==================== 长按菜单与区间选择（4.3） ====================

    /**
     * 长按条目 → 该栏变活动 + 弹上下文菜单（4.3：**不**直接进多选）。
     *
     * 进多选改由菜单里的「选择」触发（[startSelect]）。多选态下长按已在选中集里的条目，
     * 菜单就变成作用于整批（[ContextMenuEntry.batch]）。
     */
    fun showContextMenu(side: Side, entry: FileEntry) {
        val cur = _state.value
        val pane = cur[side]
        // 按「多选态」门控：退出多选时只清了活动栏，另一栏可能还留着勾。不门控的话，
        // 那一栏长按会按**整批**算，而屏幕上一个勾都不显示 —— 作用范围变得看不见。
        // f7「删除计数依旧有问题」：原先还要求「长按的那条已经在选中集里」——
        // 于是勾了 3 条、长按第 4 条（未勾）时菜单只作用于 1 条，而屏上明明是 3 个勾。
        // 口径改成：只要处于多选态且选中集非空，长按任意一条都作用于**整个选中集**
        //（长按的那条不会被并进来，与常见文件管理器的行为一致）。
        val batch = cur.multiSelect && pane.selection.isNotEmpty()
        _state.update { st ->
            st.copy(
                active = side,
                contextMenu = ContextMenuEntry(
                    side = side,
                    path = entry.path,
                    name = entry.name,
                    isDir = entry.isDir,
                    batch = batch,
                    selectionSize = pane.selection.size
                )
            )
        }
    }

    fun dismissContextMenu() {
        _state.update { st -> if (st.contextMenu == null) st else st.copy(contextMenu = null) }
    }

    /** 当前活动窗格的全部选中路径（底栏「复制/移动/删除」用）。 */
    fun selectedPaths(): List<String> = _state.value.activePane.selection.toList()

    /**
     * 菜单动作的作用范围（4.3）：菜单标了「整批」就给整批选中项，否则只给长按的这一条。
     *
     * 这样「多选 3 项 → 长按其中一项 → 删除」自然作用于 3 项，不必让用户先搞清菜单作用域。
     */
    fun menuTargets(): List<String> {
        val cm = _state.value.contextMenu ?: return emptyList()
        val pane = _state.value[cm.side]
        if (!cm.batch) return listOf(cm.path)
        // f7：整批要与**屏上能看见的勾**一致。选中集按路径存，被过滤词 / 隐藏开关挡掉的
        // 条目仍留在集合里，原样拿去删就会出现「确认框 5 项、屏幕上只勾了 3 个」。
        val visible = pane.visible().items.mapTo(HashSet()) { it.path }
        val targets = pane.selection.filter { it in visible }
        // 一个都不剩（过滤词把选中项全挡了）就退回长按的那条，别给出空操作
        return targets.ifEmpty { listOf(cm.path) }
    }

    /**
     * 手势多选的一笔（g9：**从按下那行到抬手那行**，所见即所选）。
     *
     * 原先是「两笔交替」的锚点模型：第一笔只选一行、第二笔从上次的锚点整段并入。
     * 用户看到手指从第 5 行滑到第 12 行，却只亮一行（或把上一次的旧锚点也带进来），
     * 这就是「错位」的来源。现在起止点都由这一笔给出，不再跨笔记忆：
     * - [fromPath] = 按下时那一行；[toPath] = 抬手时那一行；
     * - 区间按**可见列表**算（过滤/排序所见即所得），并入已有选择。
     *
     * 拖动过程中的高亮由列表本地预览负责（见 FileListPane 的 swipePreview），
     * 抬手时才落进状态 —— 避免拖动期间每换一行就整页重组。
     */
    fun swipeSelect(side: Side, fromPath: String, toPath: String) {
        _state.update { st ->
            val pane = st[side]
            val visible = pane.visible().items.map { it.path }
            // j.txt 第 8 条：**跨笔锚点**。
            // - 第一笔（尚未多选 / 没有锚点）：从按下行选到抬手行；
            // - 第二笔起：从上一笔的终点选到本次抬手行（A 滑一下、再滑 B = A..B 整段）；
            // - 单击仍由 toggleSelect 处理，只翻转单条、不改锚点。
            // 「上一笔」用 PaneState.rangeAnchor 保存 —— 存路径不存下标，排序/过滤后仍有效。
            val startPath = if (st.multiSelect) pane.rangeAnchor ?: fromPath else fromPath
            val a = visible.indexOf(startPath)
            val b = visible.indexOf(toPath)
            val added: Set<String> = if (a < 0 || b < 0) {
                setOf(toPath)
            } else {
                visible.subList(minOf(a, b), maxOf(a, b) + 1).toSet()
            }
            st.mutate(side) { p ->
                p.copy(
                    selection = p.selection + added,
                    // 本次抬手行成为下一笔的起点；锚点随选择集清空/被移除时由 toggleSelect 等清掉。
                    rangeAnchor = toPath
                )
            }.copy(active = side, multiSelect = true)
        }
    }

    // ==================== 文件操作（4.4 / 4.5） ====================

    /**
     * 复制 / 移动选中项到 [targetDir]（4.4）。
     *
     * 默认目标由调用方给（另一窗格当前路径），对话框里可改。撞名时 [OpUiState.Conflict] 插进来，
     * 用户答复由 [resolveConflict] 回填。整个过程在 [OpUiState.Running] 里，可随时取消。
     */
    // ==================== 单窗口：拿起 / 粘贴（用户反馈㉒） ====================

    /**
     * 单窗口的复制/移动入口：**不直接执行**，先「拿起」。
     *
     * 双窗格时目标是另一栏（`startTransfer` 直接给 `other.cwd`）；单窗口没有那一栏，
     * 目标目录要靠用户自己走到，所以只把源记进 [FileManageUiState.pendingTransfer]，
     * 列表右下角随即挂出悬浮的粘贴/关闭按钮。
     */
    fun pickupTransfer(kind: OpKind, sources: List<String>) {
        if (sources.isEmpty()) return
        _state.update { it.copy(pendingTransfer = PendingTransfer(kind, sources)) }
    }

    /** 关掉悬浮按钮、丢弃这次拿起（不做任何文件操作）。 */
    fun cancelPendingTransfer() {
        _state.update { st ->
            if (st.pendingTransfer == null) st else st.copy(pendingTransfer = null)
        }
    }

    /**
     * 把拿起的那批粘到 [targetDir]（= 悬浮按钮所在的那个目录）。
     *
     * **先收状态再执行**：`startTransfer` 会立刻进 Running，悬浮按钮跟着消失；
     * 反过来写会先看到按钮、再看到它自己消失，中间那一帧像是点了没反应。
     */
    fun pastePending(targetDir: String) {
        val pending = _state.value.pendingTransfer ?: return
        _state.update { st ->
            if (st.pendingTransfer == null) st else st.copy(pendingTransfer = null)
        }
        startTransfer(pending.kind, pending.sources, targetDir)
    }

    fun startTransfer(kind: OpKind, sources: List<String>, targetDir: String) {
        if (sources.isEmpty()) return

        // 目标在归档内 → 「复制进归档」（§4.15 / §4.7）。这一支必须**排在 isDirectory 判定之前**：
        // `File("/sdcard/x.zip!/sub").isDirectory` 恒为 false，先判就会被下面那句误拒成
        // 「目标不是文件夹」—— 可它明明是个合法落点。M4b 时这里先欠着，现在补上，
        // 顺带也让 `files_target_invalid` 这句文案重新变准确（它现在只对真正的非目录目标说话）。
        val split = VirtualPath.split(targetDir)
        if (split != null) {
            startTransferIntoArchive(kind, sources, split.first, split.second)
            return
        }

        val target = File(targetDir)
        if (!target.isDirectory) {
            _notices.tryEmit(NoticeEvent.Text(R.string.files_target_invalid))
            return
        }
        val jobs = sources.map { File(it) }
        _state.update { st -> st.copy(op = OpUiState.Idle) }
        // 归档内条目「复制/移动到另一窗格」= **解出到该窗格**（§4.15 末条，`复制 →` 语义）。
        // 归档只读、源条目不会被删，所以一律按 COPY 进引擎 ——
        // 报「正在移动」而源原封不动是撒谎；用户按的是移动，看到的却是诚实的「正在复制」。
        val inArchive = jobs.any { VirtualPath.isInArchive(it.path) }
        if (inArchive) {
            // 加密包先问密码（b10 的补口）：引擎的 fail-fast 会在动手前拒绝，可这条路上
            // 原先没有给密码的地方 —— 报一句「请在解压时输入密码」，用户却无处可输。
            // 标志位在包尾，读一遍中央目录：切到 IO 线程，判完要么挂出密码框、要么直接跑。
            viewModelScope.launch {
                val archivePath = VirtualPath.archiveOf(jobs.first().path)
                val encrypted = archivePath != null && withContext(Dispatchers.IO) {
                    val f = File(archivePath)
                    val fmt = detectArchiveFormat(f)
                    // 7z 也走这条（e15 P1-12）：头被加密的连条目表都读不出来，
                    // 不问就只会掉进下面那句「已损坏」
                    (fmt == ArchiveFormat.ZIP || fmt == ArchiveFormat.SEVEN_Z) &&
                        ArchiveEncoding.hasEncryptedEntry(f)
                }
                if (encrypted) {
                    _state.update { st ->
                        st.copy(archivePassword = ArchivePasswordRequest(sources, targetDir))
                    }
                } else {
                    runInArchiveCopy(jobs, target, null)
                }
            }
            return
        }
        runOp(kind, jobs.size) { onProgress, onConflict ->
            FileOps.executeCopyOrMove(kind, jobs, target, onProgress, onConflict)
        }
    }

    /**
     * 归档内条目 → 另一窗格（= 解出到该窗格）。**问过密码之后重跑的也是这一条**，
     * 所以单独成函数：两处各写一遍，迟早跑出「问密码的那条路」和「不问的那条路」两种行为。
     *
     * 归档只读、源条目不会被删，所以一律按 COPY 进引擎 —— 报「正在移动」而源原封不动
     * 是撒谎；用户按的是移动，看到的却是诚实的「正在复制」。
     */
    private fun runInArchiveCopy(jobs: List<File>, target: File, password: String?) {
        runOp(OpKind.COPY, jobs.size) { onProgress, onConflict ->
            val res = ArchiveExtract.execute(
                kind = OpKind.COPY,
                sources = jobs,
                targetDir = target,
                // 归档内条目复制到另一窗格**也是解出**，「还原时间」照同一份偏好走
                restoreTimestamps = FilePrefs.restoreMeta(),
                password = password,
                onProgress = onProgress,
                onConflict = onConflict
            )
            // 用户反馈 e15 P1-5 的同一条（这条是「复制出归档」的密码框）：打错就把框
            // **原样重开**，否则用户只能看着一句报错、还得自己重新长按一次才能重输。
            // 7z 的正文加密是「解到才要密码」（e15 P1-12），那一句也算「要密码」，
            // 同样把框原样重开。
            val needPassword =
                res.firstError == com.UIN.Tool.utils.Str.get(R.string.files_archive_bad_password) ||
                    res.firstError == com.UIN.Tool.utils.Str.get(R.string.files_archive_password_required)
            if (needPassword) {
                _state.update { st ->
                    st.copy(archivePassword = ArchivePasswordRequest(jobs.map { it.path }, target.path))
                }
            }
            res
        }
    }

    /**
     * 密码框按了确定。空串按「没密码」交上去 —— 引擎会自己把「需要密码」报出来。
     *
     * 两条路共用这一个框（e15 **P1-12**）：
     * - **解压 / 复制出**被打断 → 拿到密码原样重跑那次拷贝；
     * - **浏览**被打断（7z 头被加密）→ 先**验一次**再缓存，然后重开那个包。
     *
     * 验不过就**把框原样重开**（与 e15 P0-4 / P1-5 同一条底线：打错了不能只甩一句报错，
     * 否则用户还得自己重新长按一次才能重输）。
     */
    fun submitArchivePassword(password: String) {
        val req = _state.value.archivePassword ?: return
        dismissArchivePassword()
        val pw = password.takeIf { it.isNotBlank() }

        // 无论走哪条，都按包把密码记下来（只在内存）：之后的列目录、属性 CRC、搜索、
        // 包内打开文件全靠它 —— 那几条路**没有地方**让用户再输一次。
        req.sources.forEach { rememberArchivePassword(File(it), pw) }

        val browse = req.browseArchive
        val side = req.browseSide
        if (browse != null && side != null) {
            val file = File(browse)
            // 头被加密的包密码一错，在**开的那一下**就挡回来 —— 先开一次验真，
            // 别把一条错密码存进缓存（存了之后列目录、解压会一起错，还查不出是哪儿错的）。
            // 包类型决定用哪个引擎验真：RAR 走 junrar，7z 走 commons-compress。
            // 原来一律 openSevenZ —— 给 RAR 输密码时必然验不过，框会原地重开，
            // 用户以为密码错了，其实根本没验。
            val badPassword = runCatching {
                if (detectArchiveFormat(file) == ArchiveFormat.RAR) {
                    openRar(file, password).close()
                } else {
                    openSevenZ(file, password).close()
                }
            }.isFailure
            if (badPassword) {
                rememberArchivePassword(file, null)
                _state.update { st -> st.copy(archivePassword = req) }
                com.UIN.Tool.utils.AppToast.show(
                    com.UIN.Tool.UinApplication.getAppContext(),
                    com.UIN.Tool.utils.Str.get(R.string.files_archive_bad_password)
                )
                return
            }
            // cwd 还停在那个包上（loadArchive 没动过它），直接重开就行 ——
            // 走 navigateTo 会被「target == cur.cwd」那句早退挡掉，等于点了没反应。
            load(side)
            return
        }

        runInArchiveCopy(
            req.sources.map { File(it) },
            File(req.targetDir),
            pw
        )
    }

    /** 密码框按了取消 / 返回键：这次拷贝整个不做，一个字节都不写。 */
    fun dismissArchivePassword() {
        _state.update { st -> if (st.archivePassword == null) st else st.copy(archivePassword = null) }
    }

    /**
     * 把**真实文件**加进 zip（§4.15「复制进归档」）。
     *
     * 只支持 zip：7z/tar 系本版整体只读（见 `ArchiveFormat.writable`），写能力给错比不给更危险。
     * 源本身在归档内（`a.zip!/x.txt` → `b.zip`）也走这里 —— [ZipWrite.addFiles] 收的是
     * 真实 [File]，所以先把源解到临时区再加。
     *
     * **移动进归档 = 复制进去 + 全部成功后删源**。分批成功时**不删**：用户按了移动却只搬走一半，
     * 那是数据丢失；宁可停在「复制成功、源还在」，提示里如实说明。
     */
    private fun startTransferIntoArchive(
        kind: OpKind,
        sources: List<String>,
        archivePath: String,
        internalDir: String
    ) {
        val archive = File(archivePath)
        if (!archive.isFile) {
            _notices.tryEmit(NoticeEvent.Text(R.string.files_archive_broken))
            return
        }
        if (!archive.canWrite()) {
            _notices.tryEmit(NoticeEvent.Text(R.string.files_archive_readonly))
            return
        }
        val jobs = sources.map { File(it) }.filter { it.name.isNotEmpty() }
        if (jobs.isEmpty()) return

        _state.update { st -> st.copy(op = OpUiState.Idle) }
        runOp(OpKind.COPY, jobs.size) { onProgress, onConflict ->
            // 源在包里（`a.zip!/x.txt` → `b.zip`）**真的要先解出来**：注释早就这么写，
            // 但代码没做 —— `File("a.zip!/x.txt")` 不是真实文件，`addFiles` 读不到，
            // 「包 A 复制到包 B」必失败（f11 顺带发现的真 bug）。
            // 暂存区放私有 cache：包内条目已经在用户自己的存储里，不算多复制一份到共享区。
            val stagingRoot = File(
                com.UIN.Tool.UinApplication.getAppContext().cacheDir,
                "archive_stage/${System.currentTimeMillis()}_${(0..999).random()}"
            )
            try {
                val staged = if (jobs.any { VirtualPath.isInArchive(it.path) }) {
                    stageVirtualSources(jobs, stagingRoot, onProgress)
                        ?: return@runOp OpResult(
                            cancelled = false,
                            successCount = 0,
                            failedCount = jobs.size,
                            firstError = com.UIN.Tool.utils.Str.get(R.string.files_archive_broken)
                        )
                } else {
                    jobs
                }

                val result = ZipWrite.addFiles(
                    archive = archive,
                    internalDir = internalDir,
                    sources = staged,
                    onProgress = onProgress,
                    onConflict = onConflict
                ) ?: OpResult(cancelled = true, successCount = 0, failedCount = 0, firstError = null)

                // 只有「没取消、没失败、且全部条目都进了包」才删源 —— 移动的语义是**搬过去**，
                // 半途删源等于丢了没搬成的那部分。用户把冲突全选了「跳过」时
                // `successCount == 0` 且 `failedCount == 0`，按上面的旧条件会把源全删掉。
                if (
                    kind == OpKind.MOVE && !result.cancelled && result.failedCount == 0 &&
                    result.successCount == staged.size
                ) {
                    removeMovedSources(jobs, archivePath)
                }
                result
            } finally {
                runCatching { stagingRoot.deleteRecursively() }
            }
        }
    }

    /**
     * 双窗格「添加」：把一栏选中的东西加进**另一栏当前所在的归档**（f11）。
     *
     * 只在「对面是可写归档」时由 UI 摆出来（见 `FileListPane` 的 ADD 门控）。
     * 落到包内**当前目录**，不落根 —— 用户正站在 `res/` 里看着，加到根上等于加丢。
     */
    fun addToOtherArchive(side: Side, sources: List<String>) {
        if (sources.isEmpty()) return
        val st = _state.value
        val otherSide = if (side == Side.LEFT) Side.RIGHT else Side.LEFT
        val other = st[otherSide]
        val arch = other.archive
        if (arch == null) {
            _notices.tryEmit(NoticeEvent.Text(R.string.files_archive_readonly))
            return
        }
        if (!arch.format.writable) {
            _notices.tryEmit(NoticeEvent.Text(R.string.files_archive_readonly))
            return
        }
        // 加密包整包重写后密码会丢（写侧不保留 ZipCrypto/AES 头）—— 这条门禁
        // 在 `ZipWrite` 里也有，这里先挡一次，省得用户走完进度才看到报错。
        // 读中央目录是磁盘 IO，挪到 IO 线程再判（这条入口从长按菜单主线程进来）。
        viewModelScope.launch {
            val encrypted = withContext(Dispatchers.IO) {
                ArchiveEncoding.hasEncryptedEntry(File(arch.archivePath))
            }
            if (encrypted) {
                _notices.tryEmit(NoticeEvent.Text(R.string.files_archive_encrypted_edit))
                return@launch
            }
            startTransferIntoArchive(OpKind.COPY, sources, arch.archivePath, arch.internalDir)
        }
    }

    /**
     * 把混在任务里的「包内条目」解到暂存区，返回可交给 [ZipWrite.addFiles] 的真实文件。
     *
     * - 真实文件原样透传；
     * - 同一批里**重名**的包内条目逐个解到独立子目录：挤在一个目录里会互相覆盖，
     *   而 `addFiles` 按文件名取条目名 —— 重名本来就该进撞名询问，不能在这里悄悄丢一个；
     * - 任何一个解不出来就整批放弃（返回 null），绝不把半批加进包里。
     */
    private suspend fun stageVirtualSources(
        jobs: List<File>,
        stagingRoot: File,
        onProgress: (OpProgress) -> Unit
    ): List<File>? {
        val out = ArrayList<File>(jobs.size)
        val byArchive = LinkedHashMap<String, MutableList<File>>()
        for (j in jobs) {
            val arch = VirtualPath.archiveOf(j.path)
            if (arch == null) out.add(j) else byArchive.getOrPut(arch) { mutableListOf() }.add(j)
        }
        var index = 0
        for ((archPath, list) in byArchive) {
            val pw = cachedArchivePassword(File(archPath))
            val names = list.map { it.name }
            val groups: List<List<File>> =
                if (names.size == names.toSet().size) listOf(list) else list.map { listOf(it) }
            for (group in groups) {
                val dir = File(stagingRoot, "s${index++}")
                if (!dir.mkdirs()) return null
                val res = ArchiveExtract.execute(
                    kind = OpKind.COPY,
                    sources = group,
                    targetDir = dir,
                    password = pw,
                    // 暂存目录是私有 cache，别把它显示成「复制到 /data/...」——
                    // 用户没选过那个落点，看到只会以为东西被搬到别处去了。
                    onProgress = { p -> onProgress(p.copy(targetPath = "")) },
                    onConflict = { null }
                )
                if (res.cancelled || res.failedCount > 0) return null
                for (src in group) {
                    val f = File(dir, src.name)
                    if (!f.exists()) return null
                    out.add(f)
                }
            }
        }
        return out
    }

    /**
     * 移动成功后删源。真实文件直接删；包内条目按包**成组**从包里去掉
     * （一个包一次重写，逐条删会把整包重写 N 遍）。
     *
     * **同一包内搬运不删源**：`addFiles` + `remove` 不是原子的，
     * 落点或撞名一有偏差，删源就是把唯一的副本删掉。同包内保持「复制」语义。
     */
    private suspend fun removeMovedSources(jobs: List<File>, targetArchive: String) {
        for (s in jobs) {
            if (VirtualPath.isInArchive(s.path)) continue
            currentCoroutineContext().ensureActive()
            runCatching { s.deleteRecursively() }
        }
        val virtual = jobs.filter { VirtualPath.isInArchive(it.path) }
        for ((archPath, list) in virtual.groupBy { VirtualPath.archiveOf(it.path) ?: "" }) {
            if (archPath.isEmpty() || archPath == targetArchive) continue
            currentCoroutineContext().ensureActive()
            val internals = list.mapNotNull { VirtualPath.internalOf(it.path) }
            if (internals.isEmpty()) continue
            runCatching { ZipWrite.remove(File(archPath), internals) }
        }
    }

    /** 删除选中项（4.5）。调用方先弹确认框，按「删除」才到这里。 */
    fun startDelete(paths: List<String>, permanent: Boolean = false) {
        if (paths.isEmpty()) return
        val jobs = paths.map { File(it) }
        val req = _state.value.deleteRequest ?: return
        // 进来就收框 —— 与 performCompress 同一个约定。原先这里不收，
        // 删完 deleteRequest 还挂着，确认框一直盖在刷新后的列表上（用户反馈⑧）。
        // req 在收之前取好，下面还要用它的条目数/字节数做进度口径。
        dismissDelete()

        // 归档内删除 = 从包里去掉条目（zip4j 一次重写整包，所以进度只有首末两态，
        // 没有逐条百分比可报 —— 报个假的中间态比不报更糟）
        val archivePath = paths.firstNotNullOfOrNull { VirtualPath.archiveOf(it) }
        if (archivePath != null) {
            runOp(OpKind.DELETE, paths.size) { onProgress, _ ->
                val archive = File(archivePath)
                val internals = paths.mapNotNull { VirtualPath.internalOf(it) }
                onProgress(
                    OpProgress(
                        kind = OpKind.DELETE,
                        totalCount = paths.size,
                        sourcePath = archivePath
                    )
                )
                val ok = ZipWrite.remove(archive, internals)
                val okCount = if (ok) paths.size else 0
                onProgress(
                    OpProgress(
                        kind = OpKind.DELETE,
                        totalCount = paths.size,
                        doneCount = paths.size,
                        sourcePath = archivePath,
                        finished = true,
                        successCount = okCount,
                        failedCount = paths.size - okCount,
                        firstError = if (ok) null else "remove from archive failed"
                    )
                )
                OpResult(false, okCount, paths.size - okCount, if (ok) null else "remove from archive failed")
            }
            return
        }

        // 默认进回收站（用户要求：回收被删除的文件）。只有用户在确认框里显式勾了
        // 「彻底删除」才走原来的 executeDelete；归档内条目的删除在上面那条分支，
        // 走的是「重新打包」，与回收站无关。
        if (!permanent) {
            // 只发**一条**收尾 toast（原先还额外 emit 了一句 files_trash_moved，
            // 两条 AppToast 一前一后，单条/多条删除时偶尔只看得到其中一条 —— 
            // 这就是用户说的「风格不一致，有概率」。现在统一由 runOp 收尾，
            // 文案换成「已移入回收站 · 共 N 项」。
            runOp(
                OpKind.DELETE,
                paths.size,
                doneRes = R.string.files_trash_moved_count
            ) { onProgress, _ ->
                TrashStore.moveToTrash(jobs, onProgress)
            }
            return
        }

        runOp(OpKind.DELETE, paths.size) { onProgress, _ ->
            // g9：计数按顶层项走（见 executeDelete），与确认框同一口径
            FileOps.executeDelete(jobs, paths.size, req.totalBytes, onProgress)
        }
    }

    /**
     * 删除前的体积统计。
     *
     * 真实路径走 [FileOps.measure]；**归档内条目必须按条目表量** ——
     * 拿 `File("x.zip!/a").length()` 量永远是 0，确认框会显示成「0 字节 · 1 项」骗人。
     */
    private suspend fun measureAny(paths: List<String>): Pair<Long, Int> {
        val archivePath = paths.firstNotNullOfOrNull { VirtualPath.archiveOf(it) }
            ?: return FileOps.measure(paths.map { File(it) })
        return withContext(Dispatchers.IO) {
            val internals = paths.mapNotNull { VirtualPath.internalOf(it) }
            val entries = try {
                readArchiveEntries(File(archivePath))
            } catch (e: Exception) {
                emptyList()
            }
            var bytes = 0L
            var count = 0
            for (i in internals) {
                if (i.isEmpty()) continue
                val prefix = "$i/"
                for (e in entries) {
                    if (e.path != i && !e.path.startsWith(prefix)) continue
                    count++
                    if (!e.isDir) bytes += e.size
                }
            }
            bytes to count
        }
    }

    /**
     * 统一的操作执行壳：管 [OpUiState.Running] 的进出、取消、收尾 toast（4.4 第 4 条）。
     *
     * [block] 里第一参是进度回调、第二参是撞名询问（挂起直到用户答复）。
     */
    private fun runOp(
        kind: OpKind,
        totalItems: Int,
        /** 成功时的专用文案（带成功条数）；null = 通用「X 完成 · 共 N 项」。 */
        @StringRes doneRes: Int? = null,
        block: suspend (
            onProgress: (OpProgress) -> Unit,
            onConflict: suspend (List<ConflictItem>) -> ConflictDecision?
        ) -> OpResult
    ) {
        opJob?.cancel()
        opJob = viewModelScope.launch {
            // 两个 catch 已经覆盖了 block 能抛出的全部情形（取消 / 任意异常），
            // 所以**不**再挂 invokeOnCompletion 兜底 —— 那会是第二条收尾路径，
            // 一次取消就可能吐两个提示。
            val result = try {
                block(
                    { p -> publishProgress(p) },
                    { items -> awaitConflict(items) }
                )
            } catch (e: CancellationException) {
                OpResult(cancelled = true, successCount = 0, failedCount = 0, firstError = null)
            } catch (e: OutOfMemoryError) {
                // i.txt 第 12 条：压缩/复制大目录时 OOM 不能把进程带走 —— 收成一次失败提示。
                // 具体的半截文件由各引擎自己的清理路径负责（压缩那边已删残包/还原旧包）。
                OpResult(
                    cancelled = false,
                    successCount = 0,
                    failedCount = totalItems.coerceAtLeast(1),
                    firstError = com.UIN.Tool.utils.Str.get(R.string.files_op_oom)
                )
            } catch (e: Exception) {
                OpResult(
                    cancelled = false,
                    successCount = 0,
                    failedCount = 1,
                    firstError = e.message ?: e.javaClass.simpleName
                )
            }
            finishOp(kind, result, totalItems, doneRes)
        }
    }

    private fun publishProgress(p: OpProgress) {
        _state.update { st ->
            val prev = st.op as? OpUiState.Running
            st.copy(
                op = OpUiState.Running(
                    p,
                    hidden = prev?.hidden ?: false,
                    // 取消请求是**用户意图**，不能被下一帧进度覆盖回去
                    cancelling = prev?.cancelling ?: false
                )
            )
        }
    }

    /**
     * 撞名：把 [OpUiState.Conflict] 摆出来，然后**挂起**等用户答复（4.4）。
     *
     * 返回 null = 用户在对话框外面点了 = 整批取消。
     */
    private suspend fun awaitConflict(items: List<ConflictItem>): ConflictDecision? {
        val deferred = CompletableDeferred<ConflictDecision?>()
        _state.update { st -> st.copy(op = OpUiState.Conflict(items)) }
        conflictWaiter = deferred
        return deferred.await()
    }

    /** 用户对撞名对话框的答复；policy = null 表示整批取消。 */
    fun resolveConflict(policy: ConflictPolicy?, applyToAll: Boolean) {
        val d = conflictWaiter
        conflictWaiter = null
        val decision = if (policy == null) null else ConflictDecision(policy, applyToAll)
        _state.update { st ->
            if (st.op is OpUiState.Conflict) st.copy(op = OpUiState.Idle) else st
        }
        d?.complete(decision)
    }

    /** 取消进行中的操作（4.12：必须能中止，半成品由引擎清理）。 */
    fun cancelOp() {
        // 先落「取消中」再取消：底层可能还要写完手头那一整段 IO 才到检查点，
        // 这段时间必须让用户看见请求已经收到（f19）
        _state.update { st ->
            val r = st.op as? OpUiState.Running ?: return@update st
            if (r.cancelling) return@update st
            st.copy(op = r.copy(cancelling = true))
        }
        val waiter = conflictWaiter
        conflictWaiter = null
        waiter?.complete(null)
        opJob?.cancel()
    }

    /** 「隐藏」：对话框收起但操作继续；页面顶部留一个胶囊可唤回（4.12 首版不转常驻通知）。 */
    fun hideOp() {
        _state.update { st ->
            val cur = st.op
            if (cur is OpUiState.Running && !cur.hidden) st.copy(op = cur.copy(hidden = true)) else st
        }
    }

    fun showOp() {
        _state.update { st ->
            val cur = st.op
            if (cur is OpUiState.Running && cur.hidden) st.copy(op = cur.copy(hidden = false)) else st
        }
    }

    private fun finishOp(kind: OpKind, result: OpResult?, totalItems: Int, doneRes: Int? = null) {
        _state.update { st -> if (st.op is OpUiState.Idle) st else st.copy(op = OpUiState.Idle) }
        if (result == null) return
        _notices.tryEmit(NoticeEvent.OpDone(kind, result, totalItems, doneRes))
        if (!result.cancelled) {
            // 两窗同目录时联动刷新（4.13）；这里保守起见两窗都刷，成本只是两次 listFiles
            refresh(Side.LEFT)
            refresh(Side.RIGHT)
        }
    }

    // ==================== 删除确认框（4.5） ====================

    /**
     * 弹删除确认框，并**异步补体积统计**（4.5：计算中显示「统计中…」，完成后换成
     * 「8.26 MB · 3 项」）。关键系统目录再叠一层危险提示。
     */
    fun requestDelete(side: Side, paths: List<String>) {
        if (paths.isEmpty()) return
        val names = paths.map { File(it).name }
        val dangerous = paths.any { isDangerousPath(it) }
        _state.update { st ->
            st.copy(
                deleteRequest = DeleteRequest(paths, names, measuring = true, dangerous = dangerous)
            )
        }
        viewModelScope.launch {
            val (bytes, count) = measureAny(paths)
            _state.update { st ->
                val req = st.deleteRequest ?: return@update st
                if (req.paths != paths) return@update st
                st.copy(deleteRequest = req.copy(measuring = false, totalBytes = bytes, entryCount = count))
            }
        }
    }

    fun dismissDelete() {
        _state.update { st -> if (st.deleteRequest == null) st else st.copy(deleteRequest = null) }
    }

    /** 关键系统目录（4.5：删除前多一层危险提示）。根路径本身不算。 */
    private fun isDangerousPath(path: String): Boolean {
        val p = path.trimEnd('/')
        if (p.isEmpty()) return false
        return DANGEROUS_ROOTS.any { root -> p == root || p.startsWith("$root/") }
    }

    // ==================== 重命名 / 新建（4.5） ====================

    /**
     * 弹重命名框。**打开即选中主干名**（不含扩展名）—— 省掉「先删后缀再重打」。
     * 目标名已存在时把建议名一并给出来，并多提供一个「交换」键。
     */
    fun requestRename(side: Side, path: String) {
        val file = File(path)
        // g15：打开重命名框时**不给**建议名 —— 刚刚打开还没换名字，哪来的撞名？
        // 原先这里拿「自己跟自己」比 `exists()`（恒为 true），于是每次都算出一个
        // `原名 (1).ext` 的建议名；用户什么都没改、直接按「重命名」，框里那句
        // 「撞名时直接落到建议名」的逻辑就把文件悄悄改成了 `(1)`。
        // 建议名只在**真的撞名**（提交时目标已存在）之后由 performRename 回填。
        _state.update { st ->
            st.copy(
                renameRequest = RenameRequest(
                    side = side,
                    path = path,
                    initialName = file.name,
                    selectStem = true,
                    suggestedName = ""
                )
            )
        }
    }

    fun dismissRename() {
        _state.update { st -> if (st.renameRequest == null) st else st.copy(renameRequest = null) }
    }

    // ==================== 批量重命名 ====================

    /** 弹批量重命名框。空选中直接忽略 —— 没有可改的对象时不该弹出一个空框。 */
    fun requestBatchRename(side: Side, paths: List<String>) {
        if (paths.isEmpty()) return
        _state.update { it.copy(batchRename = BatchRenameRequest(side, paths)) }
    }

    fun dismissBatchRename() {
        _state.update { st -> if (st.batchRename == null) st else st.copy(batchRename = null) }
    }

    /**
     * 执行批量重命名。
     *
     * **[names] 就是最终结果**，由对话框算好传进来（模板 / 查找替换 / 正则 / 大小写都在
     * 那边展开）。这里不再自己跑一遍展开逻辑，预览显示的和真正落盘的是同一份数据 ——
     * 两条并行的展开规则早晚会对不上，而对不上时用户只会相信自己没看见的那一条。
     *
     * 与单条 [performRename] 的**刻意差异**：单条撞名会回到对话框给建议名（用户只改一个，
     * 有精力处理冲突）；批量撞名**不中断、只计数** —— 中途停下来问，等于让 200 个文件的
     * 任务变成 200 次问答。没能改的留在原地，收尾时一句汇总交代清楚。
     *
     * 同样地，这里**不自动改写名字去绕开冲突**：批量改名是用户明确表达过的意图，
     * 悄悄把 `报告_001` 改成 `报告_001 (1)` 会让结果和预览对不上。
     */
    fun performBatchRename(
        side: Side,
        paths: List<String>,
        names: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            var failed = 0
            // 真正改了名的条数（与 ok 分开算：模板为 `#p` 时全部原地成功，但路径一个没变）
            var changed = 0
            var lastRenamed = ""

            // ── 分拣（用户反馈 e15 **P2-15**「批量重命名的互换同名」）──
            // 目标名落在**本批某个源**身上（a↔b、a→b→c→a 这类循环）时，`dst.exists()`
            // 恒为真，逐条改必然一条条失败 —— 预览里明明写着 a→b、b→a，点确定却全军覆没。
            // 那不是冲突，是**换位**：得先把「被别人当目标的源」挪到临时名上腾位置，
            // 再统一落位，落不成的再搬回去（临时名三步法，成批做一遍）。
            // 批**外**的同名才是真冲突，仍按原样「只计数不中断」——绝不静默覆盖。
            val pathSet = paths.map { File(it).path }.toHashSet()
            class Todo(val index: Int, val src: File, val dst: File)
            val direct = ArrayList<Todo>()
            val cyclic = ArrayList<Todo>()

            paths.forEachIndexed { i, p ->
                val src = File(p)
                val newName = names.getOrNull(i)
                if (newName == null) {
                    failed++
                    return@forEachIndexed
                }
                val parent = src.parentFile
                if (newName.isBlank() || newName.contains('/') || parent == null) {
                    failed++
                    return@forEachIndexed
                }
                val dst = File(parent, newName)
                // 原地改名（模板没变）算成功，不是失败
                if (dst.path == src.path) {
                    ok++
                    return@forEachIndexed
                }
                if (dst.exists() && dst.path !in pathSet) {
                    failed++
                    return@forEachIndexed
                }
                if (dst.path in pathSet) cyclic += Todo(i, src, dst)
                else direct += Todo(i, src, dst)
            }

            // ── 第一段：腾位置 ──
            // 只有「被别人当目标的源」才需要让位。临时名点开头（与用户的名字不同域）、
            // 带 index 与纳秒时间戳，既不会撞上批内任何一条，也不会撞上上次没跑完的残留。
            // 这一段**不计失败**：腾位失败的，第二段照常按原名试一次，只在那里记一笔，
            // 免得同一条被数两遍。
            val temps = LinkedHashMap<String, File>()
            for (t in cyclic) {
                if (!t.src.exists()) continue
                val tmp = File(t.src.parentFile, ".uin_rename_${t.index}_${System.nanoTime()}")
                if (t.src.renameTo(tmp)) temps[t.src.path] = tmp
            }

            // ── 第二段：落位 ──
            for (t in direct + cyclic) {
                val from = temps[t.src.path] ?: t.src
                // 盘上另有东西占着目标（批外并发创建等）——**不覆盖**，记一笔失败。
                // Linux 的 rename(2) 会静默盖掉目标，这道检查是唯一拦着数据丢失的那道。
                if (t.dst.exists()) {
                    failed++
                    continue
                }
                if (from.exists() && from.renameTo(t.dst)) {
                    ok++
                    changed++
                    lastRenamed = t.dst.absolutePath
                } else {
                    failed++
                }
            }

            // ── 第三段：回滚 ──
            // 没落到目标上的临时名要搬回原名 —— 绝不留下卡在 `.uin_rename_*` 上的文件。
            // 原位已被后来者占了就改成可见的「原名 (1)」：文件必须一直看得见，
            // 改名失败已经够糟了，不能变成丢文件。
            for ((orig, tmp) in temps) {
                if (!tmp.exists()) continue
                val target = File(orig)
                if (!target.exists() && tmp.renameTo(target)) continue
                tmp.renameTo(FileOps.uniqueSibling(target))
            }
            withContext(Dispatchers.Main) {
                dismissBatchRename()
                if (ok > 0) {
                    // 高亮最后一个改成功的：批量之后用户最想确认的是「东西还在不在」
                    if (lastRenamed.isNotEmpty()) highlight(side, lastRenamed)
                    refresh(side)
                }
                if (changed > 0) {
                    // 改过名就必须退出多选：选中表里存的是**旧路径**，留着就是一批
                    // 指向不存在文件的悬空项，下一次复制/删除会整批报错。
                    // 只在真的改了名时才退 —— 模板是 `#p` 时路径没变，不该打断用户。
                    exitMultiSelect()
                }
                _notices.tryEmit(
                    NoticeEvent.Text(
                        if (failed == 0) R.string.batch_rename_done
                        else R.string.batch_rename_failed,
                        ok.toString()
                    )
                )
            }
        }
    }

    /**
     * 执行重命名（4.5）。新名已存在时**不自动覆盖**，而是回到对话框给出建议名 ——
     * 与复制的冲突语义保持一致：文件管理器不能默默吃掉一个同名文件。
     */
    fun performRename(side: Side, path: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == File(path).name) {
            dismissRename()
            return
        }
        if (trimmed.contains('/')) {
            _state.update { st ->
                val req = st.renameRequest ?: return@update st
                st.copy(renameRequest = req.copy(suggestedName = FileOps.uniqueSibling(File(path)).name))
            }
            _notices.tryEmit(NoticeEvent.Text(R.string.files_name_invalid))
            return
        }
        // 归档内改名：比对**条目名**而不是 `File.exists()` —— 后者对虚拟路径恒为 false，
        // 拿它判重名会永远放行、静默吃掉同名条目
        val archivePath = VirtualPath.archiveOf(path)
        if (archivePath != null) {
            val internal = VirtualPath.internalOf(path).orEmpty()
            val parent = internal.substringBeforeLast('/', "")
            val newInternal = if (parent.isEmpty()) trimmed else "$parent/$trimmed"
            val archive = File(archivePath)
            viewModelScope.launch(Dispatchers.IO) {
                val ok = try {
                    ZipWrite.rename(archive, internal, newInternal)
                } catch (e: Exception) {
                    false
                }
                withContext(Dispatchers.Main) {
                    if (ok) {
                        dismissRename()
                        highlight(side, VirtualPath.join(archivePath, newInternal))
                        refresh(side)
                        _notices.tryEmit(NoticeEvent.Text(R.string.files_renamed, trimmed))
                    } else {
                        val suggestion = try {
                            ZipWrite.uniqueEntryName(archive, newInternal)
                        } catch (e: Exception) {
                            newInternal
                        }
                        _state.update { st ->
                            val req = st.renameRequest ?: return@update st
                            st.copy(
                                renameRequest = req.copy(
                                    suggestedName = suggestion.substringAfterLast('/')
                                )
                            )
                        }
                        _notices.tryEmit(NoticeEvent.Text(R.string.files_rename_conflict, trimmed))
                    }
                }
            }
            return
        }

        val src = File(path)
        val dst = File(src.parentFile, trimmed)
        viewModelScope.launch(Dispatchers.IO) {
            val ok = withContext(Dispatchers.IO) {
                if (dst.exists()) false else src.renameTo(dst)
            }
            withContext(Dispatchers.Main) {
                if (ok) {
                    dismissRename()
                    highlight(side, dst.absolutePath)
                    refresh(side)
                    _notices.tryEmit(NoticeEvent.Text(R.string.files_renamed, dst.name))
                } else {
                    _state.update { st ->
                        val req = st.renameRequest ?: return@update st
                        st.copy(
                            renameRequest = req.copy(
                                suggestedName = FileOps.uniqueSibling(src).name
                            )
                        )
                    }
                    _notices.tryEmit(NoticeEvent.Text(R.string.files_rename_conflict, dst.name))
                }
            }
        }
    }

    /** 弹新建框（4.5）。一个对话框三个键：取消 / 文件夹 / 文件。 */
    fun requestCreate(side: Side) {
        val dir = _state.value[side].cwd
        _state.update { st -> st.copy(createRequest = CreateRequest(side = side, dirPath = dir)) }
    }

    fun dismissCreate() {
        _state.update { st -> if (st.createRequest == null) st else st.copy(createRequest = null) }
    }

    /**
     * 弹压缩框（§4.7）。只吃**真实文件**：归档内条目不给压 —— 那得先解出来，语义不清，
     * 宁可让用户先解压再压（提示由调用方给）。
     *
     * 建议名取「第一个源的主干名」：单选时就是它自己的名字，多选时是个合理起点，
     * 用户不必从空框开始打。
     */
    fun requestCompress(side: Side, sources: List<String>) {
        val real = sources.filter { !VirtualPath.isInArchive(it) && File(it).name.isNotEmpty() }
        if (real.isEmpty()) return
        val dir = _state.value[side].cwd
        val first = File(real.first())
        val stem = first.name.substringBeforeLast('.', first.name)
        val base = if (real.size == 1) stem else "${first.parentFile?.name ?: stem}"
        _state.update { st ->
            st.copy(
                compressRequest = CompressRequest(
                    side = side,
                    dirPath = dir,
                    sources = real,
                    initialName = base
                )
            )
        }
    }

    fun dismissCompress() {
        _state.update { st -> if (st.compressRequest == null) st else st.copy(compressRequest = null) }
    }

    /**
     * 执行压缩（§4.7）。落点 = [CompressRequest.dirPath] + 用户给的名字 + 格式后缀。
     *
     * [password] zip / 7z 会用到；其它格式调用方本就不该给输入框（`Format.supportsPassword`）。
     * [level] / [encoding] / [keepMeta] 由压缩框现给（用户反馈⑪：**不进设置**）——
     * 这里顺手写回 pref，下次打开框还是这次的选择；用户点了取消则一个字都不记。
     * 成功后新包**高亮并滚到可视处**，与新建/重命名同一套反馈。
     *
     * **输出名撞车不再静默改名**（反馈 e17）：目标已存在就先摆三选 ——
     * 追加进已有包 / 换个名字新建 / 取消，用户点了才动。
     * （原先一上来就 `uniqueSibling()` 改成 `x (1).zip`，`create` 里那个
     *   「目标已存在」的询问因此**永远不触发**，也永远没有「并进已有包」这条路。）
     */
    /**
     * 「加入已有压缩包」（用户反馈 e15 **P1-10**）。
     *
     * 与 [performCompress] 是**两条**：目标是已存在的包，没有「新建」这回事，
     * 也不该走撞名三选 —— 那是用户没主动选目标时才需要的兜底。
     * 暂存件、失败回滚、冲突三选（模式为 null 时）全部复用 [ZipWrite.addFiles]，
     * 与反馈 e17 那条追加**同一个后端**，只是「问不问」由 `updateMode` 决定。
     */
    fun performCompressAppend(archiveName: String, mode: UpdateMode, level: String) {
        val req = _state.value.compressRequest ?: return
        dismissCompress()

        FilePrefs.setZipLevel(level)
        val archive = File(req.dirPath, archiveName)
        val jobs = req.sources.map { File(it) }

        _state.update { st -> st.copy(op = OpUiState.Idle) }
        runOp(OpKind.COMPRESS, jobs.size) { onProgress, onConflict ->
            // 目标在「列目录」到「按确定」之间被删掉的话，让 addFiles 自己去撞 ——
            // 暂存件那步就会报出来。这里静默 return 等于「按了确定却什么都没发生」，
            // 是最难查的一种坏。
            ZipWrite.addFiles(
                archive = archive,
                internalDir = "",
                sources = jobs,
                level = level,
                updateMode = mode,
                kind = OpKind.COMPRESS,
                onProgress = onProgress,
                onConflict = onConflict
            ) ?: OpResult(cancelled = true, successCount = 0, failedCount = 0, firstError = null)
        }
        highlightCreatedArchive(req.side, archive.path)
    }

    fun performCompress(
        name: String,
        format: ArchiveCompress.Format,
        password: String?,
        level: String,
        encoding: String,
        keepMeta: Boolean,
        /** 分卷字节数（0 = 不分卷）。只有 zip 有分卷概念，其余格式一律置 0。 */
        splitSize: Long = 0L,
        /** 7z 专属：连文件名表一起加密。 */
        encryptHeader: Boolean = false
    ) {
        val req = _state.value.compressRequest ?: return
        dismissCompress()

        FilePrefs.setZipLevel(level)
        FilePrefs.setZipEncoding(encoding)
        FilePrefs.setZipFormat(format.extension)
        FilePrefs.setKeepMeta(keepMeta)
        // i.txt 第 12 条：分卷大小也记住；密码/加密头与密码绑定，不记。
        FilePrefs.setSplitSize(if (format.supportsSplit) splitSize else 0L)

        // 后缀跟着格式走：把**已知的归档后缀全部剥掉**再拼新的 ——
        // 只 removeSuffix(当前格式) 的话，「a.zip」换个格式压出来就是「a.zip.tar」
        val stem = name.trim().ifBlank { req.initialName }.let { n ->
            ArchiveCompress.Format.entries.fold(n) { acc, f -> acc.removeSuffix(f.extension) }
        }
        if (stem.isEmpty()) return
        val requested = File(req.dirPath, stem + format.extension)

        if (requested.exists()) {
            // 先问再动；`uniqueSibling` 只在用户选「换个名字」时才轮得到
            _state.update { st ->
                st.copy(
                    compressConflict = CompressConflict(
                        existing = requested.path,
                        suggested = FileOps.uniqueSibling(requested).name,
                        side = req.side,
                        sources = req.sources,
                        format = format,
                        password = password,
                        level = level,
                        encoding = encoding,
                        keepMeta = keepMeta,
                        splitSize = if (format.supportsSplit) splitSize else 0L,
                        encryptHeader = encryptHeader && format == ArchiveCompress.Format.SEVEN_Z
                    )
                )
            }
            return
        }
        runCompressTo(
            requested, req.side, req.sources, format, password, level, encoding, keepMeta,
            if (format.supportsSplit) splitSize else 0L,
            encryptHeader && format == ArchiveCompress.Format.SEVEN_Z
        )
    }

    /** 三选之「换个名字新建」（反馈 e17）：用撞车时算好的 `x (1).zip` 真建一个。 */
    fun compressConflictNew() {
        val c = _state.value.compressConflict ?: return
        _state.update { st -> st.copy(compressConflict = null) }
        runCompressTo(
            File(File(c.existing).parent, c.suggested),
            c.side, c.sources, c.format, c.password, c.level, c.encoding, c.keepMeta,
            c.splitSize, c.encryptHeader
        )
    }

    /**
     * 三选之「追加进已有包」（反馈 e17）。
     *
     * 走 `ZipWrite.addFiles` —— 它是**往已存在的 zip 里塞条目**的那条底层路
     * （对账报告里「有底层 API、没有入口」的入口就是这里）。
     * 撞名问顶层、可「应用到全部」，与真实目录复制同一套口径。
     */
    fun compressConflictAppend() {
        val c = _state.value.compressConflict ?: return
        _state.update { st -> st.copy(compressConflict = null) }
        val archive = File(c.existing)
        val jobs = c.sources.map { File(it) }
        _state.update { st -> st.copy(op = OpUiState.Idle) }
        runOp(OpKind.COMPRESS, jobs.size) { onProgress, onConflict ->
            ZipWrite.addFiles(
                archive, "", jobs,
                kind = OpKind.COMPRESS,
                onProgress = onProgress, onConflict = onConflict
            ) ?: OpResult(cancelled = true, successCount = 0, failedCount = 0, firstError = null)
        }
        highlightCreatedArchive(c.side, archive.path)
    }

    /** 三选之「取消」（反馈 e17）：一个字都不写，已有包原样不动。 */
    fun dismissCompressConflict() {
        _state.update { st -> if (st.compressConflict == null) st else st.copy(compressConflict = null) }
    }

    /**
     * 真正落笔压缩：目标保证**尚不存在**（撞车的那条路已经被三选接走）。
     */
    private fun runCompressTo(
        dest: File,
        side: Side,
        sources: List<String>,
        format: ArchiveCompress.Format,
        password: String?,
        level: String,
        encoding: String,
        keepMeta: Boolean,
        splitSize: Long = 0L,
        encryptHeader: Boolean = false
    ) {
        val jobs = sources.map { File(it) }

        _state.update { st -> st.copy(op = OpUiState.Idle) }
        runOp(OpKind.COMPRESS, jobs.size) { onProgress, onConflict ->
            ArchiveCompress.create(
                dest = dest,
                sources = jobs,
                password = password?.takeIf { it.isNotEmpty() && format.supportsPassword },
                splitSize = splitSize,
                level = level,
                encoding = encoding,
                keepMeta = keepMeta,
                encryptHeader = encryptHeader && format == ArchiveCompress.Format.SEVEN_Z,
                onProgress = onProgress,
                onConflict = onConflict
            ) ?: OpResult(cancelled = true, successCount = 0, failedCount = 0, firstError = null)
        }
        highlightCreatedArchive(side, dest.path)
    }

    /** 建完把焦点挪到那个包上（与新建/重命名的高亮一致）。 */
    /** 把某条路径标成落点并滚到可视处；`.bak` 恢复等 UI 侧动作也用这一条（g11）。 */
    internal fun highlightCreatedArchive(side: Side, path: String) {
        _state.update { st ->
            val pane = st[side]
            st.copy(
                left = if (side == Side.LEFT) pane.copy(highlightPath = path) else st.left,
                right = if (side == Side.RIGHT) pane.copy(highlightPath = path) else st.right
            )
        }
        scheduleHighlightClear(side, path)
    }

    // ==================== 整包解压（用户反馈⑪：落点在弹窗里选） ====================

    /**
     * 弹整包解压框。只吃**真实归档文件**：目录没有包可解，归档内条目是虚拟路径、
     * 拿不到字节（要先把外层整包解出来）。菜单那边已按 [extractableName] 挡过，
     * 这里再挡一次 —— 这个函数是公开的，别的入口也会调。
     */
    fun requestExtract(side: Side, path: String) {
        if (path.isEmpty() || VirtualPath.isInArchive(path)) return
        val file = File(path)
        if (!file.isFile || !extractableName(file.name)) return

        val cwd = _state.value[side].cwd
        // 「当前目录」只在**真实目录**里落得了地：正处在归档内时 cwd 是虚拟路径，
        // 点确定只会换来一句「目标不是文件夹」。那种情况替用户改选「另一窗口」——
        // 另一窗十有八九还在真实目录里；两窗都泡在归档内才退回「指定目录」（至少记着上次那条真实路径）。
        val stored = FilePrefs.extractTarget()
        val initial = if (stored == FilePrefs.TARGET_HERE && VirtualPath.isInArchive(cwd)) {
            val st = _state.value
            val other = st[st.otherSide].cwd
            if (other.isNotEmpty() && !VirtualPath.isInArchive(other)) {
                FilePrefs.TARGET_OTHER
            } else {
                FilePrefs.TARGET_CUSTOM
            }
        } else {
            stored
        }
        _state.update { st ->
            st.copy(extractRequest = ExtractRequest(side, file.absolutePath, cwd, initial))
        }
    }

    fun dismissExtract() {
        _state.update { st -> if (st.extractRequest == null) st else st.copy(extractRequest = null) }
    }

    /**
     * 执行整包解压。[targetDir] 由弹窗按四种落点（当前目录 / 另一窗口 / 子文件夹 / 指定目录）算好再给。
     *
     * 传给引擎的**只有顶层根**（`a.zip` 里的 `sub`、`x.txt`），不是全部条目 ——
     * 引擎按「根 → 根下面的一切」展开落点；把几十万条明细全塞进去会变成
     * 「每个文件各自成为一个根」，目录树被拍平成一坨同名文件。
     *
     * [restoreTimestamps] / [deleteSource] 写回 pref，下次打开框还是这次的选择；点了取消则一个字都不记。
     * 撞名、进度、取消全部交给 [ArchiveExtract.execute]，与「归档内条目复制到另一窗格」
     * 是同一套口径 —— 同一件事在两处入口必须有一样的手感。
     */
    fun performExtract(
        targetKey: String,
        targetDir: String,
        restoreTimestamps: Boolean,
        deleteSource: Boolean,
        /** 加密 zip 的密码（解压框里那个只在**确实加密时**才摆出来的框，b10）。 */
        password: String? = null
    ) {
        val req = _state.value.extractRequest ?: return
        dismissExtract()
        FilePrefs.setRestoreMeta(restoreTimestamps)
        // 上次选的落点记下来，下次开框还是它；「指定目录」连路径一起记
        FilePrefs.setExtractTarget(targetKey)
        FilePrefs.setExtractDeleteSource(deleteSource)
        if (targetKey == FilePrefs.TARGET_CUSTOM) FilePrefs.setCustomExtractTarget(targetDir)

        val archive = File(req.archivePath)
        val target = File(targetDir.trim())
        // 「解压到文件夹」给的目录**本来就是不存在的** —— 新建一个子目录正是这一项的定义
        // （用户反馈 d17：选默认文件夹必弹「目标不是文件夹」，一次都解不开；日志 23:35:43
        // 起连报三次）。所以只有这一项先建再验。
        // 另外两项不许顺手补：**指定目录**多半是路径过期、**另一窗格**是虚拟路径，
        // 悄悄 mkdir 等于把拼错的落点变成真的，那比报错更难查。
        if (targetKey == FilePrefs.TARGET_FOLDER && VirtualPath.split(targetDir.trim()) == null) {
            target.mkdirs()
        }
        if (!target.isDirectory) {
            _notices.tryEmit(NoticeEvent.Text(R.string.files_target_invalid))
            return
        }

        _state.update { st -> st.copy(op = OpUiState.Idle) }
        runOp(OpKind.COPY, 1) { onProgress, onConflict ->
            // 条目表在 IO 线程读：7z/tar 是全量扫描，主线程上读会直接卡住整个界面
            val roots = withContext(Dispatchers.IO) {
                val entries = runCatching { readArchiveEntries(archive) }
                    .getOrElse { e ->
                        // 7z 头被加密而密码框里是空的（e15 P1-12）：报成人话 ——
                        // 库那句英文用户看不懂，也搜不到。
                        if (isPasswordRequired(e)) {
                            throw java.io.IOException(
                                com.UIN.Tool.utils.Str.get(R.string.files_archive_encrypted)
                            )
                        }
                        throw e
                    }
                entries
                    .mapNotNull { e -> safeInternalPath(e.path)?.substringBefore('/') }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .map { File(VirtualPath.join(req.archivePath, it)) }
            }
            val res = ArchiveExtract.execute(
                kind = OpKind.COPY,
                sources = roots,
                targetDir = target,
                restoreTimestamps = restoreTimestamps,
                password = password,
                onProgress = onProgress,
                onConflict = onConflict
            )
            // 用户反馈 c14「解压后删除源文件」：只在**整批成功且没被取消**时删原包 ——
            // 删了不可逆，解到一半就删等于两头落空；失败或取消时源包原样留着，重来还有得解。
            if (deleteSource && !res.cancelled && res.failedCount == 0 && res.successCount > 0) {
                withContext(Dispatchers.IO) {
                    try {
                        archive.delete()
                    } catch (e: Exception) {
                        Logger.w("FileManageVM", "deleteSource after extract failed: ${e.message}")
                    }
                }
            }
            // 用户反馈 e15 P1-5：密码打错时**自动把解压框重开** —— 原先只报一句「密码错误」，
            // 用户得从头再点一遍才能重输。落点/还原/删源都写在 pref 里了，重开的框自己读得到，
            // 所以连当初选的那几项也一并回到用户刚选的值（req.initialTarget 是**开框那一刻**
            // 的旧值，得按当前 pref 重取）。
            // 必须写在 block 里：runOp 是「起个协程就返回」，放在它后面会在操作跑完之前执行。
            // 三句都算「要密码 / 没密码」（e15 P1-12 把 7z 也纳进来了）：
            // 密码不对、正文加密解到才要、以及压根没给密码（fail-fast 那句）——
            // 三种都重开，用户不用自己从头再点一遍。
            val needPassword =
                res.firstError == com.UIN.Tool.utils.Str.get(R.string.files_archive_bad_password) ||
                    res.firstError == com.UIN.Tool.utils.Str.get(R.string.files_archive_encrypted) ||
                    res.firstError == com.UIN.Tool.utils.Str.get(R.string.files_archive_password_required)
            if (needPassword) {
                _state.update { st ->
                    st.copy(extractRequest = req.copy(initialTarget = FilePrefs.extractTarget()))
                }
            }
            // 用户反馈 e15 P2-16：解压**成功**才给「定位」—— 失败/取消时落点里没有可去的
            // 东西，弹一个空完成框只会碍事（那两种情形已有各自的 toast 说清楚）。
            // 高亮取解出来的**第一个根**：落点就是眼前这一格时，「定位」才不是一句空话。
            if (!res.cancelled && res.failedCount == 0 && res.successCount > 0) {
                val root = roots.firstOrNull()?.name
                _notices.tryEmit(
                    NoticeEvent.ExtractLocated(
                        dir = target.absolutePath,
                        highlight = root?.let { File(target, it).absolutePath }
                    )
                )
            }
            res
        }
    }

    /** 弹属性框（4.5）。传入整批目标：多条时由 UI 汇总成「整体属性」。 */
    fun requestProperties(paths: List<String>) {
        val clean = paths.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        _state.update { st -> st.copy(propertiesPaths = clean) }
    }

    /** 兼容单条调用点。 */
    fun requestProperties(path: String) = requestProperties(listOf(path))

    fun dismissProperties() {
        _state.update { st -> if (st.propertiesPaths.isEmpty()) st else st.copy(propertiesPaths = emptyList()) }
    }

    /**
     * 执行新建（4.5）。留空给默认名、重名自动加序号；成功后新条目**高亮并滚到可视处**。
     *
     * @param defaultName 留空时用的名字（`New folder` / `New file`），由调用方给 ——
     * VM 不碰字符串资源，保证 zh/en 切换在 UI 层一次性生效。
     */
    fun performCreate(side: Side, isDir: Boolean, name: String, defaultName: String) {
        val dirPath = _state.value[side].cwd
        val raw = name.trim().ifEmpty { defaultName }
        if (raw.isEmpty()) return

        // 归档内新建 = 往包里加一条目。重名同样要按**条目名**判（File.exists() 对虚拟路径恒 false）
        val archivePath = VirtualPath.archiveOf(dirPath)
        if (archivePath != null) {
            val archive = File(archivePath)
            val internalDir = VirtualPath.internalOf(dirPath).orEmpty()
            viewModelScope.launch(Dispatchers.IO) {
                val base = if (internalDir.isEmpty()) raw else "$internalDir/$raw"
                val target = try {
                    if (ZipWrite.exists(archive, base)) {
                        ZipWrite.uniqueEntryName(archive, base)
                    } else {
                        base
                    }
                } catch (e: Exception) {
                    base
                }
                val ok = try {
                    ZipWrite.create(archive, target, isDir)
                } catch (e: Exception) {
                    false
                }
                val leaf = target.substringAfterLast('/')
                withContext(Dispatchers.Main) {
                    if (!ok) {
                        _notices.tryEmit(NoticeEvent.Text(R.string.files_create_failed, raw))
                    } else {
                        dismissCreate()
                        highlight(side, VirtualPath.join(archivePath, target))
                        refresh(side)
                        _notices.tryEmit(NoticeEvent.Text(R.string.files_created, leaf))
                    }
                }
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val created = withContext(Dispatchers.IO) {
                val base = File(dirPath, raw)
                val target = if (base.exists()) FileOps.uniqueSibling(base) else base
                val ok = if (isDir) target.mkdirs() else target.createNewFile()
                if (ok) target else null
            }
            withContext(Dispatchers.Main) {
                if (created == null) {
                    _notices.tryEmit(NoticeEvent.Text(R.string.files_create_failed, raw))
                } else {
                    dismissCreate()
                    highlight(side, created.absolutePath)
                    refresh(side)
                    _notices.tryEmit(NoticeEvent.Text(R.string.files_created, created.name))
                }
            }
        }
    }

    /**
     * 打开新建框时实时核对重名（4.5：重名自动加序号）。
     * 只在名字**变化时**才 stat 一次，输入框打字不会拖慢主线程。
     */
    fun checkCreateName(side: Side, name: String) {
        _state.update { st ->
            val req = st.createRequest ?: return@update st
            val base = File(req.dirPath, name.trim())
            val suggest = if (name.isNotBlank() && base.exists()) {
                FileOps.uniqueSibling(base).name
            } else ""
            if (suggest == req.suggestedName) st
            else st.copy(createRequest = req.copy(suggestedName = suggest, initialName = name))
        }
    }

    /** 把 [path] 标成高亮并滚动到可视处（4.5：新建/重命名成功后的落点提示）。 */
    private fun highlight(side: Side, path: String) {
        _state.update { st ->
            st.mutate(side) { p -> p.copy(highlightPath = path) }
        }
        scheduleHighlightClear(side, path)
    }

    /**
     * 「已定位」高亮 5 秒后自动退场。
     *
     * 只清**同一条**路径：到点时状态里若已经换成别的落点（用户 5 秒内又定位了），
     * 说明这条提示早就不该代表了，不能顺手把新的也清掉。
     */
    private fun scheduleHighlightClear(side: Side, path: String) {
        val idx = if (side == Side.LEFT) 0 else 1
        highlightClearJobs[idx]?.cancel()
        highlightClearJobs[idx] = viewModelScope.launch {
            kotlinx.coroutines.delay(HIGHLIGHT_LINGER_MS)
            _state.update { st ->
                if (st[side].highlightPath == path) {
                    st.mutate(side) { p -> p.copy(highlightPath = null) }
                } else st
            }
        }
    }

    /**
     * 搜索结果点选后的定位高亮（§4.8 / §4.16 共用同一套「落点高亮」）。
     *
     * **调用方必须先 `navigateTo` 再调本方法** —— `navigateTo` 会把 `highlightPath`
     * 清成 null（与 cwd 变更在同一个 copy 里完成，见下方注释），反过来调会被立刻抹掉。
     *
     * 与 [highlight] 分开是因为后者是 `private` 的内部实现，改它的可见性会把
     * 「新建/重命名成功后的落点提示」一并暴露给外部，语义上不该那样耦合。
     */
    fun highlightFromSearch(side: Side, path: String) = highlight(side, path)

    /**
     * 用户点了列表里的某一行 → 「已定位」状态作废（§4.16：点其它行后清除）。
     *
     * 换目录的情况由 `navigateTo` 在**同一个 copy** 里顺手清掉，这里只兜「点了但没换目录」
     * 的分支（同目录的文件、归档内条目）。开头那道 null 判断让**绝大多数**行点击
     * （本来就没在定位状态）不产生任何多余的状态发射。
     */
    fun clearHighlight(side: Side) {
        // 用户自己清掉/点了别的行 → 定时任务不用再等，顺手收掉。
        highlightClearJobs[if (side == Side.LEFT) 0 else 1]?.cancel()
        _state.update { st ->
            if (st[side].highlightPath == null) st
            else st.mutate(side) { p -> p.copy(highlightPath = null) }
        }
    }

    // 注：落点高亮的清除放在 navigateTo / goBack / goForward 里与 cwd 变更**同一个 copy** 完成，
    // 而不是另起一个 clearHighlight() —— 分两步会多发射一次中间状态，重组时能看到「旧目录 + 新高亮」。

    // ==================== 设置（4.17：改动即时生效） ====================

    /**
     * 把 [FilePrefs] 重新套到两窗格上。设置页返回时调一次即可：
     * 根范围变了就把**越界窗格拉回新根**，双窗格开关、默认排序、隐藏开关立刻生效。
     */
    fun applySettings(firstLaunch: Boolean = false) {
        val root = FilePrefs.rootPath()
        val sort = defaultSortSpec()
        val sysHidden = FilePrefs.showSystemHidden()
        val manHidden = FilePrefs.showManualHidden()
        val manualSet = FilePrefs.manualHidden()
        val dual = FilePrefs.dualPane()

        // 会话恢复**只算一次且放在 update 外面**：`update` 是 CAS 重试循环，放进去会把
        // `File(dir).isDirectory` 这类 stat 重复跑；这里也把校验收敛到 2 次 stat（左右 cwd 各一）。
        val base = PaneState(
            root = root,
            sort = sort,
            showSystemHidden = sysHidden,
            showManualHidden = manHidden,
            manualHidden = manualSet
        )
        val restored = if (firstLaunch) loadSession(base, root) else null

        _state.update { st ->
            if (firstLaunch) {
                // 进程被杀后重启：能读回上次会话就原样接上（含两窗历史与活动窗格）；
                // 读不回（首次启动 / 关了「记住上次目录」/ 路径已失效越界）就按起始目录重开
                if (restored != null) {
                    st.copy(
                        left = restored.first,
                        right = restored.second,
                        active = restored.third,
                        dualEnabled = dual,
                        settingsTick = st.settingsTick + 1
                    )
                } else {
                    st.copy(
                        left = base.copy(cwd = resolveStartDir(root)),
                        right = base.copy(cwd = root),
                        dualEnabled = dual,
                        settingsTick = st.settingsTick + 1
                    )
                }
            } else {
                fun fit(p: PaneState): PaneState {
                    val synced = p.copy(
                        root = root,
                        sort = sort,
                        showSystemHidden = sysHidden,
                        showManualHidden = manHidden,
                        manualHidden = manualSet
                    )
                    return if (!insideRoot(synced.cwd, root)) {
                        synced.copy(
                            cwd = resolveStartDir(root),
                            back = emptyList(),
                            forward = emptyList(),
                            items = emptyList(),
                            filter = "",
                            selection = emptySet(),
                            rangeAnchor = null,
                            errorRes = null,
                            // 换目录必须一并归零滚动位 —— navigateTo / goBack / goForward
                            // 三处都有这行，唯独这里漏了：带着上个目录的下标进起始目录，
                            // LazyListState 会把它 clamp 到末尾，看起来就是「一进来就自动
                            // 滚到最后」（用户反馈㉕）。高亮也一并作废，理由同上三处。
                            scrollIndex = 0,
                            highlightPath = null
                        )
                    } else synced
                }
                st.copy(
                    left = fit(st.left),
                    right = fit(st.right),
                    dualEnabled = dual,
                    settingsTick = st.settingsTick + 1
                )
            }
        }
        load(Side.LEFT)
        load(Side.RIGHT)
    }

    private fun defaultSortSpec() = SortSpec(
        key = when (FilePrefs.sortKey()) {
            FilePrefs.SORT_SIZE -> SortKey.SIZE
            FilePrefs.SORT_DATE -> SortKey.DATE
            FilePrefs.SORT_TYPE -> SortKey.TYPE
            else -> SortKey.NAME
        },
        ascending = FilePrefs.sortAscending(),
        dirsFirst = FilePrefs.dirsFirst()
    )

    // ==================== 会话快照（§5.2：进程重建后原样恢复） ====================

    /**
     * 生成会话快照 JSON。**只含位置相关字段**——条目列表是 IO 结果（重启后一律重扫），
     * 选择集故意不存：文件可能已变动，存下来会留下一串点不到的"幽灵选中"。
     */
    private fun sessionSnapshot(st: FileManageUiState): String {
        if (st.left.cwd.isEmpty() || st.right.cwd.isEmpty()) return ""
        return try {
            JSONObject().apply {
                put("active", if (st.active == Side.RIGHT) "right" else "left")
                put("left", paneSnapshot(st.left))
                put("right", paneSnapshot(st.right))
            }.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun paneSnapshot(p: PaneState): JSONObject = JSONObject().apply {
        put("cwd", p.cwd)
        put("back", JSONArray().also { arr -> p.back.forEach { arr.put(it) } })
        put("forward", JSONArray().also { arr -> p.forward.forEach { arr.put(it) } })
    }

    /**
     * 读回会话快照。返回 null = 没有可用快照（首次启动 / 关了「记住上次目录」/
     * 快照里的目录已不存在或越出新根），调用方回落默认目录。
     */
    private fun loadSession(base: PaneState, root: String): Triple<PaneState, PaneState, Side>? {
        if (!FilePrefs.rememberLastDir()) return null
        val raw = FilePrefs.sessionJson()
        if (raw.isBlank()) return null
        return try {
            val json = JSONObject(raw)

            fun history(holder: JSONObject, name: String): List<String> {
                val arr = holder.optJSONArray(name) ?: return emptyList()
                // 历史条目**只验「在根内」**，不逐个 stat：几十次系统调用不该发生在启动主线程上。
                // 万一历史里的目录后来被删了，点下去会走错误空态（「路径不存在」），不会崩。
                return (0 until arr.length())
                    .map { arr.optString(it, "") }
                    .filter { it.isNotEmpty() && insideRoot(it, root) }
            }

            fun restorePane(key: String): PaneState? {
                val holder = json.optJSONObject(key) ?: return null
                val cwd = holder.optString("cwd", "")
                if (cwd.isEmpty() || !insideRoot(cwd, root)) return null
                // f11：**包内位置也要恢复**。原先这里一律要求 `File(cwd).isDirectory`，
                // 虚拟路径（`x.zip!/src`）必然不是目录 → 整段恢复返回 null，两格都回到
                // 起始目录。用户从后台切回来，看起来就像「包读不出了」。
                // 虚拟 cwd 的校验换成「归档位是普通文件」；包被删了就落到既有的
                // `files_path_missing` 空态，不会崩。
                val split = VirtualPath.split(cwd)
                if (split != null) {
                    if (!File(split.first).isFile) return null
                } else if (!File(cwd).isDirectory) {
                    return null
                }
                return base.copy(
                    cwd = cwd,
                    back = history(holder, "back"),
                    forward = history(holder, "forward")
                )
            }

            val left = restorePane("left") ?: return null
            val right = restorePane("right") ?: return null
            val active = if (json.optString("active", "left") == "right") Side.RIGHT else Side.LEFT
            Triple(left, right, active)
        } catch (e: Exception) {
            null
        }
    }

    /** 设置里的「默认起始目录」；留空 = 根目录。 */
    private fun resolveStartDir(root: String): String {
        val configured = FilePrefs.startDir()
        if (configured.isNotBlank()) {
            val f = File(configured)
            if (insideRoot(f.absolutePath, root) && f.isDirectory) return f.absolutePath
        }
        return root
    }

    /** 路径是否在根范围内（根 = `/` 时一切都在）。 */
    private fun insideRoot(path: String, root: String): Boolean {
        if (path.isEmpty()) return false
        if (root == "/") return true
        return path == root || path.startsWith("$root/")
    }

    companion object {
        /**
         * 进程级**唯一**的那份状态机（e20 ③）。
         *
         * 用户拍板「编辑器侧边栏直接用单窗口文件管理器」—— 两处若各持一份实例就会
         * 各说各话：这边「拿起」了、那边没有粘贴键；一边在跑进度、另一边空闲时把常驻
         * 通知收掉；两份状态快照轮流覆盖 `FilePrefs.sessionJson`，重开 app 落到哪全看谁最后写。
         * 挂在进程上不随活动销毁：本类没有 [onCleared]，也就没有必须在销毁时收尾的东西。
         */
        private val sharedInstance: FileManageViewModel by lazy { FileManageViewModel() }

        /** 取进程级那份实例（首次调用才建）。文件页与编辑器侧边栏都走这里。 */
        fun shared(): FileManageViewModel = sharedInstance

        /**
         * 删除前要多问一层的系统目录（4.5）。都是「删了系统就起不来」的地方，
         * 没有回收站兜底，所以宁可多一次确认。
         */
        private val DANGEROUS_ROOTS = listOf(
            "/system", "/vendor", "/product", "/odm",
            "/data", "/etc", "/apex", "/su", "/sbin"
        )
    }
}
