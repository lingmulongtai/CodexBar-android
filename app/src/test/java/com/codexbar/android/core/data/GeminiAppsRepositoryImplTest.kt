package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAppsRepositoryImplTest {
    @Test fun `phone acquisition cannot substitute Gemini CLI usage`() = runTest {
        val repository = GeminiAppsRepositoryImpl()
        assertTrue(repository.fetchQuota() is Result.Failure)
        assertTrue(repository.validateCredential() is Result.Failure)
    }
}
