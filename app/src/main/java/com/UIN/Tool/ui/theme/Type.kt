package com.UIN.Tool.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * E-12：字号只有【一条映射】—— [buildTypography]。
 * 这里的静态 Typography ≡ buildTypography(UIConfig 默认值)，
 * 仅供 UIConfig 未初始化时 fallback；动态路径（Theme.buildDynamicTypography）
 * 也调同一函数，改映射/槽位只改这一处。
 */
val Typography = buildTypography(
    title = 20f,
    body = 14f,
    caption = 12f,
    section = 18f
)

/** 4 个 UIConfig size 键（title/body/caption/section）→ Material 15 槽位，与动态路径同构。 */
internal fun buildTypography(
    title: Float,
    body: Float,
    caption: Float,
    section: Float
): Typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = title.sp, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = title.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = title.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = section.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = section.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = section.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = body.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = body.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = body.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = body.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = body.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = caption.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = caption.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = caption.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontSize = caption.sp, letterSpacing = 0.sp)
)
