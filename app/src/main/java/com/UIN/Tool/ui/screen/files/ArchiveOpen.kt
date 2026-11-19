// app/src/main/java/com/UIN/Tool/ui/screen/files/ArchiveOpen.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 归档内条目「按需解出」的公共入口（f.txt 追加反馈：包里的文件都要能打开）。
 *
 * 以前只有**文本**与 **APK** 有解出副本的路，图片 / 字体 / 音视频 / 未知后缀一律一句
 * 「先解压才能打开」。用户真正要的是「点一下就看」，包内包也一样 —— 那就统一：
 * 解一条到私有缓存，再走与真实文件**完全同一条**打开分流。
 *
 * ## 为什么不复用 `ArchiveEdit`
 *
 * `ArchiveEdit` 的副本是**给编辑器写回**用的：确定性路径 + 打开即编辑 + 保存问写回。
 * 这里的副本是**一次性查看**：同一条目重复点要命中同一份（否则每次点都重新解一遍），
 * 但不需要写回语义。两条需求都要求「确定性路径」，所以共用同一套摘要规则即可，
 * 但目录分开（`cache/archive_open/` vs `cache/archive_edit/`），互不覆盖。
 *
 * ## 失败口径（用户明确要求）
 *
 * **不许一句「归档已损坏」糊过去**。能进到这个函数，说明这个包刚刚列得出目录，
 * 包本身是好的；解不出**某一条**只可能是：
 * - 条目加密 / 需要密码（单独提示，交给上层重输）；
 * - 这个条目**必须整包解压**才能取（分卷、未支持的单条目特性…）→ 单独提示；
 * 两种情况都不再说包坏了。
 */
object ArchiveOpen {

    /** `cacheDir` 下的子目录名。 */
    const val DIR = "archive_open"

    /** 超过这个时长的查看副本在下次解出前清掉（进程内只清一次）。 */
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    @Volatile
    private var cleaned = false

    /**
     * 把 [internalPath] 解到查看缓存并返回真实文件。
     *
     * 同一条目重复调用直接命中已有副本（大小 > 0 即视为完整：解出是「先写 .part 再改名」
     * 的原子落盘，见 `ArchiveExtract`/`ZipWrite`，不会留下半截正式文件）。
     *
     * @throws ArchiveEntryOpenException 取不出来时；[ArchiveEntryOpenException.needsFullExtract]
     *         区分「这个文件要整包解压」与「要先给密码」。
     */
    suspend fun extractEntry(context: Context, archive: File, internalPath: String): File {
        val path = internalPath.trim('/')
        if (path.isEmpty()) throw ArchiveEntryOpenException(needsFullExtract = true)
        val name = path.substringAfterLast('/')
        val dir = File(
            File(context.cacheDir, DIR),
            digest(archive.absolutePath + "\n" + archive.lastModified() + "\n" +
                archive.length() + "\n" + path)
        )
        val out = File(dir, name)

        cleanupOld(context)

        if (out.isFile && out.length() > 0) return out
        dir.mkdirs()
        // 上一轮失败留下的 0 字节残件：不清掉的话这次解出会因为「目标已存在」被改名成
        // `name (1)`，返回的还是空文件。
        if (out.exists()) runCatching { out.delete() }

        val src = File(VirtualPath.join(archive.absolutePath, path))
        val result = try {
            ArchiveExtract.execute(
                kind = OpKind.COPY,
                sources = listOf(src),
                targetDir = dir,
                password = cachedArchivePassword(archive),
                onProgress = {},
                onConflict = { null }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw classify(e, null)
        }
        if (result.cancelled) throw CancellationException("cancelled")
        if (result.failedCount > 0 || !out.isFile) throw classify(null, result.firstError)
        return out
    }

    /** 把条目解到**指定路径**（`ArchiveEdit` 在非 zip 包上用：见那个文件的说明）。 */
    suspend fun extractInto(out: File, archive: File, internalPath: String) {
        val path = internalPath.trim('/')
        if (path.isEmpty()) throw IOException("empty entry")
        out.parentFile?.mkdirs()
        if (out.exists()) runCatching { out.delete() }
        val dir = out.parentFile ?: throw IOException("no parent: $out")
        val src = File(VirtualPath.join(archive.absolutePath, path))
        val result = try {
            ArchiveExtract.execute(
                kind = OpKind.COPY,
                sources = listOf(src),
                targetDir = dir,
                password = cachedArchivePassword(archive),
                onProgress = {},
                onConflict = { null }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw classify(e, null)
        }
        if (result.cancelled) throw CancellationException("cancelled")
        if (result.failedCount > 0 || !out.isFile) throw classify(null, result.firstError)
    }

    private fun classify(e: Throwable?, message: String?): ArchiveEntryOpenException {
        val text = (message ?: e?.message ?: e?.javaClass?.simpleName ?: "unknown")
        val lower = text.lowercase()
        val password = lower.contains("password") ||
            lower.contains("encrypted") ||
            lower.contains("密码") ||
            lower.contains("加密")
        return ArchiveEntryOpenException(needsFullExtract = !password, detail = text)
    }

    /** 3 天前的查看副本清掉 —— 只清一次，别每次点都全目录遍历。 */
    private fun cleanupOld(context: Context) {
        if (cleaned) return
        cleaned = true
        runCatching {
            val root = File(context.cacheDir, DIR)
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            root.listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
        }
    }

    private fun digest(text: String): String = try {
        MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        Integer.toHexString(text.hashCode())
    }
}

/**
 * 包内条目取不出来。
 *
 * [needsFullExtract] = true：这个条目不能单独取出，需要先整包解压；
 * false：多半是加密条目，缺密码（上层应给「输入密码」或「先解压」的提示）。
 */
class ArchiveEntryOpenException(
    val needsFullExtract: Boolean,
    val detail: String? = null
) : IOException(detail ?: "archive entry open failed")
