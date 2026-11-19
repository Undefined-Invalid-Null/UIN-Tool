// app/src/main/java/com/UIN/Tool/ui/screen/docs/DocViewerActivity.kt
package com.UIN.Tool.ui.screen.docs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.UIN.Tool.ui.theme.UINToolTheme

/**
 * 文档阅读页。
 *
 * 两条来源，靠 intent 里的 extras 分流（`DocViewerScreen` 按有没有 [EXTRA_PATH] 判）：
 * - **内置文档**：只给 `doc_type`（help / dev / changelog / …），正文从 assets 里按语言取；
 * - **磁盘上的 markdown**（用户反馈：`.md` 要有内置的打开方式）：给 [EXTRA_PATH]，
 *   正文直接读那个文件，标题默认取文件名。
 *
 * 两条路共用同一个渲染器（commonmark → HTML → WebView），
 * 内置文档与用户自己的笔记看起来才是同一套排版。
 */
class DocViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                DocViewerScreen()
            }
        }
    }

    companion object {
        /** 非空 = 打开的是**磁盘上的真实文件**，此时 `doc_type` 不参与。 */
        const val EXTRA_PATH = "path"

        /** 标题栏文字。不给就由 `DocViewerScreen` 按来源自己推（文件名 / `docs`）。 */
        const val EXTRA_TITLE = "title"

        /**
         * 起一个「渲染磁盘上的 markdown 文件」的阅读页。
         *
         * [title] 给文件名即可 —— 标题栏写一整条绝对路径只会把栏挤没。
         */
        fun start(context: Context, path: String, title: String) {
            context.startActivity(
                Intent(context, DocViewerActivity::class.java).apply {
                    putExtra(EXTRA_PATH, path)
                    putExtra(EXTRA_TITLE, title)
                }
            )
        }

        /** 起一个**内置文档**阅读页（x.txt 第 1 条：文档之间的链接跳转用）。 */
        fun startDoc(context: Context, docType: String, title: String? = null) {
            context.startActivity(
                Intent(context, DocViewerActivity::class.java).apply {
                    putExtra("doc_type", docType)
                    if (!title.isNullOrBlank()) putExtra(EXTRA_TITLE, title)
                }
            )
        }
    }
}
