// app/src/main/java/com/UIN/Tool/widget/WidgetConfigureActivity.kt
package com.UIN.Tool.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.theme.UINToolTheme

private const val TAG = "WidgetConfigureActivity"

@OptIn(ExperimentalMaterial3Api::class)
class WidgetConfigureActivity : ComponentActivity() {
    
    private lateinit var appWidgetManager: AppWidgetManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.i(TAG, "========================================")
        Logger.i(TAG, "onCreate called")
        Logger.i(TAG, "Intent: $intent")
        Logger.i(TAG, "Intent extras: ${intent?.extras}")

        appWidgetManager = AppWidgetManager.getInstance(this)

        // 获取 appWidgetId，如果没有则使用 INVALID
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        Logger.i(TAG, "appWidgetId: $appWidgetId")

        setContent {
            val pluginManager = ServiceLocator.getPluginManager()
            var plugins by remember { mutableStateOf(pluginManager.plugins.value) }
            var selected1 by remember { mutableStateOf("") }
            var selected2 by remember { mutableStateOf("") }
            var selected3 by remember { mutableStateOf("") }
            var isPinning by remember { mutableStateOf(false) }

            // 尝试加载配置
            LaunchedEffect(Unit) {
                Logger.d(TAG, "LaunchedEffect: 加载插件列表和配置")
                plugins = pluginManager.plugins.value
                Logger.d(TAG, "插件数量: ${plugins.size}")
                
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val config = WidgetConfig.load(this@WidgetConfigureActivity, appWidgetId)
                    Logger.d(TAG, "加载的配置: ${config?.pluginId1}, ${config?.pluginId2}, ${config?.pluginId3}")
                    config?.let {
                        selected1 = it.pluginId1
                        selected2 = it.pluginId2
                        selected3 = it.pluginId3
                    }
                } else {
                    Logger.d(TAG, "从管理页面打开，加载默认配置")
                    val prefs = getSharedPreferences("widget_global_config", MODE_PRIVATE)
                    selected1 = prefs.getString("global_plugin_1", "") ?: ""
                    selected2 = prefs.getString("global_plugin_2", "") ?: ""
                    selected3 = prefs.getString("global_plugin_3", "") ?: ""
                }
                Logger.d(TAG, "选中状态: $selected1, $selected2, $selected3")
            }

            val pluginNames = listOf("-- 无 --") + plugins.map { it.name }
            val pluginIds = listOf("") + plugins.map { it.pluginId }

            UINToolTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UIComponents.TitleText(
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) 
                            "配置小部件" 
                        else 
                            "配置桌面小部件"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        UIComponents.BodyText("选择要显示的插件（最多3个，显示在前3个位置）")
                    } else {
                        UIComponents.BodyText("选择要显示的插件，将应用到所有小部件")
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // ✅ 白色背景下拉框 - 位置1
                    WidgetPluginSelectorWhite(
                        label = "位置 1",
                        current = selected1,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, "位置1 选择: $it")
                            selected1 = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ✅ 白色背景下拉框 - 位置2
                    WidgetPluginSelectorWhite(
                        label = "位置 2",
                        current = selected2,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, "位置2 选择: $it")
                            selected2 = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ✅ 白色背景下拉框 - 位置3
                    WidgetPluginSelectorWhite(
                        label = "位置 3",
                        current = selected3,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, "位置3 选择: $it")
                            selected3 = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    UIComponents.CaptionText(
                        "💡 未配置的位置将自动显示其他插件",
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ============================================================
                    // ✅ 添加小部件说明和快捷方式按钮（仅在从管理页面打开时显示）
                    // ============================================================
                    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        // 📌 提示卡片 - 如何添加小部件
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF0F4F8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF1A3A4A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "如何添加桌面小部件",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF1A3A4A),
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                                
                                Text(
                                    text = "1. 长按桌面空白处\n" +
                                           "2. 选择「小部件」或「添加工具」\n" +
                                           "3. 找到「UIN Tool」\n" +
                                           "4. 长按拖拽到桌面即可",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF444444),
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Start
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Divider(
                                    color = Color(0xFFD0D5DA),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                
                                Text(
                                    text = "💡 提示：添加后会自动应用当前配置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF666666),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // ============================================================
                        // ✅ 添加快捷方式按钮（软件内添加）
                        // ============================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    Logger.i(TAG, "点击添加快捷方式")
                                    pin1x1Widget()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = !isPinning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF1A3A4A)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("🔗 添加快捷方式")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ============================================================
                    // ✅ 保存/取消按钮
                    // ============================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UIComponents.PrimaryButton(
                            text = "保存",
                            onClick = {
                                Logger.i(TAG, "点击保存按钮")
                                Logger.d(TAG, "保存配置: $selected1, $selected2, $selected3")
                                
                                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    // 保存到指定小部件
                                    WidgetConfig(selected1, selected2, selected3).save(
                                        this@WidgetConfigureActivity,
                                        appWidgetId
                                    )
                                    
                                    // ✅ 强制刷新小部件
                                    val appWidgetManager = AppWidgetManager.getInstance(this@WidgetConfigureActivity)
                                    WidgetProvider.updateWidget(this@WidgetConfigureActivity, appWidgetManager, appWidgetId)
                                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
                                    WidgetProvider.sendRefreshIntent(this@WidgetConfigureActivity)
                                    
                                    Logger.success(TAG, "小部件配置完成并已刷新")
                                    
                                    // ✅ 重要：返回 RESULT_OK，告诉系统配置成功
                                    val resultIntent = Intent().apply {
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    }
                                    setResult(RESULT_OK, resultIntent)
                                    
                                } else {
                                    // 保存到全局配置
                                    val prefs = getSharedPreferences("widget_global_config", MODE_PRIVATE)
                                    prefs.edit().apply {
                                        putString("global_plugin_1", selected1)
                                        putString("global_plugin_2", selected2)
                                        putString("global_plugin_3", selected3)
                                    }.apply()
                                    
                                    // ✅ 刷新所有小部件
                                    WidgetProvider.forceRefreshAllWidgets(this@WidgetConfigureActivity)
                                    Logger.success(TAG, "全局配置已保存，所有小部件已刷新")
                                    
                                    // ✅ 返回 RESULT_OK
                                    setResult(RESULT_OK)
                                }
                                
                                Toast.makeText(
                                    this@WidgetConfigureActivity,
                                    "配置已保存并刷新",
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // ✅ 延迟关闭，确保刷新完成
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    finish()
                                }, 300)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        UIComponents.SecondaryButton(
                            text = "取消",
                            onClick = {
                                Logger.i(TAG, "点击取消按钮")
                                // ✅ 取消时返回 RESULT_CANCELED
                                setResult(RESULT_CANCELED)
                                finish()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    
    // ==================== 实时刷新小部件 ====================
    
    private fun refreshWidget(appWidgetId: Int) {
        try {
            Logger.d(TAG, "刷新小部件: $appWidgetId")
            WidgetProvider.updateWidget(this, appWidgetManager, appWidgetId)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            Logger.success(TAG, "小部件已刷新: $appWidgetId")
        } catch (e: Exception) {
            Logger.e(TAG, "刷新小部件失败: ${e.message}", e)
        }
    }
    
    private fun refreshAllWidgets() {
        try {
            Logger.d(TAG, "刷新所有小部件")
            val componentName = ComponentName(this, WidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                appWidgetIds.forEach { widgetId ->
                    WidgetProvider.updateWidget(this, appWidgetManager, widgetId)
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
                }
                Logger.success(TAG, "所有小部件已刷新，共 ${appWidgetIds.size} 个")
            } else {
                Logger.d(TAG, "没有已添加的小部件")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "刷新所有小部件失败: ${e.message}", e)
        }
    }
    
    // ==================== 添加快捷方式（软件内添加） ====================
    
    private fun pin1x1Widget() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val componentName = ComponentName(this, Widget1x1Provider::class.java)
                
                if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                    Logger.i(TAG, "开始添加1x1快捷方式")
                    
                    // 请求固定小部件
                    appWidgetManager.requestPinAppWidget(componentName, null, null)
                    
                    Toast.makeText(
                        this,
                        "请在桌面放置快捷方式",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    Logger.success(TAG, "已请求添加1x1快捷方式")
                } else {
                    Toast.makeText(
                        this,
                        "当前启动器不支持固定快捷方式，请长按桌面手动添加",
                        Toast.LENGTH_LONG
                    ).show()
                    Logger.w(TAG, "启动器不支持固定快捷方式")
                }
            } else {
                Toast.makeText(
                    this,
                    "Android 8.0+ 才支持此功能，请长按桌面手动添加",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "添加快捷方式失败: ${e.message}", e)
            Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "onDestroy called")
        Logger.i(TAG, "========================================")
    }
}

// ==================== 1x1 小部件配置 ====================

@OptIn(ExperimentalMaterial3Api::class)
class Widget1x1ConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.i(TAG, "========================================")
        Logger.i(TAG, "Widget1x1ConfigureActivity onCreate called")
        Logger.i(TAG, "Intent: $intent")

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        Logger.i(TAG, "1x1 appWidgetId: $appWidgetId")

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Logger.e(TAG, "无效的 appWidgetId，结束 Activity")
            finish()
            return
        }

        setContent {
            val pluginManager = ServiceLocator.getPluginManager()
            var plugins by remember { mutableStateOf(pluginManager.plugins.value) }
            var selected by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                Logger.d(TAG, "LaunchedEffect: 加载插件列表和1x1配置")
                plugins = pluginManager.plugins.value
                Logger.d(TAG, "插件数量: ${plugins.size}")
                
                val config = Widget1x1Config.load(this@Widget1x1ConfigureActivity, appWidgetId)
                Logger.d(TAG, "加载的1x1配置: ${config?.pluginId}")
                
                config?.let {
                    selected = it.pluginId
                }
                Logger.d(TAG, "当前选中: $selected")
            }

            val pluginNames = listOf("-- 无 --") + plugins.map { it.name }
            val pluginIds = listOf("") + plugins.map { it.pluginId }

            UINToolTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UIComponents.TitleText("选择快捷方式插件")
                    Spacer(modifier = Modifier.height(8.dp))
                    UIComponents.BodyText("选择一个插件作为桌面快捷方式")
                    Spacer(modifier = Modifier.height(24.dp))

                    // ✅ 白色背景下拉框
                    WidgetPluginSelectorWhite(
                        label = "选择插件",
                        current = selected,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, "选择插件: $it")
                            selected = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UIComponents.PrimaryButton(
                            text = "确认",
                            onClick = {
                                Logger.i(TAG, "点击1x1确认按钮")
                                Logger.d(TAG, "选中的插件: $selected")
                                
                                Widget1x1Config(selected).save(
                                    this@Widget1x1ConfigureActivity,
                                    appWidgetId
                                )
                                
                                Logger.d(TAG, "1x1配置已保存，刷新小部件")
                                
                                val appWidgetManager = AppWidgetManager.getInstance(this@Widget1x1ConfigureActivity)
                                Widget1x1Provider.update1x1Widget(
                                    this@Widget1x1ConfigureActivity,
                                    appWidgetManager,
                                    appWidgetId
                                )
                                
                                Logger.success(TAG, "1x1小部件配置完成")
                                
                                // ✅ 返回 RESULT_OK
                                val result = Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        UIComponents.SecondaryButton(
                            text = "设为默认",
                            onClick = {
                                Logger.i(TAG, "点击1x1设为默认按钮")
                                Widget1x1Config("").save(
                                    this@Widget1x1ConfigureActivity,
                                    appWidgetId
                                )
                                
                                val appWidgetManager = AppWidgetManager.getInstance(this@Widget1x1ConfigureActivity)
                                Widget1x1Provider.update1x1Widget(
                                    this@Widget1x1ConfigureActivity,
                                    appWidgetManager,
                                    appWidgetId
                                )
                                
                                // ✅ 返回 RESULT_OK
                                val result = Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "Widget1x1ConfigureActivity onDestroy called")
        Logger.i(TAG, "========================================")
    }
}

// ==================== 白色背景下拉框组件 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPluginSelectorWhite(
    label: String,
    current: String,
    pluginNames: List<String>,
    pluginIds: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF333333),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { 
                expanded = it 
            }
        ) {
            OutlinedTextField(
                value = if (current.isEmpty()) "-- 无 --" else pluginNames[pluginIds.indexOf(current)],
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
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
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { 
                    expanded = false 
                },
                containerColor = Color.White
            ) {
                pluginNames.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = name,
                                color = Color(0xFF1A1A1A)
                            ) 
                        },
                        onClick = {
                            onSelected(pluginIds[index])
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}