package com.UIN.Tool.ui.dev;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.TemplateUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class NativePluginWizardActivity extends BasePluginWizardActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        uiType = "native";
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String getCodeStepTitle() {
        return "编写插件代码";
    }

    @Override
    protected String getCodeStepDesc() {
        return "实现 PluginInterface 接口，通过 Java 代码创建 UI";
    }

    @Override
    protected String getUiTypeString() {
        return "native";
    }

    @Override
    protected String getEntryPath() {
        return "";
    }

    @Override
    protected void addUiTypeButton(LinearLayout configView) {
        Button btnInfo = new Button(this);
        btnInfo.setText("当前模式: 原生代码 UI");
        btnInfo.setEnabled(false);
        btnInfo.setBackgroundTintList(getResources().getColorStateList(R.color.primary));
        btnInfo.setTextColor(getResources().getColor(R.color.white));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 16;
        btnInfo.setLayoutParams(params);
        configView.addView(btnInfo);
    }

    @Override
    protected void loadCodeTemplate() {
        if (pluginId.isEmpty() || mainClass.isEmpty()) return;
        
        String className = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        String packageName = mainClass.substring(0, mainClass.lastIndexOf('.'));
        String packagePath = packageName.replace('.', '/');
        String javaFilePath = "src/" + packagePath + "/" + className + ".java";
        
        if (!fileList.contains(javaFilePath)) {
            fileList.add(javaFilePath);
        }
        
        Map<String, String> vars = getTemplateVars();
        String javaCode;
        try {
            javaCode = TemplateUtils.generateJavaCode(this, "native", vars);
        } catch (Exception e) {
            javaCode = getFallbackCodeTemplate();
        }
        fileContents.put(javaFilePath, javaCode);
        
        updateFileListForCodeStep();
    }

    @Override
    protected void updateCodeHint(TextView tvCodeHint, TextView tvCodeStatus) {
        tvCodeHint.setText(
            "📌 原生插件开发提示\n\n" +
            "• 必须实现 PluginInterface 接口的所有方法\n" +
            "• UI 必须通过 Java 代码动态创建，不能使用 XML\n" +
            "• 示例代码已包含基础的 LinearLayout + Button\n" +
            "• 可以添加任何原生 Android 控件\n" +
            "• 修改后需要重新编译生成 DEX"
        );
        tvCodeStatus.setText("原生模式");
        tvCodeStatus.setTextColor(getResources().getColor(R.color.primary));
    }

    @Override
    protected void saveUiSpecificFiles(File workDir) throws Exception {
        // 原生插件不需要额外文件
    }

    @Override
    protected String getJavaCodeTemplate(Map<String, String> vars) {
        try {
            return TemplateUtils.generateJavaCode(this, "native", vars);
        } catch (Exception e) {
            return getFallbackCodeTemplate();
        }
    }

    @Override
    protected void generateReadme(File workDir) throws Exception {
        Map<String, String> vars = new HashMap<>();
        vars.put("PLUGIN_NAME", pluginName);
        vars.put("PLUGIN_ID", pluginId);
        vars.put("PLUGIN_VERSION", pluginVersion);
        vars.put("PLUGIN_VERSION_NAME", pluginVersionName);
        vars.put("PLUGIN_AUTHOR", pluginAuthor);
        vars.put("UI_TYPE", "原生代码");
        
        String className = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        String packageName = mainClass.substring(0, mainClass.lastIndexOf('.'));
        vars.put("MAIN_CLASS_PATH", packageName.replace('.', '/') + "/" + className);
        
        String readme = TemplateUtils.generateReadme(this, vars);
        File readmeFile = new File(workDir, "README.md");
        writeStringToFile(readmeFile, readme);
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
        message.append("   • plugin.dex (占位文件)\n");
        message.append("   • src/ (Java 源码)\n\n");
        message.append("⚠️ 注意：\n");
        message.append("• plugin.dex 是占位文件，需要按 README.md 编译\n");
        message.append("• 编译后可替换 plugin.dex 文件");
        return message.toString();
    }

    private String getFallbackCodeTemplate() {
        String className = mainClass.substring(mainClass.lastIndexOf('.') + 1);
        String packageName = mainClass.substring(0, mainClass.lastIndexOf('.'));
        
        return "package " + packageName + ";\n\n" +
               "import android.content.Context;\n" +
               "import android.os.Bundle;\n" +
               "import android.view.View;\n" +
               "import android.view.ViewGroup;\n" +
               "import android.widget.Button;\n" +
               "import android.widget.LinearLayout;\n" +
               "import android.widget.TextView;\n" +
               "import android.widget.Toast;\n\n" +
               "import com.UIN.Tool.plugin.PluginInterface;\n\n" +
               "public class " + className + " implements PluginInterface {\n\n" +
               "    private Context context;\n\n" +
               "    @Override\n" +
               "    public View onCreateView(Context context, ViewGroup container, Bundle savedInstanceState) {\n" +
               "        this.context = context;\n\n" +
               "        LinearLayout layout = new LinearLayout(context);\n" +
               "        layout.setOrientation(LinearLayout.VERTICAL);\n" +
               "        layout.setPadding(50, 50, 50, 50);\n\n" +
               "        TextView title = new TextView(context);\n" +
               "        title.setText(\"" + pluginName + "\");\n" +
               "        title.setTextSize(24);\n\n" +
               "        Button button = new Button(context);\n" +
               "        button.setText(\"点击我\");\n" +
               "        button.setOnClickListener(v -> {\n" +
               "            Toast.makeText(context, \"插件运行成功！\", Toast.LENGTH_SHORT).show();\n" +
               "        });\n\n" +
               "        layout.addView(title);\n" +
               "        layout.addView(button);\n\n" +
               "        return layout;\n" +
               "    }\n\n" +
               "    @Override public void onResume(){}\n" +
               "    @Override public void onPause(){}\n" +
               "    @Override public void onDestroy(){}\n" +
               "    @Override public boolean onBackPressed() { return false; }\n" +
               "    @Override public Bundle onSaveInstanceState() { return null; }\n" +
               "}\n";
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