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
import com.UIN.Tool.ui.repo.RepoFragment;
import com.UIN.Tool.ui.tools.ToolsFragment;
import com.UIN.Tool.utils.CrashHandler;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.utils.UIConfig;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FrameLayout fragmentContainer;
    private BottomNavigationView bottomNav;
    private Fragment currentFragment;
    private UIConfig uiConfig;

    private final ActivityResultLauncher<Intent> manageStorageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (hasManageStoragePermission()) {
                    LogUtils.success(TAG, "存储权限已授予");
                    checkAndSetWorkFolder();
                } else {
                    LogUtils.w(TAG, "存储权限被拒绝");
                    Toast.makeText(this, "需要存储权限才能正常使用", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long startTime = System.currentTimeMillis();
        LogUtils.enter(TAG, "onCreate");
        
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
        
        LogUtils.separator(TAG, "应用启动");
        LogUtils.param(TAG, "版本", getVersionCode());
        LogUtils.param(TAG, "Android版本", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        LogUtils.param(TAG, "设备", Build.MANUFACTURER + " " + Build.MODEL);
        LogUtils.param(TAG, "当前主题", uiConfig.getCurrentThemeName());

        fragmentContainer = findViewById(R.id.fragment_container);
        bottomNav = findViewById(R.id.bottom_navigation);

        setupBottomNavigation();
        checkPermissions();

        if (savedInstanceState == null) {
            switchToFragment(new ToolsFragment());
            bottomNav.setSelectedItemId(R.id.nav_tools);
            LogUtils.d(TAG, "初始切换到工具页面");
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            LogUtils.d(TAG, "底部导航点击，itemId: " + itemId);
            
            Fragment newFragment = null;
            if (itemId == R.id.nav_dev) {
                newFragment = new DevFragment();
                LogUtils.action(TAG, "切换界面", "开发");
            } else if (itemId == R.id.nav_tools) {
                newFragment = new ToolsFragment();
                LogUtils.action(TAG, "切换界面", "工具");
            } else if (itemId == R.id.nav_repo) {
                newFragment = new RepoFragment();
                LogUtils.action(TAG, "切换界面", "仓库");
            } else if (itemId == R.id.nav_manage) {
                newFragment = new ManageFragment();
                LogUtils.action(TAG, "切换界面", "管理");
            }
            
            if (newFragment != null) {
                switchToFragment(newFragment);
                return true;
            }
            return false;
        });
        
        LogUtils.exit(TAG, "onCreate", startTime);
    }

    private int getVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            LogUtils.e(TAG, "获取版本号失败", e);
            return 1;
        }
    }

    private void setupBottomNavigation() {
        LogUtils.enter(TAG, "setupBottomNavigation");
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
            
            LogUtils.d(TAG, "底部导航颜色设置成功");
        } catch (Exception e) {
            LogUtils.e(TAG, "设置底部导航颜色失败: " + e.getMessage(), e);
        }
        LogUtils.exit(TAG, "setupBottomNavigation", System.currentTimeMillis());
    }

    private void checkPermissions() {
        LogUtils.enter(TAG, "checkPermissions");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasManageStoragePermission()) {
                LogUtils.w(TAG, "需要管理所有文件权限");
                showPermissionDialog();
            } else {
                LogUtils.success(TAG, "管理所有文件权限已授予");
                checkAndSetWorkFolder();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                LogUtils.w(TAG, "需要存储权限");
                showPermissionDialog();
            } else {
                LogUtils.success(TAG, "存储权限已授予");
                checkAndSetWorkFolder();
            }
        }
        
        LogUtils.exit(TAG, "checkPermissions", System.currentTimeMillis());
    }

    private boolean hasManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private void showPermissionDialog() {
        LogUtils.d(TAG, "显示权限请求对话框");
        new AlertDialog.Builder(this)
                .setTitle("需要存储权限")
                .setMessage("UIN Tool 需要管理所有文件的权限，以便导入/导出插件。\n\n请点击「授权」并允许权限。")
                .setPositiveButton("授权", (dialog, which) -> {
                    LogUtils.action(TAG, "用户点击", "授权");
                    requestManageStoragePermission();
                })
                .setNegativeButton("退出", (dialog, which) -> {
                    LogUtils.action(TAG, "用户点击", "退出");
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void requestManageStoragePermission() {
        LogUtils.i(TAG, "请求存储权限");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            manageStorageLauncher.launch(intent);
        } else {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
    }

    private void checkAndSetWorkFolder() {
        LogUtils.enter(TAG, "checkAndSetWorkFolder");
        try {
            String workFolder = PreferencesUtils.getWorkFolder(this);
            LogUtils.param(TAG, "workFolder", workFolder);
            
            if (workFolder == null || workFolder.isEmpty()) {
                File defaultFolder = new File("/storage/emulated/0/UIN_Tool");
                if (!defaultFolder.exists()) {
                    defaultFolder.mkdirs();
                    LogUtils.file(TAG, "创建工作目录", defaultFolder);
                }
                PreferencesUtils.setWorkFolder(this, defaultFolder.getAbsolutePath());
                LogUtils.success(TAG, "设置默认工作目录: " + defaultFolder.getAbsolutePath());
                Toast.makeText(this, "工作目录: " + defaultFolder.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } else {
                File folder = new File(workFolder);
                if (!folder.exists()) {
                    folder.mkdirs();
                    LogUtils.file(TAG, "创建工作目录", folder);
                }
                LogUtils.i(TAG, "使用已有工作目录: " + workFolder);
            }
        } catch (Exception e) {
            LogUtils.ex(TAG, e);
            Toast.makeText(this, "创建工作目录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        LogUtils.exit(TAG, "checkAndSetWorkFolder", System.currentTimeMillis());
    }

    private void switchToFragment(Fragment fragment) {
        if (currentFragment != null && currentFragment.getClass() == fragment.getClass()) {
            LogUtils.d(TAG, "Fragment 已是当前页面，跳过切换");
            return;
        }
        LogUtils.d(TAG, "切换 Fragment: " + fragment.getClass().getSimpleName());
        currentFragment = fragment;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.d(TAG, "onResume");
        uiConfig.applyTheme(this);
        setupBottomNavigation();
        
        // 刷新底部导航栏选中状态
        if (currentFragment instanceof DevFragment) {
            bottomNav.setSelectedItemId(R.id.nav_dev);
            LogUtils.d(TAG, "选中开发页面");
        } else if (currentFragment instanceof ToolsFragment) {
            bottomNav.setSelectedItemId(R.id.nav_tools);
            LogUtils.d(TAG, "选中工具页面");
        } else if (currentFragment instanceof RepoFragment) {
            bottomNav.setSelectedItemId(R.id.nav_repo);
            LogUtils.d(TAG, "选中仓库页面");
        } else if (currentFragment instanceof ManageFragment) {
            bottomNav.setSelectedItemId(R.id.nav_manage);
            LogUtils.d(TAG, "选中管理页面");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.d(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.i(TAG, "应用退出");
    }
    
    @Override
    public void onBackPressed() {
        LogUtils.d(TAG, "onBackPressed - 将应用移到后台");
        moveTaskToBack(true);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LogUtils.d(TAG, "onRequestPermissionsResult, requestCode: " + requestCode);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                LogUtils.success(TAG, "存储权限已授予");
                checkAndSetWorkFolder();
            } else {
                LogUtils.w(TAG, "存储权限被拒绝");
                Toast.makeText(this, "需要存储权限才能正常使用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}