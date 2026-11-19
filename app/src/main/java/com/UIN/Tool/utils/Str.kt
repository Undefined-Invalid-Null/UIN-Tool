// app/src/main/java/com/UIN/Tool/utils/Str.kt
package com.UIN.Tool.utils

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import com.UIN.Tool.UinApplication

/**
 * 无上下文字符串解析工具
 * 供 Logger、ViewModel、仓库等无 Context 的代码解析 string 资源，
 * 避免在业务代码中传递 Context。
 */
object Str {

    /**
     * v.txt 第 4 条（用户）：切语言不再重建 Activity（那样会黑屏/跳页），
     * 但 [get] 走的是 application context，Compose 观察不到语言变化。
     *
     * 这里挂一个快照状态：组合期间调用 [get] 会读取它，从而把当前组合作用域
     * 登记为依赖；[notifyLocaleChanged] 自增后，所有取过文案的组合自动重组。
     * 非组合线程读取同一状态只是普通取值，无副作用。
     */
    private val localeVersion = mutableStateOf(0)

    /** 语言资源已切换完成 —— 让所有在组合中取过文案的界面重组一次。 */
    fun notifyLocaleChanged() {
        localeVersion.value += 1
    }

    /**
     * 解析字符串资源
     * @param resId 资源 ID
     * @param formatArgs 格式化参数（资源中使用 %1$s / %1$d 占位符）
     */
    fun get(@StringRes resId: Int, vararg formatArgs: Any?): String {
        // 组合中读取 → 登记依赖（见 localeVersion 注释）。
        @Suppress("UNUSED_VARIABLE")
        val localeTick = localeVersion.value
        val context = UinApplication.getAppContext()
        return if (formatArgs.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *formatArgs)
        }
    }
}
