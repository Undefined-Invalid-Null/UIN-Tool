// app/src/main/java/com/UIN/Tool/ui/screen/files/FontViewerActivity.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
// 这里**不能**写 androidx.compose.foundation.* —— 该包里有已废弃的 foundation.Text，
// 与 material3.Text 同名同参，两个通配一碰就变成 ambiguous reference。
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedSlider
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

/**
 * 内置字体查看器（6.3.0）。
 *
 * 三块内容，缺一不可：
 *  1. **试看** —— 可编辑文本 + 字号滑杆，直接拿这个字体渲染；
 *  2. **码位表** —— 按 Unicode 区块分页浏览，点单字看它的码位；
 *  3. **元信息** —— 从 sfnt 的 `name` 表里读字体族 / 样式 / 版本。
 *
 * 为什么不调系统 `Typeface.create` 的家族名：Android **不对外暴露**字体族名，
 * 只能自己解 `name` 表（纯二进制读取，见 [parseFontNames]）。
 *
 * 只管 ttf / otf / ttc / otc —— Android 底层 FreeType **不支持 woff / woff2**，
 * 硬接进来只会打开就报错，不如让它们走系统打开方式。
 */
class FontViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            UINToolTheme {
                FontViewerRoot(
                    path = path,
                    onExit = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"

        fun start(context: Context, path: String) {
            context.startActivity(
                Intent(context, FontViewerActivity::class.java).apply {
                    putExtra(EXTRA_PATH, path)
                }
            )
        }
    }
}

/**
 * 能被 Android FreeType 直接解出来的字体容器（woff/woff2 不在其中 ——
 * 它们在 `file_types.json` 里单独归到 `webfont`，不挂内置查看器）。
 *
 * m.txt 第 3 条起清单只在注册表里，这里只是为了调用点读起来方便。
 */
val FONT_EXTENSIONS: Set<String> get() = FileTypes.FONT

/** 某个路径是不是字体文件（文件页与查看器共用同一套判定）。 */
fun isFontPath(path: String): Boolean = FileTypes.isFont(File(path).name)

// ==================== 界面 ====================

/** 默认试看文案：汉字挑「永」——笔画全、最能看出字形结构；再配拉丁与数字。 */
/** 试看文字（用户反馈 d6）：中文 / 英文 / 数字各一段 —— 三样都常在这一个光标行里打出来。 */
private const val SAMPLE_TEXT = "你好世界 Hello World 0123456789"

private const val SIZE_MIN = 12f
private const val SIZE_MAX = 72f

/**
 * 网格滚动条三段（头/拇指/尾）的 `weight` 下限。
 *
 * `weight` 必须 > 0，而全部字形同屏时拇指占满、头尾为 0 —— 崩溃日志
 * `invalid weight; must be greater than zero`（FontScrollBar）就是这么来的。
 */
private const val MIN_SCROLL_WEIGHT = 0.001f

@Composable
private fun FontViewerRoot(path: String, onExit: () -> Unit) {
    val file = remember(path) { File(path) }
    // createFromFile 解不了就当成「打不开」，交给下面的失败态 —— 不要抛到 UI外面闪退。
    val typeface = remember(path) {
        runCatching { Typeface.createFromFile(file) }.getOrNull()
    }
    val names = remember(path) { parseFontNames(file) }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = file.name.ifEmpty { Str.get(R.string.font_viewer_title) },
                onBack = onExit
            )
        }
    ) { padding ->
        if (typeface == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UnifiedCaptionText(
                    text = Str.get(R.string.font_load_failed),
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }
        FontViewerBody(
            file = file,
            names = names,
            typeface = typeface,
            fontFamily = FontFamily(typeface),
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun FontViewerBody(
    file: File,
    names: FontNames,
    typeface: Typeface,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    // Toast 要 context；必须在组合作用域里读，塞进 doJump 这种普通函数里没问题，
    // 但取值本身要来自 LocalContext.current 而不是 Activity 强转。
    val context = LocalContext.current
    // 字号/示例文本只在「试看区」内部读写，这里只持有状态对象、**不读值**：
    // 否则拖动字号滑条会让本函数（连同下方一屏上百个码位格子）整体重组，
    // 正是用户反馈的「字体查看器拖字号/滑动卡顿」。
    val sizeSp = remember { mutableFloatStateOf(28f) }
    var blockIndex by remember { mutableStateOf(0) }
    var selectedCode by remember { mutableStateOf<Int?>(null) }
    var jumpInput by remember { mutableStateOf("") }
    // 用户反馈：「跳转到码位」复选框，默认不勾。勾上后输入按十六进制码位严格解析，
    // 不再退回「取第一个字符」—— 那个兜底在用户明确想输码位时会跳到完全无关的字。
    var jumpAsCodepoint by remember { mutableStateOf(false) }
    // 用户反馈：元信息可收拢。默认收起，第一眼只留字体族一行，把高度让给试看区。
    var metaExpanded by remember { mutableStateOf(false) }

    // 用户反馈「区块是不是有点少」：预设区块放进**可变**列表，跳到冷门码位时现场造块。
    val blocks = remember {
        mutableStateListOf<GlyphBlock>().apply { addAll(GLYPH_BLOCKS) }
    }
    val block = blocks[blockIndex]
    val gridState = rememberLazyGridState()

    // f12「字体查看器滑动卡顿」：码位网格一屏上百个字形，原先每个字形都是一个
    // Compose Text（各自排版一段 Paragraph + 字体回退查询）。快滚时这些排版
    // 就是帧预算的大头。改成**用原生 Paint 直接画**：一个 Paint 复用到底，
    // 每个格子只剩一次 drawText，不再走 Compose 文本排版。
    val density = androidx.compose.ui.platform.LocalDensity.current
    val glyphColor = AppColors.textPrimary().toArgb()
    val glyphPaint = remember(typeface, density, glyphColor) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { 17.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            color = glyphColor
        }
    }

    // 区块切换和滚动必须分两步：换块后 items 数量要先重组完，再去滚才不会越界。
    var pendingJump by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(pendingJump) {
        val target = pendingJump ?: return@LaunchedEffect
        if (target.first == blockIndex) {
            gridState.animateScrollToItem(target.second)
        }
        pendingJump = null
    }

    /**
     * 码位落在哪个区块；不在任何预设区块里就**造一个**。
     *
     * 界取它所在的 256 码位页（`0x??00..0x??FF`）—— 一页 256 格，翻起来不费劲，
     * 又正好是「一个码位页」这种天然粒度。原先这里是 `indexOfFirst` 返回 -1 就
     * 直接 return，等于无论输入什么都石沉大海，用户自然觉得「不支持码位」。
     */
    fun blockIndexOf(cp: Int): Int {
        val existing = blocks.indexOfFirst { cp in it.start..it.end }
        if (existing >= 0) return existing
        val pageStart = cp and 0xFF.inv()
        val pageEnd = (pageStart + 0xFF).coerceAtMost(0x10FFFF)
        blocks.add(GlyphBlock(R.string.font_block_custom, pageStart, pageEnd))
        return blocks.lastIndex
    }

    fun doJump() {
        val raw = jumpInput
        if (raw.isBlank()) return
        val cp = if (jumpAsCodepoint) parseStrictCodePoint(raw) else parseCodePoint(raw)
        if (cp == null) {
            // 原先这里是 `?: return`，静默吞掉 —— 敲什么都像没反应。
            AppToast.warning(context, Str.get(R.string.font_jump_invalid))
            return
        }
        val index = blockIndexOf(cp)
        blockIndex = index
        selectedCode = cp
        pendingJump = index to (cp - blocks[index].start)
        jumpInput = ""
    }

    Column(modifier.fillMaxSize()) {

        // ── ① 试看区（可收拢的元信息 + 试看文本 + 字号） ──
        // 用户反馈「下面的列表高度低一点，不然上面不好预览」：原先试看区限高 216dp、
        // 网格独占剩余全部高度。改成两块各 weight(1f) 平分 —— 列表矮下去，试看区长上来。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 收拢头：整行可点，右侧箭头给方向感
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { metaExpanded = !metaExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedCaptionText(
                    text = Str.get(R.string.font_meta_title),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (metaExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = AppColors.textSecondary(),
                    modifier = Modifier.size(18.dp)
                )
            }

            // 收起时只留字体族 —— 全藏掉的话第一眼什么都看不到
            if (names.family.isNotEmpty()) {
                MetaLine(Str.get(R.string.font_family), names.family)
            }
            if (metaExpanded) {
                if (names.subfamily.isNotEmpty()) {
                    MetaLine(Str.get(R.string.font_style), names.subfamily)
                }
                if (names.version.isNotEmpty()) {
                    MetaLine(Str.get(R.string.font_version), names.version)
                }
                MetaLine(Str.get(R.string.font_file_size), FileOps.formatSize(file.length()))
                if (names.copyright.isNotEmpty()) {
                    MetaLine(Str.get(R.string.font_meta_copyright), names.copyright)
                }
                if (names.manufacturer.isNotEmpty()) {
                    MetaLine(Str.get(R.string.font_meta_manufacturer), names.manufacturer)
                }
                if (names.designer.isNotEmpty()) {
                    MetaLine(Str.get(R.string.font_meta_designer), names.designer)
                }
                if (names.description.isNotEmpty()) {
                    MetaLine(Str.get(R.string.font_meta_description), names.description)
                }
                // 证书：字体这边对应的是 sfnt 的 DSIG 数字签名表。只报有无 ——
                // 真验签要拉证书链，那不是「看字体」该干的事。
                MetaLine(
                    label = Str.get(R.string.font_meta_signature),
                    value = Str.get(if (names.signed) R.string.font_signed_yes else R.string.font_signed_no)
                )
            }

            FontSampleField(
                fontFamily = fontFamily,
                sizeSp = sizeSp,
                modifier = Modifier.fillMaxWidth()
            )

        }

        // 字号滑条固定在试看区**底部、滚动区之外**（用户反馈 d6「滑条不能拖动」）：
        // 它原先压在可滚内容的最后一条 —— 既可能被滚出视野、想调字号先得往下翻，
        // 拖的时候又要与外层纵向滚动手势抢方向（稍微斜一点就被滚动抢走，滑块纹丝不动）。
        // 挪到滚动区外：永远露着，也没有第二个滚动容器跟它抢手势。
        // 读值也放在子组合里：拖动只重组这一行，不牵动下方码位网格。
        FontSizeSliderRow(sizeSp)

        // ── ② 码位跳转 + 当前选中的字 + 最右侧跳转按钮 ──
        // 用户反馈「最右侧新增跳转按钮」：原先只能靠键盘的「搜索」键触发，屏幕上
        // 找不到入口，用外接键盘/点按的人都不知道怎么跳。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnifiedTextField(
                value = jumpInput,
                onValueChange = { jumpInput = it },
                modifier = Modifier.weight(1f),
                placeholder = Str.get(R.string.font_jump_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doJump() })
            )
            val cp = selectedCode
            if (cp != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, AppColors.outline(), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String(Character.toChars(cp)),
                        fontFamily = fontFamily,
                        fontSize = 22.sp,
                        color = AppColors.textPrimary(),
                        maxLines = 1
                    )
                }
                Column {
                    UnifiedCaptionText(
                        text = Str.get(R.string.font_glyph) + " · " + Str.get(R.string.font_codepoint)
                    )
                    Text(
                        text = "U+" + cp.toString(16).uppercase().padStart(4, '0'),
                        fontSize = 12.sp,
                        color = AppColors.textPrimary(),
                        maxLines = 1
                    )
                }
            }
            UnifiedButton(
                text = Str.get(R.string.font_jump),
                onClick = { doJump() },
                variant = ButtonVariant.Outlined,
                size = ButtonSize.Small
            )
        }

        // ── ②′ 按码位跳转（用户反馈：下一行新增复选框，默认不勾） ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = jumpAsCodepoint,
                onCheckedChange = { jumpAsCodepoint = it }
            )
            UnifiedCaptionText(Str.get(R.string.font_jump_as_codepoint))
        }

        // ── ③ 区块切换（预设 + 现场造出来的自定义区块） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnifiedCaptionText(
                text = Str.get(R.string.font_block),
                modifier = Modifier.width(36.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                blocks.forEachIndexed { index, glyphBlock ->
                    val active = index == blockIndex
                    Text(
                        text = Str.get(glyphBlock.labelRes),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (active) AppColors.primary()
                                else AppColors.surfaceVariant(),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                blockIndex = index
                                selectedCode = null
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        color = if (active) AppColors.onPrimary()
                        else AppColors.onSurfaceVariant(),
                        maxLines = 1
                    )
                }
            }
        }

        // ── ④ 码位网格 + 滚动条 ──
        // 用户反馈「要加滑条方便查看」：自适应格子一屏几十上百个，没有滑条只能干翻。
        val count = block.end - block.start + 1
        // h.txt 第 14 条：字形串按需缓存 —— 原先每个格子每次重组都 `String(Character
        // .toChars(cp))` 新建一遍，快滚时一帧几十上百次分配。缓存随区块重置，只对真正
        // 看过的码位分配，切到大区块（CJK 两万字）也不会一次性预建整表。
        val glyphCache = remember(block) { arrayOfNulls<String>(count) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                state = gridState,
                // 右侧留出滚动条的位置，免得最后一列被拇指压住
                contentPadding = PaddingValues(end = 10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(count, key = { it }, contentType = { "glyph" }) { offset ->
                    val cp = block.start + offset
                    val glyph = glyphCache[offset]
                        ?: String(Character.toChars(cp)).also { glyphCache[offset] = it }
                    val active = selectedCode == cp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(1.5.dp)
                            // h5：背景是圆角，但点击涟漪的绘制范围是整个节点 —— 不先 clip
                            // 就会在方角处溢出（字形格上尤其明显：一格一亮，四个角都是方的）。
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (active) AppColors.primary().copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .clickable { selectedCode = cp },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            // 基线居中：ascent 是负值，减掉 (ascent+descent)/2 让字形视觉居中
                            val baseline = size.height / 2f -
                                (glyphPaint.ascent() + glyphPaint.descent()) / 2f
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    glyph, size.width / 2f, baseline, glyphPaint
                                )
                            }
                        }
                    }
                }
            }
            FontScrollBar(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // ── ⑤ 提示 ──
        UnifiedCaptionText(
            text = Str.get(R.string.font_no_glyph_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 试看输入框：内部持有示例文本，字号从共享的 [sizeSp] 读取。
 * 状态读在子组合里 —— 打字/拖动字号只重组本块，不会带着码位网格一起重组。
 */
@Composable
private fun FontSampleField(
    fontFamily: FontFamily,
    sizeSp: MutableFloatState,
    modifier: Modifier = Modifier
) {
    var sample by remember { mutableStateOf(SAMPLE_TEXT) }
    TextField(
        value = sample,
        onValueChange = { sample = it },
        modifier = modifier,
        textStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = sizeSp.floatValue.sp,
            color = AppColors.textPrimary()
        ),
        maxLines = 3,
        placeholder = {
            UnifiedCaptionText(Str.get(R.string.font_preview_hint))
        }
    )
}

/** 字号滑条：读值/写值都在本组合内，拖动只重组这一行。 */
@Composable
private fun FontSizeSliderRow(sizeSp: MutableFloatState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UnifiedCaptionText(
            text = Str.get(R.string.font_size),
            modifier = Modifier.width(40.dp)
        )
        UnifiedSlider(
            value = sizeSp.floatValue,
            onValueChange = { sizeSp.floatValue = it },
            valueRange = SIZE_MIN..SIZE_MAX,
            modifier = Modifier.weight(1f)
        )
        // sp 不入资源串：这是纯单位后缀，走翻译反而多此一举。
        Text(
            text = "${sizeSp.floatValue.toInt()}sp",
            fontSize = 12.sp,
            color = AppColors.textSecondary()
        )
    }
}

/**
 * 属性行：标签 + 值（用户反馈 d6）。
 *
 * - **值不再截断**：版权 / 厂商 / 描述动辄好几行，压成一（两）行等于没显示，
 *   剩下那半截还挂着省略号让人以为就这么多。整块本来就在可滚容器里，多几行撑不坏。
 * - **长按复制该值** —— 正是这条反馈要的「属性的长按复制」；点一下也复制：
 *   只给长按的话，轻点那一片就是个「点了没反应」的死区，正是历轮反馈一直在清的毛病。
 */
@Composable
private fun MetaLine(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copy = {
        clipboard.setText(AnnotatedString(value))
        AppToast.info(context, Str.get(R.string.copied_to_clipboard))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                enabled = value.isNotBlank(),
                onClick = copy,
                onLongClick = copy
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UnifiedCaptionText(
            text = label,
            modifier = Modifier.width(64.dp),
            color = AppColors.textSecondary()
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = AppColors.textPrimary()
        )
    }
}

// ==================== 码位跳转 ====================

/**
 * 解析跳转输入。
 *
 * `U+4E2D` / `u+4e2d` 一律按十六进制码位；没有前缀的先当**纯十六进制长串**（`4E2D`、`00A9`
 * 这种直接粘码位的习惯），再不行就取第一个字符的码点 —— 所以输入「中」也能跳到「中」。
 * 单个 `U` 之类长度不够的不会被误判成码位。
 */
private fun parseCodePoint(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val hex = when {
        text.length > 2 && (text[0] == 'U' || text[0] == 'u') && text[1] == '+' ->
            text.substring(2)
        text.length >= 4 && text.all { it.isHex() } -> text
        else -> return text.codePointAt(0)
    }
    val value = hex.toIntOrNull(16) ?: return null
    if (value < 0 || value > 0x10FFFF) return null
    return value
}

private fun Char.isHex(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * 「按码位跳转」勾上后的**严格**解析：必须是十六进制码位（可带 `U+` 前缀）。
 *
 * 与 [parseCodePoint] 的区别在于不猜 —— 勾了这个框，用户就是明确要输码位，
 * 所以「不是十六进制」要报错，而不是退回去取第一个字符（那会跳到完全无关的字）。
 */
private fun parseStrictCodePoint(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val hex = when {
        text.length > 2 && (text[0] == 'U' || text[0] == 'u') && text[1] == '+' ->
            text.substring(2)
        text.all { it.isHex() } -> text
        else -> return null
    }
    if (hex.isEmpty()) return null
    val value = hex.toIntOrNull(16) ?: return null
    if (value < 0 || value > 0x10FFFF) return null
    return value
}

// ==================== Unicode 区块 ====================

private data class GlyphBlock(val labelRes: Int, val start: Int, val end: Int)

/**
 * 预设区块。**不是全部** —— 跳到不在这里的码位时 [FontViewerBody] 里的 `blockIndexOf`
 * 会现场造一个以它所在的 256 码位页为界的新区块（原先 `indexOfFirst` 返回 -1 就直接
 * return，输入什么都石沉大海 —— 这正是用户反馈「跳转到似乎不支持码位」的根因）。
 *
 * 顺序按「最可能用到」排：拉丁打头，CJK 放后面 —— CJK 有两万多个字，混在前面会把
 * 常用区块挤出首屏。
 */
private val GLYPH_BLOCKS = listOf(
    GlyphBlock(R.string.font_block_latin, 0x0020, 0x007E),
    GlyphBlock(R.string.font_block_latin1, 0x00A0, 0x00FF),
    GlyphBlock(R.string.font_block_latin_ext, 0x0100, 0x017F),
    GlyphBlock(R.string.font_block_greek, 0x0370, 0x03FF),
    GlyphBlock(R.string.font_block_cyrillic, 0x0400, 0x04FF),
    GlyphBlock(R.string.font_block_punct, 0x2000, 0x206F),
    GlyphBlock(R.string.font_block_currency, 0x20A0, 0x20CF),
    GlyphBlock(R.string.font_block_letterlike, 0x2100, 0x214F),
    GlyphBlock(R.string.font_block_arrows, 0x2190, 0x21FF),
    GlyphBlock(R.string.font_block_math, 0x2200, 0x22FF),
    GlyphBlock(R.string.font_block_box, 0x2500, 0x257F),
    GlyphBlock(R.string.font_block_geometric, 0x25A0, 0x25FF),
    GlyphBlock(R.string.font_block_symbol, 0x2600, 0x26FF),
    GlyphBlock(R.string.font_block_cjk_punct, 0x3000, 0x303F),
    GlyphBlock(R.string.font_block_kana, 0x3040, 0x30FF),
    GlyphBlock(R.string.font_block_cjk, 0x4E00, 0x9FFF),
    GlyphBlock(R.string.font_block_ext_a, 0x3400, 0x4DBF),
    GlyphBlock(R.string.font_block_hangul, 0xAC00, 0xD7AF),
    GlyphBlock(R.string.font_block_fullwidth, 0xFF00, 0xFFEF),
    GlyphBlock(R.string.font_block_emoji, 0x1F300, 0x1F5FF)
)

// ==================== sfnt name 表解析 ====================

private data class FontNames(
    val family: String = "",
    val subfamily: String = "",
    val fullName: String = "",
    val version: String = "",
    val copyright: String = "",
    val manufacturer: String = "",
    val designer: String = "",
    val description: String = "",
    /**
     * sfnt 里有没有 `DSIG` 数字签名表。
     * 只报**存在与否** —— 真正验签要拉起证书链，那不是「看字体」该干的事。
     */
    val signed: Boolean = false
)

/** name 表里我们要的 nameID：0 版权 / 1 字体族 / 2 样式 / 4 全名 / 5 版本 / 8 出版商 / 9 设计师 / 10 描述。 */
private val NAME_IDS = setOf(0, 1, 2, 4, 5, 8, 9, 10)

private fun beU16(b: ByteArray, o: Int): Int =
    ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

private fun beU32(b: ByteArray, o: Int): Long =
    ((b[o].toInt() and 0xFF).toLong() shl 24) or
        ((b[o + 1].toInt() and 0xFF).toLong() shl 16) or
        ((b[o + 2].toInt() and 0xFF).toLong() shl 8) or
        (b[o + 3].toInt() and 0xFF).toLong()

/**
 * 读 sfnt 的 `name` 表，取字体族 / 样式 / 全名 / 版本。
 *
 * 结构：12 字节文件头 → `numTables` 条 16 字节表目录（tag/校验和/偏移/长度）→
 * `name` 表内 6 字节头 + 每条 12 字节记录（平台/编码/语言/nameID/长度/偏移）→
 * 紧跟其后的字符串池。
 *
 * 平台 3 是 Windows UTF-16BE，平台 1 是 Mac Roman。**Windows 记录覆盖 Mac 记录**，
 * 反之只在 Windows 完全缺失时兜底 —— 很多老字体只有 Mac 侧的名字。
 *
 * 任何一步越界都直接返回空（字体是可被篡改的外来文件，绝不能让它把 Activity 打崩）。
 */
private fun parseFontNames(file: File): FontNames = try {
    RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 12) return@use FontNames()

        val head = ByteArray(12)
        raf.readFully(head)
        // 头部第 4、5 字节是 numTables。真字体不会超过 40 张表，给到 256 已是极大冗余。
        val numTables = beU16(head, 4)
        if (numTables <= 0 || numTables > 256) return@use FontNames()

        var nameOffset = -1L
        var nameLength = 0L
        // 顺手在表目录里找 DSIG（0x44 0x53 0x49 0x47）—— 只记存在与否，不解析签名内容。
        var signed = false
        repeat(numTables) {
            val record = ByteArray(16)
            raf.readFully(record)
            // 表目录里没有名字字段，tag 是固定 4 字节 ASCII：6e 61 6d 65。
            if (record[0] == 0x6E.toByte() && record[1] == 0x61.toByte() &&
                record[2] == 0x6D.toByte() && record[3] == 0x65.toByte()
            ) {
                nameOffset = beU32(record, 8)
                nameLength = beU32(record, 12)
            }
            // DSIG 数字签名表：44 53 49 47。
            if (record[0] == 0x44.toByte() && record[1] == 0x53.toByte() &&
                record[2] == 0x49.toByte() && record[3] == 0x47.toByte()
            ) {
                signed = true
            }
        }

        if (nameOffset < 0 || nameLength < 6) return@use FontNames(signed = signed)
        if (nameOffset + nameLength > raf.length()) return@use FontNames(signed = signed)
        // 一个 name 表上千条记录也就几 KB；截到 64KB 足够，顺便兜住被写大的异常长度。
        val nameTable = ByteArray(nameLength.toInt().coerceAtMost(1 shl 16))
        raf.seek(nameOffset)
        raf.readFully(nameTable)
        parseNameTable(nameTable).copy(signed = signed)
    }
} catch (e: Throwable) {
    FontNames()
}

private fun parseNameTable(nb: ByteArray): FontNames {
    if (nb.size < 6) return FontNames()
    val count = beU16(nb, 2)
    val stringOffset = beU16(nb, 4)
    if (count <= 0 || count > 1024) return FontNames()

    var family = ""
    var subfamily = ""
    var fullName = ""
    var version = ""
    var copyright = ""
    var manufacturer = ""
    var designer = ""
    var description = ""

    repeat(count) { i ->
        val recordOffset = 6 + i * 12
        if (recordOffset + 12 > nb.size) return@repeat
        val platform = beU16(nb, recordOffset)
        val nameId = beU16(nb, recordOffset + 6)
        if (nameId !in NAME_IDS) return@repeat
        val length = beU16(nb, recordOffset + 8)
        val offset = beU16(nb, recordOffset + 10)
        val start = stringOffset + offset
        if (length == 0 || start < 0 || start + length > nb.size) return@repeat

        val decoded = when (platform) {
            // 平台 3（Windows）是 UTF-16BE；奇数长度的串解出来会带替换符，直接丢。
            3 -> if (length % 2 != 0) {
                return@repeat
            } else {
                String(nb, start, length, Charsets.UTF_16BE)
            }
            1 -> String(nb, start, length, Charsets.ISO_8859_1)
            else -> return@repeat
        }.trim()
        if (decoded.isEmpty()) return@repeat

        // Windows 记录始终覆盖，Mac 记录只在该字段还空着时才写。
        val overwrites = platform == 3
        when (nameId) {
            0 -> if (copyright.isEmpty() || overwrites) copyright = decoded
            1 -> if (family.isEmpty() || overwrites) family = decoded
            2 -> if (subfamily.isEmpty() || overwrites) subfamily = decoded
            4 -> if (fullName.isEmpty() || overwrites) fullName = decoded
            5 -> if (version.isEmpty() || overwrites) version = decoded
            8 -> if (manufacturer.isEmpty() || overwrites) manufacturer = decoded
            9 -> if (designer.isEmpty() || overwrites) designer = decoded
            10 -> if (description.isEmpty() || overwrites) description = decoded
        }
    }

    return FontNames(
        family = family.ifEmpty { fullName },
        subfamily = subfamily,
        fullName = fullName.ifEmpty { family },
        version = version,
        copyright = copyright,
        manufacturer = manufacturer,
        designer = designer,
        description = description
    )
}

// ==================== 滚动条 ====================

/**
 * 网格右侧的滚动条（用户反馈：加滑条方便查看）。
 *
 * 不量像素 —— 用 `weight` 把「头 / 拇指 / 尾」三段按比例分掉整列高度，既省一次测量，
 * 也天然跟着容器高度走。行数不足以滚动时整根不画，免得在短区块上挂一根死条。
 *
 * **e9「拖不动」的根因**：这根条原先**只画不给摸** —— 三段画得再准，一个指针处理
 * 都没有，手指按上去纹丝不动。现在补上与文件列表滚动条同一套手势（见下面的
 * `pointerInput`）：拖动期间每帧用 `dispatchRawDelta`（**非挂起**）直接推一发位移，
 * 不走 `scrollToItem` —— 那条路每次拖动都要起协程，快速拖会堆积协程、松手后还在追。
 *
 * 单独拎成一个 composable 而不是写在 [FontViewerBody] 里：`layoutInfo` 每帧都在变，
 * 放外层会让整个页面跟着滚一遍重组。
 */
/** 字体网格滚动条的几何量（derivedStateOf 的返回值，只在跨行时变化）。 */
private data class FontBarInfo(
    val total: Int,
    val head: Float,
    val thumb: Float,
    val tail: Float,
    val contentPx: Float,
    val trackPx: Float
)

@Composable
private fun FontScrollBar(state: LazyGridState, modifier: Modifier = Modifier) {
    // f12：与 hex 滑条同一个修法 —— 把 layoutInfo 的读取收进 derivedStateOf，
    // 手指滑动但没跨行时 BarInfo 不变，组合直接跳过，不再每帧重组整根条。
    val bar by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total <= 1 || visible.isEmpty()) return@derivedStateOf null
            val first = visible.first().index
            val shown = (visible.last().index - first + 1).coerceAtLeast(1)
            if (total <= shown) return@derivedStateOf null
            val viewportPx = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            if (viewportPx <= 0f) return@derivedStateOf null
            val thumb = (shown.toFloat() / total).coerceIn(0.05f, 1f)
            val head = (first.toFloat() / total).coerceIn(0f, 1f - thumb)
            val tail = (1f - head - thumb).coerceAtLeast(0f)
            val contentPx = viewportPx * total / shown
            val thumbPx = (viewportPx * shown / total)
                .coerceAtLeast(1f)
                .coerceAtMost(viewportPx)
            val trackPx = (viewportPx - thumbPx).coerceAtLeast(1f)
            FontBarInfo(total, head, thumb, tail, contentPx, trackPx)
        }
    }
    val b = bar ?: return
    // weight 必须 > 0：全部字形同屏时 thumb = 1、head = tail = 0
    val wHead = b.head.coerceAtLeast(MIN_SCROLL_WEIGHT)
    val wThumb = b.thumb.coerceAtLeast(MIN_SCROLL_WEIGHT)
    val wTail = b.tail.coerceAtLeast(MIN_SCROLL_WEIGHT)
    // m.txt 第 11 条：拖动改为**按位置的绝对跳转**，并只保留最后一次。
    //
    // 旧实现按 `contentPx / trackPx` 放大后逐帧 dispatchRawDelta —— 字形表动辄
    // 十万行，拖 1px 等于滚几千行，每一帧都把网格甩到完全不同的位置，看起来就是
    // 拖动时卡死。现在直接用手指在轨道上的位置换算目标下标：一次拖动最多一次
    // scrollToItem 在途，目标下标没变就跳过，松手后不会再有积压的滚动在追。
    val scope = rememberCoroutineScope()
    val barNow by rememberUpdatedState(b)
    val jumpJobRef = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val lastJumpIndex = remember { intArrayOf(-1) }

    fun jumpTo(y: Float, height: Float) {
        val info = barNow
        if (height <= 0f || info.total <= 0) return
        val track = (height - info.thumb * height).coerceAtLeast(1f)
        // 拇指中心跟手：手指在轨道上的比例 → 目标首行。
        val frac = ((y - info.thumb * height / 2f) / track).coerceIn(0f, 1f)
        val shown = (info.thumb * info.total).toInt().coerceAtLeast(1)
        val target = (frac * (info.total - shown)).toInt()
            .coerceIn(0, (info.total - 1).coerceAtLeast(0))
        if (target == lastJumpIndex[0]) return
        lastJumpIndex[0] = target
        jumpJobRef[0]?.cancel()
        jumpJobRef[0] = scope.launch { state.scrollToItem(target) }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(14.dp)
            .pointerInput(b.total) {
                detectDragGestures(
                    onDragStart = { jumpTo(it.y, size.height.toFloat()) },
                    onDrag = { change, _ ->
                        change.consume()
                        jumpTo(change.position.y, size.height.toFloat())
                    },
                    onDragEnd = { lastJumpIndex[0] = -1 },
                    onDragCancel = { lastJumpIndex[0] = -1 }
                )
            }
            .padding(vertical = 4.dp)
    ) {
        Spacer(modifier = Modifier.weight(wHead))
        Box(
            modifier = Modifier
                .weight(wThumb)
                .fillMaxWidth(0.45f)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.textHint().copy(alpha = 0.55f))
        )
        Spacer(modifier = Modifier.weight(wTail))
    }
}
