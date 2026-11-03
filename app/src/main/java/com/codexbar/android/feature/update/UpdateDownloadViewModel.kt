package com.codexbar.android.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.BuildConfig
import com.codexbar.android.core.update.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel
class UpdateDownloadViewModel @Inject constructor(
    private val downloads: UpdateDownloadRepository, private val checker: GitHubReleaseUpdateChecker
) : ViewModel() {
    internal val state = downloads.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateDownloadState())
    private val mutablePending = MutableStateFlow(false)
    internal val pending = mutablePending.asStateFlow()
    private val mutableError = MutableStateFlow<UpdateFailure?>(null)
    internal val error = mutableError.asStateFlow()

    internal fun start(update: AvailableUpdate) = perform {
        downloads.start(update.artifact ?: throw UpdateDownloadException(UpdateFailure.UNAVAILABLE))
    }

    internal fun retry() = perform {
        val result = checker.checkForUpdates(BuildConfig.VERSION_NAME)
        val artifact = (result as? UpdateCheckResult.Available)?.update?.artifact
            ?: throw UpdateDownloadException(UpdateFailure.UNAVAILABLE)
        downloads.start(artifact)
    }

    internal fun cancel() = perform { downloads.cancel() }
    internal suspend fun installerIntent(id: UUID) = downloads.installerIntent(id)

    private fun perform(action: suspend () -> Unit) {
        if (mutablePending.value) return
        mutablePending.value = true
        mutableError.value = null
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableError.value = (error as? UpdateDownloadException)?.reason ?: UpdateFailure.UNAVAILABLE }
            finally { mutablePending.value = false }
        }
    }
}
