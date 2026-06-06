// app/src/main/java/com/UIN/Tool/ui/backup/BackupManagerActivity.java
package com.UIN.Tool.ui.backup;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityBackupManagerBinding;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.utils.FileUtils;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.utils.UIConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManagerActivity extends BaseActivity {

    private ActivityBackupManagerBinding binding;
    private BackupAdapter adapter;
    private List<BackupFile> backupFiles = new ArrayList<>();
    private PluginManager pluginManager;
    private static final String BACKUP_DIR = "/storage/emulated/0/UIN_Tool/backups";

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> { if (uri != null) exportBackup(uri); });

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_backup_manager;
    }

    @Override
    protected void initViews() {
        binding = ActivityBackupManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.title_backup_manager), true);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void initData() {
        pluginManager = PluginManager.getInstance(this);
        loadBackupFiles();
    }

    @Override
    protected void setupListeners() {
        binding.btnBackupContainer.setOnClickListener(v -> backupAll());
        binding.btnRestoreContainer.setOnClickListener(v -> showRestoreDialog());
        binding.btnRefreshContainer.setOnClickListener(v -> loadBackupFiles());
    }

    private void loadBackupFiles() {
        backupFiles.clear();
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) backupDir.mkdirs();

        File[] files = backupDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".zip")) {
                    backupFiles.add(new BackupFile(file));
                }
            }
        }

        backupFiles.sort((a, b) -> Long.compare(b.file.lastModified(), a.file.lastModified()));

        if (adapter == null) {
            adapter = new BackupAdapter();
            binding.recyclerView.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private void backupAll() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvStatus.setVisibility(View.VISIBLE);
        binding.tvStatus.setText(getString(R.string.backing_up));
        binding.progressBar.setIndeterminate(true);

        new Thread(() -> {
            try {
                File backupDir = new File(BACKUP_DIR);
                if (!backupDir.exists()) backupDir.mkdirs();

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File backupFile = new File(backupDir, "UIN_Tool_Backup_" + timestamp + ".zip");
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile));

                List<PluginInfo> plugins = pluginManager.getInstalledPlugins();
                int totalPlugins = plugins.size();
                int currentPlugin = 0;

                for (PluginInfo plugin : plugins) {
                    currentPlugin++;
                    final int finalCurrent = currentPlugin;
                    runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.backing_up_plugin, finalCurrent, totalPlugins, plugin.name)));

                    File pluginDir = pluginManager.getPluginDirFile(plugin.pluginId);
                    if (pluginDir != null && pluginDir.exists()) {
                        addToZip(zos, pluginDir, "plugins/" + plugin.pluginId + "/");
                    }
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.backing_up_config)));
                File prefsDir = new File(getFilesDir().getParent(), "shared_prefs");
                if (prefsDir.exists()) {
                    File[] prefFiles = prefsDir.listFiles();
                    if (prefFiles != null) {
                        for (File prefFile : prefFiles) {
                            if (prefFile.getName().endsWith(".xml")) {
                                addToZip(zos, prefFile, "config/shared_prefs/" + prefFile.getName());
                            }
                        }
                    }
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.backing_up_work_folder)));
                String workFolder = PreferencesUtils.getWorkFolder(this);
                JSONObject config = new JSONObject();
                config.put("work_folder", workFolder);
                config.put("backup_time", timestamp);
                config.put("plugin_count", plugins.size());

                File tempConfig = new File(getCacheDir(), "temp_config.json");
                FileOutputStream fos = new FileOutputStream(tempConfig);
                fos.write(config.toString(4).getBytes());
                fos.close();
                addToZip(zos, tempConfig, "config/work_folder.json");
                tempConfig.delete();

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.backing_up_ui_config)));
                File uiConfigFile = new File(getFilesDir(), "ui_config.json");
                if (uiConfigFile.exists()) {
                    addToZip(zos, uiConfigFile, "config/ui_config.json");
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.backing_up_settings)));
                SharedPreferences prefs = getSharedPreferences("uin_tool_prefs", MODE_PRIVATE);
                JSONObject settings = new JSONObject();
                settings.put("view_mode", prefs.getString("view_mode", "list"));

                File tempSettings = new File(getCacheDir(), "temp_settings.json");
                fos = new FileOutputStream(tempSettings);
                fos.write(settings.toString(4).getBytes());
                fos.close();
                addToZip(zos, tempSettings, "config/settings.json");
                tempSettings.delete();

                zos.close();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvStatus.setVisibility(View.GONE);
                    showToast(getString(R.string.backup_success, backupFile.getName(), formatSize(backupFile.length())));
                    loadBackupFiles();
                });

            } catch (Exception e) {
                LogUtils.e("BackupManager", "备份失败", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvStatus.setVisibility(View.GONE);
                    showToast(getString(R.string.backup_failed, e.getMessage()));
                });
            }
        }).start();
    }

    private void addToZip(ZipOutputStream zos, File file, String basePath) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getName().startsWith(".")) continue;
                    addToZip(zos, child, basePath + file.getName() + "/");
                }
            }
        } else {
            ZipEntry entry = new ZipEntry(basePath + file.getName());
            zos.putNextEntry(entry);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) zos.write(buffer, 0, len);
            fis.close();
            zos.closeEntry();
        }
    }

    private void showRestoreDialog() {
        if (backupFiles.isEmpty()) {
            showToast(getString(R.string.backup_no_files));
            return;
        }

        String[] backupNames = new String[backupFiles.size()];
        for (int i = 0; i < backupFiles.size(); i++) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            backupNames[i] = backupFiles.get(i).file.getName() + "\n" +
                    sdf.format(backupFiles.get(i).file.lastModified()) + " | " +
                    formatSize(backupFiles.get(i).file.length());
        }

        DialogHelper.listDialog(this, getString(R.string.select_backup), backupNames,
                (dialog, which) -> restoreBackup(backupFiles.get(which))).show();
    }

    private void restoreBackup(BackupFile backupFile) {
        DialogHelper.confirmDialog(this, getString(R.string.dialog_title_confirm),
                getString(R.string.backup_restore_confirm),
                (dialog, which) -> performRestore(backupFile.file)).show();
    }

    private void performRestore(File backupFile) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvStatus.setVisibility(View.VISIBLE);
        binding.tvStatus.setText(getString(R.string.restoring));
        binding.progressBar.setIndeterminate(true);

        new Thread(() -> {
            File tempDir = null;
            try {
                tempDir = new File(getCacheDir(), "restore_temp_" + System.currentTimeMillis());
                tempDir.mkdirs();

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.extracting_backup)));
                unzipFile(backupFile, tempDir);

                File pluginsDir = new File(tempDir, "plugins");
                if (pluginsDir.exists()) {
                    File[] pluginDirs = pluginsDir.listFiles();
                    if (pluginDirs != null) {
                        int total = pluginDirs.length;
                        int current = 0;
                        for (File pluginDir : pluginDirs) {
                            current++;
                            final int finalCurrent = current;
                            runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.restoring_plugin, finalCurrent, total, pluginDir.getName())));

                            if (pluginDir.isDirectory()) {
                                String pluginId = pluginDir.getName();
                                File destPluginDir = pluginManager.getPluginDirFile(pluginId);
                                if (destPluginDir != null) {
                                    if (destPluginDir.exists()) FileUtils.deleteDir(destPluginDir);
                                    copyDir(pluginDir, destPluginDir);
                                }
                            }
                        }
                    }
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.restoring_config)));
                File prefsBackupDir = new File(tempDir, "config/shared_prefs");
                if (prefsBackupDir.exists()) {
                    File[] prefFiles = prefsBackupDir.listFiles();
                    if (prefFiles != null) {
                        File destPrefsDir = new File(getFilesDir().getParent(), "shared_prefs");
                        destPrefsDir.mkdirs();
                        for (File prefFile : prefFiles) {
                            copyFile(prefFile, new File(destPrefsDir, prefFile.getName()));
                        }
                    }
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.restoring_work_folder)));
                File workFolderBackup = new File(tempDir, "config/work_folder.json");
                if (workFolderBackup.exists()) {
                    String json = readFileToString(workFolderBackup);
                    try {
                        JSONObject config = new JSONObject(json);
                        String workFolder = config.optString("work_folder");
                        if (!workFolder.isEmpty()) {
                            PreferencesUtils.setWorkFolder(BackupManagerActivity.this, workFolder);
                        }
                    } catch (Exception e) {
                        LogUtils.e("BackupManager", "解析工作目录配置失败", e);
                    }
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.restoring_ui_config)));
                File uiConfigBackup = new File(tempDir, "config/ui_config.json");
                if (uiConfigBackup.exists()) {
                    File destUiConfig = new File(getFilesDir(), "ui_config.json");
                    copyFile(uiConfigBackup, destUiConfig);
                    runOnUiThread(() -> {
                        UIConfig.getInstance(BackupManagerActivity.this).loadConfig();
                        UIConfig.getInstance(BackupManagerActivity.this).applyTheme(BackupManagerActivity.this);
                    });
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.restoring_settings)));
                File settingsBackup = new File(tempDir, "config/settings.json");
                if (settingsBackup.exists()) {
                    String json = readFileToString(settingsBackup);
                    try {
                        JSONObject settings = new JSONObject(json);
                        SharedPreferences prefs = getSharedPreferences("uin_tool_prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("view_mode", settings.optString("view_mode", "list"));
                        editor.apply();
                    } catch (Exception e) {
                        LogUtils.e("BackupManager", "解析应用设置失败", e);
                    }
                }

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.cleaning_up)));
                FileUtils.deleteDir(tempDir);

                runOnUiThread(() -> binding.tvStatus.setText(getString(R.string.refreshing_plugins)));
                pluginManager.refreshWorkFolder();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvStatus.setVisibility(View.GONE);
                    showToast(getString(R.string.backup_restore_success));
                    loadBackupFiles();
                });

            } catch (Exception e) {
                LogUtils.e("BackupManager", "恢复失败", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvStatus.setVisibility(View.GONE);
                    showToast(getString(R.string.backup_restore_failed, e.getMessage()));
                });
                if (tempDir != null) FileUtils.deleteDir(tempDir);
            }
        }).start();
    }

    private void unzipFile(File zipFile, File destDir) throws Exception {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        byte[] buffer = new byte[8192];

        while ((entry = zis.getNextEntry()) != null) {
            File targetFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                targetFile.mkdirs();
            } else {
                targetFile.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(targetFile);
                int len;
                while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private void copyDir(File src, File dst) throws Exception {
        if (!src.exists()) return;
        if (!dst.exists()) dst.mkdirs();

        File[] files = src.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    copyDir(file, new File(dst, file.getName()));
                } else {
                    copyFile(file, new File(dst, file.getName()));
                }
            }
        }
    }

    private void copyFile(File src, File dst) throws Exception {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
        fos.close();
        fis.close();
    }

    private String readFileToString(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return new String(data, "UTF-8");
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    private void exportBackup(Uri uri) {
        // 导出功能实现
        showToast(getString(R.string.export_not_implemented));
    }

    private class BackupFile {
        File file;
        BackupFile(File file) { this.file = file; }
    }

    private class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            BackupFile backup = backupFiles.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            holder.text1.setText(backup.file.getName());
            holder.text2.setText(sdf.format(backup.file.lastModified()) + " | " + formatSize(backup.file.length()));

            holder.itemView.setOnClickListener(v -> {
                DialogHelper.listDialog(BackupManagerActivity.this, backup.file.getName(),
                        new String[]{getString(R.string.btn_restore), getString(R.string.delete)},
                        (dialog, which) -> {
                            if (which == 0) restoreBackup(backup);
                            else {
                                if (backup.file.delete()) {
                                    backupFiles.remove(position);
                                    notifyItemRemoved(position);
                                    showToast(getString(R.string.deleted));
                                }
                            }
                        }).show();
            });
        }

        @Override
        public int getItemCount() { return backupFiles.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}