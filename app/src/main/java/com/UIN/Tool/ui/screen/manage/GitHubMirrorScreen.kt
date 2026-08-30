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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.MirrorManager
import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private const val TAG = "GitHubMirrorScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubMirrorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferenceManager = PreferenceManager(context)
    val mirrorManager = MirrorManager(OkHttpClient())

    var mirrors by remember { mutableStateOf<List<MirrorItem>>(emptyList()) }
    var enabledMirrors by remember { mutableStateOf<Set<String>>(emptySet()) }
    var useCdn by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showTestResult by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    fun loadMirrors() {
        val defaultMirrors = Constants.DEFAULT_MIRRORS.map { url ->
            MirrorItem(
                name = url.substringAfter("//").substringBefore("."),
                url = url,
                isDefault = true,
                remark = when {
                    url.contains("fastgit") -> Str.get(R.string.fast_domestic_mirror)
                    url.contains("ghproxy") -> Str.get(R.string.proxy_acceleration)
                    url.contains("moeyy") -> Str.get(R.string.domestic_mirror)
                    else -> ""
                }
            )
        }
        mirrors = defaultMirrors
        val enabled = preferenceManager.getEnabledMirrors()
        enabledMirrors = if (enabled.isNotEmpty()) enabled.toSet() else defaultMirrors.take(3).map { it.url }.toSet()
        useCdn = preferenceManager.isUseCdn()
    }

    fun importMirrors(uri: Uri) {
        scope.launch {
            try {
                isLoading = true
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (content.isNullOrEmpty()) {
                    showTestResult = Str.get(R.string.file_is_empty)
                    return@launch
                }
                var count = 0
                content.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        mirrorManager.parseMirrorFromString(trimmed)?.let {
                            mirrors = mirrors + it
                            count++
                        }
                    }
                }
                showTestResult = Str.get(R.string.imported_count_mirror_s, count)
                AppLog.success(TAG, Str.get(R.string.imported_count_mirror_s_2, count))
            } catch (e: Exception) {
                showTestResult = Str.get(R.string.import_failed_e_message, e.message)
                AppLog.e(TAG, Str.get(R.string.failed_to_import_mirrors), e)
            } finally {
                isLoading = false
            }
        }
    }

    fun exportMirrors(uri: Uri) {
        try {
            val content = StringBuilder()
            content.append(Str.get(R.string.github_mirror_list_n_format_name_url))
            mirrors.forEach {
                content.append("${it.name}|${it.url}")
                if (it.remark.isNotEmpty()) content.append("|${it.remark}")
                content.append("\n")
            }
            context.contentResolver.openOutputStream(uri)?.write(content.toString().toByteArray())
            showTestResult = Str.get(R.string.export_successful)
            AppLog.success(TAG, Str.get(R.string.exported_mirror_list))
        } catch (e: Exception) {
            showTestResult = Str.get(R.string.export_failed_e_message, e.message)
            AppLog.e(TAG, Str.get(R.string.failed_to_export_mirrors), e)
        }
    }

    fun testAllMirrors() {
        scope.launch {
            try {
                isLoading = true
                showTestResult = Str.get(R.string.testing_mirrors)
                val tested = mirrorManager.testMirrors(mirrors)
                mirrors = tested
                val reachableCount = tested.count { it.reachable == true }
                showTestResult = Str.get(R.string.test_complete_reachablecount_tested_, reachableCount, tested.size)
                AppLog.success(TAG, Str.get(R.string.test_complete))
            } catch (e: Exception) {
                showTestResult = Str.get(R.string.test_failed_e_message, e.message)
                AppLog.e(TAG, Str.get(R.string.failed_to_test_mirrors), e)
            } finally {
                isLoading = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importMirrors(uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            exportMirrors(uri)
        }
    }

    LaunchedEffect(Unit) { loadMirrors() }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            UIComponents.ManageTopAppBar(
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
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadMirrors()
                    delay(400)
                    lastRefreshTime = UIComponents.currentTimeString()
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            indicator = {
                UIComponents.PullRefreshIndicator(
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
                UIComponents.LastUpdatedCaption(
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
                        onClick = { testAllMirrors() },
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
            onConfirm = { loadMirrors(); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }

    // 添加镜像对话框
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var remark by remember { mutableStateOf("") }

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
                        if (name.isNotEmpty() && url.isNotEmpty()) {
                            mirrors = mirrors + MirrorItem(
                                name = name,
                                url = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url",
                                remark = remark,
                                isDefault = false
                            )
                            showAddDialog = false
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