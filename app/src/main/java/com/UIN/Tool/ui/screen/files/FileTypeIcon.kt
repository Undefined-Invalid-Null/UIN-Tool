// app/src/main/java/com/UIN/Tool/ui/screen/files/FileTypeIcon.kt
package com.UIN.Tool.ui.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * n.txt 第 6 条：**文件图标要一眼看出类型**。
 *
 * 图标本身由 `assets/config/file_types.json` 决定（[fileIconFor]）。
 *
 * o.txt 第 7 条：后缀角标**只给实在认不出的类型用**。之前每个文件都压一枚角标，
 * 结果一屏全是小色块，类型图标反而看不清；而注册表里认得出的类型本来就有专属图标
 * （代码 / 表格 / 字体 / 密钥库…），角标是重复信息。现在只有 [FileTypeRegistry]
 * 查不到后缀、退回「通用文件」图标时才补角标 —— 那才是「实在想不出用什么图标」的
 * 情形，角标是唯一的类型线索。
 *
 * 角标完全由文件名算出，不读盘、不查 MIME，滚动时零 IO；[badge] 关掉时与旧版
 * 完全一致（图片缩略图、目录行等场景不需要后缀）。
 */
@Composable
fun FileTypeIcon(
    fileName: String,
    modifier: Modifier = Modifier,
    isDir: Boolean = false,
    size: Dp = 24.dp,
    badge: Boolean = true,
    tint: Color? = null
) {
    val icon = if (isDir) FileTypeRegistry.directoryIcon else fileIconFor(fileName)
    val resolvedTint = tint ?: fileIconTintFor(fileName, isDir)
    val suffix = remember(fileName, isDir, badge) {
        // o.txt 第 7 条：认得出的类型不画角标；只有注册表里查不到（unknown）才补。
        if (!badge || isDir || FileTypeRegistry.typeOf(fileName) != null) {
            ""
        } else {
            suffixBadgeText(fileName)
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedTint,
            modifier = Modifier.size(if (suffix.isEmpty()) size else size * 0.84f)
        )
        if (suffix.isNotEmpty()) {
            // 充做后缀标签的底色 = 该类型的固定色；白字在上面对比度足够（浅色系也做过筛选）。
            val badgeShape = RoundedCornerShape(size * 0.08f)
            Text(
                text = suffix,
                color = Color.White,
                // 角标字号跟着图标尺寸走：24dp 图标 → 7sp 左右；20dp 紧凑行 → 6sp。
                fontSize = (size.value * 0.29f).sp,
                lineHeight = (size.value * 0.29f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(badgeShape)
                    .background(resolvedTint)
                    .padding(horizontal = 1.5.dp)
            )
        }
    }
}

/**
 * 后缀角标的文字：取扩展名、全大写、最多 4 个字符。
 *
 * 4 个字符是有意的上限 —— 再长就盖住半个图标，而 `markdown`/`properties` 这类
 * 长后缀缩成 `MARK`/`PROP` 仍然足以和别类区分。无扩展名的文件返回空串（不画角标）。
 */
internal fun suffixBadgeText(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "")
    if (ext.isEmpty() || ext.length == fileName.length) return ""
    // 复合后缀（tar.gz / tar.bz2）取最后一段即可，前面那截已经在类型图标里体现了。
    val tail = ext.substringAfterLast('.')
    return tail.take(4).uppercase()
}
