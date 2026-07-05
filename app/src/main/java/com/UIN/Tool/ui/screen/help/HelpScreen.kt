// app/src/main/java/com/UIN/Tool/ui/screen/help/HelpScreen.kt
package com.UIN.Tool.ui.screen.help

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
fun HelpScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val appContext = com.UIN.Tool.UinApplication.getInstance()
            val inputStream = appContext.assets.open("docs/Help.md")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            content = reader.readText()
            reader.close()
        } catch (e: Exception) {
            Logger.e("HelpScreen", "加载帮助文档失败", e)
            content = "加载帮助文档失败: ${e.message}"
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用帮助") },
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
                UIComponents.FullScreenLoading("加载帮助文档...")
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