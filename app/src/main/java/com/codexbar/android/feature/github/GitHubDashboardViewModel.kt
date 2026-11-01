package com.codexbar.android.feature.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.GitHubDashboardSession
import com.codexbar.android.core.domain.model.GitHubDashboardLayout
import com.codexbar.android.core.domain.model.GitHubWidget
import com.codexbar.android.core.domain.model.normalized
import com.codexbar.android.core.network.github.GitHubContributions
import com.codexbar.android.core.network.github.GitHubRepositoryActivity
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val hasStoredSession: Boolean = false,
    val profile: GitHubProfile? = null,
    val layout: GitHubDashboardLayout? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val repositoriesComplete: Boolean = false,
    val starred: List<GitHubRepository>? = null,
    val starredComplete: Boolean = false,
    val contributions: GitHubContributions? = null,
    val contributionsUnavailable: Boolean = false,
    val repositoryActivity: GitHubRepositoryActivity? = null,
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
    private var layoutSaveJob: Job? = null
    private var layoutRevision: Long? = null
    private var layoutRefreshToken: String? = null

    init {
        viewModelScope.launch {
            var previous: Long? = null
            prefs.gitHubDashboardRevision.collect { revision ->
                if (previous != null && previous != revision) {
                    loginJob?.cancel()
                    refreshJob?.cancel()
                    layoutSaveJob?.cancel()
                    layoutRevision = null
                    layoutRefreshToken = null
                    _state.value = GitHubDashboardState()
                }
                previous = revision
            }
        }
        refresh()
    }

    fun signIn() {
        if (loginJob?.isActive == true || signOutJob?.isActive == true) return
        loginJob = viewModelScope.launch {
            refreshJob?.cancelAndJoin()
            _state.update { it.copy(loading = true, userCode = null, error = null) }
            try {
                val revision = prefs.currentGitHubDashboardRevision()
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
                            if (!prefs.saveGitHubDashboardSessionIfRevision(session, revision)) {
                                _state.value = GitHubDashboardState()
                                return@launch
                            }
                            _state.update { it.copy(hasStoredSession = true) }
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
                val revision = prefs.currentGitHubDashboardRevision()
                var session = prefs.loadGitHubDashboardSession()
                if (session == null) { _state.value = GitHubDashboardState(); return@launch }
                _state.update { it.copy(hasStoredSession = true) }
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
                val repositoryResult = api.allRepositories(session.accessToken)
                val repos = repositoryResult.items.filter { isGitHubRepositoryUrl(it.htmlUrl, it.fullName) }
                currentCoroutineContext().ensureActive()
                if (prefs.loadGitHubDashboardSession()?.refreshToken != session.refreshToken) {
                    _state.value = GitHubDashboardState()
                    return@launch
                }
                val layout = profile.id?.takeIf { it > 0 }?.let { ownerId ->
                    _state.value.layout?.takeIf { it.ownerId == ownerId } ?: prefs.loadGitHubDashboardLayout(ownerId)
                }
                if (prefs.currentGitHubDashboardRevision() != revision) return@launch
                layoutRevision = revision
                layoutRefreshToken = session.refreshToken
                _state.update { it.copy(profile = profile, layout = layout, repositories = repos,
                    repositoriesComplete = repositoryResult.complete && repos.size == repositoryResult.items.size, error = null) }
                val (contributions, activity, starred) = coroutineScope {
                    val contributions = async { optionalGitHubData { api.contributions(session.accessToken) } }
                    val activity = async { optionalGitHubData { api.repositoryActivity(session.accessToken) } }
                    val starred = async { optionalGitHubData { api.starred(profile.login) } }
                    Triple(contributions.await(), activity.await(), starred.await())
                }
                currentCoroutineContext().ensureActive()
                if (prefs.loadGitHubDashboardSession()?.refreshToken != session.refreshToken) {
                    _state.value = GitHubDashboardState()
                    return@launch
                }
                val safeStars = starred?.items?.filter { isGitHubRepositoryUrl(it.htmlUrl, it.fullName) }
                _state.update { it.copy(loading = false, contributions = contributions,
                    contributionsUnavailable = contributions == null, repositoryActivity = activity,
                    starred = safeStars, starredComplete = starred?.complete == true && safeStars?.size == starred.items.size) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(loading = false, error = error.toDashboardError()) } }
        }
    }

    fun updateWidgets(widgets: List<GitHubWidget>) {
        val layout = _state.value.layout ?: return
        val revision = layoutRevision ?: return
        val token = layoutRefreshToken ?: return
        val next = layout.copy(widgets = widgets).normalized(layout.ownerId)
        _state.update { it.copy(layout = next) }
        val previous = layoutSaveJob
        layoutSaveJob = viewModelScope.launch {
            previous?.join()
            prefs.saveGitHubDashboardLayoutIfCurrent(next, revision, token)
        }
    }

    fun signOut() {
        if (signOutJob?.isActive == true) return
        val previousLogin = loginJob
        val previousRefresh = refreshJob
        val previousLayoutSave = layoutSaveJob
        layoutSaveJob = null
        layoutRevision = null
        layoutRefreshToken = null
        loginJob = null
        refreshJob = null
        val hadStoredSession = _state.value.hasStoredSession
        _state.value = GitHubDashboardState(loading = true, hasStoredSession = hadStoredSession)
        signOutJob = viewModelScope.launch {
            previousLogin?.cancelAndJoin()
            previousRefresh?.cancelAndJoin()
            previousLayoutSave?.cancelAndJoin()
            try {
                prefs.deleteGitHubDashboardSession()
                _state.value = GitHubDashboardState()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = GitHubDashboardState(hasStoredSession = hadStoredSession, error = GitHubDashboardError.Unavailable) }
        }
    }
}

private suspend fun <T> optionalGitHubData(block: suspend () -> T): T? = try { block() }
catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }

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
