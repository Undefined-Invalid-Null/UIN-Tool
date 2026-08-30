package com.UIN.Tool.ui.components.unified

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.utils.UIConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ==================== 淡入动画 ====================

@Composable
fun NeuFadeIn(
    visible: Boolean = true,
    durationMs: Int = NeuDefaults.animationDuration(),
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMs / 2)
        ) + slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = tween(durationMs / 2)
        )
    ) {
        content()
    }
}

// ==================== 滚动淡入（IntersectionObserver 等价） ====================

fun Modifier.scrollFadeIn(): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    this.then(
        Modifier.graphicsLayer {
            alpha = if (isVisible) 1f else 0f
            translationY = if (isVisible) 0f else 30f * density
        }
    )
}

// ==================== 弹性进入动画 ====================

@Composable
fun NeuElasticEnter(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elastic_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "elastic_alpha"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) {
        content()
    }
}

// ==================== 骨架屏 Shimmer ====================

fun Modifier.shimmerEffect(
    baseColor: Color = Color.LightGray.copy(alpha = 0.3f),
    highlightColor: Color = Color.LightGray.copy(alpha = 0.6f),
    durationMs: Int = 1200
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    this.drawBehind {
        val brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(translateX, 0f),
            end = Offset(translateX + size.width * 0.6f, size.height)
        )
        drawRect(brush = brush)
    }
}

// ==================== 卡片涟漪效果 ====================

fun Modifier.neuRipple(
    color: Color = NeuDefaults.GlowColor.copy(alpha = 0.3f)
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    var rippleOffset by remember { mutableStateOf(Offset.Zero) }
    var rippleRadius by remember { mutableFloatStateOf(0f) }
    var rippleAlpha by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            rippleOffset = down.position
            rippleRadius = 0f
            rippleAlpha = 0.5f

            scope.launch {
                val maxRadius = size.width.coerceAtLeast(size.height).toFloat()
                val anim = Animatable(0f)
                anim.animateTo(
                    targetValue = maxRadius,
                    animationSpec = tween(800, easing = FastOutSlowInEasing)
                )
                rippleRadius = maxRadius
            }
            scope.launch {
                val anim = Animatable(0.5f)
                anim.animateTo(0f, animationSpec = tween(800, easing = FastOutSlowInEasing))
                rippleAlpha = 0f
            }

            waitForUpOrCancellation()
        }
    }.drawWithContent {
        drawContent()
        if (rippleAlpha > 0f && rippleRadius > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = rippleAlpha),
                        Color.Transparent
                    ),
                    center = rippleOffset,
                    radius = rippleRadius
                ),
                radius = rippleRadius,
                center = rippleOffset
            )
        }
    }
}

// ==================== 汉堡菜单动画 ====================

@Composable
fun AnimatedHamburgerIcon(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidth: Dp = 2.dp,
    width: Dp = 18.dp,
    height: Dp = 14.dp
) {
    val rotation1 by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ham_rot1"
    )
    val alpha2 by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 1f,
        animationSpec = tween(200),
        label = "ham_alpha2"
    )
    val rotation3 by animateFloatAsState(
        targetValue = if (isExpanded) -45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ham_rot3"
    )
    val yOffset by animateDpAsState(
        targetValue = if (isExpanded) height / 2 - strokeWidth else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ham_yOffset"
    )

    Box(
        modifier = modifier.width(width).height(height),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = -yOffset)
                .width(width)
                .height(strokeWidth)
                .graphicsLayer { rotationZ = rotation1; transformOrigin = TransformOrigin.Center }
                .background(color, RoundedCornerShape(strokeWidth / 2))
        )
        Box(
            modifier = Modifier
                .width(width)
                .height(strokeWidth)
                .graphicsLayer { alpha = alpha2; scaleX = if (isExpanded) 0f else 1f }
                .background(color, RoundedCornerShape(strokeWidth / 2))
        )
        Box(
            modifier = Modifier
                .offset(y = yOffset)
                .width(width)
                .height(strokeWidth)
                .graphicsLayer { rotationZ = rotation3; transformOrigin = TransformOrigin.Center }
                .background(color, RoundedCornerShape(strokeWidth / 2))
        )
    }
}

// ==================== 长按弹出菜单 ====================

@Composable
fun NeuLongPressMenu(
    showMenu: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    items: List<Triple<String, String, (() -> Unit)?>> = emptyList(),
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()

        if (showMenu) {
            androidx.compose.material3.DropdownMenu(
                expanded = showMenu,
                onDismissRequest = onDismiss,
                modifier = Modifier
                    .neuCard(UIConfig.shouldUseDarkTheme(), NeuDefaults.currentIntensity())
                    .padding(4.dp)
            ) {
                items.forEach { (icon, label, action) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            action?.invoke()
                            onDismiss()
                        },
                        leadingIcon = {
                            Text(icon, fontSize = 14.sp)
                        }
                    )
                }
            }
        }
    }
}

// ==================== 滑动页面切换 ====================

@Composable
fun NeuPageSwitcher(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { if (currentPage > 0) it else -it },
                animationSpec = tween(NeuDefaults.animationDuration(), easing = FastOutSlowInEasing)
            ),
            exit = fadeOut() + androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { if (currentPage > 0) -it else it },
                animationSpec = tween(NeuDefaults.animationDuration() / 2)
            )
        ) {
            content(currentPage)
        }
    }
}

// ==================== 步进器长按重复 ====================

@Composable
fun RepeatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    initialDelay: Long = 500L,
    repeatDelay: Long = 120L,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)

    Box(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).also { it.consume() }
                currentOnClick()
                val job = scope.launch {
                    delay(initialDelay)
                    while (true) {
                        currentOnClick()
                        delay(repeatDelay)
                    }
                }
                try {
                    waitForUpOrCancellation()
                } finally {
                    job.cancel()
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ==================== 列表项光效扫描 ====================

fun Modifier.sweepHighlight(
    highlightColor: Color = Color.White.copy(alpha = 0.3f),
    durationMillis: Int = 1500
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "sweep")
    val translateX by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_translate"
    )

    this.drawWithContent {
        drawContent()
        val brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, highlightColor, Color.Transparent),
            start = Offset(x = size.width * translateX - size.width * 0.3f, y = 0f),
            end = Offset(x = size.width * translateX, y = size.height)
        )
        drawRect(brush = brush)
    }
}

// ==================== FAB 滚动隐藏 ====================

@Composable
fun NeuFAB(
    onClick: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .neuFAB(UIConfig.shouldUseDarkTheme(), NeuDefaults.currentIntensity())
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
