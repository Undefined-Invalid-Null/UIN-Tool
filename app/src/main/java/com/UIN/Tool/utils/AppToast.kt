// app/src/main/java/com/UIN/Tool/utils/AppToast.kt
package com.UIN.Tool.utils

import com.UIN.Tool.R
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import com.UIN.Tool.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 统一提示工具
 * 主界面有 SnackbarHost 时优先使用 Material Snackbar（更统一、可排队），
 * 无宿主（后台任务/非 Compose 上下文）时回退到系统 Toast。
 */
object AppToast {

    private const val TAG = "AppToast"
    private var currentToast: Toast? = null

    private var snackbarHost: SnackbarHostState? = null
        private set

    /**
     * m.txt 第 13 条：弹窗/整页 Dialog 自己的 Snackbar 宿主栈。
     *
     * 原生 Dialog 是独立窗口，主界面的 Snackbar 会被盖在下面；为了让弹窗内的
     * 「已复制」等提示仍然是 AppToast 的样式，[com.UIN.Tool.ui.components.unified.AppToastHost]
     * 会在弹窗窗口里登记自己的宿主，显示时优先用栈顶那个。
     */
    private val overlayHosts = mutableListOf<SnackbarHostState>()

    @Synchronized
    fun pushHost(host: SnackbarHostState) {
        if (!overlayHosts.contains(host)) overlayHosts.add(host)
    }

    @Synchronized
    fun popHost(host: SnackbarHostState) {
        overlayHosts.remove(host)
    }

    private val activeHost: SnackbarHostState?
        @Synchronized get() = overlayHosts.lastOrNull()
            ?: if (overlayCount == 0) snackbarHost else null

    /**
     * 当前打开的模态弹窗层数（[pushOverlay] / [popOverlay] 由 [UnifiedDialog] 维护）。
     *
     * 没有登记弹窗宿主时**必须改走系统 Toast**：Snackbar 画在宿主 Activity 的窗口里，
     * 而弹窗是另一个层级更高的窗口，提示会整个被盖在弹窗背后（用户反馈 c10）。
     * 弹窗自己登记了 [overlayHosts] 时则会显示在弹窗窗口内（m.txt 第 13 条）。
     */
    @Volatile
    private var overlayCount = 0

    /** 一个模态弹窗出现（进组合）。 */
    fun pushOverlay() {
        overlayCount++
    }

    /** 一个模态弹窗消失（离开组合，覆盖返回键/点外部/确认关闭三条路）。 */
    fun popOverlay() {
        if (overlayCount > 0) overlayCount--
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** j.txt 第 14 条：用于取消「长文本续播」，避免旧提示把新提示顶掉。 */
    private var toastGeneration = 0

    /** j.txt 第 14 条：Snackbar 的自定义时长也要按代际作废，否则旧定时器会关掉新提示。 */
    private var snackbarGeneration = 0

    /**
     * j.txt 第 14 条：按文本长度换算显示时长。
     *
     * 系统 Toast 只有 SHORT / LONG 两档，Snackbar 也只有 Short / Long / Indefinite；
     * 长文本（多行错误、路径、批量结果）用 SHORT 经常还没读完就消失。这里统一按长度
     * 分档：短句维持原时长，长句自动升到 LONG，Compose Snackbar 则用自定义毫秒数。
     */
    private fun lengthOf(message: String): Int = message.codePointCount(0, message.length)

    private fun snackbarMillis(message: String): Long {
        val n = lengthOf(message)
        return when {
            n >= 200 -> 10_000L
            n >= 120 -> 8_000L
            n >= 60 -> 6_000L
            else -> 4_000L
        }
    }

    /**
     * 由主题根节点绑定当前前台界面的 Snackbar 宿主
     */
    fun bindHost(host: SnackbarHostState) {
        snackbarHost = host
    }

    /**
     * 解绑宿主；仅当仍指向同一宿主时才清空，避免覆盖后方的界面
     */
    fun unbindHost(host: SnackbarHostState) {
        if (snackbarHost === host) {
            snackbarHost = null
        }
    }

    /**
     * 显示提示
     * 线程安全：后台线程调用时自动切回主线程显示
     * @param context 上下文
     * @param message 消息内容
     * @param duration 显示时长，默认 Toast.LENGTH_SHORT
     */
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val host = activeHost
        // 优先使用当前窗口的宿主：弹窗/整页 Dialog 会登记自己的 SnackbarHost（m.txt 第 13 条），
        // 这样「已复制」等提示在弹窗里也保持 AppToast 的样式；没有宿主时才退回系统 Toast。
        if (host != null) {
            // j.txt 第 14 条：Snackbar 用自定义毫秒数（Indefinite + 定时关闭），
            // 否则长文本只能吃固定 4s / 10s 两档，和实际阅读时间对不上。
            val autoMs = maxOf(
                snackbarMillis(message),
                if (duration == Toast.LENGTH_LONG) 6_000L else 0L
            )
            scope.launch {
                try {
                    val generation = ++snackbarGeneration
                    // 批次44 修「连点属性后 toast 隔很久才逐条冒出来」：
                    // SnackbarHostState.showSnackbar 是**串行队列** —— 连点 N 次会挂起排队，
                    // 前一条播完才放后一条，间隔被越拖越长。先把正在显示的撤掉，
                    // 让**最新一条**立刻顶上（点多少次都以最后一条为准，几乎零延迟）。
                    host.currentSnackbarData?.dismiss()
                    launch {
                        delay(autoMs)
                        if (generation == snackbarGeneration) {
                            host.currentSnackbarData?.dismiss()
                        }
                    }
                    host.showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
                } catch (e: Exception) {
                    Logger.e(TAG, Str.get(R.string.toast_failed_to_show), e)
                }
            }
            return
        }

        val appContext = context.applicationContext
        val codePoints = lengthOf(message)
        // 长文本自动从 SHORT 升到 LONG；极长的再补播一次（系统 Toast 的时长上限
        // 由 ROM 决定，不能真正自定义毫秒数，续播是唯一可靠的办法）。
        val systemDuration =
            if (duration == Toast.LENGTH_SHORT && codePoints >= 30) Toast.LENGTH_LONG else duration
        Handler(Looper.getMainLooper()).post {
            try {
                val generation = ++toastGeneration
                currentToast?.cancel()
                val toast = styledToast(appContext, message, systemDuration)
                currentToast = toast
                toast.show()
                if (codePoints >= 120) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (generation != toastGeneration) return@postDelayed
                        currentToast?.cancel()
                        val again = styledToast(appContext, message, Toast.LENGTH_LONG)
                        currentToast = again
                        again.show()
                    }, 3_200L)
                }
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.toast_failed_to_show), e)
            }
        }
    }

    /**
     * n.txt 第 8 条：没有宿主时不再用 ROM 的默认 Toast 外观。
     *
     * 默认 Toast 的底色由系统/主题决定 —— 同一个 AppToast，有宿主时是 surfaceVariant
     * 圆角卡片，没宿主时变成白色（浅色）或深灰（深色）的系统胶囊，用户看到的就是
     * 「背景色不一样」。本应用 targetSdk 停在 28，`Toast.setView` 仍然生效，
     * 因此这里按 Snackbar 的同一组配色自绘：同样的圆角、同样的底/字色。
     */
    @Suppress("DEPRECATION")
    private fun styledToast(context: Context, message: String, duration: Int): Toast {
        val cfg = UIConfig.getInstance()
        val dark = cfg.shouldUseDarkTheme()
        val bg = cfg.getColorForMode("surface_variant", dark)
        val fg = cfg.getColorForMode("text_primary", dark)
        val density = context.resources.displayMetrics.density
        val view = android.widget.TextView(context).apply {
            text = message
            setTextColor(fg)
            textSize = 14f
            gravity = android.view.Gravity.CENTER_VERTICAL
            maxWidth = (context.resources.displayMetrics.widthPixels * 0.86f).toInt()
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(bg)
                cornerRadius = 14 * density
            }
        }
        return Toast(context).apply {
            this.view = view
            this.duration = duration
            // 与根 SnackbarHost 的位置对齐（bottom + 64dp 安全区），避免两种后端一高一低。
            setGravity(
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
                0,
                (96 * density).toInt()
            )
        }
    }

    /**
     * 显示成功提示 - 使用绿色对勾
     */
    fun success(context: Context, message: String) {
        show(context, "✔ $message")
        Logger.i(TAG, "Success: $message")
    }

    /**
     * 显示错误提示 - 使用红色叉号
     */
    fun error(context: Context, message: String) {
        show(context, "✘ $message")
        Logger.e(TAG, "Error: $message")
    }

    /**
     * 显示警告提示 - 使用黄色感叹号
     */
    fun warning(context: Context, message: String) {
        show(context, "⚠ $message")
        Logger.w(TAG, "Warning: $message")
    }

    /**
     * 显示信息提示 - 使用蓝色信息图标
     */
    fun info(context: Context, message: String) {
        show(context, "ℹ $message")
        Logger.i(TAG, "Info: $message")
    }

    /**
     * 显示长提示
     */
    fun showLong(context: Context, message: String) {
        show(context, message, Toast.LENGTH_LONG)
    }

    /**
     * 取消当前显示的 Toast
     */
    fun cancel() {
        toastGeneration++
        currentToast?.cancel()
        currentToast = null
    }
}
