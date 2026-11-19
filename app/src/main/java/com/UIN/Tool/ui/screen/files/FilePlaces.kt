// app/src/main/java/com/UIN/Tool/ui/screen/files/FilePlaces.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import android.content.SharedPreferences
import com.UIN.Tool.UinApplication
import org.json.JSONArray
import org.json.JSONObject

/** 一个书签（§4.9）。[group] 为空串 = 未分组，显示在「未分组」下。 */
data class Bookmark(
    val name: String,
    val path: String,
    val group: String = ""
)

/** 一条历史（§4.9）。[at] 是最后访问时间戳，用于排序与过期清理。 */
data class HistoryEntry(
    val path: String,
    val name: String,
    val at: Long
)

/**
 * 书签与历史的持久化（§4.9 / §4.10）。
 *
 * ## 几个刻意的取舍
 *
 * 1. **历史是「目录」不是「文件」**。点历史是去一个位置继续浏览，不是打开那个文件 ——
 *    所以只记目录路径。记文件会让历史变成一堆「上次点开的文件」，与「快速回到某处」的用途不符。
 * 2. **历史点选不新增历史**（§4.9 明写）。否则点一次历史就把那条顶到最前，
 *    用户想回到的那条反而被挤下去 —— 那等于历史不能用。
 * 3. **历史上限与去重**：同一路径只留最新一条，超过 [HISTORY_LIMIT] 条按时间淘汰。
 *    不设上限的话，长期使用者的历史会攒到几千条，每次启动读写都是负担。
 * 4. **书签不去重路径、但按路径覆盖**：同一路径再收藏 = 更新名字/分组，而不是出现两条。
 */
object FilePlaces {

    private const val PREF_NAME = "file_places"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_HISTORY = "history"
    private const val KEY_SEARCH_HISTORY = "search_history"

    /** 历史最多留这么多条；超出按 `at` 从旧到新淘汰。 */
    const val HISTORY_LIMIT = 200

    @Volatile
    private var cached: SharedPreferences? = null

    private fun p(): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val sp = UinApplication.getAppContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            cached = sp
            return sp
        }
    }

    // ==================== 书签 ====================

    /** 全部书签，按「分组名 → 名称」排序。分组为空的排最后（视觉上「未分组」应当靠后）。 */
    fun bookmarks(): List<Bookmark> = readBookmarks().sortedWith(
        compareBy({ it.group.isEmpty() }, { it.group }, { it.name })
    )

    /** 收藏 / 更新。同路径已存在则覆盖名字与分组（不产生第二条同路径书签）。 */
    fun putBookmark(name: String, path: String, group: String) {
        if (path.isBlank()) return
        val list = readBookmarks().filterNot { it.path == path } +
            Bookmark(name.trim().ifBlank { path }, path, group.trim())
        writeBookmarks(list)
    }

    /** 移动到别的分组（§4.9 长按「移动」）。 */
    fun moveBookmark(path: String, group: String) {
        val list = readBookmarks().map { if (it.path == path) it.copy(group = group.trim()) else it }
        writeBookmarks(list)
    }

    fun removeBookmark(path: String) {
        writeBookmarks(readBookmarks().filterNot { it.path == path })
    }

    fun isBookmarked(path: String): Boolean = readBookmarks().any { it.path == path }

    /** 全部已用分组名（供「移动到分组」的下拉去重）。 */
    fun groups(): List<String> =
        readBookmarks().map { it.group }.filter { it.isNotEmpty() }.distinct().sorted()

    // ==================== 历史 ====================

    /**
     * 历史，最新在前。
     *
     * 读取时**顺手做一次过期清理**：上限在写入时保证，但用户若手动改过存储或上限调小过，
     * 这里兜一次底，免得 UI 要各自处理「可能超长」。
     */
    fun history(): List<HistoryEntry> {
        val list = readHistory().sortedByDescending { it.at }
        return if (list.size > HISTORY_LIMIT) list.take(HISTORY_LIMIT) else list
    }

    /**
     * 记一次访问。同一路径**去重并提到最新** —— 否则来回逛两个目录，历史里会堆满同一个路径。
     *
     * @return true = 这次是新增（调用方据此决定要不要刷新抽屉）
     */
    fun addHistory(path: String, name: String): Boolean {
        if (path.isBlank()) return false
        val old = readHistory()
        val existed = old.any { it.path == path }
        val next = (old.filterNot { it.path == path } +
            HistoryEntry(path, name.ifBlank { path }, System.currentTimeMillis()))
            .sortedByDescending { it.at }
            .take(HISTORY_LIMIT)
        writeHistory(next)
        return !existed
    }

    fun removeHistory(path: String) {
        writeHistory(readHistory().filterNot { it.path == path })
    }

    fun clearHistory() = writeHistory(emptyList())

    // ==================== 搜索历史（§4.8 查询框的历史下拉） ====================

    /**
     * 搜索词上限。**刻意远小于地点历史的 200** —— 搜索词是临时记忆，
     * 留上百条只会让下拉变成一堵读不动的墙；15 条刚好一屏扫完。
     */
    const val SEARCH_HISTORY_LIMIT = 15

    /** 搜索历史，**最新的排前面**（下拉就按这个顺序渲染）。 */
    fun searchHistory(): List<String> {
        val list = readSearchHistory()
        return if (list.size > SEARCH_HISTORY_LIMIT) list.take(SEARCH_HISTORY_LIMIT) else list
    }

    /**
     * 记一次搜索词。
     *
     * - 空白词忽略 —— 否则每开一次面板就往历史里塞一个空串；
     * - 重复词**提到最前**而不是追加第二份 —— 反复微调同一个词不该把历史刷满；
     * - 只在确实产生变化时返回 true，调用方据此决定要不要刷新下拉。
     */
    fun addSearchHistory(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        val old = readSearchHistory()
        if (old.firstOrNull() == q) return false
        writeSearchHistory((listOf(q) + old.filterNot { it == q }).take(SEARCH_HISTORY_LIMIT))
        return true
    }

    /** 删掉单条（下拉里逐条清除时用）。 */
    fun removeSearchHistory(query: String) =
        writeSearchHistory(readSearchHistory().filterNot { it == query })

    fun clearSearchHistory() = writeSearchHistory(emptyList())

    private fun readSearchHistory(): List<String> = try {
        val raw = p().getString(KEY_SEARCH_HISTORY, "") ?: ""
        if (raw.isEmpty()) emptyList()
        else JSONArray(raw).let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeSearchHistory(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        p().edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
    }

    // ==================== 读写 ====================

    private fun readBookmarks(): List<Bookmark> = try {
        val raw = p().getString(KEY_BOOKMARKS, "") ?: ""
        if (raw.isEmpty()) emptyList()
        else JSONArray(raw).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val path = o.optString("path", "")
                if (path.isEmpty()) null
                else Bookmark(
                    name = o.optString("name", path),
                    path = path,
                    group = o.optString("group", "")
                )
            }
        }
    } catch (_: Exception) {
        // 存储损坏时返回空而不是崩溃：书签丢了可以重收藏，App 起不来才是灾难
        emptyList()
    }

    private fun writeBookmarks(list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("name", b.name)
                put("path", b.path)
                put("group", b.group)
            })
        }
        p().edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }

    private fun readHistory(): List<HistoryEntry> = try {
        val raw = p().getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) emptyList()
        else JSONArray(raw).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val path = o.optString("path", "")
                if (path.isEmpty()) null
                else HistoryEntry(
                    path = path,
                    name = o.optString("name", path),
                    at = o.optLong("at", 0L)
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeHistory(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("path", h.path)
                put("name", h.name)
                put("at", h.at)
            })
        }
        p().edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
