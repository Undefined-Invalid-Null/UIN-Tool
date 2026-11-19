// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocViewerScreen.kt
package com.UIN.Tool.ui.screen.docs

import kotlin.math.max
import kotlin.math.roundToInt
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.UIN.Tool.log.Logger
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.components.unified.UnifiedLoadingIndicator
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.MarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 目录里**有子标题**的条目（下一个标题层级更深）。
 *
 * n.txt 第 5 条：这份集合同时用于「显示展开箭头」与「初始全部收拢」——
 * 两处必须是同一口径，否则会出现「有箭头但默认没收起」或「收起了却没有箭头」。
 */
private fun tocParentIds(toc: List<MarkdownRenderer.TocItem>): Set<String> = buildSet {
    toc.forEachIndexed { index, item ->
        val next = toc.getOrNull(index + 1)
        if (next != null && next.level > item.level) add(item.id)
    }
}

/**
 * 文档阅读页。
 *
 * 两种来源（见 [DocViewerActivity]）：
 * - **内置文档**：按 `doc_type` 去 assets 里取，语言与回退都交给 [DocLoader]；
 * - **磁盘上的 markdown**（`.md` 的内置打开方式）：按 [DocViewerActivity.EXTRA_PATH] 直接读文件，
 *   标题不给就用文件名。
 *
 * 两条路最后都落到 `MarkdownRenderer` —— 用户自己的笔记与内置文档必须是同一套排版，
 * 否则「打开方式里那个 Markdown 查看」看着就像另一个应用。
 *
 * l.txt 第 1 条：目录从正文里拿掉，改到标题栏最右侧的目录图标 → 右侧侧边栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocViewerScreen() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val filePath = activity?.intent?.getStringExtra(DocViewerActivity.EXTRA_PATH)
        ?.takeIf { it.isNotBlank() }
    val docType = activity?.intent?.getStringExtra("doc_type") ?: "help"
    val title = activity?.intent?.getStringExtra(DocViewerActivity.EXTRA_TITLE)
        ?: filePath?.let { File(it).name }
        ?: Str.get(R.string.docs)

    // x.txt 第 1 条（用户）：WebView 的 base URL 必须是**这份文档真正所在的目录**，
    // 否则正文里的相对链接（如 About → CHANGELOG.md）会被解析到 assets 根下，
    // 点开就是 `net::ERR_FILE_NOT_FOUND`。磁盘上的 .md 则用它自己的父目录。
    val baseUrl = remember(docType, filePath) {
        if (filePath == null) {
            DocLoader.baseUrl(DocLoader.fileNameFor(docType))
        } else {
            val parent = File(filePath).parent
            if (parent.isNullOrBlank()) "file:///android_asset/" else "file://$parent/"
        }
    }

    var content by remember { mutableStateOf("") }
    var html by remember { mutableStateOf("") }
    var toc by remember { mutableStateOf<List<MarkdownRenderer.TocItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showToc by remember { mutableStateOf(false) }
    // m.txt 第 2 条：目录里的子标题可以按层级收拢（父标题折叠后，其下所有子项一并隐藏）。
    var collapsedToc by remember { mutableStateOf<Set<String>>(emptySet()) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // p.txt 第 4 条（用户）：右侧滑条太细点不到。WebView 的原生滑条宽度跟着系统主题走
    // （通常 2~4dp），而且不可拖动。这里改为**自绘一条 14dp 的可拖动滑条**，常驻显示。
    var scrollY by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(docType, filePath) {
        isLoading = true
        if (filePath == null) {
            loadDocument(docType) { loadedContent ->
                content = loadedContent
                isLoading = false
            }
        } else {
            loadFile(filePath) { loadedContent ->
                content = loadedContent
                isLoading = false
            }
        }
    }

    // 渲染与目录提取都在后台线程：几百 KB 的 Markdown 解析放在组合线程会卡住首帧。
    // includeToc=false → 正文里不再出现目录（侧边栏独占目录）。
    LaunchedEffect(content) {
        if (content.isEmpty()) return@LaunchedEffect
        val rendered = withContext(Dispatchers.Default) {
            runCatching { MarkdownRenderer.render(content, includeToc = false) }.getOrNull()
        }
        if (rendered != null) {
            html = rendered.html
            toc = rendered.toc
        } else {
            html = MarkdownRenderer.toHtml(content)
            toc = emptyList()
        }
    }

    // n.txt 第 5 条：目录**默认全部收拢**。长文档一打开，侧栏先给出一屏章节总览，
    // 想看哪一章再点箭头展开；收拢状态只在 toc 重新装载时重置一次，用户之后的
    // 展开/收拢不会被重组覆盖。
    LaunchedEffect(toc) {
        if (toc.isNotEmpty()) collapsedToc = tocParentIds(toc)
    }

    // 侧边栏打开时，返回键先收侧边栏（不要直接退阅读页）。
    BackHandler(enabled = showToc) { showToc = false }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppColors.pageBackground(),
            topBar = {
                ManageTopAppBar(
                    titleText = title,
                    onBack = { activity?.finish() },
                    actions = {
                        // 标题栏最右侧的目录入口；没有标题时置灰（点了也没内容可跳）。
                        UnifiedIconButton(
                            icon = Icons.Default.FormatListBulleted,
                            onClick = { showToc = true },
                            contentDescription = Str.get(R.string.docs_toc),
                            tint = if (toc.isNotEmpty()) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                                },
                            enabled = toc.isNotEmpty(),
                        )
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading || html.isEmpty()) {
                    UnifiedLoadingIndicator(message = Str.get(R.string.loading_document))
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                // o.txt 第 6 条：右滑出现大片空白。WebView 默认按「宽视口」
                                // 排版（约 980 CSS px）再整体缩到屏幕宽，页面宽度与可视宽度
                                // 对不上，多出来的那截就是空白。开启 useWideViewPort 让
                                // 文档里的 width=device-width 生效，页面宽度 = 屏幕宽度；
                                // 再关掉横向滚动条与边缘过度滚动，彻底没有横向可滑的余量。
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                isHorizontalScrollBarEnabled = false
                                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                                // 用户反馈 d11：滑条**不自动淡出** —— 系统默认过几秒就消失，
                                // 想拖时只剩屏幕边缘那一点，「长」就是靠它常驻实现的。
                                isScrollbarFadingEnabled = false
                                // p.txt 第 4 条：原生滑条关掉，改用页面里自绘的粗滑条。
                                isVerticalScrollBarEnabled = false
                                // 部分 ROM 仍会按主题画一条细滑条，顺带把尺寸也调细
                                // （q.txt 第 3 条：与自绘滑条保持同一档 9dp）。
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    runCatching {
                                        setScrollBarSize((9 * resources.displayMetrics.density).toInt())
                                    }
                                }
                                // 同步滚动位置/内容高度给自绘滑条。
                                // `contentHeight` 是 **WebView** 的成员（View 上没有；而
                                // View.computeVerticalScrollRange 是 protected，跨类调不到），
                                // 所以这里先把形参收回 WebView 再取值。
                                setOnScrollChangeListener { v, _, y, _, _ ->
                                    val web = v as? WebView
                                    if (web != null) {
                                        scrollY = y
                                        contentHeight = web.contentHeight
                                        viewportHeight = web.height
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    // x.txt 第 1 条：文档之间的 .md 链接交给内置阅读页渲染，
                                    // 不让 WebView 把 markdown 源码当纯文本打开。
                                    @Suppress("DEPRECATION")
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        url: String?
                                    ): Boolean = DocLoader.openLinkedDoc(ctx, url)

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.let {
                                            scrollY = it.scrollY
                                            contentHeight = it.contentHeight
                                            viewportHeight = it.height
                                        }
                                    }
                                }
                                // 透明底：MarkdownRenderer 的 body/.container 已是 transparent，
                                // 透出 Scaffold 的 pageBackground()（渐变/深色随主题），
                                // 修复深色模式下 WebView 矩形块残留白底（原取色只读浅色 theme 区）。
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                tag = html
                                try {
                                    loadDataWithBaseURL(
                                        baseUrl, html,
                                        "text/html", "UTF-8", null
                                    )
                                } catch (e: Exception) {
                                    AppLog.e(
                                        "DocViewer",
                                        Str.get(R.string.failed_to_load_document_e_message, e.message),
                                        e
                                    )
                                    loadDataWithBaseURL(
                                        null,
                                        "<html><body><p>${
                                            Str.get(
                                                R.string.failed_to_load_document_e_message,
                                                e.message
                                            )
                                        }</p></body></html>",
                                        "text/html", "UTF-8", null
                                    )
                                }
                                webViewRef.value = this
                            }
                        },
                        update = { view ->
                            // 内容重渲染后（换文档 / 换语言）同步换页，不重建 WebView。
                            webViewRef.value = view
                            if (view.tag != html) {
                                view.tag = html
                                runCatching {
                                    view.loadDataWithBaseURL(
                                        baseUrl, html,
                                        "text/html", "UTF-8", null
                                    )
                                }
                            }
                        },
                        // 退出页面时释放 WebView，避免实例泄漏（与 ReleaseChangelog 一致）
                        onRelease = { webView ->
                            webViewRef.value = null
                            webView.stopLoading()
                            webView.destroy()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    // p.txt 第 4 条：自绘粗滑条 —— 14dp 宽、常驻、可拖动、点轨道可直接跳。
                    if (!isLoading && html.isNotEmpty()) {
                        DocScrollBar(
                            scrollY = scrollY,
                            contentHeight = contentHeight,
                            viewportHeight = viewportHeight,
                            onScrollTo = { target ->
                                webViewRef.value?.scrollTo(0, target)
                            },
                            onDragBy = { delta ->
                                webViewRef.value?.let { it.scrollTo(0, it.scrollY + delta) }
                            }
                        )
                    }
                }
            }
        }

        // ==================== 目录侧边栏 ====================
        // 遮罩与面板拆成两个 AnimatedVisibility：遮罩只淡入淡出，面板从右侧滑出。
        AnimatedVisibility(
            visible = showToc,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showToc = false }
            )
        }
        AnimatedVisibility(
            visible = showToc,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(animationSpec = tween(260)) { it },
            exit = slideOutHorizontally(animationSpec = tween(220)) { it }
        ) {
            Surface(
                color = AppColors.background(),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 360.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Str.get(R.string.docs_toc),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        UnifiedIconButton(
                            icon = Icons.Default.Close,
                            onClick = { showToc = false },
                            contentDescription = Str.get(R.string.close),
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    if (toc.isEmpty()) {
                        Text(
                            text = Str.get(R.string.docs_toc_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        // 哪些条目有子标题（下一个标题层级更深）→ 显示收拢箭头。
                        val parentIds = remember(toc) { tocParentIds(toc) }
                        // 折叠后仍应显示的条目：跳过所有「已折叠祖先」的后代。
                        val visibleToc = remember(toc, collapsedToc) {
                            val out = ArrayList<MarkdownRenderer.TocItem>(toc.size)
                            val collapsedLevels = ArrayList<Int>()
                            for (item in toc) {
                                while (collapsedLevels.isNotEmpty() &&
                                    collapsedLevels.last() >= item.level
                                ) {
                                    collapsedLevels.removeAt(collapsedLevels.lastIndex)
                                }
                                val hidden = collapsedLevels.isNotEmpty()
                                if (!hidden) out.add(item)
                                // 隐藏项本身不再入栈：它的后代已经由祖先折叠一并隐藏。
                                if (!hidden && item.id in collapsedToc) {
                                    collapsedLevels.add(item.level)
                                }
                            }
                            out
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(visibleToc, key = { it.id }) { item ->
                                val hasChildren = item.id in parentIds
                                val expanded = item.id !in collapsedToc
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // 层级用左缩进表达：h1 起步、每深一级多 14dp。
                                        .padding(start = (12 + (item.level - 1) * 14).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            // id 仅由 slugify 产生（单词/中文/连字符），
                                            // 仍经 JSONObject.quote 转义后再拼进 JS，避免任何
                                            // 意外字符破坏脚本。
                                            // evaluateJavascript 收的是**纯 JS 代码**，不能带
                                            // `javascript:` 前缀（那是 loadUrl 的写法）。
                                            val js = "(function(){var e=document.getElementById(" +
                                                JSONObject.quote(item.id) +
                                                ");if(e){e.scrollIntoView({behavior:'smooth',block:'start'});}})()"
                                            webViewRef.value?.evaluateJavascript(js, null)
                                            showToc = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasChildren) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    collapsedToc = if (expanded) {
                                                        collapsedToc + item.id
                                                    } else {
                                                        collapsedToc - item.id
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (expanded) {
                                                    Icons.Default.ExpandLess
                                                } else {
                                                    Icons.Default.ExpandMore
                                                },
                                                contentDescription = Str.get(
                                                    if (expanded) R.string.docs_toc_collapse
                                                    else R.string.docs_toc_expand
                                                ),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        // 无子项的叶子节点留同宽空位，标题左缘保持对齐。
                                        Spacer(modifier = Modifier.width(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (item.level <= 1) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (item.level <= 1) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// G-10：加载逻辑收口到 DocLoader（与 HelpScreen 共用同一语言判定与回退）
private suspend fun loadDocument(docType: String, onResult: (String) -> Unit) {
    val fileName = DocLoader.fileNameFor(docType)
    try {
        // assets 全量读 + markdown 解析都是 CPU/IO 重活，同文件的 loadFile 早就包了 IO，
        // 这里口径不一致（d20 横向排查）—— 一并挪过去
        val text = withContext(Dispatchers.IO) { DocLoader.load(fileName) }
        onResult(text)
    } catch (e: Exception) {
        AppLog.e("DocViewer", Str.get(R.string.failed_to_load_document_filename, fileName), e)
        onResult(DocLoader.errorText(e))
    }
}

/**
 * 读**磁盘上**的 markdown（`.md` 的内置打开方式）。
 *
 * 整段在 IO 线程读：`readText` 对大文件是实打实的一次全量读，
 * 放在主线程上会先卡掉一帧再把加载圈画出来。失败文案与内置文档共用 [DocLoader.errorText] ——
 * 同一个页面里两种来源报错长得不一样，用户会以为是两种病。
 */
private suspend fun loadFile(path: String, onResult: (String) -> Unit) {
    try {
        val text = withContext(Dispatchers.IO) { File(path).readText(Charsets.UTF_8) }
        onResult(text)
    } catch (e: Exception) {
        AppLog.e("DocViewer", "read $path failed", e)
        onResult(DocLoader.errorText(e))
    }
}
/**
 * p.txt 第 4 条（用户）：文档阅读页右侧的**粗滑条**。
 *
 * 原生 WebView 滑条只有 2~4dp，手指根本压不中；这里自绘一条 14dp 的常驻滑条，
 * 命中区再放宽到 28dp，并支持「拖动拇指」和「点轨道直接跳」两种操作。
 * 滚动位置始终以 WebView 为准（[onScrollTo]/[onDragBy] 回调回去），本组件只做映射，
 * 不维护第二份滚动状态 —— 否则页面里点链接跳锚点后两边会错位。
 */
@Composable
private fun BoxScope.DocScrollBar(
    scrollY: Int,
    contentHeight: Int,
    viewportHeight: Int,
    onScrollTo: (Int) -> Unit,
    onDragBy: (Int) -> Unit
) {
    if (viewportHeight <= 0 || contentHeight <= viewportHeight) return
    val density = LocalDensity.current
    // q.txt 第 3 条（用户）：滚动条稍微细一点 —— 14dp 在阅读页里显得抢眼，
    // 收到 9dp；触控热区仍保留 28dp，拖动与点轨道跳转的手感不变。
    val barWidth = 9.dp
    val touchWidth = 28.dp
    val minThumb = 56.dp
    val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1)
    var trackHeight by remember { mutableIntStateOf(0) }
    val trackPx = trackHeight.toFloat()
    val thumbHeightPx = if (trackPx <= 0f) 0f else with(density) {
        max(minThumb.toPx(), trackPx * viewportHeight / contentHeight.toFloat())
    }.coerceAtMost(trackPx)
    val thumbTopPx = if (trackPx > thumbHeightPx && thumbHeightPx > 0f) {
        (trackPx - thumbHeightPx) * (scrollY.toFloat() / maxScroll).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(touchWidth)
            .onSizeChanged { trackHeight = it.height }
            .pointerInput(trackPx, thumbHeightPx, maxScroll) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    if (trackPx > thumbHeightPx) {
                        val delta = dragAmount / (trackPx - thumbHeightPx) * maxScroll
                        onDragBy(delta.roundToInt())
                    }
                }
            }
            .pointerInput(trackPx, thumbHeightPx, maxScroll) {
                detectTapGestures { tap ->
                    if (trackPx > thumbHeightPx) {
                        val ratio = ((tap.y - thumbHeightPx / 2f) / (trackPx - thumbHeightPx))
                            .coerceIn(0f, 1f)
                        onScrollTo((ratio * maxScroll).roundToInt())
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .offset(y = with(density) { thumbTopPx.toDp() })
                .width(barWidth)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(barWidth / 2))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        )
    }
}
