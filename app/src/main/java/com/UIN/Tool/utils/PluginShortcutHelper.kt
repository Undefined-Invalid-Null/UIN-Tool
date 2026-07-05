// app/src/main/java/com/UIN/Tool/utils/PluginShortcutHelper.kt
package com.UIN.Tool.utils

import android.annotation.SuppressLint
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
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import java.io.File

object PluginShortcutHelper {

    private const val TAG = "PluginShortcutHelper"

    /**
     * 创建插件快捷方式
     * 直接指向 MainActivity，通过 extra 传递插件ID
     */
    fun createShortcut(context: Context, plugin: PluginInfo) {
        if (!isShortcutSupported(context)) {
            Toast.makeText(context, "当前设备不支持创建快捷方式", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createShortcutForOreoAndAbove(context, plugin)
        } else {
            createShortcutForOldVersions(context, plugin)
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
    private fun createShortcutForOreoAndAbove(context: Context, plugin: PluginInfo) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        // 检查是否已存在
        val existingShortcuts = shortcutManager.pinnedShortcuts
        for (info in existingShortcuts) {
            if (info.id == "plugin_${plugin.pluginId}") {
                Toast.makeText(context, "快捷方式已存在", Toast.LENGTH_SHORT).show()
                return
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
            Toast.makeText(context, "无法获取插件图标", Toast.LENGTH_SHORT).show()
            return
        }

        val shortcut = ShortcutInfo.Builder(context, "plugin_${plugin.pluginId}")
            .setShortLabel(plugin.name)
            .setLongLabel(plugin.description.ifEmpty { plugin.name })
            .setIcon(icon)
            .setIntent(intent)
            .build()

        val success = shortcutManager.requestPinShortcut(shortcut, null)
        if (success) {
            Toast.makeText(context, "快捷方式已创建", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "快捷方式已创建", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("NewApi")
    private fun getPluginIcon(context: Context, plugin: PluginInfo): Icon? {
        val bitmap = getPluginIconBitmap(context, plugin)
        return if (bitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val scaled = Bitmap.createScaledBitmap(bitmap, 72, 72, true)
            Icon.createWithBitmap(scaled)
        } else {
            null
        }
    }

    private fun getPluginIconBitmap(context: Context, plugin: PluginInfo): Bitmap? {
        return try {
            val pluginDir = File(Constants.PLUGIN_DIR, plugin.pluginId)
            if (pluginDir.exists()) {
                val iconPath = if (plugin.icon.isNotEmpty()) plugin.icon else "icon.png"
                val iconFile = File(pluginDir, iconPath)
                if (iconFile.exists()) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(iconFile.absolutePath, options)
                } else null
            } else null
        } catch (e: Exception) {
            Logger.e(TAG, "获取插件图标失败", e)
            null
        }
    }

    @Suppress("DEPRECATION", "NewApi")
    fun removeShortcut(context: Context, plugin: PluginInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                shortcutManager?.removeDynamicShortcuts(listOf("plugin_${plugin.pluginId}"))
                Toast.makeText(context, "快捷方式已移除", Toast.LENGTH_SHORT).show()
            } else {
                val removeIntent = Intent().apply {
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(context, MainActivity::class.java))
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, plugin.name)
                    action = "com.android.launcher.action.UNINSTALL_SHORTCUT"
                }
                context.sendBroadcast(removeIntent)
                Toast.makeText(context, "快捷方式已移除", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "移除快捷方式失败", e)
            Toast.makeText(context, "移除快捷方式失败", Toast.LENGTH_SHORT).show()
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