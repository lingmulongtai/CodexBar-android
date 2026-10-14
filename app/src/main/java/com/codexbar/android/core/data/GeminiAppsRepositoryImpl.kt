package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository

/** Gemini Apps credentials stay on Windows; phone mode cannot fall back to CLI quota. */
class GeminiAppsRepositoryImpl : QuotaRepository {
    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> = unavailable()
    override suspend fun validateCredential(): Result<Unit, AppError> = unavailable()
    override suspend fun validateCredential(credential: Credential): Result<Unit, AppError> = unavailable()

    private fun unavailable() = Result.Failure(AppError.AuthError(
        service = AiService.GEMINI_APPS,
        isTerminal = true,
        message = "Connect Gemini Apps in Win-CodexBar + Mobile Connect and use the Windows acquisition mode."
    ))
}
