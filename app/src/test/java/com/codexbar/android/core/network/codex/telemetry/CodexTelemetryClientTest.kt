package com.codexbar.android.core.network.codex.telemetry

import com.codexbar.android.core.domain.model.CodexTelemetryCredential
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CodexTelemetryClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val masterKey = ByteArray(32) { index -> index.toByte() }

    @Test
    fun `authenticates decrypts and validates a bounded telemetry snapshot`() = runTest {
        val now = Instant.ofEpochSecond(1_750_000_000L)
        val fixture = startServer(now = now, tamperCiphertext = false, invalidTotals = false)

        val telemetry = CodexTelemetryClient(json).fetchSnapshot(fixture.credential, now)
        fixture.thread.join(5_000)

        assertNull(fixture.failure.get())
        assertEquals("gpt-5.6", telemetry.currentContext?.model)
        assertEquals(64_000L, telemetry.currentContext?.usedTokens)
        assertEquals(12_500L, telemetry.tokenUsage.today.totalTokens)
        assertEquals(1, telemetry.tokenUsage.daily.size)
    }

    @Test
    fun `rejects tampered ciphertext and inconsistent token totals`() {
        val now = Instant.ofEpochSecond(1_750_000_000L)
        val tampered = startServer(now, tamperCiphertext = true, invalidTotals = false)
        assertThrows(CodexTelemetryAuthenticationException::class.java) {
            kotlinx.coroutines.runBlocking {
                CodexTelemetryClient(json).fetchSnapshot(tampered.credential, now)
            }
        }
        tampered.thread.join(5_000)
        assertNull(tampered.failure.get())

        val invalid = startServer(now, tamperCiphertext = false, invalidTotals = true)
        assertThrows(CodexTelemetryProtocolException::class.java) {
            kotlinx.coroutines.runBlocking {
                CodexTelemetryClient(json).fetchSnapshot(invalid.credential, now)
            }
        }
        invalid.thread.join(5_000)
        assertNull(invalid.failure.get())
    }

    private fun startServer(
        now: Instant,
        tamperCiphertext: Boolean,
        invalidTotals: Boolean
    ): ServerFixture {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val companionId = UUID.randomUUID().toString()
        val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                server.use { listener ->
                    listener.accept().use { socket ->
                        val requestLine = socket.getInputStream().bufferedReader().readLine()
                        val request = json.decodeFromString<CodexTelemetryRequest>(requestLine)
                        val authKey = CodexTelemetryClient.deriveKey(
                            masterKey,
                            CodexTelemetryClient.AUTH_KEY_CONTEXT
                        )
                        val expectedSignature = CodexTelemetryClient.hmacSha256(
                            authKey,
                            CodexTelemetryClient.requestCanonical(
                                request.companionId,
                                request.requestedAtEpochSeconds,
                                request.nonce
                            ).toByteArray(StandardCharsets.UTF_8)
                        )
                        check(
                            MessageDigest.isEqual(
                                expectedSignature,
                                CodexTelemetryClient.decodeUrlBase64(request.signature)
                            )
                        )

                        val iv = ByteArray(12) { index -> (index + 1).toByte() }
                        val envelopeTemplate = CodexTelemetryEnvelope(
                            protocolVersion = 1,
                            companionId = companionId,
                            requestNonce = request.nonce,
                            sentAtEpochSeconds = now.epochSecond,
                            iv = with(CodexTelemetryClient) { iv.toUrlBase64() },
                            ciphertext = ""
                        )
                        val totals = CodexTokenTotalsDto(
                            inputTokens = 12_000,
                            cachedInputTokens = 8_000,
                            outputTokens = 500,
                            reasoningOutputTokens = 200,
                            totalTokens = if (invalidTotals) 99 else 12_500
                        )
                        val snapshot = CodexTelemetrySnapshot(
                            schemaVersion = 1,
                            source = "codex-cli-local-jsonl",
                            generatedAtEpochSeconds = now.epochSecond,
                            currentContext = CodexContextDto(
                                capturedAtEpochSeconds = now.epochSecond,
                                model = "gpt-5.6",
                                usedTokens = 64_000,
                                contextWindowTokens = 258_400,
                                sessionTokens = 120_000
                            ),
                            tokenUsage = CodexTokenUsageDto(
                                today = totals,
                                last7Days = totals,
                                last30Days = totals,
                                daily = listOf(
                                    CodexDailyTokenUsageDto(
                                        date = "2025-06-15",
                                        inputTokens = totals.inputTokens,
                                        cachedInputTokens = totals.cachedInputTokens,
                                        outputTokens = totals.outputTokens,
                                        reasoningOutputTokens = totals.reasoningOutputTokens,
                                        totalTokens = totals.totalTokens
                                    )
                                ),
                                models = listOf(
                                    CodexModelTokenUsageDto(
                                        model = "gpt-5.6",
                                        inputTokens = totals.inputTokens,
                                        cachedInputTokens = totals.cachedInputTokens,
                                        outputTokens = totals.outputTokens,
                                        reasoningOutputTokens = totals.reasoningOutputTokens,
                                        totalTokens = totals.totalTokens
                                    )
                                )
                            )
                        )
                        val encryptionKey = CodexTelemetryClient.deriveKey(
                            masterKey,
                            CodexTelemetryClient.ENCRYPTION_KEY_CONTEXT
                        )
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(
                            Cipher.ENCRYPT_MODE,
                            SecretKeySpec(encryptionKey, "AES"),
                            GCMParameterSpec(128, iv)
                        )
                        cipher.updateAAD(
                            CodexTelemetryClient.responseCanonical(envelopeTemplate)
                                .toByteArray(StandardCharsets.UTF_8)
                        )
                        val encrypted = cipher.doFinal(
                            json.encodeToString(snapshot).toByteArray(StandardCharsets.UTF_8)
                        )
                        if (tamperCiphertext) encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
                        val envelope = envelopeTemplate.copy(
                            ciphertext = with(CodexTelemetryClient) { encrypted.toUrlBase64() }
                        )
                        socket.getOutputStream().bufferedWriter().use { writer ->
                            writer.write(json.encodeToString(envelope))
                            writer.newLine()
                            writer.flush()
                        }
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply {
            isDaemon = true
            start()
        }
        val credential = CodexTelemetryCredential(
            host = "127.0.0.1",
            port = server.localPort,
            companionId = companionId,
            sharedKeyBase64Url = with(CodexTelemetryClient) { masterKey.toUrlBase64() }
        )
        return ServerFixture(credential, thread, failure)
    }

    private data class ServerFixture(
        val credential: CodexTelemetryCredential,
        val thread: Thread,
        val failure: AtomicReference<Throwable?>
    )
}
