// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/EditorViewModel.kt
package com.UIN.Tool.ui.screen.dev.editor

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.UIN.Tool.utils.AppLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * 6.2.0 代码编辑器 ViewModel。
 *
 * 取代旧实现的三个结构性缺陷：
 *  1) 文件内容存在普通 remember 里 —— 旋转/进程回收即丢失 → 改由 VM 持有
 *  2) 用 HashMap<String,String> + Serializable 传给 Activity —— 有 TransactionTooLarge 风险
 *     → 改为只传路径/URI，内容按需读取
 *  3) 无保存语义 —— hasChanges 永不为 true → 改为 savedContent/content 对比
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "EditorVM"
        /** 单文件超过该大小时提示只读打开，避免大文件卡死。 */
        const val LARGE_FILE_BYTES = 2L * 1024 * 1024
        /** 文件树单目录最多列出的条目。 */
        const val MAX_DIR_ENTRIES = 500
    }

    // ==================== 标签页 ====================
    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _readOnly = MutableStateFlow(false)
    val readOnly: StateFlow<Boolean> = _readOnly.asStateFlow()

    // ==================== 文件树 ====================
    private val _projectRoot = MutableStateFlow<File?>(null)
    val projectRoot: StateFlow<File?> = _projectRoot.asStateFlow()

    private val _expandedDirs = MutableStateFlow<Set<String>>(emptySet())
    val expandedDirs: StateFlow<Set<String>> = _expandedDirs.asStateFlow()

    /**
     * 6.2.0 批次16：当前「选中」的目录（点目录时高亮标记）。
     * 新建文件/文件夹默认落在这里；null 时回落到 projectRoot。
     */
    private val _selectedDir = MutableStateFlow<File?>(null)
    val selectedDir: StateFlow<File?> = _selectedDir.asStateFlow()

    /**
     * 6.2.0 批次16：文件树版本号。
     *
     * 旧的 refreshTree() 靠「把根目录从 expandedDirs 里移出再加回」来触发重组，但一出一进后
     * Set 与原来完全相同 → StateFlow 对相等值去重、不发射 → FileTreePanel 的
     * remember(root, expanded) 键没变 → buildTree() 不重算 → 重命名/删除/新建后树不刷新。
     * 改为单调递增的版本号，任何文件系统变更都 +1 并作为 remember 键，保证必然重建。
     */
    private val _treeVersion = MutableStateFlow(0)
    val treeVersion: StateFlow<Int> = _treeVersion.asStateFlow()

    // ==================== 设置 ====================
    private val _settings = MutableStateFlow(EditorSettingsStore.load(app))
    val settings: StateFlow<EditorSettings> = _settings.asStateFlow()

    // ==================== 事件 ====================
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // ==================== 另存为（无写入权限时） ====================
    /**
     * 6.2.0 批次21：待另存的标签页。当保存失败（无存储权限 / 原位置不可写）时置位，
     * Activity 观察到非空后弹出 SAF 目录选择器，选完回调 [completeSaveAs]。
     */
    private val _saveAsRequest = MutableStateFlow<EditorTab?>(null)
    val saveAsRequest: StateFlow<EditorTab?> = _saveAsRequest.asStateFlow()

    fun clearSaveAsRequest() {
        _saveAsRequest.value = null
    }

    /**
     * 6.2.0 批次27：最近一次打开失败的原因（如 Android/data 系统保护目录）。
     * 非空时 CodeEditorScreen 的空状态用它替换「未打开文件」，让失败原因常驻可见，
     * 而不是只有一闪而过的 toast。成功打开文件/项目时清空。
     */
    private val _lastOpenError = MutableStateFlow<String?>(null)
    val lastOpenError: StateFlow<String?> = _lastOpenError.asStateFlow()

    /** 插件向导模式：退出时把内存文档回传给调用方。 */
    var onMemoryDocumentsCommitted: ((List<Pair<String, String>>) -> Unit)? = null

    val activeTab: EditorTab?
        get() = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun emit(message: String) {
        _events.tryEmit(message)
    }

    // ==================== 打开 ====================

    /** 打开本地真实文件（D2 主路径：直接 IO）。 */
    fun openFile(file: File, asNew: Boolean = false) {
        val path = file.absolutePath
        _tabs.value.firstOrNull { it.file?.absolutePath == path }?.let {
            _activeTabId.value = it.id
            return
        }
        if (!file.exists()) {
            emit("文件不存在: ${file.name}")
            return
        }
        if (!file.isFile) {
            emit("不是文件: ${file.name}")
            return
        }
        if (!FileAccess.isTextFile(file.name)) {
            emit("暂不支持打开非文本文件: ${file.name}")
            return
        }
        if (file.length() > LARGE_FILE_BYTES) {
            emit("文件较大（${FileAccess.formatSize(file.length())}），以只读方式打开")
            _readOnly.value = true
        }
        // 6.2.0 批次40：区分「文件过大」与「读取失败」—— 以前 OOM 也报「读取失败」，
        // 用户完全看不出真实原因，还会误以为是权限问题。
        val content = when (val r = FileAccess.readText(file)) {
            is FileAccess.ReadResult.Ok -> r.text
            is FileAccess.ReadResult.TooLarge -> {
                emit(
                    "文件过大（${FileAccess.formatSize(r.bytes)}），已拒绝打开" +
                        "（上限 ${FileAccess.formatSize(FileAccess.HARD_LIMIT_BYTES)}）"
                )
                return
            }
            FileAccess.ReadResult.Unreadable -> {
                emit("读取失败: ${file.name}")
                return
            }
        }
        addTab(
            EditorTab(
                id = newId(),
                name = file.name,
                file = file,
                savedContent = if (asNew) "" else content,
                content = content,
                // 6.2.0 批次39：asNew = 以空基线打开、正文已被写入 → 一打开就是「未保存」。
                edited = asNew && content.isNotEmpty()
            )
        )
    }

    /**
     * 打开来自外部 ACTION_VIEW 的 URI。
     * 6.2.0 批次30：直接打开、立即显示文本，不再等待 TextMate 就绪（不显示「正在打开…」占位页）。
     * 冷启动时若语法未就绪，文件先以 EmptyLanguage 打开，待就绪后由 EditorCanvas 补设语言。
     * 6.2.0 批次24：content:// 一律直接走 ContentResolver（分享方已临时授权），
     * 不再先解析成真实路径 —— android/data/、android/obb/ 被系统硬拦，解析成路径后
     * 用 java.io.File 读必然失败。file:// 或能直读的真实路径才走 java.io.File。
     */
    fun openUri(uri: Uri) {
        // 6.2.0 批次25：先尝试固化 URI 读写授权（分享方若带了 PERSISTABLE flag 才会成功，
        // 普通临时授权会抛 SecurityException，属正常，忽略即可）。个别文件管理器只给
        // 临时授权且稍纵即逝，固化后 openInputStream/openFileDescriptor 更稳。
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        // file:// 直接走真实路径。
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                val f = File(path)
                if (f.isFile && f.canRead()) {
                    openFile(f)
                    return
                }
            }
        }
        // content://（或 file:// 直读失败）：查重后用 ContentResolver 读。
        val identity = uri.toString()
        _tabs.value.firstOrNull { it.uri == identity }?.let {
            _activeTabId.value = it.id
            return
        }
        val content = FileAccess.readUri(getApplication(), uri)
        if (content == null) {
            // 兜底：尝试按解析出的真实路径直读（个别 provider 的 content:// 其实可映射到可读文件）。
            val realPath = FileAccess.resolveRealPath(getApplication(), uri)
            if (realPath != null) {
                val f = File(realPath)
                if (f.isFile && f.canRead()) {
                    openFile(f)
                    return
                }
            }
            // 6.2.0 批次26：Android 11+ 对其他应用的 Android/data|obb 是系统硬拦截（即便
            // 授予「所有文件访问」也 EACCES），直接 IO 打不开。给出明确提示，而不是含糊的
            // 「无法读取该文件/未打开文件」，引导用户改用系统文件选择器（SAF 可访问该目录）。
            val blocked = uri.path?.let(FileAccess::isProtectedPath) == true ||
                realPath?.let(FileAccess::isProtectedPath) == true
            if (blocked) {
                AppLog.e(TAG, "openUri blocked by scoped storage: $uri")
                val msg = "无法读取：该文件位于系统保护目录（Android/data），请改用系统文件选择器打开"
                _lastOpenError.value = msg
                emit(msg)
            } else {
                AppLog.e(TAG, "openUri read failed, no fallback: $uri")
                val msg = "无法读取该文件（可能过大、编码不受支持，或分享方已回收授权）"
                _lastOpenError.value = msg
                emit(msg)
            }
            return
        }
        _lastOpenError.value = null
        addTab(
            EditorTab(
                id = newId(),
                name = displayNameOf(uri),
                uri = identity,
                savedContent = content,
                content = content
            )
        )
    }

    private fun displayNameOf(uri: Uri): String {
        try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) return c.getString(i) ?: uri.lastPathSegment ?: "未命名"
                    }
                }
        } catch (e: Exception) {
            AppLog.w(TAG, "displayNameOf failed for $uri")
        }
        return uri.lastPathSegment ?: "未命名"
    }

    /** 内存文档（插件向导模式 / 新建未保存）。 */
    fun openMemory(name: String, content: String) {
        val existing = _tabs.value.firstOrNull { it.name == name && it.file == null && it.uri == null }
        if (existing != null) {
            _activeTabId.value = existing.id
            return
        }
        addTab(
            EditorTab(id = newId(), name = name, savedContent = content, content = content)
        )
    }

    /** 批量载入插件向导的文件集合。 */
    fun loadFromEntries(entries: List<Pair<String, String>>) {
        entries.forEach { (name, content) -> openMemory(name, content) }
        if (_tabs.value.isNotEmpty() && _activeTabId.value == null) {
            _activeTabId.value = _tabs.value.first().id
        }
    }

    private fun addTab(tab: EditorTab) {
        _lastOpenError.value = null
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
        // 6.2.0 批次23：外部打开文件后，让文件树立刻可见并尽量定位到该文件。
        ensureTreeRootForFile(tab.file)
    }

    // ==================== 项目 ====================

    fun openProject(dir: File) {
        if (!dir.isDirectory || !dir.canRead()) {
            emit("无法读取目录: ${dir.name}")
            return
        }
        _projectRoot.value = dir
        _expandedDirs.value = setOf(dir.absolutePath)
        _selectedDir.value = dir
    }

    /**
     * 6.2.0 批次40：**启动即建树根**。
     *
     * 应用一启动就申请存储权限，权限到手后目录树本就该有内容 —— 不该再让用户先
     * 「打开一个文件夹」才能看到文件。这里把树根默认设为外部存储根，
     * 前置条件与 [ensureTreeRootForFile] 完全一致（有权限 + 根目录可读）。
     *
     * 只在树根尚未设置时生效，用户自己 [openProject] 选的目录不会被覆盖。
     */
    fun ensureDefaultRoot() {
        if (_projectRoot.value != null) return
        if (!FileAccess.hasStorageAccess(getApplication())) return
        val storageRoot = Environment.getExternalStorageDirectory()
        if (!storageRoot.isDirectory || !storageRoot.canRead()) return
        openProject(storageRoot)
    }

    fun clearProject() {
        _projectRoot.value = null
        _expandedDirs.value = emptySet()
        _selectedDir.value = null
    }

    fun toggleDir(path: String) {
        _expandedDirs.value = _expandedDirs.value.let {
            if (path in it) it - path else it + path
        }
    }

    /** 标记某个目录为「选中」（点目录时高亮；新建文件/文件夹默认落在这里）。 */
    fun selectDir(file: File) {
        _selectedDir.value = file
    }

    /**
     * 6.2.0 批次16：把 [file] 的所有祖先目录（含自身，直到 projectRoot）标记为展开，
     * 保证新建/重命名后的节点在树里可见。
     */
    private fun expandAncestors(file: File) {
        val root = _projectRoot.value ?: return
        val rootPath = root.absolutePath
        val toExpand = mutableSetOf<String>()
        var current: File? = file
        while (current != null) {
            val p = current.absolutePath
            if (!p.startsWith(rootPath)) break
            toExpand.add(p)
            if (p == rootPath) break
            current = current.parentFile
        }
        _expandedDirs.value = _expandedDirs.value + toExpand
    }

    /**
     * 6.2.0 批次23/24：打开文件（含外部 ACTION_VIEW）后，仅在**能定位**该文件时才建树根：
     *  - 必须有真实路径（content:// 打开的 tab.file 为 null，无法定位）；
     *  - 必须有「所有文件访问」权限（否则 listFiles 读不到内部存储）。
     * 能定位 → 树根设为内部存储根、展开祖先并高亮父目录，用户点目录树即可看到当前文件。
     * 不能定位 → 不建树根（projectRoot 保持 null）→ 目录树与目录树开关图标一并隐藏，
     * 直接显示文件本身，也不再弹「授予权限」页。
     */
    private fun ensureTreeRootForFile(file: File?) {
        if (file == null) return                       // content:// 无法定位 → 不建树
        if (!FileAccess.hasStorageAccess(getApplication())) return  // 无权限 → 不建树
        val storageRoot = Environment.getExternalStorageDirectory()
        if (!storageRoot.isDirectory || !storageRoot.canRead()) return
        if (_projectRoot.value == null) {
            _projectRoot.value = storageRoot
            _expandedDirs.value = setOf(storageRoot.absolutePath)
            _selectedDir.value = storageRoot
        }
        val root = _projectRoot.value ?: return
        if (file.absolutePath.startsWith(root.absolutePath)) {
            expandAncestors(file)
            _selectedDir.value = file.parentFile ?: root
        }
    }

    /** 文件系统发生变更后调用：版本号 +1，强制文件树重建。 */
    fun refreshTree() {
        _treeVersion.value = _treeVersion.value + 1
    }

    /** 构建当前展开状态下的文件树。 */
    fun buildTree(): List<FileNode> {
        val root = _projectRoot.value ?: return emptyList()
        return listOf(buildDirNode(root, depth = 0))
    }

    private fun buildDirNode(dir: File, depth: Int): FileNode.DirNode {
        val expanded = dir.absolutePath in _expandedDirs.value
        val children: List<FileNode> = if (!expanded || depth > 12) {
            emptyList()
        } else {
            FileAccess.listProjectChildren(dir).take(MAX_DIR_ENTRIES).map { child ->
                if (child.isDirectory) buildDirNode(child, depth + 1)
                else FileNode.FileLeaf(child)
            }
        }
        return FileNode.DirNode(file = dir, children = children, isExpanded = expanded)
    }

    // ==================== 编辑 / 保存 ====================

    fun setActive(id: String) {
        if (_tabs.value.any { it.id == id }) _activeTabId.value = id
    }

    fun updateContent(id: String, newContent: String) {
        // 6.2.0 批次39（SE 审计 T3-19）：顺带置脏标记，使 isModified 变成 O(1)。
        _tabs.value = _tabs.value.map { t ->
            if (t.id == id) t.copy(content = newContent, edited = true) else t
        }
    }

    /** 保存到已知后端（文件 / content:// / 内存文档）。 */
    fun saveActive(): Boolean {
        val tab = activeTab ?: return false
        return saveTab(tab)
    }

    fun saveTab(tab: EditorTab): Boolean {
        val content = tab.content
        when {
            tab.file != null -> {
                // 6.2.0 批次21：写入失败（含无存储权限）时不再直接报错，改为引导「另存为」。
                if (readOnly.value || !FileAccess.writeText(tab.file, content)) {
                    requestSaveAs(tab)
                    emit("无法写入原文件，请选择另存目录")
                    return false
                }
            }
            tab.uri != null -> {
                if (!FileAccess.writeUri(getApplication(), Uri.parse(tab.uri), content)) {
                    requestSaveAs(tab)
                    emit("无法写入原位置，请选择另存目录")
                    return false
                }
            }
            else -> {
                // 内存文档：仅更新已保存基线，回传给调用方
                commitMemoryDocuments()
            }
        }
        _tabs.value = _tabs.value.map { t ->
            if (t.id == tab.id) t.copy(savedContent = content, edited = false) else t
        }
        emit("已保存 ${tab.name}")
        return true
    }

    /** 置位待另存标签，交由 Activity 弹 SAF 目录选择器。 */
    private fun requestSaveAs(tab: EditorTab) {
        _saveAsRequest.value = tab
    }

    /**
     * 6.2.0 批次21：用户经 SAF 选好目录后，把待另存标签的内容写进该目录的新文档，
     * 并把标签改指向新 Uri（后续保存直接走 ContentResolver，不再受存储权限限制）。
     */
    fun completeSaveAs(treeUri: Uri) {
        val tab = _saveAsRequest.value ?: return
        _saveAsRequest.value = null
        val newUri = FileAccess.saveAsToTree(
            getApplication(), treeUri, tab.name, tab.content
        )
        if (newUri != null) {
            _tabs.value = _tabs.value.map { t ->
                if (t.id == tab.id) {
                    t.copy(
                        file = null,
                        uri = newUri.toString(),
                        savedContent = t.content,
                        edited = false
                    )
                } else t
            }
            _readOnly.value = false
            emit("已另存: ${tab.name}")
        } else {
            emit("另存失败")
        }
    }

    /** 把所有内存文档回传给调用方（插件向导回调）。 */
    fun commitMemoryDocuments() {
        val docs = _tabs.value.filter { it.file == null && it.uri == null }
            .map { it.name to it.content }
        if (docs.isNotEmpty()) onMemoryDocumentsCommitted?.invoke(docs)
    }

    fun closeTab(id: String, force: Boolean = false): Boolean {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return false
        if (tab.isModified && !force) return false
        val idx = _tabs.value.indexOfFirst { it.id == id }
        val remaining = _tabs.value.filterNot { it.id == id }
        _tabs.value = remaining
        if (_activeTabId.value == id) {
            _activeTabId.value = remaining.getOrNull(idx)?.id
                ?: remaining.lastOrNull()?.id
        }
        return true
    }

    fun hasUnsaved(): Boolean = _tabs.value.any { it.isModified }

    fun discardActiveChanges() {
        val tab = activeTab ?: return
        _tabs.value = _tabs.value.map { t ->
            if (t.id == tab.id) t.copy(content = t.savedContent, edited = false) else t
        }
    }

    // ==================== 文件树操作 ====================

    /** 新建文件的默认落点：优先当前选中目录，否则项目根。 */
    private fun creationBase(): File? =
        _selectedDir.value?.takeIf { it.isDirectory } ?: _projectRoot.value

    fun newFile(name: String, content: String = ""): File? {
        val dir = creationBase() ?: return null
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) {
            emit("名称不能为空")
            return null
        }
        val target = File(dir, clean)
        if (target.exists()) {
            emit("同名文件已存在: $clean")
            return null
        }
        target.parentFile?.mkdirs()
        if (!FileAccess.writeText(target, content)) {
            emit("创建失败: $clean")
            return null
        }
        expandAncestors(target)
        refreshTree()
        openFile(target)
        return target
    }

    /**
     * 6.2.0 批次16：在当前选中目录（或根）下新建文件夹。
     * 支持形如 `a/b` 的名字 —— mkdirs() 会一并建出中间目录 a 及其下的 b。
     */
    fun newFolder(name: String): File? {
        val base = creationBase() ?: return null
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) {
            emit("名称不能为空")
            return null
        }
        val target = File(base, clean)
        if (target.exists()) {
            emit("同名目录已存在: $clean")
            return null
        }
        // mkdirs 递归建出 a/b（含中间的 a）；任一层已存在同名文件时会失败
        if (!target.mkdirs()) {
            emit("创建目录失败: $clean")
            return null
        }
        expandAncestors(target)
        refreshTree()
        emit("已创建目录 $clean")
        return target
    }

    fun deleteFile(file: File): Boolean {
        // 6.2.0 批次12：File.delete() 对**非空目录**永远返回 false。
        // 打开侧栏文件树上的 ⋮ 删除一个文件夹时，实际从未删掉过。这里改为递归删除。
        val ok = try {
            deleteRecursively(file)
        } catch (e: Exception) {
            AppLog.e(TAG, "deleteFile failed: ${file.absolutePath}", e)
            false
        }
        if (ok) {
            _tabs.value.filter {
                it.file != null &&
                    (it.file!!.absolutePath == file.absolutePath ||
                        it.file!!.absolutePath.startsWith(file.absolutePath + File.separator))
            }.forEach { closeTab(it.id, force = true) }
            refreshTree()
            emit("已删除 ${file.name}")
        } else {
            emit("删除失败: ${file.name}")
        }
        return ok
    }

    /** 递归删除文件或目录；返回 false 表示有任何一项未能删除。 */
    private fun deleteRecursively(f: File): Boolean {
        if (f.isDirectory) {
            var ok = true
            f.listFiles()?.forEach { ok = deleteRecursively(it) && ok }
            // 目录内容删干净后才能删掉目录本身
            return f.delete() && ok
        }
        return f.delete()
    }

    fun renameFile(file: File, newName: String): Boolean {
        if (newName.isBlank()) {
            emit("名称不能为空")
            return false
        }
        val target = File(file.parentFile, newName)
        if (target.exists()) {
            emit("同名文件已存在: $newName")
            return false
        }
        if (!file.renameTo(target)) {
            emit("重命名失败: ${file.name}")
            return false
        }
        // 6.2.0 批次12：重命名**目录**时，要把它下面所有已打开标签的路径一并迁移，
        // 否则标签仍指向旧路径，保存会失败（旧代码只匹配了被重命名的那一项自身）。
        val oldPath = file.absolutePath
        val newPath = target.absolutePath
        _tabs.value = _tabs.value.map { t ->
            val p = t.file?.absolutePath
            when {
                p == null -> t
                p == oldPath -> t.copy(file = target, name = newName)
                p.startsWith(oldPath + File.separator) -> {
                    // 父目录改名不改变子文件自己的 basename，标签名保持不变，只迁移路径
                    t.copy(file = File(newPath + p.removePrefix(oldPath)))
                }
                else -> t
            }
        }
        // 选中目录若在被重命名的子树内，一并迁移，避免选中态指向已不存在的旧路径
        _selectedDir.value?.absolutePath?.let { sp ->
            when {
                sp == oldPath -> _selectedDir.value = target
                sp.startsWith(oldPath + File.separator) ->
                    _selectedDir.value = File(newPath + sp.removePrefix(oldPath))
            }
        }
        // 展开集合同步迁移旧路径前缀，避免展开状态丢失
        val migrated = _expandedDirs.value.map { ep ->
            when {
                ep == oldPath -> newPath
                ep.startsWith(oldPath + File.separator) -> newPath + ep.removePrefix(oldPath)
                else -> ep
            }
        }.toSet()
        _expandedDirs.value = migrated
        expandAncestors(target)
        refreshTree()
        emit("已重命名为 $newName")
        return true
    }

    /**
     * 6.2.0 批次16：手动为当前标签页指定语法高亮 scope（null = 恢复按后缀自动推断）。
     * 右上角语法选择器调用；改的是 tab.languageScope，EditorCanvas 会据此重设语言。
     */
    fun setActiveLanguageScope(scope: String?) {
        val id = _activeTabId.value ?: return
        _tabs.value = _tabs.value.map { t ->
            if (t.id == id) t.copy(languageScope = scope) else t
        }
    }

    // ==================== 设置 ====================

    fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        EditorSettingsStore.save(getApplication(), next)
    }

    private fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
}
