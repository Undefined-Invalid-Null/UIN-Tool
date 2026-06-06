package com.UIN.Tool.ui.tools;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.FragmentToolsCategoryBinding;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.common.BaseFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolsFragment extends BaseFragment {

    private FragmentToolsCategoryBinding binding;
    private CategoryAdapter categoryAdapter;
    private PluginManager pluginManager;

    private boolean isCategoryView = true;
    private String currentKeyword = "";
    private List<PluginInfo> currentPlugins = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentToolsCategoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    protected void initViews(View view) {
        // binding 已经在 onCreateView 中创建
    }

    @Override
    protected void initData() {
        if (getContext() != null) {
            pluginManager = PluginManager.getInstance(getContext());
        }
        loadData();
    }

    @Override
    protected void setupListeners() {
        if (binding == null) return;
        
        binding.btnSearch.setOnClickListener(v -> toggleSearch());
        binding.btnSwitchView.setOnClickListener(v -> toggleView());
        binding.btnClearSearch.setOnClickListener(v -> clearSearch());
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                currentKeyword = s.toString();
                loadData();
            }
        });
    }

    private void toggleSearch() {
        if (binding == null) return;
        if (binding.searchLayout.getVisibility() == View.GONE) {
            binding.searchLayout.setVisibility(View.VISIBLE);
            binding.etSearch.requestFocus();
        } else {
            binding.searchLayout.setVisibility(View.GONE);
            binding.etSearch.setText("");
            currentKeyword = "";
            loadData();
        }
    }

    private void toggleView() {
        if (binding == null) return;
        isCategoryView = !isCategoryView;
        binding.btnSwitchView.setImageResource(isCategoryView ? R.drawable.ic_grid_view : R.drawable.ic_list_view);
        loadData();
    }

    private void clearSearch() {
        if (binding == null) return;
        binding.etSearch.setText("");
        currentKeyword = "";
        loadData();
    }

    private void loadData() {
        if (binding == null) return;
        
        if (pluginManager == null && getContext() != null) {
            pluginManager = PluginManager.getInstance(getContext());
        }
        
        if (pluginManager == null) {
            showToast(getString(R.string.toast_no_plugins));
            return;
        }
        
        if (currentKeyword.isEmpty()) {
            currentPlugins = pluginManager.getInstalledPlugins();
        } else {
            currentPlugins = pluginManager.searchPlugins(currentKeyword);
        }

        if (currentPlugins == null) {
            currentPlugins = new ArrayList<>();
        }

        if (isCategoryView) {
            showCategoryView();
        } else {
            showListView();
        }

        if (currentPlugins.isEmpty() && !isSearching()) {
            // 静默处理，不显示 Toast
        } else if (currentPlugins.isEmpty() && isSearching()) {
            showToast(getString(R.string.no_search_results, currentKeyword));
        }
    }

    private boolean isSearching() {
        return !currentKeyword.isEmpty();
    }

    private void showCategoryView() {
        if (binding == null || pluginManager == null) return;
        
        List<String> categories = new ArrayList<>();
        Map<String, List<PluginInfo>> categoryMap = new HashMap<>();

        if (isSearching()) {
            categories.add(getString(R.string.search_results, currentPlugins.size()));
            categoryMap.put(categories.get(0), currentPlugins);
        } else {
            categories = pluginManager.getAllCategories();
            for (String category : categories) {
                if (category.equals(getString(R.string.all_category))) {
                    categoryMap.put(category, pluginManager.getInstalledPlugins());
                } else {
                    categoryMap.put(category, pluginManager.getPluginsByCategory(category));
                }
            }
        }

        categoryAdapter = new CategoryAdapter(requireContext(), categories, categoryMap,
                pluginInfo -> {
                    if (pluginManager != null) {
                        pluginManager.openPlugin(pluginInfo.pluginId, getContext());
                    }
                });
        binding.expandableListView.setAdapter(categoryAdapter);

        for (int i = 0; i < categoryAdapter.getGroupCount(); i++) {
            binding.expandableListView.expandGroup(i);
        }
    }

    private void showListView() {
        if (binding == null) return;
        
        List<String> categories = new ArrayList<>();
        categories.add(getString(R.string.all_plugins));
        Map<String, List<PluginInfo>> categoryMap = new HashMap<>();
        categoryMap.put(getString(R.string.all_plugins), currentPlugins);

        categoryAdapter = new CategoryAdapter(requireContext(), categories, categoryMap,
                pluginInfo -> {
                    if (pluginManager != null) {
                        pluginManager.openPlugin(pluginInfo.pluginId, getContext());
                    }
                });
        binding.expandableListView.setAdapter(categoryAdapter);
        binding.expandableListView.expandGroup(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            pluginManager = PluginManager.getInstance(getContext());
        }
        loadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}