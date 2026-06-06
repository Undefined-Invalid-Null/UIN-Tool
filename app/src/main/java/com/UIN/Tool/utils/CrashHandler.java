package com.UIN.Tool.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static CrashHandler instance;
    private Context context;
    private Thread.UncaughtExceptionHandler defaultHandler;

    private CrashHandler() {}

    public static CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        
        // 写入启动日志到多个位置
        String startLog = "=== 应用启动 ===\n" +
                "设备: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "时间: " + getCurrentTime() + "\n" +
                "=================\n";
        
        writeToInternalLog(startLog);
        writeToExternalLog(startLog);
        
        // 清除崩溃标记
        setCrashFlag(false);
        
        Log.i(TAG, "CrashHandler 初始化完成");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // 标记发生了崩溃
        setCrashFlag(true);
        
        // 获取详细崩溃日志
        String crashLog = getDetailedCrashLog(thread, ex);
        
        // 写入到多个位置
        writeToInternalLog(crashLog);
        writeToExternalLog(crashLog);
        
        // 也尝试写入 Logcat
        Log.e(TAG, "========== 应用崩溃 ==========");
        Log.e(TAG, "线程: " + thread.getName());
        Log.e(TAG, "异常: " + ex.getMessage());
        Log.e(TAG, Log.getStackTraceString(ex));
        
        // 延迟确保日志写入完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        
        // 重启应用
        restartApp();
        
        // 杀死进程
        Process.killProcess(Process.myPid());
        System.exit(1);
    }
    
    private void setCrashFlag(boolean crashed) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("crash", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("just_crashed", crashed).apply();
        } catch (Exception e) {
            Log.e(TAG, "设置崩溃标记失败", e);
        }
    }
    
    private String getDetailedCrashLog(Thread thread, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("================== 崩溃报告 ==================\n");
        sb.append("时间: ").append(getCurrentTime()).append("\n");
        sb.append("线程: ").append(thread.getName()).append("\n");
        sb.append("线程ID: ").append(thread.getId()).append("\n");
        sb.append("线程优先级: ").append(thread.getPriority()).append("\n");
        sb.append("异常类型: ").append(ex.getClass().getName()).append("\n");
        sb.append("异常信息: ").append(ex.getMessage()).append("\n");
        sb.append("\n堆栈信息:\n");
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        sb.append(sw.toString());
        
        // 获取导致异常的根原因
        Throwable cause = ex.getCause();
        if (cause != null && cause != ex) {
            sb.append("\n根原因:\n");
            StringWriter sw2 = new StringWriter();
            PrintWriter pw2 = new PrintWriter(sw2);
            cause.printStackTrace(pw2);
            sb.append(sw2.toString());
        }
        
        sb.append("================== 崩溃报告结束 ==================\n");
        
        return sb.toString();
    }
    
    private void writeToInternalLog(String log) {
        try {
            File logFile = new File(context.getFilesDir(), "crash_log.txt");
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(log);
            fw.write("\n");
            fw.close();
            
            // 限制文件大小
            if (logFile.length() > 1024 * 1024) {
                truncateLogFile(logFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "写入内部日志失败", e);
        }
    }
    
    private void writeToExternalLog(String log) {
        try {
            File logDir = new File("/storage/emulated/0/UIN_Tool/logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            File logFile = new File(logDir, "crash_" + date + ".log");
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(log);
            fw.write("\n");
            fw.close();
        } catch (Exception e) {
            // 无法写入外部存储，忽略
            Log.e(TAG, "写入外部日志失败", e);
        }
    }
    
    private void truncateLogFile(File logFile) {
        try {
            if (logFile.length() > 500 * 1024) {
                // 保留最后 50KB
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile, "rw");
                long length = raf.length();
                raf.seek(length - 50 * 1024);
                byte[] buffer = new byte[50 * 1024];
                raf.read(buffer);
                raf.setLength(0);
                raf.write(buffer);
                raf.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "截断日志文件失败", e);
        }
    }
    
    private void restartApp() {
        try {
            // 先尝试打开日志界面
            Intent intent = new Intent();
            intent.setClassName(context, "com.UIN.Tool.ui.log.LogViewerActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("auto_open", true);
            context.startActivity(intent);
        } catch (Exception e1) {
            try {
                // 如果失败，尝试打开主界面
                Intent intent = new Intent();
                intent.setClassName(context, "com.UIN.Tool.MainActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            } catch (Exception e2) {
                Log.e(TAG, "重启应用失败", e2);
            }
        }
    }
    
    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }
}