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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.log.Logger
import com.UIN.Tool.log.LogcatRecorder
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.components.unified.UnifiedBodyText
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedConfirmDialog
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedLoadingIndicator
import com.UIN.Tool.ui.components.unified.UnifiedTitleText
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    // 6.2.0 批次36：应用日志 / logcat 双来源开关。
    // READ_LOGS 是 signature|privileged|development 级，普通安装拿不到、也无法弹窗申请，
    // 故按 checkSelfPermission 结果降级展示提示（未授权时仍能读本 UID 自己的日志行）。
    var logcatMode by remember { mutableStateOf(false) }
    var logcatDenied by remember { mutableStateOf(false) }
    val grantCommand = remember(context) {
        "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"
    }
    val clipboard = LocalClipboardManager.current
    val pullRefreshState = rememberPullToRefreshState()

    var showCrashMessage by remember { mutableStateOf(CrashLogUtils.shouldNavigateToLogs(context)) }

    fun loadLogs() {
        isLoading = true
        // 6.2.0 批次36/37：logcat 来源。AppLog/Logger 的文件日志**不含**库直接写到
        // logcat 的异常（如 sora 的 AsyncAnalysis），排查编辑器高亮不显示时这正是盲区。
        // 批次37 起 LogcatRecorder 默认把 logcat 落盘到同一目录，故优先读文件（可导出、
        // 跨进程重启留档）；文件尚未产生内容时回退到实时 `logcat -d` 读取。
        if (logcatMode) {
            val logcatFile = File(Constants.LOG_DIR, LogcatRecorder.fileForToday())
            val fileLines = readTailLines(logcatFile, maxLines = TAIL_LINES).asReversed()
            if (fileLines.isNotEmpty()) {
                logcatDenied = false
                logLines = fileLines
            } else {
                val res = readLogcat(TAIL_LINES, context)
                logcatDenied = res.denied
                logLines = res.lines
            }
            isLoading = false
            return
        }
        logcatDenied = false
        try {
            val logFile = File(
                Constants.LOG_DIR,
                "uin_tool_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.log"
            )
            // G-12：只读末尾若干行（原来整文件 readLines 进内存，日志无限增长会 OOM）
            logLines = readTailLines(logFile, maxLines = TAIL_LINES).asReversed()

            val crashLogFile = File(Constants.LOG_DIR, "crash_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.log")
            if (crashLogFile.exists()) {
                val crashLines = readTailLines(crashLogFile, maxLines = TAIL_LINES).asReversed()
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
        // 6.2.0 批次37：按当前来源导出对应文件 —— 应用日志导出 AppLog 的当日文件，
        // logcat 导出 LogcatRecorder 的当日落盘文件（此前固定只导应用日志，logcat
        // 来源下导出的是空的 AppLog 文件，等于"导不出 logcat"）。
        try {
            val logFile = if (logcatMode) {
                File(Constants.LOG_DIR, LogcatRecorder.fileForToday())
            } else {
                File(
                    Constants.LOG_DIR,
                    "uin_tool_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}.log"
                )
            }
            if (!logFile.exists() || logFile.length() == 0L) {
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
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = Str.get(R.string.runtime_logs),
                onBack = onBack,
                actions = {
                    // 6.2.0 批次36：应用日志 ↔ logcat 来源切换
                    UnifiedIconButton(
                        icon = Icons.Default.Terminal,
                        onClick = {
                            logcatMode = !logcatMode
                            loadLogs()
                            AppToast.info(
                                context,
                                if (logcatMode) Str.get(R.string.logcat_source)
                                else Str.get(R.string.runtime_logs)
                            )
                        },
                        contentDescription = if (logcatMode) Str.get(R.string.runtime_logs)
                        else Str.get(R.string.logcat_source)
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.DeleteSweep,
                        onClick = { showClearAllConfirm = true }
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            // 导出文件名带上来源，避免两份日志导出后同名互相覆盖
                            val prefix = if (logcatMode) "UIN_Tool_Logcat" else "UIN_Tool_Log"
                            exportLauncher.launch("${prefix}_$timestamp.txt")
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
                    lastRefreshTime = currentTimeString()
                    isRefreshing = false
                }
            },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                LastUpdatedCaption(
                    time = lastRefreshTime,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            // 崩溃提示 - 使用 Material Icon 替换 ⚠️
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

            // 6.2.0 批次36：READ_LOGS 未授权时的降级说明 + 一键复制 adb 授权命令。
            if (logcatMode && logcatDenied) {
                item {
                    UnifiedCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        ) {
                            UnifiedBodyText(Str.get(R.string.logcat_denied_hint, grantCommand))
                            Spacer(modifier = Modifier.height(8.dp))
                            UnifiedButton(
                                text = Str.get(R.string.copy_grant_command),
                                size = ButtonSize.Small,
                                onClick = {
                                    clipboard.setText(AnnotatedString(grantCommand))
                                    AppToast.success(context, Str.get(R.string.copied_to_clipboard))
                                }
                            )
                        }
                    }
                }
            }

            when {
                isLoading -> item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        UnifiedLoadingIndicator()
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

// G-12：日志只读末尾 N 行 / 最多 2MB，避免全量读入内存
private const val TAIL_LINES = 2000
private const val TAIL_MAX_BYTES = 2L * 1024 * 1024

private fun readTailLines(file: File, maxLines: Int = TAIL_LINES, maxBytes: Long = TAIL_MAX_BYTES): List<String> {
    if (!file.exists() || !file.isFile) return emptyList()
    return try {
        val len = file.length()
        if (len == 0L) return emptyList()
        val skip = maxOf(0L, len - maxBytes)
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(skip)
            val buf = ByteArray((len - skip).toInt())
            raf.readFully(buf)
            var text = String(buf, Charsets.UTF_8)
            if (skip > 0) {
                // 起点可能截断首行，丢掉这半行
                val nl = text.indexOf('\n')
                text = if (nl >= 0) text.substring(nl + 1) else ""
            }
            var lines = text.split('\n')
            if (lines.isNotEmpty() && lines.last().isEmpty()) lines = lines.dropLast(1)
            lines.takeLast(maxLines).map { it.removeSuffix("\r") }
        }
    } catch (e: Exception) {
        AppLog.e("LogViewer", "readTailLines failed", e)
        emptyList()
    }
}

// ==================== 6.2.0 批次36：logcat 来源 ====================

/** [readLogcat] 的结果：行按「最新在前」排列；[denied] 表示系统未授予 READ_LOGS。 */
private data class LogcatResult(val lines: List<String>, val denied: Boolean)

/**
 * 读取 logcat（`logcat -d -v threadtime -t N`，随后按本进程 pid 过滤）。
 *
 * 权限说明：
 * - `READ_LOGS` 是 **signature|privileged|development** 级，普通安装拿不到，也不是
 *   运行时权限、无法弹窗申请；有 adb 时可用 `pm grant` 授予（页面会给出命令）。
 * - **未授权时 Android 仍允许进程读自己 UID 的日志行**，所以本应用与内嵌库
 *   （sora 的 `AsyncAnalysis`、ART/Compose 的报错）打到 logcat 的内容依旧可读 ——
 *   而 `AppLog`/`Logger` 的文件日志并不收这些，正是此前排查高亮不显示的盲区。
 * - 有授权时 logcat 会返回**全量**，故按 pid 过滤，否则整机日志会把屏刷满；
 *   过滤后若一行不剩（格式差异等）则回退到原始输出，宁可噪也不给空白页。
 *
 * 用 ProcessBuilder + `redirectErrorStream(true)`：把 stderr 并进 stdout，
 * 避免「先读 stdout 时进程因 stderr 缓冲区写满而阻塞」的经典死锁。
 */
private fun readLogcat(maxLines: Int, context: android.content.Context): LogcatResult {
    val denied = context.checkSelfPermission(android.Manifest.permission.READ_LOGS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    return try {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "-t", maxLines.toString()
        )
            .redirectErrorStream(true)
            .start()
        val raw = process.inputStream.bufferedReader().use { it.readLines() }
        process.waitFor()

        // threadtime 行格式：`10-15 22:39:46.733 <PID> <TID> D Tag: msg` → 第 3 段是 pid。
        val pid = android.os.Process.myPid().toString()
        val mine = raw.filter { line ->
            val parts = line.split(' ')
            parts.size >= 3 && parts[2] == pid
        }
        LogcatResult((mine.ifEmpty { raw }).asReversed(), denied)
    } catch (e: Exception) {
        AppLog.e("LogViewer", "readLogcat failed", e)
        LogcatResult(listOf("read logcat failed: ${e.message}"), true)
    }
}
