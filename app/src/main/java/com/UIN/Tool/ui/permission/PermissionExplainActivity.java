package com.UIN.Tool.ui.permission;

import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityPermissionExplainBinding;
import com.UIN.Tool.ui.common.BaseActivity;

public class PermissionExplainActivity extends BaseActivity {

    private ActivityPermissionExplainBinding binding;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_permission_explain;
    }

    @Override
    protected void initViews() {
        binding = ActivityPermissionExplainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.permission_explain_title), true);
    }
}