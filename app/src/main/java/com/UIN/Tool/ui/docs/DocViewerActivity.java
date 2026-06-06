// app/src/main/java/com/UIN/Tool/ui/docs/DocViewerActivity.java
package com.UIN.Tool.ui.docs;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityDocViewerBinding;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.MarkdownRenderer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DocViewerActivity extends BaseActivity {

    private ActivityDocViewerBinding binding;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_doc_viewer;
    }

    @Override
    protected void initViews() {
        binding = ActivityDocViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String title = getIntent().getStringExtra("title");
        setupToolbar(binding.toolbar, title != null ? title : getString(R.string.documents), true);
    }

    @Override
    protected void initData() {
        binding.webviewDoc.getSettings().setJavaScriptEnabled(true);
        binding.webviewDoc.getSettings().setSupportZoom(true);
        binding.webviewDoc.getSettings().setBuiltInZoomControls(true);
        binding.webviewDoc.getSettings().setDisplayZoomControls(false);
        binding.webviewDoc.setWebViewClient(new WebViewClient());

        String docType = getIntent().getStringExtra("doc_type");
        loadDocument(docType);
    }

    private void loadDocument(String docType) {
        String fileName;
        String subDir = "docs/";

        if ("about".equals(docType)) {
            fileName = subDir + "About.md";
        } else if ("help".equals(docType)) {
            fileName = subDir + "Help.md";
        } else if ("dev".equals(docType)) {
            fileName = subDir + "README.md";
        } else if ("contributors".equals(docType)) {
            fileName = subDir + "CONTRIBUTORS.md";
        } else {
            fileName = subDir + "README.md";
        }

        try {
            InputStream is = getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            is.close();

            String markdown = sb.toString();
            String html = MarkdownRenderer.toHtml(markdown);
            binding.webviewDoc.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);

        } catch (Exception e) {
            LogUtils.e("DocViewer", "加载文档失败", e);
            String errorHtml = "<html><body style='padding:20px;text-align:center'><h1>" +
                    getString(R.string.error) + "</h1><p>" +
                    getString(R.string.doc_loading_failed, fileName) + "</p></body></html>";
            binding.webviewDoc.loadData(errorHtml, "text/html", "UTF-8");
        }
    }
}