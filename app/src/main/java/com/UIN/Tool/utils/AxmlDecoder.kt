// app/src/main/java/com/UIN/Tool/utils/AxmlDecoder.kt
package com.UIN.Tool.utils

import com.reandroid.apk.AndroidFrameworks
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.xml.XMLFactory
import java.io.File

/**
 * Android 二进制 XML（AXML）编解码入口 —— 直接调用 **ARSCLib**（`io.github.reandroid:ARSCLib`），
 * 不自己解析字节格式。
 *
 * 选它的原因：它是 APKEditor 的底层库，纯 Java、Apache-2.0、可在 Android 运行时使用，
 * 自带 android-23…36 的框架资源包，因此**离线**就能把 AXML 里的
 * `android:versionCode` 这类资源 ID 属性名还原成人读的名字；xml 节点、命名空间、
 * CDATA 与注释也由库统一处理。能力清单见项目根目录的 `apk_editor.md`。
 *
 * k.txt 第 10 条：编辑保存时再把文本 XML **重新编码**回二进制 AXML。
 */
object AxmlDecoder {

    /**
     * 文件头是否是 AXML（RES_XML_TYPE + 8 字节头）。
     *
     * 只读前 4 字节，不整份载入；`.xml` 里也可能出现二进制资源 XML，所以按内容判断
     * 比按后缀判断更可靠。
     */
    fun isAxml(file: File): Boolean = try {
        file.inputStream().use { input ->
            val head = ByteArray(4)
            input.read(head) == 4 &&
                (head[0].toInt() and 0xFF) == 0x03 &&
                (head[1].toInt() and 0xFF) == 0x00 &&
                (head[2].toInt() and 0xFF) == 0x08 &&
                (head[3].toInt() and 0xFF) == 0x00
        }
    } catch (_: Exception) {
        false
    }

    /**
     * 解码成可读 XML 文本；不是 AXML、文件读不了或库解析失败时返回 null。
     *
     * **调用方必须放在 IO 线程**：第一次解码会从 ARSCLib 的 jar 资源里载入
     * 对应 API 级别的框架包（android-xx.apk），有几十毫秒级的一次性开销。
     */
    fun decode(file: File): String? {
        if (!isAxml(file)) return null
        return decodeBytes(file)
    }

    /** 从字节解码（外部 content:// 没有真实路径时用）。 */
    fun decode(bytes: ByteArray): String? {
        if (!isAxml(bytes)) return null
        return try {
            val document = ResXmlDocument()
            document.readBytes(bytes.inputStream())
            applyFramework(document)
            document.serializeToXml()
        } catch (e: Throwable) {
            AppLog.w("AxmlDecoder", "decode bytes failed (${e.message})")
            null
        }
    }

    private fun decodeBytes(file: File): String? = try {
        val document = ResXmlDocument()
        file.inputStream().use { document.readBytes(it) }
        applyFramework(document)
        document.serializeToXml()
    } catch (e: Throwable) {
        AppLog.w("AxmlDecoder", "decode failed: ${file.name} (${e.message})")
        null
    }

    /**
     * k.txt 第 10 条：把编辑器里的文本 XML 重新编码成二进制 AXML。
     *
     * 用 ARSCLib 的 `ResXmlDocument.parse(XmlPullParser)` 建文档，再
     * `writeBytes(OutputStream)` 输出 —— 与 APKEditor 的 `XMLEncodeSource` 同一条路径，
     * 不手写二进制格式。失败返回 null，调用方保留原文件并提示，绝不写半截内容。
     *
     * **调用方必须放在 IO 线程**（首次会载入框架包）。
     */
    fun encodeToBytes(xml: String): ByteArray? = try {
        val document = ResXmlDocument()
        applyFramework(document)
        document.parse(XMLFactory.newPullParser(xml))
        java.io.ByteArrayOutputStream().use { out ->
            // 继承自 com.reandroid.arsc.base.Block。
            document.writeBytes(out)
            out.toByteArray()
        }
    } catch (e: Throwable) {
        AppLog.w("AxmlDecoder", "encode failed (${e.message})")
        null
    }

    private fun applyFramework(document: ResXmlDocument) {
        // 独立 AXML 没有自己的 resources.arsc，属性名只是资源 ID；挂上框架包后
        // ARSCLib 能按 ID 还原成 android:name / android:exported 等可读名字。
        val framework = runCatching {
            AndroidFrameworks.getBestMatch(android.os.Build.VERSION.SDK_INT)
                ?: AndroidFrameworks.getLatest()
        }.getOrNull()
        // 走 ApkModule 的单一返回类型签名：FrameworkApk 上还有一个协变返回的桥接方法
        //（FrameworkTable / TableBlock 同名同参），直接调会在 Kotlin 侧留下歧义隐患。
        val packageBlock = runCatching {
            (framework as? com.reandroid.apk.ApkModule)
                ?.tableBlock
                ?.getPackages()
                ?.takeIf { it.hasNext() }
                ?.next()
        }.getOrNull()
        if (packageBlock != null) {
            document.setPackageBlock(packageBlock)
        }
    }

    private fun isAxml(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            (bytes[0].toInt() and 0xFF) == 0x03 &&
            (bytes[1].toInt() and 0xFF) == 0x00 &&
            (bytes[2].toInt() and 0xFF) == 0x08 &&
            (bytes[3].toInt() and 0xFF) == 0x00
}
