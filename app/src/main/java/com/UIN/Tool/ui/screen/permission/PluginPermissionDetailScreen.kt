// app/src/main/java/com/UIN/Tool/ui/screen/permission/PluginPermissionDetailScreen.kt
package com.UIN.Tool.ui.screen.permission

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.AppColors

class PluginPermissionDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pluginId = intent.getStringExtra("plugin_id") ?: ""
        val pluginManager = ServiceLocator.getPluginManager()
        val plugin = pluginManager.getPluginInfo(pluginId)

        setContent {
            UINToolTheme {
                PluginPermissionDetailScreen(
                    plugin = plugin,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPermissionDetailScreen(
    plugin: PluginInfo?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pluginManager = ServiceLocator.getPluginManager()

    var permissions by remember {
        mutableStateOf(emptyMap<String, Boolean>())
    }
    var isRequesting by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    fun loadPermissions() {
        if (plugin != null) {
            permissions = pluginManager.getPluginPermissionStatus(plugin.pluginId)
        }
    }

    LaunchedEffect(plugin) {
        loadPermissions()
    }

    fun requestAllPermissions() {
        if (plugin == null || isRequesting) return
        isRequesting = true
        progressMessage = Str.get(R.string.requesting_permissions)

        pluginManager.requestPluginPermissionsByGroups(
            plugin.pluginId,
            onProgress = { group, current, total ->
                progressMessage = "($current/$total) $group"
            },
            onComplete = { granted ->
                isRequesting = false
                loadPermissions()
                if (granted) {
                    progressMessage = Str.get(R.string.all_permissions_granted)
                } else {
                    progressMessage = Str.get(R.string.some_permissions_denied)
                }
            }
        )
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = if (plugin != null) Str.get(R.string.plugin_name_permissions, plugin.name) else Str.get(R.string.permission_details),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadPermissions()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    UIComponents.LastUpdatedCaption(
                        time = lastRefreshTime,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (plugin == null) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            UnifiedEmptyState(
                                title = Str.get(R.string.plugin_does_not_exist),
                                description = Str.get(R.string.no_matching_plugin_info_found)
                            )
                        }
                    }
                } else {
                    // 插件信息 - 移除 📦
                    item {
                        UnifiedCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = CardVariant.Filled
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = Str.get(R.string.plugin_plugin_name, plugin.name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Str.get(R.string.id_plugin_pluginid_version_plugin_ve, plugin.pluginId, plugin.versionName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Str.get(R.string.declared_permissions_plugin_permissi_2, plugin.permissions.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 权限列表
                    if (permissions.isEmpty()) {
                        item {
                            UnifiedCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = CardVariant.Filled
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = Str.get(R.string.this_plugin_declares_no_permissions),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // 统计信息
                        item {
                            val grantedCount = permissions.values.count { it }
                            val totalCount = permissions.size
                            UnifiedCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = CardVariant.Filled
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Str.get(R.string.permission_status),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = Str.get(R.string.grantedcount_totalcount_granted, grantedCount, totalCount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (grantedCount == totalCount)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // 进度消息 - 移除 ✅ 和 ⚠️
                        if (progressMessage.isNotEmpty()) {
                            item {
                                UnifiedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    variant = CardVariant.Filled
                                ) {
                                    Text(
                                        text = progressMessage,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        color = when {
                                            progressMessage.contains(Str.get(R.string.all_permissions_granted)) -> MaterialTheme.colorScheme.primary
                                            progressMessage.contains(Str.get(R.string.some_permissions_denied)) -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // 权限列表
                        items(permissions.entries.toList()) { (permission, granted) ->
                            PermissionDetailItem(
                                permission = permission,
                                granted = granted,
                                onRequest = {
                                    if (isRequesting) return@PermissionDetailItem
                                    val pid = plugin.pluginId
                                    // 已生效 → 撤销（插件层封禁）；未生效 → 解除封禁/发起授权
                                    if (granted) {
                                        PluginPermissionManager.setPermissionBlocked(context, pid, permission, true)
                                        AppToast.info(context, Str.get(R.string.permission_revoked_permission, permission))
                                        loadPermissions()
                                    } else if (PluginPermissionManager.isPermissionBlocked(context, pid, permission)) {
                                        PluginPermissionManager.setPermissionBlocked(context, pid, permission, false)
                                        AppToast.info(context, Str.get(R.string.permission_unblocked_permission, permission))
                                        loadPermissions()
                                    } else {
                                        pluginManager.requestPluginPermissions(pid) { _ ->
                                            loadPermissions()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 操作按钮 - 移除 ✅
            if (permissions.isNotEmpty()) {
                val allGranted = permissions.values.all { it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedButton(
                        text = if (allGranted) Str.get(R.string.all_permissions_granted) else Str.get(R.string.grant_all_2),
                        onClick = { requestAllPermissions() },
                        modifier = Modifier.weight(1f),
                        enabled = !allGranted && !isRequesting,
                        loading = isRequesting
                    )

                    UnifiedButton(
                        text = Str.get(R.string.revoke_all_permissions),
                        icon = Icons.Default.Close,
                        onClick = {
                            if (isRequesting) return@UnifiedButton
                            val pid = plugin?.pluginId ?: return@UnifiedButton
                            PluginPermissionManager.blockAllDeclaredPermissions(context, pid)
                            AppToast.info(context, Str.get(R.string.all_permissions_revoked))
                            loadPermissions()
                        },
                        modifier = Modifier.weight(0.6f),
                        enabled = !isRequesting,
                        variant = ButtonVariant.Destructive
                    )

                    UnifiedButton(
                        text = Str.get(R.string.refresh),
                        icon = Icons.Default.Refresh,
                        onClick = { loadPermissions() },
                        modifier = Modifier.weight(0.4f),
                        enabled = !isRequesting,
                        variant = ButtonVariant.Outlined
                    )
                }
            }
        }
        }
    }
}

@Composable
fun PermissionDetailItem(
    permission: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CardVariant.Filled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(AppDimens.radiusLarge),
                color = if (granted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (granted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PluginPermissionManager.getPermissionDisplayName(permission),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (granted)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = PluginPermissionManager.getPermissionDescription(permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                if (PluginPermissionManager.isSpecialPermission(permission)) {
                    Text(
                        text = Str.get(R.string.special_permissions_enable_manually_),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (granted) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(AppDimens.radiusXXLarge)
                ) {
                    Text(
                        text = Str.get(R.string.granted_2),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            } else {
                UnifiedButton(
                    text = Str.get(R.string.grant),
                    onClick = onRequest,
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}