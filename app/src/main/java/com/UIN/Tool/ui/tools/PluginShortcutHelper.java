// app/src/main/java/com/UIN/Tool/ui/tools/PluginShortcutHelper.java
package com.UIN.Tool.ui.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginHostActivity;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.utils.LogUtils;

import java.io.File;
import java.util.List;

public class PluginShortcutHelper {

    public static void createShortcut(Context context, PluginInfo plugin) {
        if (!isShortcutSupported(context)) {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createShortcutForOreoAndAbove(context, plugin);
        } else {
            createShortcutForOldVersions(context, plugin);
        }
    }

    private static boolean isShortcutSupported(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
            return shortcutManager != null && shortcutManager.isRequestPinShortcutSupported();
        }
        return true;
    }

    @SuppressLint("NewApi")
    private static void createShortcutForOreoAndAbove(Context context, PluginInfo plugin) {
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager == null) return;

        List<ShortcutInfo> existingShortcuts = shortcutManager.getPinnedShortcuts();
        for (ShortcutInfo info : existingShortcuts) {
            if (info.getId().equals("plugin_" + plugin.pluginId)) {
                Toast.makeText(context, R.string.shortcut_exists, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Intent intent = new Intent(context, PluginHostActivity.class);
        intent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Icon icon = getPluginIcon(context, plugin);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, "plugin_" + plugin.pluginId)
                .setShortLabel(plugin.name)
                .setLongLabel(plugin.description != null ? plugin.description : plugin.name)
                .setIcon(icon)
                .setIntent(intent)
                .build();

        boolean success = shortcutManager.requestPinShortcut(shortcut, null);
        if (success) {
            Toast.makeText(context, R.string.shortcut_created, Toast.LENGTH_SHORT).show();
        }
    }

    private static void createShortcutForOldVersions(Context context, PluginInfo plugin) {
        Intent shortcutIntent = new Intent(context, PluginHostActivity.class);
        shortcutIntent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId);
        shortcutIntent.setAction(Intent.ACTION_VIEW);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, plugin.name);

        IconCompat iconCompat = getPluginIconCompat(context, plugin);
        if (iconCompat != null) {
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconCompat.toIcon(context));
        } else {
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_extension));
        }

        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        context.sendBroadcast(addIntent);
        Toast.makeText(context, R.string.shortcut_created, Toast.LENGTH_SHORT).show();
    }

    private static Icon getPluginIcon(Context context, PluginInfo plugin) {
        try {
            PluginManager pm = PluginManager.getInstance(context);
            File pluginDir = pm.getPluginDirFile(plugin.pluginId);
            if (pluginDir != null) {
                String iconPath = plugin.icon != null && !plugin.icon.isEmpty() ? plugin.icon : "icon.png";
                File iconFile = new File(pluginDir, iconPath);
                if (iconFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                    if (bitmap != null) {
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 72, 72, true);
                        return Icon.createWithBitmap(scaledBitmap);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("PluginShortcutHelper", "获取插件图标失败", e);
        }
        return Icon.createWithResource(context, R.drawable.ic_extension);
    }

    private static IconCompat getPluginIconCompat(Context context, PluginInfo plugin) {
        try {
            PluginManager pm = PluginManager.getInstance(context);
            File pluginDir = pm.getPluginDirFile(plugin.pluginId);
            if (pluginDir != null) {
                String iconPath = plugin.icon != null && !plugin.icon.isEmpty() ? plugin.icon : "icon.png";
                File iconFile = new File(pluginDir, iconPath);
                if (iconFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                    if (bitmap != null) {
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 72, 72, true);
                        return IconCompat.createWithBitmap(scaledBitmap);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("PluginShortcutHelper", "获取插件图标失败", e);
        }
        return null;
    }

    public static void removeShortcut(Context context, PluginInfo plugin) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
            if (shortcutManager != null) {
                shortcutManager.removeDynamicShortcuts(List.of("plugin_" + plugin.pluginId));
                Toast.makeText(context, R.string.shortcut_removed, Toast.LENGTH_SHORT).show();
            }
        } else {
            Intent removeIntent = new Intent();
            removeIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT,
                    new Intent(context, PluginHostActivity.class));
            removeIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, plugin.name);
            removeIntent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT");
            context.sendBroadcast(removeIntent);
            Toast.makeText(context, R.string.shortcut_removed, Toast.LENGTH_SHORT).show();
        }
    }
}