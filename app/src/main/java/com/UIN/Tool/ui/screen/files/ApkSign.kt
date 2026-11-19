// app/src/main/java/com/UIN/Tool/ui/screen/files/ApkSign.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * 给 APK **重新签名**（用户反馈 e15 **P1-11**「改 APK 后重签名」）。
 *
 * ## 为什么改完就装不上了
 * 往包里动一个字节，v1 的 `META-INF` 里 `*.SF` 摘要、v2/v3 的签名块就全对不上了，
 * 系统在安装那一步直接拒。而我们改包这条路本身是**暂存件 + rename**（见
 * `ZipWrite.withStagedArchive`），结构好得很 —— 缺的只有签名。
 *
 * ## 密钥从哪来
 * [KEY_ASSET] 是一把**专门生成的一次性密钥**，只用来签「用户改过的包」。
 * **绝不**拿本应用自己的签名钥匙：那把一旦被打进 APK，等于把「以 UIN Tool 名义
 * 发更新」的权力送给任何人。
 *
 * 代价必须说清楚（页面上也摆了同一句）：**签名换了**，改过的那个 APK 不能覆盖安装
 * 原应用，得先卸载。这一条绕不开 —— 没有原包的私钥，谁也签不出原来的签名。
 *
 * ## 对齐
 * [ApkSigner.setAlignmentPreserved] 尽量保留输入包里已有的对齐。但要注意：包内条目是
 * 被 zip4j **重写**过的，`lib/` 下的 `*.so` 若原本是「不压缩 + 页对齐」（`extractNativeLibs=false`），
 * 这一步救不回来 —— 那是「改包」这条路本身的代价，不是签名这一步造成的，
 * 也不是签名这一步能修的。
 */
object ApkSign {

    /** 重签用的密钥（assets 里那份）。**不是**本应用的签名钥匙，见 KDoc。 */
    private const val KEY_ASSET = "apk_resign.p12"
    private const val KEY_ALIAS = "resign"

    /**
     * 密钥口令写在代码里是有意的：这把钥匙**没有别的用处**，保护它等于保护
     * 「谁能给改过的包盖章」，而那本来就是用户自己在做的事。
     */
    private const val KEY_PASSWORD = "uintool"

    /**
     * 用自带密钥给 [apk] 重签，**就地覆盖**（先写同目录临时件，再换上去）。
     *
     * @throws IOException 读密钥 / 签名 / 落盘任一步失败；调用方负责把话说给用户
     */
    fun sign(context: Context, apk: File) {
        if (!apk.isFile) throw IOException("not a file: $apk")
        val (key, certs) = loadKey(context.applicationContext)
        val tmp = File(apk.parentFile, ".${apk.name}.resign")
        try {
            ApkSigner.Builder(
                listOf(ApkSigner.SignerConfig.Builder(KEY_ALIAS, key, certs).build())
            )
                .setInputApk(apk)
                .setOutputApk(tmp)
                // v1 覆盖老设备、v2/v3 覆盖新设备 —— 少开哪一套，就有一批机器装不上。
                // v4 是旁路签名（`.idsig`），要单独出文件，这里不摆。
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setV4SigningEnabled(false)
                .setAlignmentPreserved(true)
                .setCreatedBy("UIN Tool")
                .build()
                .sign()

            // 新件得是真的：0 字节 / 没写完的临时件绝不能顶上去
            if (!tmp.isFile || tmp.length() == 0L) {
                throw IOException("resign produced no output")
            }
            if (!tmp.renameTo(apk)) {
                // 极少数文件系统拒绝覆盖 rename → 退成拷贝。
                // **不许**先删原包：拷贝万一失败，删了就两头落空。
                apk.outputStream().use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun loadKey(context: Context): Pair<PrivateKey, List<X509Certificate>> {
        val ks = KeyStore.getInstance("PKCS12")
        context.assets.open(KEY_ASSET).use { ins ->
            ks.load(ins, KEY_PASSWORD.toCharArray())
        }
        val key = ks.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as? PrivateKey
            ?: throw IOException("resign key missing in $KEY_ASSET")
        val cert = ks.getCertificate(KEY_ALIAS) as? X509Certificate
            ?: throw IOException("resign cert missing in $KEY_ASSET")
        return key to listOf(cert)
    }
}
