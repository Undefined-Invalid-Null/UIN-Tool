package com.UIN.Tool.ui.manage;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.utils.LogUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GitHubMirrorActivity extends BaseActivity {

    private static final String TAG = "GitHubMirror";
    private static final String PREF_NAME = "github_mirror";
    private static final String KEY_MIRRORS = "custom_mirrors";
    private static final String KEY_ENABLED_MIRRORS = "enabled_mirrors";
    private static final String KEY_USE_CDN = "use_cdn";

    private RecyclerView recyclerView;
    private MirrorAdapter adapter;
    private List<MirrorItem> mirrors = new ArrayList<>();
    private Set<String> enabledMirrors = new HashSet<>();
    private boolean useCdn = true;

    private LinearLayout btnAddMirror;
    private LinearLayout btnImportMirrors;
    private LinearLayout btnExportMirrors;
    private LinearLayout btnResetMirrors;
    private LinearLayout btnTestAll;
    private TextView tvCdnStatus;
    private Button btnSave;
    private Button btnSelectAll;
    private Button btnDeselectAll;

    private final ActivityResultLauncher<String> importLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    importMirrorsFromFile(uri);
                }
            });

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_github_mirror;
    }

    @Override
    protected void initViews() {
        setContentView(R.layout.activity_github_mirror);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("GitHub 加速");
        }

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(true);

        btnAddMirror = findViewById(R.id.btn_add_mirror);
        btnImportMirrors = findViewById(R.id.btn_import_mirrors);
        btnExportMirrors = findViewById(R.id.btn_export_mirrors);
        btnResetMirrors = findViewById(R.id.btn_reset_mirrors);
        btnTestAll = findViewById(R.id.btn_test_all);
        tvCdnStatus = findViewById(R.id.tv_cdn_status);
        btnSave = findViewById(R.id.btn_save);
        btnSelectAll = findViewById(R.id.btn_select_all);
        btnDeselectAll = findViewById(R.id.btn_deselect_all);

        btnAddMirror.setOnClickListener(v -> showAddMirrorDialog());
        btnImportMirrors.setOnClickListener(v -> importLauncher.launch("text/plain"));
        btnExportMirrors.setOnClickListener(v -> exportMirrors());
        btnResetMirrors.setOnClickListener(v -> resetToDefault());
        btnTestAll.setOnClickListener(v -> testAllMirrors());
        btnSave.setOnClickListener(v -> saveSettings());
        
        // 全选/取消全选
        btnSelectAll.setOnClickListener(v -> selectAll(true));
        btnDeselectAll.setOnClickListener(v -> selectAll(false));
        
        // CDN 加速卡片点击切换
        findViewById(R.id.card_cdn).setOnClickListener(v -> {
            useCdn = !useCdn;
            updateCdnStatus();
            Toast.makeText(this, useCdn ? "CDN 加速已开启" : "CDN 加速已关闭", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void initData() {
        loadMirrors();
        loadSettings();
        adapter = new MirrorAdapter();
        recyclerView.setAdapter(adapter);
    }
    
    private void updateCdnStatus() {
        if (tvCdnStatus != null) {
            if (useCdn) {
                tvCdnStatus.setText("已开启");
                tvCdnStatus.setTextColor(getColor(R.color.primary));
            } else {
                tvCdnStatus.setText("已关闭");
                tvCdnStatus.setTextColor(getColor(R.color.text_hint));
            }
        }
    }

    private void loadMirrors() {
        mirrors.clear();
        loadDefaultMirrorsFromAssets();
        loadCustomMirrors();
    }
    
    private void loadDefaultMirrorsFromAssets() {
        try {
            InputStream is = getAssets().open("Github_Mirrors.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    MirrorItem mirror = new MirrorItem();
                    mirror.name = parts[0].trim();
                    mirror.url = parts[1].trim();
                    mirror.remark = parts.length > 2 ? parts[2].trim() : "";
                    mirror.isDefault = true;
                    mirrors.add(mirror);
                }
            }
            reader.close();
            is.close();
            LogUtils.i(TAG, "从 assets 加载默认镜像站: " + mirrors.size() + " 个");
        } catch (Exception e) {
            LogUtils.e(TAG, "加载默认镜像站失败", e);
            addFallbackMirrors();
        }
    }
    
    private void addFallbackMirrors() {
        String[][] fallback = {
            {"GitHub 官方", "https://github.com", "官方源（需代理）"},
            {"FastGit", "https://hub.fastgit.xyz", "国内高速镜像"},
            {"GhProxy", "https://ghproxy.net", "代理加速"},
            {"Mirror GhProxy", "https://mirror.ghproxy.com", "代理加速备选"},
            {"Moeyy", "https://github.moeyy.xyz", "国内镜像"},
            {"GitClone", "https://gitclone.com", "国内镜像"},
            {"GhApi", "https://gh.api.99988866.xyz", "API 加速"},
            {"Jiewen", "https://gh.jiewen.ltd", "国内镜像"},
            {"CDN Proxy", "https://ghproxy.ltd", "CDN 代理"},
            {"加速 GitHub", "https://github.zhlh6.cn", "国内加速"},
            {"Kkgithub", "https://kkgithub.com", "国内镜像"},
            {"Huu", "https://hub.uuu.moe", "国内加速"},
            {"FastGit 下载", "https://download.fastgit.xyz", "下载加速"}
        };
        for (String[] data : fallback) {
            MirrorItem mirror = new MirrorItem();
            mirror.name = data[0];
            mirror.url = data[1];
            mirror.remark = data[2];
            mirror.isDefault = true;
            mirrors.add(mirror);
        }
    }
    
    private void loadCustomMirrors() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String customMirrorsJson = prefs.getString(KEY_MIRRORS, "");
        if (!TextUtils.isEmpty(customMirrorsJson)) {
            try {
                String[] lines = customMirrorsJson.split("\n");
                for (String line : lines) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length >= 2) {
                        MirrorItem mirror = new MirrorItem();
                        mirror.name = parts[0];
                        mirror.url = parts[1];
                        mirror.remark = parts.length > 2 ? parts[2] : "";
                        mirror.isDefault = false;
                        mirrors.add(mirror);
                    }
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "解析自定义镜像站失败", e);
            }
        }
    }
    
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        String enabledStr = prefs.getString(KEY_ENABLED_MIRRORS, "");
        enabledMirrors.clear();
        if (!TextUtils.isEmpty(enabledStr)) {
            for (String id : enabledStr.split(",")) {
                enabledMirrors.add(id);
            }
        }
        
        if (enabledMirrors.isEmpty() && !mirrors.isEmpty()) {
            for (int i = 0; i < Math.min(3, mirrors.size()); i++) {
                enabledMirrors.add(mirrors.get(i).url);
            }
        }
        
        useCdn = prefs.getBoolean(KEY_USE_CDN, true);
        updateCdnStatus();
        
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        StringBuilder sb = new StringBuilder();
        for (String url : enabledMirrors) {
            if (sb.length() > 0) sb.append(",");
            sb.append(url);
        }
        editor.putString(KEY_ENABLED_MIRRORS, sb.toString());
        editor.putBoolean(KEY_USE_CDN, useCdn);
        
        StringBuilder customSb = new StringBuilder();
        for (MirrorItem mirror : mirrors) {
            if (!mirror.isDefault) {
                customSb.append(mirror.name).append("|").append(mirror.url);
                if (!TextUtils.isEmpty(mirror.remark)) {
                    customSb.append("|").append(mirror.remark);
                }
                customSb.append("\n");
            }
        }
        editor.putString(KEY_MIRRORS, customSb.toString());
        editor.apply();
        
        Intent intent = new Intent("com.UIN.Tool.UPDATE_MIRRORS");
        ArrayList<String> mirrorUrls = new ArrayList<>(getEnabledMirrorUrls());
        intent.putStringArrayListExtra("mirrors", mirrorUrls);
        intent.putExtra("use_cdn", useCdn);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private void selectAll(boolean select) {
        enabledMirrors.clear();
        if (select) {
            for (MirrorItem mirror : mirrors) {
                enabledMirrors.add(mirror.url);
            }
            Toast.makeText(this, "已全选 " + mirrors.size() + " 个镜像站", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "已取消全选", Toast.LENGTH_SHORT).show();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    private List<String> getEnabledMirrorUrls() {
        List<String> urls = new ArrayList<>();
        for (MirrorItem mirror : mirrors) {
            if (enabledMirrors.contains(mirror.url)) {
                urls.add(mirror.url);
            }
        }
        return urls;
    }
    
    private void showAddMirrorDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_mirror, null);
        EditText etName = dialogView.findViewById(R.id.et_mirror_name);
        EditText etUrl = dialogView.findViewById(R.id.et_mirror_url);
        EditText etRemark = dialogView.findViewById(R.id.et_mirror_remark);
        
        new AlertDialog.Builder(this)
                .setTitle("添加镜像站")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String url = etUrl.getText().toString().trim();
                    String remark = etRemark.getText().toString().trim();
                    
                    if (name.isEmpty()) {
                        Toast.makeText(this, "请输入镜像站名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (url.isEmpty()) {
                        Toast.makeText(this, "请输入镜像站 URL", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url;
                    }
                    
                    MirrorItem mirror = new MirrorItem();
                    mirror.name = name;
                    mirror.url = url;
                    mirror.remark = remark;
                    mirror.isDefault = false;
                    mirrors.add(mirror);
                    
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    Toast.makeText(this, "已添加镜像站", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void importMirrorsFromFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            int count = 0;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    MirrorItem mirror = new MirrorItem();
                    mirror.name = parts[0].trim();
                    mirror.url = parts[1].trim();
                    mirror.remark = parts.length > 2 ? parts[2].trim() : "";
                    mirror.isDefault = false;
                    mirrors.add(mirror);
                    count++;
                } else if (line.contains("http")) {
                    MirrorItem mirror = new MirrorItem();
                    mirror.name = "自定义镜像";
                    mirror.url = line;
                    mirror.remark = "";
                    mirror.isDefault = false;
                    mirrors.add(mirror);
                    count++;
                }
            }
            reader.close();
            is.close();
            
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            Toast.makeText(this, "成功导入 " + count + " 个镜像站", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            LogUtils.e(TAG, "导入镜像站失败", e);
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void exportMirrors() {
        StringBuilder sb = new StringBuilder();
        sb.append("# GitHub 镜像站列表\n");
        sb.append("# 格式: 名称|URL|备注\n\n");
        
        for (MirrorItem mirror : mirrors) {
            sb.append(mirror.name).append("|").append(mirror.url);
            if (!TextUtils.isEmpty(mirror.remark)) {
                sb.append("|").append(mirror.remark);
            }
            sb.append("\n");
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "导出镜像站列表"));
    }
    
    private void resetToDefault() {
        new AlertDialog.Builder(this)
                .setTitle("重置镜像站")
                .setMessage("确定要重置为默认镜像站列表吗？这将清除所有自定义添加的镜像站。")
                .setPositiveButton("确定", (dialog, which) -> {
                    mirrors.clear();
                    loadDefaultMirrorsFromAssets();
                    enabledMirrors.clear();
                    if (!mirrors.isEmpty()) {
                        for (int i = 0; i < Math.min(3, mirrors.size()); i++) {
                            enabledMirrors.add(mirrors.get(i).url);
                        }
                    }
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    Toast.makeText(this, "已重置为默认镜像站", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void testAllMirrors() {
        Toast.makeText(this, "开始测试镜像站...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            for (MirrorItem mirror : mirrors) {
                boolean reachable = testMirrorReachable(mirror.url);
                mirror.reachable = reachable;
                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                });
            }
            runOnUiThread(() -> Toast.makeText(this, "测试完成", Toast.LENGTH_SHORT).show());
        }).start();
    }
    
    private boolean testMirrorReachable(String url) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200 || responseCode == 302 || responseCode == 301;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    // ==================== MirrorItem 内部类 ====================
    
    private static class MirrorItem {
        String name;
        String url;
        String remark;
        boolean isDefault;
        Boolean reachable;
    }
    
    // ==================== MirrorAdapter 内部类 ====================
    
    private class MirrorAdapter extends RecyclerView.Adapter<MirrorAdapter.ViewHolder> {
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mirror, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            MirrorItem mirror = mirrors.get(position);
            
            holder.tvName.setText(mirror.name);
            holder.tvUrl.setText(mirror.url);
            if (!TextUtils.isEmpty(mirror.remark)) {
                holder.tvRemark.setText(mirror.remark);
                holder.tvRemark.setVisibility(View.VISIBLE);
            } else {
                holder.tvRemark.setVisibility(View.GONE);
            }
            
            // 显示可达性状态
            if (mirror.reachable != null && mirror.reachable) {
                holder.ivStatus.setImageResource(R.drawable.ic_check);
                holder.ivStatus.setColorFilter(getColor(R.color.success));
            } else if (mirror.reachable != null && !mirror.reachable) {
                holder.ivStatus.setImageResource(R.drawable.ic_close);
                holder.ivStatus.setColorFilter(getColor(R.color.error));
            } else {
                holder.ivStatus.setImageResource(R.drawable.ic_info);
                holder.ivStatus.setColorFilter(getColor(R.color.text_hint));
            }
            
            // 显示是否启用
            boolean isEnabled = enabledMirrors.contains(mirror.url);
            holder.cbEnabled.setChecked(isEnabled);
            holder.cbEnabled.setOnCheckedChangeListener(null);
            holder.cbEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    enabledMirrors.add(mirror.url);
                } else {
                    enabledMirrors.remove(mirror.url);
                }
            });
            
            // 长按删除（仅自定义镜像站）
            if (!mirror.isDefault) {
                holder.itemView.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(GitHubMirrorActivity.this)
                            .setTitle("删除镜像站")
                            .setMessage("确定要删除 " + mirror.name + " 吗？")
                            .setPositiveButton("删除", (dialog, which) -> {
                                mirrors.remove(position);
                                enabledMirrors.remove(mirror.url);
                                notifyItemRemoved(position);
                                Toast.makeText(GitHubMirrorActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                });
            } else {
                holder.itemView.setOnLongClickListener(null);
            }
        }
        
        @Override
        public int getItemCount() {
            return mirrors.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvUrl, tvRemark;
            ImageView ivStatus;
            CheckBox cbEnabled;
            
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_mirror_name);
                tvUrl = itemView.findViewById(R.id.tv_mirror_url);
                tvRemark = itemView.findViewById(R.id.tv_mirror_remark);
                ivStatus = itemView.findViewById(R.id.iv_status);
                cbEnabled = itemView.findViewById(R.id.cb_enabled);
            }
        }
    }
}