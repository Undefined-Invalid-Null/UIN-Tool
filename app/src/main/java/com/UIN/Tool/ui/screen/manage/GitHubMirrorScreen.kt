// app/src/main/java/com/UIN/Tool/ui/screen/manage/GitHubMirrorScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
import androidx.lifecycle.viewmodel.compose.viewModel
import com.UIN.Tool.ui.viewmodel.GitHubMirrorViewModel
import com.UIN.Tool.constants.AppConstants as Constants

private const val TAG = "GitHubMirrorScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubMirrorScreen(
    onBack: () -> Unit
) {
    // D-29：偏好/镜像管理器走容器单例
    val preferenceManager = com.UIN.Tool.core.di.ServiceLocator.getPreferenceManager()

    // F-05：9 项页面状态与加载/导入/导出/测速协程全部下沉 GitHubMirrorViewModel，
    // 委托语法不变，页内赋值点零改动；旋转/重建不再丢测试结果与刷新状态。
    val mirrorViewModel: GitHubMirrorViewModel = viewModel()
    var mirrors by mirrorViewModel.mirrorsState
    var enabledMirrors by mirrorViewModel.enabledMirrorsState
    var useCdn by mirrorViewModel.useCdnState
    var isLoading by mirrorViewModel.isLoadingState
    var showResetDialog by mirrorViewModel.showResetDialogState
    var showAddDialog by mirrorViewModel.showAddDialogState
    var showTestResult by mirrorViewModel.showTestResultState
    var isRefreshing by mirrorViewModel.isRefreshingState
    var lastRefreshTime by mirrorViewModel.lastRefreshTimeState
    val pullRefreshState = rememberPullToRefreshState()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            mirrorViewModel.importMirrors(uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            mirrorViewModel.exportMirrors(uri)
        }
    }

    LaunchedEffect(Unit) { mirrorViewModel.loadMirrors() }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = Str.get(R.string.github_acceleration_2),
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = Str.get(R.string.add)
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { mirrorViewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            indicator = {
                PullRefreshIndicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 72.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                LastUpdatedCaption(
                    time = lastRefreshTime,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            // CDN 开关
            item {
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth().clickable { useCdn = !useCdn }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            UnifiedBodyText(Str.get(R.string.cdn_acceleration))
                            UnifiedCaptionText(Str.get(R.string.use_cdn_proxy_to_speed_up_downloads))
                        }
                        UnifiedSwitch(
                            checked = useCdn,
                            onCheckedChange = { useCdn = it }
                        )
                    }
                }
            }
            // 操作按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedButton(
                        text = Str.get(R.string.import_label),
                        icon = Icons.Default.FileUpload,
                        onClick = { importLauncher.launch("text/plain") },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Outlined,
                        enabled = !isLoading
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedButton(
                        text = Str.get(R.string.export),
                        icon = Icons.Default.FileDownload,
                        onClick = { exportLauncher.launch("mirrors_${System.currentTimeMillis()}.txt") },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Outlined,
                        enabled = !isLoading
                    )
                    UnifiedButton(
                        text = Str.get(R.string.test),
                        icon = Icons.Default.Check,
                        onClick = { mirrorViewModel.testAllMirrors() },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Outlined,
                        enabled = !isLoading
                    )
                }
            }
            item {
                UnifiedButton(
                    text = Str.get(R.string.reset_to_default),
                    icon = Icons.Default.Refresh,
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    variant = ButtonVariant.Outlined
                )
            }

            // 测试结果
            showTestResult?.let { result ->
                item {
                    UnifiedCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        UnifiedBodyText(
                            result,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            if (mirrors.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        UnifiedBodyText(Str.get(R.string.no_mirrors_yet))
                    }
                }
            } else {
                items(mirrors) { mirror ->
                    UnifiedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                enabledMirrors = if (enabledMirrors.contains(mirror.url)) {
                                    enabledMirrors - mirror.url
                                } else {
                                    enabledMirrors + mirror.url
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enabledMirrors.contains(mirror.url),
                                onCheckedChange = {
                                    enabledMirrors = if (enabledMirrors.contains(mirror.url)) {
                                        enabledMirrors - mirror.url
                                    } else {
                                        enabledMirrors + mirror.url
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UnifiedBodyText(mirror.name)
                                    if (mirror.isDefault) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(Str.get(R.string.default_label))
                                                }
                                            }
                                        ) { }
                                    }
                                    mirror.reachable?.let {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = if (it) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.errorContainer
                                                    }
                                                ) {
                                                    Text(if (it) Str.get(R.string.reachable) else Str.get(R.string.unreachable))
                                                }
                                            }
                                        ) { }
                                    }
                                }
                                UnifiedCaptionText(mirror.url)
                                if (mirror.remark.isNotEmpty()) {
                                    UnifiedCaptionText(mirror.remark)
                                }
                            }
                            if (!mirror.isDefault) {
                                UnifiedIconButton(
                                    icon = Icons.Default.Close,
                                    onClick = {
                                        mirrors = mirrors.filter { it.url != mirror.url }
                                        enabledMirrors = enabledMirrors - mirror.url
                                    },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

            UnifiedButton(
                text = Str.get(R.string.save_settings),
                onClick = {
                    preferenceManager.setEnabledMirrors(enabledMirrors.toList())
                    preferenceManager.setUseCdn(useCdn)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
        }
        }
    }

    // 确认重置对话框
    if (showResetDialog) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.confirm_reset),
            message = Str.get(R.string.reset_to_the_default_mirror_list),
            onConfirm = { mirrorViewModel.loadMirrors(); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }

    // 添加镜像对话框
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var remark by remember { mutableStateOf("") }
        // F-06：URL 格式校验，非法值不再直接落盘（落盘后走代理必然失败且无提示）
        var urlError by remember { mutableStateOf(false) }

        UnifiedAlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(Str.get(R.string.add_mirror)) },
            text = {
                Column {
                    UnifiedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = Str.get(R.string.name),
                        placeholder = Str.get(R.string.e_g_fastgit),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = "URL",
                        placeholder = "https://example.com",
                        error = if (urlError) Str.get(R.string.invalid_url_e_message, url) else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = Str.get(R.string.note_optional),
                        placeholder = Str.get(R.string.domestic_mirror),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.add),
                    onClick = {
                        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                        if (name.isNotBlank() && url.isNotBlank() && isValidMirrorUrl(normalized)) {
                            mirrors = mirrors + MirrorItem(
                                name = name,
                                url = normalized,
                                remark = remark,
                                isDefault = false
                            )
                            showAddDialog = false
                        } else {
                            urlError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { showAddDialog = false },
                    variant = ButtonVariant.Text
                )
            }
        )
    }
}

// F-06：镜像地址必须是可解析的 http(s) 主机（含至少一个点、无空格）
private fun isValidMirrorUrl(raw: String): Boolean {
    if (raw.contains(' ')) return false
    return try {
        val u = java.net.URL(raw)
        (u.protocol == "http" || u.protocol == "https") && u.host.contains('.')
    } catch (e: Exception) {
        false
    }
}
