package com.UIN.Tool.plugin;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;

/**
 * 插件上下文 - Stub 版本
 * 对应: plugin/PluginContext.kt
 */
public class PluginContext extends ContextWrapper {

    public PluginContext(Context base, String pluginDir) {
        super(base);
    }

    public String getPluginDir() {
        return "";
    }

    public String getPluginFilePath(String relativePath) {
        return "";
    }

    public boolean hasPluginFile(String relativePath) {
        return false;
    }

    @Override
    public AssetManager getAssets() {
        return super.getAssets();
    }

    @Override
    public Resources getResources() {
        return super.getResources();
    }

    @Override
    public Object getSystemService(String name) {
        return super.getSystemService(name);
    }
}