package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.repository.QuotaRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaRepositoryRegistry @Inject constructor(
    repositories: Map<AiService, @JvmSuppressWildcards QuotaRepository>
) {
    private val repositories = repositories.toMap()

    init {
        val missing = AiService.entries.toSet() - this.repositories.keys
        check(missing.isEmpty()) {
            "Missing quota repositories for: ${missing.joinToString { it.name }}"
        }
    }

    fun repositoryFor(service: AiService): QuotaRepository = checkNotNull(repositories[service]) {
        "No repository registered for ${service.name}"
    }

    fun entries(): List<Pair<AiService, QuotaRepository>> = AiService.entries.map { service ->
        service to repositoryFor(service)
    }
}
