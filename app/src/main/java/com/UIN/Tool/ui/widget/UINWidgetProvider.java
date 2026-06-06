package com.UIN.Tool.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.LogUtils;

public class UINWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "UINWidgetProvider";

    public static final String ACTION_WIDGET_ITEM_CLICKED = "com.UIN.Tool.WIDGET_ITEM_CLICKED";
    public static final String ACTION_REFRESH_WIDGET = "com.UIN.Tool.REFRESH_WIDGET";
    public static final String EXTRA_PLUGIN_ID = "extra_plugin_id";

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        LogUtils.i(TAG, "Widget provider enabled");
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        LogUtils.i(TAG, "onUpdate: " + (appWidgetIds != null ? appWidgetIds.length : 0) + " widgets");

        if (appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        for (int appWidgetId : appWidgetIds) {
            updateAppWidgetRemoteViews(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidgetRemoteViews(@NonNull Context context,
                                                   @NonNull AppWidgetManager appWidgetManager,
                                                   int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            LogUtils.e(TAG, "updateAppWidgetRemoteViews: Invalid appWidgetId");
            return;
        }

        LogUtils.d(TAG, "updateAppWidgetRemoteViews: Updating widget " + appWidgetId);

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        // 设置空视图
        remoteViews.setEmptyView(R.id.widget_list, R.id.empty_view);

        // 设置刷新按钮点击事件
        Intent refreshIntent = new Intent(context, UINWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH_WIDGET);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        refreshIntent.setData(Uri.parse(refreshIntent.toUri(Intent.URI_INTENT_SCHEME)));

        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId,
                refreshIntent, getPendingIntentFlags());
        remoteViews.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent);

        // 设置列表适配器
        Intent serviceIntent = new Intent(context, UINWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        remoteViews.setRemoteAdapter(R.id.widget_list, serviceIntent);

        // 关键修复：设置 PendingIntent 模板，使用广播接收器
        Intent templateIntent = new Intent(context, UINWidgetProvider.class);
        templateIntent.setAction(ACTION_WIDGET_ITEM_CLICKED);
        templateIntent.setData(Uri.parse("widget://click/" + appWidgetId));
        
        PendingIntent templatePendingIntent = PendingIntent.getBroadcast(context, appWidgetId,
                templateIntent, getPendingIntentFlags());
        remoteViews.setPendingIntentTemplate(R.id.widget_list, templatePendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
        LogUtils.success(TAG, "Widget " + appWidgetId + " updated successfully");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        
        LogUtils.d(TAG, "onReceive called with action: " + action);
        
        if (action == null) {
            super.onReceive(context, intent);
            return;
        }

        if (ACTION_WIDGET_ITEM_CLICKED.equals(action)) {
            // 从 fillInIntent 中获取插件 ID
            String pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID);
            LogUtils.i(TAG, "Widget item clicked, pluginId: " + pluginId);
            
            if (pluginId != null && !pluginId.isEmpty()) {
                // 启动透明桥梁 Activity
                Intent launchIntent = new Intent(context, WidgetClickActivity.class);
                launchIntent.putExtra(EXTRA_PLUGIN_ID, pluginId);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                LogUtils.i(TAG, "Started WidgetClickActivity for plugin: " + pluginId);
            } else {
                LogUtils.e(TAG, "Plugin ID is null");
            }
        } else if (ACTION_REFRESH_WIDGET.equals(action)) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            LogUtils.d(TAG, "Refresh widget: " + appWidgetId);
            
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
        ComponentName componentName = new ComponentName(context, UINWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

        if (appWidgetIds != null && appWidgetIds.length > 0) {
            for (int appWidgetId : appWidgetIds) {
                refreshSingleWidget(context, appWidgetId);
            }
        }
    }

    public static void refreshSingleWidget(Context context, int appWidgetId) {
        LogUtils.d(TAG, "refreshSingleWidget called for widget " + appWidgetId);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateAppWidgetRemoteViews(context, appWidgetManager, appWidgetId);
    }

    public static void sendIntentToRefreshAllWidgets(Context context) {
        LogUtils.i(TAG, "sendIntentToRefreshAllWidgets called");

        Intent intent = new Intent(ACTION_REFRESH_WIDGET);
        intent.setClass(context, UINWidgetProvider.class);
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
                WidgetConfig.delete(context, appWidgetId);
            }
            LogUtils.i(TAG, "Widgets deleted: " + appWidgetIds.length);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        LogUtils.i(TAG, "Widget provider disabled");
    }
}