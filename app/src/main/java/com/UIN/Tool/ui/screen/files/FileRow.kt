// app/src/main/java/com/UIN/Tool/ui/screen/files/FileRow.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 行密度三档（FilePrefs → 4.17「行密度」），改完即时生效。 */
private fun densitySpec(density: String): Triple<Dp, TextUnit, TextUnit> = when (density) {
    FilePrefs.DENSITY_COMPACT -> Triple(44.dp, 14.sp, 10.sp)
    FilePrefs.DENSITY_LOOSE -> Triple(60.dp, 16.sp, 12.sp)
    else -> Triple(52.dp, 15.sp, 11.sp)
}

private val dateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

private fun dateText(millis: Long): String =
    if (millis <= 0L) "-" else try {
        dateFormat.get()!!.format(Date(millis))
    } catch (e: Exception) {
        "-"
    }

/**
 * 列表行：类型图标 + 名称 + 次要信息（大小 · 修改时间）。
 *
 * - 单击 = 打开（目录进、文件按类型分流，4.6）；长按 = 上下文菜单 + 该窗格变活动（4.3）；
 * - 高亮来自**选中集**，按窗格隔离，所以两格各选各的不会串；
 * - 多选态**只靠底色区分**，行首不再摆勾选框（用户反馈 c1）—— 底色已经把状态说清楚了，
 *   前面再塞一个图标只会把文件名往右挤掉一截，双窗格里那点横向空间尤其金贵；
 * - 读屏读「名称，大小，日期，目录/文件」，多选态再补「已选中」（5.5）。
 */
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    multiSelect: Boolean,
    density: String,
    /**
     * 缩略图用的 Coil 加载器，由 [FileListPane] **建一次后下发**。
     *
     * Coil 3 的 `AsyncImage` 没有默认 loader（不传就是编译错）；而每行各自建一个
     * 等于每行一份缓存，同一张图会反复回源，所以必须从上层共用一个。
     */
    thumbLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 落点高亮（4.5）：新建/重命名成功后给新位置一个**一次性**标记。
     *
     * 与 [selected] 分开画 —— 选中是主色，落点用次色。两者混成一个颜色的话，
     * 用户分不清「这是我选的」还是「这是我刚建出来的那个」。
     */
    highlight: Boolean = false,
    /**
     * 落点描边脉冲的**当前不透明度**（4.16）。
     *
     * 取值 0f..1f，动画本身由 `FileListPane` 用**唯一一条**无限动画驱动后下发，
     * 行内不再各开一条（十几行各跑一个永不停歇的动画是纯浪费）。
     * 非落点行拿 0f —— 配合 [highlight] 为 false 时整个 border 不会被构造，零成本。
     */
    highlightPulse: Float = 0f,
    /**
     * 文件名折行显示（用户反馈⑬「多行显示文件/目录名」）。
     *
     * 关 = 单行 + 省略号（默认，行高固定，列表更紧凑）；
     * 开 = 按内容换行，行高随之**撑开** —— 两个必须一起改，
     * 只改 [androidx.compose.ui.text.style.TextOverflow] 那一边会被 `.height()` 裁掉第二行。
     */
    multiLine: Boolean = false
) {
    val (rowHeight, nameSize, secondarySize) = remember(density) { densitySpec(density) }
    // j.txt 第 11 条：这四样在滚动/选择变化时会被反复重算，但输入其实只有条目本身。
    // 缓存住日期格式化、大小串与读屏文案 —— 大目录快速滑动时省掉每帧几十次字符串拼接。
    val typeText = remember(entry.isDir) {
        if (entry.isDir) Str.get(R.string.files_type_dir) else Str.get(R.string.files_type_file)
    }
    // 目录默认给 "-"（真实目录不扫盘、size 恒 0）；**归档里的目录行带着聚合大小**
    // （e15 P2-18），有数就报数 —— 判据是 size>0 而不是 isDir，否则包内子文件夹
    // 永远显示 "-"。空文件不受影响：文件仍走 formatFileSize(0) = 「0 B」。
    val sizeText = remember(entry.isDir, entry.size) {
        when {
            entry.isDir && entry.size > 0L -> formatFileSize(entry.size)
            entry.isDir -> "-"
            else -> formatFileSize(entry.size)
        }
    }
    val date = remember(entry.modified) { dateText(entry.modified) }
    // 读屏一次性播报「名称，大小，日期，目录/文件」（5.5）。多选态补一句「已选中」——
    // 行首的勾选框已按用户反馈 c1 去掉，底色是给眼睛看的，读屏只能靠这一句。
    val rowDescription = remember(entry.name, sizeText, date, typeText) {
        Str.get(R.string.files_row_desc, entry.name, sizeText, date, typeText)
    }
    // n.txt 第 6 条：类型图标 + 后缀角标统一由 [FileTypeIcon] 画（数据仍来自
    // `assets/config/file_types.json`，这里不再各自取 icon/tint）。
    val spoken = remember(rowDescription, multiSelect, selected) {
        if (multiSelect && selected) Str.get(R.string.files_row_desc_sel, rowDescription)
        else rowDescription
    }

    // i.txt 第 7 条「美化多选的横向滑动动画」：选中底色不再硬切 —— 手指扫过时
    // 每一行用 120ms 淡入，扫过去是一条颜色渐次亮起的带子，而不是一排瞬间闪白。
    // 只给颜色做动画：行高/图标/文字都不动，滚动时不会额外排版。
    val targetBg = when {
        highlight -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
        // g9：0.14 在部分目录行上几乎看不出（用户反馈「选了却没高亮」）。
        // 提到 0.20 后，深浅主题下都能一眼分辨，同时不抢落点高亮的次色。
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(targetBg, tween(120), label = "fileRowSelect")

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 多行模式下高度必须**能撑开**，否则折出来的第二行会被裁掉；
            // 单行模式仍用固定 .height()，保持三种密度的行高严格一致。
            .then(
                if (multiLine) Modifier.heightIn(min = rowHeight)
                else Modifier.height(rowHeight)
            )
            .background(bg)
            // 4.16：落点的「主题色描边脉冲」。非落点行走 else 分支拿到的是空 Modifier，
            // 连 border 都不会构造 —— 只有真正高亮的那一行才有这笔开销。
            .then(
                if (highlight) Modifier.border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = highlightPulse),
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            // k.txt 第 6 条：行是整宽矩形，涟漪不裁切会铺到屏幕边缘并露出尖角；
            // 8dp 圆角裁切后水波只在行内圆角范围内扩散。
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 用户反馈⑧：图片在列表里直接出**缩略图**，不再是一个泛泛的「图片」图标。
        // 归档内是虚拟路径（`x.zip!/a.png`），拿去喂 Coil 只会得到一个不存在的 File ——
        // 直接跳过，省掉一串注定失败的请求。
        val thumbPath = entry.path.takeIf {
            !entry.isDir && !VirtualPath.isInArchive(it) && isImagePath(it)
        }
        val iconSize = if (rowHeight < 48.dp) 20.dp else 24.dp
        if (thumbPath != null) {
            // 底层先画类型图标当 fallback：Coil 还在请求、解码失败、或这张图压根读不出来
            // （没权限/已删除）时它露出来 —— 行永远有可辨识的占位，不会空出一块白。
            //
            // 用户反馈 d3：**图一出来就把占位收起** —— 透明 PNG/WebP 会把底下的图标
            // 透出来，看着像「缩略图里印着一个相册图标」。占位只在还没图的时候才该露脸；
            // 掉到 Error 时这里会跟着变回 false，图标自动回来接着兜底。
            var thumbLoaded by remember(thumbPath) { mutableStateOf(false) }
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbLoaded) {
                    FileTypeIcon(
                        fileName = entry.name,
                        isDir = entry.isDir,
                        size = iconSize
                    )
                }
                AsyncImage(
                    model = File(thumbPath),
                    contentDescription = null,
                    imageLoader = thumbLoader,
                    contentScale = ContentScale.Crop,
                    onState = { st -> thumbLoaded = st is AsyncImagePainter.State.Success },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(5.dp))
                )
            }
        } else {
            FileTypeIcon(
                fileName = entry.name,
                isDir = entry.isDir,
                size = iconSize
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = nameSize,
                fontWeight = FontWeight.Medium,
                // 多行模式不设上限也不给省略号：文件名最长受 FS 255 字节约束，
                // 全折出来也就几行，不会拖成一条巨高行；单行模式靠省略号兜住。
                maxLines = if (multiLine) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (entry.isDir) date else "$sizeText · $date",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = secondarySize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
