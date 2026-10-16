package com.codexbar.android.core.network.desktop

import com.codexbar.android.core.domain.model.DesktopBridgeCredential
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
open class DesktopBridgeClient @Inject constructor(
    private val json: Json
) {
    private val secureRandom = SecureRandom()

    open suspend fun fetchSnapshot(
        credential: DesktopBridgeCredential,
        now: Instant = Instant.now()
    ): DesktopBridgeSnapshot = withContext(Dispatchers.IO) {
        DesktopBridgePairing.validate(credential)
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
        val request = DesktopBridgeRequest(
            protocolVersion = PROTOCOL_VERSION,
            companionId = credential.companionId,
            requestedAtEpochSeconds = requestedAt,
            nonce = nonce,
            signature = signature
        )

        val responseLine = exchange(request, credential)

        val envelope = runCatching {
            json.decodeFromString<DesktopBridgeEnvelope>(responseLine)
        }.getOrElse { throw DesktopBridgeProtocolException("Invalid companion envelope", it) }
        validateEnvelope(envelope, credential, nonce, now)
        val plaintext = decryptEnvelope(envelope, encryptionKey)
        val snapshot = runCatching {
            json.decodeFromString<DesktopBridgeSnapshot>(plaintext)
        }.getOrElse { throw DesktopBridgeProtocolException("Invalid quota snapshot", it) }
        validateSnapshot(snapshot, now)
        snapshot
    }

    private suspend fun exchange(
        request: DesktopBridgeRequest,
        credential: DesktopBridgeCredential
    ): String = suspendCancellableCoroutine { continuation ->
        val socket = Socket()
        continuation.invokeOnCancellation { runCatching { socket.close() } }
        try {
            val response = socket.use {
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
            continuation.resume(response)
        } catch (error: Exception) {
            continuation.resumeWithException(error)
        }
    }

    private fun readLimitedLine(socket: Socket): String {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        val deadlineNanos = System.nanoTime() + IO_TIMEOUT_MILLIS * 1_000_000L
        while (output.size() <= MAX_RESPONSE_BYTES) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0) throw java.net.SocketTimeoutException("Companion response timed out")
            socket.soTimeout = ((remainingNanos + 999_999L) / 1_000_000L).toInt().coerceAtLeast(1)
            val next = input.read()
            if (next == -1) throw IOException("Companion closed the connection")
            if (next == '\n'.code) {
                return output.toString(StandardCharsets.UTF_8.name()).trimEnd('\r')
            }
            output.write(next)
        }
        throw DesktopBridgeProtocolException("Companion response is too large")
    }

    private fun validateEnvelope(
        envelope: DesktopBridgeEnvelope,
        credential: DesktopBridgeCredential,
        requestNonce: String,
        now: Instant
    ) {
        if (
            envelope.protocolVersion != PROTOCOL_VERSION ||
            envelope.companionId != credential.companionId ||
            envelope.requestNonce != requestNonce ||
            envelope.sentAtEpochSeconds !in (now.epochSecond - MAX_CLOCK_SKEW_SECONDS)..(now.epochSecond + MAX_CLOCK_SKEW_SECONDS)
        ) {
            throw DesktopBridgeAuthenticationException(
                "Companion response authentication failed"
            )
        }
    }

    private fun decryptEnvelope(
        envelope: DesktopBridgeEnvelope,
        encryptionKey: ByteArray
    ): String {
        return try {
            val iv = decodeUrlBase64(envelope.iv)
            if (iv.size != GCM_IV_BYTES) {
                throw DesktopBridgeAuthenticationException("Invalid companion IV")
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
        } catch (error: DesktopBridgeAuthenticationException) {
            throw error
        } catch (error: GeneralSecurityException) {
            throw DesktopBridgeAuthenticationException(
                "Companion response authentication failed",
                error
            )
        } catch (error: IllegalArgumentException) {
            throw DesktopBridgeAuthenticationException(
                "Companion response encoding is invalid",
                error
            )
        }
    }

    internal fun validateSnapshot(snapshot: DesktopBridgeSnapshot, now: Instant) {
        validateSharedData(snapshot, now)
        if (snapshot.schemaVersion != 1 || snapshot.source != SNAPSHOT_SOURCE ||
            snapshot.providers.size > 32 || snapshot.providers.map { it.id }.toSet().size != snapshot.providers.size ||
            snapshot.generatedAtEpochSeconds < now.epochSecond - MAX_CLOCK_SKEW_SECONDS ||
            snapshot.generatedAtEpochSeconds > now.epochSecond + MAX_CLOCK_SKEW_SECONDS) {
            throw DesktopBridgeProtocolException("Invalid Windows usage snapshot")
        }
        snapshot.providers.forEach { provider ->
            if (!provider.id.matches(Regex("[a-z0-9]{1,32}")) ||
                provider.status !in setOf("ready", "needsAuthentication", "expiredSession", "localRuntimeOffline", "unavailable") ||
                provider.updatedAtEpochSeconds < 0 || provider.updatedAtEpochSeconds > now.epochSecond + MAX_CLOCK_SKEW_SECONDS ||
                provider.windows.size > 32 || provider.windows.map { it.label.lowercase() }.toSet().size != provider.windows.size ||
                provider.subjectId?.let { !it.matches(Regex("[A-Za-z0-9_-]{43}")) || decodeUrlBase64(it).size != 32 } == true) {
                throw DesktopBridgeProtocolException("Invalid Windows provider snapshot")
            }
            provider.windows.forEach { window ->
                if (window.label.isBlank() || window.label.length > 64 || window.label.hasControlCharacter() ||
                    !window.usedFraction.isFinite() || window.usedFraction !in 0.0..1.0 ||
                    window.windowDurationSeconds?.let { it !in 1..366L * 24 * 60 * 60 } == true ||
                    window.resetsAtEpochSeconds?.let { it !in 0..now.epochSecond + 366L * 24 * 60 * 60 } == true) {
                    throw DesktopBridgeProtocolException("Invalid Windows quota window")
                }
            }
            provider.budget?.let { budget ->
                if (!budget.used.isFinite() || budget.used < 0 || !budget.limit.isFinite() || budget.limit <= 0 || !budget.currency.matches(Regex("[A-Za-z]{1,8}"))) {
                    throw DesktopBridgeProtocolException("Invalid Windows budget")
                }
            }
            provider.balance?.let { balance ->
                if (!balance.amount.isFinite() || balance.amount < 0 || !balance.currency.matches(Regex("[A-Za-z]{1,8}"))) {
                    throw DesktopBridgeProtocolException("Invalid Windows balance")
                }
            }
            provider.resetCredits?.let { credits ->
                if (credits.availableCount < 0 || credits.expiresAtEpochSeconds.size > 64 ||
                    credits.expiresAtEpochSeconds.any { it !in 0..now.epochSecond + 366L * 24 * 60 * 60 }) {
                    throw DesktopBridgeProtocolException("Invalid Windows reset credits")
                }
            }
            provider.localUsage?.let { usage ->
                val validDate = runCatching { java.time.LocalDate.parse(usage.todayDate) }.isSuccess
                val validPeriod = usage.reportingPeriod == null || usage.reportingPeriod in setOf("all", "month-to-date") ||
                    usage.reportingPeriod.removePrefix("rolling:").toIntOrNull()?.let {
                        usage.reportingPeriod == "rolling:$it" && it in 1..365
                    } == true
                if (!validDate || usage.todayDate.length != 10 || !validPeriod ||
                    usage.updatedAtEpochSeconds !in 0..now.epochSecond + MAX_CLOCK_SKEW_SECONDS ||
                    listOfNotNull(usage.todayTokens, usage.thirtyDayTokens, usage.periodTokens).any { it < 0 } ||
                    listOfNotNull(usage.todayCostUsd, usage.thirtyDayCostUsd, usage.periodCostUsd).any { !it.isFinite() || it !in 0.0..1e12 } ||
                    (usage.reportingPeriod == null && (usage.periodTokens != null || usage.periodCostUsd != null)) ||
                    usage.topModel?.matches(Regex("[A-Za-z0-9_.:-]{1,64}")) == false ||
                    usage.incompleteRequestCount?.let { it < 0 } == true) {
                    throw DesktopBridgeProtocolException("Invalid Windows local totals")
                }
            }
        }
    }
    private fun validateSharedData(snapshot: DesktopBridgeSnapshot, now: Instant) {
        fun invalid(): Nothing = throw DesktopBridgeProtocolException("Invalid Windows shared history or spend")
        snapshot.providers.forEach { provider ->
            if (provider.historyScopeId?.matches(Regex("[A-Za-z0-9_-]{43}")) == false ||
                provider.history.size > 8 || (provider.history.isNotEmpty() && provider.historyScopeId == null) ||
                provider.history.map { it.label }.distinct().size != provider.history.size) invalid()
            provider.history.forEach { series ->
                if (series.label.isBlank() || series.label.length > 64 || series.label.hasControlCharacter() ||
                    provider.windows.none { it.label == series.label } || series.samples.size > 64 ||
                    series.samples.zipWithNext().any { (a, b) -> a.t >= b.t }) invalid()
                if (series.samples.any { it.t !in (now.epochSecond - 90L * 86400)..(provider.updatedAtEpochSeconds + MAX_CLOCK_SKEW_SECONDS) ||
                    !it.u.isFinite() || it.u !in 0.0..1.0 || it.r?.let { reset -> reset !in 0..now.epochSecond + 366L * 86400 } == true }) invalid()
            }
        }
        snapshot.spend?.let { spend ->
            val validPeriod = spend.reportingPeriod in setOf("all", "month-to-date") ||
                spend.reportingPeriod.removePrefix("rolling:").toIntOrNull()?.let { spend.reportingPeriod == "rolling:$it" && it in 1..365 } == true
            if (!validPeriod || spend.timeZone.isBlank() || spend.timeZone.length > 80 || spend.timeZone.hasControlCharacter() ||
                spend.updatedAtEpochSeconds !in 0..now.epochSecond + MAX_CLOCK_SKEW_SECONDS || spend.rows.size > 32 ||
                spend.rows.map { it.providerId }.distinct().size != spend.rows.size || (!spend.costTrackingEnabled && spend.rows.isNotEmpty())) invalid()
            spend.rows.forEach { row ->
                if (snapshot.providers.none { it.id == row.providerId } || !row.currency.matches(Regex("[A-Za-z]{1,8}")) ||
                    listOfNotNull(row.sevenDay, row.thirtyDay, row.periodCost).any { !it.isFinite() || it !in 0.0..1e12 } ||
                    listOfNotNull(row.sevenDayTokens, row.thirtyDayTokens, row.periodTokens).any { it < 0 } ||
                    row.daily.size > 30 || row.daily.zipWithNext().any { (a, b) -> a.day >= b.day }) invalid()
                row.daily.forEach { day ->
                    if (day.day.length != 10 || runCatching { java.time.LocalDate.parse(day.day) }.isFailure ||
                        day.tokens?.let { it < 0 } == true || (!day.isScanned && day.tokens != null) ||
                        day.costUsd?.let { !it.isFinite() || it !in 0.0..1e12 } == true) invalid()
                }
            }
        }
    }
    private fun String.hasControlCharacter(): Boolean = any(Char::isISOControl)

    companion object {
        const val PROTOCOL_VERSION = 1
        const val SNAPSHOT_SCHEMA_VERSION = 1
        const val SNAPSHOT_SOURCE = "win-codexbar"
        internal const val AUTH_KEY_CONTEXT = "codexbar-windows-auth-v1"
        internal const val ENCRYPTION_KEY_CONTEXT = "codexbar-windows-encryption-v1"
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val IO_TIMEOUT_MILLIS = 8_000
        private const val MAX_RESPONSE_BYTES = 192 * 1024
        private const val REQUEST_NONCE_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_CLOCK_SKEW_SECONDS = 120L
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
            nonce,
            "snapshot"
        ).joinToString("\n")

        internal fun responseCanonical(envelope: DesktopBridgeEnvelope): String = listOf(
            envelope.protocolVersion.toString(),
            envelope.companionId,
            envelope.requestNonce,
            envelope.sentAtEpochSeconds.toString(),
            "snapshot"
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

class DesktopBridgeAuthenticationException(
    message: String,
    cause: Throwable? = null
) : GeneralSecurityException(message, cause)

class DesktopBridgeProtocolException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
