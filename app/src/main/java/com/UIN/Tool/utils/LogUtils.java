package com.UIN.Tool.utils;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogUtils {

    private static File logFile;
    private static boolean isInitialized = false;
    private static final int MAX_LOG_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int MAX_LOG_LINES = 2000;
    private static final String LOG_DIR = "/storage/emulated/0/UIN_Tool/logs";

    public static void init(Context context) {
        if (isInitialized) return;
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 使用日期作为文件名，避免覆盖
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            logFile = new File(logDir, "uin_tool_" + date + ".log");
            
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            
            String time = getCurrentTime();
            writeLog("══════════════════════════════════════════════════");
            writeLog("  UIN Tool 日志开始");
            writeLog("  时间: " + time);
            writeLog("  设备: " + Build.MANUFACTURER + " " + Build.MODEL);
            writeLog("  Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            writeLog("══════════════════════════════════════════════════");
            isInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    private static synchronized void writeLog(String logLine) {
        if (logFile == null) return;
        try {
            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write((logLine + "\n").getBytes());
            fos.close();
            if (logFile.length() > MAX_LOG_SIZE) {
                truncateLog();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void truncateLog() {
        try {
            File tempFile = new File(logFile.getParent(), "log_temp.txt");
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            int keepLines = Math.min(MAX_LOG_LINES, lines.size());
            int startIndex = lines.size() - keepLines;
            FileOutputStream fos = new FileOutputStream(logFile);
            for (int i = startIndex; i < lines.size(); i++) {
                fos.write((lines.get(i) + "\n").getBytes());
            }
            fos.close();
            fos = new FileOutputStream(logFile, true);
            fos.write(("\n... 日志已截断，保留最近 " + keepLines + " 行 ...\n").getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    // ==================== 基础日志方法 ====================

    public static void d(String tag, String message) {
        String log = getCurrentTime() + " [DEBUG][ " + tag + " ] " + message;
        System.out.println(log);
        writeLog(log);
    }

    public static void i(String tag, String message) {
        String log = getCurrentTime() + " [INFO ][ " + tag + " ] " + message;
        System.out.println(log);
        writeLog(log);
    }

    public static void w(String tag, String message) {
        String log = getCurrentTime() + " [WARN ][ " + tag + " ] ⚠ " + message;
        System.out.println(log);
        writeLog(log);
    }

    public static void e(String tag, String message) {
        String log = getCurrentTime() + " [ERROR][ " + tag + " ] ✗ " + message;
        System.err.println(log);
        writeLog(log);
    }

    public static void e(String tag, String message, Throwable throwable) {
        String log = getCurrentTime() + " [ERROR][ " + tag + " ] ✗ " + message + " - " + throwable.getMessage();
        System.err.println(log);
        writeLog(log);
        ex(tag, throwable);
    }

    public static void ex(String tag, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String[] lines = sw.toString().split("\n");
        for (int i = 0; i < Math.min(lines.length, 15); i++) {
            writeLog(getCurrentTime() + " [ERROR][ " + tag + " ]     " + lines[i]);
        }
    }

    public static void success(String tag, String message) {
        String log = getCurrentTime() + " [SUCCESS][ " + tag + " ] ✓ " + message;
        System.out.println(log);
        writeLog(log);
    }

    // ==================== 高级日志方法 ====================

    public static void enter(String tag, String methodName) {
        d(tag, "→ " + methodName + "() 进入");
    }

    public static void exit(String tag, String methodName, long startTime) {
        long cost = System.currentTimeMillis() - startTime;
        d(tag, "← " + methodName + "() 退出, 耗时: " + cost + "ms");
    }

    public static void separator(String tag, String title) {
        writeLog(getCurrentTime() + " [INFO ][ " + tag + " ] ══════════════════════════════════════════════════");
        if (title != null && !title.isEmpty()) {
            writeLog(getCurrentTime() + " [INFO ][ " + tag + " ]   " + title);
            writeLog(getCurrentTime() + " [INFO ][ " + tag + " ] ──────────────────────────────────────────────────");
        }
    }

    public static void param(String tag, String key, String value) {
        d(tag, "  📌 " + key + " = " + value);
    }

    public static void param(String tag, String key, int value) {
        d(tag, "  📌 " + key + " = " + value);
    }

    public static void param(String tag, String key, long value) {
        d(tag, "  📌 " + key + " = " + value);
    }

    public static void param(String tag, String key, boolean value) {
        d(tag, "  📌 " + key + " = " + value);
    }

    public static void file(String tag, String operation, File file) {
        String size = "";
        if (file.exists() && file.isFile()) size = ", 大小: " + formatFileSize(file.length());
        writeLog(getCurrentTime() + " [FILE  ][ " + tag + " ] 📁 " + operation + ": " + file.getAbsolutePath() + size);
    }

    public static void detail(String tag, String title, String content) {
        i(tag, "┌────────────────────────────────────────");
        i(tag, "│ " + title);
        i(tag, "├────────────────────────────────────────");
        String[] lines = content.split("\n");
        for (String line : lines) {
            i(tag, "│ " + line);
        }
        i(tag, "└────────────────────────────────────────");
    }

    public static void action(String tag, String action, String target) {
        writeLog(getCurrentTime() + " [ACTION ][ " + tag + " ] ▶ 执行 " + action + ": " + target);
    }

    public static void result(String tag, String operation, boolean success) {
        if (success) {
            writeLog(getCurrentTime() + " [RESULT ][ " + tag + " ] ✓ " + operation + " 成功");
        } else {
            writeLog(getCurrentTime() + " [RESULT ][ " + tag + " ] ✗ " + operation + " 失败");
        }
    }

    // ==================== 文件管理方法 ====================

    public static File getLogFile() { 
        return logFile; 
    }
    
    public static String getLogDir() { 
        return LOG_DIR; 
    }
    
    public static void clear(Context context) {
        try {
            if (logFile != null && logFile.exists()) {
                logFile.delete();
            }
            // 重新初始化，创建新文件
            isInitialized = false;
            init(context);
            i("LogUtils", "日志已清空");
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
    
    public static String readLog() {
        if (logFile == null || !logFile.exists()) return "";
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) { 
            return "读取日志失败: " + e.getMessage(); 
        }
    }
    
    public static List<File> getAllLogFiles() {
        List<File> logFiles = new ArrayList<>();
        try {
            File logDir = new File(LOG_DIR);
            if (logDir.exists()) {
                File[] files = logDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".log")) {
                            logFiles.add(file);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logFiles;
    }
}