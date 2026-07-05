// app/src/main/java/com/UIN/Tool/ui/screen/permission/PluginPermissionScreen.kt
package com.UIN.Tool.ui.screen.permission

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
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.plugin.PluginPermissionManager
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPermissionScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val pluginManager = ServiceLocator.getPluginManager()

    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var selectedPluginId by remember { mutableStateOf<String?>(null) }
    var pluginPermissions by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

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
        Toast.makeText(
            context,
            if (granted) "权限已授予" else "权限被拒绝",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun requestAllPermissions() {
        selectedPluginId?.let { pluginId ->
            isLoading = true
            PluginPermissionManager.requestPermissions(context, pluginId) { allGranted ->
                isLoading = false
                refreshPermissions()
                Toast.makeText(
                    context,
                    if (allGranted) "所有权限已授予" else "部分权限被拒绝",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun togglePermission(permission: String) {
        if (PermissionUtils.isSpecialPermission(permission)) {
            if (activity != null) {
                PermissionUtils.requestSpecialPermission(
                    activity,
                    permission,
                    settingsLauncher
                )
            }
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
        topBar = {
            TopAppBar(
                title = { Text("插件权限") },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.Default.ArrowBack,
                        onClick = { activity?.finish() }
                    )
                },
                actions = {
                    UIComponents.IconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { refreshPermissions() }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (plugins.isEmpty()) {
                UIComponents.Card(
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
                        UIComponents.BodyText("暂无已安装插件")
                        UIComponents.CaptionText("请先在管理页面导入插件")
                    }
                }
                return@Scaffold
            }

            // ==================== 插件选择下拉框（白色背景） ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UIComponents.BodyText("选择插件:", modifier = Modifier.weight(0.3f))

                var expanded by remember { mutableStateOf(false) }
                val selectedPlugin = plugins.find { it.pluginId == selectedPluginId }

                // ✅ 白色背景下拉框
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPlugin?.name ?: "选择插件",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .weight(0.7f)
                            .menuAnchor()
                            .background(Color.White, RoundedCornerShape(8.dp)),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1A3A4A),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            cursorColor = Color(0xFF1A3A4A)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF1A1A1A)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        plugins.forEach { plugin ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        plugin.name,
                                        color = Color(0xFF1A1A1A),
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    selectedPluginId = plugin.pluginId
                                    loadPluginPermissions(plugin.pluginId)
                                    expanded = false
                                }
                                // ✅ 移除 MenuItemDefaults.colors
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 一键授权
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UIComponents.PrimaryButton(
                    text = "一键授权所有权限",
                    icon = Icons.Default.Check,
                    onClick = { requestAllPermissions() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && selectedPluginId != null,
                    loading = isLoading
                )
                UIComponents.SecondaryButton(
                    text = "刷新",
                    icon = Icons.Default.Refresh,
                    onClick = { refreshPermissions() },
                    modifier = Modifier.weight(0.5f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 插件信息
            selectedPluginId?.let { pluginId ->
                val plugin = plugins.find { it.pluginId == pluginId }
                if (plugin != null) {
                    UIComponents.Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            UIComponents.BodyText("📦 ${plugin.name}")
                            UIComponents.CaptionText("ID: ${plugin.pluginId} | 版本: ${plugin.versionName}")
                            UIComponents.CaptionText("声明权限: ${plugin.permissions.size} 个")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 权限列表
            when {
                selectedPluginId == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        UIComponents.BodyText("请选择插件")
                    }
                }
                pluginPermissions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                            UIComponents.TitleText("该插件没有声明权限")
                            UIComponents.CaptionText("插件在 plugin.json 中声明所需权限")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pluginPermissions.entries.toList()) { (permission, granted) ->
                            UIComponents.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
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
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        UIComponents.CaptionText(permission)
                                        if (PermissionUtils.isSpecialPermission(permission)) {
                                            UIComponents.CaptionText(
                                                "⚠️ 特殊权限，需在系统设置中手动开启",
                                                color = MaterialTheme.colorScheme.tertiary
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