// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocViewerScreen.kt
package com.UIN.Tool.ui.screen.docs

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.components.unified.UnifiedLoadingIndicator
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.MarkdownRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocViewerScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val docType = activity?.intent?.getStringExtra("doc_type") ?: "help"
    val title = activity?.intent?.getStringExtra("title") ?: Str.get(R.string.docs)

    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(docType) {
        loadDocument(docType) { loadedContent ->
            content = loadedContent
            isLoading = false
        }
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = title,
                onBack = { activity?.finish() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                UnifiedLoadingIndicator(message = Str.get(R.string.loading_document))
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = WebViewClient()
                            // 透明底：MarkdownRenderer 的 body/.container 已是 transparent，
                            // 透出 Scaffold 的 pageBackground()（渐变/深色随主题），
                            // 修复深色模式下 WebView 矩形块残留白底（原取色只读浅色 theme 区）。
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)

                            try {
                                val html = MarkdownRenderer.toHtml(content)
                                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                            } catch (e: Exception) {
                                AppLog.e("DocViewer", Str.get(R.string.failed_to_load_document_e_message, e.message), e)
                                loadDataWithBaseURL(null, "<html><body><p>${Str.get(R.string.failed_to_load_document_e_message, e.message)}</p></body></html>", "text/html", "UTF-8", null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// G-10：加载逻辑收口到 DocLoader（与 HelpScreen 共用同一语言判定与回退）
private suspend fun loadDocument(docType: String, onResult: (String) -> Unit) {
    val fileName = DocLoader.fileNameFor(docType)
    try {
        onResult(DocLoader.load(fileName))
    } catch (e: Exception) {
        AppLog.e("DocViewer", Str.get(R.string.failed_to_load_document_filename, fileName), e)
        onResult(DocLoader.errorText(e))
    }
}