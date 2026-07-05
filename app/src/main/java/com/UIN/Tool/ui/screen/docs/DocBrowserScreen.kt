// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocBrowserScreen.kt
package com.UIN.Tool.ui.screen.docs

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.UIN.Tool.ui.components.UIComponents

data class DocItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val id: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocBrowserScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val docs = listOf(
        DocItem(Icons.Default.Info, "使用帮助", "使用帮助、常见问题解答", "help"),
        DocItem(Icons.Default.DeveloperMode, "开发文档", "插件开发文档、API 参考", "dev"),
        DocItem(Icons.Default.Update, "更新日志", "版本更新历史记录", "changelog"),
        DocItem(Icons.Default.Info, "关于", "关于应用、版本信息", "about"),
        DocItem(Icons.Default.People, "贡献者", "贡献者名单", "contributors")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文档中心") },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { activity?.finish() }
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(docs) { doc ->
                UIComponents.Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val intent = Intent(context, DocViewerActivity::class.java)
                            intent.putExtra("doc_type", doc.id)
                            intent.putExtra("title", doc.title)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "无法打开文档: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            doc.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            UIComponents.BodyText(doc.title)
                            UIComponents.CaptionText(doc.description)
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}