// app/src/main/java/com/UIN/Tool/ui/screen/files/FileSearchPanel.kt
package com.UIN.Tool.ui.screen.files

import com.UIN.Tool.ui.components.unified.UnifiedDropdownMenu
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.components.unified.dialogAutoFocus
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * 搜索面板（§4.8）：查询 + 高级选项 + **常驻结果面板**。
 *
 * ## 为什么结果是常驻面板而不是跳转页
 *
 * §4.8 明写「结果落常驻结果面板（顶部可过滤、可关闭）」。跳转页会让用户失去
 * 「我还在原来那个目录里」的上下文 —— 而搜索最常见的用途恰恰是「在这个目录里找到它」，
 * 然后立刻回到浏览。常驻面板让结果与列表同屏，点一条就能跳过去。
 *
 * ## 内容匹配的诚实标注
 *
 * 内容匹配**读整个文件**（分块流式、内存有界，见 [FileSearch]）；二进制文件
 * （开头含 NUL）不做内容匹配，名字匹配不受影响。命中时结果行会带出**行号与该行原文**，
 * 用户不必再点开文件自己找。
 */
@Composable
fun FileSearchPanel(
    state: SearchState,
    results: List<SearchResult>,
    /**
     * e25：`false` = 本面板只当「搜索条件」用 —— 查询框、高级选项、分隔线整段收起。
     * 结果弹窗里没有条件可填，摆着只会跟状态行抢地方。
     */
    showInput: Boolean = true,
    /**
     * e25：`false` = 结果**不在这里**，已挪去单独的「搜索结果」弹窗。
     * 搜索弹窗走这条：它只负责「填条件 → 点搜索」。
     */
    showResults: Boolean = true,
    /**
     * e25：结果不在本面板时，把用户送去结果弹窗的入口；`null` = 不给
     * （结果弹窗自己就是目的地，再给一个等于原地打转）。
     */
    onViewResults: (() -> Unit)? = null,
    onStart: (SearchOptions) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    /**
     * 结果区是否吃掉整页剩余高度（h.txt 第 12 条的「单独页面」）。
     * 弹窗里保持 400dp 上限，避免把按钮顶出屏幕。
     */
    fillResultsHeight: Boolean = false,
    onOpen: (SearchResult) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var recursive by remember { mutableStateOf(true) }
    var caseSensitive by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var contentMatch by remember { mutableStateOf(false) }
    var minSize by remember { mutableStateOf("") }
    var maxSize by remember { mutableStateOf("") }

    // §4.8 查询框的历史下拉。与书签/历史共用 file_places 存储（见 FilePlaces）；
    // remember 缓住是免得面板每次重组都去解一遍 JSON。
    var searchHistory by remember { mutableStateOf(FilePlaces.searchHistory()) }
    var showHistory by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // ---- 查询行（e25：结果弹窗里没有条件可填，整段收起）----
        if (showInput) Row(verticalAlignment = Alignment.CenterVertically) {
            // 包一层 Box 是为了给下拉一个**锚点**：DropdownMenu 挂在输入框所在的盒子上，
            // 才会从输入框下方展开；直接放在 Row 里会锚到整个行。
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(Str.get(R.string.files_search_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        // 历史的**显式开关**，不用「聚焦即展开」—— 那样用户每点一下输入框
                        // 都会被下拉糊脸，想收起还得先把焦点移走。
                        UnifiedIconButton(
                            icon = Icons.Default.ArrowDropDown,
                            onClick = { showHistory = !showHistory },
                            contentDescription = Str.get(R.string.files_search_history),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().dialogAutoFocus()
                )
                UnifiedDropdownMenu(
                    // 历史为空时不展开：一个点不开的空菜单比没有菜单更让人困惑
                    expanded = showHistory && searchHistory.isNotEmpty(),
                    onDismissRequest = { showHistory = false }
                ) {
                    searchHistory.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            onClick = {
                                query = item
                                showHistory = false
                            },
                            trailingIcon = {
                                UnifiedIconButton(
                                    icon = Icons.Default.Close,
                                    onClick = {
                                    // 列表与存储一起改，免得下次读回来又冒出来
                                    searchHistory = searchHistory.filterNot { it == item }
                                    FilePlaces.removeSearchHistory(item)
                                },
                                    contentDescription = Str.get(R.string.files_search_clear),
                                )
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.padding(horizontal = 2.dp))
            val running = state is SearchState.Running
            UnifiedButton(
                text = Str.get(if (running) R.string.files_search_stop else R.string.files_search),
                onClick = {
                    if (running) {
                        onStop()
                    } else {
                        val q = query.trim()
                        onStart(
                            SearchOptions(
                                query = q,
                                recursive = recursive,
                                caseSensitive = caseSensitive,
                                regex = regex,
                                contentMatch = contentMatch,
                                minSize = minSize.trim().toLongOrNull() ?: -1L,
                                maxSize = maxSize.trim().toLongOrNull() ?: -1L
                            )
                        )
                        // §4.8：**点了搜索才进历史**（点「停止」不算），且只在真有变化时重读
                        if (FilePlaces.addSearchHistory(q)) {
                            searchHistory = FilePlaces.searchHistory()
                        }
                    }
                },
                variant = ButtonVariant.Primary
            )
            UnifiedIconButton(
                icon = Icons.Default.Close,
                onClick = onClose,
                contentDescription = Str.get(R.string.files_search_clear),
            )
        }

        // ---- 高级选项（默认收起）----
        // §4.8 把「区分大小写/正则/内容匹配/体积范围」列为高级项。默认收起是必要的：
        // 全摆开会让搜索框被选项淹没，而 90% 的搜索用不到其中任何一项。
        if (showInput) Text(
            text = Str.get(R.string.files_search_advanced),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                // k.txt 第 6 条：文字级点击也给圆角涟漪。
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(onClick = { showAdvanced = !showAdvanced }, onLongClick = {})
                .padding(horizontal = 4.dp, vertical = 4.dp)
        )
        if (showInput) AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                AdvancedToggle(Str.get(R.string.files_search_recursive), recursive) { recursive = it }
                AdvancedToggle(Str.get(R.string.files_search_case), caseSensitive) { caseSensitive = it }
                AdvancedToggle(Str.get(R.string.files_search_regex), regex) { regex = it }
                AdvancedToggle(Str.get(R.string.files_search_content), contentMatch) { contentMatch = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = minSize,
                        onValueChange = { minSize = it },
                        label = { Text(Str.get(R.string.files_search_min_size)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    OutlinedTextField(
                        value = maxSize,
                        onValueChange = { maxSize = it },
                        label = { Text(Str.get(R.string.files_search_max_size)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (showInput) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ---- 状态行 ----
        when (val s = state) {
            is SearchState.Idle -> {}
            is SearchState.Running -> Column {
                Text(
                    text = Str.get(
                        R.string.files_search_running,
                        s.scanned.toString(),
                        s.hits.toString()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // h.txt 第 12 条：提示文字必须横向排。这里显式给满宽 + 允许两行，
                    // 不再被同一行的其他元素挤成一列单字。
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                // n.txt 第 15 条：结果弹窗 / 整页里没有查询行（showInput=false），
                // 旧实现连「停止」按钮也一起收了起来 —— 用户看着结果在涨却停不下来。
                // 这里补一个，停止后结果原样保留（见 FileSearch.cancel）。
                if (!showInput) {
                    UnifiedButton(
                        text = Str.get(R.string.files_search_stop),
                        onClick = onStop,
                        variant = ButtonVariant.Outlined,
                        size = ButtonSize.Small,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            is SearchState.Done -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Str.get(R.string.files_search_done, s.hits.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Str.get(R.string.files_search_clear),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = onClear, onLongClick = {})
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // 「已达上限」是完整一句说明，单独占一行 —— 挤在状态行右侧会把
                // 「找到 N 项」压成竖排（h.txt 第 12 条）。
                if (s.truncated) {
                    Text(
                        text = Str.get(R.string.files_search_truncated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // n.txt 第 15 条：停止不等于「搜完了」—— 明确告诉用户是中途停下、
            // 已找到多少条，结果列表原样留在下面。
            is SearchState.Stopped -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Str.get(R.string.files_search_stopped, s.hits.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Str.get(R.string.files_search_clear),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = onClear, onLongClick = {})
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // e25：结果不摆在本面板时，给一个把用户送去「搜索结果」弹窗的入口。
        // 摆在状态行**外面**而不是塞进上面那个 when —— 搜索进行中（Done 之前）
        // 也一样能点，先看已命中的那几条。
        if (onViewResults != null && results.isNotEmpty()) {
            UnifiedButton(
                text = Str.get(R.string.files_search_view_results, results.size.toString()),
                onClick = onViewResults,
                variant = ButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        // ---- 结果（e25：搜索弹窗里不摆，挪去单独的「搜索结果」弹窗）----
        if (showResults && results.isEmpty() && state is SearchState.Done) {
            Text(
                text = Str.get(R.string.files_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else if (showResults && results.isNotEmpty()) {
            // e25：结果挪进**自己的弹窗**后它就是主角 —— 高度从 240dp 放宽到 400dp，
            // 并配一条滑条（复用文件列表那条 FastScrollBar）。上千条结果时没有滑条，
            // 只能靠猜「我到底看到第几条了」。
            // 滑条是**覆盖物**（Box 里后画的压在上层），不占布局宽度，行宽不受影响。
            Box(
                modifier = if (fillResultsHeight) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().heightIn(max = 400.dp)
                }
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = if (fillResultsHeight) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    },
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // u.txt 第 2 条（用户）：结果列表「有概率闪退」的另一条防线 ——
                    // LazyColumn 的 key 必须唯一。理论上 path 唯一，但虚拟路径 / 符号链接
                    // 等极端情况下可能撞车，撞了就是 IllegalArgumentException 直接闪退。
                    // 用「下标 + 路径」做 key：既不重复，又让已渲染的行在追加时保持稳定。
                    itemsIndexed(results, key = { i, r -> "$i:${r.path}" }) { _, r ->
                        SearchResultRow(r = r, onClick = { onOpen(r) })
                    }
                }
                FastScrollBar(
                    state = listState,
                    interactive = true,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun AdvancedToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(onClick = { onChange(!checked) }, onLongClick = {})
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

/**
 * 一条结果：名称 + 全路径。
 *
 * 路径必须给全（§4.8 结果要能直接定位）—— 同名文件在不同目录下很常见，
 * 只显示名字会让用户点错。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(r: SearchResult, onClick: () -> Unit) {
    // l.txt 第 13 条：长按结果 = 复制文件名（结果页与面板同一条行组件，行为一致）。
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // l.txt 第 15 条：搜索结果行在新拟态下浮起；关闭时仍是 8dp 圆角透明底
            // （k.txt 第 6 条：整行涟漪收在圆角内，不铺成矩形）。
            .filesNeuSurface(
                shape = RoundedCornerShape(8.dp),
                cornerRadius = 8.dp,
                fallback = androidx.compose.ui.graphics.Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    clipboard.setText(AnnotatedString(r.name))
                    AppToast.info(context, Str.get(R.string.files_copied))
                }
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = r.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = r.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 内容命中时把「哪一行、原文是什么」带出来。只有名字命中（hitLine=-1）
            // 就没有这一行 —— 名字本身已经说明问题，再补一句是噪音。
            if (r.hitLine > 0 && r.hitText.isNotBlank()) {
                Text(
                    text = Str.get(
                        R.string.files_search_hit_line,
                        r.hitLine.toString(),
                        r.hitText
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 搜索结果**整页**（h.txt 第 12 条：结果弹窗右上角的斜向拉伸图标点进来）。
 *
 * 与弹窗共用同一份 [FileSearch] 状态：这里不重新搜，只是把列表从 400dp 上限
 * 放开到整页，长结果不再需要在一个半屏窗口里翻。
 */
@Composable
fun FileSearchResultsPage(
    state: SearchState,
    results: List<SearchResult>,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpen: (SearchResult) -> Unit
) {
    // o.txt 第 12 条：全页右上角加「在结果中搜索」—— 只筛**已经找到**的这些结果，
    // 不重新走盘。
    var filterOpen by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    // q.txt 第 7 条（用户）：
    //  1. 二次筛选**只筛文件名**，不再看完整路径 —— 否则名字里没有关键词、但路径里
    //     恰好有的行也会留在结果里，用户会以为筛选没生效。
    //  2. **单击搜索才开始筛**，不再边打字边过滤 —— 与代码编辑器同一口径。
    var appliedFilter by remember { mutableStateOf("") }

    // r.txt 第 3 条（用户）：二次筛选比首次搜索还慢。
    //
    // 两个根因都在这里：
    //  1. 首次搜索是**流式**的（每 120ms 吐一批），用户看到第一屏就开始翻；
    //     二次筛选却要等整张表过完才出结果，观感上当然更慢。
    //  2. 原来 `remember(results, appliedFilter)` 在**主线程**同步跑，且搜索还在
    //     跑时 results 每 120ms 变一次，就会把不断变长的整张表反复重筛 —— 主线程
    //     被反复占满，越筛越卡。
    //
    // s.txt 第 4 条：LaunchedEffect 的 key 里有 `results`，每批结果都会取消在跑的筛选。
    // 改成常驻消费者 + snapshotFlow + conflate 之后不再被取消，但 **t.txt 第 4 条**
    // 用户仍报「筛到一半卡住」：
    //
    // 每次收到新一批结果都**从头重筛整张表**。结果上万条时，单次筛选比结果流的
    // 120ms 间隔还长，于是消费者永远在追一个越来越大的快照：UI 上的列表停在某一帧
    // 不动、进度条一直转，看起来就是卡住。
    //
    // 现在按「增量」筛：记住已筛过的前缀长度与已命中的结果，搜索流式吐新结果时
    // **只筛新增的那一截**再追加；只有换关键词、或结果表被重置（新搜索从 0 开始）
    // 才整表重筛一次。这样每批的代价与新结果数成正比，而不是与总结果数成正比。
    var filtered by remember { mutableStateOf(results) }
    var filtering by remember { mutableStateOf(false) }
    // 参数每次重组都是新值；用 rememberUpdatedState 让常驻的 snapshotFlow 读到最新值，
    // 而不是启动时捕获的那一份。
    val latestResults = rememberUpdatedState(results)
    LaunchedEffect(Unit) {
        // 当前生效的关键词、已筛过的前缀长度、已命中的结果。
        var active = ""
        var processed = 0
        val hits = ArrayList<SearchResult>()
        snapshotFlow { appliedFilter.trim() to latestResults.value }
            .conflate()
            .collect { (q, snapshot) ->
                if (q.isEmpty()) {
                    active = ""
                    processed = snapshot.size
                    hits.clear()
                    filtered = snapshot
                    filtering = false
                    return@collect
                }
                val needle = q.lowercase()
                // 换关键词 / 结果表被重置 → 整表重筛一次。
                if (q != active || snapshot.size < processed) {
                    active = q
                    filtering = true
                    val all = withContext(Dispatchers.Default) {
                        snapshot.filter { it.name.lowercase().contains(needle) }
                    }
                    hits.clear()
                    hits.addAll(all)
                    processed = snapshot.size
                    filtered = hits.toList()
                    filtering = false
                    return@collect
                }
                // 搜索还在流式吐结果：只筛新增的那一截，追加到已有命中里。
                if (snapshot.size > processed) {
                    val tail = snapshot.subList(processed, snapshot.size)
                    val added = withContext(Dispatchers.Default) {
                        tail.filter { it.name.lowercase().contains(needle) }
                    }
                    hits.addAll(added)
                    processed = snapshot.size
                    filtered = hits.toList()
                }
                filtering = false
            }
    }
    // 条件为空时直接用最新 results，避免筛选状态残留一帧旧表。
    val shown = if (appliedFilter.isBlank()) results else filtered
    Column(modifier = Modifier.fillMaxSize()) {
        ManageTopAppBar(
            titleText = Str.get(R.string.files_search_results),
            // 与文件页同处 MainActivity Scaffold 内，状态栏已由外层让出。
            windowInsets = WindowInsets(0, 0, 0, 0),
            onBack = onBack,
            actions = {
                UnifiedIconButton(
                    icon = if (filterOpen) Icons.Default.Close else Icons.Default.Search,
                    onClick = {
                        filterOpen = !filterOpen
                        // 收起时一并清条件 —— 否则「关掉了却还是筛过的列表」没法解释。
                        if (!filterOpen) {
                            filterQuery = ""
                            appliedFilter = ""
                        }
                    },
                    contentDescription = Str.get(R.string.search_in_results),
                    tint = if (filterOpen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        )
        if (filterOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = Str.get(R.string.search_in_results_hint),
                    singleLine = true,
                    showClearButton = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { appliedFilter = filterQuery }
                    ),
                    onTrailingIconClick = {
                        filterQuery = ""
                        appliedFilter = ""
                    },
                    modifier = Modifier.weight(1f)
                )
                // q.txt 第 7 条：显式「开始筛选」按钮 —— 输入不再即时生效。
                UnifiedIconButton(
                    icon = Icons.Default.Search,
                    onClick = { appliedFilter = filterQuery },
                    contentDescription = Str.get(R.string.files_search),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (filtering) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
        if (appliedFilter.isNotBlank()) {
            Text(
                text = Str.get(
                    R.string.search_in_results_matched,
                    shown.size,
                    results.size
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        FileSearchPanel(
            state = state,
            results = shown,
            showInput = false,
            fillResultsHeight = true,
            onStart = {},
            onStop = onStop,
            onClear = onClear,
            onOpen = onOpen,
            onClose = onBack
        )
    }
}
