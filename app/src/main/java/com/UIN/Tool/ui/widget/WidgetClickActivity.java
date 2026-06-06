package com.UIN.Tool.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.UIN.Tool.plugin.PluginHostActivity;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.utils.LogUtils;

public class WidgetClickActivity extends Activity {

    private static final String TAG = "WidgetClickActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LogUtils.i(TAG, "========================================");
        LogUtils.i(TAG, "onCreate called");
        LogUtils.i(TAG, "Intent: " + getIntent());
        LogUtils.i(TAG, "Intent action: " + getIntent().getAction());
        LogUtils.i(TAG, "Intent data: " + getIntent().getData());
        LogUtils.i(TAG, "Intent extras: " + getIntent().getExtras());
        
        // 从 Intent 中获取插件 ID
        String pluginId = getIntent().getStringExtra(UINWidgetProvider.EXTRA_PLUGIN_ID);
        
        LogUtils.i(TAG, "Plugin ID from intent: " + pluginId);
        
        if (pluginId == null || pluginId.isEmpty()) {
            LogUtils.e(TAG, "Plugin ID is null or empty");
            Toast.makeText(this, "无法获取插件ID", Toast.LENGTH_SHORT).show();
            LogUtils.i(TAG, "Finishing activity due to null plugin ID");
            finish();
            return;
        }
        
        LogUtils.i(TAG, "Plugin ID is valid, will execute plugin");
        
        // 延迟启动插件，让当前 Activity 先完成
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            LogUtils.i(TAG, "Handler delayed execution started");
            executePlugin(pluginId);
            LogUtils.i(TAG, "Finishing activity after plugin execution");
            finish();
        }, 50);
    }
    
    private void executePlugin(String pluginId) {
        LogUtils.i(TAG, "executePlugin called with pluginId: " + pluginId);
        
        try {
            LogUtils.i(TAG, "Getting PluginManager instance");
            PluginManager pluginManager = PluginManager.getInstance(this);
            
            LogUtils.i(TAG, "Getting plugin info for: " + pluginId);
            PluginInfo info = pluginManager.getPluginInfo(pluginId);
            
            if (info == null) {
                LogUtils.e(TAG, "Plugin not found: " + pluginId);
                Toast.makeText(this, "插件不存在: " + pluginId, Toast.LENGTH_SHORT).show();
                return;
            }
            
            LogUtils.i(TAG, "Plugin found: " + info.name);
            LogUtils.i(TAG, "Creating intent to start PluginHostActivity");
            
            Intent intent = new Intent(this, PluginHostActivity.class);
            intent.putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, pluginId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            LogUtils.i(TAG, "Starting PluginHostActivity");
            startActivity(intent);
            
            LogUtils.success(TAG, "Plugin started: " + info.name);
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to execute plugin: " + e.getMessage(), e);
            Toast.makeText(this, "启动插件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.d(TAG, "onDestroy called");
        LogUtils.i(TAG, "========================================");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.d(TAG, "onResume called");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.d(TAG, "onPause called");
    }
}