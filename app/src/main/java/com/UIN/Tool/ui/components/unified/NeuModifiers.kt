package com.UIN.Tool.ui.components.unified

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.UIN.Tool.utils.UIConfig

object NeuLightSource {
    const val LEFT_TOP = 0
    const val LEFT_BOTTOM = 1
    const val RIGHT_TOP = 2
    const val RIGHT_BOTTOM = 3
    const val DEFAULT = LEFT_TOP

    fun opposite(lightSource: Int): Int = when (lightSource) {
        LEFT_TOP -> RIGHT_BOTTOM
        RIGHT_TOP -> LEFT_BOTTOM
        LEFT_BOTTOM -> RIGHT_TOP
        RIGHT_BOTTOM -> LEFT_TOP
        else -> -1
    }
}

object NeuDefaults {
    val LightShadowLight = Color(0xFFFFFFFF)
    val LightShadowDark = Color(0xFFD9D9D9)
    val DarkShadowLight = Color(0xFF2C2C2C)
    val DarkShadowDark = Color(0xFF111111)
    val LightBackground = Color(0xFFECF0F3)
    val DarkBackground = Color(0xFF303234)
    val GlowColor = Color(0xFF1A3A4A)

    enum class Intensity(val factor: Float) {
        LIGHT(0.6f),
        MEDIUM(1.0f),
        STRONG(1.4f)
    }

    enum class AnimationSpeed(val multiplier: Float) {
        FAST(0.6f),
        MEDIUM(1.0f),
        SLOW(1.5f)
    }

    @Composable
    fun currentIntensity(): Intensity {
        val raw = UIConfig.getNeumorphismIntensity()
        return when (raw) {
            "light" -> Intensity.LIGHT
            "strong" -> Intensity.STRONG
            else -> Intensity.MEDIUM
        }
    }

    @Composable
    fun currentSpeed(): AnimationSpeed {
        val raw = UIConfig.getAnimationSpeed()
        return when (raw) {
            "fast" -> AnimationSpeed.FAST
            "slow" -> AnimationSpeed.SLOW
            else -> AnimationSpeed.MEDIUM
        }
    }

    @Composable
    fun animationDuration(): Int = (300 * currentSpeed().multiplier).toInt()

    @Composable
    fun springSpec(): SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
}

fun Modifier.neuRaised(
    shape: Shape,
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.Intensity.MEDIUM,
    cornerRadius: Dp = 0.dp,
    backgroundColor: Color = if (isDark) NeuDefaults.DarkBackground else NeuDefaults.LightBackground,
    shadowColorLight: Color = if (isDark) NeuDefaults.DarkShadowLight else NeuDefaults.LightShadowLight,
    shadowColorDark: Color = if (isDark) NeuDefaults.DarkShadowDark else NeuDefaults.LightShadowDark,
    blurRadius: Float = 4f,
    lightSource: Int = NeuLightSource.DEFAULT,
    offset: Float = 5f
): Modifier = then(object : DrawModifier {
    override fun ContentDrawScope.draw() {
        val effectiveOffset = offset * intensity.factor
        val paintShadowLight = Paint().also { paint ->
            paint.asFrameworkPaint().also { nativePaint ->
                nativePaint.isAntiAlias = true
                nativePaint.isDither = true
                nativePaint.color = shadowColorLight.toArgb()
                if (effectiveOffset > 0) nativePaint.maskFilter = BlurMaskFilter(blurRadius * intensity.factor, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val lightOffset = when (lightSource) {
            NeuLightSource.LEFT_TOP -> Offset(-effectiveOffset, -effectiveOffset)
            NeuLightSource.LEFT_BOTTOM -> Offset(-effectiveOffset, effectiveOffset)
            NeuLightSource.RIGHT_TOP -> Offset(effectiveOffset, -effectiveOffset)
            NeuLightSource.RIGHT_BOTTOM -> Offset(effectiveOffset, effectiveOffset)
            else -> Offset(0f, 0f)
        }
        val paintShadowDark = Paint().also { paint ->
            paint.asFrameworkPaint().also { nativePaint ->
                nativePaint.isAntiAlias = true
                nativePaint.isDither = true
                nativePaint.color = shadowColorDark.toArgb()
                if (effectiveOffset > 0) nativePaint.maskFilter = BlurMaskFilter(blurRadius * intensity.factor, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val darkOffset = when (NeuLightSource.opposite(lightSource)) {
            NeuLightSource.LEFT_TOP -> Offset(-effectiveOffset, -effectiveOffset)
            NeuLightSource.LEFT_BOTTOM -> Offset(-effectiveOffset, effectiveOffset)
            NeuLightSource.RIGHT_TOP -> Offset(effectiveOffset, -effectiveOffset)
            NeuLightSource.RIGHT_BOTTOM -> Offset(effectiveOffset, effectiveOffset)
            else -> Offset(0f, 0f)
        }
        drawIntoCanvas {
            it.drawRoundRect(
                lightOffset.x, lightOffset.y,
                this.size.width + lightOffset.x, this.size.height + lightOffset.y,
                cornerRadius.toPx(), cornerRadius.toPx(),
                paintShadowLight
            )
            it.drawRoundRect(
                darkOffset.x, darkOffset.y,
                this.size.width + darkOffset.x, this.size.height + darkOffset.y,
                cornerRadius.toPx(), cornerRadius.toPx(),
                paintShadowDark
            )
        }
        drawContent()
    }
})

fun Modifier.neuInset(
    shape: Shape,
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.Intensity.MEDIUM,
    cornerRadius: Dp = 0.dp,
    backgroundColor: Color = if (isDark) NeuDefaults.DarkBackground else NeuDefaults.LightBackground,
    shadowColorLight: Color = if (isDark) NeuDefaults.DarkShadowLight else NeuDefaults.LightShadowLight,
    shadowColorDark: Color = if (isDark) NeuDefaults.DarkShadowDark else NeuDefaults.LightShadowDark,
    blurRadius: Float = 4f,
    lightSource: Int = NeuLightSource.DEFAULT,
    offset: Float = 5f
): Modifier = then(object : DrawModifier {
    override fun ContentDrawScope.draw() {
        val effectiveOffset = offset * intensity.factor
        val effectiveBlur = blurRadius * intensity.factor
        val radius = cornerRadius.toPx()

        val paintShadowLight = Paint().also { paint ->
            paint.asFrameworkPaint().also { nativePaint ->
                nativePaint.isAntiAlias = true
                nativePaint.isDither = true
                nativePaint.color = shadowColorLight.toArgb()
                nativePaint.style = android.graphics.Paint.Style.STROKE
                nativePaint.strokeWidth = effectiveOffset
                if (effectiveOffset > 0) nativePaint.maskFilter =
                    BlurMaskFilter(effectiveBlur, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val paintShadowDark = Paint().also { paint ->
            paint.asFrameworkPaint().also { nativePaint ->
                nativePaint.isAntiAlias = true
                nativePaint.isDither = true
                nativePaint.color = shadowColorDark.toArgb()
                nativePaint.style = android.graphics.Paint.Style.STROKE
                nativePaint.strokeWidth = effectiveOffset
                if (effectiveOffset > 0) nativePaint.maskFilter =
                    BlurMaskFilter(effectiveBlur, BlurMaskFilter.Blur.NORMAL)
            }
        }

        val darkOffset = when (lightSource) {
            NeuLightSource.LEFT_TOP -> Offset(effectiveOffset, effectiveOffset)
            NeuLightSource.LEFT_BOTTOM -> Offset(effectiveOffset, -effectiveOffset)
            NeuLightSource.RIGHT_TOP -> Offset(-effectiveOffset, effectiveOffset)
            NeuLightSource.RIGHT_BOTTOM -> Offset(-effectiveOffset, -effectiveOffset)
            else -> Offset(0f, 0f)
        }
        val lightOffset = when (NeuLightSource.opposite(lightSource)) {
            NeuLightSource.LEFT_TOP -> Offset(effectiveOffset, effectiveOffset)
            NeuLightSource.LEFT_BOTTOM -> Offset(effectiveOffset, -effectiveOffset)
            NeuLightSource.RIGHT_TOP -> Offset(-effectiveOffset, effectiveOffset)
            NeuLightSource.RIGHT_BOTTOM -> Offset(-effectiveOffset, -effectiveOffset)
            else -> Offset(0f, 0f)
        }

        drawContent()

        drawIntoCanvas {
            it.save()
            val path = Path().also { p ->
                p.addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius, radius))
            }
            it.clipPath(path)
            it.translate(darkOffset.x, darkOffset.y)
            it.drawRoundRect(
                -effectiveOffset, -effectiveOffset,
                size.width + effectiveOffset, size.height + effectiveOffset,
                radius, radius,
                paintShadowDark
            )
            it.restore()

            it.save()
            val path2 = Path().also { p ->
                p.addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius, radius))
            }
            it.clipPath(path2)
            it.translate(lightOffset.x, lightOffset.y)
            it.drawRoundRect(
                -effectiveOffset, -effectiveOffset,
                size.width + effectiveOffset, size.height + effectiveOffset,
                radius, radius,
                paintShadowLight
            )
            it.restore()
        }
    }
})

fun Modifier.neuGlow(
    color: Color = NeuDefaults.GlowColor,
    radius: Dp = 8.dp,
    alpha: Float = 0.05f
): Modifier = this.drawBehind {
    drawCircle(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.3f),
                Color.Transparent
            ),
            radius = radius.toPx() * 2
        ),
        radius = radius.toPx() * 2
    )
}

@Composable
fun Modifier.neuPressable(
    shape: Shape,
    interactionSource: MutableInteractionSource,
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity(),
    cornerRadius: Dp = 0.dp,
    onPressScale: Float = 0.97f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) onPressScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "neu_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.then(
        if (isPressed) Modifier.neuInset(shape, isDark, intensity, cornerRadius)
        else Modifier.neuRaised(shape, isDark, intensity, cornerRadius)
    )
}

@Composable
fun Modifier.neuCard(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.Intensity.MEDIUM,
    backgroundColor: Color = if (isDark) NeuDefaults.DarkBackground else NeuDefaults.LightBackground
): Modifier = this.neuRaised(RoundedCornerShape(14.dp), isDark, intensity, cornerRadius = 14.dp, backgroundColor = backgroundColor)

@Composable
fun Modifier.neuButton(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.Intensity.MEDIUM,
    backgroundColor: Color = if (isDark) NeuDefaults.DarkBackground else NeuDefaults.LightBackground
): Modifier = this.neuRaised(RoundedCornerShape(12.dp), isDark, intensity, cornerRadius = 12.dp, backgroundColor = backgroundColor)

@Composable
fun Modifier.neuInput(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuInset(RoundedCornerShape(12.dp), isDark, intensity, cornerRadius = 12.dp)

@Composable
fun Modifier.neuSwitch(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuInset(RoundedCornerShape(15.dp), isDark, intensity, cornerRadius = 15.dp)

@Composable
fun Modifier.neuChip(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(RoundedCornerShape(20.dp), isDark, intensity, cornerRadius = 20.dp, blurRadius = 4f, offset = 4f)

@Composable
fun Modifier.neuFAB(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(CircleShape, isDark, intensity, cornerRadius = 24.dp)

@Composable
fun Modifier.neuProgressTrack(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuInset(RoundedCornerShape(4.dp), isDark, intensity, cornerRadius = 4.dp)

@Composable
fun Modifier.neuListItem(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(RoundedCornerShape(14.dp), isDark, intensity, cornerRadius = 14.dp)

@Composable
fun Modifier.neuToolbar(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(RoundedCornerShape(12.dp), isDark, intensity, cornerRadius = 12.dp)

@Composable
fun Modifier.neuAvatar(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(CircleShape, isDark, intensity, cornerRadius = 20.dp)

@Composable
fun Modifier.neuBadge(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(RoundedCornerShape(10.dp), isDark, intensity, cornerRadius = 10.dp)

@Composable
fun Modifier.neuStepper(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(RoundedCornerShape(12.dp), isDark, intensity, cornerRadius = 12.dp)

@Composable
fun Modifier.neuSliderTrack(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuInset(RoundedCornerShape(4.dp), isDark, intensity, cornerRadius = 4.dp)

@Composable
fun Modifier.neuSliderThumb(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.currentIntensity()
): Modifier = this.neuRaised(CircleShape, isDark, intensity, cornerRadius = 12.dp)
