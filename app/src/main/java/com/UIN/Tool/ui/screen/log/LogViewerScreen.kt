// app/src/main/java/com/UIN/Tool/ui/screen/log/LogViewerScreen.kt
package com.UIN.Tool.ui.screen.log

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.UIN.Tool.utils.Constants
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
                    logLines = (crashLines + listOf("--- 分隔线 ---") + logLines)
                }
            }
        } catch (e: Exception) {
            Logger.e("LogViewer", "加载日志失败", e)
            logLines = listOf("加载日志失败: ${e.message}")
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
                android.widget.Toast.makeText(context, "没有日志可导出", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                logFile.inputStream().use { input -> input.copyTo(output) }
            }
            android.widget.Toast.makeText(context, "日志已导出", android.widget.Toast.LENGTH_SHORT).show()
            Logger.success("LogViewer", "日志已导出")
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllLogs() {
        val logDir = File(Constants.LOG_DIR)
        var count = 0
        logDir.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".log") && it.delete()) count++ }
        loadLogs()
        android.widget.Toast.makeText(context, "已删除 $count 个日志文件", android.widget.Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) { exportLogs(uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行日志") },
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
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // 崩溃提示
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
                        Column {
                            UIComponents.BodyText(
                                "⚠️ 应用发生异常",
                                color = Color(0xFF856404)
                            )
                            UIComponents.CaptionText(
                                "请查看下方的崩溃日志了解详情",
                                color = Color(0xFF856404)
                            )
                        }
                        UIComponents.IconButton(
                            icon = Icons.Default.Close,
                            onClick = { showCrashMessage = false },
                            tint = Color(0xFF856404)
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
                    UIComponents.BodyText("共 ${logLines.size} 行")
                    UIComponents.CaptionText("最新日志在上方")
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
                            UIComponents.TitleText("暂无日志记录")
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
            title = "确认清空",
            message = "确定要清空所有历史日志文件吗？",
            onConfirm = { clearAllLogs(); showClearAllConfirm = false },
            onDismiss = { showClearAllConfirm = false }
        )
    }
}