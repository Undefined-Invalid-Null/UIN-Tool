// ui/screen/dev/CodeEditorScreen.kt
package com.UIN.Tool.ui.screen.dev

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
        AppLog.d(DEBUG_TAG, "setEditorLanguage: scopeName=$scopeName, fileName=$fileName")
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
        AppLog.d(DEBUG_TAG, Str.get(R.string.seteditorlanguage_language_set_to_sc, scopeName))
    } catch (e: Exception) {
        AppLog.e(DEBUG_TAG, Str.get(R.string.seteditorlanguage_failed), e)
        AppLog.w(TAG, Str.get(R.string.failed_to_set_language_e_message_for, e.message, fileName))
    }
}

// ==================== 应用主题 ====================
private fun applyTheme(editor: CodeEditor?, themeName: String) {
    if (editor == null) return
    try {
        AppLog.d(DEBUG_TAG, Str.get(R.string.applytheme_applying_theme_themename, themeName))
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
                AppLog.d(DEBUG_TAG, Str.get(R.string.applytheme_loading_theme_themename_f, themeName))
            } catch (e: Exception) {
                AppLog.e(DEBUG_TAG, Str.get(R.string.applytheme_theme_file_not_found_them, themeName), e)
                val isDark = DARK_THEMES.contains(themeName)
                editor.colorScheme = if (isDark) getDarkColorScheme() else getLightColorScheme()
                return
            }
        }
        val newScheme = TextMateColorScheme.create(themeRegistry)
        editor.colorScheme = newScheme
        editor.invalidate()
        AppLog.d(DEBUG_TAG, Str.get(R.string.applytheme_theme_applied_themename, themeName))
    } catch (e: Exception) {
        AppLog.e(DEBUG_TAG, Str.get(R.string.applytheme_failed), e)
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
                    text = Str.get(R.string.select_theme),
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
                        text = Str.get(R.string.current_theme),
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
                    text = Str.get(R.string.dark_theme),
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
                    text = Str.get(R.string.light_theme),
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
                    Text(Str.get(R.string.close), fontWeight = FontWeight.Medium)
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
                contentDescription = Str.get(R.string.delete),
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
        AppLog.d(DEBUG_TAG, "LaunchedEffect: currentFile=$currentFile, theme=$currentTheme")
        // 延迟执行，确保编辑器已创建
        delay(100)
        
        editorInstance?.let { editor ->
            val newContent = contents[currentFile] ?: ""
            AppLog.d(DEBUG_TAG, Str.get(R.string.updating_editor_file_currentfile_con, currentFile, newContent.length))
            
            // ✅ 先设置语言和主题
            setEditorLanguage(editor, currentFile)
            applyTheme(editor, currentTheme)
            
            // ✅ 最后再 setText：触发一次完整的重新分析，且不会被后续操作中断
            //    （setText 内部会调用 AnalyzeManager.reset() 重新全量高亮）
            editor.setText(newContent)
            editedContent = newContent
            hasChanges = false
            
            // ✅ 强制刷新
            editor.invalidate()
            isEditorReady = true
            AppLog.d(DEBUG_TAG, Str.get(R.string.editor_updated_language_and_theme_ap))
        } ?: run {
            AppLog.d(DEBUG_TAG, Str.get(R.string.editor_instance_is_null_waiting_to_b))
        }
    }

    fun saveCurrentFile() {
        val newContent = editorInstance?.text?.toString() ?: editedContent
        if (currentFile.isNotEmpty()) {
            contents[currentFile] = newContent
            editedContent = newContent
            hasChanges = false
            AppLog.d(DEBUG_TAG, Str.get(R.string.saving_file_currentfile_content_leng, currentFile, newContent.length))
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
            AppLog.d(DEBUG_TAG, Str.get(R.string.adding_file_filename, fileName))
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
        AppLog.d(DEBUG_TAG, Str.get(R.string.deleting_file_filename, fileName))
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
                            contentDescription = if (isSidebarVisible) Str.get(R.string.collapse_sidebar) else Str.get(R.string.expand_sidebar)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { undo() }) {
                        Icon(Icons.Default.Undo, contentDescription = Str.get(R.string.undo))
                    }
                    IconButton(onClick = { redo() }) {
                        Icon(Icons.Default.Redo, contentDescription = Str.get(R.string.redo))
                    }
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(Icons.Default.Palette, contentDescription = Str.get(R.string.select_theme))
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
                        Text(Str.get(R.string.finish), fontWeight = FontWeight.Medium)
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
                                Str.get(R.string.file_list),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { showAddFileDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = Str.get(R.string.new_file),
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
                                Str.get(R.string.modified),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            Str.get(R.string.lines_editedcontent_lines_size, editedContent.lines().size),
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
                        AppLog.d(DEBUG_TAG, Str.get(R.string.creating_codeeditor_instance_factory))
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
                            
                            // ✅ 先应用主题
                            applyTheme(this, currentTheme)
                            
                            // ✅ 再设置语言，最后 setText 触发全量高亮分析
                            setEditorLanguage(this, currentFile)
                            
                            // 设置初始内容
                            val initialContent = contents[currentFile] ?: ""
                            setText(initialContent)
                            AppLog.d(DEBUG_TAG, Str.get(R.string.initial_editor_text_length_initialco, initialContent.length))
                            
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
                                        AppLog.d(DEBUG_TAG, Str.get(R.string.full_replace_new_length_fulltext_len, fullText.length))
                                    }
                                }
                            }

                            subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                                // 选择变化日志（可选）
                            }
                            
                            editorInstance = this
                            isEditorReady = true
                            AppLog.d(DEBUG_TAG, Str.get(R.string.editor_created))
                        }
                    },
                    update = { editor ->
                        // ✅ 只在内容真正不同时更新，避免循环
                        val currentText = editor.text.toString()
                        val targetContent = contents[currentFile] ?: ""
                        if (currentText != targetContent && targetContent.isNotEmpty()) {
                            AppLog.d(DEBUG_TAG, Str.get(R.string.updating_editor_text_length_change_c, currentText.length, targetContent.length))
                            editor.setText(targetContent)
                            editedContent = targetContent
                            hasChanges = false
                        }
                        editorInstance = editor
                        // ✅ 不在 update 里重设语言/主题：update 会在每次 recomposition 时执行，
                        //    每次都调用 setEditorLanguage 会创建新的 TextMateLanguage 并中断
                        //    进行中的高亮分析（导致首次打开不高亮，切文件后才能恢复）。
                        //    语言/主题统一由 factory 与 LaunchedEffect(currentFile) 负责。
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
            title = { Text(Str.get(R.string.add_new_file)) },
            text = {
                Column {
                    UIComponents.TextInput(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = Str.get(R.string.file_name),
                        placeholder = if (uiType == "web") "web/new.html" else "src/com/example/NewClass.java",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UIComponents.TextInput(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = Str.get(R.string.file_content_optional),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                UIComponents.PrimaryButton(
                    text = Str.get(R.string.add),
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
                    text = Str.get(R.string.cancel),
                    onClick = { showAddFileDialog = false }
                )
            }
        )
    }

    if (showDeleteConfirm != null) {
        UIComponents.ConfirmDialog(
            title = Str.get(R.string.confirm_delete),
            message = Str.get(R.string.delete_1_s, showDeleteConfirm),
            confirmText = Str.get(R.string.delete),
            dismissText = Str.get(R.string.cancel),
            onConfirm = {
                deleteFile(showDeleteConfirm!!)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null },
            isDestructive = true
        )
    }
}