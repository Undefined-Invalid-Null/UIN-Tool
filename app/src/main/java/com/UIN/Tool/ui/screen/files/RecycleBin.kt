// app/src/main/java/com/UIN/Tool/ui/screen/files/RecycleBin.kt
package com.UIN.Tool.ui.screen.files

import com.UIN.Tool.constants.AppConstants
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 覆盖前的「后悔药」（用户反馈 e15 **P0-1**：覆盖已有文件时旧文件进回收站）。
 *
 * ## 为什么是**复制**而不是改名挪走
 *
 * 改名（`renameTo`）只要一次目录项操作，快得多；可挪走之后要是用户中途点了**取消**，
 * 落点就空了 —— 得再把挪走的那条挪回去。那意味着「挪去了哪儿」要一路随操作传到取消
 * 清理那段（复制/移动引擎、解压引擎各传一遍），多出来的状态就是新的出错面。
 *
 * 复制**完全不需要恢复**：原文件一直待在原地，覆盖是之后才发生的事。取消时落点要么
 * 没动、要么是半截（那是引擎的既有行为），而完整旧内容已经在回收站里，丢不了。
 * 代价是覆盖前多一次读+写 —— 换掉「取消会把文件弄丢」这个风险，值。
 *
 * ## 位置与容量
 *
 * `/sdcard/UIN_Tool/trash/`，文件名 = `时间戳_原名`。时间戳打头 → 文件名天然按时间排序，
 * 超量时删最旧的只要从头取。**同时卡个数与总字节**（见 [MAX_KEEP] / [MAX_BYTES]）——
 * 这里没有原路径可回（覆盖时旧文件就躺在同一个位置），所以界面上只提供**导出 / 删除**，
 * 不假装能「恢复原位」；容量上限仍必须卡死，不能让它悄悄把盘吃满。
 *
 * 失败一律**不拦覆盖**：没有后悔药也比「覆盖失败」强，这是降级不是报错。
 */
object RecycleBin {

    /** 最多留多少个。 */
    private const val MAX_KEEP = 200

    /** 最多占多少字节；超了按时间从旧到新删。 */
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024

    /** 回收站目录（覆盖前备份专用子目录；用户删除的条目在 `trash/deleted/`，见 TrashStore）。 */
    fun dir(): File = File(AppConstants.TRASH_DIR, "overwrite")

    /**
     * 一条「覆盖前备份」（回收站界面直接吃这个）。
     *
     * 目录里存的是普通文件，文件名编码了保存时间与原名
     * （`yyyyMMddHHmmssSSS_原名`），所以不需要元数据文件；时间解析失败就退回
     * 文件自身的修改时间，名字解析失败就原样显示文件名。
     */
    data class Backup(
        val file: File,
        /** 去掉时间戳前缀后的原文件名。 */
        val name: String,
        val savedAt: Long,
        val size: Long
    )

    /** 时间戳前缀：17 位数字 + 下划线。 */
    private val STAMP_PREFIX = Regex("^(\\d{17})_(.+)$")

    /** 列出全部备份，最新在前。 */
    suspend fun listBackups(): List<Backup> = withContext(Dispatchers.IO) {
        dir().listFiles().orEmpty()
            .filter { it.isFile }
            .map { f ->
                val m = STAMP_PREFIX.find(f.name)
                val name = m?.groupValues?.get(2) ?: f.name
                val savedAt = m?.groupValues?.get(1)?.let { parseStamp(it) } ?: f.lastModified()
                Backup(file = f, name = name, savedAt = savedAt, size = f.length())
            }
            .sortedByDescending { it.savedAt }
    }

    /** 删掉一条备份。 */
    suspend fun deleteBackup(b: Backup): Boolean = withContext(Dispatchers.IO) {
        runCatching { b.file.delete() }.getOrDefault(false)
    }

    /** 时间戳转毫秒；格式不对（老版本/手工改过名）就返回 null，由调用方退回修改时间。 */
    private fun parseStamp(s: String): Long? = try {
        SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).apply { isLenient = false }
            .parse(s)?.time
    } catch (_: Exception) {
        null
    }

    /** 清空备份，返回删掉的条数。 */
    suspend fun emptyBackups(): Int = withContext(Dispatchers.IO) {
        var n = 0
        dir().listFiles()?.forEach { f ->
            val gone = runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }
                .getOrDefault(false)
            if (gone) n++
        }
        n
    }

    /**
     * 把 [target] 的**内容**复制进回收站。
     *
     * @return 是否成功。关停开关、不是普通文件、或拷不动（没权限 / 空间不够）都返回
     *         false —— 调用方**照常覆盖**，只是这一份没有后悔药。
     */
    fun preserve(target: File): Boolean {
        if (!FilePrefs.overwriteTrash()) return false
        if (!target.isFile) return false
        return try {
            val root = dir()
            if (!root.isDirectory && !root.mkdirs()) return false
            val stamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(Date())
            var dst = File(root, "${stamp}_${target.name}")
            // 同一毫秒内覆盖两条同名文件时，别让第二条把第一条的备份顶掉
            var n = 1
            while (dst.exists()) dst = File(root, "${stamp}_${target.name}_${n++}")
            target.copyTo(dst, overwrite = true)
            trim(root)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 只保留最近 [MAX_KEEP] 个 / 不超过 [MAX_BYTES]，从最旧的开始删。 */
    private fun trim(root: File) {
        val files = root.listFiles()?.sortedBy { it.name } ?: return
        var total = 0L
        for (f in files) if (f.isFile) total += f.length()
        var i = 0
        while (i < files.size && (files.size - i > MAX_KEEP || total > MAX_BYTES)) {
            val f = files[i]
            val len = if (f.isFile) f.length() else 0L
            val gone = try {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            } catch (_: Throwable) {
                false
            }
            if (gone) total -= len
            i++
        }
    }
}
