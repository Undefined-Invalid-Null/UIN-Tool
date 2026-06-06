package com.UIN.Tool.ui.dev;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.MarkdownRenderer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DevDocActivity extends AppCompatActivity {

    private WebView webView;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_doc);

        toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.webview_doc);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());

        String docType = getIntent().getStringExtra("doc_type");
        if ("about".equals(docType)) {
            setTitle("关于");
            loadMarkdownFromAssets("docs/About.md");
        } else {
            setTitle("开发文档");
            loadMarkdownFromAssets("docs/README.md");
        }
    }

    private void loadMarkdownFromAssets(String fileName) {
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
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
            LogUtils.i("DevDocActivity", "加载文档: " + fileName);

        } catch (Exception e) {
            LogUtils.e("DevDocActivity", "加载文档失败: " + fileName);
            e.printStackTrace();
            String errorHtml = "<html><body style='padding:20px;text-align:center'><h1>错误</h1><p>无法加载文档: " + fileName + "</p></body></html>";
            webView.loadData(errorHtml, "text/html", "UTF-8");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}