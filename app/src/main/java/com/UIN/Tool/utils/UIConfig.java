package com.UIN.Tool.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;

import com.UIN.Tool.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class UIConfig {
    
    private static UIConfig instance;
    private Context context;
    private JSONObject config;
    private String currentThemeName = "默认主题";
    private boolean isDarkMode = false;
    
    private static final String CONFIG_FILE = "ui_config.json";
    private static final String PREF_NAME = "ui_prefs";
    private static final String KEY_CURRENT_THEME = "current_theme";
    private static final String KEY_USE_CUSTOM_ICON_TINT = "use_custom_icon_tint";
    
    // 是否使用自定义图标着色（true=使用主题色，false=保持图标原色）
    private boolean useCustomIconTint = true;
    
    // 所有颜色配置的 key 列表
    private static final String[] COLOR_KEYS = {
        // 主色调
        "primary", "primary_dark", "primary_light", "accent",
        // 辅助色
        "success", "warning", "error", "info",
        // 文本颜色
        "text_primary", "text_secondary", "text_hint", "text_disabled",
        // 背景色
        "background", "background_card", "background_dark",
        // 表面色
        "surface", "surface_variant", "surface_card",
        // 边框和分割线
        "divider", "border",
        // 渐变
        "gradient_start", "gradient_end", "gradient_accent_start", "gradient_accent_end",
        // 导航栏
        "nav_item_selected", "nav_item_unselected",
        // 卡片
        "card_shadow", "card_ripple",
        // 玻璃效果
        "glass_background", "glass_border", "glass_shadow",
        // 叠加色
        "overlay_light", "overlay_medium", "overlay_dark",
        // 灰色调
        "gray_light", "gray_medium", "gray_dark", "gray_text",
        // 图标颜色
        "icon_tint", "icon_tint_selected"
    };
    
    private UIConfig(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
        loadPreferences();
    }
    
    public static UIConfig getInstance(Context context) {
        if (instance == null) {
            instance = new UIConfig(context);
        }
        return instance;
    }
    
    private void loadPreferences() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        useCustomIconTint = prefs.getBoolean(KEY_USE_CUSTOM_ICON_TINT, true);
        currentThemeName = prefs.getString(KEY_CURRENT_THEME, "默认主题");
    }
    
    private void savePreferences() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_USE_CUSTOM_ICON_TINT, useCustomIconTint)
            .putString(KEY_CURRENT_THEME, currentThemeName)
            .apply();
    }
    
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
        } catch (Exception e) {
            LogUtils.e("UIConfig", "加载配置失败: " + e.getMessage());
            config = getDefaultConfig();
        }
    }
    
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
                    "    \"primary\": \"#FF37474F\",\n" +
                    "    \"primary_dark\": \"#FF263238\",\n" +
                    "    \"primary_light\": \"#FF546E7A\",\n" +
                    "    \"accent\": \"#FF607D8B\",\n" +
                    "    \"success\": \"#FF4CAF50\",\n" +
                    "    \"warning\": \"#FFFF9800\",\n" +
                    "    \"error\": \"#FFF44336\",\n" +
                    "    \"info\": \"#FF2196F3\",\n" +
                    "    \"text_primary\": \"#FF212121\",\n" +
                    "    \"text_secondary\": \"#FF757575\",\n" +
                    "    \"text_hint\": \"#FFBDBDBD\",\n" +
                    "    \"text_disabled\": \"#FF9E9E9E\",\n" +
                    "    \"background\": \"#FFF8F8F8\",\n" +
                    "    \"background_card\": \"#FFFFFFFF\",\n" +
                    "    \"background_dark\": \"#FFE0E0E0\",\n" +
                    "    \"surface\": \"#FFFFFFFF\",\n" +
                    "    \"surface_variant\": \"#FFFAFAFA\",\n" +
                    "    \"surface_card\": \"#FFFFFFFF\",\n" +
                    "    \"divider\": \"#FFE0E0E0\",\n" +
                    "    \"border\": \"#FFD0D0D0\",\n" +
                    "    \"gradient_start\": \"#FF37474F\",\n" +
                    "    \"gradient_end\": \"#FF546E7A\",\n" +
                    "    \"gradient_accent_start\": \"#FF607D8B\",\n" +
                    "    \"gradient_accent_end\": \"#FF78909C\",\n" +
                    "    \"nav_item_selected\": \"#FF37474F\",\n" +
                    "    \"nav_item_unselected\": \"#FFBDBDBD\",\n" +
                    "    \"card_shadow\": \"#1A000000\",\n" +
                    "    \"card_ripple\": \"#0D37474F\",\n" +
                    "    \"glass_background\": \"#E6FFFFFF\",\n" +
                    "    \"glass_border\": \"#40FFFFFF\",\n" +
                    "    \"glass_shadow\": \"#20000000\",\n" +
                    "    \"overlay_light\": \"#0D000000\",\n" +
                    "    \"overlay_medium\": \"#1A000000\",\n" +
                    "    \"overlay_dark\": \"#33000000\",\n" +
                    "    \"gray_light\": \"#FFF5F5F5\",\n" +
                    "    \"gray_medium\": \"#FFEEEEEE\",\n" +
                    "    \"gray_dark\": \"#FF9E9E9E\",\n" +
                    "    \"gray_text\": \"#FF616161\",\n" +
                    "    \"icon_tint\": \"#FF757575\",\n" +
                    "    \"icon_tint_selected\": \"#FF37474F\"\n" +
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
        return Color.parseColor(config.optJSONObject("theme").optString("primary", "#FF37474F"));
    }
    
    public int getPrimaryDarkColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("primary_dark", "#FF263238"));
    }
    
    public int getPrimaryLightColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("primary_light", "#FF546E7A"));
    }
    
    public int getAccentColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("accent", "#FF607D8B"));
    }
    
    public int getSuccessColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("success", "#FF4CAF50"));
    }
    
    public int getWarningColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("warning", "#FFFF9800"));
    }
    
    public int getErrorColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("error", "#FFF44336"));
    }
    
    public int getInfoColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("info", "#FF2196F3"));
    }
    
    public int getTextPrimaryColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("text_primary", "#FF212121"));
    }
    
    public int getTextSecondaryColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("text_secondary", "#FF757575"));
    }
    
    public int getTextHintColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("text_hint", "#FFBDBDBD"));
    }
    
    public int getTextDisabledColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("text_disabled", "#FF9E9E9E"));
    }
    
    public int getBackgroundColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("background", "#FFF8F8F8"));
    }
    
    public int getBackgroundCardColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("background_card", "#FFFFFFFF"));
    }
    
    public int getBackgroundDarkColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("background_dark", "#FFE0E0E0"));
    }
    
    public int getSurfaceColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("surface", "#FFFFFFFF"));
    }
    
    public int getSurfaceVariantColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("surface_variant", "#FFFAFAFA"));
    }
    
    public int getSurfaceCardColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("surface_card", "#FFFFFFFF"));
    }
    
    public int getDividerColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("divider", "#FFE0E0E0"));
    }
    
    public int getBorderColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("border", "#FFD0D0D0"));
    }
    
    public int getGradientStartColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gradient_start", "#FF37474F"));
    }
    
    public int getGradientEndColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gradient_end", "#FF546E7A"));
    }
    
    public int getGradientAccentStartColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gradient_accent_start", "#FF607D8B"));
    }
    
    public int getGradientAccentEndColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gradient_accent_end", "#FF78909C"));
    }
    
    public int getNavItemSelectedColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("nav_item_selected", "#FF37474F"));
    }
    
    public int getNavItemUnselectedColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("nav_item_unselected", "#FFBDBDBD"));
    }
    
    public int getCardShadowColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("card_shadow", "#1A000000"));
    }
    
    public int getCardRippleColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("card_ripple", "#0D37474F"));
    }
    
    public int getGlassBackgroundColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("glass_background", "#E6FFFFFF"));
    }
    
    public int getGlassBorderColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("glass_border", "#40FFFFFF"));
    }
    
    public int getGlassShadowColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("glass_shadow", "#20000000"));
    }
    
    public int getOverlayLightColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("overlay_light", "#0D000000"));
    }
    
    public int getOverlayMediumColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("overlay_medium", "#1A000000"));
    }
    
    public int getOverlayDarkColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("overlay_dark", "#33000000"));
    }
    
    public int getGrayLightColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gray_light", "#FFF5F5F5"));
    }
    
    public int getGrayMediumColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gray_medium", "#FFEEEEEE"));
    }
    
    public int getGrayDarkColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gray_dark", "#FF9E9E9E"));
    }
    
    public int getGrayTextColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("gray_text", "#FF616161"));
    }
    
    public int getIconTintColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("icon_tint", "#FF757575"));
    }
    
    public int getIconTintSelectedColor() {
        return Color.parseColor(config.optJSONObject("theme").optString("icon_tint_selected", "#FF37474F"));
    }
    
    public int getToolbarTitleColor() {
        int primaryColor = getPrimaryColor();
        double brightness = 0.299 * Color.red(primaryColor) + 0.587 * Color.green(primaryColor) + 0.114 * Color.blue(primaryColor);
        return brightness > 128 ? Color.BLACK : Color.WHITE;
    }
    
    // ==================== 图标着色开关 ====================
    
    public boolean isUseCustomIconTint() {
        return useCustomIconTint;
    }
    
    public void setUseCustomIconTint(boolean useCustomIconTint) {
        this.useCustomIconTint = useCustomIconTint;
        savePreferences();
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
    
    public void applyToolbarTheme(Toolbar toolbar) {
        if (toolbar == null) return;
        toolbar.setBackgroundColor(getPrimaryColor());
        toolbar.setTitleTextColor(getToolbarTitleColor());
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(getToolbarTitleColor());
        }
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setTint(getToolbarTitleColor());
        }
    }
    
    public void applyBottomNavigationTheme(BottomNavigationView bottomNav) {
        if (bottomNav == null) return;
        int selectedColor = getNavItemSelectedColor();
        int unselectedColor = getNavItemUnselectedColor();
        
        ColorStateList colorStateList = new ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
            },
            new int[]{selectedColor, unselectedColor}
        );
        bottomNav.setItemIconTintList(colorStateList);
        bottomNav.setItemTextColor(colorStateList);
    }
    
    public void applyButtonTheme(Button button) {
        if (button == null) return;
        GradientDrawable drawable = createRoundedBackground(getPrimaryColor(), getButtonCornerRadius());
        button.setBackground(drawable);
        button.setTextColor(Color.WHITE);
    }
    
    public void applyTextViewTheme(TextView textView) {
        if (textView == null) return;
        textView.setTextColor(getTextPrimaryColor());
    }
    
    /**
     * 应用主题到 ImageView（图标着色）- 支持开关控制
     */
    public void applyImageViewTheme(ImageView imageView) {
        if (imageView == null) return;
        
        // 如果不使用自定义着色，清除颜色滤镜
        if (!useCustomIconTint) {
            Drawable drawable = imageView.getDrawable();
            if (drawable != null) {
                drawable.mutate();
                drawable.clearColorFilter();
            }
            return;
        }
        
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) return;
        
        try {
            drawable.mutate();
            drawable.setColorFilter(getIconTintColor(), PorterDuff.Mode.SRC_IN);
        } catch (Exception e) {
            LogUtils.e("UIConfig", "应用图标主题失败: " + e.getMessage());
        }
    }
    
    public void applyCardTheme(MaterialCardView cardView) {
        if (cardView == null) return;
        cardView.setCardBackgroundColor(getSurfaceCardColor());
        cardView.setRadius(getCardCornerRadius());
        cardView.setCardElevation(2);
    }
    
    /**
     * 递归应用主题到整个 View 树
     */
    public void applyThemeToViewTree(View view) {
        if (view == null) return;
        
        try {
            // 应用主题到当前 View
            if (view instanceof Button) {
                applyButtonTheme((Button) view);
            } else if (view instanceof TextView && !(view instanceof Button)) {
                applyTextViewTheme((TextView) view);
            } else if (view instanceof ImageView) {
                applyImageViewTheme((ImageView) view);
            } else if (view instanceof MaterialCardView) {
                applyCardTheme((MaterialCardView) view);
            }
            
            // 递归处理子 View
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    applyThemeToViewTree(viewGroup.getChildAt(i));
                }
            }
        } catch (Exception e) {
            LogUtils.e("UIConfig", "应用主题到 View 失败: " + e.getMessage());
        }
    }
    
    public void updateColor(String key, String colorValue) {
        try {
            config.getJSONObject("theme").put(key, colorValue);
            saveConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void updateShape(String key, int value) {
        try {
            config.getJSONObject("shape").put(key, value);
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
        savePreferences();
    }
    
    public void resetToDefault() {
        config = getDefaultConfig();
        saveConfig();
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
    
    public String[] getAllColorNames() {
        return COLOR_KEYS;
    }
    
    public String getColorDisplayName(String key) {
        switch (key) {
            case "primary": return "主题色";
            case "primary_dark": return "深色主题色";
            case "primary_light": return "浅色主题色";
            case "accent": return "强调色";
            case "success": return "成功色";
            case "warning": return "警告色";
            case "error": return "错误色";
            case "info": return "信息色";
            case "text_primary": return "主文本色";
            case "text_secondary": return "副文本色";
            case "text_hint": return "提示文本色";
            case "text_disabled": return "禁用文本色";
            case "background": return "主背景色";
            case "background_card": return "卡片背景色";
            case "background_dark": return "深色背景";
            case "surface": return "表面色";
            case "surface_variant": return "变体表面色";
            case "surface_card": return "卡片表面色";
            case "divider": return "分割线色";
            case "border": return "边框色";
            case "gradient_start": return "渐变起始色";
            case "gradient_end": return "渐变结束色";
            case "gradient_accent_start": return "强调渐变起始色";
            case "gradient_accent_end": return "强调渐变结束色";
            case "nav_item_selected": return "导航栏选中色";
            case "nav_item_unselected": return "导航栏未选中色";
            case "card_shadow": return "卡片阴影色";
            case "card_ripple": return "卡片涟漪色";
            case "glass_background": return "玻璃背景色";
            case "glass_border": return "玻璃边框色";
            case "glass_shadow": return "玻璃阴影色";
            case "overlay_light": return "浅叠加色";
            case "overlay_medium": return "中叠加色";
            case "overlay_dark": return "深叠加色";
            case "gray_light": return "浅灰色";
            case "gray_medium": return "中灰色";
            case "gray_dark": return "深灰色";
            case "gray_text": return "灰色文本";
            case "icon_tint": return "图标着色";
            case "icon_tint_selected": return "选中图标着色";
            default: return key;
        }
    }
}