// app/src/main/java/com/UIN/Tool/ui/screen/files/InAppFilePicker.kt
package com.UIN.Tool.ui.screen.files

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedDropdownMenu
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize
import java.io.File

/**
 * m.txt 第 7 条：**内置文件选择器**。
 *
 * 系统选择器（GetContent / OpenDocument / CreateDocument / OpenDocumentTree）在
 * Android 上体验割裂：不同厂商的界面不一样、无法显示我们自己的类型图标、选完目录
 * 只给一棵用户看不懂的 tree URI。这里用应用自己的目录浏览器替代。
 *
 * 为了**尽量不改调用点**，返回值仍是 `Uri`：文件通过 [InAppPickerProvider] 包成
 * `content://<包名>.inapppicker/f/<绝对路径>/<文件名>`。这样原有
 * `contentResolver.openInputStream / openOutputStream / query` 全部照常工作，
 * 而且 URI 自带真实路径，进程重启后也不会失效。
 *
 * 目录选择返回 `file://` URI —— 编辑器那边本来就有 `resolveRealPath` 认 file scheme。
 */
object InAppFilePicker {

    const val EXTRA_MODE = "picker_mode"
    const val EXTRA_TITLE = "picker_title"
    const val EXTRA_SUGGESTED_NAME = "picker_suggested_name"
    const val EXTRA_URIS = "picker_uris"

    const val MODE_OPEN_FILE = "open_file"
    const val MODE_OPEN_FILES = "open_files"
    const val MODE_SAVE_FILE = "save_file"
    const val MODE_DIRECTORY = "directory"

    /** 由系统选择器的 `String mime` 输入对应过来（MIME 只用于展示，不参与过滤）。 */
    class OpenFile : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent =
            Intent(context, InAppFilePickerActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_OPEN_FILE)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    /** 对应 `ActivityResultContracts.OpenDocument()`（输入是 MIME 数组）。 */
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            Intent(context, InAppFilePickerActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_OPEN_FILE)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    /** 对应 `ActivityResultContracts.OpenMultipleDocuments()`。 */
    class OpenDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            Intent(context, InAppFilePickerActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_OPEN_FILES)

        @Suppress("DEPRECATION")
        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
            if (resultCode == Activity.RESULT_OK) {
                intent?.getParcelableArrayListExtra<Uri>(EXTRA_URIS).orEmpty()
            } else emptyList()
    }

    /** 对应 `ActivityResultContracts.CreateDocument(mime)`（输入是建议文件名）。 */
    class CreateDocument(private val mime: String = "*/*") :
        ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent =
            Intent(context, InAppFilePickerActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_SAVE_FILE)
                .putExtra(EXTRA_SUGGESTED_NAME, input)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    /** 对应 `ActivityResultContracts.OpenDocumentTree()`；返回所选目录的 `file://` URI。 */
    class Directory : ActivityResultContract<Uri?, Uri?>() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            Intent(context, InAppFilePickerActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_DIRECTORY)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    /** 真实文件 → 内置 provider 的 content URI（读写都支持）。 */
    fun fileUri(context: Context, file: File): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(context.packageName + ".inapppicker")
            .appendPath("f")
            .appendPath(file.absolutePath)
            .appendPath(file.name)
            .build()

    /** 内置 URI 是否指向本应用的 provider（避免误判系统 content URI）。 */
    fun isInAppUri(context: Context, uri: Uri?): Boolean =
        uri?.scheme == "content" && uri.authority == context.packageName + ".inapppicker"
}

/**
 * 给内置选择器返回的 URI 提供读写能力。
 *
 * 只在本应用内使用（`exported=false`），所以路径直接放在 URI 里；没有进程内缓存，
 * 也就不会出现「选完文件、进程被杀，回来 URI 失效」的情况。
 */
class InAppPickerProvider : ContentProvider() {

    private fun resolve(uri: Uri): File? {
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != "f") return null
        return File(segments[1])
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = resolve(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        if (mode.contains('w')) file.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(file, parseMode(mode))
    }

    /**
     * `ContentResolver.openFileDescriptor` 传来的模式串 → 打开标志。
     *
     * 平台自带的 `ParcelFileDescriptor.parseMode` 是 `@hide`（android.jar 里没有），
     * 只能自己按同样的规则翻译：`r` 只读、`w`/`wt` 截断写、`wa` 追加写、
     * `rw` 读写、`rwt` 读写并截断。
     */
    private fun parseMode(mode: String): Int = when (mode) {
        "r" -> ParcelFileDescriptor.MODE_READ_ONLY
        "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
            ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE
        "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
            ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_APPEND
        "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
        "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or
            ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE
        else -> throw IllegalArgumentException("Unsupported mode: $mode")
    }

    override fun getType(uri: Uri): String {
        val file = resolve(uri) ?: return "application/octet-stream"
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = resolve(uri) ?: return null
        val cols = projection
            ?.filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
            ?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        cursor.addRow(cols.map { col ->
            when (col) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        })
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (resolve(uri)?.delete() == true) 1 else 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

// ==================== 选择器界面 ====================

class InAppFilePickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(InAppFilePicker.EXTRA_MODE)
            ?: InAppFilePicker.MODE_OPEN_FILE
        val suggested = intent.getStringExtra(InAppFilePicker.EXTRA_SUGGESTED_NAME).orEmpty()

        setContent {
            UINToolTheme {
                PickerRoot(
                    mode = mode,
                    suggestedName = suggested,
                    onFinish = { finish() },
                    onPickFiles = { files ->
                        val uri = InAppFilePicker.fileUri(this, files.first())
                        setResult(Activity.RESULT_OK, Intent().setData(uri))
                        finish()
                    },
                    onPickMany = { files ->
                        @Suppress("DEPRECATION")
                        val uris = ArrayList(files.map { InAppFilePicker.fileUri(this, it) })
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putParcelableArrayListExtra(InAppFilePicker.EXTRA_URIS, uris)
                        )
                        finish()
                    },
                    onPickDirectory = { dir ->
                        setResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(dir)))
                        finish()
                    }
                )
            }
        }
    }
}

/**
 * 「另存为」时避免覆盖已有文件：同名就按 `名字 (1).ext` 递增。
 *
 * 系统 SAF 的 CreateDocument 也是这个行为；这里静默覆盖等于把用户别的文件抹掉，
 * 所以宁可换个名字让两份都在。
 */
private fun uniqueFile(dir: File, name: String): File {
    val first = File(dir, name)
    if (!first.exists()) return first
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "")
    var candidate = first
    var i = 1
    while (candidate.exists() && i < 100) {
        candidate = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
        i++
    }
    // 极端情况：同名文件多到 99 个。宁可用时间戳，也绝不静默覆盖别人的文件。
    if (candidate.exists()) {
        candidate = File(
            dir,
            if (ext.isEmpty()) "$base (${System.currentTimeMillis()})"
            else "$base (${System.currentTimeMillis()}).$ext"
        )
    }
    return candidate
}

@Composable
private fun PickerRoot(
    mode: String,
    suggestedName: String,
    onFinish: () -> Unit,
    onPickFiles: (List<File>) -> Unit,
    onPickMany: (List<File>) -> Unit,
    onPickDirectory: (File) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("inapp_picker", Context.MODE_PRIVATE)
    }
    val root = remember {
        val saved = prefs.getString("last_dir", null)?.let(::File)
        if (saved != null && saved.isDirectory && saved.canRead()) saved
        else Environment.getExternalStorageDirectory()
    }
    var currentDir by remember { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fileName by remember { mutableStateOf(suggestedName) }
    var reloadKey by remember { mutableStateOf(0) }
    // n.txt 第 11 条：内置选择器也提供**排序方式**。默认「名称升序、目录在前」，
    // 选择记在本地 prefs 里，下次打开还是同一套顺序。
    var sortKey by remember {
        mutableStateOf(prefs.getString("sort_key", "name") ?: "name")
    }
    var sortAsc by remember { mutableStateOf(prefs.getBoolean("sort_asc", true)) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    // o.txt 第 9 条：路径栏的「点 = 跳转 / 长按 = 复制」，与单窗格文件页完全同一套交互。
    var jumpOpen by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var jumpError by remember { mutableStateOf(false) }

    // 目录内容每次切换/刷新都在 IO 线程读，避免大目录在主线程卡住。
    androidx.compose.runtime.LaunchedEffect(currentDir, reloadKey, sortKey, sortAsc) {
        prefs.edit()
            .putString("last_dir", currentDir.absolutePath)
            .putString("sort_key", sortKey)
            .putBoolean("sort_asc", sortAsc)
            .apply()
        val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val comparator = when (sortKey) {
                    "size" -> compareBy<File> { it.length() }
                    "date" -> compareBy<File> { it.lastModified() }
                    else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                }.let { if (sortAsc) it else it.reversed() }
                // 目录恒在前：排序只作用于「同组内」的顺序，把文件夹混进文件里
                // 反而更难找到入口。
                currentDir.listFiles()
                    ?.filter { !it.name.startsWith(".") }
                    ?.sortedWith(
                        compareByDescending<File> { it.isDirectory }.then(comparator)
                    )
                    .orEmpty()
            } catch (e: Exception) {
                AppLog.w("InAppPicker", "list ${currentDir.path} failed: ${e.message}")
                emptyList()
            }
        }
        entries = list
        selected = emptySet()
    }

    fun enter(dir: File) {
        if (dir.isDirectory && dir.canRead()) {
            currentDir = dir
        } else {
            AppToast.warning(context, Str.get(R.string.file_picker_cannot_read))
        }
    }

    fun goUp() {
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead()) currentDir = parent
        else onFinish()
    }

    fun commitSave() {
        val name = fileName.trim()
        if (name.isEmpty()) {
            AppToast.warning(context, Str.get(R.string.file_picker_name_required))
            return
        }
        val target = if (name.contains('/')) null else uniqueFile(currentDir, name)
        if (target == null) {
            AppToast.warning(context, Str.get(R.string.file_picker_name_required))
            return
        }
        onPickFiles(listOf(target))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.pageBackground())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ManageTopAppBar(
                titleText = when (mode) {
                    InAppFilePicker.MODE_OPEN_FILES -> Str.get(R.string.file_picker_pick_files)
                    InAppFilePicker.MODE_SAVE_FILE -> Str.get(R.string.file_picker_save_as)
                    InAppFilePicker.MODE_DIRECTORY -> Str.get(R.string.file_picker_pick_folder)
                    else -> Str.get(R.string.file_picker_pick_file)
                },
                onBack = { goUp() },
                actions = {
                    // n.txt 第 11 条：排序方式（名称 / 大小 / 日期 + 升降序）。
                    Box {
                        UnifiedIconButton(
                            icon = Icons.Default.Sort,
                            onClick = { sortMenuOpen = true },
                            contentDescription = Str.get(R.string.files_sort_by),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        UnifiedDropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            val options = listOf(
                                "name" to R.string.files_sort_name,
                                "size" to R.string.files_sort_size,
                                "date" to R.string.files_sort_date
                            )
                            options.forEach { (key, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(Str.get(labelRes)) },
                                    leadingIcon = {
                                        if (sortKey == key) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Spacer(Modifier.width(24.dp))
                                        }
                                    },
                                    onClick = {
                                        sortKey = key
                                        sortMenuOpen = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        Str.get(
                                            if (sortAsc) R.string.files_sort_asc
                                            else R.string.files_sort_desc
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (sortAsc) Icons.Default.ArrowUpward
                                        else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    sortAsc = !sortAsc
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                    UnifiedIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { reloadKey++ },
                        contentDescription = Str.get(R.string.file_picker_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            // 当前路径：显示方式与单窗格文件页一致（单行、超长省略）；
            // o.txt 第 9 条补上交互 —— 点击弹「跳转路径」，长按复制完整路径。
            val pathClipboard = LocalClipboardManager.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = AppColors.primary(),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {
                                jumpText = currentDir.absolutePath
                                jumpError = false
                                jumpOpen = true
                            },
                            onLongClick = {
                                pathClipboard.setText(AnnotatedString(currentDir.absolutePath))
                                AppToast.info(context, Str.get(R.string.files_path_copied))
                            }
                        )
                        .padding(vertical = 4.dp)
                )
            }

            if (entries.isEmpty()) {
                Text(
                    text = Str.get(R.string.file_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.textSecondary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(entries, key = { it.absolutePath }) { entry ->
                        PickerRow(
                            entry = entry,
                            selected = entry.absolutePath in selected,
                            multi = mode == InAppFilePicker.MODE_OPEN_FILES,
                            onClick = {
                                if (entry.isDirectory) {
                                    enter(entry)
                                } else if (mode == InAppFilePicker.MODE_OPEN_FILES) {
                                    val key = entry.absolutePath
                                    selected = if (key in selected) selected - key else selected + key
                                } else if (mode == InAppFilePicker.MODE_SAVE_FILE) {
                                    fileName = entry.name
                                } else {
                                    onPickFiles(listOf(entry))
                                }
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                when (mode) {
                    InAppFilePicker.MODE_SAVE_FILE -> {
                        UnifiedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = Str.get(R.string.file_picker_file_name),
                            placeholder = suggestedName.ifEmpty {
                                Str.get(R.string.file_picker_file_name_hint)
                            },
                            singleLine = true,
                            autoFocus = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        UnifiedButton(
                            text = Str.get(R.string.file_picker_save_here),
                            onClick = { commitSave() },
                            modifier = Modifier.fillMaxWidth(),
                            size = ButtonSize.Medium
                        )
                    }
                    InAppFilePicker.MODE_DIRECTORY -> {
                        UnifiedButton(
                            text = Str.get(R.string.file_picker_use_folder),
                            onClick = {
                                if (currentDir.canRead()) onPickDirectory(currentDir)
                                else AppToast.warning(
                                    context, Str.get(R.string.file_picker_cannot_read)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            size = ButtonSize.Medium
                        )
                    }
                    InAppFilePicker.MODE_OPEN_FILES -> {
                        UnifiedButton(
                            text = Str.get(R.string.file_picker_select_count, selected.size),
                            onClick = {
                                val files = entries.filter { it.absolutePath in selected }
                                if (files.isEmpty()) {
                                    AppToast.warning(
                                        context, Str.get(R.string.file_picker_none_selected)
                                    )
                                } else {
                                    onPickMany(files)
                                }
                            },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Medium
                        )
                    }
                }
            }
        }

        // o.txt 第 9 条：路径点击 → 跳转输入框（与文件页同一个文案与判定）。
        if (jumpOpen) {
            UnifiedDialog(
                onDismissRequest = { jumpOpen = false },
                title = Str.get(R.string.files_jump_title),
                content = {
                    UnifiedTextField(
                        value = jumpText,
                        onValueChange = {
                            jumpText = it
                            jumpError = false
                        },
                        label = Str.get(R.string.files_jump_hint),
                        singleLine = true,
                        error = if (jumpError) Str.get(R.string.files_jump_invalid) else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    UnifiedDialogTextButton(
                        onClick = {
                            val target = File(jumpText.trim())
                            if (target.isDirectory && target.canRead()) {
                                currentDir = target
                                jumpOpen = false
                            } else {
                                jumpError = true
                            }
                        },
                        contentColor = MaterialTheme.colorScheme.primary
                    ) { Text(Str.get(R.string.ok)) }
                },
                dismissButton = {
                    UnifiedDialogTextButton(onClick = { jumpOpen = false }) {
                        Text(Str.get(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun PickerRow(
    entry: File,
    selected: Boolean,
    multi: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeIcon(
            fileName = entry.name,
            isDir = entry.isDirectory,
            size = 22.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDirectory) {
                UnifiedCaptionText(formatFileSize(entry.length()))
            }
        }
        if (multi && selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}