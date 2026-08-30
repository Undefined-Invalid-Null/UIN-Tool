@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.UIN.Tool.ui.screen.tools

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.FontWeight
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.screen.manage.CategoryChangeDialog
import com.UIN.Tool.ui.screen.manage.PluginDetailDialog
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.gradientBackgroundBrush
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background
            ) {
                val textPrimary = if (UIConfig.shouldUseDarkTheme()) Color(0xFFD0D0D0) else Color(0xFF333333)
                val textSecondary = if (UIConfig.shouldUseDarkTheme()) Color(0xFFB0B0B0) else Color(0xFF666666)

                Box(modifier = Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { 280.dp.toPx() }
                    val heightPx = with(LocalDensity.current) { 600.dp.toPx() }
                    val gradientBrush = gradientBackgroundBrush(widthPx, heightPx)
                    if (gradientBrush != null) {
                        Box(modifier = Modifier.fillMaxSize().background(gradientBrush))
                    }
                    Box(modifier = Modifier.fillMaxHeight().navigationBarsPadding()) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(140.dp))
                            categories.forEach { category ->
                                val isSelected = selectedCategory == category
                                val neu = UIConfig.isNeumorphismEnabled()
                                val isDark = UIConfig.shouldUseDarkTheme()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                        .then(
                                            if (neu) {
                                                Modifier.neuRaised(
                                                    RoundedCornerShape(12.dp), isDark, NeuDefaults.Intensity.LIGHT,
                                                    cornerRadius = 12.dp, backgroundColor = Color.Transparent
                                                ).background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(12.dp)
                                                )
                                            } else Modifier
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedCategory = category
                                            drawerScope.launch { drawerState.close() }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.Circle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = category,
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(Modifier.height(48.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            Column {
                                Text(
                                    "分类",
                                    color = textPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    selectedCategory,
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        gesturesEnabled = true
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnifiedIconButton(
                        icon = Icons.Default.Menu,
                        onClick = {
                            drawerScope.launch { drawerState.open() }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    UnifiedBodyText(selectedCategory)
                }
                Row {
                    UnifiedIconButton(
                        icon = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                        onClick = { isSearching = !isSearching }
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .background(
                                if (isGridView) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        UnifiedIconButton(
                            icon = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            onClick = { isGridView = !isGridView }
                        )
                    }
                }
            }

            if (isSearching) {
                UnifiedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = Str.get(R.string.search_plugins),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    leadingIcon = Icons.Default.Search
                )
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
                            contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp)
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp),
                            modifier = Modifier.fillMaxSize().elasticOverscroll(listState)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }
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
    }

    showDetailDialog?.let { plugin ->
        PluginDetailDialog(
            plugin = plugin,
            onDismiss = { showDetailDialog = null },
            onChangeCategory = { categoryTargetIds = setOf(plugin.pluginId) },
            onUninstall = { pluginToUninstall = plugin }
        )
    }

    if (categoryTargetIds.isNotEmpty()) {
        CategoryChangeDialog(
            targetPluginIds = categoryTargetIds,
            onDismiss = { categoryTargetIds = emptySet() },
            onCategoryUpdated = {
                try {
                    com.UIN.Tool.widget.WidgetProvider.forceRefreshAllWidgets(context)
                    com.UIN.Tool.widget.Widget1x1Provider.refresh1x1Widgets(context)
                } catch (e: Exception) {
                }
            }
        )
    }

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
                .height(72.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(AppDimens.radiusSmall)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plugin.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(AppDimens.radiusMedium)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plugin.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(plugin.name, style = MaterialTheme.typography.bodyMedium)
            UnifiedCaptionText("v${plugin.versionName}")
        }
    }
}
