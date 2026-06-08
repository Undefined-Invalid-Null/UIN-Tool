package com.UIN.Tool.ui.common;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.UIConfig;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

public abstract class BaseActivity extends AppCompatActivity {

    protected UIConfig uiConfig;
    protected Toolbar toolbar;
    protected BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 先初始化 uiConfig
        uiConfig = UIConfig.getInstance(this);
        uiConfig.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResourceId());
        initViews();
        initData();
        setupListeners();
        applyThemeToComponents();
    }

    protected abstract int getLayoutResourceId();

    protected void initViews() {}

    protected void initData() {}

    protected void setupListeners() {}

    protected void setupToolbar(Toolbar toolbar, String title, boolean showBack) {
        this.toolbar = toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            if (showBack) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
        if (uiConfig != null && toolbar != null) {
            uiConfig.applyToolbarTheme(toolbar);
        }
    }
    
    protected void setupBottomNavigation(BottomNavigationView bottomNav) {
        this.bottomNavigationView = bottomNav;
        if (uiConfig != null && bottomNav != null) {
            uiConfig.applyBottomNavigationTheme(bottomNav);
        }
    }
    
    protected void applyThemeToComponents() {
        if (uiConfig == null) return;
        
        if (toolbar != null) {
            uiConfig.applyToolbarTheme(toolbar);
        }
        if (bottomNavigationView != null) {
            uiConfig.applyBottomNavigationTheme(bottomNavigationView);
        }
        applyThemeToViewTree(getWindow().getDecorView());
    }
    
    private void applyThemeToViewTree(View view) {
        if (view == null || uiConfig == null) return;
        
        try {
            if (view instanceof Button) {
                uiConfig.applyButtonTheme((Button) view);
            } else if (view instanceof TextView && !(view instanceof Button)) {
                uiConfig.applyTextViewTheme((TextView) view);
            } else if (view instanceof ImageView) {
                uiConfig.applyImageViewTheme((ImageView) view);
            } else if (view instanceof MaterialCardView) {
                uiConfig.applyCardTheme((MaterialCardView) view);
            }
            
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    applyThemeToViewTree(viewGroup.getChildAt(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

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
    
    @Override
    protected void onResume() {
        super.onResume();
        if (uiConfig != null) {
            uiConfig.applyTheme(this);
            applyThemeToComponents();
        }
    }
}