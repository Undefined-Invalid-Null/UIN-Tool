// app/src/main/java/com/UIN/Tool/ui/screen/files/ApkExtractActivity.kt
package com.UIN.Tool.ui.screen.files

import com.UIN.Tool.ui.components.unified.UnifiedDropdownMenu
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.UIN.Tool.R
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedAlertDialog
import com.UIN.Tool.ui.components.unified.PullRefreshIndicator
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.UIN.Tool.ui.components.unified.UnifiedProgressDialog
import com.UIN.Tool.ui.components.unified.UnifiedProgressState
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.UIConfig
import com.UIN.Tool.utils.shareFiles
import com.UIN.Tool.ui.components.unified.NeuDefaults
import com.UIN.Tool.ui.components.unified.neuRaised
import java.io.File
import java.util.zip.ZipFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ApkExtract"

/** 列表图标统一转这么大 —— 一格才 34dp，128px 已是两倍余量，再大是白烧内存。 */
private const val ICON_PX = 128

/**
 * 一次最多分享多少个应用（反馈 e1「批量无分享选中」）。
 *
 * 中转是**逐个把 APK 拷出去**，几十个应用动辄几个 GB —— 没有上限的话，
 * 一次误选就能把存储写满，而系统分享面板多半也吃不下这么多文件。
 */
private const val MAX_SHARE_APPS = 30

/**
 * 一次提取任务：一个或多个应用（**批量提取**），[all] 为真时连分包一起算上。
 *
 * 目标文件名在 [InstalledApp.targets] 里已经带包名前缀，几个应用倒进同一个
 * 目录也不会互相覆盖 —— 批量因此只是「多跑几遍单个」，不需要另起一套命名规则。
 */
private data class ExtractJob(
    val apps: List<InstalledApp>,
    val all: Boolean,
    val dest: String,
    /**
     * 显式指定要提的 (源路径, 目标名)。给了就以它为准 ——
     * 反馈 e1「分包不能单选」：只提某一个分包走这条，
     * 不必把「提 base / 提全部」再各开一档。
     */
    val explicit: List<Pair<String, String>>? = null,
    /**
     * g5：部件改成**独立单选项**。非空 = 这次只提部件，[Part] 是用户在展开面板里
     * 勾中的那几类；结果打包成 `<包名>-parts.zip`（不再落一个文件夹）。
     */
    val parts: Set<Part> = emptySet()
)

/**
 * 一台机器上装着的一个应用。
 *
 * [sourceDir] / [splitDirs] 直接来自 [ApplicationInfo]，是**真实文件路径** ——
 * 提取就是把这些文件复制出去，不解析、不反编译，因此不需要 root，也不需要存储权限
 * （`/data/app` 下的 APK 本身是全局可读的，这是所有免 root 提取器的共同前提）。
 */
private data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val sourceDir: String,
    val splitDirs: List<String>,
    val isSystem: Boolean,
    /**
     * 安装 / 更新时间（ms）。反馈 e1「列表只按名称排」：找「上周装的那个」时
     * 按名称翻是翻不到的，这两个值让排序能按时间走。
     */
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    /**
     * g5「APK 信息支持显示数据目录」：内部 `/data/data/<包名>` 与外部
     * `Android/data/<包名>` 原文照摆。内部那份没有 root 读不了 —— 但「路径是什么」
     * 本身就是用户要的信息，不因为不可读就不显示。
     */
    val dataDir: String = "",
    val externalDataDir: String = ""
) {
    /** 单个分包的 (源路径, 目标名) —— 反馈 e1「分包不能单选」就提这一个。 */
    fun splitTarget(split: String): Pair<String, String> =
        split to expandApkName(FilePrefs.apkPartTemplate(), this, File(split).name)

    /**
     * 要复制的 (源路径, 目标文件名)。
     *
     * 文件名默认**带包名前缀**：每个应用的主包都叫 `base.apk`、分包都叫
     * `split_config.<abi>.apk`，不带前缀的话换个应用提取就把上一个覆盖了。
     * i.txt 第 3 条：模板可在右上角三点设置里改（表达式见 [expandApkName]）。
     */
    fun targets(
        all: Boolean,
        nameTemplate: String = FilePrefs.apkNameTemplate(),
        partTemplate: String = FilePrefs.apkPartTemplate()
    ): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>(splitDirs.size + 1)
        list.add(sourceDir to expandApkName(nameTemplate, this, "base.apk"))
        if (all) {
            for (split in splitDirs) {
                list.add(split to expandApkName(partTemplate, this, File(split).name))
            }
        }
        return list
    }

    /** 列表第二行的「包名 · 版本」；没读到版本就只显示包名，不留空尾巴。 */
    fun caption(): String =
        if (versionName.isEmpty()) packageName else "$packageName · $versionName"
}

/**
 * 命名表达式展开（i.txt 第 3 条）。
 *
 * 支持的占位符：
 * - `{package}` 包名
 * - `{label}`   应用显示名
 * - `{version}` 版本名（读不到时用 `unknown`）
 * - `{name}`    原始文件名（主包是 `base.apk`，分包是它自己的名字）
 *
 * 展开后把路径分隔符换掉 —— 表达式里写进 `/` 时，`File(dir, name)` 会跑到子目录去，
 * 提取时父目录不存在直接失败。剩下的字符原样保留，重名由 `copyFile` 的目标存在策略处理。
 */
private fun expandApkName(template: String, app: InstalledApp, originalName: String): String {
    val expanded = template
        .replace("{package}", app.packageName)
        .replace("{label}", app.label)
        .replace("{version}", app.versionName.ifBlank { "unknown" })
        .replace("{name}", originalName)
        .replace('/', '_')
        .replace('\u0000', '_')
        .trim()
    return expanded.ifBlank { originalName }
}

/**
 * 已装应用的图标缓存（按包名）。
 *
 * 用 **SnapshotStateMap** 而不是普通 HashMap：图标是**边看边补**的（见 [fillIcons]），
 * 补到哪个包，正在屏上那一行自己就重绘 —— 不必整表 setState，也不用自己造版本号。
 * 进程内跨页面保留，第二次进这一页图标基本是现成的。
 * 上限 512：一台机器的装机量远小于此，超出丢最旧的那条。
 */
private val iconCache = mutableStateMapOf<String, ImageBitmap?>()

/** 取（或补）一个应用的图标。**只在 IO 线程上调**：loadIcon 要读 APK 里的资源。 */
private fun iconFor(pm: PackageManager, pkg: String): ImageBitmap? {
    if (iconCache.containsKey(pkg)) return iconCache[pkg]
    val bitmap = try {
        pm.getApplicationIcon(pkg)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
    } catch (e: Exception) {
        AppLog.w(TAG, "loadIcon failed for $pkg: ${e.message}")
        null
    }
    if (iconCache.size >= 512) {
        val it = iconCache.keys.iterator()
        if (it.hasNext()) it.remove()
    }
    iconCache[pkg] = bitmap
    return bitmap
}

/**
 * 进程内的装机清单缓存。
 *
 * 「打开慢」的大头是每次进页都从零跑一遍 `getInstalledPackages` + 两百次 `loadLabel`；
 * 有上次的结果就先上屏，再后台重扫一遍（新装的应用会自己换进来）。
 * 只在本进程存活期内有效，进程被杀就重扫 —— 不落盘，省掉「缓存过期谁来失效」这摊事。
 */
private var cachedApps: List<InstalledApp>? = null

/**
 * e5：装机清单放到**启动后台**先扫一遍。
 *
 * 进程一起来就填 [cachedApps]，等用户第一次点进「提取安装包」，第一帧就是列表，
 * 不必再干等 `getInstalledPackages` + 两百次 `loadLabel` 跑完（图标仍按原样后到，
 * 进页后照旧会重扫一遍，把新装的应用换进来）。已有缓存就直接返回，重复调用不重扫。
 */
fun warmInstalledApps(context: Context) {
    if (cachedApps != null) return
    val appCtx = context.applicationContext
    val list = loadInstalledApps(appCtx)
    cachedApps = list
    // f4「初始图标空白」：光预热清单还不够 —— 清单上屏时图标仍要进页后一张张补，
    // 头一两秒每行都是灰块。这里在**启动后台**把图标一并读进 [iconCache]，
    // 用户点进页面时第一帧就是带图的（loadIcon 要读 APK 资源，只能在后台线程跑）。
    warmIcons(appCtx, list)
}

/** 把清单里的图标全部读进 [iconCache]。**只在后台线程上调**（loadIcon 要读 APK 资源）。 */
private fun warmIcons(context: Context, list: List<InstalledApp>) {
    val pm = context.packageManager
    for (app in list) {
        if (!iconCache.containsKey(app.packageName)) iconFor(pm, app.packageName)
    }
}

/**
 * 把清单里还没缓存的图标一个个补上。**列表先上屏、图标后到**：
 * 一次全量 `loadIcon` 要读两百个 APK 的资源，正是「扫描极慢」的主要来源，
 * 而用户其实只要名字先出来就能开始找应用了。
 * （启动时 [warmInstalledApps] 已经预热过一轮，走到这里通常是补新装的那几个。）
 */
private suspend fun fillIcons(context: Context, list: List<InstalledApp>) {
    withContext(Dispatchers.IO) { warmIcons(context, list) }
}

/**
 * 装机清单。targetSdk 停在 28，不受 Android 11 包可见性过滤影响，不需要 `<queries>`。
 *
 * **只出文字，不出图标** —— 图标单独由 [fillIcons] 在列表上屏之后一张张补。
 * 这一刀是「扫描极慢」的根因所在：`loadLabel` + `loadIcon` 对两百个应用逐个
 * 读 APK 资源，全做完才肯上屏，用户就得对着转圈等一两秒。
 */
private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val infos = try {
        pm.getInstalledPackages(0)
    } catch (e: Exception) {
        AppLog.w(TAG, "getInstalledPackages failed: ${e.message}")
        emptyList()
    }

    val out = ArrayList<InstalledApp>(infos.size)
    for (pi in infos) {
        val ai = pi.applicationInfo ?: continue
        val pkg = pi.packageName ?: continue
        val sourceDir = ai.sourceDir ?: continue
        val label = try {
            ai.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
        } catch (e: Exception) {
            pkg
        }
        out.add(
            InstalledApp(
                label = label,
                packageName = pkg,
                versionName = pi.versionName.orEmpty(),
                sourceDir = sourceDir,
                splitDirs = ai.splitSourceDirs?.toList().orEmpty(),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                firstInstallTime = pi.firstInstallTime,
                lastUpdateTime = pi.lastUpdateTime,
                dataDir = ai.dataDir.orEmpty(),
                externalDataDir = File(
                    Environment.getExternalStorageDirectory(),
                    "Android/data/$pkg"
                ).absolutePath
            )
        )
    }
    out.sortBy { it.label.lowercase() }
    return out
}

private sealed interface ExtractOutcome {
    object Cancelled : ExtractOutcome
    data class Done(val dir: String) : ExtractOutcome
    object Failed : ExtractOutcome
}

/**
 * 复制一个文件并回报进度。
 *
 * 返回 `null` = 用户点了取消（半成品由调用方删掉）；`true` = 复制完成；
 * `false` = 读写失败（同样由调用方删掉半成品）。
 *
 * 取消判在**每一块 64KB 之间**：一块的耗时远小于人眼可感的延迟，取消按钮因此是
 * 「点了立刻停」，而不是「等这几百 MB 跑完」。
 */
private fun copyFile(
    src: File,
    dst: File,
    cancel: AtomicBoolean,
    onChunk: (Long) -> Unit
): Boolean? {
    // 先写 `name.part`，整份写完才替换目标。原来的写法直接 `dst.outputStream()`：
    // 同名目标在写入的一瞬间就被截断，随后若取消/磁盘满/读失败，**旧文件也没了** ——
    // 用户只是重提一次包，却把上一份好包弄坏。
    val tmp = File(dst.parentFile, dst.name + ".part")
    var moved = false
    try {
        src.inputStream().use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (cancel.get()) return null
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    onChunk(n.toLong())
                }
            }
        }
        // 保留「重名覆盖」的原语义：先删旧目标再改名。窗口只有一次 rename，
        // 且此时新内容已完整落盘。
        if (dst.exists() && !dst.delete()) throw java.io.IOException("cannot replace ${dst.name}")
        if (!tmp.renameTo(dst)) throw java.io.IOException("cannot rename ${tmp.name}")
        moved = true
        return true
    } catch (e: Exception) {
        AppLog.w(TAG, "copy failed: ${e.message}")
        return false
    } finally {
        // 取消 / 失败都只清临时件，目标（若有）保持原样。
        if (!moved) tmp.delete()
    }
}

/** 跑一次提取。**只在 IO 线程上调**；进度通过 [onProgress] 回传给 UI。 */
private fun runExtract(
    context: Context,
    job: ExtractJob,
    cancel: AtomicBoolean,
    onProgress: (UnifiedProgressState) -> Unit
): ExtractOutcome {
    val dir = File(job.dest.trim())
    if (!dir.isDirectory && !dir.mkdirs()) return ExtractOutcome.Failed

    // g5：部件模式不提 APK —— 两条路是单选，[targets] 为空时整个循环跳过，
    // 进度框走「总数未知」的不定长样式。
    val targets = job.explicit
        ?: if (job.parts.isEmpty()) job.apps.flatMap { it.targets(job.all) } else emptyList()
    val total = targets.sumOf { File(it.first).length().coerceAtLeast(0L) }
    // 页面已改叫「已安装应用」（f4），进度里的这行是**动作名**，仍说「提取安装包」
    val opLabel = Str.get(R.string.apk_extract_op)
    val runningTitle = Str.get(R.string.apk_extract_running)
    var done = 0L
    var lastTick = 0L
    var ok = 0

    for ((srcPath, name) in targets) {
        // 取消也可能落在两个文件之间（前一个刚复制完、后一个还没开），这里也要认一次
        if (cancel.get()) return ExtractOutcome.Cancelled
        val src = File(srcPath)
        val dst = File(dir, name)
        fun report() {
            onProgress(
                UnifiedProgressState(
                    opLabel = opLabel,
                    title = runningTitle,
                    currentName = name,
                    sourcePath = srcPath,
                    targetPath = dst.absolutePath,
                    totalBytes = total,
                    doneBytes = done,
                    running = true
                )
            )
        }
        report()
        if (!src.canRead()) {
            // 注意：**不删** dst —— 它可能是上一次成功提取的同名包，
            // 本次读不到源是本次的失败，不该连累已有文件。
            continue
        }
        val copied = copyFile(src, dst, cancel) { n ->
            done += n
            val now = SystemClock.uptimeMillis()
            if (now - lastTick >= 100) {
                lastTick = now
                report()
            }
        }
        if (copied == null) {
            return ExtractOutcome.Cancelled
        }
        if (copied) ok++
    }

    // g5：部件模式。每台应用产出一个 `<包名>-parts.zip`（用户明确要求「不能放一个
    // 文件夹」）—— 只打包用户勾中的那几类，zip 内按包名分目录、保留 lib/<abi>/ 结构。
    var partFiles = 0
    if (job.parts.isNotEmpty() && !cancel.get()) {
        for (app in job.apps) {
            if (cancel.get()) break
            onProgress(
                UnifiedProgressState(
                    opLabel = opLabel,
                    title = runningTitle,
                    currentName = "${app.packageName}-parts.zip",
                    sourcePath = app.sourceDir,
                    targetPath = File(job.dest.trim(), "${app.packageName}-parts.zip").absolutePath,
                    running = true
                )
            )
            partFiles += extractPartsZip(context, app, job.dest, job.parts)
        }
    }

    // 部件模式按「写出了几样部件」判成败；APK 模式仍按「复制成功几个包」判
    return when {
        job.parts.isNotEmpty() -> if (partFiles > 0) ExtractOutcome.Done(dir.absolutePath)
            else ExtractOutcome.Failed
        ok == targets.size -> ExtractOutcome.Done(dir.absolutePath)
        else -> ExtractOutcome.Failed
    }
}

/**
 * 能单独抠出来的**部件**（对应反馈里「补齐功能」的部件清单）。
 *
 * 除 [ICON] / [LAUNCH] 外都是 APK 这个 zip 里的**原始条目**，原样抽出来即可 ——
 * 不解析、不反编译，跟本页「只复制文件」的定位一致。
 */
private enum class Part { ICON, MANIFEST, DEX, ARSC, LIBS, LAUNCH }

/**
 * g5：只提取用户勾中的部件，打包成 `dest/<包名>-parts.zip`，返回写出的文件数。
 *
 * zip 内统一放 `<包名>/` 目录下，文件名带包名前缀；`lib/<abi>/` 的相对结构保留
 * （扁平化会让不同 ABI 的同名 so 互相覆盖）。图标/启动项不是 APK 里的原始条目，
 * 分别写成 PNG / 文本再进包。
 *
 * base + 全部分包都会扫：分包应用的 dex / 原生库常常只存在于 split APK 里，
 * 只看 base 会得到一个近乎空包。分包条目的文件名带 split 名，避免同名 dex 互相顶掉。
 *
 * **只在 IO 线程上调**（要读 APK 中央目录、写 zip）。
 */
private fun extractPartsZip(
    context: Context,
    app: InstalledApp,
    dest: String,
    parts: Set<Part>
): Int {
    if (parts.isEmpty()) return 0
    val outFile = File(dest.trim(), "${app.packageName}-parts.zip")
    outFile.parentFile?.mkdirs()
    var n = 0
    try {
        java.util.zip.ZipOutputStream(
            java.io.BufferedOutputStream(outFile.outputStream())
        ).use { zos ->
            fun put(name: String, bytes: ByteArray) {
                val entry = java.util.zip.ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
                n++
            }

            if (Part.ICON in parts) {
                val drawable = context.packageManager.getApplicationIcon(app.packageName)
                if (drawable != null) {
                    val bitmap = drawable.toBitmap(ICON_PX * 2, ICON_PX * 2)
                    val baos = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    put("${app.packageName}/${app.packageName}.png", baos.toByteArray())
                }
            }
            if (Part.LAUNCH in parts) {
                val cls = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.component?.className
                if (!cls.isNullOrEmpty()) {
                    put(
                        "${app.packageName}/${app.packageName}-launch.txt",
                        "$cls\n".toByteArray()
                    )
                }
            }

            val zipParts = parts - Part.ICON - Part.LAUNCH
            if (zipParts.isNotEmpty()) {
                // 分包应用的 dex / 原生库经常只在 split APK 里（base 里是空的）。
                // 所以要把 base + 全部分包都扫一遍，否则「提取部件」对这些应用
                // 只会得到一个近乎空包。同名条目（清单之类）只留第一份。
                val written = HashSet<String>()
                for (apk in listOf(app.sourceDir) + app.splitDirs) {
                    if (!File(apk).isFile) continue
                    // 每个分包里的 classes.dex 都同名，文件名必须带来源标记，
                    // 否则后扫到的分包条目会被 `written` 当成重复丢掉。
                    val tag = if (apk == app.sourceDir) {
                        app.packageName
                    } else {
                        "${app.packageName}-${File(apk).name.removeSuffix(".apk")}"
                    }
                    ZipFile(apk).use { zip ->
                        val dex = Regex("classes\\d*\\.dex")
                        val all = zip.entries()
                        while (all.hasMoreElements()) {
                            val entry = all.nextElement()
                            if (entry.isDirectory) continue
                            val name = entry.name
                            val hit = (Part.MANIFEST in zipParts && name == "AndroidManifest.xml") ||
                                (Part.DEX in zipParts && dex.matches(name)) ||
                                (Part.ARSC in zipParts && name == "resources.arsc") ||
                                (Part.LIBS in zipParts &&
                                    name.startsWith("lib/") && name.endsWith(".so"))
                            if (!hit) continue
                            val rel = if (name.contains('/')) {
                                "${name.substringBeforeLast('/')}/$tag-${name.substringAfterLast('/')}"
                            } else {
                                "$tag-$name"
                            }
                            val entryName = "${app.packageName}/$rel"
                            if (!written.add(entryName)) continue
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            put(entryName, bytes)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        AppLog.w(TAG, "extractPartsZip failed for ${app.packageName}: ${e.message}")
        // 半个 zip 比没有更糟：失败直接删掉
        outFile.delete()
        return 0
    }
    return n
}

/**
 * 把 APK 拷进分享中转目录，返回待分享的文件。
 *
 * **必须先拷**：FileProvider 只签 `/storage/` 下的路径（见 `file_paths.xml`），
 * 而源文件在 `/data/app`，直接签必然失败。
 *
 * 一律带分包：只发 base 对分包应用来说是个装不起来的包，收件方只会以为文件坏了。
 * **只在 IO 线程上调**。
 */
/**
 * 把一个应用的 base + 全部分包**一次装上**（反馈 e1「多 split 不能一起装」）。
 *
 * 单个 `ACTION_VIEW` 只吃得下一个文件 —— 分包必须走 PackageInstaller 的会话：
 * 把几个 APK 依次写进**同一个** session 再提交，这正是 `adb install-multiple` 的做法。
 *
 * 不落盘、不拷贝：`/data/app` 下的 APK 全局可读，直接从源路径流进会话，
 * 装完也没有中转文件要清理。
 *
 * 不挂完成回执：系统自己的安装界面已经把成败说清楚了，
 * 再挂一个 receiver 只是把同一句话重复一遍。（`commit` 的回执参数标了非空，
 * 那就塞一个「发出去也没人接」的空 PendingIntent，与不挂等价。）
 */
private fun installSplits(context: Context, app: InstalledApp) {
    val files = (listOf(app.sourceDir) + app.splitDirs).map { File(it) }.filter { it.isFile }
    if (files.isEmpty()) {
        AppToast.warning(context, Str.get(R.string.apk_extract_install_failed))
        return
    }
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    // 先报上包名：系统能立刻把应用名和图标摆出来，也不用等读完第一个 APK 才知道装的是谁
    params.setAppPackageName(app.packageName)
    val sessionId = installer.createSession(params)
    try {
        installer.openSession(sessionId).use { session ->
            for (f in files) {
                val input = context.contentResolver.openInputStream(Uri.fromFile(f))
                    ?: throw IllegalStateException("cannot read ${f.absolutePath}")
                input.use {
                    session.openWrite(f.name, 0, f.length()).use { out ->
                        it.copyTo(out)
                        session.fsync(out)
                    }
                }
            }
            // 回执参数是非空的，但我们不打算收：塞一个指向本类、本应用里没有
            // receiver 的空 PendingIntent —— 系统发状态时没人接，与不挂等价。
            val noStatus = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, ApkExtractActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ).intentSender
            session.commit(noStatus)
        }
        AppToast.success(context, Str.get(R.string.apk_extract_install_submit))
    } catch (e: Exception) {
        AppLog.w(TAG, "install splits failed: ${e.message}")
        // 会话已经开了就得收掉：留着一个半截 session，下次进包安装器会看到一条幽灵任务
        runCatching { installer.abandonSession(sessionId) }
        AppToast.warning(context, Str.get(R.string.apk_extract_install_failed))
    }
}

/**
 * 跳文件页并定位到 [dirPath]（反馈 e1「完成无定位输出目录」「数据目录」入口）。
 *
 * 走对外那条 `uin://files?path=` 深链 —— 与插件宿主同一条路（§4.16），
 * 少一处耦合，也让这条深链在日常使用里始终被真实走到。
 */
private fun jumpToDir(context: Context, dirPath: String) {
    val uri = Uri.Builder()
        .scheme("uin")
        .authority("files")
        .appendQueryParameter("path", dirPath)
        .appendQueryParameter("file", "")
        .build()
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        AppLog.w(TAG, "locate unavailable: ${e.message}")
        AppToast.warning(context, Str.get(R.string.apk_extract_locate_failed))
    }
}

/** 启动应用（反馈 e1「面板缺启动应用」）。没有启动入口的（纯服务 / 小组件）给句实话，不静默。 */
private fun launchApp(context: Context, app: InstalledApp) {
    val intent = try {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
    } catch (e: Exception) {
        null
    }
    if (intent == null) {
        AppToast.warning(context, Str.get(R.string.apk_extract_no_launch))
        return
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppLog.w(TAG, "launch failed: ${e.message}")
        AppToast.warning(context, Str.get(R.string.apk_extract_launch_failed))
    }
}

/**
 * 数据目录（反馈 e1「面板缺数据目录」）。够得着的只有**外部**那份（`Android/data/<包名>`）；
 * 内部的 `/data/data` 没 root 进不去 —— 直接说，别装作能开。
 */
private fun openDataDir(context: Context, app: InstalledApp) {
    val external = File(
        Environment.getExternalStorageDirectory(), "Android/data/${app.packageName}"
    )
    if (external.isDirectory) {
        jumpToDir(context, external.absolutePath)
    } else {
        AppToast.warning(context, Str.get(R.string.apk_extract_no_external_data))
    }
}

/** 系统的「应用信息」页（反馈 e1「面板缺应用信息」）。 */
private fun openAppInfo(context: Context, app: InstalledApp) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${app.packageName}")
    )
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLog.w(TAG, "app details failed: ${e.message}")
        AppToast.warning(context, Str.get(R.string.apk_extract_app_info_failed))
    }
}

/**
 * 卸载（反馈 e1「提权管理动作」里唯一不需要 root 的一件）。
 *
 * 走系统的 `ACTION_DELETE`：删应用这件事必须由系统再问一遍，
 * 我们只负责把人送到那个确认框前，不替他点头。
 * 清数据 / 强停都要 root，本机没有 —— 不摆点了必然失败的键。
 */
private fun uninstallApp(context: Context, app: InstalledApp) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLog.w(TAG, "uninstall failed: ${e.message}")
        AppToast.warning(context, Str.get(R.string.apk_extract_uninstall_failed))
    }
}

/**
 * 分享前把 APK 拷进中转目录（反馈 e1「批量无分享选中」）。
 *
 * 一个目录装下全部选中项：文件名本来就带包名前缀（见 [InstalledApp.targets]），
 * 几个应用倒在一起也不会互相覆盖。
 */
private fun stageForShare(apps: List<InstalledApp>): List<File>? {
    val dir = File(
        Environment.getExternalStorageDirectory(),
        "UIN_Tool/temp/apkshare"
    )
    if (dir.exists()) dir.deleteRecursively()
    if (!dir.mkdirs()) return null
    val out = ArrayList<File>()
    val noCancel = AtomicBoolean(false)
    for (app in apps) for ((srcPath, name) in app.targets(all = true)) {
        val dst = File(dir, name)
        val ok = copyFile(File(srcPath), dst, noCancel) { }
        if (ok == true) out.add(dst) else dst.delete()
    }
    return out.ifEmpty { null }
}

// ==================== 界面 ====================

@OptIn(ExperimentalMaterial3Api::class) // 下拉刷新：PullToRefreshBox 还挂着实验标记
@Composable
private fun ApkExtractRoot(onBack: () -> Unit) {
    val context = LocalContext.current
    val defaultDest = remember {
        File(Environment.getExternalStorageDirectory(), "UIN_Tool/apk").absolutePath
    }

    var apps by remember { mutableStateOf<List<InstalledApp>?>(cachedApps) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var dest by rememberSaveable {
        // i.txt 第 3 条：提取路径记住上次的选择；没设置过才落到默认目录。
        mutableStateOf(FilePrefs.apkExtractDest().ifBlank { defaultDest })
    }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var job by remember { mutableStateOf<ExtractJob?>(null) }
    var progress by remember { mutableStateOf<UnifiedProgressState?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    /** 多选模式与已勾选的包名（用包名而不是下标：换 tab / 搜索后下标会漂）。 */
    var picking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    /** 批量提取的目标目录弹窗（单个应用的弹窗是 [selected]）。 */
    var batchDestOpen by remember { mutableStateOf(false) }
    /** 反馈 e1「列表只按名称排」：0=名称 1=安装时间 2=更新时间（时间一律新的在前）。 */
    // h.txt 第 3 条：已安装应用默认按「更新时间」排序（最新在前）
    // i.txt 第 3 条：改成**永久记住** —— 上次选了哪个，下次进来还是哪个。
    var sortMode by rememberSaveable { mutableStateOf(FilePrefs.apkSortMode()) }
    /** i.txt 第 3 条：右上角三点里的设置框（提取路径 + 两套命名表达式）。 */
    var settingsOpen by remember { mutableStateOf(false) }
    /** 命名表达式的帮助页。 */
    var namingHelpOpen by remember { mutableStateOf(false) }
    /** 提取完成的落点 + 说给用户听的那句 —— 反馈 e1「完成无定位输出目录」。 */
    var doneDir by remember { mutableStateOf<String?>(null) }
    var doneText by remember { mutableStateOf("") }
    /** 批量分享进行中（反馈 e1「批量无分享选中」）。 */
    var sharing by remember { mutableStateOf(false) }
    val cancel = remember { AtomicBoolean(false) }
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val appCtx = context.applicationContext
    // f4「排序后跳回最前」：列表原先没持滚动状态，换排序后停在原行号上 ——
    // 排序一变内容整个重排，停在原位看到的是不相干的一段，直接拉回第一行。
    val listState = rememberLazyListState()
    LaunchedEffect(sortMode) { listState.animateScrollToItem(0) }

    /**
     * 扫一遍装机清单并上屏。[showIndicator] 只管下拉指示器 ——
     * 首次进入没有缓存时由全屏转圈兜底，那时下拉也无从拉起。
     */
    suspend fun loadList(showIndicator: Boolean) {
        if (showIndicator) refreshing = true
        val fresh = withContext(Dispatchers.IO) { loadInstalledApps(appCtx) }
        cachedApps = fresh
        apps = fresh
        if (showIndicator) refreshing = false
        // 列表已经在屏上了，图标慢慢亮 —— 不能让用户替两百次 loadIcon 买单
        fillIcons(appCtx, fresh)
    }

    LaunchedEffect(Unit) {
        val cached = cachedApps
        if (cached != null) {
            // 第二次进页：上次的结果第一帧就上屏，缺的图标并行补齐，
            // 与此同时后台照样重扫一遍，新装的应用会自己换进来
            apps = cached
            fillIcons(appCtx, cached)
        }
        loadList(showIndicator = cached != null)
    }

    // 批量分享（反馈 e1「批量无分享选中」）：先整包拷进中转目录，再走系统分享面板
    LaunchedEffect(sharing) {
        if (!sharing) return@LaunchedEffect
        val picked = apps?.filter { it.packageName in checked }.orEmpty()
        val files = withContext(Dispatchers.IO) { stageForShare(picked) }
        sharing = false
        if (files == null) {
            AppToast.warning(context, Str.get(R.string.apk_extract_share_failed))
        } else {
            context.shareFiles(files)
        }
    }

    // 提取：整段在 IO 上跑，进度按字节回写。回写限 100ms 一次 ——
    // 复制的粒度是 64KB，不节流的话一个 500MB 的包就是八千次重组。
    LaunchedEffect(job) {
        val j = job ?: return@LaunchedEffect
        val count = j.apps.size
        cancel.set(false)
        val outcome = withContext(Dispatchers.IO) { runExtract(context, j, cancel) { progress = it } }
        progress = null
        job = null
        // 批量跑完把勾选清掉：留着的话下一次进多选态会莫名其妙带着上一轮的选中
        if (count > 1) {
            picking = false
            checked = emptySet()
        }
        when (outcome) {
            is ExtractOutcome.Cancelled ->
                AppToast.warning(context, Str.get(R.string.apk_extract_cancelled))
            is ExtractOutcome.Done -> {
                // 反馈 e1「完成无定位输出目录」：只给一句 toast，用户还得自己回文件页
                // 一层层找过去 —— 路径就摆在眼前，给一个「打开目录」才叫完成
                doneText = if (count > 1) {
                    Str.get(R.string.apk_extract_done_n, count, outcome.dir)
                } else {
                    Str.get(R.string.apk_extract_done, outcome.dir)
                }
                doneDir = outcome.dir
            }
            ExtractOutcome.Failed ->
                AppToast.warning(context, Str.get(R.string.apk_extract_failed))
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = Str.get(R.string.apk_extract_title),
                onBack = onBack,
                // 排序放右上角（反馈 e1 + e.txt 第11条「功能菜单放右上角的三个点」）。
                // 塞进下面那行胶囊是错的：英文一长就把「多选」顶出屏，
                // 而那行还得先给两个 Tab 让位
                actions = {
                    var sortMenu by remember { mutableStateOf(false) }
                    UnifiedIconButton(
                        icon = Icons.Default.Sort,
                        onClick = { sortMenu = true },
                        contentDescription = Str.get(R.string.apk_extract_sort),
                    )
                    UnifiedDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        val modes = intArrayOf(
                            R.string.apk_extract_sort_name,
                            R.string.apk_extract_sort_install,
                            R.string.apk_extract_sort_update
                        )
                        modes.forEachIndexed { i, res ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Str.get(res),
                                        fontWeight = if (sortMode == i) {
                                            FontWeight.Medium
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                },
                                // 选中打勾；没选中也留这个槽，几行才对得齐
                                leadingIcon = {
                                    if (sortMode == i) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null
                                        )
                                    }
                                },
                                onClick = {
                                    sortMode = i
                                    // i.txt 第 3 条：点了就永久记住，下次进来还是这个排序。
                                    FilePrefs.setApkSortMode(i)
                                    sortMenu = false
                                }
                            )
                        }
                    }
                    // i.txt 第 3 条：右上角三点 —— 提取路径与命名方式（表达式命名）。
                    var moreMenu by remember { mutableStateOf(false) }
                    UnifiedIconButton(
                        icon = Icons.Default.MoreVert,
                        onClick = { moreMenu = true },
                        contentDescription = Str.get(R.string.more),
                    )
                    UnifiedDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(Str.get(R.string.apk_extract_settings)) },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            },
                            onClick = {
                                moreMenu = false
                                settingsOpen = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val list = apps
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (list == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            UnifiedTextField(
                value = query,
                onValueChange = { query = it },
                label = Str.get(R.string.apk_extract_search),
                singleLine = true,
                showClearButton = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val wantSystem = tab == 1
            val q = query.trim()
            val filtered = list.filter { it.isSystem == wantSystem }.filter {
                q.isEmpty() || it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
            // 反馈 e1「列表只按名称排」：时间一律**新的在前** —— 找「上周装的那个」时
            // 最新的排头才对；同一时刻再按名称兜底，免得顺序抖
            val shown = when (sortMode) {
                1 -> filtered.sortedWith(
                    compareByDescending<InstalledApp> { it.firstInstallTime }
                        .thenBy { it.label.lowercase() }
                )
                2 -> filtered.sortedWith(
                    compareByDescending<InstalledApp> { it.lastUpdateTime }
                        .thenBy { it.label.lowercase() }
                )
                else -> filtered
            }

            // 分页 + 多选开关。全选要用到上面算好的 shown，所以筛选排在这一行之前。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabPill(
                    text = Str.get(R.string.apk_extract_tab_user),
                    selected = tab == 0,
                    onClick = {
                        tab = 0
                        // 换页就清勾选：留着会让「提取选中」提一堆当前这页看不见的应用
                        checked = emptySet()
                    }
                )
                TabPill(
                    text = Str.get(R.string.apk_extract_tab_system),
                    selected = tab == 1,
                    onClick = {
                        tab = 1
                        checked = emptySet()
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                if (picking) {
                    TabPill(
                        text = Str.get(R.string.apk_extract_select_all),
                        selected = false,
                        onClick = { checked = shown.map { it.packageName }.toSet() }
                    )
                    TabPill(
                        text = Str.get(R.string.cancel),
                        selected = true,
                        onClick = {
                            picking = false
                            checked = emptySet()
                        }
                    )
                } else {
                    TabPill(
                        text = Str.get(R.string.apk_extract_multi),
                        selected = false,
                        onClick = { picking = true }
                    )
                }
            }

            // 下拉刷新：装机清单会随装卸应用变化，给一个不进菜单的刷新入口。
            // 指示器只罩住列表这一块，不盖到上面的搜索框。
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { loadList(showIndicator = true) } },
                state = pullState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                indicator = {
                    PullRefreshIndicator(
                        isRefreshing = refreshing,
                        state = pullState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                if (shown.isEmpty()) {
                    Text(
                        text = Str.get(R.string.apk_extract_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.textHint(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(shown, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                showCheck = picking,
                                checked = app.packageName in checked,
                                onClick = {
                                    if (picking) {
                                        checked = if (app.packageName in checked) {
                                            checked - app.packageName
                                        } else {
                                            checked + app.packageName
                                        }
                                    } else {
                                        selected = app
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 批量入口：只在多选且有勾选时出现，平时不占一格地方。
            // 分享与提取并排（反馈 e1「批量无分享选中」）—— 两个都是「对这批选中项
            // 做点什么」，分成两行会让第二个长得像补充说明
            if (picking && checked.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnifiedButton(
                        text = Str.get(R.string.apk_extract_extract_selected, checked.size),
                        onClick = { batchDestOpen = true },
                        variant = ButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    UnifiedButton(
                        text = Str.get(R.string.apk_extract_share_selected, checked.size),
                        onClick = {
                            if (checked.size > MAX_SHARE_APPS) {
                                AppToast.warning(
                                    context,
                                    Str.get(R.string.apk_extract_share_too_many, MAX_SHARE_APPS)
                                )
                            } else {
                                sharing = true
                            }
                        },
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // ---- 提取目标弹窗 ----
    val app = selected
    if (app != null) {
        // f4「部件合并成一个复选框」：原先六颗胶囊各抠一样（图标/清单/dex/资源表/
        // 原生库/启动项），要凑齐得点六下、弹窗还不能关 —— 收成一个勾选，跟着这次
        // 提取一次抠齐。放在 if(app != null) 里：关掉弹窗即离场，下次打开回到未勾选。
        // g5：两条路改成**单选**（APK / 部件），部件展开后是多选复选框。
        // 默认提 APK；部件默认全勾上，用户取消哪类就少打包哪类。
        var mode by remember { mutableStateOf(ExtractMode.APK) }
        var selectedParts by remember { mutableStateOf(Part.entries.toSet()) }
        UnifiedDialog(
            onDismissRequest = { if (progress == null) selected = null },
            title = app.label,
            content = {
                Text(
                    text = app.caption(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textHint(),
                    modifier = Modifier.fillMaxWidth()
                )
                // ---- 管理入口（反馈 e1「面板缺启动应用 / 数据目录 / 应用信息」）----
                // 三个都是系统本来就有的能力，不需要 root。数据目录只够得着**外部**那份，
                // 内部的 /data/data 没 root 进不去 —— 那句实话在 [openDataDir] 里说
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TabPill(Str.get(R.string.apk_extract_launch), false) { launchApp(context, app) }
                    TabPill(Str.get(R.string.apk_extract_data_dir), false) {
                        openDataDir(context, app)
                    }
                    TabPill(Str.get(R.string.apk_extract_app_info), false) {
                        openAppInfo(context, app)
                    }
                    // f4「安装包信息改弹窗」：不再跳去那张整页 —— 本页要看的就是
                    // 包名 / 版本 / 大小 / 路径这几行，弹窗里一次给全，点或长按任一行
                    // 即可复制（复用文件属性框那套 InfoRow，长按复制的口径一致）
                    TabPill(Str.get(R.string.files_open_apk_info), false) {
                        selected = null
                        // i.txt 第 3 条：与 .apk 文件**共用同一个**信息弹窗。
                        // 已装应用的安装包就在 sourceDir（/data/app 下全局可读），
                        // 直接拿它当 APK 路径喂给 ApkInfoActivity 即可。
                        ApkInfoActivity.start(context, app.sourceDir)
                    }
                    // 提权管理三件里只有它不需要 root（反馈 e1）；清数据 / 强停本机没 root，
                    // 不摆点了必然失败的键
                    TabPill(Str.get(R.string.apk_extract_uninstall), false) {
                        selected = null
                        uninstallApp(context, app)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                UnifiedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    label = Str.get(R.string.apk_extract_dest),
                    singleLine = true,
                    // o.txt 第 13 条：点已安装应用弹出的这个框不要自动弹键盘 ——
                    // 用户多半是来看「启动 / 应用信息 / 提取」的，键盘会直接盖住半屏内容。
                    autoFocus = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                RadioModeRow(
                    selected = mode == ExtractMode.APK,
                    label = Str.get(R.string.apk_extract_mode_apk)
                ) { mode = ExtractMode.APK }
                if (mode == ExtractMode.APK) {
                UnifiedButton(
                    text = Str.get(R.string.apk_extract_base),
                    onClick = {
                        job = ExtractJob(listOf(app), all = false, dest = dest)
                        selected = null
                    },
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
                // 分包应用才有「全部」这一档：单包应用点它与点上面那档是同一件事
                if (app.splitDirs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedButton(
                        text = Str.get(R.string.apk_extract_all),
                        onClick = {
                            job = ExtractJob(listOf(app), all = true, dest = dest)
                            selected = null
                        },
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 反馈 e1「分包不能单选」：分包常常只想单独拿一个
                    // （比如只要 arm64 那份去比对），摆成一排胶囊，点了就提这一个
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = Str.get(R.string.apk_extract_splits),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.textHint(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        app.splitDirs.forEach { split ->
                            TabPill(
                                text = File(split).name,
                                selected = false,
                                onClick = {
                                    job = ExtractJob(
                                        apps = listOf(app),
                                        all = false,
                                        dest = dest,
                                        explicit = listOf(app.splitTarget(split))
                                    )
                                    selected = null
                                }
                            )
                        }
                    }
                    // 反馈 e1「多 split 不能一起装」：分包只能**一次全装** ——
                    // 单个 ACTION_VIEW 只吃得下一个文件，几个分包得进同一个安装会话
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedButton(
                        text = Str.get(
                            R.string.apk_extract_install_splits,
                            app.splitDirs.size + 1
                        ),
                        onClick = {
                            selected = null
                            installSplits(context, app)
                        },
                        variant = ButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                } // mode == APK

                // g5：中间一条分隔线把两种提取方式分开；选「提取部件」时弹窗向下
                // 拉伸，露出六类部件的复选框（默认全选），再给一颗独立的提取键。
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                RadioModeRow(
                    selected = mode == ExtractMode.PARTS,
                    label = Str.get(R.string.apk_extract_mode_parts)
                ) { mode = ExtractMode.PARTS }
                if (mode == ExtractMode.PARTS) {
                    Text(
                        text = Str.get(R.string.apk_extract_parts_zip_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.textHint(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Part.entries.forEach { part ->
                        DialogCheckRow(
                            label = partLabel(part),
                            checked = part in selectedParts
                        ) { on ->
                            selectedParts = if (on) selectedParts + part
                            else selectedParts - part
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedButton(
                        text = Str.get(R.string.apk_extract_parts_do),
                        onClick = {
                            job = ExtractJob(
                                apps = listOf(app),
                                all = false,
                                dest = dest,
                                parts = selectedParts
                            )
                            selected = null
                        },
                        enabled = selectedParts.isNotEmpty(),
                        variant = ButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---- 分享：先拷进中转目录再发（FileProvider 签不了 /data/app）----
                Spacer(modifier = Modifier.height(12.dp))
                UnifiedButton(
                    text = Str.get(R.string.apk_extract_share),
                    onClick = {
                        val target = app
                        selected = null
                        scope.launch {
                            val files = withContext(Dispatchers.IO) { stageForShare(listOf(target)) }
                            if (files == null) {
                                AppToast.warning(context, Str.get(R.string.apk_extract_failed))
                            } else {
                                context.shareFiles(files)
                            }
                        }
                    },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // ---- 安装包信息：i.txt 第 3 条起与 .apk 文件共用 ApkInfoActivity，
    // 原先那张只列几行的弹窗已删（点「安装包信息」直接开同一张弹窗）----

    // ---- 设置框（i.txt 第 3 条）：提取路径 + 两套命名表达式 ----
    if (settingsOpen) {
        var destEdit by remember { mutableStateOf(dest) }
        var baseEdit by remember { mutableStateOf(FilePrefs.apkNameTemplate()) }
        var partEdit by remember { mutableStateOf(FilePrefs.apkPartTemplate()) }
        UnifiedDialog(
            onDismissRequest = { settingsOpen = false },
            title = Str.get(R.string.apk_extract_settings),
            content = {
                UnifiedTextField(
                    value = destEdit,
                    onValueChange = { destEdit = it },
                    label = Str.get(R.string.apk_extract_dest),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                // 命名表达式：主包一份、部件（分包）一份。帮助图标就摆在标题行，
                // 有疑问当场能查，不用去翻文档。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Str.get(R.string.apk_extract_naming_title),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.Info,
                        onClick = { namingHelpOpen = true },
                        contentDescription = Str.get(R.string.apk_extract_naming_help),
                        iconSize = 18.dp,
                    )
                }
                UnifiedTextField(
                    value = baseEdit,
                    onValueChange = { baseEdit = it },
                    label = Str.get(R.string.apk_extract_naming_base),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                UnifiedTextField(
                    value = partEdit,
                    onValueChange = { partEdit = it },
                    label = Str.get(R.string.apk_extract_naming_part),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.save),
                    onClick = {
                        dest = destEdit.trim()
                        FilePrefs.setApkExtractDest(dest)
                        FilePrefs.setApkNameTemplate(baseEdit)
                        FilePrefs.setApkPartTemplate(partEdit)
                        settingsOpen = false
                    }
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { settingsOpen = false },
                    variant = ButtonVariant.Text
                )
            }
        )
    }

    if (namingHelpOpen) {
        UnifiedDialog(
            onDismissRequest = { namingHelpOpen = false },
            title = Str.get(R.string.apk_extract_naming_help),
            content = {
                Text(
                    text = Str.get(R.string.apk_extract_naming_help_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.ok),
                    onClick = { namingHelpOpen = false }
                )
            }
        )
    }

    // ---- 批量提取目标弹窗 ----
    // 与单个应用那张的区别只有标题与按钮顺序：批量多半是为了**备份**，
    // 所以「含分包」排在前面当主按钮 —— 只提 base 的备份拿到手是装不起来的。
    if (batchDestOpen) {
        val picked = apps?.filter { it.packageName in checked }.orEmpty()
        var batchMode by remember { mutableStateOf(ExtractMode.APK) }
        var batchParts by remember { mutableStateOf(Part.entries.toSet()) }
        UnifiedDialog(
            onDismissRequest = { if (progress == null) batchDestOpen = false },
            title = Str.get(R.string.apk_extract_extract_selected, picked.size),
            content = {
                UnifiedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    label = Str.get(R.string.apk_extract_dest),
                    singleLine = true,
                    // o.txt 第 13 条：点已安装应用弹出的这个框不要自动弹键盘 ——
                    // 用户多半是来看「启动 / 应用信息 / 提取」的，键盘会直接盖住半屏内容。
                    autoFocus = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                // g5：批量弹窗与单个弹窗保持同一套单选结构
                RadioModeRow(
                    selected = batchMode == ExtractMode.APK,
                    label = Str.get(R.string.apk_extract_mode_apk)
                ) { batchMode = ExtractMode.APK }
                if (batchMode == ExtractMode.APK) {
                    UnifiedButton(
                        text = Str.get(R.string.apk_extract_all),
                        onClick = {
                            if (picked.isEmpty()) return@UnifiedButton
                            job = ExtractJob(picked, all = true, dest = dest)
                            batchDestOpen = false
                        },
                        variant = ButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedButton(
                        text = Str.get(R.string.apk_extract_base),
                        onClick = {
                            if (picked.isEmpty()) return@UnifiedButton
                            job = ExtractJob(picked, all = false, dest = dest)
                            batchDestOpen = false
                        },
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                RadioModeRow(
                    selected = batchMode == ExtractMode.PARTS,
                    label = Str.get(R.string.apk_extract_mode_parts)
                ) { batchMode = ExtractMode.PARTS }
                if (batchMode == ExtractMode.PARTS) {
                    Text(
                        text = Str.get(R.string.apk_extract_parts_zip_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.textHint(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Part.entries.forEach { part ->
                        DialogCheckRow(
                            label = partLabel(part),
                            checked = part in batchParts
                        ) { on ->
                            batchParts = if (on) batchParts + part else batchParts - part
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    UnifiedButton(
                        text = Str.get(R.string.apk_extract_parts_do),
                        onClick = {
                            if (picked.isEmpty()) return@UnifiedButton
                            job = ExtractJob(
                                apps = picked,
                                all = false,
                                dest = dest,
                                parts = batchParts
                            )
                            batchDestOpen = false
                        },
                        enabled = batchParts.isNotEmpty(),
                        variant = ButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.cancel),
                    onClick = { batchDestOpen = false },
                    variant = ButtonVariant.Text
                )
            }
        )
    }

    // ---- 完成：给一条「定位输出目录」的路（反馈 e1） ----
    val done = doneDir
    if (done != null) {
        UnifiedAlertDialog(
            onDismissRequest = { doneDir = null },
            title = { Text(Str.get(R.string.apk_extract_done_title)) },
            text = {
                Text(
                    text = doneText,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textHint()
                )
            },
            confirmButton = {
                UnifiedButton(
                    text = Str.get(R.string.apk_extract_locate),
                    onClick = {
                        doneDir = null
                        jumpToDir(context, done)
                    },
                    variant = ButtonVariant.Primary
                )
            },
            dismissButton = {
                UnifiedButton(
                    text = Str.get(R.string.got_it),
                    onClick = { doneDir = null },
                    variant = ButtonVariant.Text
                )
            }
        )
    }

    // ---- 进度 ----
    val state = progress
    if (state != null) {
        UnifiedProgressDialog(
            state = state,
            onCancel = { cancel.set(true) },
            // 「隐藏」也按取消处理：本页没有任务中心，藏起来之后既看不到进度也停不下来，
            // 那不是隐藏，是把任务弄丢了
            onDismissRequest = { cancel.set(true) }
        )
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else AppColors.textPrimary()
    val shape = RoundedCornerShape(50)
    // v.txt 第 8 条（用户）：文件页这类「用户应用 / 系统应用」标签也要跟随
    // UI 个性化里的标签风格 —— 新拟态下未选中项同样浮起，选中项保持描边。
    val neu = UIConfig.isNeumorphismEnabled()
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = color,
        modifier = Modifier
            .then(
                if (neu && !selected) {
                    Modifier.neuRaised(
                        shape,
                        UIConfig.shouldUseDarkTheme(),
                        NeuDefaults.Intensity.LIGHT,
                        cornerRadius = 50.dp,
                        backgroundColor = Color.Transparent,
                        offset = 3f
                    )
                } else Modifier
            )
            .clip(shape)
            // j.txt 第 3 条：选中态不再铺整块主色，改用「文字 + 同色描边」。
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else AppColors.outlineVariant().copy(alpha = 0.5f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun AppRow(
    app: InstalledApp,
    showCheck: Boolean,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 多选态才出勾选框，且放在最左：一眼能看出「这一行点下去是勾选，不是打开」
        if (showCheck) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary
                        else AppColors.outlineVariant().copy(alpha = 0.45f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (checked) MaterialTheme.colorScheme.primary
                        else AppColors.outlineVariant(),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        // 图标是**后到**的：清单先上屏，[fillIcons] 一张张补进 [iconCache]，
        // 补到这一行时 SnapshotStateMap 会自己叫它重绘，占位色块随之换成真图标。
        val icon = iconCache[app.packageName]
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            // 图标读不出来就给个色块：应用本身能提，不能因为一张图把它从列表里抹掉
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.outlineVariant().copy(alpha = 0.4f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppColors.textPrimary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.caption(),
                fontSize = 11.sp,
                color = AppColors.textHint(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== Activity ====================

/**
 * 提取安装包（用户反馈 b26 / c18）。
 *
 * 能力边界：把**已装应用**的 APK 文件复制到用户指定的目录，主包与分包都支持。
 * 不解析清单、不反编译、不碰 `/data/data`、不需要 root —— 源文件本来就是全局可读的，
 * 这也是所有免 root 提取器的共同前提。提取完给一条带路径的提示，后续归用户自己处置。
 */
class ApkExtractActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UINToolTheme {
                ApkExtractRoot(onBack = { finish() })
            }
        }
    }
}


// ==================== g5：提取方式单选 / 部件勾选 ====================

/** 提取方式：提安装包（base / 含分包 / 单个分包）或提部件（打包成 zip）。 */
private enum class ExtractMode { APK, PARTS }

/** 部件的中文展示名。图标 / 启动项不是 APK 原始条目，其余直接对应包内文件。 */
private fun partLabel(part: Part): String = Str.get(
    when (part) {
        Part.ICON -> R.string.apk_extract_part_icon
        Part.MANIFEST -> R.string.apk_extract_part_manifest
        Part.DEX -> R.string.apk_extract_part_dex
        Part.ARSC -> R.string.apk_extract_part_arsc
        Part.LIBS -> R.string.apk_extract_part_libs
        Part.LAUNCH -> R.string.apk_extract_part_launch
    }
)

/** 单选项一行：圆点 + 文案，整行可点（与弹窗里的复选框行同一套交互）。 */
@Composable
private fun RadioModeRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onSelect() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
