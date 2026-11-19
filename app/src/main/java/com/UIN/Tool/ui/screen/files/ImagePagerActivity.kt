// app/src/main/java/com/UIN/Tool/ui/screen/files/ImagePagerActivity.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import coil3.request.addLastModifiedToFileCacheKey
import coil3.request.crossfade
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.Str
import java.io.File
import kotlin.math.abs

/**
 * 内置图片查看器（§4.6 / N7「图片查看器核心」）。
 *
 * 能力边界：Coil 解码 + 捏合缩放 + 目录内左右翻页 + EXIF 方向 + GIF/动图。
 * **不做网络图、不做相册扫描** —— 打开来源永远是文件页里那一个真实路径。
 *
 * ## 三个实现取舍（都查过 artifact，不是凭印象）
 *
 * 1. **不引任何缩放库，手势手写**。项目里有 `detectTransformGestures`，但它**无条件消费**，
 *    一旦挂上 `HorizontalPager` 就再也收不到横向拖动，「翻页」直接失效。手写才能做到
 *    「没放大时把横向拖让给翻页」—— 靠**是否消费**来分工，而不是靠优先级。
 * 2. **GIF 走 `AsyncImage`，不用 `rememberAsyncImagePainter`**。查过 coil-compose-core 3.6.3：
 *    `AsyncImagePainter` 的 class 常量池里**没有任何动画自刷新标识**，用它 GIF 只会停在第一帧。
 * 3. **ImageLoader 局部构造**，不挂 Application 级工厂 —— 图片查看是唯一需要动图解码器的地方，
 *    全局注册等于让整个 app 为它买单。
 *
 * ## 这一轮补的 Coil 能力（都逐个查过 3.6.3 的 API 页，不是照记忆写）
 *
 * - **`crossfade`**：Coil 3 已把它从 `ImageLoader.Builder` 挪到 `ImageRequest` 层，
 *   而且是 `coil3.request` 包下的**顶层扩展**，必须单独 import（写成 Builder 成员会编译错）。
 * - **`addLastModifiedToFileCacheKey(true)`**：同样是 `coil3.request` 的顶层扩展，挂在
 *   **loader** 上一次生效。文件管理器改图是常事，不带这个时间戳进缓存键的话，
 *   外部改完再打开看到的还是旧图 —— 这是文件管理器特有的坑，普通 App碰不到。
 * - **相邻页预取**：`ImageLoader.enqueue` 按**视口尺寸**解码。不给 `size()` 的话
 *   `sizeResolver` 默认 `SizeResolver.ORIGINAL`，等于原图进内存，4000×3000 两张就 OOM。
 * - **`onState` 报错**：解码失败原先是一片纯黑，用户以为是图本来就这么暗。
 *
 * ## 背景四档
 *
 * 黑 / 白 / 灰 / 棋盘，循环切换并持久化。**按用户口味手动切，不按图片内容自动判** ——
 * 自动判定会让翻页过程中底色跳来跳去，看图时最忌讳背景不稳定。
 * 棋盘是唯一能看出**透明区域**的一档（PNG 去背图、图标素材）。
 */
class ImagePagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startPath = intent.getStringExtra(EXTRA_PATH)
        if (startPath.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            UINToolTheme {
                ImagePagerRoot(
                    startPath = startPath,
                    siblings = intent.getStringArrayListExtra(EXTRA_SIBLINGS),
                    onExit = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        /** 同目录其它图片的绝对路径；调用方（文件页）已有现成清单，免得这里再扫一遍盘。 */
        const val EXTRA_SIBLINGS = "siblings"

        /**
         * 起查看器。[siblings] 只是**候选**，清单会按「此刻真实存在 + 图片后缀」重过滤一遍 ——
         * 文件页给的是某一时刻的快照，期间可能已被删除或改名。
         */
        fun start(context: Context, path: String, siblings: List<String>?) {
            context.startActivity(
                Intent(context, ImagePagerActivity::class.java).apply {
                    putExtra(EXTRA_PATH, path)
                    putStringArrayListExtra(
                        EXTRA_SIBLINGS,
                        ArrayList((siblings.orEmpty() + path).distinct())
                    )
                }
            )
        }
    }
}

/**
 * 图片后缀（§4.6 的「图片」分流依据）。
 *
 * m.txt 第 3 条起清单**只在 `assets/config/file_types.json` 里** —— 这里保留这个名字是为了
 * 让调用点读起来仍是「一套图片后缀」，实际值由注册表给出。
 */
val IMAGE_EXTENSIONS: Set<String> get() = FileTypes.IMAGE

/** 某个路径是不是图片文件（文件页与查看器共用同一套判定）。 */
fun isImagePath(path: String): Boolean = FileTypes.isImage(File(path).name)

// ==================== 背景四档 ====================

/** 底色。checker 档不走这里（它要画刷，见 [checkerBrush]）。 */
private fun bgFillColor(mode: String): Color = when (mode) {
    FilePrefs.IMAGE_BG_LIGHT -> Color.White
    FilePrefs.IMAGE_BG_GRAY -> Color(0xFF757575)
    else -> Color.Black
}

/**
 * 顶栏文字与图标颜色，跟着底色走。
 *
 * 灰档取 `0xFF757575`（Material Grey 600）配白字，对比度约 4.6:1 —— 刚过 WCAG AA 的 4.5 门。
 * 再浅一档白字就糊了，再深一档又和黑档分不出来。
 */
private fun bgContentColor(mode: String): Color = when (mode) {
    FilePrefs.IMAGE_BG_LIGHT, FilePrefs.IMAGE_BG_CHECKER -> Color.Black
    else -> Color.White
}

/**
 * 棋盘画刷：2×2 的小位图平铺（`TileMode.REPEAT`），只在 checker 档构造。
 *
 * 不用 `drawBehind` 逐格画 —— 1080×2340 配 16dp 格子是上千个 `drawRect`，
 * 每帧都重画一遍纯属浪费；一张 32×32 的位图交给 GPU 平铺是一次性的。
 */
private fun checkerBrush(density: Float): ShaderBrush {
    val cell = (CHECKER_CELL_DP * density).toInt().coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(0xFFFFFFFF.toInt())
    val paint = Paint().apply { color = 0xFFD0D0D0.toInt() }
    canvas.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), paint)
    canvas.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, paint)
    return ShaderBrush(
        BitmapShader(
            bmp,
            android.graphics.Shader.TileMode.REPEAT,
            android.graphics.Shader.TileMode.REPEAT
        )
    )
}

/** 棋盘格边长（dp）。16 是肉眼能看清「这是透明区」又不喧宾夺主的尺寸。 */
private const val CHECKER_CELL_DP = 16f

@Composable
private fun ImagePagerRoot(
    startPath: String,
    siblings: List<String>?,
    onExit: () -> Unit
) {
    // 快照 + 二次过滤：真目录里此刻还在的图片才进翻页序列
    val images = remember(startPath, siblings) {
        val pool = siblings.orEmpty().filter { s ->
            val f = File(s)
            f.isFile && isImagePath(s)
        }.map { File(it).absolutePath }.distinct()
        when {
            pool.isEmpty() -> listOf(startPath)
            startPath in pool -> pool
            else -> listOf(startPath) + pool
        }
    }
    val startIndex = images.indexOf(startPath).coerceAtLeast(0)

    val context = LocalContext.current
    val density = LocalDensity.current
    // 一个 loader 管整条翻页序列，别每翻一页重建一次
    val loader = remember {
        ImageLoader.Builder(context.applicationContext)
            .components { add(AnimatedImageDecoder.Factory()) }   // GIF / 动图
            // 文件页里改完图再回来看，必须是新图。时间戳进缓存键 = 文件一变就失效。
            // 挂 loader 而不是逐条 request：这里每页都要，等于全量生效。
            .addLastModifiedToFileCacheKey(true)
            .build()
    }

    // 背景档位：进页面读一次持久值，之后由按钮循环改写
    var bgMode by remember { mutableStateOf(FilePrefs.imageViewerBg()) }
    val contentColor = bgContentColor(bgMode)
    // 棋盘档需要画刷；其它档传 null，走纯色分支
    val checker = remember(density, bgMode) {
        if (bgMode == FilePrefs.IMAGE_BG_CHECKER) checkerBrush(density.density) else null
    }

    // 视口尺寸：给相邻页预取当**解码尺寸**（见 loader 上的说明）
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    val currentName = images.getOrNull(pagerState.currentPage)?.let { File(it).name }.orEmpty()

    // 相邻页预取：滑到之前先解好，横向翻页才不会白一下。
    // 只取前后各一张，再远的等当前页变了才排 —— 一次排一屏会把内存缓存冲掉。
    LaunchedEffect(pagerState.currentPage, viewport, images) {
        if (viewport == IntSize.Zero) return@LaunchedEffect
        val current = pagerState.currentPage
        for (step in intArrayOf(-1, 1)) {
            val target = images.getOrNull(current + step) ?: continue
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(File(target))
                    // 必须给尺寸：不给就是 sizeResolver 默认的 ORIGINAL，原图进内存
                    .size(viewport.width, viewport.height)
                    .build()
            )
        }
    }

    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (checker != null) Modifier.background(checker)
                else Modifier.background(bgFillColor(bgMode))
            )
            .onSizeChanged { viewport = it }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 按路径给 key：清单顺序变时（如过滤掉已删除项）仍能停在同一批图上
            key = { images[it] }
        ) { page ->
            ZoomableImage(
                path = images[page],
                imageLoader = loader,
                // 与预取同一个盒：快层的缓存键才对得上（用户反馈 d10）
                prefetchSize = viewport,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 顶栏：文件名 + 第 n/共 m 张，右侧一个背景切换按钮。
        // 用 Row + weight 而不是「居中文本 + 另放一个按钮」：文件名再长也只会被挤到中间，
        // 不会压到按钮底下。
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 24.dp, start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$currentName   " +
                    Str.get(R.string.image_viewer_index, pagerState.currentPage + 1, images.size),
                color = contentColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 48.dp)
            )
            UnifiedIconButton(
                onClick = {
                    bgMode = nextImageBg(bgMode)
                    FilePrefs.setImageViewerBg(bgMode)
                }
            ) {
                ContrastIcon(
                    tint = contentColor,
                    contentDescription = Str.get(R.string.image_viewer_bg_switch)
                )
            }
        }
    }
}

/** 四档循环：黑 → 白 → 灰 → 棋盘 → 黑。顺序按「用得最多的排前面」，黑是缺省也是终点。 */
private fun nextImageBg(current: String): String = when (current) {
    FilePrefs.IMAGE_BG_DARK -> FilePrefs.IMAGE_BG_LIGHT
    FilePrefs.IMAGE_BG_LIGHT -> FilePrefs.IMAGE_BG_GRAY
    FilePrefs.IMAGE_BG_GRAY -> FilePrefs.IMAGE_BG_CHECKER
    else -> FilePrefs.IMAGE_BG_DARK
}

/**
 * 「对比度」图标：左半填实的圆 + 外圈描边，就是通用的明暗对比符号。
 *
 * 不去 material-icons-extended 里取 `Contrast` —— 那个图标全仓一次都没用过，
 * 为一个按钮赌依赖里有没有不划算（本项目在 AndroidIDE 上编译，一轮构建很贵）。
 * 这里自己画，零依赖零风险，形状也更贴本 app 的线条感。
 */
@Composable
private fun ContrastIcon(
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(22.dp)
            .padding(1.dp)
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val box = Size(radius * 2f, radius * 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        // 左半（实心）。Android 的角度从 3 点方向顺时针起算：
        // 90° 是正下方，扫 180° 走 下→左→上，正好是左半边。
        drawArc(
            color = tint,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = topLeft,
            size = box
        )
        // 外圈：右半没有实心，靠这圈描边把圆补完整
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.16f)
        )
    }
}

/**
 * 单张图的缩放状态。
 *
 * 变换形式是 `graphicsLayer(scaleX/scaleY, translationX/translationY)`，缩放原点取**图层中心**
 * （Compose 默认 `TransformOrigin.Center`），所以下面的焦点换算都以视口中心为基准。
 */
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var dx by mutableFloatStateOf(0f)
        private set
    var dy by mutableFloatStateOf(0f)
        private set

    val isZoomed: Boolean get() = scale > 1.001f

    /**
     * 以 [focal] 为焦点缩放 —— 保证手指按住的那个点在缩放前后**停在屏幕上同一位置**。
     *
     * 推导：屏幕点 `s = c + (q - c) * scale + t`（c 为视口中心、q 为图层内点、t 为平移；
     * Compose 的 `graphicsLayer` 是**先缩放后平移**，translation 不受 scale 影响，所以 t 直接
     * 加在结果上）。令 `s` 不变、只把 scale 换成 newScale，解出新的 `t'`：
     * `t' = (s - c) - ((s - c) - t) * (newScale / scale)`
     *
     * **这里刻意不调 [clamp]**（用户反馈 c3「放大中心不对」）：夹取边界是按「内容填满视口」
     * 估出来的，一旦真夹上，`t` 就不再是让 `s` 保持不变的那个值 —— 焦点会从手指底下被拽走，
     * 看着就是「往屏幕中心缩」。焦点落在视口内时推导上根本夹不到（见 [clamp]），
     * 夹不到的改动等于没改动；落在视口外（信箱边、状态栏那一带）时宁可让它先出界，
     * 单指平移时 [panBy] 还会夹回来。缩到 1x 则必须回中，那一步不受焦点约束。
     */
    fun zoomBy(factor: Float, focal: Offset, viewport: IntSize) {
        if (viewport == IntSize.Zero) return
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = newScale / scale
        if (abs(ratio - 1f) < 1e-4f) return
        val cx = viewport.width / 2f
        val cy = viewport.height / 2f
        val fx = focal.x - cx
        val fy = focal.y - cy
        dx = fx - (fx - dx) * ratio
        dy = fy - (fy - dy) * ratio
        scale = newScale
        if (scale <= MIN_SCALE + 1e-3f) {
            dx = 0f
            dy = 0f
        }
    }

    fun panBy(delta: Offset, viewport: IntSize) {
        if (!isZoomed) return
        dx += delta.x
        dy += delta.y
        clamp(viewport)
    }

    /** 双击：在 1x 与 [DOUBLE_TAP_SCALE] 之间切换，放大时以点击处为焦点。 */
    fun toggleAt(focal: Offset, viewport: IntSize) {
        if (isZoomed) {
            scale = 1f
            dx = 0f
            dy = 0f
        } else {
            zoomBy(DOUBLE_TAP_SCALE, focal, viewport)
        }
    }

    fun reset() {
        scale = 1f
        dx = 0f
        dy = 0f
    }

    /**
     * 夹住平移量，让放大的内容不会被拖出屏幕。**只由 [panBy] 调用** —— 缩放不夹，理由见 [zoomBy]。
     *
     * **一处刻意的简化**：这里按「内容填满视口」估最大位移 `viewport * (scale-1) / 2`。
     * 真按 `ContentScale.Fit` 算的话，还得知道图片的原始宽高比（信箱边会比内容宽），
     * 而 `AsyncImage` 不外露这个尺寸。差别只在**图片比视口瘦**时允许多拖一点黑边出来，
     * 不影响缩放正确性，也不影响翻页 —— 先记在这里，真要抠再单独取 intrinsic 尺寸。
     *
     * 焦点在视口内时这个边界恰好**夹不到**：焦点缩放能到达的最大位移就是
     * `(视口半宽) * (scale - 1)`，与这里的 `maxX` 相等（两指中点永远落在视口内）。
     */
    private fun clamp(viewport: IntSize) {
        val maxX = viewport.width * (scale - 1f) / 2f
        val maxY = viewport.height * (scale - 1f) / 2f
        dx = dx.coerceIn(-maxX, maxX)
        dy = dy.coerceIn(-maxY, maxY)
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 8f
        const val DOUBLE_TAP_SCALE = 3f
    }
}

@Composable
private fun ZoomableImage(
    path: String,
    imageLoader: ImageLoader,
    /**
     * 与 [ImagePagerRoot] 的预取**同一个解码盒**（用户反馈 d10）。
     *
     * 两边尺寸必须一模一样：Coil 的内存缓存键里带尺寸，预取按视口解、页面按 4096² 解，
     * 键就永远对不上 —— 预取解出来的图没人认领，翻页照样现解，白屏照旧。
     */
    prefetchSize: IntSize = IntSize.Zero,
    modifier: Modifier = Modifier
) {
    val state = remember { ZoomState() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // 解码失败时给个明确交代。原先失败就是一片纯黑 —— 用户分不清「图这么暗」和「没读出来」。
    // d10 之后分两层，失败也要**两层都算失败**才作数：快层的小图解出来了、质量层 OOM 了，
    // 屏上明明有图，不该弹「加载失败」。
    var fastFailed by remember(path) { mutableStateOf(false) }
    var fullFailed by remember(path) { mutableStateOf(false) }
    // 必须在**组合上下文**里取：LocalContext.current 是 @Composable，
    // 写进下面 remember 的 lambda 里会编译错（回调不是组合上下文）。
    val context = LocalContext.current

    Box(
        modifier = modifier
            .onSizeChanged { viewport = it }
            // ① 双击切换倍率。detectTapGestures 只在「快速按下抬起、没有明显位移」时才消费，
            //    不会吃掉拖动/捏合，因此能与下面的缩放手势共存。
            .pointerInput(state) {
                detectTapGestures(onDoubleTap = { pos -> state.toggleAt(pos, viewport) })
            }
            // ② 捏合缩放 + 已放大时的单指平移。**未放大时一律不消费**，把横向拖让给翻页。
            //
            // 手指**按 id 认领**（用户反馈 c3「手势识别有问题、放大中心不对」）：
            //   1. 不认 id 的话，第三根手指（掌根、换手）一碰，span/focal 就当场跳一次 ——
            //      图像猛地窜一下，焦点跑到「新」的两根手指中间去；
            //   2. 两指 → 单指切换时，上一帧的基准是**两指中点**，拿它当上一帧的指针位置会把
            //      整张图拽走一大截 —— 捏完松开一根手指就偏，正是「放大中心不对」的由来。
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 本组手势认领的指针，最多两根：ids[0] 是先按下的那根
                    var ids = listOf(down.id)
                    var span = 0f
                    // 单指平移的上一帧位置，同样按 id 认
                    var panId: PointerId? = down.id
                    var panLast = down.position

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        // —— 先把「本组」的指针集合修好，再决定这一帧是捏合还是平移 ——
                        var mine = pressed.filter { p -> ids.any { it == p.id } }
                        if (mine.isEmpty()) {
                            // 认领的指针全抬了、但还有别的手指按着：就地重新起一组
                            ids = listOf(pressed[0].id)
                            span = 0f
                            panId = null
                            panLast = pressed[0].position
                        } else if (mine.size == 1 && ids.size > 1) {
                            // 捏合中抬起一根 → 转单指；基准下一帧再起（不能沿用两指中点）
                            ids = listOf(mine[0].id)
                            span = 0f
                        } else if (mine.size == 1 && ids.size == 1 && pressed.size > 1) {
                            // 单指时又按下一根 → 开始捏合
                            ids = listOf(ids[0], pressed.first { it.id != ids[0] }.id)
                        }
                        mine = pressed.filter { p -> ids.any { it == p.id } }

                        if (ids.size >= 2) {
                            // 两指 = 捏合。焦点取**这两根**指针的中点（与别的手指无关）
                            val pa = mine[0]
                            val pb = mine[1]
                            val newSpan = (pa.position - pb.position).getDistance()
                            val focal = (pa.position + pb.position) / 2f
                            if (span > 0f && newSpan > 0f) {
                                state.zoomBy(newSpan / span, focal, viewport)
                            }
                            span = newSpan
                            panId = null // 捏合期间不维护单指基准
                            event.changes.forEach { it.consume() }
                        } else {
                            val p = mine[0]
                            span = 0f
                            if (state.isZoomed) {
                                // 第一帧只记基准不平移：这一帧没有可比的上一帧
                                if (panId == p.id) {
                                    state.panBy(p.position - panLast, viewport)
                                }
                                event.changes.forEach { it.consume() }
                                panId = p.id
                                panLast = p.position
                            } else {
                                panId = null
                            }
                        }
                    }
                }
            }
    ) {
        // crossfade 必须建在 ImageRequest 上：Coil 3 已把它从 ImageLoader.Builder 挪下来，
        // 且是 coil3.request 的顶层扩展（import 见文件头，写成成员会编译错）。
        val request = remember(path) {
            ImageRequest.Builder(context = context)
                .data(File(path))
                // 用户反馈③「放大后图片模糊」—— 根子在解码尺寸，不在缩放算法。
                //
                // 反编译 coil-compose-core 3.6.3 查到的链路（不是照记忆写的）：
                //   `UtilsKt.requestOfWithSizeResolver` 只在请求**没定义** sizeResolver 时
                //   才 `rememberSizeResolver(contentScale)`；而 contentScale = Fit（≠ None）
                //   拿到的是 `ConstraintsSizeResolver` —— **按布局约束（视口那么大）解码**。
                //   `AsyncImagePainter.updateRequest` 同样只在 `defined.sizeResolver == null`
                //   时才补默认值，所以自己给的 size 会被原样尊重。
                //
                // 结果就是：1080 宽的视口解出 1080×810 的位图，再靠 graphicsLayer 拉到
                // MAX_SCALE = 8 倍 —— 8 倍插值糊是必然的，跟源图多清晰无关。
                //
                // 这里显式给一个 4096² 的**上限盒**：Coil 只缩不放，小于它的原图按原尺寸
                // 解码（12MP 照片正好落在里面），大于它的才降采样。4096² ≈ 50MB ARGB，
                // 与 largeHeap 相称；再往上走不是「看清照片」，是「赌设备内存」。
                .size(4096, 4096)
                .crossfade(CROSSFADE_MS)
                .build()
        }

        // 用户反馈 d10：**快层** —— 按视口解码，与 ImagePagerRoot 的预取同尺寸同键，
        // 滑过来时缓存里已经有了，瞬间出图。crossfade 必须关掉：淡入是「从透明开始」，
        // 底下是背景就等于又白一下。
        // 视口还没量到（初值就是 Zero）时**不许建请求**：Coil 的 .size(0, 0) 会当场抛
        // 「px must be > 0」。原先守卫写在下面的 showFast 里，可 remember 在它**之前**
        // 就已执行 —— 第一帧必崩（onSizeChanged 要等布局，晚于组合）。
        val fastRequest = remember(path, prefetchSize) {
            if (prefetchSize == IntSize.Zero) null
            else ImageRequest.Builder(context = context)
                .data(File(path))
                .size(prefetchSize.width, prefetchSize.height)
                // 与预取那条**逐字一致**：连 crossfade 都不设（Coil 默认无过渡，
                // 本文件的 loader 也没开），键里多一个字段就对不上，预取就白做了。
                .build()
        }
        // GIF 的两层各走一条时间轴，透明区会闪成两张图叠着 → 只留质量层。
        // 快层没出来（视口还没量到 / GIF）时，失败与否就只看质量层。
        val showFast = prefetchSize != IntSize.Zero && !path.endsWith(".gif", ignoreCase = true)
        val failed = if (showFast) fastFailed && fullFailed else fullFailed

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.dx
                    translationY = state.dy
                }
        ) {
            if (showFast) {
                AsyncImage(
                    model = fastRequest,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Fit,
                    // 用 onState 而不是 placeholder/error 两个参数：那两个属于另一组重载，
                    // 混着用会变成「两个候选都不适用」。onState 这组才带 transform。
                    onState = { st -> fastFailed = st is AsyncImagePainter.State.Error },
                    modifier = Modifier.fillMaxSize()
                )
            }
            // **质量层**盖在快层之上：它带 crossfade，但底下透出来的是快层的图，不是白板。
            AsyncImage(
                model = request,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                onState = { st -> fullFailed = st is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (failed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = Str.get(R.string.image_viewer_load_failed),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** 淡入时长。200ms 是「看得出有过渡」又不拖沓的下限，再长翻页就显粘。 */
private const val CROSSFADE_MS = 200
