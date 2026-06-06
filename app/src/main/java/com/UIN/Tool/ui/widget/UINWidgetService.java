package com.UIN.Tool.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.utils.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UINWidgetService extends RemoteViewsService {

    private static final String TAG = "UINWidgetService";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        LogUtils.i(TAG, "onGetViewFactory called for widget " + appWidgetId);
        return new WidgetRemoteViewsFactory(getApplicationContext(), appWidgetId);
    }

    static class WidgetRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

        private final Context context;
        private final int appWidgetId;
        private final List<PluginInfo> plugins = new ArrayList<>();
        private PluginManager pluginManager;
        private static final String FACTORY_TAG = "WidgetRemoteViewsFactory";

        WidgetRemoteViewsFactory(Context context, int appWidgetId) {
            this.context = context;
            this.appWidgetId = appWidgetId;
        }

        @Override
        public void onCreate() {
            LogUtils.d(FACTORY_TAG, "onCreate for widget " + appWidgetId);
            pluginManager = PluginManager.getInstance(context);
        }

        @Override
        public void onDataSetChanged() {
            LogUtils.d(FACTORY_TAG, "onDataSetChanged for widget " + appWidgetId);

            plugins.clear();

            if (pluginManager == null) {
                pluginManager = PluginManager.getInstance(context);
            }

            pluginManager.refreshWorkFolder();
            List<PluginInfo> installedPlugins = pluginManager.getInstalledPlugins();

            WidgetConfig config = WidgetConfig.load(context, appWidgetId);
            if (config != null && config.hasSelectedPlugins()) {
                addPluginById(config.pluginId1);
                addPluginById(config.pluginId2);
                addPluginById(config.pluginId3);
            } else {
                plugins.addAll(installedPlugins);
            }

            LogUtils.i(FACTORY_TAG, "Loaded " + plugins.size() + " plugins for widget " + appWidgetId);
        }

        private void addPluginById(String pluginId) {
            if (pluginId != null && !pluginId.isEmpty()) {
                PluginInfo info = pluginManager.getPluginInfo(pluginId);
                if (info != null) {
                    plugins.add(info);
                }
            }
        }

        @Override
        public void onDestroy() {
            LogUtils.d(FACTORY_TAG, "onDestroy for widget " + appWidgetId);
            plugins.clear();
        }

        @Override
        public int getCount() {
            return plugins.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= plugins.size()) {
                return null;
            }

            PluginInfo plugin = plugins.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_list_item);

            views.setTextViewText(R.id.widget_item_text, plugin.name);

            Bitmap icon = getPluginIcon(plugin);
            if (icon != null) {
                views.setImageViewBitmap(R.id.widget_item_icon, icon);
            } else {
                views.setImageViewResource(R.id.widget_item_icon, R.drawable.ic_plugin_default);
            }

            // 关键：fillInIntent 必须设置与模板相同的 action
            Intent fillInIntent = new Intent();
            fillInIntent.setAction(UINWidgetProvider.ACTION_WIDGET_ITEM_CLICKED);
            fillInIntent.putExtra(UINWidgetProvider.EXTRA_PLUGIN_ID, plugin.pluginId);
            views.setOnClickFillInIntent(R.id.widget_item_layout, fillInIntent);

            LogUtils.d(FACTORY_TAG, "Configured view for position " + position + 
                    ", pluginId: " + plugin.pluginId + ", action: " + fillInIntent.getAction());

            return views;
        }

        private Bitmap getPluginIcon(PluginInfo plugin) {
            try {
                File pluginDir = pluginManager.getPluginDirFile(plugin.pluginId);
                if (pluginDir != null && pluginDir.exists()) {
                    String iconPath = plugin.icon != null && !plugin.icon.isEmpty() ? plugin.icon : "icon.png";
                    File iconFile = new File(pluginDir, iconPath);
                    if (iconFile.exists()) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath(), options);
                        if (bitmap != null) {
                            return Bitmap.createScaledBitmap(bitmap, 64, 64, true);
                        }
                    }
                }
            } catch (Exception e) {
                LogUtils.e(FACTORY_TAG, "Failed to load icon: " + e.getMessage());
            }
            return null;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}