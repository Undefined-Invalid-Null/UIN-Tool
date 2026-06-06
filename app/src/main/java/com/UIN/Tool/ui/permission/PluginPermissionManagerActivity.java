package com.UIN.Tool.ui.permission;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginPermissionManagerActivity extends AppCompatActivity {

    private RecyclerView pluginRecyclerView;
    private RecyclerView permissionRecyclerView;
    private PluginAdapter pluginAdapter;
    private PermissionAdapter permissionAdapter;
    
    private String selectedPluginId = null;
    private List<PluginInfo> plugins = new ArrayList<>();
    private List<PermissionGroup> permissionGroups = new ArrayList<>();
    
    private Map<String, Map<String, Boolean>> pluginPermissions = new HashMap<>();
    
    private Button btnSelectAll;
    private Button btnClearAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_permission);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("插件权限管理");
        }
        
        pluginRecyclerView = findViewById(R.id.plugin_recycler_view);
        permissionRecyclerView = findViewById(R.id.permission_recycler_view);
        btnSelectAll = findViewById(R.id.btn_select_all_permissions);
        btnClearAll = findViewById(R.id.btn_clear_all_permissions);
        
        pluginRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        permissionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        loadPlugins();
        loadPermissionGroups();
        
        pluginAdapter = new PluginAdapter();
        pluginRecyclerView.setAdapter(pluginAdapter);
        
        permissionAdapter = new PermissionAdapter();
        permissionRecyclerView.setAdapter(permissionAdapter);
        
        btnSelectAll.setOnClickListener(v -> selectAllPermissions(true));
        btnClearAll.setOnClickListener(v -> selectAllPermissions(false));
    }
    
    private void loadPlugins() {
        PluginManager pm = PluginManager.getInstance(this);
        plugins = pm.getInstalledPlugins();
        if (plugins.isEmpty()) {
            Toast.makeText(this, "暂无已安装的插件", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadPermissionGroups() {
        // 存储权限
        permissionGroups.add(new PermissionGroup("📁 存储权限", new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "MANAGE_EXTERNAL_STORAGE"
        }));
        
        // 网络权限
        permissionGroups.add(new PermissionGroup("🌐 网络权限", new String[]{
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        }));
        
        // 相机权限
        permissionGroups.add(new PermissionGroup("📷 相机权限", new String[]{
            Manifest.permission.CAMERA
        }));
        
        // 麦克风权限
        permissionGroups.add(new PermissionGroup("🎤 麦克风权限", new String[]{
            Manifest.permission.RECORD_AUDIO
        }));
        
        // 位置权限
        permissionGroups.add(new PermissionGroup("📍 位置权限", new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }));
        
        // 电话权限
        permissionGroups.add(new PermissionGroup("📞 电话权限", new String[]{
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        }));
        
        // 短信权限
        permissionGroups.add(new PermissionGroup("📨 短信权限", new String[]{
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        }));
        
        // 联系人权限
        permissionGroups.add(new PermissionGroup("👥 联系人权限", new String[]{
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        }));
        
        // 日历权限
        permissionGroups.add(new PermissionGroup("📅 日历权限", new String[]{
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        }));
        
        // 系统权限
        permissionGroups.add(new PermissionGroup("⚙️ 系统权限", new String[]{
            "SYSTEM_ALERT_WINDOW",
            "WRITE_SETTINGS",
            "POST_NOTIFICATIONS",
            Manifest.permission.VIBRATE,
            Manifest.permission.WAKE_LOCK,
            "FLASHLIGHT",
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.NFC
        }));
        
        // 无障碍权限
        permissionGroups.add(new PermissionGroup("♿ 无障碍权限", new String[]{
            "ACCESSIBILITY"
        }));
        
        // 高级权限
        permissionGroups.add(new PermissionGroup("🔧 高级权限", new String[]{
            "REQUEST_INSTALL_PACKAGES",
            "PACKAGE_USAGE_STATS",
            Manifest.permission.KILL_BACKGROUND_PROCESSES,
            Manifest.permission.RESTART_PACKAGES,
            Manifest.permission.GET_TASKS,
            Manifest.permission.REORDER_TASKS,
            Manifest.permission.CLEAR_APP_CACHE,
            Manifest.permission.EXPAND_STATUS_BAR,
            Manifest.permission.DISABLE_KEYGUARD,
            Manifest.permission.SET_WALLPAPER,
            Manifest.permission.SET_TIME_ZONE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
            Manifest.permission.READ_LOGS
        }));
        
        // 权限增强工具
        permissionGroups.add(new PermissionGroup("🛠️ 权限增强工具", new String[]{
            "ROOT",
            "SHIZUKU",
            "DHIZUKU"
        }));
    }
    
    private void loadPluginPermissions(String pluginId) {
        String prefsName = "plugin_perms_" + pluginId;
        android.content.SharedPreferences prefs = getSharedPreferences(prefsName, MODE_PRIVATE);
        
        Map<String, Boolean> perms = new HashMap<>();
        for (PermissionGroup group : permissionGroups) {
            for (String perm : group.permissions) {
                perms.put(perm, prefs.getBoolean(perm, false));
            }
        }
        pluginPermissions.put(pluginId, perms);
    }
    
    private void savePluginPermissions(String pluginId) {
        Map<String, Boolean> perms = pluginPermissions.get(pluginId);
        if (perms == null) return;
        
        String prefsName = "plugin_perms_" + pluginId;
        android.content.SharedPreferences prefs = getSharedPreferences(prefsName, MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        for (Map.Entry<String, Boolean> entry : perms.entrySet()) {
            editor.putBoolean(entry.getKey(), entry.getValue());
        }
        editor.apply();
        
        Toast.makeText(this, "权限配置已保存", Toast.LENGTH_SHORT).show();
    }
    
    private void selectAllPermissions(boolean selectAll) {
        if (selectedPluginId == null) {
            Toast.makeText(this, "请先选择一个插件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Map<String, Boolean> perms = pluginPermissions.get(selectedPluginId);
        if (perms == null) {
            loadPluginPermissions(selectedPluginId);
            perms = pluginPermissions.get(selectedPluginId);
        }
        
        for (String perm : perms.keySet()) {
            perms.put(perm, selectAll);
        }
        
        savePluginPermissions(selectedPluginId);
        permissionAdapter.notifyDataSetChanged();
        
        Toast.makeText(this, selectAll ? "已授予所有权限" : "已撤销所有权限", Toast.LENGTH_SHORT).show();
    }
    
    private class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.ViewHolder> {
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PluginInfo info = plugins.get(position);
            holder.textView.setText(info.name);
            holder.textView.setOnClickListener(v -> {
                selectedPluginId = info.pluginId;
                loadPluginPermissions(selectedPluginId);
                permissionAdapter.notifyDataSetChanged();
                pluginAdapter.notifyDataSetChanged();
            });
            
            if (info.pluginId.equals(selectedPluginId)) {
                holder.textView.setBackgroundColor(getResources().getColor(R.color.primary));
                holder.textView.setTextColor(getResources().getColor(android.R.color.white));
            } else {
                holder.textView.setBackgroundColor(0);
                holder.textView.setTextColor(getResources().getColor(R.color.black));
            }
        }
        
        @Override
        public int getItemCount() {
            return plugins.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
                textView.setPadding(32, 16, 16, 16);
            }
        }
    }
    
    private class PermissionAdapter extends RecyclerView.Adapter<PermissionAdapter.ViewHolder> {
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_permission_detail, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PermissionGroup group = permissionGroups.get(position);
            holder.groupTitle.setText(group.name);
            
            holder.permissionContainer.removeAllViews();
            
            if (selectedPluginId == null) {
                TextView tip = new TextView(holder.itemView.getContext());
                tip.setText("请先选择一个插件");
                tip.setPadding(16, 8, 16, 8);
                holder.permissionContainer.addView(tip);
                return;
            }
            
            Map<String, Boolean> perms = pluginPermissions.get(selectedPluginId);
            if (perms == null) {
                loadPluginPermissions(selectedPluginId);
                perms = pluginPermissions.get(selectedPluginId);
            }
            
            for (String perm : group.permissions) {
                addPermissionItem(holder.permissionContainer, perm, perms);
            }
        }
        
        private void addPermissionItem(LinearLayout container, String permission, Map<String, Boolean> perms) {
            LinearLayout itemLayout = new LinearLayout(container.getContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(16, 12, 16, 12);
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            
            String displayName = getPermissionDisplayName(permission);
            TextView nameView = new TextView(container.getContext());
            nameView.setText(displayName);
            nameView.setTextSize(13);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            
            CheckBox checkBox = new CheckBox(container.getContext());
            Boolean isChecked = perms.get(permission);
            checkBox.setChecked(isChecked != null && isChecked);
            checkBox.setOnCheckedChangeListener((buttonView, checkedFlag) -> {
                perms.put(permission, checkedFlag);
                if (selectedPluginId != null) {
                    savePluginPermissions(selectedPluginId);
                }
            });
            
            // 无障碍权限特殊处理 - 添加提示
            if ("ACCESSIBILITY".equals(permission)) {
                TextView hintView = new TextView(container.getContext());
                hintView.setText("需要在系统设置中手动开启");
                hintView.setTextSize(10);
                hintView.setTextColor(getResources().getColor(R.color.gray_dark));
                hintView.setPadding(8, 0, 8, 0);
                itemLayout.addView(hintView);
            }
            
            itemLayout.addView(nameView);
            itemLayout.addView(checkBox);
            container.addView(itemLayout);
        }
        
        private String getPermissionDisplayName(String permission) {
            switch (permission) {
                case "android.permission.READ_EXTERNAL_STORAGE": return "读取存储";
                case "android.permission.WRITE_EXTERNAL_STORAGE": return "写入存储";
                case "MANAGE_EXTERNAL_STORAGE": return "管理所有文件";
                case "android.permission.INTERNET": return "访问网络";
                case "android.permission.ACCESS_NETWORK_STATE": return "获取网络状态";
                case "android.permission.ACCESS_WIFI_STATE": return "获取WiFi状态";
                case "android.permission.CAMERA": return "相机";
                case "android.permission.RECORD_AUDIO": return "录音";
                case "android.permission.ACCESS_FINE_LOCATION": return "精确位置";
                case "android.permission.ACCESS_COARSE_LOCATION": return "粗略位置";
                case "android.permission.ACCESS_BACKGROUND_LOCATION": return "后台位置";
                case "android.permission.CALL_PHONE": return "拨打电话";
                case "android.permission.READ_PHONE_STATE": return "读取手机状态";
                case "android.permission.SEND_SMS": return "发送短信";
                case "android.permission.READ_SMS": return "读取短信";
                case "android.permission.RECEIVE_SMS": return "接收短信";
                case "android.permission.READ_CONTACTS": return "读取联系人";
                case "android.permission.WRITE_CONTACTS": return "写入联系人";
                case "android.permission.READ_CALENDAR": return "读取日历";
                case "android.permission.WRITE_CALENDAR": return "写入日历";
                case "SYSTEM_ALERT_WINDOW": return "悬浮窗";
                case "WRITE_SETTINGS": return "修改系统设置";
                case "POST_NOTIFICATIONS": return "通知";
                case "android.permission.VIBRATE": return "震动";
                case "android.permission.WAKE_LOCK": return "唤醒锁";
                case "FLASHLIGHT": return "闪光灯";
                case "android.permission.BLUETOOTH": return "蓝牙";
                case "android.permission.BLUETOOTH_ADMIN": return "蓝牙管理";
                case "android.permission.NFC": return "NFC";
                case "ACCESSIBILITY": return "无障碍权限";
                case "REQUEST_INSTALL_PACKAGES": return "安装未知应用";
                case "PACKAGE_USAGE_STATS": return "使用情况访问";
                case "android.permission.KILL_BACKGROUND_PROCESSES": return "结束后台进程";
                case "android.permission.RESTART_PACKAGES": return "重启应用";
                case "android.permission.GET_TASKS": return "获取任务信息";
                case "android.permission.REORDER_TASKS": return "重新排序任务";
                case "android.permission.CLEAR_APP_CACHE": return "清除应用缓存";
                case "android.permission.EXPAND_STATUS_BAR": return "展开状态栏";
                case "android.permission.DISABLE_KEYGUARD": return "禁用键盘锁";
                case "android.permission.SET_WALLPAPER": return "设置壁纸";
                case "android.permission.SET_TIME_ZONE": return "设置时区";
                case "android.permission.MODIFY_AUDIO_SETTINGS": return "修改音频设置";
                case "android.permission.MOUNT_UNMOUNT_FILESYSTEMS": return "挂载文件系统";
                case "android.permission.READ_LOGS": return "读取日志";
                case "ROOT": return "Root 权限";
                case "SHIZUKU": return "Shizuku";
                case "DHIZUKU": return "Dhizuku";
                default: return permission;
            }
        }
        
        @Override
        public int getItemCount() {
            return permissionGroups.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView groupTitle;
            LinearLayout permissionContainer;
            ViewHolder(View itemView) {
                super(itemView);
                groupTitle = itemView.findViewById(R.id.group_title);
                permissionContainer = itemView.findViewById(R.id.permission_container);
            }
        }
    }
    
    private static class PermissionGroup {
        String name;
        String[] permissions;
        PermissionGroup(String name, String[] permissions) {
            this.name = name;
            this.permissions = permissions;
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}