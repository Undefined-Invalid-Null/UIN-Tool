package com.UIN.Tool.ui.theme

import android.app.Activity
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.UIN.Tool.R
import com.UIN.Tool.constants.AppConstants as Constants
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig

/**
 * 冷启动的静态画面：底色 + 主题色图标 + 应用名 + 版本号。
 *
 * 之前开屏的最终形态就是这三行，后来嫌「放大动画」多余、又不想看到白屏，
 * 于是把同一份内容直接画进 windowBackground：从系统 starting window 到
 * 主界面首帧之间都能看到，且不依赖 Compose 首帧，启动再慢也不是白屏。
 *
 * 动画已去掉 —— 用户要求「和之前一样，只不过去掉动画，显示最终结果」。
 */
object LaunchScreenBackground {

    private const val TAG = "LaunchScreenBackground"

    /** 给 Activity 的窗口换上静态启动画面；UIConfig 未就绪时保留主题里的兜底背景 */
    fun apply(activity: Activity) {
        if (!UIConfig.isInitialized()) return
        try {
            val uiConfig = UIConfig.getInstance()
            val dark = uiConfig.shouldUseDarkTheme()
            val background = android.graphics.Color.parseColor(uiConfig.getColorStringForMode("background", dark))
            val primary = android.graphics.Color.parseColor(uiConfig.getColorStringForMode("primary", dark))
            val textPrimary = android.graphics.Color.parseColor(uiConfig.getColorStringForMode("text_primary", dark))
            // 与 Compose 开屏一致：版本行是 onBackground 的 60%
            val subtitleColor = ColorUtils.setAlphaComponent(textPrimary, 153)
            val version = Str.get(
                R.string.version_constants_app_version_build_,
                Constants.APP_VERSION,
                Constants.APP_VERSION_CODE
            )
            activity.window?.setBackgroundDrawable(
                LaunchScreenDrawable(
                    activity = activity,
                    iconTint = primary,
                    backgroundColor = background,
                    title = Str.get(R.string.app_name),
                    subtitle = version,
                    titleColor = primary,
                    subtitleColor = subtitleColor,
                    captionSizeSp = uiConfig.getCaptionTextSize()
                )
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "set launch background failed", e)
        }
    }

    /**
     * u.txt 第 4 条（用户）：配置变更（深浅色 / 语言）触发的 Activity 重建，
     * 不该再闪一次「图标 + 应用名 + 版本号」的开屏，也不该露出一块与主题不符的
     * 底色（深色下看着就是黑屏）。这里只把窗口底色换成**当前配置的背景色**，
     * 重建期间看到的就是一块与页面一致的纯色，首帧一到直接接上内容。
     */
    fun applyPlainBackground(activity: Activity) {
        if (!UIConfig.isInitialized()) return
        try {
            val uiConfig = UIConfig.getInstance()
            val dark = uiConfig.shouldUseDarkTheme()
            val background = android.graphics.Color.parseColor(
                uiConfig.getColorStringForMode("background", dark)
            )
            activity.window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(background)
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "set plain background failed", e)
        }
    }
}

/**
 * 只画「图标 + 应用名 + 版本号」的静态 Drawable，尺寸/间距对齐开屏 Compose 布局：
 * 图标 120dp、图标与标题 24dp、标题 24sp 粗体、标题与版本 8dp、版本行 caption 字号。
 */
class LaunchScreenDrawable(
    activity: Activity,
    iconTint: Int,
    backgroundColor: Int,
    private val title: String,
    private val subtitle: String,
    titleColor: Int,
    subtitleColor: Int,
    captionSizeSp: Float
) : Drawable() {

    private val metrics = activity.resources.displayMetrics
    private val icon: Drawable = requireNotNull(
        ContextCompat.getDrawable(activity, R.drawable.ic_splash_foreground)
    ).mutate().apply { setTint(iconTint) }

    private val iconSize = 120f * metrics.density
    private val gapIconTitle = 24f * metrics.density
    private val gapTitleSubtitle = 8f * metrics.density

    private val backgroundPaint = Paint().apply { color = backgroundColor }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textAlign = Paint.Align.CENTER
        textSize = 24f * metrics.scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = subtitleColor
        textAlign = Paint.Align.CENTER
        textSize = captionSizeSp * metrics.scaledDensity
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        canvas.drawRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            backgroundPaint
        )

        val titleHeight = lineHeight(titlePaint)
        val subtitleHeight = lineHeight(subtitlePaint)
        val totalHeight = iconSize + gapIconTitle + titleHeight + gapTitleSubtitle + subtitleHeight
        var top = bounds.top + (bounds.height() - totalHeight) / 2f
        val centerX = bounds.exactCenterX()

        val iconLeft = (centerX - iconSize / 2f).toInt()
        icon.setBounds(iconLeft, top.toInt(), (iconLeft + iconSize).toInt(), (top + iconSize).toInt())
        icon.draw(canvas)

        top += iconSize + gapIconTitle
        canvas.drawText(title, centerX, top - titlePaint.fontMetrics.ascent, titlePaint)

        top += titleHeight + gapTitleSubtitle
        canvas.drawText(subtitle, centerX, top - subtitlePaint.fontMetrics.ascent, subtitlePaint)
    }

    private fun lineHeight(paint: Paint): Float =
        paint.fontMetrics.let { it.descent - it.ascent }

    override fun setAlpha(alpha: Int) {
        // 启动画面不做透明度动画
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // 颜色由构造参数决定
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
