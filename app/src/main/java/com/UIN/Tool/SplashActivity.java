package com.UIN.Tool;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.UIN.Tool.ui.onboarding.OnboardingActivity;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.MarkdownRenderer;
import com.UIN.Tool.utils.UpdateChecker;
import com.UIN.Tool.utils.UpdateDownloader;

import java.io.File;
import java.util.List;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final String PREF_NAME = "app_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_LAST_VERSION = "last_version";
    private static final String KEY_IGNORE_VERSION = "ignore_version";
    private static final String GITHUB_RELEASES_URL = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases";
    
    private UpdateChecker updateChecker;
    private UpdateDownloader updateDownloader;
    private boolean hasPendingNavigation = false;
    private AlertDialog progressDialog;
    private AlertDialog updateDialog;
    private boolean isActivityAlive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        isActivityAlive = true;
        
        TextView tvVersion = findViewById(R.id.tv_version);
        tvVersion.setText(getString(R.string.app_version_title, getVersionName()));
        
        updateChecker = new UpdateChecker(this);
        updateDownloader = new UpdateDownloader(this);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isActivityAlive) {
                checkForUpdate();
            }
        }, 500);
    }
    
    private boolean isActivityAlive() {
        return isActivityAlive && !isFinishing() && !isDestroyed();
    }
    
    private void safeShowDialog(AlertDialog dialog) {
        if (dialog != null && isActivityAlive()) {
            try {
                dialog.show();
            } catch (Exception e) {
                LogUtils.e(TAG, "显示 Dialog 失败: " + e.getMessage());
            }
        }
    }
    
    private void safeDismissDialog(AlertDialog dialog) {
        if (dialog != null && dialog.isShowing() && !isFinishing() && !isDestroyed()) {
            try {
                dialog.dismiss();
            } catch (Exception e) {
                // 忽略
            }
        }
    }
    
    private void checkForUpdate() {
        LogUtils.enter(TAG, "checkForUpdate");
        
        updateChecker.setOnUpdateCheckListener(new UpdateChecker.OnUpdateCheckListener() {
            @Override
            public void onCheckStart() {
                LogUtils.d(TAG, "开始检查更新");
            }
            
            @Override
            public void onCheckSuccess(List<UpdateChecker.ReleaseInfo> releases, boolean hasNewer, boolean forceUpdate) {
                if (!isActivityAlive()) return;
                
                LogUtils.d(TAG, "检查更新完成, hasNewer=" + hasNewer + ", forceUpdate=" + forceUpdate);
                
                if (hasNewer && releases != null && !releases.isEmpty()) {
                    UpdateChecker.ReleaseInfo latestRelease = releases.get(0);
                    
                    // 强制更新时忽略版本忽略检查
                    if (!forceUpdate && isVersionIgnored(latestRelease.versionName)) {
                        LogUtils.d(TAG, "用户已忽略版本: " + latestRelease.versionName);
                        navigateToNext();
                        return;
                    }
                    
                    showUpdateDialog(latestRelease, forceUpdate);
                } else {
                    navigateToNext();
                }
            }
            
            @Override
            public void onCheckFailed(String error) {
                if (!isActivityAlive()) return;
                LogUtils.e(TAG, "检查更新失败: " + error);
                showManualDownloadOnlyDialog(error);
            }
            
            @Override
            public void onNoUpdate(String currentVersion) {
                if (!isActivityAlive()) return;
                LogUtils.d(TAG, "当前已是最新版本: " + currentVersion);
                navigateToNext();
            }
        });
        
        updateChecker.checkUpdate();
    }
    
    private boolean isVersionIgnored(String versionName) {
        if (TextUtils.isEmpty(versionName)) return false;
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String ignoredVersion = prefs.getString(KEY_IGNORE_VERSION, "");
        return versionName.equals(ignoredVersion);
    }
    
    private void setVersionIgnored(String versionName) {
        if (TextUtils.isEmpty(versionName)) return;
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_IGNORE_VERSION, versionName).apply();
    }
    
    private void showUpdateDialog(UpdateChecker.ReleaseInfo releaseInfo, boolean forceUpdate) {
        LogUtils.enter(TAG, "showUpdateDialog, forceUpdate=" + forceUpdate);
        
        if (!isActivityAlive()) {
            LogUtils.w(TAG, "Activity 已销毁，跳过显示更新 Dialog");
            navigateToNext();
            return;
        }
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update, null);
        
        TextView tvVersionName = dialogView.findViewById(R.id.tv_version_name);
        TextView tvVersionCode = dialogView.findViewById(R.id.tv_version_code);
        TextView tvReleaseDate = dialogView.findViewById(R.id.tv_release_date);
        TextView tvFileSize = dialogView.findViewById(R.id.tv_file_size);
        WebView wvReleaseNotes = dialogView.findViewById(R.id.wv_release_notes);
        ProgressBar progressWebView = dialogView.findViewById(R.id.progress_webview);
        
        Button btnAutoDownload = dialogView.findViewById(R.id.btn_auto_download);
        Button btnManualDownload = dialogView.findViewById(R.id.btn_manual_download);
        Button btnIgnore = dialogView.findViewById(R.id.btn_ignore);
        
        // 强制更新时隐藏忽略按钮
        if (forceUpdate) {
            btnIgnore.setVisibility(View.GONE);
        } else {
            btnIgnore.setVisibility(View.VISIBLE);
        }
        
        tvVersionName.setText("版本号：" + releaseInfo.versionName);
        tvVersionCode.setText("版本代码：" + releaseInfo.versionCode);
        tvReleaseDate.setText("发布日期：" + releaseInfo.getFormattedDate());
        
        if (releaseInfo.apkSize != null && !releaseInfo.apkSize.isEmpty()) {
            tvFileSize.setText("文件大小：" + releaseInfo.apkSize);
        } else {
            tvFileSize.setText("文件大小：未知");
        }
        
        String releaseNotes = releaseInfo.releaseNotes;
        if (releaseNotes == null || releaseNotes.isEmpty()) {
            releaseNotes = "暂无更新日志";
        }
        
        if (progressWebView != null) {
            progressWebView.setVisibility(View.VISIBLE);
        }
        
        configureWebViewForMarkdown(wvReleaseNotes, releaseNotes, progressWebView);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        updateDialog = builder.create();
        
        btnAutoDownload.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "自动下载");
            safeDismissDialog(updateDialog);
            startDownload(releaseInfo);
        });
        
        btnManualDownload.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "手动下载");
            safeDismissDialog(updateDialog);
            openBrowserForDownload(releaseInfo);
        });
        
        btnIgnore.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "暂不更新");
            safeDismissDialog(updateDialog);
            setVersionIgnored(releaseInfo.versionName);
            navigateToNext();
        });
        
        safeShowDialog(updateDialog);
    }
    
    private void configureWebViewForMarkdown(WebView webView, String markdownContent, ProgressBar progressBar) {
        if (webView == null) return;
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        webView.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
        }
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                LogUtils.d(TAG, "WebView 加载完成");
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                LogUtils.e(TAG, "WebView 加载错误: " + description);
                view.loadData("<html><body style='padding:16px;text-align:center;color:#666'><p>无法加载更新日志</p></body></html>", 
                        "text/html", "UTF-8");
            }
        });
        
        String html = MarkdownRenderer.toHtml(markdownContent);
        
        String styledHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body { padding: 8px; margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size: 12px; color: #333; line-height: 1.5; }" +
                "h1 { font-size: 16px; margin: 8px 0; }" +
                "h2 { font-size: 14px; margin: 6px 0; }" +
                "h3 { font-size: 13px; margin: 4px 0; }" +
                "p { margin: 4px 0; }" +
                "ul, ol { margin: 4px 0; padding-left: 20px; }" +
                "li { margin: 2px 0; }" +
                "code { background: #f4f4f4; padding: 2px 4px; border-radius: 4px; font-size: 11px; }" +
                "pre { background: #2d2d2d; padding: 8px; border-radius: 8px; overflow-x: auto; }" +
                "pre code { background: transparent; color: #f8f8f2; }" +
                "blockquote { margin: 8px 0; padding: 4px 12px; border-left: 3px solid #37474F; background: #f5f5f5; }" +
                "a { color: #37474F; text-decoration: none; }" +
                "table { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 11px; }" +
                "th, td { border: 1px solid #ddd; padding: 4px 6px; text-align: left; }" +
                "th { background: #f0f0f0; }" +
                "</style>" +
                "</head><body>" +
                html +
                "</body></html>";
        
        webView.loadDataWithBaseURL("file:///android_asset/", styledHtml, "text/html", "UTF-8", null);
    }
    
    private void showManualDownloadOnlyDialog(String errorMessage) {
        LogUtils.enter(TAG, "showManualDownloadOnlyDialog");
        
        if (!isActivityAlive()) {
            LogUtils.w(TAG, "Activity 已销毁，跳过显示手动下载 Dialog");
            navigateToNext();
            return;
        }
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update_manual, null);
        
        TextView tvError = dialogView.findViewById(R.id.tv_error_message);
        Button btnGoDownload = dialogView.findViewById(R.id.btn_go_download);
        Button btnEnterApp = dialogView.findViewById(R.id.btn_enter_app);
        
        tvError.setText("无法检查更新：" + errorMessage + "\n\n是否前往 GitHub Releases 页面手动下载最新版本？");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        
        btnGoDownload.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "前往 GitHub Releases");
            safeDismissDialog(dialog);
            openBrowser(GITHUB_RELEASES_URL);
        });
        
        btnEnterApp.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "进入应用");
            safeDismissDialog(dialog);
            navigateToNext();
        });
        
        safeShowDialog(dialog);
    }
    
    private void openBrowserForDownload(UpdateChecker.ReleaseInfo releaseInfo) {
        LogUtils.enter(TAG, "openBrowserForDownload");
        
        String releaseUrl = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/" + releaseInfo.tagName;
        openBrowser(releaseUrl);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isActivityAlive()) {
                navigateToNext();
            }
        }, 500);
    }
    
    private void openBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            LogUtils.success(TAG, "打开浏览器: " + url);
        } catch (Exception e) {
            LogUtils.e(TAG, "打开浏览器失败", e);
            Toast.makeText(this, "无法打开浏览器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startDownload(UpdateChecker.ReleaseInfo releaseInfo) {
        LogUtils.enter(TAG, "startDownload");
        
        if (!isActivityAlive()) {
            LogUtils.w(TAG, "Activity 已销毁，跳过下载");
            return;
        }
        
        if (releaseInfo.downloadUrl == null || releaseInfo.downloadUrl.isEmpty()) {
            LogUtils.e(TAG, "下载链接为空");
            Toast.makeText(this, "下载链接无效，请稍后重试", Toast.LENGTH_SHORT).show();
            navigateToNext();
            return;
        }
        
        showProgressDialog();
        
        updateDownloader.setOnDownloadListener(new UpdateDownloader.OnDownloadListener() {
            @Override
            public void onStart() {
                LogUtils.d(TAG, "开始下载");
            }
            
            @Override
            public void onProgress(int progress, long downloaded, long total) {
                if (isActivityAlive()) {
                    updateProgressDialog(progress, downloaded, total);
                }
            }
            
            @Override
            public void onSuccess(File file) {
                LogUtils.success(TAG, "下载成功");
                safeDismissDialog(progressDialog);
                if (isActivityAlive()) {
                    showInstallDialog(releaseInfo, file);
                }
            }
            
            @Override
            public void onFailed(String error) {
                LogUtils.e(TAG, "下载失败: " + error);
                safeDismissDialog(progressDialog);
                if (isActivityAlive()) {
                    Toast.makeText(SplashActivity.this, "下载失败: " + error, Toast.LENGTH_LONG).show();
                    showDownloadFailedDialog(releaseInfo, error);
                }
            }
        });
        
        updateDownloader.startDownload(releaseInfo.downloadUrl, releaseInfo.versionName);
    }
    
    private void showDownloadFailedDialog(UpdateChecker.ReleaseInfo releaseInfo, String error) {
        if (!isActivityAlive()) return;
        
        new AlertDialog.Builder(this)
            .setTitle("下载失败")
            .setMessage("自动下载失败：" + error + "\n\n是否前往 GitHub Releases 页面手动下载？")
            .setPositiveButton("手动下载", (dialog, which) -> {
                openBrowserForDownload(releaseInfo);
            })
            .setNegativeButton("进入应用", (dialog, which) -> {
                navigateToNext();
            })
            .setCancelable(false)
            .show();
    }
    
    private void showProgressDialog() {
        if (!isActivityAlive()) return;
        
        View view = getLayoutInflater().inflate(R.layout.dialog_download_progress, null);
        
        progressDialog = new AlertDialog.Builder(this)
                .setTitle("正在下载更新")
                .setView(view)
                .setCancelable(false)
                .create();
        
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                updateDownloader.cancelDownload();
                safeDismissDialog(progressDialog);
                navigateToNext();
            });
        }
        
        safeShowDialog(progressDialog);
    }
    
    private void updateProgressDialog(int progress, long downloaded, long total) {
        if (progressDialog == null || !progressDialog.isShowing()) return;
        if (!isActivityAlive()) return;
        
        View view = progressDialog.getWindow().getDecorView().findViewById(android.R.id.content);
        if (view == null) return;
        
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        TextView tvProgress = view.findViewById(R.id.tv_progress);
        
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        
        if (tvProgress != null) {
            String downloadedStr = formatSize(downloaded);
            String totalStr = formatSize(total);
            tvProgress.setText(String.format("%d%% (%s/%s)", progress, downloadedStr, totalStr));
        }
    }
    
    private void showInstallDialog(UpdateChecker.ReleaseInfo releaseInfo, File apkFile) {
        if (!isActivityAlive()) return;
        
        new AlertDialog.Builder(this)
            .setTitle("下载完成")
            .setMessage("UIN Tool " + releaseInfo.versionName + " 已下载完成，是否立即安装？\n\n选择\"稍后安装\"将保存到 downloads 目录。")
            .setPositiveButton("立即安装", (dialog, which) -> {
                updateDownloader.installApk(apkFile);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isActivityAlive()) {
                        finish();
                    }
                }, 500);
            })
            .setNegativeButton("稍后安装", (dialog, which) -> {
                Toast.makeText(this, "安装包已保存到 " + apkFile.getParent(), Toast.LENGTH_LONG).show();
                navigateToNext();
            })
            .setCancelable(false)
            .show();
    }
    
    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }
    
    private void navigateToNext() {
        if (hasPendingNavigation) {
            LogUtils.d(TAG, "导航已执行，跳过重复调用");
            return;
        }
        
        if (!isActivityAlive()) {
            LogUtils.w(TAG, "Activity 已销毁，跳过导航");
            return;
        }
        
        hasPendingNavigation = true;
        
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityAlive = false;
        safeDismissDialog(updateDialog);
        safeDismissDialog(progressDialog);
        if (updateDownloader != null) {
            updateDownloader.cancelDownload();
        }
    }
}