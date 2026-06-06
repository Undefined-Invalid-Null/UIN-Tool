// app/src/main/java/com/UIN/Tool/ui/common/BaseActivity.java
package com.UIN.Tool.ui.common;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.UIConfig;

/**
 * 所有 Activity 的基类
 * 提供统一的主题应用、工具栏设置和通用方法
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected UIConfig uiConfig;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        uiConfig = UIConfig.getInstance(this);
        uiConfig.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResourceId());
        initViews();
        initData();
        setupListeners();
    }

    /**
     * 获取布局资源 ID
     */
    protected abstract int getLayoutResourceId();

    /**
     * 初始化视图
     */
    protected void initViews() {}

    /**
     * 初始化数据
     */
    protected void initData() {}

    /**
     * 设置监听器
     */
    protected void setupListeners() {}

    /**
     * 设置工具栏
     */
    protected void setupToolbar(Toolbar toolbar, String title, boolean showBack) {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            if (showBack) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    /**
     * 显示短提示
     */
    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示长提示
     */
    protected void showLongToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}