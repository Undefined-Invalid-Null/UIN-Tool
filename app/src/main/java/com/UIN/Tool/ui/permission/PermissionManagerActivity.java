package com.UIN.Tool.ui.permission;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityPermissionManagerBinding;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.ui.common.PermissionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PermissionManagerActivity extends BaseActivity {

    private ActivityPermissionManagerBinding binding;
    private List<PermissionItem> allPermissions = new ArrayList<>();
    private PermissionAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> multiplePermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                List<String> deniedPermissions = new ArrayList<>();
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue()) {
                        deniedPermissions.add(entry.getKey());
                    }
                }
                refreshAllPermissions();
                if (!deniedPermissions.isEmpty()) {
                    showPermissionDeniedDialog(deniedPermissions.get(0));
                } else {
                    showToast(getString(R.string.toast_permission_granted));
                }
            });

    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                refreshAllPermissions();
                showToast(getString(R.string.toast_permission_granted));
            });

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_permission_manager;
    }

    @Override
    protected void initViews() {
        binding = ActivityPermissionManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.title_permission_manager), true);
        
        adapter = new PermissionAdapter(allPermissions);
        binding.permissionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.permissionRecyclerView.setAdapter(adapter);
    }

    @Override
    protected void initData() {
        createAllPermissionItems();
    }

    @Override
    protected void setupListeners() {
        binding.btnGrantAllContainer.setOnClickListener(v -> requestAllPermissions());
        binding.btnRefreshContainer.setOnClickListener(v -> {
            refreshAllPermissions();
            showToast(getString(R.string.permission_refreshed));
        });
        binding.btnPluginPermissionContainer.setOnClickListener(v -> {
            startActivity(new Intent(this, PluginPermissionManagerActivity.class));
        });
    }

    private void createAllPermissionItems() {
        // 存储权限
        addPermissionItem(R.drawable.ic_folder, R.string.perm_category_storage,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "MANAGE_EXTERNAL_STORAGE");

        // 网络权限
        addPermissionItem(R.drawable.ic_network, R.string.perm_category_network,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE);

        // 相机权限
        addPermissionItem(R.drawable.ic_camera, R.string.perm_category_camera,
                Manifest.permission.CAMERA);

        // 麦克风权限
        addPermissionItem(R.drawable.ic_microphone, R.string.perm_category_microphone,
                Manifest.permission.RECORD_AUDIO);

        // 位置权限
        addPermissionItem(R.drawable.ic_location, R.string.perm_category_location,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        // 电话权限
        addPermissionItem(R.drawable.ic_phone, R.string.perm_category_phone,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE);

        // 短信权限
        addPermissionItem(R.drawable.ic_message, R.string.perm_category_sms,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS);

        // 联系人权限
        addPermissionItem(R.drawable.ic_contacts, R.string.perm_category_contacts,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS);

        // 日历权限
        addPermissionItem(R.drawable.ic_calendar, R.string.perm_category_calendar,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR);

        // 系统权限
        addPermissionItem(R.drawable.ic_settings, R.string.perm_category_system,
                "SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS",
                "POST_NOTIFICATIONS", Manifest.permission.VIBRATE,
                Manifest.permission.WAKE_LOCK, "FLASHLIGHT");

        // 无障碍权限
        addPermissionItem(R.drawable.ic_accessibility, R.string.perm_category_accessibility,
                "ACCESSIBILITY");

        // 高级权限
        addPermissionItem(R.drawable.ic_security, R.string.perm_category_advanced,
                "REQUEST_INSTALL_PACKAGES", "PACKAGE_USAGE_STATS");

        // 权限增强工具
        addPermissionItem(R.drawable.ic_developer_mode, R.string.perm_category_tools,
                "ROOT", "SHIZUKU", "DHIZUKU");
    }

    private void addPermissionItem(int iconRes, int categoryRes, String... permissions) {
        String category = getString(categoryRes);
        for (String permission : permissions) {
            boolean isSpecial = isSpecialPermission(permission);
            allPermissions.add(new PermissionItem(iconRes, category, permission, isSpecial));
        }
        // 使用 Handler 延迟更新，避免在 RecyclerView 布局期间修改
        mainHandler.post(() -> {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private boolean isSpecialPermission(String permission) {
        return permission.equals("MANAGE_EXTERNAL_STORAGE") ||
                permission.equals("SYSTEM_ALERT_WINDOW") ||
                permission.equals("WRITE_SETTINGS") ||
                permission.equals("REQUEST_INSTALL_PACKAGES") ||
                permission.equals("PACKAGE_USAGE_STATS") ||
                permission.equals("ACCESSIBILITY");
    }

    private boolean checkPermission(String permission) {
        return PermissionHelper.hasPermission(this, permission) ||
                PermissionHelper.hasSpecialPermission(this, permission);
    }

    private void refreshAllPermissions() {
        // 使用 Handler 延迟更新，避免在 RecyclerView 布局期间修改
        mainHandler.post(() -> {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void requestPermission(String permission) {
        if (isSpecialPermission(permission)) {
            PermissionHelper.requestSpecialPermission(this, permission, settingsLauncher);
        } else {
            multiplePermissionsLauncher.launch(new String[]{permission});
        }
    }

    private void requestAllPermissions() {
        List<String> normalPermissions = new ArrayList<>();
        List<String> specialPermissions = new ArrayList<>();

        for (PermissionItem item : allPermissions) {
            if (!checkPermission(item.permission)) {
                if (item.isSpecial) {
                    specialPermissions.add(item.permission);
                } else {
                    normalPermissions.add(item.permission);
                }
            }
        }

        if (!normalPermissions.isEmpty()) {
            multiplePermissionsLauncher.launch(normalPermissions.toArray(new String[0]));
        }

        for (String permission : specialPermissions) {
            PermissionHelper.requestSpecialPermission(this, permission, settingsLauncher);
        }

        if (specialPermissions.isEmpty() && normalPermissions.isEmpty()) {
            showToast(getString(R.string.toast_permission_granted));
        } else if (!specialPermissions.isEmpty()) {
            showToast(getString(R.string.permission_grant_one_by_one));
        }
    }

    private void showPermissionDeniedDialog(String permission) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_warning))
                .setMessage(getString(R.string.permission_permanently_denied,
                        PermissionHelper.getPermissionDisplayName(this, permission)))
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class PermissionItem {
        int iconRes;
        String category;
        String permission;
        boolean isSpecial;

        PermissionItem(int iconRes, String category, String permission, boolean isSpecial) {
            this.iconRes = iconRes;
            this.category = category;
            this.permission = permission;
            this.isSpecial = isSpecial;
        }
    }

    private class PermissionAdapter extends RecyclerView.Adapter<PermissionAdapter.ViewHolder> {
        private List<PermissionItem> items;

        PermissionAdapter(List<PermissionItem> items) {
            this.items = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_permission, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PermissionItem item = items.get(position);
            boolean granted = checkPermission(item.permission);

            holder.icon.setImageResource(item.iconRes);
            holder.icon.setColorFilter(getColor(R.color.primary));
            
            holder.tvPermission.setText(PermissionHelper.getPermissionDisplayName(
                    PermissionManagerActivity.this, item.permission));
            holder.tvCategory.setText(item.category);
            holder.tvCategory.setTextColor(getColor(R.color.text_secondary));

            // 避免在绑定过程中触发事件
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(granted);
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !granted) {
                    requestPermission(item.permission);
                }
            });

            holder.itemView.setOnClickListener(v -> requestPermission(item.permission));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView tvPermission;
            TextView tvCategory;
            CheckBox checkBox;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.iv_permission_icon);
                tvPermission = itemView.findViewById(R.id.tv_permission_name);
                tvCategory = itemView.findViewById(R.id.tv_permission_category);
                checkBox = itemView.findViewById(R.id.cb_permission_granted);
            }
        }
    }
}