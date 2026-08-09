// app/src/main/java/com/UIN/Tool/widget/WidgetConfigureActivity.kt
package com.UIN.Tool.widget

import com.UIN.Tool.utils.Str
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens

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
                Logger.d(TAG, Str.get(R.string.launchedeffect_loading_plugin_list_a))
                plugins = pluginManager.plugins.value
                Logger.d(TAG, Str.get(R.string.plugin_count_plugins_size, plugins.size))
                
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val config = WidgetConfig.load(this@WidgetConfigureActivity, appWidgetId)
                    Logger.d(TAG, Str.get(R.string.loaded_config_config_pluginid1_confi, config?.pluginId1, config?.pluginId2, config?.pluginId3))
                    config?.let {
                        selected1 = it.pluginId1
                        selected2 = it.pluginId2
                        selected3 = it.pluginId3
                    }
                } else {
                    Logger.d(TAG, Str.get(R.string.opened_from_manage_page_loading_defa))
                    val prefs = getSharedPreferences("widget_global_config", MODE_PRIVATE)
                    selected1 = prefs.getString("global_plugin_1", "") ?: ""
                    selected2 = prefs.getString("global_plugin_2", "") ?: ""
                    selected3 = prefs.getString("global_plugin_3", "") ?: ""
                }
                Logger.d(TAG, Str.get(R.string.selected_state_selected1_selected2_s, selected1, selected2, selected3))
            }

            val pluginNames = listOf(Str.get(R.string.none_2)) + plugins.map { it.name }
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
                            Str.get(R.string.configure_widget) 
                        else 
                            Str.get(R.string.configure_home_screen_widget)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        UIComponents.BodyText(Str.get(R.string.choose_plugins_to_show_up_to_3_shown))
                    } else {
                        UIComponents.BodyText(Str.get(R.string.choose_plugins_to_display_applied_to))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // ✅ 白色背景下拉框 - 位置1
                    WidgetPluginSelectorWhite(
                        label = Str.get(R.string.position_1_2),
                        current = selected1,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, Str.get(R.string.position_1_selection_it, it))
                            selected1 = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ✅ 白色背景下拉框 - 位置2
                    WidgetPluginSelectorWhite(
                        label = Str.get(R.string.position_2_2),
                        current = selected2,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, Str.get(R.string.position_2_selection_it, it))
                            selected2 = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ✅ 白色背景下拉框 - 位置3
                    WidgetPluginSelectorWhite(
                        label = Str.get(R.string.position_3_2),
                        current = selected3,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, Str.get(R.string.position_3_selection_it, it))
                            selected3 = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    UIComponents.CaptionText(
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
                                    text = Str.get(R.string.tip_current_config_is_applied_automa),
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
                                    containerColor = Color.White,
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
                        UIComponents.PrimaryButton(
                            text = Str.get(R.string.save),
                            onClick = {
                                Logger.i(TAG, Str.get(R.string.tap_the_save_button))
                                Logger.d(TAG, Str.get(R.string.saving_config_selected1_selected2_se, selected1, selected2, selected3))
                                
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
                                        putString("global_plugin_1", selected1)
                                        putString("global_plugin_2", selected2)
                                        putString("global_plugin_3", selected3)
                                    }.apply()
                                    
                                    // ✅ 刷新所有小部件
                                    WidgetProvider.forceRefreshAllWidgets(this@WidgetConfigureActivity)
                                    Logger.success(TAG, Str.get(R.string.global_config_saved_all_widgets_refr))
                                    
                                    // ✅ 返回 RESULT_OK
                                    setResult(RESULT_OK)
                                }
                                
                                Toast.makeText(
                                    this@WidgetConfigureActivity,
                                    Str.get(R.string.config_saved_and_refreshed),
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
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
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
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
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
                    
                    Toast.makeText(
                        this,
                        Str.get(R.string.place_the_shortcut_on_your_home_scre),
                        Toast.LENGTH_LONG
                    ).show()
                    
                    Logger.success(TAG, Str.get(R.string.widget_1x1_shortcut_request_sent))
                } else {
                    Toast.makeText(
                        this,
                        Str.get(R.string.your_launcher_doesn_t_support_pinnin_2),
                        Toast.LENGTH_LONG
                    ).show()
                    Logger.w(TAG, Str.get(R.string.launcher_does_not_support_pinning_sh))
                }
            } else {
                Toast.makeText(
                    this,
                    Str.get(R.string.this_requires_android_8_0_long_press),
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_add_shortcut_e_message, e.message), e)
            Toast.makeText(this, Str.get(R.string.add_failed_e_message, e.message), Toast.LENGTH_SHORT).show()
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
            Logger.e(TAG, Str.get(R.string.invalid_appwidgetid_finishing_activi))
            finish()
            return
        }

        setContent {
            val pluginManager = ServiceLocator.getPluginManager()
            var plugins by remember { mutableStateOf(pluginManager.plugins.value) }
            var selected by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                Logger.d(TAG, Str.get(R.string.launchedeffect_loading_plugin_list_a_2))
                plugins = pluginManager.plugins.value
                Logger.d(TAG, Str.get(R.string.plugin_count_plugins_size, plugins.size))
                
                val config = Widget1x1Config.load(this@Widget1x1ConfigureActivity, appWidgetId)
                Logger.d(TAG, Str.get(R.string.loaded_1x1_config_config_pluginid, config?.pluginId))
                
                config?.let {
                    selected = it.pluginId
                }
                Logger.d(TAG, Str.get(R.string.currently_selected_selected, selected))
            }

            val pluginNames = listOf(Str.get(R.string.none_2)) + plugins.map { it.name }
            val pluginIds = listOf("") + plugins.map { it.pluginId }

            UINToolTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UIComponents.TitleText(Str.get(R.string.choose_shortcut_plugin))
                    Spacer(modifier = Modifier.height(8.dp))
                    UIComponents.BodyText(Str.get(R.string.choose_a_plugin_as_a_home_screen_sho))
                    Spacer(modifier = Modifier.height(24.dp))

                    // ✅ 白色背景下拉框
                    WidgetPluginSelectorWhite(
                        label = Str.get(R.string.select_plugin_2),
                        current = selected,
                        pluginNames = pluginNames,
                        pluginIds = pluginIds,
                        onSelected = { 
                            Logger.d(TAG, Str.get(R.string.select_plugin_it, it))
                            selected = it 
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UIComponents.PrimaryButton(
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
                        UIComponents.SecondaryButton(
                            text = Str.get(R.string.set_as_default),
                            onClick = {
                                Logger.i(TAG, Str.get(R.string.tapped_1x1_set_default_button))
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
                value = if (current.isEmpty()) Str.get(R.string.none_2) else pluginNames[pluginIds.indexOf(current)],
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .background(Color.White, RoundedCornerShape(AppDimens.inputCornerRadius)),
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
                shape = RoundedCornerShape(AppDimens.inputCornerRadius)
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