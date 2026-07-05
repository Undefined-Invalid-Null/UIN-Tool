// app/src/main/java/com/UIN/Tool/widget/WidgetProvider.kt
package com.UIN.Tool.widget

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
import com.UIN.Tool.utils.Constants
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
        Logger.d("WidgetConfig", "保存配置: appWidgetId=$appWidgetId")
        context.getSharedPreferences(Constants.PREF_WIDGET, Context.MODE_PRIVATE).edit().apply {
            putString("${key}_1", pluginId1)
            putString("${key}_2", pluginId2)
            putString("${key}_3", pluginId3)
        }.apply()
        Logger.success("WidgetConfig", "配置保存成功")
    }

    companion object {
        fun load(context: Context, appWidgetId: Int): WidgetConfig? {
            val key = "widget_$appWidgetId"
            val prefs = context.getSharedPreferences(Constants.PREF_WIDGET, Context.MODE_PRIVATE)
            val id1 = prefs.getString("${key}_1", "") ?: ""
            val id2 = prefs.getString("${key}_2", "") ?: ""
            val id3 = prefs.getString("${key}_3", "") ?: ""
            
            Logger.d("WidgetConfig", "加载配置: appWidgetId=$appWidgetId, id1=$id1, id2=$id2, id3=$id3")
            
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
            Logger.i(TAG, "[$time] 🔄 refreshAllWidgets 开始")
            
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, WidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                
                Logger.i(TAG, "[$time] 📊 找到 ${appWidgetIds.size} 个小部件")
                
                appWidgetIds.forEachIndexed { index, appWidgetId ->
                    Logger.i(TAG, "[$time]   ➜ 刷新第 ${index + 1} 个小部件, ID: $appWidgetId")
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
                
                Logger.i(TAG, "[$time] ✅ refreshAllWidgets 完成")
                Logger.i(TAG, "══════════════════════════════════════════════════")
            } catch (e: Exception) {
                Logger.e(TAG, "[$time] ❌ refreshAllWidgets 异常: ${e.message}", e)
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
                Logger.d(TAG, "📤 刷新广播已发送")
            } catch (e: Exception) {
                Logger.e(TAG, "❌ sendRefreshIntent 异常: ${e.message}", e)
            }
        }

        @JvmStatic
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                Logger.w(TAG, "[$time] ⚠️ updateWidget: 无效的 appWidgetId，跳过")
                return
            }

            Logger.enter(TAG, "updateWidget")
            Logger.param(TAG, "appWidgetId", appWidgetId)
            Logger.i(TAG, "[$time] ══════════════════════════════════════════════════")
            Logger.i(TAG, "[$time] 🚀 开始更新小部件 ID: $appWidgetId")
            
            try {
                // ============================================================
                // 步骤1: 创建 RemoteViews
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤1: 创建 RemoteViews")
                val views = RemoteViews(context.packageName, R.layout.widget_layout_grid)
                Logger.success(TAG, "[$time] ✅ RemoteViews 创建成功")
                Logger.d(TAG, "[$time]    布局文件: widget_layout_grid")

                // ============================================================
                // 步骤2: 获取插件管理器
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤2: 获取 PluginManager")
                val pluginManager = PluginManager.getInstance(context)
                Logger.success(TAG, "[$time] ✅ PluginManager 获取成功")
                Logger.d(TAG, "[$time]    PluginManager 实例: ${pluginManager.hashCode()}")

                // ============================================================
                // 步骤3: 刷新插件列表
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤3: 刷新插件列表")
                pluginManager.refreshPlugins()
                Logger.success(TAG, "[$time] ✅ 插件列表已刷新")

                // ============================================================
                // 步骤4: 获取所有插件
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤4: 获取所有插件")
                val allPlugins = pluginManager.plugins.value
                Logger.param(TAG, "[$time] 插件总数", allPlugins.size)
                Logger.d(TAG, "[$time]    allPlugins 类型: ${allPlugins.javaClass.simpleName}")
                Logger.d(TAG, "[$time]    allPlugins 是否为空: ${allPlugins.isEmpty()}")
                
                if (allPlugins.isEmpty()) {
                    Logger.w(TAG, "[$time] ⚠️ 没有已安装的插件!")
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    for (i in 0 until MAX_PLUGINS) {
                        val slotId = getSlotId(i)
                        views.setViewVisibility(slotId, android.view.View.GONE)
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    Logger.w(TAG, "[$time] ⚠️ 已更新空小部件（无插件）")
                    Logger.exit(TAG, "updateWidget", System.currentTimeMillis())
                    return
                }
                
                // 打印所有插件信息
                allPlugins.forEachIndexed { index, plugin ->
                    Logger.d(TAG, "[$time]    插件 ${index + 1}: ${plugin.name} (${plugin.pluginId})")
                }

                // ============================================================
                // 步骤5: 加载配置
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤5: 加载小部件配置")
                val config = WidgetConfig.load(context, appWidgetId)
                Logger.d(TAG, "[$time]    配置对象: ${if (config != null) "存在" else "null"}")
                
                var displayPlugins = mutableListOf<PluginInfo>()
                
                if (config != null && config.hasSelectedPlugins()) {
                    Logger.i(TAG, "[$time] ✅ 找到配置: pluginId1=${config.pluginId1}, pluginId2=${config.pluginId2}, pluginId3=${config.pluginId3}")
                    
                    // 按配置添加插件
                    addPluginById(config.pluginId1, pluginManager, displayPlugins, "位置1", time)
                    addPluginById(config.pluginId2, pluginManager, displayPlugins, "位置2", time)
                    addPluginById(config.pluginId3, pluginManager, displayPlugins, "位置3", time)
                    
                    // 如果配置的插件少于9个，用其他插件填充
                    if (displayPlugins.size < MAX_PLUGINS) {
                        Logger.d(TAG, "[$time]    配置插件 ${displayPlugins.size} 个，需要填充到 $MAX_PLUGINS 个")
                        val remaining = allPlugins.filter { !displayPlugins.contains(it) }
                        Logger.d(TAG, "[$time]    剩余可用插件: ${remaining.size} 个")
                        val toAdd = remaining.take(MAX_PLUGINS - displayPlugins.size)
                        displayPlugins.addAll(toAdd)
                        Logger.d(TAG, "[$time]    填充了 ${toAdd.size} 个插件")
                    }
                } else {
                    Logger.i(TAG, "[$time] ℹ️ 没有配置，显示所有插件（最多 $MAX_PLUGINS 个）")
                    displayPlugins = allPlugins.take(MAX_PLUGINS).toMutableList()
                }
                
                Logger.param(TAG, "[$time] 最终显示插件数量", displayPlugins.size)
                displayPlugins.forEachIndexed { index, plugin ->
                    Logger.d(TAG, "[$time]    位置 ${index + 1}: ${plugin.name}")
                }

                // ============================================================
                // 步骤6: 设置标题
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤6: 设置标题")
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                Logger.success(TAG, "[$time] ✅ 标题设置完成: ${context.getString(R.string.app_name)}")

                // ============================================================
                // 步骤7: 设置每个插槽
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤7: 设置 $MAX_PLUGINS 个插槽")
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
                    Logger.d(TAG, "[$time]    设置插槽 ${i + 1} ($slotName): ${if (plugin != null) plugin.name else "空"}")
                    setupSlot(views, slotId, plugin, context, appWidgetId, i, time)
                }
                Logger.success(TAG, "[$time] ✅ 所有插槽设置完成")

                // ============================================================
                // 步骤8: 更新小部件
                // ============================================================
                Logger.i(TAG, "[$time] 📌 步骤8: 更新小部件")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                Logger.success(TAG, "[$time] ✅ 小部件更新完成, appWidgetId: $appWidgetId")
                
                // 验证更新是否成功
                Logger.i(TAG, "[$time] 📌 步骤9: 验证更新")
                try {
                    val updatedViews = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    Logger.d(TAG, "[$time]    小部件选项: $updatedViews")
                } catch (e: Exception) {
                    Logger.w(TAG, "[$time]    无法获取小部件选项: ${e.message}")
                }

            } catch (e: Exception) {
                Logger.e(TAG, "[$time] ❌ updateWidget 异常: ${e.message}", e)
                Logger.e(TAG, "[$time] ❌ 异常堆栈: ${e.stackTraceToString()}")
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
            Logger.d(TAG, "[$time]    $position: 尝试添加插件 ID: $pluginId")
            if (!pluginId.isNullOrEmpty()) {
                val plugin = pluginManager.getPluginInfo(pluginId)
                if (plugin != null && !list.contains(plugin)) {
                    list.add(plugin)
                    Logger.success(TAG, "[$time]    ✅ $position 添加成功: ${plugin.name}")
                } else if (plugin == null) {
                    Logger.w(TAG, "[$time]    ⚠️ $position 插件不存在: $pluginId")
                } else {
                    Logger.d(TAG, "[$time]    ℹ️ $position 插件已存在，跳过: ${plugin.name}")
                }
            } else {
                Logger.d(TAG, "[$time]    ℹ️ $position 未配置插件")
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
                Logger.d(TAG, "[$time]      插槽 ${index + 1} ($slotName): 显示插件 '${plugin.name}'")
                
                // 显示插件
                views.setViewVisibility(slotId, android.view.View.VISIBLE)
                views.setTextViewText(nameId, plugin.name)
                Logger.d(TAG, "[$time]        名称已设置: ${plugin.name}")
                
                // 加载图标
                val icon = loadPluginIcon(context, plugin, time)
                if (icon != null) {
                    views.setImageViewBitmap(iconId, icon)
                    Logger.d(TAG, "[$time]        图标已设置: ${icon.width}x${icon.height}")
                } else {
                    views.setImageViewResource(iconId, R.drawable.ic_extension)
                    Logger.w(TAG, "[$time]        ⚠️ 使用默认图标")
                }
                
                // 点击事件
                val clickIntent = Intent(context, PluginHostActivity::class.java).apply {
                    putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId + slotId, clickIntent, getPendingIntentFlags()
                )
                views.setOnClickPendingIntent(slotId, pendingIntent)
                Logger.d(TAG, "[$time]        点击事件已绑定到 PluginHostActivity")
                Logger.d(TAG, "[$time]        插件ID: ${plugin.pluginId}")
                
            } else {
                Logger.d(TAG, "[$time]      插槽 ${index + 1} ($slotName): 空，隐藏")
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
            Logger.d(TAG, "[$time]        loadPluginIcon: 加载插件 '${plugin.name}' 的图标")
            try {
                val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
                Logger.d(TAG, "[$time]          插件目录: ${pluginDir.absolutePath}")
                Logger.d(TAG, "[$time]          目录是否存在: ${pluginDir.exists()}")
                
                if (pluginDir.exists()) {
                    val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                    val iconFile = File(pluginDir, iconPath)
                    Logger.d(TAG, "[$time]          图标文件: ${iconFile.absolutePath}")
                    Logger.d(TAG, "[$time]          文件是否存在: ${iconFile.exists()}")
                    Logger.d(TAG, "[$time]          文件大小: ${iconFile.length()} bytes")
                    
                    if (iconFile.exists()) {
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, options)
                        if (bitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                            Logger.success(TAG, "[$time]          ✅ 图标加载成功: ${scaled.width}x${scaled.height}")
                            return scaled
                        } else {
                            Logger.w(TAG, "[$time]          ⚠️ 图标解码失败")
                        }
                    } else {
                        Logger.w(TAG, "[$time]          ⚠️ 图标文件不存在")
                    }
                } else {
                    Logger.w(TAG, "[$time]          ⚠️ 插件目录不存在")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "[$time]          ❌ loadPluginIcon 异常: ${e.message}", e)
            }
            return null
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, "[$time] 📱 onUpdate 被系统调用")
        Logger.param(TAG, "[$time] 小部件数量", appWidgetIds.size)
        Logger.d(TAG, "[$time] appWidgetIds: ${appWidgetIds.joinToString()}")
        
        if (appWidgetIds.isEmpty()) {
            Logger.w(TAG, "[$time] ⚠️ onUpdate: 没有小部件ID")
            return
        }
        
        appWidgetIds.forEachIndexed { index, appWidgetId ->
            Logger.i(TAG, "[$time]   ➜ 更新小部件 ${index + 1}, ID: $appWidgetId")
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        Logger.i(TAG, "[$time] ✅ onUpdate 完成")
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.d(TAG, "[$time] 📨 onReceive: ${intent.action}")
        
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                Logger.i(TAG, "[$time] 🔄 收到刷新广播")
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                Logger.param(TAG, "[$time] appWidgetId", appWidgetId)
                
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Logger.d(TAG, "[$time] 刷新指定小部件: $appWidgetId")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, appWidgetId)
                } else {
                    Logger.d(TAG, "[$time] 刷新所有小部件")
                    refreshAllWidgets(context)
                }
                Logger.i(TAG, "[$time] ✅ 刷新广播处理完成")
            }
            else -> {
                Logger.d(TAG, "[$time] 未处理的事件: ${intent.action}")
                super.onReceive(context, intent)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, "[$time] 🗑️ onDeleted: 小部件被删除")
        Logger.param(TAG, "[$time] 删除数量", appWidgetIds.size)
        appWidgetIds.forEach { appWidgetId ->
            Logger.d(TAG, "[$time]   删除小部件 ID: $appWidgetId")
            WidgetConfig.delete(context, appWidgetId)
            Logger.d(TAG, "[$time]   配置已清理")
        }
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }

    override fun onEnabled(context: Context) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, "[$time] ✅ onEnabled: 小部件功能启用")
        Logger.i(TAG, "[$time] ⏰ 1秒后延迟刷新所有小部件")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Logger.d(TAG, "[$time] ⏰ 延迟刷新执行")
            forceRefreshAllWidgets(context)
        }, 1000)
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }
    
    override fun onDisabled(context: Context) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Logger.i(TAG, "══════════════════════════════════════════════════")
        Logger.i(TAG, "[$time] ⛔ onDisabled: 小部件功能禁用")
        Logger.i(TAG, "══════════════════════════════════════════════════")
    }
}

// ==================== 1x1 Widget Provider ====================

class Widget1x1Provider : AppWidgetProvider() {

    companion object {
        private const val TAG = "Widget1x1Provider"
        const val ACTION_REFRESH_WIDGET_1x1 = "com.UIN.Tool.REFRESH_WIDGET_1x1"

        @JvmStatic
        fun refresh1x1Widgets(context: Context) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            Logger.i(TAG, "[$time] 🔄 refresh1x1Widgets 开始")
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, Widget1x1Provider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                Logger.d(TAG, "[$time] 找到 ${appWidgetIds.size} 个1x1小部件")
                
                appWidgetIds.forEach { appWidgetId ->
                    update1x1Widget(context, appWidgetManager, appWidgetId)
                }
                Logger.success(TAG, "[$time] ✅ refresh1x1Widgets 完成")
            } catch (e: Exception) {
                Logger.e(TAG, "[$time] ❌ refresh1x1Widgets 异常: ${e.message}", e)
            }
        }

        @JvmStatic
        fun sendRefreshIntent(context: Context) {
            try {
                val intent = Intent(ACTION_REFRESH_WIDGET_1x1)
                intent.setClass(context, Widget1x1Provider::class.java)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Logger.e(TAG, "sendRefreshIntent 异常: ${e.message}", e)
            }
        }

        @JvmStatic
        fun update1x1Widget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

            Logger.enter(TAG, "update1x1Widget")
            Logger.param(TAG, "appWidgetId", appWidgetId)
            Logger.i(TAG, "[$time] 🚀 开始更新1x1小部件 ID: $appWidgetId")
            
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
                        Logger.d(TAG, "[$time] ✅ 1x1绑定插件: ${info.name}")
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
                Logger.success(TAG, "[$time] ✅ 1x1小部件更新完成")

            } catch (e: Exception) {
                Logger.e(TAG, "[$time] ❌ update1x1Widget 异常: ${e.message}", e)
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
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, options)
                        return bitmap?.let { Bitmap.createScaledBitmap(it, 48, 48, true) }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "加载1x1图标失败: ${e.message}")
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
        Logger.i(TAG, "[$time] 📨 SystemEventReceiver 收到事件: ${intent?.action}")
        
        if (intent?.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            Logger.d(TAG, "[$time] 系统启动或应用更新，5秒后刷新小部件")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Logger.d(TAG, "[$time] ⏰ 延迟刷新执行")
                WidgetProvider.forceRefreshAllWidgets(context)
                Widget1x1Provider.refresh1x1Widgets(context)
            }, 5000)
        }
    }
}