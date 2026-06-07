package com.UIN.Tool.ui.dev;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.FragmentDevBinding;
import com.UIN.Tool.ui.common.BaseFragment;
import com.UIN.Tool.ui.common.DialogHelper;
import com.UIN.Tool.ui.docs.DocViewerActivity;
import com.UIN.Tool.utils.PreferencesUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DevFragment extends BaseFragment {

    private FragmentDevBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDevBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    protected void initViews(View view) {
        // binding already initialized
    }

    @Override
    protected void initData() {
        // 初始化数据
    }

    @Override
    protected void setupListeners() {
        if (binding == null) return;
        
        binding.btnCreatePlugin.setOnClickListener(v -> showPluginTypeDialog());
        binding.btnExportTemplate.setOnClickListener(v -> exportTemplate());
        binding.btnViewDocs.setOnClickListener(v -> showDocsDialog());
    }

    private void showPluginTypeDialog() {
        String[] types = {
            getString(R.string.plugin_ui_type_native),
            getString(R.string.plugin_ui_type_web)
        };

        DialogHelper.listDialog(requireContext(), getString(R.string.dialog_title_ui_type), types,
                (dialog, which) -> {
                    Intent intent;
                    if (which == 0) {
                        intent = new Intent(getContext(), NativePluginWizardActivity.class);
                    } else {
                        intent = new Intent(getContext(), WebPluginWizardActivity.class);
                    }
                    startActivity(intent);
                }).show();
    }

    private void showDocsDialog() {
        String[] docs = {
            getString(R.string.doc_type_dev),
            getString(R.string.doc_type_help),
            getString(R.string.doc_type_changelog),
            getString(R.string.doc_type_about),
            getString(R.string.doc_type_contributors)
        };

        DialogHelper.listDialog(requireContext(), getString(R.string.btn_view_docs), docs,
                (dialog, which) -> {
                    Intent intent = new Intent(getContext(), DocViewerActivity.class);
                    switch (which) {
                        case 0:
                            intent.putExtra("doc_type", "dev");
                            intent.putExtra("title", getString(R.string.doc_type_dev));
                            break;
                        case 1:
                            intent.putExtra("doc_type", "help");
                            intent.putExtra("title", getString(R.string.doc_type_help));
                            break;
                        case 2:
                            intent.putExtra("doc_type", "changelog");
                            intent.putExtra("title", getString(R.string.doc_type_changelog));
                            break;
                        case 3:
                            intent.putExtra("doc_type", "about");
                            intent.putExtra("title", getString(R.string.doc_type_about));
                            break;
                        case 4:
                            intent.putExtra("doc_type", "contributors");
                            intent.putExtra("title", getString(R.string.doc_type_contributors));
                            break;
                    }
                    startActivity(intent);
                }).show();
    }

    private void exportTemplate() {
        try {
            String workFolder = PreferencesUtils.getWorkFolder(requireContext());
            File workDir = new File(workFolder);
            if (!workDir.exists()) workDir.mkdirs();

            // 导出原生插件模板（直接复制 TPK 文件）
            exportAssetToFile("templates/native_template.tpk", new File(workDir, "native_plugin_template.tpk"));
            
            // 导出 Web 插件模板（打包为 TPK）
            createWebTemplateTpk(workDir);
            
            // 导出开发文档
            File docsDir = new File(workDir, "docs");
            docsDir.mkdirs();
            exportAssetToFile("docs/README.md", new File(docsDir, "README.md"));
            exportAssetToFile("docs/Help.md", new File(docsDir, "Help.md"));
            exportAssetToFile("docs/About.md", new File(docsDir, "About.md"));
            exportAssetToFile("docs/CONTRIBUTORS.md", new File(docsDir, "CONTRIBUTORS.md"));
            exportAssetToFile("docs/CHANGELOG.md", new File(docsDir, "CHANGELOG.md"));

            showLongToast(getString(R.string.template_exported, workFolder));

        } catch (Exception e) {
            e.printStackTrace();
            showToast(getString(R.string.export_failed, e.getMessage()));
        }
    }

    private void createWebTemplateTpk(File workDir) throws Exception {
        File tpkFile = new File(workDir, "web_plugin_template.tpk");
        
        // 创建临时目录来构建 TPK
        File tempDir = new File(requireContext().getCacheDir(), "web_template_build");
        if (tempDir.exists()) {
            deleteDirectory(tempDir);
        }
        tempDir.mkdirs();
        
        // 创建 plugin.json
        String pluginJson = "{\n" +
                "    \"pluginId\": \"com.example.webplugin\",\n" +
                "    \"version\": 1,\n" +
                "    \"versionName\": \"1.0.0\",\n" +
                "    \"minHostVersion\": 1,\n" +
                "    \"name\": \"Web插件模板\",\n" +
                "    \"author\": \"UIN Team\",\n" +
                "    \"description\": \"这是一个Web插件模板，使用HTML/CSS/JS开发\",\n" +
                "    \"icon\": \"icon.png\",\n" +
                "    \"mainClass\": \"\",\n" +
                "    \"apiLevel\": 21,\n" +
                "    \"uiType\": \"web\",\n" +
                "    \"entry\": \"web/index.html\"\n" +
                "}\n";
        
        // 写入 plugin.json
        writeStringToFile(new File(tempDir, "plugin.json"), pluginJson);
        
        // 创建图标占位文件
        File iconFile = new File(tempDir, "icon.png");
        iconFile.createNewFile();
        
        // 创建 web 目录
        File webDir = new File(tempDir, "web");
        webDir.mkdirs();
        
        // 复制模板文件到 web 目录
        String indexHtml = loadAssetFile("plugin_templates/web/index.html");
        String styleCss = loadAssetFile("plugin_templates/web/style.css");
        String scriptJs = loadAssetFile("plugin_templates/web/script.js");
        String blankIndexHtml = loadAssetFile("plugin_templates/web/blank_index.html");
        
        // 替换变量（使用默认值）
        indexHtml = indexHtml.replace("{{PLUGIN_NAME}}", "Web插件模板")
                             .replace("{{PLUGIN_DESCRIPTION}}", "这是一个Web插件模板")
                             .replace("{{PLUGIN_ID}}", "com.example.webplugin");
        styleCss = styleCss.replace("{{PLUGIN_NAME}}", "Web插件模板");
        scriptJs = scriptJs.replace("{{PLUGIN_NAME}}", "Web插件模板")
                           .replace("{{PLUGIN_ID}}", "com.example.webplugin");
        blankIndexHtml = blankIndexHtml.replace("{{PLUGIN_NAME}}", "Web插件模板")
                                       .replace("{{PLUGIN_DESCRIPTION}}", "这是一个Web插件模板")
                                       .replace("{{PLUGIN_ID}}", "com.example.webplugin");
        
        writeStringToFile(new File(webDir, "index.html"), indexHtml);
        writeStringToFile(new File(webDir, "style.css"), styleCss);
        writeStringToFile(new File(webDir, "script.js"), scriptJs);
        writeStringToFile(new File(webDir, "blank_index.html"), blankIndexHtml);
        
        // 打包为 ZIP（TPK）
        zipDirectory(tempDir, tpkFile);
        
        // 清理临时目录
        deleteDirectory(tempDir);
    }
    
    private void writeStringToFile(File file, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes("UTF-8"));
        fos.close();
    }
    
    private void zipDirectory(File sourceDir, File zipFile) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        zipFile(sourceDir, sourceDir, zos);
        zos.close();
    }
    
    private void zipFile(File rootDir, File file, ZipOutputStream zos) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    zipFile(rootDir, child, zos);
                }
            }
        } else {
            String entryName = file.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1);
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            fis.close();
            zos.closeEntry();
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

    private String loadAssetFile(String path) throws Exception {
        InputStream is = requireContext().getAssets().open(path);
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        is.close();
        return sb.toString();
    }

    private void exportAssetToFile(String assetPath, File destFile) throws Exception {
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();

        InputStream is = requireContext().getAssets().open(assetPath);
        FileOutputStream fos = new FileOutputStream(destFile);
        byte[] buffer = new byte[8192];
        int length;
        while ((length = is.read(buffer)) > 0) fos.write(buffer, 0, length);
        fos.close();
        is.close();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}