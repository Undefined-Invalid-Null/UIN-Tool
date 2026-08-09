// app/src/main/java/com/UIN/Tool/ui/screen/permission/PermissionManagerScreen.kt
package com.UIN.Tool.ui.screen.permission

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.Manifest
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.PermissionUtils
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PermissionManagerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

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
            PermissionItem(Str.get(R.string.advanced_permissions), "PACKAGE_USAGE_STATS", true),
            PermissionItem(Str.get(R.string.system_permission), "SHIZUKU", true),
            PermissionItem(Str.get(R.string.system_permission), "DHIZUKU", true)
        )
    }

    fun checkPermission(permission: String): Boolean {
        return PermissionUtils.hasPermission(context, permission) ||
                PermissionUtils.hasSpecialPermission(context, permission)
    }

    // 授权状态派生自 refreshKey：授权回调/Shizuku 监听/下拉刷新都会 +1，
    // 触发重新检查所有权限状态，从而自动刷新列表勾选。
    val permissionStates = remember(refreshKey) {
        permissionItems.associate { item -> item.permission to checkPermission(item.permission) }
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

    fun requestShizukuPermission() {
        if (PermissionUtils.isShizukuBinderAlive()) {
            PermissionUtils.requestShizukuPermission()
        } else {
            val intent = activity?.packageManager
                ?.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                settingsLauncher.launch(intent)
            }
        }
    }

    fun requestDhizukuPermission() {
        PermissionUtils.requestDhizukuPermission(context) { granted ->
            Handler(Looper.getMainLooper()).post {
                refreshKey++
                AppToast.info(
                    context,
                    if (granted) Str.get(R.string.permission_granted)
                    else Str.get(R.string.permission_denied)
                )
            }
        }
    }

    fun requestPermission(permission: String) {
        when (permission) {
            "SHIZUKU" -> requestShizukuPermission()
            "DHIZUKU" -> requestDhizukuPermission()
            else -> {
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
        }
    }

    // Shizuku 权限请求结果为异步 binder 回调，需要注册结果监听器刷新状态
    DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PermissionUtils.SHIZUKU_REQUEST_CODE) {
                refreshKey++
                AppToast.info(context, Str.get(R.string.permission_status_refreshed))
            }
        }
        PermissionUtils.addShizukuPermissionResultListener(listener)
        onDispose {
            PermissionUtils.removeShizukuPermissionResultListener(listener)
        }
    }

    // 从系统设置/Shizuku/Dhizuku 返回前台时自动刷新权限状态，无需手动下拉
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                requestPermission(it.permission)
            }
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.permission_management),
                onBack = { activity?.finish() }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    refreshKey++
                    AppToast.info(context, Str.get(R.string.permission_status_refreshed))
                    delay(400)
                    lastRefreshTime = UIComponents.currentTimeString()
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            indicator = {
                UIComponents.PullRefreshIndicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            item {
                UIComponents.LastUpdatedCaption(time = lastRefreshTime)
            }
            // 插件权限管理入口卡片
            item {
                UnifiedCard(
                    onClick = {
                        try {
                            context.startActivity(Intent(context, PluginPermissionActivity::class.java))
                        } catch (e: Exception) {
                            AppToast.warning(context, Str.get(R.string.plugin_permission_feature_under_deve))
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(AppDimens.radiusMedium),
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Str.get(R.string.manage_each_plugin_s_declared_permis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    UnifiedTitleText(Str.get(R.string.app_permissions))
                    UnifiedButton(
                        text = Str.get(R.string.grant_all),
                        onClick = { requestAllPermissions() },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // 权限列表
            items(permissionItems) { item ->
                val granted = permissionStates[item.permission] ?: checkPermission(item.permission)
                UnifiedCard(
                    onClick = {
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
                                item.permission == "SHIZUKU" -> Icons.Default.Shield
                                item.permission == "DHIZUKU" -> Icons.Default.Security
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
                UnifiedCaptionText(
                    Str.get(R.string.note_some_permissions_e_g_overlay_wi)
                )
            }
            }
        }
    }
}

data class PermissionItem(
    val category: String,
    val permission: String,
    val isSpecial: Boolean = false
)