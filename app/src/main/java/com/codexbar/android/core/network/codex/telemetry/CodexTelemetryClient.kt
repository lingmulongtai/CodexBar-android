package com.codexbar.android.core.network.codex.telemetry

import com.codexbar.android.core.domain.model.CodexContextUsage
import com.codexbar.android.core.domain.model.CodexDailyTokenUsage
import com.codexbar.android.core.domain.model.CodexModelTokenUsage
import com.codexbar.android.core.domain.model.CodexTelemetry
import com.codexbar.android.core.domain.model.CodexTelemetryCredential
import com.codexbar.android.core.domain.model.CodexTokenTotals
import com.codexbar.android.core.domain.model.CodexTokenUsage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
open class CodexTelemetryClient @Inject constructor(
    private val json: Json
) {
    private val secureRandom = SecureRandom()

    open suspend fun fetchSnapshot(
        credential: CodexTelemetryCredential,
        now: Instant = Instant.now()
    ): CodexTelemetry = withContext(Dispatchers.IO) {
        CodexTelemetryPairing.validate(credential)
        val masterKey = decodeUrlBase64(credential.sharedKeyBase64Url)
        val authKey = deriveKey(masterKey, AUTH_KEY_CONTEXT)
        val encryptionKey = deriveKey(masterKey, ENCRYPTION_KEY_CONTEXT)
        val nonce = ByteArray(REQUEST_NONCE_BYTES).also(secureRandom::nextBytes).toUrlBase64()
        val requestedAt = now.epochSecond
        val signature = hmacSha256(
            authKey,
            requestCanonical(
                companionId = credential.companionId,
                requestedAtEpochSeconds = requestedAt,
                nonce = nonce
            ).toByteArray(StandardCharsets.UTF_8)
        ).toUrlBase64()
        val request = CodexTelemetryRequest(
            protocolVersion = PROTOCOL_VERSION,
            companionId = credential.companionId,
            requestedAtEpochSeconds = requestedAt,
            nonce = nonce,
            signature = signature
        )

        val responseLine = Socket().use { socket ->
            socket.soTimeout = IO_TIMEOUT_MILLIS
            socket.connect(
                InetSocketAddress(credential.host, credential.port),
                CONNECT_TIMEOUT_MILLIS
            )
            socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(request))
                writer.newLine()
                writer.flush()
                readLimitedLine(socket)
            }
        }

        val envelope = runCatching {
            json.decodeFromString<CodexTelemetryEnvelope>(responseLine)
        }.getOrElse { throw CodexTelemetryProtocolException("Invalid companion envelope", it) }
        validateEnvelope(envelope, credential, nonce, now)
        val plaintext = decryptEnvelope(envelope, encryptionKey)
        val snapshot = runCatching {
            json.decodeFromString<CodexTelemetrySnapshot>(plaintext)
        }.getOrElse { throw CodexTelemetryProtocolException("Invalid telemetry snapshot", it) }
        validateAndMapSnapshot(snapshot, now)
    }

    private fun readLimitedLine(socket: Socket): String {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        while (output.size() <= MAX_RESPONSE_BYTES) {
            val next = input.read()
            if (next == -1) throw IOException("Companion closed the connection")
            if (next == '\n'.code) {
                return output.toString(StandardCharsets.UTF_8.name()).trimEnd('\r')
            }
            output.write(next)
        }
        throw CodexTelemetryProtocolException("Companion response is too large")
    }

    private fun validateEnvelope(
        envelope: CodexTelemetryEnvelope,
        credential: CodexTelemetryCredential,
        requestNonce: String,
        now: Instant
    ) {
        if (
            envelope.protocolVersion != PROTOCOL_VERSION ||
            envelope.companionId != credential.companionId ||
            envelope.requestNonce != requestNonce ||
            kotlin.math.abs(envelope.sentAtEpochSeconds - now.epochSecond) > MAX_CLOCK_SKEW_SECONDS
        ) {
            throw CodexTelemetryAuthenticationException("Companion response authentication failed")
        }
    }

    private fun decryptEnvelope(
        envelope: CodexTelemetryEnvelope,
        encryptionKey: ByteArray
    ): String {
        return try {
            val iv = decodeUrlBase64(envelope.iv)
            if (iv.size != GCM_IV_BYTES) {
                throw CodexTelemetryAuthenticationException("Invalid companion IV")
            }
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(encryptionKey, AES_ALGORITHM),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(responseCanonical(envelope).toByteArray(StandardCharsets.UTF_8))
            String(
                cipher.doFinal(decodeUrlBase64(envelope.ciphertext)),
                StandardCharsets.UTF_8
            )
        } catch (error: CodexTelemetryAuthenticationException) {
            throw error
        } catch (error: GeneralSecurityException) {
            throw CodexTelemetryAuthenticationException(
                "Companion response authentication failed",
                error
            )
        } catch (error: IllegalArgumentException) {
            throw CodexTelemetryAuthenticationException(
                "Companion response encoding is invalid",
                error
            )
        }
    }

    private fun validateAndMapSnapshot(snapshot: CodexTelemetrySnapshot, now: Instant): CodexTelemetry {
        if (
            snapshot.schemaVersion != SNAPSHOT_SCHEMA_VERSION ||
            snapshot.source != SNAPSHOT_SOURCE ||
            snapshot.generatedAtEpochSeconds > now.epochSecond + MAX_CLOCK_SKEW_SECONDS ||
            now.epochSecond - snapshot.generatedAtEpochSeconds > MAX_SNAPSHOT_AGE_SECONDS ||
            snapshot.tokenUsage.daily.size > MAX_DAILY_ENTRIES ||
            snapshot.tokenUsage.models.size > MAX_MODEL_ENTRIES
        ) {
            throw CodexTelemetryProtocolException("Companion snapshot failed validation")
        }
        val currentContext = snapshot.currentContext?.let { context ->
            validateModel(context.model)
            if (
                context.capturedAtEpochSeconds > now.epochSecond + MAX_CLOCK_SKEW_SECONDS ||
                now.epochSecond - context.capturedAtEpochSeconds > MAX_CONTEXT_AGE_SECONDS ||
                context.contextWindowTokens !in 1..MAX_TOKEN_COUNT ||
                context.usedTokens !in 0..MAX_TOKEN_COUNT ||
                context.sessionTokens !in context.usedTokens..MAX_TOKEN_COUNT
            ) {
                throw CodexTelemetryProtocolException("Invalid context telemetry")
            }
            CodexContextUsage(
                capturedAt = Instant.ofEpochSecond(context.capturedAtEpochSeconds),
                model = context.model,
                usedTokens = context.usedTokens,
                contextWindowTokens = context.contextWindowTokens,
                sessionTokens = context.sessionTokens
            )
        }
        val dailyDates = mutableSetOf<LocalDate>()
        val daily = snapshot.tokenUsage.daily.map { entry ->
            val date = try {
                LocalDate.parse(entry.date)
            } catch (error: DateTimeException) {
                throw CodexTelemetryProtocolException("Invalid daily telemetry date", error)
            }
            if (!dailyDates.add(date)) {
                throw CodexTelemetryProtocolException("Duplicate daily telemetry date")
            }
            CodexDailyTokenUsage(date, entry.toTotals())
        }.sortedBy(CodexDailyTokenUsage::date)
        val modelNames = mutableSetOf<String>()
        val models = snapshot.tokenUsage.models.map { entry ->
            validateModel(entry.model)
            if (!modelNames.add(entry.model.lowercase())) {
                throw CodexTelemetryProtocolException("Duplicate model telemetry")
            }
            CodexModelTokenUsage(entry.model, entry.toTotals())
        }
        return CodexTelemetry(
            generatedAt = Instant.ofEpochSecond(snapshot.generatedAtEpochSeconds),
            currentContext = currentContext,
            tokenUsage = CodexTokenUsage(
                today = snapshot.tokenUsage.today.toTotals(),
                last7Days = snapshot.tokenUsage.last7Days.toTotals(),
                last30Days = snapshot.tokenUsage.last30Days.toTotals(),
                daily = daily,
                models = models
            )
        )
    }

    private fun CodexDailyTokenUsageDto.toTotals(): CodexTokenTotals = validatedTotals(
        inputTokens,
        cachedInputTokens,
        outputTokens,
        reasoningOutputTokens,
        totalTokens
    )

    private fun CodexModelTokenUsageDto.toTotals(): CodexTokenTotals = validatedTotals(
        inputTokens,
        cachedInputTokens,
        outputTokens,
        reasoningOutputTokens,
        totalTokens
    )

    private fun CodexTokenTotalsDto.toTotals(): CodexTokenTotals = validatedTotals(
        inputTokens,
        cachedInputTokens,
        outputTokens,
        reasoningOutputTokens,
        totalTokens
    )

    private fun validatedTotals(
        input: Long,
        cached: Long,
        output: Long,
        reasoning: Long,
        total: Long
    ): CodexTokenTotals {
        if (
            input !in 0..MAX_TOKEN_COUNT ||
            cached !in 0..input ||
            output !in 0..MAX_TOKEN_COUNT ||
            reasoning !in 0..output ||
            total !in 0..MAX_TOKEN_COUNT ||
            total != input + output
        ) {
            throw CodexTelemetryProtocolException("Invalid token totals")
        }
        return CodexTokenTotals(input, cached, output, reasoning, total)
    }

    private fun validateModel(model: String) {
        if (
            model.isBlank() ||
            model.length > MAX_MODEL_LABEL_LENGTH ||
            model.any(Char::isISOControl)
        ) {
            throw CodexTelemetryProtocolException("Invalid model label")
        }
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val SNAPSHOT_SCHEMA_VERSION = 1
        const val SNAPSHOT_SOURCE = "codex-cli-local-jsonl"
        internal const val AUTH_KEY_CONTEXT = "codexbar-codex-telemetry-auth-v1"
        internal const val ENCRYPTION_KEY_CONTEXT = "codexbar-codex-telemetry-encryption-v1"
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val IO_TIMEOUT_MILLIS = 8_000
        private const val MAX_RESPONSE_BYTES = 256 * 1024
        private const val REQUEST_NONCE_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_CLOCK_SKEW_SECONDS = 120L
        private const val MAX_SNAPSHOT_AGE_SECONDS = 2 * 60 * 60L
        private const val MAX_CONTEXT_AGE_SECONDS = 366L * 24 * 60 * 60
        private const val MAX_DAILY_ENTRIES = 90
        private const val MAX_MODEL_ENTRIES = 9
        private const val MAX_MODEL_LABEL_LENGTH = 80
        private const val MAX_TOKEN_COUNT = 9_000_000_000_000_000L
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_ALGORITHM = "AES"

        internal fun requestCanonical(
            companionId: String,
            requestedAtEpochSeconds: Long,
            nonce: String
        ): String = listOf(
            PROTOCOL_VERSION.toString(),
            companionId,
            requestedAtEpochSeconds.toString(),
            nonce
        ).joinToString("\n")

        internal fun responseCanonical(envelope: CodexTelemetryEnvelope): String = listOf(
            envelope.protocolVersion.toString(),
            envelope.companionId,
            envelope.requestNonce,
            envelope.sentAtEpochSeconds.toString()
        ).joinToString("\n")

        internal fun deriveKey(masterKey: ByteArray, context: String): ByteArray {
            return hmacSha256(masterKey, context.toByteArray(StandardCharsets.UTF_8))
        }

        internal fun hmacSha256(key: ByteArray, payload: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(payload)
        }

        internal fun ByteArray.toUrlBase64(): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(this)

        internal fun decodeUrlBase64(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
    }
}

class CodexTelemetryAuthenticationException(
    message: String,
    cause: Throwable? = null
) : GeneralSecurityException(message, cause)

class CodexTelemetryProtocolException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
