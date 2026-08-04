// app/src/main/java/com/UIN/Tool/ui/screen/permission/PermissionManagerScreen.kt
package com.UIN.Tool.ui.screen.permission

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.PermissionUtils

private const val TAG = "PermissionManagerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var refreshKey by remember { mutableStateOf(0) }

    val permissionItems = remember {
        listOf(
            PermissionItem(Str.get(R.string.storage_permission), "MANAGE_EXTERNAL_STORAGE", true),
            PermissionItem(Str.get(R.string.storage_permission), Manifest.permission.READ_EXTERNAL_STORAGE),
            PermissionItem(Str.get(R.string.storage_permission), Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PermissionItem(Str.get(R.string.network_permission), Manifest.permission.INTERNET),
            PermissionItem(Str.get(R.string.network_permission), Manifest.permission.ACCESS_NETWORK_STATE),
            PermissionItem(Str.get(R.string.network_permission), Manifest.permission.ACCESS_WIFI_STATE),
            PermissionItem(Str.get(R.string.camera_permission), Manifest.permission.CAMERA),
            PermissionItem(Str.get(R.string.microphone_permission), Manifest.permission.RECORD_AUDIO),
            PermissionItem(Str.get(R.string.location_permission), Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionItem(Str.get(R.string.location_permission), Manifest.permission.ACCESS_COARSE_LOCATION),
            PermissionItem(Str.get(R.string.phone_permission), Manifest.permission.CALL_PHONE),
            PermissionItem(Str.get(R.string.phone_permission), Manifest.permission.READ_PHONE_STATE),
            PermissionItem(Str.get(R.string.sms_permission), Manifest.permission.SEND_SMS),
            PermissionItem(Str.get(R.string.sms_permission), Manifest.permission.READ_SMS),
            PermissionItem(Str.get(R.string.sms_permission), Manifest.permission.RECEIVE_SMS),
            PermissionItem(Str.get(R.string.contacts_permission), Manifest.permission.READ_CONTACTS),
            PermissionItem(Str.get(R.string.contacts_permission), Manifest.permission.WRITE_CONTACTS),
            PermissionItem(Str.get(R.string.calendar_permission), Manifest.permission.READ_CALENDAR),
            PermissionItem(Str.get(R.string.calendar_permission), Manifest.permission.WRITE_CALENDAR),
            PermissionItem(Str.get(R.string.system_permission), "SYSTEM_ALERT_WINDOW", true),
            PermissionItem(Str.get(R.string.system_permission), "WRITE_SETTINGS", true),
            PermissionItem(Str.get(R.string.system_permission), "POST_NOTIFICATIONS"),
            PermissionItem(Str.get(R.string.system_permission), Manifest.permission.VIBRATE),
            PermissionItem(Str.get(R.string.accessibility_permission), "ACCESSIBILITY", true),
            PermissionItem(Str.get(R.string.advanced_permissions), "REQUEST_INSTALL_PACKAGES", true),
            PermissionItem(Str.get(R.string.advanced_permissions), "PACKAGE_USAGE_STATS", true)
        )
    }

    fun checkPermission(permission: String): Boolean {
        return PermissionUtils.hasPermission(context, permission) ||
                PermissionUtils.hasSpecialPermission(context, permission)
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshKey++
        AppToast.info(context, Str.get(R.string.permission_status_refreshed))
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshKey++
        AppToast.info(context, Str.get(R.string.permission_status_refreshed))
    }

    fun requestPermission(permission: String) {
        if (PermissionUtils.isSpecialPermission(permission)) {
            if (activity != null) {
                PermissionUtils.requestSpecialPermission(
                    activity,
                    permission,
                    settingsLauncher
                )
            }
        } else {
            multiplePermissionLauncher.launch(arrayOf(permission))
        }
    }

    fun requestAllPermissions() {
        val permissions = permissionItems.map { it.permission }
            .filter { !checkPermission(it) }
            .filter { !PermissionUtils.isSpecialPermission(it) }
        if (permissions.isNotEmpty()) {
            multiplePermissionLauncher.launch(permissions.toTypedArray())
        }
        permissionItems.filter { PermissionUtils.isSpecialPermission(it.permission) }
            .forEach {
                if (activity != null) {
                    PermissionUtils.requestSpecialPermission(
                        activity,
                        it.permission,
                        settingsLauncher
                    )
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Str.get(R.string.permission_management)) },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.Default.ArrowBack,
                        onClick = { activity?.finish() }
                    )
                },
                actions = {
                    UIComponents.IconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { refreshKey++ }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 插件权限管理入口卡片
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(Intent(context, PluginPermissionActivity::class.java))
                            } catch (e: Exception) {
                                AppToast.warning(context, Str.get(R.string.plugin_permission_feature_under_deve))
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Str.get(R.string.plugin_permission_management),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = Str.get(R.string.manage_each_plugin_s_declared_permis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 应用权限标题
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIComponents.TitleText(Str.get(R.string.app_permissions))
                    UIComponents.PrimaryButton(
                        text = Str.get(R.string.grant_all),
                        onClick = { requestAllPermissions() },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // 权限列表
            items(permissionItems) { item ->
                val granted = checkPermission(item.permission)
                UIComponents.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!granted) {
                                requestPermission(item.permission)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when {
                                item.category.contains(Str.get(R.string.storage)) -> Icons.Default.Folder
                                item.category.contains(Str.get(R.string.network)) -> Icons.Default.Wifi
                                item.category.contains(Str.get(R.string.camera)) -> Icons.Default.Camera
                                item.category.contains(Str.get(R.string.microphone)) -> Icons.Default.Mic
                                item.category.contains(Str.get(R.string.location)) -> Icons.Default.LocationOn
                                item.category.contains(Str.get(R.string.phone)) -> Icons.Default.Phone
                                item.category.contains(Str.get(R.string.sms)) -> Icons.Default.Message
                                item.category.contains(Str.get(R.string.contacts)) -> Icons.Default.People
                                item.category.contains(Str.get(R.string.calendar)) -> Icons.Default.DateRange
                                item.category.contains(Str.get(R.string.system)) -> Icons.Default.Settings
                                item.category.contains(Str.get(R.string.accessibility)) -> Icons.Default.Accessibility
                                item.category.contains(Str.get(R.string.advanced)) -> Icons.Default.Security
                                else -> Icons.Default.Security
                            },
                            contentDescription = null,
                            tint = if (granted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = PermissionUtils.getPermissionDisplayName(item.permission),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (item.isSpecial) {
                            Text(
                                text = Str.get(R.string.special),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        Checkbox(
                            checked = granted,
                            onCheckedChange = {
                                if (!granted) {
                                    requestPermission(item.permission)
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // 底部提示 - 移除 Emoji
            item {
                Spacer(modifier = Modifier.height(16.dp))
                UIComponents.CaptionText(
                    Str.get(R.string.note_some_permissions_e_g_overlay_wi)
                )
            }
        }
    }
}

data class PermissionItem(
    val category: String,
    val permission: String,
    val isSpecial: Boolean = false
)