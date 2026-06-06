package com.UIN.Tool.ui.dev;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.compiler.JavaToDexCompiler;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.PreferencesUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BasePluginWizardActivity extends AppCompatActivity {

    protected static final int REQUEST_CODE_EDITOR = 1001;

    protected int currentStep = 0;
    protected LinearLayout stepContainer;
    protected Button btnPrev, btnNext, btnFinish;
    protected TextView tvStepTitle, tvStepDesc;
    protected ProgressBar progressBar;

    protected String pluginId = "com.example.myplugin";
    protected String pluginName = "我的插件";
    protected String pluginAuthor = "开发者";
    protected String pluginDescription = "这是一个示例插件";
    protected String pluginVersion = "1";
    protected String pluginVersionName = "1.0.0";
    protected String mainClass = "com.example.MainPlugin";
    protected String iconPath = "";
    protected List<String> resourcePaths = new ArrayList<>();
    protected String uiType;

    protected String workFolder;
    protected String pluginWorkDir;

    protected Map<String, String> fileContents = new HashMap<>();
    protected List<String> fileList = new ArrayList<>();
    protected ArrayAdapter<String> fileListAdapter;
    protected Spinner spinnerFiles;
    protected EditText etCode;

    protected final ActivityResultLauncher<Intent> iconPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        saveIconFromUri(uri);
                    }
                }
            });

    protected final ActivityResultLauncher<String> resourcePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    addResourceFromUri(uri);
                }
            });

    private final ActivityResultLauncher<Intent> codeEditorLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    Bundle bundle = data.getExtras();
                    if (bundle != null) {
                        List<String> newFileList = bundle.getStringArrayList("file_list");
                        if (newFileList != null) {
                            fileList.clear();
                            fileList.addAll(newFileList);
                        }
                        HashMap<String, String> contentsMap = (HashMap<String, String>) bundle.getSerializable("file_contents");
                        if (contentsMap != null) {
                            fileContents.clear();
                            fileContents.putAll(contentsMap);
                        }
                        updateFileListForCodeStep();
                        Toast.makeText(this, "代码已更新", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_wizard);

        workFolder = PreferencesUtils.getWorkFolder(this);
        
        stepContainer = findViewById(R.id.step_container);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnFinish = findViewById(R.id.btn_finish);
        tvStepTitle = findViewById(R.id.tv_step_title);
        tvStepDesc = findViewById(R.id.tv_step_desc);
        progressBar = findViewById(R.id.progress_bar);

        btnPrev.setOnClickListener(v -> prevStep());
        btnNext.setOnClickListener(v -> nextStep());
        btnFinish.setOnClickListener(v -> finishWizard());

        showStep(0);
    }

    protected void showStep(int step) {
        currentStep = step;
        stepContainer.removeAllViews();

        progressBar.setProgress((step + 1) * 100 / 5);

        switch (step) {
            case 0:
                tvStepTitle.setText("步骤 1/5：配置插件信息");
                tvStepDesc.setText("填写插件的基本配置信息");
                showConfigStep();
                break;
            case 1:
                tvStepTitle.setText("步骤 2/5：设置插件图标");
                tvStepDesc.setText("选择一个 PNG 图片作为图标（可选）");
                showIconStep();
                break;
            case 2:
                tvStepTitle.setText("步骤 3/5：" + getCodeStepTitle());
                tvStepDesc.setText(getCodeStepDesc());
                showCodeStep();
                break;
            case 3:
                tvStepTitle.setText("步骤 4/5：添加资源文件");
                tvStepDesc.setText("可选的图片、音频等资源文件");
                showResourcesStep();
                break;
            case 4:
                tvStepTitle.setText("步骤 5/5：生成项目文件");
                tvStepDesc.setText("生成项目结构并打包为 TPK");
                showPackageStep();
                break;
        }

        btnPrev.setEnabled(step > 0);
        btnNext.setVisibility(step < 4 ? View.VISIBLE : View.GONE);
        btnFinish.setVisibility(step == 4 ? View.VISIBLE : View.GONE);
    }

    protected abstract String getCodeStepTitle();
    protected abstract String getCodeStepDesc();
    protected abstract void loadCodeTemplate();
    protected abstract void updateCodeHint(TextView tvCodeHint, TextView tvCodeStatus);
    protected abstract String getUiTypeString();
    protected abstract void saveUiSpecificFiles(File workDir) throws Exception;
    protected abstract void addUiTypeButton(LinearLayout configView);
    protected abstract String getSuccessMessage(File resultFile);
    protected abstract String getJavaCodeTemplate(Map<String, String> vars);
    protected abstract void generateReadme(File workDir) throws Exception;
    protected abstract String getEntryPath();

    protected void showConfigStep() {
        LinearLayout view = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.wizard_step_config, stepContainer, false);
        EditText etPluginId = view.findViewById(R.id.et_plugin_id);
        EditText etPluginName = view.findViewById(R.id.et_plugin_name);
        EditText etPluginAuthor = view.findViewById(R.id.et_plugin_author);
        EditText etPluginDescription = view.findViewById(R.id.et_plugin_description);
        EditText etPluginVersion = view.findViewById(R.id.et_plugin_version);
        EditText etPluginVersionName = view.findViewById(R.id.et_plugin_version_name);
        EditText etMainClass = view.findViewById(R.id.et_main_class);

        etPluginId.setText(pluginId);
        etPluginName.setText(pluginName);
        etPluginAuthor.setText(pluginAuthor);
        etPluginDescription.setText(pluginDescription);
        etPluginVersion.setText(pluginVersion);
        etPluginVersionName.setText(pluginVersionName);
        etMainClass.setText(mainClass);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                pluginId = etPluginId.getText().toString().trim();
                pluginName = etPluginName.getText().toString().trim();
                pluginAuthor = etPluginAuthor.getText().toString().trim();
                pluginDescription = etPluginDescription.getText().toString().trim();
                pluginVersion = etPluginVersion.getText().toString().trim();
                pluginVersionName = etPluginVersionName.getText().toString().trim();
                mainClass = etMainClass.getText().toString().trim();
                loadCodeTemplate();
                updateFileListForCodeStep();
            }
        };
        etPluginId.addTextChangedListener(watcher);
        etPluginName.addTextChangedListener(watcher);
        etPluginAuthor.addTextChangedListener(watcher);
        etPluginDescription.addTextChangedListener(watcher);
        etPluginVersion.addTextChangedListener(watcher);
        etPluginVersionName.addTextChangedListener(watcher);
        etMainClass.addTextChangedListener(watcher);

        addUiTypeButton(view);
        stepContainer.addView(view);
    }

    protected void showIconStep() {
        View view = LayoutInflater.from(this).inflate(R.layout.wizard_step_icon, stepContainer, false);
        ImageView ivIcon = view.findViewById(R.id.iv_icon_preview);
        Button btnPickIcon = view.findViewById(R.id.btn_pick_icon);
        TextView tvIconPath = view.findViewById(R.id.tv_icon_path);
        Button btnSkipIcon = view.findViewById(R.id.btn_skip_icon);

        if (!iconPath.isEmpty()) {
            File iconFile = new File(iconPath);
            if (iconFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(iconPath);
                ivIcon.setImageBitmap(bitmap);
                tvIconPath.setText(iconFile.getName());
            }
        }

        btnPickIcon.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            iconPickerLauncher.launch(intent);
        });

        btnSkipIcon.setOnClickListener(v -> {
            iconPath = "";
            Toast.makeText(this, "将使用默认图标", Toast.LENGTH_SHORT).show();
            nextStep();
        });

        stepContainer.addView(view);
    }

    protected void saveIconFromUri(Uri uri) {
        try {
            String safePluginId = pluginId.replace("/", "_").replace("\\", "_").replace(":", "_");
            File tempIconDir = new File(getCacheDir(), "temp_icon_" + safePluginId);
            if (!tempIconDir.exists()) tempIconDir.mkdirs();
            
            File iconFile = new File(tempIconDir, "icon.png");
            copyUriToFile(uri, iconFile);
            iconPath = iconFile.getAbsolutePath();
            Toast.makeText(this, "图标已保存", Toast.LENGTH_SHORT).show();
            nextStep();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存图标失败", Toast.LENGTH_SHORT).show();
        }
    }

    protected void showCodeStep() {
        if (fileList.isEmpty() || fileContents.isEmpty()) {
            loadFileListForCodeStep();
        }
        
        Intent intent = new Intent(this, CodeEditorActivity.class);
        intent.putExtra("ui_type", uiType);
        intent.putExtra("main_class", mainClass);
        intent.putExtra("plugin_name", pluginName);
        intent.putExtra("plugin_id", pluginId);
        
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("file_list", new ArrayList<>(fileList));
        bundle.putSerializable("file_contents", new HashMap<>(fileContents));
        intent.putExtras(bundle);
        
        codeEditorLauncher.launch(intent);
        
        View view = LayoutInflater.from(this).inflate(R.layout.wizard_step_code_placeholder, stepContainer, false);
        TextView tvCodeHint = view.findViewById(R.id.tv_code_hint);
        TextView tvCodeStatus = view.findViewById(R.id.tv_code_status);
        Button btnOpenEditor = view.findViewById(R.id.btn_open_editor);
        
        tvCodeHint.setText(getCodeStepHint());
        tvCodeStatus.setText("点击按钮打开代码编辑器");
        
        btnOpenEditor.setOnClickListener(v -> {
            Intent editorIntent = new Intent(this, CodeEditorActivity.class);
            editorIntent.putExtra("ui_type", uiType);
            editorIntent.putExtra("main_class", mainClass);
            editorIntent.putExtra("plugin_name", pluginName);
            editorIntent.putExtra("plugin_id", pluginId);
            
            Bundle editorBundle = new Bundle();
            editorBundle.putStringArrayList("file_list", new ArrayList<>(fileList));
            editorBundle.putSerializable("file_contents", new HashMap<>(fileContents));
            editorIntent.putExtras(editorBundle);
            
            codeEditorLauncher.launch(editorIntent);
        });
        
        stepContainer.addView(view);
    }

    protected String getCodeStepHint() {
        if ("web".equals(uiType)) {
            return "📌 Web 插件说明\n\n" +
                   "• Web 插件不需要编写 Java 代码\n" +
                   "• UI 界面请编辑 web/index.html、web/style.css、web/script.js\n" +
                   "• JavaScript 可通过 UINPlugin.callHost() 调用原生功能\n" +
                   "• 修改 HTML/CSS/JS 后无需重新编译\n" +
                   "• 可以直接导入已有的 HTML/CSS/JS 项目";
        } else {
            return "📌 原生插件开发提示\n\n" +
                   "• 必须实现 PluginInterface 接口的所有方法\n" +
                   "• UI 必须通过 Java 代码动态创建，不能使用 XML\n" +
                   "• 示例代码已包含基础的 LinearLayout + Button\n" +
                   "• 修改后需要重新编译生成 DEX";
        }
    }

    protected void loadFileListForCodeStep() {
        fileList.clear();
        fileContents.clear();
        
        if ("web".equals(uiType)) {
            fileList.add("web/index.html");
            fileList.add("web/style.css");
            fileList.add("web/script.js");
            
            fileContents.put("web/index.html", getDefaultHtmlTemplate());
            fileContents.put("web/style.css", getDefaultCssTemplate());
            fileContents.put("web/script.js", getDefaultJsTemplate());
        } else {
            String className = mainClass.contains(".") ? 
                    mainClass.substring(mainClass.lastIndexOf('.') + 1) : "MainPlugin";
            String packageName = mainClass.contains(".") ? 
                    mainClass.substring(0, mainClass.lastIndexOf('.')) : "com.example";
            String packagePath = packageName.replace('.', '/');
            
            String javaFilePath = "src/" + packagePath + "/" + className + ".java";
            fileList.add(javaFilePath);
            
            Map<String, String> vars = getTemplateVars();
            fileContents.put(javaFilePath, getJavaCodeTemplate(vars));
        }
    }

    protected void updateFileListForCodeStep() {
        // 用于子类重写
    }

    protected Map<String, String> getTemplateVars() {
        String className = mainClass.contains(".") ? 
                mainClass.substring(mainClass.lastIndexOf('.') + 1) : "MainPlugin";
        String packageName = mainClass.contains(".") ? 
                mainClass.substring(0, mainClass.lastIndexOf('.')) : "com.example";
        
        Map<String, String> vars = new HashMap<>();
        vars.put("PACKAGE_NAME", packageName);
        vars.put("CLASS_NAME", className);
        vars.put("PLUGIN_NAME", pluginName);
        vars.put("PLUGIN_ID", pluginId);
        vars.put("PLUGIN_VERSION", pluginVersion);
        vars.put("PLUGIN_VERSION_NAME", pluginVersionName);
        vars.put("PLUGIN_AUTHOR", pluginAuthor);
        vars.put("PLUGIN_DESCRIPTION", pluginDescription);
        return vars;
    }

    protected String getDefaultHtmlTemplate() {
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>" + pluginName + "</title>\n" +
               "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
               "</head>\n" +
               "<body>\n" +
               "    <div class=\"container\">\n" +
               "        <h1>" + pluginName + "</h1>\n" +
               "        <p>" + pluginDescription + "</p>\n" +
               "        <button onclick=\"UINPlugin.callHost('toast', 'Hello from Web Plugin!')\">点击测试</button>\n" +
               "        <button onclick=\"UINPlugin.callHost('finish', '')\">关闭插件</button>\n" +
               "    </div>\n" +
               "    <script src=\"script.js\"></script>\n" +
               "</body>\n" +
               "</html>";
    }

    protected String getDefaultCssTemplate() {
        return "* {\n" +
               "    margin: 0;\n" +
               "    padding: 0;\n" +
               "    box-sizing: border-box;\n" +
               "}\n" +
               "\n" +
               "body {\n" +
               "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
               "    background: #f5f5f5;\n" +
               "    padding: 20px;\n" +
               "}\n" +
               "\n" +
               ".container {\n" +
               "    max-width: 600px;\n" +
               "    margin: 0 auto;\n" +
               "    background: white;\n" +
               "    border-radius: 16px;\n" +
               "    padding: 24px;\n" +
               "    box-shadow: 0 2px 8px rgba(0,0,0,0.1);\n" +
               "}\n" +
               "\n" +
               "h1 {\n" +
               "    color: #37474F;\n" +
               "    margin-bottom: 12px;\n" +
               "}\n" +
               "\n" +
               "p {\n" +
               "    color: #666;\n" +
               "    margin-bottom: 20px;\n" +
               "}\n" +
               "\n" +
               "button {\n" +
               "    background: #37474F;\n" +
               "    color: white;\n" +
               "    border: none;\n" +
               "    padding: 10px 20px;\n" +
               "    border-radius: 8px;\n" +
               "    margin: 5px;\n" +
               "    cursor: pointer;\n" +
               "}\n" +
               "\n" +
               "button:hover {\n" +
               "    background: #263238;\n" +
               "}";
    }

    protected String getDefaultJsTemplate() {
        return "// " + pluginName + " - Web Plugin JavaScript\n" +
               "console.log('Web Plugin loaded: " + pluginId + "');\n" +
               "\n" +
               "// 获取插件信息\n" +
               "try {\n" +
               "    var pluginInfo = JSON.parse(UINPlugin.getPluginInfo());\n" +
               "    console.log('Plugin Info:', pluginInfo);\n" +
               "} catch(e) {\n" +
               "    console.log('Failed to get plugin info:', e);\n" +
               "}\n" +
               "\n" +
               "// 获取设备信息\n" +
               "try {\n" +
               "    var deviceInfo = JSON.parse(UINPlugin.getDeviceInfo());\n" +
               "    console.log('Device Info:', deviceInfo);\n" +
               "} catch(e) {\n" +
               "    console.log('Failed to get device info:', e);\n" +
               "}\n" +
               "\n" +
               "// 生命周期事件监听\n" +
               "window.addEventListener('resume', function() {\n" +
               "    console.log('Plugin resumed');\n" +
               "});\n" +
               "\n" +
               "window.addEventListener('pause', function() {\n" +
               "    console.log('Plugin paused');\n" +
               "});\n" +
               "\n" +
               "window.addEventListener('destroy', function() {\n" +
               "    console.log('Plugin destroyed');\n" +
               "});";
    }

    protected void showResourcesStep() {
        View view = LayoutInflater.from(this).inflate(R.layout.wizard_step_resources, stepContainer, false);
        RecyclerView rvResources = view.findViewById(R.id.rv_resources);
        Button btnAddResource = view.findViewById(R.id.btn_add_resource);
        Button btnSkipResources = view.findViewById(R.id.btn_skip_resources);

        ResourcesAdapter adapter = new ResourcesAdapter(resourcePaths);
        rvResources.setLayoutManager(new LinearLayoutManager(this));
        rvResources.setAdapter(adapter);

        btnAddResource.setOnClickListener(v -> resourcePickerLauncher.launch("*/*"));
        btnSkipResources.setOnClickListener(v -> nextStep());

        stepContainer.addView(view);
    }

    protected void addResourceFromUri(Uri uri) {
        try {
            String fileName = getFileNameFromUri(uri);
            
            String safePluginId = pluginId.replace("/", "_").replace("\\", "_").replace(":", "_");
            File tempResDir = new File(getCacheDir(), "temp_res_" + safePluginId);
            if (!tempResDir.exists()) tempResDir.mkdirs();
            
            File resourceFile = new File(tempResDir, fileName);
            copyUriToFile(uri, resourceFile);
            resourcePaths.add(resourceFile.getAbsolutePath());
            Toast.makeText(this, "已添加: " + fileName, Toast.LENGTH_SHORT).show();
            showStep(3);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "添加资源失败", Toast.LENGTH_SHORT).show();
        }
    }

    protected String getFileNameFromUri(Uri uri) {
        String fileName = "resource";
        try {
            String[] projection = {"_display_name"};
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex("_display_name");
                if (index >= 0) fileName = cursor.getString(index);
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileName;
    }

    protected void showPackageStep() {
        View view = LayoutInflater.from(this).inflate(R.layout.wizard_step_compile, stepContainer, false);
        TextView tvStatus = view.findViewById(R.id.tv_compile_status);
        Button btnPackage = view.findViewById(R.id.btn_compile);
        Button btnOpenFolder = view.findViewById(R.id.btn_open_folder);
        ProgressBar packageProgress = view.findViewById(R.id.compile_progress);
        
        btnPackage.setText("生成项目文件");

        btnPackage.setOnClickListener(v -> {
            btnPackage.setEnabled(false);
            packageProgress.setVisibility(View.VISIBLE);
            tvStatus.setText("正在生成项目文件...");
            
            String safePluginId = pluginId.replace("/", "_").replace("\\", "_").replace(":", "_");
            pluginWorkDir = new File(workFolder, safePluginId).getAbsolutePath();
            File workDir = new File(pluginWorkDir);
            
            boolean saveSuccess = saveAllProjectFiles(workDir);
            
            if (!saveSuccess) {
                packageProgress.setVisibility(View.GONE);
                tvStatus.setText("生成项目文件失败\n请检查存储权限");
                btnPackage.setEnabled(true);
                return;
            }
            
            tvStatus.setText("正在打包 TPK...");
            
            File tpkFile = new File(workFolder, pluginId + ".tpk");
            
            JavaToDexCompiler compiler = new JavaToDexCompiler(BasePluginWizardActivity.this);
            compiler.setUiType(uiType);
            compiler.setOnCompileListener(new JavaToDexCompiler.OnCompileListener() {
                @Override
                public void onStart() {
                    tvStatus.setText("开始打包...");
                }
                
                @Override
                public void onProgress(String message) {
                    tvStatus.setText(message);
                }
                
                @Override
                public void onSuccess(File resultFile) {
                    packageProgress.setVisibility(View.GONE);
                    tvStatus.setText(getSuccessMessage(resultFile));
                    btnOpenFolder.setVisibility(View.VISIBLE);
                    btnPackage.setText("已打包");
                }
                
                @Override
                public void onError(String error) {
                    packageProgress.setVisibility(View.GONE);
                    tvStatus.setText("打包失败:\n" + error);
                    btnPackage.setEnabled(true);
                    btnPackage.setText("重试打包");
                }
            });
            
            compiler.packageToTpk(workDir, tpkFile);
        });
        
        btnOpenFolder.setOnClickListener(v -> openFolder());
        stepContainer.addView(view);
    }

    protected boolean saveAllProjectFiles(File workDir) {
        try {
            if (!workDir.exists()) {
                workDir.mkdirs();
            }
            
            JSONObject json = new JSONObject();
            json.put("pluginId", pluginId);
            json.put("version", Integer.parseInt(pluginVersion));
            json.put("versionName", pluginVersionName);
            json.put("minHostVersion", 1);
            json.put("name", pluginName);
            json.put("author", pluginAuthor);
            json.put("description", pluginDescription);
            json.put("icon", "icon.png");
            json.put("mainClass", "web".equals(uiType) ? "" : mainClass);
            json.put("apiLevel", 21);
            json.put("uiType", getUiTypeString());
            json.put("entry", getEntryPath());
            
            File jsonFile = new File(workDir, "plugin.json");
            writeStringToFile(jsonFile, json.toString(4));
            
            for (Map.Entry<String, String> entry : fileContents.entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();
                File targetFile = new File(workDir, filePath);
                targetFile.getParentFile().mkdirs();
                writeStringToFile(targetFile, content);
            }
            
            File iconFile = new File(workDir, "icon.png");
            if (iconPath != null && !iconPath.isEmpty()) {
                File srcIcon = new File(iconPath);
                if (srcIcon.exists()) {
                    copyFile(srcIcon, iconFile);
                } else {
                    iconFile.createNewFile();
                }
            } else {
                iconFile.createNewFile();
            }
            
            if (!resourcePaths.isEmpty()) {
                File resDir = new File(workDir, "res");
                resDir.mkdirs();
                for (String resPath : resourcePaths) {
                    File srcRes = new File(resPath);
                    if (srcRes.exists()) {
                        File dstRes = new File(resDir, srcRes.getName());
                        copyFile(srcRes, dstRes);
                    }
                }
            }
            
            saveUiSpecificFiles(workDir);
            generateReadme(workDir);
            
            Toast.makeText(this, "项目文件已保存到: " + workDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return true;
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    protected void writeStringToFile(File file, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes("UTF-8"));
        fos.close();
    }

    protected void copyFile(File src, File dst) throws Exception {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
        fis.close();
    }

    protected void copyUriToFile(Uri uri, File destFile) throws Exception {
        ContentResolver resolver = getContentResolver();
        InputStream is = resolver.openInputStream(uri);
        FileOutputStream fos = new FileOutputStream(destFile);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
        is.close();
    }

    protected void openFolder() {
        if (pluginWorkDir == null) {
            String safePluginId = pluginId.replace("/", "_").replace("\\", "_").replace(":", "_");
            pluginWorkDir = new File(workFolder, safePluginId).getAbsolutePath();
        }
        File dir = new File(pluginWorkDir);
        if (dir.exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider",
                    dir);
            intent.setDataAndType(uri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "选择文件管理器"));
        } else {
            Toast.makeText(this, "文件夹不存在: " + pluginWorkDir, Toast.LENGTH_SHORT).show();
        }
    }

    protected void prevStep() {
        if (currentStep > 0) {
            showStep(currentStep - 1);
        }
    }

    protected void nextStep() {
        if (validateCurrentStep()) {
            if (currentStep < 4) {
                showStep(currentStep + 1);
            }
        }
    }

    protected boolean validateCurrentStep() {
        switch (currentStep) {
            case 0:
                if (pluginId.isEmpty() || pluginName.isEmpty() || mainClass.isEmpty()) {
                    Toast.makeText(this, "请填写必填项（插件ID、名称、主类）", Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (!mainClass.contains(".")) {
                    Toast.makeText(this, "主类名必须包含包名，如 com.example.MainPlugin", Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (!pluginId.matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")) {
                    Toast.makeText(this, "插件ID格式不正确，应为域名倒序格式", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            case 1:
                return true;
            case 2:
                return true;
            default:
                return true;
        }
    }

    protected void finishWizard() {
        String safePluginId = pluginId.replace("/", "_").replace("\\", "_").replace(":", "_");
        pluginWorkDir = new File(workFolder, safePluginId).getAbsolutePath();
        Toast.makeText(this, "插件创建完成！项目文件位置: " + pluginWorkDir, Toast.LENGTH_LONG).show();
        finish();
    }

    protected class ResourcesAdapter extends RecyclerView.Adapter<ResourcesAdapter.ViewHolder> {
        private List<String> resources;

        ResourcesAdapter(List<String> resources) {
            this.resources = resources;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String path = resources.get(position);
            File file = new File(path);
            holder.textView.setText(file.getName());
            holder.itemView.setOnLongClickListener(v -> {
                resources.remove(position);
                notifyItemRemoved(position);
                Toast.makeText(BasePluginWizardActivity.this, "已移除", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return resources.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}