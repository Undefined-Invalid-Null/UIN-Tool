// app/src/main/java/com/UIN/Tool/ui/screen/manage/ManageScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.screen.log.LogViewerActivity
import com.UIN.Tool.ui.screen.backup.BackupManagerActivity
import com.UIN.Tool.ui.screen.docs.DocBrowserActivity
import com.UIN.Tool.ui.screen.permission.PermissionManagerActivity
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ManageScreen"

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

    fun performCheckUpdate(showNoUpdateToast: Boolean = false) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        updateMessage = Str.get(R.string.checking_for_updates)

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
                    updateMessage = Str.get(
                        R.string.update_dialog_new_version,
                        latest.versionName,
                        latest.getFormattedSize(),
                        latest.getFormattedDate(),
                        if (forceUpdate) Str.get(R.string.this_is_a_mandatory_update_you_must_) else Str.get(R.string.tap_download_update_to_start_downloa),
                        (latest.releaseNotes?.take(200) ?: "") + if ((latest.releaseNotes?.length ?: 0) > 200) "..." else ""
                    )
                    downloadTarget = latest
                    showUpdateDialog = true
                } else {
                    hasNewVersion = false
                    shouldShowUpdateDialog = false
                    if (showNoUpdateToast) {
                        // ✅ 移除 Emoji
                        AppToast.info(context, Str.get(R.string.you_are_already_on_the_latest_versio))
                    }
                }
            }

            override fun onCheckFailed(error: String) {
                isCheckingUpdate = false
                hasCheckedUpdate = true
                if (showNoUpdateToast) {
                    AppToast.error(context, Str.get(R.string.update_check_failed_error, error))
                }
            }

            override fun onNoUpdate(currentVersion: String) {
                isCheckingUpdate = false
                hasCheckedUpdate = true
                hasNewVersion = false
                shouldShowUpdateDialog = false
                if (showNoUpdateToast) {
                    // ✅ 移除 Emoji
                    AppToast.info(context, Str.get(R.string.you_are_already_on_the_latest_versio_2, currentVersion))
                }
            }
        })

        updateChecker?.checkUpdate()
    }

    LaunchedEffect(checkUpdate) {
        if (checkUpdate && !hasCheckedUpdate) {
            delay(300)
            performCheckUpdate(showNoUpdateToast = false)
        }
    }

    fun startDownload(release: ReleaseInfo) {
        showProgressDialog = true
        progressMessage = Str.get(R.string.downloading_release_versionname, release.versionName)

        updateDownloader?.setOnDownloadListener(object : UpdateDownloader.OnDownloadListener {
            override fun onStart() {}

            override fun onProgress(progress: Int, downloaded: Long, total: Long) {
                progressMessage = Str.get(R.string.downloading_release_versionname_prog, release.versionName, progress, formatFileSize(downloaded), formatFileSize(total))
            }

            override fun onSuccess(file: File) {
                showProgressDialog = false
                updateDownloader?.installApk(file)
            }

            override fun onFailed(error: String) {
                showProgressDialog = false
                updateMessage = Str.get(R.string.download_failed_error, error)
                showUpdateDialog = true
            }
        })

        updateDownloader?.startDownload(release.downloadUrl, release.versionName)
    }

    fun openWidgetConfig() {
        try {
            AppLog.i(TAG, Str.get(R.string.open_widget_config))
            val intent = Intent(context, WidgetConfigActivity::class.java)
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_open_widget_config), e)
            AppToast.error(context, Str.get(R.string.failed_to_open_config_e_message, e.message))
        }
    }

    val menuItems = listOf(
        ManageMenuItem(
            id = "plugin_manage",
            title = Str.get(R.string.plugin_management),
            icon = Icons.Default.Extension,
            description = Str.get(R.string.import_export_and_uninstall_plugins)
        ) {
            try {
                context.startActivity(Intent(context, PluginManageActivity::class.java))
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.plugin_management_feature_under_deve))
            }
        },
        ManageMenuItem(
            id = "permission",
            title = Str.get(R.string.permission_management),
            icon = Icons.Default.Security,
            description = Str.get(R.string.manage_app_and_plugin_permissions)
        ) {
            try {
                context.startActivity(Intent(context, PermissionManagerActivity::class.java))
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.permission_management_feature_under_))
            }
        },
        ManageMenuItem(
            id = "docs",
            title = Str.get(R.string.docs_center),
            icon = Icons.Default.Info,
            description = Str.get(R.string.help_and_development_docs)
        ) {
            try {
                context.startActivity(Intent(context, DocBrowserActivity::class.java))
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.docs_center_feature_under_developmen))
            }
        },
        ManageMenuItem(
            id = "logs",
            title = Str.get(R.string.runtime_logs),
            icon = Icons.Default.BugReport,
            description = Str.get(R.string.view_export_and_clear_logs)
        ) {
            context.startActivity(Intent(context, LogViewerActivity::class.java))
        },
        ManageMenuItem(
            id = "backup",
            title = Str.get(R.string.backup_restore),
            icon = Icons.Default.Backup,
            description = Str.get(R.string.back_up_and_restore_plugin_config)
        ) {
            try {
                val intent = Intent(context, BackupManagerActivity::class.java)
                context.startActivity(intent)
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.backup_restore_feature_under_develop))
            }
        },
        ManageMenuItem(
            id = "ui_config",
            title = Str.get(R.string.ui_customization_2),
            icon = Icons.Default.Palette,
            description = Str.get(R.string.theme_colors_corner_radius_settings)
        ) {
            try {
                val intent = Intent(context, UIConfigActivity::class.java)
                context.startActivity(intent)
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.ui_customization_feature_under_devel))
            }
        },
        ManageMenuItem(
            id = "widget_config",
            title = Str.get(R.string.widget_configuration),
            icon = Icons.Default.Widgets,
            description = Str.get(R.string.configure_shortcuts_and_widgets)
        ) {
            try {
                val intent = Intent(context, WidgetConfigActivity::class.java)
                context.startActivity(intent)
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.widget_config_feature_under_developm))
            }
        },
        ManageMenuItem(
            id = "update",
            title = Str.get(R.string.check_for_updates),
            icon = Icons.Default.Update,
            description = Str.get(R.string.check_latest_github_version)
        ) {
            performCheckUpdate(showNoUpdateToast = true)
        },
        ManageMenuItem(
            id = "github_mirror",
            title = Str.get(R.string.github_acceleration),
            icon = Icons.Default.Settings,
            description = Str.get(R.string.configure_mirror_acceleration)
        ) {
            try {
                context.startActivity(Intent(context, GitHubMirrorActivity::class.java))
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.github_acceleration_feature_under_de))
            }
        },
        ManageMenuItem(
            id = "developer",
            title = Str.get(R.string.developer_options),
            icon = Icons.Default.DeveloperMode,
            description = Str.get(R.string.signature_verification_and_debug_set)
        ) {
            showDeveloperOptions = true
        }
    )

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
            UIComponents.TitleText(Str.get(R.string.manage))
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
                            dampingRatio = 0.5f,
                            stiffness = 500f
                        )
                    )
                )
            }
        }
    }

    // ==================== 更新对话框 ====================
    if (showUpdateDialog && shouldShowUpdateDialog) {
        UIComponents.ConfirmDialog(
            title = if (isCheckingUpdate) Str.get(R.string.check_for_updates) else Str.get(R.string.update_info),
            message = updateMessage,
            confirmText = if (!isCheckingUpdate && downloadTarget != null) Str.get(R.string.download_update) else Str.get(R.string.ok_2),
            dismissText = if (!isCheckingUpdate && downloadTarget == null) Str.get(R.string.close) else Str.get(R.string.cancel),
            onConfirm = {
                if (downloadTarget != null) {
                    startDownload(downloadTarget!!)
                }
                showUpdateDialog = false
                shouldShowUpdateDialog = false
            },
            onDismiss = {
                if (!isCheckingUpdate) {
                    showUpdateDialog = false
                    shouldShowUpdateDialog = false
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
    // ✅ 使用 AlertDialog 保持原有 UI
    if (showDeveloperOptions) {
        var ignoreSignature by remember {
            mutableStateOf(PluginManager.isIgnoreSignatureWarning())
        }

        AlertDialog(
            onDismissRequest = { showDeveloperOptions = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    Str.get(R.string.developer_options),
                    color = MaterialTheme.colorScheme.onSurface
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
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = Str.get(R.string.ignore_signature_verification_dev_on),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Str.get(R.string.when_enabled_unsigned_plugins_can_be),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                UIComponents.PrimaryButton(
                    text = Str.get(R.string.save),
                    onClick = {
                        PluginManager.setIgnoreSignatureWarning(ignoreSignature)
                        // ✅ 移除 Emoji
                        AppToast.info(
                            context,
                            if (ignoreSignature) Str.get(R.string.signature_verification_ignored) else Str.get(R.string.signature_verification_enabled)
                        )
                        showDeveloperOptions = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                UIComponents.TextButton(
                    text = Str.get(R.string.cancel),
                    onClick = { showDeveloperOptions = false }
                )
            }
        )
    }
}

@Composable
fun ManageMenuItemCard(
    item: ManageMenuItem,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
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
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}