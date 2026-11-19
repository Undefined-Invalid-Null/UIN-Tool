// app/src/main/java/com/UIN/Tool/ui/screen/files/TrashStore.kt
package com.UIN.Tool.ui.screen.files

import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件管理器「删除」的**回收站**（用户要求：回收被删除的文件）。
 *
 * ## 位置与结构
 *
 * `/sdcard/UIN_Tool/trash/deleted/<id>/`
 * - `data` —— 被删的那一条（文件或整个目录），**保持原名之外的任何内容原样**；
 * - `meta.json` —— 原名、原绝对路径、是否目录、删除时间、体积。
 *
 * 放共享目录而不是应用私有目录：卸载重装后回收站还在，用户能自己进去捞；
 * 与 `RecycleBin` 的「覆盖前备份」共用 `trash/` 根、各占一个子目录，互不干扰。
 *
 * ## 移动策略
 *
 * 先 `renameTo`（同卷内是一次目录项操作，瞬间完成）；跨卷（内置存储删到 SD 卡 /
 * OTG 的东西）才退化成「复制 + 删源」。**复制成功之前绝不先删源** —— 半途失败时
 * 原文件还在，比「进了回收站其实只有一半」安全得多。
 *
 * ## 上限
 *
 * 同卷重命名不占额外空间，跨卷才会真的多一份。仍卡 [MAX_ITEMS] / [MAX_BYTES]，
 * 超了从最旧的开始**真删**（那是回收站自己的容量管理，不是用户这次删除）。
 */
object TrashStore {

    private const val MAX_ITEMS = 500
    private const val MAX_BYTES = 4L * 1024 * 1024 * 1024

    /** 回收站条目（UI 直接吃这个）。 */
    data class Item(
        val id: String,
        val name: String,
        val originalPath: String,
        val isDir: Boolean,
        /** 目录记 -1（删除时不为它再走一遍全树）；文件记真实字节。 */
        val size: Long,
        val deletedAt: Long
    ) {
        fun dir(): File = File(root(), id)
        fun data(): File = File(dir(), "data")
    }

    fun root(): File = File(AppConstants.TRASH_DIR, "deleted")

    /** 列全部条目，最新在前。坏掉的条目目录（无 meta / JSON 坏了）直接跳过。 */
    suspend fun list(): List<Item> = withContext(Dispatchers.IO) {
        val root = root()
        root.listFiles().orEmpty().mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            readMeta(dir)?.let { meta ->
                Item(
                    id = meta.optString("id", dir.name),
                    name = meta.optString("name", "?"),
                    originalPath = meta.optString("originalPath", ""),
                    isDir = meta.optBoolean("isDir", false),
                    size = meta.optLong("size", -1L),
                    deletedAt = meta.optLong("deletedAt", dir.lastModified())
                )
            }
        }.sortedByDescending { it.deletedAt }
    }

    /**
     * 把 [targets] 移进回收站。
     *
     * 返回的 [OpResult] 与普通删除同口径（成功/失败条数 + 首个错误），进度按**顶层条数**报：
     * 同卷移动是瞬间的，逐文件报百分比只会闪成一团。
     */
    suspend fun moveToTrash(
        targets: List<File>,
        onProgress: (OpProgress) -> Unit
    ): OpResult = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext OpResult(false, 0, 0, null)
        var ok = 0
        var failed = 0
        var firstError: String? = null
        val total = targets.size
        onProgress(
            OpProgress(
                kind = OpKind.DELETE,
                totalCount = total,
                totalBytes = -1L,
                sourcePath = targets.first().path
            )
        )
        for ((index, target) in targets.withIndex()) {
            currentCoroutineContext().ensureActive()
            val itemDir = File(root(), nextId())
            // 源已经搬进 itemDir 了吗？取消/失败时的清理只能删**没搬完**的半成品 ——
            // 搬完之后再删 itemDir 等于把用户刚删的东西真删了。
            var moved = false
            try {
                if (!target.exists()) throw IOException("no such file: ${target.path}")
                if (insideTrash(target)) throw IOException("already in trash: ${target.path}")
                if (wouldSwallowTrash(target)) {
                    // 删的目录里正好包着回收站自己：挪进去 = 把自己装进自己。
                    // 这种情况只能让用户显式选「彻底删除」，不能悄悄降级。
                    throw IOException(
                        Str.get(R.string.files_trash_contains_bin)
                    )
                }
                val data = File(itemDir, "data")
                if (!itemDir.mkdirs()) throw IOException("cannot create trash item")
                movePath(target, data)
                moved = true
                // 目录的体积一次算清并写进 meta：回收站列表与容量上限都直接读它，
                // 不再每次打开/每次删除都把整棵回收站走一遍。
                val size = if (data.isDirectory) {
                    data.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } else {
                    data.length()
                }
                writeMeta(
                    itemDir,
                    JSONObject().apply {
                        put("id", itemDir.name)
                        put("name", target.name)
                        put("originalPath", target.absolutePath)
                        put("isDir", data.isDirectory)
                        put("size", size)
                        put("deletedAt", System.currentTimeMillis())
                    }
                )
                // 元数据写成功才算「进回收站」。写失败时下面 catch 会把数据挪回原处 ——
                // 否则 itemDir 里有 data、却没有 meta.json，列表读不到它，用户既看不到
                // 也恢复不了，等于凭空丢了一份数据。
                ok++
            } catch (e: CancellationException) {
                // 取消：只清还没搬完的半成品；已经搬进去的保持原样，源文件才不至于丢
                if (!moved) runCatching { itemDir.deleteRecursively() }
                throw e
            } catch (e: Exception) {
                failed++
                if (moved) {
                    // 搬进去了但收尾失败（多半是 meta 写不进去）：尽量把数据放回原处，
                    // 让「删除失败」真的等于没删。回滚也失败就保留 itemDir —— 数据还在，
                    // 至少能从文件管理器里捞出来。
                    val data = File(itemDir, "data")
                    if (data.exists()) {
                        val rolledBack = runCatching { movePath(data, target) }.isSuccess
                        if (rolledBack) runCatching { itemDir.deleteRecursively() }
                    } else {
                        runCatching { itemDir.deleteRecursively() }
                    }
                } else {
                    runCatching { itemDir.deleteRecursively() }
                }
                if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
            }
            onProgress(
                OpProgress(
                    kind = OpKind.DELETE,
                    totalCount = total,
                    doneCount = index + 1,
                    totalBytes = -1L,
                    sourcePath = target.path,
                    successCount = ok,
                    failedCount = failed,
                    firstError = firstError,
                    finished = index == total - 1
                )
            )
        }
        trim()
        OpResult(false, ok, failed, firstError)
    }

    /** 恢复到原位置；原位置已被占用时用 `名字 (1)` 另存，返回最终落点。 */
    suspend fun restore(item: Item): File? = withContext(Dispatchers.IO) {
        try {
            val data = item.data()
            if (!data.exists()) return@withContext null
            // 老条目 / meta 坏掉时没有原路径可回 —— 宁可报失败，也不往当前工作目录乱放
            if (item.originalPath.isBlank()) return@withContext null
            var dest = File(item.originalPath)
            if (dest.exists()) dest = FileOps.uniqueSibling(dest)
            val parent = dest.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) return@withContext null
            movePath(data, dest)
            item.dir().deleteRecursively()
            dest
        } catch (_: Exception) {
            null
        }
    }

    /** 彻底删掉一条。 */
    suspend fun deleteForever(item: Item): Boolean = withContext(Dispatchers.IO) {
        runCatching { item.dir().deleteRecursively() }.getOrDefault(false)
    }

    /** 清空。返回删掉的条数。 */
    suspend fun emptyAll(): Int = withContext(Dispatchers.IO) {
        var n = 0
        root().listFiles()?.forEach { if (it.isDirectory && it.deleteRecursively()) n++ }
        n
    }

    // ==================== 内部 ====================

    private fun readMeta(dir: File): JSONObject? = try {
        val f = File(dir, "meta.json")
        if (!f.isFile) null else JSONObject(f.readText())
    } catch (_: Exception) {
        null
    }

    private fun writeMeta(dir: File, json: JSONObject) {
        // 先写临时件再改名：写到一半断电/失败时，宁可没有 meta（条目列表看不见它，
        // 但 data 还在原处），也不要留下半截 JSON 让 readMeta 永远解析失败。
        val f = File(dir, "meta.json")
        val tmp = File(dir, "meta.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(f)) {
            runCatching { tmp.delete() }
            throw java.io.IOException("cannot write trash metadata: ${f.path}")
        }
    }

    private fun nextId(): String {
        val stamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(Date())
        var id = stamp
        var n = 1
        while (File(root(), id).exists()) id = "${stamp}_${n++}"
        return id
    }

    /**
     * 移动一条：同卷 `renameTo`，跨卷「复制 → 删源」。
     *
     * 跨卷失败时**源文件保持原样**（复制不完整就抛，删源那一步走不到）。
     */
    private fun movePath(src: File, dest: File) {
        dest.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("cannot create ${it.path}") }
        if (src.renameTo(dest)) return
        if (src.isDirectory) {
            src.copyRecursively(dest, overwrite = true)
        } else {
            src.copyTo(dest, overwrite = true)
        }
        // 复制已经完整落盘，才轮到删源；删不掉就报错，让上层如实计数
        if (!src.deleteRecursively()) throw IOException("copied but cannot remove source: ${src.path}")
    }

    /** 已经在回收站里（避免把垃圾再回收一遍，套娃）。 */
    private fun insideTrash(target: File): Boolean {
        val rootPath = runCatching { root().canonicalPath }.getOrDefault(root().absolutePath)
        val targetPath = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
        return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    }

    /** 目标是不是回收站的祖先（删 `UIN_Tool` 整目录时会撞上）。 */
    private fun wouldSwallowTrash(target: File): Boolean {
        if (!target.isDirectory) return false
        val rootPath = runCatching { root().canonicalPath }.getOrDefault(root().absolutePath)
        val targetPath = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
        return rootPath.startsWith(targetPath + File.separator)
    }

    /** 容量上限：从最旧的开始真删（体积直接读 meta，不再遍历内容）。 */
    private fun trim() {
        val dirs = root().listFiles()?.filter { it.isDirectory } ?: return
        val sizes = dirs.associateWith { d ->
            runCatching { readMeta(d)?.optLong("size", 0L) ?: 0L }.getOrDefault(0L)
        }
        if (dirs.size <= MAX_ITEMS && sizes.values.sum() <= MAX_BYTES) return
        val ordered = dirs.sortedBy { it.name }
        var count = dirs.size
        var bytes = sizes.values.sum()
        for (d in ordered) {
            if (count <= MAX_ITEMS && bytes <= MAX_BYTES) break
            if (d.deleteRecursively()) {
                count--
                bytes -= (sizes[d] ?: 0L)
            }
        }
    }
}
