package com.UIN.Tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }

    val presetColors = listOf(
        Color(0xFF37474F), Color(0xFF263238), Color(0xFF546E7A),
        Color(0xFF607D8B), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF9C27B0),
        Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF795548),
        Color(0xFF9E9E9E), Color(0xFF212121), Color(0xFFFFFFFF),
        Color(0xFFE91E63)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(4.dp)
                        .background(selectedColor, RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                presetColors.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clickable { selectedColor = color }
                                    .background(color, RoundedCornerShape(4.dp))
                            )
                        }
                        repeat(4 - rowColors.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = String.format(
                        "#%02X%02X%02X",
                        (selectedColor.red * 255).toInt(),
                        (selectedColor.green * 255).toInt(),
                        (selectedColor.blue * 255).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedColor); onDismiss() }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}