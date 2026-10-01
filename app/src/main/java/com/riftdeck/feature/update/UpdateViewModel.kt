package com.riftdeck.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riftdeck.data.repository.AppUpdateRepository
import com.riftdeck.data.repository.UpdateError
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateViewModel(private val repository: AppUpdateRepository) : ViewModel() {
    val uiState = repository.state

    fun onForeground() = repository.onForeground()
    fun check() = repository.check(manual = true)
    fun setAutoCheck(value: Boolean) = repository.setAutoCheck(value)
    fun download() = repository.download()
    fun cancelDownload() = repository.cancelDownload()
    fun dismissPrompt() = repository.dismissPrompt()
    fun reportInstallError(error: UpdateError) = repository.reportInstallError(error)

    /** Validation runs away from the UI thread; only a validated file reaches the installer. */
    fun prepareInstall(onReady: (File) -> Unit) = viewModelScope.launch {
        val apk = withContext(Dispatchers.IO) { repository.validatedApk() }
        if (apk != null) onReady(apk)
    }

    class Factory(private val repository: AppUpdateRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UpdateViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return UpdateViewModel(repository) as T
        }
    }
}
