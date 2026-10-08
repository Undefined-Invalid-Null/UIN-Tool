package com.UIN.Tool.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * E-13：形状只有【一条映射】—— [buildShapes]。
 * 静态 Shapes ≡ buildShapes(UIConfig 默认值)，仅供未初始化时 fallback；
 * 动态路径（Theme.buildDynamicShapes）也调同一函数。
 */
val Shapes = buildShapes(
    cornerSmall = 8f,
    cornerMedium = 12f,
    cornerLarge = 16f,
    cornerExtraLarge = 24f,
    dialog = 20f
)

/** UIConfig shape 键（cornerRadiusSmall/Medium/Large/ExtraLarge + dialog）→ Material 5 槽位。 */
internal fun buildShapes(
    cornerSmall: Float,
    cornerMedium: Float,
    cornerLarge: Float,
    cornerExtraLarge: Float,
    dialog: Float
): Shapes = Shapes(
    extraSmall = RoundedCornerShape(cornerSmall.dp),
    small = RoundedCornerShape(cornerMedium.dp),
    medium = RoundedCornerShape(cornerLarge.dp),
    large = RoundedCornerShape(cornerExtraLarge.dp),
    extraLarge = RoundedCornerShape(dialog.dp)
)
