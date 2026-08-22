// app/src/main/java/com/UIN/Tool/plugin/PluginOpenDispatchActivity.kt
package com.UIN.Tool.plugin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Str
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * 系统「分享 / 用其他应用打开」中转页（完整页面）。
 *
 * 接收 ACTION_SEND / SEND_MULTIPLE / VIEW 传入的文本、URL、文件，整理为 openData JSON，
 * 列出所有声明 openWith 且匹配的插件，由用户选择将内容交给哪个插件（支持把分享的文件
 * 复制进所选插件的 .incoming/ 目录，供插件后端直接读取）。
 */
class PluginOpenDispatchActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PluginOpenDispatch"
        /** 单次接收的单个文件大小上限（100MB） */
        private const val MAX_INCOMING_FILE_SIZE = 100L * 1024 * 1024
        /** 单次接收的最大文件个数 */
        private const val MAX_INCOMING_FILES = 50
    }

    private val pluginManager by lazy { ServiceLocator.getPluginManager() }

    private var candidates by mutableStateOf<List<PluginInfo>>(emptyList())
    private var openData by mutableStateOf<JSONObject?>(null)
    private var kind by mutableStateOf("")
    private var kindLabel by mutableStateOf("")
    private var kindIcon by mutableStateOf<ImageVector>(Icons.Outlined.TextSnippet)
    private var summary by mutableStateOf("")
    private var searchQuery by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(intent)
        setContent {
            UINToolTheme {
                OpenReceiverScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialize(intent)
    }

    private fun initialize(intent: Intent) {
        try {
            val result = collectOpenData(intent) ?: run {
                Logger.w(TAG, Str.get(R.string.open_data_collect_failed))
                Toast.makeText(this, Str.get(R.string.no_open_data_received), Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            kind = result.first
            val data = result.second
            openData = data
            kindLabel = when (kind) {
                "file" -> Str.get(R.string.open_with_share_files)
                "url" -> Str.get(R.string.open_with_share_url)
                else -> Str.get(R.string.open_with_share_text)
            }
            kindIcon = when (kind) {
                "file" -> Icons.Outlined.InsertDriveFile
                "url" -> Icons.Outlined.Link
                else -> Icons.Outlined.TextSnippet
            }
            summary = buildSummary(kind, data)

            // 匹配声明 openWith 的插件（按 kind 与 mime 过滤）
            candidates = pluginManager.plugins.value.filter { plugin ->
                plugin.openWith?.let {
                    it.matches(data.optString("mime").ifEmpty { null }, kind)
                } == true
            }

            if (candidates.isEmpty()) {
                Logger.w(TAG, Str.get(R.string.no_plugin_matches_open_data))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.open_dispatch_failed), e)
            finish()
        }
    }

    // ============================================================
    // 内容解析
    // ============================================================

    /**
     * 将系统 intent 解析为 (kind, openDataJson)。
     * kind ∈ {"url", "text", "file"}
     */
    private fun collectOpenData(intent: Intent): Pair<String, JSONObject>? {
        val action = intent.action
        val mime: String? = intent.type
        val sharedText: String? = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }

        val streamUri: Uri? = if (Intent.ACTION_SEND.equals(action)) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.clipData?.let { if (it.itemCount > 0) it.getItemAt(0).uri else null }
        } else {
            null
        }

        val dataUri: Uri? = intent.data
        val uri: Uri? = streamUri ?: dataUri

        val payload = JSONObject().apply {
            put("when", System.currentTimeMillis())
            mime?.let { put("mime", it) }
        }

        // 1) URL/链接（发送或直接打开）
        if (uri != null && !isLocalFileUri(uri)) {
            val url = uri.toString()
            payload.put("kind", "url")
            payload.put("type", "url")
            payload.put("url", url)
            sharedText?.let { payload.put("text", it) }
            return "url" to payload
        }

        // 2) 文件（content:// 或 file://）
        if (uri != null) {
            payload.put("kind", "file")
            payload.put("type", "file")
            payload.put("uri", uri.toString())
            val name = queryDisplayName(uri) ?: uri.lastPathSegment
            payload.put("name", name ?: "")
            payload.put("mime", mime ?: guessMime(name))
            resolveFilePath(uri)?.let { payload.put("filePath", it) }
            intent.clipData?.let { if (it.itemCount > 1) payload.put("streamCount", it.itemCount) }
            return "file" to payload
        }

        // 3) 多文件（SEND_MULTIPLE，多个 clipData item）
        val multi = intent.clipData
        if (Intent.ACTION_SEND_MULTIPLE.equals(action) && multi != null && multi.itemCount > 1) {
            val filesArr = JSONArray()
            var firstMime: String? = null
            for (i in 0 until multi.itemCount) {
                val u = multi.getItemAt(i).uri ?: continue
                val nm = queryDisplayName(u) ?: u.lastPathSegment ?: "file"
                val fm = mime ?: guessMime(nm)
                if (firstMime == null) firstMime = fm
                filesArr.put(
                    JSONObject().apply {
                        put("uri", u.toString())
                        put("name", nm)
                        put("mime", fm)
                    }
                )
            }
            if (filesArr.length() > 0) {
                payload.put("kind", "file")
                payload.put("type", "file")
                payload.put("mime", firstMime ?: "")
                payload.put("files", filesArr)
                payload.put("streamCount", filesArr.length())
                return "file" to payload
            }
        }

        // 4) 纯文本
        if (sharedText != null) {
            val looksUrl = isUrlLike(sharedText)
            val k = if (looksUrl) "url" else "text"
            payload.put("kind", k)
            payload.put("type", k)
            if (looksUrl) payload.put("url", sharedText.trim()) else payload.put("text", sharedText)
            return k to payload
        }

        return null
    }

    private fun isLocalFileUri(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file", "content" -> true
            else -> false
        }
    }

    private fun isUrlLike(text: String): Boolean {
        val t = text.trim()
        return Patterns.WEB_URL.matcher(t).matches() ||
            t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true) ||
            t.startsWith("magnet:", ignoreCase = true)
    }

    private fun guessMime(fileName: String?): String {
        if (fileName.isNullOrEmpty()) return ""
        val dot = fileName.lastIndexOf('.')
        if (dot >= 0 && dot < fileName.length - 1) {
            val ext = fileName.substring(dot + 1).lowercase(Locale.ROOT)
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return ""
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveFilePath(uri: Uri): String? {
        return if (uri.scheme?.lowercase(Locale.ROOT) == "file") uri.path else null
    }

    private fun buildSummary(kind: String, data: JSONObject): String {
        return when (kind) {
            "file" -> {
                data.optJSONArray("files")?.let { arr ->
                    if (arr.length() > 1) {
                        Str.get(R.string.open_with_share_files_colon, arr.length())
                    } else null
                } ?: data.optString("name").ifEmpty {
                    data.optString("uri", Str.get(R.string.unknown))
                }
            }
            "url" -> data.optString("url").ifEmpty { Str.get(R.string.unknown) }
            else -> data.optString("text").ifEmpty { Str.get(R.string.unknown) }
        }
    }

    // ============================================================
    // 内容复制进插件 + 打开插件
    // ============================================================

    private fun openPlugin(plugin: PluginInfo, data: JSONObject) {
        val finalData = if (kind == "file") copyIncomingFiles(plugin, data) else data
        val intent = Intent(this, PluginHostActivity::class.java).apply {
            putExtra(PluginHostActivity.EXTRA_PLUGIN_ID, plugin.pluginId)
            putExtra(PluginHostActivity.EXTRA_OPEN_DATA, finalData.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Logger.i(TAG, Str.get(R.string.opening_plugin_for_open_data, plugin.pluginId))
        startActivity(intent)
        finish()
    }

    /**
     * 把分享的文件（content://）复制进所选插件的 <.incoming/> 目录，
     * 使插件后端（proot 容器中挂载的 /plugins/<id>/.incoming）能直接读取。
     * 单文件时更新 openData 顶层字段；多文件时更新 files 数组内每一项。
     */
    private fun copyIncomingFiles(plugin: PluginInfo, data: JSONObject): JSONObject {
        val dir = File(File(Constants.PLUGIN_DIR, plugin.pluginId), ".incoming")
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                Logger.w(TAG, Str.get(R.string.failed_to_create_incoming_dir))
                return data
            }
            var copied = 0

            fun copyOne(uriStr: String, name: String, target: JSONObject) {
                try {
                    if (copied >= MAX_INCOMING_FILES) {
                        Logger.w(TAG, Str.get(R.string.open_with_copy_limit_reached))
                        return
                    }
                    val uri = Uri.parse(uriStr)
                    val safeName = sanitizeFileName(name.ifEmpty { "file" })
                    if (safeName.isEmpty()) return
                    val dest = File(dir, "${System.currentTimeMillis()}_$safeName")
                    val input = contentResolver.openInputStream(uri) ?: return
                    input.use { ins ->
                        dest.outputStream().use { out -> ins.copyTo(out) }
                    }
                    if (dest.length() > MAX_INCOMING_FILE_SIZE) {
                        dest.delete()
                        Logger.w(TAG, Str.get(R.string.open_with_copy_file_too_large))
                        return
                    }
                    target.put("filePath", dest.absolutePath)
                    target.put("incomingName", dest.name)
                    target.put("containerPath", "/plugins/${plugin.pluginId}/.incoming/${dest.name}")
                    copied++
                } catch (e: Exception) {
                    Logger.e(TAG, Str.get(R.string.open_with_copy_failed, e.message ?: ""), e)
                }
            }

            data.optJSONArray("files")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    copyOne(item.optString("uri"), item.optString("name"), item)
                }
            }
            if (data.optJSONArray("files") == null && data.has("uri")) {
                copyOne(data.optString("uri"), data.optString("name"), data)
            }
            if (copied == 0) {
                Logger.w(TAG, Str.get(R.string.open_with_copy_failed, Str.get(R.string.unknown)))
            }
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.open_with_copy_failed, e.message ?: ""), e)
        }
        return data
    }

    private fun sanitizeFileName(name: String): String {
        var s = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim()
        if (s.length > 120) s = s.substring(0, 120)
        return s
    }

    // ============================================================
    // 中转页面（完整 Compose 页面）
    // ============================================================

    @Composable
    private fun OpenReceiverScreen() {
        Scaffold(
            containerColor = AppColors.pageBackground(),
            topBar = {
                UIComponents.ManageTopAppBar(
                    titleText = Str.get(R.string.open_with_choose_title),
                    onBack = { finish() }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                SummaryCard(
                    icon = kindIcon,
                    label = kindLabel,
                    summary = summary
                )
                if (candidates.isEmpty()) {
                    EmptyState(modifier = Modifier.weight(1f))
                } else {
                    val query = searchQuery.trim()
                    val filtered = if (query.isEmpty()) candidates else candidates.filter { plugin ->
                        parseLabel(plugin).contains(query, ignoreCase = true) ||
                            plugin.pluginId.contains(query, ignoreCase = true) ||
                            plugin.description.contains(query, ignoreCase = true)
                    }
                    CandidateList(
                        list = filtered,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        onPick = { plugin ->
                            openData?.let { openPlugin(plugin, it) }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun SummaryCard(icon: ImageVector, label: String, summary: String) {
        UIComponents.Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun CandidateList(
        list: List<PluginInfo>,
        query: String,
        onQueryChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        onPick: (PluginInfo) -> Unit
    ) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(Str.get(R.string.search_plugins)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
            item {
                Text(
                    text = Str.get(R.string.open_with_receiver_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        text = Str.get(R.string.no_plugin_for_open_with),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
            items(list) { plugin ->
                PluginRow(plugin = plugin, onClick = { onPick(plugin) })
            }
        }
    }

    private fun parseLabel(plugin: PluginInfo): String =
        plugin.openWith?.label?.takeIf { it.isNotBlank() } ?: plugin.name

    @Composable
    private fun PluginRow(plugin: PluginInfo, onClick: () -> Unit) {
        val label = parseLabel(plugin)
        UIComponents.Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    plugin.description.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun EmptyState(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = Str.get(R.string.no_plugin_for_open_with),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { finish() }) {
                Text(Str.get(R.string.open_with_done))
            }
        }
    }
}