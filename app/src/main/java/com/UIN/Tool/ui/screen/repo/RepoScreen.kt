// app/src/main/java/com/UIN/Tool/ui/screen/repo/RepoScreen.kt
package com.UIN.Tool.ui.screen.repo

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.RepoPluginInfo
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.viewmodel.RepoViewModel
import com.UIN.Tool.utils.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "RepoScreen"

@Composable
fun RepoScreen() {
    val context = LocalContext.current
    val repoViewModel: RepoViewModel = viewModel()
    val pluginManager = ServiceLocator.getPluginManager()

    LaunchedEffect(Unit) {
        repoViewModel.init(context)
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIComponents.TitleText(Str.get(R.string.plugin_repository))
            UIComponents.IconButton(
                icon = Icons.Default.Refresh,
                onClick = { repoViewModel.refresh() }
            )
        }

        UIComponents.TextInput(
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

        // 下载进度
        if (downloadProgress.isDownloading) {
            UIComponents.Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UIComponents.BodyText(Str.get(R.string.downloading_downloadprogress_plugini, downloadProgress.pluginId))
                        UIComponents.BodyText(
                            "${downloadProgress.progress}%",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    UIComponents.LinearProgressIndicator(progress = downloadProgress.progress / 100f)
                    if (downloadProgress.total > 0) {
                        UIComponents.CaptionText(
                            "${formatSize(downloadProgress.downloaded)} / ${formatSize(downloadProgress.total)}"
                        )
                    }
                }
            }
        }

        when {
            uiState.isLoading && filteredPlugins.isEmpty() -> {
                UIComponents.FullScreenLoading(Str.get(R.string.loading_plugin_list))
            }
            filteredPlugins.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                        UIComponents.BodyText(
                            if (searchText.isNotEmpty()) Str.get(R.string.no_matching_plugins) else Str.get(R.string.no_plugins_available)
                        )
                        UIComponents.CaptionText(
                            if (searchText.isNotEmpty()) Str.get(R.string.try_other_keywords) else Str.get(R.string.check_your_network_connection_and_re)
                        )
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredPlugins) { plugin ->
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
    }

    // ==================== 插件详情对话框 ====================
    if (showDetailDialog != null) {
        val plugin = showDetailDialog!!
        UIComponents.ConfirmDialog(
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
    UIComponents.Card(
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
            UIComponents.CaptionText(plugin.pluginId)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                UIComponents.CaptionText(Str.get(R.string.author_plugin_author, plugin.author))
                UIComponents.CaptionText(plugin.getFormattedDate())
            }
            if (plugin.description.isNotEmpty()) {
                UIComponents.BodyText(
                    plugin.description,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UIComponents.CaptionText(plugin.getFormattedSize())
                when {
                    isDownloading -> {
                        UIComponents.PrimaryButton(
                            text = "${downloadProgress}%",
                            onClick = {},
                            modifier = Modifier.height(32.dp),
                            loading = true
                        )
                    }
                    isInstalled -> {
                        UIComponents.SecondaryButton(
                            text = Str.get(R.string.open),
                            onClick = onOpen,
                            modifier = Modifier.height(32.dp)
                        )
                    }
                    else -> {
                        UIComponents.PrimaryButton(
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