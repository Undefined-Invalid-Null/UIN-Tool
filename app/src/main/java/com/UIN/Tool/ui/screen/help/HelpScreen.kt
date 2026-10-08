// app/src/main/java/com/UIN/Tool/ui/screen/help/HelpScreen.kt
package com.UIN.Tool.ui.screen.help

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
import com.UIN.Tool.ui.screen.docs.DocLoader
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.MarkdownRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // G-10：与 DocViewerScreen 共用 DocLoader（语言判定/回退只此一份），
        // A-01 的路径问题由 fileNameFor("help") → docs/<lang>/Help.md 解决
        try {
            content = DocLoader.load(DocLoader.fileNameFor("help"))
        } catch (e: Exception) {
            AppLog.e("HelpScreen", Str.get(R.string.failed_to_load_help_document), e)
            content = DocLoader.errorText(e)
        }
        isLoading = false
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = Str.get(R.string.help_2),
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
                UnifiedLoadingIndicator(message = Str.get(R.string.loading_help_document))
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = WebViewClient()

                            val html = MarkdownRenderer.toHtml(content)
                            loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}