package com.UIN.Tool.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * UI 配置管理器
 * 支持用户自定义主题、形状、字体等
 */
public class UIConfig {
    
    private static UIConfig instance;
    private Context context;
    private JSONObject config;
    private String currentThemeName = "默认主题";
    
    private static final String CONFIG_FILE = "ui_config.json";
    private static final String PREF_NAME = "ui_prefs";
    private static final String KEY_CURRENT_THEME = "current_theme";
    
    private UIConfig(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
    }
    
    public static UIConfig getInstance(Context context) {
        if (instance == null) {
            instance = new UIConfig(context);
        }
        return instance;
    }
    
    /**
     * 加载配置 - 改为 public
     */
    public void loadConfig() {
        try {
            File userConfig = new File(context.getFilesDir(), CONFIG_FILE);
            if (userConfig.exists()) {
                String json = readFileToString(userConfig);
                config = new JSONObject(json);
                LogUtils.i("UIConfig", "从用户目录加载配置");
            } else {
                InputStream is = context.getAssets().open(CONFIG_FILE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                is.close();
                config = new JSONObject(sb.toString());
                LogUtils.i("UIConfig", "从 assets 加载默认配置");
            }
            
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            currentThemeName = prefs.getString(KEY_CURRENT_THEME, "默认主题");
            
        } catch (Exception e) {
            LogUtils.e("UIConfig", "加载配置失败: " + e.getMessage());
            config = getDefaultConfig();
        }
    }
    
    /**
     * 保存配置
     */
    public void saveConfig() {
        try {
            File userConfig = new File(context.getFilesDir(), CONFIG_FILE);
            FileOutputStream fos = new FileOutputStream(userConfig);
            fos.write(config.toString(4).getBytes());
            fos.close();
            LogUtils.success("UIConfig", "配置已保存");
        } catch (Exception e) {
            LogUtils.e("UIConfig", "保存配置失败: " + e.getMessage());
        }
    }
    
    private JSONObject getDefaultConfig() {
        try {
            String json = "{\n" +
                    "  \"theme\": {\n" +
                    "    \"primaryColor\": \"#1E3A5F\",\n" +
                    "    \"primaryDarkColor\": \"#152A45\",\n" +
                    "    \"primaryLightColor\": \"#3A5A8A\",\n" +
                    "    \"accentColor\": \"#4A90E2\",\n" +
                    "    \"successColor\": \"#2ECC71\",\n" +
                    "    \"warningColor\": \"#F39C12\",\n" +
                    "    \"errorColor\": \"#E74C3C\",\n" +
                    "    \"infoColor\": \"#3498DB\",\n" +
                    "    \"backgroundColor\": \"#F0F2F5\",\n" +
                    "    \"surfaceColor\": \"#FFFFFF\",\n" +
                    "    \"textPrimaryColor\": \"#1A1A2E\",\n" +
                    "    \"textSecondaryColor\": \"#6C757D\",\n" +
                    "    \"textHintColor\": \"#ADB5BD\",\n" +
                    "    \"dividerColor\": \"#E9ECEF\"\n" +
                    "  },\n" +
                    "  \"shape\": {\n" +
                    "    \"cornerRadiusSmall\": 8,\n" +
                    "    \"cornerRadiusMedium\": 12,\n" +
                    "    \"cornerRadiusLarge\": 16,\n" +
                    "    \"cornerRadiusExtraLarge\": 24,\n" +
                    "    \"buttonCornerRadius\": 12,\n" +
                    "    \"cardCornerRadius\": 16,\n" +
                    "    \"dialogCornerRadius\": 20\n" +
                    "  }\n" +
                    "}";
            return new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
    
    // ==================== 颜色获取方法 ====================
    
    public int getPrimaryColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("primaryColor", "#1E3A5F"));
    }
    
    public int getPrimaryDarkColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("primaryDarkColor", "#152A45"));
    }
    
    public int getPrimaryLightColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("primaryLightColor", "#3A5A8A"));
    }
    
    public int getAccentColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("accentColor", "#4A90E2"));
    }
    
    public int getSuccessColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("successColor", "#2ECC71"));
    }
    
    public int getWarningColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("warningColor", "#F39C12"));
    }
    
    public int getErrorColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("errorColor", "#E74C3C"));
    }
    
    public int getInfoColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("infoColor", "#3498DB"));
    }
    
    public int getBackgroundColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("backgroundColor", "#F0F2F5"));
    }
    
    public int getSurfaceColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("surfaceColor", "#FFFFFF"));
    }
    
    public int getTextPrimaryColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("textPrimaryColor", "#1A1A2E"));
    }
    
    public int getTextSecondaryColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("textSecondaryColor", "#6C757D"));
    }
    
    public int getTextHintColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("textHintColor", "#ADB5BD"));
    }
    
    public int getDividerColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("dividerColor", "#E9ECEF"));
    }
    
    // ==================== 形状获取方法 ====================
    
    public float getCornerRadiusSmall() {
        return (float) config.optJSONObject("shape").optDouble("cornerRadiusSmall", 8);
    }
    
    public float getCornerRadiusMedium() {
        return (float) config.optJSONObject("shape").optDouble("cornerRadiusMedium", 12);
    }
    
    public float getCornerRadiusLarge() {
        return (float) config.optJSONObject("shape").optDouble("cornerRadiusLarge", 16);
    }
    
    public float getCornerRadiusExtraLarge() {
        return (float) config.optJSONObject("shape").optDouble("cornerRadiusExtraLarge", 24);
    }
    
    public float getButtonCornerRadius() {
        return (float) config.optJSONObject("shape").optDouble("buttonCornerRadius", 12);
    }
    
    public float getCardCornerRadius() {
        return (float) config.optJSONObject("shape").optDouble("cardCornerRadius", 16);
    }
    
    public float getDialogCornerRadius() {
        return (float) config.optJSONObject("shape").optDouble("dialogCornerRadius", 20);
    }
    
    // ==================== UI 应用方法 ====================
    
    public GradientDrawable createRoundedBackground(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
    
    public GradientDrawable createGradientBackground(int startColor, int endColor, int angle) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColors(new int[]{startColor, endColor});
        if (angle == 0) drawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        else if (angle == 90) drawable.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
        else if (angle == 180) drawable.setOrientation(GradientDrawable.Orientation.RIGHT_LEFT);
        else if (angle == 270) drawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        else drawable.setOrientation(GradientDrawable.Orientation.TL_BR);
        return drawable;
    }
    
    public void applyTheme(android.app.Activity activity) {
        if (activity == null) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(getPrimaryDarkColor());
            activity.getWindow().setNavigationBarColor(getSurfaceColor());
        }
        
        View root = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        if (root != null) {
            root.setBackgroundColor(getBackgroundColor());
        }
    }
    
    public void applyThemeToView(View view) {
        if (view == null) return;
        
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextColor(getTextPrimaryColor());
        }
        
        if (view instanceof Button) {
            Button btn = (Button) view;
            GradientDrawable drawable = createRoundedBackground(getPrimaryColor(), getButtonCornerRadius());
            btn.setBackground(drawable);
            btn.setTextColor(Color.WHITE);
        }
    }
    
    private String readFileToString(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
    
    public void exportConfig(File destFile) throws Exception {
        FileOutputStream fos = new FileOutputStream(destFile);
        fos.write(config.toString(4).getBytes());
        fos.close();
    }
    
    public boolean importConfig(File sourceFile) {
        try {
            String json = readFileToString(sourceFile);
            config = new JSONObject(json);
            saveConfig();
            return true;
        } catch (Exception e) {
            LogUtils.e("UIConfig", "导入配置失败: " + e.getMessage());
            return false;
        }
    }
    
    public void resetToDefault() {
        config = getDefaultConfig();
        saveConfig();
    }
    
    public void updateColor(String key, String colorValue) {
        try {
            config.getJSONObject("theme").put(key, colorValue);
            saveConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public JSONObject getConfig() {
        return config;
    }
    
    public String getCurrentThemeName() {
        return currentThemeName;
    }
    
    public void setCurrentThemeName(String name) {
        this.currentThemeName = name;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CURRENT_THEME, name).apply();
    }
}