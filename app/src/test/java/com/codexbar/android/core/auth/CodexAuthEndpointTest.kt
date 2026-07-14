package com.codexbar.android.core.auth

import com.codexbar.android.core.network.oauth.CodexDeviceAuthService
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class CodexAuthEndpointTest {
    @Test
    fun `all Codex device auth URLs use the official host`() {
        val urls = listOf(
            CodexDeviceAuthService.BASE_URL,
            CodexDeviceAuthService.CODEX_DEVICE_VERIFICATION_URL,
            CodexDeviceAuthService.CODEX_DEVICE_REDIRECT_URI
        )

        urls.forEach { url ->
            val uri = URI.create(url)
            assertEquals("https", uri.scheme)
            assertEquals("auth.openai.com", uri.host)
        }
    }
}
