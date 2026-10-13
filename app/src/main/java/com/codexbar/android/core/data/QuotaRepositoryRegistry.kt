package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.security.EncryptedPrefsManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaRepositoryRegistry @Inject constructor(
    repositories: Map<AiService, @JvmSuppressWildcards QuotaRepository>,
    private val prefs: EncryptedPrefsManager,
    private val desktop: DesktopQuotaSource
) {
    private val repositories = repositories.toMap()

    init {
        val missing = AiService.entries.toSet() - this.repositories.keys
        check(missing.isEmpty()) {
            "Missing quota repositories for: ${missing.joinToString { it.name }}"
        }
    }

    fun repositoryFor(service: AiService): QuotaRepository {
        val native = checkNotNull(repositories[service]) { "No repository registered for ${service.name}" }
        return object : QuotaRepository {
            override suspend fun fetchQuota(): Result<QuotaInfo, AppError> = if (prefs.usesDesktopBridge(service)) desktop.fetchQuota(service) else native.fetchQuota()
            override suspend fun validateCredential(): Result<Unit, AppError> = native.validateCredential()
            override suspend fun validateCredential(credential: Credential): Result<Unit, AppError> = native.validateCredential(credential)
        }
    }

    fun entries(): List<Pair<AiService, QuotaRepository>> = AiService.entries.map { service ->
        service to repositoryFor(service)
    }
}
