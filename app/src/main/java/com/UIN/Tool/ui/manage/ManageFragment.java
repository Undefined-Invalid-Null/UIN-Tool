package com.UIN.Tool.ui.manage;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.FragmentManageBinding;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.backup.BackupManagerActivity;
import com.UIN.Tool.ui.common.BaseFragment;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.ui.docs.DocViewerActivity;
import com.UIN.Tool.ui.log.LogViewerActivity;
import com.UIN.Tool.ui.permission.PermissionManagerActivity;
import com.UIN.Tool.ui.plugin.PluginManageActivity;
import com.UIN.Tool.ui.settings.UIConfigActivity;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.utils.UpdateChecker;
import com.UIN.Tool.utils.UpdateDownloader;
import com.UIN.Tool.widget.UINWidgetProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ManageFragment extends BaseFragment {

    private static final String TAG = "ManageFragment";
    
    private FragmentManageBinding binding;
    private PluginManager pluginManager;
    private UpdateChecker updateChecker;
    private UpdateDownloader updateDownloader;
    
    // 更新相关对话框
    private AlertDialog checkingDialog;
    private AlertDialog versionListDialog;
    private AlertDialog downloadProgressDialog;
    private VersionAdapter versionAdapter;
    private List<UpdateChecker.ReleaseInfo> allReleases = new ArrayList<>();
    
    // 下载进度组件
    private ProgressBar downloadProgressBar;
    private TextView downloadProgressText;
    private TextView downloadSpeedText;
    private Button downloadCancelButton;
    
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
        binding.cardDocs.setOnClickListener(v -> showDocsPanel());
        binding.cardLog.setOnClickListener(v -> startActivity(new Intent(getContext(), LogViewerActivity.class)));
        binding.cardBackup.setOnClickListener(v -> startActivity(new Intent(getContext(), BackupManagerActivity.class)));
        binding.cardUiConfig.setOnClickListener(v -> startActivity(new Intent(getContext(), UIConfigActivity.class)));
        binding.cardWidget.setOnClickListener(v -> showWidgetGuide());
        binding.cardUpdate.setOnClickListener(v -> checkForUpdate());
        
        if (binding.cardDeveloper != null) {
            binding.cardDeveloper.setOnClickListener(v -> showDeveloperOptions());
        }
    }
    
    /**
     * 安全显示 Toast
     */
    private void safeShowToast(final String message) {
        if (!isFragmentAlive || !isAdded() || getActivity() == null) {
            LogUtils.w(TAG, "Fragment 已销毁，无法显示 Toast: " + message);
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isFragmentAlive && getActivity() != null) {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    /**
     * 检查更新
     */
    private void checkForUpdate() {
        if (!isFragmentAlive) return;
        showCheckingDialog();
        
        updateChecker.setOnUpdateCheckListener(new UpdateChecker.OnUpdateCheckListener() {
            @Override
            public void onCheckStart() {
                LogUtils.d(TAG, "开始检查更新");
            }
            
            @Override
            public void onCheckSuccess(List<UpdateChecker.ReleaseInfo> releases, boolean hasNewer) {
                if (!isFragmentAlive) return;
                dismissCheckingDialog();
                allReleases.clear();
                allReleases.addAll(releases);
                showVersionListDialog(hasNewer);
            }
            
            @Override
            public void onCheckFailed(String error) {
                if (!isFragmentAlive) return;
                dismissCheckingDialog();
                safeShowToast("检查更新失败: " + error);
                LogUtils.e(TAG, "检查更新失败: " + error);
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
    
    /**
     * 显示检查中的对话框
     */
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
    
    /**
     * 关闭检查对话框
     */
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
    
    /**
     * 显示版本列表对话框
     */
    private void showVersionListDialog(boolean hasNewer) {
        if (!isFragmentAlive || getActivity() == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_version_list, null);
        
        TextView tvCurrentVersion = dialogView.findViewById(R.id.tv_current_version);
        RecyclerView rvVersions = dialogView.findViewById(R.id.rv_versions);
        TextView tvNoVersions = dialogView.findViewById(R.id.tv_no_versions);
        
        String currentVersion = "当前版本: v" + getCurrentVersionName();
        tvCurrentVersion.setText(currentVersion);
        
        if (allReleases.isEmpty()) {
            tvNoVersions.setVisibility(View.VISIBLE);
            rvVersions.setVisibility(View.GONE);
        } else {
            tvNoVersions.setVisibility(View.GONE);
            rvVersions.setVisibility(View.VISIBLE);
            rvVersions.setLayoutManager(new LinearLayoutManager(requireContext()));
            versionAdapter = new VersionAdapter(allReleases, hasNewer);
            rvVersions.setAdapter(versionAdapter);
        }
        
        versionListDialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("关闭", null)
                .create();
        
        versionListDialog.show();
    }
    
    /**
     * 获取当前版本名
     */
    private String getCurrentVersionName() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }
    
    // ==================== 版本列表适配器 ====================
    
    private class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.ViewHolder> {
        
        private List<UpdateChecker.ReleaseInfo> releases;
        private boolean hasNewer;
        
        VersionAdapter(List<UpdateChecker.ReleaseInfo> releases, boolean hasNewer) {
            this.releases = releases;
            this.hasNewer = hasNewer;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_version, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UpdateChecker.ReleaseInfo release = releases.get(position);
            
            holder.tvVersionName.setText("v" + release.versionName);
            holder.tvVersionCode.setText("版本代码: " + release.versionCode);
            holder.tvReleaseDate.setText("发布日期: " + release.getFormattedDate());
            holder.tvFileSize.setText("文件大小: " + release.apkSize);
            
            if (release.isNewer && hasNewer && position == 0) {
                holder.tvBadge.setVisibility(View.VISIBLE);
                holder.tvBadge.setText("最新");
                holder.tvBadge.setBackgroundColor(getResources().getColor(R.color.success));
            } else if (release.isPreRelease) {
                holder.tvBadge.setVisibility(View.VISIBLE);
                holder.tvBadge.setText("预览版");
                holder.tvBadge.setBackgroundColor(getResources().getColor(R.color.warning));
            } else {
                holder.tvBadge.setVisibility(View.GONE);
            }
            
            if (position == 0 && release.releaseNotes != null && !release.releaseNotes.isEmpty()) {
                holder.tvReleaseNotesPreview.setVisibility(View.VISIBLE);
                String preview = release.releaseNotes.length() > 100 ? 
                        release.releaseNotes.substring(0, 100) + "..." : release.releaseNotes;
                holder.tvReleaseNotesPreview.setText(preview);
            } else {
                holder.tvReleaseNotesPreview.setVisibility(View.GONE);
            }
            
            holder.btnDownload.setOnClickListener(v -> {
                if (versionListDialog != null) {
                    versionListDialog.dismiss();
                }
                showVersionDetailDialog(release);
            });
        }
        
        @Override
        public int getItemCount() {
            return releases.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvVersionName, tvVersionCode, tvBadge, tvReleaseDate, tvFileSize, tvReleaseNotesPreview;
            Button btnDownload;
            
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvVersionName = itemView.findViewById(R.id.tv_version_name);
                tvVersionCode = itemView.findViewById(R.id.tv_version_code);
                tvBadge = itemView.findViewById(R.id.tv_badge);
                tvReleaseDate = itemView.findViewById(R.id.tv_release_date);
                tvFileSize = itemView.findViewById(R.id.tv_file_size);
                tvReleaseNotesPreview = itemView.findViewById(R.id.tv_release_notes_preview);
                btnDownload = itemView.findViewById(R.id.btn_download);
            }
        }
    }
    
    // ==================== 版本详情和下载 ====================
    
    /**
     * 显示版本详情对话框
     */
    private void showVersionDetailDialog(UpdateChecker.ReleaseInfo release) {
        if (!isFragmentAlive || getActivity() == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_update_info, null);
        
        TextView tvVersionInfo = dialogView.findViewById(R.id.tv_version_info);
        TextView tvReleaseDate = dialogView.findViewById(R.id.tv_release_date);
        TextView tvFileSize = dialogView.findViewById(R.id.tv_file_size);
        TextView tvReleaseNotes = dialogView.findViewById(R.id.tv_release_notes);
        Button btnUpdate = dialogView.findViewById(R.id.btn_update);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        tvVersionInfo.setText("版本 " + release.versionName + " (代码: " + release.versionCode + ")");
        tvReleaseDate.setText("发布日期: " + release.getFormattedDate());
        tvFileSize.setText("文件大小: " + release.apkSize);
        
        String releaseNotes = release.releaseNotes;
        if (releaseNotes == null || releaseNotes.isEmpty()) {
            releaseNotes = "暂无更新日志\n\n请访问 GitHub 查看完整信息";
        }
        tvReleaseNotes.setText(releaseNotes);
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        btnUpdate.setOnClickListener(v -> {
            dialog.dismiss();
            startDownload(release);
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    /**
     * 开始下载
     */
    private void startDownload(UpdateChecker.ReleaseInfo release) {
        if (!isFragmentAlive) return;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (getContext() != null && getContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                safeShowToast("请先授予存储权限");
                return;
            }
        }
        
        showDownloadProgressDialog();
        
        updateDownloader.setOnDownloadListener(new UpdateDownloader.OnDownloadListener() {
            @Override
            public void onStart() {
                LogUtils.d(TAG, "开始下载: " + release.versionName);
                if (isFragmentAlive && downloadProgressText != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (downloadProgressText != null) {
                                downloadProgressText.setText("连接中...");
                            }
                        }
                    });
                }
            }
            
            @Override
            public void onProgress(int progress, long downloaded, long total) {
                if (isFragmentAlive) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateDownloadProgress(progress, downloaded, total);
                        }
                    });
                }
            }
            
            @Override
            public void onSuccess(final File file) {
                if (isFragmentAlive) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissDownloadProgressDialog();
                            safeShowToast("下载完成: " + file.getName());
                        }
                    });
                }
            }
            
            @Override
            public void onFailed(final String error) {
                if (isFragmentAlive) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissDownloadProgressDialog();
                            safeShowToast("下载失败: " + error);
                        }
                    });
                }
            }
        });
        
        updateDownloader.startDownload(release.downloadUrl, release.versionName);
    }
    
    /**
     * 显示下载进度对话框
     */
    private void showDownloadProgressDialog() {
        if (!isFragmentAlive || getActivity() == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_download_progress, null);
        downloadProgressBar = dialogView.findViewById(R.id.progress_bar);
        downloadProgressText = dialogView.findViewById(R.id.tv_progress);
        downloadSpeedText = dialogView.findViewById(R.id.tv_speed);
        downloadCancelButton = dialogView.findViewById(R.id.btn_cancel);
        
        downloadProgressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("正在下载")
                .setView(dialogView)
                .setCancelable(false)
                .create();
        
        downloadCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateDownloader.cancelDownload();
                dismissDownloadProgressDialog();
                safeShowToast("已取消下载");
            }
        });
        
        downloadProgressDialog.show();
    }
    
    /**
     * 更新下载进度
     */
    private void updateDownloadProgress(int progress, long downloaded, long total) {
        if (downloadProgressBar != null) {
            downloadProgressBar.setProgress(progress);
        }
        if (downloadProgressText != null) {
            String downloadedStr = formatSize(downloaded);
            String totalStr = formatSize(total);
            downloadProgressText.setText(progress + "% (" + downloadedStr + "/" + totalStr + ")");
        }
        if (downloadSpeedText != null) {
            downloadSpeedText.setText("下载中...");
        }
    }
    
    /**
     * 关闭下载进度对话框
     */
    private void dismissDownloadProgressDialog() {
        if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
            try {
                downloadProgressDialog.dismiss();
            } catch (Exception e) {
                LogUtils.e(TAG, "关闭对话框失败", e);
            }
            downloadProgressDialog = null;
        }
    }
    
    /**
     * 格式化文件大小
     */
    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
    
    // ==================== 其他功能 ====================
    
    private void showDocsPanel() {
        if (!isFragmentAlive) return;
        String[] docs = {
            getString(R.string.doc_type_help),
            getString(R.string.doc_type_dev),
            getString(R.string.doc_type_changelog),
            getString(R.string.doc_type_about),
            getString(R.string.doc_type_contributors)
        };

        DialogHelper.listDialog(requireContext(), getString(R.string.btn_view_docs), docs,
                (dialog, which) -> {
                    Intent intent = new Intent(getContext(), DocViewerActivity.class);
                    switch (which) {
                        case 0:
                            intent.putExtra("doc_type", "help");
                            intent.putExtra("title", getString(R.string.doc_type_help));
                            break;
                        case 1:
                            intent.putExtra("doc_type", "dev");
                            intent.putExtra("title", getString(R.string.doc_type_dev));
                            break;
                        case 2:
                            intent.putExtra("doc_type", "changelog");
                            intent.putExtra("title", getString(R.string.doc_type_changelog));
                            break;
                        case 3:
                            intent.putExtra("doc_type", "about");
                            intent.putExtra("title", getString(R.string.doc_type_about));
                            break;
                        case 4:
                            intent.putExtra("doc_type", "contributors");
                            intent.putExtra("title", getString(R.string.doc_type_contributors));
                            break;
                    }
                    startActivity(intent);
                }).show();
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
        // 在 onPause 时不销毁标记，因为可能还会回来
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentAlive = false;
        dismissCheckingDialog();
        dismissDownloadProgressDialog();
        if (versionListDialog != null && versionListDialog.isShowing()) {
            try {
                versionListDialog.dismiss();
            } catch (Exception e) {
                LogUtils.e(TAG, "关闭版本列表对话框失败", e);
            }
            versionListDialog = null;
        }
        if (updateDownloader != null) {
            updateDownloader.cancelDownload();
        }
        binding = null;
    }
}