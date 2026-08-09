package com.UIN.Tool.ui.screen.permission

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.PermissionUtils
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PluginPermissionScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPermissionScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val pluginManager = ServiceLocator.getPluginManager()
    val scope = rememberCoroutineScope()

    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var selectedPluginId by remember { mutableStateOf<String?>(null) }
    var pluginPermissions by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    fun loadPluginPermissions(pluginId: String) {
        val perms = PluginPermissionManager.getPluginDeclaredPermissions(context, pluginId)
        val status = perms.associateWith { permission ->
            PermissionUtils.hasPermission(context, permission) ||
                    PermissionUtils.hasSpecialPermission(context, permission)
        }
        pluginPermissions = status
    }

    fun refreshPermissions() {
        selectedPluginId?.let { loadPluginPermissions(it) }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissions()
        AppToast.info(
            context,
            if (granted) Str.get(R.string.permission_granted) else Str.get(R.string.permission_denied)
        )
    }

    /**
     * 请求所有缺失权限
     */
    fun requestAllPermissions() {
        selectedPluginId?.let { pluginId ->
            val activity = context as? Activity
            if (activity == null) {
                AppToast.error(context, Str.get(R.string.failed_to_get_activity))
                return
            }
            
            isLoading = true
            
            val missing = PluginPermissionManager.getMissingPermissions(context, pluginId)
            if (missing.isEmpty()) {
                isLoading = false
                refreshPermissions()
                AppToast.info(context, Str.get(R.string.all_permissions_granted))
                return
            }
            
            // 分离普通权限和特殊权限
            val normal = missing.filter { !PluginPermissionManager.isSpecialPermission(it) }
            val special = missing.filter { PluginPermissionManager.isSpecialPermission(it) }
            
            // 请求普通权限
            if (normal.isNotEmpty()) {
                PluginPermissionManager.requestPermissions(
                    activity,
                    normal.toTypedArray(),
                    1001
                )
            }
            
            // 引导特殊权限
            if (special.isNotEmpty()) {
                PluginPermissionManager.openAppSettings(activity)
                AppToast.info(context, Str.get(R.string.enable_special_permissions_manually_))
            }
            
            isLoading = false
            refreshPermissions()
        }
    }

    /**
     * 切换单个权限
     */
    fun togglePermission(permission: String) {
        val activity = context as? Activity
        if (activity == null) {
            AppToast.error(context, Str.get(R.string.failed_to_get_activity))
            return
        }
        
        if (PermissionUtils.isSpecialPermission(permission)) {
            PermissionUtils.requestSpecialPermission(
                activity,
                permission,
                settingsLauncher
            )
            return
        }
        permissionLauncher.launch(permission)
    }

    LaunchedEffect(Unit) {
        pluginManager.refreshPlugins()
        plugins = pluginManager.plugins.value
        if (plugins.isNotEmpty()) {
            selectedPluginId = plugins.first().pluginId
            loadPluginPermissions(plugins.first().pluginId)
        }
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            UIComponents.ManageTopAppBar(
                titleText = Str.get(R.string.plugin_permissions),
                onBack = { activity?.finish() }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    refreshPermissions()
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
                UIComponents.LastUpdatedCaption(
                    time = lastRefreshTime,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (plugins.isEmpty()) {
                item {
                    UnifiedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            UnifiedBodyText(Str.get(R.string.no_installed_plugins))
                            UnifiedCaptionText(Str.get(R.string.import_plugins_on_the_manage_page_fi))
                        }
                    }
                }
            } else {
            // 插件选择下拉框
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UnifiedBodyText(Str.get(R.string.select_plugin), modifier = Modifier.weight(0.3f))

                    var expanded by remember { mutableStateOf(false) }
                    val selectedPlugin = plugins.find { it.pluginId == selectedPluginId }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedPlugin?.name ?: Str.get(R.string.select_plugin_2),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .weight(0.7f)
                                .menuAnchor()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(AppDimens.inputCornerRadius)),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(AppDimens.inputCornerRadius),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            plugins.forEach { plugin ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            plugin.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = AppDimens.bodyTextSize.sp
                                        )
                                    },
                                    onClick = {
                                        selectedPluginId = plugin.pluginId
                                        loadPluginPermissions(plugin.pluginId)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedButton(
                        text = Str.get(R.string.grant_all_permissions),
                        icon = Icons.Default.Check,
                        onClick = { requestAllPermissions() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && selectedPluginId != null,
                        loading = isLoading
                    )
                    UnifiedButton(
                        text = Str.get(R.string.refresh),
                        icon = Icons.Default.Refresh,
                        onClick = { refreshPermissions() },
                        modifier = Modifier.weight(0.5f),
                        variant = ButtonVariant.Outlined
                    )
                }
            }

            // 插件信息
            selectedPluginId?.let { pluginId ->
                val plugin = plugins.find { it.pluginId == pluginId }
                if (plugin != null) {
                    item {
                        UnifiedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                UnifiedBodyText(Str.get(R.string.plugin_plugin_name, plugin.name))
                                UnifiedCaptionText(Str.get(R.string.id_plugin_pluginid_version_plugin_ve, plugin.pluginId, plugin.versionName))
                                UnifiedCaptionText(Str.get(R.string.declared_permissions_plugin_permissi, plugin.permissions.size))
                            }
                        }
                    }
                }
            }

            // 权限列表
            when {
                selectedPluginId == null -> item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        UnifiedBodyText(Str.get(R.string.please_select_a_plugin))
                    }
                }
                pluginPermissions.isEmpty() -> item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            UnifiedTitleText(Str.get(R.string.this_plugin_declares_no_permissions_2))
                            UnifiedCaptionText(Str.get(R.string.plugins_declare_their_required_permi))
                        }
                    }
                }
                else -> items(pluginPermissions.entries.toList()) { (permission, granted) ->
                    UnifiedCard(
                        onClick = {
                            if (!granted) {
                                togglePermission(permission)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = PermissionUtils.getPermissionDisplayName(permission),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (granted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                UnifiedCaptionText(permission)
                                if (PermissionUtils.isSpecialPermission(permission)) {
                                    UnifiedCaptionText(
                                        Str.get(R.string.special_permission_requires_enabling_2),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Checkbox(
                                checked = granted,
                                onCheckedChange = {
                                    if (!granted) {
                                        togglePermission(permission)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                enabled = !isLoading
                            )
                        }
                    }
                }
            }
            }
        }
        }
    }
}
