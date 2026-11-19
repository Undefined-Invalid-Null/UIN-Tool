// app/src/main/java/com/UIN/Tool/ui/screen/files/FileListPane.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedStatusChip
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow   // 正确包名：它在 compose.runtime，不在 kotlinx.coroutines.flow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 「已定位」状态条的文案（§4.16）。
 *
 * 被定位的文件若落在**中转页的缓存副本目录**里，就补一句「已复制到缓存」——
 * 否则用户会以为文件本来就在那儿，回头找不到。
 *
 * 刻意**不从深链带一个 `copied` 标志位**，而是看路径前缀：`cache/files/` 下的
 * 本来就是副本，看一眼就知道。穿参数会随旋转/进程重建丢，路径不会；
 * 用户手动进缓存目录高亮某个文件时，这句话同样是对的。
 *
 * 与 [FilePrefs] 同包，无需 import。
 */
private fun locateLabel(path: String): String {
    val name = path.substringAfterLast('/')
    val cachePrefix = FilePrefs.filesCacheDir().absolutePath + "/"
    return if (path.startsWith(cachePrefix)) {
        Str.get(R.string.files_located_status_copied, name)
    } else {
        Str.get(R.string.files_located_status, name)
    }
}

/**
 * 长按上下文菜单的动作（4.3）。
 *
 * 枚举而不是字符串，是因为菜单要按**动作**做门控（只读位置不给写操作、归档内
 * 把「复制」改名「解压」），文案与图标一律从枚举派生，枚举只管"做哪件事"。
 * （f3 起文案不再带方向箭头 —— 复制/移动都是**拿起**，没有"点一下甩向对面"这回事。）
 */
enum class ContextAction {
    // 「打开」不在菜单里（用户反馈 c5）：点一下就是打开，长按之后再点一次「打开」
    // 纯属绕路。目录同理，点进去即可。故曾从 12 项减到 11 项；后补的「隐藏」「添加书签」
    // 都是**另一个**动作，不与 c5 的结论冲突。动作数随门控浮动，最多 12 项
    // → 3 列 4 行、末行留 0 个空位（右缘由等宽占位补齐，见 ContextMenuFor）。
    // （f3 又收掉了 e21 的「自选目标」两格 —— 复制/移动自己就是拿起，摆两遍是误导。）

    /**
     * 复制 / 移动：**拿起 → 走到哪粘到哪**（右下角浮出粘贴钮）。
     *
     * f3 起双窗格与单窗口**同一套口径**：不再有「点一下就甩到对面」的直达版，
     * 也不再单摆「复制到…」的自选目标格 —— 同一份菜单里摆两套复制语义，
     * 扫一眼分不出哪格是哪格。落点一律由用户走过去自己定。
     */
    COPY,

    /** 见 [COPY]：拿起后走到目标目录再粘。 */
    MOVE,

    /** 删除。 */
    DELETE,

    /** 重命名。 */
    RENAME,

    /** 属性。 */
    PROPERTIES,

    /** 压缩到归档（§4.7）：读源 + 在**当前目录**新建包，对只读位置也是合法操作。 */
    COMPRESS,

    /**
     * 整包解出（用户反馈⑪：解压的落点在**弹窗里选**，不放设置）。
     *
     * **纯读归档 + 写目标目录**，所以不在 [WRITE_ONLY_ACTIONS] 里 ——
     * 归档本身只读也照样能解。菜单里由 `ContextMenuFor` 按
     * [extractableName] 门控：`cpio`/`ar` 认得出但本版不支持，给了就是点了才报错。
     */
    EXTRACT,

    /** 「选择」——进多选并选中这一条（4.3：长按不直接进多选，要经菜单确认）。 */
    SELECT,

    /**
     * 分享（子 agent 对比建议的最后一条：上下文菜单缺「分享」）。
     *
     * **纯读操作**，所以不在 [WRITE_ONLY_ACTIONS] 里 —— 只读位置照样能分享出去，
     * 这正是从归档/只读分区里把文件交出去的唯一通路。
     */
    SHARE,

    /**
     * 「打开方式」（用户反馈③）：先弹**内置**能力的选择框，一个都不适用就直接交给系统；
     * 框左下角的「全部」随时可以换成整个系统的应用列表。
     */
    OPEN_WITH,

    /**
     * 并排对比选中的两个文件（6.3.0）。
     *
     * **只读**，不在 [WRITE_ONLY_ACTIONS] 里。菜单里由 `ContextMenuFor` 按
     * 「整批且恰好两条」门控 —— 对比天然要两个文件，给一条或三条都是点了才知道不行。
     */
    CONTRAST,

    /**
     * 「隐藏 / 取消隐藏」（MT.md §4.8 手动隐藏集）。
     *
     * 这一条补的是一处**只做了读侧**的半套功能（用户反馈 b28「类比排查」）：
     * `PaneState` 的过滤、设置页开关、偏好导入导出全都活着，`FilePrefs.setManualHidden`
     * 却从来没有任何调用点 —— 想藏也藏不了，开关摆着像坏了。
     *
     * **不碰磁盘**，只写应用自己的偏好，所以不在 [WRITE_ONLY_ACTIONS] 里：只读分区、
     * 只读归档里照样能把条目从列表里藏起来。归档内条目例外（虚拟路径记进偏好后
     * 永远对不上），由 `ContextMenuFor` 按 [VirtualPath.isInArchive] 门控。
     *
     * 文案随状态变（[R.string.files_ctx_hide] ↔ [R.string.files_ctx_unhide]）：
     * 只给「隐藏」的话，藏起来之后就再也没有把它请回来的入口。
     */
    HIDE,

    /**
     * 收藏这一条（用户反馈 e4：文件操作菜单里要能「添加书签」）。
     *
     * 页头 ⋮ 菜单早就有「收藏当前目录」，但那是**整格**的收藏 —— 长按列表里某一条
     * 想把它存起来时没有任何入口。落子规则由 `ContextMenuFor` 门控：
     * **只给真实目录**（书签是导航目标，文件当不了目录），
     * 归档内的虚拟路径也先出包再收藏。
     *
     * 纯写应用自己的偏好，不碰磁盘 → 不在 [WRITE_ONLY_ACTIONS] 里。
     */
    BOOKMARK,

    /**
     * 「添加到压缩包」（f11）：把选中项加进**另一窗格当前所在的归档**。
     *
     * 只在「双窗格 && 对面停在**可写**归档里」时摆出来，并**替换**掉
     * 解压 / 复制 / 移动三格 —— 那一刻用户的意思很清楚：东西往对面那个包里放。
     * 同一份菜单里留着「复制」，他就得先拿起、再走到包里、再粘，绕一圈还是回到这。
     *
     * 落点是包里**当前目录**（不是根）。源在别的包里也行：先在私有暂存区解出来再往里加，
     * 全程不落共享存储。
     */
    ADD
}

/** 从 [ContextAction] 生成菜单项文案。 */
fun contextActionLabel(action: ContextAction): String = when (action) {
    ContextAction.ADD -> Str.get(R.string.files_ctx_add)
    ContextAction.COPY -> Str.get(R.string.files_copy)
    ContextAction.MOVE -> Str.get(R.string.files_move)
    ContextAction.DELETE -> Str.get(R.string.files_ctx_delete)
    ContextAction.RENAME -> Str.get(R.string.files_ctx_rename)
    ContextAction.PROPERTIES -> Str.get(R.string.files_ctx_properties)
    ContextAction.COMPRESS -> Str.get(R.string.files_ctx_compress)
    ContextAction.EXTRACT -> Str.get(R.string.files_ctx_extract)
    ContextAction.OPEN_WITH -> Str.get(R.string.files_ctx_open_with)
    ContextAction.SHARE -> Str.get(R.string.share)
    ContextAction.SELECT -> Str.get(R.string.files_ctx_select)
    ContextAction.CONTRAST -> Str.get(R.string.cmp_open)
    ContextAction.HIDE -> Str.get(R.string.files_ctx_hide)
    ContextAction.BOOKMARK -> Str.get(R.string.files_add_bookmark)
}

/** 菜单项里配的图标。 */
fun contextActionIcon(action: ContextAction): ImageVector = when (action) {
    ContextAction.ADD -> Icons.Default.AddCircle
    ContextAction.COPY -> Icons.Default.ContentCopy
    ContextAction.MOVE -> Icons.Default.ContentCut
    ContextAction.DELETE -> Icons.Default.Delete
    ContextAction.RENAME -> Icons.Default.Edit
    ContextAction.PROPERTIES -> Icons.Default.Info
    ContextAction.COMPRESS -> Icons.Default.FolderZip
    ContextAction.EXTRACT -> Icons.Default.Unarchive
    ContextAction.OPEN_WITH -> Icons.Default.OpenInNew
    ContextAction.SHARE -> Icons.Default.Share
    ContextAction.SELECT -> Icons.Default.Check
    ContextAction.CONTRAST -> Icons.Default.Compare
    ContextAction.HIDE -> Icons.Default.VisibilityOff
    ContextAction.BOOKMARK -> Icons.Default.Bookmark
}

/**
 * 手指落在窗格内任意位置（点击、长按、滑动都算）就把该栏切成活动窗格（4.1 / 4.13）。
 *
 * 用 **Initial pass** 抢在子节点消费事件之前观察，所以列表行自己的点击不会挡住它；
 * 活动窗格切换决定「拿起之后往哪粘」—— 粘贴钮跟着活动窗格走。
 */
private fun Modifier.paneActivate(onActivate: () -> Unit): Modifier =
    this.pointerInput(onActivate) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) onActivate()
            }
        }
    }

/**
 * 单个窗格：路径栏（← ↑ → + 面包屑 + 只读徽标）+ 可下拉刷新的列表。
 *
 * - **不共用顶栏路径**，每格自带一条，窄屏也能一眼判断"我在哪"（4.1 形态对齐 MT）；
 * - 退到根时列表末尾追加「⚙ 文件管理设置」行（4.17，**子目录看不到**）；
 * - 状态齐备：加载 / 空目录 / 无权限 / 路径消失，统一空态不白屏（5.4）。
 */
@OptIn(ExperimentalMaterial3Api::class)   // rememberPullToRefreshState / PullToRefreshBox 仍是实验 API
@Composable
fun FileListPane(
    side: Side,
    pane: PaneState,
    isActive: Boolean,
    multiSelect: Boolean,
    onActivate: () -> Unit,
    onUp: () -> Unit,
    onOpen: (FileEntry) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    onToggleSelect: (FileEntry) -> Unit,
    /**
     * 手势多选的一笔（用户反馈④）。
     *
     * **抬手并超过确认距离后**才调用，由 ViewModel 按「锚点交替」的两笔模型处理：
     * 没锚点 → 只加这一条并把它定为锚点；有锚点 → 锚点到落点整段并入，然后清锚
     * （下一笔重新起锚）。拖动过程中只动画面、不改选择 —— 反馈是那一行跟着手指走。
     */
    onSwipeSelect: (fromPath: String, toPath: String) -> Unit,
    /** 长按弹出的上下文菜单目标（4.3）。非空时在对应行上挂一个下拉菜单。 */
    contextMenu: ContextMenuEntry?,
    /** 菜单项动作：`action` 是下面 [ContextAction] 的序号。 */
    onContextAction: (action: ContextAction) -> Unit,
    /**
     * 菜单格的**长按**动作（g4）。
     *
     * 双击/长按在网格里不冲突：短按「复制/剪切」= 直接送到另一窗格；
     * 长按 = 原先那套「拿起 → 走过去再粘」。两套语义都得留着，才能既快又不误送。
     */
    onContextActionLong: (action: ContextAction) -> Unit = onContextAction,
    onDismissContextMenu: () -> Unit,
    onRefresh: () -> Unit,
    onScrollChanged: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    /** f11：另一窗格停在可写归档里 → 菜单把「解压/复制/移动」换成「添加」。 */
    otherArchiveWritable: Boolean = false,
    /**
     * l.txt 第 11 条：屏幕最左侧的边缘热区（px）。
     *
     * 这里起手且判定为**横滑**时，本次手势直接让开 —— 交给 FilesScreen 那层
     * 「右滑开侧边栏」处理。竖滑照常滚动列表，边缘不会变成死区。
     */
    edgeSwipeWidthPx: Float = 0f,
    /**
     * n.txt 第 9 条：是否画「活动窗格」的底部细线。
     *
     * 这条线只在**双窗格**里有意义 —— 单窗格没有「哪一栏是活动的」这个问题，
     * 一条贴在屏幕底边的主题色线只会像一道多余的分隔。由调用方按 `!singlePane` 传入。
     */
    showActiveLine: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 整张列表共用**一个** loader。每行各建一个 = 每行一份内存/磁盘缓存，
    // 同一张缩略图在相邻两行会各回源一次，缓存形同虚设。
    val thumbLoader = remember {
        ImageLoader.Builder(context.applicationContext).build()
    }
    // 按「目录 + 过滤词」重建列表状态：换目录/改过滤必须回到顶部，
    // 切 tab 回来时这两个键都没变、而组合体是新的 → 仍用 pane.scrollIndex 接上原位置（验收 15）
    // 按位置传第一个参数（= firstVisibleItemIndex），不用具名参数 ——
    // foundation 1.9.0 的 LazyListState 构造签名已变，具名写法绑死在旧参数名上。
    val listState = remember(pane.cwd, pane.filter) {
        LazyListState(pane.scrollIndex)
    }
    val refreshState = rememberPullToRefreshState()
    // 用户反馈⑥：顶部那个转圈**只属于下拉手势**。原先写的是 `isRefreshing = pane.loading`，
    // 而 navigateTo、操作完成后的联动刷新都会把 loading 置真 —— 于是「返回父目录」时顶部
    // 也冒出一个下拉刷新的转圈，可那根本不是用户拉出来的刷新，语义是错的。
    // 改成组件本地标记：只有 onRefresh 真被触发才置真，loading 一落就收。
    var pullRefreshing by remember { mutableStateOf(false) }
    // 收起指示器有两条路：正常是等 `loading` 落回 false；但**小目录扫得太快时，
    // loading 的 true 会整个落在两次重组之间**（读不到），只盯 loading 就永远收不掉 ——
    // 那正是用户反馈⑫「刷新动画不关闭」。所以把 pullRefreshing 也挂进 key：
    // 任一变化都重算一次，读到「没在加载」就收，另给 150ms 最短可见时间防止一闪而过。
    LaunchedEffect(pullRefreshing, pane.loading) {
        if (!pullRefreshing) return@LaunchedEffect
        if (!pane.loading) {
            delay(150)
            pullRefreshing = false
        }
    }
    // 用户反馈⑨：换目录不再闪「加载中…」。秒开的目录让提示一闪而过反而像卡顿；
    // 只有加载**确实超过 400ms**（大目录 / 慢存储 / 归档扫包）才报，报的是「正在加载」
    // 而不是故障。计时只跟 loading 走 —— 每次 loading 变 true 都重新等一轮。
    var slowLoad by remember { mutableStateOf(false) }
    LaunchedEffect(pane.loading) {
        if (pane.loading) {
            slowLoad = false
            delay(400)
            slowLoad = true
        } else {
            slowLoad = false
        }
    }
    // 用户反馈⑨：内容切换改成**快速淡入**。原来直接替换，在明暗相近的两个目录之间看起来
    // 就是「闪一下」。alpha 只跟 cwd 走 —— 跟 items 走的话每次过滤/排序都会重播一遍。
    val listAlpha = remember(pane.cwd) { Animatable(0f) }
    LaunchedEffect(pane.cwd) {
        listAlpha.snapTo(0f)
        listAlpha.animateTo(1f, tween(120, easing = LinearEasing))
    }
    // 显式按键记忆：滚动/选中这类无关字段变化不重排列表（大目录下每次滚动都重排会掉帧）
    val visible = remember(
        pane.items, pane.filter, pane.sort,
        pane.showSystemHidden, pane.showManualHidden, pane.manualHidden
    ) { pane.visible() }
    // 手势协程是**长驻**的，闭包里捕到的 visible 只有按键变化时才会更新；
    // 过滤/排序一改就得读到最新的那份，否则区间会落在旧列表的下标上。
    val latestVisible = rememberUpdatedState(visible)
    // j.txt 第 8 条：第二笔起要读到**上一次横滑的终点**（= VM 的 rangeAnchor），
    // 以及当前是否已在多选。pointerInput 是长驻协程，必须走 rememberUpdatedState，
    // 否则闭包里永远是首帧的 null / false。
    val latestAnchor = rememberUpdatedState(pane.rangeAnchor)
    val latestMultiSelect = rememberUpdatedState(multiSelect)
    // 手势多选的橡皮筋反馈（用户反馈④）：被按住的那一行**跟着手指横移**，抬手时
    // 只有位移够 45dp 才算数（冲到 50dp 再弹回），不够就 180ms 归位、选择不变。
    // 选中状态在**抬手那一刻**才落，拖动中只动画面 —— 反馈就是那一行本身在走。
    var swipeRow by remember { mutableStateOf<String?>(null) }
    var swipeOffsetPx by remember { mutableStateOf(0f) }
    // g9：拖动中的**实时区间预览**。手指从哪一行滑到哪一行，其间所有行立刻亮起；
    // 抬手时再由 onSwipeSelect 一次性落进状态。这样「看到勾了哪些」与「最后选中哪些」
    // 永远一致，不会出现手指停在 12 行、亮的却是 5 行那种错位。
    var swipeFromPath by remember { mutableStateOf<String?>(null) }
    var swipePreview by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 0 静止 / 1 冲出 / 2 冲出后回弹 / 3 取消回弹。动画走 LaunchedEffect 而不是塞进手势
    // 协程：动画要 300ms，塞进去会让下面那段 while(true) 拿不到下一次按下，期间的点按
    // 全丢。手势在**确认方向的那一下**把它拨回 0 = 掐掉上一段还在跑的回弹。
    var swipeAnim by remember { mutableStateOf(0) }
    // 橡皮筋的三个刻度。**必须包 with(LocalDensity.current)** —— Dp.toPx() 是 Density 的
    // 成员扩展，组合作用域里没有那个接收者（能直接 toPx() 的只有 drawBehind / pointerInput
    // 这类自己带 Density 的作用域），裸调在编译期就断。
    val swipeRefPx = with(LocalDensity.current) { 60.dp.toPx() }
    val swipeConfirmPx = with(LocalDensity.current) { 45.dp.toPx() }
    // 回弹只冲过确认线一点点（45 → 50dp）。原来给 69dp，整行横着窜出去一大截，
    // 在双窗格里看着就是「弹出格子了」（用户反馈 c1：弹跳不要过于明显）。
    val swipeOvershootPx = with(LocalDensity.current) { 50.dp.toPx() }
    // 快速滚动条热区（与下方 SCROLLBAR_HIT 同源）。手势按下点落在最右侧这一条时，
    // 竖向拖动交给滚动条；横向仍然按侧滑多选处理 —— 从行尾起手也不该丢手势。
    val scrollbarHitPx = with(LocalDensity.current) { SCROLLBAR_HIT.toPx() }
    // D6：清「手势画面」的唯一入口。以前清理只写在回弹动画的结束分支里 ——
    // 新一笔开始时 `swipeAnim = 0` 会掐掉旧动画，清理就永远不执行；按在 ".." 上时
    // 旧行会永久停在半位移处，旧 from 还会被下一笔复用。现在三条路径（开始/取消/
    // 换目录）都显式调它，动画只负责把 offset 归零。
    fun clearSwipeVisuals() {
        swipeRow = null
        swipeFromPath = null
        swipePreview = emptySet()
        swipeOffsetPx = 0f
    }
    LaunchedEffect(pane.cwd, pane.filter) {
        swipeAnim = 0
        clearSwipeVisuals()
    }
    LaunchedEffect(swipeAnim) {
        when (swipeAnim) {
            1 -> {
                val a = Animatable(swipeOffsetPx)
                // D5：冲出方向必须跟手指方向一致。原先写死正数，左滑会先飞到右侧再弹回。
                val target = if (swipeOffsetPx >= 0f) swipeOvershootPx else -swipeOvershootPx
                a.animateTo(target, tween(80)) { swipeOffsetPx = value }
                swipeAnim = 2
            }
            2 -> {
                val a = Animatable(swipeOffsetPx)
                a.animateTo(0f, tween(220)) { swipeOffsetPx = value }
                clearSwipeVisuals()
                swipeAnim = 0
            }
            3 -> {
                val a = Animatable(swipeOffsetPx)
                a.animateTo(0f, tween(180)) { swipeOffsetPx = value }
                clearSwipeVisuals()
                swipeAnim = 0
            }
        }
    }
    // ".." 那条占的位（根目录没有它）：进协程前算好，同样是为了不给 key 增加变数
    val leadOffset = if (pane.canGoUp) 1 else 0
    val density = FilePrefs.rowDensity()
    // 用户反馈⑬：文件名折行开关。与 density 同样是**每次组合现读** —— 设置页改完
    // 回来立刻生效，不必等 applySettings 走一圈状态机。
    val multiLine = FilePrefs.multiLineNames()
    val errorRes = pane.errorRes
    // 归档索引进度（4.15：7z/tar 没中央索引，得整包扫一遍）。提成局部量才能 smart cast
    val indexing = pane.indexingCount
    // 行里要用的那几个窗格字段**先取出来再进 items 闭包**（e12 滑动卡顿的根因之二）：
    // `PaneState` 含 List/Map，Compose 判它为不稳定类型，闭包里捕获的 `pane` 只能按
    // **引用**比 —— 引用一变（哪怕只是 loading/indexingCount 这种跟行毫不相干的字段动了），
    // 整批可见行就全部重来一遍。换成下面这几个值之后，它们该不变时纹丝不动，
    // 行闭包被 memo 住，只有真正变了的那一项才重画。
    val selection = pane.selection
    // g9/D1：手势协程是**长驻**的（pointerInput 只按 leadOffset 重启），块里直接捕获的
    // `selection` / `listState` 会永远停在协程启动那一刻：
    //  · 上一笔改过选择 → 下一笔预览仍用旧选中集，抬手后却按新集落选；
    //  · 换目录/改过滤时下面 `remember(pane.cwd, pane.filter)` 会新建 LazyListState，
    //    旧对象保留着上一次测量的 offset/size，y→行号整体错位。
    // 两者都包成 rememberUpdatedState，手势里一律读 .value。
    val latestSelection = rememberUpdatedState(selection)
    val latestListState = rememberUpdatedState(listState)
    val highlightPath = pane.highlightPath
    val rowReadOnly = pane.readOnly
    val manualHidden = pane.manualHidden
    // 首个可见项**变化时**才回写（不是每像素）。`setScroll` 只写 VM 里一个 IntArray
    // （不碰 _state，见 e12），开销极小 —— 这里**不能**加 debounce：快速滑动后立刻
    // 点进目录/返回时，待写入的最后一个下标会被取消，回来就跳到旧位置。
    val scrollSink = rememberUpdatedState(onScrollChanged)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> scrollSink.value(index) }
    }
    // 落点滚动（4.5）：新建/重命名/压缩后把新条目**滚到可视处**，不然高亮给了却看不见等于没给。
    //
    // g17：键里必须带 `loading`。压缩是在**操作开始前**就把 highlightPath 落进状态的，
    // 那一刻列表里还没有新包；只用 highlightPath 当键的话，effect 跑一次找不到就结束，
    // 等刷新回来（loading true → false）也不会再跑 —— 表现就是「已定位没反应」。
    // 加上 loading 之后，列表落地那次状态更新会再触发一遍，正好在条目已经存在时滚过去。
    LaunchedEffect(pane.highlightPath, pane.loading) {
        val target = pane.highlightPath ?: return@LaunchedEffect
        if (pane.loading) return@LaunchedEffect
        val index = visible.items.indexOfFirst { it.path == target }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    // 用户反馈 c11：返回父目录 / 后退 / 前进要把**上次的位置**接回来（上次屏幕中间是 A，
    // 这次回来中间还得是 A）。光给 `LazyListState(pane.scrollIndex)` 一个初值不够 ——
    // navigateTo 会先把 items 清空，列表先以**空表**量一次，那次量完下标很可能就被夹回 0，
    // 等 load 回来再量已经晚了。所以等 loading 落地后，按 scrolls 里记的那份**再拉一次**。
    // 每个目录只拉一次（restorePending），否则每次刷新都会把用户后来的滚动顶掉。
    var restorePending by remember(pane.cwd, pane.filter) { mutableStateOf(true) }
    LaunchedEffect(pane.loading, pane.cwd) {
        if (!restorePending || pane.loading) return@LaunchedEffect
        restorePending = false
        val target = pane.scrolls[pane.cwd] ?: 0
        if (target > 0 && listState.firstVisibleItemIndex < target) {
            listState.scrollToItem(target)
        }
    }

    // 落点脉冲（4.16 的「主题色描边」）：全表**只跑这一条**无限动画，再由每行按自己的
    // highlight 取舍（0 = 不画）。给每行各开一条的话，可见十几行就是十几个永不停歇的动画，
    // 白烧 CPU 与重组，而真正需要脉冲的永远只有一行。
    val highlightPulse by rememberInfiniteTransition(label = "highlightPulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "highlightPulseAlpha"
    )

    // 用户反馈⑦：当前窗格改用**底部一条 1dp 细线**标识。原来的 2dp 整圈描边在双窗格里
    // 既吃掉内边距又抢注意力；一条线足够认出「我现在在哪一格」。颜色要先在组合里取出 ——
    // drawBehind 不是组合作用域，读不到 MaterialTheme。
    val activeLineColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxSize()
            .paneActivate(onActivate)
            .drawBehind {
                if (isActive && showActiveLine) {
                    val w = 1.dp.toPx()
                    // 压在最底缘中线上，线宽一半在内一半在外，视觉厚度正好 1dp
                    val y = size.height - w / 2f
                    drawLine(
                        color = activeLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = w
                    )
                }
            }
    ) {
        // ==================== 路径栏 ====================
        // ==================== 「已定位」状态条（4.16） ====================
        // 紧贴路径栏下方：告诉用户「你刚被带到这儿、目标是这个」，而路径栏本身可点
        // （展开面包屑跳父目录）—— 两者是配套的，所以条子只报名字、不塞操作按钮。
        // 消失时机交给状态本身：点其它行由 vm.clearHighlight 清，离开目录由 navigateTo 清。
        if (pane.highlightPath != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    // 只报文件名：整条绝对路径在窄窗格里会被截成「…/x.txt」，等于没报
                    text = locateLabel(pane.highlightPath),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ==================== 目录 / 文件计数（用户反馈⑦：放顶部，不放底部） ====================
        // 紧贴列表上方，仍在 PullToRefreshBox **外面** —— 不占列表滚动区，下拉刷新时也不会跟着走。
        // 数字是**截断前**的（见 VisibleList）：若显示渲染行数，一个 50000 项的目录
        // 永远只报 2000，用户会以为目录就这么点东西。
        // `total > 0` 才出：空目录已有居中空态占位，再补一行 0/0 是纯噪音。
        // j.txt 第 3/7 条：原来的 40dp「只读 / 归档内」整行横幅去掉 —— 普通目录不亮，
        // 亮的时候又只挂一枚小徽标，等于用一整行换两个字。
        // l.txt 第 12 条：徽标从计数行搬走 —— 计数行只留「N 个文件夹 · M 个文件」，
        // 不再被两三个字挤掉尾部；徽标改挂在下方「..」父目录行的右端。
        if (visible.total > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Str.get(R.string.files_count_summary, visible.dirs, visible.files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ==================== 列表 ====================
        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = {
                pullRefreshing = true
                onRefresh()
            },
            state = refreshState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 用户反馈⑨：整块内容挂一个**只跟 cwd 走**的 alpha —— 换目录时 120ms 淡入，
            // 不再「闪一下」。错误 / 索引 / 加载这些提示一起淡，视觉上才是同一张列表在翻页。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = listAlpha.value }
            ) {
            when {
                errorRes != null -> CenteredHint(
                    icon = Icons.Default.Info,
                    text = Str.get(errorRes),
                    sub = Str.get(R.string.files_error_hint)
                )

                // 7z/tar 没有中央索引，大包必须整包扫过才列得出来（4.15「索引中…」）。
                // 只报「加载中…」会让用户对着空列表以为卡死 —— 把已扫条目数报出来。
                pane.loading && pane.items.isEmpty() && indexing != null -> CenteredHint(
                    icon = Icons.Default.Info,
                    text = Str.get(R.string.files_indexing, indexing),
                    sub = null,
                    progress = true
                )

                // 用户反馈⑨：秒开的目录**不报**「加载中…」，只有慢加载（大目录 / 慢存储 /
                // 归档扫包）才报。提示若总是一闪而过，反而让人以为页面在闪屏。
                pane.loading && pane.items.isEmpty() && slowLoad -> CenteredHint(
                    icon = Icons.Default.Info,
                    text = Str.get(R.string.files_loading),
                    sub = null,
                    progress = true
                )

                // 用户反馈⑨：空目录**不给提示** —— 列表本来就是空的，一眼看得出来，
                // 摆一句「此文件夹为空」只是噪音。真出错仍走上一支 errorRes。
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // 橡皮筋把行横着推时**裁在本格内**（用户反馈 c1「不得超出单窗格」）：
                        // 行是整格宽的，右缘必然探出去；不裁的话它会压到隔壁窗格上。
                        // 上下文菜单是独立窗口的 Dialog，不受这次裁剪影响。
                        .clipToBounds()
                        .semantics { selectableGroup() }
                        // 手势多选（用户反馈⑤ / MT 手册「左右滑动任意文件即可进入多选」）：
                        // **任意状态下横向滑动都算数** —— 不要求先处于多选态。
                        //
                        // 判方向是必须的：`detectDragGestures` 会把按下之后的所有拖动都吃掉，
                        // 就再也没法竖向滚列表了。这里先看首个位移是横还是竖 ——
                        // 竖的**不消费**、原样让给列表滚动；横的才进入画区间的手势。
                        //
                        // g9 的模型（**所见即所选**）：这一笔自己带着起点（按下行）和终点
                        // （抬手行），区间实时高亮、抬手一次性并进选择。不再跨笔记锚点 ——
                        // 用户从第 5 行滑到第 12 行，就只该亮 5..12。
                        //
                        // 协程是长驻的：listState / visible / selection 一律走
                        // rememberUpdatedState 的 .value，否则换目录或上一笔之后会读旧值
                        // （D1/D2 的错位根因）。
                        .pointerInput(leadOffset) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var targetEntry: FileEntry? = null
                                    var finalOffset = 0f
                                    // 0 = 未定 / 1 = 侧滑多选 / 2 = 快速滚动条 / 3 = 交给列表竖滚
                                    var mode = 0
                                    var lastY = down.position.y
                                    // 滚动条模式是否已经对齐过起点（见 mode==2）
                                    var scrollbarReady = false
                                    val inScrollbar = down.position.x >= size.width - scrollbarHitPx
                                    while (true) {
                                        // 事件本身要留住：抬手/被父级掐掉写在 `PointerEvent.type` 上，
                                        // 而 `PointerInputChange.type` 是 `PointerType`（DOWN/MOVE/…），
                                        // 拿它去比 `PointerEventType` 是类型不匹配，编译不过。
                                        val event = awaitPointerEvent()
                                        val change = event.changes
                                            .firstOrNull { it.id == down.id }
                                        if (change == null) {
                                            // 指针从事件流里消失（少见，不等于抬手）：
                                            // 不当成功，直接把那一行弹回去
                                            if (targetEntry != null) swipeAnim = 3
                                            break
                                        }
                                        // D4：系统取消（下拉通知栏、来电、旋转、系统手势抢占）
                                        // 会被合成成「pressed=false 且已被消费」的事件，其
                                        // `PointerEvent.type` 仍然是 Release —— 光看 type 会把取消
                                        // 当抬手提交。必须在**自己 consume 之前**记下这个标记。
                                        val systemCancelled = change.isConsumed
                                        if (mode == 0) {
                                            val dx =
                                                abs(change.position.x - down.position.x)
                                            val dy =
                                                abs(change.position.y - down.position.y)
                                            if (dx > viewConfiguration.touchSlop ||
                                                dy > viewConfiguration.touchSlop
                                            ) {
                                                // l.txt 第 11 条：最左侧热区里的**横滑**归
                                                // 「右滑开侧边栏」，本窗格不参与侧滑多选。
                                                // 竖滑不受影响，继续走下面的列表滚动分支。
                                                if (dx > dy && edgeSwipeWidthPx > 0f &&
                                                    down.position.x < edgeSwipeWidthPx
                                                ) {
                                                    break
                                                }
                                                mode = when {
                                                    // 横向优先：即使起手点在右侧滚动条热区，
                                                    // 横滑也必须走侧滑多选，不能被滚动条吃掉。
                                                    dx > dy -> 1
                                                    // 右侧热区里的竖拖 = 拖快速滚动条。
                                                    inScrollbar -> 2
                                                    // 其余竖向手势：撒手不管，列表照常滚。
                                                    else -> 3
                                                }
                                                if (mode == 3) break
                                                if (mode == 1) {
                                                    // 掐掉上一段还在跑的回弹。**放在定位之前、且不带条件** ——
                                                    // 原先只在「定位到行」时才拨 0，定位不到（列表正在重排）
                                                    // 就会让上一段动画继续跑，收尾时把 swipeRow 清成 null，
                                                    // 这一笔的高亮就闪一下没了，看着像「选到别处去了」（e27）。
                                                    swipeAnim = 0
                                                    // D6：swipeAnim=0 已经掐掉了旧的收尾分支，这里必须
                                                    // 自己把旧位移/旧预览清掉，否则旧行会停住不动。
                                                    clearSwipeVisuals()
                                                    // 被拖的行先取**按下时**手指压着的那条
                                                    // （顶部那条 ".." 不在 visible.items 里，
                                                    // entryAtY 天然返回 null，不参与手势）；
                                                    // 拖动过程中会**跟着手指换行**，见下面 mode==1 那段。
                                                    targetEntry = entryAtY(
                                                        latestListState.value, latestVisible,
                                                        down.position.y, leadOffset
                                                    )
                                                    // val 副本：targetEntry 是循环里的 var，
                                                    // 拿 val 才谈得上智能转换，不会被
                                                    // 「被闭包改过」这条规则挡住
                                                    val dragged = targetEntry
                                                    if (dragged != null) {
                                                        swipeRow = dragged.path
                                                        // i.txt 第 7 条→j.txt 第 8 条：手势规则改为**跨笔锚点**。
                                                        // 第一笔：从按下行选到抬手行；第二笔起：从上一笔的终点
                                                        // （rangeAnchor）选到本次抬手行。这样 A 滑一下、再滑 B，
                                                        // 得到的就是 A..B 整段，而不是只剩 B。
                                                        // 单击仍走 toggleSelect，只改单条、不动锚点。
                                                        val base = if (latestMultiSelect.value) {
                                                            latestAnchor.value
                                                        } else null
                                                        val startPath = base ?: dragged.path
                                                        swipeFromPath = startPath
                                                        swipePreview =
                                                            latestSelection.value + startPath
                                                    }
                                                }
                                            }
                                        }
                                        if (mode == 2) {
                                            // 快速滚动条：只吃竖向位移。每帧现算换算比例，
                                            // 换目录/可见项数变化都能跟上（旧实现靠 key=total
                                            // 的 pointerInput，拖动中变化会读到旧值）。
                                            // 进入这一模式的当帧 `lastY` 还停在按下点 ——
                                            // 直接相减会把「越过 touchSlop 的那段」一次性补上，
                                            // 手指刚一动列表就跳一截。先对齐再看位移。
                                            if (!scrollbarReady) {
                                                scrollbarReady = true
                                                lastY = change.position.y
                                            }
                                            val dy = change.position.y - lastY
                                            lastY = change.position.y
                                            change.consume()
                                            val ls = latestListState.value
                                            val scale = scrollbarDragScale(ls)
                                            if (scale > 0f) ls.dispatchRawDelta(dy * scale)
                                        }
                                        if (mode == 1) {
                                            change.consume()
                                            // **60dp 封顶**：双窗格下单格只有约 170dp，
                                            // 行是整格宽的，位移一大右半截就压到隔壁格子上去了。
                                            // 原先开方阻尼仍会随距离继续往外走（拖 240dp 能到 112dp），
                                            // 等于没封顶 —— 用户反馈 c1「不得超出单窗格」。
                                            finalOffset = (change.position.x - down.position.x)
                                                .coerceIn(-swipeRefPx, swipeRefPx)
                                            // e27「有时侧滑多选，选中的内容错位」：被拖的行**跟着手指走**。
                                            //
                                            // 原先整段手势只在**确认方向的那一下**定位一次、之后就钉死在
                                            // 按下那一行 —— 于是从第 5 行按下、斜着拖到第 12 行松手，
                                            // 选中的却是第 5 行，手指明明停在 12 上，「错位」就是这么来的。
                                            // 纯横向滑动（绝大多数情况）两行本来相同，行为完全不变；
                                            // 只有斜着拖才有差别，而那正是出问题的场景。
                                            // 拖出列表外由 entryAtY 钳到首/末可见行，不会中途断掉。
                                            val moved = entryAtY(
                                                latestListState.value, latestVisible,
                                                change.position.y, leadOffset
                                            )
                                            if (moved != null) {
                                                targetEntry = moved
                                                // 高亮跟着换行：橡皮筋那一行就是「我这一笔落在哪一行」
                                                swipeRow = moved.path
                                                // D3：从顶部 ".." / 底部页脚起手时，方向确认
                                                // 那一下解析不到文件行；把**第一个能解析成文件的行**
                                                // 定为起点，后续预览/落选才有 from。
                                                if (swipeFromPath == null) {
                                                    swipeFromPath = moved.path
                                                    swipePreview = latestSelection.value + moved.path
                                                }
                                                // 实时区间：起点 → 当前行整段亮起（并入已有选择）。
                                                // D1：必须读 State 里的**当前**列表与选中集 ——
                                                // 捕获值在换目录/上一笔之后已经过期，会亮错行。
                                                val from = swipeFromPath
                                                if (from != null) {
                                                    val items = latestVisible.value.items
                                                    val a = items.indexOfFirst { it.path == from }
                                                    val b = items.indexOfFirst { it.path == moved.path }
                                                    if (a >= 0 && b >= 0) {
                                                        swipePreview = latestSelection.value + items
                                                            .subList(minOf(a, b), maxOf(a, b) + 1)
                                                            .map { it.path }
                                                    }
                                                }
                                            }
                                            if (targetEntry != null) {
                                                swipeOffsetPx = finalOffset
                                            }
                                        }
                                        if (!change.pressed) {
                                            // 只有**正常抬手**且超过确认距离才算数；
                                            // 事件被父级掐掉（CANCEL）一律当作取消。
                                            // 判 Release 必须看 type —— `changedToUp()`
                                            // 会因为上面 consume 过而恒为 false，光看 pressed
                                            // 又分不出抬手和取消（D4：取消靠 systemCancelled）。
                                            val from = swipeFromPath
                                            val hit = mode == 1 &&
                                                !systemCancelled &&
                                                from != null &&
                                                targetEntry != null &&
                                                event.type == PointerEventType.Release &&
                                                abs(finalOffset) >= swipeConfirmPx
                                            if (hit) {
                                                onSwipeSelect(from!!, targetEntry!!.path)
                                                swipeAnim = 1   // 冲出 → 回弹
                                            } else {
                                                swipeAnim = 3   // 直接回弹
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // 用户反馈⑥：列表最上方固定一行「..」= 返回父目录。作为**首条 item**
                    // 而不是画在列表外 —— 这样它跟着列表一起滚，滚动到顶才出现，
                    // 平时不占一条固定高度。canGoUp 为假（根目录）时整行不渲染。
                    if (pane.canGoUp || pane.archive != null || pane.readOnly) {
                        item(key = "_parent") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (pane.canGoUp) 52.dp else 32.dp)
                                    // k.txt 第 6 条：父目录行同样圆角裁切涟漪。
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (pane.canGoUp) {
                                            Modifier.clickable(onClick = onUp)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pane.canGoUp) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "..",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                // l.txt 第 12 条：归档内 / 只读徽标挂到「..」行右端 ——
                                // 计数行只报数字，谁也不挡谁。
                                Spacer(modifier = Modifier.weight(1f))
                                if (pane.archive != null) {
                                    UnifiedStatusChip(
                                        label = Str.get(R.string.files_archive_in_badge),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else if (pane.readOnly) {
                                    UnifiedStatusChip(
                                        label = Str.get(R.string.files_read_only),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    items(
                        visible.items,
                        key = { it.path },
                        // j.txt 第 11 条：同一类行共用一种组合布局，滚动时回收更彻底
                        //（文件名折行会让行高不同，但内容结构一致，可安全复用）。
                        contentType = { "file-row" }
                    ) { entry ->
                        Box(
                            // 橡皮筋：被拖那一行跟着手指横移。只读 swipeRow/swipeOffsetPx，
                            // 非被拖行恒为 0，视觉上等于没加修饰。
                            // i.txt 第 7 条：再加一点「拿起来」的手感 —— 位移越大，
                            // 被拖行放大 2% 并稍微提亮，扫过时能看出是哪一条在跟着走。
                            modifier = Modifier
                                .offset {
                                    val x = if (entry.path == swipeRow) {
                                        swipeOffsetPx.roundToInt()
                                    } else 0
                                    IntOffset(x, 0)
                                }
                                .graphicsLayer {
                                    val p = if (entry.path == swipeRow) {
                                        (abs(swipeOffsetPx) / swipeRefPx).coerceIn(0f, 1f)
                                    } else 0f
                                    val s = 1f + 0.02f * p
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            FileRow(
                                entry = entry,
                                // 勾的可见性跟着「多选态」走：退出多选只清活动栏，
                                // 另一栏的勾是**保留**的，但不在多选态里显示 ——
                                // 否则就是「有高亮、没操作栏」的半截状态
                                // g9：多选态已选项 + 拖动中的实时区间，两者都亮
                                selected = (multiSelect && entry.path in selection) ||
                                    entry.path in swipePreview,
                                multiSelect = multiSelect,
                                density = density,
                                thumbLoader = thumbLoader,
                                onClick = {
                                    if (multiSelect) onToggleSelect(entry) else onOpen(entry)
                                },
                                onLongClick = { onLongPress(entry) },
                                // 落点高亮（4.5）：新建/重命名成功后新位置一次性标出来
                                highlight = highlightPath == entry.path,
                                // 4.16：落点额外带主题色描边脉冲；非落点行拿 0，等于不画。
                                // **必须写成条件读**：`highlightPulse` 来自那条永不停歇的无限动画，
                                // 每一帧都在改值。无条件读它 = 每个可见行的组合范围每帧都被它打醒，
                                // 整列常驻 60fps 重组 —— e12「滑动卡顿」的头号来源。
                                // 条件分支只在**真正高亮的那一行**才会去取值；平时全表一次都不读。
                                highlightPulse = if (highlightPath == entry.path) highlightPulse else 0f,
                                // 用户反馈⑬：文件名折行（关 = 单行省略号）
                                multiLine = multiLine
                            )
                            // 上下文菜单（4.3）：挂在**长按的那一行**上，不是全屏弹窗 ——
                            // 用户的手指就在那儿，菜单从那儿长出来最省移动距离。
                            ContextMenuFor(
                                entry = entry,
                                contextMenu = contextMenu,
                                readOnly = rowReadOnly,
                                // 「隐藏」还是「取消隐藏」看长按的这一条 —— 菜单文案与
                                // FilesScreen 侧的落子判定用的是同一个 cm.path，两边不会各说各话
                                hidden = entry.path in manualHidden,
                                addToArchive = otherArchiveWritable,
                                onAction = onContextAction,
                                onActionLong = onContextActionLong,
                                onDismiss = onDismissContextMenu
                            )
                        }
                    }
                    if (visible.truncated) {
                        item(key = "_truncated") {
                            Text(
                                text = Str.get(R.string.files_truncated, visible.total),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                    // 用户反馈㉔：根目录（= 默认起始目录）列表**末尾不再摆「文件管理设置」**。
                    // 那条路本来就有两处更合适的位置 —— 侧栏底部与页头 ⋮ 菜单，
                    // 摆在文件列表里既挤占首屏，又会让人以为它是「这个目录的设置」。
                    // `onOpenSettings` 参数因此暂时没有调用点，先留着（改参数要连动
                    // PaneBox 与三处调用，收益不抵风险）。
                }
            }
            // 快速滚动条。画在 LazyColumn **之后** —— Box 里后画的压在上层，
            // 所以它是悬浮在列表右侧的覆盖物，不占布局宽度：双栏每栏只有约 180dp，
            // 让出 6dp 给滚动条会把本就紧张的文件名挤得更窄。
            FastScrollBar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            }   // ← 淡入 Box 收口（与上方 Box( 对齐）
        }
    }
}

/** 滑块最小高度。再短就按不中了 —— 十万项的目录里按比例算出来只有几个像素。 */
private val MIN_THUMB = 40.dp

/**
 * 滑块（**视觉**）宽度。i.txt 第 11 条：6dp → 8dp —— 双栏每栏只有约 180dp，
 * 6dp 的细条在窄窗格里仍然难按；8dp 仍不挡文件名，但拇指能看清也压得住。
 */
private val THUMB_WIDTH = 10.dp

/**
 * 滑块的**触摸热区**宽度，居中套在视觉条外面。
 *
 * i.txt 第 11 条：12dp → 20dp。12dp 在 411dp 屏上只有约 3% 宽，手指一偏就脱靶；
 * 20dp 仍在「不误触行」的范围内（行尾本来就没有可点控件），换来的是真能一次按住。
 */
private val SCROLLBAR_HIT = 20.dp

/**
 * 快速滚动条的拖动换算：手指移动 1px，列表滚多少 px。
 *
 * 与 [FastScrollBar] 的几何同源（整列高度 ≈ 视口 × 总项 / 可见项；轨道 = 视口 − 滑块高），
 * 但**每次拖动现算**：换目录、可见项数变化都不会读到旧值。手势统一走列表自己的
 * pointerInput，滚动条本身不再挂 pointerInput —— 否则它会成为右缘的最上层命中节点，
 * 把从行尾起手的横滑多选整个吃掉（连点击也到不了行）。
 */
private fun scrollbarDragScale(state: LazyListState): Float {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    val total = info.totalItemsCount
    if (visible.isEmpty() || total <= 1) return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val shown = (visible.last().index - visible.first().index + 1).coerceAtLeast(1)
    if (viewport <= 0f || total <= shown) return 0f
    // 手指在轨道上挪 1px，列表应滚 `可滚范围 / 轨道长`：
    //   可滚范围 = content - viewport = viewport * (total - shown) / shown
    //   轨道长  = viewport - thumb   = viewport * (total - shown) / total
    // 两者相除正好是 total / shown。原先算的是 content / track，等于多乘了
    // total/(total-shown) —— 越接近列表末尾拖得越快，最后一段直接飞出去。
    return total.toFloat() / shown
}

/** 滑块一帧要用的几何量（i.txt 第 11 条：收进 derivedStateOf，避免整条每帧重组）。 */
private data class BarGeometry(val thumbH: Float, val thumbTop: Float)

/**
 * 右侧快速滚动条。
 *
 * ## 为什么是覆盖物而不是伴生布局
 *
 * 双栏布局下每栏只有约 180dp，文件名本来就顶着边在排。把滚动条做成布局的一部分
 * （`Row { List; Thumb }`）会让每一行都窄掉一截，代价远大于收益。
 * 悬浮在右边缘、半透明，不参与测量，视觉上也不抢文件名。
 *
 * ## 几何怎么算
 *
 * - 滑块高度按 **可见项数 / 总项数** 的比例给（`viewportPx * shown / total`），
 *   再夹到 `[MIN_THUMB, viewportPx]` —— 不夹下限的话，十万项的目录里滑块只有几个像素，
 *   根本按不中。
 * - 滑块位置用 `firstIndex / (total - shown)` 归一化：分母是**最大可滚动下标**，
 *   这样滚到底时滑块正好贴在轨道末端，而不是差一截。
 * - 拖动换算用 [androidx.compose.runtime.LazyListState.dispatchRawDelta]（**非挂起**），
 *   拖动期间每帧直接推一发位移；走 `scrollToItem` 的话每次拖动都要起协程，
 *   快速拖动会堆积协程、松手后列表还在追。
 *
 * ## 为什么只在一屏装不下时才画
 *
 * `total <= shown` 时轨道长度为 0，画出来的滑块等于整条轨道 —— 纯装饰，
 * 还会让人以为列表可以拖。
 */
@Composable
fun FastScrollBar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    /**
     * 自己接管拖动/点按手势。
     *
     * 文件列表**不能**开：它的 pointerInput 挂在列表上，右缘要同时兼容横滑多选。
     * 搜索结果、文本对比这两处没有横滑手势，滑条却只是画上去的（h.txt 第 12、13 条
     * 「滑动条拉不动」），由这里补上。
     */
    interactive: Boolean = false
) {
    val density = LocalDensity.current
    val minThumbPx = with(density) { MIN_THUMB.toPx() }

    // i.txt 第 11 条：几何量收进 derivedStateOf —— 原先每次滚动都会让整根滚动条重组，
    // 大目录快速拖动时这一根条本身就成了负担。现在只有「跨行 / 视口变化」才触发重组，
    // 同一行内的像素级滚动不再重画它。
    val geometry by remember(state) {
        derivedStateOf {
            val layout = state.layoutInfo
            val visibleItems = layout.visibleItemsInfo
            val total = layout.totalItemsCount
            val viewportPx = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
            val firstIndex = visibleItems.firstOrNull()?.index ?: 0
            val lastIndex = visibleItems.lastOrNull()?.index ?: 0
            // LazyColumn 的可见下标是连续的，末减首加一就是可见项数
            val shown = (lastIndex - firstIndex + 1).coerceAtLeast(1)
            // j.txt 第 12 条：内容少时滑条会闪一下。根因是「可见项数」在首帧
            // 还没量全时偏小（总数 > 已量到的可见数），于是画出一条满高度的滑块，
            // 下一帧量全后又消失。`canScroll*` 才是「真的能滚」的直接答案：
            // 两个都为 false 就说明一屏装得下，直接不画。
            if (visibleItems.isEmpty() || total <= shown || viewportPx <= 0f ||
                (!state.canScrollForward && !state.canScrollBackward)
            ) {
                null
            } else {
                val thumbH = (viewportPx * shown / total)
                    .coerceAtLeast(minThumbPx)
                    .coerceAtMost(viewportPx)
                val trackPx = (viewportPx - thumbH).coerceAtLeast(1f)
                BarGeometry(
                    thumbH = thumbH,
                    // 分母是最大可滚动下标：滚到底时 thumb 正好落在轨道末端
                    thumbTop = firstIndex.toFloat() / (total - shown).coerceAtLeast(1) * trackPx
                )
            }
        }
    }

    val g = geometry
    // j.txt 第 12 条：再加一道「延迟显示」——首帧的布局信息可能只是暂态（可见项
    // 还没量全），等 140ms 仍是可滚状态才真正画出来。内容确实不够一屏时永远不出现。
    var barVisible by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state, g != null) {
        if (g == null) {
            barVisible = false
        } else {
            delay(140)
            if (geometry != null) barVisible = true
        }
    }
    if (g == null || !barVisible) return
    run {
        val thumbH = g.thumbH
        val thumbTop = g.thumbTop

        // 整列高度/拖动换算的比例不再预先算：手势统一由列表的 pointerInput 处理；
        // 搜索结果这类没有横滑语义的页面在上面补拖动，按触点比例绝对定位。

        val scope = rememberCoroutineScope()
        // n.txt 第 15 条（参照代码编辑器搜索）：拖动只保留**最后一次**跳转。
        // 旧实现拖一下会给每个拖动事件各起一条 scrollToItem 协程，结果列表同时还在
        // 按 120ms 持续追加，队列里堆满的跳转松手后还在跑 —— 看起来就是「拖滚动条卡死」。
        // 现在目标下标没变直接跳过（普通数组，不进快照状态，不触发整条重组），
        // 变了先取消上一个未完成的跳转再发新的。
        val jumpJobRef = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
        val lastJumpIndex = remember { intArrayOf(-1) }

        /** 触点纵向比例 → 目标下标；绝对定位，与列表当前内容量无关地稳定。 */
        fun jumpToFraction(raw: Float) {
            val total = state.layoutInfo.totalItemsCount
            if (total <= 0) return
            val index = (raw.coerceIn(0f, 1f) * (total - 1)).toInt().coerceIn(0, total - 1)
            if (index == lastJumpIndex[0]) return
            lastJumpIndex[0] = index
            jumpJobRef[0]?.cancel()
            jumpJobRef[0] = scope.launch { state.scrollToItem(index) }
        }

        Box(
            modifier = modifier
                .padding(horizontal = 2.dp)
                .fillMaxHeight()
                // 默认只做视觉占位：文件列表的右缘手势挂在列表自己身上，这里再挂一层
                // 会成为最上层命中节点，把横滑多选整个吃掉。interactive=true 的页面
                // （搜索结果 / 文本对比）没有横滑语义，才在这里补拖动与点按。
                .width(SCROLLBAR_HIT)
                .then(
                    if (interactive) {
                        Modifier
                            .pointerInput(state) {
                                detectDragGestures(
                                    onDragStart = { at ->
                                        jumpToFraction(at.y / size.height.toFloat())
                                    },
                                    onDragEnd = { lastJumpIndex[0] = -1 },
                                    onDragCancel = { lastJumpIndex[0] = -1 }
                                ) { change, _ ->
                                    change.consume()
                                    jumpToFraction(change.position.y / size.height.toFloat())
                                }
                            }
                            .pointerInput(state) {
                                detectTapGestures { at ->
                                    jumpToFraction(at.y / size.height.toFloat())
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    // 视觉条只占 THUMB_WIDTH，在 SCROLLBAR_HIT 热区里水平居中。
                    // 这层是 **BoxScope**，能给的是 `Alignment` 而不是 `Alignment.Horizontal`
                    // （`CenterHorizontally` 属于 ColumnScope），所以取 TopCenter ——
                    // 垂直位置本来就由下面那句 offset 决定，交出去反而会叠两次。
                    .align(Alignment.TopCenter)
                    .width(THUMB_WIDTH)
                    .height(with(density) { thumbH.toDp() })
                    .offset(y = with(density) { thumbTop.toDp() })
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(THUMB_WIDTH / 2)
                    )
            )
        }
    }
}

/** 统一的居中空态（加载/空目录/无权限/路径消失），零硬编码颜色。 */
@Composable
private fun CenteredHint(
    icon: ImageVector,
    text: String,
    sub: String?,
    /**
     * 用户反馈 d9「补加载动画」：**在忙就转起来**。
     *
     * 「加载中 / 索引中」是还在动的状态，摆一个静止的图标看着像已经停那儿了；
     * 只有错误态才配静态图标 —— 它本来就该是「到此为止」的样子。
     */
    progress: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (progress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            if (sub != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * 把**屏幕 y 坐标**换算成可见列表里的下标（区间选择用，4.3）。
 *
 * `listState.layoutInfo.visibleItemsInfo` 给的是**已考虑滚动偏移**的相对位置，
 * 所以要先减掉首项的滚动量才和触摸坐标对得上；超出首尾时钳到边界，
 * 手指拖出列表外仍能选到最后一行，而不是突然中断。
 */
private fun indexAtY(listState: LazyListState, y: Float): Int? {
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val offset = info.viewportStartOffset
    val first = visible.first()
    val last = visible.last()
    // 钳到边界：拖出列表上方/下方仍选到首/末行
    if (y <= first.offset - offset) return first.index
    if (y >= last.offset + last.size - offset) return last.index
    for (item in visible) {
        val top = item.offset - offset
        if (y >= top && y < top + item.size) return item.index
    }
    return null
}

/**
 * 只读位置不该摆出来的动作：MOVE 要删源、DELETE/RENAME 要改目录或包。
 * **COPY 是纯读，仍然给** —— 从只读位置复制出来是正当操作。
 */
private val WRITE_ONLY_ACTIONS = setOf(
    ContextAction.MOVE,
    ContextAction.DELETE,
    ContextAction.RENAME
)

/**
 * 长按操作弹窗（用户反馈③：**居中、网格排列、格子加大**）。
 *
 * 原来是挂在那一行尾部的窄 `DropdownMenu`：手指按在行上，视线却要横穿整行去逐条扫，
 * 一屏放不下还只能纵向滚。改成居中网格后两列平铺、每格尺寸固定，
 * 眼睛扫一遍就定位得到，拇指也不必再沿列表找行。
 *
 * `BoxScope` 也一并去掉了 —— 下拉菜单需要父作用域做锚点，居中弹窗不需要。
 */
@Composable
private fun ContextMenuFor(
    entry: FileEntry,
    contextMenu: ContextMenuEntry?,
    readOnly: Boolean,
    hidden: Boolean,
    /**
     * 另一窗格正停在**可写归档**里（f11）。
     *
     * true 时「添加」替换掉解压/复制/移动：此刻用户要的就是「放进对面那个包」，
     * 再摆几个绕路的入口只会让人多想一步。
     */
    addToArchive: Boolean,
    onAction: (ContextAction) -> Unit,
    /** 长按格（g4）：目前只有「复制/剪切」有长按语义（= 拿起），其余回落到短按。 */
    onActionLong: (ContextAction) -> Unit,
    onDismiss: () -> Unit
) {
    val expanded = contextMenu != null && contextMenu.path == entry.path
    if (!expanded) return

    val actions = ContextAction.entries.filter { action ->
        // f11：对面是归档时，「添加」替换三动作
        if (addToArchive && (action == ContextAction.EXTRACT ||
                action == ContextAction.COPY || action == ContextAction.MOVE)
        ) return@filter false
        if (!addToArchive && action == ContextAction.ADD) return@filter false
        // 整批作用域下不再给「选择」—— 弹窗本身就是作用于整批的，再点它没有新信息
        if (action == ContextAction.SELECT && contextMenu.batch) return@filter false
        // 对比要**恰好两条**：给一条或三条都是点了才知道不行（4.11「不该让用户试了才知道」）。
        // 还要 batch —— 只长按不在选中集里的那条时，menuTargets() 只回一条，门开了会拿错。
        if (action == ContextAction.CONTRAST &&
            (!contextMenu.batch || contextMenu.selectionSize != 2)
        ) return@filter false
        // 「解压」只对**单条真实归档**给：目录没有包可解；归档内条目是虚拟路径、
        // 拿不到字节（要先把外层整包解出来）；`cpio`/`ar` 认得出但本版不支持 ——
        // 摆上去就是个点了必然报错的按钮。整批时也收起来：解压框一次只吃一个包，
        // 多选态下给它就等于「看着是三个、其实只解了一个」（用户反馈⑪）
        if (action == ContextAction.EXTRACT &&
            (contextMenu.batch ||
                entry.isDir ||
                VirtualPath.isInArchive(entry.path) ||
                !extractableName(entry.name))
        ) return@filter false
        // j.txt 第 10 条：文件夹没有「打开方式」—— 目录能「打开」的只有「进目录」，
        // 摆一格出来只会得到系统文件管理器或一句失败。归档内的目录同理。
        if (action == ContextAction.OPEN_WITH && entry.isDir) return@filter false
        // 「隐藏」对**归档内条目**不给：那是虚拟路径，写进手动隐藏集之后再也对不上 ——
        // 看着是藏住了，其实只是这次过滤碰巧拦到，关掉开关立刻又冒出来
        if (action == ContextAction.HIDE && VirtualPath.isInArchive(entry.path)) return@filter false
        // 「添加书签」（用户反馈 e4）只给**真实目录**：书签是导航目标，文件当不了目录
        // （收进去点开只会是一片空，想收藏文件请收藏它所在的文件夹）；归档内是虚拟路径，
        // 要先出包才跳得过去。整批态也不给 —— 菜单按长按的那条落子，多选时给它语义不明。
        if (action == ContextAction.BOOKMARK &&
            (contextMenu.batch || !entry.isDir || VirtualPath.isInArchive(entry.path))
        ) return@filter false
        // 反馈 e8「复制移动改为解压」：包里的条目**拿不到字节**，要落地只能整条解出来 ——
        // 复制与移动其实走的是同一条解压路，摆两格一模一样的才是误导。收掉「移动」，
        // 把剩下那格改名成「解压」（见下方 extractTile）。删除另走「重新打包」那条。
        if (action == ContextAction.MOVE && VirtualPath.isInArchive(entry.path)) return@filter false
        // 只读位置（权限不足 / 7z·tar 归档）不摆写操作：让用户点了才失败，
        // 不如一开始就不给 —— 4.11/4.15「不该让用户试了才知道」
        !(readOnly && action in WRITE_ONLY_ACTIONS)
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_ctx_title),
        // 用户反馈⑤「文件操作弹窗需要小很多」：原先是 2 列 × 6 行 × 92% 屏宽，
        // 实测高度≈580dp —— 比 360×640 机型的可用屏还高，看着就是一块贴脸的面板。
        //
        // i.txt 第 5 条：改成 **2 列**、每格「图标在左、文字在右」横排 —— 一格只有
        // 一行字高（≈44dp），整块面板因此变成**宽大于高**，横屏/大屏都不用再上下找。
        // 12 项 = 6 行 × 44dp ≈ 280dp，配 92% 屏宽，观感从「竖条」变成「操作板」。
        // 最长标签「打开方式」「添加书签」「压缩到归档」都是 4 个汉字，半屏宽仍放得下。
        widthFraction = 0.92f,
        content = {
            // w.txt 第 8 条（用户）：整个菜单只有**一个**操作时，别再让它横跨整行 ——
            // 那一格会一直顶到内容区右缘，看起来像边框贴着右边。保持普通格宽即可。
            val singleActionMenu = actions.size == 1
            // 两列一行；末尾凑不齐时补等宽占位，网格右缘才不会塌进去
            actions.chunked(CTX_MENU_COLUMNS).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { action ->
                        // 「隐藏」的文案与图标随状态走（同一次重绘里定死，绝不各算各的）：
                        // 藏起来之后菜单必须改说「取消隐藏」，否则那条入口永远找不回来
                        val hiddenTile = action == ContextAction.HIDE && hidden
                        // 反馈 e8：包内的「复制」就是**解压**，文案与图标一起换，
                        // 免得写着「复制」、实际整条解出来
                        val extractTile = action == ContextAction.COPY &&
                            VirtualPath.isInArchive(entry.path)
                        ActionTile(
                            label = if (hiddenTile) Str.get(R.string.files_ctx_unhide)
                            else if (extractTile) Str.get(R.string.files_ctx_extract)
                            else contextActionLabel(action),
                            icon = if (hiddenTile) Icons.Default.Visibility
                            else if (extractTile) Icons.Default.Unarchive
                            else contextActionIcon(action),
                            // j.txt 第 2 条：末行只剩一格时让它横跨整行。
                            // 原先补等宽 Spacer = 给每格一个固定槽位，最后却空出半行；
                            // 现在把空出来的那一半直接并进这格，容器变大且没有废白。
                            //
                            // w.txt 第 8 条：但「整个菜单只有一个操作」不属于「末行补位」，
                            // 横跨整行会让边框贴到最右侧；这一档保持普通格宽。
                            modifier = Modifier.weight(
                                if (row.size == 1 && !singleActionMenu) {
                                    CTX_MENU_COLUMNS.toFloat()
                                } else {
                                    1f
                                }
                            ),
                            onClick = { onAction(action) },
                            onLongClick = { onActionLong(action) }
                        )
                    }
                    if (row.size == 1 && singleActionMenu) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                // 收尾只留一次呼吸位：`row` 是 chunk 出来的**整行**，拿它和单个元素
                // `actions.last()` 比是 List vs ContextAction，永远不相等 ——
                // 结果是每一行下面都多出一条，最后一行也跟着多。
                if (row.last() != actions.last()) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    )
}

/** 文件操作菜单的列数（i.txt 第 5 条：两列横排，整块面板宽大于高）。 */
private const val CTX_MENU_COLUMNS = 2

/**
 * 网格里的一个操作格。
 *
 * i.txt 第 5 条：图标在左、文字在右**横排**，一格只占一行字高 —— 这样两列网格
 * 整块是「宽 > 高」，不会像原先竖排图标那样把弹窗拉成一块竖长条。
 * 文字最多两行也够：4 个汉字的标签在半屏宽里一行就放得下。
 *
 * j.txt 第 2 条：格子本身再放高一点（上下 15dp、图标 24dp、文字 14sp），
 * 末行单格横跨整行 —— 手指按下去的范围更大，又不会出现「有槽位没内容」的废白。
 */
@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val tileShape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            // l.txt 第 15 条：文件操作格在新拟态下浮起（与设置页的卡片同一套光影）；
            // 关闭新拟态时仍是原来那块 50% 的 surfaceVariant 色块。
            .then(
                if (UIConfig.isNeumorphismEnabled()) {
                    Modifier.filesNeuSurface(tileShape, 10.dp)
                } else {
                    Modifier
                        .clip(tileShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                }
            )
            // g4：长按与短按各走一条；没有长按语义的格传 null，行为与原先完全一致。
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // l.txt 第 10 条：文件操作弹窗再收紧一档 —— 纵向 12 → 8dp、图标 26 → 22dp、
            // 横排间距 6 → 5dp，整板高度明显下来（12 项 ≈ 6 行，省 48dp 上下留白）。
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        // k.txt 第 2 条：图标与文字贴近（9 → 6dp），文字吃掉剩余宽度 ——
        // 原先文字不占权重，长标签虽然省略、整格右半边却空着；末行单格横跨整行时
        // 尤其明显。现在内容真正铺满格子，格子高度也略微收紧（15 → 12dp）。
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 把手指所在的 y 换算成**那一条文件**（手势多选用）。
 *
 * [latestVisible] 走 `rememberUpdatedState` 而不是直接捕获 `visible` —— 手势协程活得比
 * 一次组合久，捕获值会在改过滤词之后把旧下标套到新列表上。
 *
 * [leadOffset] 补掉 LazyColumn 里排在文件之前的非文件条目（顶部那条 ".."）：不补则
 * 区间整体**往前错一条**，而且只在非根目录才错 —— 这类「有时候是对的」偏移最难被发现。
 *
 * 换不到就返回 null：手指已经拖到列表外、或这一帧列表还在重排，都属正常，
 * 调用方就此跳过这一帧即可，不该因此中断整段手势。
 */
private fun entryAtY(
    listState: LazyListState,
    latestVisible: State<VisibleList>,
    y: Float,
    leadOffset: Int
): FileEntry? {
    val index = indexAtY(listState, y) ?: return null
    val items = latestVisible.value.items
    val i = index - leadOffset
    // f7：显式边界，不再靠「减掉偏移量正好错开」这条隐含约定 —— 顶部 ".." 行、
    // 底部「已截断」页脚、以及将来可能加进来的分组头/横幅都不在 items 里，
    // 多一种行类型就会整体错一条（还是那种「有时才对」的错）。
    if (i < 0 || i >= items.size) return null
    return items[i]
}
