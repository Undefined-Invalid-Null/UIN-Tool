// ui/screen/dev/JsonSyntaxHighlight.kt
package com.UIN.Tool.ui.screen.dev

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 轻量 JSON 语法高亮（用于 plugin.json 编辑弹窗）。
 * 高亮字符串、键名、数字与 true/false/null 字面量。
 */
internal class JsonSyntaxHighlighter(
    private val keyColor: Color,
    private val stringColor: Color,
    private val numberColor: Color,
    private val literalColor: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        if (source.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(source)
        val tokens = ArrayList<Pair<Int, Int>>() // (start, end)
        val colors = ArrayList<Color>()

        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            when {
                c == '"' -> {
                    val start = i
                    i++
                    while (i < n) {
                        val ch = source[i]
                        if (ch == '\\') {
                            i += 2
                            continue
                        }
                        i++
                        if (ch == '"') break
                    }
                    val isKey = i < n && source[i] == ':'
                    tokens.add(start to i)
                    colors.add(if (isKey) keyColor else stringColor)
                }
                c == '-' || c.isDigit() -> {
                    val start = i
                    while (i < n && (source[i].isDigit() || source[i] in ".eE+-")) i++
                    tokens.add(start to i)
                    colors.add(numberColor)
                }
                source.startsWith("true", i) || source.startsWith("false", i) || source.startsWith("null", i) -> {
                    val start = i
                    i += if (source.startsWith("false", i)) 5 else 4
                    tokens.add(start to i)
                    colors.add(literalColor)
                }
                else -> i++
            }
        }

        // 从后往前添加样式，避免偏移冲突
        for (idx in tokens.indices.reversed()) {
            val (start, end) = tokens[idx]
            if (start < end) {
                builder.addStyle(SpanStyle(color = colors[idx]), start, end)
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
