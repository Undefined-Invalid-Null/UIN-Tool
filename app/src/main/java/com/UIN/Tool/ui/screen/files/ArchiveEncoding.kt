// app/src/main/java/com/UIN/Tool/ui/screen/files/ArchiveEncoding.kt
package com.UIN.Tool.ui.screen.files

import java.io.File
import java.nio.charset.Charset
import org.apache.commons.compress.archivers.zip.ZipFile as CommonsZipFile

/**
 * zip 条目名的编码判定（§4.7-C4 / M4d）。
 *
 * ## 为什么必须是「一个」helper
 *
 * zip 的条目名是**字节串**，编码由打包工具决定：Windows 上的压缩工具用系统代码页
 * （中文环境就是 GBK/Big5），命令行与现代工具用 UTF-8。读错的表现是**乱码文件名**，
 * 而不是报错 —— 用户不会知道是编码错了，只会以为包坏了。
 *
 * 但更要命的是「只修一半」：如果列目录用 UTF-8、提取用 GBK，同一个包会
 * **列得出来、取不出来**（列出来的名字与提取时找的名字对不上）。所以这里把判定收敛成
 * 唯一入口 [openZipArchive]，**列目录与提取都必须走它**，不允许各自 `ZipFile.builder()`。
 *
 * ## 判定顺序
 *
 * 1. 用户在设置里**显式**选了非 UTF-8 编码 → 先按它试；但它若反而给出乱码，就退回自动判定
 *    （防止「选了 GBK，结果遇到 UTF-8 的包，从此所有包都乱码」这种把用户关进小黑屋的设置）。
 * 2. 自动：先按 UTF-8 读一遍条目名，若出现替换符 U+FFFD（解码失败的标记），再依次试 GBK、Big5，
 *    取第一个不再产生 U+FFFD 的。
 * 3. 都不行 → 退回 UTF-8。**宁可乱码也不报错**：乱码至少还能看出条目数量与大小，能继续解压。
 *
 * ## 一个必须保住的行为
 *
 * commons-compress 的 `ZipFile` 会**逐条**看通用标志位第 11 位：置了就一律按 UTF-8 解，
 * 不看我们传的 charset。这正是我们要的 —— 有些包里混着「显式 UTF-8 的新条目」与
 * 「按代码页的老条目」，逐条判定才能两边都对。所以这里传的 charset 只是**兜底**，
 * 不会把那些正确标记了 UTF-8 的条目也一起按 GBK 解掉。
 */
internal object ArchiveEncoding {

    /** 解码失败时 `String` 里会出现的替换符；出现即说明这一版编码不对。 */
    private const val REPLACEMENT = '\uFFFD'

    /**
     * 自动判定时的候选顺序。
     *
     * 中文排最前（本机用户群），后面跟 **ISO-8859-1 与 CP437** —— 用户反馈 e15 的
     * 「编码尾巴」：这两个才是 zip 的**历史正主**（APPNOTE 规定的默认就是 CP437，
     * 老 Unix 工具写的是 ISO-8859-1），原先链条断在 GBK/Big5 之后直接退回 UTF-8，
     * 这类包的条目名就是一串 U+FFFD。
     *
     * 为什么不能把它们排到前面：单字节编码**任何字节都解得出来**、永不产生替换符，
     * 只要排在 GBK 前面，中文包就再也没有 GBK 的事了。所以顺序是
     * `GBK → Big5 → ISO-8859-1 → CP437`，且两者各带一道**别的**门（见 [plausibleName]）：
     * - ISO-8859-1 解 0x80–0x9F 得到 C1 控制字符 → 出局，把 CP437 文件里的重音字母
     *   （`é` 在 CP437 就是 0x80–0x9F 那一段）让回 CP437；
     * - CP437 连 0x01–0x1F 都映射成符号、天生不产生控制符 → 它是最后一道，
     *   于是**拉丁 1 文件名（0xA0–0xFF）归 ISO、老 DOS 包归 CP437**，各自得其所。
     */
    private val FALLBACKS = listOf("GBK", "Big5", "ISO-8859-1", "CP437")

    /**
     * 打开一个 zip 并把条目名编码定下来。**列目录与提取的唯一入口。**
     *
     * 判定要多读一遍中央目录 —— 但那只是索引、不解压正文，相对后续任何一次真实读取都可忽略；
     * 换来的是「列出来什么名字，提取时就找什么名字」，这个一致性值得。
     */
    internal fun openZipArchive(file: File): CommonsZipFile {
        val charset = pickCharset(file)
        return CommonsZipFile.builder().setFile(file).setCharset(charset).get()
    }

    /** 判定 [file] 的条目名编码。判定失败一律退回 UTF-8，绝不抛。 */
    internal fun pickCharset(file: File): Charset {
        val utf8 = Charset.forName("UTF-8")

        // 1) 用户显式选的非 UTF-8：优先尊重，但要验一遍真的不乱码
        configuredOrNull()?.let { cfg ->
            // 两道门一起过：`noMojibake` 对单字节编码（ISO-8859-1 / CP437）恒为真 ——
            // 用户选了 ISO 却撞上一串 C1 控制符时，只查替换符等于没验，
            // 那还不如退回自动判定。GBK / Big5 解不出控制符，第二道门对它们是空的，
            // 所以中文包的手动指定行为**一个字没变**。
            if (noMojibake(file, cfg) && plausibleName(file, cfg)) return cfg
        }

        // 2) 自动：UTF-8 干净就用它
        if (noMojibake(file, utf8)) return utf8

        // 3) 依次试 GBK / Big5 / ISO-8859-1 / CP437 —— 四档的门不一样，见 [FALLBACKS]
        for (name in FALLBACKS) {
            val cs = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val ok = if (name == "GBK" || name == "Big5") {
                // 双字节编码：解不动就出替换符，出替换符即出局（原行为，一个字没动）
                noMojibake(file, cs)
            } else {
                // 单字节编码：任何字节都解得出来、**永不**产生替换符，
                // `noMojibake` 对它们永远是真 —— 必须换成「像不像人起的名字」这道门
                plausibleName(file, cs)
            }
            if (ok) return cs
        }

        // 4) 都不行 —— 退回 UTF-8，宁可乱码也不让整包打不开
        return utf8
    }

    /**
     * 与 [noMojibake] 同一趟读法，多问一句：解出来的**像不像一个人会起的文件名**。
     *
     * 专给单字节编码（ISO-8859-1 / CP437）用 —— 它们是**满射**，任何字节都能落成一个
     * 字符，`noMojibake` 对它们恒为真，没这道门它们会连 GBK 的位置一起吃掉。
     * 判据只有一条：**不许出现控制字符**（C0 `0x00–0x1F`、DEL、C1 `0x7F–0x9F`）。
     *
     * 这一条刚好把典型情形分开放对：
     * - 老 DOS 包把 `é` 存成 `0x80–0x9F`：CP437 解出重音字母（无控制符）→ 过；
     *   同一批字节在 ISO-8859-1 下是 C1 控制符 → 不过，正好让给 CP437。
     * - 拉丁 1 文件名用 `0xA0–0xFF`：两种编码都无控制符，而顺序里 ISO 在前 → 归 ISO
     *   （`0xC0` 以后两者并不相同，CP437 那一段混着希腊字母与数学符号，ISO 给的才是重音字母）。
     * - CP437 把 `0x01–0x1F` 映射成 ♠♥► 这类**符号**（不是控制符）→ 过；
     *   同一批字节在 ISO 下是 C0 控制符 → 不过，这种包最终仍由 CP437 接手。
     */
    private fun plausibleName(file: File, charset: Charset): Boolean = try {
        CommonsZipFile.builder().setFile(file).setCharset(charset).get().use { zip ->
            val en = zip.entries
            var ok = true
            while (en.hasMoreElements()) {
                val name = en.nextElement().name
                val bad = name.indexOf(REPLACEMENT) >= 0 ||
                    name.any { ch -> ch.code < 0x20 || ch.code in 0x7F..0x9F }
                if (bad) {
                    ok = false
                    break
                }
            }
            ok
        }
    } catch (_: Exception) {
        // 打不开不是编码问题（损坏/没权限），交给真正打开的那一次去报错
        false
    }

    /**
     * 读一遍全部条目名，只要有一条解出 U+FFFD 就返回 false 并**立刻停** ——
     * 不必把上万条读完才知道这版编码不对。
     */
    private fun noMojibake(file: File, charset: Charset): Boolean = try {
        CommonsZipFile.builder().setFile(file).setCharset(charset).get().use { zip ->
            val en = zip.entries
            while (en.hasMoreElements()) {
                if (en.nextElement().name.indexOf(REPLACEMENT) >= 0) return@use false
            }
            true
        }
    } catch (_: Exception) {
        // 打不开不是编码问题（损坏/没权限），交给真正打开的那一次去报错
        false
    }

    /** 设置里的显式选择；选的是默认值 UTF-8 就当作「没选」，交给自动判定。 */
    private fun configuredOrNull(): Charset? {
        val v = FilePrefs.zipEncoding()
        if (v.equals(FilePrefs.ENCODING_UTF8, ignoreCase = true)) return null
        return runCatching { Charset.forName(v) }.getOrNull()
    }

    /**
     * 这个 zip 里有没有**加密条目** —— 只读通用标志位，不解密，**不需要密码**。
     *
     * 两处要用，且都只在这一步之后才会去碰密码：
     * - 解压框据此决定摆不摆密码框（没加密就摆，等于加高一个用不上的弹窗，用户反馈 c15）；
     * - 解出引擎据此在**动手之前**先拒绝「没有密码的加密包」—— 否则要等解到那一条
     *   才发现解不动，前面已经落了一批文件，半截产物比一句报错难收拾得多。
     *
     * 判不出来一律按**没加密**处理：判错的代价只是「白问一次密码」，
     * 反过来（把能解的说成不能）才是真的关上门。
     */
    /**
     * 这个包**没密码读不动**吗（b10 / e15 **P1-12**）。
     *
     * - **zip**：读中央目录的通用标志位，不解密、**不需要密码**；
     * - **7z**：没有可白读的标志位，只能**试开一次** —— 头被加密的（连文件名都读不出）
     *   开的那一下就会被库挡回来。头没加密的还得再进一步：拿**第一条文件**开个流试读
     *   一个字节，AES 解码器在建流/首读那一下才初始化，没密码又加密的当场就挡回来。
     *   只读一个字节，solid 包也只解到这儿为止，不会为了探一下把整包解一遍。
     *
     * 已经输过密码的包（见 `rememberArchivePassword`）这里开得成功 → 返回 false，
     * 该摆的框不摆第二遍。
     */
    internal fun hasEncryptedEntry(file: File): Boolean = try {
        when (detectArchiveFormat(file)) {
            ArchiveFormat.ZIP -> {
                // 编码无所谓 —— 这里一个条目名都不解，只看标志位
                CommonsZipFile.builder().setFile(file).get().use { zip ->
                    val en = zip.entries
                    while (en.hasMoreElements()) {
                        // 加密位在**通用标志位**里，得从这儿取 —— 条目本身没有直取的口子
                        if (en.nextElement().generalPurposeBit?.usesEncryption() == true) {
                            return@use true
                        }
                    }
                    false
                }
            }

            ArchiveFormat.SEVEN_Z -> {
                openSevenZ(file).use { z ->
                    var encrypted = false
                    while (true) {
                        val e = z.getNextEntry() ?: break
                        if (e.isDirectory) continue
                        try {
                            z.getInputStream(e).use { ins -> ins.read() }
                        } catch (ex: Exception) {
                            if (isPasswordRequired(ex)) encrypted = true else throw ex
                        }
                        break
                    }
                    encrypted
                }
            }

            // RAR 的密码位 junrar 自己会读：头被加密时 openRar 直接抛，
            // 落到下面那个 catch 里转成 true（该摆的框要摆，不能当没加密）
            ArchiveFormat.RAR -> openRar(file).use { it.isPasswordProtected() }

            else -> false
        }
    } catch (e: Exception) {
        // 开不起来只有两种解释：要密码、或包本身坏了。只认前者 —— 报「损坏」的话，
        // 用户只会以为包坏了（其实它好好的，只是没给密码）。
        isPasswordRequired(e)
    }
}
