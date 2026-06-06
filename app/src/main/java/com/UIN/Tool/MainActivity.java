package com.UIN.Tool;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.UIN.Tool.ui.dev.DevFragment;
import com.UIN.Tool.ui.manage.ManageFragment;
import com.UIN.Tool.ui.tools.ToolsFragment;
import com.UIN.Tool.utils.CrashHandler;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.utils.UIConfig;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private FrameLayout fragmentContainer;
    private BottomNavigationView bottomNav;
    private Fragment currentFragment;
    private UIConfig uiConfig;

    private final ActivityResultLauncher<Intent> manageStorageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (hasManageStoragePermission()) {
                    LogUtils.success("MainActivity", "存储权限已授予");
                    checkAndSetWorkFolder();
                } else {
                    LogUtils.w("MainActivity", "存储权限被拒绝");
                    Toast.makeText(this, "需要存储权限才能正常使用", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter("MainActivity", "onCreate");
        
        // 确保应用在最近任务中可见
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(
                getString(R.string.app_name),
                null,
                getColor(R.color.primary)
            ));
        }
        
        uiConfig = UIConfig.getInstance(this);
        uiConfig.applyTheme(this);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CrashHandler.getInstance().init(this);
        LogUtils.init(this);
        
        LogUtils.separator("MainActivity", "应用启动");
        LogUtils.param("MainActivity", "版本", getVersionCode());
        LogUtils.param("MainActivity", "Android版本", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        LogUtils.param("MainActivity", "设备", Build.MANUFACTURER + " " + Build.MODEL);
        LogUtils.param("MainActivity", "当前主题", uiConfig.getCurrentThemeName());

        fragmentContainer = findViewById(R.id.fragment_container);
        bottomNav = findViewById(R.id.bottom_navigation);

        setupBottomNavigation();
        checkPermissions();

        if (savedInstanceState == null) {
            switchToFragment(new ToolsFragment());
            bottomNav.setSelectedItemId(R.id.nav_tools);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment newFragment = null;
            if (itemId == R.id.nav_dev) {
                newFragment = new DevFragment();
                LogUtils.action("MainActivity", "切换界面", "开发");
            } else if (itemId == R.id.nav_tools) {
                newFragment = new ToolsFragment();
                LogUtils.action("MainActivity", "切换界面", "工具");
            } else if (itemId == R.id.nav_manage) {
                newFragment = new ManageFragment();
                LogUtils.action("MainActivity", "切换界面", "管理");
            }
            
            if (newFragment != null) {
                switchToFragment(newFragment);
                return true;
            }
            return false;
        });
        
        LogUtils.exit("MainActivity", "onCreate", startTime);
    }

    private int getVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    private void setupBottomNavigation() {
        try {
            int selectedColor = uiConfig.getPrimaryColor();
            int unselectedColor = uiConfig.getTextHintColor();
            
            android.content.res.ColorStateList colorStateList = new android.content.res.ColorStateList(
                new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                    selectedColor,
                    unselectedColor
                }
            );
            bottomNav.setItemIconTintList(colorStateList);
            bottomNav.setItemTextColor(colorStateList);
            bottomNav.setBackgroundColor(uiConfig.getSurfaceColor());
            
        } catch (Exception e) {
            LogUtils.e("MainActivity", "设置底部导航颜色失败: " + e.getMessage());
        }
    }

    private void checkPermissions() {
        LogUtils.enter("MainActivity", "checkPermissions");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasManageStoragePermission()) {
                LogUtils.w("MainActivity", "需要管理所有文件权限");
                showPermissionDialog();
            } else {
                LogUtils.success("MainActivity", "管理所有文件权限已授予");
                checkAndSetWorkFolder();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                LogUtils.w("MainActivity", "需要存储权限");
                showPermissionDialog();
            } else {
                LogUtils.success("MainActivity", "存储权限已授予");
                checkAndSetWorkFolder();
            }
        }
        
        LogUtils.exit("MainActivity", "checkPermissions", System.currentTimeMillis());
    }

    private boolean hasManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要存储权限")
                .setMessage("UIN Tool 需要管理所有文件的权限，以便导入/导出插件。\n\n请点击「授权」并允许权限。")
                .setPositiveButton("授权", (dialog, which) -> requestManageStoragePermission())
                .setNegativeButton("退出", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void requestManageStoragePermission() {
        LogUtils.i("MainActivity", "请求存储权限");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            manageStorageLauncher.launch(intent);
        } else {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
    }

    private void checkAndSetWorkFolder() {
        try {
            String workFolder = PreferencesUtils.getWorkFolder(this);
            LogUtils.param("MainActivity", "workFolder", workFolder);
            
            if (workFolder == null || workFolder.isEmpty()) {
                File defaultFolder = new File("/storage/emulated/0/UIN_Tool");
                if (!defaultFolder.exists()) {
                    defaultFolder.mkdirs();
                    LogUtils.file("MainActivity", "创建工作目录", defaultFolder);
                }
                PreferencesUtils.setWorkFolder(this, defaultFolder.getAbsolutePath());
                LogUtils.success("MainActivity", "设置默认工作目录: " + defaultFolder.getAbsolutePath());
                Toast.makeText(this, "工作目录: " + defaultFolder.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } else {
                File folder = new File(workFolder);
                if (!folder.exists()) {
                    folder.mkdirs();
                    LogUtils.file("MainActivity", "创建工作目录", folder);
                }
                LogUtils.i("MainActivity", "使用已有工作目录: " + workFolder);
            }
        } catch (Exception e) {
            LogUtils.ex("MainActivity", e);
            Toast.makeText(this, "创建工作目录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void switchToFragment(Fragment fragment) {
        if (currentFragment != null && currentFragment.getClass() == fragment.getClass()) {
            return;
        }
        currentFragment = fragment;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.d("MainActivity", "onResume");
        uiConfig.applyTheme(this);
        setupBottomNavigation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.d("MainActivity", "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.i("MainActivity", "应用退出");
    }
    
    @Override
    public void onBackPressed() {
        // 按返回键时，将应用移到后台而不是销毁
        // 这样可以在最近任务中看到应用
        moveTaskToBack(true);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                LogUtils.success("MainActivity", "存储权限已授予");
                checkAndSetWorkFolder();
            } else {
                LogUtils.w("MainActivity", "存储权限被拒绝");
                Toast.makeText(this, "需要存储权限才能正常使用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}