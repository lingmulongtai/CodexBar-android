package com.codexbar.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.BuildConfig
import com.codexbar.android.core.update.GitHubReleaseUpdateChecker
import com.codexbar.android.core.update.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AppInfoUiState(val checking: Boolean = false, val result: UpdateCheckResult? = null)

@HiltViewModel
class AppInfoViewModel @Inject constructor(private val checker: GitHubReleaseUpdateChecker) : ViewModel() {
    private val state = MutableStateFlow(AppInfoUiState())
    internal val uiState = state.asStateFlow()

    fun checkForUpdates() {
        if (state.value.checking) return
        state.value = AppInfoUiState(checking = true)
        viewModelScope.launch {
            state.value = AppInfoUiState(result = checker.checkForUpdates(BuildConfig.VERSION_NAME))
        }
    }
}
