package com.codexbar.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderCatalogTest {
    @Test
    fun `catalog covers every service with a stable guide anchor`() {
        val metadata = AiService.entries.map { ProviderCatalog.metadataFor(it) }

        assertEquals(AiService.entries.size, metadata.size)
        assertEquals(metadata.size, metadata.map { it.guideAnchor }.toSet().size)
    }

    @Test
    fun `auth mode determines stored provider secret kind`() {
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.ZAI.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.KIMI.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.ELEVENLABS.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.OPENROUTER.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.API_KEY,
            AiService.SYNTHETIC.providerMetadata.secretKind
        )
        assertEquals(
            ProviderSecretKind.COOKIE_HEADER,
            AiService.CURSOR.providerMetadata.secretKind
        )
        assertNull(AiService.CODEX.providerMetadata.secretKind)
    }
}
