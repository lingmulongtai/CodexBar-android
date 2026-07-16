package com.codexbar.android.core.domain.model

enum class AiService(
    val displayName: String,
    val brandColor: Long,
    val baseUrl: String,
    val requiresManualCredentials: Boolean
) {
    CLAUDE(
        displayName = "Claude",
        brandColor = 0xFFD4A574,
        baseUrl = "https://api.anthropic.com/",
        requiresManualCredentials = false
    ),
    CODEX(
        displayName = "Codex",
        brandColor = 0xFF10A37F,
        baseUrl = "https://chatgpt.com/",
        requiresManualCredentials = false
    ),
    GEMINI(
        displayName = "Gemini",
        brandColor = 0xFF4285F4,
        baseUrl = "codexbar://gemini-companion/",
        requiresManualCredentials = false
    ),
    COPILOT(
        displayName = "GitHub Copilot",
        brandColor = 0xFF6E7681,
        baseUrl = "https://api.github.com/",
        requiresManualCredentials = false
    ),
    ZENMUX(
        displayName = "ZenMux",
        brandColor = 0xFF6750A4,
        baseUrl = "https://zenmux.ai/api/v1/management/",
        requiresManualCredentials = true
    )
}
