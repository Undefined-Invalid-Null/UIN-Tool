// app/src/main/java/com/UIN/Tool/ui/screen/files/KeystoreViewerActivity.kt
package com.UIN.Tool.ui.screen.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.UIN.Tool.R
import com.UIN.Tool.core.crypto.BcProvider
import com.UIN.Tool.core.crypto.JksEntry
import com.UIN.Tool.core.crypto.JksFormatException
import com.UIN.Tool.core.crypto.JksKeyStore
import com.UIN.Tool.core.crypto.JksOutputEntry
import com.UIN.Tool.core.crypto.JksPasswordException
import com.UIN.Tool.ui.components.unified.ButtonSize
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.ManageTopAppBar
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedCaptionText
import com.UIN.Tool.ui.components.unified.UnifiedCard
import com.UIN.Tool.ui.components.unified.UnifiedTextField
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Provider
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内置密钥库查看 / 转换页（m.txt 第 9 条）。
 *
 * 支持 Android 上常见的三种容器：
 *  - **JKS**（Android Studio 默认的 `.jks` / `.keystore`）；
 *  - **PKCS12**（`.p12` / `.pfx`，现在 Android Studio 也默认用它）；
 *  - **BKS / BKS-V1**（BouncyCastle，旧版 Android 签名常用）。
 *
 * 查看：输入库口令 → 列出每个别名、条目类型、算法、证书主题/签发者、有效期与
 * SHA-256 指纹。私钥**不会**被导出成明文，页面上只显示「存在私钥」。
 *
 * 转换：选目标格式 + 新口令 → 在同目录生成 `<原名>_converted.<扩展名>`。
 * 私钥条目沿用**源库口令**去解（JKS 的 key password 通常与 store password 相同；
 * 不同则这一条会明确报「无法导出私钥」，其余条目照常转换，不会整页失败）。
 */
class KeystoreViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            UINToolTheme {
                KeystoreRoot(path = path, onExit = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"

        fun start(context: Context, path: String) {
            context.startActivity(
                Intent(context, KeystoreViewerActivity::class.java).apply {
                    putExtra(EXTRA_PATH, path)
                }
            )
        }
    }
}

// ==================== 数据 ====================

/** 一条密钥库条目在界面上要展示的信息。 */
private data class KeystoreEntryInfo(
    val alias: String,
    val kind: EntryKind,
    val algorithm: String,
    val subject: String?,
    val issuer: String?,
    val notBefore: Long?,
    val notAfter: Long?,
    val fingerprint: String?,
    val hasPrivateKey: Boolean
)

private enum class EntryKind { PRIVATE_KEY, SECRET_KEY, TRUSTED_CERT }

/** 打开成功的结果。 */
private data class KeystoreContent(
    val format: String,
    val entries: List<KeystoreEntryInfo>
)

/** 可转换的目标格式。 */
private enum class KeystoreFormat(val keyStoreType: String, val extension: String, val labelRes: Int) {
    JKS("JKS", "jks", R.string.keystore_format_jks),
    PKCS12("PKCS12", "p12", R.string.keystore_format_pkcs12),
    BKS("BKS", "bks", R.string.keystore_format_bks)
}

/** 按魔数猜格式；猜不出再交给 [tryTypes] 逐个试。 */
private fun guessType(file: File): String? {
    return try {
        FileInputStream(file).use { input ->
            val head = ByteArray(4)
            val n = input.read(head)
            if (n < 4) return null
            val b = head.map { it.toInt() and 0xFF }
            when {
                b == listOf(0xFE, 0xED, 0xFE, 0xED) -> "JKS"
                b == listOf(0xCE, 0xCE, 0xCE, 0xCE) -> "JCEKS"
                b[0] == 0x30 && b[1] == 0x82 -> "PKCS12"
                // BKS 没有稳定的公开魔数；交给下面的逐个尝试。
                else -> null
            }
        }
    } catch (e: Exception) {
        null
    }
}

// JKS 不在这里：Android 系统没有 JKS provider，BC 也不提供，统一交给 JksKeyStore。
private val TRY_TYPES = listOf("PKCS12", "JCEKS", "BKS-V1", "BKS", "UBER")

/** 打开失败的类别 —— 「密码错」与「格式不支持」必须分开，用户才知道下一步改什么。 */
private enum class KeystoreFailure { WRONG_PASSWORD, UNSUPPORTED, UNREADABLE, UNKNOWN }

private class KeystoreOpenException(
    val failure: KeystoreFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 打开密钥库。口令错误与格式不认识要分开报，用户才知道该改哪一样。
 *
 * 这里同时修掉旧实现的三个坑：
 *  1. 每试一种格式都用**同一份 CharArray** —— 某些 Provider 在 load 失败时会把数组
 *     清零，后面的候选类型就永远解不开。现在每次传 `password.copyOf()`。
 *  2. 条目枚举失败曾与「load 失败」共用同一个 catch：库其实已经解开，只是 `aliases()`
 *     或某个证书字段抛异常，整次 open 就被判失败，界面一律显示「密码错误」。现在
 *     load 成功即算成功，枚举失败只让条目列表为空（并记日志）。
 *  3. 以往把所有异常都折叠成「密码错误」；现在按异常类型归类，并把原因写进日志。
 */
private fun openKeystore(file: File, password: CharArray): Result<KeystoreContent> {
    if (!file.isFile || !file.canRead()) {
        return Result.failure(
            KeystoreOpenException(
                KeystoreFailure.UNREADABLE,
                "keystore is not readable: ${file.absolutePath}"
            )
        )
    }

    // o.txt 第 11 条：JKS 走项目内的读取器（BC 提供算法），不依赖系统 KeyStore。
    if (guessType(file) == "JKS") {
        return try {
            val store = JksKeyStore.read(file, password)
            Result.success(
                KeystoreContent(
                    format = "JKS",
                    entries = store.entries
                        .map { jksEntryInfo(it, password) }
                        .sortedBy { it.alias }
                )
            )
        } catch (e: JksPasswordException) {
            Result.failure(
                KeystoreOpenException(KeystoreFailure.WRONG_PASSWORD, e.message ?: "jks", e)
            )
        } catch (e: JksFormatException) {
            Result.failure(
                KeystoreOpenException(KeystoreFailure.UNSUPPORTED, e.message ?: "jks", e)
            )
        } catch (e: Exception) {
            AppLog.w("KeystoreViewer", "jks reader failed: ${e.message}")
            Result.failure(
                KeystoreOpenException(
                    KeystoreFailure.UNKNOWN,
                    e.message ?: e.javaClass.simpleName,
                    e
                )
            )
        }
    }

    return try {
        val (type, store) = loadStockStore(file, password)
        // load 成功 = 口令对、格式对。枚举失败只让列表为空，不能反过来说密码错。
        val entries = try {
            readEntries(store, password)
        } catch (e: Exception) {
            AppLog.w("KeystoreViewer", "list entries after successful load failed: ${e.message}")
            emptyList()
        }
        Result.success(KeystoreContent(type, entries))
    } catch (e: KeystoreOpenException) {
        Result.failure(e)
    }
}

private val PROVIDER_CHAIN: List<Provider?> = listOf(null, BcProvider.instance)

/**
 * 非 JKS 格式：系统 provider 优先，失败再用 BC（BKS/PKCS12 在 Android 上并非处处都有）。
 * 每次 load 都传 `password.copyOf()` —— 某些 provider 失败时会清空传入的 char[]。
 */
private fun loadStockStore(file: File, password: CharArray): Pair<String, KeyStore> {
    val guessed = guessType(file)
    val candidates = buildList {
        if (guessed != null && guessed != "JKS") add(guessed)
        addAll(TRY_TYPES)
    }.distinct()

    var last: Throwable? = null
    var sawWrongPassword = false
    var sawProvider = false
    for (type in candidates) {
        for (provider in PROVIDER_CHAIN) {
            val store = try {
                if (provider == null) KeyStore.getInstance(type)
                else KeyStore.getInstance(type, provider)
            } catch (e: Exception) {
                last = e
                sawProvider = true
                continue
            }
            try {
                FileInputStream(file).use { store.load(it, password.copyOf()) }
            } catch (e: Exception) {
                last = e
                if (isWrongPasswordError(e)) sawWrongPassword = true
                continue
            }
            return type to store
        }
    }

    val failure = when {
        sawWrongPassword -> KeystoreFailure.WRONG_PASSWORD
        sawProvider -> KeystoreFailure.UNSUPPORTED
        else -> KeystoreFailure.UNKNOWN
    }
    val detail = last?.let { "${it.javaClass.simpleName}: ${it.message}" }
        ?: "no KeyStore provider accepted the file"
    AppLog.w("KeystoreViewer", "open failed ($failure): $detail")
    throw KeystoreOpenException(failure, detail, last)
}

/** JKS 条目 → 展示模型；私钥解不开（key password 与库口令不同）时仍显示证书信息。 */
private fun jksEntryInfo(entry: JksEntry, password: CharArray): KeystoreEntryInfo = when (entry) {
    is JksEntry.PrivateKeyEntry -> {
        val chain = runCatching { JksKeyStore.parseChain(entry) }.getOrDefault(emptyList())
        val cert = chain.firstOrNull()
        val keyAlg = runCatching { JksKeyStore.decryptPrivateKey(entry, password).algorithm }
            .getOrNull()
        certInfo(
            alias = entry.alias,
            kind = EntryKind.PRIVATE_KEY,
            algorithm = keyAlg ?: cert?.publicKey?.algorithm.orEmpty(),
            cert = cert,
            hasPrivateKey = true
        )
    }
    is JksEntry.TrustedCertificateEntry -> {
        val cert = runCatching { JksKeyStore.parseCertificate(entry) }.getOrNull()
        certInfo(
            alias = entry.alias,
            kind = EntryKind.TRUSTED_CERT,
            algorithm = cert?.sigAlgName.orEmpty(),
            cert = cert,
            hasPrivateKey = false
        )
    }
}

/** 各 Provider 对「口令错」的措辞不一样，统一按关键词识别。 */
private fun isWrongPasswordError(e: Throwable): Boolean {
    if (e is java.security.UnrecoverableKeyException) return true
    val text = (e.message ?: "").lowercase(Locale.ROOT)
    return text.contains("password") ||
        text.contains("tampered") ||
        text.contains("integrity") ||
        text.contains("mac invalid") ||
        text.contains("digest") ||
        text.contains("encrypted")
}

private fun readEntries(store: KeyStore, password: CharArray): List<KeystoreEntryInfo> {
    val out = ArrayList<KeystoreEntryInfo>()
    val aliases = store.aliases()
    while (aliases.hasMoreElements()) {
        val alias = aliases.nextElement()
        try {
            // 不用 store.getEntry(alias, null)：Android 上 JKS/BKS 的实现里这个方法
            // 有的版本直接抛 UnsupportedOperationException（null 参数不在支持范围），
            // 结果就是「库明明有内容，查看页却显示空」。isKeyEntry / isCertificateEntry
            // + getKey / getCertificate 是所有 Provider 都实现的基础接口。
            val chain = runCatching { store.getCertificateChain(alias) }.getOrNull()
            val key = runCatching { store.getKey(alias, password) }.getOrNull()
            val cert = (
                chain?.firstOrNull()
                    ?: runCatching { store.getCertificate(alias) }.getOrNull()
                ) as? X509Certificate
            when {
                key is javax.crypto.SecretKey -> {
                    out += KeystoreEntryInfo(
                        alias = alias,
                        kind = EntryKind.SECRET_KEY,
                        algorithm = key.algorithm.orEmpty(),
                        subject = null,
                        issuer = null,
                        notBefore = null,
                        notAfter = null,
                        fingerprint = null,
                        hasPrivateKey = false
                    )
                }
                key is java.security.PrivateKey || chain != null -> {
                    out += certInfo(
                        alias = alias,
                        kind = EntryKind.PRIVATE_KEY,
                        algorithm = key?.algorithm ?: cert?.publicKey?.algorithm.orEmpty(),
                        cert = cert,
                        hasPrivateKey = true
                    )
                }
                store.isCertificateEntry(alias) || cert != null -> {
                    out += certInfo(
                        alias = alias,
                        kind = EntryKind.TRUSTED_CERT,
                        algorithm = cert?.sigAlgName.orEmpty(),
                        cert = cert,
                        hasPrivateKey = false
                    )
                }
                else -> Unit
            }
        } catch (e: Exception) {
            AppLog.w("KeystoreViewer", "read alias $alias failed: ${e.message}")
        }
    }
    return out.sortedBy { it.alias }
}

private fun certInfo(
    alias: String,
    kind: EntryKind,
    algorithm: String,
    cert: X509Certificate?,
    hasPrivateKey: Boolean
): KeystoreEntryInfo = KeystoreEntryInfo(
    alias = alias,
    kind = kind,
    algorithm = algorithm,
    subject = cert?.subjectDN?.name,
    issuer = cert?.issuerDN?.name,
    notBefore = cert?.notBefore?.time,
    notAfter = cert?.notAfter?.time,
    fingerprint = cert?.let { sha256(it.encoded) },
    hasPrivateKey = hasPrivateKey
)

private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(":") { "%02X".format(it) }
}

/**
 * 把 [source] 转成 [target] 并写到同目录。
 *
 * 返回生成的文件；私钥无法用源口令解出的条目会被跳过并记在 warnings 里。
 */
/** 转换成统一的中间表示，JKS 与系统 provider 的条目都走这里。 */
private data class SourceEntry(
    val alias: String,
    val privateKey: PrivateKey?,
    val chain: List<Certificate>,
    val certificate: Certificate?
)

private fun readSourceEntries(source: File, password: CharArray): List<SourceEntry> {
    if (JksKeyStore.isJks(source)) {
        return JksKeyStore.read(source, password).entries.mapNotNull { entry ->
            when (entry) {
                is JksEntry.PrivateKeyEntry -> {
                    val chain = runCatching { JksKeyStore.parseChain(entry).toList() }
                        .getOrDefault(emptyList())
                    val key = runCatching { JksKeyStore.decryptPrivateKey(entry, password) }
                        .getOrNull()
                    SourceEntry(entry.alias, key, chain, null)
                }
                is JksEntry.TrustedCertificateEntry -> SourceEntry(
                    entry.alias,
                    null,
                    emptyList(),
                    runCatching { JksKeyStore.parseCertificate(entry) as Certificate }.getOrNull()
                )
            }
        }
    }

    val (_, store) = loadStockStore(source, password)
    val out = ArrayList<SourceEntry>()
    val aliases = store.aliases()
    while (aliases.hasMoreElements()) {
        val alias = aliases.nextElement()
        try {
            if (store.isKeyEntry(alias)) {
                val chain = runCatching { store.getCertificateChain(alias)?.toList() }
                    .getOrNull()
                    .orEmpty()
                val key = runCatching { store.getKey(alias, password) as? PrivateKey }
                    .getOrNull()
                out += SourceEntry(alias, key, chain, null)
            } else if (store.isCertificateEntry(alias)) {
                out += SourceEntry(alias, null, emptyList(), store.getCertificate(alias))
            }
        } catch (e: Exception) {
            AppLog.w("KeystoreViewer", "read alias $alias failed: ${e.message}")
        }
    }
    return out
}

/**
 * 把 [source] 转成 [target] 并写到同目录。
 *
 * 目标 JKS 走项目内写出器（BC 负责私钥 PBE 加密），PKCS12/BKS 走 BC provider。
 * 私钥无法用源口令解出的条目会被跳过并记在 warnings 里，不会让整次转换失败。
 */
private fun convertKeystore(
    source: File,
    sourcePassword: CharArray,
    target: KeystoreFormat,
    targetPassword: CharArray
): Pair<File, List<String>> {
    val sourceEntries = readSourceEntries(source, sourcePassword)
    val warnings = ArrayList<String>()

    val base = source.nameWithoutExtension.ifBlank { "keystore" }
    var out = File(source.parentFile, "${base}_converted.${target.extension}")
    var index = 2
    while (out.exists()) {
        out = File(source.parentFile, "${base}_converted_$index.${target.extension}")
        index++
        if (index > 99) break
    }

    val items = ArrayList<JksOutputEntry>()
    for (entry in sourceEntries) {
        if (entry.privateKey != null) {
            if (entry.chain.isEmpty()) {
                // 没有证书链的私钥无法写入任何密钥库，只能跳过。
                warnings += entry.alias
                continue
            }
            items += JksOutputEntry.PrivateKeyEntry(entry.alias, entry.privateKey, entry.chain)
        } else if (entry.certificate != null) {
            items += JksOutputEntry.TrustedCertificateEntry(entry.alias, entry.certificate)
        } else {
            warnings += entry.alias
        }
    }

    if (target == KeystoreFormat.JKS) {
        JksKeyStore.write(out, items, targetPassword)
        return out to warnings
    }

    val dst = KeyStore.getInstance(target.keyStoreType, BcProvider.instance)
    dst.load(null, targetPassword)
    for (item in items) {
        try {
            when (item) {
                is JksOutputEntry.PrivateKeyEntry -> dst.setKeyEntry(
                    item.alias,
                    item.privateKey,
                    targetPassword,
                    item.chain.toTypedArray()
                )
                is JksOutputEntry.TrustedCertificateEntry ->
                    dst.setCertificateEntry(item.alias, item.certificate)
            }
        } catch (e: Exception) {
            AppLog.w("KeystoreViewer", "convert alias ${item.alias} failed: ${e.message}")
            warnings += item.alias
        }
    }
    FileOutputStream(out).use { dst.store(it, targetPassword) }
    return out to warnings
}

// ==================== 界面 ====================

@Composable
private fun KeystoreRoot(path: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf<KeystoreContent?>(null) }

    var targetFormat by remember { mutableStateOf(KeystoreFormat.PKCS12) }
    var newPassword by remember { mutableStateOf("") }
    var converting by remember { mutableStateOf(false) }

    fun unlock() {
        if (password.isEmpty()) {
            error = Str.get(R.string.keystore_password_required)
            return
        }
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                openKeystore(file, password.toCharArray())
            }
            loading = false
            result.onSuccess {
                content = it
                // 转换默认用同格式，用户不必先想清楚要转成什么。
                targetFormat = KeystoreFormat.entries.firstOrNull { f -> f.keyStoreType == it.format }
                    ?: targetFormat
                newPassword = password
            }.onFailure {
                // n.txt 第 12 条：按失败类别给不同文案 —— 旧实现不管什么原因一律
                // 「密码错误或不支持」，用户无从判断到底是口令打错还是格式不认。
                error = when ((it as? KeystoreOpenException)?.failure) {
                    KeystoreFailure.WRONG_PASSWORD -> Str.get(R.string.keystore_wrong_password)
                    KeystoreFailure.UNSUPPORTED -> Str.get(R.string.keystore_unsupported_format)
                    KeystoreFailure.UNREADABLE -> Str.get(R.string.keystore_file_unreadable)
                    else -> Str.get(
                        R.string.keystore_open_failed_detail,
                        it.message ?: it.javaClass.simpleName
                    )
                }
                AppLog.w("KeystoreViewer", "open failed: ${it.message}")
            }
        }
    }

    Scaffold(
        containerColor = AppColors.pageBackground(),
        topBar = {
            ManageTopAppBar(
                titleText = file.name.ifEmpty { Str.get(R.string.keystore_title) },
                onBack = onExit
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UnifiedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = AppColors.primary(),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        UnifiedCaptionText(
                            Str.get(R.string.keystore_file_hint, file.length())
                        )
                    }
                }
            }

            if (content == null) {
                UnifiedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = Str.get(R.string.keystore_password),
                    isPassword = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                UnifiedButton(
                    text = Str.get(R.string.keystore_unlock),
                    onClick = { unlock() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (loading) null else Icons.Default.LockOpen,
                    loading = loading,
                    variant = ButtonVariant.Primary
                )
            } else {
                val data = content!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UnifiedCaptionText(
                        Str.get(R.string.keystore_summary, data.format, data.entries.size)
                    )
                    Spacer(Modifier.weight(1f))
                    UnifiedButton(
                        text = Str.get(R.string.keystore_relock),
                        onClick = {
                            content = null
                            password = ""
                        },
                        variant = ButtonVariant.Text,
                        size = ButtonSize.Small
                    )
                }

                if (data.entries.isEmpty()) {
                    Text(
                        text = Str.get(R.string.keystore_empty),
                        color = AppColors.textSecondary(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    data.entries.forEach { entry ->
                        KeystoreEntryCard(entry)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = Str.get(R.string.keystore_convert_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = Str.get(R.string.keystore_convert_hint),
                    color = AppColors.textSecondary(),
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeystoreFormat.entries.forEach { format ->
                        val selected = format == targetFormat
                        UnifiedButton(
                            text = Str.get(format.labelRes),
                            onClick = { targetFormat = format },
                            modifier = Modifier.weight(1f),
                            variant = if (selected) ButtonVariant.Primary else ButtonVariant.Outlined,
                            size = ButtonSize.Small
                        )
                    }
                }

                UnifiedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = Str.get(R.string.keystore_new_password),
                    isPassword = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                UnifiedButton(
                    text = Str.get(R.string.keystore_convert),
                    onClick = {
                        if (newPassword.isEmpty()) {
                            AppToast.warning(context, Str.get(R.string.keystore_password_required))
                            return@UnifiedButton
                        }
                        converting = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    convertKeystore(
                                        source = file,
                                        sourcePassword = password.toCharArray(),
                                        target = targetFormat,
                                        targetPassword = newPassword.toCharArray()
                                    )
                                }
                            }
                            converting = false
                            result.onSuccess { (out, warnings) ->
                                val message = if (warnings.isEmpty()) {
                                    Str.get(R.string.keystore_convert_success, out.absolutePath)
                                } else {
                                    Str.get(
                                        R.string.keystore_convert_partial,
                                        out.absolutePath,
                                        warnings.joinToString(", ")
                                    )
                                }
                                AppToast.success(context, message)
                            }.onFailure {
                                AppLog.w("KeystoreViewer", "convert failed: ${it.message}")
                                AppToast.error(
                                    context,
                                    Str.get(R.string.keystore_convert_failed, it.message ?: "")
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = converting,
                    icon = if (converting) null else Icons.Default.Lock,
                    variant = ButtonVariant.Secondary
                )

                Box(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun KeystoreEntryCard(entry: KeystoreEntryInfo) {
    UnifiedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entry.hasPrivateKey) Icons.Default.Lock else Icons.Default.Key,
                    contentDescription = null,
                    tint = if (entry.hasPrivateKey) AppColors.primary() else AppColors.textSecondary(),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.alias,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = kindLabel(entry),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.textSecondary()
            )
            if (entry.algorithm.isNotBlank()) {
                FieldRow(Str.get(R.string.keystore_algorithm), entry.algorithm)
            }
            entry.subject?.let { FieldRow(Str.get(R.string.keystore_subject), it) }
            entry.issuer?.let { FieldRow(Str.get(R.string.keystore_issuer), it) }
            if (entry.notBefore != null && entry.notAfter != null) {
                FieldRow(
                    Str.get(R.string.keystore_validity),
                    "${formatDate(entry.notBefore)} - ${formatDate(entry.notAfter)}",
                    mono = true
                )
            }
            entry.fingerprint?.let {
                FieldRow(Str.get(R.string.keystore_fingerprint), it, mono = true)
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, mono: Boolean = false) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // p.txt 第 6 条（用户）：密钥库的每条信息**点一行复制一行**。
            // 原先只有指纹旁边那颗小按钮能复制，算法/主题/签发者都得手选。
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                AppToast.success(context, Str.get(R.string.keystore_field_copied))
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textHint()
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // q.txt 第 5 条（用户）：去掉单条信息右侧的复制图标 —— 整行都可点，
        // 图标反而像「只有图标能复制」。单击复制的行为保留在整行的 clickable 上。
    }
}

private fun kindLabel(entry: KeystoreEntryInfo): String = when (entry.kind) {
    EntryKind.PRIVATE_KEY -> Str.get(
        if (entry.hasPrivateKey) R.string.keystore_private_key
        else R.string.keystore_private_key_locked
    )
    EntryKind.SECRET_KEY -> Str.get(R.string.keystore_secret_key)
    EntryKind.TRUSTED_CERT -> Str.get(R.string.keystore_trusted_cert)
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

private fun formatDate(millis: Long?): String =
    if (millis == null || millis <= 0L) "-"
    else try {
        dateFormat.format(Date(millis))
    } catch (e: Exception) {
        "-"
    }
