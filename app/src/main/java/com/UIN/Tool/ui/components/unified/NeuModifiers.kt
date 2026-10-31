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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
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

/**
 * 批次60（收敛）：按 [shape] 的轮廓把 [color] 铺满当前绘制区 —— 即「按形状裁剪填充」，
 * 供 [neuRaised]/[neuInset] 的底色（backgroundColor）使用。Rectangle / Rounded 是本工程
 * 全部调用点（RoundedCornerShape、CircleShape、0 圆角），Generic 为自定义路径轮廓，
 * 原样并入、不猜形状。只在底色不透明时才调用，避免每帧白建 Path。
 */
private fun ContentDrawScope.fillShape(shape: Shape, color: Color) {
    val path = Path()
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }
    drawPath(path, color)
}

/**
 * 新拟态**凸起**：先画双侧柔影（亮影与暗影各偏移一次），再按 [backgroundColor] 在
 * 影与内容之间铺一层底，最后画内容。
 *
 * 参数语义（6.x 收敛，SE 上报「shape/backgroundColor 只声明不使用」后补齐）：
 *  - [cornerRadius] —— **阴影几何**：阴影按 `size ± offset` 的圆角矩形绘制，圆角取此值；
 *  - [shape] —— **铺底形状**：[backgroundColor] 按 shape 的 Outline 轮廓填充（等价于
 *    按形状裁剪），调用点几乎都传与 [cornerRadius] 同值的圆角，两者视觉一致；
 *  - [backgroundColor] —— 铺在**影与内容之间**的一层底色。默认 `Color.Transparent`
 *    = 不铺底，与历史渲染逐像素一致（阴影本身已画出不透明底板），只有显式传色的
 *    调用点才会多出这一层 —— 别把默认值改回不透明色，否则玻璃/渐变底的调用点会被盖掉。
 *
 * 阴影仍画在最外层：调用方必须把它放在滚动容器/clip **外层**（见 CodeEditorScreen
 * 的 neuChrome 规则），否则阴影会被裁掉。
 *
 * [punchInterior]（批次61，用户「编辑器背景依旧用渐变背景，不要发灰」）：影色是**不透明**
 * 的（亮影 #FFFFFF/#D9D9D9、暗影 #2C2C2C/#111111），整块底板会把透明 chrome 下的页面
 * 渐变盖成灰条。置 true 时按内容圆角矩形把底板**中心挖空**（`ClipOp.Difference` 裁剪差集），只留四周一圈柔影 ——
 * 内部透出渐变/玻璃，外圈仍是凸起阴影，等价于「浮在渐变上的凸起」。默认 false，
 * 存量调用点（不透明底的卡片/按钮，底板本来就被自己的底色盖住）渲染不变。
 */
fun Modifier.neuRaised(
    shape: Shape,
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.Intensity.MEDIUM,
    cornerRadius: Dp = 0.dp,
    backgroundColor: Color = Color.Transparent,
    shadowColorLight: Color = if (isDark) NeuDefaults.DarkShadowLight else NeuDefaults.LightShadowLight,
    shadowColorDark: Color = if (isDark) NeuDefaults.DarkShadowDark else NeuDefaults.LightShadowDark,
    blurRadius: Float = 4f,
    lightSource: Int = NeuLightSource.DEFAULT,
    offset: Float = 5f,
    punchInterior: Boolean = false
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
            if (punchInterior) {
                // 批次61：把「内容圆角矩形」从裁剪区挖掉（ClipOp.Difference），影子只落在
                // 外圈 —— 内部不落任何影色，页面渐变/玻璃原样透出（等价挖空底板中心，
                // 且无需离屏图层）。save/restore 包住裁剪，不影响后续 fillShape/drawContent。
                val r = cornerRadius.toPx()
                val interior = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
                }
                it.save()
                it.clipPath(interior, ClipOp.Difference)
                it.drawRoundRect(
                    lightOffset.x, lightOffset.y,
                    this.size.width + lightOffset.x, this.size.height + lightOffset.y,
                    r, r,
                    paintShadowLight
                )
                it.drawRoundRect(
                    darkOffset.x, darkOffset.y,
                    this.size.width + darkOffset.x, this.size.height + darkOffset.y,
                    r, r,
                    paintShadowDark
                )
                it.restore()
            } else {
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
        }
        // 批次60 收敛：补上 backgroundColor 语义 —— 按 shape 的轮廓在**影与内容之间**
        // 铺一层底色；默认透明 = 不铺底（与历史渲染一致），仅显式传色的调用点生效。
        if (backgroundColor.alpha > 0f) {
            fillShape(shape, backgroundColor)
        }
        drawContent()
    }
})

/**
 * 新拟态**内凹**：先按 [backgroundColor] 在内容之下铺一层底，画完内容后再在**内容之上**
 * 画双侧描边（描边被 [cornerRadius] 的路径裁在内侧，形成内凹阴影）。
 *
 * 参数语义与 [neuRaised] 一致：[cornerRadius] 决定阴影几何、[shape] 决定 [backgroundColor]
 * 的铺底形状；默认透明 = 不铺底（与历史渲染一致 —— neuInset 只描边、本就不画底板）。
 * 调用方同样要把本修饰符放在滚动容器/clip 外层。
 */
fun Modifier.neuInset(
    shape: Shape,
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.Intensity.MEDIUM,
    cornerRadius: Dp = 0.dp,
    backgroundColor: Color = Color.Transparent,
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

        // 批次60 收敛：补上 backgroundColor 语义 —— 铺底必须在内容**之前**（内凹描边画在
        // 内容之上，铺底若放在其后会盖住描边与内容）；默认透明 = 不铺底，历史渲染不变。
        if (backgroundColor.alpha > 0f) {
            fillShape(shape, backgroundColor)
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

/**
 * 新拟态风格下的 **chrome 凸起**（自绘顶栏/标签栏/搜索面板/底栏/符号栏/文件树这类条状、
 * 卡片状外壳）：非新拟态风格原样返回，布局与旧版逐像素一致；新拟态风格下叠一层
 * [neuRaised] 双侧柔影（亮度取 LIGHT、底色恒 [Color.Transparent]，不额外铺底）。
 *
 * 6.2.0 批次58（用户）提出，原本内联在 CodeEditorScreen；批次60 收敛时下沉到 unified，
 * 供其他自绘外壳复用（调用点同步迁移，行为不变）。
 *
 * **修饰符顺序铁律（批次59 修标签栏/符号栏时定，改调用点前先读）**：本修饰必须放在
 * **滚动容器（horizontalScroll / verticalScroll）与 clip 的外层、background 的内层之前**：
 * `Modifier.fillMaxWidth().neuChrome().horizontalScroll(state).background(color)`。
 * 放进滚动容器内层时阴影会落在滚动内容的边界上 —— 滚动容器把它裁掉、紧随其后的
 * pageBackground 又把留在内部的部分盖住，整条「凸起」实际画不出来。
 *
 * 注（批次61 改口径，用户「编辑器背景依旧用渐变背景，不要发灰」）：[neuRaised] 的柔影
 * 原本是一块不透明底板（亮影 #FFFFFF/#D9D9D9），会把渐变开启时的页面底色盖成灰条 ——
 * 现改为 `punchInterior = true` **挖空底板中心**：chrome 内部透出页面渐变/玻璃，
 * 只有外圈保留一圈柔影，「凸起」与渐变同时成立。
 */
fun Modifier.neuChrome(
    shape: Shape = RoundedCornerShape(0.dp),
    cornerRadius: Dp = 0.dp
): Modifier =
    if (UIConfig.isNeumorphismEnabled()) {
        then(
            Modifier.neuRaised(
                shape = shape,
                isDark = UIConfig.shouldUseDarkTheme(),
                intensity = NeuDefaults.Intensity.LIGHT,
                cornerRadius = cornerRadius,
                backgroundColor = Color.Transparent,
                punchInterior = true
            )
        )
    } else {
        this
    }

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
    // 默认透明：省略该参数的调用点保持原样（此前该参数根本未被绘制）
    backgroundColor: Color = Color.Transparent
): Modifier = this.neuRaised(RoundedCornerShape(14.dp), isDark, intensity, cornerRadius = 14.dp, backgroundColor = backgroundColor)

@Composable
fun Modifier.neuButton(
    isDark: Boolean = false,
    intensity: NeuDefaults.Intensity = NeuDefaults.Intensity.MEDIUM,
    // 默认透明：省略该参数的调用点保持原样（此前该参数根本未被绘制）
    backgroundColor: Color = Color.Transparent
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
