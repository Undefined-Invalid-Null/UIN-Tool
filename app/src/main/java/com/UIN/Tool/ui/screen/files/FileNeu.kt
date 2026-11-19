// app/src/main/java/com/UIN/Tool/ui/screen/files/FileNeu.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.UIN.Tool.ui.components.unified.NeuDefaults
import com.UIN.Tool.ui.components.unified.neuInset
import com.UIN.Tool.ui.components.unified.neuRaised
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.utils.UIConfig

/**
 * 文件管理器页面的**统一新拟态底板**（l.txt 第 15 条）。
 *
 * 背景里已经有一套 `UnifiedCard` / `UnifiedButton` 会在开启新拟态时自动加凸起，
 * 但文件管理器还有不少地方用的是裸 `Row` + `surfaceVariant` 色块（打开方式卡片、
 * 回收站行、任务卡片、文件操作格…）。这些地方各画各的，就会出现「一半浮起、
 * 一半还是贴纸」的割裂感。这里收成一处：
 *
 * - 关闭新拟态 → 原样返回，调用方的旧样式（配色 / 圆角）完全不变；
 * - 开启新拟态 → 先画双侧柔影，再按形状裁剪铺一层不透明底（玻璃开关只影响色相，
 *   不影响 alpha —— 浮起的板块必须自己是一张「面」，否则影子落在透明层上会发灰）。
 *
 * [pressed] 供可点条目在被按下时改用内凹效果，与 `UnifiedCard` 的按感一致。
 */
@Composable
internal fun Modifier.filesNeuSurface(
    shape: Shape = RoundedCornerShape(14.dp),
    cornerRadius: Dp = 14.dp,
    pressed: Boolean = false,
    fill: Color? = null,
    /**
     * 关闭新拟态时是否仍铺一层平底色。旧页面本来就是「圆角 + surfaceVariant 色块」，
     * 关掉新拟态不该把这些板块变成全透明 —— 传原来的色值即可保持旧观感；
     * 不传则原样返回（调用方自己已经铺过底）。
     */
    fallback: Color? = null
): Modifier {
    if (!UIConfig.isNeumorphismEnabled()) {
        return if (fallback == null) this
        else this.clip(shape).background(fallback, shape)
    }
    val isDark = UIConfig.shouldUseDarkTheme()
    val base = fill ?: if (AppColors.glassEnabled()) {
        AppColors.glassBackground()
    } else {
        MaterialTheme.colorScheme.surface
    }
    // 玻璃底的 alpha 只有 0.5，影子会透上来变脏 —— 底板钉到不透明，只借它的色相。
    val solid = base.copy(alpha = 1f)
    return this
        .then(
            if (pressed) {
                Modifier.neuInset(
                    shape = shape,
                    isDark = isDark,
                    intensity = NeuDefaults.Intensity.LIGHT,
                    cornerRadius = cornerRadius,
                    backgroundColor = Color.Transparent
                )
            } else {
                Modifier.neuRaised(
                    shape = shape,
                    isDark = isDark,
                    intensity = NeuDefaults.Intensity.LIGHT,
                    cornerRadius = cornerRadius,
                    backgroundColor = Color.Transparent
                )
            }
        )
        .clip(shape)
        .background(solid, shape)
}

/** 圆形新拟态底板（拿起后的粘贴 / 关闭圆钮等）。 */
@Composable
internal fun Modifier.filesNeuCircle(pressed: Boolean = false): Modifier =
    filesNeuSurface(shape = CircleShape, cornerRadius = 26.dp, pressed = pressed)