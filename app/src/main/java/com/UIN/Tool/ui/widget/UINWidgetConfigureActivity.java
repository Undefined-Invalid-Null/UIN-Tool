package com.UIN.Tool.ui.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityWidgetConfigureBinding;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.utils.LogUtils;  // 添加这个 import
import com.UIN.Tool.widget.UINWidgetProvider;
import com.UIN.Tool.widget.WidgetConfig;

import java.util.ArrayList;
import java.util.List;

public class UINWidgetConfigureActivity extends BaseActivity {

    private ActivityWidgetConfigureBinding binding;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private List<PluginInfo> plugins = new ArrayList<>();
    private List<String> pluginNames = new ArrayList<>();

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_widget_configure;
    }

    @Override
    protected void initViews() {
        binding = ActivityWidgetConfigureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.widget_config_title), true);
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
        binding.btnConfirm.setOnClickListener(v -> saveWidgetConfig());
        binding.btnSkip.setOnClickListener(v -> skipAndFinish());
    }

    private void loadPlugins() {
        try {
            PluginManager pm = PluginManager.getInstance(this);
            plugins = pm.getInstalledPlugins();

            pluginNames.clear();
            pluginNames.add(getString(R.string.widget_no_plugin));
            for (PluginInfo info : plugins) {
                pluginNames.add(info.name);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, pluginNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            binding.spinnerPlugin1.setAdapter(adapter);
            binding.spinnerPlugin2.setAdapter(adapter);
            binding.spinnerPlugin3.setAdapter(adapter);

        } catch (Exception e) {
            LogUtils.e("UINWidgetConfigure", "加载插件失败", e);
            showToast(getString(R.string.widget_load_plugins_failed));
        }
    }

    private void loadSavedConfig() {
        WidgetConfig config = WidgetConfig.load(this, appWidgetId);
        if (config != null) {
            setSpinnerSelection(binding.spinnerPlugin1, config.pluginId1);
            setSpinnerSelection(binding.spinnerPlugin2, config.pluginId2);
            setSpinnerSelection(binding.spinnerPlugin3, config.pluginId3);
        }
    }

    private void setSpinnerSelection(Spinner spinner, String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            spinner.setSelection(0);
            return;
        }

        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i).pluginId.equals(pluginId)) {
                spinner.setSelection(i + 1);
                return;
            }
        }
        spinner.setSelection(0);
    }

    private void saveWidgetConfig() {
        int pos1 = binding.spinnerPlugin1.getSelectedItemPosition();
        int pos2 = binding.spinnerPlugin2.getSelectedItemPosition();
        int pos3 = binding.spinnerPlugin3.getSelectedItemPosition();

        String id1 = (pos1 > 0 && pos1 <= plugins.size()) ? plugins.get(pos1 - 1).pluginId : "";
        String id2 = (pos2 > 0 && pos2 <= plugins.size()) ? plugins.get(pos2 - 1).pluginId : "";
        String id3 = (pos3 > 0 && pos3 <= plugins.size()) ? plugins.get(pos3 - 1).pluginId : "";

        WidgetConfig config = new WidgetConfig(id1, id2, id3);
        config.save(this, appWidgetId);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        UINWidgetProvider.updateAppWidgetRemoteViews(this, appWidgetManager, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);

        showToast(getString(R.string.widget_config_saved));
        finish();
    }

    private void skipAndFinish() {
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}