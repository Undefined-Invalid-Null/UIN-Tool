package com.UIN.Tool.ui.dev;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.LogUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class WebPluginWizardActivity extends BasePluginWizardActivity {

    private int webTemplateType = 0; // 0=完整模板, 1=空白模板, 2=导入已有项目, 3=跳过
    private Uri importedWebUri = null;
    private Button btnUiType;

    private final ActivityResultLauncher<String> importZipLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    importedWebUri = uri;
                    webTemplateType = 2;
                    Toast.makeText(this, "已选择 ZIP 文件，将在打包时导入", Toast.LENGTH_LONG).show();
                    updateUiTypeButtonText();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        uiType = "web";
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String getCodeStepTitle() {
        return "配置 Web 插件";
    }

    @Override
    protected String getCodeStepDesc() {
        return "Web 插件不需要编写 Java 代码，只需准备 HTML/CSS/JS 文件";
    }

    @Override
    protected String getUiTypeString() {
        return "web";
    }

    @Override
    protected String getEntryPath() {
        return "web/index.html";
    }

    @Override
    protected void addUiTypeButton(LinearLayout configView) {
        btnUiType = new Button(this);
        updateUiTypeButtonText();
        btnUiType.setBackgroundTintList(getResources().getColorStateList(R.color.gray_medium));
        btnUiType.setTextColor(getResources().getColor(R.color.black));
        btnUiType.setOnClickListener(v -> showWebOptionsDialog());
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 16;
        btnUiType.setLayoutParams(params);
        configView.addView(btnUiType);
    }

    private void updateUiTypeButtonText() {
        if (btnUiType == null) return;
        String optionText;
        switch (webTemplateType) {
            case 0:
                optionText = "完整模板 (生成示例 HTML/CSS/JS)";
                break;
            case 1:
                optionText = "空白模板 (生成基础 HTML 框架)";
                break;
            case 2:
                optionText = "导入已有 Web 项目 (ZIP 文件)";
                break;
            default:
                optionText = "跳过 (创建空 web 目录)";
                break;
        }
        btnUiType.setText("Web 插件类型: " + optionText);
    }

    private void showWebOptionsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_web_options, null);
        
        RadioButton rbFull = dialogView.findViewById(R.id.rb_full);
        RadioButton rbBlank = dialogView.findViewById(R.id.rb_blank);
        RadioButton rbImport = dialogView.findViewById(R.id.rb_import);
        RadioButton rbSkip = dialogView.findViewById(R.id.rb_skip);
        
        switch (webTemplateType) {
            case 0: rbFull.setChecked(true); break;
            case 1: rbBlank.setChecked(true); break;
            case 2: rbImport.setChecked(true); break;
            case 3: rbSkip.setChecked(true); break;
            default: rbFull.setChecked(true);
        }
        
        View cardFull = dialogView.findViewById(R.id.card_full);
        View cardBlank = dialogView.findViewById(R.id.card_blank);
        View cardImport = dialogView.findViewById(R.id.card_import);
        View cardSkip = dialogView.findViewById(R.id.card_skip);
        
        cardFull.setOnClickListener(v -> {
            rbFull.setChecked(true);
            rbBlank.setChecked(false);
            rbImport.setChecked(false);
            rbSkip.setChecked(false);
        });
        cardBlank.setOnClickListener(v -> {
            rbBlank.setChecked(true);
            rbFull.setChecked(false);
            rbImport.setChecked(false);
            rbSkip.setChecked(false);
        });
        cardImport.setOnClickListener(v -> {
            rbImport.setChecked(true);
            rbFull.setChecked(false);
            rbBlank.setChecked(false);
            rbSkip.setChecked(false);
        });
        cardSkip.setOnClickListener(v -> {
            rbSkip.setChecked(true);
            rbFull.setChecked(false);
            rbBlank.setChecked(false);
            rbImport.setChecked(false);
        });
        
        new AlertDialog.Builder(this)
                .setTitle("Web 插件开发选项")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    if (rbFull.isChecked()) {
                        webTemplateType = 0;
                        importedWebUri = null;
                        Toast.makeText(this, "将生成完整的 HTML/CSS/JS 模板", Toast.LENGTH_SHORT).show();
                        loadTemplateFromAssets();
                    } else if (rbBlank.isChecked()) {
                        webTemplateType = 1;
                        importedWebUri = null;
                        Toast.makeText(this, "将生成基础 HTML 框架，请自行填充内容", Toast.LENGTH_SHORT).show();
                        loadTemplateFromAssets();
                    } else if (rbImport.isChecked()) {
                        webTemplateType = 2;
                        importZipLauncher.launch("application/zip");
                    } else {
                        webTemplateType = 3;
                        importedWebUri = null;
                        Toast.makeText(this, "将创建空 web/ 目录，请自行添加 HTML 文件", Toast.LENGTH_SHORT).show();
                    }
                    updateUiTypeButtonText();
                    loadCodeTemplate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadTemplateFromAssets() {
        try {
            if (webTemplateType == 0) {
                // 完整模板 - 加载 index.html, style.css, script.js
                String indexHtml = loadAssetFile("plugin_templates/web/index.html");
                String styleCss = loadAssetFile("plugin_templates/web/style.css");
                String scriptJs = loadAssetFile("plugin_templates/web/script.js");
                
                // 替换变量
                indexHtml = indexHtml.replace("{{PLUGIN_NAME}}", pluginName)
                                     .replace("{{PLUGIN_DESCRIPTION}}", pluginDescription)
                                     .replace("{{PLUGIN_ID}}", pluginId);
                styleCss = styleCss.replace("{{PLUGIN_NAME}}", pluginName);
                scriptJs = scriptJs.replace("{{PLUGIN_NAME}}", pluginName)
                                   .replace("{{PLUGIN_ID}}", pluginId);
                
                fileContents.put("web/index.html", indexHtml);
                fileContents.put("web/style.css", styleCss);
                fileContents.put("web/script.js", scriptJs);
                
                if (!fileList.contains("web/index.html")) fileList.add("web/index.html");
                if (!fileList.contains("web/style.css")) fileList.add("web/style.css");
                if (!fileList.contains("web/script.js")) fileList.add("web/script.js");
                
            } else if (webTemplateType == 1) {
                // 空白模板 - 只加载 blank_index.html
                String blankIndex = loadAssetFile("plugin_templates/web/blank_index.html");
                blankIndex = blankIndex.replace("{{PLUGIN_NAME}}", pluginName)
                                       .replace("{{PLUGIN_DESCRIPTION}}", pluginDescription)
                                       .replace("{{PLUGIN_ID}}", pluginId);
                fileContents.put("web/index.html", blankIndex);
                
                if (!fileList.contains("web/index.html")) fileList.add("web/index.html");
            }
            
            LogUtils.success("WebPluginWizard", "从 assets 加载模板成功");
            
        } catch (Exception e) {
            LogUtils.e("WebPluginWizard", "加载模板失败: " + e.getMessage());
        }
    }
    
    private String loadAssetFile(String path) throws Exception {
        InputStream is = getAssets().open(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        is.close();
        return sb.toString();
    }

    @Override
    protected void loadCodeTemplate() {
        if (webTemplateType == 0 || webTemplateType == 1) {
            loadTemplateFromAssets();
        }
        updateFileListForCodeStep();
    }

    @Override
    protected void updateCodeHint(TextView tvCodeHint, TextView tvCodeStatus) {
        tvCodeHint.setText(
            "📌 Web 插件说明\n\n" +
            "• Web 插件不需要编写 Java 代码\n" +
            "• UI 界面请编辑 web/index.html、web/style.css、web/script.js\n" +
            "• JavaScript 可通过 UINPlugin.callHost() 调用原生功能\n" +
            "• 修改 HTML/CSS/JS 后无需重新编译\n" +
            "• 可以直接导入已有的 HTML/CSS/JS 项目\n\n" +
            "📦 导入已有项目：\n" +
            "• 将您的 web 项目打包成 ZIP 文件\n" +
            "• 在选择插件类型时选择「导入已有 Web 项目」\n" +
            "• 系统会自动解压并复制到插件目录"
        );
        tvCodeStatus.setText("Web 模式 (无需 Java 代码)");
        tvCodeStatus.setTextColor(getResources().getColor(R.color.primary));
    }

    @Override
    protected void saveUiSpecificFiles(File workDir) throws Exception {
        if (webTemplateType == 2 && importedWebUri != null) {
            importWebProject(workDir);
        } else if (webTemplateType == 3) {
            new File(workDir, "web").mkdirs();
        }
    }

    private void importWebProject(File workDir) throws Exception {
        LogUtils.i("WebPluginWizard", "导入 Web 项目: " + importedWebUri.toString());
        
        File tempDir = new File(getCacheDir(), "temp_web_import_" + System.currentTimeMillis());
        tempDir.mkdirs();
        
        File zipFile = new File(tempDir, "import.zip");
        try (InputStream is = getContentResolver().openInputStream(importedWebUri);
             FileOutputStream fos = new FileOutputStream(zipFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        
        File extractDir = new File(tempDir, "extract");
        unzipFile(zipFile, extractDir);
        
        File webDir = findWebDirectory(extractDir);
        
        if (webDir != null && webDir.exists()) {
            copyDirectory(webDir, new File(workDir, "web"));
            LogUtils.success("WebPluginWizard", "Web 项目导入成功");
        } else {
            File indexHtml = findIndexHtml(extractDir);
            if (indexHtml != null && indexHtml.exists()) {
                File targetWebDir = new File(workDir, "web");
                targetWebDir.mkdirs();
                copyDirectory(extractDir, targetWebDir);
                LogUtils.success("WebPluginWizard", "Web 文件导入成功");
            } else {
                throw new Exception("ZIP 文件中未找到有效的 web 项目（需要 index.html 或 web/index.html）");
            }
        }
        
        deleteDirectory(tempDir);
    }

    private File findWebDirectory(File dir) {
        File webDir = new File(dir, "web");
        if (webDir.exists() && webDir.isDirectory()) return webDir;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File result = findWebDirectory(file);
                    if (result != null) return result;
                } else if (file.getName().equalsIgnoreCase("index.html")) {
                    return dir;
                }
            }
        }
        return null;
    }

    private File findIndexHtml(File dir) {
        File indexHtml = new File(dir, "index.html");
        if (indexHtml.exists()) return indexHtml;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File result = findIndexHtml(file);
                    if (result != null) return result;
                } else if (file.getName().equalsIgnoreCase("index.html")) {
                    return file;
                }
            }
        }
        return null;
    }

    private void unzipFile(File zipFile, File destDir) throws Exception {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File targetFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    targetFile.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void copyDirectory(File src, File dst) throws Exception {
        if (!src.exists()) return;
        if (!dst.exists()) dst.mkdirs();
        
        File[] files = src.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(dst, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    @Override
    protected String getJavaCodeTemplate(Map<String, String> vars) {
        return "";
    }

    @Override
    protected void generateReadme(File workDir) throws Exception {
        Map<String, String> vars = new HashMap<>();
        vars.put("PLUGIN_NAME", pluginName);
        vars.put("PLUGIN_ID", pluginId);
        vars.put("PLUGIN_VERSION", pluginVersion);
        vars.put("PLUGIN_VERSION_NAME", pluginVersionName);
        vars.put("PLUGIN_AUTHOR", pluginAuthor);
        vars.put("UI_TYPE", "WebView");
        
        String optionName;
        switch (webTemplateType) {
            case 0: optionName = "完整模板"; break;
            case 1: optionName = "空白模板"; break;
            case 2: optionName = "导入的项目"; break;
            default: optionName = "跳过生成"; break;
        }
        vars.put("WEB_OPTION", optionName);
        
        String readme = generateWebReadme(vars);
        File readmeFile = new File(workDir, "README.md");
        writeStringToFile(readmeFile, readme);
    }

    private String generateWebReadme(Map<String, String> vars) {
        return "# " + vars.get("PLUGIN_NAME") + "\n\n" +
               "## 插件信息\n\n" +
               "- **插件ID**: " + vars.get("PLUGIN_ID") + "\n" +
               "- **版本**: " + vars.get("PLUGIN_VERSION_NAME") + "\n" +
               "- **作者**: " + vars.get("PLUGIN_AUTHOR") + "\n" +
               "- **类型**: WebView 插件\n\n" +
               "## 文件结构\n\n" +
               "```\n" +
               "├── plugin.json\n" +
               "├── icon.png\n" +
               "└── web/\n" +
               "    ├── index.html\n" +
               "    ├── style.css\n" +
               "    └── script.js\n" +
               "```\n\n" +
               "## JavaScript API\n\n" +
               "```javascript\n" +
               "UINPlugin.callHost('toast', '消息');\n" +
               "UINPlugin.callHost('finish', '');\n" +
               "UINPlugin.getPluginInfo();\n" +
               "UINPlugin.getDeviceInfo();\n" +
               "```\n\n" +
               "## 生命周期事件\n\n" +
               "```javascript\n" +
               "window.addEventListener('resume', () => {});\n" +
               "window.addEventListener('pause', () => {});\n" +
               "window.addEventListener('destroy', () => {});\n" +
               "```";
    }

    @Override
    protected String getSuccessMessage(File resultFile) {
        StringBuilder message = new StringBuilder();
        message.append("✅ 打包成功！\n\n");
        message.append("项目目录: ").append(pluginWorkDir).append("\n");
        message.append("TPK 文件: ").append(resultFile.getAbsolutePath()).append("\n\n");
        message.append("📌 TPK 包含以下文件：\n");
        message.append("   • plugin.json\n");
        message.append("   • icon.png\n");
        message.append("   • web/index.html\n");
        
        if (webTemplateType == 0) {
            message.append("   • web/style.css\n");
            message.append("   • web/script.js\n");
        }
        if (webTemplateType == 2) {
            message.append("   • 导入的 Web 项目文件\n");
        }
        
        message.append("\n⚠️ 注意：\n");
        message.append("• Web 插件不需要编译 Java 代码\n");
        message.append("• 修改前端代码无需重新编译，直接重新打开插件即可");
        return message.toString();
    }
    
    @Override
    protected void updateFileListForCodeStep() {
        if (fileListAdapter != null) {
            fileListAdapter.clear();
            fileListAdapter.addAll(fileList);
            fileListAdapter.notifyDataSetChanged();
        }
    }
}