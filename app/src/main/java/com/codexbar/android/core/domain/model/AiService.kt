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
    CURSOR(
        displayName = "Cursor",
        brandColor = 0xFF00A67E,
        baseUrl = "https://cursor.com/",
        requiresManualCredentials = true
    ),
    ZAI(
        displayName = "z.ai",
        brandColor = 0xFFE85A6A,
        baseUrl = "https://api.z.ai/",
        requiresManualCredentials = true
    ),
    ZENMUX(
        displayName = "ZenMux",
        brandColor = 0xFF6750A4,
        baseUrl = "https://zenmux.ai/api/v1/management/",
        requiresManualCredentials = true
    ),
    KIMI(
        displayName = "Kimi",
        brandColor = 0xFF00B8A9,
        baseUrl = "https://api.kimi.com/",
        requiresManualCredentials = true
    ),
    ELEVENLABS(
        displayName = "ElevenLabs",
        brandColor = 0xFF6C5CE7,
        baseUrl = "https://api.elevenlabs.io/",
        requiresManualCredentials = true
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        brandColor = 0xFF4D6BFE,
        baseUrl = "https://openrouter.ai/api/v1/",
        requiresManualCredentials = true
    )
}
