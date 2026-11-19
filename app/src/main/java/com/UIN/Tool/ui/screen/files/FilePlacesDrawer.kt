// app/src/main/java/com/UIN/Tool/ui/screen/files/FilePlacesDrawer.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.UnifiedBottomSheet
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 书签 / 历史底部抽屉（§4.9）。
 *
 * ## 四个行为要点
 *
 * 1. **历史点选不新增历史**。点历史是为了「回到那儿继续看」，若点一次就把那条顶到最前，
 *    用户想回的那条反而被挤走 —— 那等于历史没法用。所以调用方传的是**绕过记历史**
 *    的跳转入口（`navigateTo(..., recordHistory = false)`）。
 * 2. **书签按分组分节显示**，未分组的排最后。分组名是**用户自己的组织方式**，
 *    不该被字典序顶到最前。
 * 3. **历史行右侧带访问时间，只到分钟**。秒级时间戳在「上周去过的目录」这个场景里
 *    是纯噪声，反而让列表显得杂乱（用户反馈 c9：这一栏原先根本没显示时间）。
 * 4. **列表自己持一份可刷新的副本**。父组件传进来的两份是上次重组时的快照 ——
 *    抽屉里删一条而父组件没重组，那条就还挂在列表里（c9 说的「显示问题」）。
 *    所以长按删除后立刻 [reload] 重读，不等外面重组。
 *
 * 长按书签出「改名 / 移动分组 / 删除」（§4.9 明写，原先只在文档里、代码里没有）；
 * 长按历史直接删这一条。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePlacesDrawer(
    bookmarks: List<Bookmark>,
    history: List<HistoryEntry>,
    onBookmarkClick: (Bookmark) -> Unit,
    onHistoryClick: (HistoryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // g8：书签/历史抽屉占屏约 1/3。原先写死 420dp —— 在 640dp 高的机器上占了近七成，
    // 一拉开就像整页替换，而不是「从底部升起的一层」。随屏高算并留上下限：
    // 小屏不被压到只剩一条，大屏也不会因为 1/3 太多而失衡。
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val listHeight = (screenHeightDp * 0.33f).dp.coerceIn(180.dp, 300.dp)
    var tab by remember { mutableStateOf(PlacesTab.BOOKMARKS) }

    /** 长按改中的书签；null = 关着。 */
    var bmMenu by remember { mutableStateOf<Bookmark?>(null) }

    // 外部传入的是快照；内部改动后自己重读（见类注 4）
    var bmList by remember(bookmarks) { mutableStateOf(bookmarks) }
    var hiList by remember(history) { mutableStateOf(history) }
    val reload = {
        bmList = FilePlaces.bookmarks()
        hiList = FilePlaces.history()
    }

    UnifiedBottomSheet(onDismissRequest = onDismiss, title = Str.get(R.string.files_places_title)) {
        // Tab 切换。用两个可点文本而不是 TabRow —— 抽屉高度有限，
        // TabRow 的指示条与内边距会再吃掉一行，而这里只有两项。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlacesTabEntry(
                text = Str.get(R.string.files_places_bookmarks),
                selected = tab == PlacesTab.BOOKMARKS,
                onClick = { tab = PlacesTab.BOOKMARKS }
            )
            PlacesTabEntry(
                text = Str.get(R.string.files_places_history),
                selected = tab == PlacesTab.HISTORY,
                onClick = { tab = PlacesTab.HISTORY }
            )
        }

        Spacer(Modifier.padding(top = 4.dp))

        // 抽屉里列表要有上限：书签/历史都可能很长，不限高会把整个屏幕占满，
        // 用户反而看不到「这是个抽屉」的上下文。
        // f6「高度固定」：原先 `heightIn(max = …)` 是**随内容涨**的 —— 书签三条、
        // 历史四十条，一换 Tab 抽屉就整个跳一次高矮，看着像重新弹了一回。
        // 改成定高：无论哪一档、几条，抽屉都停在同一个位置，只有列表内容换。
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when (tab) {
                PlacesTab.BOOKMARKS -> {
                    if (bmList.isEmpty()) {
                        item { EmptyHint(Str.get(R.string.files_places_empty_bookmarks)) }
                    } else {
                        // 按分组分节；未分组（group == ""）排最后
                        val grouped = bmList.groupBy { it.group }
                        val ordered = grouped.keys
                            .filter { it.isNotEmpty() }
                            .sorted() + listOf("")
                        for (g in ordered) {
                            val list = grouped[g].orEmpty()
                            if (list.isEmpty()) continue
                            if (g.isNotEmpty()) {
                                item(key = "hdr:$g") {
                                    GroupHeader(
                                        Str.get(R.string.files_places_group_named, g)
                                    )
                                }
                            }
                            items(list, key = { "bm:${it.path}" }) { b ->
                                PlaceRow(
                                    title = b.name,
                                    sub = b.path,
                                    onClick = { onBookmarkClick(b) },
                                    // §4.9：长按 = 改名 / 移动分组 / 删除
                                    onLongClick = { bmMenu = b }
                                )
                            }
                        }
                    }
                }

                PlacesTab.HISTORY -> {
                    if (hiList.isEmpty()) {
                        item { EmptyHint(Str.get(R.string.files_places_empty_history)) }
                    }
                    items(hiList, key = { "hi:${it.path}" }) { h ->
                        PlaceRow(
                            title = h.name,
                            sub = h.path,
                            // 访问时间挂右上：单行省略只管路径，时间不能被它挤掉（用户反馈 c9）
                            trailing = remember(h.at) { placeTimeFormat.format(Date(h.at)) },
                            onClick = { onHistoryClick(h) },
                            // 长按 = 删这一条（§4.9）。历史是缓存，删错的代价只是少一条捷径，
                            // 不必再叠一层确认框；删完给一句提示，误删看得见。
                            onLongClick = {
                                FilePlaces.removeHistory(h.path)
                                AppToast.info(context, Str.get(R.string.files_places_history_removed))
                                reload()
                            }
                        )
                    }
                }
            }
        }
    }

    // ==================== 书签长按菜单（改名 / 分组 / 删除） ====================
    bmMenu?.let { bm ->
        var renameText by remember(bm) { mutableStateOf(bm.name) }
        var pickedGroup by remember(bm) { mutableStateOf(bm.group) }
        var newGroup by remember(bm) { mutableStateOf("") }
        // 分组下拉只给**已有**的分组，外加一条「未分组」；建新分组走下面那个输入框
        val groupOptions = remember(bm) { listOf("") + FilePlaces.groups() }

        UnifiedDialog(
            onDismissRequest = { bmMenu = null },
            title = bm.name,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UnifiedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = Str.get(R.string.files_places_rename),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = Str.get(R.string.files_places_group),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    groupOptions.forEach { g ->
                        val checked = pickedGroup == g && newGroup.isBlank()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                // k.txt 第 6 条：分组选项行涟漪按 8dp 圆角裁切。
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { pickedGroup = g; newGroup = "" },
                                    onLongClick = {}
                                )
                                .padding(vertical = 1.dp)
                        ) {
                            RadioButton(
                                selected = checked,
                                onClick = { pickedGroup = g; newGroup = "" }
                            )
                            Text(
                                text = g.ifEmpty { Str.get(R.string.files_places_ungrouped) },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 打了新分组就用它，否则用选中的那条 —— 两处不必互相置灰，
                    // 输入框留空 = 不改分组，语义与「指定目录留空不让按」相反，这里反而该放行
                    UnifiedTextField(
                        value = newGroup,
                        onValueChange = { newGroup = it },
                        label = Str.get(R.string.files_places_new_group),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()
                    UnifiedDialogTextButton(
                        onClick = {
                            FilePlaces.removeBookmark(bm.path)
                            AppToast.info(context, Str.get(R.string.files_bookmark_removed))
                            bmMenu = null
                            reload()
                        },
                        contentColor = MaterialTheme.colorScheme.error
                    ) { Text(Str.get(R.string.files_ctx_delete)) }
                }
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.ok),
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        FilePlaces.putBookmark(
                            name = renameText.trim(),
                            path = bm.path,
                            group = newGroup.trim().ifEmpty { pickedGroup }
                        )
                        bmMenu = null
                        reload()
                    },
                    variant = ButtonVariant.Primary
                )
            },
            dismissButton = {
                UnifiedDialogTextButton(onClick = { bmMenu = null }) {
                    Text(Str.get(R.string.cancel))
                }
            }
        )
    }
}

private enum class PlacesTab { BOOKMARKS, HISTORY }

/**
 * 一个 Tab。**选中态不能只靠颜色** —— 浅底上主色与次色的差别一眼扫过去读不出来，
 * 现在再叠一层胶囊底色 + 加粗，停在哪个 Tab 一眼可见（用户反馈 c9）。
 */
@Composable
private fun RowScope.PlacesTabEntry(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .weight(1f)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
                shape = RoundedCornerShape(percent = 50)
            )
            // h.txt 第 5 条：涟漪按胶囊裁切，别溢出圆角
            .clip(RoundedCornerShape(percent = 50))
            .combinedClickable(onClick = onClick, onLongClick = {})
            .padding(vertical = 6.dp, horizontal = 8.dp)
    )
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 历史行右侧的时间，**只到分钟**（类注 3）。 */
private val placeTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * 一行「名称 + 全路径」（§4.9 明写条目 = 名称 + 全路径）。
 *
 * 路径用次要色 + 单行省略：它是**佐证**不是主角，两行都抢眼会让列表没法扫读。
 * [trailing]（历史的访问时间）另占右列，不进省略号那一行。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(
    title: String,
    sub: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // l.txt 第 15 条：地点行在新拟态下浮起；关闭时仍是透明圆角（k.txt 第 6 条）。
            .filesNeuSurface(
                shape = RoundedCornerShape(8.dp),
                cornerRadius = 8.dp,
                fallback = Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
