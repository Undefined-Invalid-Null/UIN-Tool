package com.UIN.Tool.ui.dev;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityCodeEditorBinding;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeEditorActivity extends AppCompatActivity {

    private ActivityCodeEditorBinding binding;
    private List<String> fileList = new ArrayList<>();
    private Map<String, String> fileContents = new HashMap<>();
    private String currentFile = "";
    private String uiType;
    private String mainClass;
    private String pluginName;
    private String pluginId;
    private boolean hasChanges = false;
    private boolean isSidebarVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCodeEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        loadData();
        setupFileTree();
        setupListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("代码编辑器");
        }
        toolbar.setNavigationOnClickListener(v -> toggleSidebar());
    }

    private void loadData() {
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            fileList = bundle.getStringArrayList("file_list");
            Serializable serializable = bundle.getSerializable("file_contents");
            if (serializable instanceof HashMap) {
                fileContents = (HashMap<String, String>) serializable;
            }
            uiType = bundle.getString("ui_type");
            mainClass = bundle.getString("main_class");
            pluginName = bundle.getString("plugin_name");
            pluginId = bundle.getString("plugin_id");
            
            if (fileList == null || fileList.isEmpty()) {
                fileList = new ArrayList<>();
                if ("web".equals(uiType)) {
                    fileList.add("web/index.html");
                    fileList.add("web/style.css");
                    fileList.add("web/script.js");
                } else {
                    String className = mainClass.substring(mainClass.lastIndexOf('.') + 1);
                    String packageName = mainClass.substring(0, mainClass.lastIndexOf('.'));
                    String packagePath = packageName.replace('.', '/');
                    fileList.add("src/" + packagePath + "/" + className + ".java");
                }
            }
            
            if (fileContents == null) {
                fileContents = new HashMap<>();
            }
            
            if (!fileList.isEmpty()) {
                currentFile = fileList.get(0);
                if (!fileContents.containsKey(currentFile)) {
                    fileContents.put(currentFile, "");
                }
            }
        }
        
        if (fileList == null) {
            fileList = new ArrayList<>();
        }
        if (fileContents == null) {
            fileContents = new HashMap<>();
        }
    }

    private void setupFileTree() {
        LinearLayout container = binding.fileTreeContainer;
        container.removeAllViews();
        
        for (int i = 0; i < fileList.size(); i++) {
            String fileName = fileList.get(i);
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_file_tree, container, false);
            
            ImageView ivIcon = itemView.findViewById(R.id.iv_file_icon);
            TextView tvName = itemView.findViewById(R.id.tv_file_name);
            
            ivIcon.setImageResource(getFileIcon(fileName));
            tvName.setText(fileName);
            
            if (fileName.equals(currentFile)) {
                itemView.setBackgroundColor(getColor(R.color.surface_variant));
                tvName.setTextColor(getColor(R.color.primary));
            } else {
                itemView.setBackgroundColor(0);
                tvName.setTextColor(getColor(R.color.text_primary));
            }
            
            final String selectedFile = fileName;
            itemView.setOnClickListener(v -> switchToFile(selectedFile));
            itemView.setOnLongClickListener(v -> {
                showDeleteFileDialog(selectedFile);
                return true;
            });
            
            container.addView(itemView);
        }
        
        if (currentFile != null && fileContents.containsKey(currentFile)) {
            binding.etCode.setText(fileContents.get(currentFile));
            binding.tvCurrentFile.setText(currentFile);
        }
    }

    private int getFileIcon(String fileName) {
        if (fileName.endsWith(".java")) return R.drawable.ic_java;
        if (fileName.endsWith(".html")) return R.drawable.ic_html;
        if (fileName.endsWith(".css")) return R.drawable.ic_css;
        if (fileName.endsWith(".js")) return R.drawable.ic_javascript;
        if (fileName.endsWith(".xml")) return R.drawable.ic_xml;
        if (fileName.endsWith(".json")) return R.drawable.ic_json;
        return R.drawable.ic_file;
    }

    private void switchToFile(String fileName) {
        if (currentFile != null && hasChanges) {
            fileContents.put(currentFile, binding.etCode.getText().toString());
            binding.tvFileStatus.setText("已保存");
            hasChanges = false;
        }
        
        currentFile = fileName;
        binding.tvCurrentFile.setText(currentFile);
        binding.etCode.setText(fileContents.get(currentFile));
        binding.tvFileStatus.setText("未修改");
        binding.tvFileStatus.setTextColor(getColor(R.color.text_hint));
        
        refreshFileTree();
    }

    private void refreshFileTree() {
        LinearLayout container = binding.fileTreeContainer;
        for (int i = 0; i < container.getChildCount(); i++) {
            View itemView = container.getChildAt(i);
            TextView tvName = itemView.findViewById(R.id.tv_file_name);
            String fileName = tvName.getText().toString();
            
            if (fileName.equals(currentFile)) {
                itemView.setBackgroundColor(getColor(R.color.surface_variant));
                tvName.setTextColor(getColor(R.color.primary));
            } else {
                itemView.setBackgroundColor(0);
                tvName.setTextColor(getColor(R.color.text_primary));
            }
        }
    }

    private void toggleSidebar() {
        if (isSidebarVisible) {
            binding.sidebarLayout.setVisibility(View.GONE);
            binding.divider.setVisibility(View.GONE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.codeEditorContainer.getLayoutParams();
            params.weight = 100;
            binding.codeEditorContainer.setLayoutParams(params);
            isSidebarVisible = false;
        } else {
            binding.sidebarLayout.setVisibility(View.VISIBLE);
            binding.divider.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.codeEditorContainer.getLayoutParams();
            params.weight = 70;
            binding.codeEditorContainer.setLayoutParams(params);
            isSidebarVisible = true;
        }
    }

    private void showAddFileDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_file, null);
        EditText etFileName = dialogView.findViewById(R.id.et_file_name);
        EditText etFileContent = dialogView.findViewById(R.id.et_file_content);
        
        if ("web".equals(uiType)) {
            etFileName.setHint("例如: web/new.html 或 web/js/custom.js");
        } else {
            etFileName.setHint("例如: src/com/example/NewClass.java");
        }
        
        new AlertDialog.Builder(this)
            .setTitle("添加新文件")
            .setView(dialogView)
            .setPositiveButton("添加", (dialog, which) -> {
                String fileName = etFileName.getText().toString().trim();
                String content = etFileContent.getText().toString();
                if (!fileName.isEmpty()) {
                    if (!fileList.contains(fileName)) {
                        fileList.add(fileName);
                        fileContents.put(fileName, content.isEmpty() ? getDefaultFileContent(fileName) : content);
                        setupFileTree();
                        Toast.makeText(this, "已添加: " + fileName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "文件已存在", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "请输入文件名", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showDeleteFileDialog(String fileName) {
        if (fileList.size() <= 1) {
            Toast.makeText(this, "至少需要保留一个文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("删除文件")
            .setMessage("确定要删除 " + fileName + " 吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                fileList.remove(fileName);
                fileContents.remove(fileName);
                setupFileTree();
                if (!fileList.isEmpty() && fileName.equals(currentFile)) {
                    switchToFile(fileList.get(0));
                }
                Toast.makeText(this, "已删除: " + fileName, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private String getDefaultFileContent(String fileName) {
        if (fileName.endsWith(".html")) {
            return "<!DOCTYPE html>\n<html>\n<head>\n    <meta charset=\"UTF-8\">\n    <title>New Page</title>\n</head>\n<body>\n    <h1>New Page</h1>\n</body>\n</html>";
        } else if (fileName.endsWith(".css")) {
            return "/* New CSS File */\n\n* {\n    margin: 0;\n    padding: 0;\n    box-sizing: border-box;\n}";
        } else if (fileName.endsWith(".js")) {
            return "// New JavaScript File\n\nconsole.log('Script loaded');";
        } else if (fileName.endsWith(".java")) {
            String className = fileName.substring(fileName.lastIndexOf('/') + 1, fileName.lastIndexOf('.'));
            String packageName = "com.example";
            return "package " + packageName + ";\n\npublic class " + className + " {\n    \n    public " + className + "() {\n        // Constructor\n    }\n    \n    public void method() {\n        // Method implementation\n    }\n}";
        } else {
            return "# New File\n\nContent goes here...";
        }
    }

    private void setupListeners() {
        binding.btnAddFile.setOnClickListener(v -> showAddFileDialog());
        binding.btnDeleteFile.setOnClickListener(v -> {
            if (currentFile != null) {
                showDeleteFileDialog(currentFile);
            }
        });
        binding.btnSave.setOnClickListener(v -> saveAndClose());
        binding.btnCancel.setOnClickListener(v -> cancel());
        
        binding.etCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                binding.tvFileStatus.setText("已修改");
                binding.tvFileStatus.setTextColor(getColor(R.color.warning));
                hasChanges = true;
            }
        });
    }

    private void saveAndClose() {
        if (currentFile != null) {
            fileContents.put(currentFile, binding.etCode.getText().toString());
        }
        
        Intent resultIntent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("file_list", new ArrayList<>(fileList));
        bundle.putSerializable("file_contents", (HashMap<String, String>) fileContents);
        resultIntent.putExtras(bundle);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void cancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (hasChanges) {
            new AlertDialog.Builder(this)
                .setTitle("未保存的更改")
                .setMessage("您有未保存的更改，是否保存？")
                .setPositiveButton("保存", (dialog, which) -> saveAndClose())
                .setNegativeButton("不保存", (dialog, which) -> cancel())
                .setNeutralButton("取消", null)
                .show();
        } else {
            cancel();
        }
    }
}