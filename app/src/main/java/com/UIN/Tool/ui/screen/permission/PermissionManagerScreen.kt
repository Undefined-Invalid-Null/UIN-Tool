// app/src/main/java/com/UIN/Tool/ui/screen/permission/PermissionManagerScreen.kt
package com.UIN.Tool.ui.screen.permission

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
import com.UIN.Tool.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var refreshKey by remember { mutableStateOf(0) }

    val permissionItems = remember {
        listOf(
            PermissionItem("存储权限", "MANAGE_EXTERNAL_STORAGE", true),
            PermissionItem("存储权限", Manifest.permission.READ_EXTERNAL_STORAGE),
            PermissionItem("存储权限", Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PermissionItem("网络权限", Manifest.permission.INTERNET),
            PermissionItem("网络权限", Manifest.permission.ACCESS_NETWORK_STATE),
            PermissionItem("网络权限", Manifest.permission.ACCESS_WIFI_STATE),
            PermissionItem("相机权限", Manifest.permission.CAMERA),
            PermissionItem("麦克风权限", Manifest.permission.RECORD_AUDIO),
            PermissionItem("位置权限", Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionItem("位置权限", Manifest.permission.ACCESS_COARSE_LOCATION),
            PermissionItem("电话权限", Manifest.permission.CALL_PHONE),
            PermissionItem("电话权限", Manifest.permission.READ_PHONE_STATE),
            PermissionItem("短信权限", Manifest.permission.SEND_SMS),
            PermissionItem("短信权限", Manifest.permission.READ_SMS),
            PermissionItem("短信权限", Manifest.permission.RECEIVE_SMS),
            PermissionItem("联系人权限", Manifest.permission.READ_CONTACTS),
            PermissionItem("联系人权限", Manifest.permission.WRITE_CONTACTS),
            PermissionItem("日历权限", Manifest.permission.READ_CALENDAR),
            PermissionItem("日历权限", Manifest.permission.WRITE_CALENDAR),
            PermissionItem("系统权限", "SYSTEM_ALERT_WINDOW", true),
            PermissionItem("系统权限", "WRITE_SETTINGS", true),
            PermissionItem("系统权限", "POST_NOTIFICATIONS"),
            PermissionItem("系统权限", Manifest.permission.VIBRATE),
            PermissionItem("无障碍权限", "ACCESSIBILITY", true),
            PermissionItem("高级权限", "REQUEST_INSTALL_PACKAGES", true),
            PermissionItem("高级权限", "PACKAGE_USAGE_STATS", true)
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
        Toast.makeText(context, "权限状态已刷新", Toast.LENGTH_SHORT).show()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshKey++
        Toast.makeText(context, "权限状态已刷新", Toast.LENGTH_SHORT).show()
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
                title = { Text("权限管理") },
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
                }
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
            // ==================== 插件权限管理入口卡片 ====================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(Intent(context, PluginPermissionActivity::class.java))
                            } catch (e: Exception) {
                                Toast.makeText(context, "插件权限功能开发中", Toast.LENGTH_SHORT).show()
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
                        // 图标 - 使用 Surface 替代 Box + background
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
                                text = "插件权限管理",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "管理每个插件声明的权限 →",
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
                    UIComponents.TitleText("应用权限")
                    UIComponents.PrimaryButton(
                        text = "一键授权",
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
                                item.category.contains("存储") -> Icons.Default.Folder
                                item.category.contains("网络") -> Icons.Default.Wifi
                                item.category.contains("相机") -> Icons.Default.Camera
                                item.category.contains("麦克风") -> Icons.Default.Mic
                                item.category.contains("位置") -> Icons.Default.LocationOn
                                item.category.contains("电话") -> Icons.Default.Phone
                                item.category.contains("短信") -> Icons.Default.Message
                                item.category.contains("联系人") -> Icons.Default.People
                                item.category.contains("日历") -> Icons.Default.DateRange
                                item.category.contains("系统") -> Icons.Default.Settings
                                item.category.contains("无障碍") -> Icons.Default.Accessibility
                                item.category.contains("高级") -> Icons.Default.Security
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (item.isSpecial) {
                            Text(
                                text = "特殊",
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

            // 底部提示
            item {
                Spacer(modifier = Modifier.height(16.dp))
                UIComponents.CaptionText(
                    "💡 提示：部分权限（如悬浮窗、修改系统设置）需要在系统设置中手动开启"
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