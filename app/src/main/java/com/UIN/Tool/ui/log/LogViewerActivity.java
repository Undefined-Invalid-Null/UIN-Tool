package com.UIN.Tool.ui.log;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityLogViewerBinding;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.utils.LogUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogViewerActivity extends BaseActivity {

    private ActivityLogViewerBinding binding;
    private boolean isAutoOpened = false;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> { if (uri != null) exportLog(uri); });

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_log_viewer;
    }

    @Override
    protected void initViews() {
        binding = ActivityLogViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.title_log_viewer), true);
    }

    @Override
    protected void initData() {
        isAutoOpened = getIntent().getBooleanExtra("auto_open", false);
        if (isAutoOpened) {
            binding.tvHint.setVisibility(android.view.View.VISIBLE);
            binding.tvHint.setText(getString(R.string.log_auto_open_title) + "\n" + getString(R.string.log_auto_open_message));
        }
        loadLog();
    }

    @Override
    protected void setupListeners() {
        binding.btnRefreshContainer.setOnClickListener(v -> loadLog());
        binding.btnClearContainer.setOnClickListener(v -> clearCurrentLog());
        binding.btnClearAllContainer.setOnClickListener(v -> clearAllLogs());
        binding.btnExportContainer.setOnClickListener(v -> exportLogFile());
    }

    private void loadLog() {
        StringBuilder sb = new StringBuilder();

        File crashLog = new File(getFilesDir(), "crash_log.txt");
        if (crashLog.exists() && crashLog.length() > 0) {
            sb.append("========== ").append(getString(R.string.crash_log)).append(" ==========\n");
            try {
                BufferedReader reader = new BufferedReader(new FileReader(crashLog));
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                reader.close();
                sb.append("\n========== ").append(getString(R.string.runtime_log)).append(" ==========\n\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String runLog = LogUtils.readLog();
        if (runLog != null && !runLog.isEmpty()) sb.append(runLog);

        if (sb.length() == 0) {
            binding.tvLogContent.setText(getString(R.string.log_no_records));
        } else {
            binding.tvLogContent.setText(sb.toString());
        }
    }

    private void clearCurrentLog() {
        DialogHelper.confirmDialog(this, getString(R.string.dialog_title_confirm),
                getString(R.string.clear_log_confirm),
                (dialog, which) -> {
                    LogUtils.clear(this);
                    File crashLog = new File(getFilesDir(), "crash_log.txt");
                    if (crashLog.exists()) crashLog.delete();
                    loadLog();
                    showToast(getString(R.string.log_cleared));
                }).show();
    }

    private void clearAllLogs() {
        DialogHelper.confirmDialog(this, getString(R.string.dialog_title_confirm),
                getString(R.string.clear_all_logs_confirm),
                (dialog, which) -> {
                    List<File> logFiles = LogUtils.getAllLogFiles();
                    int count = 0;
                    for (File file : logFiles) {
                        if (file.delete()) count++;
                    }
                    File crashLog = new File(getFilesDir(), "crash_log.txt");
                    if (crashLog.exists()) {
                        crashLog.delete();
                        count++;
                    }
                    LogUtils.init(this);
                    loadLog();
                    showToast(getString(R.string.log_all_cleared, count));
                }).show();
    }

    private void exportLogFile() {
        File logFile = LogUtils.getLogFile();
        if (logFile == null || !logFile.exists() || logFile.length() == 0) {
            File crashLog = new File(getFilesDir(), "crash_log.txt");
            if (crashLog.exists() && crashLog.length() > 0) {
                logFile = crashLog;
            } else {
                showToast(getString(R.string.log_no_records));
                return;
            }
        }
        String fileName = "UIN_Tool_Log_" + getCurrentTime() + ".txt";
        exportLauncher.launch(fileName);
    }

    private void exportLog(Uri uri) {
        try {
            File logFile = LogUtils.getLogFile();
            if (logFile == null || !logFile.exists()) {
                logFile = new File(getFilesDir(), "crash_log.txt");
            }
            FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri);
            FileInputStream fis = new FileInputStream(logFile);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) fos.write(buffer, 0, length);
            fos.close();
            fis.close();
            showToast(getString(R.string.log_exported));
        } catch (Exception e) {
            showToast(getString(R.string.log_export_failed, e.getMessage()));
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }
}