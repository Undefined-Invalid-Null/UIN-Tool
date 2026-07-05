// app/src/main/java/com/UIN/Tool/ui/screen/manage/GitHubMirrorScreen.kt
package com.UIN.Tool.ui.screen.manage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.NavController
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.MirrorManager
import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.Constants
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubMirrorScreen(
    navController: NavController
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

    fun loadMirrors() {
        val defaultMirrors = Constants.DEFAULT_MIRRORS.map { url ->
            MirrorItem(
                name = url.substringAfter("//").substringBefore("."),
                url = url,
                isDefault = true,
                remark = when {
                    url.contains("fastgit") -> "国内高速镜像"
                    url.contains("ghproxy") -> "代理加速"
                    url.contains("moeyy") -> "国内镜像"
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
                    showTestResult = "文件为空"
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
                showTestResult = "成功导入 $count 个镜像站"
                Logger.success("GitHubMirror", "导入 $count 个镜像站")
            } catch (e: Exception) {
                showTestResult = "导入失败: ${e.message}"
                Logger.e("GitHubMirror", "导入镜像失败", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun exportMirrors(uri: Uri) {
        try {
            val content = StringBuilder()
            content.append("# GitHub 镜像站列表\n# 格式: 名称|URL|备注\n\n")
            mirrors.forEach {
                content.append("${it.name}|${it.url}")
                if (it.remark.isNotEmpty()) content.append("|${it.remark}")
                content.append("\n")
            }
            context.contentResolver.openOutputStream(uri)?.write(content.toString().toByteArray())
            showTestResult = "导出成功"
            Logger.success("GitHubMirror", "导出镜像站列表")
        } catch (e: Exception) {
            showTestResult = "导出失败: ${e.message}"
            Logger.e("GitHubMirror", "导出镜像失败", e)
        }
    }

    fun testAllMirrors() {
        scope.launch {
            try {
                isLoading = true
                showTestResult = "正在测试镜像站..."
                val tested = mirrorManager.testMirrors(mirrors)
                mirrors = tested
                val reachableCount = tested.count { it.reachable == true }
                showTestResult = "测试完成，$reachableCount/${tested.size} 个可达"
                Logger.success("GitHubMirror", "测试完成")
            } catch (e: Exception) {
                showTestResult = "测试失败: ${e.message}"
                Logger.e("GitHubMirror", "测试镜像失败", e)
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
        topBar = {
            TopAppBar(
                title = { Text("GitHub 加速") },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.Default.ArrowBack,
                        onClick = { navController.navigateUp() }
                    )
                },
                actions = {
                    UIComponents.IconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { loadMirrors() }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            // CDN 开关
            UIComponents.Card(
                modifier = Modifier.fillMaxWidth().clickable { useCdn = !useCdn }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        UIComponents.BodyText("CDN 加速")
                        UIComponents.CaptionText("使用 CDN 代理加速下载")
                    }
                    UIComponents.ToggleSwitch(
                        checked = useCdn,
                        onCheckedChange = { useCdn = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UIComponents.SecondaryButton(
                    text = "添加",
                    icon = Icons.Default.Add,
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                )
                UIComponents.SecondaryButton(
                    text = "导入",
                    icon = Icons.Default.FileUpload,
                    onClick = { importLauncher.launch("text/plain") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UIComponents.SecondaryButton(
                    text = "导出",
                    icon = Icons.Default.FileDownload,
                    onClick = { exportLauncher.launch("mirrors_${System.currentTimeMillis()}.txt") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
                UIComponents.SecondaryButton(
                    text = "测试",
                    icon = Icons.Default.Check,
                    onClick = { testAllMirrors() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
            }

            UIComponents.SecondaryButton(
                text = "重置为默认",
                icon = Icons.Default.Refresh,
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 测试结果
            showTestResult?.let { result ->
                UIComponents.Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    UIComponents.BodyText(
                        result,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            if (mirrors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    UIComponents.BodyText("暂无镜像站")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mirrors) { mirror ->
                        UIComponents.Card(
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
                                        UIComponents.BodyText(mirror.name)
                                        if (mirror.isDefault) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text("默认")
                                                    }
                                                }
                                            ) {}
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
                                                        Text(if (it) "✅ 可达" else "❌ 不可达")
                                                    }
                                                }
                                            ) {}
                                        }
                                    }
                                    UIComponents.CaptionText(mirror.url)
                                    if (mirror.remark.isNotEmpty()) {
                                        UIComponents.CaptionText(mirror.remark)
                                    }
                                }
                                if (!mirror.isDefault) {
                                    UIComponents.IconButton(
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

            Spacer(modifier = Modifier.height(16.dp))
            
            UIComponents.PrimaryButton(
                text = "保存设置",
                onClick = {
                    preferenceManager.setEnabledMirrors(enabledMirrors.toList())
                    preferenceManager.setUseCdn(useCdn)
                    navController.navigateUp()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
        }
    }

    if (showResetDialog) {
        UIComponents.ConfirmDialog(
            title = "确认重置",
            message = "确定要重置为默认镜像站列表吗？",
            onConfirm = { loadMirrors(); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var remark by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加镜像站") },
            text = {
                Column {
                    UIComponents.TextInput(
                        value = name,
                        onValueChange = { name = it },
                        label = "名称",
                        placeholder = "如: FastGit",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UIComponents.TextInput(
                        value = url,
                        onValueChange = { url = it },
                        label = "URL",
                        placeholder = "https://example.com",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UIComponents.TextInput(
                        value = remark,
                        onValueChange = { remark = it },
                        label = "备注（可选）",
                        placeholder = "国内镜像",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                UIComponents.PrimaryButton(
                    text = "添加",
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
                UIComponents.TextButton(
                    text = "取消",
                    onClick = { showAddDialog = false }
                )
            }
        )
    }
}