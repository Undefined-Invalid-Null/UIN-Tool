// app/src/main/java/com/UIN/Tool/ui/components/unified/UnifiedComponents.kt
package com.UIN.Tool.ui.components.unified

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.isBoldEnabled
import com.UIN.Tool.utils.UIConfig
import androidx.compose.material3.LocalContentColor

enum class ButtonVariant {
    Primary,
    Secondary,
    Outlined,
    Text,
    Destructive,
    Success,
    Warning
}

enum class ButtonSize(
    val height: Dp,
    val minWidth: Dp,
    /** 兜底圆角；Medium 的实际值见 [effectiveCornerRadius]。 */
    val cornerRadius: Dp,
    val fontSize: Float
) {
    Small(
        height = 32.dp,
        minWidth = 64.dp,
        cornerRadius = 8.dp,
        fontSize = 12f
    ),
    Medium(
        height = 44.dp,
        minWidth = 80.dp,
        // 与 AppDimens.buttonCornerRadius 的未初始化兜底值一致（12.dp）
        cornerRadius = 12.dp,
        fontSize = 14f
    ),
    Large(
        height = 56.dp,
        minWidth = 100.dp,
        cornerRadius = 16.dp,
        fontSize = 16f
    );

    /**
     * 按钮实际使用的圆角：Medium 跟随「按钮圆角」配置（[AppDimens.buttonCornerRadius] 动态读取），
     * Small/Large 固定。原先直接把 AppDimens.buttonCornerRadius 写进枚举构造器 ——
     * 枚举类初始化只求值一次，之后配置页怎么改按钮圆角都不会生效。
     */
    val effectiveCornerRadius: Dp
        get() = if (this == Medium) AppDimens.buttonCornerRadius else cornerRadius
}

enum class IconPosition {
    Start,
    End
}

// ==================== 统一按钮 ====================

/**
 * 「当前是否在弹窗里」—— 由 `UnifiedDialog` 的按钮槽位注入。
 *
 * 用户反馈 e29：**弹窗里的按钮一律不铺底色，最多给文字上色**（原话「不要加背景
 * （主题色背景或者红色背景），最多只能给文字着色。整个软件都改一下」）。
 *
 * 全软件有几十处调用点，逐个改必然漏；所以由对话框统一打标记、按钮自己降级 ——
 * 一处生效，以后新增的弹窗也自动合规。**只在按钮槽位里为 true**，
 * 页面正文里的 `UnifiedButton`（底栏、卡片、工具条）不受影响。
 */
val LocalInDialog = staticCompositionLocalOf { false }

/**
 * 弹窗内「第一个输入框自动聚焦」的认领令牌（i.txt 第 4 条）。
 *
 * 由 [com.UIN.Tool.ui.components.unified.UnifiedDialog] / `UnifiedAlertDialog` 在正文外
 * 提供一份；输入框组合时**先到先得** —— 弹窗里通常从上到下排字段，最上面的那个就是
 * 用户要输入的第一个。认领后自动 requestFocus 并弹键盘，其余字段不再抢。
 */
class DialogAutoFocus {
    internal var claimed = false
}

val LocalDialogAutoFocus = staticCompositionLocalOf<DialogAutoFocus?> { null }

/**
 * 给当前输入框申请「弹窗首个字段」的自动聚焦；不是首个/不在弹窗里 → 返回 null，调用方
 * 什么都不用做。`enabled = false` 的字段不认领（例如压缩框「加入已有包」时名字框禁用，
 * 该轮到密码框）。
 */
@Composable
fun rememberDialogAutoFocus(enabled: Boolean = true): FocusRequester? {
    val state = LocalDialogAutoFocus.current ?: return null
    val mine = remember { enabled && !state.claimed }
    if (mine) state.claimed = true
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (!mine) return@LaunchedEffect
        // 等一帧让字段挂上焦点节点；直接 requestFocus 在首次组合里可能找不到节点。
        kotlinx.coroutines.delay(80)
        runCatching { requester.requestFocus() }
        keyboard?.show()
    }
    return if (mine) requester else null
}

/** 任意输入框的 modifier 版自动聚焦（M3 原生 `OutlinedTextField` 也能用）。 */
@Composable
fun Modifier.dialogAutoFocus(enabled: Boolean = true): Modifier {
    val requester = rememberDialogAutoFocus(enabled) ?: return this
    return focusRequester(requester)
}

/**
 * o.txt 第 3 条：新拟态下**所有带影子的控件共用的那一层不透明表面**。
 *
 * 新拟态的凸起是「先画一整块影子，再在上面盖表面」。表面若透明，影子底板就整片露出来 ——
 * 用户看到的就是「一块灰蒙蒙的阴影」，而不是浮起的按钮。文字按钮与图标按钮必须用同一层
 * 表面，否则同一排控件里一个白、一个灰。
 *
 *  - 玻璃模式：跟玻璃底（透出页面渐变）；
 *  - 深色：跟 surface（白色蒙层会把暗色主题洗灰）；
 *  - 浅色：纯白 —— 这是「白色覆盖层」的直译，也让浮起的控件与同色的页面材质区分开。
 */
@Composable
internal fun neuSurfaceFill(): Color = when {
    AppColors.glassEnabled() -> AppColors.glassBackground()
    // p.txt 第 2 条（用户）：新拟态按钮的覆盖层**不能硬编码纯白** —— 浅色主题下
    // 那块 #FFFFFF 与页面卡片底色对不上，按钮像贴了一张白纸。这里直接取卡片底色
    // （AppColors.cardBackground() 就是主题 surface），深浅色都跟着背景走。
    else -> AppColors.cardBackground()
}

@Composable
fun UnifiedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    iconPosition: IconPosition = IconPosition.Start,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    // 用户反馈 e29：**弹窗里的按钮一律不铺底色，最多给文字上色**（「不要加背景
    // （主题色背景或者红色背景），最多只能给文字着色」）。标记由 UnifiedDialog 注入
    // （[LocalInDialog]），这里把填充型降级成文字型 —— 几十处调用点一处生效；
    // 页面正文里的按钮（标记为 false）完全不受影响。
    val inDialog = LocalInDialog.current
    val isFilledVariant = variant == ButtonVariant.Primary || variant == ButtonVariant.Secondary ||
        variant == ButtonVariant.Destructive || variant == ButtonVariant.Success ||
        variant == ButtonVariant.Warning
    // k.txt 第 3 条（用户）：**全软件不再使用底色填充按钮** —— 所有填充型统一降级成
    // 「文字 + 同色描边」。弹窗内原本就已是文字型（e29），这里把**页面正文**的填充型
    // 也一并降级：41 个场地、上百个调用点只改这一处，不会再冒出白字压在色块上的按钮。
    // 语义色（红删除 / 绿成功 / 主题色主操作）保留在文字与描边上，识别度不降。
    val effectiveVariant = when {
        isFilledVariant -> ButtonVariant.Outlined
        else -> variant
    }
    // 降级后文字/描边用哪个色：**就用它原本要铺的那块底色**（危险→红、成功→绿、主色→蓝…）。
    val fallbackTextColor = when {
        containerColor != null -> containerColor
        variant == ButtonVariant.Destructive -> MaterialTheme.colorScheme.error
        variant == ButtonVariant.Secondary -> MaterialTheme.colorScheme.secondary
        variant == ButtonVariant.Success -> Color(0xFF4CAF50)
        variant == ButtonVariant.Warning -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }
    val (resolvedContainerColor, resolvedContentColor) = when {
        containerColor != null && contentColor != null -> containerColor to contentColor
        containerColor != null -> containerColor to MaterialTheme.colorScheme.onSurface
        contentColor != null -> MaterialTheme.colorScheme.primary to contentColor
        else -> when (variant) {
            ButtonVariant.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            ButtonVariant.Secondary -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
            ButtonVariant.Destructive -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
            ButtonVariant.Success -> Color(0xFF4CAF50) to Color.White
            ButtonVariant.Warning -> Color(0xFFFF9800) to Color.White
            else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        }
    }

    val shape = RoundedCornerShape(size.effectiveCornerRadius)
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()
    // 订阅配置版本（玻璃开关读取即登记依赖）：保存后本按钮重组重读新拟态/深浅色等配置
    AppColors.glassEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // n.txt 第 4 条：**文字按钮也要有一层像图标按钮那样的表面**。
    // 原先 Text 变体是全透明的，新拟态要么没影（一块悬空的字），要么一加影就变成
    // 「整片字块 + 外圈灰影」。这里与 [UnifiedIconButtonShell] 同口径：给一层
    // 贴合的 surface 底，阴影只沿着按钮自己的轮廓走，按下切内凹。
    // o.txt 第 3 条：文字按钮的「表面」必须是不透明白色覆盖层 —— 只给柔影时，
    // 整块按钮区域会透出 #FFFFFF/#D9D9D9 的阴影底板，看起来就是一片灰蒙蒙的色块。
    // 深色模式沿用 surface，避免白色蒙层把暗色主题洗灰；玻璃模式跟玻璃底。
    val neuSurfaceColor = neuSurfaceFill()

    val neuModifier = if (neu) {
        val currentOffset = if (isPressed) 0f else 5f
        val neuInset = UIConfig.isNeumorphismInsetEnabled()
        when (effectiveVariant) {
            // m.txt 第 1 条：k.txt 起所有填充型按钮都降级成 Outlined —— 可这里原先
            // 只给“真正填充”的分支加新拟态，降级后等于**全软件按钮都丢了浮起感**。
            // 现在 Outlined（也就是降级后的主/次/危险/成功/警告）同样给 neuRaised，
            // 按下时按开关切 neuInset，按钮重新与卡片、输入框处在同一套光影里。
            ButtonVariant.Outlined -> {
                // 不进 backgroundColor：Button 的容器是透明的，阴影直接落在页面底上，
                // 正是新拟态的“从同一块材质里浮出来”效果。
                if (neuInset && isPressed) {
                    Modifier.neuInset(
                        shape, isDark, NeuDefaults.Intensity.MEDIUM,
                        cornerRadius = size.effectiveCornerRadius,
                        backgroundColor = neuSurfaceColor,
                        offset = currentOffset
                    )
                } else {
                    Modifier.neuRaised(
                        shape, isDark, NeuDefaults.Intensity.MEDIUM,
                        cornerRadius = size.effectiveCornerRadius,
                        backgroundColor = neuSurfaceColor,
                        offset = currentOffset
                    )
                }
            }
            // n.txt 第 4 条：文字按钮不再「保持无阴影」。给和图标按钮一样的凸起轮廓，
            // 底由下面 colors 里的 containerColor 铺（见 textButtonColors）。
            ButtonVariant.Text -> {
                if (neuInset && isPressed) {
                    Modifier.neuInset(
                        shape, isDark, NeuDefaults.Intensity.LIGHT,
                        cornerRadius = size.effectiveCornerRadius,
                        backgroundColor = neuSurfaceColor,
                        offset = currentOffset
                    )
                } else {
                    Modifier.neuRaised(
                        shape, isDark, NeuDefaults.Intensity.LIGHT,
                        cornerRadius = size.effectiveCornerRadius,
                        backgroundColor = neuSurfaceColor,
                        offset = currentOffset
                    )
                }
            }
            else -> {
                val currentOffset2 = currentOffset
                // 不给 neu* 传 backgroundColor：Button 自身的 containerColor 已经铺底
                // （批次60 起 neuRaised/neuInset 会真按该参数铺色，再铺一层是重复叠加）。
                if (neuInset && isPressed) {
                    Modifier.neuInset(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = size.effectiveCornerRadius, offset = currentOffset2)
                } else {
                    Modifier.neuRaised(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = size.effectiveCornerRadius, offset = currentOffset2)
                }
            }
        }
    } else Modifier

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .then(neuModifier)
            .defaultMinSize(minHeight = size.height, minWidth = size.minWidth),
        shape = shape,
        interactionSource = interactionSource,
        border = when {
            effectiveVariant == ButtonVariant.Outlined ->
                BorderStroke(
                    1.dp,
                    if (isFilledVariant) fallbackTextColor.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outline
                )
            // i.txt 第 2 条：弹窗里由填充型降级来的文字按钮要有一圈描边 ——
            // 否则深色弹窗底上只剩几个彩色词，边界看不出来。用原本底色（或其对应
            // 字色）的半透明描边，语义仍由颜色区分。
            inDialog && isFilledVariant ->
                BorderStroke(1.dp, fallbackTextColor.copy(alpha = 0.45f))
            else -> null
        },
        colors = when (effectiveVariant) {
            // o.txt 第 3 条：Outlined 在新拟态下也要有白色表面盖住柔影底板，
            // 否则整块按钮就是一片阴影（文字按钮同理，见下）。
            ButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors(
                containerColor = if (neu) neuSurfaceColor else Color.Transparent,
                contentColor = when {
                    isFilledVariant -> fallbackTextColor
                    contentColor != null -> resolvedContentColor
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            ButtonVariant.Text -> ButtonDefaults.textButtonColors(
                // n.txt 第 4 条：新拟态下文字按钮铺一层与页面同色的表面，
                // 阴影才会贴着按钮轮廓；关闭新拟态时仍保持全透明（旧观感不变）。
                containerColor = if (neu) neuSurfaceColor else Color.Transparent,
                // 弹窗里降级来的按钮：用它**原本要铺的那块底色**当字色（危险→红、主色→蓝），
                // 而不是 Text 默认的主色 —— 否则「删除」会从红字变成蓝字，语义就丢了。
                contentColor = when {
                    contentColor != null -> resolvedContentColor
                    isFilledVariant -> fallbackTextColor
                    else -> MaterialTheme.colorScheme.primary
                },
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            else -> ButtonDefaults.buttonColors(
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp
        ),
        // m.txt 第 8 条：**不要再给纵向内边距**。很多调用点用 `Modifier.height(28/32dp)`
        // 把按钮钉成固定高度，纵向 padding 会直接吃掉文字行高，底部被裁得更明显。
        // 纵向留白统一交给 size.height / defaultMinSize 与 Row 的居中对齐完成。
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = when (effectiveVariant) {
                    ButtonVariant.Outlined, ButtonVariant.Text ->
                        if (isFilledVariant) fallbackTextColor else MaterialTheme.colorScheme.primary
                    else -> resolvedContentColor
                },
                strokeWidth = 2.dp
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null && iconPosition == IconPosition.Start) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                }
                Text(
                    text = text,
                    fontSize = size.fontSize.sp,
                    // m.txt 第 8 条：行高跟着字号走，别再继承 LocalTextStyle 的固定
                    // 行高（labelLarge 是 20sp）。很多调用点用 `Modifier.height(28/32dp)`
                    // 钉住按钮高度，12sp 的字却占着 20sp 的行盒，系统字体放大后行盒还会
                    // 继续变大 —— 超出的部分被按钮边界裁掉，看着就是「字底被切了」。
                    // 1.3 倍既能容纳中英文的下伸部，又比固定行高省出高度。
                    lineHeight = (size.fontSize * 1.3f).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = Int.MAX_VALUE,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
                if (icon != null && iconPosition == IconPosition.End) {
                    Spacer(modifier = Modifier.width(AppDimens.spacingSmall))
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ==================== 统一下拉菜单 ====================

/**
 * n.txt 第 4 条：**下拉框也要新拟态**。
 *
 * 全软件 18 处 `DropdownMenu` 原先都是 Material 默认的「高阴影浮层」——与页面上
 * 新拟态的卡片/按钮放在一起，像从另一个主题里抠出来的。这里统一收口：
 *  - 开新拟态：菜单本体有一层与页面同色的表面 + 贴合的柔影（[Modifier.neuRaised]），
 *    外面留 8dp 给阴影，避免阴影被弹窗窗口边界裁成一条硬边；
 *  - 关新拟态：原样交给 Material，观感与旧版逐像素一致。
 *
 * 定位/滚动/键盘关闭仍由 Material 的 [DropdownMenu] 负责 —— 自己用 `Popup` 重写
 * 位置计算反而会把「贴着锚点、超出屏幕自动翻转」这套成熟逻辑丢掉。
 */
@Composable
fun UnifiedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val neu = UIConfig.isNeumorphismEnabled()
    val menuModifier = if (neu) {
        val isDark = UIConfig.shouldUseDarkTheme()
        val surfaceColor = if (AppColors.glassEnabled()) {
            MaterialTheme.colorScheme.surface.copy(alpha = AppColors.translucentAlpha())
        } else {
            MaterialTheme.colorScheme.surface
        }
        modifier
            .padding(8.dp)
            .neuRaised(
                RoundedCornerShape(AppDimens.cardCornerRadius),
                isDark,
                NeuDefaults.Intensity.LIGHT,
                cornerRadius = AppDimens.cardCornerRadius,
                backgroundColor = surfaceColor
            )
    } else {
        modifier
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = menuModifier,
        offset = offset,
        content = content
    )
}

// ==================== 统一卡片 ====================

enum class CardVariant {
    Elevated,
    Filled,
    Outlined,
    Glass
}

@Composable
fun UnifiedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: CardVariant = CardVariant.Elevated,
    shape: Shape = RoundedCornerShape(AppDimens.cardCornerRadius),
    elevation: Dp = AppDimens.cardElevation,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val glass = AppColors.glassEnabled()
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    val finalModifier = modifier
        .then(
            if (neu) {
                val surfaceColor = containerColor ?: MaterialTheme.colorScheme.surface
                // w.txt 第 3 条（用户）：显式传入 containerColor（弹窗底、强调卡）必须**不透明** ——
                // 弹窗是独立窗口，背后只有半透明遮罩，再叠一层玻璃 alpha 会直接透出下层内容。
                // 只有「跟随主题、未显式指定」的普通卡片才参与玻璃半透明。
                val bgColor = if (glass && containerColor == null) {
                    surfaceColor.copy(alpha = AppColors.translucentAlpha())
                } else {
                    surfaceColor
                }
                val neuInset = UIConfig.isNeumorphismInsetEnabled()
                Modifier.then(
                        if (neuInset && isPressed) {
                            Modifier.neuInset(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent)
                        } else {
                            Modifier.neuRaised(shape, isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = AppDimens.cardCornerRadius, backgroundColor = Color.Transparent)
                        }
                    )
                    .clip(shape)
                    .background(bgColor, shape)
            } else {
                Modifier.clip(shape)
                    .then(
                        // glass 模式默认不画背景（透出页面玻璃底）。但弹窗是独立窗口，
                        // 背后没有页面玻璃底、只有半透明遮罩 → 卡片会直接透到下层内容。
                        // 因此：glass 下若显式给了 containerColor（弹窗传不透明底），仍画出来。
                        if (!glass || containerColor != null) Modifier.background(
                            containerColor ?: MaterialTheme.colorScheme.surface, shape
                        ) else Modifier
                    )
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    when (variant) {
        CardVariant.Elevated -> {
            Card(
                modifier = finalModifier,
                border = null,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                        ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = shape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    content = content
                )
            }
        }
        CardVariant.Filled -> {
            Card(
                modifier = finalModifier,
                border = null,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                        ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = shape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    content = content
                )
            }
        }
        CardVariant.Outlined -> {
            Card(
                modifier = finalModifier,
                border = null,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = shape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    content = content
                )
            }
        }
        CardVariant.Glass -> {
            Surface(
                modifier = finalModifier
                    .background(AppColors.glassBackground()),
                color = Color.Transparent,
                shape = shape,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.cardPadding),
                    content = content
                )
            }
        }
    }
}

// ==================== 统一输入框 ====================

@Composable
fun UnifiedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    showClearButton: Boolean = false,
    error: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onTrailingIconClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    // 批次45：透传键盘行为（数字键盘/回车跳页等）—— 缺省与原先完全一致。
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions? = null,
    // m.txt 第 6 条：并非每个输入框都该抢弹窗焦点（如文件属性里的「粘贴校验」，
    // 查看属性的人极少用它，弹键盘只会挡住内容）。传 false 可让该字段不认领自动聚焦。
    autoFocus: Boolean = true
) {
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()
    var passwordVisible by remember { mutableStateOf(!isPassword) }

    val inputShape = RoundedCornerShape(AppDimens.inputCornerRadius)
    // i.txt 第 4 条：弹窗里最上面的可编辑输入框自动拿焦点并弹键盘。
    val autoFocusRequester = rememberDialogAutoFocus(enabled && autoFocus)
    val focused = if (autoFocusRequester != null) Modifier.focusRequester(autoFocusRequester) else Modifier

    if (neu) {
        val surfaceColor = containerColor ?: MaterialTheme.colorScheme.background
        val glass = AppColors.glassEnabled()
        val bgColor = if (containerColor != null) containerColor else if (glass) AppColors.glassBackground() else surfaceColor
        Box(
            modifier = modifier
                // 铺底由紧随其后的 .background(bgColor) 承担：neuInset 现在会真的按
                // backgroundColor 铺一层（批次60），玻璃态下两层半透明底叠加会比旧版更实。
                .neuInset(RoundedCornerShape(AppDimens.inputCornerRadius), isDark, NeuDefaults.Intensity.MEDIUM, cornerRadius = AppDimens.inputCornerRadius)
                .background(bgColor, RoundedCornerShape(AppDimens.inputCornerRadius))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).then(focused),
                    // E-01：原先固定 0xFF333333，深色模式下深灰字压深底几乎不可读
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    enabled = enabled,
                    keyboardOptions = keyboardOptions ?: KeyboardOptions.Default,
                    keyboardActions = keyboardActions ?: KeyboardActions.Default,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    }
                )
                if (isPassword) {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = enabled,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) Str.get(R.string.hide_password) else Str.get(R.string.show_password),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (showClearButton && value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        enabled = enabled,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = Str.get(R.string.clear_2), modifier = Modifier.size(20.dp))
                    }
                }
                trailingIcon?.let {
                    IconButton(
                        onClick = { onTrailingIconClick?.invoke() },
                        enabled = enabled,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (error != null || supportingText != null) {
            Text(
                error ?: supportingText ?: "",
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    } else {
        val glass = AppColors.glassEnabled()
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.then(focused),
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
            trailingIcon = {
                Row {
                    if (isPassword) {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) Str.get(R.string.hide_password) else Str.get(R.string.show_password)
                            )
                        }
                    }
                    if (showClearButton && value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = Str.get(R.string.clear_2))
                        }
                    }
                    trailingIcon?.let {
                        IconButton(onClick = { onTrailingIconClick?.invoke() }) {
                            Icon(it, contentDescription = null)
                        }
                    }
                }
            },
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            isError = error != null,
            supportingText = error?.let { { Text(it) } } ?: supportingText?.let { { Text(it) } },
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions ?: KeyboardOptions.Default,
            keyboardActions = keyboardActions ?: KeyboardActions.Default,
            shape = RoundedCornerShape(AppDimens.inputCornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = containerColor ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.background,
                unfocusedContainerColor = containerColor ?: if (glass) AppColors.glassBackground() else MaterialTheme.colorScheme.background
            )
        )
    }
}

// ==================== 统一文本 ====================

@Composable
fun UnifiedTitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = AppDimens.titleTextSize.sp,
            fontWeight = if (isBoldEnabled()) FontWeight.Bold else FontWeight.SemiBold
        ),
        color = color ?: MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun UnifiedBodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = AppDimens.bodyTextSize.sp
        ),
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun UnifiedCaptionText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = AppDimens.captionTextSize.sp
        ),
        color = color ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun UnifiedSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = AppDimens.sectionTitleTextSize.sp,
            fontWeight = if (isBoldEnabled()) FontWeight.SemiBold else FontWeight.Medium
        ),
        color = color ?: MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

// ==================== 统一开关 ====================

@Composable
fun UnifiedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    // 订阅配置版本（玻璃开关读取即登记依赖）：保存后本开关重组，深浅色/新拟态等配置即时生效
    AppColors.glassEnabled()
    if (neu) {
        val trackColor by animateColorAsState(
            targetValue = if (checked) MaterialTheme.colorScheme.primary
            else if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surfaceVariant,
            animationSpec = tween(NeuDefaults.animationDuration()),
            label = "switch_track"
        )
        val thumbColor by animateColorAsState(
            targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            animationSpec = tween(NeuDefaults.animationDuration()),
            label = "switch_thumb"
        )
        val thumbOffset by animateFloatAsState(
            targetValue = if (checked) 24f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "switch_offset"
        )

        // E-07：触控目标由 54x30 扩到最小 48x48（视觉轨道仍 54x30 居中，布局不塌）
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onClick = { onCheckedChange(!checked) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp, 30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(trackColor, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset.dp)
                        .padding(3.dp)
                        .size(24.dp)
                        .background(thumbColor, CircleShape)
                )
            }
        }
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        )
    }
}

// ==================== 统一标签 ====================

@Composable
fun UnifiedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()

    if (neu) {
        val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
        val textColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            modifier = modifier
                .then(
                    if (!selected) Modifier.neuRaised(
                        RoundedCornerShape(AppDimens.radiusXXXLarge),
                        isDark, NeuDefaults.Intensity.LIGHT,
                        AppDimens.radiusXXXLarge,
                        backgroundColor = Color.Transparent
                    ) else Modifier
                )
                .clip(RoundedCornerShape(AppDimens.radiusXXXLarge))
                .background(bgColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Box(contentAlignment = Alignment.Center) { leadingIcon() }
                }
                Text(
                    text = label,
                    fontSize = AppDimens.captionTextSize.sp,
                    color = textColor
                )
            }
        }
    } else {
        AssistChip(
            onClick = onClick,
            label = { Text(text = label, fontSize = AppDimens.captionTextSize.sp) },
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary else if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface,
                labelColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ),
            border = if (selected) null else AssistChipDefaults.assistChipBorder(
                borderColor = MaterialTheme.colorScheme.outline,
                enabled = enabled
            ),
            shape = RoundedCornerShape(AppDimens.radiusXXXLarge)
        )
    }
}

// ==================== 统一状态标签 ====================
/**
 * 描边式小状态标签（j.txt 第 3/7 条）。
 *
 * 与 [UnifiedChip] 的区别：**不填背景**，只画一圈同色描边 + 同色文字。用于「只读」
 * 「归档内」这类状态提示 —— 整条横幅会空出大片背景，既占高度又像一块色块；
 * 收进一行文字里既省空间，也符合「文字 + 描边着色」的统一口径。
 */
@Composable
fun UnifiedStatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: Float = 10f
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ==================== 统一滑块 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true
) {
    val isDark = UIConfig.shouldUseDarkTheme()
    val neu = UIConfig.isNeumorphismEnabled()
    val surfaceColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = primaryColor,
            activeTrackColor = primaryColor,
            inactiveTrackColor = surfaceColor
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(primaryColor, CircleShape)
            )
        },
        track = { sliderState ->
            Box(modifier = Modifier.height(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .then(
                            if (neu) Modifier.neuInset(RoundedCornerShape(4.dp), isDark, NeuDefaults.Intensity.LIGHT, backgroundColor = surfaceColor)
                            else Modifier.background(surfaceColor, RoundedCornerShape(4.dp))
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth((sliderState.value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(primaryColor)
                )
            }
        }
    )
}

// ==================== 统一加载指示器 ====================

@Composable
fun UnifiedLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.progressHeight)
            .clip(RoundedCornerShape(AppDimens.progressCornerRadius))
    )
}

@Composable
fun UnifiedLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            message?.let {
                Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 统一空状态 ====================

@Composable
fun UnifiedEmptyState(
    title: String,
    description: String? = null,
    icon: ImageVector = Icons.Default.Info,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimens.spacingXXXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.iconXXLarge),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Spacer(modifier = Modifier.height(AppDimens.spacingXLarge))
        UnifiedTitleText(
            text = title,
            textAlign = TextAlign.Center
        )
        if (!description.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
            UnifiedBodyText(
                text = description,
                textAlign = TextAlign.Center
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(AppDimens.spacingXXLarge))
            UnifiedButton(
                text = actionText,
                onClick = onAction,
                variant = ButtonVariant.Primary
            )
        }
    }
}

// ==================== 统一图标按钮 ====================

/**
 * 统一图标按钮。
 *
 * **无障碍警示**：[contentDescription] 默认 `null` —— 不传时 TalkBack 只会念「按钮」，
 * 读不出这个图标是干什么的（纯装饰性图标才允许留空）。除装饰性图标外，**所有调用点
 * 都应显式传入**（一般用 `Str.get(R.string.…)`），不要依赖默认值；默认值保持 null
 * 只是为了不破坏既有调用点。
 */
@Composable
fun UnifiedIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    /**
     * m.txt 第 1 条：图标按钮也补上禁用态。
     *
     * 原生 `IconButton` 有 `enabled`，而统一图标按钮之前没有 —— 结果是那些
     * 「不可用时置灰」的图标（上一处/下一处差异、目录空时的目录键…）只能继续
     * 用原生 `IconButton`，新拟态外壳就漏了一排。禁用时点击不响应、图标按
     * Material 规范的 38% 透明度压暗，视觉与原生一致。
     */
    enabled: Boolean = true,
    /** 图标边长；默认 24dp。原生化的小图标（18/20dp）按原尺寸传。 */
    iconSize: Dp = 24.dp
) {
    val iconTint = (tint ?: LocalContentColor.current).let {
        if (enabled) it else it.copy(alpha = 0.38f)
    }
    UnifiedIconButtonShell(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
    }
}

/**
 * 统一图标按钮的「自定义内容」重载（m.txt 第 1 条纳管原生画布图标）。
 *
 * 少数图标不是 `ImageVector`，而是用 `Canvas` 现画的（如图片查看器的明暗切换）。
 * 这些地方原先把原生 `IconButton` 当外壳，新拟态下就成了一块没有底板的贴纸。
 * 这里复用同一套外壳，图标本身由调用方按自己的配色绘制。
 */
@Composable
fun UnifiedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    UnifiedIconButtonShell(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        content = content
    )
}

/** 图标按钮的统一外壳：新拟态凸起圆底 + 点按/长按/禁用态。 */
@Composable
private fun UnifiedIconButtonShell(
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val neu = UIConfig.isNeumorphismEnabled()
    val isDark = UIConfig.shouldUseDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    // 未提供 onLongClick 时沿用原 clickable（绝大多数调用点观感完全不变）；
    // 提供时换成 combinedClickable（如保存按钮长按=另存为）。
    val pressMod: Modifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
    }

    if (neu) {
        // o.txt 第 3 条：与文字按钮共用同一层表面（见 [neuSurfaceFill]），
        // 同一排控件里不会一个白一个灰。
        val surfaceColor = neuSurfaceFill()
        Box(
            modifier = modifier
                .neuRaised(CircleShape, isDark, NeuDefaults.Intensity.LIGHT, cornerRadius = 20.dp, backgroundColor = Color.Transparent)
                .clip(CircleShape)
                .background(if (enabled) surfaceColor else surfaceColor.copy(alpha = 0.6f), CircleShape)
                .then(pressMod)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else if (onLongClick != null) {
        // material3.IconButton 不支持长按；仅在需要长按时换成 Box + combinedClickable，
        // 用 defaultMinSize 保住 IconButton 的 48dp 最小触控区，视觉（圆形涟漪）一致。
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .then(pressMod),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            content()
        }
    }
}

// ==================== 统一列表项 ====================

@Composable
fun UnifiedListItem(
    leadingContent: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedCard(
        modifier = modifier,
        onClick = onClick,
        variant = CardVariant.Elevated
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent?.let {
                it()
                Spacer(modifier = Modifier.width(AppDimens.spacingMedium))
            }
            Column(modifier = Modifier.weight(1f)) {
                UnifiedBodyText(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subtitle?.let {
                    Spacer(modifier = Modifier.height(AppDimens.spacingXSmall))
                    UnifiedCaptionText(text = it)
                }
            }
            trailingContent?.invoke()
        }
    }
}