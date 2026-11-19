// app/src/main/java/com/UIN/Tool/ui/screen/files/FilesScreen.kt
package com.UIN.Tool.ui.screen.files

import com.UIN.Tool.ui.components.unified.UnifiedDropdownMenu
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.unified.AppToastHost
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.NeuDefaults
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.neuChrome
import com.UIN.Tool.ui.components.unified.neuRaised
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedBottomSheet
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedProgressDialog
import com.UIN.Tool.ui.components.unified.UnifiedProgressState
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.components.unified.dialogAutoFocus
import com.UIN.Tool.ui.screen.dev.CodeEditorActivity
import com.UIN.Tool.ui.screen.dev.editor.FileAccess
import com.UIN.Tool.ui.screen.docs.DocViewerActivity
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.gradientBackgroundBrush
import com.UIN.Tool.ui.theme.pageGradientBrush
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.utils.openFile
import com.UIN.Tool.utils.shareFiles
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "FilesScreen"

/** 进多选一次性提示条的停留时长：够读完一句中文，又不至于挡住底栏太久。 */
private const val SELECT_HINT_MS = 4_000L

/**
 * 6.3.0「文件」页：MT 管理器式的**左右双窗格**文件管理器（净室——只复刻参考实现的
 * 用户操作流程，结构自拟、外观与控件全用本仓风格）。
 *
 * - 竖屏固定左右分栏，每窗格**自带路径栏**，分隔条可拖动、双击复位（问题 1 已拍板）；
 * - 任一交互先切活动窗格（高亮 2dp 主题描边），保证「复制 →」方向永远符合直觉（4.13）；
 * - 退到根目录时列表末尾出现「⚙ 文件管理设置」行，点开 [FileSettingsScreen]（4.17）；
 * - 返回键优先级：关过滤条 → 退多选 → 上一级 → 交给外层二次确认退出（4.2）。
 */
@Composable
fun FilesScreen(
    /** 用户反馈⑩：⋮ →「退出」= 退出文件管理器回到主页面（不是退出整个应用）。 */
    onExit: () -> Unit = {}
) {
    // e20 ③：与编辑器侧边栏**共用进程级那一份**（不再走 activity 作用域的 viewModel()）——
    // 两处各持一份会各说各话：这边「拿起」了那边没有粘贴键、一边跑进度另一边把通知收掉。
    val vm: FileManageViewModel = FileManageViewModel.shared()
    val state by vm.state.collectAsState()
    // e12 滚动位的**离场补笔**：滚动期间 `setScroll` 只写 ViewModel 里那个没人订阅的
    // 普通字段（写状态 = 整页 + 所有可见行重组一遍，正是卡顿的来源），所以切 tab /
    // 退出文件页时要一次性把那份实时位置落进状态 —— 回来时 `LazyListState` 是重建的，
    // 初值取的正是它。
    DisposableEffect(Unit) {
        onDispose { vm.flushScrolls() }
    }

    // l.txt 第 3 条：前台期间持续刷新 —— 别的应用改了文件，这里 2.5s 内就能看见；
    // 回到前台先补一次（后台期间不轮询，省电）。
    val lifecycleOwner = LocalLifecycleOwner.current
    var autoRefreshRunning by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    autoRefreshRunning = true
                    vm.autoRefresh()
                }
                Lifecycle.Event.ON_PAUSE -> autoRefreshRunning = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(autoRefreshRunning) {
        if (!autoRefreshRunning) return@LaunchedEffect
        while (true) {
            delay(2_500)
            vm.autoRefresh()
        }
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    /**
     * 进多选的一次性提示条（§4.3「给可发现性兜底」）。
     *
     * 初值看持久化标记：出过一次就永远不再出。用 `rememberSaveable` 而不是 `remember`，
     * 是为了让横竖屏切换不会把它重新点亮 —— 否则「只出一次」会变成「每转一次出一次」。
     */
    var selectHintVisible by rememberSaveable {
        mutableStateOf(!FilePrefs.selectHintShown())
    }
    var paneWeight by rememberSaveable { mutableStateOf(FilePrefs.paneWeight()) }
    // 设置页改完窗格比例要套回来：`rememberSaveable` 的初值只在首次组合跑一次，
    // 而设置页是**整页替换**（关掉才重新组合到列表），所以关回来时再比一次。
    // 只在比例真的变了时才覆盖 —— 否则手动拖出来的分隔条每次逛一趟设置页就被抹平。
    var ratioSeen by remember { mutableStateOf(FilePrefs.paneRatio()) }
    /** 用户反馈 d14：比例三态（可变 / 不可变 / 默认）——只在设置页改，回来时重读。 */
    var ratioMode by remember { mutableStateOf(FilePrefs.paneRatioMode()) }
    LaunchedEffect(showSettings) {
        if (showSettings) return@LaunchedEffect
        val newMode = FilePrefs.paneRatioMode()
        val now = FilePrefs.paneRatio()
        // 换成「不可变 / 默认」时把分隔条按回已存比例 —— 手拖出来的临时值不该赖着不走
        val snap = newMode != ratioMode && newMode != FilePrefs.RATIO_VARIABLE
        ratioMode = newMode
        if (now != ratioSeen || snap) {
            ratioSeen = now
            paneWeight = now / 100f
        }
    }
    var menuOpen by remember { mutableStateOf(false) }

    /** 书签/历史底部面板（§4.9 / 用户反馈①）。入口：**底栏从下往上拉**。 */
    var placesOpen by remember { mutableStateOf(false) }

    /**
     * 左侧**侧边栏**（用户反馈①：侧边栏是侧边栏，底部最近位置/书签是另一个）。
     * 入口：页头 ☰。点遮罩或点任意条目即收起。
     */
    var navOpen by remember { mutableStateOf(false) }

    /** 搜索面板（§4.8，⋮ → 搜索）。 */
    var searchOpen by remember { mutableStateOf(false) }

    // j.txt 第 4 条：过滤条也是「点出来就该能直接打字」的输入框 —— 展开即聚焦并弹键盘。
    val filterFocus = remember { FocusRequester() }
    val filterKeyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.filterBarVisible) {
        if (state.filterBarVisible) {
            delay(120)
            runCatching { filterFocus.requestFocus() }
            filterKeyboard?.show()
        }
    }

    /**
     * e25：**搜索结果**弹窗。与 [searchOpen] **互斥** —— 同一时间只开一个：
     * 点「搜索」时关掉条件弹窗、打开结果弹窗（原话「同时关闭之前的搜索弹窗」）。
     * 关掉它才 `FileSearch.clear()`；只关条件弹窗不清结果，否则「查看 N 条结果」
     * 那个入口点进去会是空的。
     */
    var searchResultsOpen by remember { mutableStateOf(false) }

    /** h.txt 第 12 条：结果弹窗右上角「斜向拉伸」进入的整页结果视图。 */
    var searchResultsPage by remember { mutableStateOf(false) }

    /** 用户反馈⑥：页头路径点一下弹出的「跳转路径」输入框。 */
    var jumpOpen by remember { mutableStateOf(false) }

    /** 用户反馈⑩：⋮ →「排序方式」的网格弹窗（单选 + 两个复选框）。 */
    var sortDialogOpen by remember { mutableStateOf(false) }

    // 用户反馈③：「打开方式」弹窗。存的是**目标文件的绝对路径**（空串 = 关闭）——
    // 弹窗要按后缀算内置能力、按文件查系统应用，路径就是它的全部输入。
    var openWith by rememberSaveable { mutableStateOf("") }
    // 默认打开方式的管理页：与设置页同级的整页替换，所以也用 rememberSaveable
    var openManage by rememberSaveable { mutableStateOf(false) }

    /** 远程管理页（FTP / HTTP）。与设置页、打开方式管理页同一套「整页替换 + 返回收口」。 */
    var showRemote by rememberSaveable { mutableStateOf(false) }

    /** 回收站页：删除的文件先落在这里，可恢复 / 彻底删除 / 清空。 */
    var showTrash by rememberSaveable { mutableStateOf(false) }

    /** g18：后台任务独立页面（侧边栏入口）。整页替换，与回收站/远程页同一套。 */
    var showTasks by rememberSaveable { mutableStateOf(false) }

    // 配置导入会绕过本页直接改 FilePrefs（UIConfigScreen → FilePrefs.importJson），
    // 所以盯住 UIConfig 的配置版本号：一变就把新设置套回两窗格，免得「导入了却没生效」
    val configVersion by UIConfig.configVersion.collectAsState()
    var seenVersion by remember { mutableStateOf(configVersion) }
    LaunchedEffect(configVersion) {
        if (configVersion != seenVersion) {
            seenVersion = configVersion
            vm.applySettings()
        }
    }

    // §4.16：深链 uin://files?path=&file= 到达后跳目录 + 高亮文件。
    // 顺序与搜索结果点选完全一致 —— navigateTo 会在**同一个 copy** 里把 highlightPath
    // 清成 null，所以必须先跳再高亮，反过来调会被立刻抹掉（详见 highlightFromSearch 的 KDoc）。
    val deepLink by FileDeepLinkBus.pending.collectAsState()
    LaunchedEffect(deepLink) {
        val d = deepLink ?: return@LaunchedEffect
        val side = state.active
        val mount = d.mountArchive
        if (mount != null) {
            // i.txt 第 6 条：APK 信息页的「以压缩包查看」—— 直接把这个包挂进活动窗格。
            // `navigateTo` 认 `x.zip!/dir` 这种虚拟路径，包根就是 `join(x, "")`，
            // 与在列表里点包进入走同一条路。
            vm.navigateTo(side, VirtualPath.join(mount, ""))
        } else {
            if (d.dirPath.isNotEmpty()) vm.navigateTo(side, d.dirPath)
            d.filePath?.let { vm.highlightFromSearch(side, it) }
        }
        // 只消费一次：不 consume 会让请求一直挂着，旋转/进程重建后重复跳一遍目录。
        FileDeepLinkBus.consume()
    }

    // 硬件返回键优先级（4.2）：关抽屉 → 关上下文菜单 → 关对话框 → 收过滤 → 退多选 → 上一级
    val anyDialogOpen = state.contextMenu != null ||
        state.deleteRequest != null ||
        state.renameRequest != null ||
        state.batchRename != null ||
        state.createRequest != null ||
        state.compressRequest != null ||
        state.extractRequest != null ||
        state.propertiesPaths.isNotEmpty() ||
        state.op is OpUiState.Conflict

    // 侧栏 / 搜索面板是**自绘覆盖层**（AnimatedVisibility + if），不是 Dialog 窗口 ——
    // 落在它们身上的硬件返回**不会**像弹窗那样自动 `onDismissRequest`，不进这条链的话按返回会
    // 直接跳到「上一级」，把开着的抽屉留在原地（4.2 承诺的「关抽屉」只写了后半截；
    // 用户反馈 b28「类比排查」）。
    // 书签 / 历史（用户反馈 d8）走 UnifiedBottomSheet = ModalBottomSheet = **独立窗口**，
    // 窗口自己其实也接返回；照样排进这条链，为的是三块覆盖层**关的次序一致**，
    // 也免得哪天它改成自绘覆盖层时返回键静默失效。
    val overlayOpen = navOpen || placesOpen || searchOpen || searchResultsOpen

    BackHandler(
        enabled = overlayOpen || state.filterBarVisible || state.multiSelect ||
            state.activePane.canGoUp || anyDialogOpen
    ) {
        when {
            // 覆盖层排最前：它们盖在页面之上，「谁在最上面先关谁」才不违反返回的直觉
            navOpen -> navOpen = false
            placesOpen -> placesOpen = false
            // e25：结果弹窗压在条件弹窗之上（两者互斥），先关它并清结果
            searchResultsOpen -> {
                searchResultsOpen = false
                FileSearch.clear()
            }
            searchOpen -> searchOpen = false
            state.contextMenu != null -> vm.dismissContextMenu()
            state.deleteRequest != null -> vm.dismissDelete()
            state.renameRequest != null -> vm.dismissRename()
            state.batchRename != null -> vm.dismissBatchRename()
            state.createRequest != null -> vm.dismissCreate()
            state.compressRequest != null -> vm.dismissCompress()
            state.extractRequest != null -> vm.dismissExtract()
            state.propertiesPaths.isNotEmpty() -> vm.dismissProperties()
            state.op is OpUiState.Conflict -> vm.resolveConflict(null, false)
            state.filterBarVisible -> vm.toggleFilterBar()
            // 退出多选**只清活动栏**（用户答复：另一栏的勾保留，「焦点在哪就退哪」）
            state.multiSelect -> vm.exitMultiSelectKeepingOtherPane()
            else -> vm.goUp(state.active)
        }
    }

    if (showSettings) {
        // 后注册者优先（LIFO）：设置页开着时，硬件返回 = 关设置，而不是落到上一层的
        // 关过滤 / 退多选 / 上一级
        BackHandler {
            showSettings = false
            // 4.17：设置改动即时生效（根范围/双窗格/排序/隐藏立刻套回两窗格）
            vm.applySettings()
        }
        FileSettingsScreen(onBack = {
            showSettings = false
            vm.applySettings()
        }, onOpenManage = {
            // 设置页只负责入口，真正的管理页与它平级 —— 回来时两层状态都要收干净
            showSettings = false
            openManage = true
        })
        return
    }

    if (openManage) {
        // 默认打开方式管理页（用户反馈③）。与设置页同一套「整页替换 + 返回键收口」。
        BackHandler { openManage = false }
        FileOpenWithManageScreen(onBack = { openManage = false })
        return
    }

    if (showRemote) {
        // 远程管理页。**返回只关页面、不停服务** —— 服务挂在 RemoteManager 单例上，
        // 用户开完服务是要切回列表继续干活的，页面一关就掐 socket 等于功能残废。
        BackHandler { showRemote = false }
        RemoteManageScreen(onBack = { showRemote = false })
        return
    }

    if (showTrash) {
        // 回收站页：与设置/远程同一套整页替换，返回键只关这一页。
        BackHandler { showTrash = false }
        TrashScreen(onBack = { showTrash = false })
        return
    }

    if (showTasks) {
        // g18：后台任务整页。返回只关页面，任务照跑；「查看进度」把进度框唤回。
        BackHandler { showTasks = false }
        FileTasksScreen(
            op = state.op as? OpUiState.Running,
            onBack = { showTasks = false },
            onShow = {
                vm.showOp()
                showTasks = false
            },
            onCancel = { vm.cancelOp() }
        )
        // 任务跑到一半正好撞名 / 要密码时，冲突框仍要压在这一页上 —— 否则
        // 底层停在「等用户回答」而我们停在「看起来还在跑」，只能一直等下去。
        // 进这一页时进度框本来已经收起（否则侧边栏点不到），所以这里只会有冲突类弹窗。
        M3Dialogs(vm = vm, state = state)
        return
    }

    val activePane = state.activePane

    // 页面级 context：底部工具抽屉要拿它起 Intent，而抽屉在主体 Column **之外**渲染 ——
    // 声明放 Column 里的话，抽屉块根本取不到。
    val context = LocalContext.current

    // 用户反馈①：左侧抽屉按「侧边栏」来做 —— 内容与打开方式见下方 `AnimatedVisibility` 块。
    // 这里把主体 Column 包进 Box，好让侧栏覆盖层能压在列表**之上**（覆盖层必须是
    // Column 的兄弟节点才能盖住它，兄弟节点又得有个 Box 才谈得上叠放）。
    // l.txt 第 11 条：屏幕最左侧留一条 20dp 的「边缘热区」，从这里向右滑 = 打开侧边栏。
    // 热区内的横滑由这一层接手（FileListPane 的侧滑多选会主动让开，见其 edgeSwipeWidthPx），
    // 竖滑仍归列表滚动 —— 所以既不会和侧滑多选打架，也不会把最左一条按死。
    val edgeSwipePx = with(LocalDensity.current) { 20.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(navOpen, placesOpen, edgeSwipePx) {
                if (navOpen || placesOpen) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edgeSwipePx) return@awaitEachGesture
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        // 竖滑优先交还给列表，只有明确的右滑才认。
                        if (abs(dy) > viewConfiguration.touchSlop && abs(dy) >= abs(dx)) break
                        if (dx > viewConfiguration.touchSlop && dx > abs(dy)) {
                            change.consume()
                            if (dx > edgeSwipePx * 2) {
                                navOpen = true
                                break
                            }
                        }
                    }
                }
            }
    ) {
    Column(modifier = Modifier.fillMaxSize().background(AppColors.pageBackground())) {
        // ==================== 页头 ====================
        val clipboard = LocalClipboardManager.current
        // k.txt 第 7 条：与开发/管理页统一使用同一套页面顶栏（相同高度、容器色、
        // 动作区与图标样式）；路径仍可点/长按，菜单仍收在右侧。
        ManageTopAppBar(
            titleText = "",
            // MainActivity 的 Scaffold 已让出状态栏，这里不再重复加内边距。
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                // ☰ = 左侧侧边栏（用户反馈①）
                UnifiedIconButton(
                    icon = Icons.Default.Menu,
                    onClick = { navOpen = true },
                    contentDescription = Str.get(R.string.files_sidebar_open),
                )
            },
            titleContent = {
            // 用户反馈⑥：这里**不再写「文件」二字**，直接显示活动窗格的路径。
            // 点 = 弹「跳转路径」输入框；长按 = 复制当前路径。双窗格下跟着活动窗格走 ——
            // 点另一窗格的行会先 setActive，路径随即跟着换。
            Text(
                text = activePane.cwd,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 8.dp, end = 4.dp)
                    // k.txt 第 6 条：顶栏路径的点击涟漪也收进圆角。
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { jumpOpen = true },
                        onLongClick = {
                            clipboard.setText(AnnotatedString(activePane.cwd))
                            AppToast.info(context, Str.get(R.string.files_path_copied))
                        }
                    )
                    .padding(vertical = 6.dp)
            )
            },
            actions = {
            Box {
                UnifiedIconButton(
                    icon = Icons.Default.MoreVert,
                    onClick = { menuOpen = true },
                    contentDescription = Str.get(R.string.files_menu),
                )
                // 用户反馈⑩：菜单固定为「刷新 / 搜索 / 过滤 / 排序方式 / 添加书签 /
                // 设为首页 / 交换窗口 / 设置 / 退出」九项。原来的两个隐藏开关与
                // 「同步到另一窗格」被拿掉 —— 前者挪进设置，后者底栏已有同功能按键。
                UnifiedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_menu_refresh)) },
                        onClick = { menuOpen = false; vm.refresh(state.active) }
                    )
                    // 搜索（§4.8）与「过滤」是两回事：过滤只看当前目录、输入即过滤；
                    // 搜索要亲自递归走盘、可选读内容、结果落常驻面板。
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_search)) },
                        onClick = {
                            menuOpen = false
                            // e25：两个搜索弹窗互斥 —— 开条件弹窗就先收结果弹窗。
                            // **不清结果**：条件弹窗里还留着「查看 N 条结果」这个入口，
                            // 清了它点进去会是空的。
                            searchResultsOpen = false
                            searchOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_menu_filter)) },
                        onClick = { menuOpen = false; vm.toggleFilterBar() }
                    )
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_sort_by)) },
                        onClick = { menuOpen = false; sortDialogOpen = true }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                // 已收藏就写「取消书签」，让菜单自己说明当前状态
                                if (FilePlaces.isBookmarked(activePane.cwd)) {
                                    Str.get(R.string.files_remove_bookmark)
                                } else {
                                    Str.get(R.string.files_add_bookmark)
                                }
                            )
                        },
                        onClick = {
                            menuOpen = false
                            val cwd = activePane.cwd
                            if (FilePlaces.isBookmarked(cwd)) {
                                FilePlaces.removeBookmark(cwd)
                                AppToast.info(context, Str.get(R.string.files_bookmark_removed))
                            } else {
                                FilePlaces.putBookmark(
                                    name = cwd.substringAfterLast('/').ifBlank { cwd },
                                    path = cwd,
                                    group = ""
                                )
                                AppToast.success(context, Str.get(R.string.files_bookmark_added))
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_set_home)) },
                        onClick = {
                            menuOpen = false
                            FilePrefs.setStartDir(activePane.cwd)
                            AppToast.success(context, Str.get(R.string.files_home_set))
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_swap)) },
                        onClick = { menuOpen = false; vm.swapPanes() }
                    )
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_settings)) },
                        onClick = { menuOpen = false; showSettings = true }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(Str.get(R.string.files_exit_manager)) },
                        onClick = { menuOpen = false; onExit() }
                    )
                }
            }
            }
        )

        // ==================== 过滤条（4.8：独立一条，不替换路径行）====================
        if (state.filterBarVisible) {
            UnifiedTextField(
                value = activePane.filter,
                onValueChange = { vm.setFilter(state.active, it) },
                placeholder = Str.get(R.string.files_filter_hint),
                leadingIcon = Icons.Default.Search,
                showClearButton = true,
                onTrailingIconClick = { vm.setFilter(state.active, "") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .focusRequester(filterFocus)
            )
            // 语法另起一行而不是塞进 placeholder：三种写法加起来在窄屏上必然被截断，
            // 而被截断的恰恰是新增的那半截（/re/、!取反）—— 等于白提示。
            UnifiedCaptionText(
                Str.get(R.string.files_filter_syntax),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // ==================== 搜索（用户反馈 d13：独立弹窗，不再占页面顶部）====================
        // 原来是常驻面板压在列表上方 —— 一条查询框 + 选项 + 结果把列表挤掉半屏，
        // 关掉之前翻目录都得隔着它看。收进弹窗后页面顶部再没有搜索框，列表始终满高。
        //
        // e25 再拆一层：**条件**与**结果**分成两个弹窗，点「搜索」时条件弹窗当场关掉、
        // 结果弹窗打开（原话「同时关闭之前的搜索弹窗」）。
        //
        // 状态 / 结果 / 提示收集器**提到 `if` 外面**收：结果弹窗要用它们；提示收集器
        // 更不能挂在 `if (searchOpen)` 里 —— 点搜索就关掉条件弹窗，收集器会跟着一起
        // 取消，`Error` 那种异步提示就永远弹不出来了。
        val searchState by FileSearch.state.collectAsState()
        val searchResults by FileSearch.results.collectAsState()
        val searchScope = rememberCoroutineScope()
        val searchContext = LocalContext.current
        // 一次性提示：Blank / BadRegex / Error。SharedFlow 不重放，所以不会连弹。
        LaunchedEffect(Unit) {
            FileSearch.notices.collect { n ->
                when (n) {
                    FileSearch.Notice.Blank ->
                        AppToast.info(searchContext, Str.get(R.string.files_search_blank))
                    FileSearch.Notice.BadRegex ->
                        AppToast.warning(searchContext, Str.get(R.string.files_search_bad_regex))
                    is FileSearch.Notice.Error ->
                        AppToast.warning(searchContext, Str.get(R.string.files_search_error))
                    // 在包里搜、又勾了「匹配文件内容」—— 包内没有可读字节流，
                    // 只能按名字搜。明说，别让用户以为「搜不到 = 里面没有」
                    FileSearch.Notice.ArchiveNameOnly ->
                        AppToast.info(searchContext, Str.get(R.string.files_search_archive_name_only))
                }
            }
        }

        /** 点一条结果 = 在**活动窗格**跳过去，两个弹窗一起收、结果清掉。 */
        val openSearchResult: (SearchResult) -> Unit = { r ->
            // 搜索范围就是活动窗格的当前目录，所以结果必然是该目录（或其子目录）下的
            // 路径（可能是**虚拟路径**）。
            // 顺序要紧：navigateTo 会把 highlightPath 清成 null，所以必须**先跳再高亮** ——
            // 反过来高亮会被立刻抹掉。
            val target = VirtualPath.split(r.path)?.let { (arc, internal) ->
                // 虚拟路径不能 substringBeforeLast('/')：`x.zip!/a.txt`
                // 会切出 `x.zip!`，既不是包也不是目录，跳过去必然是「路径不存在」
                VirtualPath.join(arc, VirtualPath.parentInternal(internal) ?: "")
            } ?: r.path.substringBeforeLast('/')
            vm.navigateTo(state.active, target)
            vm.highlightFromSearch(state.active, r.path)
            searchOpen = false
            searchResultsOpen = false
            searchResultsPage = false
            FileSearch.clear()
        }

        /** 收起结果弹窗（并清结果）。 */
        val closeSearchResults: () -> Unit = {
            searchResultsOpen = false
            FileSearch.clear()
        }

        /** 起一次搜索；**真跑起来**才切到结果弹窗（见下）。 */
        val startSearch: (SearchOptions) -> Unit = { opts ->
            // 直接给**路径字符串**：窗格在包里时 cwd 是虚拟路径，
            // 包成 File 之后一路问下去全是 false（反馈 e15 P0-2）
            FileSearch.start(
                scope = searchScope,
                root = activePane.cwd,
                options = opts
            )
            // 空词 / 非法正则在 `FileSearch.start` 里是**当场 return**（state 仍是 Idle）——
            // 这两种情况把条件弹窗留着别关，用户还得改词；只有真进 Running 才切弹窗，
            // 否则会出现「弹窗关了、结果一片空白、只有一条 toast」的空档。
            if (FileSearch.state.value is SearchState.Running) {
                searchOpen = false
                searchResultsOpen = true
            }
        }

        // ---- 条件弹窗 ----
        if (searchOpen) {
            UnifiedDialog(
                onDismissRequest = {
                    searchOpen = false
                    FileSearch.clear()
                },
                title = Str.get(R.string.files_search),
                // 必须点名 content：最后一个参数是 widthFraction(Float)，尾随 lambda
                // 会去绑它 —— 绑不上就把 content 漏掉，整个弹窗体都编不过（d20 构建门禁）
                content = {
                    // 高级选项一展开在小屏会把「关闭」顶出屏幕 —— 外层可滚。
                    // **结果不在这里**（e25）：它有自己的弹窗，这里只管填条件。
                    // 这里**不能再自己滚一层**：UnifiedDialog 的正文区（scrollContent，默认开）
                    // 已经套了 verticalScroll，两层叠起来 = 内层被量到**无界高度** →
                    // 打开即崩（f9「点搜索闪退」）。正文交给外层滚，按钮照样钉在卡片底栏。
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FileSearchPanel(
                            state = searchState,
                            results = searchResults,
                            showResults = false,
                            onViewResults = {
                                // 条件弹窗关掉、结果弹窗打开（**不清结果**，否则点进去是空的）
                                searchOpen = false
                                searchResultsOpen = true
                            },
                            onStart = startSearch,
                            onStop = { FileSearch.cancel() },
                            onClear = { FileSearch.clear() },
                            onOpen = openSearchResult,
                            onClose = {
                                searchOpen = false
                                FileSearch.clear()
                            }
                        )
                    }
                }
            )
        }

        // ---- 结果弹窗（e25：单开一个，带滑条）----
        if (searchResultsOpen) {
            UnifiedDialog(
                onDismissRequest = closeSearchResults,
                title = Str.get(R.string.files_search_results),
                titleActions = {
                    // h.txt 第 12 条：右上角斜向拉伸 —— 点开进整页，长结果不再半屏翻
                    UnifiedIconButton(
                        icon = Icons.Default.OpenInFull,
                        onClick = {
                            searchResultsOpen = false
                            searchResultsPage = true
                        },
                        contentDescription = Str.get(R.string.files_search_expand),
                    )
                },
                content = {
                    // showInput=false：这里没有条件可填，查询框/高级选项/分隔线整段收起，
                    // 竖向空间全让给结果列表（列表自己带 heightIn 上限 + 滑条）。
                    // 同上：正文交给 UnifiedDialog 的外层滚动，别再自己滚一层
                    // （两层竖滚 = 内层无界高度 → 打开即崩）。
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FileSearchPanel(
                            state = searchState,
                            results = searchResults,
                            showInput = false,
                            onStart = startSearch,
                            onStop = { FileSearch.cancel() },
                            onClear = { FileSearch.clear() },
                            onOpen = openSearchResult,
                            onClose = closeSearchResults
                        )
                    }
                }
            )
        }

        // ---- 搜索结果整页（h.txt 第 12 条）----
        if (searchResultsPage) {
            Dialog(
                onDismissRequest = {
                    searchResultsPage = false
                    searchResultsOpen = true
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                // i.txt 第 10 条：渐变背景开启时 `pageBackground()` 返回 **透明**（主页面
                // 靠身后的渐变兜底），但 Dialog 窗口后面没有渐变 —— 于是整页能直接透到
                // 文件列表上去。这里自己铺一层同款渐变（无渐变时用不透明底色）。
                val brush = pageGradientBrush()
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = if (brush != null) AppColors.background() else AppColors.pageBackground()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (brush != null) Modifier.background(brush) else Modifier)
                    ) {
                        FileSearchResultsPage(
                            state = searchState,
                            results = searchResults,
                            onBack = {
                                searchResultsPage = false
                                searchResultsOpen = true
                            },
                            onStop = { FileSearch.cancel() },
                            onClear = { FileSearch.clear() },
                            onOpen = openSearchResult
                        )
                        // m.txt 第 13 条：整页 Dialog 自己的 AppToast 宿主 ——
                        // 搜索结果长按「已复制」保持 AppToast 样式，而不是退回系统 Toast。
                        AppToastHost(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }

        // ==================== 双窗格 ====================
        // 外面套一层 Box 是为了给「单窗口拿起待粘贴」的悬浮按钮一个锚点（用户反馈㉒）——
        // 按钮要浮在列表**之上**、跟着用户走进别的目录继续可用，排进 Column 流里就只能
        // 待在底栏旁边，导航一走就跟丢了。
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (state.dualEnabled) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val totalPx = constraints.maxWidth.toFloat()
                    Row(modifier = Modifier.fillMaxSize()) {
                        // l.txt 第 8 条：拿起后的「粘贴 / 关闭」两个圆钮必须落在**活动窗格**里，
                        // 不能永远贴在整页右下角（那会压在另一栏的文件名上）。所以每个窗格
                        // 各套一层 Box，只有活动的那栏才挂这组按钮。
                        Box(modifier = Modifier.weight(paneWeight)) {
                            PaneBox(
                                side = Side.LEFT,
                                state = state,
                                vm = vm,
                                modifier = Modifier.fillMaxSize(),
                                onOpenSettings = { showSettings = true },
                                onRequestOpenWith = { openWith = it },
                                edgeSwipeWidthPx = edgeSwipePx
                            )
                            if (state.pendingTransfer != null && state.active == Side.LEFT) {
                                PendingTransferBar(
                                    onPaste = { vm.pastePending(state.activePane.cwd) },
                                    onClose = { vm.cancelPendingTransfer() },
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                        }
                        DragDivider(
                            split = paneWeight,
                            totalPx = totalPx,
                            // d14「不可变」档锁死；其余两档都能拖
                            enabled = ratioMode != FilePrefs.RATIO_FIXED,
                            onChange = { paneWeight = it },
                            onDragEnd = {
                                // d14「可变」档：拖完把结果存成新的默认比例（每帧写太重，抬手写一次）
                                if (ratioMode == FilePrefs.RATIO_VARIABLE) {
                                    FilePrefs.setPaneRatio((paneWeight * 100).roundToInt())
                                }
                            },
                            // 复位到**已存的默认比例**，不是硬编码的 0.5（用户反馈 c4）
                            onReset = { paneWeight = FilePrefs.paneWeight() }
                        )
                        Box(modifier = Modifier.weight(1f - paneWeight)) {
                            PaneBox(
                                side = Side.RIGHT,
                                state = state,
                                vm = vm,
                                modifier = Modifier.fillMaxSize(),
                                onOpenSettings = { showSettings = true },
                                onRequestOpenWith = { openWith = it }
                            )
                            if (state.pendingTransfer != null && state.active == Side.RIGHT) {
                                PendingTransferBar(
                                    onPaste = { vm.pastePending(state.activePane.cwd) },
                                    onClose = { vm.cancelPendingTransfer() },
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    PaneBox(
                        side = Side.LEFT,
                        state = state,
                        vm = vm,
                        modifier = Modifier.fillMaxSize(),
                        onOpenSettings = { showSettings = true },
                        onRequestOpenWith = { openWith = it },
                        // o.txt 第 8 条：这里是真正的单窗格 —— 底边那条「活动窗格」细线
                        // 只在双窗格里表示「我在哪一栏」，单窗格画它纯属多余。
                        singlePane = true,
                        edgeSwipeWidthPx = edgeSwipePx
                    )
                    // 拿起后才出现；单窗口下它就在唯一那栏里。
                    if (state.pendingTransfer != null) {
                        PendingTransferBar(
                            onPaste = { vm.pastePending(state.activePane.cwd) },
                            onClose = { vm.cancelPendingTransfer() },
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }
        }

        // ==================== 进行中操作（隐藏后留胶囊，4.12）====================
        if (state.op is OpUiState.Running && (state.op as OpUiState.Running).hidden) {
            RunningPill(
                progress = (state.op as OpUiState.Running).progress,
                onClick = { vm.showOp() }
            )
        }

        // ==================== 底栏（多选态整排替换，4.1）====================
        if (state.multiSelect) {
            // 进多选的一次性提示条（§4.3）：横向框选是**看不见的手势**，不摆一句出来
            // 没人想得到还能这么选。停 4 秒自己收，点一下也能立刻收；两种收法都会写盘，
            // 之后整个生命周期不再出现 —— 反复弹就从「兜底」变成「噪音」。
            if (selectHintVisible) {
                LaunchedEffect(Unit) {
                    delay(SELECT_HINT_MS)
                    selectHintVisible = false
                    FilePrefs.markSelectHintShown()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        // j.txt 第 3 条：提示条不再铺整块主色底，改成同色描边 + 文字。
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectHintVisible = false
                            FilePrefs.markSelectHintShown()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Str.get(R.string.files_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            MultiSelectBar(
                count = state.selectedCount,
                // 文件动作（复制/移动/删除）**不在这一栏** —— 走长按菜单（用户反馈④）
                onSelectAll = { vm.selectAll(state.active) },
                onInvert = { vm.invertSelection(state.active) },
                onSameType = { vm.selectSameType(state.active) },
                // 与返回键同一个语义：退出 = 只收活动栏，另一栏的勾保留
                onExit = { vm.exitMultiSelectKeepingOtherPane() }
            )
        } else {
            NormalBar(
                canBack = activePane.canGoBack,
                canForward = activePane.canGoForward,
                canUp = activePane.canGoUp,
                canSync = state.dualEnabled && activePane.cwd != state.otherPane.cwd,
                canCreate = !activePane.readOnly,
                onBack = { vm.goBack(state.active) },
                onForward = { vm.goForward(state.active) },
                onSync = { vm.sync() },
                onUp = { vm.goUp(state.active) },
                onCreate = { vm.requestCreate(state.active) },
                // 用户反馈①：底栏**从下往上拉** = 打开底部「最近位置 / 书签」面板。
                // 与页头 ☰ 的侧边栏是两块不同的东西，入口也分开。
                // 打开前先关侧栏：三块覆盖层（侧栏 / 书签历史 / 搜索）允许同时为 true，
                // 现在只靠「侧栏遮罩挡住底栏」才没叠起来 —— 换个入口就会叠两层。
                onSwipeUp = {
                    navOpen = false
                    placesOpen = true
                }
            )
        }
    }

    // ==================== 左侧侧边栏（用户反馈①） ====================
    // 覆盖层画在主体 Column **之后**（同层后画的压在上面），而且必须留在这个 Box 里 ——
    // 跑到 Box 外面就只是父布局的又一个兄弟节点，父布局若不是 Box 就盖不住列表。
    // 用户反馈 c7：要有弹出动画，且样式照抄文本编辑器的目录树抽屉。
    //
    // **拆成两个 AnimatedVisibility**（遮罩一个、面板一个）而不是包一个：可见性只能带一套
    // enter/exit，塞一起就变成「遮罩跟着面板一起平移」，整块在飘而不是面板从边上推出来。
    // 遮罩只淡入淡出；面板 tween(280) 从左滑出 —— 与编辑器抽屉同一条曲线。
    AnimatedVisibility(
        visible = navOpen,
        modifier = Modifier.fillMaxSize().align(Alignment.TopStart),
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        // 遮罩：点一下收起。用 detectTapGestures 而不是 clickable ——
        // 全屏 clickable 会在遮罩上画一个满屏水波纹，纯属噪声。
        // 0.45 → 0.25（用户反馈 c15：抽屉遮罩太黑，底下列表看不清）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .pointerInput(Unit) { detectTapGestures { navOpen = false } }
        )
    }
    AnimatedVisibility(
        visible = navOpen,
        // 宽度只给 280：滑入距离 = 这个布局自己的宽度。包成全屏宽的话 `it` 会是整屏宽，
        // 面板得滑完一整屏才到位，看着是「闪出来」而不是「推出来」
        modifier = Modifier.fillMaxHeight().width(280.dp).align(Alignment.TopStart),
        enter = slideInHorizontally(animationSpec = tween(280)) { -it } +
            fadeIn(animationSpec = tween(200)),
        exit = slideOutHorizontally(animationSpec = tween(240)) { -it } +
            fadeOut(animationSpec = tween(160))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 新拟态的凸起**画在圆角裁剪之外**（影子要落在抽屉外圈的页面上），
                // 所以 .then(neuRaised) 必须排在 .clip 之前 —— 排后面影子会被裁掉
                .then(
                    if (UIConfig.isNeumorphismEnabled()) Modifier.neuRaised(
                        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        UIConfig.shouldUseDarkTheme(),
                        NeuDefaults.Intensity.LIGHT,
                        cornerRadius = 16.dp,
                        backgroundColor = Color.Transparent
                    ) else Modifier
                )
                // 圆角只给右边：左边贴着屏幕边，给圆角会露出一条缝，看着像没贴住
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                // 用户反馈 e2「侧边栏依旧半透明」：玻璃底的 alpha 只有 0.5，直接铺的话
                // 身后的文件列表会透上来 —— 这就是「依旧半透明」的来源。色相照旧跟着玻璃
                // 开关走，**只把 alpha 钉死在 1**：侧栏要的是自己的一张「面」，
                // 不是蒙在列表上的一层纱。
                .background(
                    (if (AppColors.glassEnabled()) AppColors.glassBackground()
                    else MaterialTheme.colorScheme.background).copy(alpha = 1f)
                )
                // 参照**代码编辑器抽屉**的同一套写法（e2 指定的）：渐变开启时按侧栏自身
                // 尺寸（280 × 屏高）取样再铺一层，让侧栏自己有面、不透出身后页面的渐变；
                // 渐变关闭则露上面那层不透明底色 —— brush 为 null 就什么都不加。
                .then(
                    gradientBackgroundBrush(
                        with(LocalDensity.current) { 280.dp.toPx() },
                        with(LocalDensity.current) {
                            LocalConfiguration.current.screenHeightDp.dp.toPx()
                        }
                    )?.let { b -> Modifier.background(b) } ?: Modifier
                )
        ) {
            FileNavDrawer(
                onPick = { path ->
                    vm.navigateTo(state.active, path)
                    navOpen = false
                },
                onOpenEditor = {
                    // 文本编辑器**不带路径**直接开：侧边栏里没有「选中文件」这个上下文，
                    // 带一个空 file_path 只会让编辑器的意图解析走一遍再回退。
                    navOpen = false
                    runCatching {
                        context.startActivity(Intent(context, CodeEditorActivity::class.java))
                    }.onFailure {
                        AppToast.warning(context, Str.get(R.string.open_with_open_editor_failed))
                    }
                },
                onOpenTerminal = {
                    // 用户反馈㉕：点「终端」要**直接进终端** —— 原先这里是切到「开发」tab，
                    // 用户看到的是开发页而不是命令行，以为按钮坏了。现在与开发页里那个
                    // 入口走同一个 Activity（本页是全屏态，另起 Activity 不受其遮挡）。
                    navOpen = false
                    runCatching {
                        com.UIN.Tool.terminal.SimpleTerminalActivity.start(context)
                    }.onFailure {
                        AppToast.warning(context, Str.get(R.string.open_with_open_editor_failed))
                    }
                },
                onOpenApkExtract = {
                    // 提取安装包（b26/c18）：独立 Activity —— 装机清单的读取与图标解码
                    // 要占几百毫秒，留在本页会让侧栏收起这一下也跟着卡
                    navOpen = false
                    runCatching {
                        context.startActivity(
                            Intent(context, ApkExtractActivity::class.java)
                        )
                    }.onFailure {
                        AppToast.warning(context, Str.get(R.string.apk_extract_failed))
                    }
                },
                onOpenTasks = {
                    // 侧边栏收起再开页面：两者都是覆盖层，叠着开会把返回键的层级搞乱
                    navOpen = false
                    showTasks = true
                },
                onOpenTrash = {
                    navOpen = false
                    showTrash = true
                },
                onOpenSettings = {
                    navOpen = false
                    showSettings = true
                },
                onOpenRemote = {
                    navOpen = false
                    showRemote = true
                }
            )
        }
    }
    } // Box：主体 Column + 侧栏覆盖层

    // ==================== 书签/历史底部面板（4.9，底栏上滑，用户反馈①）====================
    if (placesOpen) {
        // historyTick 是个自增计数器，唯一作用是**让历史变化触发重组**。
        // 数据本身每次现读 FilePlaces（不在 UI 状态里留副本），免得两处漂移。
        val historyTick by vm.historyTick.collectAsState()
        FilePlacesDrawer(
            bookmarks = FilePlaces.bookmarks(),
            history = remember(historyTick) { FilePlaces.history() },
            onBookmarkClick = { b ->
                vm.navigateTo(state.active, b.path, recordHistory = false)
                placesOpen = false
            },
            onHistoryClick = { h ->
                vm.navigateTo(state.active, h.path, recordHistory = false)
                placesOpen = false
            },
            onDismiss = { placesOpen = false }
        )
    }

    // ==================== 页头路径 → 跳转输入框（用户反馈⑥） ====================
    if (jumpOpen) {
        var pathText by rememberSaveable { mutableStateOf("") }
        var jumpError by rememberSaveable { mutableStateOf(false) }
        // 打开的这一帧才把文本对准当前路径 —— 放 remember 初值的话，用户改过一次之后
        // 再打开会残留上次输入，看着像没刷新。
        LaunchedEffect(jumpOpen) {
            pathText = activePane.cwd
            jumpError = false
        }
        UnifiedDialog(
            onDismissRequest = { jumpOpen = false },
            title = Str.get(R.string.files_jump_title),
            content = {
                OutlinedTextField(
                    value = pathText,
                    onValueChange = {
                        pathText = it
                        jumpError = false
                    },
                    singleLine = true,
                    label = { Text(Str.get(R.string.files_jump_hint)) },
                    isError = jumpError,
                    supportingText = if (jumpError) {
                        { Text(Str.get(R.string.files_jump_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                )
            },
            confirmButton = {
                UnifiedDialogTextButton(
                    onClick = {
                        val t = pathText.trim()
                        if (t.isNotEmpty() && File(t).isDirectory) {
                            vm.navigateTo(state.active, t)
                            jumpOpen = false
                        } else {
                            // 只导航**不创建**：路径不存在就报错。自动建目录会把「打错一个字」
                            // 变成磁盘上多出一棵空树，属于导航动作里藏着的写副作用。
                            jumpError = true
                        }
                    },
                    contentColor = MaterialTheme.colorScheme.primary
                ) { Text(Str.get(R.string.ok)) }
            },
            dismissButton = {
                UnifiedDialogTextButton(onClick = { jumpOpen = false }) {
                    Text(Str.get(R.string.cancel))
                }
            }
        )
    }

    // ==================== ⋮ → 排序方式（用户反馈⑩：网格弹窗） ====================
    if (sortDialogOpen) {
        var onlyThisFolder by rememberSaveable { mutableStateOf(false) }
        // 开框时按「当前目录有没有专属排序」对准复选框 —— 状态声明在 if 外面，
        // 关开之间不会丢；这里只负责每次打开时重新对齐。
        LaunchedEffect(sortDialogOpen) {
            onlyThisFolder = FilePrefs.folderSort(activePane.cwd) != null
        }
        UnifiedDialog(
            onDismissRequest = { sortDialogOpen = false },
            title = Str.get(R.string.files_sort_by),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 单选：4 种常见排序摆成两列网格，点整格切换（比 4 行窄一半，拇指少滚一次）
                    listOf(
                        SortKey.NAME to Str.get(R.string.files_sort_name),
                        SortKey.SIZE to Str.get(R.string.files_sort_size),
                        SortKey.DATE to Str.get(R.string.files_sort_date),
                        SortKey.TYPE to Str.get(R.string.files_sort_type)
                    ).chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { (key, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            vm.setSort(state.active, key, onlyThisFolder)
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = activePane.sort.key == key,
                                        onClick = {
                                            vm.setSort(state.active, key, onlyThisFolder)
                                        }
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // 复选：目录优先 / 仅用于此文件夹 / 逆向排序
                    DialogCheckRow(
                        label = Str.get(R.string.files_menu_dirs_first),
                        checked = activePane.sort.dirsFirst
                    ) { vm.toggleDirsFirst(state.active, onlyThisFolder) }

                    DialogCheckRow(
                        label = Str.get(R.string.files_sort_this_folder),
                        checked = onlyThisFolder
                    ) {
                        onlyThisFolder = it
                        vm.setSortScopeThisFolder(state.active, it)
                    }

                    // 「逆向」= 与当前相反的方向，所以直接取反当前值即可，不另存一份
                    DialogCheckRow(
                        label = Str.get(R.string.files_sort_reverse),
                        checked = !activePane.sort.ascending
                    ) {
                        vm.setSortAscending(state.active, !activePane.sort.ascending, onlyThisFolder)
                    }
                }
            },
            confirmButton = {
                UnifiedDialogTextButton(
                    onClick = { sortDialogOpen = false },
                    contentColor = MaterialTheme.colorScheme.primary
                ) { Text(Str.get(R.string.ok)) }
            }
        )
    }

    // ==================== 打开方式（用户反馈③）====================
    if (openWith.isNotEmpty()) {
        OpenWithDialog(
            path = openWith,
            onDismiss = { openWith = "" },
            onOpenManage = {
                openWith = ""
                openManage = true
            },
            onBrowseArchive = { archivePath ->
                openWith = ""
                // 挂载到活动窗格；`VirtualPath.join(x, "")` 是「包根」，与点包进入同一条路。
                // 记下来源目录，按「↑」才回到压缩包所在文件夹。
                vm.mountArchive(archivePath, state.activePane.cwd)
                vm.navigateTo(state.active, VirtualPath.join(archivePath, ""))
            }
        )
    }

    // ==================== M3 对话框与进度（4.4/4.5/4.12）====================
    M3Dialogs(
        vm = vm,
        state = state
    )
}

// ==================== 打开方式弹窗（用户反馈③）====================

/**
 * 「打开方式」弹窗。
 *
 * 结构：靠左标题 + 右上搜索/帮助 → 可收拢的内置能力网格 → 插件搜索与候选 →
 * 最下左「类型」右「关闭」。「类型」下拉在 内置 / 系统建议 / 全部 之间切；
 * 网格项点开即用，长按进详情设为该后缀默认。
 *
 * r.txt 第 1 条：内置能力卡片压低、可收拢；下方插件区只列能接住当前文件的
 * 已安装插件，并带独立搜索框，点选直接把文件交给插件打开。
 */
@Composable
internal fun OpenWithDialog(
    path: String,
    onDismiss: () -> Unit,
    onOpenManage: () -> Unit,
    /** g10：在文件页当前窗格里挂载压缩包。 */
    onBrowseArchive: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val file = File(path)

    // 用户反馈 d5：**未知后缀**进来的文件，内置一个能力都接不住 —— 初始就摆在「系统建议」上，
    // 否则一打开是张空网格，用户还得自己去找「类型」下拉才看得见候选。
    // 按 path 记：换一个文件重开时档位要回到这个文件自己的默认值。
    // g10：这里用「完整内置」档 —— 未知后缀也能选图片/APK/字体/归档等工具，
    // 不再因为后缀不认识就给一张只剩编辑器与十六进制的空网格。
    val builtInItems = remember(path) {
        builtInOpsFor(
            context, path,
            includeAll = true,
            onBrowseArchive = { p ->
                onDismiss()
                onBrowseArchive(p)
            }
        )
    }
    var mode by rememberSaveable(path) {
        // m.txt 第 10 条：后缀在注册表里**认不出来**（本应用完全不认识这个类型）时，
        // 初始档位直接摆在「系统建议」上 —— 用户要的是能选的单子，而不是一张只有
        // 编辑器/十六进制的内置网格。注册表认识的后缀仍从内置档开始。
        val known = FileTypes.isKnown(path)
        mutableStateOf(
            (if (known) OpenWithMode.BUILT_IN else OpenWithMode.SUGGESTED).name
        )
    }
    val currentMode = remember(mode) {
        OpenWithMode.entries.firstOrNull { it.name == mode } ?: OpenWithMode.BUILT_IN
    }
    var searchOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var helpOpen by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    // 详情框要显示的那一格；null = 关着
    var detail by remember { mutableStateOf<OpenWithItem?>(null) }

    // r.txt 第 1 条：内置能力区可收拢；下方插件区有自己的搜索框。
    var builtInExpanded by rememberSaveable { mutableStateOf(true) }
    var pluginQuery by rememberSaveable { mutableStateOf("") }
    val mime = remember(path) { mimeOf(file) }
    val pluginManager = remember(context) { PluginManager.getInstance(context) }
    val installedPlugins by pluginManager.plugins.collectAsState()
    // 只列真正声明了 openWith、且接受「文件」、MIME 也匹配的插件 —— 与系统分享
    // 中转页同一套判定（OpenWithConfig.matches），不另造一份规则。
    val pluginOpeners = remember(installedPlugins, mime) {
        installedPlugins.filter {
            it.hasOpenWith() && it.openWith?.matches(mime, "file") == true
        }
    }
    val visiblePlugins = remember(pluginOpeners, pluginQuery) {
        val q = pluginQuery.trim()
        if (q.isEmpty()) pluginOpeners
        else pluginOpeners.filter { p ->
            p.name.contains(q, ignoreCase = true) ||
                p.pluginId.contains(q, ignoreCase = true) ||
                p.description.contains(q, ignoreCase = true) ||
                (p.openWith?.label ?: "").contains(q, ignoreCase = true)
        }
    }

    // 系统候选要 queryIntentActivities + 逐个 loadLabel/loadIcon（含 bitmap 解码），
    // 是实打实的 IO —— 原来写在 remember 里，组合期在主线程跑，首次弹「打开方式」必掉帧。
    // 口径对齐 FileOpenWithManageScreen 的已修写法：LaunchedEffect + withContext(IO)。
    var systemItems by remember { mutableStateOf<List<OpenWithItem>>(emptyList()) }
    LaunchedEffect(path, currentMode) {
        // s.txt 第 1 条：插件是独立一档，不需要向系统查候选。
        if (currentMode == OpenWithMode.BUILT_IN || currentMode == OpenWithMode.PLUGINS) {
            systemItems = emptyList()
            return@LaunchedEffect
        }
        // 查询与启动共用同一个 URI —— 用 file URI 列出来的应用，
        // 拿去真启动时签的是 content URI，可能一个都点不动
        val uri = try {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "open with uri failed: ${e.message}")
            Uri.fromFile(file)
        }
        systemItems = withContext(Dispatchers.IO) { systemAppsFor(context, mime, uri, currentMode) }
    }
    val items = if (currentMode == OpenWithMode.BUILT_IN) builtInItems else systemItems
    val visible = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = null,
        content = {
            // ---- 顶栏：靠左标题，右侧搜索 / 帮助 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Str.get(R.string.files_ctx_open_with),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                UnifiedIconButton(
                    icon = Icons.Default.Search,
                    onClick = {
                        searchOpen = !searchOpen
                        // 关搜索时两个框一起清 —— 插件档用的是 pluginQuery。
                        if (!searchOpen) {
                            query = ""
                            pluginQuery = ""
                        }
                    },
                    contentDescription = Str.get(R.string.files_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UnifiedIconButton(
                    icon = Icons.Default.Info,
                    onClick = { helpOpen = true },
                    contentDescription = Str.get(R.string.help),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (searchOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    // s.txt 第 1 条：插件档的搜索就是插件搜索 —— 顶栏一个输入框，
                    // 不再上下摆两个各管一半。
                    value = if (currentMode == OpenWithMode.PLUGINS) pluginQuery else query,
                    onValueChange = {
                        if (currentMode == OpenWithMode.PLUGINS) pluginQuery = it else query = it
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            Str.get(
                                if (currentMode == OpenWithMode.PLUGINS) {
                                    R.string.files_open_plugin_search
                                } else {
                                    R.string.files_search_hint
                                }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- 内置能力 / 系统候选（r.txt 第 1 条：可收拢）----
            // s.txt 第 1 条：插件已独立成档，网格只在内置 / 系统建议 / 全部三档出现。
            val showBuiltInGrid = currentMode == OpenWithMode.BUILT_IN
            val showSystemGrid = currentMode == OpenWithMode.SUGGESTED || currentMode == OpenWithMode.ALL
            if (showBuiltInGrid || showSystemGrid) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { builtInExpanded = !builtInExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentMode == OpenWithMode.BUILT_IN) {
                            Str.get(R.string.files_open_builtin_section)
                        } else {
                            modeLabel(currentMode)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (builtInExpanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        contentDescription = Str.get(
                            if (builtInExpanded) R.string.files_open_collapse
                            else R.string.files_open_expand
                        ),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(visible = (showBuiltInGrid || showSystemGrid) && builtInExpanded) {
                if (visible.isEmpty()) {
                    Text(
                        text = Str.get(R.string.files_open_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    // 用户反馈㉔：网格改成**可滚动且限高** —— 系统应用几十个时，原先的
                    // `forEach` 会把弹窗一路撑到满屏，底部「类型 / 关闭」被顶出屏幕外。
                    // 用普通 Column + verticalScroll 而不是 LazyColumn：Dialog 里嵌可懒列表
                    // 要多背一层嵌套滚动，几十条一列排完就够（与「挑打开方式」框同一取舍）。
                    Column(
                        modifier = Modifier
                            .heightIn(max = 208.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        visible.chunked(3).forEachIndexed { idx, row ->
                            if (idx > 0) Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { item ->
                                    OpenWithTile(
                                        item = item,
                                        // 用户反馈②：**点一下就用它打开**。原先是点开详情框、
                                        // 看完还得再点一次才真打开 —— 选个打开方式被做成了两步。
                                        // 「管理默认」挪到长按（详情框里本就有绑定/解绑两颗钮）。
                                        //
                                        // i.txt 第 8 条：不能走 launchTarget —— 那个入口是给
                                        // 「默认打开方式」用的，没有窗格上下文，「浏览压缩包」只能
                                        // 退化成一句「在文件列表中打开」。这里直接跑格子自己的
                                        // 启动器，归档才会真的在当前窗格挂载。
                                        onClick = {
                                            item.launch()
                                            onDismiss()
                                        },
                                        onLongClick = { detail = item },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // 凑不满一行时补等宽占位，右缘才不会塌进来
                                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            if (showBuiltInGrid || showSystemGrid) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ---- 插件：独立一档（下拉里的「插件」），「全部」里也并列展示 ----
            if (currentMode == OpenWithMode.PLUGINS ||
                (currentMode == OpenWithMode.ALL && pluginOpeners.isNotEmpty())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Str.get(R.string.files_open_plugins),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (pluginOpeners.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pluginOpeners.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 插件档用顶栏搜索；「全部」里网格与插件并列，插件区保留自己的输入框。
                if (currentMode != OpenWithMode.PLUGINS) {
                    Spacer(modifier = Modifier.height(4.dp))
                    UnifiedTextField(
                        value = pluginQuery,
                        onValueChange = { pluginQuery = it },
                        placeholder = Str.get(R.string.files_open_plugin_search),
                        leadingIcon = Icons.Default.Search,
                        showClearButton = true,
                        autoFocus = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                when {
                    pluginOpeners.isEmpty() -> Text(
                        text = Str.get(R.string.files_open_plugin_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    visiblePlugins.isEmpty() -> Text(
                        text = Str.get(R.string.files_open_plugin_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    else -> Column(
                        modifier = Modifier
                            .heightIn(max = 184.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        visiblePlugins.forEach { plugin ->
                            OpenWithPluginRow(
                                plugin = plugin,
                                onClick = {
                                    openFileWithPlugin(context, plugin, file, mime)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // ---- 底栏：左「类型」，右「关闭」 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    UnifiedDialogTextButton(onClick = { typeMenu = true }) {
                        Text(modeLabel(currentMode))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    UnifiedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        OpenWithMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(modeLabel(m), maxLines = 1) },
                                leadingIcon = {
                                    if (m == currentMode) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    mode = m.name
                                    typeMenu = false
                                }
                            )
                        }
                    }
                }
                UnifiedDialogTextButton(onClick = onDismiss) {
                    Text(Str.get(R.string.close))
                }
            }
        }
    )

    // 这两个框画在 UnifiedDialog **之外**：同一个 composable 里，Dialog 窗口彼此是兄弟，
    // 叠在弹窗上层。塞进 content 会让它们变成外层窗口的子窗口，遮罩与焦点都会乱。
    if (helpOpen) {
        OpenWithHelpDialog(
            onDismiss = { helpOpen = false },
            onOpenManage = {
                helpOpen = false
                onOpenManage()
            }
        )
    }

    detail?.let { item ->
        OpenWithDetailDialog(
            item = item,
            path = path,
            onDismiss = { detail = null }
        )
    }
}

/**
 * 打开方式网格里的一格：图标在上、名称在下。**点开即用它打开**，长按进详情（管理该后缀默认）。
 *
 * s.txt 第 1 条：`internal` 放开 —— 系统分享中转页也要用**同一套**格子，
 * 两处各画一份只会慢慢长歪。
 */
@Composable
internal fun OpenWithTile(
    item: OpenWithItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // q.txt 第 2 条（用户）：内置能力卡片要更小、更紧凑 —— 原先上下 14dp + 28dp 图标
    // 一格接近 70dp，三四行就把弹窗顶满。现在图标 22dp、上下 8dp、名称 11sp，
    // 一行只占约 46dp，同样的高度能多看一行半。
    // r.txt 第 1 条（用户）：再压低一档 —— 图标 20dp、上下 6dp、名称 10sp，
    // 一行约 38dp；配合可收拢，内置能力区不再霸占整个弹窗。
    // v.txt 第 5 条（用户）：打开方式的图标再放大一档 —— 20dp 在小屏上辨识度太差，
    // 统一到 28dp、名称 11sp、上下 8dp（仍比最早的 70dp 一格紧凑）。
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val ic = item.icon) {
            is OpenWithIcon.Vector -> Icon(
                imageVector = ic.value,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            is OpenWithIcon.Raster -> Image(
                bitmap = ic.value,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 「打开方式」插件区的一行（r.txt 第 1 条）：图标 + 名称 + 一句副标题。
 *
 * 点击即把当前文件交给该插件（`openData.kind = file`），与系统分享中转页
 * 交给插件的数据形状一致。
 */
@Composable
private fun OpenWithPluginRow(
    plugin: PluginInfo,
    onClick: () -> Unit
) {
    val subtitle = plugin.openWith?.label
        ?.takeIf { it.isNotBlank() && it != plugin.name }
        ?: plugin.description
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OpenWithPluginIcon(plugin = plugin, size = 28.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name.ifBlank { plugin.pluginId },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 插件图标：从插件目录异步解码；没有图标时退回名称首字母。 */
@Composable
private fun OpenWithPluginIcon(plugin: PluginInfo, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var bitmap by remember(plugin.pluginId, plugin.icon) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(plugin.pluginId, plugin.icon) {
        bitmap = withContext(Dispatchers.IO) {
            val dir = File(Constants.PLUGIN_DIR, plugin.pluginId)
            val iconFile = File(dir, plugin.icon.ifEmpty { "icon.png" })
            if (iconFile.exists()) {
                runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }
                    .getOrNull()
            } else null
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = plugin.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单个打开方式的详情（用户反馈③）。
 *
 * 正文是**一整段纯文本**、长按全选复制 —— 用户要的往往就是那串组件名，
 * 好拿去别处查或粘；拆成字段行反而复制不全。
 * 左下「切换」= 绑成该后缀的默认，右下「删除」= 解绑该后缀。
 */
@Composable
private fun OpenWithDetailDialog(
    item: OpenWithItem,
    path: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val file = File(path)
    val suffix = file.extension.lowercase()
    val mime = if (file.isDirectory) "resource/folder"
    else MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(suffix) ?: "*/*"
    // 有绑定才谈得上「删除」；没有就把右下角那颗按灰，别让用户点了没反应
    val bound = FilePrefs.openWithDefault(suffix)

    val body = listOf(
        "${Str.get(R.string.files_ctx_open_with)}: ${item.label}",
        "${Str.get(R.string.files_open_suffix)}: $suffix",
        "${Str.get(R.string.files_prop_mime)}: $mime",
        item.target
    ).joinToString("\n")

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.files_ctx_open_with),
        content = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    // k.txt 第 6 条：长按复制的文字涟漪按 6dp 圆角裁切。
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClick = { /* 单击不做事，留给长按 —— 这里没有可点的目标 */ },
                        onLongClick = {
                            clipboard.setText(AnnotatedString(body))
                            AppToast.success(context, Str.get(R.string.files_copied))
                        }
                    )
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedDialogTextButton(
                    onClick = {
                        FilePrefs.setOpenWithDefault(suffix, item.target)
                        AppToast.success(context, Str.get(R.string.files_open_set_default))
                        onDismiss()
                    },
                    contentColor = MaterialTheme.colorScheme.primary
                ) { Text(Str.get(R.string.files_open_switch)) }

                UnifiedDialogTextButton(
                    onClick = {
                        // 没有绑定就谈不上删除；这颗按已置灰，这里再兜一层
                        if (bound != null) {
                            FilePrefs.removeOpenWithDefault(suffix)
                            AppToast.success(context, Str.get(R.string.files_open_removed))
                            onDismiss()
                        }
                    },
                    contentColor = if (bound == null) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                ) { Text(Str.get(R.string.delete)) }
            }
        }
    )
}

/**
 * 帮助框（用户反馈③）：只说一件事 —— 网格项可以长按设为默认；
 * 左下角的「管理」是去默认打开方式管理页的入口。
 */
@Composable
private fun OpenWithHelpDialog(
    onDismiss: () -> Unit,
    onOpenManage: () -> Unit
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.help),
        content = {
            Text(
                text = Str.get(R.string.files_open_long_press_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedDialogTextButton(
                    onClick = onOpenManage,
                    contentColor = MaterialTheme.colorScheme.primary
                ) { Text(Str.get(R.string.manage)) }
                UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.close)) }
            }
        }
    )
}

// ==================== 窗格容器 ====================

/**
 * 把 [FileListPane] 接到状态机上。回调一律带 `side` 而不是读 `state.active`——
 * 活动窗格的 StateFlow 更新要到下一帧才可见，读它会把点击落到**旧的**活动窗格上。
 *
 * 文件级 `private` 放开（e20 ③）：**代码编辑器的侧边栏里就是这一栏** ——
 * 用户拍板「侧边栏直接用单窗口文件管理器」，所以抽屉挂的是同一份接线，
 * 不另写一套；同一栏在两处长得不一样、行为却要一致，只能共用一个实现。
 */
@Composable
internal fun PaneBox(
    side: Side,
    state: FileManageUiState,
    vm: FileManageViewModel,
    onOpenSettings: () -> Unit,
    /** 「打开方式」弹窗由 `FilesScreen` 持有状态，这里只负责把它递上去。 */
    onRequestOpenWith: (String) -> Unit,
    /**
     * o.txt 第 5 条：**宿主自定义普通文件的点击行为**。
     *
     * 代码编辑器侧边栏不能用文件页那套「看类型分流 + 打开方式」：用户点文件就是想让
     * 编辑器打开它。非空时，真实文件（非目录、非归档、非归档内条目）直接交给它；
     * 目录仍导航、归档仍进包浏览、长按菜单完全不变。null = 文件页原有行为。
     */
    onFileOpen: ((FileEntry) -> Unit)? = null,
    /**
     * 宿主只画了**一栏**（e20 ③：代码编辑器抽屉里那一栏）。
     *
     * 状态机是进程级共用的，里面的 `dualEnabled` 可能开着（用户在文件页开了双窗格），
     * 可抽屉里只有左边一栏 —— 不压掉的话菜单会给出「复制到另一窗格」，而那个目标在
     * 抽屉里根本看不见，点了就是把东西传到一个看不见的地方。压成单窗口口径后，
     * 复制 / 移动 = 拿起 → 走到哪粘到哪，与文件页关掉双窗格时一模一样。
     */
    singlePane: Boolean = false,
    /** l.txt 第 11 条：最左侧边缘热区（px），只有真正贴屏幕左缘的那一栏才传。 */
    edgeSwipeWidthPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pane = state[side]
    // g11：点 `a.xxx.bak` 先问「恢复/取消」，不直接丢进编辑器或查看器。
    var bakRestore by remember { mutableStateOf<FileEntry?>(null) }

    // l.txt 第 15 条：开启新拟态时，每个窗格是一块浮起的「面」（双窗格 = 两块并排的
    // 卡片，单窗格 = 一整块），列表与路径都在同一张面上；关闭时完全保持原布局。
    val paneNeu = UIConfig.isNeumorphismEnabled()
    Box(
        modifier = modifier.then(
            if (paneNeu) {
                Modifier
                    .padding(4.dp)
                    .filesNeuSurface(RoundedCornerShape(14.dp), 14.dp)
            } else {
                Modifier
            }
        )
    ) {
    FileListPane(
        side = side,
        pane = pane,
        // 宿主只画一栏时按「活动」画：共享状态里的活动窗格可能是右边那栏（用户刚在
        // 文件页用了双窗格），照读会把抽屉里这栏画成**未激活**的灰样子。
        isActive = if (singlePane) true else state.active == side,
        // n.txt 第 9 条：单窗格不画活动窗格的底边细线（那条线只在双窗格里表示「我在哪一栏」）。
        showActiveLine = !singlePane,
        multiSelect = state.multiSelect,
        onActivate = { vm.setActive(side) },
        onUp = { vm.goUp(side) },
        onOpen = { entry ->
            vm.setActive(side)
            // §4.16：一旦用户又点了别的行，「已定位」就该退场 —— 它是给「刚跳过来」用的
            // 一次性状态。换目录的分支由 navigateTo 自己清，这里兜住「点了但没走」的那些。
            vm.clearHighlight(side)
            // g11：`.bak` 是「备份」语义，点它先问要不要恢复。归档内的虚拟条目没有
            // 可重命名的真实路径，仍然按普通条目打开。
            // o.txt 第 5 条：编辑器抽屉里，普通文件直接进编辑器；不弹「打开方式」。
            // 归档本身仍按目录进（里面还有文件要挑），归档内条目仍走原有解包逻辑。
            val archiveLike = looksLikeArchive(entry.name) &&
                !entry.name.endsWith(".apk", ignoreCase = true)
            if (!entry.isDir && onFileOpen != null &&
                !VirtualPath.isInArchive(entry.path) && !archiveLike
            ) {
                onFileOpen(entry)
            } else if (!entry.isDir && entry.name.endsWith(".bak", ignoreCase = true) &&
                entry.name.length > 4 && !VirtualPath.isInArchive(entry.path)
            ) {
                bakRestore = entry
            } else when {
                entry.isDir -> vm.navigateTo(side, entry.path)
                // 归档当目录进（4.15）：扩展名只是入口判断，真格式在 loadArchive 的 IO 线程里认。
                //
                // f5：**`.apk` 除外** —— 它确实也是个 zip，但点它的九成九是想看
                // 版本 / 权限 / 签名，不是想翻它的中央目录。原先这里先到先得，
                // 点开就是归档视图，安装包信息反倒得绕路。摘掉这一个后缀让它
                // 落到最下面的 [openEntry]（→ 内置安装包信息页）；真要翻包内结构，
                // 长按菜单里的「解压」还在，解出来怎么翻都行。
                //
                // 包内的包不再「进不去」：走下面的 [openArchivedEntry]，先解一份到缓存
                // 再原地浏览（那份副本带「来源目录」，按 ↑ 能回父包原位）。
                looksLikeArchive(entry.name) && !entry.name.endsWith(".apk", ignoreCase = true) &&
                    !VirtualPath.isInArchive(entry.path) ->
                    vm.navigateTo(side, VirtualPath.join(entry.path, ""))
                // 归档内的文件：**所有类型都能打开**（用户要求）。文本/APK 有专门的路
                // （写回来源 / 信息页），其余统一「解一条到查看缓存 → 走真实文件的分流」；
                // 包里的包解出来再进。取不出来时按「要密码」/「需整包解压」分别提示，
                // 绝不再拿「归档已损坏」糊弄 —— 包是刚列过目录的，它没坏。
                VirtualPath.isInArchive(entry.path) ->
                    openArchivedEntry(scope, context, vm, side, entry, onRequestOpenWith)
                // 同目录的图片清单直接从窗格状态取（`items` 是原始未过滤条目）——
                // 查看器靠它做「左右翻页」，不必再扫一遍盘。
                else -> openEntry(
                    context, entry,
                    siblingImages = pane.items.filter { !it.isDir && isImagePath(it.path) }
                        .map { it.path },
                    onRequestOpenWith = onRequestOpenWith,
                    // w.txt 第 4 条：默认绑成「浏览压缩包」时，在当前窗格挂载它。
                    // 先记下来源目录，按「↑」才会回到压缩包所在文件夹，
                    // 而不是把用户丢进缓存/根目录。
                    onBrowseArchive = { p ->
                        vm.mountArchive(p, pane.cwd)
                        vm.navigateTo(side, VirtualPath.join(p, ""))
                    }
                )
            }
        },
        onLongPress = { entry -> vm.showContextMenu(side, entry) },
        onToggleSelect = { entry -> vm.toggleSelect(side, entry.path) },
        // 手势多选的一笔（用户反馈④）：抬手超过 45dp 才调，两笔交替的锚点模型在 VM 里
        onSwipeSelect = { from, to -> vm.swipeSelect(side, from, to) },
        contextMenu = state.contextMenu?.takeIf { it.side == side },
        onContextAction = { action ->
            handleContextAction(scope, context, vm, state, action, onRequestOpenWith)
        },
        // g4：「复制/剪切」格长按 = 原先的「拿起」；短按在双窗格下直接送对面。
        onContextActionLong = { action ->
            handleContextActionLong(scope, context, vm, state, action, onRequestOpenWith)
        },
        onDismissContextMenu = { vm.dismissContextMenu() },
        onRefresh = { vm.refresh(side) },
        onScrollChanged = { vm.setScroll(side, it) },
        onOpenSettings = onOpenSettings,
        // f11：**只在双窗格**时看对面 —— 单栏宿主（编辑器抽屉）里对面那格看不见，
        // 「添加到对面那个包」就成了往看不见的地方写东西。两边停在**同一个包**里时
        // 也不给：那等于把条目复制进它自己，只会多出一条重复项。
        otherArchiveWritable = !singlePane &&
            state[if (side == Side.LEFT) Side.RIGHT else Side.LEFT].let { other ->
                val arch = other.archive
                arch != null && arch.format.writable &&
                    arch.archivePath != pane.archive?.archivePath
            },
        edgeSwipeWidthPx = edgeSwipeWidthPx,
        modifier = Modifier.fillMaxSize()
    )
    }

    // ==================== .bak 恢复确认（g11）====================
    bakRestore?.let { entry ->
        val targetName = entry.name.dropLast(4)
        val targetExists = File(entry.path).parentFile?.let { File(it, targetName).exists() } == true
        UnifiedDialog(
            onDismissRequest = { bakRestore = null },
            title = Str.get(R.string.files_bak_restore_title),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = Str.get(R.string.files_bak_restore_message, entry.name, targetName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (targetExists) {
                        Text(
                            text = Str.get(R.string.files_bak_restore_note, targetName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                UnifiedDialogTextButton(onClick = {
                    bakRestore = null
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            runCatching { FileOps.restoreBak(File(entry.path)) }.getOrNull()
                        }
                        if (res == null) {
                            AppToast.warning(
                                context,
                                Str.get(R.string.files_bak_restore_failed, entry.name)
                            )
                        } else {
                            if (res.backedUp != null) {
                                AppToast.success(
                                    context,
                                    Str.get(
                                        R.string.files_bak_restore_replaced,
                                        res.target.name,
                                        res.backedUp
                                    )
                                )
                            } else {
                                AppToast.success(
                                    context,
                                    Str.get(R.string.files_bak_restore_done, res.target.name)
                                )
                            }
                            vm.refresh(side)
                            vm.highlightCreatedArchive(side, res.target.path)
                        }
                    }
                }) { Text(Str.get(R.string.files_bak_restore_confirm)) }
            },
            dismissButton = {
                UnifiedDialogTextButton(onClick = { bakRestore = null }) {
                    Text(Str.get(R.string.cancel))
                }
            }
        )
    }
}

// ==================== 分隔条 ====================

/**
 * 可拖分隔条：拖动调比例（25%–75% 之间，避免把任一窗格拖没），**双击复位 50%**（问题 1 已拍板）。
 * 拖动过程中的比例与回调都走 `rememberUpdatedState`，**不 key 进 pointerInput**——
 * 否则每次比例变化都会重启手势，拖到一半就断。
 */
@Composable
private fun DragDivider(
    split: Float,
    totalPx: Float,
    /** 用户反馈 d14「不可变」档：锁死时拖动与双击复位都不响应。 */
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
    /** 抬手 / 手势取消时回调 —— 「可变」档在这里落盘，拖动过程中每帧写 prefs 太重。 */
    onDragEnd: () -> Unit = {},
    onReset: () -> Unit
) {
    val splitState = rememberUpdatedState(split)
    val totalState = rememberUpdatedState(totalPx)
    val enabledState = rememberUpdatedState(enabled)
    val sink = rememberUpdatedState(onChange)
    val dragEndSink = rememberUpdatedState(onDragEnd)
    val resetSink = rememberUpdatedState(onReset)

    Box(
        modifier = Modifier
            .width(14.dp)
            .fillMaxHeight()
            .pointerInput(totalPx) {
                detectDragGestures(
                    onDragEnd = { dragEndSink.value() },
                    onDragCancel = { dragEndSink.value() }
                ) { change, drag ->
                    // 「不可变」档：手势照收但什么都不动 —— 分隔条看得见摸得着，就是拖不动
                    if (!enabledState.value) return@detectDragGestures
                    change.consume()
                    val total = totalState.value
                    if (total > 0f) {
                        val nextPx = (splitState.value * total + drag.x)
                            .coerceIn(total * 0.25f, total * 0.75f)
                        sink.value(nextPx / total)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (enabledState.value) resetSink.value()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.outline.copy(
                        alpha = if (splitState.value in 0.49f..0.51f) 0.55f else 0.35f
                    ),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

// ==================== 底栏 ====================

/** 图标 + 极小文字标签；**长按给完整文字说明**（调研结论 8：纯图标在弱描边下可发现性不足）。 */
@Composable
private fun RowScope.BarButton(
    icon: ImageVector,
    label: String,
    hint: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            // k.txt 第 6 条：底栏按键的涟漪收进 12dp 圆角，不再横贯一条矩形。
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = { AppToast.info(context, hint) }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 常态底栏 5 键：4 个导航 + 「＋ 新建」（4.1；新建框随 M3 一起上，不再是死按钮）。 */
@Composable
internal fun NormalBar(
    canBack: Boolean,
    canForward: Boolean,
    canUp: Boolean,
    canSync: Boolean,
    canCreate: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSync: () -> Unit,
    onUp: () -> Unit,
    onCreate: () -> Unit,
    /** 底栏上滑（§4.9）：打开书签/历史抽屉。 */
    onSwipeUp: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            // l.txt 第 15 条：底栏在新拟态下整条浮起（透明底 + 双侧柔影），
            // 下面的页面渐变照常透出；关闭新拟态时这一层什么都不画。
            .neuChrome()
            .background(MaterialTheme.colorScheme.surface)
            // 用户反馈①：文件页全屏后这条底栏下面**没有**底部导航了，系统手势条会直接压上来
            .navigationBarsPadding()
            // §4.9「在底栏上滑打开底部抽屉」。用 detectVerticalDragGestures 而不是
            // pointerInput 里手算 —— 前者已把「按下后才算竖向拖」处理好，不会把
            // 误触的横向滑动也算成上滑。阈值 24dp 挡掉手指轻微抖动。
            .pointerInput(Unit) {
                var startY = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset -> startY = offset.y },
                    onDragEnd = {},
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (startY - change.position.y > 24.dp.toPx() && dragAmount < 0) {
                            onSwipeUp()
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 顺序按用户反馈②定：退 → 进 → 新建 → 同步 → 上一级（新建要在中间，拇指最短路径）
        BarButton(Icons.Default.ArrowBack, Str.get(R.string.files_back), Str.get(R.string.files_back_hint), canBack, onBack)
        BarButton(Icons.Default.ArrowForward, Str.get(R.string.files_forward), Str.get(R.string.files_forward_hint), canForward, onForward)
        BarButton(Icons.Default.Add, Str.get(R.string.files_new), Str.get(R.string.files_new_hint), canCreate, onCreate)
        BarButton(Icons.Default.Sync, Str.get(R.string.files_sync), Str.get(R.string.files_sync_hint), canSync, onSync)
        BarButton(Icons.Default.ArrowUpward, Str.get(R.string.files_up), Str.get(R.string.files_up_hint), canUp, onUp)
    }
}

/**
 * 多选态底栏：常驻「已选 N 项」计数 + **只动选择**的四个键（用户反馈④）。
 *
 * 这一栏刻意**不放**复制 / 移动 / 删除 —— 它们是文件动作，走长按菜单；
 * 放在这里既挤爆 360dp 的屏宽，也把「选择」和「操作」两件事混在一层。
 * 「说明」把两笔的锚点模型讲清楚，免得手势靠猜。
 */
@Composable
internal fun MultiSelectBar(
    count: Int,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onSameType: () -> Unit,
    onExit: () -> Unit
) {
    var helpOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            // l.txt 第 15 条：与常态底栏同一套新拟态；同时补上导航栏内边距，
            // 免得全屏时手势条直接压在多选按钮上。
            .neuChrome()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Str.get(R.string.files_selected_count, count),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp)
        )
        // 键序与参考实现一致：全选 / 反选 / **中间的 X = 退出** / 同类型 / 说明
        BarButton(Icons.Default.SelectAll, Str.get(R.string.files_select_all), Str.get(R.string.files_select_all), true, onSelectAll)
        BarButton(Icons.Default.Sort, Str.get(R.string.files_select_inverse), Str.get(R.string.files_select_inverse), true, onInvert)
        BarButton(Icons.Default.Close, Str.get(R.string.files_select_exit), Str.get(R.string.files_select_exit), true, onExit)
        BarButton(Icons.Default.FilterList, Str.get(R.string.files_select_same_type), Str.get(R.string.files_select_same_type), true, onSameType)
        BarButton(Icons.Default.Help, Str.get(R.string.files_gesture_help_title), Str.get(R.string.files_gesture_help_title), true) { helpOpen = true }
    }
    if (helpOpen) {
        UnifiedDialog(
            onDismissRequest = { helpOpen = false },
            title = Str.get(R.string.files_gesture_help_title),
            // 内容必须**点名给** content：UnifiedDialog 的最后一个形参是 widthFraction，
            // 用尾随语法糖会把它当成那个 Float —— 编译器直接报「缺 content」
            content = {
                Text(
                    text = Str.get(R.string.files_gesture_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.ok),
                    variant = ButtonVariant.Primary,
                    onClick = { helpOpen = false }
                )
            }
        )
    }
}

// ==================== 菜单项 ====================

/**
 * 排序弹窗里的复选行（用户反馈⑩）。
 *
 * 用 [Checkbox] 而不是「打勾图标」：下拉菜单那种给每项末尾补一个对勾的写法，
 * 放进对话框里读不出「这是个可勾选项」，勾选语义交给标准控件。
 *
 * 文件级 `private` 放开：压缩框与解压框（`FileOpsDialogs.kt`）也用同一行 ——
 * 同一种控件在同一个包里长出两种样子，才是真的难看。
 */
@Composable
fun DialogCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

// ==================== 打开文件（4.6 分流的第一步）====================

/**
 * 上下文菜单的动作分发（4.3/4.4/4.5）。
 *
 * 方向类动作（复制/移动）的**默认目标是另一窗格当前路径**（4.4 第 2 条）——
 * 菜单文案里的箭头已经写明方向，这里照着执行即可。
 */
/**
 * g4：短按「复制/剪切」时，若目标是另一窗格且是个**可写的真实目录**，就直接开始传输。
 *
 * 返回 false 表示这条路走不了，调用方回落到「拿起 → 粘贴」（单窗格、两窗同目录、
 * 对面停在归档里、对面目录已不可用）。判据只看「目标是否真实存在且可写」——
 * 其余交给 `startTransfer` 自己的校验与提示。
 */
private fun directTransferToOtherPane(
    vm: FileManageViewModel,
    state: FileManageUiState,
    side: Side,
    kind: OpKind,
    targets: List<String>
): Boolean {
    if (!state.dualEnabled || targets.isEmpty()) return false
    val other = state[if (side == Side.LEFT) Side.RIGHT else Side.LEFT]
    val cwd = other.cwd
    // 同一目录里再复制一份给自己毫无意义；归档内落点走「添加」，不在这一格的语义里
    if (cwd.isEmpty() || cwd == state[side].cwd) return false
    if (VirtualPath.isInArchive(cwd)) return false
    // 只读 / 已消失的目录：让 startTransfer 走它会先弹「目标无效」，这里直接放弃，
    // 退回「拿起」至少不会让用户以为操作已经开始了
    val dir = File(cwd)
    if (!dir.isDirectory || !dir.canWrite()) return false
    vm.startTransfer(kind, targets, cwd)
    return true
}

/**
 * g4：菜单格的长按。
 *
 * 只有「复制/剪切」有长按语义 —— 一律走「拿起」（f3 的现行效果）；
 * 别的格长按回落到短按，避免出现「长按了却没反应」的死格。
 */
private fun handleContextActionLong(
    scope: CoroutineScope,
    context: Context,
    vm: FileManageViewModel,
    state: FileManageUiState,
    action: ContextAction,
    onRequestOpenWith: (String) -> Unit
) {
    when (action) {
        ContextAction.COPY, ContextAction.MOVE -> {
            state.contextMenu ?: return
            val targets = vm.menuTargets()
            vm.dismissContextMenu()
            vm.exitMultiSelect()
            vm.pickupTransfer(
                if (action == ContextAction.COPY) OpKind.COPY else OpKind.MOVE,
                targets
            )
        }
        else -> handleContextAction(scope, context, vm, state, action, onRequestOpenWith)
    }
}

private fun handleContextAction(
    scope: CoroutineScope,
    context: Context,
    vm: FileManageViewModel,
    state: FileManageUiState,
    action: ContextAction,
    onRequestOpenWith: (String) -> Unit
) {
    val cm = state.contextMenu ?: return
    val side = cm.side
    val targets = vm.menuTargets()

    when (action) {
        ContextAction.COMPRESS -> {
            // 先关菜单再算可压项：原先只有「一个都不能压」那支关，成功支不关 ——
            // 压缩框叠在没关的菜单上，退回来菜单还在（用户反馈⑫）。
            vm.dismissContextMenu()
            // 只有**真实文件**能压：归档内条目要压得先解出来，语义不清 —— 明说，别默默丢掉。
            val real = targets.filter { !VirtualPath.isInArchive(it) }
            if (real.isEmpty()) {
                AppToast.info(context, Str.get(R.string.files_archive_extract_first))
            } else {
                vm.requestCompress(side, real)
            }
        }

        ContextAction.EXTRACT -> {
            // 菜单先关（用户反馈⑫：点完动作弹窗必须收起来）。落点在**下一个弹窗**里选，
            // 这里只把包递过去。用长按的那条而不是 targets —— 菜单门控只认 entry，
            // 整批时「第一个归档」是谁全看排序，解错了包比不解更糟。
            vm.dismissContextMenu()
            val path = cm.path
            if (VirtualPath.isInArchive(path) || !File(path).isFile || !extractableName(File(path).name)) {
                AppToast.info(context, Str.get(R.string.files_archive_broken))
            } else {
                vm.requestExtract(side, path)
            }
        }

        // f11：「添加」——把选中项加进**另一窗格当前所在的归档**。
        // 菜单只在那个包可写时才摆这一格（`otherArchiveWritable`），落点是包内当前目录；
        // 源在别的包里也行，VM 会先在私有暂存区解出来再往里加。
        ContextAction.ADD -> {
            vm.dismissContextMenu()
            vm.exitMultiSelect()
            vm.addToOtherArchive(side, targets)
        }

        // f3：双窗格不再「点一下就甩到对面」—— 复制/移动一律**拿起**，自己走过去再粘
        // （右下角浮出粘贴钮），与单窗口完全同一套口径。原先并存的「复制到…/移动到…」
        // 两格已删：同一份菜单里摆两套复制语义（一套点一下、一套要走过去），
        // 扫一眼分不出哪格是哪格；落点选择交给「拿起 → 走到哪粘到哪」。
        // g4：双窗格下**短按直接送对面**；单窗格 / 对面不是可写落点时，退回「拿起」
        // （f3 那套：右下角出现粘贴按钮，走到哪粘到哪）。长按永远是「拿起」，见
        // [handleContextActionLong]——想慢慢选落点的人有确定的入口，不会被迫走快路。
        ContextAction.COPY -> {
            vm.dismissContextMenu()
            vm.exitMultiSelect()
            if (!directTransferToOtherPane(vm, state, side, OpKind.COPY, targets)) {
                vm.pickupTransfer(OpKind.COPY, targets)
            }
        }

        ContextAction.MOVE -> {
            vm.dismissContextMenu()
            vm.exitMultiSelect()
            if (!directTransferToOtherPane(vm, state, side, OpKind.MOVE, targets)) {
                vm.pickupTransfer(OpKind.MOVE, targets)
            }
        }

        ContextAction.DELETE -> {
            vm.dismissContextMenu()
            // t.txt 第 6 条（用户）：**不要在这里退出多选** —— 确认框还没答，用户点
            // 「取消」回来时选中集已经没了，等于白选一遍。退出多选挪到「确认删除」之后。
            vm.requestDelete(side, targets)
        }

        ContextAction.RENAME -> {
            vm.dismissContextMenu()
            // 单条走普通重命名框，批量走模板式批量重命名 —— 同一个菜单项，两种语义。
            // 原先这里在批量态直接什么都不做，等于菜单上摆了个死按钮。
            if (cm.batch) vm.requestBatchRename(side, targets)
            else vm.requestRename(side, cm.path)
        }

        ContextAction.PROPERTIES -> {
            vm.dismissContextMenu()
            // n.txt 第 1 条：把**整批**目标交给属性框；多条时由 UI 汇总成整体属性
            //（原先只取第一条，用户看到的是第一项的属性，与多选预期不符）。
            vm.requestProperties(targets)
        }

        // 「选择」= 进多选并把这条定为锚点（4.3：长按不直接进多选）
        ContextAction.SELECT -> {
            vm.dismissContextMenu()
            vm.startSelect(side, cm.path)
        }

        // 分享（子 agent 对比建议：上下文菜单补「分享」）：**只发真实文件** ——
        // 归档内条目没有真实路径，FileProvider 签不出 URI，发出去必然是个坏流，
        // 还不如明说先解压。多选时一次走 ACTION_SEND_MULTIPLE。
        ContextAction.SHARE -> {
            vm.dismissContextMenu()
            val real = targets.filter { !VirtualPath.isInArchive(it) }
            if (real.isEmpty()) {
                AppToast.info(context, Str.get(R.string.files_archive_extract_first))
            } else {
                context.shareFiles(real.map(::File))
            }
        }

        // 「打开方式」（用户反馈③）：这个文件**有没有内置能力接得住**决定了要不要弹框。
        // 一个都没有（未知二进制、无后缀文件…）就不摆空框 —— 直接把系统的抬上来，
        // 正对用户那句「默认显示内置，如果没有则直接显示系统的」。
        //
        // j.txt 第 10 条：归档内条目**直接自动解一条**再弹框，不再说「先解压」。
        // 用户已经明确点了「打开方式」，此时让他先退出、解压、再回来点一遍纯属绕路；
        // 解出来的是临时副本，位置在 cache/archive_open，不污染用户目录。
        ContextAction.OPEN_WITH -> {
            vm.dismissContextMenu()
            if (cm.isDir) return
            val path = cm.path
            if (VirtualPath.isInArchive(path)) {
                val split = VirtualPath.split(path) ?: return
                val archive = File(split.first)
                if (!archive.isFile) {
                    AppToast.warning(context, Str.get(R.string.files_archive_broken))
                    return
                }
                AppToast.info(context, Str.get(R.string.files_archive_opening))
                scope.launch {
                    val copy = try {
                        ArchiveOpen.extractEntry(
                            context.applicationContext, archive, split.second
                        )
                    } catch (e: ArchiveEntryOpenException) {
                        AppToast.warning(context, Str.get(archiveEntryFailureRes(e)))
                        return@launch
                    } catch (e: Exception) {
                        AppLog.w(TAG, "open archived entry failed: ${e.message}")
                        AppToast.warning(context, Str.get(R.string.files_archive_broken))
                        return@launch
                    }
                    onRequestOpenWith(copy.absolutePath)
                }
            } else {
                // l.txt 第 9 条：没有内置能力也照样进本应用的「打开方式」弹窗 ——
                // 里面本来就有「系统建议 / 全部」两栏，比单纯甩给系统选择器更好用。
                onRequestOpenWith(path)
            }
        }

        ContextAction.CONTRAST -> {
            // 作用域**只能用 when 之前取的那份 targets**，绝不能在这里再读一次：
            // menuTargets() 第一行就是「contextMenu 为 null 直接返回空」，而下面这句
            // dismissContextMenu() 恰好把它置空 —— 关完再读必然是空列表，
            // `size != 2` 永远命中，菜单就成了点了没反应的死按钮（6.3.0 上线即有的 bug）。
            vm.dismissContextMenu()
            // 门在菜单那儿已卡过「恰好两条」，这里再核一次是不是**同一种**：
            // 两个文件、或两个文件夹都行（文件夹走递归比对），一文件一目录没有可比的
            // 东西 —— 混着放进去，对目录读字节只会换来一句含糊的「读不出来」。
            when {
                targets.size != 2 -> Unit
                File(targets[0]).isDirectory != File(targets[1]).isDirectory ->
                    AppToast.info(context, Str.get(R.string.cmp_not_file))
                else -> CompareActivity.start(context, targets[0], targets[1])
            }
        }

        // 「隐藏 / 取消隐藏」（MT.md §4.8 手动隐藏集）。
        // 落子判定**只看长按的那条**（cm.path）—— 菜单文案正是按它算的，两边同源才不会
        // 出现「写着隐藏、点了却变回可见」。整批时按这一条统一：不可见 → 全部藏起来，
        // 可见 → 全部放出来，对本就处在目标状态的条目是幂等操作。
        ContextAction.HIDE -> {
            val hide = cm.path !in state[side].manualHidden
            vm.dismissContextMenu()
            vm.setManualHidden(targets, hide)
            // 藏完这一行立刻就没了，用户很容易以为点丢了 —— 说清楚去哪儿把它找回来
            if (hide) AppToast.info(context, Str.get(R.string.files_hidden_toast, targets.size))
        }

        // 「添加书签」（用户反馈 e4）。与页头 ⋮ 菜单的「收藏当前目录」同一套落子，
        // 只是对象换成长按的这一条。`putBookmark` 按路径去重，重复点是**更新**不是多一条。
        // 菜单那侧已按「只给真实目录、整批不给、归档内不给」门控，这里拿到的必是真实目录。
        ContextAction.BOOKMARK -> {
            val path = cm.path
            vm.dismissContextMenu()
            FilePlaces.putBookmark(File(path).name, path, "")
            AppToast.info(context, Str.get(R.string.files_bookmark_added))
        }
    }
}

/** 从 [File] 造一个仅供 [openEntry] 使用的最小 [FileEntry]。 */
private fun entryOf(file: File): FileEntry = FileEntry(
    path = file.absolutePath,
    name = file.name,
    isDir = file.isDirectory,
    size = file.length(),
    modified = file.lastModified()
)

// ==================== 打开文件（4.6 分流的第一步，续）====================

/**
 * 文本类扩展名 → 进内置编辑器（验收 15：文本文件点开 → CodeEditor 带路径）。
 *
 * k.txt 第 1 条：这里**直接复用文件类型注册表的 [FileTypes.TEXT_EXTENSIONS]** ——
 * 原先文件页维护第二份短名单，编辑器支持、文件页却没列出的后缀（`.svelte`、
 * `.glsl`、`.lrc` 等）不会走编辑器，还会在文件列表里掉进「未知文件」灰图标。
 * 打开分流、图标判定、内置能力表从此共用同一份清单。
 */
val TEXT_EXTENSIONS: Set<String> = FileTypes.TEXT_EXTENSIONS

/**
 * markdown 后缀 —— **不从 [TEXT_EXTENSIONS] 里挑**，因为它俩回答的是不同的问题：
 * 那份白名单问「编辑器接不接得住这个文件」（`.md` 当然是），这份问
 * 「点下去该进**阅读页**还是进编辑器」（进阅读页）。
 *
 * 两处共用一份口径（文件页的分流 + 「打开方式」里的内置能力），
 * 少了任一边都会退化成「列表里有、点了却没有」。
 */
val MARKDOWN_EXTENSIONS: Set<String> get() = FileTypes.MARKDOWN

/**
 * 打开**归档内的文本条目**（M4b「编辑归档内文本」）：
 *
 * 流式解到编辑临时副本区（`ArchiveEdit`）→ 带 `origin_archive` / `origin_entry`
 * 两个来源 extras 起编辑器。编辑器据此知道「这份内容其实住在哪个归档的哪个条目里」，
 * 保存成功后额外询问要不要写回归档。
 *
 * 用调用方传进来的 [scope] 而不是自己开协程：它是 `rememberCoroutineScope()`，
 * 页面离开即取消 —— 解到一半用户退出文件页时不会继续往缓存里写。
 */
private fun openArchiveEntry(
    scope: CoroutineScope,
    context: Context,
    entry: FileEntry
) {
    val split = VirtualPath.split(entry.path) ?: return
    val (archivePath, internalPath) = split
    val archive = File(archivePath)
    if (!archive.isFile) {
        // 归档本身已不在（被删/被移走）—— 直接说，不要让用户盯着「解压中」等一个永远不来的结果。
        AppToast.warning(context, Str.get(R.string.files_archive_broken))
        return
    }
    // e15 **P1-6**「嵌套归档写回给提示」：`VirtualPath.split` 只切**第一个** `!/`，
    // 包中包拆出来的 internal 仍是 `b.zip!/x.txt` —— 拿它去 `a.zip` 里查条目必然
    // 一条都对不上，报出来的却是「压缩包已损坏」，把用户指向完全错误的方向。
    // 这种包本版进不去（见上面对嵌套归档的那条拦截），照实说清并给退路。
    if (internalPath.contains("!/")) {
        AppToast.warning(context, Str.get(R.string.files_archive_nested_unsupported))
        return
    }
    scope.launch {
        val copy = try {
            ArchiveEdit.extract(context.applicationContext.cacheDir, archive, internalPath)
        } catch (e: Exception) {
            AppLog.w(TAG, "extract entry for edit failed: ${e.message}")
            AppToast.warning(context, Str.get(archiveEntryFailureRes(e)))
            return@launch
        }
        try {
            context.startActivity(
                Intent(context, CodeEditorActivity::class.java).apply {
                    putExtra("file_path", copy.absolutePath)
                    // 写回来源（编辑器据此在保存后询问是否写回归档）
                    putExtra("origin_archive", archivePath)
                    putExtra("origin_entry", internalPath)
                }
            )
        } catch (e: Exception) {
            // 编辑器起不来就退一步：副本是真的，至少能交给系统打开。
            AppLog.w(TAG, "open editor failed: ${e.message}")
            openWithSystem(context, copy)
        }
    }
}

/**
 * e10：**归档内的 APK 也能看安装包信息**。
 *
 * 原先包里点 `.apk` 只会得到一句「文件要先解压才能打开」—— 可用户正是在包里翻到
 * 这个安装包的，让他先手动解压再去找一遍，等于把一件事拆成两件。这里把它解到
 * **信息页专用的临时副本区**（每次用之前清一遍，缓存里最多留一份），解完直接开
 * [ApkInfoActivity] —— 那一页读的是清单、权限与签名，**不安装、不 root**。
 *
 * 体积闸 [APK_INFO_MAX_BYTES]：自动往缓存里解一个 2GB 的东西是灾难（同 M4d 对
 * 归档内视频的口径），而信息页真正要的只有清单那几段。超了就退回原来那句话，
 * 让人自己决定怎么解。
 */
private fun openArchiveApkInfo(
    scope: CoroutineScope,
    context: Context,
    entry: FileEntry
) {
    val split = VirtualPath.split(entry.path) ?: return
    val (archivePath, internalPath) = split
    val archive = File(archivePath)
    if (!archive.isFile) {
        // 归档本身已不在（被删/被移走）—— 直接说，不要让用户盯着「解压中」等一个永远不来的结果。
        AppToast.warning(context, Str.get(R.string.files_archive_broken))
        return
    }
    // 与 openArchiveEntry 同一条拦截：包中包拆出来的 internal 仍带 `!/`，
    // 拿去查条目必然一条都对不上，报出来的却是「压缩包已损坏」，把人指向完全错误的方向。
    if (internalPath.contains("!/")) {
        AppToast.warning(context, Str.get(R.string.files_archive_nested_unsupported))
        return
    }
    // 体积闸：超了就退回原来那句话，让人自己决定怎么解。条目大小未知（0）时放行 ——
    // 宁可多占一份缓存，也别把「看个信息」变成点不动。
    if (entry.size > APK_INFO_MAX_BYTES) {
        AppToast.info(context, Str.get(R.string.files_archive_extract_first))
        return
    }
    AppToast.info(context, Str.get(R.string.apk_extract_running))
    scope.launch {
        // 专用子目录 + 用前整棵清空（副本是 `目录/摘要/条目路径` 这样嵌套的，
        // 只删一层清不掉）：信息页读完就不再需要这份副本，缓存里最多同时留一份。
        val dir = File(context.cacheDir, "apkinfo")
        dir.deleteRecursively()
        val copy = try {
            ArchiveEdit.extract(dir, archive, internalPath)
        } catch (e: Exception) {
            AppLog.w(TAG, "extract apk for info failed: ${e.message}")
            AppToast.warning(context, Str.get(archiveEntryFailureRes(e)))
            return@launch
        }
        try {
            ApkInfoActivity.start(context, copy.absolutePath)
        } catch (e: Exception) {
            AppLog.w(TAG, "open apk info failed: ${e.message}")
            AppToast.warning(context, Str.get(R.string.apk_info_load_failed))
        }
    }
}

/** e10：包内 APK 解副本的体积上限。信息页只要清单与签名，不值得为它搬一个巨包。 */
private const val APK_INFO_MAX_BYTES = 512L * 1024 * 1024

/**
 * 包内条目取不出来时该说哪句话。
 *
 * 用户明确要求：**列得出目录的包不许再说「已损坏」**。这里按原因分成
 * 「缺密码」与「这个条目要整包解压」两句；认不出原因时也归后者 —— 那是更常见、
 * 也是用户真能自己动手解决的那一种。
 */
private fun archiveEntryFailureRes(e: Throwable): Int {
    if (e is ArchiveEntryOpenException) {
        return if (e.needsFullExtract) R.string.files_archive_entry_need_extract
        else R.string.files_archive_entry_password
    }
    val text = (e.message ?: "").lowercase()
    val password = text.contains("password") || text.contains("encrypted") ||
        text.contains("密码") || text.contains("加密")
    return if (password) R.string.files_archive_entry_password
    else R.string.files_archive_entry_need_extract
}

/**
 * 打开**归档内的任意条目**（f.txt 追加反馈：包里的文件都要能打开，包括包里的包）。
 *
 * 分工：
 * - 文本 → [openArchiveEntry]：解到编辑临时区并带「写回来源」，保存后可以问要不要写回；
 * - APK → [openArchiveApkInfo]：解到信息页专用副本区，带体积闸；
 * - 其余（图片 / 字体 / 音视频 / 文档 / 未知后缀）→ 解一条到 `cache/archive_open/`，
 *   然后走**与真实文件完全相同**的 [openEntry] 分流；
 * - 包里的包 → 同上解出，再 `mountArchive` 记住来源后原地进包。
 *
 * 失败**分两种说**（用户明确要求不要再说「归档已损坏」）：缺密码 vs 这个条目必须
 * 整包解压。包本身刚刚列得出目录，它没坏。
 */
private fun openArchivedEntry(
    scope: CoroutineScope,
    context: Context,
    vm: FileManageViewModel,
    side: Side,
    entry: FileEntry,
    onRequestOpenWith: (String) -> Unit
) {
    val split = VirtualPath.split(entry.path) ?: return
    val (archivePath, internalPath) = split
    val archive = File(archivePath)
    if (!archive.isFile) {
        AppToast.warning(context, Str.get(R.string.files_archive_broken))
        return
    }
    if (internalPath.contains("!/")) {
        AppToast.warning(context, Str.get(R.string.files_archive_nested_unsupported))
        return
    }
    // y.txt 第 2 条（用户）：这个后缀被**显式**绑成「浏览压缩包」时，优先于下面
    // 「文本 → 编辑器」的快捷路。否则 `.tpk` 这种「未知后缀按文本处理」的包，
    // 在归档里点开就永远进编辑器 —— 用户设的默认等于没设。
    val boundArchive = FilePrefs.openWithDefault(FileTypes.extensionOf(entry.name)) == TARGET_ARCHIVE
    if (!boundArchive && FileTypes.isText(entry.name)) {
        openArchiveEntry(scope, context, entry)
        return
    }
    if (!boundArchive && FileTypes.isApk(entry.name)) {
        openArchiveApkInfo(scope, context, entry)
        return
    }

    val isArchive = boundArchive || looksLikeArchive(entry.name)
    val originCwd = vm.state.value[side].cwd
    AppToast.info(context, Str.get(R.string.files_archive_opening))
    scope.launch {
        val copy = try {
            ArchiveOpen.extractEntry(context.applicationContext, archive, internalPath)
        } catch (e: ArchiveEntryOpenException) {
            AppToast.warning(context, Str.get(archiveEntryFailureRes(e)))
            return@launch
        } catch (e: Exception) {
            AppLog.w(TAG, "extract archived entry failed: ${e.message}")
            AppToast.warning(context, Str.get(archiveEntryFailureRes(e)))
            return@launch
        }
        if (isArchive) {
            // 包里的包：先记住来源，再进。它的「上一级」由 VM 送回父包原位，
            // 而不是把用户丢到 cache 目录里。
            vm.mountArchive(copy.absolutePath, originCwd)
            vm.navigateTo(side, VirtualPath.join(copy.absolutePath, ""))
        } else {
            // 走到这里说明既不是压缩包、也没被绑成「浏览压缩包」（那个分支在上面
            // 已经挂载并 return 了），所以不需要再传 onBrowseArchive。
            openEntry(
                context,
                entryOf(copy),
                onRequestOpenWith = onRequestOpenWith
            )
        }
    }
}

private fun openEntry(
    context: Context,
    entry: FileEntry,
    siblingImages: List<String> = emptyList(),
    /** 用户反馈 d5：未知后缀要走**本应用**的「打开方式」弹窗，把选择权摆出来。 */
    onRequestOpenWith: (String) -> Unit,
    /**
     * w.txt 第 4 条（用户）：把某个后缀的默认打开方式绑成「浏览压缩包」后，
     * 打开文件要真的在当前窗格挂载它 —— 这个能力没有独立 Activity，
     * 只能由文件页把落点递进来；为 null（归档内解出来的副本等）时退回原分流。
     */
    onBrowseArchive: ((String) -> Unit)? = null
) {
    val file = File(entry.path)
    val ext = FileTypes.extensionOf(entry.name)
    // 该后缀设过默认打开方式（管理页新增 / 打开方式弹窗长按设的）→ 先走它。
    // 起不来就落到下面原有分流，不让「设了个失效默认」把打开这件事卡死 ——
    // 目标应用被卸载、内置能力改名都会走这条兜底。归档内是虚拟路径，
    // FileProvider 签不出 URI，直接不参与。
    if (!entry.isDir && !VirtualPath.isInArchive(entry.path)) {
        FilePrefs.openWithDefault(ext)?.let { target ->
            // 「浏览压缩包」必须在这里处理：launchTarget 那边没有窗格上下文，
            // 只会弹一句「请在文件列表中打开」，用户看到的就是「选了却没生效」。
            //
            // 这里**不看后缀**：用户把某个后缀显式绑成「浏览压缩包」，就是要按内容
            // 打开它（典型是 `a.b` 这种被改了名的 7z/zip）—— 真格式由
            // detectArchiveFormat 读魔数判断，后缀只作兜底。
            if (target == TARGET_ARCHIVE && onBrowseArchive != null) {
                onBrowseArchive(file.absolutePath)
                return
            }
            if (launchTarget(context, target, file)) return
        }
    }
    // 分流依据**全部来自 `assets/config/file_types.json`**（m.txt 第 3 条）。这里不再按
    // 后缀写死判断：新增/调整类型只改 JSON，文件页与「打开方式」自动同步。
    //
    // Markdown：`.md` 默认进**内置阅读页**（渲染后的样子），要改字再从「打开方式」里
    // 挑编辑器 —— 两者摆在同一张网格里，谁当默认由用户在管理页定。
    // 必须排在文本编辑器**之前**：`.md` 也能被编辑器接住，排后面就永远轮不到阅读页。
    if (FileTypes.isMarkdown(entry.name)) {
        try {
            DocViewerActivity.start(context, file.absolutePath, entry.name)
            return
        } catch (e: Exception) {
            // 阅读页起不来就落到编辑器，再不行还有系统分流 —— 不让用户点了没反应
            AppLog.w(TAG, "open markdown viewer failed: ${e.message}")
        }
    }
    // 注意是 isEditableText 而不是 isText：后者是黑名单制（未知后缀也算文本），
    // 用在这里会把「后缀不认识」的文件全部塞进编辑器，m.txt 第 10 条要的
    // 「未知后缀摆出打开方式」就永远走不到。
    if (FileTypes.isEditableText(entry.name)) {
        try {
            context.startActivity(
                Intent(context, CodeEditorActivity::class.java).apply {
                    putExtra("file_path", file.absolutePath)
                }
            )
            return
        } catch (e: Exception) {
            // 编辑器起不来就继续走系统分流，不让用户点了没反应
            AppLog.w(TAG, "open editor failed: ${e.message}")
        }
    }
    // 图片类（4.6）→ 内置查看器：捏合缩放 + 目录内左右翻页 + EXIF + GIF。
    // 起失败就退回系统打开，不让用户点了没反应（例如极个别厂商 ROM 拦自启 Activity）。
    if (FileTypes.isImage(entry.name)) {
        try {
            ImagePagerActivity.start(context, file.absolutePath, siblingImages)
            return
        } catch (e: Exception) {
            AppLog.w(TAG, "open image viewer failed: ${e.message}")
        }
    }
    // 字体（ttf/otf/ttc）→ 内置查看器：试看 + 码位表 + 字体族名。同样失败就退系统分流。
    if (FileTypes.isFont(entry.name)) {
        try {
            FontViewerActivity.start(context, file.absolutePath)
            return
        } catch (e: Exception) {
            AppLog.w(TAG, "open font viewer failed: ${e.message}")
        }
    }
    // APK（4.6）→ 内置信息页：包名/版本/权限/签名 + 安装/分享。同样失败就退系统分流。
    if (FileTypes.isApk(entry.name)) {
        try {
            ApkInfoActivity.start(context, file.absolutePath)
            return
        } catch (e: Exception) {
            AppLog.w(TAG, "open apk info failed: ${e.message}")
        }
    }
    // m.txt 第 9 条：密钥库（JKS / PKCS12 / BKS）→ 内置查看 / 转换页。
    if (FileTypes.isKeystore(entry.name)) {
        try {
            KeystoreViewerActivity.start(context, file.absolutePath)
            return
        } catch (e: Exception) {
            AppLog.w(TAG, "open keystore viewer failed: ${e.message}")
        }
    }
    // l.txt 第 9 条：**没有预定内置打开方式**的后缀一律摆出本应用的「打开方式」弹窗
    //（内置 / 系统建议 / 全部 三个页签），不再直接甩给系统选择器。
    // 内置能力在上面已逐个接走（md / 文本 / 图片 / 字体 / apk）；走到这里就说明
    // 这个后缀本应用没有第一款应用 —— 用户要的是「能选的地方」，而不是系统那张
    // 什么都能接、却看不出哪个真能用的单子。
    onRequestOpenWith(file.absolutePath)
}

/**
 * 交给系统打开。URI 由 [com.UIN.Tool.utils.openFile] 经 FileProvider 签发
 * （白名单见 `res/xml/file_paths.xml`，N13 已放宽到 `/storage/` 下所有存储卷 →
 * DCIM/、Download/、SD 卡都能直接打开）。
 */
private fun openWithSystem(context: Context, file: File) {
    context.openFile(file)
}

// ==================== M3：操作对话框与进度（4.4/4.5/4.12）====================

/**
 * 把 M3 的所有一次性 UI（进度 / 撞名 / 删除确认 / 重命名 / 新建）收在一处。
 *
 * 单独抽成一个 composable 是为了让主页面的 Column 不被五个 `if` 淹掉 ——
 * 这些框彼此互斥，但互斥关系本身不值得在主流程里显式编码。
 */
@Composable
internal fun M3Dialogs(
    vm: FileManageViewModel,
    state: FileManageUiState
) {
    val context = LocalContext.current

    // ---- 七层进度对话框（4.12）----
    val op = state.op
    if (op is OpUiState.Running && !op.hidden) {
        UnifiedProgressDialog(
            state = op.progress.toUnifiedState(context, op.cancelling),
            onCancel = { vm.cancelOp() },
            onDismissRequest = { vm.hideOp() }
        )
    }

    // ---- 撞名三选（4.4）----
    if (op is OpUiState.Conflict) {
        FileConflictDialog(
            items = op.items,
            onDecision = { policy, applyAll -> vm.resolveConflict(policy, applyAll) },
            onCancel = { vm.resolveConflict(null, false) }
        )
    }

    // ---- 删除确认（4.5）----
    state.deleteRequest?.let { req ->
        FileDeleteDialog(
            request = req,
            onConfirm = { permanent ->
                val paths = req.paths
                // 确认后才收多选（t.txt 第 6 条：取消不能把选中状态一起清掉）。
                vm.exitMultiSelect()
                vm.startDelete(paths, permanent)
            },
            onDismiss = { vm.dismissDelete() }
        )
    }

    // ---- 重命名（4.5）----
    state.renameRequest?.let { req ->
        FileRenameDialog(
            request = req,
            onConfirm = { newName -> vm.performRename(req.side, req.path, newName) },
            onDismiss = { vm.dismissRename() }
        )
    }

    // ---- 批量重命名 ----
    state.batchRename?.let { req ->
        FileBatchRenameDialog(
            paths = req.paths,
            // 对话框算好整批新名直接交进来：预览即最终结果，VM 不再跑第二套展开逻辑
            onRename = { names ->
                vm.performBatchRename(req.side, req.paths, names)
            },
            onDismiss = { vm.dismissBatchRename() }
        )
    }

    // ---- 新建（4.5）----
    state.createRequest?.let { req ->
        FileCreateDialog(
            request = req,
            onNameChange = { vm.checkCreateName(req.side, it) },
            onCreate = { isDir, name ->
                val defaultName = if (isDir) Str.get(R.string.files_default_folder)
                else Str.get(R.string.files_default_file)
                vm.performCreate(req.side, isDir, name, defaultName)
            },
            onDismiss = { vm.dismissCreate() }
        )
    }

    // ---- 压缩（4.7）----
    state.compressRequest?.let { req ->
        FileCompressDialog(
            request = req,
            onConfirm = { name, format, password, level, encoding, keepMeta, splitSize, encryptHeader ->
                vm.performCompress(
                    name, format, password, level, encoding, keepMeta, splitSize, encryptHeader
                )
            },
            // 「加入已有压缩包」（e15 P1-10）：目标是已存在的包，与新建分流
            onAppend = { archiveName, mode, level ->
                vm.performCompressAppend(archiveName, mode, level)
            },
            onDismiss = { vm.dismissCompress() }
        )
    }

    // ---- 输出名撞车（反馈 e17 → f16 改单选框 + 跳过该文件）----
    state.compressConflict?.let { c ->
        FileCompressConflictDialog(
            conflict = c,
            onAppend = { vm.compressConflictAppend() },
            onNewName = { vm.compressConflictNew() },
            onSkip = { vm.dismissCompressConflict() },
            onDismiss = { vm.dismissCompressConflict() }
        )
    }

    // ---- 整包解压（用户反馈⑪：落点在弹窗里选；c14：换成四个落点）----
    state.extractRequest?.let { req ->
        FileExtractDialog(
            request = req,
            // 「另一窗口」= **长按那个包所在窗格**的对面，不是 active 对面 ——
            // 弹窗是模态的，这会儿 active 谁都可能已经变过，按 req.side 才是用户看到的那两格
            otherCwd = state[if (req.side == Side.LEFT) Side.RIGHT else Side.LEFT].cwd,
            onConfirm = { key, targetDir, restore, deleteSource, password ->
                vm.performExtract(key, targetDir, restore, deleteSource, password)
            },
            onDismiss = { vm.dismissExtract() }
        )
    }

    // ---- 归档内条目粘到另一窗格撞上加密包：先要密码再动手（b10 补口）----
    state.archivePassword?.let {
        FileArchivePasswordDialog(
            onConfirm = { pw -> vm.submitArchivePassword(pw) },
            onDismiss = { vm.dismissArchivePassword() }
        )
    }

    // ---- 属性（4.5；n.txt 第 1 条：多选时给整批的汇总属性）----
    if (state.propertiesPaths.isNotEmpty()) {
        val paths = state.propertiesPaths
        when {
            // 包内条目（e15 P1-7）：`File("x.zip!/a.txt")` 永远 exists()==false，
            // 原来一律落到 else 把框收掉 —— 看上去就是「点了属性没反应」。
            paths.size == 1 && VirtualPath.isInArchive(paths[0]) ->
                ArchiveEntryPropertiesDialog(paths[0], onDismiss = { vm.dismissProperties() })

            paths.size > 1 -> MultiFilePropertiesDialog(
                paths = paths,
                onDismiss = { vm.dismissProperties() }
            )

            else -> {
                val path = paths[0]
                val file = remember(path) { File(path) }
                if (file.exists()) {
                    FilePropertiesDialog(file = file, onDismiss = { vm.dismissProperties() })
                } else {
                    // 文件在弹框前被删了（另一窗格同步删掉等）——直接收起，别弹一个空框
                    LaunchedEffect(path) { vm.dismissProperties() }
                }
            }
        }
    }

    // ---- 解压完成 →「定位」回文件管理器（e15 P2-16）----
    // 落点要先被 UI 拿住才能回填：收集跑在 LaunchedEffect 里，那不是组合作用域，
    // 声明不出 remember 状态 —— 所以声明必须落在 LaunchedEffect **之前**。
    // Pair：先跳转后高亮（`navigateTo` 会把 highlightPath 清成 null，顺序反了高亮就没了）。
    var locateDone by remember { mutableStateOf<Pair<String, String?>?>(null) }
    locateDone?.let { (dir, highlight) ->
        UnifiedDialog(
            onDismissRequest = { locateDone = null },
            title = Str.get(R.string.files_extract_done),
            content = {
                Text(
                    text = dir,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                UnifiedDialogTextButton(onClick = {
                    // 先把框收了再跳：跳转本身会换掉一整格内容，留着框没意义
                    locateDone = null
                    vm.navigateTo(state.active, dir)
                    if (highlight != null) vm.highlightFromSearch(state.active, highlight)
                }) {
                    Text(Str.get(R.string.files_locate))
                }
            },
            dismissButton = {
                UnifiedDialogTextButton(onClick = { locateDone = null }) {
                    Text(Str.get(R.string.close))
                }
            }
        )
    }

    // ---- 一次性提示（操作收尾汇总 / 重命名与新建的结果）----
    LaunchedEffect(Unit) {
        vm.notices.collect { event ->
            when (event) {
                is NoticeEvent.ExtractLocated -> {
                    // 解压在后台跑完的，这会儿用户可能已经翻到别的目录了 ——
                    // 框里给的是**真正的落点**，不是「当前目录」这种随位置漂移的说法。
                    locateDone = event.dir to event.highlight
                }

                is NoticeEvent.OpDone -> {
                    val r = event.result
                    val kindLabel = when (event.kind) {
                        OpKind.COPY -> Str.get(R.string.files_copy)
                        OpKind.MOVE -> Str.get(R.string.files_move)
                        OpKind.DELETE -> Str.get(R.string.files_delete)
                        OpKind.COMPRESS -> Str.get(R.string.files_compress)
                    }
                    // 「跳过」既不是成功也不是失败，单列一句 —— 否则整批都跳过时会
                    // 弹「已复制 0 项」，用户以为操作没生效（其实是按他的选择没动）。
                    val skipNote = if (r.skippedCount > 0) {
                        " · " + Str.get(R.string.files_op_skipped, r.skippedCount.toString())
                    } else {
                        ""
                    }
                    when {
                        r.cancelled -> AppToast.warning(
                            context,
                            Str.get(R.string.files_op_cancelled, kindLabel)
                        )

                        r.failedCount == 0 && r.successCount == 0 && r.skippedCount > 0 ->
                            AppToast.info(
                                context,
                                Str.get(R.string.files_op_skipped, r.skippedCount.toString())
                            )

                        r.failedCount == 0 -> AppToast.success(
                            context,
                            (if (event.doneRes != null) {
                                Str.get(event.doneRes, r.successCount.toString())
                            } else {
                                Str.get(
                                    R.string.files_op_done,
                                    kindLabel,
                                    r.successCount.toString()
                                )
                            }) + skipNote
                        )

                        else -> AppToast.warning(
                            context,
                            Str.get(
                                R.string.files_op_done_with_errors,
                                kindLabel,
                                r.successCount.toString(),
                                r.failedCount.toString()
                            ) + skipNote + (r.firstError?.let { " · $it" } ?: "")
                        )
                    }
                }

                is NoticeEvent.Text -> AppToast.info(context, Str.get(event.res, event.arg1))
            }
        }
    }
}

/**
 * 单窗口「拿起待粘贴」的悬浮按钮（用户反馈㉒）。
 *
 * 右下角上下两个圆钮 —— **粘贴在上、关闭在下**（原话「右下角弹出一个粘贴图标，
 * 再下面弹出关闭图标」）。它们浮在列表**之上**而不是排进底栏：按钮要陪着用户
 * 走进别的目录继续可用，排进底栏就变成「必须先退回原处才能粘」。
 *
 * [modifier] 由调用方给 —— 锚点 `Alignment.BottomEnd` 属于 BoxScope，
 * 挪进这个函数就没有那个作用域了。
 */
@Composable
internal fun PendingTransferBar(
    onPaste: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 16.dp, bottom = 16.dp),
        // l.txt 第 8 条：两个圆钮大小不同（52 / 44），原先都靠右对齐 —— 两个图标的
        // 圆心差 4dp，看起来是歪的。改成整列居中对齐，圆心落在同一条竖线上。
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 粘贴（上）：k.txt 第 3 条 —— 不再铺实心主色，改为主色描边 + 主色图标
        Box(
            modifier = Modifier
                .size(52.dp)
                // l.txt 第 15 条：圆钮在新拟态下浮起，与页面里其它板块同一套光影。
                .filesNeuCircle()
                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(onClick = onPaste),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = Str.get(R.string.files_paste),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        // 关闭（下）：同样不收底色，描边弱化 —— 它只是「不粘了」，不该和粘贴抢注意力
        Box(
            modifier = Modifier
                .size(44.dp)
                .filesNeuCircle()
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = Str.get(R.string.close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 「隐藏」后留在页头的进行中胶囊（4.12）。
 *
 * 首版**不转常驻通知**（问题 12 已拍板），但也不能让用户「按了隐藏就再也找不到正在进行的操作」
 * —— 所以给一个常驻小胶囊，点一下把七层对话框唤回来。
 */
@Composable
internal fun RunningPill(progress: OpProgress, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.FileCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = progressTitle(progress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = Str.get(R.string.files_view),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 「正在复制 3 项…」—— 进度对话框标题、胶囊与**状态栏通知**三处共用同一句，免得措辞漂移。 */
fun progressTitle(p: OpProgress): String {
    val verb = when (p.kind) {
        OpKind.COPY -> Str.get(R.string.files_copy)
        OpKind.MOVE -> Str.get(R.string.files_move)
        OpKind.DELETE -> Str.get(R.string.files_delete)
        OpKind.COMPRESS -> Str.get(R.string.files_compress)
    }
    return Str.get(R.string.files_op_running, verb, p.totalCount.toString())
}

/** [OpProgress] → [UnifiedProgressState]：把七层数据从文件页口径翻成通用控件口径。 */
private fun OpProgress.toUnifiedState(
    context: Context,
    cancelling: Boolean = false
): UnifiedProgressState =
    UnifiedProgressState(
        opLabel = when (kind) {
            OpKind.COPY -> Str.get(R.string.files_copy)
            OpKind.MOVE -> Str.get(R.string.files_move)
            OpKind.DELETE -> Str.get(R.string.files_delete)
            OpKind.COMPRESS -> Str.get(R.string.files_compress)
        },
        title = progressTitle(this),
        currentName = currentName,
        sourcePath = sourcePath,
        targetPath = targetPath,
        totalCount = totalCount,
        doneCount = doneCount,
        totalBytes = totalBytes,
        doneBytes = doneBytes,
        running = !finished,
        cancelling = cancelling
    )
