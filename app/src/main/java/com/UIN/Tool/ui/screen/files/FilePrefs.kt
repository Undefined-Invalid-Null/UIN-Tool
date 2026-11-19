// app/src/main/java/com/UIN/Tool/ui/screen/files/FilePrefs.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import android.content.SharedPreferences
import com.UIN.Tool.UinApplication
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.log.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 文件页专属设置（MT.md §4.17 / N12）。
 *
 * - 与 [com.UIN.Tool.utils.UIConfig] **并列**、独立 SharedPreferences（`file_prefs`），
 *   **归属文件页**，不在管理 → UI 个性化里（N12 ③）；
 * - 随「UI 个性化 → 导出/导入」一起走：[exportJson] 生成 `files` 段、[importJson] 读回；
 * - 全部读取带缺省值，配置被改坏也不会崩；
 * - 访问走 [UinApplication.getAppContext]，调用方不必层层传 Context（与 Str 同一套路）。
 */
object FilePrefs {

    private const val TAG = "FilePrefs"
    private const val PREF_NAME = "file_prefs"

    // ===== 根目录范围（4.17「根的定义」：默认内部存储，可切真根 /）=====
    const val ROOT_INTERNAL = "internal"
    const val ROOT_REAL = "real"

    /** 默认根（内部存储）。与 4.17 一致，不写死到设置里，方便后续调整。 */
    const val INTERNAL_ROOT = "/storage/emulated/0"

    // ===== 默认排序（4.8：名称/大小/日期/类型 × 升降序 + 目录优先）=====
    const val SORT_NAME = "name"
    const val SORT_SIZE = "size"
    const val SORT_DATE = "date"
    const val SORT_TYPE = "type"

    // ===== 行密度 =====
    const val DENSITY_COMPACT = "compact"
    const val DENSITY_NORMAL = "normal"
    const val DENSITY_LOOSE = "loose"

    // ===== 搜索结果上限（i.txt 第 10 条）=====
    /** 默认 2000 条，与旧版硬编码一致。 */
    const val SEARCH_LIMIT_DEFAULT = 2000
    /** 0 = 不限（扫到哪算哪）。 */
    const val SEARCH_LIMIT_UNLIMITED = 0

    // ===== 返回键行为（4.17「返回」分组）=====
    const val BACK_GO_UP = "go_up"
    const val BACK_EXIT = "exit"

    // ===== 解压目标（解压框四选一，用户反馈⑪ + c14）=====
    /** 解到当前窗格所在目录。默认值 —— 长按哪个包，多半就想在它旁边看结果。 */
    const val TARGET_HERE = "here"
    /**
     * 解到**另一窗格**的当前目录。
     *
     * 顶掉了原来的「归档旁」（用户反馈 c14）：归档旁永远等于当前目录 —— 列表里长按的包
     * 就躺在 cwd 下面，两个选项指向同一个地方，摆两条纯属凑数。另一窗格才是真能与
     * 当前目录不同的落点，也正是双窗格「这边挑、那边放」的用法。
     */
    const val TARGET_OTHER = "other"
    /**
     * 解到当前目录下的一个**子文件夹**（用户反馈 c14）。文件夹名在弹窗里输，见 [TARGET_FOLDER] 的输入框。
     *
     * 不存单独的路径键：落点 = `cwd + 文件夹名`，与当前目录绑定，跟着 cwd 走。
     */
    const val TARGET_FOLDER = "folder"
    /** 解到用户指定的目录，路径记在 [customExtractTarget]。 */
    const val TARGET_CUSTOM = "custom"

    // ===== 压缩格式（压缩框记住上次的选择，用户反馈⑪）=====
    // 与 `ArchiveCompress.Format.extension` 同形（带点）。`.7z` 本版**只解不压**，
    // 不在此列 —— 摆一个点下去必然失败的选项，比不给更糟（设置页原来那行就是这么个东西）
    private val FORMAT_EXTS = arrayOf(".zip", ".tar", ".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst")

    // ===== 压缩级别（zip4j / commons-compress 通用三档）=====
    const val LEVEL_FAST = "fast"
    const val LEVEL_DEFAULT = "default"
    const val LEVEL_BEST = "best"

    // zip 条目名回退编码（4.7-C4；GBK/Big5 是代码页名，全球同名，不进字符串资源）
    const val ENCODING_UTF8 = "UTF-8"
    const val ENCODING_GBK = "GBK"
    const val ENCODING_BIG5 = "Big5"
    /** zip 的历史正主（APPNOTE 默认），老 DOS 包的条目名编码。 */
    const val ENCODING_CP437 = "CP437"
    /** 老 Unix 工具写包用的单字节编码。 */
    const val ENCODING_ISO88591 = "ISO-8859-1"

    // ===== 图片查看器背景（四档循环切换）=====
    /** 纯黑：默认。深色底让照片的暗部不被灰底「顶亮」，也是查看器的通行做法。 */
    const val IMAGE_BG_DARK = "dark"
    /** 纯白：看白底截图、文档扫描件时用 —— 黑底会让白图边缘糊掉，看不出留白边界。 */
    const val IMAGE_BG_LIGHT = "light"
    /** 中灰：比纯白柔和，长时间看图不刺眼，也不会像纯黑那样吃掉浅色图的对比度。 */
    const val IMAGE_BG_GRAY = "gray"
    /** 棋盘格：只有这一档能看出**透明区域**（PNG 去背图、图标素材）。 */
    const val IMAGE_BG_CHECKER = "checker"

    // ===== 远程管理默认端口 =====
    /** FTP 默认端口。**不是 21** —— 绑 21 要 root，Android 上必然失败。 */
    const val DEFAULT_FTP_PORT = 2121
    /** HTTP 网页端口。 */
    const val DEFAULT_HTTP_PORT = 8080

    private const val KEY_START_DIR = "start_dir"
    private const val KEY_DUAL_PANE = "dual_pane"
    private const val KEY_PANE_RATIO = "pane_ratio"
    /** 窗格比例三态（用户反馈 d14）：可变 / 不可变 / 默认。 */
    private const val KEY_PANE_RATIO_MODE = "pane_ratio_mode"
    private const val KEY_SORT_KEY = "sort_key"
    private const val KEY_SORT_ASC = "sort_asc"
    private const val KEY_DIRS_FIRST = "dirs_first"
    private const val KEY_SYS_HIDDEN = "show_sys_hidden"
    private const val KEY_MAN_HIDDEN = "show_man_hidden"
    private const val KEY_MANUAL_HIDDEN = "manual_hidden"
    private const val KEY_ROW_DENSITY = "row_density"
    private const val KEY_SEARCH_LIMIT = "search_result_limit"
    private const val KEY_BACK_BEHAVIOR = "back_behavior"
    private const val KEY_REMEMBER_DIR = "remember_last_dir"
    private const val KEY_SESSION = "session"
    private const val KEY_ZIP_FORMAT = "zip_format"
    private const val KEY_ZIP_LEVEL = "zip_level"
    private const val KEY_ZIP_ENCODING = "zip_encoding"
    private const val KEY_KEEP_META = "keep_meta"
    private const val KEY_SPLIT_SIZE = "zip_split_size"
    // 已安装应用页（i.txt 第 3 条）：排序 / 提取目录 / 两套命名表达式都记住。
    private const val KEY_APK_SORT = "apk_sort_mode"
    private const val KEY_APK_DEST = "apk_extract_dest"
    private const val KEY_APK_NAME_TEMPLATE = "apk_name_template"
    private const val KEY_APK_PART_TEMPLATE = "apk_part_template"
    private const val KEY_EXTRACT_TARGET = "extract_target"
    private const val KEY_EXTRACT_DELETE_SRC = "extract_delete_src"
    private const val KEY_CUSTOM_TARGET = "custom_target"
    private const val KEY_RESTORE_META = "restore_meta"
    private const val KEY_OPEN_WITH = "open_with_default"
    private const val KEY_ROOT_SCOPE = "root_scope"
    private const val KEY_ARCHIVE_RO_HINT = "archive_ro_hint"
    /** 归档写回前是否留 `.bak`（反馈 e18）。默认**开** —— 保险不该让人自己想起来打开。 */
    private const val KEY_ARCHIVE_BACKUP = "archive_backup"

    /** 回收站总开关（h.txt 第 2 条）。关掉后删除默认走彻底删除。 */
    private const val KEY_RECYCLE_BIN = "recycle_bin_enabled"

    /** 覆盖已有文件前先复制进回收站（反馈 e15 P0-1）。 */
    private const val KEY_OVERWRITE_TRASH = "overwrite_trash"
    private const val KEY_FOLDER_SORT = "folder_sort"
    private const val KEY_MULTI_LINE = "multi_line_names"
    private const val KEY_IMAGE_BG = "image_viewer_bg"
    /** 进多选的一次性提示条是否已经出过（§4.3）。出过就永不再出。 */
    private const val KEY_SELECT_HINT = "select_hint_shown"
    private const val KEY_REMOTE_FTP_PORT = "remote_ftp_port"
    private const val KEY_REMOTE_HTTP_PORT = "remote_http_port"
    private const val KEY_REMOTE_USER = "remote_user"
    private const val KEY_REMOTE_PASS = "remote_pass"
    private const val KEY_REMOTE_ROOT = "remote_root"

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

    // ==================== 根目录（4.17） ====================

    /** 根目录范围：`internal` = /storage/emulated/0，`real` = /（需已授权文件访问）。 */
    fun rootScope(): String = pick(p().getString(KEY_ROOT_SCOPE, ROOT_INTERNAL), ROOT_INTERNAL, ROOT_INTERNAL, ROOT_REAL)

    fun setRootScope(v: String) = p().edit().putString(KEY_ROOT_SCOPE, pick(v, ROOT_INTERNAL, ROOT_INTERNAL, ROOT_REAL)).apply()

    /** 当前根目录的绝对路径。 */
    fun rootPath(): String = if (rootScope() == ROOT_REAL) "/" else INTERNAL_ROOT

    /** 当前根目录。越界判定统一用它的绝对路径字符串，避免 `/a/` 与 `/a` 判不等。 */
    fun rootFile(): File = File(rootPath())

    // ==================== 浏览 ====================

    /** 默认起始目录；空串 = 用根目录。 */
    fun startDir(): String = p().getString(KEY_START_DIR, "") ?: ""

    /**
     * 「设为首页」（页头 ⋮）：把文件管理器冷启动时的默认起始目录写成这个路径。
     *
     * 与 [startDir] 同一个键 —— 设置页里那个「默认起始目录」输入框显示的就是它，
     * 两处共用一条存储，不会出现「菜单设了、设置页却还是空」的两头不一致。
     */
    fun setStartDir(v: String) = p().edit().putString(KEY_START_DIR, v.trim()).apply()

    fun dualPane(): Boolean = p().getBoolean(KEY_DUAL_PANE, true)

    fun setDualPane(v: Boolean) = p().edit().putBoolean(KEY_DUAL_PANE, v).apply()

    // ==================== 双窗格比例（用户反馈 c4） ====================

    /** 导入外部 JSON 用的整十档收敛（那份数据本来就按 30/40/50/60/70 写）。 */
    private fun normalizePaneRatio(raw: Int): Int = (raw.coerceIn(30, 70) + 5) / 10 * 10

    /**
     * 把任意输入收敛到**分隔条自己的拖动区间**（25~75）。
     *
     * 不再按整十取整：d14 之后比例由拖动产生，拖到 43% 就得存 43%，
     * 存成 40 再读回来，分隔条会当着用户的面跳一下。整十档只在导入外部 JSON 时
     * 还走 [normalizePaneRatio]（那份数据本来就是按档位写的）。
     */
    private fun clampPaneRatio(raw: Int): Int = raw.coerceIn(25, 75)

    /** 左窗格占的宽度百分比（默认 50）。 */
    fun paneRatio(): Int =
        clampPaneRatio(p().getString(KEY_PANE_RATIO, "50")?.toIntOrNull() ?: 50)

    fun setPaneRatio(v: Int) =
        p().edit().putString(KEY_PANE_RATIO, clampPaneRatio(v).toString()).apply()

    /** [paneRatio] 的浮点形态，直接喂给窗格的 `weight`。 */
    fun paneWeight(): Float = paneRatio() / 100f

    // ==================== 窗格比例三态（用户反馈 d14）====================

    /** **可变**：分隔条可拖，拖完把结果存成新的默认比例。 */
    const val RATIO_VARIABLE = "variable"

    /** **不可变**：分隔条锁死在已保存的比例上，拖不动（防误触把布局拖歪）。 */
    const val RATIO_FIXED = "fixed"

    /** **默认**：按默认比例走 —— 可拖但**不保存**，每次进文件页都回到默认值。 */
    const val RATIO_DEFAULT = "default"

    fun paneRatioMode(): String =
        p().getString(KEY_PANE_RATIO_MODE, RATIO_VARIABLE)
            ?.takeIf { it == RATIO_VARIABLE || it == RATIO_FIXED || it == RATIO_DEFAULT }
            ?: RATIO_VARIABLE

    fun setPaneRatioMode(v: String) = p().edit().putString(
        KEY_PANE_RATIO_MODE,
        if (v == RATIO_FIXED || v == RATIO_DEFAULT) v else RATIO_VARIABLE
    ).apply()

    fun sortKey(): String = pick(p().getString(KEY_SORT_KEY, SORT_NAME), SORT_NAME, SORT_NAME, SORT_SIZE, SORT_DATE, SORT_TYPE)

    fun setSortKey(v: String) = p().edit().putString(KEY_SORT_KEY, pick(v, SORT_NAME, SORT_NAME, SORT_SIZE, SORT_DATE, SORT_TYPE)).apply()

    fun sortAscending(): Boolean = p().getBoolean(KEY_SORT_ASC, true)

    fun setSortAscending(v: Boolean) = p().edit().putBoolean(KEY_SORT_ASC, v).apply()

    fun dirsFirst(): Boolean = p().getBoolean(KEY_DIRS_FIRST, true)

    fun setDirsFirst(v: Boolean) = p().edit().putBoolean(KEY_DIRS_FIRST, v).apply()

    // ==================== 仅用于此文件夹的排序（页头 ⋮ 排序方式） ====================

    /**
     * 读某个目录**专属**的排序设定；没设过返回 null，回落到全局 [sortKey] 系列。
     *
     * 存成 `路径 -> "NAME|1|1"`（键|升序|目录优先）一条字符串，不建表也不引入对象 ——
     * 这张表的量级就是「被用户逐个调过的目录数」，几十条而已，每次读一条全量也无所谓。
     *
     * 每次 `p()` 都是 SharedPreferences 的内存缓存，所以循环读不算 IO。
     */
    fun folderSort(path: String): SortSpec? = try {
        val raw = p().getString(KEY_FOLDER_SORT, "")!!.split('\n')
            .firstOrNull { it.substringBefore('=') == path }
            ?.substringAfter('=') ?: return null
        val parts = raw.split('|')
        SortSpec(
            key = when (parts.getOrNull(0)) {
                SORT_SIZE -> SortKey.SIZE
                SORT_DATE -> SortKey.DATE
                SORT_TYPE -> SortKey.TYPE
                else -> SortKey.NAME
            },
            ascending = parts.getOrNull(1) == "1",
            dirsFirst = parts.getOrNull(2) == "1"
        )
    } catch (_: Exception) {
        null
    }

    /** 设为该目录专属排序；传 null 即清除（取消「仅用于此文件夹」时用）。 */
    fun setFolderSort(path: String, spec: SortSpec?) {
        val rows = (p().getString(KEY_FOLDER_SORT, "") ?: "")
            .split('\n').filter { it.isNotBlank() && it.substringBefore('=') != path }
        if (spec != null) {
            val body = rows + buildString {
                append(path)
                append('=')
                append(prefKeyOfSort(spec.key))
                append('|')
                append(if (spec.ascending) '1' else '0')
                append('|')
                append(if (spec.dirsFirst) '1' else '0')
            }
            p().edit().putString(KEY_FOLDER_SORT, body.joinToString("\n")).apply()
        } else {
            p().edit().putString(KEY_FOLDER_SORT, rows.joinToString("\n")).apply()
        }
    }

    /** [SortKey] → 存储串（与 [folderSort] 反向对应）。 */
    private fun prefKeyOfSort(key: SortKey): String = when (key) {
        SortKey.SIZE -> SORT_SIZE
        SortKey.DATE -> SORT_DATE
        SortKey.TYPE -> SORT_TYPE
        else -> SORT_NAME
    }

    // ==================== 多行文件名（用户反馈⑬） ====================

    /**
     * 文件 / 目录名折行显示。默认关 —— 单行省出的竖向空间在列表里更值钱，
     * 长名字走省略号；开了之后长名字占满整行、可点开属性看全名。
     */
    fun multiLineNames(): Boolean = p().getBoolean(KEY_MULTI_LINE, false)

    fun setMultiLineNames(v: Boolean) = p().edit().putBoolean(KEY_MULTI_LINE, v).apply()

    /**
     * 进多选的一次性提示条是否已经出过（§4.3 的可发现性兜底）。
     *
     * 横向框选是**看不见的手势**，不提示就没人发现得了；但每次进多选都弹一次，
     * 两次之后就变成纯噪音 —— 所以用持久化标记把它钉成「整个生命周期只出一次」。
     */
    fun selectHintShown(): Boolean = p().getBoolean(KEY_SELECT_HINT, false)

    fun markSelectHintShown() = p().edit().putBoolean(KEY_SELECT_HINT, true).apply()

    // ==================== 图片查看器背景 ====================

    /**
     * 查看器背景档位。默认黑 —— 换档是**按用户口味**的事，不是按图片内容自动切的：
     * 自动判定会让人每次翻页底色都在跳，看图时最忌讳背景不稳定。
     */
    fun imageViewerBg(): String =
        pick(p().getString(KEY_IMAGE_BG, IMAGE_BG_DARK), IMAGE_BG_DARK, IMAGE_BG_DARK, IMAGE_BG_LIGHT, IMAGE_BG_GRAY, IMAGE_BG_CHECKER)

    fun setImageViewerBg(v: String) =
        p().edit().putString(KEY_IMAGE_BG, pick(v, IMAGE_BG_DARK, IMAGE_BG_DARK, IMAGE_BG_LIGHT, IMAGE_BG_GRAY, IMAGE_BG_CHECKER)).apply()

    // ==================== 远程管理（FTP / HTTP） ====================

    /**
     * FTP 监听端口。**不能默认 21** —— 那要 root 才绑得上，Android 上必然启动失败；
     * 2121 是无特权端口里最像 FTP 的一个，用户也容易记住。
     */
    fun remoteFtpPort(): Int = p().getInt(KEY_REMOTE_FTP_PORT, DEFAULT_FTP_PORT)

    fun setRemoteFtpPort(v: Int) =
        p().edit().putInt(KEY_REMOTE_FTP_PORT, v.coerceIn(1, 65535)).apply()

    /** HTTP 网页端口。8080 是「Web 界面」的事实默认值，且同样不需要 root。 */
    fun remoteHttpPort(): Int = p().getInt(KEY_REMOTE_HTTP_PORT, DEFAULT_HTTP_PORT)

    fun setRemoteHttpPort(v: Int) =
        p().edit().putInt(KEY_REMOTE_HTTP_PORT, v.coerceIn(1, 65535)).apply()

    /**
     * 共享根目录。空 = 跟随文件页当前的根（[rootPath]）。
     *
     * 单独存一个而不是直接用 [rootFile]：用户多半只想共享某个下载目录，
     * 不想把整个内部存储都暴露到局域网里。
     */
    fun remoteRoot(): String {
        val raw = p().getString(KEY_REMOTE_ROOT, "")?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        // 旧版本把这里写进过非路径的值，用户看到的「共享目录默认是 ;」就是这么来的。
        // 存着不清的话每次打开页面都会显示它，启动服务时还会拿 `File(";")` 当共享目录
        // （必然报「共享目录不存在」）。不是绝对路径的一律当没设，并顺手把脏值清掉。
        if (!raw.startsWith("/")) {
            p().edit().putString(KEY_REMOTE_ROOT, "").apply()
            return ""
        }
        return raw
    }

    fun setRemoteRoot(v: String) = p().edit().putString(KEY_REMOTE_ROOT, v.trim()).apply()

    /**
     * 远程账号 / 密码。
     *
     * **明文存在 SharedPreferences 里**，这是权衡后的取舍：它保护的是家里局域网里
     * 一个「顺手开一下」的共享，不是账号体系；引入加密就要多一套密钥管理，
     * 而密钥本身同样得落在设备上，并不真的提高门槛。留空 = 不校验（UI 会明确提示）。
     */
    fun remoteUser(): String = p().getString(KEY_REMOTE_USER, "") ?: ""

    fun setRemoteUser(v: String) = p().edit().putString(KEY_REMOTE_USER, v).apply()

    fun remotePass(): String = p().getString(KEY_REMOTE_PASS, "") ?: ""

    fun setRemotePass(v: String) = p().edit().putString(KEY_REMOTE_PASS, v).apply()

    // ==================== 默认打开方式（用户反馈③·管理页） ====================

    /**
     * 后缀 → 默认打开方式的全部绑定，按写入顺序返回。
     *
     * 存法与 [folderSort] 同构：一行一条 `后缀=目标`，`目标` 要么是
     * `builtin:<id>`（内置能力），要么是 `包名/类名`（系统应用的组件）。
     * 拆行而不是存 JSON，是为了让「删一条 = 过滤掉一行」保持 O(n) 且不用引号转义。
     */
    fun openWithDefaults(): List<Pair<String, String>> =
        (p().getString(KEY_OPEN_WITH, "") ?: "")
            .split('\n')
            .filter { it.isNotBlank() && '=' in it }
            .map { it.substringBefore('=') to it.substringAfter('=') }

    /** 这个后缀当前绑定的打开方式；没绑过返回 null。后缀大小写不敏感。 */
    fun openWithDefault(suffix: String): String? =
        openWithDefaults().firstOrNull { it.first.equals(suffix, ignoreCase = true) }?.second

    /** 新增 / 覆盖一条绑定（同后缀只留最后写入的那条）。 */
    fun setOpenWithDefault(suffix: String, target: String) {
        val key = suffix.trim().trimStart('.').lowercase()
        if (key.isEmpty() || target.isEmpty()) return
        val rest = openWithDefaults().filter { !it.first.equals(key, ignoreCase = true) }
        val body = (rest + (key to target)).joinToString("\n") { "${it.first}=${it.second}" }
        p().edit().putString(KEY_OPEN_WITH, body).apply()
    }

    /** 删一条绑定；后缀大小写不敏感。 */
    fun removeOpenWithDefault(suffix: String) {
        val body = openWithDefaults()
            .filter { !it.first.equals(suffix, ignoreCase = true) }
            .joinToString("\n") { "${it.first}=${it.second}" }
        p().edit().putString(KEY_OPEN_WITH, body).apply()
    }

    /** 系统隐藏（以 `.` 开头）。 */
    fun showSystemHidden(): Boolean = p().getBoolean(KEY_SYS_HIDDEN, false)

    fun setShowSystemHidden(v: Boolean) = p().edit().putBoolean(KEY_SYS_HIDDEN, v).apply()

    /** 手动隐藏（用户标记的条目）。 */
    fun showManualHidden(): Boolean = p().getBoolean(KEY_MAN_HIDDEN, false)

    fun setShowManualHidden(v: Boolean) = p().edit().putBoolean(KEY_MAN_HIDDEN, v).apply()

    /** 手动隐藏清单（绝对路径）。 */
    fun manualHidden(): Set<String> = p().getStringSet(KEY_MANUAL_HIDDEN, emptySet()) ?: emptySet()

    fun setManualHidden(path: String, hidden: Boolean) {
        val cur = manualHidden().toMutableSet()
        if (hidden) cur.add(path) else cur.remove(path)
        p().edit().putStringSet(KEY_MANUAL_HIDDEN, cur).apply()
    }

    /**
     * 批量改手动隐藏集 —— **一次 commit**。
     *
     * 逐条调 [setManualHidden] 会连着写 N 次 prefs：中途被取消/失败就留下**半截**状态，
     * 前一半藏了、后一半还露着，而且没有任何地方能看出来这次到底改到第几个。
     *
     * @param paths 为空是空操作（不动 prefs，也就不会白触发一次监听）
     */
    fun setManualHiddenAll(paths: Collection<String>, hidden: Boolean) {
        if (paths.isEmpty()) return
        val cur = manualHidden().toMutableSet()
        if (hidden) cur.addAll(paths) else cur.removeAll(paths)
        p().edit().putStringSet(KEY_MANUAL_HIDDEN, cur).apply()
    }

    fun rowDensity(): String = pick(p().getString(KEY_ROW_DENSITY, DENSITY_NORMAL), DENSITY_NORMAL, DENSITY_COMPACT, DENSITY_NORMAL, DENSITY_LOOSE)

    fun setRowDensity(v: String) = p().edit().putString(KEY_ROW_DENSITY, pick(v, DENSITY_NORMAL, DENSITY_COMPACT, DENSITY_NORMAL, DENSITY_LOOSE)).apply()

    /** 搜索结果上限；[SEARCH_LIMIT_UNLIMITED] = 不限。旧版本没存过时取默认 2000。 */
    fun searchResultLimit(): Int =
        p().getInt(KEY_SEARCH_LIMIT, SEARCH_LIMIT_DEFAULT).coerceAtLeast(SEARCH_LIMIT_UNLIMITED)

    fun setSearchResultLimit(v: Int) =
        p().edit().putInt(KEY_SEARCH_LIMIT, v.coerceAtLeast(SEARCH_LIMIT_UNLIMITED)).apply()

    // ==================== 返回 ====================

    fun backBehavior(): String = pick(p().getString(KEY_BACK_BEHAVIOR, BACK_GO_UP), BACK_GO_UP, BACK_GO_UP, BACK_EXIT)

    fun setBackBehavior(v: String) = p().edit().putString(KEY_BACK_BEHAVIOR, pick(v, BACK_GO_UP, BACK_GO_UP, BACK_EXIT)).apply()

    fun rememberLastDir(): Boolean = p().getBoolean(KEY_REMEMBER_DIR, true)

    fun setRememberLastDir(v: Boolean) = p().edit().putBoolean(KEY_REMEMBER_DIR, v).apply()

    /**
     * 上次会话快照（两窗 cwd/back/forward + 活动窗格），JSON 字符串。
     * 进程被系统杀掉后重启靠它还原位置与历史（§5.2「进程重建后原样恢复」）；
     * 受 [rememberLastDir] 开关控制，且**不进导出 JSON**（会话痕迹不该跟着配置走）。
     */
    fun sessionJson(): String = p().getString(KEY_SESSION, "") ?: ""

    fun setSessionJson(v: String) = p().edit().putString(KEY_SESSION, v).apply()

    // ==================== 压缩（M4d 消费，先落值） ====================

    fun zipFormat(): String = pick(p().getString(KEY_ZIP_FORMAT, ".zip"), ".zip", *FORMAT_EXTS)

    fun setZipFormat(v: String) = p().edit().putString(KEY_ZIP_FORMAT, pick(v, ".zip", *FORMAT_EXTS)).apply()

    fun zipLevel(): String = pick(p().getString(KEY_ZIP_LEVEL, LEVEL_DEFAULT), LEVEL_DEFAULT, LEVEL_FAST, LEVEL_DEFAULT, LEVEL_BEST)

    fun setZipLevel(v: String) = p().edit().putString(KEY_ZIP_LEVEL, pick(v, LEVEL_DEFAULT, LEVEL_FAST, LEVEL_DEFAULT, LEVEL_BEST)).apply()

    fun zipEncoding(): String = pick(p().getString(KEY_ZIP_ENCODING, ENCODING_UTF8), ENCODING_UTF8, ENCODING_UTF8, ENCODING_GBK, ENCODING_BIG5,
        ENCODING_ISO88591, ENCODING_CP437)

    fun setZipEncoding(v: String) = p().edit().putString(KEY_ZIP_ENCODING, pick(v, ENCODING_UTF8, ENCODING_UTF8, ENCODING_GBK, ENCODING_BIG5,
        ENCODING_ISO88591, ENCODING_CP437)).apply()

    fun keepMeta(): Boolean = p().getBoolean(KEY_KEEP_META, true)

    fun setKeepMeta(v: Boolean) = p().edit().putBoolean(KEY_KEEP_META, v).apply()

    /** 上次用的分卷大小（0 = 不分卷；i.txt 第 12 条：记住用户选择，密码不记）。 */
    fun splitSize(): Long = p().getLong(KEY_SPLIT_SIZE, 0L).coerceAtLeast(0L)

    fun setSplitSize(v: Long) = p().edit().putLong(KEY_SPLIT_SIZE, v.coerceAtLeast(0L)).apply()

    // ==================== 已安装应用页（i.txt 第 3 条）====================

    /** 默认命名表达式：主包固定叫 `<包名>-base.apk`。 */
    const val APK_NAME_TEMPLATE_DEFAULT = "{package}-base.apk"

    /** 默认部件（分包）命名：`<包名>-<原文件名>`，几个应用提到同一目录也不互撞。 */
    const val APK_PART_TEMPLATE_DEFAULT = "{package}-{name}"

    /** 排序方式：0=名称 1=安装时间 2=更新时间（默认 2，与 h.txt 第 3 条一致）。 */
    fun apkSortMode(): Int = p().getInt(KEY_APK_SORT, 2).coerceIn(0, 2)

    fun setApkSortMode(v: Int) = p().edit().putInt(KEY_APK_SORT, v.coerceIn(0, 2)).apply()

    /** 上次用的提取目录；空 = 没设置过，调用方用自己的默认值。 */
    fun apkExtractDest(): String = p().getString(KEY_APK_DEST, "").orEmpty()

    fun setApkExtractDest(v: String) = p().edit().putString(KEY_APK_DEST, v).apply()

    fun apkNameTemplate(): String =
        p().getString(KEY_APK_NAME_TEMPLATE, APK_NAME_TEMPLATE_DEFAULT)
            .orEmpty().ifBlank { APK_NAME_TEMPLATE_DEFAULT }

    fun setApkNameTemplate(v: String) =
        p().edit().putString(KEY_APK_NAME_TEMPLATE, v.ifBlank { APK_NAME_TEMPLATE_DEFAULT }).apply()

    fun apkPartTemplate(): String =
        p().getString(KEY_APK_PART_TEMPLATE, APK_PART_TEMPLATE_DEFAULT)
            .orEmpty().ifBlank { APK_PART_TEMPLATE_DEFAULT }

    fun setApkPartTemplate(v: String) =
        p().edit().putString(KEY_APK_PART_TEMPLATE, v.ifBlank { APK_PART_TEMPLATE_DEFAULT }).apply()

    /**
     * 反馈 e18 → f18：写回前留 `.bak` 的总开关。**默认开**。
     *
     * 现在覆盖两处：归档写回（[ZipWrite]）与文本编辑器保存（整读档 / 分页档都算）——
     * 「改之前先留一份」是同一个意图，摆两个开关只会让用户漏开一个。
     */
    fun archiveBackup(): Boolean = p().getBoolean(KEY_ARCHIVE_BACKUP, true)

    fun setArchiveBackup(v: Boolean) = p().edit().putBoolean(KEY_ARCHIVE_BACKUP, v).apply()

    /** 覆盖已有文件前先复制进回收站（反馈 e15 P0-1）。默认开 —— 没有后悔药是不可逆的。 */
    fun overwriteTrash(): Boolean = p().getBoolean(KEY_OVERWRITE_TRASH, true)

    fun setOverwriteTrash(v: Boolean) = p().edit().putBoolean(KEY_OVERWRITE_TRASH, v).apply()

    // ==================== 回收站总开关（h.txt 第 2 条） ====================

    /**
     * 回收站总开关，默认**开**（保持原有行为）。
     *
     * 关掉后删除确认框默认勾上「彻底删除」；但不会禁用回收站本身 ——
     * 里面已有的文件仍然能进去恢复，也不会改变「覆盖前备份」那一项。
     */
    fun recycleBinEnabled(): Boolean = p().getBoolean(KEY_RECYCLE_BIN, true)

    fun setRecycleBinEnabled(v: Boolean) = p().edit().putBoolean(KEY_RECYCLE_BIN, v).apply()

    // ==================== 解压（M4d 消费，先落值） ====================

    fun extractTarget(): String = pick(
        p().getString(KEY_EXTRACT_TARGET, TARGET_HERE),
        TARGET_HERE, TARGET_HERE, TARGET_OTHER, TARGET_FOLDER, TARGET_CUSTOM
    )

    fun setExtractTarget(v: String) = p().edit().putString(
        // 老版本存过 `beside`，白名单里已经没有它 → 自动回落「当前目录」，一次写入即自愈
        KEY_EXTRACT_TARGET, pick(v, TARGET_HERE, TARGET_HERE, TARGET_OTHER, TARGET_FOLDER, TARGET_CUSTOM)
    ).apply()

    fun customExtractTarget(): String = p().getString(KEY_CUSTOM_TARGET, "") ?: ""

    fun setCustomExtractTarget(v: String) = p().edit().putString(KEY_CUSTOM_TARGET, v.trim()).apply()

    fun restoreMeta(): Boolean = p().getBoolean(KEY_RESTORE_META, true)

    fun setRestoreMeta(v: Boolean) = p().edit().putBoolean(KEY_RESTORE_META, v).apply()

    /** 「解压后删除源文件」（用户反馈 c14）。默认关 —— 删原包不可逆，必须每次由用户勾。 */
    fun extractDeleteSource(): Boolean = p().getBoolean(KEY_EXTRACT_DELETE_SRC, false)

    fun setExtractDeleteSource(v: Boolean) =
        p().edit().putBoolean(KEY_EXTRACT_DELETE_SRC, v).apply()

    // ==================== 杂项 ====================

    /** 归档只读提示（N3：7z/tar 等只读格式置灰时给说明，开关关掉就只置灰不啰嗦）。 */
    fun archiveReadOnlyHint(): Boolean = p().getBoolean(KEY_ARCHIVE_RO_HINT, true)

    fun setArchiveReadOnlyHint(v: Boolean) = p().edit().putBoolean(KEY_ARCHIVE_RO_HINT, v).apply()

    // ==================== 缓存（4.16 落点 + N9 每天自动 + 手动口） ====================

    /** 分享/系统打开方式中转过来的复制件落点。 */
    fun filesCacheDir(): File = File(Constants.CACHE_DIR, "files")

    /**
     * 缓存占用字节数。
     *
     * g3：原先只量 `cache/files`，用户看到清完还有几百 K，以为「没清干净」。
     * 现在量的是整个工作目录下的 `cache/`（含 okhttp_cache / icons / disk_cache /
     * archive_open 等）。这些目录都是可再生的，删除不会丢用户数据。
     */
    fun cacheSizeBytes(): Long = dirSize(File(Constants.CACHE_DIR))

    /**
     * 清空整个工作缓存目录，返回释放的字节数（删除失败不抛，只记日志）。
     *
     * g3：原先只删 `cache/files`，okhttp 的响应缓存（`cache/okhttp_cache`）永远留着。
     * 现在：
     * 1. 先让共享 OkHttpClient 自己 `evictAll()`（正在运行的实例不会因为 journal 被
     *    外部删掉而在下次写入时报错）；
     * 2. 再把 `cache/` 下所有子目录清掉 —— 包括插件侧那份 okhttp_cache、图标缓存、
     *    归档临时解包等。
     *
     * 不删 `cache/` 目录本身：目录常驻，省得下次各模块再各自 mkdirs。
     */
    fun clearCache(): Long {
        val root = File(Constants.CACHE_DIR)
        val freed = dirSize(root)
        runCatching {
            com.UIN.Tool.core.di.ServiceLocator.getOkHttpClient().cache?.evictAll()
        }.onFailure { Logger.w(TAG, "okhttp evictAll failed: ${it.message}") }
        try {
            root.listFiles()?.forEach { child ->
                runCatching { child.deleteRecursively() }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "clearCache failed: ${e.message}")
        }
        return freed
    }

    private fun dirSize(dir: File): Long {
        if (!dir.isDirectory) return if (dir.isFile) dir.length() else 0L
        var total = 0L
        dir.listFiles()?.forEach { f ->
            total += if (f.isDirectory) dirSize(f) else f.length()
        }
        return total
    }

    // ==================== 导出 / 导入（N12：纳入统一配置） ====================

    /** 生成 `files` 段，供 UI 个性化的导出 JSON 合并。 */
    fun exportJson(): JSONObject = JSONObject().apply {
        put(KEY_START_DIR, startDir())
        put(KEY_DUAL_PANE, dualPane())
        put(KEY_PANE_RATIO, paneRatio())
        put(KEY_PANE_RATIO_MODE, paneRatioMode())
        put(KEY_SORT_KEY, sortKey())
        put(KEY_SORT_ASC, sortAscending())
        put(KEY_DIRS_FIRST, dirsFirst())
        put(KEY_SYS_HIDDEN, showSystemHidden())
        put(KEY_MAN_HIDDEN, showManualHidden())
        put(KEY_MANUAL_HIDDEN, JSONArray().apply { manualHidden().forEach { put(it) } })
        put(KEY_ROW_DENSITY, rowDensity())
        put(KEY_BACK_BEHAVIOR, backBehavior())
        put(KEY_REMEMBER_DIR, rememberLastDir())
        put(KEY_ZIP_FORMAT, zipFormat())
        put(KEY_ZIP_LEVEL, zipLevel())
        put(KEY_ZIP_ENCODING, zipEncoding())
        put(KEY_KEEP_META, keepMeta())
        put(KEY_EXTRACT_TARGET, extractTarget())
        put(KEY_CUSTOM_TARGET, customExtractTarget())
        put(KEY_RESTORE_META, restoreMeta())
        put(KEY_EXTRACT_DELETE_SRC, extractDeleteSource())
        put(KEY_ROOT_SCOPE, rootScope())
        put(KEY_ARCHIVE_RO_HINT, archiveReadOnlyHint())
        put(KEY_IMAGE_BG, imageViewerBg())
        put(KEY_SELECT_HINT, selectHintShown())
        put(KEY_REMOTE_FTP_PORT, remoteFtpPort())
        put(KEY_REMOTE_HTTP_PORT, remoteHttpPort())
        put(KEY_REMOTE_USER, remoteUser())
        put(KEY_REMOTE_PASS, remotePass())
        put(KEY_REMOTE_ROOT, remoteRoot())
    }

    /**
     * 读回 `files` 段。**逐键校验**：缺键沿用当前值，非法值落缺省——
     * 导入一份手改坏的配置不会把文件页搞崩。
     */
    fun importJson(json: JSONObject) {
        try {
            val e = p().edit()
            if (json.has(KEY_START_DIR)) e.putString(KEY_START_DIR, json.optString(KEY_START_DIR, "").trim())
            if (json.has(KEY_DUAL_PANE)) e.putBoolean(KEY_DUAL_PANE, json.optBoolean(KEY_DUAL_PANE, dualPane()))
            if (json.has(KEY_PANE_RATIO)) e.putString(KEY_PANE_RATIO, normalizePaneRatio(json.optInt(KEY_PANE_RATIO, 50)).toString())
            if (json.has(KEY_PANE_RATIO_MODE)) e.putString(KEY_PANE_RATIO_MODE, pick(json.optString(KEY_PANE_RATIO_MODE), RATIO_VARIABLE, RATIO_VARIABLE, RATIO_FIXED, RATIO_DEFAULT))
            if (json.has(KEY_SORT_KEY)) e.putString(KEY_SORT_KEY, pick(json.optString(KEY_SORT_KEY), SORT_NAME, SORT_NAME, SORT_SIZE, SORT_DATE, SORT_TYPE))
            if (json.has(KEY_SORT_ASC)) e.putBoolean(KEY_SORT_ASC, json.optBoolean(KEY_SORT_ASC, sortAscending()))
            if (json.has(KEY_DIRS_FIRST)) e.putBoolean(KEY_DIRS_FIRST, json.optBoolean(KEY_DIRS_FIRST, dirsFirst()))
            if (json.has(KEY_SYS_HIDDEN)) e.putBoolean(KEY_SYS_HIDDEN, json.optBoolean(KEY_SYS_HIDDEN, showSystemHidden()))
            if (json.has(KEY_MAN_HIDDEN)) e.putBoolean(KEY_MAN_HIDDEN, json.optBoolean(KEY_MAN_HIDDEN, showManualHidden()))
            val hidden = json.optJSONArray(KEY_MANUAL_HIDDEN)
            if (hidden != null) {
                val set = HashSet<String>()
                for (i in 0 until hidden.length()) hidden.optString(i, "").takeIf { it.isNotBlank() }?.let(set::add)
                e.putStringSet(KEY_MANUAL_HIDDEN, set)
            }
            if (json.has(KEY_ROW_DENSITY)) e.putString(KEY_ROW_DENSITY, pick(json.optString(KEY_ROW_DENSITY), DENSITY_NORMAL, DENSITY_COMPACT, DENSITY_NORMAL, DENSITY_LOOSE))
            if (json.has(KEY_BACK_BEHAVIOR)) e.putString(KEY_BACK_BEHAVIOR, pick(json.optString(KEY_BACK_BEHAVIOR), BACK_GO_UP, BACK_GO_UP, BACK_EXIT))
            if (json.has(KEY_REMEMBER_DIR)) e.putBoolean(KEY_REMEMBER_DIR, json.optBoolean(KEY_REMEMBER_DIR, rememberLastDir()))
            if (json.has(KEY_ZIP_FORMAT)) e.putString(KEY_ZIP_FORMAT, pick(json.optString(KEY_ZIP_FORMAT), ".zip", *FORMAT_EXTS))
            if (json.has(KEY_ZIP_LEVEL)) e.putString(KEY_ZIP_LEVEL, pick(json.optString(KEY_ZIP_LEVEL), LEVEL_DEFAULT, LEVEL_FAST, LEVEL_DEFAULT, LEVEL_BEST))
            if (json.has(KEY_ZIP_ENCODING)) e.putString(KEY_ZIP_ENCODING, pick(json.optString(KEY_ZIP_ENCODING), ENCODING_UTF8, ENCODING_UTF8, ENCODING_GBK, ENCODING_BIG5,
        ENCODING_ISO88591, ENCODING_CP437))
            if (json.has(KEY_KEEP_META)) e.putBoolean(KEY_KEEP_META, json.optBoolean(KEY_KEEP_META, keepMeta()))
            if (json.has(KEY_EXTRACT_TARGET)) e.putString(KEY_EXTRACT_TARGET, pick(json.optString(KEY_EXTRACT_TARGET), TARGET_HERE, TARGET_HERE, TARGET_OTHER, TARGET_FOLDER, TARGET_CUSTOM))
            if (json.has(KEY_CUSTOM_TARGET)) e.putString(KEY_CUSTOM_TARGET, json.optString(KEY_CUSTOM_TARGET, "").trim())
            if (json.has(KEY_RESTORE_META)) e.putBoolean(KEY_RESTORE_META, json.optBoolean(KEY_RESTORE_META, restoreMeta()))
            if (json.has(KEY_EXTRACT_DELETE_SRC)) e.putBoolean(KEY_EXTRACT_DELETE_SRC, json.optBoolean(KEY_EXTRACT_DELETE_SRC, extractDeleteSource()))
            if (json.has(KEY_ROOT_SCOPE)) e.putString(KEY_ROOT_SCOPE, pick(json.optString(KEY_ROOT_SCOPE), ROOT_INTERNAL, ROOT_INTERNAL, ROOT_REAL))
            if (json.has(KEY_ARCHIVE_RO_HINT)) e.putBoolean(KEY_ARCHIVE_RO_HINT, json.optBoolean(KEY_ARCHIVE_RO_HINT, archiveReadOnlyHint()))
            if (json.has(KEY_SELECT_HINT)) e.putBoolean(KEY_SELECT_HINT, json.optBoolean(KEY_SELECT_HINT, selectHintShown()))
            if (json.has(KEY_IMAGE_BG)) e.putString(KEY_IMAGE_BG, pick(json.optString(KEY_IMAGE_BG), IMAGE_BG_DARK, IMAGE_BG_DARK, IMAGE_BG_LIGHT, IMAGE_BG_GRAY, IMAGE_BG_CHECKER))
            // 端口要夹在 1..65535 再落盘：直接 putInt 的话，手改坏的配置会在下次启动
            // 绑端口时才炸，那时用户已经不知道是哪份配置的问题了
            if (json.has(KEY_REMOTE_FTP_PORT)) e.putInt(KEY_REMOTE_FTP_PORT, json.optInt(KEY_REMOTE_FTP_PORT, DEFAULT_FTP_PORT).coerceIn(1, 65535))
            if (json.has(KEY_REMOTE_HTTP_PORT)) e.putInt(KEY_REMOTE_HTTP_PORT, json.optInt(KEY_REMOTE_HTTP_PORT, DEFAULT_HTTP_PORT).coerceIn(1, 65535))
            if (json.has(KEY_REMOTE_USER)) e.putString(KEY_REMOTE_USER, json.optString(KEY_REMOTE_USER))
            if (json.has(KEY_REMOTE_PASS)) e.putString(KEY_REMOTE_PASS, json.optString(KEY_REMOTE_PASS))
            if (json.has(KEY_REMOTE_ROOT)) e.putString(KEY_REMOTE_ROOT, json.optString(KEY_REMOTE_ROOT).trim())
            e.apply()
        } catch (ex: Exception) {
            Logger.w(TAG, "importJson failed: ${ex.message}")
        }
    }

    /** 枚举式取值：不在白名单里就用缺省，导入/深链都靠它挡脏数据。 */
    private fun pick(v: String?, def: String, vararg allowed: String): String =
        if (v != null && allowed.contains(v)) v else def
}
