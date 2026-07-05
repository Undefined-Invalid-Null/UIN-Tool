// app/src/main/java/com/UIN/Tool/ui/screen/permission/PluginPermissionDetailActivity.kt
package com.UIN.Tool.ui.screen.permission

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.UIN.Tool.ui.theme.UINToolTheme
import kotlinx.coroutines.launch

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
        progressMessage = "正在请求权限..."
        
        pluginManager.requestPluginPermissionsByGroups(
            plugin.pluginId,
            onProgress = { group, current, total ->
                progressMessage = "($current/$total) $group"
            },
            onComplete = { granted ->
                isRequesting = false
                loadPermissions()
                if (granted) {
                    progressMessage = "✅ 所有权限已授予"
                } else {
                    progressMessage = "⚠️ 部分权限被拒绝"
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (plugin != null) "${plugin.name} - 权限" else "权限详情")
                },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack
                    )
                },
                actions = {
                    if (plugin != null && permissions.isNotEmpty()) {
                        UIComponents.IconButton(
                            icon = Icons.Default.Refresh,
                            onClick = { loadPermissions() }
                        )
                    }
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
            if (plugin == null) {
                UIComponents.EmptyState(
                    title = "插件不存在",
                    description = "未找到对应的插件信息"
                )
                return@Scaffold
            }
            
            // 插件信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "📦 ${plugin.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: ${plugin.pluginId} | 版本: ${plugin.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "声明权限: ${plugin.permissions.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 权限列表
            if (permissions.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
                            text = "该插件没有声明任何权限",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 统计信息
                val grantedCount = permissions.values.count { it }
                val totalCount = permissions.size
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "权限状态",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$grantedCount / $totalCount 已授予",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (grantedCount == totalCount)
                                Color(0xFF4CAF50)
                            else
                                Color(0xFFFF9800)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 进度消息
                if (progressMessage.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = progressMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            color = when {
                                progressMessage.contains("✅") -> Color(0xFF4CAF50)
                                progressMessage.contains("⚠️") -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 权限列表
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(permissions.entries.toList()) { (permission, granted) ->
                        PermissionDetailItem(
                            permission = permission,
                            granted = granted,
                            onRequest = {
                                if (!granted && !isRequesting) {
                                    pluginManager.requestPluginPermissions(
                                        plugin.pluginId
                                    ) { _ ->
                                        loadPermissions()
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 操作按钮
            if (permissions.isNotEmpty()) {
                val allGranted = permissions.values.all { it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UIComponents.PrimaryButton(
                        text = if (allGranted) "✅ 所有权限已授予" else "一键授权所有",
                        onClick = { requestAllPermissions() },
                        modifier = Modifier.weight(1f),
                        enabled = !allGranted && !isRequesting,
                        loading = isRequesting
                    )
                    
                    UIComponents.SecondaryButton(
                        text = "刷新",
                        icon = Icons.Default.Refresh,
                        onClick = { loadPermissions() },
                        modifier = Modifier.weight(0.4f),
                        enabled = !isRequesting
                    )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标 - 使用 Surface 替代 Box + background
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(18.dp),
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
            
            // 权限信息
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
                        text = "⚠️ 特殊权限：需在系统设置中手动开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }
            
            // 状态标签或操作按钮
            if (granted) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "已授予",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            } else {
                UIComponents.PrimaryButton(
                    text = "授权",
                    onClick = onRequest,
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}