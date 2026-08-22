// app/src/main/java/com/UIN/Tool/ui/screen/repo/RepoScreen.kt
package com.UIN.Tool.ui.screen.repo

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.RepoPluginInfo
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.viewmodel.RepoViewModel
import com.UIN.Tool.utils.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "RepoScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repoViewModel: RepoViewModel = viewModel()
    val pluginManager = ServiceLocator.getPluginManager()

    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        repoViewModel.init(context.applicationContext)
        repoViewModel.loadPlugins()
        AppLog.i(TAG, Str.get(R.string.start_loading_plugin_list))
    }

    val uiState by repoViewModel.uiState.collectAsState()
    val filteredPlugins by repoViewModel.filteredPlugins.collectAsState()
    val installedIds by pluginManager.installedPluginIds.collectAsState()
    val downloadProgress by repoViewModel.downloadProgress.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var showDetailDialog by remember { mutableStateOf<RepoPluginInfo?>(null) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                repoViewModel.refresh()
                delay(400)
                lastRefreshTime = UIComponents.currentTimeString()
                isRefreshing = false
            }
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            UIComponents.PullRefreshIndicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedTitleText(Str.get(R.string.plugin_repository))
            }
        }
        item {
            UIComponents.LastUpdatedCaption(
                time = lastRefreshTime,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        item {
            UnifiedTextField(
                value = searchText,
                onValueChange = { text ->
                    searchText = text
                    searchJob?.cancel()
                    searchJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        delay(300)
                        repoViewModel.searchPlugins(text)
                    }
                },
                placeholder = Str.get(R.string.search_plugins),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                leadingIcon = Icons.Default.Search
            )
        }

        // 下载进度
        if (downloadProgress.isDownloading) {
            item {
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UnifiedBodyText(Str.get(R.string.downloading_downloadprogress_plugini, downloadProgress.pluginId))
                            UnifiedBodyText(
                                "${downloadProgress.progress}%",
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                        UnifiedLinearProgressIndicator(progress = downloadProgress.progress / 100f)
                        if (downloadProgress.total > 0) {
                            UnifiedCaptionText(
                                "${formatSize(downloadProgress.downloaded)} / ${formatSize(downloadProgress.total)}"
                            )
                        }
                    }
                }
            }
        }

        when {
            uiState.isLoading && filteredPlugins.isEmpty() -> item {
                UIComponents.PluginListSkeleton(
                    modifier = Modifier.fillParentMaxSize()
                )
            }
            filteredPlugins.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UnifiedBodyText(
                            if (searchText.isNotEmpty()) Str.get(R.string.no_matching_plugins) else Str.get(R.string.no_plugins_available)
                        )
                        UnifiedCaptionText(
                            if (searchText.isNotEmpty()) Str.get(R.string.try_other_keywords) else Str.get(R.string.check_your_network_connection_and_re)
                        )
                    }
                }
            }
            else -> items(filteredPlugins) { plugin ->
                RepoPluginCard(
                    plugin = plugin,
                    isInstalled = installedIds.contains(plugin.pluginId),
                    isDownloading = downloadProgress.isDownloading && downloadProgress.pluginId == plugin.pluginId,
                    downloadProgress = downloadProgress.progress,
                    onInstall = { repoViewModel.downloadAndInstall(plugin) },
                    onOpen = { pluginManager.openPlugin(plugin.pluginId, context) },
                    onClick = { showDetailDialog = plugin }
                )
            }
        }
    }
    }

    // ==================== 插件详情对话框 ====================
    if (showDetailDialog != null) {
        val plugin = showDetailDialog!!
        UnifiedConfirmDialog(
            title = plugin.name,
            message = buildString {
                append("ID: ${plugin.pluginId}\n")
                append(Str.get(R.string.version_plugin_versionname_n, plugin.versionName))
                append(Str.get(R.string.author_plugin_author_n, plugin.author))
                append(Str.get(R.string.size_plugin_getformattedsize_n, plugin.getFormattedSize()))
                append(Str.get(R.string.updated_plugin_getformatteddate_n, plugin.getFormattedDate()))
                if (plugin.description.isNotEmpty()) {
                    append(Str.get(R.string.ndescription_plugin_description_n, plugin.description))
                }
                if (plugin.updateLog.isNotEmpty()) {
                    append(Str.get(R.string.repo_update_log, plugin.updateLog.take(200), if (plugin.updateLog.length > 200) "..." else ""))
                }
            },
            confirmText = if (installedIds.contains(plugin.pluginId)) Str.get(R.string.open) else Str.get(R.string.install),
            dismissText = Str.get(R.string.close),
            onConfirm = {
                if (installedIds.contains(plugin.pluginId)) {
                    pluginManager.openPlugin(plugin.pluginId, context)
                } else {
                    repoViewModel.downloadAndInstall(plugin)
                }
                showDetailDialog = null
            },
            onDismiss = { showDetailDialog = null }
        )
    }
}

@Composable
fun RepoPluginCard(
    plugin: RepoPluginInfo,
    isInstalled: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onClick: () -> Unit
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isInstalled) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(Str.get(R.string.installed))
                            }
                        }
                    ) { }
                }
            }
            UnifiedCaptionText(plugin.pluginId)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                UnifiedCaptionText(Str.get(R.string.author_plugin_author, plugin.author))
                UnifiedCaptionText(plugin.getFormattedDate())
            }
            if (plugin.description.isNotEmpty()) {
                UnifiedBodyText(
                    plugin.description,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedCaptionText(plugin.getFormattedSize())
                when {
                    isDownloading -> {
                        UnifiedButton(
                            text = "${downloadProgress}%",
                            onClick = {},
                            modifier = Modifier.height(32.dp),
                            loading = true
                        )
                    }
                    isInstalled -> {
                        UnifiedButton(
                            text = Str.get(R.string.open),
                            onClick = onOpen,
                            modifier = Modifier.height(32.dp),
                            variant = ButtonVariant.Outlined
                        )
                    }
                    else -> {
                        UnifiedButton(
                            text = Str.get(R.string.install),
                            onClick = onInstall,
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}