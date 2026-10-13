package com.codexbar.android.core.network.desktop

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class DesktopBridgePairingTest {
    private val code = "CBWIN1|100.74.102.71|43825|5b017391-6dc4-4ab7-b0ad-2255dada62d7|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    @Test fun `private numeric endpoints and keys are required`() {
        val credential = DesktopBridgePairing.parse(code)
        assertEquals(43825, credential.port)
        assertFalse(credential.toString().contains(credential.sharedKeyBase64Url))
        for (address in listOf("8.8.8.8", "example.com", "0.0.0.0", "100.128.0.1")) {
            assertThrows(IllegalArgumentException::class.java) { DesktopBridgePairing.parse(code.replace("100.74.102.71", address)) }
        }
        assertThrows(IllegalArgumentException::class.java) { DesktopBridgePairing.parse(code.replace("43825", "80")) }
    }
    @Test fun `request matches Rust and independently generated Node vector`() {
        val master = ByteArray(32) { it.toByte() }
        val key = DesktopBridgeClient.deriveKey(master, DesktopBridgeClient.AUTH_KEY_CONTEXT)
        val canonical = DesktopBridgeClient.requestCanonical("5b017391-6dc4-4ab7-b0ad-2255dada62d7", 1700000000L, Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { 1 }))
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(DesktopBridgeClient.hmacSha256(key, canonical.toByteArray(Charsets.UTF_8)))
        assertEquals("EcBHhL1ayalXOhEJMO2C1tWFeqcBHsdjugdzUJBNVdQ", signature)
    }
}
