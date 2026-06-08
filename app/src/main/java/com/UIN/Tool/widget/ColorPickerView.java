package com.UIN.Tool.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.UIN.Tool.R;

import java.util.ArrayList;
import java.util.List;

public class ColorPickerView extends FrameLayout {

    private GridLayout gridLayout;
    private OnColorSelectedListener listener;
    private int selectedColor = Color.parseColor("#1E3A5F");
    private List<View> colorViews = new ArrayList<>();
    
    // 预设颜色
    private int[] presetColors = {
        Color.parseColor("#1E3A5F"), // 深蓝
        Color.parseColor("#37474F"), // 灰蓝
        Color.parseColor("#4A90E2"), // 蓝色
        Color.parseColor("#2ECC71"), // 绿色
        Color.parseColor("#F39C12"), // 橙色
        Color.parseColor("#E74C3C"), // 红色
        Color.parseColor("#9B59B6"), // 紫色
        Color.parseColor("#1ABC9C"), // 青色
        Color.parseColor("#E67E22"), // 橙色
        Color.parseColor("#34495E"), // 深灰
        Color.parseColor("#7F8C8D"), // 灰色
        Color.parseColor("#95A5A6"), // 浅灰
        Color.parseColor("#F1C40F"), // 黄色
        Color.parseColor("#E84393"), // 粉色
        Color.parseColor("#00BCD4"), // 青色
        Color.parseColor("#8BC34A"), // 亮绿
        Color.parseColor("#FF5722"), // 橙红
        Color.parseColor("#607D8B")  // 蓝灰
    };
    
    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
    
    public ColorPickerView(Context context) {
        super(context);
        init();
    }
    
    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public ColorPickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 创建主布局
        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        
        // 创建颜色网格
        gridLayout = new GridLayout(getContext());
        gridLayout.setColumnCount(6);
        
        int size = getResources().getDimensionPixelSize(R.dimen.avatar_medium);
        int margin = getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        
        for (int i = 0; i < presetColors.length; i++) {
            int color = presetColors[i];
            View colorView = new View(getContext());
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            colorView.setBackground(drawable);
            
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(margin, margin, margin, margin);
            colorView.setLayoutParams(params);
            colorView.setTag(color);
            
            final int finalColor = color;
            colorView.setOnClickListener(v -> {
                selectedColor = finalColor;
                if (listener != null) {
                    listener.onColorSelected(selectedColor);
                }
                updateSelection(v);
            });
            
            gridLayout.addView(colorView);
            colorViews.add(colorView);
        }
        
        mainLayout.addView(gridLayout);
        addView(mainLayout);
        
        // 初始选中第一个
        if (!colorViews.isEmpty()) {
            updateSelection(colorViews.get(0));
        }
    }
    
    private void updateSelection(View selectedView) {
        for (View view : colorViews) {
            GradientDrawable drawable = (GradientDrawable) view.getBackground();
            if (view == selectedView) {
                // 选中状态：添加白色边框
                drawable.setStroke(4, Color.WHITE);
            } else {
                drawable.setStroke(0, Color.TRANSPARENT);
            }
            view.setBackground(drawable);
        }
    }
    
    public void setInitialColor(int color) {
        for (View view : colorViews) {
            int viewColor = (int) view.getTag();
            if (viewColor == color) {
                updateSelection(view);
                selectedColor = color;
                break;
            }
        }
    }
    
    public void setColorListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }
    
    public int getSelectedColor() {
        return selectedColor;
    }
}