package com.UIN.Tool.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.core.update.UpdateChecker
import com.UIN.Tool.core.update.UpdateDownloader
import com.UIN.Tool.domain.model.ReleaseInfo
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.AppToast
import com.UIN.Tool.utils.Str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate

/**
 * B-79：静默更新（每日一次检查 + 下载 + 弹窗状态）从 Activity/组合下沉到 ViewModel。
 *
 * 这样旋转/重建时检查与下载状态不再丢失（原 `remember { mutableStateOf }` 会被重置，
 * 正在下载的进度弹窗也随之消失），且 Activity 不再内嵌更新业务逻辑。
 * checker/downloader 是 App 级单例，监听器随 ViewModel 清除（原 B-58 的组合 onDispose 清理
 * 在这里改为 onCleared，旋转时不会误清）。
 */
class UpdateViewModel : ViewModel() {

    data class UpdateUiState(
        /** 检测到待展示的新版本，null 表示不弹更新框 */
        val pending: ReleaseInfo? = null,
        /** 强制更新：不允许点空白处关闭 */
        val force: Boolean = false,
        /** 是否处于下载进度弹窗 */
        val downloading: Boolean = false,
        /** 下载进度百分比 0..100 */
        val progressPct: Int = 0,
        /** 正在下载的版本号（进度提示用） */
        val downloadingVersion: String = "",
        /** 已下载/总字节数（进度提示用） */
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0
    )

    /** 管理页「检查更新」的回调：单例 listener 归本 VM 独占，手动检查经一次性 handler 分发 */
    interface ManualCheckHandler {
        fun onUpdateAvailable(release: ReleaseInfo, forceUpdate: Boolean)
        /** currentVersion 为空表示来源是 onCheckSuccess(无新版本) 分支 */
        fun onNoUpdate(currentVersion: String)
        fun onFailed(error: String)
    }

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** B-23：持有 downloader 引用，否则「取消」按钮停不掉后台下载 */
    private var downloader: UpdateDownloader? = null

    private var checkStarted = false
    private var manualHandler: ManualCheckHandler? = null

    /**
     * 唯一的检查监听器：静默与手动检查共用（原实现管理页/静默各 setOnUpdateListener 互相覆盖）。
     * 当日额度在联网成功（onCheckSuccess/onNoUpdate）后才写入，离线重试不消耗额度。
     */
    private val updateListener = object : UpdateChecker.OnUpdateListener {
        override fun onCheckStart() {}

        override fun onCheckSuccess(
            releases: List<ReleaseInfo>,
            hasNewer: Boolean,
            forceUpdate: Boolean
        ) {
            val pref = ServiceLocator.getPreferenceManager()
            pref.setLastUpdateCheckDay(LocalDate.now().toEpochDay())
            // 手动检查（manualHandler 非空）只回手动回调，避免管理页与全局弹窗重复弹
            if (manualHandler == null && hasNewer && releases.isNotEmpty()) {
                val latest = releases.first()
                if (forceUpdate || latest.versionName != pref.getIgnoredVersion()) {
                    pref.setLastChangelog(latest.releaseNotes ?: "")
                    _uiState.value = _uiState.value.copy(pending = latest, force = forceUpdate)
                }
            }
            manualHandler?.let { h ->
                manualHandler = null
                if (hasNewer && releases.isNotEmpty()) {
                    h.onUpdateAvailable(releases.first(), forceUpdate)
                } else {
                    h.onNoUpdate("")
                }
            }
        }

        override fun onCheckFailed(error: String) {
            manualHandler?.let { h -> manualHandler = null; h.onFailed(error) }
        }

        override fun onNoUpdate(currentVersion: String) {
            ServiceLocator.getPreferenceManager()
                .setLastUpdateCheckDay(LocalDate.now().toEpochDay())
            manualHandler?.let { h -> manualHandler = null; h.onNoUpdate(currentVersion) }
        }
    }

    /** 每次发起检查前重新挂上监听器（可能被 Splash 的 clearUpdateListener 清掉） */
    private fun ensureRegistered() {
        ServiceLocator.getUpdateChecker().setOnUpdateListener(updateListener)
    }

    /** 每日静默检查一次 */
    fun checkSilentlyIfNeeded() {
        if (checkStarted) return
        checkStarted = true
        try {
            val pref = ServiceLocator.getPreferenceManager()
            if (pref.getLastUpdateCheckDay() == LocalDate.now().toEpochDay()) return
            ensureRegistered()
            ServiceLocator.getUpdateChecker().checkUpdate()
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.update_check_failed), e)
        }
    }

    /** 管理页手动检查（带一次性回调），正在检查时忽略重复调用 */
    fun checkManually(handler: ManualCheckHandler) {
        if (manualHandler != null) return
        manualHandler = handler
        try {
            ensureRegistered()
            ServiceLocator.getUpdateChecker().checkUpdate()
        } catch (e: Exception) {
            manualHandler = null
            handler.onFailed(e.message ?: Str.get(R.string.update_check_failed))
        }
    }

    /** 非强更时点空白处关闭弹窗 */
    fun dismissPending() {
        if (!_uiState.value.force) {
            _uiState.value = _uiState.value.copy(pending = null)
        }
    }

    /**
     * 开始下载，成功后直接拉起安装。
     * @param target 缺省下载静默检测到的 pending 版本；管理页手动检查后传其 release
     */
    fun startDownload(context: Context, target: ReleaseInfo? = null) {
        val release = target ?: _uiState.value.pending ?: return
        _uiState.value = _uiState.value.copy(
            pending = null,
            downloading = true,
            progressPct = 0,
            downloadingVersion = release.versionName,
            downloadedBytes = 0,
            totalBytes = 0
        )

        val appContext = context.applicationContext
        val d = ServiceLocator.getUpdateDownloader()
        downloader = d
        d.setOnDownloadListener(object : UpdateDownloader.OnDownloadListener {
            override fun onStart() {}

            override fun onProgress(progress: Int, downloaded: Long, total: Long) {
                _uiState.value = _uiState.value.copy(
                    progressPct = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }

            override fun onSuccess(file: File) {
                _uiState.value = _uiState.value.copy(downloading = false)
                d.installApk(file)
            }

            override fun onFailed(error: String) {
                _uiState.value = _uiState.value.copy(downloading = false)
                AppToast.error(appContext, Str.get(R.string.download_failed_error, error))
            }
        })
        d.startDownload(release.downloadUrl, release.versionName, release.sha256)
    }

    /** B-24：走探测到的镜像打开发布页，不再硬编码 GitHub 直连地址 */
    fun openReleasePage(context: Context) {
        val target = _uiState.value.pending ?: return
        _uiState.value = _uiState.value.copy(pending = null)
        try {
            val url = ServiceLocator.getUpdateChecker().getReleasePageUrl(target.tagName)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            AppLog.e(TAG, Str.get(R.string.failed_to_open_browser), e)
        }
    }

    /** 记住「忽略此版本」并关闭弹窗 */
    fun ignoreCurrent() {
        val target = _uiState.value.pending ?: return
        ServiceLocator.getPreferenceManager().setIgnoredVersion(target.versionName)
        _uiState.value = _uiState.value.copy(pending = null)
    }

    /** B-23：真正取消后台下载（原实现只关弹窗，下载照跑完照样拉安装） */
    fun cancelDownload() {
        downloader?.cancelDownload()
        downloader = null
        _uiState.value = _uiState.value.copy(downloading = false)
    }

    override fun onCleared() {
        // B-58：单例 listener 必须随持有方销毁清掉，否则 Activity 已销毁仍回调
        ServiceLocator.getUpdateChecker().clearUpdateListener()
        ServiceLocator.getUpdateDownloader().clearDownloadListener()
        downloader = null
        super.onCleared()
    }

    private companion object {
        private const val TAG = "UpdateViewModel"
    }
}
