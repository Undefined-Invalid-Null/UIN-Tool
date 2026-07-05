// app/src/main/java/com/UIN/Tool/ui/components/FullColorPickerDialog.kt
package com.UIN.Tool.ui.components

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
import kotlin.math.roundToInt

/**
 * 完整颜色选择器 - 支持 RGB + Alpha 通道
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
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "完整颜色选择器",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 颜色预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(4.dp)
                        .background(currentColor, RoundedCornerShape(8.dp))
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 颜色值显示
                Text(
                    text = hexColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
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
                    text = "预设颜色",
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
                                    .background(color, RoundedCornerShape(4.dp))
                            )
                        }
                        repeat(4 - rowColors.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onColorSelected(currentColor)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    isAlpha: Boolean = false
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
            color = if (isAlpha) Color.Gray else MaterialTheme.colorScheme.onSurface
        )
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            steps = 255,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = if (isAlpha) Color.Gray else MaterialTheme.colorScheme.primary,
                activeTrackColor = if (isAlpha) Color.Gray else MaterialTheme.colorScheme.primary
            )
        )
        
        Text(
            text = value.toString(),
            modifier = Modifier.width(32.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}