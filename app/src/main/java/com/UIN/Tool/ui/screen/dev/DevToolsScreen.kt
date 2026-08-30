// app/src/main/java/com/UIN/Tool/ui/screen/dev/DevToolsScreen.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "DevToolsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    var showCrashMessage by remember { mutableStateOf(CrashLogUtils.shouldNavigateToLogs(context)) }
    val pluginManager = com.UIN.Tool.core.di.ServiceLocator.getPluginManager()
    var nativeMultiInstance by remember {
        mutableStateOf(pluginManager.isNativeMultiInstanceEnabled())
    }
    var retainSession by remember {
        mutableStateOf(pluginManager.isSharedSessionRetainEnabled())
    }
    var ignoreSignature by remember {
        mutableStateOf(pluginManager.isDevIgnoreSignatureEnabled())
    }

    fun loadLogs() {
        isLoading = true
        try {
            val logFile = File(
                Constants.LOG_DIR,
                "uin_tool_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.log"
            )
            logLines = if (logFile.exists()) logFile.readLines().reversed() else emptyList()

            val crashLogFile = File(Constants.LOG_DIR, "crash_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.log")
            if (crashLogFile.exists()) {
                val crashLines = crashLogFile.readLines().reversed()
                if (crashLines.isNotEmpty()) {
                    logLines = (crashLines + listOf(Str.get(R.string.separator)) + logLines)
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_load_logs), e)
            logLines = listOf(Str.get(R.string.failed_to_load_log_e_message, e.message))
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        if (showCrashMessage) {
            CrashLogUtils.clearNavigateFlag(context)
        }
        loadLogs()
    }

    fun exportLogs(uri: Uri) {
        try {
            val logFile = File(Constants.LOG_DIR, "uin_tool_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.log")
            if (!logFile.exists()) {
                AppToast.warning(context, Str.get(R.string.no_logs_to_export))
                return
            }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                logFile.inputStream().use { input -> input.copyTo(output) }
            }
            AppToast.success(context, Str.get(R.string.logs_exported))
            AppLog.success(TAG, Str.get(R.string.logs_exported))
        } catch (e: Exception) {
            AppToast.error(context, Str.get(R.string.export_failed_e_message, e.message))
        }
    }

    fun clearAllLogs() {
        val logDir = File(Constants.LOG_DIR)
        var count = 0
        logDir.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".log") && it.delete()) count++ }
        loadLogs()
        AppToast.info(context, Str.get(R.string.deleted_count_log_file_s, count))
    }

    fun saveDeveloperOptions() {
        pluginManager.setNativeMultiInstanceEnabled(nativeMultiInstance)
        pluginManager.setSharedSessionRetainEnabled(retainSession)
        pluginManager.setDevIgnoreSignatureEnabled(ignoreSignature)
        AppToast.info(context, Str.get(R.string.dev_options_saved))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) { exportLogs(uri) }
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.dev_tools),
                onBack = onBack,
                actions = {
                    UnifiedIconButton(
                        icon = Icons.Default.DeleteSweep,
                        onClick = { showClearAllConfirm = true }
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            exportLauncher.launch("UIN_Tool_Log_$timestamp.txt")
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadLogs()
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // ==================== 开发者选项 ====================
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    UnifiedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        UnifiedSectionTitle(Str.get(R.string.developer_options))
                        Spacer(modifier = Modifier.height(4.dp))

                        // 原生插件多开（实验性）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = nativeMultiInstance,
                                onCheckedChange = { nativeMultiInstance = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = Str.get(R.string.dev_native_plugin_multi_instance),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = Str.get(R.string.dev_native_plugin_multi_instance_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 关闭时保留会话（共享端口模式）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = retainSession,
                                onCheckedChange = { retainSession = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = Str.get(R.string.dev_shared_session_retain),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = Str.get(R.string.dev_shared_session_retain_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 忽略签名验证（仅开发用）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = ignoreSignature,
                                onCheckedChange = { ignoreSignature = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = Str.get(R.string.dev_ignore_signature_verification),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = Str.get(R.string.dev_ignore_signature_verification_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedButton(
                            text = Str.get(R.string.save),
                            onClick = { saveDeveloperOptions() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ==================== 运行日志标题 ====================
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UnifiedSectionTitle(Str.get(R.string.runtime_logs))
                    }
                }

                item {
                    UIComponents.LastUpdatedCaption(
                        time = lastRefreshTime,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                    )
                }

                // 崩溃提示
                if (showCrashMessage) {
                    item {
                        UnifiedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        UnifiedBodyText(
                                            Str.get(R.string.an_exception_occurred),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        UnifiedCaptionText(
                                            Str.get(R.string.please_check_the_crash_log_below_for),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                UnifiedIconButton(
                                    icon = Icons.Default.Close,
                                    onClick = { showCrashMessage = false },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // 统计信息
                item {
                    UnifiedCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            UnifiedBodyText(Str.get(R.string.loglines_size_lines_in_total, logLines.size))
                            UnifiedCaptionText(Str.get(R.string.latest_logs_at_the_top))
                        }
                    }
                }

                when {
                    isLoading -> item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            UnifiedLoadingIndicator(message = Str.get(R.string.loading))
                        }
                    }
                    logLines.isEmpty() -> item {
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
                                UnifiedTitleText(Str.get(R.string.no_log_records_yet))
                            }
                        }
                    }
                    else -> items(logLines) { line ->
                        val color = when {
                            line.contains("[ERROR]") || line.contains("[E]") -> MaterialTheme.colorScheme.error
                            line.contains("[WARN]") || line.contains("[W]") -> MaterialTheme.colorScheme.tertiary
                            line.contains("[SUCCESS]") || line.contains("[✓]") -> MaterialTheme.colorScheme.primary
                            line.contains("==================") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val isHeader = line.contains("==================")
                        UnifiedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = if (isHeader) 12.sp else 11.sp,
                                    fontWeight = if (isHeader) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                ),
                                color = color,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        UnifiedConfirmDialog(
            title = Str.get(R.string.confirm_clear),
            message = Str.get(R.string.clear_all_historical_log_files),
            onConfirm = { clearAllLogs(); showClearAllConfirm = false },
            onDismiss = { showClearAllConfirm = false }
        )
    }
}
