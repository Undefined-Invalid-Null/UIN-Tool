package com.UIN.Tool.plugin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

/**
 * 所有插件必须实现这个接口
 */
public interface PluginInterface {

    View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState);

    void onResume();

    void onPause();

    void onDestroy();

    boolean onBackPressed();

    Bundle onSaveInstanceState();
    
    /**
     * 转发 Activity 的 onActivityResult 到插件
     */
    default void onActivityResult(int requestCode, int resultCode, Intent data) {}
}