package com.UIN.Tool.ui.components.unified

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTopAppBar(
    titleText: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val container = AppColors.pageBackground()
    val content = MaterialTheme.colorScheme.onBackground
    TopAppBar(
        title = {
            Text(titleText, color = content)
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = Str.get(R.string.back),
                        tint = content
                    )
                }
            }
        },
        actions = {
            CompositionLocalProvider(LocalContentColor provides content) {
                actions()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = container,
            titleContentColor = content,
            navigationIconContentColor = content,
            actionIconContentColor = content
        )
    )
}


/**
 * 当前时间字符串（HH:mm），用于“上次更新”展示
 */
fun currentTimeString(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())


/**
 * “上次更新: HH:mm” 说明文字，显示时淡入、1 秒后淡出
 */
@Composable
fun LastUpdatedCaption(time: String?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(time) {
        if (time != null) {
            visible = true
            delay(1000)
            visible = false
        } else {
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier
    ) {
        UnifiedCaptionText(text = Str.get(R.string.last_update_time, time ?: ""))
    }
}


/**
 * 下拉刷新指示器：使用主题色替换 Material 默认紫色背景
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefreshIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
    modifier: Modifier = Modifier
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface,
        color = MaterialTheme.colorScheme.primary
    )
}


/**
 * 骨架屏：插件/仓库列表加载占位，用呼吸闪烁的灰色卡片替代整屏转圈
 */
@Composable
fun PluginListSkeleton(
    itemCount: Int = 5,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(itemCount) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = RoundedCornerShape(AppDimens.cardCornerRadius),
                border = null,
                colors = CardDefaults.cardColors(
                    containerColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(AppDimens.radiusMedium))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(AppDimens.radiusMedium))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
                            )
                    )
                }
            }
        }
    }
}
