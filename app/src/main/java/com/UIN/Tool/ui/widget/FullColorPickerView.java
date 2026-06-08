package com.UIN.Tool.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.UIN.Tool.R;

public class FullColorPickerView extends FrameLayout {

    private SeekBar seekBarRed;
    private SeekBar seekBarGreen;
    private SeekBar seekBarBlue;
    private SeekBar seekBarAlpha;
    private EditText etHexColor;
    private TextView tvRedValue;
    private TextView tvGreenValue;
    private TextView tvBlueValue;
    private TextView tvAlphaValue;
    private View colorPreview;
    
    private int currentRed = 30;
    private int currentGreen = 58;
    private int currentBlue = 95;
    private int currentAlpha = 255;
    
    private OnColorChangedListener listener;
    
    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }
    
    public FullColorPickerView(Context context) {
        super(context);
        init();
    }
    
    public FullColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public FullColorPickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        View rootView = LayoutInflater.from(getContext()).inflate(R.layout.view_full_color_picker, this, true);
        
        seekBarRed = rootView.findViewById(R.id.seek_bar_red);
        seekBarGreen = rootView.findViewById(R.id.seek_bar_green);
        seekBarBlue = rootView.findViewById(R.id.seek_bar_blue);
        seekBarAlpha = rootView.findViewById(R.id.seek_bar_alpha);
        etHexColor = rootView.findViewById(R.id.et_hex_color);
        tvRedValue = rootView.findViewById(R.id.tv_red_value);
        tvGreenValue = rootView.findViewById(R.id.tv_green_value);
        tvBlueValue = rootView.findViewById(R.id.tv_blue_value);
        tvAlphaValue = rootView.findViewById(R.id.tv_alpha_value);
        colorPreview = rootView.findViewById(R.id.color_preview);
        
        // 设置最大值
        seekBarRed.setMax(255);
        seekBarGreen.setMax(255);
        seekBarBlue.setMax(255);
        seekBarAlpha.setMax(255);
        
        // 设置初始值
        seekBarRed.setProgress(currentRed);
        seekBarGreen.setProgress(currentGreen);
        seekBarBlue.setProgress(currentBlue);
        seekBarAlpha.setProgress(currentAlpha);
        
        updateValueTexts();
        updateColorPreview();
        
        // 红色滑块监听
        seekBarRed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentRed = progress;
                tvRedValue.setText(String.valueOf(progress));
                updateColorPreview();
                updateHexColor();
                notifyColorChanged();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 绿色滑块监听
        seekBarGreen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentGreen = progress;
                tvGreenValue.setText(String.valueOf(progress));
                updateColorPreview();
                updateHexColor();
                notifyColorChanged();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 蓝色滑块监听
        seekBarBlue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentBlue = progress;
                tvBlueValue.setText(String.valueOf(progress));
                updateColorPreview();
                updateHexColor();
                notifyColorChanged();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 透明度滑块监听
        seekBarAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentAlpha = progress;
                tvAlphaValue.setText(String.valueOf(progress));
                updateColorPreview();
                updateHexColor();
                notifyColorChanged();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 十六进制颜色输入
        etHexColor.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String hex = s.toString();
                if (hex.matches("^#[0-9A-Fa-f]{6}$") || hex.matches("^#[0-9A-Fa-f]{8}$")) {
                    try {
                        int color = Color.parseColor(hex);
                        currentRed = Color.red(color);
                        currentGreen = Color.green(color);
                        currentBlue = Color.blue(color);
                        currentAlpha = Color.alpha(color);
                        updateSliders();
                        updateValueTexts();
                        updateColorPreview();
                        notifyColorChanged();
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        });
    }
    
    private void updateValueTexts() {
        tvRedValue.setText(String.valueOf(currentRed));
        tvGreenValue.setText(String.valueOf(currentGreen));
        tvBlueValue.setText(String.valueOf(currentBlue));
        tvAlphaValue.setText(String.valueOf(currentAlpha));
    }
    
    private void updateSliders() {
        seekBarRed.setProgress(currentRed);
        seekBarGreen.setProgress(currentGreen);
        seekBarBlue.setProgress(currentBlue);
        seekBarAlpha.setProgress(currentAlpha);
    }
    
    private void updateColorPreview() {
        int color = Color.argb(currentAlpha, currentRed, currentGreen, currentBlue);
        colorPreview.setBackgroundColor(color);
    }
    
    private void updateHexColor() {
        if (currentAlpha == 255) {
            String hex = String.format("#%02X%02X%02X", currentRed, currentGreen, currentBlue);
            etHexColor.setText(hex);
        } else {
            String hex = String.format("#%02X%02X%02X%02X", currentAlpha, currentRed, currentGreen, currentBlue);
            etHexColor.setText(hex);
        }
    }
    
    private void notifyColorChanged() {
        if (listener != null) {
            listener.onColorChanged(getCurrentColor());
        }
    }
    
    public int getCurrentColor() {
        return Color.argb(currentAlpha, currentRed, currentGreen, currentBlue);
    }
    
    public void setInitialColor(int color) {
        currentRed = Color.red(color);
        currentGreen = Color.green(color);
        currentBlue = Color.blue(color);
        currentAlpha = Color.alpha(color);
        updateSliders();
        updateValueTexts();
        updateColorPreview();
        updateHexColor();
    }
    
    public void setColorListener(OnColorChangedListener listener) {
        this.listener = listener;
    }
}