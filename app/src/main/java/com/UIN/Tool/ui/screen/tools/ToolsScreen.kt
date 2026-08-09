// app/src/main/java/com/UIN/Tool/ui/screen/tools/ToolsScreen.kt
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.UIN.Tool.ui.screen.tools

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items          // ✅ 关键 import
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.screen.manage.CategoryChangeDialog
import com.UIN.Tool.ui.screen.manage.PluginDetailDialog
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppToast
import kotlinx.coroutines.launch

private const val TAG = "ToolsScreen"

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val pluginManager = ServiceLocator.getPluginManager()
    val plugins by pluginManager.plugins.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(Str.get(R.string.all)) }
    var isGridView by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<PluginInfo?>(null) }
    var categoryTargetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pluginToUninstall by remember { mutableStateOf<PluginInfo?>(null) }
    val scope = rememberCoroutineScope()

    val categories = remember(plugins) {
        listOf(Str.get(R.string.all)) + plugins.map { it.category }.distinct().filter { it != Str.get(R.string.uncategorized) } + listOf(Str.get(R.string.uncategorized))
    }

    val filteredPlugins = remember(searchText, selectedCategory, plugins) {
        var result = if (searchText.isNotEmpty()) {
            pluginManager.searchPlugins(searchText)
        } else {
            plugins
        }
        if (selectedCategory != Str.get(R.string.all)) {
            result = result.filter { it.category == selectedCategory }
        }
        result
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnifiedTitleText(Str.get(R.string.plugins))
            Row {
                UnifiedIconButton(
                    icon = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                    onClick = { isSearching = !isSearching }
                )
                UnifiedIconButton(
                    icon = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                    onClick = { isGridView = !isGridView }
                )
            }
        }

        if (isSearching) {
            UnifiedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = Str.get(R.string.search_plugins),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                leadingIcon = Icons.Default.Search
            )
        }

        // 分类筛选（横向滚动，避免分类过多被截断）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                UnifiedChip(
                    label = category,
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category }
                )
            }
        }

        when {
            filteredPlugins.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UnifiedBodyText(
                            if (searchText.isNotEmpty()) Str.get(R.string.no_matching_plugins_found) else Str.get(R.string.no_plugins_yet)
                        )
                        UnifiedCaptionText(
                            if (searchText.isNotEmpty()) Str.get(R.string.try_other_keywords) else Str.get(R.string.import_plugins_on_the_manage_page_fi)
                        )
                    }
                }
            }
            isGridView -> {
                val gridState = rememberLazyGridState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        itemsIndexed(filteredPlugins) { index, plugin ->
                            PluginGridItem(
                                plugin = plugin,
                                onClick = { pluginManager.openPlugin(plugin.pluginId, context) },
                                onLongClick = { showDetailDialog = plugin }
                            )
                        }
                    }
                }
            }
            else -> {
                val listState = rememberLazyListState()
                // ✅ 现在 items 扩展函数可见，正确匹配 List 版本
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredPlugins) { index, plugin ->
                            PluginListItem(
                                plugin = plugin,
                                onClick = { pluginManager.openPlugin(plugin.pluginId, context) },
                                onLongClick = { showDetailDialog = plugin }
                            )
                        }
                    }
                }
            }
        }
    }

    // 插件详情对话框
    showDetailDialog?.let { plugin ->
        PluginDetailDialog(
            plugin = plugin,
            onDismiss = { showDetailDialog = null },
            onChangeCategory = { categoryTargetIds = setOf(plugin.pluginId) },
            onUninstall = { pluginToUninstall = plugin }
        )
    }

    // 更换分类对话框
    if (categoryTargetIds.isNotEmpty()) {
        CategoryChangeDialog(
            targetPluginIds = categoryTargetIds,
            onDismiss = { categoryTargetIds = emptySet() },
            onCategoryUpdated = {
                try {
                    com.UIN.Tool.widget.WidgetProvider.forceRefreshAllWidgets(context)
                    com.UIN.Tool.widget.Widget1x1Provider.refresh1x1Widgets(context)
                } catch (e: Exception) {
                    // ignore
                }
            }
        )
    }

    // 卸载确认对话框
    pluginToUninstall?.let { plugin ->
        UnifiedConfirmDialog(
            title = Str.get(R.string.confirm_delete),
            message = Str.get(R.string.delete_plugin_showdeletedialog_name_, plugin.name),
            confirmText = Str.get(R.string.delete),
            dismissText = Str.get(R.string.cancel),
            onConfirm = {
                scope.launch {
                    try {
                        pluginManager.uninstallPlugin(plugin.pluginId)
                        pluginToUninstall = null
                        AppToast.success(context, Str.get(R.string.uninstall_successful_plugin_name, plugin.name))
                    } catch (e: Exception) {
                        AppToast.error(context, Str.get(R.string.uninstall_failed_e_message, e.message))
                    }
                }
            },
            onDismiss = { pluginToUninstall = null },
            isDestructive = true
        )
    }
}

@Composable
fun PluginListItem(
    plugin: PluginInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        onClick = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(AppDimens.radiusSmall)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plugin.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                UnifiedBodyText(
                    plugin.description.ifEmpty { Str.get(R.string.no_description) }
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedCaptionText("v${plugin.versionName}")
                    UnifiedCaptionText(plugin.category)
                    if (plugin.isWebPlugin()) {
                        UnifiedCaptionText(Str.get(R.string.web_label))
                    }
                }
            }
        }
    }
}

@Composable
fun PluginGridItem(
    plugin: PluginInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        onClick = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(AppDimens.radiusMedium)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plugin.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(plugin.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            UnifiedCaptionText("v${plugin.versionName}")
        }
    }
}