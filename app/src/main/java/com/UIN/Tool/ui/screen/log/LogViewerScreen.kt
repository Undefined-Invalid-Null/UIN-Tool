// app/src/main/java/com/UIN/Tool/ui/screen/log/LogViewerScreen.kt
package com.UIN.Tool.ui.screen.log

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.CrashLogUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    navController: NavController = rememberNavController()
) {
    val context = LocalContext.current
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    var showCrashMessage by remember { mutableStateOf(CrashLogUtils.shouldNavigateToLogs(context)) }

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
            AppLog.e("LogViewer", Str.get(R.string.failed_to_load_logs), e)
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
            AppLog.success("LogViewer", Str.get(R.string.logs_exported))
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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) { exportLogs(uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Str.get(R.string.runtime_logs)) },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { navController.navigateUp() }
                    )
                },
                actions = {
                    UIComponents.IconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { loadLogs() }
                    )
                    UIComponents.IconButton(
                        icon = Icons.Default.DeleteSweep,
                        onClick = { showClearAllConfirm = true }
                    )
                    UIComponents.IconButton(
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            exportLauncher.launch("UIN_Tool_Log_$timestamp.txt")
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // 崩溃提示 - 使用 Material Icon 替换 ⚠️
            if (showCrashMessage) {
                UIComponents.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
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
                                UIComponents.BodyText(
                                    Str.get(R.string.an_exception_occurred),
                                    color = MaterialTheme.colorScheme.error
                                )
                                UIComponents.CaptionText(
                                    Str.get(R.string.please_check_the_crash_log_below_for),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        UIComponents.IconButton(
                            icon = Icons.Default.Close,
                            onClick = { showCrashMessage = false },
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 统计信息
            UIComponents.Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    UIComponents.BodyText(Str.get(R.string.loglines_size_lines_in_total, logLines.size))
                    UIComponents.CaptionText(Str.get(R.string.latest_logs_at_the_top))
                }
            }

            when {
                isLoading -> UIComponents.FullScreenLoading()
                logLines.isEmpty() -> {
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
                            UIComponents.TitleText(Str.get(R.string.no_log_records_yet))
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logLines) { line ->
                        val color = when {
                            line.contains("[ERROR]") || line.contains("[E]") -> MaterialTheme.colorScheme.error
                            line.contains("[WARN]") || line.contains("[W]") -> MaterialTheme.colorScheme.tertiary
                            line.contains("[SUCCESS]") || line.contains("[✓]") -> MaterialTheme.colorScheme.primary
                            line.contains("==================") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val isHeader = line.contains("==================")
                        UIComponents.Card(
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
        UIComponents.ConfirmDialog(
            title = Str.get(R.string.confirm_clear),
            message = Str.get(R.string.clear_all_historical_log_files),
            onConfirm = { clearAllLogs(); showClearAllConfirm = false },
            onDismiss = { showClearAllConfirm = false }
        )
    }
}