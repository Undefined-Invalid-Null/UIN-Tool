package com.UIN.Tool.ui.screen.manage.uiconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.components.unified.UnifiedSlider
import com.UIN.Tool.ui.components.unified.NeuDefaults
import com.UIN.Tool.ui.components.unified.UnifiedSwitch
import com.UIN.Tool.ui.components.unified.neuRaised
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.UIConfig
import kotlin.math.roundToInt

@Composable
fun NumericPropertyRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String = "dp",
    step: Float = 1f
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryLight = MaterialTheme.colorScheme.primary
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    var textValue by remember(value) {
        mutableStateOf(value.roundToInt().toString())
    }

    val rowShape = RoundedCornerShape(AppDimens.cardCornerRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (neu) Modifier.neuRaised(rowShape, isDark, NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent) else Modifier)
            .clip(rowShape)
            .background(surfaceColor)
            .padding(AppDimens.cardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = AppDimens.bodyTextSize.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary
            )
            Text(
                text = "${value.roundToInt()} $unit",
                fontSize = AppDimens.captionTextSize.sp,
                color = primaryLight,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spacingSmall))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnifiedSlider(
                value = value,
                onValueChange = {
                    val stepped = (it / step).roundToInt() * step
                    val clamped = stepped.coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(clamped)
                    textValue = clamped.roundToInt().toString()
                },
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(40.dp)
                    .border(1.dp, outlineColor, RoundedCornerShape(AppDimens.inputCornerRadius))
                    .background(surfaceColor, RoundedCornerShape(AppDimens.inputCornerRadius))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() || it == '-' }
                        textValue = cleaned
                        val parsed = cleaned.toFloatOrNull()
                        if (parsed != null) {
                            val clamped = parsed.roundToInt().toFloat().coerceIn(
                                valueRange.start,
                                valueRange.endInclusive
                            )
                            onValueChange(clamped)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(primaryLight),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { innerTextField -> innerTextField() }
                )
            }
        }
    }
}

@Composable
fun DropdownPropertyRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryLight = MaterialTheme.colorScheme.primary
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    var expanded by remember { mutableStateOf(false) }
    val displayName = options.find { it.first == value }?.second ?: value

    val rowShape = RoundedCornerShape(AppDimens.cardCornerRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (neu) Modifier.neuRaised(rowShape, isDark, NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent) else Modifier)
            .clip(rowShape)
            .background(surfaceColor)
            .padding(AppDimens.cardPadding)
    ) {
        Text(
            text = label,
            fontSize = AppDimens.bodyTextSize.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            modifier = Modifier.padding(bottom = AppDimens.spacingSmall)
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimens.inputCornerRadius))
                    .background(surfaceColor)
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    fontSize = 15.sp,
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(AppDimens.inputCornerRadius))
            ) {
                options.forEach { (optionValue, optionName) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionName,
                                fontSize = AppDimens.captionTextSize.sp,
                                color = if (optionValue == value) primaryLight else textPrimary,
                                fontWeight = if (optionValue == value) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onValueChange(optionValue)
                            expanded = false
                        },
                        trailingIcon = if (optionValue == value) {
                            {
                                Text("✓", color = primaryLight, fontSize = 14.sp)
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPropertyRow(
    label: String,
    colorValue: String,
    onClick: () -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    val parsedColor = try {
        if (colorValue.isNotEmpty() && colorValue.startsWith("#") && colorValue.length >= 7) {
            Color(android.graphics.Color.parseColor(colorValue))
        } else {
            Color(0xFF1A3A4A)
        }
    } catch (e: Exception) {
        Color(0xFF1A3A4A)
    }

    val rowShape = RoundedCornerShape(AppDimens.cardCornerRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (neu) Modifier.neuRaised(rowShape, isDark, NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent) else Modifier)
            .clip(rowShape)
            .background(surfaceColor)
            .clickable { onClick() }
            .padding(AppDimens.cardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = AppDimens.bodyTextSize.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacingSmall)
            ) {
                Text(
                    text = colorValue,
                    fontSize = AppDimens.captionTextSize.sp,
                    color = textPrimary.copy(alpha = 0.6f)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(parsedColor)
                        .border(
                            width = 1.dp,
                            color = textPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun BooleanPropertyRow(
    label: String,
    description: String = "",
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    val rowShape = RoundedCornerShape(AppDimens.cardCornerRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (neu) Modifier.neuRaised(rowShape, isDark, NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent) else Modifier)
            .clip(rowShape)
            .background(surfaceColor)
            .clickable { onValueChange(!value) }
            .padding(AppDimens.cardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = AppDimens.bodyTextSize.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                )
                if (description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppDimens.spacingXSmall))
                    Text(
                        text = description,
                        fontSize = AppDimens.captionTextSize.sp,
                        color = textSecondary
                    )
                }
            }
            UnifiedSwitch(
                checked = value,
                onCheckedChange = onValueChange,
                enabled = true
            )
        }
    }
}
