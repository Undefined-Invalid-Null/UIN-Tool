// app/src/main/java/com/UIN/Tool/ui/screen/files/FileDeepLink.kt
package com.UIN.Tool.ui.screen.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `uin://files?path=<目录>&file=<文件绝对路径>` 的一次待处理请求（§4.16）。
 *
 * 两个参数都是**绝对路径**：`path` 定位目录，`file` 在其之上再定位具体文件。
 * 缺 `path` 时用 `file` 的父目录兜底；两个都缺 = 只切到文件页（等价 `uin://files`）。
 */
data class FileDeepLink(
    /** 目标目录绝对路径；空 = 无从推断 → 沿用当前窗格的目录 */
    val dirPath: String,
    /** 要定位高亮的文件绝对路径；null = 只跳目录不高亮 */
    val filePath: String? = null,
    /**
     * 挂着浏览的归档绝对路径（i.txt 第 6 条：APK 信息页的「以压缩包查看」）。
     *
     * 非空时不再「跳目录 + 高亮」，而是直接把这个包**挂进活动窗格** —— 与在文件列表里
     * 点包进去是同一条路（`VirtualPath.join(path, "")` 即包根）。
     */
    val mountArchive: String? = null
)

/**
 * 深链请求的**单次投递总线**。
 *
 * 做成单例而不是把请求当参数从 `MainActivity` 一路传进 `FilesScreen`，两个理由：
 *
 * 1. 文件页的 VM 是组合里 `viewModel()` 创建的，Activity 拿不到实例，无处下发；
 * 2. 深链可能在**本页尚未组合**时就到达（冷启动直接停在别的 tab），
 *    参数式下发会随那一次重组丢掉，必须有个地方存着等页面出现。
 *
 * 消费方式与 [FileSearch] 一致：MainActivity 侧 `post`，`FilesScreen` 侧
 * `collectAsState` 收到后立刻 `consume()` —— **只消费一次**。
 * 不 `consume` 会让值一直挂着，旋转/重建后重复跳一次目录（高亮也跟着重来）。
 *
 * 用 `StateFlow` 而非 `SharedFlow` 是刻意的：`post` 可能发生在页面组合之前，
 * SharedFlow 的 buffer 只能保证「订阅后」的事件，StateFlow 则是「当前值」——
 * 正好是我们要的「等页面出现时再取」。
 */
object FileDeepLinkBus {
    private val _pending = MutableStateFlow<FileDeepLink?>(null)
    val pending: StateFlow<FileDeepLink?> = _pending.asStateFlow()

    /** 深链到达。覆盖上一次未被消费的请求（新的更近，旧的没有被处理的价值）。 */
    fun post(link: FileDeepLink) {
        _pending.value = link
    }

    /** 消费掉当前请求。幂等，可以重复调。 */
    fun consume() {
        _pending.value = null
    }
}
