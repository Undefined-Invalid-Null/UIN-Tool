package com.UIN.Tool.ui.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.widget.UINWidgetProvider;
import com.UIN.Tool.widget.WidgetConfig;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfigureActivity extends AppCompatActivity {

    private static final String TAG = "WidgetConfigure";
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner spinnerPlugin1;
    private Spinner spinnerPlugin2;
    private Spinner spinnerPlugin3;
    private Button btnConfirm;
    private List<PluginInfo> plugins = new ArrayList<>();
    private List<String> pluginNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_configure);

        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            LogUtils.e(TAG, "无效的 appWidgetId");
            finish();
            return;
        }

        spinnerPlugin1 = findViewById(R.id.spinner_plugin1);
        spinnerPlugin2 = findViewById(R.id.spinner_plugin2);
        spinnerPlugin3 = findViewById(R.id.spinner_plugin3);
        btnConfirm = findViewById(R.id.btn_confirm);

        loadPlugins();

        btnConfirm.setOnClickListener(v -> saveWidgetConfig());
    }

    private void loadPlugins() {
        try {
            PluginManager pm = PluginManager.getInstance(this);
            plugins = pm.getInstalledPlugins();

            pluginNames.clear();
            pluginNames.add("-- 无 --");
            for (PluginInfo info : plugins) {
                pluginNames.add(info.name);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, pluginNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            spinnerPlugin1.setAdapter(adapter);
            spinnerPlugin2.setAdapter(adapter);
            spinnerPlugin3.setAdapter(adapter);

            // 加载保存的配置
            loadSavedConfig();

        } catch (Exception e) {
            LogUtils.e(TAG, "加载插件失败: " + e.getMessage());
            Toast.makeText(this, "加载插件失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedConfig() {
        WidgetConfig config = WidgetConfig.load(this, appWidgetId);
        if (config != null) {
            setSpinnerSelection(spinnerPlugin1, config.pluginId1);
            setSpinnerSelection(spinnerPlugin2, config.pluginId2);
            setSpinnerSelection(spinnerPlugin3, config.pluginId3);
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
        int pos1 = spinnerPlugin1.getSelectedItemPosition();
        int pos2 = spinnerPlugin2.getSelectedItemPosition();
        int pos3 = spinnerPlugin3.getSelectedItemPosition();

        String id1 = (pos1 > 0 && pos1 <= plugins.size()) ? plugins.get(pos1 - 1).pluginId : "";
        String id2 = (pos2 > 0 && pos2 <= plugins.size()) ? plugins.get(pos2 - 1).pluginId : "";
        String id3 = (pos3 > 0 && pos3 <= plugins.size()) ? plugins.get(pos3 - 1).pluginId : "";

        WidgetConfig config = new WidgetConfig(id1, id2, id3);
        config.save(this, appWidgetId);

        // 使用新的 UINWidgetProvider 刷新小部件
        UINWidgetProvider.refreshSingleWidget(this, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);

        Toast.makeText(this, "小部件配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}