package com.codexbar.android.feature.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.GitHubDashboardSession
import com.codexbar.android.core.network.github.GitHubDashboardApi
import com.codexbar.android.core.network.github.GitHubDashboardException
import com.codexbar.android.core.network.github.GitHubProfile
import com.codexbar.android.core.network.github.GitHubRepository
import com.codexbar.android.core.network.github.GitHubTokenResponse
import com.codexbar.android.core.security.EncryptedPrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class GitHubDashboardError { Unavailable, Unauthorized, RateLimited, Expired, InvalidResponse }
internal data class GitHubDashboardState(
    val loading: Boolean = false,
    val userCode: String? = null,
    val profile: GitHubProfile? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val error: GitHubDashboardError? = null
)

@HiltViewModel
internal class GitHubDashboardViewModel @Inject constructor(
    private val api: GitHubDashboardApi,
    private val prefs: EncryptedPrefsManager
) : ViewModel() {
    private val _state = MutableStateFlow(GitHubDashboardState())
    val state: StateFlow<GitHubDashboardState> = _state.asStateFlow()
    private var loginJob: Job? = null
    private var refreshJob: Job? = null
    private var signOutJob: Job? = null

    init { refresh() }

    fun signIn() {
        if (loginJob?.isActive == true || signOutJob?.isActive == true) return
        loginJob = viewModelScope.launch {
            refreshJob?.cancelAndJoin()
            _state.update { it.copy(loading = true, userCode = null, error = null) }
            try {
                val clientId = api.clientId()
                val challenge = api.start(clientId)
                _state.update { it.copy(userCode = challenge.userCode) }
                val end = System.currentTimeMillis() + challenge.expiresIn * 1000
                var interval = challenge.interval
                var completed = false
                while (isActive && System.currentTimeMillis() < end) {
                    delay(interval * 1000)
                    val token = api.poll(clientId, challenge.deviceCode)
                    when (token.error) {
                        "authorization_pending" -> continue
                        "slow_down" -> { interval = (interval + 5).coerceAtMost(120); continue }
                        "expired_token", "access_denied" -> throw GitHubLoginExpired()
                        null -> {
                            val session = token.toSession(clientId)
                            currentCoroutineContext().ensureActive()
                            prefs.saveGitHubDashboardSession(session)
                            completed = true
                            break
                        }
                        else -> throw IOException("GitHub authorization failed")
                    }
                }
                if (!completed) throw GitHubLoginExpired()
                _state.update { it.copy(userCode = null, loading = false) }
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(loading = false, userCode = null, error = error.toDashboardError()) } }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true || signOutJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                var session = prefs.loadGitHubDashboardSession()
                if (session == null) { _state.value = GitHubDashboardState(); return@launch }
                if (session.expiresAtMillis <= System.currentTimeMillis() + 60_000) {
                    val previous = session
                    val rotated = api.refresh(previous).toSession(previous.clientId)
                    currentCoroutineContext().ensureActive()
                    if (!prefs.replaceGitHubDashboardSession(previous.refreshToken, rotated)) {
                        _state.value = GitHubDashboardState()
                        return@launch
                    }
                    session = rotated
                }
                val profile = api.profile(session.accessToken)
                if (!profile.login.matches(Regex("[A-Za-z0-9-]{1,39}"))) throw IOException("Invalid GitHub profile")
                val repos = api.repositories(session.accessToken).filter { isGitHubRepositoryUrl(it.htmlUrl, it.fullName) }
                currentCoroutineContext().ensureActive()
                if (prefs.loadGitHubDashboardSession()?.refreshToken != session.refreshToken) {
                    _state.value = GitHubDashboardState()
                    return@launch
                }
                _state.update { it.copy(loading = false, profile = profile, repositories = repos, error = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(loading = false, error = error.toDashboardError()) } }
        }
    }

    fun signOut() {
        if (signOutJob?.isActive == true) return
        val previousLogin = loginJob
        val previousRefresh = refreshJob
        loginJob = null
        refreshJob = null
        _state.value = GitHubDashboardState(loading = true)
        signOutJob = viewModelScope.launch {
            previousLogin?.cancelAndJoin()
            previousRefresh?.cancelAndJoin()
            try {
                prefs.deleteGitHubDashboardSession()
                _state.value = GitHubDashboardState()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = GitHubDashboardState(error = GitHubDashboardError.Unavailable) }
        }
    }
}

internal fun isGitHubRepositoryUrl(value: String, fullName: String): Boolean {
    val parts = fullName.split('/')
    if (parts.size != 2 || !parts[0].matches(Regex("[A-Za-z0-9-]{1,39}")) ||
        !parts[1].matches(Regex("[A-Za-z0-9_.-]{1,100}")) || parts[1] in setOf(".", "..")) return false
    return value.toHttpUrlOrNull()?.let {
        it.scheme == "https" && it.host == "github.com" && it.port == 443 &&
            it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null &&
            it.pathSegments == parts
    } == true
}

private fun GitHubTokenResponse.toSession(clientId: String): GitHubDashboardSession {
    if (error in setOf("bad_refresh_token", "expired_token", "access_denied")) throw GitHubLoginExpired()
    val access = accessToken?.takeIf { it.isNotBlank() } ?: throw IOException("Missing GitHub access token")
    val refresh = refreshToken?.takeIf { it.isNotBlank() } ?: throw IOException("Missing GitHub refresh token")
    val expires = expiresIn?.takeIf { it in 1..2_592_000 } ?: throw IOException("Invalid GitHub token expiry")
    return GitHubDashboardSession(clientId, access, refresh, System.currentTimeMillis() + expires * 1000)
}

private class GitHubLoginExpired : IOException()
private fun Exception.toDashboardError(): GitHubDashboardError = when (this) {
    is GitHubLoginExpired -> GitHubDashboardError.Expired
    is GitHubDashboardException -> when (status) {
        401 -> GitHubDashboardError.Unauthorized
        403, 429 -> GitHubDashboardError.RateLimited
        else -> GitHubDashboardError.Unavailable
    }
    is IllegalArgumentException -> GitHubDashboardError.InvalidResponse
    else -> GitHubDashboardError.Unavailable
}
