package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str

/**
 * 默认打开方式管理页（用户反馈③）。
 *
 * 顶部「退出 + 靠左标题 + 最右多选」，下面是**纵向卡片**：左边图标与名称
 * （系统应用用它自己的图标，图标读不出来才退回通用图标），右边切换与删除。
 * 点最右的多选进批量态 —— 卡片右侧两颗图标让位给勾选框，底部浮出
 * 全选 / 全不选 / 批量删除。绑定多起来以后逐条删不可用，批量态是必需的。
 */
@Composable
fun FileOpenWithManageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 每次进页读一遍、增删后立刻重读。这一页的全部状态就是这一张表，
    // 再复制一份进 Compose 只会多一处与 FilePrefs 不同步的机会。
    var entries by remember { mutableStateOf(FilePrefs.openWithDefaults()) }
    var multiSelect by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    // 正在为哪个后缀挑目标；null = 没开选择器
    var switchSuffix by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    // 绑定多起来以后翻着找不现实（用户反馈 c17 要的搜索框）。按**后缀**过滤，纯字符串比较 ——
    // 卡片上的图标与名称是逐条问 PackageManager 查出来的，拿它当过滤条件等于每次按键都把整表重查一遍
    var query by rememberSaveable { mutableStateOf("") }
    val visibleEntries = if (query.isBlank()) entries
    else entries.filter { it.first.contains(query.trim(), ignoreCase = true) }

    fun reload() {
        entries = FilePrefs.openWithDefaults()
    }

    // 多选态一关，勾选也一起清 —— 留着下次进来会莫名其妙删掉几条
    fun exitMulti() {
        multiSelect = false
        selected = emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.pageBackground())
    ) {
        // ---- k.txt 第 7 条：与文件页/开发页/管理页统一顶栏 ----
        ManageTopAppBar(
            titleText = "",
            // 文件页嵌在 MainActivity Scaffold 内，状态栏已由外层让出。
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                UnifiedIconButton(
                    icon = Icons.Default.ArrowBack,
                    onClick = { if (multiSelect) exitMulti() else onBack() },
                    contentDescription = Str.get(R.string.back),
                )
            },
            titleContent = {
                Text(
                    text = Str.get(R.string.files_open_manage_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            actions = {
                // 一条都没有时多选没有意义，不摆出来
                if (entries.isNotEmpty()) {
                    UnifiedIconButton(
                        // 进去时画勾选列表，再点是「退出多选」—— 与顶栏左侧返回键同义，
                        // 只是给惯用右上角操作的人一条顺手的路
                        icon = if (multiSelect) Icons.Default.Close
                        else Icons.Default.Checklist,
                        onClick = {
                            multiSelect = !multiSelect
                            selected = emptySet()
                        },
                        contentDescription = if (multiSelect) Str.get(R.string.cancel)
                        else Str.get(R.string.files_select_all),
                    )
                }
            }
        )

        // 搜索框（用户反馈 c17）。一条都没有时没有可筛的，不摆出来占地方
        if (entries.isNotEmpty()) {
            UnifiedTextField(
                value = query,
                onValueChange = { query = it },
                label = Str.get(R.string.files_open_manage_search),
                singleLine = true,
                showClearButton = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "_hint") {
                Text(
                    text = Str.get(R.string.files_open_manage_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 筛没了给句人话，别让列表只剩提示与「新增」两行、看着像数据丢了
            if (visibleEntries.isEmpty()) {
                item(key = "_nomatch") {
                    Text(
                        text = Str.get(R.string.files_open_search_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(visibleEntries, key = { it.first }) { (suffix, target) ->
                OpenWithCard(
                    suffix = suffix,
                    target = target,
                    multiSelect = multiSelect,
                    checked = suffix in selected,
                    onToggle = {
                        selected = if (suffix in selected) selected - suffix
                        else selected + suffix
                    },
                    onSwitch = { switchSuffix = suffix },
                    onRemove = {
                        FilePrefs.removeOpenWithDefault(suffix)
                        reload()
                        AppToast.success(context, Str.get(R.string.files_open_removed))
                    }
                )
            }

            // 新增：与卡片同一套底板，但只有图标 + 文字，读起来是「来加一条」而不是「已有的一条」
            item(key = "_add") {
                Row(
                    modifier = Modifier
                        // y.txt 第 3 条（用户）：只有「添加」两项内容时，按钮不该被拉满整行、
                        // 一直顶到右边缘（新拟态下描边尤其明显）。宽度跟着内容走。
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(14.dp))
                        // j.txt 第 3 条：新增入口用描边 + 主色文字，不再铺一层色块。
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { addOpen = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = Str.get(R.string.add),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ---- 多选底部栏：全选 / 全不选 / 批量删除 ----
        if (multiSelect) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    UnifiedDialogTextButton(
                        onClick = { selected = entries.map { it.first }.toSet() }
                    ) { Text(Str.get(R.string.files_select_all)) }
                    UnifiedDialogTextButton(
                        onClick = { selected = emptySet() }
                    ) { Text(Str.get(R.string.files_select_none)) }
                }
                UnifiedDialogTextButton(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            val count = selected.size
                            selected.forEach { FilePrefs.removeOpenWithDefault(it) }
                            reload()
                            exitMulti()
                            AppToast.success(
                                context, Str.get(R.string.files_open_removed_count, count)
                            )
                        }
                    },
                    // 什么都没选就按灰：点了没反应比按不动更让人困惑
                    contentColor = if (selected.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                ) { Text(Str.get(R.string.delete)) }
            }
        }
    }

    switchSuffix?.let { suffix ->
        OpenerPickerDialog(
            fixedSuffix = suffix,
            onPick = { _, target ->
                FilePrefs.setOpenWithDefault(suffix, target)
                reload()
                switchSuffix = null
                AppToast.success(context, Str.get(R.string.files_open_set_default))
            },
            onDismiss = { switchSuffix = null }
        )
    }

    if (addOpen) {
        OpenerPickerDialog(
            fixedSuffix = null,
            onPick = { s, target ->
                FilePrefs.setOpenWithDefault(s, target)
                reload()
                addOpen = false
                AppToast.success(context, Str.get(R.string.files_open_set_default))
            },
            onDismiss = { addOpen = false }
        )
    }
}

/**
 * 一张默认打开方式卡片。
 *
 * 左边「图标 + 名称」，名称下面是后缀 —— 卡片展示的是**打开方**，
 * 但绑定是按后缀存的，不把后缀写出来就分不清同名应用的两条绑定。
 * 多选态隐藏右侧两颗图标，让位给勾选框：那两颗在批量态下没有对应操作。
 */
@Composable
private fun OpenWithCard(
    suffix: String,
    target: String,
    multiSelect: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 目标应用被卸载 / 组件被改名时 openerOf 返回 null —— 那条绑定已经没意义，
    // 名称直接显示存储串、图标退回通用，比画个问号更能让用户看懂要删的是什么。
    val opener = remember(target) { openerOf(context, target) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // l.txt 第 15 条：打开方式卡片在新拟态下浮起；关闭时仍是原来的圆角色块。
            .filesNeuSurface(
                shape = RoundedCornerShape(14.dp),
                cornerRadius = 14.dp,
                fallback = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .then(if (multiSelect) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (multiSelect) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.width(6.dp))
        }

        OpenWithIconImage(opener?.second, size = 34.dp)
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = opener?.first ?: target,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${Str.get(R.string.files_open_suffix)}: $suffix",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!multiSelect) {
            UnifiedIconButton(
                icon = Icons.Default.SwapHoriz,
                onClick = onSwitch,
                contentDescription = Str.get(R.string.files_open_switch),
                tint = MaterialTheme.colorScheme.primary,
            )
            UnifiedIconButton(
                icon = Icons.Default.Delete,
                onClick = onRemove,
                contentDescription = Str.get(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 图标位：矢量走 tint，位图（系统应用图标）原样铺；都缺就画通用图标兜底。 */
@Composable
private fun OpenWithIconImage(
    icon: OpenWithIcon?,
    size: Dp
) {
    when (icon) {
        is OpenWithIcon.Vector -> Icon(
            imageVector = icon.value,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size)
        )
        is OpenWithIcon.Raster -> Image(
            bitmap = icon.value,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size)
        )
        null -> Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * 挑一个打开方式（新增与「切换」共用同一个框）。
 *
 * [fixedSuffix] 非 null 是切换：后缀定死，只挑目标；为 null 是新增：
 * 顶部多一个后缀输入框，候选**随输入实时重查** —— 用户能在同一屏里
 * 看着候选决定后缀怎么写，不必先填完再下一步才发现挑不到东西。
 *
 * 候选 = 内置能力 + 系统全量：这里是刻意做决定的场合，藏选项只会让人以为没有。
 */
@Composable
private fun OpenerPickerDialog(
    fixedSuffix: String?,
    onPick: (suffix: String, target: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var typed by rememberSaveable { mutableStateOf("") }
    val suffix = (fixedSuffix ?: typed).trim().trimStart('.').lowercase()

    // 用户反馈 c17：候选分两档，**内置能力是本地拼的**（几微秒），系统应用那一档才要问
    // PackageManager（两次 queryIntentActivities + 逐个 loadIcon/loadLabel）。两档分开加载：
    // 内置的立刻摆出来，系统的一到再接在后面 —— 原先一转圈到底，用户等的是他不看的那一档。
    // （更早这段是 remember 里同步跑主线程、点「切换」卡到秒级，见㉚；后来改成了挂起，但还是一整档。）
    val builtIns = remember(suffix) {
        if (suffix.isEmpty()) {
            emptyList()
        } else {
            // w.txt 第 4 条（用户）：候选要含「浏览压缩包」。它需要文件页的窗格上下文
            // 才能真挂载，这里给空实现只为让这一格能选、能绑；真正打开时由
            // FilesScreen.openEntry 把 TARGET_ARCHIVE 交给当前窗格。
            builtInOpsFor(
                context, "sample.$suffix",
                includeAll = true,
                onBrowseArchive = {}
            )
        }
    }
    var systemChoices by remember { mutableStateOf<List<OpenWithItem>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }
    // 候选几十条，肉眼找不如敲两个字（c17 要的搜索框）
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(suffix) {
        if (suffix.isEmpty()) {
            systemChoices = emptyList()
            choosing = false
        } else {
            choosing = true
            // 换后缀先把上一档清掉：留着会先闪一眼旧后缀的候选，再被换掉
            systemChoices = emptyList()
            val sys = withContext(Dispatchers.IO) { systemChoicesCached(context, suffix) }
            systemChoices = sys
            choosing = false
        }
    }

    val all = builtIns + systemChoices
    val visible = if (query.isBlank()) all
    else all.filter { it.label.contains(query.trim(), ignoreCase = true) }

    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = if (fixedSuffix == null) Str.get(R.string.files_open_add_title)
        else Str.get(R.string.files_open_select_target),
        content = {
            if (fixedSuffix == null) {
                UnifiedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = Str.get(R.string.files_open_suffix),
                    placeholder = Str.get(R.string.files_open_suffix_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 搜索框（用户反馈 c17）：只在**真有候选可筛**时才摆出来 ——
            // 后缀还没填、列表是空的，摆一个搜不到东西的框只会让人以为它坏了
            if (all.isNotEmpty()) {
                UnifiedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = Str.get(R.string.files_open_search),
                    singleLine = true,
                    showClearButton = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 查询中给个转圈 —— 候选是挂起查的，没这一下用户会以为「点了没反应」。
            // 只有**一档都还没到**时才给整块转圈；内置能力已经垫在下面时改成列表顶上
            // 一枚小的，别把刚摆出来的列表又整块盖回去
            if (all.isEmpty() && choosing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (all.isEmpty()) {
                Text(
                    text = Str.get(R.string.files_open_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                // 普通滚动列而不是 LazyColumn：Dialog 里嵌可懒列表要多背一层嵌套滚动，
                // 而这里的候选量（几十条）用一列排完就够，省掉那层协调成本。
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 系统档还在路上：给一条细的进度，免得列表「自己少几行」让人以为就这些
                    if (choosing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    // 搜出来一条不剩时给句人话，别留一片空白
                    if (visible.isEmpty()) {
                        Text(
                            text = Str.get(R.string.files_open_search_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    visible.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(suffix, item.target) }
                                .padding(vertical = 9.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OpenWithIconImage(item.icon, size = 26.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(onClick = onDismiss) { Text(Str.get(R.string.cancel)) }
        }
    )
}
