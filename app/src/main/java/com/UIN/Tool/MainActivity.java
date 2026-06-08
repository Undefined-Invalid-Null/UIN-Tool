package com.UIN.Tool;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;
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
    private static final String EXTRA_SELECTED_TAB = "selected_tab";
    private static final String EXTRA_CHECK_UPDATE = "check_update";
    
    private FrameLayout fragmentContainer;
    private BottomNavigationView bottomNav;
    private Fragment currentFragment;
    private UIConfig uiConfig;
    private boolean isFromShortcut = false;

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
        
        // 初始化 UI 配置
        uiConfig = UIConfig.getInstance(this);
        uiConfig.applyTheme(this);
        
        // 设置状态栏颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(uiConfig.getPrimaryDarkColor());
            window.setNavigationBarColor(uiConfig.getSurfaceColor());
        }
        
        // 设置窗口背景色，避免白屏
        getWindow().setBackgroundDrawableResource(R.color.background);
        
        // 确保应用在最近任务中可见 - 确保颜色完全不透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int primaryColor = uiConfig.getPrimaryColor();
            // 移除 Alpha 通道，确保颜色完全不透明
            int opaqueColor = primaryColor | 0xFF000000;
            
            setTaskDescription(new ActivityManager.TaskDescription(
                getString(R.string.app_name),
                null,
                opaqueColor
            ));
        }
        
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
        applyBottomNavigationTheme();

        // 处理快捷方式启动
        handleShortcutIntent(getIntent());

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
        
        // 延迟检查权限，避免影响启动动画
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            checkPermissions();
        }, 1000);
        
        LogUtils.exit(TAG, "onCreate", startTime);
    }
    
    /**
     * 应用主题到底部导航栏
     */
    private void applyBottomNavigationTheme() {
        if (bottomNav != null && uiConfig != null) {
            uiConfig.applyBottomNavigationTheme(bottomNav);
            bottomNav.setBackgroundColor(uiConfig.getSurfaceColor());
        }
    }
    
    /**
     * 处理快捷方式启动
     */
    private void handleShortcutIntent(Intent intent) {
        if (intent == null) return;
        
        String selectedTab = intent.getStringExtra(EXTRA_SELECTED_TAB);
        boolean checkUpdate = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false);
        
        // 兼容 meta-data 方式
        if (selectedTab == null && intent.getExtras() != null) {
            selectedTab = intent.getExtras().getString("selected_tab");
        }
        
        LogUtils.d(TAG, "handleShortcutIntent - selectedTab: " + selectedTab + ", checkUpdate: " + checkUpdate);
        
        if (checkUpdate) {
            switchToCheckUpdatePage();
        } else if ("tools".equals(selectedTab)) {
            switchToToolsPage();
        } else {
            // 默认打开工具页面
            if (currentFragment == null) {
                switchToToolsPage();
            }
        }
    }
    
    /**
     * 切换到工具页面
     */
    private void switchToToolsPage() {
        LogUtils.d(TAG, "切换到工具页面");
        switchToFragment(new ToolsFragment());
        bottomNav.setSelectedItemId(R.id.nav_tools);
        isFromShortcut = true;
    }
    
    /**
     * 切换到检查更新页面
     */
    private void switchToCheckUpdatePage() {
        LogUtils.d(TAG, "切换到管理页面并检查更新");
        ManageFragment manageFragment = new ManageFragment();
        switchToFragment(manageFragment);
        bottomNav.setSelectedItemId(R.id.nav_manage);
        isFromShortcut = true;
        
        // 延迟执行检查更新
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (manageFragment != null && isFragmentVisible(manageFragment)) {
                manageFragment.checkUpdateFromShortcut();
            }
        }, 500);
    }
    
    /**
     * 检查 Fragment 是否可见
     */
    private boolean isFragmentVisible(Fragment fragment) {
        return fragment != null && fragment.isAdded() && !fragment.isHidden() && fragment.getUserVisibleHint();
    }

    /**
     * 处理新 Intent（当应用已在后台时，通过快捷方式再次启动）
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        LogUtils.d(TAG, "onNewIntent");
        
        // 重新处理快捷方式
        handleShortcutIntent(intent);
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
            int selectedColor = uiConfig.getNavItemSelectedColor();
            int unselectedColor = uiConfig.getNavItemUnselectedColor();
            
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
                    finishAffinity();
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
        
        // 重新应用主题
        if (uiConfig != null) {
            uiConfig.applyTheme(this);
            applyBottomNavigationTheme();
            setupBottomNavigation();
            
            // 更新状态栏颜色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(uiConfig.getPrimaryDarkColor());
            }
        }
        
        // 恢复选中的页面
        if (!isFromShortcut) {
            if (currentFragment instanceof DevFragment) {
                bottomNav.setSelectedItemId(R.id.nav_dev);
            } else if (currentFragment instanceof ToolsFragment) {
                bottomNav.setSelectedItemId(R.id.nav_tools);
            } else if (currentFragment instanceof RepoFragment) {
                bottomNav.setSelectedItemId(R.id.nav_repo);
            } else if (currentFragment instanceof ManageFragment) {
                bottomNav.setSelectedItemId(R.id.nav_manage);
            }
        }
        isFromShortcut = false;
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
        LogUtils.d(TAG, "onBackPressed");
        
        // 显示退出确认对话框
        new AlertDialog.Builder(this)
                .setTitle("退出应用")
                .setMessage("确定要退出 UIN Tool 吗？")
                .setPositiveButton("退出", (dialog, which) -> {
                    LogUtils.action(TAG, "用户选择", "退出应用");
                    finishAffinity();
                })
                .setNegativeButton("取消", null)
                .show();
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
                finishAffinity();
            }
        }
    }
}