// app/src/main/java/com/UIN/Tool/ui/components/unified/UnifiedDialogs.kt
package com.UIN.Tool.ui.components.unified

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.dialogBackgroundOf
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.UIConfig

// ==================== 统一弹窗文本按钮（纯文字） ====================

/**
 * 弹窗内的文字按钮：**无底色，保留一圈细描边 + 文字颜色**。
 *
 * 用户反馈④ 曾要求「不要再加描边和背景涂色，仅文字改色」—— 去掉描边后，
 * 深色弹窗背景上一排纯文字按钮很难看出哪里能点（i.txt 第 2 条又要求加回来）。
 * 现在折中：不铺底色，只画 1dp **同字色半透明描边** —— 有边界感，又不会变成
 * 一圈实心方框。
 */
@Composable
fun UnifiedDialogTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // h.txt 第 10 条：默认就用主题色。原先默认 onSurface（浅色主题下近乎纯黑），
    // 同一个弹窗里「主按钮是主题色、取消按钮是黑色」看着像两套风格。
    contentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        // l.txt 第 7 条：描边 + 文字同时完整可见。
        // m.txt 第 8 条：纵向 padding 改为 0 —— 弹窗按钮槽若被父布局限高，纵向 padding
        // 会挤掉文字行高导致底部裁切；留白交给 heightIn 的最小高度与文字居中。
        modifier = modifier.heightIn(min = 40.dp),
        // 用 ButtonDefaults 而不是 TextButtonDefaults —— 后者在当前 material3 版本里
        // 没随 *.jvm 一起解析出来（仓内 UnifiedComponents 走的也是 ButtonDefaults）
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
        // i.txt 第 2 条：把描边加回来。alpha 压低，只用作「可点区域」的边界提示。
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.45f))
    ) {
        content()
    }
}

// ==================== 动画常量 - 纯淡入淡出 ====================

private val FadeInAnimation = fadeIn(
    animationSpec = tween(
        durationMillis = 250,
        easing = FastOutSlowInEasing
    )
)

private val FadeOutAnimation = fadeOut(
    animationSpec = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )
)

private val DialogEnterTransition = FadeInAnimation
private val DialogExitTransition = FadeOutAnimation

// ==================== 基础统一对话框 ====================

@Composable
fun UnifiedDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    /**
     * 标题行右端的小动作（如搜索结果「整页查看」的斜向拉伸图标）。
     * 与标题同一行，靠右对齐；不传时标题行观感与原先完全一致。
     */
    titleActions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable (RowScope.() -> Unit)? = null,
    dismissButton: @Composable (RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showScrim: Boolean = true,
    /**
     * 卡片占屏宽的比例（0.92 = 92%）。
     *
     * 默认值就是历史观感，**不要顺手改** —— 大多数确认框靠这个宽度撑住标题与正文的行长。
     * 只有「内容天生很高、或本就该是一块贴脸的菜单」时才单独调小（如文件操作菜单）。
     */
    widthFraction: Float = 0.92f,
    /**
     * 正文区是否套一层**滚动容器**（e22，默认套）。
     *
     * 套上之后正文再长也只在卡片内滚，标题与按钮钉在原地。但滚动容器会把**下发给正文的
     * 高度约束放成无界** —— 正文里若直接用 `Modifier.weight(...)` 撑高度（靠「总高封顶 +
     * weight 吃剩余」自己排版的那种弹窗），weight 就再也算不出份额、会一路撑到数据尽头。
     * 这类弹窗请传 `false`，让它沿用原来的自排版。
     */
    scrollContent: Boolean = true
) {
    // 6.2.0 批次18：由 focusable Popup 改为真正的 [Dialog] 窗口。
    // Popup 里的输入框获焦时，软键盘光标手柄会被渲染到弹窗左上角而非输入框内
    // （Popup 与 IME 的窗口定位冲突）；Dialog 窗口能正确处理软键盘与光标定位。
    // 遮罩仍自绘 0.5 黑，并把 Dialog 自带的窗口变暗关掉（setDimAmount(0)）避免叠加变黑。

    // 用户反馈 c10：弹窗一开，任何走 Snackbar 的提示都会被这个窗口盖住
    // （Snackbar 在 Activity 窗口里，Dialog 层级更高）。登记层数后由 AppToast
    // 自动改走系统 Toast，提示才压得住弹窗。覆盖返回键、点外部、点按钮三条关闭路径。
    DisposableEffect(Unit) {
        AppToast.pushOverlay()
        onDispose { AppToast.popOverlay() }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            // 关闭 Dialog 默认的背景变暗，遮罩只由下面自绘的一层黑负责 —— 两层叠起来更黑
            // （用户反馈 c15「弹窗/抽屉的黑色遮罩」要淡：0.5 → 0.25，底下列表透得出来）
            (dialogView.context as? android.app.Dialog)?.window?.setDimAmount(0f)
        }
        // 背景遮罩 - 纯淡入淡出
        AnimatedVisibility(
            visible = true,
            enter = FadeInAnimation,
            exit = FadeOutAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showScrim) {
                            Modifier.background(Color.Black.copy(alpha = 0.25f))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 卡片内容 - 纯淡入淡出
                AnimatedVisibility(
                    visible = true,
                    enter = FadeInAnimation,
                    exit = FadeOutAnimation
                ) {
val neuDialog = UIConfig.isNeumorphismEnabled()
                    val isDarkDialog = UIConfig.shouldUseDarkTheme()
                    // e22：卡片**高度上限** = 屏高 − 上下各留的边距。内容超高时交给正文区滚动
                    //（下面那个 weight + verticalScroll），标题与按钮留在滚动区外面 ——
                    // 按钮永远在屏幕内，不会像从前那样被正文顶到屏幕外头去。
                    val maxDialogHeight = (
                        LocalConfiguration.current.screenHeightDp.dp -
                            AppDimens.dialogVerticalMargin * 2
                        ).coerceAtLeast(240.dp)

                    Surface(
                    // 用户反馈 e6：输入框获焦时，键盘会把卡片下半截（含按钮）盖住。
                    // `imePadding` 必须放在**最外层** —— 放内层的话背景会一路铺到键盘底下，
                    // 看着像卡片陷进了键盘里。能生效的前提是本 Dialog 的 properties
                    // 已经是 `decorFitsSystemWindows = false`（IME 的 inset 才到得了这里）。
                    modifier = modifier
                        .imePadding()
                        // e22：宽、高两个方向都封顶。`widthIn` 必须排在 `fillMaxWidth`
                        // **前面** —— 它先把可用宽度截到上限，fill 再按比例铺；
                        // 反过来排的话比例会先按整块屏算好，上限就白设了。
                        // 手机上 0.92×360 ≈ 331dp 本就低于上限，竖屏观感与改前一致。
                        .heightIn(max = maxDialogHeight)
                        .widthIn(max = AppDimens.dialogMaxWidth)
                        .fillMaxWidth(widthFraction)
                        .wrapContentHeight()
                        .then(if (neuDialog) Modifier.neuRaised(RoundedCornerShape(AppDimens.dialogCornerRadius), isDarkDialog, NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.dialogCornerRadius, backgroundColor = Color.Transparent) else Modifier)
                        .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* 阻止点击穿透到背景 */ }
                        ),
                    shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                        // h.txt 第 18 条：弹窗内**任何位置**的按钮都不铺主题色/红色大色块，
                        // 最多给文字上色。标记覆盖整个弹窗（标题 / 正文 / 按钮槽）——
                        // 原先只包住按钮槽，正文里自己摆的 Primary 按钮仍是一整块实色。
                        CompositionLocalProvider(
                            LocalInDialog provides true,
                            // i.txt 第 4 条：正文里第一个可编辑字段自动聚焦。
                            LocalDialogAutoFocus provides remember { DialogAutoFocus() }
                        ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimens.dialogPadding)
                        ) {
                            // 标题（可带右端动作）
                            if (title != null || titleActions != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = AppDimens.spacingMedium),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (title != null) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    titleActions?.invoke(this)
                                }
                            }
                            
                            // e22：正文区**自己滚**，标题与按钮钉在外面。`weight(1f, fill=false)`
                            // 把「屏高 − 标题 − 按钮」剩下的高度分给它：内容矮时按内容走
                            //（fill=false 不硬撑），内容高时顶到上限就地滚动，按钮始终在屏幕内。
                            // 没有这层的话，整卡是 wrapContentHeight，正文一长就把按钮顶出屏幕，
                            // 而外层是居中的，超出的部分两头都看不见。
                            if (scrollContent) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    content()
                                }
                            } else {
                                // 正文自己按「总高封顶 + weight 吃剩余」排版的弹窗走这条 ——
                                // 滚动容器会把约束放成无界，weight 就算不出份额了。
                                content()
                            }
                            
                            // 按钮
                            if (confirmButton != null || dismissButton != null) {
                                Spacer(modifier = Modifier.height(AppDimens.spacingLarge))
                                // 用户反馈 e29：弹窗里的按钮**一律不铺底色，最多给文字上色**。
                                // 这里打标记，[UnifiedButton] 见到就把填充型降级成文字型 ——
                                // 全软件几十处调用点一处生效，不用逐个改，新增弹窗也自动合规。
                                // 标记已由外层覆盖（见 Surface 内容开头的 CompositionLocalProvider）。
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dismissButton?.invoke(this)
                                    confirmButton?.invoke(this)
                                }
                            }
                        }
                        }
                    }
                }
                // m.txt 第 13 条：弹窗窗口内自己的 AppToast 宿主 —— 提示不再退回系统样式。
                AppToastHost(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                )
            }
        }
    }
}

// ==================== 统一确认对话框 ====================

@Composable
fun UnifiedConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = Str.get(R.string.ok_2),
    dismissText: String = Str.get(R.string.cancel),
    isDestructive: Boolean = false,
    confirmButtonColor: Color? = null
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            UnifiedButton(
                text = confirmText,
                onClick = onConfirm,
                containerColor = confirmButtonColor ?: if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (isDestructive) {
                    MaterialTheme.colorScheme.onError
                } else if (confirmButtonColor != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                modifier = Modifier.weight(1f)
            )
        },
        dismissButton = {
            UnifiedButton(
                text = dismissText,
                onClick = onDismiss,
                variant = ButtonVariant.Outlined,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

// ==================== 统一信息对话框 ====================

@Composable
fun UnifiedInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    buttonText: String = Str.get(R.string.ok_2)
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            // 反馈 e29：弹窗按钮不铺底色 —— 原先这块 surfaceVariant 底也是「背景」
            UnifiedDialogTextButton(
                onClick = onDismiss,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text(buttonText)
            }
        }
    )
}

// ==================== 统一Alert对话框（渐变背景） ====================

/**
 * AlertDialog 风格的对话框：与 Material3 [AlertDialog] 参数对齐，
 * 但背景用与主页面同款渐变铺底（[dialogBackgroundOf]），
 * 关闭或未启用渐变时回退到与页面一致的纯色背景。
 */
@Composable
fun UnifiedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        val neuAlertDialog = UIConfig.isNeumorphismEnabled()
        val isDarkAlertDialog = UIConfig.shouldUseDarkTheme()

        // m.txt 第 13 条：弹窗窗口内自己的 AppToast 宿主（同时保持卡片居中）。
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            // 用户反馈 e6：一并给键盘让位（本框通常不带输入框，兜住不亏）
            // e22：与 UnifiedDialog 同一套宽度口径。原先前缀只有 `imePadding` ——
            // 正文一个 fillMaxWidth 就把卡片拉满整屏，比其它弹窗宽出一截，观感对不上。
            modifier = modifier
                .imePadding()
                .widthIn(max = AppDimens.dialogMaxWidth)
                .fillMaxWidth(AppDimens.dialogWidthFraction)
                .then(if (neuAlertDialog) Modifier.neuRaised(shape, isDarkAlertDialog, NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.dialogCornerRadius, backgroundColor = Color.Transparent) else Modifier)
                .then(Modifier.dialogBackgroundOf(shape)),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            // h.txt 第 18 条：与 UnifiedDialog 同一口径 —— 本框正文/按钮槽里的填充型按钮
            // 一律降级为文字色，不铺主题色/红色大块（调用方常显式给 containerColor=主色）。
            CompositionLocalProvider(
                LocalInDialog provides true,
                // i.txt 第 4 条：与 UnifiedDialog 同口径。
                LocalDialogAutoFocus provides remember { DialogAutoFocus() }
            ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(vertical = AppDimens.dialogPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacingMedium)
            ) {
                if (title != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.dialogPadding),
                        horizontalAlignment = Alignment.Start
                    ) {
                        title()
                    }
                }
                if (text != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.dialogPadding),
                        horizontalAlignment = Alignment.Start
                    ) {
                        text()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.dialogPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
            }
        }
        AppToastHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        )
        }
    }
}

// ==================== 统一加载对话框 ====================

@Composable
fun UnifiedLoadingDialog(
    message: String = Str.get(R.string.loading),
    onCancel: (() -> Unit)? = null
) {
    Popup(
        onDismissRequest = { onCancel?.invoke() },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        // 背景遮罩 - 纯淡入淡出
        AnimatedVisibility(
            visible = true,
            enter = FadeInAnimation,
            exit = FadeOutAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 0.5 → 0.25（用户反馈 c15：弹窗遮罩太黑，底下列表看不清）
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                // 卡片内容 - 纯淡入淡出
                AnimatedVisibility(
                    visible = true,
                    enter = FadeInAnimation,
                    exit = FadeOutAnimation
                ) {
                    Surface(
                        modifier = Modifier
                            // e22：轻量弹窗走窄一档的占比，同样封顶 —— 宽屏不许铺满
                            .widthIn(max = AppDimens.dialogMaxWidth)
                            .fillMaxWidth(AppDimens.dialogCompactFraction)
                            .wrapContentHeight()
                            .then(
                                if (UIConfig.isNeumorphismEnabled()) {
                                    Modifier.neuRaised(RoundedCornerShape(AppDimens.dialogCornerRadius), UIConfig.shouldUseDarkTheme(), NeuDefaults.Intensity.LIGHT, cornerRadius = AppDimens.dialogCornerRadius, backgroundColor = Color.Transparent)
                                } else Modifier
                            )
                            .then(Modifier.dialogBackgroundOf(RoundedCornerShape(AppDimens.dialogCornerRadius))),
                        shape = RoundedCornerShape(AppDimens.dialogCornerRadius),
                        color = Color.Transparent,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimens.dialogPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(AppDimens.spacingXLarge))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            onCancel?.let {
                                Spacer(modifier = Modifier.height(AppDimens.spacingMedium))
                                UnifiedDialogTextButton(onClick = it) {
                                    Text(Str.get(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 统一列表对话框 ====================

@Composable
fun UnifiedListDialog(
    title: String,
    items: List<String>,
    onItemClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    UnifiedDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column {
                items.forEachIndexed { index, item ->
                    UnifiedDialogTextButton(
                        onClick = { onItemClick(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (index < items.size - 1) {
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = AppDimens.dividerThickness
                        )
                    }
                }
            }
        },
        dismissButton = {
            UnifiedDialogTextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.spacingMedium)
            ) {
                Text(Str.get(R.string.cancel))
            }
        }
    )
}

// ==================== 统一底部弹窗 ====================

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedBottomSheet(
    onDismissRequest: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        // 用户反馈 d8：底色**必须走 containerColor**，不能塞进 modifier。
        // material3 1.4.0 的 ModalBottomSheet 内部有一个「按锚点整体下移到屏幕底部」的
        // 节点：挂在 modifier 上的背景落在这个节点**外侧**，于是背景留在屏幕顶部，
        // 而内容沉到底部 —— 顶部一块白板、底部却透出被遮罩压暗的页面，中间还断开。
        // 背景与内容回到同一个节点，就不会再被拆成两截。
        containerColor = AppColors.dialogBackground(),
        // f6「去掉顶部横线」：ModalBottomSheet 默认自带一枚拖拽把手（SheetDefaults.DragHandle），
        // 画在抽屉最上沿 —— 就是那条横线。本抽屉是点背景或返回键关闭的，
        // 不靠拖拽，摆一枚把手纯属多一条没用的横线；显式给个空槽把它去掉
        //（库默认值不是 null，不写就会画）。UnifiedBottomSheet 全仓只有
        // 「书签与历史」这一处在用，不会连累别的弹层。
        dragHandle = {},
        // 遮罩对齐仓内其它覆盖层的 0.25（统一弹层观感，原为库默认 0.32）
        scrimColor = Color.Black.copy(alpha = 0.25f),
        shape = RoundedCornerShape(
            topStart = AppDimens.radiusXXLarge,
            topEnd = AppDimens.radiusXXLarge
        )
    ) {
        // 底部抽屉同样是**独立窗口**（ModalBottomSheet 内部走 Dialog）：不登记层数的话，
        // 提示照样会被它盖住（用户反馈 c10）。
        DisposableEffect(Unit) {
            AppToast.pushOverlay()
            onDispose { AppToast.popOverlay() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.dialogPadding)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = AppDimens.spacingMedium)
                )
            }
            content()
        }
    }
}