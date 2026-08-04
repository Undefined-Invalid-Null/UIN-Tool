// app/src/main/java/com/UIN/Tool/utils/Str.kt
package com.UIN.Tool.utils

import androidx.annotation.StringRes
import com.UIN.Tool.UinApplication

/**
 * 无上下文字符串解析工具
 * 供 Logger、ViewModel、仓库等无 Context 的代码解析 string 资源，
 * 避免在业务代码中传递 Context。
 */
object Str {

    /**
     * 解析字符串资源
     * @param resId 资源 ID
     * @param formatArgs 格式化参数（资源中使用 %1$s / %1$d 占位符）
     */
    fun get(@StringRes resId: Int, vararg formatArgs: Any?): String {
        val context = UinApplication.getAppContext()
        return if (formatArgs.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *formatArgs)
        }
    }
}
