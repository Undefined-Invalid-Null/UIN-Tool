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

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.FragmentManageBinding;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.backup.BackupManagerActivity;
import com.UIN.Tool.ui.common.BaseFragment;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.ui.docs.DocBrowserActivity;
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
import com.UIN.Tool.widget.Widget1x1Provider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ManageFragment extends BaseFragment {

    private static final String TAG = "ManageFragment";
    
    private FragmentManageBinding binding;
    private PluginManager pluginManager;
    private UpdateChecker updateChecker;
    private UpdateDownloader updateDownloader;
    
    private AlertDialog checkingDialog;
    private AlertDialog downloadProgressDialog;
    
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
        
        if (binding.cardDeveloper != null) {
            binding.cardDeveloper.setOnClickListener(v -> showDeveloperOptions());
        }
    }
    
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
                if (hasNewer && !releases.isEmpty()) {
                    UpdateChecker.ReleaseInfo latest = releases.get(0);
                    safeShowToast("发现新版本: v" + latest.versionName);
                    updateChecker.openReleasePage();
                } else {
                    safeShowToast("当前已是最新版本");
                }
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
        if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
            try {
                downloadProgressDialog.dismiss();
            } catch (Exception e) {
                LogUtils.e(TAG, "关闭对话框失败", e);
            }
            downloadProgressDialog = null;
        }
        if (updateDownloader != null) {
            updateDownloader.cancelDownload();
        }
        binding = null;
    }
}