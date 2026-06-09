package com.UIN.Tool.ui.settings;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityUiConfigNewBinding;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.ui.widget.FullColorPickerView;
import com.UIN.Tool.utils.UIConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class UIConfigActivity extends BaseActivity {

    private ActivityUiConfigNewBinding binding;
    private UIConfig uiConfig;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> { if (uri != null) exportConfig(uri); });

    private final ActivityResultLauncher<String> importLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) importConfig(uri); });

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_ui_config_new;
    }

    @Override
    protected void initViews() {
        binding = ActivityUiConfigNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.title_ui_config), true);
        
        uiConfig = UIConfig.getInstance(this);
    }

    @Override
    protected void initData() {
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        setupIconTintSwitch();
        loadAllColorConfigs();
        loadShapeConfigs();
        updatePreview();
        applyThemeToToolbar();
    }

    @Override
    protected void setupListeners() {
        binding.btnResetContainer.setOnClickListener(v -> resetConfig());
        binding.btnExportContainer.setOnClickListener(v -> exportLauncher.launch("ui_config.json"));
        binding.btnImportContainer.setOnClickListener(v -> importLauncher.launch("application/json"));
        binding.btnApplyContainer.setOnClickListener(v -> applyCurrentTheme());
    }
    
    /**
     * 图标着色开关设置 - 使用 TextView 状态显示
     */
    private void setupIconTintSwitch() {
        if (uiConfig == null) return;
        
        TextView tvStatus = binding.tvIconTintStatus;
        if (tvStatus == null) return;
        
        // 更新状态显示
        updateIconTintStatus(tvStatus);
        
        // 卡片点击切换
        if (binding.cardIconTint != null) {
            binding.cardIconTint.setOnClickListener(v -> {
                boolean newState = !uiConfig.isUseCustomIconTint();
                uiConfig.setUseCustomIconTint(newState);
                updateIconTintStatus(tvStatus);
                showToast(newState ? "图标将使用主题色" : "图标将保持原色");
                refreshAllViewsIconTint();
            });
        }
    }
    
    private void updateIconTintStatus(TextView tvStatus) {
        if (uiConfig == null) return;
        boolean isChecked = uiConfig.isUseCustomIconTint();
        if (isChecked) {
            tvStatus.setText("已开启");
            tvStatus.setTextColor(uiConfig.getPrimaryColor());
        } else {
            tvStatus.setText("已关闭");
            tvStatus.setTextColor(uiConfig.getTextHintColor());
        }
    }
    
    /**
     * 刷新所有 View 的图标着色
     */
    private void refreshAllViewsIconTint() {
        if (uiConfig == null) return;
        
        if (binding.getRoot() != null) {
            uiConfig.applyThemeToViewTree(binding.getRoot());
        }
        
        applyThemeToToolbar();
        updatePreview();
        
        if (getWindow() != null) {
            getWindow().setStatusBarColor(uiConfig.getPrimaryDarkColor());
        }
        
        // 刷新状态文字颜色
        if (binding.tvIconTintStatus != null) {
            updateIconTintStatus(binding.tvIconTintStatus);
        }
    }
    
    private void applyCurrentTheme() {
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        uiConfig.saveConfig();
        uiConfig.applyTheme(this);
        applyThemeToToolbar();
        updatePreview();
        refreshColorList();
        refreshShapeList();
        if (binding.getRoot() != null) {
            uiConfig.applyThemeToViewTree(binding.getRoot());
        }
        showToast(getString(R.string.ui_config_apply_success));
    }
    
    private void applyThemeToToolbar() {
        if (uiConfig == null) return;
        if (binding.toolbar != null) {
            binding.toolbar.setBackgroundColor(uiConfig.getPrimaryColor());
            binding.toolbar.setTitleTextColor(uiConfig.getToolbarTitleColor());
            if (binding.toolbar.getNavigationIcon() != null) {
                binding.toolbar.getNavigationIcon().setTint(uiConfig.getToolbarTitleColor());
            }
        }
        if (getWindow() != null) {
            getWindow().setStatusBarColor(uiConfig.getPrimaryDarkColor());
        }
    }
    
    private void refreshColorList() {
        if (uiConfig == null) return;
        JSONObject theme = uiConfig.getConfig().optJSONObject("theme");
        if (theme == null) return;
        
        int childCount = binding.colorContainer.getChildCount();
        String[] colorKeys = uiConfig.getAllColorNames();
        
        for (int i = 0; i < childCount && i < colorKeys.length; i++) {
            View itemView = binding.colorContainer.getChildAt(i);
            if (itemView instanceof com.google.android.material.card.MaterialCardView) {
                String colorValue = theme.optString(colorKeys[i], "#FFFFFFFF");
                
                View colorPreview = itemView.findViewById(R.id.view_color_preview);
                TextView colorValueView = itemView.findViewById(R.id.tv_color_value);
                
                if (colorPreview != null) {
                    colorPreview.setBackgroundColor(Color.parseColor(colorValue));
                }
                if (colorValueView != null) {
                    colorValueView.setText(colorValue);
                }
            }
        }
    }
    
    private void refreshShapeList() {
        if (uiConfig == null) return;
        JSONObject shape = uiConfig.getConfig().optJSONObject("shape");
        if (shape == null) return;
        
        int childCount = binding.shapeContainer.getChildCount();
        String[] shapeKeys = {"cornerRadiusSmall", "cornerRadiusMedium", "cornerRadiusLarge", 
                              "cornerRadiusExtraLarge", "buttonCornerRadius", "cardCornerRadius", "dialogCornerRadius"};
        
        for (int i = 0; i < childCount && i < shapeKeys.length; i++) {
            View itemView = binding.shapeContainer.getChildAt(i);
            if (itemView instanceof com.google.android.material.card.MaterialCardView) {
                TextView valueView = itemView.findViewById(R.id.tv_shape_value);
                if (valueView != null) {
                    int value = shape.optInt(shapeKeys[i], 0);
                    valueView.setText(value + " dp");
                }
            }
        }
    }

    private void loadAllColorConfigs() {
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        String[] colorKeys = uiConfig.getAllColorNames();
        JSONObject theme = uiConfig.getConfig().optJSONObject("theme");
        
        for (String key : colorKeys) {
            String displayName = uiConfig.getColorDisplayName(key);
            String defaultValue = "#FFFFFFFF";
            if (theme != null) {
                defaultValue = theme.optString(key, "#FFFFFFFF");
            }
            addColorItem(displayName, key, defaultValue);
        }
    }

    private void addColorItem(String name, String key, String defaultValue) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_color_config, binding.colorContainer, false);
        
        TextView tvName = itemView.findViewById(R.id.tv_color_name);
        View colorPreview = itemView.findViewById(R.id.view_color_preview);
        TextView tvValue = itemView.findViewById(R.id.tv_color_value);
        
        tvName.setText(name);
        colorPreview.setBackgroundColor(Color.parseColor(defaultValue));
        tvValue.setText(defaultValue);
        
        itemView.setOnClickListener(v -> showFullColorPickerDialog(key, name, defaultValue, colorPreview, tvValue));
        binding.colorContainer.addView(itemView);
    }

    private void showFullColorPickerDialog(String key, String name, String currentColor, View preview, TextView valueView) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_full_color_picker, null);
        
        FullColorPickerView colorPickerView = dialogView.findViewById(R.id.full_color_picker);
        
        int initialColor = Color.parseColor(currentColor);
        colorPickerView.setInitialColor(initialColor);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(name)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (d, which) -> {
                    if (uiConfig == null) {
                        uiConfig = UIConfig.getInstance(UIConfigActivity.this);
                    }
                    int newColorInt = colorPickerView.getCurrentColor();
                    String newColor;
                    if (Color.alpha(newColorInt) == 255) {
                        newColor = String.format("#%06X", (0xFFFFFF & newColorInt));
                    } else {
                        newColor = String.format("#%08X", newColorInt);
                    }
                    uiConfig.updateColor(key, newColor);
                    preview.setBackgroundColor(newColorInt);
                    valueView.setText(newColor);
                    updatePreview();
                    applyThemeToToolbar();
                    if (binding.getRoot() != null) {
                        uiConfig.applyThemeToViewTree(binding.getRoot());
                    }
                    refreshAllViewsIconTint();
                    showToast(getString(R.string.ui_config_color_updated, name));
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        
        dialog.show();
    }

    private void loadShapeConfigs() {
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        JSONObject shape = uiConfig.getConfig().optJSONObject("shape");
        if (shape == null) return;

        addShapeItem(getString(R.string.ui_config_corner_small), "cornerRadiusSmall", shape.optInt("cornerRadiusSmall", 8));
        addShapeItem(getString(R.string.ui_config_corner_medium), "cornerRadiusMedium", shape.optInt("cornerRadiusMedium", 12));
        addShapeItem(getString(R.string.ui_config_corner_large), "cornerRadiusLarge", shape.optInt("cornerRadiusLarge", 16));
        addShapeItem(getString(R.string.ui_config_corner_extra_large), "cornerRadiusExtraLarge", shape.optInt("cornerRadiusExtraLarge", 24));
        addShapeItem(getString(R.string.ui_config_button_corner), "buttonCornerRadius", shape.optInt("buttonCornerRadius", 12));
        addShapeItem(getString(R.string.ui_config_card_corner), "cardCornerRadius", shape.optInt("cardCornerRadius", 16));
        addShapeItem(getString(R.string.ui_config_dialog_corner), "dialogCornerRadius", shape.optInt("dialogCornerRadius", 20));
    }

    private void addShapeItem(String name, String key, int defaultValue) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_shape_config, binding.shapeContainer, false);
        
        TextView tvName = itemView.findViewById(R.id.tv_shape_name);
        TextView tvValue = itemView.findViewById(R.id.tv_shape_value);
        
        tvName.setText(name);
        tvValue.setText(defaultValue + " dp");
        
        itemView.setOnClickListener(v -> showSeekBarDialog(key, name, defaultValue, tvValue));
        binding.shapeContainer.addView(itemView);
    }

    private void showSeekBarDialog(String key, String name, int currentValue, TextView valueView) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_seekbar_advanced, null);
        
        SeekBar seekBar = dialogView.findViewById(R.id.seek_bar);
        TextView tvCurrentValue = dialogView.findViewById(R.id.tv_current_value);
        View previewCard = dialogView.findViewById(R.id.preview_card);
        
        seekBar.setMax(100);
        seekBar.setProgress(currentValue);
        tvCurrentValue.setText(String.valueOf(currentValue));
        
        updatePreviewCorner(previewCard, currentValue);
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCurrentValue.setText(String.valueOf(progress));
                updatePreviewCorner(previewCard, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(name)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (d, which) -> {
                    if (uiConfig == null) {
                        uiConfig = UIConfig.getInstance(UIConfigActivity.this);
                    }
                    int newValue = seekBar.getProgress();
                    uiConfig.updateShape(key, newValue);
                    valueView.setText(newValue + " dp");
                    updatePreview();
                    refreshAllViewsIconTint();
                    showToast(getString(R.string.ui_config_shape_updated, name));
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        
        dialog.show();
    }
    
    private void updatePreviewCorner(View view, int radius) {
        if (view == null) return;
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(uiConfig.getPrimaryColor());
        drawable.setCornerRadius(radius);
        view.setBackground(drawable);
    }

    private void updatePreview() {
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        float radius = uiConfig.getCardCornerRadius();

        binding.previewCard.setCardBackgroundColor(uiConfig.getSurfaceCardColor());
        binding.previewCard.setRadius(radius);

        binding.previewTitle.setTextColor(uiConfig.getPrimaryColor());
        binding.previewText.setTextColor(uiConfig.getTextSecondaryColor());

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(uiConfig.getPrimaryColor());
        btnBg.setCornerRadius(uiConfig.getButtonCornerRadius());
        binding.previewButton.setBackground(btnBg);
        binding.previewButton.setTextColor(Color.WHITE);
    }

    private void resetConfig() {
        DialogHelper.confirmDialog(this, getString(R.string.dialog_title_confirm),
                getString(R.string.ui_config_reset_confirm),
                (dialog, which) -> {
                    if (uiConfig == null) {
                        uiConfig = UIConfig.getInstance(UIConfigActivity.this);
                    }
                    uiConfig.resetToDefault();
                    binding.colorContainer.removeAllViews();
                    binding.shapeContainer.removeAllViews();
                    loadAllColorConfigs();
                    loadShapeConfigs();
                    updatePreview();
                    applyThemeToToolbar();
                    // 刷新状态文字
                    if (binding.tvIconTintStatus != null) {
                        updateIconTintStatus(binding.tvIconTintStatus);
                    }
                    refreshAllViewsIconTint();
                    showToast(getString(R.string.ui_config_reset_success));
                }).show();
    }

    private void exportConfig(Uri uri) {
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        try {
            File tempFile = new File(getCacheDir(), "ui_config_export.json");
            uiConfig.exportConfig(tempFile);
            FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri);
            FileInputStream fis = new FileInputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
            fos.close();
            fis.close();
            tempFile.delete();
            showToast(getString(R.string.ui_config_export_success));
        } catch (Exception e) {
            showToast(getString(R.string.ui_config_export_failed, e.getMessage()));
        }
    }

    private void importConfig(Uri uri) {
        if (uiConfig == null) {
            uiConfig = UIConfig.getInstance(this);
        }
        try {
            File tempFile = new File(getCacheDir(), "ui_config_import.json");
            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
            fos.close();
            is.close();

            if (uiConfig.importConfig(tempFile)) {
                tempFile.delete();
                binding.colorContainer.removeAllViews();
                binding.shapeContainer.removeAllViews();
                loadAllColorConfigs();
                loadShapeConfigs();
                updatePreview();
                applyThemeToToolbar();
                if (binding.tvIconTintStatus != null) {
                    updateIconTintStatus(binding.tvIconTintStatus);
                }
                refreshAllViewsIconTint();
                showToast(getString(R.string.ui_config_import_success));
            } else {
                showToast(getString(R.string.ui_config_import_failed));
            }
        } catch (Exception e) {
            showToast(getString(R.string.ui_config_import_failed));
        }
    }
}