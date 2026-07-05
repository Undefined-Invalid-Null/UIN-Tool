// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocViewerScreen.kt
package com.UIN.Tool.ui.screen.docs

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
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.MarkdownRenderer
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocViewerScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val docType = activity?.intent?.getStringExtra("doc_type") ?: "help"
    val title = activity?.intent?.getStringExtra("title") ?: "文档"

    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(docType) {
        loadDocument(docType) { loadedContent ->
            content = loadedContent
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { activity?.finish() }
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                UIComponents.FullScreenLoading("加载文档中...")
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

private suspend fun loadDocument(docType: String, onResult: (String) -> Unit) {
    val fileName = when (docType) {
        "help" -> "Help.md"
        "dev" -> "README.md"
        "changelog" -> "CHANGELOG.md"
        "about" -> "About.md"
        "contributors" -> "CONTRIBUTORS.md"
        else -> "README.md"
    }

    try {
        val appContext = com.UIN.Tool.UinApplication.getInstance()
        val inputStream = appContext.assets.open("docs/$fileName")
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        val content = reader.readText()
        reader.close()
        onResult(content)
    } catch (e: Exception) {
        Logger.e("DocViewer", "加载文档失败: $fileName", e)
        onResult("加载文档失败: ${e.message}")
    }
}