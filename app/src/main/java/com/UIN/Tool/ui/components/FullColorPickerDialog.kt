// app/src/main/java/com/UIN/Tool/ui/components/FullColorPickerDialog.kt
package com.UIN.Tool.ui.components

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import com.UIN.Tool.ui.theme.AppDimens

/**
 * 完整颜色选择器 - 支持 RGB + Alpha 通道
 *
 * 使用固定白色容器背景与深色文字，避免跟随主题变色导致难以辨认。
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

    val currentColor = Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)
    val hexColor = String.format("#%02X%02X%02X%02X", alpha, red, green, blue)

    val onWhite = Color(0xFF212121)
    val onWhiteSecondary = Color(0xFF616F7E)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = Color.White,
            shape = RoundedCornerShape(AppDimens.radiusXXLarge),
            shadowElevation = 8.dp
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
                    color = onWhite
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

                // 颜色值显示
                Text(
                    text = hexColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = onWhite
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Red 滑块
                ColorSliderRow(
                    label = "R",
                    value = red,
                    onValueChange = { red = it },
                    labelColor = onWhite
                )

                // Green 滑块
                ColorSliderRow(
                    label = "G",
                    value = green,
                    onValueChange = { green = it },
                    labelColor = onWhite
                )

                // Blue 滑块
                ColorSliderRow(
                    label = "B",
                    value = blue,
                    onValueChange = { blue = it },
                    labelColor = onWhite
                )

                // Alpha 滑块
                ColorSliderRow(
                    label = "A",
                    value = alpha,
                    onValueChange = { alpha = it },
                    isAlpha = true,
                    labelColor = onWhiteSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 预设颜色
                Text(
                    text = Str.get(R.string.preset_colors),
                    fontSize = 12.sp,
                    color = onWhiteSecondary,
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
                                    .background(color, RoundedCornerShape(AppDimens.radiusSmall))
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

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Str.get(R.string.cancel), color = onWhite)
                }
            }
        }
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    isAlpha: Boolean = false,
    labelColor: Color = Color(0xFF212121)
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

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            steps = 255,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = if (isAlpha) Color(0xFF616F7E) else Color(0xFF1A3A4A),
                activeTrackColor = if (isAlpha) Color(0xFF616F7E) else Color(0xFF1A3A4A),
                inactiveTrackColor = Color(0xFFE0E4E8)
            )
        )

        Text(
            text = value.toString(),
            modifier = Modifier.width(32.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF212121)
        )
    }
}