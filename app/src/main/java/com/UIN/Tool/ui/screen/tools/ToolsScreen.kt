// app/src/main/java/com/UIN/Tool/ui/screen/tools/ToolsScreen.kt
package com.UIN.Tool.ui.screen.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.PluginShortcutHelper
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()
    val plugins by pluginManager.plugins.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var isGridView by remember { mutableStateOf(false) }
    var showShortcutDialog by remember { mutableStateOf<PluginInfo?>(null) }

    val categories = remember(plugins) {
        listOf("全部") + plugins.map { it.category }.distinct().filter { it != "未分类" } + listOf("未分类")
    }

    val filteredPlugins = remember(searchText, selectedCategory, plugins) {
        var result = if (searchText.isNotEmpty()) {
            pluginManager.searchPlugins(searchText)
        } else {
            plugins
        }
        if (selectedCategory != "全部") {
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
            UIComponents.TitleText("工具箱")
            Row {
                UIComponents.IconButton(
                    icon = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                    onClick = { isSearching = !isSearching }
                )
                UIComponents.IconButton(
                    icon = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                    onClick = { isGridView = !isGridView }
                )
            }
        }

        if (isSearching) {
            UIComponents.TextInput(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "搜索插件...",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                leadingIcon = Icons.Default.Search
            )
        }

        // 分类筛选
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.take(6).forEach { category ->
                UIComponents.Chip(
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
                        UIComponents.BodyText(
                            if (searchText.isNotEmpty()) "没有找到相关插件" else "暂无插件"
                        )
                        UIComponents.CaptionText(
                            if (searchText.isNotEmpty()) "尝试其他关键词" else "请先在管理页面导入插件"
                        )
                    }
                }
            }
            isGridView -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPlugins) { plugin ->
                        PluginGridItem(
                            plugin = plugin,
                            onClick = { pluginManager.openPlugin(plugin.pluginId, context) }
                        )
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredPlugins) { plugin ->
                        PluginListItem(
                            plugin = plugin,
                            onClick = { pluginManager.openPlugin(plugin.pluginId, context) },
                            onLongClick = { showShortcutDialog = plugin }
                        )
                    }
                }
            }
        }
    }

    // 快捷方式创建对话框
    showShortcutDialog?.let { plugin ->
        UIComponents.ConfirmDialog(
            title = "创建快捷方式",
            message = "是否在桌面创建 \"${plugin.name}\" 的快捷方式？",
            onConfirm = {
                scope.launch {
                    PluginShortcutHelper.createShortcut(context, plugin)
                    showShortcutDialog = null
                }
            },
            onDismiss = { showShortcutDialog = null }
        )
    }
}

@Composable
fun PluginListItem(
    plugin: PluginInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    UIComponents.Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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
                        RoundedCornerShape(8.dp)
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
                UIComponents.BodyText(
                    plugin.description.ifEmpty { "无描述" }
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UIComponents.CaptionText("v${plugin.versionName}")
                    UIComponents.CaptionText(plugin.category)
                    if (plugin.isWebPlugin()) {
                        UIComponents.CaptionText("Web")
                    }
                }
            }
            UIComponents.IconButton(
                icon = Icons.Default.Add,
                onClick = onLongClick
            )
        }
    }
}

@Composable
fun PluginGridItem(
    plugin: PluginInfo,
    onClick: () -> Unit
) {
    UIComponents.Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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
                        RoundedCornerShape(12.dp)
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
            UIComponents.CaptionText("v${plugin.versionName}")
        }
    }
}