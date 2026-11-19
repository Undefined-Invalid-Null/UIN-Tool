// app/src/main/java/com/UIN/Tool/ui/screen/files/ApkInfoActivity.kt
package com.UIN.Tool.ui.screen.files

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.UIN.Tool.ui.components.unified.UnifiedIconButton
import com.android.apksig.ApkVerifier
import com.UIN.Tool.R
import com.UIN.Tool.ui.theme.UINToolTheme
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import com.UIN.Tool.utils.openFile
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APK 信息页（§4.6）：包名 / 版本 / 应用名 / 图标 / 权限 / 签名证书 + 安装 + 分享。
 *
 * 全部信息来自 [PackageManager.getPackageArchiveInfo] —— 它读的是 **APK 里的清单**，
 * 不需要安装、不需要 root、也不需要解析二进制 XML。签名同理，取的是 APK 自带的证书，
 * 因此**不需要任何存储权限**。
 *
 * ## 一个必须知道的坑
 *
 * 档案 APK 的 [ApplicationInfo] 里 `sourceDir` 是空的，直接 `loadIcon` 拿到的是**系统默认
 * 图标**。必须先把 `sourceDir` / `publicSourceDir` 指回 APK 自身，`loadIcon` 才会去那个
 * 文件里找图标。这不是可选优化 —— 不做的话「图标」那一栏永远是个灰色机器人。
 */
class ApkInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            // g7：窗口是透明的（Theme.UIN.Tool.Dialog），主题**不能再铺满底色**，
            // 否则那层底色又把上一个界面挡住，弹窗观感前功尽弃。
            UINToolTheme(fillBackground = false, syncWindowBars = false) {
                ApkInfoScreen(path = path, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"

        fun start(context: Context, path: String) {
            context.startActivity(
                Intent(context, ApkInfoActivity::class.java).putExtra(EXTRA_PATH, path)
            )
        }
    }
}

/** 一张证书的可展示信息。指纹按**整个证书 DER 编码**算，与 `keytool -printcert` 一致。 */
private data class CertInfo(
    val subject: String,
    val issuer: String,
    val sha256: String
)

/**
 * apksig 的密码学校验结论（用户答复 12：**apksig 签名验证**）。
 *
 * [PackageManager] 只是把包里的签名**读出来**，从不校验它对不对 —— 把内容改掉、
 * 甚至把 v2 签名块整个抹掉，它照样给你一张证书。[verified] 是拿公钥比对过 APK 内容摘要
 * 之后的结论，[schemes] 是验通过的那几套方案（v1 / v2 / v3…），
 * [errors] 是没通过时 apksig 给出的具体原因（原文，未翻译：那是一句精确诊断）。
 *
 * `null` = 这一步压根没跑成，与「验了没通过」是两回事，页面上分开说话。
 */
private data class SignVerify(
    val verified: Boolean,
    val schemes: List<String>,
    val errors: List<String>
)

private data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val appName: String,
    val icon: Drawable?,
    val minSdk: Int,
    val targetSdk: Int,
    val size: Long,
    val permissions: List<String>,
    val certificates: List<CertInfo>,
    val sign: SignVerify?,
    /**
     * 加壳特征命中的厂商（反馈 e1「信息页无加壳检测」）。空 = **没认出来** ——
     * 不是「确定没加固」，页面上按这个措辞说。
     */
    val packers: List<String> = emptyList(),
    /** 本机已装同包名的 versionCode；null = 本机没装（反馈 e1「不与设备已装版本对比」）。 */
    val installedVersionCode: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApkInfoScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = File(path)

    // 重签一次 reloadKey 就 +1：证书、指纹、校验结论**全都换了**，不是只换一行 ——
    // 整页重读，别在旧结论上打补丁
    var reloadKey by remember { mutableStateOf(0) }
    var resigning by remember { mutableStateOf(false) }
    // 是否已完成一次读取（成功或失败）。空值 + loaded=false 才是「读取中」
    var loaded by remember { mutableStateOf(false) }

    val state by produceState<ApkInfo?>(initialValue = null, key1 = path, key2 = reloadKey) {
        loaded = false
        value = withContext(Dispatchers.IO) { readApkInfo(context.applicationContext, file) }
        loaded = true
    }

    // 读不出清单的 APK（损坏 / 加壳工具把清单挪走 / 非 APK 改名）：
    // 直接说读不了，并把「安装」也收起来 —— 让用户对着空信息页去点安装是添乱。
    // 必须用 loaded 区分「还在读」和「读完但为 null」：只看 state==null 时，
    // 非 APK 文件会永远停在「正在读取应用信息…」（h.txt 第 9 条）。
    val info = state

    // j.txt 第 6 条：读取期间给加载动画。i.txt 第 6 条担心的是「秒开的文件先闪一张
    // 加载卡再切换」—— 这里用 180ms 延迟兼顾两者：读得快就一次都不画，真的在等才出现。
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (!loaded) {
            delay(180)
            if (!loaded) showLoading = true
        }
    }

    // i.txt 第 6 条：退出动画与 Compose 弹窗对齐。原来这个 Activity 是「啪」地消失，
    // 与其它 Dialog 的「遮罩淡出 + 卡片缩回」完全是两套观感。这里自己播一遍再 finish。
    var closing by remember { mutableStateOf(false) }
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    val close: () -> Unit = { if (!closing) closing = true }
    LaunchedEffect(closing) {
        if (closing) {
            appear.animateTo(0f, tween(durationMillis = 140))
            onBack()
        } else {
            appear.animateTo(1f, tween(durationMillis = 180))
        }
    }

    // 读不出清单的 APK（损坏 / 加壳工具把清单挪走 / 非 APK 改名）：
    // 直接说读不了，并把「安装」也收起来 —— 让用户对着空信息页去点安装是添乱。
    // 必须用 loaded 区分「还在读」和「读完但为 null」：只看 state==null 时，
    // 非 APK 文件会永远停在「正在读取应用信息…」（h.txt 第 9 条）。
    if (state == null && loaded) {
        ApkInfoFailed(appear = appear.value, onClose = close)
        return
    }

    BackHandler(onBack = close)

    // g7：整页 → **弹窗**。入口（文件列表 / 打开方式 / 属性框）一律还是
    // `ApkInfoActivity.start`，但窗口里画的是居中卡片 + 遮罩：点空隙返回、
    // 卡片内独立滚动。这样调用点一处都不用改，观感与其它弹窗一致。
    Box(
        modifier = Modifier
            .fillMaxSize()
            // i.txt 第 6 条：遮罩跟着动画淡入淡出（与弹窗 exit 的观感对齐）。
            .graphicsLayer { alpha = appear.value }
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                indication = null,
                onClick = close
            ),
        contentAlignment = Alignment.Center
    ) {
        // i.txt 第 6 条：非 APK 在「读完清单」之前**不先画信息卡** —— 读失败时那张卡
        // 会瞬间换成失败提示，看着像闪屏。j.txt 第 6 条要在等待时给加载动画，
        // 因此这里只画一张**独立的小加载卡**（180ms 后才出现），信息卡仍然等 loaded。
        if (!loaded) {
            if (showLoading) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(0.62f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = Str.get(R.string.apk_info_reading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return@Box
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                // 与 Compose Dialog 的 scaleIn/scaleOut 同向：0.9 → 1.0。
                .graphicsLayer {
                    val s = 0.9f + 0.1f * appear.value
                    scaleX = s
                    scaleY = s
                }
                .clickable(
                    interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    UnifiedIconButton(
                        icon = Icons.Default.Close,
                        onClick = close,
                        contentDescription = Str.get(R.string.close),
                    )
                }
        if (info == null) {
            // 读取中：给一句占位，别让卡片空着像崩了
            Text(
                text = Str.get(R.string.apk_info_reading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- 头部：图标 + 应用名 + 包名 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = info.icon
                if (icon != null) {
                    // toBitmap 明确尺寸：AdaptiveIconDrawable 的 intrinsic 在某些 ROM 上是 -1，
                    // 直接 asImageBitmap 会崩。128dp 足够清晰又不吃内存。
                    val bitmap = remember(icon) {
                        runCatching { icon.toBitmap(128, 128).asImageBitmap() }.getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                }
                Column {
                    Text(info.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = info.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 基本信息 ----
            InfoCard {
                InfoRow(Str.get(R.string.apk_info_version), info.versionName)
                InfoRow(Str.get(R.string.apk_info_version_code), info.versionCode.toString())
                // 升降级提示（反馈 e1「不与设备已装版本对比」）：装不装得上，看的是
                // **这个包**跟本机那版谁大 —— 而不是版本名好不好看
                InfoRow(Str.get(R.string.apk_info_vs_installed), vsInstalled(info))
                InfoRow(Str.get(R.string.apk_info_package), info.packageName)
                InfoRow(Str.get(R.string.apk_info_size), FileOps.formatSize(info.size))
                InfoRow(Str.get(R.string.apk_info_min_sdk), info.minSdk.toString())
                InfoRow(Str.get(R.string.apk_info_target_sdk), info.targetSdk.toString())
                // 加壳检测（反馈 e1「信息页无加壳检测」）。措辞是「疑似」：
                // 认不出的壳照样报「未检测到」
                InfoRow(
                    Str.get(R.string.apk_info_packer),
                    if (info.packers.isEmpty()) {
                        Str.get(R.string.apk_info_packer_none)
                    } else {
                        Str.get(
                            R.string.apk_info_packer_found,
                            info.packers.joinToString(", ")
                        )
                    }
                )
            }

            // ---- 权限 ----
            // ---- 权限（h.txt 第 4 条：默认收拢；点/长按任一权限名复制） ----
            InfoCard {
                CollapsibleSection(
                    title = Str.get(R.string.apk_info_permissions, info.permissions.size)
                ) {
                    if (info.permissions.isEmpty()) {
                        Text(
                            text = Str.get(R.string.apk_info_no_permissions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // 权限名通常很长（android.permission.xxx），允许换行而不是横向滚动 ——
                        // 屏幕就这么大，横向滚动藏信息不如纵向排开。
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            info.permissions.forEach { p ->
                                CopyableValueLine(text = p, secondary = true)
                            }
                        }
                    }
                }
            }

            // ---- 签名证书（h.txt 第 4 条：默认收拢；证书各字段可复制） ----
            InfoCard {
                CollapsibleSection(title = Str.get(R.string.apk_info_certificate)) {
                    // 校验结论摆在证书**上面**：证书只说明「包里写着谁签的」，
                    // 结论才说明「验过没有」—— 顺序反了，人会先看到一张可信的脸
                    SignVerifyLine(info.sign)
                    if (info.certificates.isEmpty()) {
                        Text(
                            text = Str.get(R.string.apk_info_no_certificate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        info.certificates.forEachIndexed { index, cert ->
                            if (index > 0) Spacer(Modifier.height(8.dp))
                            CopyableValueLine(text = cert.subject)
                            CopyableValueLine(
                                text = Str.get(R.string.apk_info_issuer, cert.issuer),
                                secondary = true
                            )
                            CopyableValueLine(
                                text = Str.get(R.string.apk_info_sha256, cert.sha256),
                                secondary = true
                            )
                        }
                    }
                }
            }

            // ---- 操作 ----
            // 重签（用户反馈 e15 **P1-11**「改 APK 后重签名」）。
            // 只在**确实验过、且没过**时才摆：
            // - 签名有效 → 摆它是纯破坏（重签会把签名换掉，用户白白丢掉覆盖安装的能力）；
            // - 压根没验成（sign == null）→ 更不能摆 —— 那是「没测」，不是「坏了」，
            //   摆一个「重签」等于替用户下结论。
            if (info.sign?.verified == false) {
                val scope = rememberCoroutineScope()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UnifiedButton(
                        text = Str.get(R.string.apk_resign),
                        onClick = {
                            if (!resigning) {
                                resigning = true
                                scope.launch(Dispatchers.IO) {
                                    val err = runCatching {
                                        ApkSign.sign(context.applicationContext, file)
                                    }.exceptionOrNull()
                                    withContext(Dispatchers.Main) {
                                        resigning = false
                                        if (err == null) {
                                            AppToast.show(
                                                context, Str.get(R.string.apk_resign_done)
                                            )
                                            reloadKey++
                                        } else {
                                            AppToast.show(
                                                context,
                                                Str.get(
                                                    R.string.apk_resign_failed,
                                                    err.message ?: "?"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        variant = ButtonVariant.Warning,
                        enabled = !resigning,
                        loading = resigning,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 换签名这件事必须先说：改过的包**不能覆盖安装**原应用了
                    Text(
                        text = Str.get(R.string.apk_resign_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // n.txt 第 2 条：底部只有「安装 / 查看」两颗，并且**移出滚动区**固定在卡片底部
            //（原先 4 颗通栏按钮排在信息列表末尾，权限/证书一长就要滚到底才能按到）。
            // 分享与复制包名收起：包名行本来就支持长按复制，分享可回文件页右键。
        }
            // n.txt 第 2 条：固定底栏。放在纵向滚动之外，列表怎么滚都不动。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnifiedButton(
                    text = Str.get(R.string.apk_info_install),
                    onClick = { installApk(context, file) },
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
                // i.txt 第 6 条：APK 本身就是一个 zip —— 不想装、只想翻里面的
                // classes.dex / 资源 / so，直接在文件页里当压缩包挂起来看。
                UnifiedButton(
                    text = Str.get(R.string.apk_info_view_archive_short),
                    onClick = {
                        openAsArchive(context, file)
                        close()
                    },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.weight(1f)
                )
            }
        } // info != null
            }
        }
    }
}

@Composable
private fun ApkInfoFailed(appear: Float, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    // g7：失败态也走同一套「遮罩 + 居中卡片」——窗口是透明的，
    // 这里若直接铺满一列，看起来仍是独立页面，与成功态两张脸。
    // i.txt 第 6 条：与成功态共用同一套进出动画（遮罩淡入淡出 + 卡片缩放）。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear }
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    val s = 0.9f + 0.1f * appear
                    scaleX = s
                    scaleY = s
                }
                .clickable(
                    interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Str.get(R.string.apk_info_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                UnifiedButton(text = Str.get(R.string.back), onClick = onClose)
            }
        }
    }
}

/** 一张浅色信息卡：页面里所有分区都用它，避免每块各画各的边框。 */
@Composable
// 注意是 ColumnScope 而不是 Column：Column 是**函数**，没有对应类型，
// 用它当接收者类型会直接「Unresolved reference 'Column'」。
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

/**
 * 签名校验结论一行（apksig）。三态各有各的话，**谁也不许替谁说话**：
 * 验过 = 通过 + 用了哪几套方案；验了没过 = 结论 + 第一条原因；
 * 压根没跑成 = 「未能验证」—— 把「没测」说成「没通过」是编结果。
 */
@Composable
private fun SignVerifyLine(sign: SignVerify?) {
    val color = when {
        sign == null -> MaterialTheme.colorScheme.onSurfaceVariant
        sign.verified -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val text = when {
        sign == null -> Str.get(R.string.apk_info_sign_unknown)
        sign.verified -> Str.get(
            R.string.apk_info_sign_ok,
            sign.schemes.joinToString(", ").ifEmpty { Str.get(R.string.apk_info_unknown) }
        )
        else -> Str.get(R.string.apk_info_sign_fail)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
    // 原因用 apksig 的原文摆在下一行：那是一句精确诊断，翻译反而容易失真；
    // 外面套一层「原因：」是为了让人知道它不是正文。最多两条，够定位问题又不刷屏
    if (sign != null && !sign.verified) {
        sign.errors.take(2).forEach { reason ->
            Text(
                text = Str.get(R.string.apk_info_sign_reason, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 属性行：标签 + 值。
 *
 * 用户反馈 d6「排查其他工具属性的长按复制」：这里原先**一行都复制不了** ——
 * 包名、版本名、APK 路径全是长串，看得到抄不走。文件属性框、代码编辑器属性框
 * 早就支持复制了，这里是漏掉的那个。长按复制，点一下也复制：只给长按的话，
 * 轻点那一片就是个「点了没反应」的死区。
 *
 * `internal`：已安装应用页的「安装包信息」弹窗（f4）复用这一行，两处的复制口径不许分叉。
 */
@Composable
internal fun InfoRow(label: String, value: String) {
    val context = LocalContext.current
    // g5：弹窗里的复制反馈不再用系统 Toast —— 弹窗是独立窗口，Snackbar 会被盖住，
    // AppToast 只能退回系统 Toast，与整套 App 的观感不一致（用户点名）。
    // 改成行内就地反馈：值右侧短暂出现「已复制」，不打扰、也不会被窗口层级吃掉。
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1400)
            copied = false
        }
    }
    val copy = {
        copyToClipboard(context, value, showToast = false)
        copied = true
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                enabled = value.isNotBlank(),
                onClick = copy,
                onLongClick = copy
            )
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (copied) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = Str.get(R.string.apk_info_copied),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 可折叠分区（h.txt 第 4 条）：权限 / 签名证书默认收拢，点标题展开。
 * 标题行同时承担「展开/收起」的可访问性描述。
 */
@Composable
private fun CollapsibleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = Str.get(
                    if (expanded) R.string.apk_info_collapse_section
                    else R.string.apk_info_expand_section
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp), content = content)
        }
    }
}

/**
 * 可复制的信息行（权限名 / 证书字段）。点一下、长按都复制，
 * 与 [InfoRow] 同口径，只是没有左侧标签列。
 */
@Composable
private fun CopyableValueLine(text: String, secondary: Boolean = false) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1400)
            copied = false
        }
    }
    val copy = {
        copyToClipboard(context, text, showToast = false)
        copied = true
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = copy, onLongClick = copy)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (secondary) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        if (copied) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = Str.get(R.string.apk_info_copied),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ==================== 读取 ====================

/**
 * 在 IO 线程把 APK 的清单、权限、签名读成 [ApkInfo]。读不出返回 null。
 *
 * `GET_SIGNING_CERTIFICATES` 要 API 28；`signatures` 是它的老身位，28 之后被标成废弃但
 * **对档案 APK 仍然是最可靠的一条路**（部分加壳产物的 `SigningInfo` 会拿不到）。
 * 这里两条都试，谁给得出证书用谁。
 */
private fun readApkInfo(context: Context, file: File): ApkInfo? {
    if (!file.isFile || !file.exists()) return null
    return try {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA or
            PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SIGNING_CERTIFICATES

        // getPackageArchiveInfo 的 (String, int) 重载在 API 33 起被标废弃（新的是 PackageInfoFlags），
        // 但档案 APK 这条老路没有等价的新写法覆盖到我们关心的字段，故保留并显式压掉告警。
        @Suppress("DEPRECATION")
        val pi = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null

        // 关键一步：档案 APK 的 applicationInfo 里 sourceDir 是空的，
        // 不指回来 loadIcon 永远只会给系统默认图标。
        pi.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }

        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }

        val appName = try {
            pi.applicationInfo?.loadLabel(pm)?.toString().orEmpty()
                .ifBlank { pi.packageName }
        } catch (e: Exception) {
            pi.packageName
        }

        val icon = try {
            pi.applicationInfo?.loadIcon(pm)
        } catch (e: Exception) {
            AppLog.w(TAG, "load apk icon failed: ${e.message}")
            null
        }

        val permissions = pi.requestedPermissions?.toList().orEmpty().sorted()

        // 本机装没装、装的哪一版。拿不到（没装）当 null，**别当 0** ——
        // 0 会被读成「版本号 0」，那是一句假话
        val installedCode = try {
            @Suppress("DEPRECATION")
            val ipi = pm.getPackageInfo(pi.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                ipi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                ipi.versionCode.toLong()
            }
        } catch (e: Exception) {
            null
        }

        ApkInfo(
            packageName = pi.packageName.orEmpty(),
            versionName = pi.versionName ?: Str.get(R.string.apk_info_unknown),
            versionCode = versionCode,
            appName = appName,
            icon = icon,
            // ApplicationInfo.getMinSdkVersion() 是 **API 24** 才有的方法（我们 minSdk 23），
            // 直接调会在 23 设备上 NoSuchMethodError。拿不到就显示 0（调用方展示为「未知」语义下的空值）。
            minSdk = if (android.os.Build.VERSION.SDK_INT >= 24) {
                pi.applicationInfo?.minSdkVersion ?: 0
            } else {
                0
            },
            targetSdk = pi.applicationInfo?.targetSdkVersion ?: 0,
            size = file.length(),
            permissions = permissions,
            certificates = readCertificates(pi),
            // 放在最后一项：它要真把包读一遍，是这几项里最贵的
            sign = verifySignature(file),
            // 加壳只看条目名（zip 中央目录），不读文件内容 —— 便宜，
            // 也不会为了下个结论把几十 MB 的代码拖进来
            packers = detectPackers(file),
            installedVersionCode = installedCode
        )
    } catch (e: Exception) {
        AppLog.w(TAG, "read apk info failed: ${e.message}")
        null
    }
}

/**
 * 跟本机已装的那版比一比（反馈 e1「升降级提示」）。
 *
 * 没装就是没装 —— 单给一个说法，别混进「相同」里。降级那句是**系统行为**的实话：
 * Android 不让装比已装版本低的包，与其让人点了才失败，不如先把话说在前面。
 */
private fun vsInstalled(info: ApkInfo): String {
    val installed = info.installedVersionCode ?: return Str.get(R.string.apk_info_vs_none)
    return when {
        info.versionCode > installed -> Str.get(R.string.apk_info_vs_upgrade)
        info.versionCode < installed -> Str.get(R.string.apk_info_vs_downgrade)
        else -> Str.get(R.string.apk_info_vs_same)
    }
}

/**
 * 加壳（加固）特征（反馈 e1「信息页无加壳检测」）。
 *
 * **特征匹配，不是鉴定**：只认得出表里这些厂商留下的文件名，认不出的一律返回空。
 * 所以页面上写「疑似」，不写「已加固」—— 写死了，遇到认不出的壳就是在撒谎。
 *
 * 只看**条目名**（zip 的中央目录），不读文件内容：便宜，也不会因为读了人家
 * dex 里的巧合字符串而误报。
 */
private fun detectPackers(file: File): List<String> {
    val markers = listOf(
        "libjiagu" to "360",
        "assets/jiagu" to "360",
        "libsecexe" to "梆梆",
        "libsec-main" to "梆梆",
        "libdexhelper" to "爱加密",
        "libshella" to "爱加密",
        "libchaosvmp" to "娜迦",
        "libtersafe" to "乐变",
        "libbaiduprotect" to "百度",
        "libmobisec" to "阿里",
        "libnqshield" to "网易"
    )
    return try {
        val hits = LinkedHashSet<String>()
        ZipFile(file).use { zf ->
            for (entry in zf.entries()) {
                val name = entry.name.lowercase()
                for ((marker, vendor) in markers) {
                    if (name.contains(marker)) hits.add(vendor)
                }
            }
        }
        hits.toList()
    } catch (e: Exception) {
        AppLog.w(TAG, "packer scan failed: ${e.message}")
        emptyList()
    }
}

/**
 * 用 apksig 真正**验**一遍签名（用户答复 12）。
 *
 * 返回 null = 这一步没跑成（文件读不了、格式怪），页面退回「未能验证」而不是编一个结论 ——
 * 「没测」和「测出问题」是两回事，混起来既吓人又误导排障。
 *
 * 之所以另起一条路：[PackageManager] 给的证书是**它读到的**，不是**验过的**，
 * 内容被改过的包在它那儿照样显示得下证书。apksig 拿公钥去比对 APK 的内容摘要，
 * 顺带告诉我们用的是哪套方案 —— 那正是「这包在新系统上还能不能装」的依据。
 */
private fun verifySignature(file: File): SignVerify? = try {
    val result = ApkVerifier.Builder(file).build().verify()
    SignVerify(
        verified = result.isVerified,
        // 方案名是国际通用写法（v1/v2/v3），不进翻译表
        schemes = buildList {
            if (result.isVerifiedUsingV1Scheme()) add("v1")
            if (result.isVerifiedUsingV2Scheme()) add("v2")
            if (result.isVerifiedUsingV3Scheme()) add("v3")
            if (result.isVerifiedUsingV31Scheme()) add("v3.1")
            if (result.isVerifiedUsingV4Scheme()) add("v4")
        },
        errors = result.errors.map { it.toString() }
    )
} catch (e: Exception) {
    AppLog.w(TAG, "verify apk signature failed: ${e.message}")
    null
}

/** 取签名证书。API 28+ 先走 [PackageInfo.signingInfo]，拿不到再退回 [PackageInfo.signatures]。 */
private fun readCertificates(pi: PackageInfo): List<CertInfo> {
    val raw = ArrayList<android.content.pm.Signature>()
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        pi.signingInfo?.apkContentsSigners?.let { raw.addAll(it) }
    }
    if (raw.isEmpty()) {
        @Suppress("DEPRECATION")
        pi.signatures?.let { raw.addAll(it) }
    }
    return raw.distinctBy { it.hashCode() }.mapNotNull { sig ->
        try {
            val der = sig.toByteArray()
            val md = MessageDigest.getInstance("SHA-256").digest(der)
            CertInfo(
                subject = sig.parseSubject(),
                issuer = sig.parseIssuer(),
                sha256 = md.joinToString(":") { "%02X".format(it) }
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "parse cert failed: ${e.message}")
            null
        }
    }
}

/**
 * [android.content.pm.Signature] 只给得出 DER 字节，subject/issuer 要自己从 X.509 里解。
 * 走 `keytool`/`jarsigner` 同款的 `java.security.cert.CertificateFactory`，
 * 拿到 [java.security.cert.X509Certificate] 后直接读 subject/issuer 判别名。
 * 解不动（非 X.509、老式签名）就退回调用方给的占位串，不影响其余字段。
 */
private fun android.content.pm.Signature.parseSubject(): String =
    x509OrNull()?.subjectX500Principal?.name ?: Str.get(R.string.apk_info_unknown)

private fun android.content.pm.Signature.parseIssuer(): String =
    x509OrNull()?.issuerX500Principal?.name ?: Str.get(R.string.apk_info_unknown)

private fun android.content.pm.Signature.x509OrNull(): java.security.cert.X509Certificate? = try {
    java.security.cert.CertificateFactory.getInstance("X.509")
        .generateCertificate(toByteArray().inputStream()) as? java.security.cert.X509Certificate
} catch (e: Exception) {
    null
}

// ==================== 操作 ====================

/**
 * i.txt 第 6 条：以压缩包方式查看。
 *
 * APK 本身就是一个 zip，没必要为了翻 `classes.dex` / 资源再解压一遍。这里把请求
 * 投给文件页的深链总线（[FileDeepLinkBus]），由那边把包**挂进活动窗格** ——
 * 与在文件列表里点包进去是同一条路，只是入口多了一个。
 */
private fun openAsArchive(context: Context, file: File) {
    // 既投总线（万一文件页已经在前台，能立刻响应），也显式把主界面拉回来 ——
    // 本页可能从「已安装应用」进来，finish() 只会退回那一页，文件页根本看不见。
    // MainActivity 是 singleTask：这条 uin://files 深链会走 onNewIntent → applyDeepLink，
    // 由它统一切 tab + 挂载归档，不会另起一个实例。
    FileDeepLinkBus.post(
        FileDeepLink(
            dirPath = file.parent ?: "",
            filePath = file.path,
            mountArchive = file.path
        )
    )
    val uri = android.net.Uri.Builder()
        .scheme("uin")
        .authority("files")
        .appendQueryParameter("path", file.parent ?: "")
        .appendQueryParameter("file", file.path)
        .appendQueryParameter("archive", file.path)
        .build()
    val intent = Intent(context, com.UIN.Tool.MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = uri
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { AppLog.w(TAG, "open as archive failed: ${it.message}") }
}

/**
 * 起系统安装器。URI 由 FileProvider 签发（白名单 `res/xml/file_paths.xml`）。
 *
 * 装不了分两种，提示要分开 —— 用户排查方向完全不同：
 * ① 找不到安装器（极少数精简 ROM 砍了 PackageInstaller 入口）；
 * ② 安装器在但拒绝（未开「安装未知应用」/ 版本过低 / 签名冲突）。
 * ② 的具体原因在**安装器的界面里**，本应用拿不到，只能如实告诉用户去看那边。
 */
private fun installApk(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLog.w(TAG, "start installer failed: ${e.message}")
        AppToast.warning(context, Str.get(R.string.apk_info_install_failed))
    }
}

private fun copyToClipboard(context: Context, text: String, showToast: Boolean = true) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    if (cm == null) {
        AppToast.warning(context, Str.get(R.string.apk_info_load_failed))
        return
    }
    cm.setPrimaryClip(android.content.ClipData.newPlainText(text, text))
    // Android 13+ 系统自己会弹「已复制」提示，再弹一次是两遍。
    if (showToast && android.os.Build.VERSION.SDK_INT < 33) {
        AppToast.success(context, Str.get(R.string.apk_info_copied))
    }
}

// 大小文案统一走 [FileOps.formatSize]（转发到 com.UIN.Tool.utils.formatFileSize）。
// 这里**刻意不再复制一份** —— 四处（进度条/冲突框/属性框/下载进度）显示同一个字节数
// 必须长得一样，复制副本迟早会漂移。同包顶层重名还会直接构成重声明编译错。

private const val TAG = "ApkInfo"
