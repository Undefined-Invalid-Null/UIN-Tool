package com.UIN.Tool.plugin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import java.util.List;

/**
 * 插件接口 - Stub 版本
 * 对应: plugin/PluginInterface.kt
 * 注意: 所有 default 方法改为 abstract
 */
public interface PluginInterface {

    // ==================== 生命周期 ====================
    View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState);

    void onResume();

    void onPause();

    void onDestroy();

    boolean onBackPressed();

    Bundle onSaveInstanceState();

    void onActivityResult(int requestCode, int resultCode, Intent data);

    void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults);

    // ==================== 宿主 UI 集成 ====================
    String getPluginTitle();

    List<PluginMenuItem> getPluginMenuItems();

    Fragment getPluginFragment();

    View getPluginView(Context context, ViewGroup container);

    View getConfigView(Context context);

    void onHostEvent(String event, Bundle data);

    void sendHostEvent(String event, Bundle data);

    <T> T getHostService(Class<T> serviceClass);
}

/**
 * 插件菜单项 - Stub 版本
 * 对应: PluginInterface.kt 中的 data class PluginMenuItem
 */
class PluginMenuItem {
    private int id;
    private String title;
    private Integer icon;
    private Runnable onClick;

    public PluginMenuItem(int id, String title, Integer icon, Runnable onClick) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.onClick = onClick;
    }

    public PluginMenuItem(int id, String title, Runnable onClick) {
        this(id, title, null, onClick);
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public Integer getIcon() { return icon; }
    public Runnable getOnClick() { return onClick; }
}

/**
 * 宿主服务接口 - Stub 版本
 * 对应: PluginInterface.kt 中的 interface HostService
 */
interface HostService {
    void showToast(String message, int duration);
    HostLoadingDialog showLoading(String message);
    void hideLoading();
    void showDialog(String title, String message, String positiveText, String negativeText,
                    Runnable onPositive, Runnable onNegative);
    String getWorkFolder();
    void openUrl(String url);
    void shareText(String text);
    void copyToClipboard(String text);
    PluginManager getPluginManager();
}

/**
 * 加载对话框接口 - Stub 版本
 * 对应: PluginInterface.kt 中的 interface HostLoadingDialog
 */
interface HostLoadingDialog {
    void setMessage(String message);
    void setProgress(int progress);
    void dismiss();
}

/**
 * PluginManager 存根 - 仅用于编译
 */
class PluginManager {
    public static PluginManager getInstance(Context context) { return null; }
}