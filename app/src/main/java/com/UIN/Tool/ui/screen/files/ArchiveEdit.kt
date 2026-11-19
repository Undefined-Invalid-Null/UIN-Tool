// app/src/main/java/com/UIN/Tool/ui/screen/files/ArchiveEdit.kt
package com.UIN.Tool.ui.screen.files

import java.io.File
import java.security.MessageDigest

/**
 * 归档内条目的**编辑临时副本区**（MT.md §4.15「编辑归档内文本」）。
 *
 * ## 为什么要有这么一层
 *
 * 编辑器整套能力（分页窗口模式按**字节偏移**取页、全文检索、撤销栈、原子替换落盘）
 * 都建立在「一个真实可 seek 的文件路径」之上。往归档里塞虚拟路径
 * （`x.zip!/a/b.txt`）会让每个入口都得特判，且分页那套根本没法用 ——
 * 归档条目在磁盘上是**压缩过的连续字节**，没有可寻址的行。
 *
 * 所以：**先解成真实副本，编辑副本，保存时再问用户要不要写回归档。**
 * 这样 99% 的能力零改动复用，写回归档只是一次独立的收尾动作（`ZipWrite.replaceEntry`）。
 *
 * ## 路径为什么是确定性的
 *
 * `<cacheDir>/archive_edit/<归档路径摘要>/<条目路径>`
 *
 * 同一个归档内同一条目**永远**解到同一个位置。这有两个用处：
 * 1. 编辑器按 `file.absolutePath` 去重 —— 重复打开同一归档内同一条目会命中已有标签，不会开两份；
 * 2. 用户连开、关、再开时，看到的仍是同一份（含上次没写回的改动），不会莫名回到旧内容。
 *
 * 摘要用 [MessageDigest] 而不是 `String.hashCode()`：后者虽是 JLS 规定的确定值，
 * 但只有 32 位、碰撞后两条目会共用一份副本 —— 那是**写错文件**级别的事故。
 * SHA-1 取前 12 个十六进制字符（48 位）对「一台手机上的归档数」足够。
 */
object ArchiveEdit {

    /** [cacheDir] 下的子目录名（与编辑器草稿区 `editor_draft` 平级、互不相干）。 */
    const val DIR = "archive_edit"

    /** 临时副本区根目录。 */
    fun root(cacheDir: File): File = File(cacheDir, DIR)

    /** 某绝对路径是否落在副本区内（编辑器据此**不把它记成「上次打开的文件」**）。 */
    fun isStagingPath(cacheDir: File, path: String): Boolean =
        path.startsWith(root(cacheDir).absolutePath + File.separator)

    /**
     * 条目副本的确定性路径。
     *
     * 条目路径里带 `/`，直接当文件名会变成多层目录 —— 那样反而更自然
     * （`a/b/c.txt` 的副本就是 `<hash>/a/b/c.txt`），父目录由 [extract] 创建。
     */
    fun stagingFile(cacheDir: File, archive: File, entryPath: String): File =
        File(File(root(cacheDir), digest(archive)), entryPath.removeSuffix("/"))

    /**
     * 解出条目到副本，返回该副本。
     *
     * 走 [ZipWrite.copyEntryTo] 流式落盘，不整条读进内存 —— 归档里的文本条目
     * 大小完全不受控，几百 MB 很常见，读成 `ByteArray` 必 OOM。
     *
     * 时间戳**不对齐**：副本的修改时间就是落盘时刻。对齐条目时间需要额外读一次
     * 条目头，而它对用户没有可观察价值（编辑器状态栏显示的是编码/行数，不是文件时间），
     * 写回归档时用的也是这次的编辑内容、不是这个时间。
     */
    suspend fun extract(cacheDir: File, archive: File, entryPath: String): File {
        val out = stagingFile(cacheDir, archive, entryPath)
        out.parentFile?.mkdirs()
        // 写侧只有 zip 能写回（`ZipWrite` 是 zip4j），但**读**所有格式都能读：
        // 非 zip 的条目交给通用解出（ArchiveOpen），先让用户能打开；保存时若选「写回」，
        // 编辑器那条路会如实报失败并引导「另存为」——包装不了不假装能装。
        if (detectArchiveFormat(archive) == ArchiveFormat.ZIP) {
            ZipWrite.copyEntryTo(archive, entryPath, out)
        } else {
            ArchiveOpen.extractInto(out, archive, entryPath)
        }
        return out
    }

    /** 归档路径 → 12 位十六进制摘要（见类注释：不用 hashCode，碰撞会写错文件）。 */
    private fun digest(archive: File): String = try {
        MessageDigest.getInstance("SHA-1")
            .digest(archive.absolutePath.toByteArray(Charsets.UTF_8))
            .take(6)                                  // 6 字节 = 12 个十六进制字符
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        // SHA-1 一定存在；真取不到就退化成路径替换（仍确定、只是更长更丑）。
        archive.absolutePath.replace(Regex("[^A-Za-z0-9]"), "_")
    }
}
