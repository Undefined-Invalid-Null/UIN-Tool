// app/src/main/java/com/UIN/Tool/ui/screen/dev/DevDocScreen.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
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
import com.UIN.Tool.utils.AppToast

data class DevDocItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val id: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevDocScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val docs = listOf(
        DevDocItem(Icons.Default.Help, Str.get(R.string.help_2), Str.get(R.string.help_and_faq), "help"),
        DevDocItem(Icons.Default.DeveloperMode, Str.get(R.string.development_docs), Str.get(R.string.plugin_development_docs_and_api_refe), "dev"),
        DevDocItem(Icons.Default.Update, Str.get(R.string.changelog), Str.get(R.string.version_history_and_changelog), "changelog"),
        DevDocItem(Icons.Default.Info, Str.get(R.string.about), Str.get(R.string.about_the_app_and_version_info), "about"),
        DevDocItem(Icons.Default.People, Str.get(R.string.contributors), Str.get(R.string.list_of_contributors), "contributors")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Str.get(R.string.docs_center)) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(context, DevDocActivity::class.java)
                                intent.putExtra("doc_type", doc.id)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                AppToast.error(context, Str.get(R.string.failed_to_open_document_e_message, e.message))
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