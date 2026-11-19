// app/src/main/java/com/UIN/Tool/plugin/PluginOpenDispatchActivity.kt
package com.UIN.Tool.plugin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Patterns
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.domain.model.PluginInfo
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.screen.dev.CodeEditorActivity
import com.UIN.Tool.ui.screen.dev.editor.FileAccess
import com.UIN.Tool.ui.screen.files.FileOps
import com.UIN.Tool.ui.screen.files.FilePrefs
import com.UIN.Tool.ui.screen.files.OpenWithIcon
import com.UIN.Tool.ui.screen.files.OpenWithItem
import com.UIN.Tool.ui.screen.files.OpenWithTile
import com.UIN.Tool.ui.screen.files.TARGET_ARCHIVE
import com.UIN.Tool.ui.screen.files.TARGET_EDITOR
import com.UIN.Tool.ui.screen.files.builtInOpsFor
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Str
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.UIN.Tool.utils.AppToast

/**
 * 系统「分享 / 用其他应用打开」中转页（完整页面）。
 *
 * 接收 ACTION_SEND / SEND_MULTIPLE / VIEW 传入的文本、URL、文件，整理为 openData JSON，
 * 列出所有声明 openWith 且匹配的插件，由用户选择将内容交给哪个插件。
 *
 * u.txt 第 1 条（用户）：真实文件优先**直通原路径**（内置 proot 默认按原路径挂载共享
 * 存储，插件改的就是原文件，也不限大小）；拿不到真实路径或后端看不到时才复制进
 * 所选插件的 `.incoming/` 兜底。
 */
class PluginOpenDispatchActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PluginOpenDispatch"
        /** 私有目录 / content:// 文件复制进**本应用缓存**时的体积上限（100MB）。
         *  u.txt 第 1 条：交给插件的文件优先直通原路径，不受此限 —— 这里只限制
         *  「内置能力需要一份可随机访问的本地副本」这条路径。 */
        private const val MAX_INCOMING_FILE_SIZE = 100L * 1024 * 1024
        /** 单次接收的最大文件个数 */
        private const val MAX_INCOMING_FILES = 50

        /** 缓存复制的进度刷新节流间隔。每 8KB 一刷会把状态发射本身变成复制的瓶颈。 */
        private const val COPY_TICK_MS = 200L
    }

    private val pluginManager by lazy { ServiceLocator.getPluginManager() }

    private var candidates by mutableStateOf<List<PluginInfo>>(emptyList())
    private var openData by mutableStateOf<JSONObject?>(null)
    private var kind by mutableStateOf("")
    private var kindLabel by mutableStateOf("")
    private var kindIcon by mutableStateOf<ImageVector>(Icons.Outlined.TextSnippet)
    private var summary by mutableStateOf("")
    private var searchQuery by mutableStateOf("")

    /** §4.16 复制到缓存的进行态：非 null 时页面挂七层进度对话框（N10 通用控件）。 */
    private var copyProgress by mutableStateOf<UnifiedProgressState?>(null)

    /** 复制任务句柄，对话框的「取消」就取消它。 */
    private var copyJob: Job? = null

    /** n.txt 第 3 条：私有目录文件的预拷贝是否仍在进行（进行中禁用内置能力并给提示）。 */
    private var preparing by mutableStateOf(false)

    /**
     * 复制用的协程宿主，**不挂在任何生命周期上** ——
     * 用户把本页切到后台甚至按返回时，复制该继续跑完再定位，而不是半途作废留下半个文件。
     * 代价是必须在 [copyToCacheThenLocate] 里自己处理取消与失败清理。
     */
    private val copyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
                AppToast.show(this, Str.get(R.string.no_open_data_received))
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

            // n.txt 第 3 条：分享进来的文件若在**私有目录**（别的应用的 /data/...、
            // Android/data|obb 这类系统硬拦区，或只有临时授权的 content://），
            // 预先复制到本应用工作目录，再交给内置能力。见 [prepareIncomingPayload]。
            if (kind == "file") prepareIncomingPayload(data)

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

    /**
     * n.txt 第 3 条：**传入的文件若在私有目录，先复制到本应用工作目录**。
     *
     * 本应用的内置能力（代码编辑器、hex、归档挂载、APK 信息、定位…）全部建立在
     * 「一个真实、可读、可随机访问的文件路径」上；分享方给的 `content://` 只能由
     * ContentResolver 顺序读，落在别的应用 `/data/...` 私有目录里的路径则直接没有
     * 读权限 —— 这两种文件交给内置能力只会得到「打不开 / 文件不存在」。
     *
     * 处理口径：
     *  1. 能解析出**可读且不在私有目录**的真实路径 → 回填 `filePath`，不复制
     *     （普通 /storage 文件用原路径，一次「看一眼」不该复制几百兆）；
     *  2. 解析不出 / 不可读 / 在私有目录 → 复制到 `cache/files`，回填副本路径；
     *  3. 超过 [MAX_INCOMING_FILE_SIZE] 的不复制（编辑器仍可按 content:// 流式打开）。
     *
     * 复制跑在 IO 线程、复用页面已有的七层进度框（可取消，取消后副本删除、保留原 URI）。
     */
    private fun prepareIncomingPayload(data: JSONObject) {
        copyJob?.cancel()
        val items = mutableListOf<JSONObject>()
        data.optJSONArray("files")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { items += it }
        }
        if (data.optJSONArray("files") == null) items += data

        // 第一遍：能直接用的路径先回填；剩下确实要复制的才进 pending。
        val pending = mutableListOf<Pair<JSONObject, Uri>>()
        for (item in items) {
            val uri = item.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse)
            val existing = item.optString("filePath")
            val resolved = existing.ifEmpty { uri?.let { resolveReadablePath(it) } ?: "" }
            if (resolved.isNotEmpty() && File(resolved).let { it.isFile && it.canRead() } &&
                !isForeignPrivatePath(resolved)
            ) {
                item.put("filePath", resolved)
                continue
            }
            if (uri != null) pending += item to uri
        }
        if (pending.isEmpty()) return

        preparing = true
        copyJob = copyScope.launch {
            var ok = 0
            try {
                withContext(Dispatchers.IO) {
                    val dir = FilePrefs.filesCacheDir()
                    if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("mkdirs failed")
                    pending.forEachIndexed { index, (item, uri) ->
                        ensureActive()
                        val total = querySize(uri)
                        if (total > MAX_INCOMING_FILE_SIZE) {
                            Logger.w(TAG, "skip private copy, too large: $uri ($total)")
                            return@forEachIndexed
                        }
                        val name = sanitizeFileName(
                            item.optString("name").ifEmpty {
                                queryDisplayName(uri) ?: uri.lastPathSegment ?: "file"
                            }
                        )
                        val dest = FileOps.uniqueSibling(File(dir, name))
                        var done = 0L
                        var lastTick = 0L
                        try {
                            val input = contentResolver.openInputStream(uri)
                                ?: throw IllegalStateException("openInputStream failed")
                            input.use { ins ->
                                dest.outputStream().use { out ->
                                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        ensureActive()
                                        val read = ins.read(buf)
                                        if (read < 0) break
                                        out.write(buf, 0, read)
                                        done += read
                                        if (done > MAX_INCOMING_FILE_SIZE) {
                                            throw IllegalStateException("file too large")
                                        }
                                        val now = SystemClock.elapsedRealtime()
                                        if (now - lastTick >= COPY_TICK_MS) {
                                            lastTick = now
                                            copyProgress = UnifiedProgressState(
                                                opLabel = Str.get(R.string.files_copy),
                                                title = Str.get(R.string.open_with_preparing_files),
                                                currentName = dest.name,
                                                sourcePath = uri.toString(),
                                                targetPath = dest.absolutePath,
                                                totalBytes = if (total > 0) total else -1L,
                                                doneBytes = done,
                                                running = true,
                                                totalCount = pending.size,
                                                doneCount = index
                                            )
                                        }
                                    }
                                }
                            }
                            // 写盘成功才回填：JSONObject 在 IO 线程改字符串，
                            // UI 侧只在 preparing=false 之后读（见 openInCodeEditor）。
                            item.put("filePath", dest.absolutePath)
                            ok++
                        } catch (e: CancellationException) {
                            dest.delete()
                            throw e
                        } catch (e: Exception) {
                            dest.delete()
                            Logger.w(TAG, "private copy failed: $uri, ${e.message}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                // 用户取消：未完成的半成品已在上面逐个删除，保留原 content:// 路径。
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.open_with_copy_to_cache_failed), e)
                AppToast.warning(this@PluginOpenDispatchActivity, Str.get(R.string.open_with_copy_to_cache_failed))
            } finally {
                copyProgress = null
                preparing = false
            }
            if (ok > 0) {
                Logger.i(TAG, "pre-copied $ok private incoming file(s) to app storage")
            }
        }
    }

    /** 路径是否在**别的应用**的私有目录（本应用自己的 dataDir 可读，不算）。 */
    private fun isForeignPrivatePath(path: String): Boolean {
        val p = path.replace('\\', '/')
        if (!p.startsWith("/data/")) return false
        // dataDir 是 File（Context.getDataDir()），先取绝对路径再做分隔符归一。
        val own = dataDir.absolutePath.replace('\\', '/').trimEnd('/')
        return p != own && !p.startsWith("$own/")
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
     * 把分享的文件（content://）交给插件后端读取。
     *
     * u.txt 第 1 条（用户）：**能直通原路径就直通** —— 内置 proot 默认按原路径挂载
     * 共享存储，`file://` 分享来的真实文件不需要复制，插件改的就是原文件，也不限大小。
     * 只有拿不到真实路径（content://）或后端看不到时才复制进 `.incoming/` 兜底。
     * 单文件时更新 openData 顶层字段；多文件时更新 files 数组内每一项。
     */
    private fun copyIncomingFiles(plugin: PluginInfo, data: JSONObject): JSONObject {
        val dir = File(File(Constants.PLUGIN_DIR, plugin.pluginId), ".incoming")
        try {
            var copied = 0

            fun copyOne(uriStr: String, name: String, target: JSONObject) {
                try {
                    if (copied >= MAX_INCOMING_FILES) {
                        Logger.w(TAG, Str.get(R.string.open_with_copy_limit_reached))
                        return
                    }
                    val uri = Uri.parse(uriStr)
                    // 直通：能解析出真实文件、且后端看得见 → 不复制、不限大小。
                    // 优先用 prepareIncomingPayload 已回填的 filePath（content:// 若指向
                    // 共享存储，那一步已经解析成真实路径）；否则再按 uri 解析一次。
                    val directPath = target.optString("filePath").takeIf { it.isNotEmpty() }
                        ?: resolveReadablePath(uri)
                    val direct = directPath?.let { path ->
                        val f = File(path)
                        PluginBackendPaths.containerPath(this@PluginOpenDispatchActivity, f)
                            ?.let { container -> container to f.name }
                    }
                    if (direct != null) {
                        target.put("filePath", direct.first)
                        target.put("incomingName", direct.second)
                        target.put("containerPath", direct.first)
                        copied++
                        return
                    }
                    if (!dir.exists() && !dir.mkdirs()) {
                        Logger.w(TAG, Str.get(R.string.failed_to_create_incoming_dir))
                        return
                    }
                    val safeName = sanitizeFileName(name.ifEmpty { "file" })
                    if (safeName.isEmpty()) return
                    val dest = File(dir, "${System.currentTimeMillis()}_$safeName")
                    val input = contentResolver.openInputStream(uri) ?: return
                    input.use { ins ->
                        dest.outputStream().use { out -> ins.copyTo(out) }
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
    // 内置能力（§4.6「三类选项」的前两项）
    // ============================================================

    /**
     * 内置能力1 的置亮条件：**所有文件都置亮**（e23「不论是否是文本」）。
     *
     * 原先按「是不是文本」置灰，理由是二进制塞进编辑器只会读出一屏乱码 —— 但**该不该
     * 这么干由看的人自己判断**，不该替他决定，而且这正是「点了才报错」的反面：
     * 给一个永远置灰的选项，等于告诉他「你这个文件不配」。
     *
     * 判定改成编辑器自己的黑名单（[FileAccess.isTextFile]：只有**已知二进制**后缀才否决），
     * 比原先那份窄白名单宽得多 —— 分享来的 `.dat`/`.nfo`/无后缀文件一律放行。
     * MIME 仍优先（分享方给了 `text/` 开头的就直接认）。
     * URL / 纯文本不给：没有「文件」可编辑。
     */
    private val canOpenInEditor: Boolean
        get() {
            val payload = openData
            return when (kind) {
                "text" -> true
                "file" -> {
                    if (payload == null) return false
                    // 单文件的 mime/name 在顶层；**多文件只写进 files 数组**，旧实现只读顶层，
                    // 于是「分享多个文本」时这一行恒为置灰（点了没反应的另一半原因）。
                    val first = payload.optJSONArray("files")?.optJSONObject(0)
                    val mime = payload.optString("mime")
                        .ifEmpty { first?.optString("mime") ?: "" }
                    val name = payload.optString("name")
                        .ifEmpty { first?.optString("name") ?: "" }
                    mime.startsWith("text/") || FileAccess.isTextFile(name)
                }
                // URL 没有「文件」可编辑
                else -> false
            }
        }

    /** 内置能力2 只对文件成立：URL / 纯文本没有「所在位置」可言。 */
    private val canLocate: Boolean get() = kind == "file"

    /** §4.6 内置能力1：把内容交给代码编辑器，复用编辑器既有的 ACTION_VIEW / file_path 链路。 */
    private fun openInCodeEditor() {
        if (preparing) {
            toast(R.string.open_with_preparing_wait)
            return
        }
        val payload = openData ?: return

        // 目标 URI：单文件在顶层；**多文件只写进 files 数组**（见 parseIntent 的 SEND_MULTIPLE
        // 分支），旧实现只读顶层 uri，多文件时 uriStr 与 text 双空 → 静默 return，表现就是
        // 「点了没反应」。这里统一收口成「顶层 → files[0]」两步取。
        val uriStr = payload.optString("uri").ifEmpty {
            payload.optJSONArray("files")?.optJSONObject(0)?.optString("uri") ?: ""
        }

        // ① 能解析出真实路径就走 file_path —— 编辑器直接 File读，**不经过 URI 授权**，
        //    没有「分享方临时授权已回收」「FLAG 没转授成功」这一整类失败模式，最稳。
        val realPath = payload.optString("filePath").ifEmpty {
            uriStr.takeIf { it.isNotEmpty() }?.let { resolveReadablePath(Uri.parse(it)) } ?: ""
        }
        if (realPath.isNotEmpty() && File(realPath).isFile) {
            openEditorAt(realPath)
            return
        }

        // ② 纯文本分享（SEND text/plain）压根没有 URI —— 先落成缓存副本再交给编辑器。
        if (uriStr.isEmpty()) {
            val text = payload.optString("text")
            if (text.isEmpty()) {
                toast(R.string.open_with_open_editor_failed)
                return
            }
            val dest = writeToCache(text, "shared.txt")
            if (dest == null) {
                toast(R.string.open_with_copy_to_cache_failed)
                return
            }
            openEditorAt(dest.absolutePath)
            return
        }

        // ③ 只剩 content:// 且解析不出路径（android/data 这类被系统硬拦的）：显式 Intent 直指
        //    编辑器，并把分享方给的**临时读权限跟着转授**过去 —— 不带
        //    FLAG_GRANT_READ_URI_PERMISSION 的话，编辑器对这个 content:// 一无所权。
        try {
            val mime = payload.optString("mime")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                setClass(this@PluginOpenDispatchActivity, CodeEditorActivity::class.java)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (mime.isNotEmpty()) type = mime
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.open_with_open_editor_failed), e)
            toast(R.string.open_with_open_editor_failed)
        }
    }

    private fun openEditorAt(path: String) {
        try {
            startActivity(Intent(this, CodeEditorActivity::class.java).putExtra("file_path", path))
            finish()
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.open_with_open_editor_failed), e)
            toast(R.string.open_with_open_editor_failed)
        }
    }

    /**
     * §4.6 内置能力2 / §4.16「定位到所在位置」。两条路：
     *
     * 1. **能解析成真实路径且现在读得到** → 直接深链跳文件页定位。
     *    不复制 —— 为「看一眼位置」复制一份 2GB 视频是灾难；
     * 2. 否则（`content://` 给不出真路径、或跨应用临时授权已失效）→
     *    复制进 `cache/files/` 再定位副本：带进度、可取消、复制前先看剩余空间。
     */
    private fun locateInFileManager() {
        if (preparing) {
            toast(R.string.open_with_preparing_wait)
            return
        }
        val payload = openData ?: return
        val uri = primaryFileUri(payload) ?: run {
            toast(R.string.open_with_locate_unavailable)
            return
        }
        val resolved = resolveReadablePath(uri)
        if (resolved != null) {
            jumpToFilesPage(File(resolved).parent, resolved)
            return
        }
        copyToCacheThenLocate(uri, payload)
    }

    /**
     * 被「定位」的那一个文件。多文件分享取**第一个** ——
     * 定位本质上是单文件概念，把五个文件同时「定位」在界面上没有意义。
     */
    private fun primaryFileUri(payload: JSONObject): Uri? {
        val uriStr = payload.optJSONArray("files")?.let { arr ->
            if (arr.length() > 0) arr.optJSONObject(0)?.optString("uri") else null
        } ?: payload.optString("uri")
        return if (uriStr.isNullOrEmpty()) null else Uri.parse(uriStr)
    }

    /**
     * 把 URI 落成**本应用此刻读得到的真实路径**；拿不到一律返回 null，由调用方转去复制缓存。
     * 三条路按可靠性递减：
     *  1. `file://` —— 本来就是路径；
     *  2. `content://` 的 `MediaStore.DATA` 列 —— 老 provider 与相册类仍会给出；
     *  3. 外置存储 provider 的 documentId —— 文件管理器分享的绝大多数走这条，
     *     documentId 就是「卷 + 相对路径」（`primary:Download/x.txt`）。
     */
    @Suppress("DEPRECATION") // MediaColumns.DATA 已废弃，但它是 content:// → 真路径仅存的通用线索
    private fun resolveReadablePath(uri: Uri): String? {
        val raw = when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> uri.path
            "content" -> queryMediaDataPath(uri) ?: parseExternalStoragePath(uri)
            else -> null
        } ?: return null
        val f = File(raw)
        // isFile：目录没有「所在位置」可言；canRead：跨应用的临时授权可能早已过期
        return if (f.isFile && f.canRead()) f.absolutePath else null
    }

    @Suppress("DEPRECATION")
    private fun queryMediaDataPath(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** `content://com.android.externalstorage.documents/document/primary:Download%2Fx.txt` → 真路径。 */
    private fun parseExternalStoragePath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = uri.lastPathSegment ?: return null
        return when {
            docId.startsWith("primary:") -> File(
                Environment.getExternalStorageDirectory(),
                docId.removePrefix("primary:")
            ).absolutePath

            // 机身自带的 TF 卡：`XXXX-XXXX:子路径`，卷名就是 /storage 下的目录名
            Regex("""^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}:.+""").matches(docId) -> File(
                "/storage/${docId.substringBefore(':')}",
                docId.substringAfter(':')
            ).absolutePath

            else -> null
        }
    }

    /** 分享方告知的总大小；拿不到返回 -1（进度条退化为「无总量」形态，仍显示已复制字节）。 */
    private fun querySize(uri: Uri): Long = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    /** 纯文本分享落成缓存副本（供「用代码编辑器打开」用），重名自动加序号。 */
    private fun writeToCache(text: String, name: String): File? = try {
        val dir = FilePrefs.filesCacheDir()
        if (!dir.exists() && !dir.mkdirs()) return null
        val dest = FileOps.uniqueSibling(File(dir, name))
        dest.writeText(text)
        dest
    } catch (e: Exception) {
        Logger.e(TAG, Str.get(R.string.open_with_copy_to_cache_failed), e)
        null
    }

    private fun copyToCacheThenLocate(uri: Uri, payload: JSONObject) {
        val dir = FilePrefs.filesCacheDir()
        if (!dir.exists() && !dir.mkdirs()) {
            toast(R.string.open_with_copy_to_cache_failed)
            return
        }
        // 重名自动加序号（§4.16 明列）：`a.txt` → `1_a.txt` → `2_a.txt` …
        val name = sanitizeFileName(payload.optString("name").ifEmpty { "file" })
        var dest = File(dir, name)
        var n = 1
        while (dest.exists()) {
            dest = File(dir, "${n}_$name")
            n++
        }

        // 复制前先看剩余空间（§4.16 明列）。总大小拿不到就只能先复制再说。
        val total = querySize(uri)
        if (total > 0 && dir.usableSpace < total) {
            AppToast.showLong(this, Str.get(R.string.open_with_no_space_for_copy))
            return
        }

        val input = try {
            contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
        if (input == null) {
            toast(R.string.open_with_copy_to_cache_failed)
            return
        }

        val label = Str.get(R.string.files_copy)
        val runningTitle = Str.get(R.string.open_with_copy_to_cache_running)
        copyJob = copyScope.launch {
            try {
                // IO 必须挪出主线程：这是本页唯一的重活，压在 Main 上会直接卡住整个页面
                // （进度再怎么节流也救不了一个不返回的 write）。进度状态写在 withContext
                // 内是安全的 —— Compose 的 snapshot state 本身线程安全。
                withContext(Dispatchers.IO) {
                    input.use { ins ->
                        dest.outputStream().use { out ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var done = 0L
                            var lastTick = 0L
                            while (true) {
                                ensureActive()
                                val read = ins.read(buf)
                                if (read < 0) break
                                out.write(buf, 0, read)
                                done += read
                                // 节流：8KB 一刷会把状态发射本身变成复制的瓶颈
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastTick >= COPY_TICK_MS) {
                                    lastTick = now
                                    copyProgress = UnifiedProgressState(
                                        opLabel = label,
                                        title = runningTitle,
                                        currentName = dest.name,
                                        sourcePath = uri.toString(),
                                        targetPath = dest.absolutePath,
                                        totalBytes = if (total > 0) total else -1L,
                                        doneBytes = done,
                                        running = true
                                    )
                                }
                            }
                            // 不写「收尾态」（running=false）：紧跟着就是 jumpToFilesPage，
                            // 再把对话框刷成 100% 只会让它在跳转前闪一下。
                        }
                    }
                }
                jumpToFilesPage(dest.parent, dest.absolutePath)
            } catch (e: CancellationException) {
                // 取消必须**把半成品删掉**，否则缓存里堆一堆残缺文件，还会被每天的清理任务一直背着
                dest.delete()
                copyProgress = null
                toast(R.string.open_with_copy_to_cache_cancelled)
                throw e
            } catch (e: Exception) {
                dest.delete()
                copyProgress = null
                Logger.e(TAG, Str.get(R.string.open_with_copy_to_cache_failed), e)
                toast(R.string.open_with_copy_to_cache_failed)
            }
        }
    }

    /**
     * 跳文件页并定位（§4.16）。
     *
     * **不直接拨弄 MainActivity 的内部状态**，而是走对外那条 `uin://files` 深链 ——
     * 与浏览器里点开链接完全同一条路：少一处耦合，也让这条深链在日常使用里始终被真实走到。
     *
     * 「已复制到缓存」不在这儿提示：文件页的状态条会**自己看出**被定位的文件就在缓存目录里
     * （`PaneState.highlightPath` 前缀比对），消息留在它该在的地方，不用再穿一层参数。
     */
    private fun jumpToFilesPage(dirPath: String?, filePath: String?) {
        val uri = Uri.Builder()
            .scheme("uin")
            .authority("files")
            .appendQueryParameter("path", dirPath ?: "")
            .appendQueryParameter("file", filePath ?: "")
            .build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
        } catch (e: Exception) {
            Logger.e(TAG, Str.get(R.string.open_with_locate_unavailable), e)
            return
        }
        finish()
    }

    private fun toast(resId: Int) {
        AppToast.show(this, Str.get(resId))
    }

    // ============================================================
    // 中转页面（完整 Compose 页面）
    // ============================================================

    @Composable
    private fun OpenReceiverScreen() {
        // §4.16 复制到缓存的七层进度（N10 通用控件）。
        // 语义与文件页 M3/M4d 保持一致：「取消」真的中断复制，「隐藏」只是把框收起来、
        // 复制照跑 —— 跑完仍然会定位跳转，不会因为藏了框就丢了结果。
        copyProgress?.let { progress ->
            UnifiedProgressDialog(
                state = progress,
                onCancel = { copyJob?.cancel() },
                onDismissRequest = { copyProgress = null }
            )
        }
        Scaffold(
            containerColor = AppColors.pageBackground(),
            topBar = {
                ManageTopAppBar(
                    titleText = Str.get(R.string.open_with_choose_title),
                    onBack = { finish() }
                )
            }
        ) { paddingValues ->
            // p.txt 第 3 条（用户）：中转页要**检测内置能力能不能打开这个文件**，能就摆出来。
            // 例：分享进来一个 .md，除了编辑器还应该有「Markdown 阅读」。能力表本来就由
            // `assets/config/file_types.json` 驱动（builtInOpsFor），这里只是把它接到中转页 ——
            // 编辑器/定位两行是页面自己的语义（能处理纯文本、content://），单独保留。
            val extraBuiltIns = remember(openData, preparing) {
                val payload = openData
                if (payload == null || preparing) {
                    emptyList()
                } else {
                    val uriStr = payload.optString("uri").ifEmpty {
                        payload.optJSONArray("files")?.optJSONObject(0)?.optString("uri") ?: ""
                    }
                    val realPath = payload.optString("filePath").ifEmpty {
                        uriStr.takeIf { it.isNotEmpty() }
                            ?.let { resolveReadablePath(Uri.parse(it)) } ?: ""
                    }
                    if (realPath.isEmpty() || !File(realPath).isFile) {
                        emptyList()
                    } else {
                        // 压缩包没有浏览上下文（中转页没有文件页窗格），传 null 时它自己会略过。
                        builtInOpsFor(this, realPath)
                            .filter { it.target != TARGET_EDITOR && it.target != TARGET_ARCHIVE }
                    }
                }
            }
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
                // s.txt 第 1 条：内置能力改成和文件页「打开方式」弹窗同一套网格；
                // 插件列表（含搜索）仍在下方。
                BuiltInGrid(
                    // n.txt 第 3 条：私有目录文件预拷贝期间先别放行 —— 点开时
                    // `openData` 里的 filePath 还没回填，内置能力拿到的仍是不可读路径。
                    canOpenInEditor = canOpenInEditor && !preparing,
                    canLocate = canLocate && !preparing,
                    extraOps = extraBuiltIns,
                    onOpenInEditor = { openInCodeEditor() },
                    onLocate = { locateInFileManager() }
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

    /**
     * s.txt 第 1 条（用户）：内置能力改成**网格排列**，与文件页「打开方式」弹窗
     * 共用同一套格子（[OpenWithTile]）—— 一行一条的大卡片在整页里太占高度，
     * 而且和弹窗里的呈现对不上。
     *
     * 只列出**当前内容确实能打开**的能力：编辑器 / 定位 / 文件类型自带的查看器。
     * 一个都不适用时整段隐藏，把版面留给插件。
     */
    @Composable
    private fun BuiltInGrid(
        canOpenInEditor: Boolean,
        canLocate: Boolean,
        extraOps: List<OpenWithItem>,
        onOpenInEditor: () -> Unit,
        onLocate: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val items = buildList {
            if (canOpenInEditor) add(
                OpenWithItem(
                    label = Str.get(R.string.open_with_builtin_editor),
                    icon = OpenWithIcon.Vector(Icons.Default.Edit),
                    target = TARGET_EDITOR,
                    launch = onOpenInEditor
                )
            )
            if (canLocate) add(
                OpenWithItem(
                    label = Str.get(R.string.open_with_builtin_locate),
                    icon = OpenWithIcon.Vector(Icons.Outlined.Folder),
                    target = "builtin:locate",
                    launch = onLocate
                )
            )
            // p.txt 第 3 条：文件类型自带的内置查看器（Markdown 阅读 / 图片 / 安装包信息 /
            // 字体 / 密钥库 / 十六进制…），由 file_types.json 的能力表决定。
            addAll(extraOps)
        }
        if (items.isEmpty()) return
        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = Str.get(R.string.files_open_builtin_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 168.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                items.chunked(3).forEachIndexed { idx, row ->
                    if (idx > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { item ->
                            OpenWithTile(
                                item = item,
                                onClick = item.launch,
                                onLongClick = {},
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    @Composable
    private fun SummaryCard(icon: ImageVector, label: String, summary: String) {
        UnifiedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
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
        UnifiedCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // 点击由外层 UnifiedCard 统一处理（它已带 onClick），这里再挂一次
                    // 只会多一层语义节点；内层不再重复注册点击。
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
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
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = Str.get(R.string.no_plugin_for_open_with),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            UnifiedButton(
                text = Str.get(R.string.open_with_done),
                onClick = { finish() },
                variant = ButtonVariant.Outlined
            )
        }
    }
}