// app/src/main/java/com/UIN/Tool/widget/WidgetProvider.kt
package com.UIN.Tool.widget

import com.UIN.Tool.utils.Str
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import com.UIN.Tool.MainActivity
import com.UIN.Tool.R
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginHostActivity
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.constants.AppConstants as Constants
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ==================== Widget Configurations ====================

class WidgetConfig(
    var pluginId1: String = "",
    var pluginId2: String = "",
    var pluginId3: String = ""
) {
    fun hasSelectedPlugins(): Boolean = pluginId1.isNotEmpty() || pluginId2.isNotEmpty() || pluginId3.isNotEmpty()

    fun save(context: Context, appWidgetId: Int) {
        val key = "widget_$appWidgetId"
        Logger.d("WidgetConfig", Str.get(R.string.saving_config_appwidgetid_appwidgeti, appWidgetId))
        context.getSharedPreferences(Constants.PREF_WIDGET, Context.MODE_PRIVATE).edit().apply {
            putString("${key}_1", pluginId1)
            putString("${key}_2", pluginId2)
            putString("${key}_3", pluginId3)
        }.apply()
        Logger.success("WidgetConfig", Str.get(R.string.config_saved_2))
    }

    companion object {
        fun load(context: Context, appWidgetId: Int): WidgetConfig? {
            val key = "widget_$appWidgetId"
            val prefs = context.getSharedPreferences(Constants.PREF_WIDGET, Context.MODE_PRIVATE)
            val id1 = prefs.getString("${key}_1", "") ?: ""
            val id2 = prefs.getString("${key}_2", "") ?: ""
            val id3 = prefs.getString("${key}_3", "") ?: ""
            
            Logger.d("WidgetConfig", Str.get(R.string.loading_config_appwidgetid_appwidget, appWidgetId, id1, id2, id3))
            
            return if (id1.isEmpty() && id2.isEmpty() && id3.isEmpty()) {
                null
            } else {
                WidgetConfig(id1, id2, id3)
            }
        }

        fun delete(context: Context, appWidgetId: Int) {
            val key = "widget_$appWidgetId"
            context.getSharedPreferences(Constants.PREF_WIDGET, Context.MODE_PRIVATE).edit().apply {
                remove("${key}_1")
                remove("${key}_2")
                remove("${key}_3")
            }.apply()
        }
    }
}

class Widget1x1Config(
    var pluginId: String = ""
) {
    fun hasPlugin(): Boolean = pluginId.isNotEmpty()

    fun save(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(Constants.PREF_WIDGET_1X1, Context.MODE_PRIVATE).edit()
            .putString("widget_1x1_${appWidgetId}_plugin", pluginId).apply()
    }

    companion object {
        fun load(context: Context, appWidgetId: Int): Widget1x1Config? {
            val id = context.getSharedPreferences(Constants.PREF_WIDGET_1X1, Context.MODE_PRIVATE)
                .getString("widget_1x1_${appWidgetId}_plugin", "") ?: ""
            return if (id.isEmpty()) null else Widget1x1Config(id)
        }

        fun delete(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(Constants.PREF_WIDGET_1X1, Context.MODE_PRIVATE).edit()
                .remove("widget_1x1_${appWidgetId}_plugin").apply()
        }
    }
}

// ==================== 主 Widget Provider ====================

class WidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "WidgetProvider"
        const val ACTION_REFRESH_WIDGET = "com.UIN.Tool.REFRESH_WIDGET"
        private const val MAX_PLUGINS = 9
        // 小部件图标目标尺寸（主网格）
        private const val ICON_SIZE = 64

        // ✅ Widget 更新涉及磁盘 I/O（refreshPlugins、图标解码），
        //    AppWidget 回调运行在主线程 → 移到后台单线程执行器，避免 ANR
        private val widgetExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

        @JvmStatic
        fun getPendingIntentFlags(): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            return flags
        }

        @JvmStatic
        fun refreshAllWidgets(context: Context) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            Logger.i(TAG, "══════════════════════════════════════════════════")
            Logger.i(TAG, Str.get(R.string.time_refreshallwidgets_started, time))
            
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, WidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                
                Logger.i(TAG, Str.get(R.string.time_found_appwidgetids_size_widget_, time, appWidgetIds.size))
                
                appWidgetIds.forEachIndexed { index, appWidgetId ->
                    Logger.i(TAG, Str.get(R.string.time_refreshing_widget_index_1_id_ap, time, index + 1, appWidgetId))
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
                
                Logger.i(TAG, Str.get(R.string.time_refreshallwidgets_complete, time))
                Logger.i(TAG, "══════════════════════════════════════════════════")
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.time_refreshallwidgets_error_e_messa, time, e.message), e)
            }
        }

        @JvmStatic
        fun forceRefreshAllWidgets(context: Context) {
            refreshAllWidgets(context)
        }

        @JvmStatic
        fun sendRefreshIntent(context: Context) {
            try {
                val intent = Intent(ACTION_REFRESH_WIDGET).setPackage(context.packageName)
                context.sendBroadcast(intent)
                Logger.d(TAG, Str.get(R.string.refresh_broadcast_sent))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.sendrefreshintent_error_e_message, e.message), e)
            }
        }

        @JvmStatic
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                Logger.w(TAG, Str.get(R.string.time_updatewidget_invalid_appwidgeti, "HH:mm:ss.SSS"))
                return
            }

            Logger.enter(TAG, "updateWidget")
            Logger.param(TAG, "appWidgetId", appWidgetId)

            // ✅ 整体工作（refreshPlugins + 图标解码 + RemoteViews 构建）移到后台线程
            widgetExecutor.execute {
                doUpdateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun doUpdateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

            Logger.i(TAG, "[$time] ══════════════════════════════════════════════════")
            Logger.i(TAG, Str.get(R.string.time_updating_widget_id_appwidgetid, time, appWidgetId))
            
            try {
                // ============================================================
                // 步骤1: 创建 RemoteViews
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_1_creating_remoteviews, time))
                val views = RemoteViews(context.packageName, R.layout.widget_layout_grid)
                Logger.success(TAG, Str.get(R.string.time_remoteviews_created, time))
                Logger.d(TAG, Str.get(R.string.time_layout_widget_layout_grid, time))

                // ============================================================
                // 步骤2: 获取插件管理器
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_2_getting_pluginmanager, time))
                val pluginManager = PluginManager.getInstance(context)
                Logger.success(TAG, Str.get(R.string.time_pluginmanager_obtained, time))
                Logger.d(TAG, Str.get(R.string.time_pluginmanager_instance_pluginma, time, pluginManager.hashCode()))

                // ============================================================
                // 步骤3: 刷新插件列表
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_3_refreshing_plugin_list, time))
                pluginManager.refreshPlugins()
                Logger.success(TAG, Str.get(R.string.time_plugin_list_refreshed, time))

                // ============================================================
                // 步骤4: 获取所有插件
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_4_getting_all_plugins, time))
                val allPlugins = pluginManager.plugins.value
                Logger.param(TAG, Str.get(R.string.time_plugin_count, time), allPlugins.size)
                Logger.d(TAG, Str.get(R.string.time_allplugins_type_allplugins_java, time, allPlugins.javaClass.simpleName))
                Logger.d(TAG, Str.get(R.string.time_allplugins_empty_allplugins_ise, time, allPlugins.isEmpty()))
                
                if (allPlugins.isEmpty()) {
                    Logger.w(TAG, Str.get(R.string.time_no_installed_plugins, time))
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    for (i in 0 until MAX_PLUGINS) {
                        val slotId = getSlotId(i)
                        views.setViewVisibility(slotId, android.view.View.GONE)
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    Logger.w(TAG, Str.get(R.string.time_updated_empty_widget_no_plugins, time))
                    Logger.exit(TAG, "updateWidget", System.currentTimeMillis())
                    return
                }
                
                // 打印所有插件信息
                allPlugins.forEachIndexed { index, plugin ->
                    Logger.d(TAG, Str.get(R.string.time_plugin_index_1_plugin_name_plug, time, index + 1, plugin.name, plugin.pluginId))
                }

                // ============================================================
                // 步骤5: 加载配置
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_5_loading_widget_config, time))
                val config = WidgetConfig.load(context, appWidgetId)
                Logger.d(TAG, Str.get(R.string.w_time_config_object_if_config, if (config != null) Str.get(R.string.exists) else "null"))
                
                var displayPlugins = mutableListOf<PluginInfo>()
                
                if (config != null && config.hasSelectedPlugins()) {
                    Logger.i(TAG, Str.get(R.string.time_found_config_pluginid1_config_p, time, config.pluginId1, config.pluginId2, config.pluginId3))
                    
                    // 按配置添加插件
                    addPluginById(config.pluginId1, pluginManager, displayPlugins, Str.get(R.string.position_1), time)
                    addPluginById(config.pluginId2, pluginManager, displayPlugins, Str.get(R.string.position_2), time)
                    addPluginById(config.pluginId3, pluginManager, displayPlugins, Str.get(R.string.position_3), time)
                    
                    // 如果配置的插件少于9个，用其他插件填充
                    if (displayPlugins.size < MAX_PLUGINS) {
                        Logger.d(TAG, Str.get(R.string.time_config_has_displayplugins_size_, time, displayPlugins.size, MAX_PLUGINS))
                        val remaining = allPlugins.filter { !displayPlugins.contains(it) }
                        Logger.d(TAG, Str.get(R.string.time_remaining_plugins_remaining_siz, time, remaining.size))
                        val toAdd = remaining.take(MAX_PLUGINS - displayPlugins.size)
                        displayPlugins.addAll(toAdd)
                        Logger.d(TAG, Str.get(R.string.time_filled_toadd_size_plugin_s, time, toAdd.size))
                    }
                } else {
                    Logger.i(TAG, Str.get(R.string.time_no_config_showing_all_plugins_m, time, MAX_PLUGINS))
                    displayPlugins = allPlugins.take(MAX_PLUGINS).toMutableList()
                }
                
                Logger.param(TAG, Str.get(R.string.time_final_displayed_plugin_count, time), displayPlugins.size)
                displayPlugins.forEachIndexed { index, plugin ->
                    Logger.d(TAG, Str.get(R.string.time_position_index_1_plugin_name, time, index + 1, plugin.name))
                }

                // ============================================================
                // 步骤6: 设置标题
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_6_setting_title, time))
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                Logger.success(TAG, Str.get(R.string.time_title_set_context_getstring_r_s, time, context.getString(R.string.app_name)))

                // ============================================================
                // 步骤7: 设置每个插槽
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_7_setting_max_plugins_slot, time, MAX_PLUGINS))
                for (i in 0 until MAX_PLUGINS) {
                    val slotId = getSlotId(i)
                    val plugin = if (i < displayPlugins.size) displayPlugins[i] else null
                    val slotName = when (i) {
                        0 -> "slot_1"
                        1 -> "slot_2"
                        2 -> "slot_3"
                        3 -> "slot_4"
                        4 -> "slot_5"
                        5 -> "slot_6"
                        6 -> "slot_7"
                        7 -> "slot_8"
                        8 -> "slot_9"
                        else -> "unknown"
                    }
                    Logger.d(TAG, Str.get(R.string.w_time_slot_position_if_plugin, i + 1, slotName, if (plugin != null) plugin.name else Str.get(R.string.empty)))
                    setupSlot(views, slotId, plugin, context, appWidgetId, i, time)
                }
                Logger.success(TAG, Str.get(R.string.time_all_slots_set, time))

                // ============================================================
                // 步骤8: 更新小部件
                // ============================================================
                Logger.i(TAG, Str.get(R.string.time_step_8_updating_widget, time))
                appWidgetManager.updateAppWidget(appWidgetId, views)
                Logger.success(TAG, Str.get(R.string.time_widget_updated_appwidgetid_appw, time, appWidgetId))
                
                // 验证更新是否成功
                Logger.i(TAG, Str.get(R.string.time_step_9_verifying_update, time))
                try {
                    val updatedViews = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    Logger.d(TAG, Str.get(R.string.time_widget_options_updatedviews, time, updatedViews))
                } catch (e: Exception) {
                    Logger.w(TAG, Str.get(R.string.time_failed_to_get_widget_options_e_, time, e.message))
                }

            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.time_updatewidget_error_e_message, time, e.message), e)
                Logger.e(TAG, Str.get(R.string.time_error_stack_e_stacktracetostrin, time, e.stackTraceToString()))
            } finally {
                Logger.exit(TAG, "updateWidget", System.currentTimeMillis())
                Logger.i(TAG, "[$time] ══════════════════════════════════════════════════")
            }
        }

        private fun addPluginById(
            pluginId: String?, 
            pluginManager: PluginManager, 
            list: MutableList<PluginInfo>, 
            position: String,
            time: String
        ) {
            Logger.d(TAG, Str.get(R.string.time_position_trying_to_add_plugin_i, time, position, pluginId))
            if (!pluginId.isNullOrEmpty()) {
                val plugin = pluginManager.getPluginInfo(pluginId)
                if (plugin != null && !list.contains(plugin)) {
                    list.add(plugin)
                    Logger.success(TAG, Str.get(R.string.time_position_added_plugin_name, time, position, plugin.name))
                } else if (plugin == null) {
                    Logger.w(TAG, Str.get(R.string.time_position_plugin_not_found_plugi, time, position, pluginId))
                } else {
                    Logger.d(TAG, Str.get(R.string.time_position_plugin_exists_skipping, time, position, plugin.name))
                }
            } else {
                Logger.d(TAG, Str.get(R.string.time_position_no_plugin_configured, time, position))
            }
        }

        private fun getSlotId(index: Int): Int {
            return when (index) {
                0 -> R.id.slot_1
                1 -> R.id.slot_2
                2 -> R.id.slot_3
                3 -> R.id.slot_4
                4 -> R.id.slot_5
                5 -> R.id.slot_6
                6 -> R.id.slot_7
                7 -> R.id.slot_8
                8 -> R.id.slot_9
                else -> R.id.slot_1
            }
        }

        private fun setupSlot(
            views: RemoteViews, 
            slotId: Int, 
            plugin: PluginInfo?, 
            context: Context, 
            appWidgetId: Int,
            index: Int,
            time: String
        ) {
            val iconId = getIconId(slotId)
            val nameId = getNameId(slotId)
            val slotName = when (index) {
                0 -> "slot_1"
                1 -> "slot_2"
                2 -> "slot_3"
                3 -> "slot_4"
                4 -> "slot_5"
                5 -> "slot_6"
                6 -> "slot_7"
                7 -> "slot_8"
                8 -> "slot_9"
                else -> "unknown"
            }
            
            if (plugin != null) {
                Logger.d(TAG, Str.get(R.string.time_slot_index_1_slotname_showing_p, time, index + 1, slotName, plugin.name))
                
                // 显示插件
                views.setViewVisibility(slotId, android.view.View.VISIBLE)
                views.setTextViewText(nameId, plugin.name)
                Logger.d(TAG, Str.get(R.string.time_name_set_plugin_name, time, plugin.name))
                
                // 加载图标
                val icon = loadPluginIcon(context, plugin, time)
                if (icon != null) {
                    views.setImageViewBitmap(iconId, icon)
                    Logger.d(TAG, Str.get(R.string.time_icon_set_icon_width_x_icon_heig, time, icon.width, icon.height))
                } else {
                    views.setImageViewResource(iconId, R.drawable.ic_extension)
                    Logger.w(TAG, Str.get(R.string.time_using_default_icon, time))
                }
                
                // 点击事件
                val clickIntent = Intent(context, PluginHostActivity::class.java).apply {
                    putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId + slotId, clickIntent, getPendingIntentFlags()
                )
                views.setOnClickPendingIntent(slotId, pendingIntent)
                Logger.d(TAG, Str.get(R.string.time_click_bound_to_pluginhostactivi, time))
                Logger.d(TAG, Str.get(R.string.time_plugin_id_plugin_pluginid, time, plugin.pluginId))
                
            } else {
                Logger.d(TAG, Str.get(R.string.time_slot_index_1_slotname_empty_hid, time, index + 1, slotName))
                views.setViewVisibility(slotId, android.view.View.GONE)
            }
        }

        private fun getIconId(slotId: Int): Int {
            return when (slotId) {
                R.id.slot_1 -> R.id.slot_1_icon
                R.id.slot_2 -> R.id.slot_2_icon
                R.id.slot_3 -> R.id.slot_3_icon
                R.id.slot_4 -> R.id.slot_4_icon
                R.id.slot_5 -> R.id.slot_5_icon
                R.id.slot_6 -> R.id.slot_6_icon
                R.id.slot_7 -> R.id.slot_7_icon
                R.id.slot_8 -> R.id.slot_8_icon
                R.id.slot_9 -> R.id.slot_9_icon
                else -> R.id.slot_1_icon
            }
        }

        private fun getNameId(slotId: Int): Int {
            return when (slotId) {
                R.id.slot_1 -> R.id.slot_1_name
                R.id.slot_2 -> R.id.slot_2_name
                R.id.slot_3 -> R.id.slot_3_name
                R.id.slot_4 -> R.id.slot_4_name
                R.id.slot_5 -> R.id.slot_5_name
                R.id.slot_6 -> R.id.slot_6_name
                R.id.slot_7 -> R.id.slot_7_name
                R.id.slot_8 -> R.id.slot_8_name
                R.id.slot_9 -> R.id.slot_9_name
                else -> R.id.slot_1_name
            }
        }

        @JvmStatic
        fun loadPluginIcon(context: Context, plugin: PluginInfo, time: String = ""): Bitmap? {
            Logger.d(TAG, Str.get(R.string.time_loadpluginicon_loading_icon_of_, time, plugin.name))
            try {
                val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
                Logger.d(TAG, Str.get(R.string.time_plugin_directory_plugindir_abso, time, pluginDir.absolutePath))
                Logger.d(TAG, Str.get(R.string.time_directory_exists_plugindir_exis, time, pluginDir.exists()))
                
                if (pluginDir.exists()) {
                    val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                    val iconFile = File(pluginDir, iconPath)
                    Logger.d(TAG, Str.get(R.string.time_icon_file_iconfile_absolutepath, time, iconFile.absolutePath))
                    Logger.d(TAG, Str.get(R.string.time_file_exists_iconfile_exists, time, iconFile.exists()))
                    Logger.d(TAG, Str.get(R.string.time_file_size_iconfile_length_bytes, time, iconFile.length()))
                    
                    if (iconFile.exists()) {
                        // ✅ 先读边界计算 inSampleSize 降采样，避免全尺寸解码 OOM/大内存
                        val bounds = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(iconFile.absolutePath, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= ICON_SIZE &&
                            bounds.outHeight / (sample * 2) >= ICON_SIZE
                        ) {
                            sample *= 2
                        }
                        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, opts)
                        if (bitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(bitmap, ICON_SIZE, ICON_SIZE, true)
                            if (scaled != bitmap) bitmap.recycle()
                            Logger.success(TAG, Str.get(R.string.time_icon_loaded_scaled_width_x_scal, time, scaled.width, scaled.height))
                            return scaled
                        } else {
                            Logger.w(TAG, Str.get(R.string.time_icon_decode_failed, time))
                        }
                    } else {
                        Logger.w(TAG, Str.get(R.string.time_icon_file_not_found, time))
                    }
                } else {
                    Logger.w(TAG, Str.get(R.string.time_plugin_directory_not_found, time))
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.time_loadpluginicon_error_e_message, time, e.message), e)
            }
            return null
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, Str.get(R.string.time_onupdate_called_by_system, time))
        Logger.param(TAG, Str.get(R.string.time_widget_count, time), appWidgetIds.size)
        Logger.d(TAG, "[$time] appWidgetIds: ${appWidgetIds.joinToString()}")
        
        if (appWidgetIds.isEmpty()) {
            Logger.w(TAG, Str.get(R.string.time_onupdate_no_widget_ids, time))
            return
        }
        
        appWidgetIds.forEachIndexed { index, appWidgetId ->
            Logger.i(TAG, Str.get(R.string.time_updating_widget_index_1_id_appw, time, index + 1, appWidgetId))
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        Logger.i(TAG, Str.get(R.string.time_onupdate_complete, time))
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.d(TAG, "[$time] 📨 onReceive: ${intent.action}")
        
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                Logger.i(TAG, Str.get(R.string.time_received_refresh_broadcast, time))
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                Logger.param(TAG, "[$time] appWidgetId", appWidgetId)
                
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Logger.d(TAG, Str.get(R.string.time_refreshing_widget_appwidgetid, time, appWidgetId))
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, appWidgetId)
                } else {
                    Logger.d(TAG, Str.get(R.string.time_refreshing_all_widgets, time))
                    refreshAllWidgets(context)
                }
                Logger.i(TAG, Str.get(R.string.time_refresh_broadcast_handled, time))
            }
            else -> {
                Logger.d(TAG, Str.get(R.string.time_unhandled_event_intent_action, time, intent.action))
                super.onReceive(context, intent)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, Str.get(R.string.time_ondeleted_widget_deleted, time))
        Logger.param(TAG, Str.get(R.string.time_deleted_count, time), appWidgetIds.size)
        appWidgetIds.forEach { appWidgetId ->
            Logger.d(TAG, Str.get(R.string.time_deleted_widget_id_appwidgetid, time, appWidgetId))
            WidgetConfig.delete(context, appWidgetId)
            Logger.d(TAG, Str.get(R.string.time_config_cleared, time))
        }
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }

    override fun onEnabled(context: Context) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, Str.get(R.string.time_onenabled_widget_feature_enable, time))
        Logger.i(TAG, Str.get(R.string.time_delayed_refresh_of_all_widgets_, time))
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Logger.d(TAG, Str.get(R.string.time_delayed_refresh_executed, time))
            forceRefreshAllWidgets(context)
        }, 1000)
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }
    
    override fun onDisabled(context: Context) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, Str.get(R.string.time_ondisabled_widget_feature_disab, time))
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }
}

// ==================== 1x1 Widget Provider ====================

class Widget1x1Provider : AppWidgetProvider() {

    companion object {
        private const val TAG = "Widget1x1Provider"
        const val ACTION_REFRESH_WIDGET_1x1 = "com.UIN.Tool.REFRESH_WIDGET_1x1"
        // 1x1 小部件图标目标尺寸
        private const val ICON_SIZE_1X1 = 48

        // ✅ 同主 Widget：磁盘 I/O 移到后台线程，避免主线程 ANR
        private val widgetExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

        @JvmStatic
        fun refresh1x1Widgets(context: Context) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            Logger.i(TAG, Str.get(R.string.time_refresh1x1widgets_started, time))
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, Widget1x1Provider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                Logger.d(TAG, Str.get(R.string.time_found_appwidgetids_size_1x1_wid, time, appWidgetIds.size))
                
                appWidgetIds.forEach { appWidgetId ->
                    update1x1Widget(context, appWidgetManager, appWidgetId)
                }
                Logger.success(TAG, Str.get(R.string.time_refresh1x1widgets_complete, time))
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.time_refresh1x1widgets_error_e_messa, time, e.message), e)
            }
        }

        @JvmStatic
        fun sendRefreshIntent(context: Context) {
            try {
                val intent = Intent(ACTION_REFRESH_WIDGET_1x1)
                intent.setClass(context, Widget1x1Provider::class.java)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.sendrefreshintent_error_e_message_2, e.message), e)
            }
        }

        @JvmStatic
        fun update1x1Widget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

            Logger.enter(TAG, "update1x1Widget")
            Logger.param(TAG, "appWidgetId", appWidgetId)

            // ✅ 移到后台线程
            widgetExecutor.execute {
                doUpdate1x1Widget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun doUpdate1x1Widget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            Logger.i(TAG, Str.get(R.string.time_updating_1x1_widget_id_appwidge, time, appWidgetId))
            
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_layout_1x1)

                // 默认打开主界面
                views.setTextViewText(R.id.widget_1x1_label, context.getString(R.string.app_name))
                views.setImageViewResource(R.id.widget_1x1_icon, R.drawable.ic_launcher_foreground)

                val defaultIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("selected_tab", "tools")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val defaultPendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, defaultIntent, getPendingIntentFlags()
                )
                views.setOnClickPendingIntent(R.id.widget_1x1_root, defaultPendingIntent)

                // 加载插件配置
                val config = Widget1x1Config.load(context, appWidgetId)
                if (config != null && config.hasPlugin()) {
                    val pm = PluginManager.getInstance(context)
                    val info = pm.getPluginInfo(config.pluginId)

                    if (info != null) {
                        views.setTextViewText(R.id.widget_1x1_label, info.name)
                        
                        val icon = loadPluginIconFor1x1(context, info)
                        if (icon != null) {
                            views.setImageViewBitmap(R.id.widget_1x1_icon, icon)
                        } else {
                            views.setImageViewResource(R.id.widget_1x1_icon, R.drawable.ic_extension)
                        }

                        val pluginIntent = Intent(context, PluginHostActivity::class.java).apply {
                            putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, info.pluginId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val pluginPendingIntent = PendingIntent.getActivity(
                            context, appWidgetId, pluginIntent, getPendingIntentFlags()
                        )
                        views.setOnClickPendingIntent(R.id.widget_1x1_root, pluginPendingIntent)
                        Logger.d(TAG, Str.get(R.string.time_1x1_bound_plugin_info_name, time, info.name))
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
                Logger.success(TAG, Str.get(R.string.time_1x1_widget_updated, time))

            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.time_update1x1widget_error_e_message, time, e.message), e)
            } finally {
                Logger.exit(TAG, "update1x1Widget", System.currentTimeMillis())
            }
        }

        private fun loadPluginIconFor1x1(context: Context, plugin: PluginInfo): Bitmap? {
            try {
                val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
                if (pluginDir.exists()) {
                    val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                    val iconFile = File(pluginDir, iconPath)
                    if (iconFile.exists()) {
                        // ✅ 降采样避免全尺寸解码
                        val bounds = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(iconFile.absolutePath, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= ICON_SIZE_1X1 &&
                            bounds.outHeight / (sample * 2) >= ICON_SIZE_1X1
                        ) {
                            sample *= 2
                        }
                        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, opts)
                        return bitmap?.let {
                            val scaled = Bitmap.createScaledBitmap(it, ICON_SIZE_1X1, ICON_SIZE_1X1, true)
                            if (scaled != it) it.recycle()
                            scaled
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_load_1x1_icon_e_message, e.message))
            }
            return null
        }

        private fun getPendingIntentFlags(): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            return flags
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            update1x1Widget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH_WIDGET_1x1 -> {
                refresh1x1Widgets(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Widget1x1Config.delete(context, it) }
    }

    override fun onEnabled(context: Context) {
        Logger.i(TAG, "1x1 Widget enabled")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            refresh1x1Widgets(context)
        }, 1000)
    }
    
    override fun onDisabled(context: Context) {
        Logger.i(TAG, "1x1 Widget disabled")
    }
}

// ==================== System Event Receiver ====================

class SystemEventReceiver : android.content.BroadcastReceiver() {
    companion object {
        private const val TAG = "SystemEventReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent?) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, Str.get(R.string.time_systemeventreceiver_got_event_i, time, intent?.action))
        
        if (intent?.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            Logger.d(TAG, Str.get(R.string.time_system_boot_or_app_update_refre, time))
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Logger.d(TAG, Str.get(R.string.time_delayed_refresh_executed, time))
                WidgetProvider.forceRefreshAllWidgets(context)
                Widget1x1Provider.refresh1x1Widgets(context)
            }, 5000)
        }
    }
}