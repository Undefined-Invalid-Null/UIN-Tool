package com.UIN.Tool.ui.tools;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;  // 添加这个 import

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.FragmentToolsCategoryBinding;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;
import com.UIN.Tool.ui.common.BaseFragment;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.ui.tools.PluginShortcutHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ... 其余代码保持不变

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
                new CategoryAdapter.OnPluginClickListener() {
                    @Override
                    public void onPluginClick(PluginInfo plugin) {
                        if (pluginManager != null) {
                            pluginManager.openPlugin(plugin.pluginId, getContext());
                        }
                    }
                    
                    @Override
                    public void onPluginLongClick(PluginInfo plugin) {
                        showPluginDetailDialog(plugin);
                    }
                });
        binding.expandableListView.setAdapter(categoryAdapter);

        for (int i = 0; i < categoryAdapter.getGroupCount(); i++) {
            binding.expandableListView.expandGroup(i);
        }
        
        // 设置指示器位置
        binding.expandableListView.setIndicatorBounds(
            binding.expandableListView.getWidth() - 50, 
            binding.expandableListView.getWidth()
        );
    }

    private void showListView() {
        if (binding == null) return;
        
        List<String> categories = new ArrayList<>();
        categories.add(getString(R.string.all_plugins));
        Map<String, List<PluginInfo>> categoryMap = new HashMap<>();
        categoryMap.put(getString(R.string.all_plugins), currentPlugins);

        categoryAdapter = new CategoryAdapter(requireContext(), categories, categoryMap,
                new CategoryAdapter.OnPluginClickListener() {
                    @Override
                    public void onPluginClick(PluginInfo plugin) {
                        if (pluginManager != null) {
                            pluginManager.openPlugin(plugin.pluginId, getContext());
                        }
                    }
                    
                    @Override
                    public void onPluginLongClick(PluginInfo plugin) {
                        showPluginDetailDialog(plugin);
                    }
                });
        binding.expandableListView.setAdapter(categoryAdapter);
        binding.expandableListView.expandGroup(0);
    }

    private boolean isSearching() {
        return !currentKeyword.isEmpty();
    }

    private void showPluginDetailDialog(PluginInfo plugin) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_plugin_detail, null);
        TextView tvName = dialogView.findViewById(R.id.tv_name);
        TextView tvId = dialogView.findViewById(R.id.tv_id);
        TextView tvVersion = dialogView.findViewById(R.id.tv_version);
        TextView tvAuthor = dialogView.findViewById(R.id.tv_author);
        TextView tvCategory = dialogView.findViewById(R.id.tv_category);
        TextView tvDescription = dialogView.findViewById(R.id.tv_description);
        View btnRun = dialogView.findViewById(R.id.btn_run);
        View btnShortcut = dialogView.findViewById(R.id.btn_shortcut);
        View btnChangeCategory = dialogView.findViewById(R.id.btn_change_category);
        View btnUninstall = dialogView.findViewById(R.id.btn_uninstall);

        tvName.setText(plugin.name);
        tvId.setText(plugin.pluginId);
        tvVersion.setText(plugin.versionName + " (" + plugin.version + ")");
        tvAuthor.setText(plugin.author != null && !plugin.author.isEmpty() ? plugin.author : getString(R.string.plugin_unknown_author));
        String categoryText = (plugin.category != null && !plugin.category.isEmpty()) ? plugin.category : getString(R.string.plugin_default_category);
        tvCategory.setText(categoryText);
        tvDescription.setText(plugin.description != null && !plugin.description.isEmpty() ? plugin.description : getString(R.string.plugin_no_description));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(plugin.name)
                .setView(dialogView)
                .setPositiveButton(R.string.close, null)
                .create();

        btnRun.setOnClickListener(v -> {
            if (pluginManager != null) {
                pluginManager.openPlugin(plugin.pluginId, getContext());
            }
            dialog.dismiss();
        });

        btnShortcut.setOnClickListener(v -> {
            PluginShortcutHelper.createShortcut(requireContext(), plugin);
            showToast(getString(R.string.creating_shortcut));
            dialog.dismiss();
        });

        btnChangeCategory.setOnClickListener(v -> {
            showCategoryChangeDialogForPlugin(plugin);
            dialog.dismiss();
        });

        btnUninstall.setOnClickListener(v -> {
            DialogHelper.confirmDialog(requireContext(), getString(R.string.dialog_title_confirm),
                    getString(R.string.uninstall_plugin_confirm, plugin.name),
                    (d, w) -> {
                        if (pluginManager != null) {
                            pluginManager.uninstallPlugin(plugin.pluginId);
                            loadData();
                            showToast(getString(R.string.toast_uninstall_success, plugin.name));
                        }
                        dialog.dismiss();
                    }).show();
        });

        dialog.show();
    }

    private void showCategoryChangeDialogForPlugin(PluginInfo info) {
        List<String> categories = pluginManager.getAllCategories();
        categories.remove(getString(R.string.all_category));
        
        final String NEW_CATEGORY_OPTION = "+ " + getString(R.string.create_new_category);
        List<String> displayCategories = new ArrayList<>(categories);
        displayCategories.add(NEW_CATEGORY_OPTION);
        
        String[] categoryArray = displayCategories.toArray(new String[0]);

        DialogHelper.listDialog(requireContext(), getString(R.string.dialog_title_select_category), categoryArray,
                (dialog, which) -> {
                    String selected = categoryArray[which];
                    if (selected.equals(NEW_CATEGORY_OPTION)) {
                        showCreateCategoryDialogForPlugin(info);
                    } else {
                        boolean success = pluginManager.updatePluginCategory(info.pluginId, selected);
                        if (success) {
                            showToast(getString(R.string.category_updated, selected));
                            loadData();
                        }
                    }
                }).show();
    }

    private void showCreateCategoryDialogForPlugin(PluginInfo info) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        final EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.category_name_hint));
        input.setPadding(50, 20, 50, 20);
        
        builder.setTitle(R.string.category_manager_title)
                .setView(input)
                .setPositiveButton(R.string.add_category, (dialog, which) -> {
                    String newCategory = input.getText().toString().trim();
                    if (!newCategory.isEmpty()) {
                        pluginManager.addCategory(newCategory);
                        pluginManager.updatePluginCategory(info.pluginId, newCategory);
                        showToast(getString(R.string.category_updated, newCategory));
                        loadData();
                    } else {
                        showToast(getString(R.string.category_name_empty));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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