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
    protected View rootView;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.rootView = view;
        uiConfig = UIConfig.getInstance(requireContext());
        
        // 应用主题到 Activity
        if (getActivity() != null) {
            uiConfig.applyTheme(getActivity());
        }
        
        // 应用主题到 Fragment 中的所有 View
        if (uiConfig != null) {
            try {
                uiConfig.applyThemeToViewTree(view);
            } catch (Exception e) {
                // 忽略主题应用中的异常，不影响正常使用
                e.printStackTrace();
            }
        }
        
        initViews(view);
        initData();
        setupListeners();
    }

    protected void initViews(View view) {}

    protected void initData() {}

    protected void setupListeners() {}

    protected void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    protected void showLongToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 每次恢复时重新应用主题
        if (rootView != null && uiConfig != null && getActivity() != null) {
            try {
                uiConfig.applyTheme(getActivity());
                uiConfig.applyThemeToViewTree(rootView);
            } catch (Exception e) {
                // 忽略主题应用中的异常
                e.printStackTrace();
            }
        }
    }
}