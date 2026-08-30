package com.UIN.Tool.ui.screen.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.UIConfig

@Composable
fun NeumorphismPreviewSection() {
    val isDark = UIConfig.shouldUseDarkTheme()
    val neuIntensity = currentNeuIntensity()

    UnifiedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            UnifiedBodyText(
                text = "新拟态预览",
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            UnifiedCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .neuCard(isDark, neuIntensity)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .neuRaised(CircleShape, isDark, neuIntensity)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                UnifiedCaptionText(
                                    text = "卡片",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .neuButton(isDark, neuIntensity)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(AppDimens.buttonCornerRadius))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "按钮",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = AppDimens.bodyTextSize.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        var previewSwitch by remember { mutableStateOf(true) }
                        UnifiedSwitch(
                            checked = previewSwitch,
                            onCheckedChange = { previewSwitch = it }
                        )
                        UnifiedCaptionText(
                            text = "开关",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neuInput(isDark, neuIntensity)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        UnifiedCaptionText(
                            text = "搜索插件...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("A", "B", "C").forEachIndexed { index, label ->
                            Box(
                                modifier = Modifier
                                    .neuChip(isDark, neuIntensity)
                                    .background(
                                        if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (index == 0) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontSize = AppDimens.captionTextSize.sp
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neuProgressTrack(isDark, neuIntensity)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}
