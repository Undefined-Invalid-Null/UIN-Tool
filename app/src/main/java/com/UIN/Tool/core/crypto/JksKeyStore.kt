// app/src/main/java/com/UIN/Tool/core/crypto/JksKeyStore.kt
package com.UIN.Tool.core.crypto

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Provider
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * o.txt 第 11 条：Android 自带的 KeyStore 没有 JKS provider。
 *
 * `KeyStore.getInstance("JKS")` 在 Android 上必然抛异常（系统只注册了
 * AndroidCAStore / AndroidKeyStore / BKS / PKCS12 等），所以以前无论口令对不对，
 * `.jks` 文件都只会显示「格式不支持」。
 *
 * 这里用 BouncyCastle（新引入的依赖）完成算法部分：
 *  - JKS 私钥是 PKCS#8 `EncryptedPrivateKeyInfo`，默认算法
 *    `PBEWithSHA1And3-KeyTripleDES-CBC`，BC 提供该 PBE 与 ASN.1 解析；
 *  - 旧版 JKS 还会在 `EncryptedPrivateKeyInfo` 外面再套一层 SHA-1 异或保护
 *    （Sun 的 `KeyProtector`），这里两种都支持；
 *  - BKS / PKCS12 直接交给 BC provider 的 KeyStore 实现。
 *
 * 容器本身（`FEED FEED` 头、条目表、SHA-1 完整性摘要）没有任何主流库提供
 * `KeyStoreSpi`，因此这一层由本文件完成，算法仍全部走 JCA/BC。
 */

/** JKS 文件结构不合法（不是 JKS、版本不认识、内容截断）。 */
class JksFormatException(message: String) : Exception(message)

/** 完整性摘要对不上：口令错误，或文件被改动。 */
class JksPasswordException(message: String) : Exception(message)

/** 需要 BC 算法时的单例 provider，不往全局 [java.security.Security] 注册，避免和系统 BC 打架。 */
object BcProvider {
    val instance: Provider by lazy { BouncyCastleProvider() }
}

/** 一条 JKS 条目。私钥保持加密状态，只有真正需要时才用口令解。 */
sealed class JksEntry {
    abstract val alias: String
    abstract val timestamp: Long

    class PrivateKeyEntry(
        override val alias: String,
        override val timestamp: Long,
        val protectedKey: ByteArray,
        val chain: List<ByteArray>
    ) : JksEntry()

    class TrustedCertificateEntry(
        override val alias: String,
        override val timestamp: Long,
        val certificate: ByteArray
    ) : JksEntry()
}

/** 读出来的完整密钥库。 */
data class JksStore(
    val version: Int,
    val entries: List<JksEntry>
)

/** 写 JKS 时的条目。 */
sealed class JksOutputEntry {
    abstract val alias: String

    class PrivateKeyEntry(
        override val alias: String,
        val privateKey: PrivateKey,
        val chain: List<Certificate>
    ) : JksOutputEntry()

    class TrustedCertificateEntry(
        override val alias: String,
        val certificate: Certificate
    ) : JksOutputEntry()
}

object JksKeyStore {

    /** Sun/Oracle 的 `KeyProtector` 里固定的口令盐。 */
    private val PEPPER = "Mighty Aphrodite".toByteArray(Charsets.UTF_8)

    /** JKS 文件头魔数（`FEED FEED`）。用 val 而非 const：`.toInt()` 不算编译期常量。 */
    private val MAGIC = 0xFEEDFEED.toInt()
    private const val DIGEST_LEN = 20
    private const val SALT_LEN = 20

    /** Sun/Oracle 专有的 JKS 私钥保护算法 OID。 */
    private const val SUN_JKS_OID = "1.3.6.1.4.1.42.2.17.1.1"
    private const val MAX_ENTRIES = 10_000
    private const val MAX_FILE_BYTES = 64 * 1024 * 1024

    fun isJks(file: File): Boolean = try {
        if (!file.isFile || file.length() < 8) false
        else file.inputStream().use { input ->
            val head = ByteArray(4)
            input.read(head) == 4 && readInt(head, 0) == MAGIC
        }
    } catch (e: Exception) {
        false
    }

    /**
     * 读取并校验收件。口令错误抛 [JksPasswordException]，结构不对抛 [JksFormatException]。
     */
    fun read(file: File, password: CharArray): JksStore {
        if (!file.isFile || !file.canRead()) {
            throw JksFormatException("keystore is not readable: ${file.absolutePath}")
        }
        if (file.length() > MAX_FILE_BYTES) {
            throw JksFormatException("keystore is too large: ${file.length()}")
        }
        val data = file.readBytes()
        if (data.size < 12 + DIGEST_LEN) {
            throw JksFormatException("truncated jks header")
        }
        val cursor = Cursor(data)
        if (cursor.u4() != MAGIC) {
            throw JksFormatException("bad jks magic")
        }
        val version = cursor.u4()
        if (version !in 1..3) {
            throw JksFormatException("unsupported jks version $version")
        }
        val count = cursor.u4()
        if (count < 0 || count > MAX_ENTRIES) {
            throw JksFormatException("bad entry count $count")
        }
        val entries = ArrayList<JksEntry>(count)
        repeat(count) {
            when (cursor.u4()) {
                1 -> {
                    val alias = cursor.utf()
                    val timestamp = cursor.u8()
                    val protectedKey = cursor.bytes(cursor.u4())
                    val chainCount = cursor.u4()
                    if (chainCount < 0 || chainCount > MAX_ENTRIES) {
                        throw JksFormatException("bad chain count $chainCount")
                    }
                    val chain = ArrayList<ByteArray>(chainCount)
                    repeat(chainCount) {
                        cursor.utf() // 证书类型，正常都是 X.509
                        chain += cursor.bytes(cursor.u4())
                    }
                    entries += JksEntry.PrivateKeyEntry(alias, timestamp, protectedKey, chain)
                }
                2 -> {
                    val alias = cursor.utf()
                    val timestamp = cursor.u8()
                    cursor.utf() // 证书类型
                    entries += JksEntry.TrustedCertificateEntry(
                        alias,
                        timestamp,
                        cursor.bytes(cursor.u4())
                    )
                }
                else -> throw JksFormatException("unknown entry tag")
            }
        }
        val bodyEnd = data.size - DIGEST_LEN
        if (cursor.position != bodyEnd) {
            throw JksFormatException("entry table does not end at digest")
        }
        verifyDigest(data, bodyEnd, password)
        return JksStore(version, entries)
    }

    /** 用库口令解出私钥；解不开（key password 与 store password 不同）时抛异常。 */
    fun decryptPrivateKey(entry: JksEntry.PrivateKeyEntry, password: CharArray): PrivateKey {
        val encrypted = PKCS8EncryptedPrivateKeyInfo(entry.protectedKey)
        val plain = if (encrypted.encryptionAlgorithm.algorithm.id == SUN_JKS_OID) {
            recoverSunKeyProtector(encrypted.encryptedData, passwordBytes(password))
        } else {
            // 少数工具会把 JKS 私钥按标准 PKCS#12 PBE 存，BC 直接解。
            val decryptor = JceOpenSSLPKCS8DecryptorProviderBuilder()
                .setProvider(BcProvider.instance)
                .build(password)
            encrypted.decryptPrivateKeyInfo(decryptor).encoded
        }
        return buildPrivateKey(plain)
    }

    /** PKCS#8 私钥 → JCA PrivateKey（BC 负责算法映射）。 */
    private fun buildPrivateKey(pkcs8: ByteArray): PrivateKey {
        val info = PrivateKeyInfo.getInstance(pkcs8)
        val algorithm = when (info.privateKeyAlgorithm.algorithm.id) {
            PKCSObjectIdentifiers.rsaEncryption.id,
            PKCSObjectIdentifiers.id_RSASSA_PSS.id -> "RSA"
            X9ObjectIdentifiers.id_ecPublicKey.id -> "EC"
            X9ObjectIdentifiers.id_dsa.id -> "DSA"
            PKCSObjectIdentifiers.dhKeyAgreement.id -> "DH"
            else -> "RSA"
        }
        return KeyFactory.getInstance(algorithm, BcProvider.instance)
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    fun parseChain(entry: JksEntry.PrivateKeyEntry): List<X509Certificate> =
        entry.chain.mapNotNull { bytes ->
            runCatching { parseCertificate(bytes) }.getOrNull()
        }

    fun parseCertificate(entry: JksEntry.TrustedCertificateEntry): X509Certificate =
        parseCertificate(entry.certificate)

    fun parseCertificate(bytes: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return ByteArrayInputStream(bytes).use { input ->
            factory.generateCertificate(input) as X509Certificate
        }
    }

    /**
     * 写出 JKS（目标格式选 JKS 时用）。私钥用 Sun KeyProtector 算法封装，
     * 与 keytool 产出的结构一致；完整性摘要用「口令字节 + Mighty Aphrodite + 正文」的 SHA-1。
     */
    fun write(file: File, entries: List<JksOutputEntry>, password: CharArray) {
        val out = ByteArrayOutputStream()
        val timestamp = System.currentTimeMillis()
        writeInt(out, MAGIC)
        writeInt(out, 2)
        writeInt(out, entries.size)
        for (entry in entries) {
            when (entry) {
                is JksOutputEntry.PrivateKeyEntry -> {
                    writeInt(out, 1)
                    writeUtf(out, entry.alias)
                    writeLong(out, timestamp)
                    val protectedKey = protectPrivateKey(entry.privateKey, password)
                    writeInt(out, protectedKey.size)
                    out.write(protectedKey)
                    writeInt(out, entry.chain.size)
                    entry.chain.forEach { certificate ->
                        writeUtf(out, "X.509")
                        val encoded = certificate.encoded
                        writeInt(out, encoded.size)
                        out.write(encoded)
                    }
                }
                is JksOutputEntry.TrustedCertificateEntry -> {
                    writeInt(out, 2)
                    writeUtf(out, entry.alias)
                    writeLong(out, timestamp)
                    writeUtf(out, "X.509")
                    val encoded = entry.certificate.encoded
                    writeInt(out, encoded.size)
                    out.write(encoded)
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(passwordBytes(password))
        digest.update(PEPPER)
        digest.update(out.toByteArray())
        out.write(digest.digest())

        val bytes = out.toByteArray()
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.writeBytes(bytes)
                temp.delete()
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * JKS 私钥保护（Sun KeyProtector，OID 1.3.6.1.4.1.42.2.17.1.1）。
     * 存储格式：salt(20) + XOR(明文, SHA-1 流) + SHA-1(口令 + 明文)(20)。
     */
    private fun protectPrivateKey(key: PrivateKey, password: CharArray): ByteArray {
        val pass = passwordBytes(password)
        val plain = key.encoded
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val stream = keystream(pass, salt, plain.size)
        val protectedData = ByteArray(SALT_LEN + plain.size + DIGEST_LEN)
        System.arraycopy(salt, 0, protectedData, 0, SALT_LEN)
        for (index in plain.indices) {
            protectedData[SALT_LEN + index] =
                (plain[index].toInt() xor stream[index].toInt()).toByte()
        }
        val checksum = MessageDigest.getInstance("SHA-1").apply {
            update(pass)
            update(plain)
        }.digest()
        System.arraycopy(checksum, 0, protectedData, SALT_LEN + plain.size, DIGEST_LEN)

        val algorithm = AlgorithmIdentifier(
            ASN1ObjectIdentifier(SUN_JKS_OID),
            DERNull.INSTANCE
        )
        return PKCS8EncryptedPrivateKeyInfo(
            org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo(algorithm, protectedData)
        ).encoded
    }

    private fun recoverSunKeyProtector(protectedKey: ByteArray, pass: ByteArray): ByteArray {
        if (protectedKey.size < SALT_LEN + DIGEST_LEN) {
            throw JksFormatException("protected key is too short")
        }
        val salt = protectedKey.copyOfRange(0, SALT_LEN)
        val encrypted = protectedKey.copyOfRange(SALT_LEN, protectedKey.size - DIGEST_LEN)
        val stream = keystream(pass, salt, encrypted.size)
        val plain = ByteArray(encrypted.size) { index ->
            (encrypted[index].toInt() xor stream[index].toInt()).toByte()
        }
        val checksum = MessageDigest.getInstance("SHA-1").apply {
            update(pass)
            update(plain)
        }.digest()
        val stored = protectedKey.copyOfRange(protectedKey.size - DIGEST_LEN, protectedKey.size)
        if (!MessageDigest.isEqual(checksum, stored)) {
            throw JksPasswordException("protected key checksum mismatch")
        }
        return plain
    }

    /** 与 OpenJDK KeyProtector 一致：d0 = salt，dn = SHA-1(口令 + d(n-1))。 */
    private fun keystream(pass: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        val digest = MessageDigest.getInstance("SHA-1")
        var state = salt
        var offset = 0
        while (offset < length) {
            digest.update(pass)
            digest.update(state)
            state = digest.digest()
            val count = minOf(DIGEST_LEN, length - offset)
            System.arraycopy(state, 0, output, offset, count)
            offset += count
        }
        return output
    }

    private fun verifyDigest(data: ByteArray, bodyEnd: Int, password: CharArray) {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(passwordBytes(password))
        digest.update(PEPPER)
        digest.update(data, 0, bodyEnd)
        val computed = digest.digest()
        val stored = data.copyOfRange(bodyEnd, bodyEnd + DIGEST_LEN)
        if (!MessageDigest.isEqual(computed, stored)) {
            throw JksPasswordException("jks integrity digest mismatch")
        }
    }

    /** JKS 的口令字节是 UTF-16BE（每个 char 两字节，高位在前）。 */
    private fun passwordBytes(password: CharArray): ByteArray {
        val out = ByteArray(password.size * 2)
        password.forEachIndexed { index, c ->
            out[index * 2] = (c.code ushr 8).toByte()
            out[index * 2 + 1] = c.code.toByte()
        }
        return out
    }

    private class Cursor(private val data: ByteArray) {
        var position: Int = 0
            private set

        private fun ensure(count: Int) {
            if (count < 0 || position + count > data.size) {
                throw JksFormatException("truncated jks at $position")
            }
        }

        fun u4(): Int {
            ensure(4)
            val value = readInt(data, position)
            position += 4
            return value
        }

        fun u8(): Long {
            ensure(8)
            var value = 0L
            repeat(8) {
                value = (value shl 8) or (data[position++].toLong() and 0xFF)
            }
            return value
        }

        fun bytes(count: Int): ByteArray {
            ensure(count)
            val value = data.copyOfRange(position, position + count)
            position += count
            return value
        }

        fun utf(): String {
            ensure(2)
            val length = ((data[position].toInt() and 0xFF) shl 8) or
                (data[position + 1].toInt() and 0xFF)
            ensure(2 + length)
            position += 2
            val value = String(data, position, length, Charsets.UTF_8)
            position += length
            return value
        }
    }

    private fun readInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 24)
        out.write(value ushr 16)
        out.write(value ushr 8)
        out.write(value)
    }

    private fun writeLong(out: ByteArrayOutputStream, value: Long) {
        for (shift in 56 downTo 0 step 8) {
            out.write((value ushr shift).toInt())
        }
    }

    private fun writeUtf(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write((bytes.size ushr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }
}
