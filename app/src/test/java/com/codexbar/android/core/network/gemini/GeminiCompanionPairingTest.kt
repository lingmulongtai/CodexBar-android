package com.codexbar.android.core.network.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiCompanionPairingTest {
    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val id = "5b017391-6dc4-4ab7-b0ad-2255dada62d7"

    @Test
    fun `parses a numeric private network pairing URI`() {
        val credential = GeminiCompanionPairing.parse(
            "codexbar://gemini-pair?v=1&address=192.168.1.24&port=43821&id=$id&key=$key"
        )

        assertEquals("192.168.1.24", credential.host)
        assertEquals(43821, credential.port)
        assertEquals(id, credential.companionId)
        assertEquals(key, credential.sharedKeyBase64Url)
    }

    @Test
    fun `rejects DNS and public addresses to prevent companion SSRF`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeminiCompanionPairing.parse(
                "codexbar://gemini-pair?v=1&address=example.com&port=43821&id=$id&key=$key"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeminiCompanionPairing.parse(
                "codexbar://gemini-pair?v=1&address=8.8.8.8&port=43821&id=$id&key=$key"
            )
        }
    }

    @Test
    fun `rejects duplicate and unknown fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeminiCompanionPairing.parse(
                "codexbar://gemini-pair?v=1&v=1&address=127.0.0.1&port=43821&id=$id&key=$key"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeminiCompanionPairing.parse(
                "codexbar://gemini-pair?v=1&address=127.0.0.1&port=43821&id=$id&key=$key&next=https%3A%2F%2Fevil.test"
            )
        }
    }
}
