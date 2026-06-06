// app/src/main/java/com/UIN/Tool/ui/help/HelpActivity.java
package com.UIN.Tool.ui.help;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityHelpBinding;
import com.UIN.Tool.ui.common.BaseActivity;
import com.UIN.Tool.utils.LogUtils;
import com.UIN.Tool.utils.MarkdownRenderer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HelpActivity extends BaseActivity {

    private ActivityHelpBinding binding;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_help;
    }

    @Override
    protected void initViews() {
        binding = ActivityHelpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.title_help), true);
    }

    @Override
    protected void initData() {
        binding.webviewHelp.getSettings().setJavaScriptEnabled(true);
        binding.webviewHelp.getSettings().setSupportZoom(true);
        binding.webviewHelp.getSettings().setBuiltInZoomControls(true);
        binding.webviewHelp.getSettings().setDisplayZoomControls(false);
        binding.webviewHelp.setWebViewClient(new WebViewClient());

        loadHelpDocument();
    }

    private void loadHelpDocument() {
        try {
            InputStream is = getAssets().open("docs/Help.md");
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
            binding.webviewHelp.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);

        } catch (Exception e) {
            LogUtils.e("HelpActivity", "加载帮助文档失败", e);
            String errorHtml = "<html><body style='padding:20px;text-align:center'><h1>" +
                    getString(R.string.error) + "</h1><p>" +
                    getString(R.string.doc_loading_failed, "Help.md") + "</p></body></html>";
            binding.webviewHelp.loadData(errorHtml, "text/html", "UTF-8");
        }
    }
}