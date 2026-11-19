// app/src/main/java/com/UIN/Tool/widget/WidgetConfigureActivity.kt
package com.UIN.Tool.widget

import com.UIN.Tool.utils.Str
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppToast

private const val TAG = "WidgetConfigureActivity"

@OptIn(ExperimentalMaterial3Api::class)
class WidgetConfigureActivity : AppCompatActivity() {
    
    private lateinit var appWidgetManager: AppWidgetManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.i(TAG, "========================================")
        Logger.i(TAG, "onCreate called")
        Logger.i(TAG, "Intent action: ${intent.action}")
        Logger.i(TAG, "Intent extras: ${intent?.extras?.keySet()}")

        appWidgetManager = AppWidgetManager.getInstance(this)

        // 获取 appWidgetId，如果没有则使用 INVALID
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        Logger.i(TAG, "appWidgetId: $appWidgetId")

        setContent {
            val pluginManager = ServiceLocator.getPluginManager()
            // p.txt 第 13/14 条：列表要**跟着插件清单刷新** —— 插件是异步加载的，
            // 原来只在进入页面时取一次快照，慢一步就永远是空列表。
            val plugins by pluginManager.plugins.collectAsState()
            // o.txt 第 16 条：3x3 小部件有 9 个格子，配置页也必须能填满 9 个 ——
            // 原来只有 3 个下拉框，剩下 6 格只能自动补位，用户指定不了。
            // p.txt 第 13 条（用户）：9 个下拉框换成一个可搜索的多选列表 ——
            // 勾选顺序就是第 1..9 格，未勾满的格子仍由 provider 自动补位。
            val selectedIds = remember { mutableStateListOf<String>() }
            var isPinning by remember { mutableStateOf(false) }

            // 尝试加载配置
            LaunchedEffect(Unit) {
                Logger.d(TAG, Str.get(R.string.launchedeffect_loading_plugin_list_and))
                Logger.d(TAG, Str.get(R.string.plugin_count_plugins_size, plugins.size))
                
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val config = WidgetConfig.load(this@WidgetConfigureActivity, appWidgetId)
                    Logger.d(TAG, Str.get(R.string.loaded_config_config_pluginid1_confi, config?.pluginId1, config?.pluginId2, config?.pluginId3))
                    selectedIds.clear()
                    config?.let { c ->
                        for (i in 0 until WIDGET_PLUGIN_SLOTS) {
                            val id = c.idAt(i)
                            // 旧配置里同一插件可能占多格；多选列表每项只出现一次。
                            if (id.isNotEmpty() && id !in selectedIds) selectedIds.add(id)
                        }
                    }
                } else {
                    Logger.d(TAG, Str.get(R.string.opened_from_manage_page_loading_default))
                    val prefs = getSharedPreferences("widget_global_config", MODE_PRIVATE)
                    selectedIds.clear()
                    for (i in 0 until WIDGET_PLUGIN_SLOTS) {
                        val id = prefs.getString("global_plugin_${i + 1}", "") ?: ""
                        if (id.isNotEmpty() && id !in selectedIds) selectedIds.add(id)
                    }
                }
                Logger.d(TAG, "selected state: ${selectedIds.toList()}")
            }

            UINToolTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UnifiedTitleText(
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) 
                            Str.get(R.string.configure_widget) 
                        else 
                            Str.get(R.string.configure_home_screen_widget)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        UnifiedBodyText(Str.get(R.string.choose_plugins_to_show_up_to_9_shown))
                    } else {
                        UnifiedBodyText(Str.get(R.string.choose_plugins_to_display_applied_to))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // p.txt 第 13 条（用户）：一个搜索框 + 一份多选列表，取代原先 9 个下拉框。
                    WidgetPluginMultiSelect(
                        selectedIds = selectedIds,
                        plugins = plugins,
                        onToggle = { id ->
                            if (id in selectedIds) {
                                selectedIds.remove(id)
                            } else if (selectedIds.size >= WIDGET_PLUGIN_SLOTS) {
                                AppToast.info(
                                    this@WidgetConfigureActivity,
                                    Str.get(R.string.widget_plugin_limit, WIDGET_PLUGIN_SLOTS)
                                )
                            } else {
                                selectedIds.add(id)
                            }
                            Logger.d(TAG, "selection changed: ${selectedIds.toList()}")
                        },
                        onClear = { selectedIds.clear() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    UnifiedCaptionText(
                        Str.get(R.string.unconfigured_positions_automatically),
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
                            border = null,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (AppColors.glassEnabled())
                                    AppColors.glassBackground()
                                else
                                    Color(0xFFF0F4F8)
                            ),
                            shape = RoundedCornerShape(AppDimens.cardCornerRadius)
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
                                        text = Str.get(R.string.how_to_add_a_home_screen_widget),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF1A3A4A),
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                                
                                Text(
                                    text = Str.get(R.string.widget_config_steps),
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
                                    text = Str.get(R.string.tip_current_config_is_applied),
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
                                    Logger.i(TAG, Str.get(R.string.tap_to_add_shortcut))
                                    pin1x1Widget()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = !isPinning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color(0xFF1A3A4A)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFB0C4D0)),
                                shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                            ) {
                                Text(Str.get(R.string.add_shortcut))
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
                        UnifiedButton(
                            text = Str.get(R.string.save),
                            onClick = {
                                Logger.i(TAG, Str.get(R.string.tap_the_save_button))
                                // 勾选顺序 → 第 1..9 格；没勾满的补空串，provider 自动补位。
                                val slots = List(WIDGET_PLUGIN_SLOTS) {
                                    selectedIds.getOrElse(it) { "" }
                                }
                                Logger.d(TAG, "saving config: $slots")
                                
                                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    // 保存到指定小部件
                                    WidgetConfig(slots).save(
                                        this@WidgetConfigureActivity,
                                        appWidgetId
                                    )
                                    
                                    // ✅ 强制刷新小部件
                                    val appWidgetManager = AppWidgetManager.getInstance(this@WidgetConfigureActivity)
                                    WidgetProvider.updateWidget(this@WidgetConfigureActivity, appWidgetManager, appWidgetId)
                                    WidgetProvider.sendRefreshIntent(this@WidgetConfigureActivity)
                                    
                                    Logger.success(TAG, Str.get(R.string.widget_configured_and_refreshed))
                                    
                                    // ✅ 重要：返回 RESULT_OK，告诉系统配置成功
                                    val resultIntent = Intent().apply {
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    }
                                    setResult(RESULT_OK, resultIntent)
                                    
                                } else {
                                    // 保存到全局配置
                                    val prefs = getSharedPreferences("widget_global_config", MODE_PRIVATE)
                                    prefs.edit().apply {
                                        for (i in 0 until WIDGET_PLUGIN_SLOTS) {
                                            putString("global_plugin_${i + 1}", slots[i])
                                        }
                                    }.apply()
                                    
                                    // ✅ 刷新所有小部件
                                    WidgetProvider.forceRefreshAllWidgets(this@WidgetConfigureActivity)
                                    Logger.success(TAG, Str.get(R.string.global_config_saved_all_widgets))
                                    
                                    // ✅ 返回 RESULT_OK
                                    setResult(RESULT_OK)
                                }
                                
                                AppToast.show(this@WidgetConfigureActivity, Str.get(R.string.config_saved_and_refreshed))
                                
                                // ✅ 延迟关闭，确保刷新完成
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    finish()
                                }, 300)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        UnifiedButton(variant = ButtonVariant.Outlined, 
                            text = Str.get(R.string.cancel),
                            onClick = {
                                Logger.i(TAG, Str.get(R.string.tap_the_cancel_button))
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
            Logger.d(TAG, Str.get(R.string.refreshing_widget_appwidgetid, appWidgetId))
            WidgetProvider.updateWidget(this, appWidgetManager, appWidgetId)
            Logger.success(TAG, Str.get(R.string.widget_refreshed_appwidgetid, appWidgetId))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_refresh_widget_e_message, e.message), e)
        }
    }
    
    private fun refreshAllWidgets() {
        try {
            Logger.d(TAG, Str.get(R.string.refresh_all_widgets))
            val componentName = ComponentName(this, WidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                appWidgetIds.forEach { widgetId ->
                    WidgetProvider.updateWidget(this, appWidgetManager, widgetId)
                }
                Logger.success(TAG, Str.get(R.string.all_widgets_refreshed_appwidgetids_s, appWidgetIds.size))
            } else {
                Logger.d(TAG, Str.get(R.string.no_widgets_added_yet))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_refresh_all_widgets_e_mess, e.message), e)
        }
    }
    
    // ==================== 添加快捷方式（软件内添加） ====================
    
    private fun pin1x1Widget() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val componentName = ComponentName(this, Widget1x1Provider::class.java)
                
                if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                    Logger.i(TAG, Str.get(R.string.adding_1x1_shortcut))
                    
                    // 请求固定小部件
                    appWidgetManager.requestPinAppWidget(componentName, null, null)
                    
                    AppToast.showLong(this, Str.get(R.string.place_the_shortcut_on_your_home_screen))
                    
                    Logger.success(TAG, Str.get(R.string.widget_1x1_shortcut_request_sent))
                } else {
                    AppToast.showLong(this, Str.get(R.string.your_launcher_doesn_t_support_pinnin_2))
                    Logger.w(TAG, Str.get(R.string.launcher_does_not_support_pinning))
                }
            } else {
                AppToast.showLong(this, Str.get(R.string.this_requires_android_8_0_long_press))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_add_shortcut_e_message, e.message), e)
            AppToast.show(this, Str.get(R.string.add_failed_e_message, e.message))
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
class Widget1x1ConfigureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.i(TAG, "========================================")
        Logger.i(TAG, "Widget1x1ConfigureActivity onCreate called")
        Logger.i(TAG, "Intent action: ${intent.action}")

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        Logger.i(TAG, "1x1 appWidgetId: $appWidgetId")

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Logger.e(TAG, Str.get(R.string.invalid_appwidgetid_finishing_activity))
            finish()
            return
        }

        setContent {
            val pluginManager = ServiceLocator.getPluginManager()
            // p.txt 第 13/14 条：列表要**跟着插件清单刷新** —— 插件是异步加载的，
            // 原来只在进入页面时取一次快照，慢一步就永远是空列表。
            val plugins by pluginManager.plugins.collectAsState()
            var selected by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                Logger.d(TAG, Str.get(R.string.launchedeffect_loading_plugin_list_a_2))
                Logger.d(TAG, Str.get(R.string.plugin_count_plugins_size, plugins.size))
                
                val config = Widget1x1Config.load(this@Widget1x1ConfigureActivity, appWidgetId)
                Logger.d(TAG, Str.get(R.string.loaded_1x1_config_config_pluginid, config?.pluginId))
                
                config?.let {
                    selected = it.pluginId
                }
                Logger.d(TAG, Str.get(R.string.currently_selected_selected, selected))
            }

            UINToolTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UnifiedTitleText(Str.get(R.string.choose_shortcut_plugin))
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedBodyText(Str.get(R.string.choose_a_plugin_as_a_home_screen))
                    Spacer(modifier = Modifier.height(24.dp))

                    // p.txt 第 14 条（用户）：下拉框换成「搜索框 + 单选列表」——
                    // 插件一多，下拉里滚着找比列表里直接勾选难得多。
                    WidgetPluginRadioList(
                        selected = selected,
                        plugins = plugins,
                        onSelect = {
                            Logger.d(TAG, Str.get(R.string.select_plugin_it, it))
                            selected = it
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnifiedButton(
                            text = Str.get(R.string.confirm_2),
                            onClick = {
                                Logger.i(TAG, Str.get(R.string.tapped_1x1_confirm_button))
                                Logger.d(TAG, Str.get(R.string.selected_plugin_selected, selected))
                                
                                Widget1x1Config(selected).save(
                                    this@Widget1x1ConfigureActivity,
                                    appWidgetId
                                )
                                
                                Logger.d(TAG, Str.get(R.string.widget_1x1_config_saved_refreshing_widget))
                                
                                val appWidgetManager = AppWidgetManager.getInstance(this@Widget1x1ConfigureActivity)
                                Widget1x1Provider.update1x1Widget(
                                    this@Widget1x1ConfigureActivity,
                                    appWidgetManager,
                                    appWidgetId
                                )
                                
                                Logger.success(TAG, Str.get(R.string.widget_1x1_widget_configured))
                                
                                // ✅ 返回 RESULT_OK
                                val result = Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        // q.txt 第 9 条（用户）：去掉「设为默认」按钮 —— 1x1 快捷方式的
                        // 「不指定」已经在列表首项，再摆一颗把配置清空的按钮是重复入口，
                        // 而且容易误触。确认后照常保存当前选择。
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

// ==================== 插件选择列表（搜索 + 多选 / 单选） ====================

/**
 * p.txt 第 13 条（用户）：3x3 小部件配置页 —— 一个搜索框 + 一份多选列表，
 * 取代原来 9 个下拉框。勾选顺序就是小部件第 1..9 格；右侧数字把顺序说清楚。
 */
@Composable
private fun WidgetPluginMultiSelect(
    selectedIds: List<String>,
    plugins: List<PluginInfo>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = filterPlugins(plugins, query)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Str.get(
                    R.string.widget_plugin_selected,
                    selectedIds.size,
                    WIDGET_PLUGIN_SLOTS
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.textSecondary()
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectedIds.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(Str.get(R.string.widget_plugin_clear))
                }
            }
        }
        UnifiedTextField(
            value = query,
            onValueChange = { query = it },
            label = Str.get(R.string.widget_plugin_search),
            leadingIcon = Icons.Default.Search,
            showClearButton = true,
            // 配置页是「进来就挑」，自动弹键盘会盖住半个列表。
            autoFocus = false,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (shown.isEmpty()) {
            UnifiedCaptionText(
                text = Str.get(R.string.widget_plugin_no_match),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            )
        } else {
            // 外层是 verticalScroll，这里必须给一个**上界**，否则 LazyColumn 量不到高度。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(shown, key = { it.pluginId }) { p ->
                    val order = selectedIds.indexOf(p.pluginId)
                    PluginPickRow(
                        name = p.name,
                        caption = pluginCaption(p),
                        checked = order >= 0,
                        order = if (order >= 0) order + 1 else null,
                        radio = false,
                        onClick = { onToggle(p.pluginId) }
                    )
                }
            }
        }
        if (selectedIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            UnifiedCaptionText(
                text = Str.get(R.string.widget_plugin_order_hint),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * p.txt 第 14 条（用户）：1x1 快捷方式配置页 —— 下拉框换成搜索框 + 单选列表。
 * 列表首项是「不指定」，与原先下拉里的「无」等价。
 */
@Composable
private fun WidgetPluginRadioList(
    selected: String,
    plugins: List<PluginInfo>,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = filterPlugins(plugins, query)

    Column(modifier = Modifier.fillMaxWidth()) {
        UnifiedTextField(
            value = query,
            onValueChange = { query = it },
            label = Str.get(R.string.widget_plugin_search),
            leadingIcon = Icons.Default.Search,
            showClearButton = true,
            autoFocus = false,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 「不指定」只在没搜东西时出现：搜索时它跟任何关键词都不匹配，
            // 留着会让人以为搜到了什么。
            if (query.isBlank()) {
                item(key = "__none__") {
                    PluginPickRow(
                        name = Str.get(R.string.none_2),
                        caption = Str.get(R.string.widget_plugin_none_caption),
                        checked = selected.isEmpty(),
                        order = null,
                        radio = true,
                        onClick = { onSelect("") }
                    )
                }
            }
            if (shown.isEmpty()) {
                item(key = "__empty__") {
                    UnifiedCaptionText(
                        text = Str.get(R.string.widget_plugin_no_match),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(shown, key = { it.pluginId }) { p ->
                    PluginPickRow(
                        name = p.name,
                        caption = pluginCaption(p),
                        checked = selected == p.pluginId,
                        order = null,
                        radio = true,
                        onClick = { onSelect(p.pluginId) }
                    )
                }
            }
        }
    }
}

/** 名称 / 包内 id / 作者都可搜 —— 用户记得的往往是其中一个片段。 */
private fun filterPlugins(plugins: List<PluginInfo>, query: String): List<PluginInfo> {
    val q = query.trim()
    if (q.isEmpty()) return plugins
    return plugins.filter {
        it.name.contains(q, ignoreCase = true) ||
            it.pluginId.contains(q, ignoreCase = true) ||
            it.author.contains(q, ignoreCase = true)
    }
}

/** 副标题：作者优先（人记得住），没有再退回插件 id。 */
private fun pluginCaption(p: PluginInfo): String =
    if (p.author.isNotBlank()) "${p.author} · ${p.pluginId}" else p.pluginId

/**
 * 列表行：多选画方勾选框，单选画圆点；[order] 非空时在右侧标出它在小部件里的格位。
 */
@Composable
private fun PluginPickRow(
    name: String,
    caption: String,
    checked: Boolean,
    order: Int?,
    radio: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (radio) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (checked) accent else AppColors.outlineVariant(),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) accent else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (checked) accent else AppColors.outlineVariant(),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = AppColors.textPrimary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    fontSize = 11.sp,
                    color = AppColors.textHint(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (order != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.toString(),
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = accent
                )
            }
        }
    }
}
