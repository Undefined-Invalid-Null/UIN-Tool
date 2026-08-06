// app/src/main/java/com/UIN/Tool/ui/screen/manage/ManageScreen.kt
package com.UIN.Tool.ui.screen.manage

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.core.update.UpdateChecker
import com.UIN.Tool.core.update.UpdateDownloader
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.UpdateDialog
import com.UIN.Tool.ui.screen.dev.DevToolsActivity
import com.UIN.Tool.ui.screen.backup.BackupManagerActivity
import com.UIN.Tool.ui.screen.docs.DocBrowserActivity
import com.UIN.Tool.ui.screen.permission.PermissionManagerActivity
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens

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
    val preferenceManager = PreferenceManager(context)

    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateChecker by remember { mutableStateOf<UpdateChecker?>(null) }
    var updateDownloader by remember { mutableStateOf<UpdateDownloader?>(null) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var downloadTarget by remember { mutableStateOf<ReleaseInfo?>(null) }

    var hasCheckedUpdate by remember { mutableStateOf(false) }
    var hasNewVersion by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        updateChecker = UpdateChecker(context, preferenceManager)
        updateDownloader = UpdateDownloader(context)
    }

    fun performCheckUpdate(showNoUpdateToast: Boolean = false) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
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
                    val latest = releases.first()
                    downloadTarget = latest
                    showUpdateDialog = true
                } else {
                    hasNewVersion = false
                    if (showNoUpdateToast) {
                        // ✅ 移除 Emoji
                        AppToast.info(context, Str.get(R.string.you_are_already_on_the_latest_versio))
                    }
                }
            }

            override fun onCheckFailed(error: String) {
                isCheckingUpdate = false
                hasCheckedUpdate = true
                hasNewVersion = false
                downloadTarget = null
                showUpdateDialog = false
                if (showNoUpdateToast) {
                    AppToast.error(context, Str.get(R.string.update_check_failed_error, error))
                }
            }

            override fun onNoUpdate(currentVersion: String) {
                isCheckingUpdate = false
                hasCheckedUpdate = true
                hasNewVersion = false
                downloadTarget = null
                showUpdateDialog = false
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
                AppToast.error(context, Str.get(R.string.download_failed_error, error))
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
            id = "dev_tools",
            title = Str.get(R.string.dev_tools),
            icon = Icons.Default.DeveloperMode,
            description = Str.get(R.string.dev_tools_desc)
        ) {
            try {
                context.startActivity(Intent(context, DevToolsActivity::class.java))
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.dev_tools_feature_under_developmen))
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
            id = "backend_settings",
            title = Str.get(R.string.backend_runtime_settings),
            icon = Icons.Default.Storage,
            description = Str.get(R.string.backend_runtime_settings_desc)
        ) {
            try {
                context.startActivity(Intent(context, BackendSettingsActivity::class.java))
            } catch (e: Exception) {
                AppToast.warning(context, Str.get(R.string.backend_runtime_settings_open_failed))
            }
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

    // ==================== 更新对话框（与开屏共用的 Markdown 弹窗） ====================
    val updateTarget = downloadTarget
    if (showUpdateDialog && updateTarget != null) {
        UpdateDialog(
            releaseInfo = updateTarget,
            forceUpdate = updateTarget.forceUpdate,
            onDismiss = {
                if (!updateTarget.forceUpdate) {
                    showUpdateDialog = false
                    downloadTarget = null
                }
            },
            onDownload = {
                showUpdateDialog = false
                startDownload(updateTarget)
            },
            onManualDownload = {
                showUpdateDialog = false
                downloadTarget = null
                try {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/${updateTarget.tagName}")
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    AppToast.error(context, Str.get(R.string.failed_to_open_browser))
                }
            },
            onIgnore = {
                showUpdateDialog = false
                downloadTarget = null
                preferenceManager.setIgnoredVersion(updateTarget.versionName)
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
}

@Composable
fun ManageMenuItemCard(
    item: ManageMenuItem,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        ),
        label = "card_scale"
    )
    val cardShape = RoundedCornerShape(AppDimens.cardCornerRadius)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .scale(scale)
            .clip(cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = item.onClick,
                onClickLabel = item.title
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (AppColors.glassEnabled())
                AppColors.glassBackground()
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
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
                            shape = RoundedCornerShape(AppDimens.radiusMedium)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(AppDimens.radiusMedium)
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
                        fontSize = AppDimens.bodyTextSize.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = AppDimens.captionTextSize.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}