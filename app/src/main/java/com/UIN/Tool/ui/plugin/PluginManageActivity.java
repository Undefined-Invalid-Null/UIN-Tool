package com.UIN.Tool.ui.plugin;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityPluginManageBinding;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.ui.tools.PluginShortcutHelper;
import com.UIN.Tool.utils.FileUtils;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.PreferencesUtils;
import com.UIN.Tool.widget.UINWidgetProvider;
import com.UIN.Tool.widget.Widget1x1Provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class PluginManageActivity extends BaseActivity {

    private ActivityPluginManageBinding binding;
    private PluginListAdapter adapter;
    private PluginManager pluginManager;
    private String workFolder;

    private List<String> pendingImportPaths = new ArrayList<>();
    private int pendingImportIndex = 0;
    private int batchSuccessCount = 0;
    private int batchFailCount = 0;
    private StringBuilder batchFailNames = new StringBuilder();

    private final ActivityResultLauncher<String> importLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) importSinglePlugin(uri); });

    private final ActivityResultLauncher<String[]> batchImportLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> { if (uris != null && !uris.isEmpty()) startBatchImport(uris); });

    private final ActivityResultLauncher<String> importZipLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) importPluginSet(uri); });

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_plugin_manage;
    }

    @Override
    protected void initViews() {
        binding = ActivityPluginManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.title_plugin_manage), true);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void initData() {
        pluginManager = PluginManager.getInstance(this);
        workFolder = PreferencesUtils.getWorkFolder(this);
        loadPlugins();
    }

    @Override
    protected void setupListeners() {
        binding.btnImportContainer.setOnClickListener(v -> importLauncher.launch("*/*"));
        binding.btnBatchImportContainer.setOnClickListener(v -> batchImportLauncher.launch(new String[]{"*/*"}));
        binding.btnImportZipContainer.setOnClickListener(v -> importZipLauncher.launch("application/zip"));
        binding.btnSelectAllContainer.setOnClickListener(v -> { if (adapter != null) adapter.selectAll(); });
        binding.btnExportContainer.setOnClickListener(v -> exportSelectedPlugins());
        binding.btnUninstallContainer.setOnClickListener(v -> uninstallSelectedPlugins());
        
        binding.tpkHintContainer.setOnClickListener(v -> showTpkHintDialog());
    }

    private void showTpkHintDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📦 关于 TPK 文件")
                .setMessage(getString(R.string.tpk_hint_message))
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton("查看插件目录", (dialog, which) -> openPluginsFolder())
                .show();
    }

    private void openPluginsFolder() {
        File pluginsDir = new File(workFolder);
        if (pluginsDir.exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", pluginsDir);
            intent.setDataAndType(uri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "选择文件管理器"));
        } else {
            showToast("插件目录不存在");
        }
    }

    private void importSinglePlugin(Uri uri) {
        try {
            String fileName = getFileNameFromUri(uri);
            File tempFile = new File(getCacheDir(), "temp_import_" + System.currentTimeMillis() + ".tpk");
            if (!FileUtils.copyUriToFile(this, uri, tempFile)) {
                showToast(getString(R.string.toast_import_failed, fileName));
                return;
            }
            PluginInfo info = pluginManager.installPlugin(tempFile.getAbsolutePath(), fileName);
            FileUtils.deleteDir(tempFile);
            if (info != null) {
                showToast(getString(R.string.toast_import_success, info.name));
                loadPlugins();
                refreshWidgets();
            } else {
                showToast(getString(R.string.toast_import_failed, fileName));
            }
        } catch (Exception e) {
            LogUtils.e("PluginManageActivity", "导入失败", e);
            showToast(getString(R.string.toast_import_failed, e.getMessage()));
        }
    }

    private void startBatchImport(List<Uri> uris) {
        pendingImportPaths.clear();
        for (Uri uri : uris) pendingImportPaths.add(uri.toString());
        pendingImportIndex = 0;
        batchSuccessCount = 0;
        batchFailCount = 0;
        batchFailNames = new StringBuilder();
        binding.importProgress.setVisibility(View.VISIBLE);
        binding.tvProgressStatus.setVisibility(View.VISIBLE);
        binding.importProgress.setMax(pendingImportPaths.size());
        processNextBatchImport();
    }

    private void processNextBatchImport() {
        if (pendingImportIndex >= pendingImportPaths.size()) {
            binding.importProgress.setVisibility(View.GONE);
            binding.tvProgressStatus.setVisibility(View.GONE);
            String msg = getString(R.string.plugin_manage_import_result, batchSuccessCount, batchFailCount);
            if (batchFailCount > 0) msg += "\n\n" + getString(R.string.failed_list) + "\n" + batchFailNames.toString();

            DialogHelper.infoDialog(this, getString(R.string.dialog_title_info), msg)
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        if (batchSuccessCount > 0) {
                            loadPlugins();
                            refreshWidgets();
                        }
                    }).show();
            return;
        }

        String uriStr = pendingImportPaths.get(pendingImportIndex);
        Uri uri = Uri.parse(uriStr);
        String fileName = getFileNameFromUri(uri);
        binding.tvProgressStatus.setText(getString(R.string.importing_progress, pendingImportIndex + 1, pendingImportPaths.size(), fileName));
        binding.importProgress.setProgress(pendingImportIndex + 1);

        new Thread(() -> {
            try {
                File tempFile = new File(getCacheDir(), "temp_import_" + System.currentTimeMillis() + ".tpk");
                if (FileUtils.copyUriToFile(PluginManageActivity.this, uri, tempFile)) {
                    PluginInfo info = pluginManager.installPlugin(tempFile.getAbsolutePath(), fileName);
                    FileUtils.deleteDir(tempFile);
                    if (info != null) batchSuccessCount++;
                    else { batchFailCount++; batchFailNames.append(fileName).append("\n"); }
                } else { batchFailCount++; batchFailNames.append(fileName).append("\n"); }
            } catch (Exception e) {
                LogUtils.e("PluginManageActivity", "批量导入失败", e);
                batchFailCount++;
                batchFailNames.append(fileName).append("\n");
            }
            pendingImportIndex++;
            runOnUiThread(this::processNextBatchImport);
        }).start();
    }

    private void importPluginSet(Uri uri) {
        binding.importProgress.setVisibility(View.VISIBLE);
        binding.tvProgressStatus.setVisibility(View.VISIBLE);
        binding.tvProgressStatus.setText(getString(R.string.processing_plugin_set));
        binding.importProgress.setMax(100);
        binding.importProgress.setIndeterminate(true);

        new Thread(() -> {
            int success = 0, fail = 0;
            StringBuilder failNames = new StringBuilder();
            try {
                File zipFile = new File(getCacheDir(), "temp_zip_" + System.currentTimeMillis() + ".zip");
                if (!FileUtils.copyUriToFile(PluginManageActivity.this, uri, zipFile)) throw new Exception("复制失败");
                File extractDir = new File(getCacheDir(), "extract_" + System.currentTimeMillis());
                extractDir.mkdirs();
                ZipFile zf = new ZipFile(zipFile);
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".tpk")) {
                        File out = new File(extractDir, new File(entry.getName()).getName());
                        try (FileInputStream is = (FileInputStream) zf.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(out)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                        }
                        PluginInfo info = pluginManager.installPlugin(out.getAbsolutePath(), out.getName());
                        if (info != null) success++;
                        else { fail++; failNames.append(out.getName()).append("\n"); }
                    }
                }
                zf.close();
                zipFile.delete();
                FileUtils.deleteDir(extractDir);
            } catch (Exception e) {
                LogUtils.e("PluginManageActivity", "导入插件集失败", e);
                fail++;
            }
            final int fSuccess = success, fFail = fail;
            final String fFailNames = failNames.toString();
            runOnUiThread(() -> {
                binding.importProgress.setVisibility(View.GONE);
                binding.tvProgressStatus.setVisibility(View.GONE);
                String msg = getString(R.string.plugin_manage_zip_import_result, fSuccess, fFail);
                if (fFail > 0) msg += "\n\n" + getString(R.string.failed_list) + "\n" + fFailNames;

                DialogHelper.infoDialog(PluginManageActivity.this, getString(R.string.dialog_title_info), msg)
                        .setPositiveButton(R.string.ok, (d, w) -> {
                            if (fSuccess > 0) {
                                loadPlugins();
                                refreshWidgets();
                            }
                        }).show();
            });
        }).start();
    }

    private void exportSelectedPlugins() {
        if (adapter == null || adapter.getSelectedPlugins().isEmpty()) {
            showToast(getString(R.string.toast_please_select_plugin));
            return;
        }

        try {
            Set<String> ids = adapter.getSelectedPlugins();
            List<PluginInfo> plugins = new ArrayList<>();
            for (String id : ids) {
                PluginInfo info = pluginManager.getPluginInfo(id);
                if (info != null) plugins.add(info);
            }

            File zipFile = new File(workFolder, "Plugins_" + System.currentTimeMillis() + ".zip");
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));

            for (PluginInfo info : plugins) {
                File dir = pluginManager.getPluginDirFile(info.pluginId);
                if (dir != null && dir.exists()) {
                    addDirToZip(zos, dir, info.pluginId + "/");
                }
            }
            zos.close();

            showToast(getString(R.string.plugin_manage_export_result, plugins.size(), zipFile.getAbsolutePath()));
            adapter.clearSelection();
        } catch (Exception e) {
            LogUtils.e("PluginManageActivity", "导出失败", e);
            showToast(getString(R.string.toast_export_failed, e.getMessage()));
        }
    }

    private void addDirToZip(ZipOutputStream zos, File dir, String base) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                addDirToZip(zos, f, base + f.getName() + "/");
            } else {
                ZipEntry entry = new ZipEntry(base + f.getName());
                zos.putNextEntry(entry);
                FileInputStream fis = new FileInputStream(f);
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) > 0) zos.write(buf, 0, len);
                fis.close();
                zos.closeEntry();
            }
        }
    }

    private void uninstallSelectedPlugins() {
        if (adapter == null || adapter.getSelectedPlugins().isEmpty()) {
            showToast(getString(R.string.toast_please_select_uninstall));
            return;
        }

        Set<String> ids = adapter.getSelectedPlugins();
        List<PluginInfo> plugins = new ArrayList<>();
        for (String id : ids) {
            PluginInfo info = pluginManager.getPluginInfo(id);
            if (info != null) plugins.add(info);
        }

        DialogHelper.confirmDialog(this, getString(R.string.dialog_title_confirm),
                getString(R.string.plugin_manage_uninstall_confirm, plugins.size()),
                (d, w) -> {
                    binding.uninstallProgress.setVisibility(View.VISIBLE);
                    new Thread(() -> {
                        int success = 0, fail = 0;
                        StringBuilder failNames = new StringBuilder();
                        for (PluginInfo info : plugins) {
                            if (pluginManager.uninstallPlugin(info.pluginId)) success++;
                            else { fail++; failNames.append(info.name).append("\n"); }
                        }
                        final int fs = success, ff = fail;
                        final String ffn = failNames.toString();
                        runOnUiThread(() -> {
                            binding.uninstallProgress.setVisibility(View.GONE);
                            String msg = getString(R.string.plugin_manage_uninstall_result, fs, ff);
                            if (ff > 0) msg += "\n\n" + getString(R.string.failed_list) + "\n" + ffn;

                            DialogHelper.infoDialog(PluginManageActivity.this, getString(R.string.dialog_title_info), msg)
                                    .setPositiveButton(R.string.ok, (d2, w2) -> {
                                        if (fs > 0) {
                                            loadPlugins();
                                            refreshWidgets();
                                        }
                                    }).show();
                        });
                    }).start();
                }).show();
    }

    private void loadPlugins() {
        pluginManager.refreshWorkFolder();
        List<PluginInfo> plugins = pluginManager.getInstalledPlugins();
        if (adapter == null) {
            adapter = new PluginListAdapter(plugins);
            binding.recyclerView.setAdapter(adapter);
        } else {
            adapter.setPlugins(plugins);
        }
    }

    private void refreshWidgets() {
        try {
            UINWidgetProvider.sendIntentToRefreshAllWidgets(this);
            Widget1x1Provider.sendRefreshIntent(this);
        } catch (Exception e) {
            LogUtils.e("PluginManageActivity", "刷新小部件失败", e);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String name = "plugin.tpk";
        if (uri.getPath() != null) {
            name = uri.getPath();
            int lastSlash = name.lastIndexOf('/');
            if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        }
        return name;
    }

    private void showPluginDetail(PluginInfo info) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_plugin_detail, null);
        TextView tvName = dialogView.findViewById(R.id.tv_name);
        TextView tvId = dialogView.findViewById(R.id.tv_id);
        TextView tvVersion = dialogView.findViewById(R.id.tv_version);
        TextView tvAuthor = dialogView.findViewById(R.id.tv_author);
        TextView tvCategory = dialogView.findViewById(R.id.tv_category);
        TextView tvDescription = dialogView.findViewById(R.id.tv_description);
        View btnRun = dialogView.findViewById(R.id.btn_run);
        View btnShortcut = dialogView.findViewById(R.id.btn_shortcut);
        View btnChangeCategory = dialogView.findViewById(R.id.btn_change_category);
        View btnUninstall = dialogView.findViewById(R.id.btn_uninstall);

        tvName.setText(info.name);
        tvId.setText(info.pluginId);
        tvVersion.setText(info.versionName + " (" + info.version + ")");
        tvAuthor.setText(info.author != null && !info.author.isEmpty() ? info.author : getString(R.string.plugin_unknown_author));
        tvCategory.setText(info.category != null && !info.category.isEmpty() ? info.category : getString(R.string.plugin_default_category));
        tvDescription.setText(info.description != null && !info.description.isEmpty() ? info.description : getString(R.string.plugin_no_description));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(info.name)
                .setView(dialogView)
                .setPositiveButton(R.string.close, null)
                .create();

        btnRun.setOnClickListener(v -> {
            pluginManager.openPlugin(info.pluginId, this);
            dialog.dismiss();
        });

        btnShortcut.setOnClickListener(v -> {
            PluginShortcutHelper.createShortcut(this, info);
            showToast(getString(R.string.creating_shortcut));
            dialog.dismiss();
        });

        btnChangeCategory.setOnClickListener(v -> {
            showCategoryChangeDialog(info);
            dialog.dismiss();
        });

        btnUninstall.setOnClickListener(v -> {
            DialogHelper.confirmDialog(this, getString(R.string.dialog_title_confirm),
                    getString(R.string.uninstall_plugin_confirm, info.name),
                    (d, w) -> {
                        pluginManager.uninstallPlugin(info.pluginId);
                        loadPlugins();
                        refreshWidgets();
                        showToast(getString(R.string.toast_uninstall_success, info.name));
                        dialog.dismiss();
                    }).show();
        });

        dialog.show();
    }

    private void showCategoryChangeDialog(PluginInfo info) {
        List<String> categories = pluginManager.getAllCategories();
        categories.remove(getString(R.string.all_category));
        
        final String NEW_CATEGORY_OPTION = "+ " + getString(R.string.create_new_category);
        List<String> displayCategories = new ArrayList<>(categories);
        displayCategories.add(NEW_CATEGORY_OPTION);
        
        String[] categoryArray = displayCategories.toArray(new String[0]);

        DialogHelper.listDialog(this, getString(R.string.dialog_title_select_category), categoryArray,
                (dialog, which) -> {
                    String selected = categoryArray[which];
                    if (selected.equals(NEW_CATEGORY_OPTION)) {
                        showSimpleCreateCategoryDialog(info);
                    } else {
                        boolean success = pluginManager.updatePluginCategory(info.pluginId, selected);
                        if (success) {
                            showToast(getString(R.string.category_updated, selected));
                            loadPlugins();
                        }
                    }
                }).show();
    }

    private void showSimpleCreateCategoryDialog(PluginInfo info) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setHint(getString(R.string.category_name_hint));
        input.setPadding(50, 20, 50, 20);
        
        builder.setTitle(R.string.category_manager_title)
                .setView(input)
                .setPositiveButton(R.string.add_category, (dialog, which) -> {
                    String newCategory = input.getText().toString().trim();
                    if (!newCategory.isEmpty()) {
                        pluginManager.addCategory(newCategory);
                        pluginManager.updatePluginCategory(info.pluginId, newCategory);
                        showToast(getString(R.string.category_updated, newCategory));
                        loadPlugins();
                    } else {
                        showToast(getString(R.string.category_name_empty));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private class PluginListAdapter extends RecyclerView.Adapter<PluginListAdapter.ViewHolder> {
        private List<PluginInfo> plugins = new ArrayList<>();
        private Set<String> selectedIds = new HashSet<>();

        PluginListAdapter(List<PluginInfo> p) { this.plugins = p; }

        void setPlugins(List<PluginInfo> p) {
            plugins = p;
            selectedIds.clear();
            notifyDataSetChanged();
        }

        Set<String> getSelectedPlugins() { return selectedIds; }

        void selectAll() {
            if (selectedIds.size() == plugins.size()) {
                selectedIds.clear();
            } else {
                selectedIds.clear();
                for (PluginInfo i : plugins) selectedIds.add(i.pluginId);
            }
            notifyDataSetChanged();
        }

        void clearSelection() {
            selectedIds.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_plugin_manage, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            PluginInfo info = plugins.get(p);
            h.checkBox.setChecked(selectedIds.contains(info.pluginId));
            h.checkBox.setOnCheckedChangeListener(null);
            h.checkBox.setOnCheckedChangeListener((b, isChecked) -> {
                if (isChecked) selectedIds.add(info.pluginId);
                else selectedIds.remove(info.pluginId);
            });
            h.tvName.setText(info.name);
            h.tvId.setText(info.pluginId);
            h.itemView.setOnClickListener(v -> showPluginDetail(info));
            h.itemView.setOnLongClickListener(v -> {
                showPluginDetail(info);
                return true;
            });
        }

        @Override
        public int getItemCount() { return plugins.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            TextView tvName, tvId;

            ViewHolder(View v) {
                super(v);
                checkBox = v.findViewById(R.id.cb_select);
                tvName = v.findViewById(R.id.tv_name);
                tvId = v.findViewById(R.id.tv_id);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlugins();
    }
}