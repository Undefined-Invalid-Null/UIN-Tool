// app/src/main/java/com/UIN/Tool/utils/ThemeShortcuts.kt
package com.UIN.Tool.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.UIN.Tool.R
import com.UIN.Tool.ShortcutAppInfoActivity
import com.UIN.Tool.log.Logger

/**
 * o.txt 第 15 条：桌面长按图标的快捷方式，图标颜色跟**主题色**走。
 *
 * `res/xml/shortcuts.xml` 里的静态快捷方式只能引用固定的 drawable —— 启动器在**自己的**
 * 进程里加载它们，既拿不到本应用的主题，也不会去解析 `?attr/colorPrimary`。所以这里在
 * 运行时把同一批图标按当前主题色染色，再以**动态快捷方式**发布；动态与静态 id 相同，
 * 会覆盖静态那条（ShortcutManager 的既有行为），于是长按菜单里看到的就是主题色图标。
 *
 * 静态 XML 仍然保留：动态发布失败（旧系统 / 被限频 / 用户清过）时，至少还有可用的
 * 快捷方式，只是图标退回 drawable 的固有色。
 *
 * 只在「主题色变了」或「缺了某条」时才重新发布 —— 系统对动态快捷方式的更新有频率限制。
 */
object ThemeShortcuts {

    private const val TAG = "ThemeShortcuts"
    private const val PREFS = "theme_shortcuts"
    private const val KEY_TINT = "icon_tint"
    private const val KEY_ICON_VERSION = "icon_version"

    /**
     * 图标画法版本。**画法一变就必须 +1** —— 只比主题色的话，改了尺寸 / 形状 / 配色
     * 之后已装的用户永远不会重新发布（存下的 tint 没变、四条也都在，于是直接 return）。
     * t.txt 第 2 条：2 = 512px 徽章 + 四枚图形统一纯黑。
     */
    private const val ICON_VERSION = 2

    /**
     * 快捷方式图标位图边长（px）。
     *
     * p.txt 第 12 条（用户）：原先 108px 在启动器里发虚、图形也小。启动器按
     * 108dp 的 xxhdpi 档位要 324px。
     * s.txt 第 2 条（用户）：再提到 512px —— 图形一律取自已有的 ic_* drawable
     * （矢量），位图只负责把「圆角底 + 图形」合成为一张高分辨率成品，放大、
     * 高分屏或启动器二次裁切都不会糊。
     */
    private const val ICON_PX = 512

    private data class Spec(
        val id: String,
        @DrawableRes val icon: Int,
        val shortLabel: Int,
        val longLabel: Int,
        val intent: (Context) -> Intent
    )

    private fun specs(): List<Spec> = listOf(
        // 「打开文档浏览器」→「打开文件管理器」：走 uin://files 深链，直接落到文件页。
        Spec(
            id = "open_files",
            icon = R.drawable.ic_folder,
            shortLabel = R.string.shortcut_open_files,
            longLabel = R.string.shortcut_open_files_long,
            intent = { ctx ->
                Intent(Intent.ACTION_VIEW, Uri.parse("uin://files"))
                    .setClassName(ctx.packageName, "${ctx.packageName}.MainActivity")
            }
        ),
        Spec(
            id = "open_terminal",
            icon = R.drawable.ic_terminal_prompt,
            shortLabel = R.string.shortcut_open_terminal,
            longLabel = R.string.shortcut_open_terminal_long,
            intent = { ctx ->
                Intent(Intent.ACTION_RUN).setClassName(
                    ctx.packageName,
                    "${ctx.packageName}.terminal.SimpleTerminalActivity"
                )
            }
        ),
        // 「打开后端设置」→「打开应用信息」：系统设置里的本应用详情页。
        Spec(
            id = "open_app_info",
            icon = R.drawable.ic_app_info,
            shortLabel = R.string.shortcut_open_app_info,
            longLabel = R.string.shortcut_open_app_info_long,
            // 动态快捷方式的 intent 必须带 action（ShortcutInfo 的硬性校验），
            // 不能只给 component —— 所以显式补一个 ACTION_VIEW。
            intent = { ctx ->
                Intent(Intent.ACTION_VIEW).setClassName(
                    ctx.packageName,
                    ShortcutAppInfoActivity::class.java.name
                )
            }
        ),
        Spec(
            id = "open_code_editor",
            icon = R.drawable.ic_code,
            shortLabel = R.string.shortcut_open_editor,
            longLabel = R.string.shortcut_open_editor_long,
            intent = { ctx ->
                Intent("com.UIN.Tool.action.OPEN_CODE_EDITOR").setClassName(
                    ctx.packageName,
                    "${ctx.packageName}.ui.screen.dev.CodeEditorActivity"
                )
            }
        )
    )

    /** 启动时 / 主题色变化时调用。异常一律吞掉 —— 快捷方式不值得影响主流程。 */
    fun sync(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val app = context.applicationContext
        try {
            val manager = app.getSystemService(ShortcutManager::class.java) ?: return
            val config = UIConfig.getInstance()
            val dark = config.shouldUseDarkTheme(config.isSystemDark())
            val tint = config.getColorForMode("primary", dark)
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            val all = specs()
            val published = manager.dynamicShortcuts.map { it.id }.toSet()
            val allPresent = all.all { it.id in published }
            if (prefs.getInt(KEY_TINT, 0) == tint &&
                prefs.getInt(KEY_ICON_VERSION, 0) == ICON_VERSION &&
                allPresent
            ) return

            val infos = all.mapNotNull { spec ->
                val icon = tintedIcon(app, spec.icon, tint) ?: return@mapNotNull null
                ShortcutInfo.Builder(app, spec.id)
                    .setShortLabel(app.getString(spec.shortLabel))
                    .setLongLabel(app.getString(spec.longLabel))
                    .setIcon(icon)
                    .setIntent(spec.intent(app))
                    .build()
            }
            if (infos.isEmpty()) return
            // addDynamicShortcuts：按 id 增量发布，不动插件自己创建的 plugin_* 快捷方式。
            // 系统对动态快捷方式有数量上限（getMaxShortcutCountPerActivity，常见 5 条），
            // 插件占满时整批会被拒 —— 退回逐条 push，让系统按优先级自己淘汰。
            try {
                manager.addDynamicShortcuts(infos)
            } catch (e: Exception) {
                infos.forEach { runCatching { manager.pushDynamicShortcut(it) } }
            }
            prefs.edit()
                .putInt(KEY_TINT, tint)
                .putInt(KEY_ICON_VERSION, ICON_VERSION)
                .apply()
        } catch (e: Exception) {
            Logger.w(TAG, "sync failed: ${e.message}")
        }
    }

    /**
     * p.txt 第 12 条（用户）：快捷方式图标**颜色统一、图形清晰**。
     *
     * 旧实现只把矢量图形本身染成主题色，四个图标在启动器里是四块粗细不一的
     * 细线条，既不同形也不同重。现在统一成同一套「圆角底 + 白色图形」徽章：
     *  - 底色取主题主色，并向下压暗 22% 做轻微渐变（四枚完全同色）；
     *  - 图形按底色亮度选黑/白，保证对比度；
     *  - 512px 位图 + 10% 安全边距，启动器裁圆/裁方都切不到图形。
     *  - 图形全部来自已有的 `ic_folder` / `ic_terminal_prompt` / `ic_app_info` /
     *    `ic_code`（s.txt 第 2 条：不自己画），这里只做合成与染色。
     */
    private fun tintedIcon(context: Context, @DrawableRes res: Int, tint: Int): Icon? {
        // 注意：这里传的必须是**纯图形** drawable（ic_folder / ic_terminal_prompt /
        // ic_app_info / ic_code），不是 res/drawable/ic_shortcut_*.xml —— 后者本身
        // 已经画了圆角底，再走一遍这套徽章就会叠成两层。
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val size = ICON_PX.toFloat()

        // r.txt 第 4 条（用户）：背景别占满整格、图形再大一点并保持居中。
        // 背景从贴边收进 8%，圆角随背景尺寸一起算；图形安全边距 10%（原 16%），
        // 图形与背景四周都留出均匀的一圈空隙，视觉重心落在正中间。
        val bgInset = size * 0.08f
        val bgRect = RectF(bgInset, bgInset, size - bgInset, size - bgInset)
        val background = Paint(Paint.ANTI_ALIAS_FLAG)
        background.shader = LinearGradient(
            0f, bgInset, 0f, size - bgInset,
            tint,
            ColorUtils.blendARGB(tint, Color.BLACK, 0.22f),
            Shader.TileMode.CLAMP
        )
        val radius = bgRect.width() * 0.26f
        canvas.drawRoundRect(bgRect, radius, radius, background)

        drawable.mutate()
        drawable.setTint(
            if (ColorUtils.calculateLuminance(tint) > 0.6) Color.BLACK else Color.WHITE
        )
        // q.txt 第 8 条（用户）：图标要更大、底色别占太多。
        // r.txt 第 4 条（用户）：再放大一档并居中 —— 10% 安全边距即 80% 画布。
        val inset = (size * 0.10f).toInt()
        drawable.setBounds(inset, inset, ICON_PX - inset, ICON_PX - inset)
        drawable.draw(canvas)
        return Icon.createWithBitmap(bitmap)
    }
}
