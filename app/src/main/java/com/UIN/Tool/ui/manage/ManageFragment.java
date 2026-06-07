package com.UIN.Tool.ui.manage;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.widget.UINWidgetProvider;

public class ManageFragment extends BaseFragment {

    private FragmentManageBinding binding;
    private PluginManager pluginManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentManageBinding.inflate(inflater, container, false);
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
        
        // 开发者选项卡片
        if (binding.cardDeveloper != null) {
            binding.cardDeveloper.setOnClickListener(v -> showDeveloperOptions());
        }
    }

    private void showDocsPanel() {
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
        DialogHelper.infoDialog(requireContext(), getString(R.string.widget_guide_title),
                getString(R.string.widget_guide_message))
                .setNeutralButton(R.string.refresh, (dialog, which) -> refreshWidget())
                .show();
    }

    private void refreshWidget() {
        try {
            if (getContext() != null) {
                UINWidgetProvider.sendIntentToRefreshAllWidgets(requireContext());
                showToast(getString(R.string.widget_refresh_sent));
            }
        } catch (Exception e) {
            showToast(getString(R.string.widget_refresh_failed, e.getMessage()));
        }
    }

    private void showDeveloperOptions() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_developer_options, null);
        
        CheckBox cbIgnoreSignature = dialogView.findViewById(R.id.cb_ignore_signature);
        cbIgnoreSignature.setChecked(PluginManager.isIgnoreSignatureWarning());
        
        new AlertDialog.Builder(requireContext())
                .setTitle("开发者选项")
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    PluginManager.setIgnoreSignatureWarning(cbIgnoreSignature.isChecked());
                    showToast(cbIgnoreSignature.isChecked() ? "已忽略签名验证" : "已启用签名验证");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            PreferencesUtils.getWorkFolder(requireContext());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}