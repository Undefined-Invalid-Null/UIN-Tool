// app/src/main/java/com/UIN/Tool/ui/viewmodel/ViewModelFactory.kt
package com.UIN.Tool.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ViewModel 工厂 - 支持依赖注入
 * 注意：由于 ViewModel 使用 init(context) 方式注入依赖，
 * 此工厂主要用于扩展，目前直接使用 viewModel() 即可
 */
class ViewModelFactory : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                MainViewModel() as T
            }
            modelClass.isAssignableFrom(RepoViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                RepoViewModel() as T
            }
            modelClass.isAssignableFrom(BackupViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                BackupViewModel(com.UIN.Tool.UinApplication.getAppContext()) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private val INSTANCE by lazy { ViewModelFactory() }

        @JvmStatic
        fun getInstance(): ViewModelFactory = INSTANCE
    }
}