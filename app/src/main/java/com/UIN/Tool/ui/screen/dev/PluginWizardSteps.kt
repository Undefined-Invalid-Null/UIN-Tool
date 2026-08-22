// ui/screen/dev/PluginWizardSteps.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.FileUtils
import com.UIN.Tool.utils.PermissionUtils
import com.UIN.Tool.utils.formatFileSize
import java.io.File
import com.UIN.Tool.ui.theme.AppDimens

private fun loadBitmapFromFile(path: String, maxSize: Int = 512): Bitmap? {
    return try {
        // 先读边界，再按目标尺寸降采样，避免全分辨率解码导致内存飙升/OOM
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        val longest = maxOf(opts.outWidth, opts.outHeight)
        while (longest / (sample * 2) >= maxSize) {
            sample *= 2
        }
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
        })
    } catch (e: Exception) {
        null
    }
}

private fun handleIconSelection(
    context: android.content.Context,
    uri: Uri,
    onIconSelected: (String) -> Unit
) {
    try {
        val tempFile = File(context.cacheDir, "temp_icon_${System.currentTimeMillis()}.png")
        if (FileUtils.copyUriToFile(context, uri, tempFile)) {
            onIconSelected(tempFile.absolutePath)
        }
    } catch (e: Exception) {
        // 忽略异常
    }
}

private fun handleResourceSelection(
    context: android.content.Context,
    uri: Uri,
    onResourceAdded: (String) -> Unit
) {
    try {
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "resource"
        val tempFile = File(context.cacheDir, "temp_res_${System.currentTimeMillis()}_$fileName")
        if (FileUtils.copyUriToFile(context, uri, tempFile)) {
            onResourceAdded(tempFile.absolutePath)
        }
    } catch (e: Exception) {
        // 忽略异常
    }
}

@Composable
fun PluginConfigStep(
    pluginId: String,
    onPluginIdChange: (String) -> Unit,
    pluginName: String,
    onPluginNameChange: (String) -> Unit,
    pluginAuthor: String,
    onPluginAuthorChange: (String) -> Unit,
    pluginDescription: String,
    onPluginDescriptionChange: (String) -> Unit,
    pluginVersion: String,
    onPluginVersionChange: (String) -> Unit,
    pluginVersionName: String,
    onPluginVersionNameChange: (String) -> Unit,
    mainClass: String,
    onMainClassChange: (String) -> Unit,
    entryPath: String,
    onEntryPathChange: (String) -> Unit,
    pluginNotice: String,
    onPluginNoticeChange: (String) -> Unit,
    uiType: String,
    backendType: String = "",
    backendPreCommand: String = "",
    onBackendPreCommandChange: (String) -> Unit = {},
    backendStartCommand: String = "",
    onBackendStartCommandChange: (String) -> Unit = {},
permissions: List<String> = emptyList(),
    onPermissionsChange: (List<String>) -> Unit = {},
    minHostVersion: String = "1",
    onMinHostVersionChange: (String) -> Unit = {},
    category: String = "",
    onCategoryChange: (String) -> Unit = {},
    updateUrl: String = "",
    onUpdateUrlChange: (String) -> Unit = {},
    backendTimeout: String = "30",
    onBackendTimeoutChange: (String) -> Unit = {},
    backendHealthCheck: String = "/health",
    onBackendHealthCheckChange: (String) -> Unit = {},
    openWithEnabled: Boolean = false,
    onOpenWithEnabledChange: (Boolean) -> Unit = {},
    openWithLabel: String = "",
    onOpenWithLabelChange: (String) -> Unit = {},
    openWithMimeTypes: String = "",
    onOpenWithMimeTypesChange: (String) -> Unit = {},
    openWithAcceptText: Boolean = true,
    onOpenWithAcceptTextChange: (Boolean) -> Unit = {},
    openWithAcceptUrl: Boolean = true,
    onOpenWithAcceptUrlChange: (Boolean) -> Unit = {},
    openWithAcceptFile: Boolean = true,
    onOpenWithAcceptFileChange: (Boolean) -> Unit = {}
) {
    var showPermissionDialog by remember { mutableStateOf(false) }

    Column {
        ConfigFieldRow {
            UnifiedTextField(
                value = pluginId,
                onValueChange = onPluginIdChange,
                label = Str.get(R.string.plugin_id),
                placeholder = "com.example.myplugin",
                modifier = Modifier.fillMaxWidth(),
                error = if (pluginId.isNotEmpty() && !pluginId.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))) {
                    Str.get(R.string.must_be_a_reversed_domain_name_e_g_c)
                } else null
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        ConfigFieldRow {
            UnifiedTextField(
                value = pluginName,
                onValueChange = onPluginNameChange,
                label = Str.get(R.string.plugin_name),
                placeholder = Str.get(R.string.my_plugins),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        ConfigFieldRow {
            UnifiedTextField(
                value = pluginAuthor,
                onValueChange = onPluginAuthorChange,
                label = Str.get(R.string.author),
                placeholder = Str.get(R.string.developer_2),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        ConfigFieldRow {
            UnifiedTextField(
                value = pluginDescription,
                onValueChange = onPluginDescriptionChange,
                label = Str.get(R.string.description),
                placeholder = Str.get(R.string.this_is_an_example_plugin),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfigFieldRow(
                modifier = Modifier.weight(1f)
            ) {
                UnifiedTextField(
                    value = pluginVersion,
                    onValueChange = onPluginVersionChange,
                    label = Str.get(R.string.version_code),
                    placeholder = "1",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ConfigFieldRow(
                modifier = Modifier.weight(1f)
            ) {
                UnifiedTextField(
                    value = pluginVersionName,
                    onValueChange = onPluginVersionNameChange,
                    label = Str.get(R.string.version_name),
                    placeholder = "1.0.0",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiType == "native") {
            ConfigFieldRow {
                UnifiedTextField(
                    value = mainClass,
                    onValueChange = onMainClassChange,
                    label = Str.get(R.string.main_class),
                    placeholder = "com.example.MainPlugin",
                    modifier = Modifier.fillMaxWidth(),
error = if (mainClass.isNotEmpty() && !mainClass.contains(".")) {
                    Str.get(R.string.must_include_the_package_e_g_com_exa)
                } else null
                )
            }
        } else if (uiType == "web") {
            ConfigFieldRow {
                UnifiedTextField(
                    value = entryPath,
                    onValueChange = onEntryPathChange,
                    label = Str.get(R.string.entry_file),
                    placeholder = "web/index.html",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==================== 最低宿主版本 ====================
        ConfigFieldRow {
            UnifiedTextField(
                value = minHostVersion,
                onValueChange = onMinHostVersionChange,
                label = Str.get(R.string.min_host_version),
                placeholder = "1",
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        ConfigFieldRow {
            UnifiedTextField(
                value = category,
                onValueChange = onCategoryChange,
                label = Str.get(R.string.category),
                placeholder = Str.get(R.string.uncategorized),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        ConfigFieldRow {
            UnifiedTextField(
                value = updateUrl,
                onValueChange = onUpdateUrlChange,
                label = Str.get(R.string.update_url),
                placeholder = "https://example.com/plugin.json",
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ==================== 权限选择（弹窗） ====================
        ConfigFieldRow {
            PermissionPickerField(
                selected = permissions,
                onOpenDialog = { showPermissionDialog = true }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ✅ 插件说明
        ConfigFieldRow {
            UnifiedTextField(
                value = pluginNotice,
                onValueChange = onPluginNoticeChange,
                label = Str.get(R.string.plugin_notice_optional),
                placeholder = Str.get(R.string.shown_on_first_launch_to_explain_the),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
        }
        UnifiedCaptionText(
            Str.get(R.string.this_notice_appears_when_the_plugin_),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        if (uiType == "web" && backendType.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            // ✅ 启动命令（必填）：宿主用 sh -lc 执行，运行环境由用户在开发页全局设定
            ConfigFieldRow {
                UnifiedTextField(
                    value = backendStartCommand,
                    onValueChange = onBackendStartCommandChange,
                    label = Str.get(R.string.start_command_required),
                    placeholder = "sh scripts/start.sh",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
            UnifiedCaptionText(
                Str.get(R.string.start_command_runs_in_sh_lc_to_s),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (uiType == "cui") {
            Spacer(modifier = Modifier.height(16.dp))

            // ✅ CUI 启动命令
            ConfigFieldRow {
                UnifiedTextField(
                    value = backendPreCommand,
                    onValueChange = onBackendPreCommandChange,
                    label = Str.get(R.string.start_command_run_in_terminal_when_t),
                    placeholder = "python3 scripts/script.py",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
            UnifiedCaptionText(
                Str.get(R.string.the_plugin_opens_a_fullscreen_termin),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (uiType == "web" && backendType.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfigFieldRow(
                    modifier = Modifier.weight(1f)
                ) {
                    UnifiedTextField(
                        value = backendTimeout,
                        onValueChange = onBackendTimeoutChange,
                        label = Str.get(R.string.backend_timeout),
                        placeholder = "30",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ConfigFieldRow(
                    modifier = Modifier.weight(1f)
                ) {
                    UnifiedTextField(
                        value = backendHealthCheck,
                        onValueChange = onBackendHealthCheckChange,
                        label = Str.get(R.string.backend_health_check),
                        placeholder = "/health",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==================== 外部内容接收（openWith） ====================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                UnifiedBodyText(Str.get(R.string.receive_external_content))
                UnifiedCaptionText(
                    Str.get(R.string.receive_external_content_desc),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            UnifiedSwitch(
                checked = openWithEnabled,
                onCheckedChange = onOpenWithEnabledChange
            )
        }

        if (openWithEnabled) {
            Spacer(modifier = Modifier.height(8.dp))

            ConfigFieldRow {
                UnifiedTextField(
                    value = openWithLabel,
                    onValueChange = onOpenWithLabelChange,
                    label = Str.get(R.string.receiver_label_optional),
                    placeholder = Str.get(R.string.receiver_label_placeholder),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            ConfigFieldRow {
                UnifiedTextField(
                    value = openWithMimeTypes,
                    onValueChange = onOpenWithMimeTypesChange,
                    label = Str.get(R.string.mime_types),
                    placeholder = "text/*,application/pdf",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Str.get(R.string.accept),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnifiedSwitch(
                        checked = openWithAcceptText,
                        onCheckedChange = onOpenWithAcceptTextChange
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        Str.get(R.string.open_with_share_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnifiedSwitch(
                        checked = openWithAcceptUrl,
                        onCheckedChange = onOpenWithAcceptUrlChange
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        Str.get(R.string.open_with_share_url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnifiedSwitch(
                        checked = openWithAcceptFile,
                        onCheckedChange = onOpenWithAcceptFileChange
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        Str.get(R.string.open_with_share_files),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ==================== 权限选择弹窗 ====================
    if (showPermissionDialog) {
        PermissionSelectionDialog(
            selected = permissions,
            onConfirm = { updated ->
                onPermissionsChange(updated)
                showPermissionDialog = false
            },
            onDismiss = { showPermissionDialog = false }
        )
    }
}

/** 权限字段：点击后打开权限选择弹窗，仅显示摘要，不占空间 */
@Composable
private fun PermissionPickerField(
    selected: List<String>,
    onOpenDialog: () -> Unit
) {
    val summary = if (selected.isEmpty()) {
        Str.get(R.string.no_permission_selected)
    } else {
        selected.take(3).joinToString(" / ") { PermissionUtils.getPermissionDisplayName(it) } +
            if (selected.size > 3) Str.get(R.string.permissions_more_count, selected.size - 3) else ""
    }
    OutlinedButton(
        onClick = onOpenDialog,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.inputCornerRadius)
    ) {
        Text(
            text = summary,
            fontSize = AppDimens.captionTextSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 权限多选弹窗：列表 + 复选框，确定后生效 */
@Composable
private fun PermissionSelectionDialog(
    selected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(selected.toSet()) }

    UnifiedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                Str.get(R.string.select_permissions),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(KNOWN_PERMISSIONS) { permission ->
                    val displayName = PermissionUtils.getPermissionDisplayName(permission)
                    val description = PluginPermissionManager.getPermissionDescription(permission)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = if (permission in draft) draft - permission else draft + permission
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = permission in draft,
                            onCheckedChange = { checked ->
                                draft = if (checked) draft + permission else draft - permission
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
UnifiedButton(
                    variant = ButtonVariant.Text,
                    text = Str.get(R.string.ok_2),
                    onClick = { onConfirm(draft.toList()) }
                )
        },
        dismissButton = {
            UnifiedButton(
                variant = ButtonVariant.Text,
                text = Str.get(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

/** 每个配置字段：仅输入区（字段级信息图标已移除，仅保留标题行的总览按钮） */
@Composable
private fun ConfigFieldRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}

/** 已知插件权限清单（用于配置页多选） */
private val KNOWN_PERMISSIONS = listOf(
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "MANAGE_EXTERNAL_STORAGE",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.CALL_PHONE",
    "android.permission.READ_PHONE_STATE",
    "android.permission.SEND_SMS",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_CALENDAR",
    "android.permission.WRITE_CALENDAR",
    "SYSTEM_ALERT_WINDOW",
    "WRITE_SETTINGS",
    "POST_NOTIFICATIONS",
    "android.permission.VIBRATE",
    "android.permission.WAKE_LOCK",
    "FLASHLIGHT",
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN",
    "android.permission.NFC",
    "ACCESSIBILITY",
    "REQUEST_INSTALL_PACKAGES",
    "PACKAGE_USAGE_STATS",
    "android.permission.KILL_BACKGROUND_PROCESSES",
    "android.permission.READ_LOGS",
    "READ_CLIPBOARD",
    "WRITE_CLIPBOARD",
    "ROOT",
    "SHIZUKU",
    "DHIZUKU"
)

@Composable
fun PluginIconStep(
    iconPath: String,
    onIconSelected: (String) -> Unit
) {
    val context = LocalContext.current

    val iconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleIconSelection(context, uri, onIconSelected)
        }
    }

    // 用 remember 缓存解码结果，避免重组时反复全量解码
    val bitmap = remember(iconPath) {
        if (iconPath.isNotEmpty() && File(iconPath).exists()) {
            loadBitmapFromFile(iconPath)
        } else {
            null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(AppDimens.radiusLarge)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = Str.get(R.string.icon_preview),
                    modifier = Modifier.size(80.dp)
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = Str.get(R.string.icon_preview),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnifiedButton(
                text = if (iconPath.isNotEmpty()) Str.get(R.string.change_icon) else Str.get(R.string.select_icon),
                icon = Icons.Default.FileUpload,
                onClick = { iconPickerLauncher.launch("image/*") }
            )

            if (iconPath.isNotEmpty()) {
                UnifiedButton(
                    variant = ButtonVariant.Outlined,
                    text = Str.get(R.string.remove_icon),
                    onClick = { onIconSelected("") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedCaptionText(
            Str.get(R.string.recommend_128x128_png)
        )
    }
}

@Composable
fun NativeCodeStep(
    onOpenEditor: () -> Unit,
    fileCount: Int
) {
    Column {
        UnifiedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            UnifiedBodyText(
                Str.get(R.string.native_plugin_dev_tips, if (fileCount > 0) fileCount else 0),
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedButton(
            text = Str.get(R.string.open_code_editor_files, fileCount),
            icon = Icons.Default.Edit,
            onClick = onOpenEditor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WebCodeStep(
    fileCount: Int,
    onOpenEditor: () -> Unit,
    onImportWebProject: () -> Unit
) {
    Column {
        UnifiedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            UnifiedBodyText(
                Str.get(R.string.web_plugin_dev_tips, if (fileCount > 0) fileCount else 0),
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedButton(
            text = Str.get(R.string.open_code_editor_files, fileCount),
            icon = Icons.Default.Edit,
            onClick = onOpenEditor,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        UnifiedButton(
            variant = ButtonVariant.Outlined,
            text = Str.get(R.string.import_existing_web_project_zip),
            icon = Icons.Default.FileUpload,
            onClick = onImportWebProject,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CuiCodeStep(
    fileCount: Int,
    onOpenEditor: () -> Unit
) {
    Column {
        UnifiedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            UnifiedBodyText(
                Str.get(R.string.cui_plugin_dev_tips, if (fileCount > 0) fileCount else 0),
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedButton(
            text = Str.get(R.string.open_code_editor_files, fileCount),
            icon = Icons.Default.Edit,
            onClick = onOpenEditor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ResourcesStep(
    resourcePaths: List<String>,
    onResourceAdded: (String) -> Unit,
    onResourceRemoved: (Int) -> Unit
) {
    val context = LocalContext.current

    val resourcePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleResourceSelection(context, uri, onResourceAdded)
        }
    }

    Column {
        UnifiedBodyText(Str.get(R.string.add_resource_files_optional))
        Spacer(modifier = Modifier.height(8.dp))
        UnifiedCaptionText(Str.get(R.string.add_images_audio_resources))
        Spacer(modifier = Modifier.height(16.dp))

        if (resourcePaths.isEmpty()) {
            UnifiedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedCaptionText(Str.get(R.string.no_resource_files))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(resourcePaths.indices.toList()) { index ->
                    val path = resourcePaths[index]
                    UnifiedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                UnifiedBodyText(File(path).name)
                            }
                            UnifiedIconButton(
                                icon = Icons.Default.Close,
                                onClick = { onResourceRemoved(index) },
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedButton(
            text = Str.get(R.string.add_resource_files),
            icon = Icons.Default.Add,
            onClick = { resourcePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PackageStep(
    isCompiling: Boolean,
    compileMessage: String,
    compileProgress: Int,
    tpkFile: File?
) {
    Column {
        UnifiedTitleText(Str.get(R.string.generate_project_files))
        Spacer(modifier = Modifier.height(8.dp))

        UnifiedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompiling) 280.dp else 240.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (isCompiling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UnifiedLinearProgressIndicator(progress = compileProgress / 100f)
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedBodyText(compileMessage)
                        UnifiedCaptionText("$compileProgress%")
                    } else if (tpkFile != null && tpkFile.exists()) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UnifiedTitleText(Str.get(R.string.packaged_successfully))
                        Spacer(modifier = Modifier.height(8.dp))
                        UnifiedCaptionText(tpkFile.absolutePath)
                        UnifiedCaptionText(Str.get(R.string.size_formatfilesize_tpkfile_length, formatFileSize(tpkFile.length())))
                    } else {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UnifiedBodyText(Str.get(R.string.tap_the_finish_button_to_generate_th))
                        if (compileMessage.isNotEmpty() && !compileMessage.contains(Str.get(R.string.success))) {
                            Spacer(modifier = Modifier.height(8.dp))
                            UnifiedCaptionText(
                                compileMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BinaryFileSelectionStep(
    filePath: String,
    onFileSelected: (String) -> Unit,
    onFilePicker: () -> Unit
) {
    Column {
        UnifiedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            UnifiedBodyText(
                Str.get(R.string.binary_backend_instructions),
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filePath.isNotEmpty()) {
            UnifiedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        UnifiedBodyText(Str.get(R.string.file_selected))
                        UnifiedCaptionText(File(filePath).name)
                        UnifiedCaptionText(
                            Str.get(R.string.size_formatfilesize_file_filepath_le, formatFileSize(File(filePath).length())),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    UnifiedIconButton(
                        icon = Icons.Default.Close,
                        onClick = { onFileSelected("") },
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        UnifiedButton(
            text = if (filePath.isNotEmpty()) Str.get(R.string.change_binary_file) else Str.get(R.string.select_binary_file),
            icon = Icons.Default.FileUpload,
            onClick = onFilePicker,
            modifier = Modifier.fillMaxWidth()
        )
    }
}