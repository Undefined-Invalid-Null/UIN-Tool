package com.UIN.Tool.ui.components.unified

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens

// ==================== Speed Dial ====================

@Composable
fun SpeedDial(
    items: List<SpeedDialItem>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "fab_rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (expanded) {
            items.forEachIndexed { index, item ->
                val delay = (items.size - 1 - index) * 50
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(expanded) {
                    if (expanded) {
                        kotlinx.coroutines.delay(delay.toLong())
                        visible = true
                    } else {
                        visible = false
                    }
                }
                val scale by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "dial_item_$index"
                )
                Row(
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (item.label.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .shadow(3.dp, RoundedCornerShape(8.dp))
                                .background(AppColors.cardBackground(), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = item.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(4.dp, CircleShape)
                            .background(AppColors.cardBackground(), CircleShape)
                            .clickable {
                                expanded = false
                                item.onClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = item.icon, fontSize = 18.sp)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(6.dp, CircleShape)
                .background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                    CircleShape
                )
                .clickable { expanded = expanded.not() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

data class SpeedDialItem(
    val icon: String,
    val label: String,
    val onClick: () -> Unit
)

// ==================== Notification Panel ====================

data class NotificationItem(
    val title: String,
    val time: String,
    val color: Color,
    val onClick: () -> Unit = {}
)

@Composable
fun NotificationPanel(
    items: List<NotificationItem>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppDimens.cardCornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), shape)
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeuStatusIndicator(color = item.color)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(text = item.time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index < items.lastIndex) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

// ==================== Skeleton Loading ====================

@Composable
fun SkeletonItem(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f
) {
    val shape = RoundedCornerShape(AppDimens.radiusSmall)
    val shimmerColors = listOf(
        AppColors.cardBackground(),
        AppColors.cardBackground().copy(alpha = 0.6f),
        AppColors.cardBackground()
    )
    val transition = rememberInfiniteTransition(label = "skeleton")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_shimmer"
    )
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateAnim - 200f, 0f),
                    end = Offset(translateAnim, 0f)
                )
            )
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(AppDimens.cardCornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), shape)
            .padding(16.dp)
    ) {
        SkeletonItem(widthFraction = 0.55f)
        Spacer(Modifier.height(12.dp))
        SkeletonItem(widthFraction = 0.85f)
        Spacer(Modifier.height(10.dp))
        SkeletonItem(widthFraction = 0.7f)
    }
}

@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(AppDimens.radiusMedium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMedium))
                .background(AppColors.cardBackground().copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonItem(widthFraction = 0.6f)
            Spacer(Modifier.height(8.dp))
            SkeletonItem(widthFraction = 0.4f)
        }
    }
}

// ==================== Elastic Overscroll ====================

fun Modifier.elasticOverscroll(
    lazyListState: LazyListState,
    maxStretch: Float = 0.05f
): Modifier = composed {
    var overscroll by remember { mutableFloatStateOf(0f) }

    Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = { overscroll = 0f },
            onDragCancel = { overscroll = 0f },
            onVerticalDrag = { change, dragAmount ->
                val canStretchUp = lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0 && dragAmount > 0
                val canStretchDown = !lazyListState.canScrollBackward && dragAmount < 0
                if (canStretchUp || canStretchDown) {
                    overscroll = (overscroll + dragAmount * 0.35f).coerceIn(-maxStretch * size.height, maxStretch * size.height)
                    change.consume()
                }
            }
        )
    }.graphicsLayer {
        if (overscroll != 0f) {
            scaleY = 1f + overscroll
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                pivotFractionX = 0.5f,
                pivotFractionY = if (overscroll > 0) 0f else 1f
            )
        } else {
            scaleY = 1f
        }
    }
}
