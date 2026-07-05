// app/src/main/java/com/UIN/Tool/ui/components/UIComponentsExtensions.kt
package com.UIN.Tool.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * UIComponents 扩展函数
 */
object UIComponentsExtensions {

    fun Modifier.backgroundColor(color: Color): Modifier = composed {
        drawBehind {
            drawRect(color)
        }
    }

    fun Modifier.borderWidth(width: Dp, color: Color): Modifier = composed {
        drawBehind {
            val strokeWidthPx = width.toPx()
            drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = size,
                style = Stroke(strokeWidthPx)
            )
        }
    }

    fun Modifier.borderRadius(
        width: Dp,
        color: Color,
        cornerRadius: Dp
    ): Modifier = composed {
        drawBehind {
            val strokeWidthPx = width.toPx()
            val radius = cornerRadius.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(strokeWidthPx)
            )
        }
    }
}