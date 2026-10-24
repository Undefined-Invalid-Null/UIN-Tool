// app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/EditorSessionStore.kt
package com.UIN.Tool.ui.screen.dev.editor

import com.UIN.Tool.utils.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 批次56：编辑器**会话缓存** —— 关闭软件后再次打开，恢复上次的标签与未保存修改。
 *
 * 落盘形态（filesDir/editor_session/）：
 *  - `session.json`：标签清单（顺序 / 路径 / 编码 / 激活项 / 窗口页码 / 是否带改动）；
 *  - `draft_<id>.txt`：整读档的未保存正文（只存有改动的；超长草稿由调用方截弃）；
 *  - `ov_<id>_<页号>.txt`：窗口档的改动页（页号语义与运行时 overlays 一致，0 起）。
 *
 * 文件本体**不缓存** —— 打开时重新从磁盘加载（用户预期「文件可能还是得重新加载」）；
 * 全文替换的磁盘 spool 不跨会话恢复（v1 范围，见 CHANGELOG 批次56）。
 * 恢复是尽力而为：任何读失败都吞成空列表，绝不能挡住编辑器打开。
 */
class EditorSessionStore(private val dir: File) {

    data class Entry(
        val id: String,
        val file: String?,
        val uri: String?,
        val name: String,
        val charset: String,
        val windowed: Boolean,
        val active: Boolean,
        val page: Int,
        val edited: Boolean,
        /** 批次56 复查修：文件指纹 —— 恢复时与磁盘比对，防外部改过的文件被按旧页码写坏。 */
        val size: Long = -1L,
        val mtime: Long = -1L
    )

    data class Loaded(
        val entry: Entry,
        /** 整读档草稿正文（null = 无未保存改动，回盘读真文件）。 */
        val draft: String?,
        /** 窗口档改动页（页号 0 起 → 该页改后全文；null = 无改动）。 */
        val overlays: Map<Int, String>?
    )

    private val sessionFile = File(dir, "session.json")
    private fun draftFile(id: String) = File(dir, "draft_$id.txt")
    private fun overlayFile(id: String, page: Int) = File(dir, "ov_${id}_$page.txt")

    @Synchronized
    fun load(): List<Loaded> {
        if (!sessionFile.isFile) return emptyList()
        return try {
            val arr = JSONArray(sessionFile.readText())
            val out = ArrayList<Loaded>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (id.isEmpty()) continue
                val e = Entry(
                    id = id,
                    file = o.optString("file").takeIf { it.isNotEmpty() },
                    uri = o.optString("uri").takeIf { it.isNotEmpty() },
                    name = o.optString("name").ifEmpty { "未命名" },
                    charset = o.optString("charset", FileAccess.CHARSET_AUTO),
                    windowed = o.optBoolean("win"),
                    active = o.optBoolean("active"),
                    page = o.optInt("page", 1).coerceAtLeast(1),
                    edited = o.optBoolean("edited"),
                    size = o.optLong("size", -1L),
                    mtime = o.optLong("mtime", -1L)
                )
                val draft = if (!e.windowed && e.edited) {
                    draftFile(id).takeIf { it.isFile }?.readText()
                } else null
                val overlays = if (e.windowed && e.edited) readOverlays(id) else null
                out.add(Loaded(e, draft, overlays))
            }
            out
        } catch (ex: Exception) {
            AppLog.e(TAG, "session load failed", ex)
            emptyList()
        }
    }

    private fun readOverlays(id: String): Map<Int, String>? {
        val files = dir.listFiles { f ->
            f.name.startsWith("ov_${id}_") && f.name.endsWith(".txt")
        } ?: return null
        if (files.isEmpty()) return null
        val out = HashMap<Int, String>(files.size)
        files.forEach { f ->
            val page = f.name.removePrefix("ov_${id}_").removeSuffix(".txt").toIntOrNull()
            if (page != null) runCatching { out[page] = f.readText() }
        }
        return if (out.isEmpty()) null else out
    }

    /** 写会话（正文/改动页先落，json 最后原子替换 —— 中断时最坏回到上一份完整快照）。 */
    @Synchronized
    fun save(
        entries: List<Entry>,
        drafts: Map<String, String>,
        overlays: Map<String, Map<Int, String>>
    ) {
        dir.mkdirs()
        val keep = HashSet<String>().apply { add(sessionFile.name) }
        val arr = JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            e.file?.let { o.put("file", it) }
            e.uri?.let { o.put("uri", it) }
            o.put("name", e.name)
            o.put("charset", e.charset)
            o.put("win", e.windowed)
            o.put("active", e.active)
            o.put("page", e.page)
            o.put("edited", e.edited)
            o.put("size", e.size)
            o.put("mtime", e.mtime)
            arr.put(o)
            drafts[e.id]?.let { text ->
                draftFile(e.id).writeText(text)
                keep.add(draftFile(e.id).name)
            }
            overlays[e.id]?.forEach { (page, text) ->
                overlayFile(e.id, page).writeText(text)
                keep.add(overlayFile(e.id, page).name)
            }
        }
        val tmp = File(dir, "session.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(sessionFile)) {
            sessionFile.writeText(arr.toString())
            tmp.delete()
        }
        // 清孤儿：已关闭标签 / 改动已保存清脏的旧草稿与改动页。
        dir.listFiles()?.forEach { f -> if (f.name !in keep) f.delete() }
    }

    companion object {
        private const val TAG = "SessionStore"
    }
}
