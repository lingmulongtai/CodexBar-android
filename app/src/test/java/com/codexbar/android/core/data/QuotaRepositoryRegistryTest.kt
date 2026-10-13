package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import org.junit.Assert.assertEquals
import org.mockito.Mockito.*
import kotlinx.coroutines.test.runTest
import com.codexbar.android.core.security.EncryptedPrefsManager
import org.junit.Test

class QuotaRepositoryRegistryTest {
    private val repositories = AiService.entries.associateWith { FakeQuotaRepository() }
    private val prefs = mock(EncryptedPrefsManager::class.java)
    private val desktop = mock(DesktopQuotaSource::class.java)

    @Test
    fun `registry preserves service order and resolves every provider`() = runTest {
        val registry = QuotaRepositoryRegistry(repositories, prefs, desktop)

        assertEquals(AiService.entries, registry.entries().map { it.first })
        AiService.entries.forEach { service ->
            assertEquals(Result.Failure(AppError.CredentialNotFound(service)), registry.repositoryFor(service).fetchQuota())
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `registry fails closed when a provider binding is missing`() {
        QuotaRepositoryRegistry(repositories - AiService.entries.last(), prefs, desktop)
    }

    @Test fun `selected Windows provider routes remotely while native validation stays native`() = runTest {
        val service = AiService.CODEX
        `when`(prefs.usesDesktopBridge(service)).thenReturn(true)
        val remote: Result<QuotaInfo, AppError> = Result.Failure(AppError.ServiceUnavailable)
        `when`(desktop.fetchQuota(service)).thenReturn(remote)
        val registry = QuotaRepositoryRegistry(repositories, prefs, desktop)
        assertEquals(remote, registry.repositoryFor(service).fetchQuota())
        assertEquals(Result.Success(Unit), registry.repositoryFor(service).validateCredential(Credential.CodexCredential("access", "refresh")))
        verify(desktop).fetchQuota(service)
        assertEquals(1, repositories.getValue(service).validations)
    }

    private inner class FakeQuotaRepository : QuotaRepository {
        var validations = 0
        override suspend fun fetchQuota(): Result<QuotaInfo, AppError> = Result.Failure(AppError.CredentialNotFound(repositories.entries.first { it.value === this }.key))

        override suspend fun validateCredential(): Result<Unit, AppError> = error("not used")

        override suspend fun validateCredential(credential: Credential): Result<Unit, AppError> { validations++; return Result.Success(Unit) }
    }
}
