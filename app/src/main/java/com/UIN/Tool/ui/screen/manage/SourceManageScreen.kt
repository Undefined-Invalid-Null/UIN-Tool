// app/src/main/java/com/UIN/Tool/ui/screen/manage/SourceManageScreen.kt
package com.UIN.Tool.ui.screen.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.domain.model.SourceInfo
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManageScreen() {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }
    
    var sources by remember { mutableStateOf<List<SourceInfo>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<SourceInfo?>(null) }
    
    fun loadSources() {
        val json = preferenceManager.getSourcesJson()
        if (json.isNotEmpty()) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<SourceInfo>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(SourceInfo(
                        sourceId = obj.optString("sourceId", ""),
                        name = obj.optString("name", ""),
                        owner = obj.optString("owner", ""),
                        repo = obj.optString("repo", ""),
                        branch = obj.optString("branch", "dist"),
                        description = obj.optString("description", ""),
                        trustLevel = obj.optString("trustLevel", "community"),
                        addedAt = obj.optString("addedAt", ""),
                        enabled = obj.optBoolean("enabled", true)
                    ))
                }
                sources = list
            } catch (e: Exception) {
                sources = emptyList()
            }
        }
    }
    
    fun saveSources() {
        val array = JSONArray()
        sources.forEach { source ->
            val obj = JSONObject().apply {
                put("sourceId", source.sourceId)
                put("name", source.name)
                put("owner", source.owner)
                put("repo", source.repo)
                put("branch", source.branch)
                put("description", source.description)
                put("trustLevel", source.trustLevel)
                put("addedAt", source.addedAt)
                put("enabled", source.enabled)
            }
            array.put(obj)
        }
        preferenceManager.setSourcesJson(array.toString())
    }
    
    LaunchedEffect(Unit) {
        loadSources()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnifiedTitleText(Str.get(R.string.source_manage))
            
            UnifiedIconButton(
                icon = Icons.Default.Add,
                onClick = { showAddDialog = true },
                contentDescription = Str.get(R.string.add_source)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 源列表
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                UnifiedBodyText(Str.get(R.string.no_source_hint))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(sources, key = { it.sourceId }) { source ->
                    SourceItem(
                        source = source,
                        onToggleEnabled = { enabled ->
                            sources = sources.map {
                                if (it.sourceId == source.sourceId) it.copy(enabled = enabled)
                                else it
                            }
                            saveSources()
                        },
                        onDelete = { showDeleteConfirm = source }
                    )
                }
            }
        }
    }
    
    // 添加源对话框
    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { source ->
                if (sources.any { it.sourceId == source.sourceId }) {
                    AppToast.warning(context, Str.get(R.string.source_id_exists))
                    return@AddSourceDialog
                }
                sources = sources + source
                saveSources()
                showAddDialog = false
                AppToast.success(context, Str.get(R.string.add_source_success))
            }
        )
    }
    
    // 删除确认对话框
    showDeleteConfirm?.let { source ->
        UnifiedConfirmDialog(
            title = Str.get(R.string.delete_source),
            message = Str.get(R.string.confirm_delete_source, source.getDisplayName()),
            confirmText = Str.get(R.string.delete),
            dismissText = Str.get(R.string.cancel),
            isDestructive = true,
            onConfirm = {
                sources = sources.filter { it.sourceId != source.sourceId }
                saveSources()
                showDeleteConfirm = null
                AppToast.success(context, Str.get(R.string.source_deleted))
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }
}

@Composable
fun SourceItem(
    source: SourceInfo,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        fontSize = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    val trustColor = when (source.trustLevel) {
                        "official" -> MaterialTheme.colorScheme.primary
                        "verified" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Surface(
                        color = trustColor.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = source.getTrustLabel(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = trustColor
                        )
                    }
                }
                
                if (source.description.isNotEmpty()) {
                    UnifiedCaptionText(
                        text = source.description,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                UnifiedCaptionText(
                    text = "${source.owner}/${source.repo}",
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            UnifiedSwitch(
                checked = source.enabled,
                onCheckedChange = onToggleEnabled
            )
            
            UnifiedIconButton(
                icon = Icons.Default.Delete,
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
                contentDescription = Str.get(R.string.delete)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (SourceInfo) -> Unit
) {
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("dist") }
    var description by remember { mutableStateOf("") }
    
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = Str.get(R.string.add_source),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UnifiedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    placeholder = Str.get(R.string.github_username_hint)
                )
                UnifiedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    placeholder = Str.get(R.string.repo_name_hint)
                )
                UnifiedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = Str.get(R.string.display_name_optional_hint)
                )
                UnifiedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    placeholder = Str.get(R.string.branch_hint)
                )
                UnifiedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = Str.get(R.string.description_optional_hint),
                    singleLine = false
                )
            }
        },
        confirmButton = {
            UnifiedButton(
                text = Str.get(R.string.add_source),
                onClick = {
                    if (owner.isBlank() || repo.isBlank()) return@UnifiedButton
                    val sourceId = "${owner.lowercase()}.${repo.lowercase()}"
                    val sourceName = name.ifEmpty { repo }
                    onAdd(SourceInfo(
                        sourceId = sourceId,
                        name = sourceName,
                        owner = owner,
                        repo = repo,
                        branch = branch.ifEmpty { "dist" },
                        description = description,
                        trustLevel = "community",
                        addedAt = java.text.SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ssZ",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date()),
                        enabled = true
                    ))
                },
                enabled = owner.isNotBlank() && repo.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        },
        dismissButton = {
            UnifiedButton(
                text = Str.get(R.string.cancel),
                onClick = onDismiss,
                variant = ButtonVariant.Outlined,
                modifier = Modifier.weight(1f)
            )
        }
    )
}
