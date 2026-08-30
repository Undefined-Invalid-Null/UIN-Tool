package com.UIN.Tool.ui.screen.manage

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.UIConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.Fill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextDecoration

class RoundedStarShape(
    private val points: Int = 5,
    private val innerRadiusRatio: Float = 0.4f,
    private val cornerRadius: Float = 0.3f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val outerRadius = minOf(size.width, size.height) / 2f
        val innerRadius = outerRadius * innerRadiusRatio
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val angleStep = PI / points
        val totalVertices = points * 2
        val r = cornerRadius * outerRadius

        val vertices = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until totalVertices) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = i * angleStep - PI / 2
            vertices.add(
                centerX + radius * cos(angle).toFloat() to
                centerY + radius * sin(angle).toFloat()
            )
        }

        for (i in 0 until totalVertices) {
            val (x, y) = vertices[i]
            val (prevX, prevY) = vertices[(i - 1 + totalVertices) % totalVertices]
            val (nextX, nextY) = vertices[(i + 1) % totalVertices]

            val toPrevX = prevX - x
            val toPrevY = prevY - y
            val toNextX = nextX - x
            val toNextY = nextY - y
            val toPrevLen = sqrt(toPrevX * toPrevX + toPrevY * toPrevY)
            val toNextLen = sqrt(toNextX * toNextX + toNextY * toNextY)

            val cp1x = x + toPrevX / toPrevLen * r
            val cp1y = y + toPrevY / toPrevLen * r
            val cp2x = x + toNextX / toNextLen * r
            val cp2y = y + toNextY / toNextLen * r

            if (i == 0) {
                path.moveTo(cp1x, cp1y)
            } else {
                path.lineTo(cp1x, cp1y)
            }
            path.quadraticBezierTo(x, y, cp2x, cp2y)
        }

        path.close()
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIConfigPreviewScreen(
    onBack: () -> Unit
) {
    val neuIntensity = NeuDefaults.currentIntensity()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var selectedDrawerItem by remember { mutableStateOf("preview") }

    val drawerItems = listOf(
        Triple("preview", "T", "Tools"),
        Triple("repo", "R", "Repository"),
        Triple("manage", "M", "Manage"),
        Triple("backup", "B", "Backup"),
        Triple("log", "L", "Log Viewer"),
        Triple("dev", "D", "Dev Tools"),
        Triple("settings", "S", "Settings"),
        Triple("help", "?", "Help")
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (UIConfig.shouldUseDarkTheme()) Color(0xFF1E1E22) else Color.White
            val textPrimary = if (UIConfig.shouldUseDarkTheme()) Color(0xFFD0D0D0) else Color(0xFF333333)
            val primaryLight = Color(0xFF1A3A4A)
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = surfaceColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(diagonalGradient(listOf(Color(0xFF1A3A4A), primaryLight)))
                        .padding(28.dp)
                ) {
                    Column {
                        Text("UIN Tool", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("v5.6.0 Menu", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                drawerItems.forEachIndexed { index, (key, icon, label) ->
                    if (index == 3 || index == 6) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = if (UIConfig.shouldUseDarkTheme()) Color(0xFF444444) else Color(0xFFE0E0E0)
                        )
                    }
                    DrawerItem(
                        icon = icon,
                        label = label,
                        isSelected = selectedDrawerItem == key,
                        surfaceColor = surfaceColor,
                        textPrimary = textPrimary,
                        primaryLight = primaryLight
                    ) {
                        selectedDrawerItem = key
                        drawerScope.launch { drawerState.close() }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("新拟态预览", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            drawerScope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        }) {
                            Icon(Icons.Default.Menu, "菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (UIConfig.shouldUseDarkTheme()) Color(0xFF1E1E22) else Color.White,
                        navigationIconContentColor = if (UIConfig.shouldUseDarkTheme()) Color(0xFFD0D0D0) else Color(0xFF333333)
                    )
                )
            }
        ) { padding ->
            UIConfigPreviewContent(
                neuIntensity = neuIntensity,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIConfigPreviewContent(
    neuIntensity: NeuDefaults.Intensity,
    modifier: Modifier = Modifier
) {
    val isDark = UIConfig.shouldUseDarkTheme()
    val bgColor = if (isDark) Color(0xFF18181A) else Color(0xFFF5F7FA)
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666)
    val textTertiary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF888888)
    val primaryColor = Color(0xFF1A3A4A)
    val primaryLight = Color(0xFF1A3A4A)
    val successColor = Color(0xFF27AE60)
    val warningColor = Color(0xFFF59E0B)
    val errorColor = Color(0xFFE74C3C)
    val infoColor = Color(0xFF3B82F6)
    val shadowDark = if (isDark) Color(0xFF0A0A0A) else Color(0xFF1E1E1E)
    val shadowLight = if (isDark) Color(0xFF3A3A3A) else Color.White
    val glowColor = if (isDark) Color(0x1A4A8A9E) else Color(0x2E4A8A9E)

    val context = LocalContext.current

    Box(modifier = modifier.fillMaxWidth().background(bgColor)) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ==================== Header ====================
        Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            diagonalGradient(listOf(primaryColor, primaryLight))
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("UIN Tool 5.6.0", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("v14: 所有UI组件优化", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }

            // ==================== 1. 按钮 ====================
            SectionHeader("按钮", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButtonDemo("主要", primaryColor, primaryLight, Color.White, neuIntensity, isDark, Modifier.weight(1f)) { }
                    NeuButtonDemo("次要", surfaceColor.copy(alpha = 0.9f), surfaceColor.copy(alpha = 0.7f), textPrimary, neuIntensity, isDark, Modifier.weight(1f)) { }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButtonDemo("线框", primaryLight, textPrimary, Modifier.weight(1f)) { }
                    TextButtonDemo("文本", primaryLight, Modifier.weight(1f)) { }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButtonDemo("删除", Color(0xFFC0392B), errorColor, Color.White, neuIntensity, isDark, Modifier.weight(1f)) { }
                    NeuButtonDemo("确认", successColor, Color(0xFF2ECC71), Color.White, neuIntensity, isDark, Modifier.weight(1f)) { }
                }
            }

            // ==================== 2. 新拟态卡片 ====================
            SectionHeader("新拟态卡片", textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PluginCardDemo(
                    icon = "🧩", name = "WebView Plugin v2.1",
                    chips = listOf("活跃" to successColor, "v2.1" to primaryLight),
                    isDark = isDark, intensity = neuIntensity,
                    modifier = Modifier.weight(1f)
                )
                PluginCardDemo(
                    icon = "💻", name = "Native Plugin v1.0",
                    chips = listOf("非活跃" to textTertiary, "v1.0" to primaryLight),
                    isDark = isDark, intensity = neuIntensity,
                    modifier = Modifier.weight(1f)
                )
            }

            // ==================== 3. 页面切换 ====================
            SectionHeader("页面切换", textPrimary)
            var currentTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("标签 A", "标签 B", "标签 C")
            val pageCards = listOf(
                listOf("WebView 插件" to "浏览插件", "Native 插件" to "本地插件"),
                listOf("备份管理器" to "备份数据", "日志查看器" to "查看日志"),
                listOf("UI 配置" to "自定义界面", "权限管理" to "管理权限")
            )

            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { index, label ->
                        val isActive = currentTab == index
                        var isTabPressed by remember { mutableStateOf(false) }
                        val tabScale by animateFloatAsState(
                            targetValue = if (isTabPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "tab_scale"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = tabScale; scaleY = tabScale }
                                .then(if (isActive) Modifier.neuInset(RoundedCornerShape(10.dp), isDark, neuIntensity, backgroundColor = Color.Transparent) else Modifier.neuRaised(RoundedCornerShape(10.dp), isDark, neuIntensity, backgroundColor = surfaceColor))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isActive) diagonalGradient(listOf(primaryColor, primaryLight))
                                    else Brush.linearGradient(listOf(surfaceColor, surfaceColor))
                                )
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        isTabPressed = true
                                        val up = waitForUpOrCancellation()
                                        isTabPressed = false
                                    }
                                }
                                .clickable { currentTab = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isActive) Color.White else textPrimary, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.animation.AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        slideInHorizontally { if (targetState > initialState) it else -it } + fadeIn() togetherWith
                        slideOutHorizontally { if (targetState > initialState) -it else it } + fadeOut()
                    },
                    label = "page_switch"
                ) { page ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pageCards[page].forEach { (title, subtitle) ->
                            MiniCardDemo(title, subtitle, isDark, neuIntensity) { }
                        }
                    }
                }
            }

            // ==================== 4. 输入框 ====================
            SectionHeader("输入框", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var searchQuery by remember { mutableStateOf("") }
                var searchFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neuInset(RoundedCornerShape(14.dp), isDark, neuIntensity, backgroundColor = surfaceColor)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { searchFocused = it.isFocused },
                            textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("搜索插件...", color = textSecondary, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text("✕", color = textTertiary, modifier = Modifier.clickable { searchQuery = "" }, fontSize = 14.sp)
                        }
                    }
                }
                var password by remember { mutableStateOf("") }
                var passwordFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neuInset(RoundedCornerShape(14.dp), isDark, neuIntensity, backgroundColor = surfaceColor)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { passwordFocused = it.isFocused },
                            textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            decorationBox = { innerTextField ->
                                if (password.isEmpty()) {
                                    Text("密码", color = textSecondary, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            // ==================== 5. 切换开关·芯片·滑块 ====================
            SectionHeader("切换开关 · 芯片 · 滑块", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var switchOn by remember { mutableStateOf(true) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("切换开关", color = textSecondary, modifier = Modifier.weight(1f))
                    UnifiedSwitch(
                        checked = switchOn,
                        onCheckedChange = { switchOn = it }
                    )
                }

                var selectedChip by remember { mutableIntStateOf(0) }
                val chipLabels = listOf("浅色", "深色", "自动")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chipLabels.forEachIndexed { index, label ->
                        val isActive = selectedChip == index
                        val chipInteractionSource = remember { MutableInteractionSource() }
                        var isChipPressed by remember { mutableStateOf(false) }
                        val chipScale by animateFloatAsState(
                            targetValue = if (isChipPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "chip_scale"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                                .clip(RoundedCornerShape(20.dp))
                                .then(
                                    if (isActive) Modifier.background(diagonalGradient(listOf(primaryColor, primaryLight)))
                                    else Modifier.neuRaised(RoundedCornerShape(20.dp), isDark, neuIntensity, backgroundColor = surfaceColor)
                                        .background(surfaceColor, RoundedCornerShape(20.dp))
                                )
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        isChipPressed = true
                                        val up = waitForUpOrCancellation()
                                        isChipPressed = false
                                    }
                                }
                                .clickable(interactionSource = chipInteractionSource, indication = null) { selectedChip = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isActive) Color.White else textPrimary, fontSize = 13.sp)
                        }
                    }
                }

                var sliderValue by remember { mutableFloatStateOf(400f) }
                Column {
                    Text("动画速度", color = textSecondary, fontSize = 13.sp)
                    UnifiedSlider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 100f..800f
                    )
                }

                var glowValue by remember { mutableFloatStateOf(35f) }
                Column {
                    Text("发光强度", color = textSecondary, fontSize = 13.sp)
                    UnifiedSlider(
                        value = glowValue,
                        onValueChange = { glowValue = it },
                        valueRange = 0f..100f
                    )
                }
            }

            // ==================== 6. 下拉菜单 ====================
            SectionHeader("下拉菜单", textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var expanded1 by remember { mutableStateOf(false) }
                Box {
                    DropdownTrigger("排序 ▾", surfaceColor, textPrimary, isDark, neuIntensity) { expanded1 = true }
                    DropdownMenu(expanded = expanded1, onDismissRequest = { expanded1 = false }) {
                        listOf("名称 (A-Z)", "名称 (Z-A)", "修改日期").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { expanded1 = false })
                        }
                    }
                }

                var expanded2 by remember { mutableStateOf(false) }
                Box {
                    DropdownTrigger("操作 ▾", surfaceColor, textPrimary, isDark, neuIntensity) { expanded2 = true }
                    DropdownMenu(expanded = expanded2, onDismissRequest = { expanded2 = false }) {
                        listOf("备份", "恢复", "清除数据", "全部删除").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { expanded2 = false })
                        }
                    }
                }

                Box {
                    var badgeCount by remember { mutableIntStateOf(3) }
                    BadgedBox(
                        badge = { Badge { Text("$badgeCount") } }
                    ) {
                        DropdownTrigger("通知", surfaceColor, textPrimary, isDark, neuIntensity) { }
                    }
                }
            }

            // ==================== 7. 列表项 ====================
            SectionHeader("列表项", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItemDemo("P", "插件管理器", "管理已安装插件", listOf(primaryColor, primaryLight), isDark, neuIntensity) { }
                ListItemDemo("R", "仓库", "浏览在线插件", listOf(Color(0xFF667EEA), Color(0xFF764BA2)), isDark, neuIntensity) { }
                ListItemDemo("B", "备份", "备份和恢复数据", listOf(Color(0xFFF093FB), Color(0xFFF5576C)), isDark, neuIntensity) { }
            }

            // ==================== 8. 滑动切换 ====================
            SectionHeader("滑动切换", textPrimary)
            val pagerState = rememberPagerState(pageCount = { 3 })
            val swipeColors = listOf(
                listOf(primaryColor, primaryLight),
                listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                listOf(Color(0xFFF093FB), Color(0xFFF5576C))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(diagonalGradient(swipeColors[page])),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("页面 ${page + 1}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == index) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            // ==================== 9. 骨架屏加载 ====================
            SectionHeader("骨架屏加载", textPrimary)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .neuCard(isDark, neuIntensity, backgroundColor = surfaceColor)

                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(surfaceColor).shimmerEffect())
                Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(surfaceColor).shimmerEffect())
                Box(Modifier.fillMaxWidth(0.70f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(surfaceColor).shimmerEffect())
            }

            // ==================== 10. 进度条 ====================
            SectionHeader("进度条", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressDemo("正在下载插件...", 0.67f, primaryColor, primaryLight, isDark, neuIntensity)
                ProgressDemo("安装中", 0.32f, primaryColor, primaryLight, isDark, neuIntensity)
                ProgressDemo("完成", 1.0f, successColor, successColor, isDark, neuIntensity)
            }

            // ==================== 11. Toast 通知 ====================
            SectionHeader("Toast 通知", textPrimary)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToastButton("成功 Toast", successColor, Color.White) {
                        AppToast.success(context, "插件安装成功！")
                    }
                    ToastButton("错误 Toast", errorColor, Color.White) {
                        AppToast.error(context, "连接失败")
                    }
                    ToastButton("信息 Toast", infoColor, Color.White) {
                        AppToast.info(context, "有可用更新")
                    }
                }
            }

            // ==================== 12. 复选框 & 单选框 ====================
            SectionHeader("复选框 & 单选框", textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var cb1 by remember { mutableStateOf(true) }
                    var cb2 by remember { mutableStateOf(false) }
                    var cb3 by remember { mutableStateOf(true) }
                    CheckboxRow("WebView 插件", cb1) { cb1 = it }
                    CheckboxRow("终端插件", cb2) { cb2 = it }
                    CheckboxRow("备份管理器", cb3) { cb3 = it }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var selected by remember { mutableIntStateOf(0) }
                    val options = listOf("浅色模式", "深色模式", "自动")
                    options.forEachIndexed { index, label ->
                        RadioButtonRow(label, selected == index) { selected = index }
                    }
                }
            }

            // ==================== 13. 折叠面板 ====================
            SectionHeader("折叠面板", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccordionDemo("插件详情", "WebView Plugin v2.1 是一个功能强大的网页浏览插件，支持 JavaScript 注入、CSS 自定义和本地存储管理。", isDark, neuIntensity)
                AccordionDemo("更新日志", "v2.1: 性能优化\nv2.0: 新增 JS 注入\nv1.5: 初始版本", isDark, neuIntensity)
            }

            // ==================== 14. 提示条 ====================
            SectionHeader("提示条", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertBannerDemo("ℹ", "有新版本可用，请更新获取最新功能。", infoColor, isDark, neuIntensity)
                AlertBannerDemo("⚠", "存储空间不足，请考虑清理缓存。", warningColor, isDark, neuIntensity)
                AlertBannerDemo("✕", "加载插件失败，请检查网络连接。", errorColor, isDark, neuIntensity)
                AlertBannerDemo("✔", "备份完成，已保存3个插件。", successColor, isDark, neuIntensity)
            }

            // ==================== 15. 状态指示器 ====================
            SectionHeader("状态指示器", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow("在线", successColor, true, textPrimary)
                StatusRow("离线", textTertiary, false, textPrimary)
                StatusRow("更新中", warningColor, true, textPrimary)
                StatusRow("错误", errorColor, true, textPrimary)
            }

            // ==================== 16. 搜索栏 ====================
            SectionHeader("搜索栏", textPrimary)
            var searchQuery2 by remember { mutableStateOf("") }
            var searchFocused2 by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neuInset(RoundedCornerShape(14.dp), isDark, neuIntensity, backgroundColor = surfaceColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery2,
                        onValueChange = { searchQuery2 = it },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { searchFocused2 = it.isFocused },
                        textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (searchQuery2.isEmpty()) {
                                Text("搜索插件、仓库...", color = textSecondary, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery2.isNotEmpty()) {
                        Text("✕", color = textTertiary, modifier = Modifier.clickable { searchQuery2 = "" }, fontSize = 14.sp)
                    }
                }
            }

            // ==================== 17. 代码块 ====================
            SectionHeader("代码块", textPrimary)
            CodeBlockDemo(isDark)

            // ==================== 18. 头像 ====================
            SectionHeader("头像", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarDemo("A", 32, listOf(Color(0xFF667EEA), Color(0xFF764BA2)))
                    AvatarDemo("B", 42, listOf(primaryColor, primaryLight))
                    AvatarDemo("C", 56, listOf(Color(0xFFF093FB), Color(0xFFF5576C)))
                    AvatarDemo("D", 72, listOf(Color(0xFF27AE60), Color(0xFF2ECC71)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    AvatarDemo("1", 42, listOf(Color(0xFF667EEA), Color(0xFF8B9CF7)))
                    AvatarDemo("2", 42, listOf(Color(0xFF764BA2), Color(0xFF9B6DC6)))
                    AvatarDemo("3", 42, listOf(Color(0xFF1A3A4A), Color(0xFF6AADBE)))
                }
            }

            // ==================== 19. 分页 ====================
            SectionHeader("分页", textPrimary)
            var currentPage by remember { mutableIntStateOf(1) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("«", "‹").forEach { char ->
                    PaginationBtn(char, false, primaryColor, primaryLight, textPrimary, isDark, neuIntensity) { }
                }
                listOf("1", "2", "3", "...", "12").forEach { char ->
                    val pageNum = char.toIntOrNull()
                    PaginationBtn(char, char == "$currentPage", primaryColor, primaryLight, textPrimary, isDark, neuIntensity) {
                        if (pageNum != null) currentPage = pageNum
                    }
                }
                listOf("›", "»").forEach { char ->
                    PaginationBtn(char, false, primaryColor, primaryLight, textPrimary, isDark, neuIntensity) { }
                }
            }

            // ==================== 20. 空状态 ====================
            SectionHeader("空状态", textPrimary)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .neuCard(isDark, neuIntensity, backgroundColor = surfaceColor)

                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📦", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("未安装插件", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("浏览仓库以查找有用的插件", color = textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(diagonalGradient(listOf(primaryColor, primaryLight)))
                        .clickable { }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("浏览仓库", color = Color.White, fontSize = 14.sp)
                }
            }

            // ==================== 21. 数字步进器 ====================
            SectionHeader("数字步进器", textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StepperDemo("字体大小", 3, isDark, neuIntensity)
                StepperDemo("历史行数", 14, isDark, neuIntensity)
            }

            // ==================== 22. 滚动标签 ====================
            SectionHeader("滚动标签", textPrimary)
            var scrollTab by remember { mutableIntStateOf(0) }
            val scrollTabs = listOf("全部", "WebView", "终端", "UI工具", "备份", "网络", "安全", "实用工具")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(scrollTabs) { index, label ->
                    val isActive = scrollTab == index
                    var isScrollTabPressed by remember { mutableStateOf(false) }
                    val scrollTabScale by animateFloatAsState(
                            targetValue = if (isScrollTabPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "scroll_tab_scale"
                    )
                    Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = scrollTabScale; scaleY = scrollTabScale }
                                .then(if (isActive) Modifier.neuInset(RoundedCornerShape(10.dp), isDark, neuIntensity, backgroundColor = Color.Transparent) else Modifier.neuRaised(RoundedCornerShape(10.dp), isDark, neuIntensity, backgroundColor = surfaceColor))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                if (isActive) diagonalGradient(listOf(primaryColor, primaryLight))
                                else Brush.linearGradient(listOf(surfaceColor, surfaceColor))
                            )
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isScrollTabPressed = true
                                    val up = waitForUpOrCancellation()
                                    isScrollTabPressed = false
                                }
                            }
                            .clickable { scrollTab = index }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (isActive) Color.White else textPrimary, fontSize = 13.sp)
                    }
                }
            }

            // ==================== 23. 顶部横幅 ====================
            SectionHeader("顶部横幅", textPrimary)
            var showBanner by remember { mutableStateOf(true) }
            AnimatedVisibility(
                visible = showBanner,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(diagonalGradient(listOf(primaryColor, primaryLight)))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📢 ", fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("更新可用", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("UIN Tool v5.7.0 已准备安装", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                        Text("✕", color = Color.White, modifier = Modifier.clickable { showBanner = false }, fontSize = 16.sp)
                    }
                }
            }

            // ==================== 24. 底部弹出 ====================
            SectionHeader("底部弹出", textPrimary)
            var showBottomSheet by remember { mutableStateOf(false) }
            val sheetState = rememberModalBottomSheetState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neuRaised(RoundedCornerShape(14.dp), isDark, neuIntensity, backgroundColor = surfaceColor)
                    .background(surfaceColor, RoundedCornerShape(14.dp))
                    .clickable { showBottomSheet = true }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📋", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("点击打开底部弹窗", color = textPrimary, fontSize = 14.sp)
                }
            }
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    containerColor = surfaceColor,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text("选择操作", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        listOf(
                            "📋 备份数据" to "创建当前配置的备份",
                            "🔄 恢复数据" to "从备份恢复配置",
                            "🗑 清除缓存" to "清除临时文件和缓存",
                            "📤 导出配置" to "导出为JSON文件",
                            "🔒 隐私设置" to "管理隐私选项"
                        ).forEach { (title, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showBottomSheet = false }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(desc, color = textSecondary, fontSize = 12.sp)
                                }
                                Text("›", color = textTertiary, fontSize = 18.sp)
                            }
                            HorizontalDivider(color = if (isDark) Color(0xFF333333) else Color(0xFFE8E8E8))
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // ==================== 25. 评分 ====================
            SectionHeader("评分", textPrimary)
            var rating by remember { mutableFloatStateOf(4f) }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..5) {
                        val starInteractionSource = remember { MutableInteractionSource() }
                        var isStarPressed by remember { mutableStateOf(false) }
                        val starScale by animateFloatAsState(
                            targetValue = if (isStarPressed) 1.2f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "star_scale"
                        )
                        Canvas(
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer { scaleX = starScale; scaleY = starScale }
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        isStarPressed = true
                                        val up = waitForUpOrCancellation()
                                        isStarPressed = false
                                    }
                                }
                                .clickable(interactionSource = starInteractionSource, indication = null) { rating = i.toFloat() }
                        ) {
                            val starShape = RoundedStarShape(
                                points = 5,
                                innerRadiusRatio = 0.4f,
                                cornerRadius = 0.2f
                            )
                            drawPath(
                                path = starShape.createOutline(size, LayoutDirection.Ltr, this).let {
                                    when (it) {
                                        is Outline.Generic -> it.path
                                        else -> Path()
                                    }
                                },
                                color = if (i <= rating) warningColor else warningColor.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
                Text("${rating}.0 / 5.0", color = textSecondary, fontSize = 13.sp)
            }

            // ==================== 26. 颜色选择器 ====================
            SectionHeader("颜色选择器", textPrimary)
            var selectedColor by remember { mutableStateOf(Color(0xFF1A3A4A)) }
            val colors = listOf(
                Color(0xFF1A3A4A), Color(0xFF1A3A4A), Color(0xFF667EEA), Color(0xFF764BA2),
                Color(0xFFF093FB), Color(0xFF27AE60), Color(0xFFF59E0B), Color(0xFFE74C3C)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(colors.size) { index ->
                    val color = colors[index]
                    val colorInteractionSource = remember { MutableInteractionSource() }
                    var isColorPressed by remember { mutableStateOf(false) }
                    val colorScale by animateFloatAsState(
                        targetValue = if (isColorPressed || selectedColor == color) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "color_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer { scaleX = colorScale; scaleY = colorScale }
                            .clip(CircleShape)
                            .background(color)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isColorPressed = true
                                    val up = waitForUpOrCancellation()
                                    isColorPressed = false
                                }
                            }
                            .clickable(interactionSource = colorInteractionSource, indication = null) { selectedColor = color }
                            .then(
                                if (selectedColor == color) Modifier.border(3.dp, Color.White, CircleShape)
                                else Modifier
                            )
                    )
                }
            }

            // ==================== 27. 环形进度 ====================
            SectionHeader("环形进度", textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CircularProgressDemo(0.75f, primaryLight)
                CircularProgressDemo(1.0f, successColor)
                CircularProgressDemo(0.2f, warningColor)
            }

            // ==================== 28. 文件上传 ====================
            SectionHeader("文件上传", textPrimary)
            var uploadHovered by remember { mutableStateOf(false) }
            val uploadScale by animateFloatAsState(
                targetValue = if (uploadHovered) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "upload_scale"
            )
            val uploadBorderColor by animateColorAsState(
                targetValue = if (uploadHovered) primaryLight else textTertiary,
                animationSpec = tween(300),
                label = "upload_border"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = uploadScale; scaleY = uploadScale }
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, uploadBorderColor, RoundedCornerShape(14.dp))
                    .background(if (uploadHovered) primaryLight.copy(alpha = 0.08f) else surfaceColor)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                uploadHovered = event.changes.any { it.pressed }
                            }
                        }
                    }
                    .clickable { }
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📂", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("点击上传插件或配置", color = textPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("支持 .zip, .json, .tar.gz", color = textTertiary, fontSize = 12.sp)
                }
            }

            // ==================== 29. 时间选择器 ====================
            SectionHeader("时间选择器", textPrimary)
            var hours by remember { mutableIntStateOf(14) }
            var minutes by remember { mutableIntStateOf(30) }
            var seconds by remember { mutableIntStateOf(0) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeColumn("HH", hours, 0..23) { hours = it }
                Text(":", color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                TimeColumn("MM", minutes, 0..59) { minutes = it }
                Text(":", color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                TimeColumn("SS", seconds, 0..59) { seconds = it }
            }

            // ==================== 30. 图片查看器 ====================
            SectionHeader("图片查看器", textPrimary)
            var imgHovered by remember { mutableStateOf(false) }
            val imgScale by animateFloatAsState(
                targetValue = if (imgHovered) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "img_scale"
            )
            val imgOverlayAlpha by animateFloatAsState(
                targetValue = if (imgHovered) 1f else 0.4f,
                animationSpec = tween(300),
                label = "img_overlay"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .graphicsLayer { scaleX = imgScale; scaleY = imgScale }
                    .clip(RoundedCornerShape(14.dp))
                    .background(diagonalGradient(listOf(primaryColor, primaryLight)))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                imgHovered = event.changes.any { it.pressed }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("插件截图", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = imgOverlayAlpha))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔍 查看全屏", color = Color.White, fontSize = 14.sp)
                }
            }

            // ==================== 31. 工具栏 ====================
            SectionHeader("工具栏", textPrimary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neuToolbar(isDark, neuIntensity)
                    .background(surfaceColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☰", fontSize = 20.sp, modifier = Modifier.clickable { })
                    Spacer(Modifier.width(12.dp))
                    Text("插件管理器", color = textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.size(24.dp).clickable { }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Search, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.size(24.dp).clickable { }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Settings, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ==================== 32. 面包屑 ====================
            SectionHeader("面包屑", textPrimary)
            val breadcrumbItems = listOf("首页", "插件", "WebView", "设置")
            Row(verticalAlignment = Alignment.CenterVertically) {
                breadcrumbItems.forEachIndexed { index, label ->
                    val isLast = index == breadcrumbItems.lastIndex
                    var isBcUnderlined by remember { mutableStateOf(false) }
                    val bcInteractionSource = remember { MutableInteractionSource() }
                    val bcScale by animateFloatAsState(
                        targetValue = if (isBcUnderlined) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "bc_scale"
                    )
                    Box(
                        modifier = Modifier
                            .graphicsLayer { scaleX = bcScale; scaleY = bcScale }
                            .clickable(interactionSource = bcInteractionSource, indication = null) {
                                if (!isLast) isBcUnderlined = !isBcUnderlined
                            }
                    ) {
                        Text(
                            label,
                            color = if (isLast) textPrimary else primaryLight,
                            fontSize = 13.sp,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            textDecoration = if (isLast || !isBcUnderlined) TextDecoration.None else TextDecoration.Underline
                        )
                    }
                    if (!isLast) {
                        Text(" › ", color = textTertiary, fontSize = 13.sp)
                    }
                }
            }

            // ==================== 33. 通知面板 ====================
            SectionHeader("通知面板", textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationRow(Color(0xFFE74C3C), "插件错误", "WebView插件加载失败", "2分钟前", isDark, neuIntensity) { }
                NotificationRow(Color(0xFF3B82F6), "更新可用", "UIN Tool v5.7.0 已就绪", "1小时前", isDark, neuIntensity) { }
                NotificationRow(Color(0xFF27AE60), "备份完成", "已备份3个插件", "3小时前", isDark, neuIntensity) { }
            }

            // ==================== 34. 快速拨号 ====================
            SectionHeader("快速拨号", textPrimary)
            var fabExpanded by remember { mutableStateOf(false) }
            val rotation by animateFloatAsState(
                targetValue = if (fabExpanded) 45f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "fab_rot"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 70.dp)
                        .zIndex(4f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val fabItems = listOf("➕ 新建插件", "📂 导入", "⚙ 设置")
                    fabItems.forEachIndexed { index, item ->
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(delayMillis = index * 80)
                            ) + fadeIn(animationSpec = tween(delayMillis = index * 80)),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            FABSubItem(item, isDark) { fabExpanded = false }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .zIndex(3f)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(diagonalGradient(listOf(primaryColor, primaryLight)))
                        .graphicsLayer { rotationZ = rotation }
                        .clickable { fabExpanded = !fabExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = Color.White, fontSize = 24.sp)
                }
            }

            // ==================== 35. 确认操作 ====================
            SectionHeader("确认操作", textPrimary)
            var showDialog by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neuRaised(RoundedCornerShape(14.dp), isDark, neuIntensity, backgroundColor = surfaceColor)
                    .background(surfaceColor, RoundedCornerShape(14.dp))
                    .clickable { showDialog = true }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("点击弹出确认对话框", color = textPrimary, fontSize = 14.sp)
                }
            }
            if (showDialog) {
                Dialog(onDismissRequest = { showDialog = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showDialog = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .background(surfaceColor, RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { }
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("确认操作", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("您确定吗？这将立即应用更改。", color = textSecondary, fontSize = 14.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var dismissPressed by remember { mutableStateOf(false) }
                                val dismissScale by animateFloatAsState(
                                    targetValue = if (dismissPressed) 0.95f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "dismiss_scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = dismissScale; scaleY = dismissScale }
                                        .neuRaised(RoundedCornerShape(10.dp), isDark, neuIntensity, backgroundColor = surfaceColor)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(surfaceColor)
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                dismissPressed = true
                                                val up = waitForUpOrCancellation()
                                                dismissPressed = false
                                            }
                                        }
                                        .clickable { showDialog = false }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("取消", color = textSecondary, fontSize = 14.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                var confirmPressed by remember { mutableStateOf(false) }
                                val confirmScale by animateFloatAsState(
                                    targetValue = if (confirmPressed) 0.95f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "confirm_scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = confirmScale; scaleY = confirmScale }
                                        .neuRaised(RoundedCornerShape(10.dp), isDark, neuIntensity, backgroundColor = Color.Transparent)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(diagonalGradient(listOf(primaryColor, primaryLight)))
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                confirmPressed = true
                                                val up = waitForUpOrCancellation()
                                                confirmPressed = false
                                            }
                                        }
                                        .clickable { showDialog = false }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("确认", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

    } // Column end
    }
}

// ==================== Helper Components ====================

@Composable
private fun DrawerItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    surfaceColor: Color,
    textPrimary: Color,
    primaryLight: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(if (isSelected) primaryLight.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(primaryLight)
                )
                Spacer(Modifier.width(14.dp))
            }
            Text(
                icon,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = if (isSelected) primaryLight else textPrimary
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) primaryLight else textPrimary
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Color(0xFF1A3A4A), thickness = 2.dp)
    }
}

private fun diagonalGradient(colors: List<Color>) = Brush.linearGradient(
    colors = colors,
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f)
)

@Composable
private fun NeuButtonDemo(
    text: String, startColor: Color, endColor: Color, textColor: Color,
    intensity: NeuDefaults.Intensity, isDark: Boolean, modifier: Modifier = Modifier,
    onEmptyClick: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "btn_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (isPressed) Modifier.neuInset(RoundedCornerShape(12.dp), isDark, intensity, backgroundColor = Color.Transparent)
                else Modifier.neuRaised(RoundedCornerShape(12.dp), isDark, intensity, backgroundColor = Color.Transparent)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(diagonalGradient(listOf(startColor, endColor)))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onEmptyClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 14.sp)
    }
}

@Composable
private fun OutlineButtonDemo(text: String, borderColor: Color, textColor: Color, modifier: Modifier = Modifier, onEmptyClick: () -> Unit = {}) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "outline_btn_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onEmptyClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 14.sp)
    }
}

@Composable
private fun TextButtonDemo(text: String, textColor: Color, modifier: Modifier = Modifier, onEmptyClick: () -> Unit = {}) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "text_btn_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onEmptyClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 14.sp)
    }
}

@Composable
private fun PluginCardDemo(
    icon: String, name: String, chips: List<Pair<String, Color>>,
    isDark: Boolean, intensity: NeuDefaults.Intensity, modifier: Modifier = Modifier,
    onEmptyClick: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (isPressed) Modifier.neuInset(RoundedCornerShape(14.dp), isDark, intensity, backgroundColor = surfaceColor)
                else Modifier.neuCard(isDark, intensity, backgroundColor = surfaceColor)
            )
            .background(surfaceColor, RoundedCornerShape(14.dp))
            .neuRipple()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onEmptyClick() }
            .padding(14.dp)
    ) {
        Text(icon, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text(name, color = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { (label, color) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(label, color = color, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniCardDemo(title: String, subtitle: String, isDark: Boolean, intensity: NeuDefaults.Intensity, onEmptyClick: () -> Unit = {}) {
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666)
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onEmptyClick() }
            .neuCard(isDark, intensity, backgroundColor = surfaceColor)

            .padding(14.dp)
    ) {
        Column {
            Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DropdownTrigger(
    text: String, bgColor: Color, textColor: Color,
    isDark: Boolean, intensity: NeuDefaults.Intensity, onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dd_scale"
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .neuRaised(RoundedCornerShape(10.dp), isDark, intensity, backgroundColor = bgColor)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = textColor, fontSize = 13.sp)
    }
}

@Composable
private fun ListItemDemo(
    icon: String, title: String, subtitle: String,
    iconColors: List<Color>, isDark: Boolean, intensity: NeuDefaults.Intensity,
    onEmptyClick: () -> Unit = {}
) {
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666)

    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "list_press_scale"
    )
    var isHovered by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = if (isHovered || isPressed) 8f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "list_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .then(
                if (isPressed) Modifier.neuInset(RoundedCornerShape(14.dp), isDark, intensity, backgroundColor = surfaceColor)
                    .background(surfaceColor, RoundedCornerShape(14.dp))
                else Modifier.neuCard(isDark, intensity, backgroundColor = surfaceColor)

            )
            .sweepHighlight()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        isHovered = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onEmptyClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(diagonalGradient(iconColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = textSecondary, fontSize = 12.sp)
            }
            Text("›", color = textSecondary, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ProgressDemo(
    label: String, progress: Float, startColor: Color, endColor: Color,
    isDark: Boolean, intensity: NeuDefaults.Intensity
) {
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666)
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column {
        Text(label, color = textPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .neuProgressTrack(isDark, intensity)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(5.dp))
                    .background(diagonalGradient(listOf(startColor, endColor)))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("${(animatedProgress * 100).toInt()}%", color = textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ToastButton(text: String, bgColor: Color, textColor: Color, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = textColor, fontSize = 13.sp)
    }
}

@Composable
private fun CheckboxRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cb_scale"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Text(text, color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun RadioButtonRow(text: String, selected: Boolean, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "radio_scale"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(text, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun AccordionDemo(title: String, content: String, isDark: Boolean, intensity: NeuDefaults.Intensity) {
    var expanded by remember { mutableStateOf(false) }
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "accordion_arrow"
    )

    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { expanded = !expanded }
            .neuCard(isDark, intensity, backgroundColor = surfaceColor)

    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("▾", color = textSecondary, fontSize = 14.sp, modifier = Modifier.graphicsLayer { rotationZ = rotation })
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                content,
                color = textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AlertBannerDemo(
    icon: String, message: String, color: Color,
    isDark: Boolean, intensity: NeuDefaults.Intensity
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .drawBehind {
                drawRect(
                    color = color,
                    topLeft = Offset.Zero,
                    size = Size(4.dp.toPx(), size.height)
                )
            }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(message, color = color, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusRow(name: String, color: Color, hasGlow: Boolean, textColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_radius"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .then(
                    if (hasGlow) Modifier.drawBehind {
                        drawCircle(
                            color = color.copy(alpha = glowAlpha * 0.25f),
                            radius = size.maxDimension * 0.25f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
                                radius = pulseRadius.dp.toPx()
                            ),
                            radius = pulseRadius.dp.toPx()
                        )
                    } else Modifier
                )
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, color = textColor, fontSize = 14.sp)
    }
}

@Composable
private fun CodeBlockDemo(isDark: Boolean) {
    val bgColor = Color(0xFF1A1A2E)
    val keywordColor = Color(0xFFC792EA)
    val stringColor = Color(0xFFC3E88D)
    val commentColor = Color(0xFF546E7A)
    val funcColor = Color(0xFF82AAFF)

    val code = buildAnnotatedString {
        withStyle(SpanStyle(keywordColor, fontWeight = FontWeight.Bold)) { append("const ") }
        withStyle(SpanStyle(funcColor)) { append("config") }
        append(" = {\n")
        withStyle(SpanStyle(stringColor)) { append("  name: " + "\"Terminal Plugin\"") }
        append(",\n")
        withStyle(SpanStyle(stringColor)) { append("  version: " + "\"1.0.0\"") }
        append(",\n")
        withStyle(SpanStyle(stringColor)) { append("  author: " + "\"UIN Tool\"") }
        append("\n}\n\n")
        withStyle(SpanStyle(keywordColor, fontWeight = FontWeight.Bold)) { append("function ") }
        withStyle(SpanStyle(funcColor)) { append("initTerminal") }
        withStyle(SpanStyle(keywordColor)) { append("(container") }
        append(", ")
        withStyle(SpanStyle(keywordColor)) { append("options") }
        withStyle(SpanStyle(keywordColor)) { append(") {\n  ") }
        withStyle(SpanStyle(commentColor)) { append("// 初始化终端") }
        withStyle(SpanStyle(keywordColor)) { append("\n  const ") }
        withStyle(SpanStyle(funcColor)) { append("term") }
        append(" = new Terminal(options)\n")
        withStyle(SpanStyle(funcColor)) { append("  term") }
        append(".open(container)\n")
        withStyle(SpanStyle(keywordColor)) { append("  return ") }
        withStyle(SpanStyle(funcColor)) { append("term") }
        append("\n}")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            code,
            color = Color(0xFFD0D0D0),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun AvatarDemo(letter: String, sizeDp: Int, colors: List<Color>) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "avatar_scale"
    )
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontSize = (sizeDp / 2.5).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PaginationBtn(
    text: String, active: Boolean, primaryColor: Color, primaryLight: Color,
    textColor: Color, isDark: Boolean, intensity: NeuDefaults.Intensity,
    onEmptyClick: () -> Unit = {}
) {
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isBtnEnabled = text != "..."
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pg_scale"
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (active) Modifier.neuInset(RoundedCornerShape(10.dp), isDark, intensity, backgroundColor = Color.Transparent)
                else Modifier.neuRaised(RoundedCornerShape(10.dp), isDark, intensity, backgroundColor = surfaceColor)
            )
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) diagonalGradient(listOf(primaryColor, primaryLight))
                else Brush.linearGradient(listOf(surfaceColor, surfaceColor))
            )
            .pointerInput(isBtnEnabled) {
                if (isBtnEnabled) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        val up = waitForUpOrCancellation()
                        isPressed = false
                    }
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, enabled = isBtnEnabled) { onEmptyClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) Color.White else textColor,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun StepperDemo(label: String, initialValue: Int, isDark: Boolean, intensity: NeuDefaults.Intensity) {
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)
    val primaryLight = Color(0xFF1A3A4A)
    var value by remember { mutableIntStateOf(initialValue) }
    var bounced by remember { mutableStateOf(false) }
    val bounceScale by animateFloatAsState(
        targetValue = if (bounced) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bounce"
    )
    val scope = rememberCoroutineScope()
    var minusPressed by remember { mutableStateOf(false) }
    val minusInteractionSource = remember { MutableInteractionSource() }
    val minusScale by animateFloatAsState(
        targetValue = if (minusPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "minusScale"
    )
    var plusPressed by remember { mutableStateOf(false) }
    val plusInteractionSource = remember { MutableInteractionSource() }
    val plusScale by animateFloatAsState(
        targetValue = if (plusPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "plusScale"
    )

    Column {
        Text(label, color = textPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { scaleX = minusScale; scaleY = minusScale }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            minusPressed = true
                            val up = waitForUpOrCancellation()
                            minusPressed = false
                        }
                    }
                    .clickable(interactionSource = minusInteractionSource, indication = null) {
                        if (value > 0) {
                            value--
                            if (value == 0) {
                                bounced = true
                                scope.launch { delay(200); bounced = false }
                            }
                        }
                    }
                    .neuRaised(RoundedCornerShape(8.dp), isDark, intensity, backgroundColor = surfaceColor)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceColor),
                contentAlignment = Alignment.Center
            ) { Text("−", color = primaryLight, fontSize = 16.sp) }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = bounceScale; scaleY = bounceScale
                },
                contentAlignment = Alignment.Center
            ) {
                Text("$value", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { scaleX = plusScale; scaleY = plusScale }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            plusPressed = true
                            val up = waitForUpOrCancellation()
                            plusPressed = false
                        }
                    }
                    .clickable(interactionSource = plusInteractionSource, indication = null) {
                        if (value < 99) {
                            value++
                            if (value == 99) {
                                bounced = true
                                scope.launch { delay(200); bounced = false }
                            }
                        }
                    }
                    .neuRaised(RoundedCornerShape(8.dp), isDark, intensity, backgroundColor = surfaceColor)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceColor),
                contentAlignment = Alignment.Center
            ) { Text("+", color = primaryLight, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun CircularProgressDemo(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "circular_progress"
    )
    val sweepAngle = animatedProgress * 360f

    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            "${(animatedProgress * 100).toInt()}%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TimeColumn(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    val primaryLight = Color(0xFF1A3A4A)
    val isDark = UIConfig.shouldUseDarkTheme()
    val neuIntensity = NeuDefaults.currentIntensity()
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)

    Column(
        modifier = Modifier
            .neuCard(isDark, neuIntensity, backgroundColor = surfaceColor)

            .clip(RoundedCornerShape(10.dp))
            .background(surfaceColor)
            .width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val upInteractionSource = remember { MutableInteractionSource() }
        var isUpPressed by remember { mutableStateOf(false) }
        val upScale by animateFloatAsState(
            targetValue = if (isUpPressed) 0.85f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "tp_up_scale"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = upScale; scaleY = upScale }
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isUpPressed = true
                        val up = waitForUpOrCancellation()
                        isUpPressed = false
                    }
                }
                .clickable(interactionSource = upInteractionSource, indication = null) { onValueChange(if (value >= range.last) range.first else value + 1) }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) { Text("▲", color = primaryLight, fontSize = 12.sp) }
        Text(
            "%02d".format(value),
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        val downInteractionSource = remember { MutableInteractionSource() }
        var isDownPressed by remember { mutableStateOf(false) }
        val downScale by animateFloatAsState(
            targetValue = if (isDownPressed) 0.85f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "tp_down_scale"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = downScale; scaleY = downScale }
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isDownPressed = true
                        val up = waitForUpOrCancellation()
                        isDownPressed = false
                    }
                }
                .clickable(interactionSource = downInteractionSource, indication = null) { onValueChange(if (value <= range.first) range.last else value - 1) }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) { Text("▼", color = primaryLight, fontSize = 12.sp) }
    }
}

@Composable
private fun NotificationRow(
    dotColor: Color, title: String, description: String, time: String,
    isDark: Boolean, intensity: NeuDefaults.Intensity,
    onEmptyClick: () -> Unit = {}
) {
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else if (isDark) Color(0xFF1E1E22) else Color.White
    val textPrimary = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666)
    val textTertiary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF888888)
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "notif_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .then(
                if (isPressed) Modifier.neuInset(RoundedCornerShape(14.dp), isDark, intensity, backgroundColor = surfaceColor)
                else Modifier.neuCard(isDark, intensity, backgroundColor = surfaceColor)
            )
            .background(surfaceColor, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { onEmptyClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(description, color = textSecondary, fontSize = 12.sp)
            }
            Text(time, color = textTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FABSubItem(label: String, isDark: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "fab_sub_scale"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isDark) Color(0xFF1E1E22).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(label, color = if (isDark) Color(0xFFD0D0D0) else Color(0xFF333333), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
