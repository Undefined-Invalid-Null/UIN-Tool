package com.UIN.Tool.ui.components.unified

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.isAppDarkTheme
import com.UIN.Tool.utils.UIConfig

object NeuColors {
    @Composable
    fun shadowDark(): Color = if (UIConfig.shouldUseDarkTheme()) Color(0xA6000000) else Color(0x1F000000)
    @Composable
    fun shadowLight(): Color = if (UIConfig.shouldUseDarkTheme()) Color(0x08FFFFFF) else Color(0xD9FFFFFF)
    @Composable
    fun glow(): Color = Color(0x594A8A9E)
    @Composable
    fun glowLight(): Color = Color(0x144A8A9E)
}

@Composable
fun currentNeuIntensity(): NeuDefaults.Intensity = UIConfig.getNeumorphismIntensity().let {
    when (it) {
        "light" -> NeuDefaults.Intensity.LIGHT
        "strong" -> NeuDefaults.Intensity.STRONG
        else -> NeuDefaults.Intensity.MEDIUM
    }
}

@Composable
fun neuShadow(
    shape: RoundedCornerShape = RoundedCornerShape(AppDimens.cardCornerRadius)
): Modifier {
    val isDark = UIConfig.shouldUseDarkTheme()
    val intensity = currentNeuIntensity()
    return Modifier.neuRaised(shape, isDark, intensity)
}

@Composable
fun neuInset(
    shape: RoundedCornerShape = RoundedCornerShape(AppDimens.cardCornerRadius)
): Modifier {
    val isDark = UIConfig.shouldUseDarkTheme()
    val intensity = currentNeuIntensity()
    return Modifier.neuInset(shape, isDark, intensity)
}

@Composable
fun neuGlow(
    color: Color = NeuDefaults.GlowColor
): Modifier {
    if (!UIConfig.isNeumorphismGlowEnabled()) return Modifier
    return Modifier.neuGlow(color, 8.dp, 0.05f)
}

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(AppDimens.cardCornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = UIConfig.shouldUseDarkTheme()
    val intensity = currentNeuIntensity()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "neu_card_scale"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isPressed) Modifier.neuInset(shape, isDark, intensity)
                else Modifier.neuRaised(shape, isDark, intensity)
            )
            .background(AppColors.cardBackground(), shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(AppDimens.cardPadding),
        content = content
    )
}

@Composable
fun NeuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = UIConfig.shouldUseDarkTheme()
    val intensity = currentNeuIntensity()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "neu_btn_scale"
    )
    val bgBrush = if (primary) Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    ) else null

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (primary) Modifier.background(bgBrush!!, shape)
                else if (isPressed) Modifier.neuInset(shape, isDark, intensity)
                    .background(AppColors.cardBackground(), shape)
                else Modifier.neuRaised(shape, isDark, intensity)
                    .background(AppColors.cardBackground(), shape)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else MaterialTheme.colorScheme.primary,
            fontSize = AppDimens.bodyTextSize.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NeuSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = UIConfig.shouldUseDarkTheme()
    val intensity = currentNeuIntensity()
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 24f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "neu_switch_thumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(NeuDefaults.animationDuration()),
        label = "switch_track"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White else AppColors.cardBackground(),
        animationSpec = tween(NeuDefaults.animationDuration()),
        label = "switch_thumb"
    )
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .size(54.dp, 30.dp)
            .neuInset(shape, isDark, intensity)
            .clip(shape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (3 + thumbOffset).dp)
                .size(24.dp)
                .neuRaised(CircleShape, isDark, intensity)
                .background(thumbColor, CircleShape)
        )
    }
}

@Composable
fun NeuCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "neu_cb_scale"
    )
    Box(
        modifier = modifier
            .size(22.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (checked) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .then(
                if (!checked) Modifier.shadow(2.dp, RoundedCornerShape(6.dp), ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
                    .background(AppColors.cardBackground(), RoundedCornerShape(6.dp))
                else Modifier
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NeuRadio(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "neu_radio_scale"
    )
    Box(
        modifier = modifier
            .size(22.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                else Modifier.shadow(2.dp, CircleShape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
                    .background(AppColors.cardBackground(), CircleShape)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(modifier = Modifier.size(10.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
fun NeuProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = AppDimens.progressHeight
) {
    val shape = RoundedCornerShape(AppDimens.radiusSmall)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(2.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), shape)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                    RoundedCornerShape(AppDimens.radiusSmall)
                )
        )
    }
}

@Composable
fun NeuCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun NeuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "neu_chip_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (selected) Modifier.background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                    shape
                )
                else Modifier.shadow(3.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
                    .background(AppColors.cardBackground(), shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val shape = RoundedCornerShape(AppDimens.inputCornerRadius)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    )
}

@Composable
fun NeuAlert(
    message: String,
    type: AlertType = AlertType.Info,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, textColor, icon) = when (type) {
        AlertType.Info -> AlertColors(Color(0x1A3B82F6), Color(0x333B82F6), Color(0xFF60A5FA), "ℹ")
        AlertType.Warning -> AlertColors(Color(0x1AF59E0B), Color(0x33F59E0B), Color(0xFFFBBF24), "⚠")
        AlertType.Error -> AlertColors(Color(0x1AEF4444), Color(0x33EF4444), Color(0xFFF87171), "✕")
        AlertType.Success -> AlertColors(Color(0x1A27AE60), Color(0x3327AE60), Color(0xFF34D399), "✓")
    }
    val shape = RoundedCornerShape(AppDimens.radiusMedium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(bgColor, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 18.sp, color = textColor)
        Spacer(Modifier.width(10.dp))
        Text(text = message, color = textColor, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

enum class AlertType { Info, Warning, Error, Success }

private data class AlertColors(val bg: Color, val border: Color, val text: Color, val icon: String)

@Composable
fun NeuBanner(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppDimens.radiusMedium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                shape
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun NeuToolbar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(AppDimens.radiusMedium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun NeuSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索..."
) {
    val shape = RoundedCornerShape(AppDimens.radiusMedium)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Text("🔍", fontSize = 16.sp) },
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true
    )
}

@Composable
fun NeuStatusIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (color != Color(0xFF999999)) Modifier.shadow(3.dp, CircleShape, ambientColor = color.copy(alpha = 0.5f), spotColor = color.copy(alpha = 0.5f))
                else Modifier
            )
    )
}

@Composable
fun NeuAvatar(
    text: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    size: Dp = 42.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NeuBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE74C3C)
) {
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(9.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NeuStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..100
) {
    val shape = RoundedCornerShape(AppDimens.radiusSmall)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NeuButton(
            text = "−",
            onClick = { if (value > range.first) onValueChange(value - 1) },
            enabled = value > range.first
        )
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(32.dp)
                .shadow(2.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
                .background(AppColors.cardBackground(), shape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$value", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
        NeuButton(
            text = "+",
            onClick = { if (value < range.last) onValueChange(value + 1) },
            enabled = value < range.last
        )
    }
}

@Composable
fun NeuAccordion(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val shape = RoundedCornerShape(AppDimens.radiusMedium)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "acc_arrow"
    )
    Column(
        modifier = modifier
            .shadow(2.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(text = "▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.graphicsLayer { rotationZ = rotation })
        }
        if (expanded) {
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun NeuPagination(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppDimens.radiusSmall)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPage > 1) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
                    .background(AppColors.cardBackground(), shape)
                    .clickable { onPageChange(currentPage - 1) },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
        }
        for (i in 1..totalPages) {
            if (i == 1 || i == totalPages || (i in (currentPage - 1)..(currentPage + 1))) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (i == currentPage) Modifier.background(
                                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                                shape
                            )
                            else Modifier.shadow(2.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
                                .background(AppColors.cardBackground(), shape)
                        )
                        .clickable { onPageChange(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$i",
                        fontSize = 13.sp,
                        color = if (i == currentPage) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (i == currentPage) FontWeight.Bold else FontWeight.Normal
                    )
                }
            } else if (i == currentPage - 2 || i == currentPage + 2) {
                Text("...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (currentPage < totalPages) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
                    .background(AppColors.cardBackground(), shape)
                    .clickable { onPageChange(currentPage + 1) },
                contentAlignment = Alignment.Center
            ) { Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun NeuColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "color_dot_scale"
    )
    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(2.dp, CircleShape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
    )
}

@Composable
fun NeuEmptyState(
    icon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            NeuButton(text = actionText, onClick = onAction, primary = true)
        }
    }
}

@Composable
fun NeuListItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(AppDimens.radiusMedium)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val offsetX by animateFloatAsState(
        targetValue = if (isPressed) 4f else 0f,
        animationSpec = tween(200),
        label = "li_offset"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = NeuColors.shadowDark(), spotColor = NeuColors.shadowDark())
            .background(AppColors.cardBackground(), shape)
            .graphicsLayer { translationX = offsetX }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(AppDimens.radiusMedium))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp, color = Color.White)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text(text = "›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
