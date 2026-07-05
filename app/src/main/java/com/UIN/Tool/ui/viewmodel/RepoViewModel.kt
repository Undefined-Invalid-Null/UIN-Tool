// app/src/main/java/com/UIN/Tool/ui/viewmodel/RepoViewModel.kt
package com.UIN.Tool.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.UIN.Tool.core.di.ServiceLocator
import com.UIN.Tool.data.remote.GitHubApiService
import com.UIN.Tool.domain.model.RepoPluginInfo
import com.UIN.Tool.domain.repository.IPluginRepository
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.PluginManager
import com.UIN.Tool.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

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

    private var allPlugins: List<RepoPluginInfo> = emptyList()
    private lateinit var context: Context
    private lateinit var pluginRepository: IPluginRepository
    private lateinit var pluginManager: PluginManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .cache(okhttp3.Cache(File(Constants.CACHE_DIR, "okhttp_cache"), Constants.CACHE_SIZE))
        .build()

    private lateinit var gitHubApiService: GitHubApiService

    fun init(context: Context) {
        this.context = context
        this.pluginRepository = ServiceLocator.getPluginRepository()
        this.pluginManager = ServiceLocator.getPluginManager()
        this.gitHubApiService = GitHubApiService(client)
    }

    fun loadPlugins() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                Logger.i(TAG, "开始加载插件列表")

                val repos = withContext(Dispatchers.IO) {
                    gitHubApiService.fetchOrgRepos()
                }

                allPlugins = repos
                _plugins.value = repos
                _filteredPlugins.value = repos

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pluginCount = repos.size
                )
                Logger.i(TAG, "已加载 ${repos.size} 个仓库插件")
            } catch (e: Exception) {
                Logger.e(TAG, "加载插件失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun searchPlugins(keyword: String) {
        val filtered = if (keyword.isEmpty()) {
            allPlugins
        } else {
            val lowerKeyword = keyword.lowercase()
            allPlugins.filter { plugin ->
                plugin.name.lowercase().contains(lowerKeyword) ||
                plugin.pluginId.lowercase().contains(lowerKeyword) ||
                plugin.description.lowercase().contains(lowerKeyword) ||
                plugin.author.lowercase().contains(lowerKeyword)
            }
        }
        _filteredPlugins.value = filtered
        Logger.d(TAG, "搜索结果: ${filtered.size} 个插件")
    }

    fun downloadAndInstall(plugin: RepoPluginInfo) {
        if (_downloadProgress.value.isDownloading) {
            return
        }

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
                    val info = withContext(Dispatchers.IO) {
                        pluginRepository.installPlugin(file.absolutePath)
                    }
                    if (info != null) {
                        Logger.success(TAG, "安装成功: ${info.name}")
                        pluginRepository.refreshPlugins()
                    } else {
                        _downloadProgress.value = _downloadProgress.value.copy(
                            error = "安装失败"
                        )
                    }
                }

                _downloadProgress.value = DownloadProgress(isDownloading = false)

            } catch (e: Exception) {
                Logger.e(TAG, "下载安装失败", e)
                _downloadProgress.value = DownloadProgress(
                    isDownloading = false,
                    error = e.message
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

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("下载失败: ${response.code}")
                }

                val body = response.body ?: throw Exception("响应体为空")
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

                            val progress = if (contentLength > 0) {
                                (downloaded * 100 / contentLength).toInt()
                            } else 0

                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }

                tempFile
            } catch (e: Exception) {
                Logger.e(TAG, "下载文件失败", e)
                null
            }
        }
    }

    fun refresh() {
        loadPlugins()
    }

    data class RepoUiState(
        val isLoading: Boolean = false,
        val pluginCount: Int = 0,
        val error: String? = null
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