package com.codexbar.android.core.network.codex.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CodexTelemetryPairingTest {
    private val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val id = "5b017391-6dc4-4ab7-b0ad-2255dada62d7"

    @Test
    fun `parses a numeric private network pairing URI`() {
        val credential = CodexTelemetryPairing.parse(
            "codexbar://codex-telemetry-pair?v=1&address=192.168.1.24&port=43822&id=$id&key=$key"
        )

        assertEquals("192.168.1.24", credential.host)
        assertEquals(43822, credential.port)
        assertEquals(id, credential.companionId)
        assertEquals(key, credential.sharedKeyBase64Url)
    }

    @Test
    fun `rejects DNS public and parameter-injection destinations`() {
        listOf(
            "codexbar://codex-telemetry-pair?v=1&address=example.com&port=43822&id=$id&key=$key",
            "codexbar://codex-telemetry-pair?v=1&address=8.8.8.8&port=43822&id=$id&key=$key",
            "codexbar://codex-telemetry-pair?v=1&v=1&address=127.0.0.1&port=43822&id=$id&key=$key",
            "codexbar://codex-telemetry-pair?v=1&address=127.0.0.1&port=43822&id=$id&key=$key&next=https%3A%2F%2Fevil.test"
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                CodexTelemetryPairing.parse(value)
            }
        }
    }
}
