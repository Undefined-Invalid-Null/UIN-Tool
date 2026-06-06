package com.UIN.Tool;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.UIN.Tool.plugin.PluginManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UinApplication extends Application {
    
    private static UinApplication instance;
    
    static {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                writeCrashLogImmediately(thread, ex);
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // 初始化 PluginManager
        PluginManager.getInstance(this);
        
        try {
            boolean hasCrashed = getSharedPreferences("crash", MODE_PRIVATE).getBoolean("just_crashed", false);
            if (hasCrashed) {
                getSharedPreferences("crash", MODE_PRIVATE).edit().putBoolean("just_crashed", false).apply();
                
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        Intent intent = new Intent(this, Class.forName("com.UIN.Tool.ui.log.LogViewerActivity"));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("auto_open", true);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        Log.i("UinApplication", "应用启动");
    }
    
    private static void writeCrashLogImmediately(Thread thread, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("================== 崩溃报告 ==================\n");
        sb.append("时间: ").append(getCurrentTime()).append("\n");
        sb.append("线程: ").append(thread.getName()).append("\n");
        sb.append("异常类型: ").append(ex.getClass().getName()).append("\n");
        sb.append("异常信息: ").append(ex.getMessage()).append("\n");
        sb.append("堆栈信息:\n");
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        sb.append(sw.toString());
        
        Throwable cause = ex.getCause();
        if (cause != null) {
            sb.append("\n根原因:\n");
            StringWriter sw2 = new StringWriter();
            PrintWriter pw2 = new PrintWriter(sw2);
            cause.printStackTrace(pw2);
            sb.append(sw2.toString());
        }
        
        sb.append("================== 崩溃报告结束 ==================\n");
        
        try {
            File logDir = new File("/storage/emulated/0/UIN_Tool/logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            File logFile = new File(logDir, "crash_" + date + ".log");
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(sb.toString());
            fw.close();
        } catch (Exception e) {}
        
        try {
            File crashFile = new File("/data/data/com.UIN.Tool/files/crash_log.txt");
            File parent = crashFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileWriter fw = new FileWriter(crashFile, true);
            fw.write(sb.toString());
            fw.close();
        } catch (Exception e) {}
        
        Log.e("CRASH", sb.toString());
    }
    
    private static String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }
    
    public static UinApplication getInstance() {
        return instance;
    }
}