// app/src/main/java/com/UIN/Tool/ui/screen/files/PaneState.kt
package com.UIN.Tool.ui.screen.files

import androidx.annotation.StringRes

/** 窗格侧别。选择集与历史都按侧别隔离（4.3「两格各选一个」的基础）。 */
enum class Side { LEFT, RIGHT }

/** 排序键（4.8：名称/大小/日期/类型 × 升降序 + 目录优先）。 */
enum class SortKey { NAME, SIZE, DATE, TYPE }

data class SortSpec(
    val key: SortKey = SortKey.NAME,
    val ascending: Boolean = true,
    val dirsFirst: Boolean = true
)

/**
 * 列表条目：只带渲染需要的字段。
 * 目录列表在 IO 线程一次性扫好存进 [PaneState.items]，UI 层不再碰 [java.io.File]——
 * 大目录下每个 `File.length()` 都是一次系统调用，绝不能发生在主线程。
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long
) {
    /** 系统隐藏 = 以 `.` 开头（4.8 的第一个复选框）。 */
    val isSystemHidden: Boolean get() = name.startsWith(".")
}

/**
 * 过滤+排序+截断后的结果。
 *
 * [total]、[dirs]、[files] 全部是**截断前**的数字：页脚要回答的是「这个目录里到底有多少东西」，
 * 而不是「屏幕上渲染了几行」—— 后者被 `LIST_CAP` 卡死在 2000，拿去显示只会把
 * 「其实还有八千个」这件事藏起来，比不显示更误导。
 *
 * 英文文案用 `Folders 3 · Files 12` 这种标签式写法而不是 `3 folders`：
 * 仓库里没有一个 `<plurals>`，为这一行单开复数机制不值当，而标签式在单复数下都读得通。
 */
data class VisibleList(
    val items: List<FileEntry>,
    val total: Int,
    val dirs: Int = 0,
    val files: Int = 0
) {
    val truncated: Boolean get() = total > items.size
}

/** 单窗格状态（MT.md §5.2）。全部是可序列化的值类型，方便整体替换与保存恢复。 */
data class PaneState(
    /** 当前目录绝对路径。 */
    val cwd: String = "",
    /** 当前根目录绝对路径（4.17：根范围切换后由 ViewModel 重新注入）。 */
    val root: String = "",
    /** 后退历史（旧 → 新，末项为来的那个）。 */
    val back: List<String> = emptyList(),
    /** 前进历史。 */
    val forward: List<String> = emptyList(),
    /** 目录原始条目（未过滤）。 */
    val items: List<FileEntry> = emptyList(),
    val sort: SortSpec = SortSpec(),
    val showSystemHidden: Boolean = false,
    val showManualHidden: Boolean = false,
    /** 手动隐藏清单快照：放进状态里，[visible] 才是纯函数（改隐藏 → 重组即变，不靠外部读盘）。 */
    val manualHidden: Set<String> = emptySet(),
    /** 路径栏旁的独立过滤条（4.8：不替换路径行）。 */
    val filter: String = "",
    /** 选中集，键为绝对路径。 */
    val selection: Set<String> = emptySet(),
    /**
     * 区间选择的锚点（4.3：多选态下从 A 拖到 B，选中 A..B 全部）。
     * 存路径而非下标 —— 排序/过滤一变下标就失效，路径还对得上。
     */
    val rangeAnchor: String? = null,
    /**
     * 新建/重命名后要高亮并滚到可视处的条目（4.5）。
     * 一次性：用户下一次点击/导航即清除。
     */
    val highlightPath: String? = null,
    /**
     * 首个可见项下标。窗格状态活在 ViewModel 里，**切走 tab 再回来**时（FilesScreen 已被
     * AnimatedContent 释放）靠它把滚动位置接回去——对齐验收 15「回到文件页不丢滚动」。
     */
    val scrollIndex: Int = 0,
    /**
     * **每个目录各自记住的**滚动位置（路径 → 首个可见项下标），用户反馈 c11：
     * 「返回上一个目录/父目录要记得上次的 UI 位置，上次屏幕中间是文件夹 A，这次还得是 A」。
     *
     * [scrollIndex] 只装得下**当前**目录的位置，离开它的一瞬间就作废了；要"回来还在原处"，
     * 就必须把每个走过的目录各留一份。离开某目录时写一条、进入时查一条（[PaneState.memoScroll]）。
     * 上限 64 条、按插入序淘汰 —— 这份表活在 ViewModel 状态里，不限长迟早跟着会话一起长胖。
     */
    val scrolls: Map<String, Int> = emptyMap(),
    val loading: Boolean = false,
    /**
     * 归档索引进度（4.15：「索引中…」）；null = 不在索引、或用不着提示。
     *
     * 7z 有索引但要建、tar 系根本没有索引，两者都得把整包扫一遍才列得出目录。
     * 只有**列表还空着**时才显示，列表一出来就不该再闪提示。
     */
    val indexingCount: Int? = null,
    /** 非空 = 目录读不出来（无权限/被拔卡）。渲染成错误空态，不白屏不崩溃（5.4）。 */
    @StringRes val errorRes: Int? = null,
    /** 只读目录（4.11/验收 19）：不可写就给徽标，不让用户试了才知道。 */
    val readOnly: Boolean = false,
    /**
     * 该窗格是否正处在某个归档**内部**（4.15）。
     *
     * 非空 = 当前目录其实是归档内的虚拟目录。条目仍复用 [items]，只是每条的
     * [FileEntry.path] 是虚拟路径（`zip!/内部路径`）—— 这样 `visible()`、行渲染、
     * 选中集、上下文菜单全都原样可用，不必为归档另开一套渲染。
     */
    val archive: ArchiveState? = null
) {
    val canGoBack: Boolean get() = back.isNotEmpty()
    val canGoForward: Boolean get() = forward.isNotEmpty()

    /** 已退到根（4.17：此时列表末尾要出现「文件管理设置」行）。 */
    val atRoot: Boolean get() = root.isNotEmpty() && cwd == root

    /** 上一级可点（到根禁用，不越出根）。 */
    val canGoUp: Boolean get() = root.isNotEmpty() && cwd != root

    /**
     * 可见条目：隐藏开关 → 过滤词 → 排序 → 截断（5.4：>2000 只渲染前 2000）。
     * 隐藏判定在这里做而非读目录时，⛔ 才能「勾一下立刻生效」且不用重新扫盘。
     */
    fun visible(): VisibleList {
        var list = items
        if (!showSystemHidden || !showManualHidden) {
            list = list.filter { e ->
                (showSystemHidden || !e.isSystemHidden) &&
                    (showManualHidden || e.path !in manualHidden)
            }
        }
        val q = filter.trim()
        if (q.isNotEmpty()) {
            // 每次 visible() 只解析一次，再拿返回的判定函数去 filter ——
            // 不能写成 `list.filter { parseFilter(q)(it.name) }`，那会对每一行重复编译正则
            val matches = parseFilter(q)
            // p.txt 第 9 条：这里**仍然只匹配名称**。窗格里列出的是同一个目录的直接子项，
            // 它们的 path 全都以同一个父目录开头 —— 把 path 也算进来，输入父目录名
            //（如 Download）就会命中整页每一行，过滤器当场失去意义。
            // 「包含路径」的要求落在两处真正会跨目录的地方：文件搜索的命中判定
            //（FileSearch）与搜索结果页的「在当前结果中筛选」。
            list = list.filter { matches(it.name) }
        }
        if (list.size > 1) list = list.sortedWith(entryComparator(sort))
        // 计数必须在 take(LIST_CAP) **之前**做，否则永远只会数出 2000
        val dirs = list.count { it.isDir }
        return VisibleList(
            items = if (list.size > LIST_CAP) list.take(LIST_CAP) else list,
            total = list.size,
            dirs = dirs,
            files = list.size - dirs
        )
    }

    /**
     * 面包屑分段：从根到当前层，每段带可点击的绝对路径（4.2）。
     * 根是第一段，所以「根 = /storage/emulated/0 或 /」两种范围都对。
     *
     * 归档内时（4.15）接在归档文件那一层之后：`… / 下载 / x.zip / src / main`。
     * 每段的目标是**虚拟路径**，所以点归档名回的是归档根、点内部段回的是那一层 ——
     * 逐层进出和真实目录完全一致，用户不必区分自己在哪种目录里。
     */
    fun breadcrumb(): List<Pair<String, String>> {
        if (cwd.isEmpty()) return emptyList()
        val arch = archive
        if (arch != null) {
            val out = realCrumbs(arch.archivePath).toMutableList()
            var acc = ""
            for (seg in arch.internalDir.split('/')) {
                if (seg.isEmpty()) continue
                acc = if (acc.isEmpty()) seg else "$acc/$seg"
                out += seg to VirtualPath.join(arch.archivePath, acc)
            }
            return out
        }
        return realCrumbs(cwd)
    }

    /** 真实（非虚拟）路径的面包屑分段。 */
    private fun realCrumbs(path: String): List<Pair<String, String>> {
        val segments = mutableListOf(root to root)
        if (path == root) return segments
        // root = "/" 时 removePrefix 后仍以 / 开头，统一 trim 处理
        val relative = path.removePrefix(root).trim('/')
        if (relative.isEmpty()) return segments
        var current = root
        for (part in relative.split('/')) {
            current = if (current == "/") "/$part" else "$current/$part"
            segments.add(part to current)
        }
        return segments
    }

    companion object {
        /** 5.4 性能预算：单目录只渲染前 2000 项。 */
        const val LIST_CAP = 2000

        /** [PaneState.scrolls] 的上限：够覆盖一次会话里来回逛的目录，又不会让状态越长越肥。 */
        private const val SCROLL_MEMO_LIMIT = 64

        /**
         * 记下 [path] 的滚动位置，返回**新表**（数据类不可变，只能整表换）。
         *
         * 先删后插：重插会把这条顶到末尾，淘汰时取 `first()` 就是「最久没回去过」的那个 ——
         * 语义上正该先忘掉它。index 为 0（还在顶部）时不记：记一条 0 与没记等价，白占名额。
         */
        fun memoScroll(prev: Map<String, Int>, path: String, index: Int): Map<String, Int> {
            if (path.isEmpty() || index <= 0) return prev
            val next = LinkedHashMap<String, Int>(prev)
            next.remove(path)
            next[path] = index
            while (next.size > SCROLL_MEMO_LIMIT) {
                val oldest = next.keys.firstOrNull() ?: break
                next.remove(oldest)
            }
            return next
        }

        /** 读回某个目录记过的位置；没记过 = 0（从头看）。 */
        fun scrollOf(memo: Map<String, Int>, path: String): Int = memo[path] ?: 0
    }
}

/** 目录优先 + 按键排序 + 同键按名称兜底（避免同大小/同秒文件顺序乱跳）。 */
fun entryComparator(spec: SortSpec): Comparator<FileEntry> = Comparator { a, b ->
    if (spec.dirsFirst && a.isDir != b.isDir) {
        return@Comparator if (a.isDir) -1 else 1
    }
    val raw = when (spec.key) {
        SortKey.NAME -> a.name.compareTo(b.name, ignoreCase = true)
        SortKey.SIZE -> a.size.compareTo(b.size)
        SortKey.DATE -> a.modified.compareTo(b.modified)
        SortKey.TYPE -> {
            val t = a.name.substringAfterLast('.', "").lowercase()
                .compareTo(b.name.substringAfterLast('.', "").lowercase())
            if (t != 0) t else a.name.compareTo(b.name, ignoreCase = true)
        }
    }
    if (spec.ascending) raw else -raw
}

/**
 * 把过滤词解析成「名字是否命中」的判定函数。
 *
 * 三种写法，覆盖从「随手敲两个字」到「精确挑一类」的全部场景：
 *
 * | 写法 | 含义 |
 * |---|---|
 * | `zip` | 普通子串，忽略大小写（**默认，与加过滤功能之前的行为完全一致**） |
 * | `/^a.*\.txt$/` | 正则，两侧斜杠包裹 |
 * | `!zip` / `!/x/` | 对上面任一种取反 |
 *
 * ## 两个刻意的取舍
 *
 * 1. **正则写错回落成字面量，而不是「一条都匹配不上」**。用户敲 `/abc(` 的中途，
 *    若直接判空，列表会瞬间清空 —— 看起来像文件被删了。回落成字面量虽然也匹配不到，
 *    但补上 `)` 的那一刻立刻生效，中间不会有一次「全没了」的惊吓。
 *    （搜索面板走的是另一条路：它有 `BadRegex` 提示，因为搜索是显式点按钮触发的；
 *    过滤是**边打边生效**的，弹提示会打断输入，所以这里选择静默回落。）
 * 2. **`!` 单独出现时显示全部**而不是显示空：`!` 是取反的前缀，前面还没有可取反的东西，
 *    等于还没输入完 —— 按「显示全部」处理才是用户当下的预期。
 *
 * 每次 [PaneState.visible] 只调用一次本函数，返回的闭包再逐行判定；
 * 正则编译是相对贵的操作，放进循环里会对 2000 行重复执行。
 */
fun parseFilter(raw: String): (String) -> Boolean {
    var text = raw.trim()
    val negate = text.startsWith("!")
    if (negate) text = text.substring(1).trim()
    // 「!/」「!」这类还没写完的前缀：没有可匹配的内容，等于没过滤
    if (text.isEmpty()) return { true }

    // 两侧都是斜杠且长度够（`/` 单独一个不算）才当正则
    val pattern = if (text.length >= 2 && text.startsWith("/") && text.endsWith("/")) {
        try {
            Regex(text.substring(1, text.length - 1), RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }

    val literal = text
    val base: (String) -> Boolean = if (pattern != null) {
        { name -> pattern.containsMatchIn(name) }
    } else {
        { name -> name.contains(literal, ignoreCase = true) }
    }
    return if (negate) { name -> !base(name) } else base
}
