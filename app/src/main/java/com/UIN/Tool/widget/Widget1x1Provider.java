package com.UIN.Tool.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.UIN.Tool.MainActivity;
import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginHostActivity;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.utils.LogUtils;

import java.io.File;

/**
 * 1x1 桌面小部件提供者。
 * 可绑定一个特定插件作为快捷方式，点击直接启动该插件；
 * 未选择插件时，点击打开 UIN Tool 主界面。
 */
public class Widget1x1Provider extends AppWidgetProvider {

    private static final String TAG = "Widget1x1Provider";

    public static final String ACTION_REFRESH_WIDGET_1x1 = "com.UIN.Tool.REFRESH_WIDGET_1x1";

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        LogUtils.i(TAG, "1x1 Widget provider enabled");
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        LogUtils.i(TAG, "onUpdate: " + (appWidgetIds != null ? appWidgetIds.length : 0) + " widgets");

        if (appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(@NonNull Context context,
                                        @NonNull AppWidgetManager appWidgetManager,
                                        int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            LogUtils.e(TAG, "updateAppWidget: Invalid appWidgetId");
            return;
        }

        LogUtils.d(TAG, "updateAppWidget: Updating 1x1 widget " + appWidgetId);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout_1x1);

        // 读取配置，检查是否绑定了插件
        Widget1x1Config config = Widget1x1Config.load(context, appWidgetId);
        boolean hasPlugin = config != null && config.hasPlugin();

        if (hasPlugin) {
            // 绑定了特定插件 → 显示插件图标和名称，点击启动该插件
            PluginManager pm = PluginManager.getInstance(context);
            PluginInfo info = pm.getPluginInfo(config.pluginId);

            if (info != null) {
                views.setTextViewText(R.id.widget_1x1_label, info.name);
                Bitmap icon = loadPluginIcon(context, pm, info);
                if (icon != null) {
                    views.setImageViewBitmap(R.id.widget_1x1_icon, icon);
                } else {
                    views.setImageViewResource(R.id.widget_1x1_icon, R.drawable.ic_extension);
                }

                // 点击 → 直接启动该插件
                Intent pluginIntent = new Intent(context, PluginHostActivity.class);
                pluginIntent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, info.pluginId);
                pluginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                pluginIntent.setData(Uri.parse("widget1x1://plugin/" + appWidgetId));

                PendingIntent pendingIntent = PendingIntent.getActivity(context,
                        appWidgetId, pluginIntent, getPendingIntentFlags());
                views.setOnClickPendingIntent(R.id.widget_1x1_root, pendingIntent);

                LogUtils.i(TAG, "Widget " + appWidgetId + " bound to plugin: " + info.name);
            } else {
                // 插件已被卸载 → 回退到主界面
                LogUtils.w(TAG, "Widget " + appWidgetId + " plugin not found: " + config.pluginId);
                setupMainAppShortcut(context, views, appWidgetId);
            }
        } else {
            // 未绑定插件 → 显示应用图标和名称，点击打开主界面
            setupMainAppShortcut(context, views, appWidgetId);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
        LogUtils.success(TAG, "1x1 Widget " + appWidgetId + " updated successfully");
    }

    /**
     * 设置为打开主界面的快捷方式（默认行为）。
     */
    private static void setupMainAppShortcut(@NonNull Context context,
                                              @NonNull RemoteViews views,
                                              int appWidgetId) {
        views.setTextViewText(R.id.widget_1x1_label, context.getString(R.string.app_name));
        views.setImageViewResource(R.id.widget_1x1_icon, R.drawable.ic_launcher_foreground);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.setData(Uri.parse("widget1x1://open/" + appWidgetId));

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                appWidgetId, openIntent, getPendingIntentFlags());
        views.setOnClickPendingIntent(R.id.widget_1x1_root, pendingIntent);
    }

    /**
     * 加载插件图标，缩放到小部件尺寸。
     */
    private static Bitmap loadPluginIcon(Context context, PluginManager pm, PluginInfo plugin) {
        try {
            File pluginDir = pm.getPluginDirFile(plugin.pluginId);
            if (pluginDir != null && pluginDir.exists()) {
                String iconPath = plugin.icon != null && !plugin.icon.isEmpty()
                        ? plugin.icon : "icon.png";
                File iconFile = new File(pluginDir, iconPath);
                if (iconFile.exists()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath(), options);
                    if (bitmap != null) {
                        return Bitmap.createScaledBitmap(bitmap, 48, 48, true);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to load plugin icon: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;

        LogUtils.d(TAG, "onReceive called with action: " + action);

        if (action == null) {
            super.onReceive(context, intent);
            return;
        }

        if (ACTION_REFRESH_WIDGET_1x1.equals(action)) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            LogUtils.d(TAG, "Refresh 1x1 widget: " + appWidgetId);

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                refreshSingleWidget(context, appWidgetId);
            } else {
                refreshAllWidgets(context);
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    public static void refreshAllWidgets(Context context) {
        LogUtils.i(TAG, "refreshAllWidgets called");

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, Widget1x1Provider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

        if (appWidgetIds != null && appWidgetIds.length > 0) {
            for (int appWidgetId : appWidgetIds) {
                refreshSingleWidget(context, appWidgetId);
            }
        }
    }

    public static void refreshSingleWidget(Context context, int appWidgetId) {
        LogUtils.d(TAG, "refreshSingleWidget called for 1x1 widget " + appWidgetId);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    public static void sendRefreshIntent(Context context) {
        LogUtils.i(TAG, "sendRefreshIntent called");

        Intent intent = new Intent(ACTION_REFRESH_WIDGET_1x1);
        intent.setClass(context, Widget1x1Provider.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        context.sendBroadcast(intent);
    }

    private static int getPendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        if (appWidgetIds != null) {
            for (int appWidgetId : appWidgetIds) {
                Widget1x1Config.delete(context, appWidgetId);
            }
            LogUtils.i(TAG, "1x1 Widgets deleted: " + appWidgetIds.length);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        LogUtils.i(TAG, "1x1 Widget provider disabled");
    }
}
