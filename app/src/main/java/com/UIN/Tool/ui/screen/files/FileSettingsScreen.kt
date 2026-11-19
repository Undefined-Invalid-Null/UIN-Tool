// app/src/main/java/com/UIN/Tool/ui/screen/files/FileSettingsScreen.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedSectionTitle
import com.UIN.Tool.ui.components.unified.UnifiedSwitch
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.screen.manage.uiconfig.DropdownPropertyRow
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「文件管理设置」页（MT.md §4.17 / N12）。
 *
 * - **只属于文件页**：入口 = 根目录列表末尾那行（主）+ 文件页页头 ⋮（兜底），
 *   **不在管理 → UI 个性化里**（N12 ③）；
 * - 三个分组：浏览 / 返回 / 杂项，全部存 [FilePrefs]，
 *   并随 UI 个性化的导出/导入一起走；
 *   **压缩与解压的选项不在这里**（用户反馈⑪）—— 它们长在各自的弹窗里，
 *   这儿只把改动套回去，不摆任何一项档案设置；
 * - 风格：只用 `unified` 既有控件 + `DropdownPropertyRow`，颜色全走 `MaterialTheme`，零硬编码；
 * - 返回时由调用方 `applySettings()` 把改动套回两窗格（根范围变化会把越界窗格拉回新根）。
 */
@Composable
fun FileSettingsScreen(onBack: () -> Unit, onOpenManage: () -> Unit) {
    val context = LocalContext.current
    // 用户反馈㉙：「默认起始目录」输入框必须**收得掉** —— 回车即确认并交还焦点，
    // 否则光标一直闪、键盘也不走，看着像卡住了。
    val focusManager = LocalFocusManager.current
    // 用户反馈 c13：点别处必须把焦点收掉，否则光标一直闪、键盘也不走。
    // 「别处」按**坐标**判，不用 detectTapGestures —— 父子两个手势谁先消费没有保证，
    // 赌错就是「点输入框自己反而失焦、字都打不进去」。按下点落在输入框矩形之外才收。
    var fieldRect by remember { mutableStateOf<Rect?>(null) }
    var hostTopLeft by remember { mutableStateOf(Offset.Zero) }

    // 打开即读一遍；改完同时写回 FilePrefs，返回时 applySettings 统一生效
    var startDir by remember { mutableStateOf(FilePrefs.startDir()) }
    var dualPane by remember { mutableStateOf(FilePrefs.dualPane()) }
    // 用户反馈 c4 + d14：双窗格比例改成三态（可变 / 不可变 / 默认），拖动结果才是比例本身
    var paneRatioMode by remember { mutableStateOf(FilePrefs.paneRatioMode()) }
    var showHidden by remember { mutableStateOf(FilePrefs.showSystemHidden()) }
    // 手动隐藏开关：**只有确实藏过条目时才摆这行**。集合为空时拨它什么都不会变 ——
    // 那正是 b28「类比排查」要清掉的「点了没反应」；藏过之后它才出现，出现即有用。
    var showManualHidden by remember { mutableStateOf(FilePrefs.showManualHidden()) }
    var hasManualHidden by remember { mutableStateOf(FilePrefs.manualHidden().isNotEmpty()) }
    // 排序三项的状态不用再留了：默认排序/方向/目录优先已从设置页移除，
    // 改由右上角「排序方式」弹窗直接读写 FilePrefs（用户反馈⑬「简化设置内容」）
    var density by remember { mutableStateOf(FilePrefs.rowDensity()) }
    // i.txt 第 10 条：搜索结果上限（0 = 不限）。
    var searchLimit by remember { mutableStateOf(FilePrefs.searchResultLimit().toString()) }
    var multiLine by remember { mutableStateOf(FilePrefs.multiLineNames()) }
    var backBehavior by remember { mutableStateOf(FilePrefs.backBehavior()) }
    var rememberDir by remember { mutableStateOf(FilePrefs.rememberLastDir()) }
    var rootScope by remember { mutableStateOf(FilePrefs.rootScope()) }
    var roHint by remember { mutableStateOf(FilePrefs.archiveReadOnlyHint()) }
    // 反馈 e18：归档写回前的 `.bak` 备份开关（默认开，见 FilePrefs.archiveBackup）
    var archiveBackup by remember { mutableStateOf(FilePrefs.archiveBackup()) }
    var cacheSize by remember { mutableStateOf(0L) }
    // 缓存统计是递归 walk（文件系统 IO），必须下 IO 线程；先 0 → 按钮暂不可点，量完再亮
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        cacheSize = withContext(Dispatchers.IO) { FilePrefs.cacheSizeBytes() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.pageBackground())
            .onGloballyPositioned { hostTopLeft = it.boundsInRoot().topLeft }
            .pointerInput(focusManager) {
                awaitPointerEventScope {
                    while (true) {
                        // Initial 通道抢在子节点之前看到按下，所以「点了别处」当场就收
                        val pressed = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.pressed } ?: continue
                        val rect = fieldRect ?: continue
                        if (!rect.contains(hostTopLeft + pressed.position)) {
                            focusManager.clearFocus()
                        }
                    }
                }
            }
    ) {
        // ==================== 页头 ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnifiedIconButton(
                icon = Icons.Default.ArrowBack,
                onClick = onBack,
                contentDescription = Str.get(R.string.back),
            )
            Text(
                text = Str.get(R.string.files_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                UnifiedCaptionText(
                    text = Str.get(R.string.fs_settings_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ==================== 浏览 ====================
            item {
                UnifiedSectionTitle(Str.get(R.string.fs_group_browse))
                UnifiedTextField(
                    value = startDir,
                    onValueChange = {
                        startDir = it
                        FilePrefs.setStartDir(it)
                    },
                    label = Str.get(R.string.fs_start_dir),
                    placeholder = FilePrefs.rootPath(),
                    singleLine = true,
                    // 值本身在 onValueChange 里已经逐字写进 FilePrefs，这里只负责「收起来」：
                    // 键盘 Done / 回车 → 清焦点。IME 是单行框，本来就给 Done 键。
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        // 给「点别处收焦点」提供判定矩形（随滚动/键盘开合实时更新）
                        .onGloballyPositioned { fieldRect = it.boundsInRoot() }
                )
                UnifiedCaptionText(
                    text = Str.get(R.string.fs_start_dir_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                DropdownPropertyRow(
                    label = Str.get(R.string.fs_dual_pane),
                    value = if (dualPane) "dual" else "single",
                    options = listOf(
                        "single" to Str.get(R.string.fs_pane_single),
                        "dual" to Str.get(R.string.fs_pane_dual)
                    ),
                    onValueChange = {
                        dualPane = it == "dual"
                        FilePrefs.setDualPane(dualPane)
                    }
                )
                // 用户反馈 c4：窗格比例。单窗格下这一项没有意义，跟着双窗格开关一起显隐，
                // 免得摆一个改了没反应的选项（死设置正是本批反馈抓过的毛病）。
                if (dualPane) {
                    DropdownPropertyRow(
                        label = Str.get(R.string.fs_pane_ratio),
                        value = paneRatioMode,
                        options = listOf(
                            FilePrefs.RATIO_VARIABLE to Str.get(R.string.fs_pane_ratio_variable),
                            FilePrefs.RATIO_FIXED to Str.get(R.string.fs_pane_ratio_fixed),
                            FilePrefs.RATIO_DEFAULT to Str.get(R.string.fs_pane_ratio_default)
                        ),
                        onValueChange = {
                            paneRatioMode = it
                            FilePrefs.setPaneRatioMode(it)
                        }
                    )
                    UnifiedCaptionText(
                        text = Str.get(R.string.fs_pane_ratio_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 用户反馈⑬：**默认排序 / 排序方向 / 目录优先** 三项从设置里删掉 ——
                // 它们在右上角「排序方式」弹窗里已经能改，而且改的是同一批 pref
                // （FileManageViewModel.setSort 里就写着 FilePrefs.setSortKey…）。
                // 同一个开关摆两处，只会让人怀疑哪一处才算数。
                DropdownPropertyRow(
                    label = Str.get(R.string.fs_row_density),
                    value = density,
                    options = listOf(
                        FilePrefs.DENSITY_COMPACT to Str.get(R.string.fs_density_compact),
                        FilePrefs.DENSITY_NORMAL to Str.get(R.string.fs_density_normal),
                        FilePrefs.DENSITY_LOOSE to Str.get(R.string.fs_density_loose)
                    ),
                    onValueChange = {
                        density = it
                        FilePrefs.setRowDensity(it)
                    }
                )
                DropdownPropertyRow(
                    label = Str.get(R.string.fs_search_limit),
                    value = searchLimit,
                    options = listOf(
                        "500" to "500",
                        "2000" to "2000",
                        "5000" to "5000",
                        "10000" to "10000",
                        FilePrefs.SEARCH_LIMIT_UNLIMITED.toString() to
                            Str.get(R.string.fs_search_limit_unlimited)
                    ),
                    onValueChange = {
                        searchLimit = it
                        FilePrefs.setSearchResultLimit(
                            it.toIntOrNull() ?: FilePrefs.SEARCH_LIMIT_DEFAULT
                        )
                    }
                )
                UnifiedCaptionText(
                    text = Str.get(R.string.fs_search_limit_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GroupCard {
                    // 「目录优先」也在「排序方式」弹窗里，这里不再重复摆（用户反馈⑬）
                    SwitchRow(
                        title = Str.get(R.string.fs_show_hidden),
                        subtitle = Str.get(R.string.fs_show_hidden_desc),
                        checked = showHidden
                    ) {
                        showHidden = it
                        FilePrefs.setShowSystemHidden(it)
                    }
                    // 「隐藏」的动作在长按菜单，这个开关是**把它们放回来**的唯一通路 ——
                    // 两处分开放是有意的：动作是列表上的一次性决定，开关是长期偏好。
                    if (hasManualHidden) {
                        SwitchRow(
                            title = Str.get(R.string.fs_manual_hidden),
                            subtitle = Str.get(R.string.fs_manual_hidden_desc),
                            checked = showManualHidden
                        ) {
                            showManualHidden = it
                            FilePrefs.setShowManualHidden(it)
                        }
                    }
                    // 用户反馈⑬：长文件名折成多行显示。默认关 —— 单行省下的竖向空间在
                    // 列表里更值钱，省略号够用；开了之后长名字占满整行、一眼读全。
                    SwitchRow(
                        title = Str.get(R.string.fs_multi_line_names),
                        checked = multiLine
                    ) {
                        multiLine = it
                        FilePrefs.setMultiLineNames(it)
                    }
                    // h.txt 第 2 条：回收站总开关。默认开；关掉后删除确认框默认勾「彻底删除」。
                    var recycleBin by remember { mutableStateOf(FilePrefs.recycleBinEnabled()) }
                    SwitchRow(
                        title = Str.get(R.string.fs_recycle_bin),
                        subtitle = Str.get(R.string.fs_recycle_bin_desc),
                        checked = recycleBin
                    ) {
                        recycleBin = it
                        FilePrefs.setRecycleBinEnabled(it)
                    }
                    // 用户反馈 e15 P0-1：覆盖即真删是没有后悔药的 —— 给一份「覆盖前先存起来」
                    // 的开关，默认开。它管的是**所有**覆盖路径（复制 / 移动 / 解压），
                    // 备份落在共享工作目录的 trash 里，用户用任意文件管理器都能进去翻。
                    var overwriteTrash by remember { mutableStateOf(FilePrefs.overwriteTrash()) }
                    SwitchRow(
                        title = Str.get(R.string.fs_overwrite_trash),
                        subtitle = Str.get(R.string.fs_overwrite_trash_desc),
                        checked = overwriteTrash
                    ) {
                        overwriteTrash = it
                        FilePrefs.setOverwriteTrash(it)
                    }
                }
            }

            // ==================== 返回 ====================
            item {
                UnifiedSectionTitle(Str.get(R.string.fs_group_back))
                DropdownPropertyRow(
                    label = Str.get(R.string.fs_back_behavior),
                    value = backBehavior,
                    options = listOf(
                        FilePrefs.BACK_GO_UP to Str.get(R.string.fs_back_go_up),
                        FilePrefs.BACK_EXIT to Str.get(R.string.fs_back_exit)
                    ),
                    onValueChange = {
                        backBehavior = it
                        FilePrefs.setBackBehavior(it)
                    }
                )
                GroupCard {
                    SwitchRow(
                        title = Str.get(R.string.fs_remember_dir),
                        checked = rememberDir
                    ) {
                        rememberDir = it
                        FilePrefs.setRememberLastDir(it)
                    }
                }
            }

            // ==================== 压缩 / 解压 ====================
            // 这两组已经**整个搬走**了（用户反馈⑪「压缩，弹窗选择格式级别和编码等，
            // 不要放在设置。解压也是，解压时弹窗选择」）：格式、级别、编码、保留时间戳
            // 长在压缩框里；落点与还原时间长在解压框里。它们仍写回 FilePrefs，
            // 那只是为了下次开框记住上次的选择 —— 是记忆，不是设置。
            // 顺带清掉的两行是**从来没人消费**的死开关（覆盖策略只在冲突框里问过一次，
            // 默认目标/默认格式在这儿改了也改不动任何行为）—— 设置里摆一行不生效的
            // 选项，比不摆更糟：用户以为改了。

            // ==================== 杂项 ====================
            item {
                UnifiedSectionTitle(Str.get(R.string.fs_group_misc))
                DropdownPropertyRow(
                    label = Str.get(R.string.fs_root_scope),
                    value = rootScope,
                    options = listOf(
                        FilePrefs.ROOT_INTERNAL to Str.get(R.string.fs_root_internal),
                        FilePrefs.ROOT_REAL to Str.get(R.string.fs_root_real)
                    ),
                    onValueChange = {
                        rootScope = it
                        FilePrefs.setRootScope(it)
                    }
                )
                UnifiedCaptionText(
                    text = Str.get(R.string.fs_root_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                GroupCard {
                    SwitchRow(
                        title = Str.get(R.string.fs_archive_ro_hint),
                        checked = roHint
                    ) {
                        roHint = it
                        FilePrefs.setArchiveReadOnlyHint(it)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // 反馈 e18：写回前的 .bak 备份。开关与说明放一起，关掉的后果写明白
                    SwitchRow(
                        title = Str.get(R.string.fs_archive_backup),
                        checked = archiveBackup
                    ) {
                        archiveBackup = it
                        FilePrefs.setArchiveBackup(it)
                    }
                    UnifiedCaptionText(
                        text = Str.get(R.string.fs_archive_backup_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 用户反馈③：默认打开方式的管理入口**也要进设置** —— 从这里来的用户
                // 是先想起有这么个东西才来找，不能只有打开方式弹窗里那一条路。
                UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenManage() }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Str.get(R.string.files_open_manage_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Str.get(R.string.files_open_manage_hint),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Str.get(R.string.fs_clear_cache),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Str.get(R.string.fs_cache_size, formatFileSize(cacheSize)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        UnifiedButton(
                            text = Str.get(R.string.fs_clear_cache),
                            onClick = {
                                scope.launch {
                                    val freed = withContext(Dispatchers.IO) {
                                        FilePrefs.clearCache()
                                    }
                                    cacheSize = withContext(Dispatchers.IO) {
                                        FilePrefs.cacheSizeBytes()
                                    }
                                    AppToast.success(
                                        context,
                                        Str.get(R.string.fs_cache_cleared, formatFileSize(freed))
                                    )
                                }
                            },
                            enabled = cacheSize > 0
                        )
                    }
                }
            }
        }
    }
}

// ==================== 局部控件 ====================

/** 分组卡片：`unified` 既有 [UnifiedCard] 包一层，行自带内边距。 */
@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(4.dp))
    UnifiedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

/** 「左标题 + 右开关」行，颜色全走主题。 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    /**
     * 一句话说清这个开关管的是**哪一批**条目（用户反馈 d2）：
     * 「显示隐藏文件」和「手动隐藏」都带「隐藏」，光看标题分不清谁是谁。
     */
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        UnifiedSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
