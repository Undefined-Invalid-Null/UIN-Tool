package com.UIN.Tool.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.UIN.Tool.utils.LogUtils;

public class SystemEventReceiver extends BroadcastReceiver {

    private static final String TAG = "SystemEventReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        long uptime = SystemClock.uptimeMillis();

        LogUtils.i(TAG, "Received intent at " + uptime + "ms: " + action);

        if (action == null) {
            return;
        }

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                // 延迟刷新，确保系统完全启动
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    LogUtils.i(TAG, "Refreshing widgets after system event: " + action);
                    UINWidgetProvider.sendIntentToRefreshAllWidgets(context);
                }, 5000);
                break;

            default:
                LogUtils.d(TAG, "Unhandled action: " + action);
                break;
        }
    }
}