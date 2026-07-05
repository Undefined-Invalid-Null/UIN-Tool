// app/src/main/java/com/UIN/Tool/ui/screen/manage/WidgetConfigScreen.kt
package com.UIN.Tool.ui.screen.manage

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.widget.Widget1x1Provider
import com.UIN.Tool.widget.WidgetProvider

private const val TAG = "WidgetConfigScreen"

// ✅ 常量移到顶层
private const val NO_PLUGIN = ""

@OptIn(ExperimentalMaterial3Api::class)
class WidgetConfigActivity : ComponentActivity() {
    
    private lateinit var appWidgetManager: AppWidgetManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appWidgetManager = AppWidgetManager.getInstance(this)
        
        setContent {
            UINToolTheme {
                WidgetConfigScreen(
                    onBack = { finish() },
                    onAddWidget = { pinWidget() },
                    onAddShortcut = { pinShortcut() },
                    onRefresh = { refreshAllWidgets() }
                )
            }
        }
    }
    
    // ==================== 添加3x3小部件 ====================
    private fun pinWidget() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Toast.makeText(
                    this,
                    "Android 8.0+ 才支持此功能",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val componentName = ComponentName(this, WidgetProvider::class.java)
            
            if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
                Toast.makeText(
                    this,
                    "当前启动器不支持固定小部件，请长按桌面手动添加",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            Logger.i(TAG, "开始添加3x3小部件")
            appWidgetManager.requestPinAppWidget(componentName, null, null)
            
            Toast.makeText(
                this,
                "请在桌面放置小部件",
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: Exception) {
            Logger.e(TAG, "添加小部件失败: ${e.message}", e)
            Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 添加快捷方式 ====================
    private fun pinShortcut() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Toast.makeText(
                    this,
                    "Android 8.0+ 才支持此功能",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val componentName = ComponentName(this, Widget1x1Provider::class.java)
            
            if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
                Toast.makeText(
                    this,
                    "当前启动器不支持固定快捷方式，请长按桌面手动添加",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            Logger.i(TAG, "开始添加快捷方式")
            appWidgetManager.requestPinAppWidget(componentName, null, null)
            
            Toast.makeText(
                this,
                "请在桌面放置快捷方式",
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: Exception) {
            Logger.e(TAG, "添加快捷方式失败: ${e.message}", e)
            Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 刷新所有小部件 ====================
    private fun refreshAllWidgets() {
        try {
            WidgetProvider.forceRefreshAllWidgets(this)
            Widget1x1Provider.refresh1x1Widgets(this)
            Toast.makeText(this, "所有小部件已刷新", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "刷新失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// ==================== Compose UI ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    onBack: () -> Unit,
    onAddWidget: () -> Unit,
    onAddShortcut: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pluginManager = ServiceLocator.getPluginManager()
    
    // ==================== 状态 ====================
    var allPlugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var selectedWidgetPlugin1 by remember { mutableStateOf("") }
    var selectedWidgetPlugin2 by remember { mutableStateOf("") }
    var selectedWidgetPlugin3 by remember { mutableStateOf("") }
    var selectedShortcutPlugin by remember { mutableStateOf("") }
    
    var expandedWidget1 by remember { mutableStateOf(false) }
    var expandedWidget2 by remember { mutableStateOf(false) }
    var expandedWidget3 by remember { mutableStateOf(false) }
    var expandedShortcut by remember { mutableStateOf(false) }
    
    // 加载数据
    LaunchedEffect(Unit) {
        pluginManager.refreshPlugins()
        allPlugins = pluginManager.plugins.value
        
        // 加载全局配置
        val prefs = context.getSharedPreferences("widget_global_config", Context.MODE_PRIVATE)
        selectedWidgetPlugin1 = prefs.getString("global_plugin_1", "") ?: ""
        selectedWidgetPlugin2 = prefs.getString("global_plugin_2", "") ?: ""
        selectedWidgetPlugin3 = prefs.getString("global_plugin_3", "") ?: ""
        selectedShortcutPlugin = prefs.getString("shortcut_plugin", "") ?: ""
    }
    
    val pluginNames = listOf("-- 无 --") + allPlugins.map { it.name }
    
    fun getPluginIdByName(name: String): String {
        return if (name == "-- 无 --" || name.isEmpty()) {
            ""
        } else {
            allPlugins.find { it.name == name }?.pluginId ?: ""
        }
    }
    
    fun getPluginNameById(id: String): String {
        return if (id.isEmpty()) {
            "-- 无 --"
        } else {
            allPlugins.find { it.pluginId == id }?.name ?: "-- 无 --"
        }
    }
    
    // ==================== 保存配置 ====================
    fun saveConfig() {
        Logger.i(TAG, "保存配置")
        Logger.d(TAG, "Widget1: $selectedWidgetPlugin1, Widget2: $selectedWidgetPlugin2, Widget3: $selectedWidgetPlugin3, Shortcut: $selectedShortcutPlugin")
        
        val prefs = context.getSharedPreferences("widget_global_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("global_plugin_1", selectedWidgetPlugin1)
            putString("global_plugin_2", selectedWidgetPlugin2)
            putString("global_plugin_3", selectedWidgetPlugin3)
            putString("shortcut_plugin", selectedShortcutPlugin)
        }.apply()
        
        // 刷新所有小部件
        WidgetProvider.forceRefreshAllWidgets(context)
        Widget1x1Provider.refresh1x1Widgets(context)
        
        Toast.makeText(context, "配置已保存并刷新", Toast.LENGTH_SHORT).show()
    }

    // ==================== UI ====================
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "小部件配置",
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A3A4A)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // ============================================================
            // 快捷方式配置
            // ============================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // 标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Shortcut,
                                contentDescription = null,
                                tint = Color(0xFF1A3A4A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "快捷方式配置",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A3A4A)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // 添加快捷方式按钮
                            Button(
                                onClick = onAddShortcut,
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1A3A4A),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "添加",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("添加", fontSize = 12.sp)
                            }
                        }
                        
                        Divider(color = Color(0xFFE0E0E0))
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 选择插件
                        Text(
                            text = "选择快捷方式绑定的插件",
                            fontSize = 14.sp,
                            color = Color(0xFF555555),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = expandedShortcut,
                            onExpandedChange = { expandedShortcut = it }
                        ) {
                            OutlinedTextField(
                                value = getPluginNameById(selectedShortcutPlugin),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .background(Color.White, RoundedCornerShape(8.dp)),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedShortcut)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1A3A4A),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedShortcut,
                                onDismissRequest = { expandedShortcut = false },
                                containerColor = Color.White
                            ) {
                                pluginNames.forEach { name ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                name,
                                                color = Color(0xFF1A1A1A)
                                            ) 
                                        },
                                        onClick = {
                                            selectedShortcutPlugin = getPluginIdByName(name)
                                            expandedShortcut = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "💡 选择后，1x1快捷方式将直接打开该插件",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
            
            // ============================================================
            // 3x3 小部件配置
            // ============================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // 标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Widgets,
                                contentDescription = null,
                                tint = Color(0xFF1A3A4A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3x3 小部件配置",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A3A4A)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // 添加小部件按钮
                            Button(
                                onClick = onAddWidget,
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1A3A4A),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "添加",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("添加", fontSize = 12.sp)
                            }
                        }
                        
                        Divider(color = Color(0xFFE0E0E0))
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "选择3x3小部件中前3个位置显示的插件",
                            fontSize = 14.sp,
                            color = Color(0xFF555555),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // 位置1
                        WidgetSlotSelector(
                            label = "位置 1",
                            current = selectedWidgetPlugin1,
                            pluginNames = pluginNames,
                            expandedState = expandedWidget1,
                            onExpandedChange = { expandedWidget1 = it },
                            onSelected = { 
                                selectedWidgetPlugin1 = getPluginIdByName(it)
                                expandedWidget1 = false
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 位置2
                        WidgetSlotSelector(
                            label = "位置 2",
                            current = selectedWidgetPlugin2,
                            pluginNames = pluginNames,
                            expandedState = expandedWidget2,
                            onExpandedChange = { expandedWidget2 = it },
                            onSelected = { 
                                selectedWidgetPlugin2 = getPluginIdByName(it)
                                expandedWidget2 = false
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 位置3
                        WidgetSlotSelector(
                            label = "位置 3",
                            current = selectedWidgetPlugin3,
                            pluginNames = pluginNames,
                            expandedState = expandedWidget3,
                            onExpandedChange = { expandedWidget3 = it },
                            onSelected = { 
                                selectedWidgetPlugin3 = getPluginIdByName(it)
                                expandedWidget3 = false
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "💡 未配置的位置将自动显示其他插件",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
            
            // ============================================================
            // 保存按钮
            // ============================================================
            item {
                Button(
                    onClick = { saveConfig() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A3A4A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存配置", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            // 底部间距
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ==================== 小部件插槽选择器 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSlotSelector(
    label: String,
    current: String,
    pluginNames: List<String>,
    expandedState: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit
) {
    val pluginManager = ServiceLocator.getPluginManager()
    
    fun getPluginNameById(id: String): String {
        return if (id.isEmpty()) {
            "-- 无 --"
        } else {
            pluginManager.getPluginInfo(id)?.name ?: "-- 无 --"
        }
    }
    
    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF333333),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        ExposedDropdownMenuBox(
            expanded = expandedState,
            onExpandedChange = onExpandedChange
        ) {
            OutlinedTextField(
                value = getPluginNameById(current),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .background(Color.White, RoundedCornerShape(8.dp)),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1A3A4A),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    color = Color(0xFF1A1A1A)
                )
            )
            ExposedDropdownMenu(
                expanded = expandedState,
                onDismissRequest = { onExpandedChange(false) },
                containerColor = Color.White
            ) {
                pluginNames.forEach { name ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                name,
                                color = Color(0xFF1A1A1A),
                                fontSize = 13.sp
                            ) 
                        },
                        onClick = {
                            onSelected(name)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}