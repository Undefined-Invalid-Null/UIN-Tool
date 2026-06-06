package com.UIN.Tool.ui.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityWidgetShortcutConfigureBinding;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.widget.Widget1x1Config;

import java.util.ArrayList;
import java.util.List;

/**
 * 1x1 小部件配置界面。
 * 用户选择一个插件，该小部件将作为该插件的桌面快捷方式。
 */
public class Widget1x1ConfigureActivity extends BaseActivity {

    private ActivityWidgetShortcutConfigureBinding binding;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private List<PluginInfo> plugins = new ArrayList<>();
    private List<String> pluginNames = new ArrayList<>();

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_widget_shortcut_configure;
    }

    @Override
    protected void initViews() {
        binding = ActivityWidgetShortcutConfigureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.widget_1x1_config_title), true);
    }

    @Override
    protected void initData() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        loadPlugins();
        loadSavedConfig();
    }

    @Override
    protected void setupListeners() {
        binding.btnConfirm.setOnClickListener(v -> saveAndFinish());
        binding.btnSkip.setOnClickListener(v -> skipAndFinish());
    }

    private void loadPlugins() {
        try {
            PluginManager pm = PluginManager.getInstance(this);
            plugins = pm.getInstalledPlugins();

            pluginNames.clear();
            pluginNames.add(getString(R.string.widget_1x1_launch_main));
            for (PluginInfo info : plugins) {
                pluginNames.add(info.name);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, pluginNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            binding.spinnerPlugin.setAdapter(adapter);

        } catch (Exception e) {
            showToast(getString(R.string.toast_import_failed, e.getMessage()));
        }
    }

    private void loadSavedConfig() {
        Widget1x1Config config = Widget1x1Config.load(this, appWidgetId);
        if (config != null && config.hasPlugin()) {
            for (int i = 0; i < plugins.size(); i++) {
                if (plugins.get(i).pluginId.equals(config.pluginId)) {
                    binding.spinnerPlugin.setSelection(i + 1);
                    return;
                }
            }
        }
    }

    private void saveAndFinish() {
        int pos = binding.spinnerPlugin.getSelectedItemPosition();
        String pluginId = "";
        if (pos > 0 && pos <= plugins.size()) {
            pluginId = plugins.get(pos - 1).pluginId;
        }

        Widget1x1Config config = new Widget1x1Config(pluginId);
        config.save(this, appWidgetId);

        // 刷新小部件
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        com.UIN.Tool.widget.Widget1x1Provider.updateAppWidget(this, appWidgetManager, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);

        showToast(getString(R.string.widget_1x1_config_saved));
        finish();
    }

    private void skipAndFinish() {
        // 不选择插件，保持默认（打开主界面）
        Widget1x1Config config = new Widget1x1Config("");
        config.save(this, appWidgetId);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        com.UIN.Tool.widget.Widget1x1Provider.updateAppWidget(this, appWidgetManager, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
