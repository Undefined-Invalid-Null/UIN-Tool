// app/src/main/java/com/UIN/Tool/ui/components/FullColorPickerDialog.kt
package com.UIN.Tool.ui.components

import android.graphics.Color as AndroidColor
import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf
import com.UIN.Tool.ui.components.unified.UnifiedDialogTextButton
import com.UIN.Tool.ui.components.unified.UnifiedSlider
import kotlin.math.roundToInt

/**
 * 完整颜色选择器 - 支持可视化取色 + RGB + Alpha 通道
 *
 * 容器背景跟随主题（玻璃效果或 MaterialTheme surface），文字使用主题色。
 */
@Composable
fun FullColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableStateOf((initialColor.red * 255).roundToInt()) }
    var green by remember { mutableStateOf((initialColor.green * 255).roundToInt()) }
    var blue by remember { mutableStateOf((initialColor.blue * 255).roundToInt()) }
    var alpha by remember { mutableStateOf((initialColor.alpha * 255).roundToInt()) }

    var hue by remember { mutableStateOf(toHsv(initialColor)[0]) }
    var saturation by remember { mutableStateOf(toHsv(initialColor)[1] * 100f) }
    var value by remember { mutableStateOf(toHsv(initialColor)[2] * 100f) }

    var hexInput by remember { mutableStateOf("") }

    fun updateHexInput() {
        hexInput = String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
    }

    fun parseHex(input: String) {
        val cleaned = input.trim().removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return
        val hexValue = cleaned.toLongOrNull(16) ?: return
        var a = alpha
        var r: Int
        var g: Int
        var b: Int
        if (cleaned.length == 8) {
            a = ((hexValue shr 24) and 0xFF).toInt()
            r = ((hexValue shr 16) and 0xFF).toInt()
            g = ((hexValue shr 8) and 0xFF).toInt()
            b = (hexValue and 0xFF).toInt()
        } else {
            r = ((hexValue shr 16) and 0xFF).toInt()
            g = ((hexValue shr 8) and 0xFF).toInt()
            b = (hexValue and 0xFF).toInt()
        }
        alpha = a
        red = r
        green = g
        blue = b
        val hsv = floatArrayOf(0f, 0f, 0f)
        AndroidColor.colorToHSV(
            AndroidColor.rgb(r, g, b),
            hsv
        )
        hue = hsv[0]
        saturation = hsv[1] * 100f
        value = hsv[2] * 100f
    }

    LaunchedEffect(red, green, blue, alpha) {
        updateHexInput()
    }

    val currentColor = Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    val applyHsv: (Float, Float, Float) -> Unit = { newHue, newSat, newVal ->
        hue = newHue
        saturation = newSat
        value = newVal
        val rgb = Color.hsv(newHue, newSat / 100f, newVal / 100f)
        red = (rgb.red * 255).roundToInt()
        green = (rgb.green * 255).roundToInt()
        blue = (rgb.blue * 255).roundToInt()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.radiusXXLarge))),
            color = Color.Transparent,
            shape = RoundedCornerShape(AppDimens.radiusXXLarge),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Str.get(R.string.full_color_picker),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 颜色预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(currentColor, RoundedCornerShape(AppDimens.radiusSmall))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 十六进制颜色值输入
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = {
                        val cleaned = it.trim().removePrefix("#").uppercase()
                        if (cleaned.length <= 8 && cleaned.all { c -> c.isDigit() || c in 'A'..'F' }) {
                            hexInput = "#$cleaned"
                            parseHex(hexInput)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(AppDimens.radiusSmall)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 可视化取色面板（饱和度 × 亮度）
                SVSquare(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    indicatorColor = currentColor,
                    onSaturationChange = { sat -> applyHsv(hue, sat, value) },
                    onValueChange = { v -> applyHsv(hue, saturation, v) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 色相条
                HueBar(
                    hue = hue,
                    onHueChange = { h -> applyHsv(h, saturation, value) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Red 滑块
                ColorSliderRow(
                    label = "R",
                    value = red,
                    onValueChange = { red = it }
                )

                // Green 滑块
                ColorSliderRow(
                    label = "G",
                    value = green,
                    onValueChange = { green = it }
                )

                // Blue 滑块
                ColorSliderRow(
                    label = "B",
                    value = blue,
                    onValueChange = { blue = it }
                )

                // Alpha 滑块
                ColorSliderRow(
                    label = "A",
                    value = alpha,
                    onValueChange = { alpha = it },
                    isAlpha = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 预设颜色
                Text(
                    text = Str.get(R.string.preset_colors),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                val presetColors = listOf(
                    Color(0xFF1A3A4A), Color(0xFF37474F), Color(0xFF263238),
                    Color(0xFF607D8B), Color(0xFF4CAF50), Color(0xFFFF9800),
                    Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF9C27B0),
                    Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF795548),
                    Color(0xFF9E9E9E), Color(0xFF212121), Color(0xFFFFFFFF),
                    Color(0xFFE91E63)
                )

                presetColors.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowColors.forEach { color ->
                            val swatchShape = RoundedCornerShape(AppDimens.radiusSmall)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clickable {
                                        red = (color.red * 255).roundToInt()
                                        green = (color.green * 255).roundToInt()
                                        blue = (color.blue * 255).roundToInt()
                                        alpha = (color.alpha * 255).roundToInt()
                                    }
                                    .then(
                                        if (color == Color.White) {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, swatchShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .background(color, swatchShape)
                            )
                        }
                        repeat(4 - rowColors.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onColorSelected(currentColor)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Str.get(R.string.ok_2))
                }

                UnifiedDialogTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Str.get(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbow = listOf(
        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onHueChange((offset.x / size.width * 360f).coerceIn(0f, 360f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onHueChange((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                    }
                )
            }
    ) {
        drawRect(brush = Brush.horizontalGradient(rainbow))
        val x = (hue / 360f * size.width).coerceIn(0f, size.width)
        drawLine(
            color = Color.White,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SVSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    indicatorColor: Color,
    onSaturationChange: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = Color.hsv(hue, 1f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onSaturationChange((offset.x / size.width * 100f).coerceIn(0f, 100f))
                        onValueChange(((1f - offset.y / size.height) * 100f).coerceIn(0f, 100f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onSaturationChange((change.position.x / size.width * 100f).coerceIn(0f, 100f))
                        onValueChange(((1f - change.position.y / size.height) * 100f).coerceIn(0f, 100f))
                    }
                )
            }
    ) {
        drawRect(baseColor)
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val x = saturation / 100f * size.width
        val y = (1f - value / 100f) * size.height
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, y))
        drawCircle(indicatorColor, radius = 5.dp.toPx(), center = Offset(x, y))
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    isAlpha: Boolean = false,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    valueRange: ClosedFloatingPointRange<Float> = 0f..255f,
    steps: Int = 255
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(20.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )

        UnifiedSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value.toString(),
            modifier = Modifier.width(32.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun toHsv(color: Color): FloatArray {
    val hsv = floatArrayOf(0f, 0f, 0f)
    AndroidColor.colorToHSV(
        AndroidColor.rgb(
            (color.red * 255).roundToInt(),
            (color.green * 255).roundToInt(),
            (color.blue * 255).roundToInt()
        ),
        hsv
    )
    return hsv
}