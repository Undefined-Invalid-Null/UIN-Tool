// app/src/main/java/com/UIN/Tool/utils/PluginShortcutHelper.kt
package com.UIN.Tool.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.UIN.Tool.MainActivity
import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.widget.Widget1x1Provider
import com.UIN.Tool.widget.WidgetPinnedReceiver
import java.io.File

object PluginShortcutHelper {

    private const val TAG = "PluginShortcutHelper"
    const val ACTION_WIDGET_1X1_PINNED = "com.UIN.Tool.WIDGET_1X1_PINNED"

    /**
     * 创建插件快捷方式
     * 直接指向 MainActivity，通过 extra 传递插件ID
     */
    fun createShortcut(context: Context, plugin: PluginInfo) {
        Logger.i(TAG, "createShortcut() plugin=${plugin.pluginId} sdk=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (pin1x1Widget(context, plugin)) {
                return
            }
            Logger.w(TAG, "小部件固定不可用，回退到 requestPinShortcut")
            if (!isShortcutSupported(context)) {
                Logger.w(TAG, Str.get(R.string.this_device_does_not_support_shortcu))
                Toast.makeText(context, Str.get(R.string.this_device_does_not_support_shortcu), Toast.LENGTH_SHORT).show()
                return
            }
            val created = createShortcutForOreoAndAbove(context, plugin)
            if (!created) {
                Logger.w(TAG, "requestPinShortcut 失败或返回 false，回退到旧版广播方式")
                createShortcutForOldVersions(context, plugin)
            }
        } else {
            createShortcutForOldVersions(context, plugin)
        }
    }

    private fun pin1x1Widget(context: Context, plugin: PluginInfo): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
                Logger.w(TAG, "Launcher 不支持固定小部件")
                return false
            }
            val callbackIntent = Intent(context, WidgetPinnedReceiver::class.java).apply {
                action = ACTION_WIDGET_1X1_PINNED
                putExtra(WidgetPinnedReceiver.EXTRA_PLUGIN_ID, plugin.pluginId)
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            val successCallback = PendingIntent.getBroadcast(
                context, plugin.pluginId.hashCode(), callbackIntent, flags
            )
            appWidgetManager.requestPinAppWidget(
                ComponentName(context, Widget1x1Provider::class.java), null, successCallback
            )
            Toast.makeText(
                context,
                Str.get(R.string.place_the_shortcut_on_your_home_scre),
                Toast.LENGTH_LONG
            ).show()
            Logger.success(TAG, "已请求固定 1x1 快捷方式: ${plugin.pluginId}")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "请求固定 1x1 快捷方式失败", e)
            false
        }
    }

    private fun isShortcutSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            return shortcutManager != null && shortcutManager.isRequestPinShortcutSupported
        }
        return true
    }

    @SuppressLint("NewApi")
    private fun createShortcutForOreoAndAbove(context: Context, plugin: PluginInfo): Boolean {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false

        // 检查是否已存在
        val existingShortcuts = shortcutManager.pinnedShortcuts
        for (info in existingShortcuts) {
            if (info.id == "plugin_${plugin.pluginId}") {
                Logger.i(TAG, "快捷方式已存在: ${plugin.pluginId}")
                Toast.makeText(context, Str.get(R.string.shortcut_already_exists), Toast.LENGTH_SHORT).show()
                return true
            }
        }

        // 直接指向 MainActivity，通过 extra 传递插件ID
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("plugin_id", plugin.pluginId)
            putExtra("open_plugin", true)
            action = Intent.ACTION_VIEW
            // 不加任何 FLAG，让系统正常处理
        }

        val icon = getPluginIcon(context, plugin)
        if (icon == null) {
            Logger.w(TAG, "无法获取插件图标，使用默认图标 plugin=${plugin.pluginId}")
        }

        val shortcut = ShortcutInfo.Builder(context, "plugin_${plugin.pluginId}")
            .setShortLabel(plugin.name)
            .setLongLabel(plugin.description.ifEmpty { plugin.name })
            .setIcon(icon)
            .setIntent(intent)
            .build()

        return try {
            val success = shortcutManager.requestPinShortcut(shortcut, null)
            Logger.i(TAG, "requestPinShortcut 返回: $success plugin=${plugin.pluginId}")
            if (success) {
                Toast.makeText(context, Str.get(R.string.shortcut_created), Toast.LENGTH_SHORT).show()
            }
            success
        } catch (e: Exception) {
            Logger.e(TAG, "requestPinShortcut 抛出异常 plugin=${plugin.pluginId}", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun createShortcutForOldVersions(context: Context, plugin: PluginInfo) {
        // 直接指向 MainActivity，通过 extra 传递插件ID
        val shortcutIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("plugin_id", plugin.pluginId)
            putExtra("open_plugin", true)
            action = Intent.ACTION_VIEW
        }

        val addIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, plugin.name)
            action = "com.android.launcher.action.INSTALL_SHORTCUT"
        }

        val bitmap = getPluginIconBitmap(context, plugin)
        if (bitmap != null) {
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
        } else {
            addIntent.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_extension)
            )
        }

        context.sendBroadcast(addIntent)
        Logger.i(TAG, Str.get(R.string.creating_legacy_shortcut_plugin_name, plugin.name))
        Toast.makeText(context, Str.get(R.string.shortcut_created), Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("NewApi")
    private fun getPluginIcon(context: Context, plugin: PluginInfo): Icon? {
        return try {
            val bitmap = getPluginIconBitmap(context, plugin)
            if (bitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val scaled = Bitmap.createScaledBitmap(bitmap, 72, 72, true)
                Icon.createWithBitmap(scaled)
            } else {
                Icon.createWithResource(context, R.drawable.ic_extension)
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_get_plugin_icon), e)
            Icon.createWithResource(context, R.drawable.ic_extension)
        }
    }

    private fun getPluginIconBitmap(context: Context, plugin: PluginInfo): Bitmap? {
        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
            if (pluginDir.exists()) {
                val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                val iconFile = File(pluginDir, iconPath)
                if (iconFile.exists()) {
                    BitmapFactory.decodeFile(iconFile.absolutePath)
                } else null
            } else null
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_get_plugin_icon), e)
            null
        }
    }

    @Suppress("DEPRECATION", "NewApi")
    fun removeShortcut(context: Context, plugin: PluginInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                shortcutManager?.removeDynamicShortcuts(listOf("plugin_${plugin.pluginId}"))
                Toast.makeText(context, Str.get(R.string.shortcut_removed), Toast.LENGTH_SHORT).show()
            } else {
                val removeIntent = Intent().apply {
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(context, MainActivity::class.java))
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, plugin.name)
                    action = "com.android.launcher.action.UNINSTALL_SHORTCUT"
                }
                context.sendBroadcast(removeIntent)
                Toast.makeText(context, Str.get(R.string.shortcut_removed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.failed_to_remove_shortcut), e)
            Toast.makeText(context, Str.get(R.string.failed_to_remove_shortcut), Toast.LENGTH_SHORT).show()
        }
    }

    fun isShortcutExists(context: Context, pluginId: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                val pinnedShortcuts = shortcutManager?.pinnedShortcuts ?: return false
                pinnedShortcuts.any { it.id == "plugin_$pluginId" }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}