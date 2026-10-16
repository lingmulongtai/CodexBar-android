package com.codexbar.android.core.network.desktop

import com.codexbar.android.core.domain.model.DesktopBridgeCredential
import com.codexbar.android.core.network.desktop.DesktopBridgeClient.Companion.deriveKey
import com.codexbar.android.core.network.desktop.DesktopBridgeClient.Companion.hmacSha256
import com.codexbar.android.core.network.desktop.DesktopBridgeClient.Companion.decodeUrlBase64
import com.codexbar.android.core.network.desktop.DesktopBridgeClient.Companion.toUrlBase64
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DesktopRemoteSettingsTest {
    @Test fun `v2 sends explicit version authenticates exact payload and rejects a resource-swapped response`() {
        for (wrongResource in listOf(false, true)) {
            val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val key = ByteArray(32) { it.toByte() }
            val credential = DesktopBridgeCredential("127.0.0.1", server.localPort, "5b017391-6dc4-4ab7-b0ad-2255dada62d7", key.toUrlBase64())
            val failure = AtomicReference<Throwable?>()
            val thread = Thread {
                try { server.use { it.accept().use { socket ->
                    val request = Json.decodeFromString<DesktopControlRequest>(socket.getInputStream().bufferedReader().readLine())
                    assertEquals(2, request.protocolVersion)
                    assertEquals("settings.get", request.resource)
                    val hash = MessageDigest.getInstance("SHA-256").digest(decodeUrlBase64(request.payload)).toUrlBase64()
                    val canonical = "2\n${request.companionId}\n${request.requestedAtEpochSeconds}\n${request.nonce}\n${request.resource}\n$hash"
                    assertArrayEquals(hmacSha256(deriveKey(key, "codexbar-windows-auth-v2"), canonical.toByteArray()), decodeUrlBase64(request.signature))
                    val now = Instant.now().epochSecond
                    val resource = if (wrongResource) "settings.update" else request.resource
                    val aad = "2\n${request.companionId}\n${request.nonce}\n$now\n$resource\n$hash"
                    val iv = ByteArray(12) { (it + 1).toByte() }
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(key, "codexbar-windows-encryption-v2"), "AES"), GCMParameterSpec(128, iv))
                    cipher.updateAAD(aad.toByteArray())
                    val plaintext = Json.encodeToString(DesktopRemoteSettingsResult("ok", DesktopRemoteSettings(true, "a".repeat(64), 300, false, "rolling:30")))
                    val envelope = DesktopBridgeEnvelope(2, request.companionId, request.nonce, now, iv.toUrlBase64(), cipher.doFinal(plaintext.toByteArray()).toUrlBase64())
                    socket.getOutputStream().write((Json.encodeToString(envelope) + "\n").toByteArray())
                } } } catch (error: Throwable) { failure.set(error) }
            }.apply { isDaemon = true; start() }
            if (wrongResource) assertThrows(DesktopBridgeAuthenticationException::class.java) { runBlocking { DesktopBridgeClient(Json).remoteSettings(credential) } }
            else assertEquals(300L, runBlocking { DesktopBridgeClient(Json).remoteSettings(credential) }.settings.refreshIntervalSecs)
            thread.join(5000)
            assertNull(failure.get())
        }
    }
}
