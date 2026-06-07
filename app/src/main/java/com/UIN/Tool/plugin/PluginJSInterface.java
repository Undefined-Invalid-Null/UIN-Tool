package com.UIN.Tool.plugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.UIN.Tool.utils.LogUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PluginJSInterface {

    private final Context context;
    private final String pluginId;
    private final PluginInfo pluginInfo;
    private SensorManager sensorManager;
    private SensorEventListener sensorListener;
    private String currentSensorType = null;
    private OkHttpClient httpClient;

    public PluginJSInterface(Context context, String pluginId, PluginInfo pluginInfo) {
        this.context = context;
        this.pluginId = pluginId;
        this.pluginInfo = pluginInfo;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ==================== 基础功能 ====================

    @JavascriptInterface
    public void callHost(String action, String data) {
        LogUtils.i("PluginJSInterface", "JS 调用: " + action + " -> " + data);
        
        if (data == null) data = "";

        switch (action) {
            case "toast":
                showToast(data);
                break;
            case "finish":
                closePlugin();
                break;
            case "log":
                LogUtils.i("WebPlugin[" + pluginId + "]", data);
                break;
            case "alert":
                showAlert(data);
                break;
            case "confirm":
                showConfirm(data);
                break;
            case "vibrate":
                vibrate(data);
                break;
            case "copy":
                copyToClipboard(data);
                break;
            case "openUrl":
                openUrl(data);
                break;
            case "share":
                share(data);
                break;
            default:
                LogUtils.w("PluginJSInterface", "未知调用: " + action);
        }
    }

    @JavascriptInterface
    public void callPlugin(String method, String params) {
        LogUtils.i("PluginJSInterface", "调用插件方法: " + method + " -> " + params);
    }

    // ==================== 网络请求 ====================

    @JavascriptInterface
    public void httpGet(String url, final String callbackId) {
        if (url == null || url.isEmpty()) {
            sendCallbackToJs(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}");
            return;
        }
        
        LogUtils.i("PluginJSInterface", "HTTP GET: " + url);
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String error = "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
                sendCallbackToJs(callbackId, error);
                LogUtils.e("PluginJSInterface", "GET 请求失败", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                JSONObject result = new JSONObject();
                try {
                    result.put("success", true);
                    result.put("statusCode", response.code());
                    result.put("data", body);
                    sendCallbackToJs(callbackId, result.toString());
                } catch (JSONException e) {
                    sendCallbackToJs(callbackId, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
                response.close();
            }
        });
    }

    @JavascriptInterface
    public void httpPost(String url, String jsonBody, final String callbackId) {
        if (url == null || url.isEmpty()) {
            sendCallbackToJs(callbackId, "{\"success\":false,\"error\":\"URL不能为空\"}");
            return;
        }
        
        LogUtils.i("PluginJSInterface", "HTTP POST: " + url);
        
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonBody != null ? jsonBody : "{}", JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String error = "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
                sendCallbackToJs(callbackId, error);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject result = new JSONObject();
                try {
                    result.put("success", true);
                    result.put("statusCode", response.code());
                    result.put("data", responseBody);
                    sendCallbackToJs(callbackId, result.toString());
                } catch (JSONException e) {
                    sendCallbackToJs(callbackId, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
                response.close();
            }
        });
    }

    // ==================== 文件系统 ====================

    @JavascriptInterface
    public boolean writeFile(String fileName, String content) {
        if (fileName == null || fileName.isEmpty()) {
            LogUtils.e("PluginJSInterface", "文件名不能为空");
            return false;
        }
        
        try {
            File pluginDir = new File("/storage/emulated/0/UIN_Tool/" + pluginId);
            if (!pluginDir.exists()) {
                pluginDir.mkdirs();
            }
            
            File file = new File(pluginDir, fileName);
            // 安全检查：防止路径遍历
            String canonicalPath = file.getCanonicalPath();
            if (!canonicalPath.startsWith(pluginDir.getCanonicalPath())) {
                LogUtils.e("PluginJSInterface", "非法的文件路径: " + fileName);
                return false;
            }
            
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(content != null ? content : "");
            writer.close();
            fos.close();
            
            LogUtils.i("PluginJSInterface", "写入文件成功: " + fileName);
            return true;
        } catch (Exception e) {
            LogUtils.e("PluginJSInterface", "写入文件失败: " + fileName, e);
            return false;
        }
    }

    @JavascriptInterface
    public String readFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            LogUtils.e("PluginJSInterface", "文件名不能为空");
            return null;
        }
        
        try {
            File pluginDir = new File("/storage/emulated/0/UIN_Tool/" + pluginId);
            File file = new File(pluginDir, fileName);
            
            // 安全检查
            String canonicalPath = file.getCanonicalPath();
            if (!canonicalPath.startsWith(pluginDir.getCanonicalPath())) {
                LogUtils.e("PluginJSInterface", "非法的文件路径: " + fileName);
                return null;
            }
            
            if (!file.exists()) {
                return null;
            }
            
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            fis.close();
            
            LogUtils.i("PluginJSInterface", "读取文件成功: " + fileName);
            return content.toString();
        } catch (Exception e) {
            LogUtils.e("PluginJSInterface", "读取文件失败: " + fileName, e);
            return null;
        }
    }

    @JavascriptInterface
    public boolean deleteFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            LogUtils.e("PluginJSInterface", "文件名不能为空");
            return false;
        }
        
        try {
            File pluginDir = new File("/storage/emulated/0/UIN_Tool/" + pluginId);
            File file = new File(pluginDir, fileName);
            
            String canonicalPath = file.getCanonicalPath();
            if (!canonicalPath.startsWith(pluginDir.getCanonicalPath())) {
                LogUtils.e("PluginJSInterface", "非法的文件路径: " + fileName);
                return false;
            }
            
            if (file.exists()) {
                boolean deleted = file.delete();
                LogUtils.i("PluginJSInterface", "删除文件: " + fileName + " - " + deleted);
                return deleted;
            }
            return false;
        } catch (Exception e) {
            LogUtils.e("PluginJSInterface", "删除文件失败: " + fileName, e);
            return false;
        }
    }

    @JavascriptInterface
    public String[] listFiles(String dirPath) {
        try {
            File pluginDir = new File("/storage/emulated/0/UIN_Tool/" + pluginId);
            File targetDir = (dirPath == null || dirPath.isEmpty()) ? pluginDir : new File(pluginDir, dirPath);
            
            String canonicalPath = targetDir.getCanonicalPath();
            if (!canonicalPath.startsWith(pluginDir.getCanonicalPath())) {
                LogUtils.e("PluginJSInterface", "非法的目录路径: " + dirPath);
                return new String[0];
            }
            
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                return new String[0];
            }
            
            File[] files = targetDir.listFiles();
            if (files == null) {
                return new String[0];
            }
            
            String[] fileNames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                fileNames[i] = files[i].getName();
            }
            
            return fileNames;
        } catch (Exception e) {
            LogUtils.e("PluginJSInterface", "列出文件失败", e);
            return new String[0];
        }
    }

    @JavascriptInterface
    public boolean fileExists(String path) {
        if (path == null || path.isEmpty()) return false;
        File file = new File(path);
        return file.exists();
    }

    // ==================== 传感器 ====================

    @JavascriptInterface
    public void startSensor(String sensorType, final String callbackId) {
        if (sensorType == null || sensorType.isEmpty()) {
            sendCallbackToJs(callbackId, "{\"success\":false,\"error\":\"传感器类型不能为空\"}");
            return;
        }
        
        if (sensorManager == null) {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        
        // 停止当前正在监听的传感器
        if (sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
        }
        
        int sensorTypeInt = getSensorType(sensorType);
        if (sensorTypeInt == -1) {
            sendCallbackToJs(callbackId, "{\"success\":false,\"error\":\"不支持的传感器类型: " + sensorType + "\"}");
            return;
        }
        
        Sensor sensor = sensorManager.getDefaultSensor(sensorTypeInt);
        if (sensor == null) {
            sendCallbackToJs(callbackId, "{\"success\":false,\"error\":\"设备不支持该传感器: " + sensorType + "\"}");
            return;
        }
        
        currentSensorType = sensorType;
        sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                JSONObject data = new JSONObject();
                try {
                    data.put("success", true);
                    data.put("type", sensorType);
                    data.put("timestamp", event.timestamp);
                    data.put("accuracy", event.accuracy);
                    
                    // 根据不同传感器类型组织数据
                    if (sensorTypeInt == Sensor.TYPE_ACCELEROMETER) {
                        data.put("x", event.values[0]);
                        data.put("y", event.values[1]);
                        data.put("z", event.values[2]);
                    } else if (sensorTypeInt == Sensor.TYPE_GYROSCOPE) {
                        data.put("x", event.values[0]);
                        data.put("y", event.values[1]);
                        data.put("z", event.values[2]);
                    } else if (sensorTypeInt == Sensor.TYPE_MAGNETIC_FIELD) {
                        data.put("x", event.values[0]);
                        data.put("y", event.values[1]);
                        data.put("z", event.values[2]);
                    } else if (sensorTypeInt == Sensor.TYPE_LIGHT) {
                        data.put("lux", event.values[0]);
                    } else if (sensorTypeInt == Sensor.TYPE_PROXIMITY) {
                        data.put("distance", event.values[0]);
                    } else if (sensorTypeInt == Sensor.TYPE_PRESSURE) {
                        data.put("pressure", event.values[0]);
                    } else {
                        data.put("values", event.values);
                    }
                    
                    sendCallbackToJs(callbackId, data.toString());
                } catch (JSONException e) {
                    LogUtils.e("PluginJSInterface", "传感器数据解析失败", e);
                }
            }
            
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                JSONObject data = new JSONObject();
                try {
                    data.put("type", "accuracy");
                    data.put("sensorType", sensorType);
                    data.put("accuracy", accuracy);
                    sendCallbackToJs(callbackId, data.toString());
                } catch (JSONException e) {
                    LogUtils.e("PluginJSInterface", "传感器精度变化处理失败", e);
                }
            }
        };
        
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        sendCallbackToJs(callbackId, "{\"success\":true,\"message\":\"传感器启动成功: " + sensorType + "\"}");
        LogUtils.i("PluginJSInterface", "启动传感器: " + sensorType);
    }

    @JavascriptInterface
    public void stopSensor() {
        if (sensorManager != null && sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
            sensorListener = null;
            currentSensorType = null;
            LogUtils.i("PluginJSInterface", "停止所有传感器");
        }
    }

    @JavascriptInterface
    public String getAvailableSensors() {
        if (sensorManager == null) {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        
        JSONObject result = new JSONObject();
        try {
            result.put("accelerometer", sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);
            result.put("gyroscope", sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null);
            result.put("magneticField", sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null);
            result.put("light", sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null);
            result.put("proximity", sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null);
            result.put("pressure", sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null);
            result.put("temperature", sensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE) != null);
        } catch (JSONException e) {
            LogUtils.e("PluginJSInterface", "获取传感器列表失败", e);
        }
        return result.toString();
    }

    private int getSensorType(String sensorType) {
        if (sensorType == null) return -1;
        switch (sensorType.toLowerCase()) {
            case "accelerometer":
                return Sensor.TYPE_ACCELEROMETER;
            case "gyroscope":
                return Sensor.TYPE_GYROSCOPE;
            case "magnetic":
            case "magneticfield":
                return Sensor.TYPE_MAGNETIC_FIELD;
            case "light":
                return Sensor.TYPE_LIGHT;
            case "proximity":
                return Sensor.TYPE_PROXIMITY;
            case "pressure":
                return Sensor.TYPE_PRESSURE;
            case "temperature":
                return Sensor.TYPE_TEMPERATURE;
            default:
                return -1;
        }
    }

    // ==================== 获取信息 ====================

    @JavascriptInterface
    public String getPluginInfo() {
        if (pluginInfo != null) {
            return pluginInfo.toJson();
        }
        return "{}";
    }

    @JavascriptInterface
    public String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("android", Build.VERSION.RELEASE);
            info.put("api", Build.VERSION.SDK_INT);
            info.put("device", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("brand", Build.BRAND);
            info.put("product", Build.PRODUCT);
            info.put("board", Build.BOARD);
            info.put("hardware", Build.HARDWARE);
            info.put("fingerprint", Build.FINGERPRINT);
            
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            info.put("screenWidth", dm.widthPixels);
            info.put("screenHeight", dm.heightPixels);
            info.put("screenDensity", dm.density);
            info.put("screenDensityDpi", dm.densityDpi);
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return info.toString();
    }

    @JavascriptInterface
    public String getNetworkInfo() {
        JSONObject info = new JSONObject();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
            info.put("connected", isConnected);
            if (isConnected) {
                info.put("type", activeNetwork.getTypeName());
                if (activeNetwork.getSubtypeName() != null) {
                    info.put("subtype", activeNetwork.getSubtypeName());
                }
                info.put("isWifi", activeNetwork.getType() == ConnectivityManager.TYPE_WIFI);
                info.put("isMobile", activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return info.toString();
    }

    @JavascriptInterface
    public String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    // ==================== UI 操作 ====================

    @JavascriptInterface
    public void setTitle(String title) {
        LogUtils.i("PluginJSInterface", "设置标题: " + title);
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(() -> activity.setTitle(title));
        }
    }

    @JavascriptInterface
    public void setFullscreen(boolean fullscreen) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(() -> {
                if (fullscreen) {
                    activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    );
                } else {
                    activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                }
            });
        }
    }

    // ==================== 存储操作 ====================

    @JavascriptInterface
    public void setStorage(String key, String value) {
        if (key == null || key.isEmpty()) return;
        context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .edit()
                .putString(key, value != null ? value : "")
                .apply();
        LogUtils.i("PluginJSInterface", "存储数据: " + key + " = " + value);
    }

    @JavascriptInterface
    public String getStorage(String key) {
        if (key == null || key.isEmpty()) return "";
        String value = context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .getString(key, "");
        LogUtils.i("PluginJSInterface", "读取数据: " + key + " = " + value);
        return value;
    }

    @JavascriptInterface
    public void removeStorage(String key) {
        if (key == null || key.isEmpty()) return;
        context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply();
        LogUtils.i("PluginJSInterface", "删除数据: " + key);
    }

    @JavascriptInterface
    public void clearStorage() {
        context.getSharedPreferences("web_plugin_" + pluginId, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        LogUtils.i("PluginJSInterface", "清空所有存储数据");
    }

    // ==================== 系统操作 ====================

    @JavascriptInterface
    public void openSettings() {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        LogUtils.i("PluginJSInterface", "打开系统设置");
    }

    @JavascriptInterface
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        LogUtils.i("PluginJSInterface", "打开应用设置");
    }

    @JavascriptInterface
    public boolean checkPermission(String permission) {
        if (permission == null || permission.isEmpty()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // ==================== 剪贴板功能 ====================

    @JavascriptInterface
    public String paste() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                String text = item.getText().toString();
                LogUtils.i("PluginJSInterface", "粘贴文本: " + (text.length() > 50 ? text.substring(0, 50) + "..." : text));
                return text;
            }
        }
        return "";
    }

    // ==================== 插件目录 ====================

    @JavascriptInterface
    public String getPluginDir() {
        File pluginDir = new File("/storage/emulated/0/UIN_Tool/" + pluginId);
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }
        LogUtils.i("PluginJSInterface", "插件目录: " + pluginDir.getAbsolutePath());
        return pluginDir.getAbsolutePath();
    }

    // ==================== 私有方法 ====================

    private void sendCallbackToJs(String callbackId, String data) {
        if (callbackId == null || callbackId.isEmpty()) return;
        
        if (context instanceof Activity) {
            final Activity activity = (Activity) context;
            activity.runOnUiThread(() -> {
                if (activity instanceof PluginHostActivity) {
                    String js = "if(window.UINPluginCallbacks && window.UINPluginCallbacks['" + callbackId + "']) {" +
                                "    window.UINPluginCallbacks['" + callbackId + "'](" + data + ");" +
                                "}";
                    ((PluginHostActivity) activity).evaluateJs(js);
                }
            });
        }
    }

    private void showToast(String message) {
        if (message == null) message = "";
        final String msg = message;
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }
        LogUtils.i("PluginJSInterface", "显示Toast: " + message);
    }

    private void closePlugin() {
        if (context instanceof Activity) {
            ((Activity) context).finish();
            LogUtils.i("PluginJSInterface", "关闭插件");
        }
    }

    private void showAlert(String message) {
        if (message == null) message = "";
        final String msg = message;
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> {
                new AlertDialog.Builder(context)
                        .setTitle("提示")
                        .setMessage(msg)
                        .setPositiveButton("确定", null)
                        .show();
            });
        }
        LogUtils.i("PluginJSInterface", "显示弹窗: " + message);
    }

    private void showConfirm(String message) {
        if (message == null) message = "";
        final String msg = message;
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> {
                new AlertDialog.Builder(context)
                        .setTitle("确认")
                        .setMessage(msg)
                        .setPositiveButton("确定", (dialog, which) -> {})
                        .setNegativeButton("取消", (dialog, which) -> {})
                        .show();
            });
        }
        LogUtils.i("PluginJSInterface", "显示确认框: " + message);
    }

    private void vibrate(String durationMs) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long duration = Long.parseLong(durationMs);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
                LogUtils.i("PluginJSInterface", "震动: " + duration + "ms");
            }
        } catch (Exception e) {
            LogUtils.e("PluginJSInterface", "震动失败: " + e.getMessage());
        }
    }

    private void copyToClipboard(String text) {
        if (text == null) text = "";
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("plugin_text", text);
        clipboard.setPrimaryClip(clip);
        showToast("已复制到剪贴板");
        LogUtils.i("PluginJSInterface", "复制到剪贴板: " + text);
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) {
            showToast("URL不能为空");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogUtils.i("PluginJSInterface", "打开链接: " + url);
        } catch (Exception e) {
            showToast("无法打开链接");
            LogUtils.e("PluginJSInterface", "打开链接失败: " + e.getMessage());
        }
    }

    private void share(String text) {
        if (text == null) text = "";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, "分享"));
        LogUtils.i("PluginJSInterface", "分享: " + text);
    }
}