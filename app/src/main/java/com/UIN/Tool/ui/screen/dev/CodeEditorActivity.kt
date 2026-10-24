// app/src/main/java/com/UIN/Tool/ui/screen/dev/CodeEditorActivity.kt
package com.UIN.Tool.ui.screen.dev

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.UIN.Tool.R
import com.UIN.Tool.ui.screen.dev.editor.EditorViewModel
import com.UIN.Tool.ui.screen.dev.editor.FileAccess
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import java.io.File

private const val TAG = "CodeEditorActivity"

/**
 * 6.2.0 代码编辑器入口。
 *
 * 支持两种进入方式：
 *  1) 插件向导：`project_dir` + ui_type/main_class/plugin_name/plugin_id（不再传 Serializable HashMap）
 *  2) 外部应用 `ACTION_VIEW`：携带 content:// 或 file:// URI，供其他 App「用 UIN Tool 打开」
 *
 * D2（用户拍板）：默认申请文件访问权限，获批后直接用 java.io.File 读写。
 */
class CodeEditorActivity : AppCompatActivity() {

    private val viewModel: EditorViewModel by viewModels()

    private var pendingUri: Uri? = null
    private var projectDirPath: String? = null
    /** 6.2.0 批次22：ACTION_VIEW 打开时置位，跳过「进入即申请存储权限」的打扰
     *  —— content:// 已由分享方临时授权，无需存储权限即可读写。 */
    private var openedViaActionView = false

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onStoragePermissionResult()
    }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onStoragePermissionResult()
    }

    private val openFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val path = FileAccess.resolveRealPath(this, uri)
        val dir = path?.let(::File)
        // 6.2.0 修「选完文件夹没反应」：解析失败时原先是静默 `return`，看起来像卡死/没点上。
        // 现在给出明确失败提示（tree URI 解析已由 resolveRealPath 支持，这里只剩极端 provider）。
        if (dir == null || !dir.isDirectory || !dir.canRead()) {
            AppLog.w(TAG, "openFolder: resolve/read failed path=$path uri=$uri")
            AppToast.error(this, Str.get(R.string.editor_open_folder_failed))
            return@registerForActivityResult
        }
        if (!FileAccess.hasStorageAccess(this)) {
            viewModel.emit(Str.get(R.string.editor_permission_denied_readonly))
        }
        viewModel.openProject(dir)
    }

    // 6.2.0 批次21：另存为目录选择器 —— 保存失败（无存储权限）时弹出，
    // 选完把内容写进该 SAF 目录（无需存储权限）。
    private val saveAsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            viewModel.clearSaveAsRequest()
        } else {
            viewModel.completeSaveAs(uri)
        }
    }

    /**
     * 6.2.0：长按「保存」图标 → 系统级「另存为」文件选择器（ACTION_CREATE_DOCUMENT）。
     * 与 [saveAsLauncher]（只能选目录、沿用原文件名）不同，这里用户能同时决定**文件名和位置**，
     * 才是真正的「另存为」。选择器不限定 MIME 类型，任何代码文件后缀都能保存。
     * 输入=建议文件名（当前标签名），返回=用户选定的可写 Uri。
     */
    private val saveAsDocLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri ->
        if (uri == null) {
            viewModel.clearSaveAsDocRequest()
        } else {
            viewModel.completeSaveAsDoc(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileAccess.initPackageName(this)

        viewModel.onMemoryDocumentsCommitted = { docs ->
            val data = Intent().apply {
                putStringArrayListExtra("file_list", ArrayList(docs.map { it.first }))
                putStringArrayListExtra("file_contents_list", ArrayList(docs.map { it.second }))
            }
            setResult(Activity.RESULT_OK, data)
        }

        handleIntent(intent, fromRestore = savedInstanceState != null)

        // 权限被授予后，把先前挂起的 URI 补开
        if (pendingUri != null && FileAccess.hasStorageAccess(this)) {
            pendingUri?.let(viewModel::openUri)
            pendingUri = null
        }

        setContent {
            UINToolTheme {
                EditorRoot(
                    viewModel = viewModel,
                    onRequestPermission = { requestStorageAccess() },
                    onOpenFolder = {
                        try {
                            openFolderLauncher.launch(null)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "launch OpenDocumentTree failed", e)
                            AppToast.error(this, Str.get(R.string.editor_open_folder_failed))
                        }
                    },
                    onExit = { attemptExit() }
                )
            }

            // 用户说「默认都获取权限」——进入即发起授权，不等手动点。
            // 注意：LaunchedEffect 是 @Composable，必须位于 setContent 的组合上下文内。
            // 6.2.0 批次22：ACTION_VIEW 打开时跳过 —— content:// 已临时授权，够用；
            // 强行跳设置页只会把刚打开的编辑器盖住、让人误以为「没打开」。
            LaunchedEffect(Unit) {
                if (!openedViaActionView && !FileAccess.hasStorageAccess(this@CodeEditorActivity)) {
                    requestStorageAccess()
                }
            }

            // 6.2.0 批次21：观察「另存为」请求（保存因无写入权限失败时置位），
            // 弹出 SAF 目录选择器让用户另存到有权限的目录。
            val saveAsTab by viewModel.saveAsRequest.collectAsState()
            LaunchedEffect(saveAsTab) {
                if (saveAsTab != null) {
                    try {
                        saveAsLauncher.launch(null)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "launch save-as picker failed", e)
                        viewModel.clearSaveAsRequest()
                    }
                }
            }

            // 6.2.0：长按「保存」→ 系统「另存为」文件选择器，建议文件名用当前标签名。
            val saveAsDocTab by viewModel.saveAsDocRequest.collectAsState()
            LaunchedEffect(saveAsDocTab) {
                val tab = saveAsDocTab ?: return@LaunchedEffect
                try {
                    saveAsDocLauncher.launch(tab.name)
                } catch (e: Exception) {
                    AppLog.e(TAG, "launch CreateDocument picker failed", e)
                    viewModel.clearSaveAsDocRequest()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 6.2.0：singleTop 复用本实例时把最新 Intent 存下 —— 否则进程被回收后
        // 系统重建 Activity 投递的还是**最初**那条 Intent，后来打开的文件无法被恢复。
        setIntent(intent)
        handleIntent(intent, fromRestore = false)
    }

    private fun handleIntent(intent: Intent?, fromRestore: Boolean) {
        if (intent == null) return
        val projectDir = intent.getStringExtra("project_dir")
        AppLog.d(
            TAG,
            "handleIntent action=${intent.action} data=${intent.data} " +
                "fromRestore=$fromRestore projectDir=${projectDir != null} " +
                "filePath=${intent.getStringExtra("file_path") != null} " +
                "browseOnly=${intent.getBooleanExtra("browse_only", false)}"
        )

        // 外部应用「用 UIN Tool 打开」
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            // 6.2.0 批次25：记录 flags/type，便于排查 android/data/ 打不开（是否带授权 flag）。
            AppLog.d(
                TAG,
                "ACTION_VIEW uri=$uri scheme=${uri.scheme} type=${intent.type} " +
                    "flags=0x${Integer.toHexString(intent.flags)} " +
                    "grantedRead=${intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0}"
            )
            // 6.2.0 批次22：直接 openUri，不要因「暂无存储权限」挂起 pendingUri 等授权。
            // 6.2.0 修「从外部打开后无法保存」：`openedViaActionView` 会让下方 LaunchedEffect
            // 跳过存储权限申请，但那条豁免只对 **content://**（分享方已临时授权）成立；
            // `file://` 拿不到任何授权，权限不申请 → 后续 writeText 必然 EACCES，
            // 保存静默转成「另存为」。故只有 content:// 才跳过申请。
            openedViaActionView = uri.scheme != "file"
            viewModel.openUri(uri)
            // 6.2.0 修「从多任务窗口回来文件又没了」：进程重建后原 ACTION_VIEW 会被重新
            // 投递，分享方的临时授权此时多半已失效（content:// 读不出、tabs 仍为空）——
            // 回落到「上次打开的文档」恢复，别让编辑器停在空态。仅恢复场景如此：
            // 全新启动时打开失败应如实显示失败原因，而不是换回上一个不相干的文件。
            if (fromRestore && viewModel.tabs.value.isEmpty()) {
                viewModel.restoreLastOpenedIfAny()
            }
            return
        }

        // 6.2.0 批次53：插件 API（Web callHost "openEditor" / 原生 PluginEditorApi）
        // 打开**单个文件** —— file_path 优先于 project_dir 判定（两者同传 = 文件 + 目录树）。
        val filePath = intent.getStringExtra("file_path")
        if (!filePath.isNullOrBlank()) {
            val f = File(filePath)
            AppLog.d(TAG, "open file_path=$filePath exists=${f.isFile} browse=${projectDir != null}")
            if (f.isDirectory) {
                // 批次53修：目录被当成 file_path 传入（老插件/自定义 Intent 直塞路径）——
                // 按浏览模式打开该目录（挂树 + 开首个文本文件）。旧逻辑继续按「文件」走，
                // openProject 后无文档托底 → 空态「未打开文件」，还误报「文件不存在」。
                this.projectDirPath = filePath
                if (!fromRestore || viewModel.tabs.value.isEmpty()) {
                    viewModel.openProject(f)
                    val files = mutableListOf<File>()
                    collectTextFiles(f, files, 0)
                    files.sortedBy { it.relativeTo(f).path.lowercase() }
                        .firstOrNull()?.let { viewModel.openFile(it) }
                }
                if (fromRestore && viewModel.tabs.value.isEmpty()) {
                    viewModel.restoreLastOpenedIfAny()
                }
                return
            }
            if (!projectDir.isNullOrBlank()) {
                // 带项目目录：挂上目录树（与向导同款 projectDirPath 记账，仅 browse 差异见下）。
                this.projectDirPath = projectDir
                if (!fromRestore || viewModel.tabs.value.isEmpty()) {
                    viewModel.openProject(File(projectDir))
                }
            }
            if (!fromRestore || viewModel.tabs.value.isEmpty()) {
                if (f.isFile) {
                    viewModel.openFile(f)
                } else {
                    viewModel.emit("文件不存在: $filePath")
                }
            }
            if (fromRestore && viewModel.tabs.value.isEmpty()) {
                // 进程重建重投 file_path：真实文件路径无授权时效问题，直接重开本文件；
                // 文件已不在（被删/移）才回落「上次打开的文档」。
                if (f.isFile) viewModel.openFile(f) else viewModel.restoreLastOpenedIfAny()
            }
            return
        }

        // 插件向导模式（内容已落盘到 project_dir）
        val dirPath = projectDir
        if (!dirPath.isNullOrBlank()) {
            // project_dir 每次进都记下，否则旋转/进程重建后 attemptExit() 会变成 CANCELED、
            // 向导读不回目录（旧实现整块被 `!fromRestore` 跳过，这个字段一直是 null）。
            this.projectDirPath = dirPath
            // browse_only（批次53 插件 openProject）= 浏览模式：只挂目录树 + 打开**首个**
            // 文本文件（编辑器空态不显示目录树，必须有一页文档托底）；不浏览模式（向导）
            // 才整套收集打开全部草稿文件。
            val browseOnly = intent.getBooleanExtra("browse_only", false)
            // 批次57②（用户指定）：插件向导草稿模式 —— 进入即隐藏用户没关的其他标签
            //（只隐不关），草稿签不进会话缓存（VM.buildSessionSnapshot 按前缀剔除）。
            val wizardDraft = intent.getBooleanExtra("wizard_draft", false)
            if (wizardDraft) viewModel.enterWizardMode(File(dirPath))
            // 配置变更时 tabs 还在（Activity-scoped ViewModel 活着）→ 不重复打开；
            // 进程重建时 tabs 已空 → 重新打开整套草稿。批次57②补：进程重建走会话恢复时
            // 恢复出来的是**用户**标签（草稿从不入缓存）→ 草稿一枚都不在，也要重开整套。
            if (!fromRestore || viewModel.tabs.value.isEmpty() ||
                (wizardDraft && viewModel.tabs.value.none {
                    it.file?.absolutePath?.startsWith(dirPath + File.separator) == true
                })
            ) {
                val dir = File(dirPath)
                viewModel.openProject(dir)
                // 6.2.0 修复：草稿是 src/com/foo/Main.java 这类**嵌套**结构，
                // 只 listFiles() 顶层会拿到全是目录 → 一个文件都开不出来（表现为「未打开文件」）。
                // 此处递归收集全部文本文件，再按相对路径排序打开。
                val files = mutableListOf<File>()
                collectTextFiles(dir, files, 0)
                val sorted = files.sortedBy { it.relativeTo(dir).path.lowercase() }
                if (browseOnly) {
                    sorted.firstOrNull()?.let { viewModel.openFile(it) }
                } else {
                    sorted.forEach { viewModel.openFile(it) }
                }
            }
            // 恢复场景下草稿目录已不可读/为空（tabs 仍空）→ 同样回落到上次文档。
            if (fromRestore && viewModel.tabs.value.isEmpty()) {
                viewModel.restoreLastOpenedIfAny()
            }
            return
        }

        // 6.2.0 修「退出后从多任务窗口回来显示未打开文件」：进程被回收时回来的是
        // 全新 Activity + 空 Intent（既无 ACTION_VIEW 也无 project_dir），旧实现直接空态。
        // 交给 VM 把上次打开的文档恢复回来。
        viewModel.restoreLastOpenedIfAny()
    }

    /** 递归收集目录下的文本文件；depth 上限防止异常嵌套目录把主线程拖死。 */
    private fun collectTextFiles(current: File, out: MutableList<File>, depth: Int) {
        if (depth > 12 || out.size > EditorViewModel.MAX_DIR_ENTRIES * 20) return
        val children = current.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectTextFiles(child, out, depth + 1)
            } else if (FileAccess.isTextFile(child.name)) {
                out += child
            }
        }
    }

    // ==================== 权限 ====================

    private fun requestStorageAccess() {
        if (FileAccess.hasStorageAccess(this)) {
            onStoragePermissionResult()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = FileAccess.manageAllFilesIntent()
            if (intent != null) {
                try {
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    AppLog.e(TAG, "launch MANAGE_APP_ALL_FILES_ACCESS failed", e)
                    AppToast.error(this, Str.get(R.string.editor_permission_launch_failed))
                }
            }
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun onStoragePermissionResult() {
        val granted = FileAccess.hasStorageAccess(this)
        AppLog.d(TAG, "storage permission granted=$granted")
        if (granted) {
            // 6.2.0 批次40：权限到手即把目录树根设为外部存储根 —— 用户打开应用后
            // 直接就能在树里挑文件，不必先「打开一个文件夹」。
            viewModel.ensureDefaultRoot()
            pendingUri?.let(viewModel::openUri)
            pendingUri = null
        } else {
            viewModel.emit(Str.get(R.string.editor_permission_denied_readonly))
        }
    }

    // ==================== 退出 ====================

    private fun attemptExit() {
        if (projectDirPath != null) {
            // 向导模式：编辑器已直接写盘，回传目录让向导读回
            val data = Intent().apply {
                putExtra("project_dir", projectDirPath)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

}

// ============================================================
// 入口 UI：无标签页时的空态 + 权限引导；有内容时交给 CodeEditorScreen
// ============================================================
@Composable
private fun EditorRoot(
    viewModel: EditorViewModel,
    onRequestPermission: () -> Unit,
    onOpenFolder: () -> Unit,
    onExit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            if (msg.isNotEmpty()) AppToast.info(context, msg)
        }
    }

    // 返回拦截：有未保存改动时先确认
    BackHandler { onExit() }

    // 6.2.0 批次24：不再单独渲染「授予权限 / 未打开文件」引导页 —— 统一交给 CodeEditorScreen。
    // 它内部按「没打开文件 → 打开文件夹引导；打开了文件 → 直接显示内容」处理；
    // 从外部（content://）打开的文件本就无需存储权限，无权限时也不再弹权限页、不显示目录树。
    CodeEditorScreen(
        viewModel = viewModel,
        onExit = onExit,
        onRequestPermission = onRequestPermission,
        onOpenFolder = onOpenFolder
    )
}
