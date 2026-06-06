package com.UIN.Tool.ui.common;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.UIN.Tool.utils.UIConfig;

public abstract class BaseFragment extends Fragment {

    protected UIConfig uiConfig;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        uiConfig = UIConfig.getInstance(requireContext());
        uiConfig.applyTheme(requireActivity());
        initViews(view);
        initData();
        setupListeners();
    }

    /**
     * 初始化视图
     */
    protected void initViews(View view) {}

    /**
     * 初始化数据
     */
    protected void initData() {}

    /**
     * 设置监听器
     */
    protected void setupListeners() {}

    /**
     * 显示短提示
     */
    protected void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示长提示
     */
    protected void showLongToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}