package com.UIN.Tool.ui.screen.manage.uiconfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.gradientBackgroundBrush
import com.UIN.Tool.ui.components.unified.NeuDefaults
import com.UIN.Tool.ui.components.unified.neuRaised
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class TreeNode(
    val id: String,
    val label: String,
    val labelResId: Int = 0,
    val children: List<TreeNode> = emptyList()
)

private val treeRoot = listOf(
    TreeNode("style", "风格", R.string.sidebar_style, listOf(
        TreeNode("style_default", "默认", R.string.sidebar_style),
        TreeNode("style_neumorphism", "新拟态", R.string.style_neumorphism_title)
    )),
    TreeNode("appearance", "外观属性", R.string.sidebar_appearance, listOf(
        TreeNode("appearance_colors", "色彩", R.string.sidebar_colors),
        TreeNode("appearance_shapes", "形状", R.string.sidebar_shapes, listOf(
            TreeNode("appearance_shapes_corner", "圆角", R.string.sidebar_corner_radius),
            TreeNode("appearance_shapes_size", "宽高", R.string.sidebar_width_height),
            TreeNode("appearance_shapes_border", "边框粗细", R.string.sidebar_border_thickness)
        )),
        TreeNode("appearance_material", "材质", R.string.sidebar_material, listOf(
            TreeNode("appearance_material_shadow", "阴影", R.string.sidebar_shadow),
            TreeNode("appearance_material_blur", "半透明效果", R.string.sidebar_translucent)
        )),
        TreeNode("appearance_bg", "背景", R.string.sidebar_background, listOf(
            TreeNode("appearance_bg_gradient", "渐变", R.string.sidebar_gradient),
            TreeNode("appearance_bg_solid", "纯色", R.string.sidebar_solid),
            TreeNode("appearance_bg_image", "图片", R.string.sidebar_image)
        ))
    )),
    TreeNode("content", "内容属性", R.string.sidebar_content, listOf(
        TreeNode("content_text", "文字", R.string.sidebar_text, listOf(
            TreeNode("content_text_size", "文字大小", R.string.sidebar_text_size),
            TreeNode("content_text_font", "字体", R.string.sidebar_font),
            TreeNode("content_text_weight", "粗细", R.string.sidebar_weight),
            TreeNode("content_text_language", "语言", R.string.language_label)
        )),
        TreeNode("content_image", "图片", R.string.sidebar_image)
    )),
    TreeNode("interaction", "交互属性", R.string.sidebar_interaction, listOf(
        TreeNode("interaction_click", "点击效果", R.string.sidebar_click_effect),
        TreeNode("interaction_animation", "动画速度", R.string.sidebar_animation_speed)
    )),
    TreeNode("layout", "布局属性", R.string.sidebar_layout, listOf(
        TreeNode("layout_margin", "外边距", R.string.sidebar_margin),
        TreeNode("layout_position", "排版占位", R.string.sidebar_position)
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIConfigSidebar(
    selectedSection: String,
    onSectionSelected: (String) -> Unit,
    onReset: () -> Unit = {},
    drawerState: DrawerState,
    drawerScope: CoroutineScope,
    content: @Composable (PaddingValues) -> Unit
) {
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryLight = MaterialTheme.colorScheme.primary

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp).then(
                    if (UIConfig.isNeumorphismEnabled()) Modifier.neuRaised(RoundedCornerShape(topEnd = 16.dp), UIConfig.shouldUseDarkTheme(), NeuDefaults.Intensity.LIGHT, cornerRadius = 16.dp, backgroundColor = Color.Transparent)
                    else Modifier
                ),
                drawerContainerColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { 280.dp.toPx() }
                    val heightPx = with(LocalDensity.current) { 600.dp.toPx() }
                    val gradientBrush = gradientBackgroundBrush(widthPx, heightPx)
                    if (gradientBrush != null) {
                        Box(modifier = Modifier.fillMaxSize().background(gradientBrush))
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.background)
                                .padding(32.dp)
                        ) {
                            Column {
                                Text(
                                    "UIN Tool",
                                    color = textPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "v5.6.0",
                                    color = textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            treeRoot.forEach { rootItem ->
                                TreeGroup(
                                    node = rootItem,
                                    selectedSection = selectedSection,
                                    onSectionSelected = { id ->
                                        onSectionSelected(id)
                                        drawerScope.launch { drawerState.close() }
                                    },
                                    surfaceColor = surfaceColor,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    primaryLight = primaryLight,
                                    depth = 0
                                )
                            }
                            Spacer(Modifier.height(48.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                UnifiedButton(
                                    text = Str.get(R.string.reset_default_settings),
                                    icon = Icons.Default.Refresh,
                                    onClick = {
                                        onReset()
                                        drawerScope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    variant = ButtonVariant.Outlined
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(Str.get(R.string.ui_personalization), fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            drawerScope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        }) {
                            Icon(Icons.Default.Menu, Str.get(R.string.menu))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.background,
                        navigationIconContentColor = textPrimary
                    )
                )
            }
        ) { padding ->
            content(padding)
        }
    }
}

@Composable
private fun TreeGroup(
    node: TreeNode,
    selectedSection: String,
    onSectionSelected: (String) -> Unit,
    surfaceColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    primaryLight: Color,
    depth: Int
) {
    var expanded by remember { mutableStateOf(false) }
    val hasChildren = node.children.isNotEmpty()
    val isSelected = node.id == selectedSection

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) surfaceColor.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "tree_bg"
    )

    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (neu && isSelected) {
                    Modifier.neuRaised(RoundedCornerShape(8.dp), isDark, NeuDefaults.Intensity.LIGHT, cornerRadius = 8.dp, backgroundColor = Color.Transparent)
                        .background(bgColor, RoundedCornerShape(8.dp))
                } else {
                    Modifier.background(bgColor)
                }
            )
            .clickable {
                if (hasChildren) {
                    expanded = !expanded
                } else {
                    onSectionSelected(node.id)
                }
            }
            .padding(
                start = (16 + depth * 20).dp,
                end = 16.dp,
                top = if (depth == 0) 12.dp else 8.dp,
                bottom = if (depth == 0) 12.dp else 8.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (hasChildren) {
                Text(
                    text = if (expanded) "▼" else "▶",
                    fontSize = 10.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(18.dp))
            }
            Text(
                text = if (node.labelResId != 0) Str.get(node.labelResId) else node.label,
                fontSize = if (depth == 0) 15.sp else 13.sp,
                fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) primaryLight else if (depth == 0) textPrimary else textSecondary,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(primaryLight)
                )
            }
        }
    }

    AnimatedVisibility(
        visible = expanded && hasChildren,
        enter = expandVertically(
            animationSpec = tween(200, easing = FastOutSlowInEasing)
        ),
        exit = shrinkVertically(
            animationSpec = tween(150, easing = FastOutSlowInEasing)
        )
    ) {
        Column {
            node.children.forEach { child ->
                TreeGroup(
                    node = child,
                    selectedSection = selectedSection,
                    onSectionSelected = onSectionSelected,
                    surfaceColor = surfaceColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    primaryLight = primaryLight,
                    depth = depth + 1
                )
            }
        }
    }
}
