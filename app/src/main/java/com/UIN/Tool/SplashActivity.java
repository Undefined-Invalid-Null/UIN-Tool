package com.UIN.Tool;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.UIN.Tool.ui.onboarding.OnboardingActivity;

public class SplashActivity extends AppCompatActivity {

    private static final String PREF_NAME = "app_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_LAST_VERSION = "last_version";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        TextView tvVersion = findViewById(R.id.tv_version);
        tvVersion.setText(getString(R.string.app_version_title, getVersionName()));
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            navigateToNext();
        }, 1500);
    }
    
    private void navigateToNext() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
        
        String lastVersion = prefs.getString(KEY_LAST_VERSION, "");
        String currentVersion = getVersionName();
        boolean isVersionUpdated = !lastVersion.equals(currentVersion) && !isFirstLaunch;
        
        prefs.edit()
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .putString(KEY_LAST_VERSION, currentVersion)
            .apply();
        
        Intent intent;
        if (isFirstLaunch || isVersionUpdated) {
            intent = new Intent(this, OnboardingActivity.class);
            intent.putExtra("is_version_update", isVersionUpdated);
            intent.putExtra("version_name", currentVersion);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        
        startActivity(intent);
        finish();
    }
    
    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}