// app/src/main/java/com/UIN/Tool/ui/components/UpdateContent.kt
package com.UIN.Tool.ui.components

import com.UIN.Tool.R
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.utils.Str
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.UIN.Tool.ui.theme.AppColors
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.MarkdownRenderer

/**
 * 统一的 Markdown 变更日志渲染组件。
 *
 * 开屏（全屏）与「检查更新」大弹窗共用此渲染，保证两处外观一致。
 */
@Composable
fun ReleaseChangelog(
    markdown: String,
    modifier: Modifier = Modifier,
    minHeight: Int = 80,
    maxHeight: Int = 250,
    loadFinished: (() -> Unit)? = null
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    defaultTextEncodingName = "UTF-8"
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        AppLog.d("ReleaseChangelog", Str.get(R.string.webview_load_complete))
                        loadFinished?.invoke()
                    }
                }

                val html = MarkdownRenderer.toHtml(markdown)
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp, max = maxHeight.dp)
            .clip(RoundedCornerShape(AppDimens.radiusSmall))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/**
 * 统一的更新信息对话框（「检查更新」大弹窗 / 开屏检测到新版本时弹窗）。
 *
 * 与 [UpdateContent] 展示相同的头信息 + Markdown 变更日志 + 操作按钮。
 */
@Composable
fun UpdateDialog(
    releaseInfo: ReleaseInfo,
    forceUpdate: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onManualDownload: () -> Unit,
    onIgnore: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!forceUpdate) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(AppDimens.cardCornerRadius)),
            colors = CardDefaults.cardColors(
                containerColor = if (AppColors.glassEnabled()) AppColors.glassBackground() else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            UpdateContent(
                releaseInfo = releaseInfo,
                forceUpdate = forceUpdate,
                onDownload = onDownload,
                onManualDownload = onManualDownload,
                onIgnore = onIgnore
            )
        }
    }
}

/**
 * 更新信息的统一内容主体（头信息 + 版本信息 + Markdown 变更日志 + 操作按钮）。
 *
 * 供开屏全屏页与更新弹窗共用，保证两处外观一致。
 */
@Composable
fun UpdateContent(
    releaseInfo: ReleaseInfo,
    forceUpdate: Boolean,
    onDownload: () -> Unit,
    onManualDownload: () -> Unit,
    onIgnore: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (forceUpdate) Icons.Outlined.Warning else Icons.Outlined.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (forceUpdate) Color(0xFFD32F2F) else colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (forceUpdate) Str.get(R.string.mandatory_update) else Str.get(R.string.new_version_found),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (forceUpdate) Color(0xFFD32F2F) else colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = Str.get(R.string.version_releaseinfo_versionname, releaseInfo.versionName),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = Str.get(R.string.version_code_releaseinfo_versioncode, releaseInfo.versionCode),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = Str.get(R.string.size_releaseinfo_getformattedsize, releaseInfo.getFormattedSize()),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = Str.get(R.string.released_releaseinfo_getformatteddat, releaseInfo.getFormattedDate()),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!releaseInfo.releaseNotes.isNullOrEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Str.get(R.string.changelog),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }

            ReleaseChangelog(markdown = releaseInfo.releaseNotes ?: "")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(Str.get(R.string.download_update), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onManualDownload,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Str.get(R.string.manual_download), fontSize = 13.sp)
                }

                if (!forceUpdate) {
                    Button(
                        onClick = onIgnore,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFD32F2F)
                        ),
                        shape = RoundedCornerShape(AppDimens.buttonCornerRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Str.get(R.string.not_now), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
