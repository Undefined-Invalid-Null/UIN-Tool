package com.UIN.Tool.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.R
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.local.PreferenceManager
import com.UIN.Tool.data.remote.MirrorManager
import com.UIN.Tool.domain.model.MirrorItem
import com.UIN.Tool.ui.components.unified.*
import com.UIN.Tool.utils.AppLog
import com.UIN.Tool.utils.Str
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * F-05：GitHub 镜像页状态 + 加载/导入/导出/测速协程收进 ViewModel。
 *
 * 原先 9 个 `remember { mutableStateOf }` 与 4 个手写 `scope.launch` 都活在组合里，
 * 旋转/重建即丢失（下拉刷新时间、测试结果、正在导入的中间态全部重来），
 * 且这些逻辑无法脱离 UI 复用。组合侧仍用 `var mirrors by vm.mirrorsState` 委托读写，
 * 页面内所有赋值点零改动。
 *
 * 镜像列表每次进入页面都会 `loadMirrors()` 重新读偏好（与原 LaunchedEffect 语义一致），
 * 覆盖 VM 持有期间其他页面写入的配置。
 */
class GitHubMirrorViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val preferenceManager: PreferenceManager = ServiceLocator.getPreferenceManager()
    private val mirrorManager: MirrorManager = ServiceLocator.getMirrorManager()

    val mirrorsState: MutableState<List<MirrorItem>> = mutableStateOf(emptyList())
    val enabledMirrorsState: MutableState<Set<String>> = mutableStateOf(emptySet())
    val useCdnState: MutableState<Boolean> = mutableStateOf(true)
    val isLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val showResetDialogState: MutableState<Boolean> = mutableStateOf(false)
    val showAddDialogState: MutableState<Boolean> = mutableStateOf(false)
    val showTestResultState: MutableState<String?> = mutableStateOf(null)
    val isRefreshingState: MutableState<Boolean> = mutableStateOf(false)
    val lastRefreshTimeState: MutableState<String?> = mutableStateOf(null)

    private val defaultMirrors: List<MirrorItem> = Constants.DEFAULT_MIRRORS.map { url ->
        MirrorItem(
            name = url.substringAfter("//").substringBefore("."),
            url = url,
            isDefault = true,
            remark = when {
                url.contains("fastgit") -> Str.get(R.string.fast_domestic_mirror)
                url.contains("ghproxy") -> Str.get(R.string.proxy_acceleration)
                url.contains("moeyy") -> Str.get(R.string.domestic_mirror)
                else -> ""
            }
        )
    }

    fun loadMirrors() {
        mirrorsState.value = defaultMirrors
        val enabled = preferenceManager.getEnabledMirrors()
        enabledMirrorsState.value = if (enabled.isNotEmpty()) enabled.toSet()
        else defaultMirrors.take(3).map { it.url }.toSet()
        useCdnState.value = preferenceManager.isUseCdn()
    }

    /** 下拉刷新：重读配置并记录刷新时间 */
    fun refresh() {
        viewModelScope.launch {
            isRefreshingState.value = true
            loadMirrors()
            delay(400)
            lastRefreshTimeState.value = currentTimeString()
            isRefreshingState.value = false
        }
    }

    fun importMirrors(uri: Uri) {
        viewModelScope.launch {
            try {
                isLoadingState.value = true
                val content = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (content.isNullOrEmpty()) {
                    showTestResultState.value = Str.get(R.string.file_is_empty)
                    return@launch
                }
                var count = 0
                content.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        mirrorManager.parseMirrorFromString(trimmed)?.let {
                            mirrorsState.value = mirrorsState.value + it
                            count++
                        }
                    }
                }
                showTestResultState.value = Str.get(R.string.imported_count_mirror_s, count)
                AppLog.success(TAG, Str.get(R.string.imported_count_mirror_s_2, count))
            } catch (e: Exception) {
                showTestResultState.value = Str.get(R.string.import_failed_e_message, e.message)
                AppLog.e(TAG, Str.get(R.string.failed_to_import_mirrors), e)
            } finally {
                isLoadingState.value = false
            }
        }
    }

    fun exportMirrors(uri: Uri) {
        try {
            val content = StringBuilder()
            content.append(Str.get(R.string.github_mirror_list_n_format_name_url))
            mirrorsState.value.forEach {
                content.append("${it.name}|${it.url}")
                if (it.remark.isNotEmpty()) content.append("|${it.remark}")
                content.append("\n")
            }
            ctx.contentResolver.openOutputStream(uri)?.write(content.toString().toByteArray())
            showTestResultState.value = Str.get(R.string.export_successful)
            AppLog.success(TAG, Str.get(R.string.exported_mirror_list))
        } catch (e: Exception) {
            showTestResultState.value = Str.get(R.string.export_failed_e_message, e.message)
            AppLog.e(TAG, Str.get(R.string.failed_to_export_mirrors), e)
        }
    }

    fun testAllMirrors() {
        viewModelScope.launch {
            try {
                isLoadingState.value = true
                showTestResultState.value = Str.get(R.string.testing_mirrors)
                val tested = mirrorManager.testMirrors(mirrorsState.value)
                mirrorsState.value = tested
                val reachableCount = tested.count { it.reachable == true }
                showTestResultState.value =
                    Str.get(R.string.test_complete_reachablecount_tested_, reachableCount, tested.size)
                AppLog.success(TAG, Str.get(R.string.test_complete))
            } catch (e: Exception) {
                showTestResultState.value = Str.get(R.string.test_failed_e_message, e.message)
                AppLog.e(TAG, Str.get(R.string.failed_to_test_mirrors), e)
            } finally {
                isLoadingState.value = false
            }
        }
    }

    private companion object {
        private const val TAG = "GitHubMirrorViewModel"
    }
}
