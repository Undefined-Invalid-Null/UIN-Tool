// app/src/main/java/com/UIN/Tool/ui/settings/UIConfigActivity.java
package com.UIN.Tool.ui.settings;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.cardview.widget.CardView;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityUiConfigBinding;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.utils.UIConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class UIConfigActivity extends BaseActivity {

    private ActivityUiConfigBinding binding;
    private UIConfig uiConfig;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> { if (uri != null) exportConfig(uri); });

    private final ActivityResultLauncher<String> importLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) importConfig(uri); });

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_ui_config;
    }

    @Override
    protected void initViews() {
        binding = ActivityUiConfigBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.title_ui_config), true);
    }

    @Override
    protected void initData() {
        uiConfig = UIConfig.getInstance(this);
        loadColorConfigs();
        loadShapeConfigs();
        updatePreview();
    }

    @Override
    protected void setupListeners() {
        binding.btnResetContainer.setOnClickListener(v -> resetConfig());
        binding.btnExportContainer.setOnClickListener(v -> exportLauncher.launch("ui_config.json"));
        binding.btnImportContainer.setOnClickListener(v -> importLauncher.launch("application/json"));
        binding.btnApplyContainer.setOnClickListener(v -> {
            uiConfig.applyTheme(this);
            showToast(getString(R.string.ui_config_apply_success));
        });
    }

    private void loadColorConfigs() {
        JSONObject theme = uiConfig.getConfig().optJSONObject("theme");
        if (theme == null) return;

        addColorItem(getString(R.string.ui_config_primary_color), "primaryColor", theme.optString("primaryColor", "#1E3A5F"));
        addColorItem(getString(R.string.ui_config_primary_dark), "primaryDarkColor", theme.optString("primaryDarkColor", "#152A45"));
        addColorItem(getString(R.string.ui_config_primary_light), "primaryLightColor", theme.optString("primaryLightColor", "#3A5A8A"));
        addColorItem(getString(R.string.ui_config_accent_color), "accentColor", theme.optString("accentColor", "#4A90E2"));
        addColorItem(getString(R.string.ui_config_success_color), "successColor", theme.optString("successColor", "#2ECC71"));
        addColorItem(getString(R.string.ui_config_warning_color), "warningColor", theme.optString("warningColor", "#F39C12"));
        addColorItem(getString(R.string.ui_config_error_color), "errorColor", theme.optString("errorColor", "#E74C3C"));
        addColorItem(getString(R.string.ui_config_info_color), "infoColor", theme.optString("infoColor", "#3498DB"));
        addColorItem(getString(R.string.ui_config_background_color), "backgroundColor", theme.optString("backgroundColor", "#F0F2F5"));
        addColorItem(getString(R.string.ui_config_surface_color), "surfaceColor", theme.optString("surfaceColor", "#FFFFFF"));
        addColorItem(getString(R.string.ui_config_text_primary), "textPrimaryColor", theme.optString("textPrimaryColor", "#1A1A2E"));
        addColorItem(getString(R.string.ui_config_text_secondary), "textSecondaryColor", theme.optString("textSecondaryColor", "#6C757D"));
    }

    private void addColorItem(String name, String key, String defaultValue) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.spacing_md);
        card.setLayoutParams(params);
        card.setRadius(uiConfig.getCornerRadiusMedium());
        card.setCardElevation(1);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(getResources().getDimensionPixelSize(R.dimen.spacing_lg),
                getResources().getDimensionPixelSize(R.dimen.spacing_md),
                getResources().getDimensionPixelSize(R.dimen.spacing_lg),
                getResources().getDimensionPixelSize(R.dimen.spacing_md));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(14);
        nameView.setTextColor(uiConfig.getTextPrimaryColor());
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        View colorPreview = new View(this);
        LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(48, 48);
        colorParams.setMargins(getResources().getDimensionPixelSize(R.dimen.spacing_sm), 0,
                getResources().getDimensionPixelSize(R.dimen.spacing_sm), 0);
        colorPreview.setLayoutParams(colorParams);
        colorPreview.setBackgroundColor(Color.parseColor(defaultValue));

        TextView valueView = new TextView(this);
        valueView.setText(defaultValue);
        valueView.setTextSize(12);
        valueView.setTextColor(uiConfig.getTextSecondaryColor());

        content.addView(nameView);
        content.addView(colorPreview);
        content.addView(valueView);
        card.addView(content);

        card.setOnClickListener(v -> showColorPickerDialog(key, name, defaultValue, colorPreview, valueView));
        binding.colorContainer.addView(card);
    }

    private void loadShapeConfigs() {
        JSONObject shape = uiConfig.getConfig().optJSONObject("shape");
        if (shape == null) return;

        addShapeItem(getString(R.string.ui_config_corner_small), "cornerRadiusSmall", shape.optInt("cornerRadiusSmall", 8), "dp");
        addShapeItem(getString(R.string.ui_config_corner_medium), "cornerRadiusMedium", shape.optInt("cornerRadiusMedium", 12), "dp");
        addShapeItem(getString(R.string.ui_config_corner_large), "cornerRadiusLarge", shape.optInt("cornerRadiusLarge", 16), "dp");
        addShapeItem(getString(R.string.ui_config_corner_extra_large), "cornerRadiusExtraLarge", shape.optInt("cornerRadiusExtraLarge", 24), "dp");
        addShapeItem(getString(R.string.ui_config_button_corner), "buttonCornerRadius", shape.optInt("buttonCornerRadius", 12), "dp");
        addShapeItem(getString(R.string.ui_config_card_corner), "cardCornerRadius", shape.optInt("cardCornerRadius", 16), "dp");
        addShapeItem(getString(R.string.ui_config_dialog_corner), "dialogCornerRadius", shape.optInt("dialogCornerRadius", 20), "dp");
    }

    private void addShapeItem(String name, String key, int defaultValue, String unit) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.spacing_md);
        card.setLayoutParams(params);
        card.setRadius(uiConfig.getCornerRadiusMedium());
        card.setCardElevation(1);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(getResources().getDimensionPixelSize(R.dimen.spacing_lg),
                getResources().getDimensionPixelSize(R.dimen.spacing_md),
                getResources().getDimensionPixelSize(R.dimen.spacing_lg),
                getResources().getDimensionPixelSize(R.dimen.spacing_md));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(14);
        nameView.setTextColor(uiConfig.getTextPrimaryColor());
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView valueView = new TextView(this);
        valueView.setText(defaultValue + unit);
        valueView.setTextSize(14);
        valueView.setTextColor(uiConfig.getAccentColor());

        content.addView(nameView);
        content.addView(valueView);
        card.addView(content);

        card.setOnClickListener(v -> showSeekBarDialog(key, name, defaultValue, valueView, unit));
        binding.shapeContainer.addView(card);
    }

    private void showColorPickerDialog(String key, String name, String currentColor, View preview, TextView valueView) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null);
        EditText etHex = dialogView.findViewById(R.id.et_hex);
        View colorPreview = dialogView.findViewById(R.id.color_preview);

        etHex.setText(currentColor);
        colorPreview.setBackgroundColor(Color.parseColor(currentColor));

        etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    int color = Color.parseColor(s.toString());
                    colorPreview.setBackgroundColor(color);
                } catch (Exception e) {}
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String newColor = etHex.getText().toString();
                    if (newColor.matches("^#[0-9A-Fa-f]{6}$")) {
                        uiConfig.updateColor(key, newColor);
                        preview.setBackgroundColor(Color.parseColor(newColor));
                        valueView.setText(newColor);
                        updatePreview();
                        showToast(getString(R.string.ui_config_color_updated, name));
                    } else {
                        showToast(getString(R.string.ui_config_color_format_error));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSeekBarDialog(String key, String name, int currentValue, TextView valueView, String unit) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_seekbar, null);
        TextView tvValue = dialogView.findViewById(R.id.tv_value);
        SeekBar seekBar = dialogView.findViewById(R.id.seek_bar);

        seekBar.setMax(100);
        seekBar.setProgress(currentValue);
        tvValue.setText(currentValue + unit);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvValue.setText(progress + unit);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    int newValue = seekBar.getProgress();
                    try {
                        uiConfig.getConfig().getJSONObject("shape").put(key, newValue);
                        uiConfig.saveConfig();
                        valueView.setText(newValue + unit);
                        updatePreview();
                        showToast(getString(R.string.ui_config_shape_updated, name));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updatePreview() {
        float radius = uiConfig.getCardCornerRadius();

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(uiConfig.getSurfaceColor());
        cardBg.setCornerRadius(radius);
        binding.previewCard.setBackground(cardBg);

        binding.previewTitle.setTextColor(uiConfig.getPrimaryColor());
        binding.previewText.setTextColor(uiConfig.getTextSecondaryColor());

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(uiConfig.getPrimaryColor());
        btnBg.setCornerRadius(uiConfig.getButtonCornerRadius());
        binding.previewButton.setBackground(btnBg);
        binding.previewButton.setTextColor(Color.WHITE);

        binding.previewContainer.setBackgroundColor(uiConfig.getBackgroundColor());
    }

    private void resetConfig() {
        DialogHelper.confirmDialog(this, getString(R.string.dialog_title_confirm),
                getString(R.string.ui_config_reset_confirm),
                (dialog, which) -> {
                    uiConfig.resetToDefault();
                    binding.colorContainer.removeAllViews();
                    binding.shapeContainer.removeAllViews();
                    loadColorConfigs();
                    loadShapeConfigs();
                    updatePreview();
                    showToast(getString(R.string.ui_config_reset_success));
                }).show();
    }

    private void exportConfig(Uri uri) {
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
                loadColorConfigs();
                loadShapeConfigs();
                updatePreview();
                showToast(getString(R.string.ui_config_import_success));
            } else {
                showToast(getString(R.string.ui_config_import_failed));
            }
        } catch (Exception e) {
            showToast(getString(R.string.ui_config_import_failed));
        }
    }
}