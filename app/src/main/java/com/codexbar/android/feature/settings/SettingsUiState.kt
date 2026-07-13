package com.codexbar.android.feature.settings

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.security.PrivacySettings

data class SettingsUiState(
    val serviceStates: Map<AiService, ServiceCredentialState> = AiService.entries.associateWith {
        ServiceCredentialState()
    },
    val refreshIntervalMinutes: Long = 30L,
    val notificationsEnabled: Boolean = true,
    val privacySettings: PrivacySettings = PrivacySettings(),
    val showDeleteConfirmDialog: Boolean = false,
    val disconnectConfirmService: AiService? = null
)

data class ServiceCredentialState(
    val accessToken: String = "",
    val refreshToken: String = "",
    val accountId: String = "", // Codex only
    val oauthClientId: String = "", // Gemini only
    val expiresAtDisplay: String = "", // Gemini only (read-only)
    val isValidating: Boolean = false,
    val isAccountLinking: Boolean = false,
    val accountLinkPrompt: AccountLinkPrompt? = null,
    val validationResult: ValidationResult? = null,
    val isConnected: Boolean = false,
    val hasUnsavedChanges: Boolean = false
)

data class AccountLinkPrompt(
    val verificationUrl: String,
    val userCode: String,
    val expiresAtDisplay: String
)

sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Failure(val message: String) : ValidationResult()
}
