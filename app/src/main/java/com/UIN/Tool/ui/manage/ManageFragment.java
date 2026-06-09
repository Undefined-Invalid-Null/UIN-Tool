package com.UIN.Tool.ui.manage;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.FragmentManageBinding;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.backup.BackupManagerActivity;
import com.UIN.Tool.ui.common.BaseFragment;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.ui.docs.DocBrowserActivity;
import com.UIN.Tool.ui.log.LogViewerActivity;
import com.UIN.Tool.ui.permission.PermissionManagerActivity;
import com.UIN.Tool.ui.plugin.PluginManageActivity;
import com.UIN.Tool.ui.settings.UIConfigActivity;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.MarkdownRenderer;
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.utils.UpdateChecker;
import com.UIN.Tool.utils.UpdateDownloader;
import com.UIN.Tool.widget.UINWidgetProvider;
import com.UIN.Tool.widget.Widget1x1Provider;

import java.io.File;
import java.util.List;

public class ManageFragment extends BaseFragment {

    private static final String TAG = "ManageFragment";
    private static final String GITHUB_RELEASES_URL = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases";
    private static final String PREF_NAME = "app_prefs";
    private static final String KEY_IGNORE_VERSION = "ignore_version";
    
    private FragmentManageBinding binding;
    private PluginManager pluginManager;
    private UpdateChecker updateChecker;
    private UpdateDownloader updateDownloader;
    
    private AlertDialog checkingDialog;
    private AlertDialog downloadProgressDialog;
    private AlertDialog updateDialog;
    
    private Handler mainHandler;
    private boolean isFragmentAlive = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentManageBinding.inflate(inflater, container, false);
        mainHandler = new Handler(Looper.getMainLooper());
        isFragmentAlive = true;
        return binding.getRoot();
    }

    @Override
    protected void initViews(View view) {
        // binding already initialized
    }

    @Override
    protected void initData() {
        if (getContext() != null) {
            pluginManager = PluginManager.getInstance(getContext());
            updateChecker = new UpdateChecker(requireContext());
            updateDownloader = new UpdateDownloader(requireContext());
        }
    }

    @Override
    protected void setupListeners() {
        if (binding == null) return;
        
        binding.cardPluginManage.setOnClickListener(v -> startActivity(new Intent(getContext(), PluginManageActivity.class)));
        binding.cardPermission.setOnClickListener(v -> startActivity(new Intent(getContext(), PermissionManagerActivity.class)));
        binding.cardDocCenter.setOnClickListener(v -> startActivity(new Intent(getContext(), DocBrowserActivity.class)));
        binding.cardLog.setOnClickListener(v -> startActivity(new Intent(getContext(), LogViewerActivity.class)));
        binding.cardBackup.setOnClickListener(v -> startActivity(new Intent(getContext(), BackupManagerActivity.class)));
        binding.cardUiConfig.setOnClickListener(v -> startActivity(new Intent(getContext(), UIConfigActivity.class)));
        binding.cardWidget.setOnClickListener(v -> showWidgetGuide());
        binding.cardUpdate.setOnClickListener(v -> checkForUpdate());
        binding.cardGithubAccelerate.setOnClickListener(v -> {
            try {
                Class<?> clazz = Class.forName("com.UIN.Tool.ui.manage.GitHubMirrorActivity");
                startActivity(new Intent(getContext(), clazz));
            } catch (ClassNotFoundException e) {
                LogUtils.e(TAG, "GitHubMirrorActivity not found", e);
                showToast("GitHub 加速功能开发中");
            }
        });
        
        if (binding.cardDeveloper != null) {
            binding.cardDeveloper.setOnClickListener(v -> showDeveloperOptions());
        }
    }
    
    // ==================== 更新相关方法 ====================
    
    public void checkUpdateFromShortcut() {
        if (!isFragmentAlive) return;
        LogUtils.enter(TAG, "checkUpdateFromShortcut");
        checkForUpdate();
    }
    
    private void checkForUpdate() {
        if (!isFragmentAlive || getActivity() == null) {
            LogUtils.w(TAG, "Fragment 未附加，无法检查更新");
            return;
        }
        
        LogUtils.enter(TAG, "checkForUpdate");
        
        showCheckingDialog();
        
        updateChecker.setOnUpdateCheckListener(new UpdateChecker.OnUpdateCheckListener() {
            @Override
            public void onCheckStart() {
                LogUtils.d(TAG, "开始检查更新");
            }
            
            @Override
            public void onCheckSuccess(List<UpdateChecker.ReleaseInfo> releases, boolean hasNewer, boolean forceUpdate) {
                if (!isFragmentAlive) return;
                dismissCheckingDialog();
                
                if (hasNewer && !releases.isEmpty()) {
                    UpdateChecker.ReleaseInfo latest = releases.get(0);
                    
                    // 强制更新时忽略版本忽略检查
                    if (!forceUpdate && isVersionIgnored(latest.versionName)) {
                        LogUtils.d(TAG, "用户已忽略版本: " + latest.versionName);
                        return;
                    }
                    
                    LogUtils.success(TAG, "发现新版本: " + latest.versionName + ", 强制更新: " + forceUpdate);
                    showUpdateDialog(latest, forceUpdate);
                } else {
                    safeShowToast("当前已是最新版本");
                }
            }
            
            @Override
            public void onCheckFailed(String error) {
                if (!isFragmentAlive) return;
                dismissCheckingDialog();
                showManualDownloadOnlyDialog(error);
            }
            
            @Override
            public void onNoUpdate(String currentVersion) {
                if (!isFragmentAlive) return;
                dismissCheckingDialog();
                safeShowToast("当前已是最新版本 (v" + currentVersion + ")");
            }
        });
        
        updateChecker.checkUpdate();
    }
    
    private boolean isVersionIgnored(String versionName) {
        if (versionName == null || versionName.isEmpty()) return false;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
        String ignoredVersion = prefs.getString(KEY_IGNORE_VERSION, "");
        return versionName.equals(ignoredVersion);
    }
    
    private void showUpdateDialog(UpdateChecker.ReleaseInfo releaseInfo, boolean forceUpdate) {
        if (!isFragmentAlive || getActivity() == null) return;
        
        LogUtils.enter(TAG, "showUpdateDialog, forceUpdate=" + forceUpdate);
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_update, null);
        
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
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        updateDialog = builder.create();
        
        btnAutoDownload.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "自动下载");
            updateDialog.dismiss();
            startDownload(releaseInfo);
        });
        
        btnManualDownload.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "手动下载");
            updateDialog.dismiss();
            openBrowserForDownload(releaseInfo);
        });
        
        btnIgnore.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "暂不更新");
            SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_IGNORE_VERSION, releaseInfo.versionName).apply();
            updateDialog.dismiss();
        });
        
        updateDialog.show();
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
        
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
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
        if (!isFragmentAlive || getActivity() == null) return;
        
        LogUtils.enter(TAG, "showManualDownloadOnlyDialog");
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_update_manual, null);
        
        TextView tvError = dialogView.findViewById(R.id.tv_error_message);
        Button btnGoDownload = dialogView.findViewById(R.id.btn_go_download);
        Button btnEnterApp = dialogView.findViewById(R.id.btn_enter_app);
        
        tvError.setText("无法检查更新：" + errorMessage + "\n\n是否前往 GitHub Releases 页面手动下载最新版本？");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        
        btnGoDownload.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "前往 GitHub Releases");
            dialog.dismiss();
            openBrowser(GITHUB_RELEASES_URL);
        });
        
        btnEnterApp.setOnClickListener(v -> {
            LogUtils.action(TAG, "用户选择", "取消");
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void openBrowserForDownload(UpdateChecker.ReleaseInfo releaseInfo) {
        if (!isFragmentAlive || getActivity() == null) return;
        
        LogUtils.enter(TAG, "openBrowserForDownload");
        
        String releaseUrl = "https://github.com/Undefined-Invalid-Null/UIN-Tool/releases/tag/" + releaseInfo.tagName;
        openBrowser(releaseUrl);
    }
    
    private void openBrowser(String url) {
        if (!isFragmentAlive || getActivity() == null) return;
        
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            LogUtils.success(TAG, "打开浏览器: " + url);
        } catch (Exception e) {
            LogUtils.e(TAG, "打开浏览器失败", e);
            safeShowToast("无法打开浏览器: " + e.getMessage());
        }
    }
    
    private void startDownload(UpdateChecker.ReleaseInfo releaseInfo) {
        if (!isFragmentAlive || getActivity() == null) {
            LogUtils.w(TAG, "Fragment 未附加，无法下载");
            return;
        }
        
        LogUtils.enter(TAG, "startDownload");
        
        if (releaseInfo.downloadUrl == null || releaseInfo.downloadUrl.isEmpty()) {
            LogUtils.e(TAG, "下载链接为空");
            safeShowToast("下载链接无效，请稍后重试");
            return;
        }
        
        showDownloadProgressDialog();
        
        updateDownloader.setOnDownloadListener(new UpdateDownloader.OnDownloadListener() {
            @Override
            public void onStart() {
                LogUtils.d(TAG, "开始下载");
            }
            
            @Override
            public void onProgress(int progress, long downloaded, long total) {
                updateDownloadProgress(progress, downloaded, total);
            }
            
            @Override
            public void onSuccess(File file) {
                LogUtils.success(TAG, "下载成功");
                dismissDownloadProgressDialog();
                showInstallConfirmDialog(releaseInfo, file);
            }
            
            @Override
            public void onFailed(String error) {
                LogUtils.e(TAG, "下载失败: " + error);
                dismissDownloadProgressDialog();
                showDownloadFailedDialog(releaseInfo, error);
            }
        });
        
        updateDownloader.startDownload(releaseInfo.downloadUrl, releaseInfo.versionName);
    }
    
    private void showDownloadFailedDialog(UpdateChecker.ReleaseInfo releaseInfo, String error) {
        if (!isFragmentAlive || getActivity() == null) return;
        
        new AlertDialog.Builder(requireContext())
            .setTitle("下载失败")
            .setMessage("自动下载失败：" + error + "\n\n是否前往 GitHub Releases 页面手动下载？")
            .setPositiveButton("手动下载", (dialog, which) -> {
                openBrowserForDownload(releaseInfo);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void showDownloadProgressDialog() {
        if (!isFragmentAlive || getActivity() == null) return;
        
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_download_progress, null);
        
        downloadProgressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("正在下载更新")
                .setView(view)
                .setCancelable(false)
                .create();
        
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                updateDownloader.cancelDownload();
                dismissDownloadProgressDialog();
                safeShowToast("下载已取消");
            });
        }
        
        downloadProgressDialog.show();
    }
    
    private void updateDownloadProgress(int progress, long downloaded, long total) {
        if (downloadProgressDialog == null || !downloadProgressDialog.isShowing()) return;
        
        View view = downloadProgressDialog.getWindow().getDecorView().findViewById(android.R.id.content);
        if (view == null) return;
        
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        TextView tvProgress = view.findViewById(R.id.tv_progress);
        
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        
        if (tvProgress != null) {
            String downloadedStr = formatFileSize(downloaded);
            String totalStr = formatFileSize(total);
            tvProgress.setText(String.format("%d%% (%s/%s)", progress, downloadedStr, totalStr));
        }
    }
    
    private void dismissDownloadProgressDialog() {
        if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
            downloadProgressDialog.dismiss();
            downloadProgressDialog = null;
        }
    }
    
    private void showInstallConfirmDialog(UpdateChecker.ReleaseInfo releaseInfo, File apkFile) {
        if (!isFragmentAlive || getActivity() == null) return;
        
        new AlertDialog.Builder(requireContext())
            .setTitle("下载完成")
            .setMessage("UIN Tool " + releaseInfo.versionName + " 已下载完成，是否立即安装？\n\n选择\"稍后安装\"将保存到 downloads 目录。")
            .setPositiveButton("立即安装", (dialog, which) -> {
                LogUtils.action(TAG, "用户选择", "立即安装");
                updateDownloader.installApk(apkFile);
            })
            .setNegativeButton("稍后安装", (dialog, which) -> {
                LogUtils.action(TAG, "用户选择", "稍后安装");
                safeShowToast("安装包已保存到 " + apkFile.getParent());
            })
            .setCancelable(false)
            .show();
    }
    
    private void showCheckingDialog() {
        if (!isFragmentAlive || getActivity() == null) return;
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_checking_update, null);
        
        checkingDialog = new AlertDialog.Builder(requireContext())
                .setTitle("检查更新")
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        checkingDialog.show();
    }
    
    private void dismissCheckingDialog() {
        if (checkingDialog != null && checkingDialog.isShowing()) {
            try {
                checkingDialog.dismiss();
            } catch (Exception e) {
                LogUtils.e(TAG, "关闭对话框失败", e);
            }
            checkingDialog = null;
        }
    }
    
    // ==================== 其他方法 ====================
    
    private void safeShowToast(final String message) {
        if (!isFragmentAlive || !isAdded() || getActivity() == null) {
            LogUtils.w(TAG, "Fragment 已销毁，无法显示 Toast: " + message);
            return;
        }
        mainHandler.post(() -> {
            if (isFragmentAlive && getActivity() != null) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }
    
    private void showWidgetGuide() {
        if (!isFragmentAlive) return;
        DialogHelper.infoDialog(requireContext(), getString(R.string.widget_guide_title),
                getString(R.string.widget_guide_message))
                .setNeutralButton(R.string.refresh, (dialog, which) -> refreshWidget())
                .show();
    }

    private void refreshWidget() {
        try {
            if (getContext() != null) {
                UINWidgetProvider.sendIntentToRefreshAllWidgets(requireContext());
                Widget1x1Provider.sendRefreshIntent(requireContext());
                safeShowToast(getString(R.string.widget_refresh_sent));
            }
        } catch (Exception e) {
            safeShowToast(getString(R.string.widget_refresh_failed, e.getMessage()));
        }
    }

    private void showDeveloperOptions() {
        if (!isFragmentAlive) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_developer_options, null);
        
        android.widget.CheckBox cbIgnoreSignature = dialogView.findViewById(R.id.cb_ignore_signature);
        cbIgnoreSignature.setChecked(PluginManager.isIgnoreSignatureWarning());
        
        new AlertDialog.Builder(requireContext())
                .setTitle("开发者选项")
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    PluginManager.setIgnoreSignatureWarning(cbIgnoreSignature.isChecked());
                    safeShowToast(cbIgnoreSignature.isChecked() ? "已忽略签名验证" : "已启用签名验证");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        isFragmentAlive = true;
        if (getContext() != null) {
            PreferencesUtils.getWorkFolder(requireContext());
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentAlive = false;
        
        dismissCheckingDialog();
        dismissDownloadProgressDialog();
        
        if (updateDialog != null && updateDialog.isShowing()) {
            updateDialog.dismiss();
            updateDialog = null;
        }
        
        if (updateDownloader != null) {
            updateDownloader.cancelDownload();
        }
        
        binding = null;
    }
}