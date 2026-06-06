package com.UIN.Tool.plugin;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.LayoutInflater;

import com.UIN.Tool.utils.LogUtils;

import java.io.File;
import java.lang.reflect.Method;

public class PluginContext extends ContextWrapper {

    private final String pluginDir;
    private AssetManager pluginAssetManager;
    private Resources pluginResources;
    private LayoutInflater pluginInflater;

    public PluginContext(Context base, String pluginDir) {
        super(base);
        this.pluginDir = pluginDir;
        initPluginResources();
    }

    private void initPluginResources() {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            
            addAssetPath.invoke(assetManager, pluginDir);
            
            File resDir = new File(pluginDir, "res");
            if (resDir.exists()) {
                addAssetPath.invoke(assetManager, resDir.getAbsolutePath());
            }
            
            this.pluginAssetManager = assetManager;
            
            Resources superRes = super.getResources();
            this.pluginResources = new Resources(assetManager, superRes.getDisplayMetrics(), superRes.getConfiguration());
            // 修复：使用 getBaseContext() 而不是 base
            this.pluginInflater = LayoutInflater.from(getBaseContext());
            
            LogUtils.success("PluginContext", "插件资源加载成功: " + pluginDir);
        } catch (Exception e) {
            LogUtils.w("PluginContext", "资源加载失败: " + e.getMessage());
            this.pluginAssetManager = super.getAssets();
            this.pluginResources = super.getResources();
            // 修复：使用 getBaseContext()
            this.pluginInflater = LayoutInflater.from(getBaseContext());
        }
    }

    public String getPluginDir() { 
        return pluginDir; 
    }
    
    public String getPluginFilePath(String relativePath) { 
        return pluginDir + "/" + relativePath; 
    }
    
    public boolean hasPluginFile(String relativePath) { 
        return new File(pluginDir, relativePath).exists(); 
    }

    @Override
    public AssetManager getAssets() { 
        return pluginAssetManager != null ? pluginAssetManager : super.getAssets(); 
    }

    @Override
    public Resources getResources() { 
        return pluginResources != null ? pluginResources : super.getResources(); 
    }
    
    @Override
    public Object getSystemService(String name) {
        if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
            return pluginInflater;
        }
        return super.getSystemService(name);
    }
}