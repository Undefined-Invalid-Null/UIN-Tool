// app/src/main/java/com/UIN/Tool/ui/screen/manage/ManageScreen.kt
package com.UIN.Tool.ui.screen.manage

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.core.update.UpdateChecker
import com.UIN.Tool.core.update.UpdateDownloader
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.ui.screen.manage.UIConfigActivity
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.log.LogViewerActivity
import com.UIN.Tool.ui.screen.backup.BackupManagerActivity
import com.UIN.Tool.ui.screen.docs.DocBrowserActivity
import com.UIN.Tool.ui.screen.permission.PermissionManagerActivity
import com.UIN.Tool.utils.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class ManageMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    checkUpdate: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()
    val preferenceManager = PreferenceManager(context)

    // ==================== 更新相关状态 ====================
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateChecker by remember { mutableStateOf<UpdateChecker?>(null) }
    var updateDownloader by remember { mutableStateOf<UpdateDownloader?>(null) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var showDeveloperOptions by remember { mutableStateOf(false) }
    var downloadTarget by remember { mutableStateOf<ReleaseInfo?>(null) }
    
    var hasCheckedUpdate by remember { mutableStateOf(false) }
    var hasNewVersion by remember { mutableStateOf(false) }
    var shouldShowUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        updateChecker = UpdateChecker(context, preferenceManager)
        updateDownloader = UpdateDownloader(context)
    }

    // ==================== 检查更新函数 ====================
    fun performCheckUpdate(showNoUpdateToast: Boolean = false) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        updateMessage = "正在检查更新..."

        updateChecker?.setOnUpdateListener(object : UpdateChecker.OnUpdateListener {
            override fun onCheckStart() {}

            override fun onCheckSuccess(
                releases: List<ReleaseInfo>,
                hasNewer: Boolean,
                forceUpdate: Boolean
            ) {
                isCheckingUpdate = false
                hasCheckedUpdate = true
                
                if (hasNewer && releases.isNotEmpty()) {
                    hasNewVersion = true
                    shouldShowUpdateDialog = true
                    val latest = releases.first()
                    updateMessage = """
                        🎉 发现新版本！
                        
                        版本: ${latest.versionName}
                        大小: ${latest.getFormattedSize()}
                        发布日期: ${latest.getFormattedDate()}
                        
                        ${if (forceUpdate) "⚠️ 这是强制更新，必须安装最新版本" else "点击「下载更新」开始下载"}
                        
                        ${latest.releaseNotes?.take(200) ?: ""}${if ((latest.releaseNotes?.length ?: 0) > 200) "..." else ""}
                    """.trimIndent()
                    downloadTarget = latest
                    showUpdateDialog = true
                } else {
                    hasNewVersion = false
                    shouldShowUpdateDialog = false
                    if (showNoUpdateToast) {
                        Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCheckFailed(error: String) {
                isCheckingUpdate = false
                hasCheckedUpdate = true
                if (showNoUpdateToast) {
                    Toast.makeText(context, "检查更新失败：$error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onNoUpdate(currentVersion: String) {
                isCheckingUpdate = false
                hasCheckedUpdate = true
                hasNewVersion = false
                shouldShowUpdateDialog = false
                if (showNoUpdateToast) {
                    Toast.makeText(context, "当前已是最新版本 (v$currentVersion)", Toast.LENGTH_SHORT).show()
                }
            }
        })

        updateChecker?.checkUpdate()
    }

    // 处理 checkUpdate 参数
    LaunchedEffect(checkUpdate) {
        if (checkUpdate && !hasCheckedUpdate) {
            delay(300)
            performCheckUpdate(showNoUpdateToast = false)
        }
    }

    // ==================== 下载函数 ====================
    fun startDownload(release: ReleaseInfo) {
        showProgressDialog = true
        progressMessage = "正在下载 ${release.versionName}..."

        updateDownloader?.setOnDownloadListener(object : UpdateDownloader.OnDownloadListener {
            override fun onStart() {}

            override fun onProgress(progress: Int, downloaded: Long, total: Long) {
                progressMessage = "正在下载 ${release.versionName}... $progress% (${formatFileSize(downloaded)}/${formatFileSize(total)})"
            }

            override fun onSuccess(file: File) {
                showProgressDialog = false
                updateDownloader?.installApk(file)
            }

            override fun onFailed(error: String) {
                showProgressDialog = false
                updateMessage = "❌ 下载失败：$error"
                showUpdateDialog = true
            }
        })

        updateDownloader?.startDownload(release.downloadUrl, release.versionName)
    }

    // ==================== 打开小部件配置 ====================
    fun openWidgetConfig() {
        try {
            Logger.i("ManageScreen", "打开小部件配置")
            val intent = Intent(context, WidgetConfigActivity::class.java)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e("ManageScreen", "打开小部件配置失败", e)
            Toast.makeText(context, "打开配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 菜单项 ====================
    val menuItems = listOf(
        ManageMenuItem(
            id = "plugin_manage",
            title = "插件管理",
            icon = Icons.Default.Extension,
            description = "导入导出卸载插件"
        ) {
            try {
                context.startActivity(Intent(context, PluginManageActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(context, "插件管理功能开发中", Toast.LENGTH_SHORT).show()
            }
        },
        ManageMenuItem(
            id = "permission",
            title = "权限管理",
            icon = Icons.Default.Security,
            description = "管理应用和插件权限"
        ) {
            try {
                context.startActivity(Intent(context, PermissionManagerActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(context, "权限管理功能开发中", Toast.LENGTH_SHORT).show()
            }
        },
        ManageMenuItem(
            id = "docs",
            title = "文档中心",
            icon = Icons.Default.Info,
            description = "使用帮助开发文档"
        ) {
            try {
                context.startActivity(Intent(context, DocBrowserActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(context, "文档中心功能开发中", Toast.LENGTH_SHORT).show()
            }
        },
        ManageMenuItem(
            id = "logs",
            title = "运行日志",
            icon = Icons.Default.BugReport,
            description = "查看导出清空日志"
        ) {
            context.startActivity(Intent(context, LogViewerActivity::class.java))
        },
        ManageMenuItem(
            id = "backup",
            title = "备份恢复",
            icon = Icons.Default.Backup,
            description = "备份恢复插件配置"
        ) {
            try {
                val intent = Intent(context, BackupManagerActivity::class.java)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "备份恢复功能开发中", Toast.LENGTH_SHORT).show()
            }
        },
        ManageMenuItem(
            id = "ui_config",
            title = "UI个性化",
            icon = Icons.Default.Palette,
            description = "主题颜色圆角设置"
        ) {
            try {
                val intent = Intent(context, UIConfigActivity::class.java)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "UI个性化功能开发中", Toast.LENGTH_SHORT).show()
            }
        },
        ManageMenuItem(
            id = "widget_config",
            title = "小部件配置",
            icon = Icons.Default.Widgets,
            description = "配置快捷方式和小部件"
        ) {
            try {
                val intent = Intent(context, WidgetConfigActivity::class.java)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "小部件配置功能开发中", Toast.LENGTH_SHORT).show()
            }
        },
        ManageMenuItem(
            id = "update",
            title = "检查更新",
            icon = Icons.Default.Update,
            description = "检查GitHub最新版本"
        ) {
            performCheckUpdate(showNoUpdateToast = true)
        },
        ManageMenuItem(
            id = "github_mirror",
            title = "GitHub加速",
            icon = Icons.Default.Settings,
            description = "配置镜像站加速"
        ) {
            try {
                context.startActivity(Intent(context, GitHubMirrorActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(context, "GitHub加速功能开发中", Toast.LENGTH_SHORT).show()
            }
        },
        ManageMenuItem(
            id = "developer",
            title = "开发者选项",
            icon = Icons.Default.DeveloperMode,
            description = "签名验证调试设置"
        ) {
            showDeveloperOptions = true
        }
    )

    // ==================== UI ====================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIComponents.TitleText("管理")
            UIComponents.IconButton(
                icon = Icons.Default.Refresh,
                onClick = { /* 刷新插件列表 */ }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(menuItems) { item ->
                ManageMenuItemCard(
                    item = item,
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(300),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                )
            }
        }
    }

    // ==================== 更新对话框 ====================
    if (showUpdateDialog && shouldShowUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCheckingUpdate) {
                    showUpdateDialog = false
                    shouldShowUpdateDialog = false
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    if (isCheckingUpdate) "检查更新" else "更新信息",
                    color = Color(0xFF1A3A4A)
                )
            },
            text = {
                Text(
                    text = updateMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF333333)
                )
            },
            confirmButton = {
                if (!isCheckingUpdate && downloadTarget != null) {
                    Row {
                        UIComponents.PrimaryButton(
                            text = "下载更新",
                            onClick = {
                                downloadTarget?.let { startDownload(it) }
                                showUpdateDialog = false
                                shouldShowUpdateDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        UIComponents.TextButton(
                            text = "忽略",
                            onClick = {
                                showUpdateDialog = false
                                shouldShowUpdateDialog = false
                            }
                        )
                    }
                } else {
                    UIComponents.PrimaryButton(
                        text = "确定",
                        onClick = {
                            showUpdateDialog = false
                            shouldShowUpdateDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                if (!isCheckingUpdate && downloadTarget == null) {
                    UIComponents.TextButton(
                        text = "关闭",
                        onClick = {
                            showUpdateDialog = false
                            shouldShowUpdateDialog = false
                        }
                    )
                }
            }
        )
    }

    // ==================== 进度对话框 ====================
    if (showProgressDialog) {
        UIComponents.LoadingDialog(
            message = progressMessage,
            onCancel = {
                showProgressDialog = false
                updateDownloader?.cancelDownload()
            }
        )
    }

    // ==================== 开发者选项对话框 ====================
    if (showDeveloperOptions) {
        var ignoreSignature by remember {
            mutableStateOf(PluginManager.isIgnoreSignatureWarning())
        }

        AlertDialog(
            onDismissRequest = { showDeveloperOptions = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "开发者选项",
                    color = Color(0xFF1A3A4A)
                )
            },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = ignoreSignature,
                            onCheckedChange = { ignoreSignature = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF1A3A4A),
                                uncheckedColor = Color(0xFF9AA6B2)
                            )
                        )
                        Text(
                            text = "忽略签名验证（仅开发用）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ 开启后可以安装未签名的插件，但存在安全风险",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD32F2F)
                    )
                }
            },
            confirmButton = {
                UIComponents.PrimaryButton(
                    text = "保存",
                    onClick = {
                        PluginManager.setIgnoreSignatureWarning(ignoreSignature)
                        Toast.makeText(
                            context,
                            if (ignoreSignature) "已忽略签名验证" else "已启用签名验证",
                            Toast.LENGTH_SHORT
                        ).show()
                        showDeveloperOptions = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                UIComponents.TextButton(
                    text = "取消",
                    onClick = { showDeveloperOptions = false }
                )
            }
        )
    }
}

// ==================== 菜单项卡片 ====================
@Composable
fun ManageMenuItemCard(
    item: ManageMenuItem,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .scale(scale)
            .clickable(
                onClick = item.onClick,
                onClickLabel = item.title
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.8f),
                            Color.White.copy(alpha = 0.5f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1A3A4A).copy(alpha = 0.1f),
                                    Color(0xFF1A3A4A).copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            color = Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = Color(0xFF1A3A4A),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = Color(0xFF666666),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}