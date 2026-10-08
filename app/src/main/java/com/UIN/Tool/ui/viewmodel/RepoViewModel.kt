// app/src/main/java/com/UIN/Tool/ui/viewmodel/RepoViewModel.kt
package com.UIN.Tool.ui.viewmodel

import com.UIN.Tool.R
import com.UIN.Tool.utils.Str
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.remote.GitHubApiService
import com.UIN.Tool.domain.model.RepoPluginInfo
import com.UIN.Tool.domain.model.SourceInfo
import com.UIN.Tool.domain.repository.IPluginRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.constants.AppConstants as Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class RepoViewModel : ViewModel() {

    companion object {
        private const val TAG = "RepoViewModel"
    }

    private val _uiState = MutableStateFlow(RepoUiState())
    val uiState: StateFlow<RepoUiState> = _uiState.asStateFlow()

    private val _plugins = MutableStateFlow<List<RepoPluginInfo>>(emptyList())
    val plugins: StateFlow<List<RepoPluginInfo>> = _plugins.asStateFlow()

    private val _filteredPlugins = MutableStateFlow<List<RepoPluginInfo>>(emptyList())
    val filteredPlugins: StateFlow<List<RepoPluginInfo>> = _filteredPlugins.asStateFlow()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val _sources = MutableStateFlow<List<SourceInfo>>(emptyList())
    val sources: StateFlow<List<SourceInfo>> = _sources.asStateFlow()

    private val _selectedSourceId = MutableStateFlow<String?>(null)
    val selectedSourceId: StateFlow<String?> = _selectedSourceId.asStateFlow()

    // D-42①：安装/更新派生态外移——按 pluginId 索引，不再混入 RepoPluginInfo
    private val _installedMeta = MutableStateFlow<Map<String, InstalledMeta>>(emptyMap())
    val installedMeta: StateFlow<Map<String, InstalledMeta>> = _installedMeta.asStateFlow()

    private var allPlugins: List<RepoPluginInfo> = emptyList()
    private lateinit var context: Context
    private lateinit var pluginRepository: IPluginRepository
    private lateinit var pluginManager: PluginManager

    private lateinit var gitHubApiService: GitHubApiService

    fun init(context: Context) {
        this.context = context
        this.pluginRepository = ServiceLocator.getPluginRepository()
        this.pluginManager = ServiceLocator.getPluginManager()
        // D-29：GitHubApiService/OkHttpClient 改由容器提供，不再自建第三套连接池与缓存
        this.gitHubApiService = ServiceLocator.getGitHubApiService()
    }

    fun loadPlugins() {
        viewModelScope.launch {
            try {
                // F-14：开始加载即清掉上一次错误，否则横幅永不消失
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                Logger.i(TAG, Str.get(R.string.start_loading_plugin_list))

                val sources = loadSources()
                _sources.value = sources

                val enabledSources = sources.filter { it.enabled }
                if (enabledSources.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = Str.get(R.string.no_enabled_sources)
                    )
                    return@launch
                }

                val plugins = withContext(Dispatchers.IO) {
                    gitHubApiService.fetchAllSourcesPlugins(enabledSources)
                }

                // D-42①：安装/更新态改存 meta map（model 元素保持纯净，不再 copy 字段）
                _installedMeta.value = buildInstalledMeta(plugins)
                allPlugins = plugins
                _plugins.value = plugins
                _filteredPlugins.value = plugins

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pluginCount = plugins.size,
                    lastRefreshTime = System.currentTimeMillis(),
                    // D-31①：部分源失败也要上浮（原先只有全挂抛异常才提示）
                    error = gitHubApiService.lastFetchFailures
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString("; ")
                        ?.let { Str.get(R.string.source_list_load_failed, it) }
                )
                Logger.i(TAG, "Loaded ${plugins.size} plugins from ${enabledSources.size} sources")
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_load_plugins), e)
                // F-14/D-31：失败上浮到 UI 状态（原先只有日志 → 列表空、无任何提示）
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = Str.get(R.string.source_list_load_failed, e.message ?: e.toString())
                )
            }
        }
    }

    /** F-14：清除安装/下载失败提示（用户点击「知道了」） */
    fun clearInstallError() {
        _downloadProgress.value = _downloadProgress.value.copy(error = null)
    }

    fun searchPlugins(keyword: String) {
        val filtered = if (keyword.isEmpty()) {
            filterBySource(allPlugins)
        } else {
            val lowerKeyword = keyword.lowercase()
            filterBySource(allPlugins).filter { plugin ->
                plugin.name.lowercase().contains(lowerKeyword) ||
                plugin.pluginId.lowercase().contains(lowerKeyword) ||
                plugin.description.lowercase().contains(lowerKeyword) ||
                plugin.author.lowercase().contains(lowerKeyword)
            }
        }
        _filteredPlugins.value = filtered
        Logger.d(TAG, "Search filtered to ${filtered.size} plugins")
    }

    fun selectSource(sourceId: String?) {
        _selectedSourceId.value = sourceId
        searchPlugins("")
    }

    private fun filterBySource(plugins: List<RepoPluginInfo>): List<RepoPluginInfo> {
        val selectedId = _selectedSourceId.value
        return if (selectedId == null) plugins
        else plugins.filter { it.sourceId == selectedId }
    }

    private fun buildInstalledMeta(plugins: List<RepoPluginInfo>): Map<String, InstalledMeta> {
        val installedMap = pluginManager.plugins.value.associateBy { it.pluginId }

        val meta = plugins.mapNotNull { plugin ->
            val installed = installedMap[plugin.pluginId] ?: return@mapNotNull null
            val remoteVersion = plugin.version.toIntOrNull() ?: 0
            plugin.pluginId to InstalledMeta(
                isInstalled = true,
                installedVersion = installed.version,
                hasUpdate = remoteVersion > 0 && remoteVersion > installed.version
            )
        }.toMap()

        val updateCount = meta.values.count { it.hasUpdate }
        if (updateCount > 0) {
            Logger.i(TAG, "Found $updateCount plugin(s) with available updates")
        }
        return meta
    }

    fun downloadAndInstall(plugin: RepoPluginInfo) {
        if (_downloadProgress.value.isDownloading) return

        viewModelScope.launch {
            try {
                _downloadProgress.value = DownloadProgress(
                    isDownloading = true,
                    pluginId = plugin.pluginId,
                    progress = 0
                )

                val file = withContext(Dispatchers.IO) {
                    downloadFile(plugin.downloadUrl, plugin.pluginId) { progress ->
                        _downloadProgress.value = _downloadProgress.value.copy(progress = progress)
                    }
                }

                if (file != null) {
                    val installedInfo = withContext(Dispatchers.IO) {
                        pluginRepository.installPlugin(file.absolutePath)
                    }
                    if (installedInfo != null) {
                        val info = installedInfo.copy(sourceId = plugin.sourceId)
                        Logger.success(TAG, Str.get(R.string.installed_info_name, info.name))
                        pluginRepository.refreshPlugins()
                    } else {
                        _downloadProgress.value = _downloadProgress.value.copy(
                            error = Str.get(R.string.install_failed)
                        )
                    }
                } else {
                    _downloadProgress.value = _downloadProgress.value.copy(
                        error = Str.get(R.string.download_failed)
                    )
                }

                // F-14：只置 isDownloading，保留刚写入的失败原因
                //（原先无条件新建 DownloadProgress 会把 error 立即抹掉 → 失败静默）
                _downloadProgress.value = _downloadProgress.value.copy(isDownloading = false)
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.download_and_install_failed), e)
                _downloadProgress.value = DownloadProgress(
                    isDownloading = false,
                    error = Str.get(R.string.download_failed_error, e.message ?: e.toString())
                )
            }
        }
    }

    private suspend fun downloadFile(
        url: String,
        name: String,
        onProgress: (Int) -> Unit
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "UIN-Tool-Android")
                    .build()

                val response = ServiceLocator.getOkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception(Str.get(R.string.download_failed_response_code, response.code))
                }

                val body = response.body ?: throw Exception(Str.get(R.string.response_body_is_empty))
                val contentLength = body.contentLength()

                val tempFile = File(Constants.TEMP_DIR, "repo_${name}_${System.currentTimeMillis()}.tpk")
                tempFile.parentFile?.mkdirs()

                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L
                        var lastProgress = -1

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val progress = if (contentLength > 0) (downloaded * 100 / contentLength).toInt() else 0
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }

                tempFile
            } catch (e: Exception) {
                Logger.e(TAG, Str.get(R.string.failed_to_download_file), e)
                null
            }
        }
    }

    fun refresh() {
        loadPlugins()
    }

    private fun loadSources(): List<SourceInfo> {
        val sourcesJson = ServiceLocator.getPreferenceManager().getSourcesJson()
        if (sourcesJson.isNotEmpty()) {
            return parseSourcesJson(sourcesJson)
        }
        val defaultJson = Constants.DEFAULT_SOURCES_JSON
        ServiceLocator.getPreferenceManager().setSourcesJson(defaultJson)
        return parseSourcesJson(defaultJson)
    }

    private fun parseSourcesJson(json: String): List<SourceInfo> {
        val sources = mutableListOf<SourceInfo>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                sources.add(SourceInfo(
                    sourceId = obj.optString("sourceId", ""),
                    name = obj.optString("name", ""),
                    owner = obj.optString("owner", ""),
                    repo = obj.optString("repo", ""),
                    branch = obj.optString("branch", "dist"),
                    description = obj.optString("description", ""),
                    trustLevel = obj.optString("trustLevel", "community"),
                    addedAt = obj.optString("addedAt", ""),
                    enabled = obj.optBoolean("enabled", true)
                ))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse sources JSON", e)
        }
        return sources
    }

    /** D-42①：仓库列表项的安装/更新派生态（UI 层状态，不入 domain model） */
    data class InstalledMeta(
        val isInstalled: Boolean = false,
        val installedVersion: Int = 0,
        val hasUpdate: Boolean = false
    )

    data class RepoUiState(
        val isLoading: Boolean = false,
        val pluginCount: Int = 0,
        val error: String? = null,
        val lastRefreshTime: Long = 0
    )

    data class DownloadProgress(
        val isDownloading: Boolean = false,
        val pluginId: String = "",
        val progress: Int = 0,
        val downloaded: Long = 0,
        val total: Long = 0,
        val error: String? = null
    )
}