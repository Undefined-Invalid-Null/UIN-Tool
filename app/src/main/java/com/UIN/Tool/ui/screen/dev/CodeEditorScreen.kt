// app/src/main/java/com/UIN/Tool/ui/screen/dev/CodeEditorScreen.kt
package com.UIN.Tool.ui.screen.dev

import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.UIComponents

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
    var currentFile by remember { mutableStateOf(fileList.firstOrNull() ?: "") }
    var files by remember { mutableStateOf(fileList.toMutableList()) }
    var contents by remember { mutableStateOf(fileContents.toMutableMap()) }
    var hasChanges by remember { mutableStateOf(false) }
    
    var isDarkTheme by remember { mutableStateOf(false) }
    var showAddFileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    var sidebarWidth by remember { mutableStateOf(250.dp) }
    var isSidebarVisible by remember { mutableStateOf(true) }
    val minSidebarWidth = 0.dp
    val maxSidebarWidth = 300.dp

    var editorInstance by remember { mutableStateOf<PinchZoomEditText?>(null) }

    val currentContent = contents[currentFile] ?: ""
    var editedContent by remember { mutableStateOf(currentContent) }

    LaunchedEffect(currentFile) {
        editedContent = contents[currentFile] ?: ""
        hasChanges = false
        editorInstance?.setText(editedContent)
        editorInstance?.resetZoom()
    }

    fun saveCurrentFile() {
        val newContent = editorInstance?.text?.toString() ?: editedContent
        if (currentFile.isNotEmpty()) {
            contents[currentFile] = newContent
            editedContent = newContent
            hasChanges = false
            Logger.i("CodeEditor", "保存文件: $currentFile")
        }
    }

    fun addFile(fileName: String, content: String = "") {
        if (fileName.isNotEmpty() && !files.contains(fileName)) {
            files.add(fileName)
            contents[fileName] = content
            currentFile = fileName
            editedContent = content
            hasChanges = false
            Logger.i("CodeEditor", "添加文件: $fileName")
        }
    }

    fun deleteFile(fileName: String) {
        if (files.size <= 1) return
        files.remove(fileName)
        contents.remove(fileName)
        if (currentFile == fileName) {
            currentFile = files.firstOrNull() ?: ""
            editedContent = contents[currentFile] ?: ""
        }
        Logger.i("CodeEditor", "删除文件: $fileName")
    }

    fun getFileIcon(fileName: String): String {
        return when {
            fileName.endsWith(".java") -> "☕"
            fileName.endsWith(".kt") -> "📘"
            fileName.endsWith(".kts") -> "📘"
            fileName.endsWith(".xml") -> "📄"
            fileName.endsWith(".html") -> "🌐"
            fileName.endsWith(".css") -> "🎨"
            fileName.endsWith(".js") -> "📜"
            fileName.endsWith(".json") -> "📦"
            else -> "📄"
        }
    }

    var isDragging by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("代码编辑器") },
                navigationIcon = {
                    UIComponents.IconButton(
                        icon = if (isSidebarVisible) Icons.Default.MenuOpen else Icons.Default.Menu,
                        onClick = {
                            isSidebarVisible = !isSidebarVisible
                            if (isSidebarVisible) {
                                sidebarWidth = 250.dp
                            } else {
                                sidebarWidth = 0.dp
                            }
                        }
                    )
                },
                actions = {
                    UIComponents.IconButton(
                        icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        onClick = { isDarkTheme = !isDarkTheme }
                    )
                    UIComponents.IconButton(
                        icon = Icons.Default.Add,
                        onClick = { showAddFileDialog = true }
                    )
                    UIComponents.IconButton(
                        icon = Icons.Default.Save,
                        onClick = { saveCurrentFile() }
                    )
                    UIComponents.PrimaryButton(
                        text = "完成",
                        onClick = {
                            saveCurrentFile()
                            onSave(files, contents)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 侧边栏
            Box(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
            ) {
                UIComponents.Card(
                    modifier = Modifier
                        .fillMaxSize(),
                    elevation = 0.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UIComponents.BodyText("项目文件")
                            UIComponents.CaptionText("${files.size} 个文件")
                        }

                        Divider()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
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
                                    onDelete = {
                                        showDeleteConfirm = file
                                    }
                                )
                            }
                        }
                    }
                }

                // 拖动把手
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(6.dp)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                isDragging = true
                                val newWidth = (sidebarWidth + delta.dp).coerceIn(minSidebarWidth, maxSidebarWidth)
                                sidebarWidth = newWidth
                                if (newWidth < 20.dp) {
                                    isSidebarVisible = false
                                } else {
                                    isSidebarVisible = true
                                }
                            },
                            onDragStopped = {
                                isDragging = false
                                if (sidebarWidth < 20.dp) {
                                    sidebarWidth = 0.dp
                                    isSidebarVisible = false
                                } else if (sidebarWidth < 100.dp) {
                                    sidebarWidth = 100.dp
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

            if (isSidebarVisible) {
                Divider(modifier = Modifier.width(1.dp))
            }

            // 代码编辑器区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // 文件信息栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getFileIcon(currentFile),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        UIComponents.BodyText(currentFile.takeLast(50))
                    }
                    Row {
                        if (hasChanges) {
                            UIComponents.CaptionText(
                                "● 已修改",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        UIComponents.CaptionText("行数: ${editedContent.lines().size}")
                    }
                }

                Divider()

                // 编辑器
                val context = LocalContext.current
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            val scrollView = ScrollView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setPadding(0, 0, 0, 0)
                            }

                            val editor = PinchZoomEditText(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                setPadding(24, 16, 24, 16)
                                setTextSize(16f)
                                setTypeface(Typeface.MONOSPACE)
                                setHorizontallyScrolling(true)
                                minLines = 30

                                if (isDarkTheme) {
                                    setTextColor(android.graphics.Color.WHITE)
                                    setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                                } else {
                                    setTextColor(android.graphics.Color.BLACK)
                                    setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
                                }

                                addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                    override fun afterTextChanged(s: Editable?) {
                                        val newText = s?.toString() ?: ""
                                        if (newText != editedContent) {
                                            editedContent = newText
                                            hasChanges = true
                                        }
                                    }
                                })
                            }

                            scrollView.addView(editor)
                            editorInstance = editor
                            addView(scrollView)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        val editor = editorInstance
                        if (editor != null) {
                            val newContent = contents[currentFile] ?: ""
                            if (editor.text.toString() != newContent) {
                                editor.setText(newContent)
                                editedContent = newContent
                                hasChanges = false
                            }
                            if (isDarkTheme) {
                                editor.setTextColor(android.graphics.Color.WHITE)
                                editor.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                            } else {
                                editor.setTextColor(android.graphics.Color.BLACK)
                                editor.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
                            }
                        }
                    }
                )
            }
        }
    }

    // 添加文件对话框
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

    // 删除确认对话框
    if (showDeleteConfirm != null) {
        UIComponents.ConfirmDialog(
            title = "确认删除",
            message = "确定要删除 \"${showDeleteConfirm}\" 吗？",
            onConfirm = {
                deleteFile(showDeleteConfirm!!)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }
}

@Composable
fun FileTreeItem(
    fileName: String,
    icon: String,
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
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 16.sp,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        UIComponents.BodyText(
            fileName,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (hasChanges) {
            Text(
                text = "●",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        UIComponents.IconButton(
            icon = Icons.Default.Close,
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ==================== PinchZoomEditText ====================

class PinchZoomEditText(context: android.content.Context) : EditText(context) {

    private var scaleGestureDetector: ScaleGestureDetector
    private var initialFontSize: Float = 16f
    private var currentScaleFactor: Float = 1f

    init {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                currentScaleFactor *= scaleFactor
                currentScaleFactor = currentScaleFactor.coerceIn(0.5f, 3.0f)
                val newSize = initialFontSize * currentScaleFactor
                setTextSize(newSize.coerceIn(8f, 48f))
                requestLayout()
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                initialFontSize = textSize
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {}
        })
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)
        if (event.pointerCount >= 2) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
            View.MeasureSpec.getSize(heightMeasureSpec) * 2,
            View.MeasureSpec.UNSPECIFIED
        ))
    }

    fun resetZoom() {
        currentScaleFactor = 1f
        setTextSize(initialFontSize)
        requestLayout()
    }

    override fun setTextSize(size: Float) {
        super.setTextSize(size)
        initialFontSize = size
        currentScaleFactor = 1f
    }
}