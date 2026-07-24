// ui/screen/dev/CodeEditorScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.UIN.Tool.ui.components.UIComponents
import com.UIN.Tool.utils.AppLog
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
// ui/screen/dev/CodeEditorScreen.kt - 顶部添加导入
import kotlinx.coroutines.delay

private const val TAG = "CodeEditorScreen"
private const val DEBUG_TAG = "EditorDebug"

// ==================== 主题列表 ====================
private val THEMES = listOf(
    "dark-plus", "dracula", "dracula-soft",
    "github-dark", "github-dark-dimmed", "github-light",
    "light-plus", "material-theme", "material-theme-darker",
    "material-theme-lighter", "material-theme-ocean", "material-theme-palenight",
    "min-dark", "min-light", "monokai", "nord",
    "one-dark-pro", "poimandres",
    "rose-pine", "rose-pine-dawn", "rose-pine-moon",
    "slack-dark", "slack-ochin",
    "solarized-dark", "solarized-light",
    "vitesse-black", "vitesse-dark", "vitesse-light"
)

private val DARK_THEMES = setOf(
    "dark-plus", "dracula", "dracula-soft",
    "github-dark", "github-dark-dimmed",
    "material-theme", "material-theme-darker",
    "material-theme-ocean", "material-theme-palenight",
    "min-dark", "monokai", "nord",
    "one-dark-pro", "poimandres",
    "rose-pine", "rose-pine-moon",
    "slack-dark", "slack-ochin",
    "solarized-dark",
    "vitesse-black", "vitesse-dark"
)

private val LIGHT_THEMES = setOf(
    "github-light", "light-plus", "material-theme-lighter",
    "min-light", "rose-pine-dawn", "solarized-light", "vitesse-light"
)

// ==================== 文件图标映射 ====================
private fun getFileIcon(fileName: String): ImageVector {
    return when {
        fileName.endsWith(".java") -> Icons.Default.Code
        fileName.endsWith(".kt") -> Icons.Default.Code
        fileName.endsWith(".kts") -> Icons.Default.Code
        fileName.endsWith(".xml") -> Icons.Default.Description
        fileName.endsWith(".html") || fileName.endsWith(".htm") -> Icons.Default.Language
        fileName.endsWith(".css") -> Icons.Default.Palette
        fileName.endsWith(".js") -> Icons.Default.Javascript
        fileName.endsWith(".json") -> Icons.Default.DataArray
        fileName.endsWith(".py") -> Icons.Default.Code
        fileName.endsWith(".md") -> Icons.Default.Description
        fileName.endsWith(".txt") -> Icons.Default.Description
        fileName.endsWith(".gradle") || fileName.endsWith(".pro") || fileName.endsWith(".properties") -> Icons.Default.Settings
        fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".toml") -> Icons.Default.DataArray
        fileName.endsWith(".sh") || fileName.endsWith(".bash") -> Icons.Default.Terminal
        fileName.endsWith(".sql") -> Icons.Default.Storage
        else -> Icons.Default.InsertDriveFile
    }
}

// ==================== 获取语言 Scope Name ====================
private fun getLanguageScopeName(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "")
    return when (extension.lowercase()) {
        "java" -> "source.java"
        "kt", "kts" -> "source.kotlin"
        "xml" -> "text.xml"
        "html", "htm" -> "text.html.basic"
        "css" -> "source.css"
        "js" -> "source.js"
        "json" -> "source.json"
        "py" -> "source.python"
        "md", "markdown" -> "text.html.markdown"
        "ts" -> "source.ts"
        "tsx" -> "source.tsx"
        "cpp", "c", "h", "hpp" -> "source.cpp"
        "cs" -> "source.cs"
        "go" -> "source.go"
        "rs" -> "source.rust"
        "php" -> "source.php"
        "rb" -> "source.ruby"
        "swift" -> "source.swift"
        "sh", "bash", "zsh" -> "source.shell"
        "sql" -> "source.sql"
        "ps1" -> "source.powershell"
        "yaml", "yml" -> "source.yaml"
        "toml" -> "source.toml"
        "dart" -> "source.dart"
        "lua" -> "source.lua"
        "r" -> "source.r"
        "scala" -> "source.scala"
        "pl" -> "source.perl"
        "hs" -> "source.haskell"
        "ex", "exs" -> "source.elixir"
        "erl" -> "source.erlang"
        "clj" -> "source.clojure"
        "groovy" -> "source.groovy"
        "dockerfile" -> "source.dockerfile"
        "makefile", "mk" -> "source.makefile"
        "ini" -> "source.ini"
        "properties" -> "source.properties"
        "vb" -> "source.vb"
        "fs" -> "source.fsharp"
        "bat" -> "source.batch"
        "diff" -> "source.diff"
        "docker" -> "source.dockerfile"
        "dotenv" -> "source.dotenv"
        "handlebars" -> "text.html.handlebars"
        "hlsl" -> "source.hlsl"
        "julia" -> "source.julia"
        "latex" -> "text.tex.latex"
        "less" -> "source.less"
        "log" -> "source.log"
        "make" -> "source.makefile"
        "objective-c" -> "source.objc"
        "pug" -> "source.pug"
        "razor" -> "source.razor"
        "restructuredtext" -> "source.rst"
        "scss" -> "source.scss"
        "shaderlab" -> "source.shaderlab"
        "shellscript" -> "source.shell"
        else -> "text.plain"
    }
}

// ==================== 颜色方案（回退方案） ====================
private fun getLightColorScheme(): EditorColorScheme {
    return object : EditorColorScheme() {
        override fun getColor(color: Int): Int {
            return when (color) {
                EditorColorScheme.WHOLE_BACKGROUND -> AndroidColor.WHITE
                EditorColorScheme.TEXT_NORMAL -> AndroidColor.BLACK
                EditorColorScheme.TEXT_SELECTED -> AndroidColor.BLUE
                EditorColorScheme.LINE_NUMBER -> AndroidColor.parseColor("#888888")
                EditorColorScheme.LINE_NUMBER_BACKGROUND -> AndroidColor.parseColor("#F0F0F0")
                EditorColorScheme.CURRENT_LINE -> AndroidColor.parseColor("#EEEEEE")
                EditorColorScheme.LINE_DIVIDER -> AndroidColor.parseColor("#E0E0E0")
                else -> AndroidColor.TRANSPARENT
            }
        }
    }
}

private fun getDarkColorScheme(): EditorColorScheme {
    return object : EditorColorScheme() {
        override fun getColor(color: Int): Int {
            return when (color) {
                EditorColorScheme.WHOLE_BACKGROUND -> AndroidColor.parseColor("#1E1E1E")
                EditorColorScheme.TEXT_NORMAL -> AndroidColor.parseColor("#D4D4D4")
                EditorColorScheme.TEXT_SELECTED -> AndroidColor.WHITE
                EditorColorScheme.LINE_NUMBER -> AndroidColor.parseColor("#858585")
                EditorColorScheme.LINE_NUMBER_BACKGROUND -> AndroidColor.parseColor("#252525")
                EditorColorScheme.CURRENT_LINE -> AndroidColor.parseColor("#2A2A2A")
                EditorColorScheme.LINE_DIVIDER -> AndroidColor.parseColor("#333333")
                else -> AndroidColor.TRANSPARENT
            }
        }
    }
}

// ==================== 设置编辑器语言 ====================
private fun setEditorLanguage(editor: CodeEditor?, fileName: String) {
    if (editor == null) return
    try {
        val scopeName = getLanguageScopeName(fileName)
        Log.d(DEBUG_TAG, "setEditorLanguage: scopeName=$scopeName, fileName=$fileName")
        val language = TextMateLanguage.create(scopeName, true)
        language.isAutoCompleteEnabled = true
        language.setCompleterKeywords(
            arrayOf(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new", "null",
                "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "void", "volatile", "while"
            )
        )
        editor.setEditorLanguage(language)
        val autoCompletion = editor.getComponent(EditorAutoCompletion::class.java)
        autoCompletion?.isEnabled = true
        Log.d(DEBUG_TAG, "setEditorLanguage: 成功设置语言 $scopeName")
    } catch (e: Exception) {
        Log.e(DEBUG_TAG, "setEditorLanguage: 失败", e)
        AppLog.w(TAG, "⚠️ 设置语言失败: ${e.message} for $fileName")
    }
}

// ==================== 应用主题 ====================
private fun applyTheme(editor: CodeEditor?, themeName: String) {
    if (editor == null) return
    try {
        Log.d(DEBUG_TAG, "applyTheme: 开始应用主题 $themeName")
        val themeRegistry = ThemeRegistry.getInstance()
        if (!themeRegistry.setTheme(themeName)) {
            val context = editor.context
            try {
                val path = "textmate/themes/$themeName.json"
                val inputStream = context.assets.open(path)
                val themeSource = IThemeSource.fromInputStream(inputStream, path, null)
                val themeModel = ThemeModel(themeSource, themeName)
                themeModel.isDark = DARK_THEMES.contains(themeName)
                themeRegistry.loadTheme(themeModel, true)
                Log.d(DEBUG_TAG, "applyTheme: 从 assets 加载主题 $themeName")
            } catch (e: Exception) {
                Log.w(DEBUG_TAG, "applyTheme: 主题文件不存在 $themeName", e)
                val isDark = DARK_THEMES.contains(themeName)
                editor.colorScheme = if (isDark) getDarkColorScheme() else getLightColorScheme()
                return
            }
        }
        val newScheme = TextMateColorScheme.create(themeRegistry)
        editor.colorScheme = newScheme
        editor.invalidate()
        Log.d(DEBUG_TAG, "applyTheme: 成功应用主题 $themeName")
    } catch (e: Exception) {
        Log.e(DEBUG_TAG, "applyTheme: 失败", e)
        val isDark = DARK_THEMES.contains(themeName)
        editor.colorScheme = if (isDark) getDarkColorScheme() else getLightColorScheme()
    }
}

// ==================== 主题选择对话框 ====================
@Composable
fun ThemeSelectionDialog(
    currentTheme: String,
    themes: List<String>,
    darkThemes: Set<String>,
    lightThemes: Set<String>,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "选择主题",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前主题",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF666666)
                    )
                    Surface(
                        color = Color(0xFFE8F0FE),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = currentTheme,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1A3A4A)
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Divider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "深色主题",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                themes.filter { it in darkThemes }.chunked(3).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            ThemeChip(
                                name = theme,
                                isSelected = currentTheme == theme,
                                onClick = { onThemeSelected(theme) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowThemes.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "浅色主题",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                themes.filter { it in lightThemes }.chunked(3).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            ThemeChip(
                                name = theme,
                                isSelected = currentTheme == theme,
                                onClick = { onThemeSelected(theme) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowThemes.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF0F0F0),
                        contentColor = Color(0xFF333333)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("关闭", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ==================== 主题标签 ====================
@Composable
fun ThemeChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        color = if (isSelected) Color(0xFF1A3A4A) else Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            color = if (isSelected) Color.White else Color(0xFF666666),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ==================== 文件树项 ====================
@Composable
fun FileTreeItem(
    fileName: String,
    icon: ImageVector,
    isSelected: Boolean,
    hasChanges: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hasChanges) {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                modifier = Modifier.size(6.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================
// ✅ 修复：主界面 - 高亮问题已修复
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    fileList: List<String>,
    fileContents: Map<String, String>,
    uiType: String,
    mainClass: String,
    pluginName: String,
    pluginId: String,
    onSave: (List<String>, Map<String, String>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)

    var currentFile by remember { mutableStateOf(fileList.firstOrNull() ?: "") }
    var currentTheme by remember {
        mutableStateOf(prefs.getString("selected_theme", "one-dark-pro") ?: "one-dark-pro")
    }
    var files by remember { mutableStateOf(fileList.toMutableList()) }
    var contents by remember { mutableStateOf(fileContents.toMutableMap()) }
    var hasChanges by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showAddFileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    var sidebarWidth by remember { mutableStateOf(180.dp) }
    var isSidebarVisible by remember { mutableStateOf(true) }
    val minSidebarWidth = 0.dp
    val maxSidebarWidth = 300.dp

    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var isEditorReady by remember { mutableStateOf(false) }

    val currentContent = contents[currentFile] ?: ""
    var editedContent by remember { mutableStateOf(currentContent) }

    // ============================================================
    // ✅ 修复：切换文件时，使用 LaunchedEffect 确保应用高亮
    // ============================================================
    LaunchedEffect(currentFile, currentTheme) {
        Log.d(DEBUG_TAG, "LaunchedEffect: currentFile=$currentFile, theme=$currentTheme")
        // 延迟执行，确保编辑器已创建
        delay(100)
        
        editorInstance?.let { editor ->
            val newContent = contents[currentFile] ?: ""
            Log.d(DEBUG_TAG, "更新编辑器: 文件=$currentFile, 内容长度=${newContent.length}")
            
            // 设置内容
            editor.setText(newContent)
            editedContent = newContent
            hasChanges = false
            
            // ✅ 关键修复：设置语言后立即应用主题
            setEditorLanguage(editor, currentFile)
            
            // ✅ 延迟一点再应用主题，确保语言已加载
            delay(50)
            applyTheme(editor, currentTheme)
            
            // ✅ 强制刷新
            editor.invalidate()
            isEditorReady = true
            Log.d(DEBUG_TAG, "✅ 编辑器更新完成: 语言和主题已应用")
        } ?: run {
            Log.d(DEBUG_TAG, "编辑器实例为 null，等待创建")
        }
    }

    fun saveCurrentFile() {
        val newContent = editorInstance?.text?.toString() ?: editedContent
        if (currentFile.isNotEmpty()) {
            contents[currentFile] = newContent
            editedContent = newContent
            hasChanges = false
            Log.d(DEBUG_TAG, "保存文件: $currentFile, 内容长度: ${newContent.length}")
        }
    }

    fun addFile(fileName: String, content: String = "") {
        if (fileName.isNotEmpty() && !files.contains(fileName)) {
            val newFiles = files.toMutableList()
            newFiles.add(fileName)
            files = newFiles

            val newContents = contents.toMutableMap()
            newContents[fileName] = content
            contents = newContents

            currentFile = fileName
            editedContent = content
            hasChanges = false
            Log.d(DEBUG_TAG, "添加文件: $fileName")
        }
    }

    fun deleteFile(fileName: String) {
        if (files.size <= 1) return
        val newFiles = files.toMutableList()
        newFiles.remove(fileName)
        files = newFiles
        contents = contents.toMutableMap().apply { remove(fileName) }

        if (currentFile == fileName) {
            currentFile = files.firstOrNull() ?: ""
            editedContent = contents[currentFile] ?: ""
        }
        Log.d(DEBUG_TAG, "删除文件: $fileName")
    }

    var isDragging by remember { mutableStateOf(false) }

    fun undo() { editorInstance?.undo() }
    fun redo() { editorInstance?.redo() }

    fun saveAll() {
        saveCurrentFile()
        onSave(files, contents)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* 不显示标题 */ },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            isSidebarVisible = !isSidebarVisible
                            if (isSidebarVisible) sidebarWidth = 180.dp else sidebarWidth = 0.dp
                        }
                    ) {
                        Icon(
                            if (isSidebarVisible) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = if (isSidebarVisible) "收起侧边栏" else "展开侧边栏"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { undo() }) {
                        Icon(Icons.Default.Undo, contentDescription = "撤销")
                    }
                    IconButton(onClick = { redo() }) {
                        Icon(Icons.Default.Redo, contentDescription = "重做")
                    }
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "选择主题")
                    }
                    Button(
                        onClick = { saveAll() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("完成", fontWeight = FontWeight.Medium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // 侧边栏
            if (isSidebarVisible) {
                Box(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "文件列表",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { showAddFileDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "新增文件",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Divider()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(files) { file ->
                                FileTreeItem(
                                    fileName = file,
                                    icon = getFileIcon(file),
                                    isSelected = file == currentFile,
                                    hasChanges = file == currentFile && hasChanges,
                                    onClick = {
                                        saveCurrentFile()
                                        currentFile = file
                                    },
                                    onDelete = { showDeleteConfirm = file }
                                )
                            }
                        }
                    }

                    // 拖动条
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(4.dp)
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    isDragging = true
                                    sidebarWidth = (sidebarWidth + delta.dp).coerceIn(minSidebarWidth, maxSidebarWidth)
                                    if (sidebarWidth < 30.dp) isSidebarVisible = false else isSidebarVisible = true
                                },
                                onDragStopped = {
                                    isDragging = false
                                    if (sidebarWidth < 30.dp) {
                                        sidebarWidth = 0.dp
                                        isSidebarVisible = false
                                    } else if (sidebarWidth < 80.dp) {
                                        sidebarWidth = 80.dp
                                        isSidebarVisible = true
                                    }
                                }
                            )
                            .background(
                                if (isDragging)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else
                                    Color.Transparent
                            )
                    )
                }
            }

            // 编辑器区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // 文件信息栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            getFileIcon(currentFile),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            currentFile.takeLast(50),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row {
                        if (hasChanges) {
                            Text(
                                "已修改",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            "行数: ${editedContent.lines().size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider()

                // ============================================================
                // ✅ 修复：AndroidView 使用 key 强制重建
                // ============================================================
                AndroidView(
                    factory = { ctx ->
                        Log.d(DEBUG_TAG, "创建 CodeEditor 实例 (factory)")
                        CodeEditor(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            
                            typefaceText = android.graphics.Typeface.MONOSPACE
                            isLineNumberEnabled = true
                            isHighlightCurrentLine = true
                            tabWidth = 4
                            isWordwrap = false
                            setTextSize(16f)
                            
                            // 设置初始内容
                            val initialContent = contents[currentFile] ?: ""
                            setText(initialContent)
                            Log.d(DEBUG_TAG, "编辑器初始文本长度: ${initialContent.length}")
                            
                            // ✅ 立即设置语言和主题
                            setEditorLanguage(this, currentFile)
                            applyTheme(this, currentTheme)
                            
                            // 内容变更监听
                            subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                                val actionName = when (event.action) {
                                    ContentChangeEvent.ACTION_INSERT -> "INSERT"
                                    ContentChangeEvent.ACTION_DELETE -> "DELETE"
                                    ContentChangeEvent.ACTION_SET_NEW_TEXT -> "SET_NEW_TEXT"
                                    else -> "UNKNOWN(${event.action})"
                                }
                                
                                if (event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                                    val fullText = event.getChangedText().toString()
                                    if (kotlin.math.abs(fullText.length - editedContent.length) > 1) {
                                        editedContent = fullText
                                        hasChanges = true
                                        if (currentFile.isNotEmpty()) {
                                            contents[currentFile] = fullText
                                        }
                                        Log.d(DEBUG_TAG, "全文替换，新长度: ${fullText.length}")
                                    }
                                }
                            }

                            subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                                // 选择变化日志（可选）
                            }
                            
                            editorInstance = this
                            isEditorReady = true
                            Log.d(DEBUG_TAG, "✅ 编辑器创建完成")
                        }
                    },
                    update = { editor ->
                        // ✅ 只在内容真正不同时更新，避免循环
                        val currentText = editor.text.toString()
                        val targetContent = contents[currentFile] ?: ""
                        if (currentText != targetContent && targetContent.isNotEmpty()) {
                            Log.d(DEBUG_TAG, "更新编辑器文本，长度变化: ${currentText.length} -> ${targetContent.length}")
                            editor.setText(targetContent)
                            editedContent = targetContent
                            hasChanges = false
                        }
                        editorInstance = editor
                        
                        // ✅ 每次 update 时重新应用语言和主题（确保高亮）
                        setEditorLanguage(editor, currentFile)
                        applyTheme(editor, currentTheme)
                        editor.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    // ============================================================
    // 对话框
    // ============================================================
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            themes = THEMES,
            darkThemes = DARK_THEMES,
            lightThemes = LIGHT_THEMES,
            onThemeSelected = { theme ->
                currentTheme = theme
                prefs.edit().putString("selected_theme", theme).apply()
                showThemeDialog = false
                // ✅ 立即应用主题
                applyTheme(editorInstance, theme)
                editorInstance?.invalidate()
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showAddFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        var newFileContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFileDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("添加新文件") },
            text = {
                Column {
                    UIComponents.TextInput(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = "文件名",
                        placeholder = if (uiType == "web") "web/new.html" else "src/com/example/NewClass.java",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UIComponents.TextInput(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = "文件内容（可选）",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                UIComponents.PrimaryButton(
                    text = "添加",
                    onClick = {
                        if (newFileName.isNotEmpty()) {
                            addFile(newFileName, newFileContent)
                            showAddFileDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                UIComponents.TextButton(
                    text = "取消",
                    onClick = { showAddFileDialog = false }
                )
            }
        )
    }

    if (showDeleteConfirm != null) {
        UIComponents.ConfirmDialog(
            title = "确认删除",
            message = "确定要删除 \"${showDeleteConfirm}\" 吗？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                deleteFile(showDeleteConfirm!!)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null },
            isDestructive = true
        )
    }
}