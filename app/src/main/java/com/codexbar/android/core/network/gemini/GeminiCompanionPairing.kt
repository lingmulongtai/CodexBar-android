package com.codexbar.android.core.network.gemini

import com.codexbar.android.core.domain.model.Credential
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

object GeminiCompanionPairing {
    const val SCHEME = "codexbar"
    const val HOST = "gemini-pair"
    const val PROTOCOL_VERSION = 1

    fun parse(input: String): Credential.GeminiCompanionCredential {
        val trimmed = input.trim()
        require(trimmed.length in 1..MAX_PAIRING_URI_LENGTH) { "Pairing code is too long" }
        val uri = runCatching { URI(trimmed) }
            .getOrElse { throw IllegalArgumentException("Invalid pairing code", it) }
        require(uri.scheme.equals(SCHEME, ignoreCase = true)) { "Invalid pairing scheme" }
        require(uri.host.equals(HOST, ignoreCase = true)) { "Invalid pairing destination" }
        require(uri.userInfo == null && uri.fragment == null) { "Invalid pairing code" }
        require(uri.path.isNullOrEmpty() || uri.path == "/") { "Invalid pairing path" }

        val parameters = parseQuery(uri.rawQuery.orEmpty())
        require(parameters.keys == REQUIRED_PARAMETERS) { "Pairing fields are incomplete" }
        require(parameters.getValue("v") == PROTOCOL_VERSION.toString()) {
            "Unsupported companion protocol"
        }

        val address = normalizeAndValidateAddress(parameters.getValue("address"))
        val port = parameters.getValue("port").toIntOrNull()
            ?.takeIf { it in MIN_PORT..MAX_PORT }
            ?: throw IllegalArgumentException("Invalid companion port")
        val companionId = runCatching { UUID.fromString(parameters.getValue("id")) }
            .getOrElse { throw IllegalArgumentException("Invalid companion ID", it) }
            .toString()
        val sharedKey = parameters.getValue("key")
        require(sharedKey.matches(BASE64_URL_PATTERN)) { "Invalid pairing key" }
        val decodedKey = runCatching { Base64.getUrlDecoder().decode(sharedKey) }
            .getOrElse { throw IllegalArgumentException("Invalid pairing key", it) }
        require(decodedKey.size == SHARED_KEY_BYTES) { "Invalid pairing key length" }

        return Credential.GeminiCompanionCredential(
            host = address,
            port = port,
            companionId = companionId,
            sharedKeyBase64Url = sharedKey
        )
    }

    fun validate(credential: Credential.GeminiCompanionCredential) {
        normalizeAndValidateAddress(credential.host)
        require(credential.port in MIN_PORT..MAX_PORT) { "Invalid companion port" }
        require(UUID.fromString(credential.companionId).toString() == credential.companionId) {
            "Invalid companion ID"
        }
        val key = Base64.getUrlDecoder().decode(credential.sharedKeyBase64Url)
        require(key.size == SHARED_KEY_BYTES) { "Invalid pairing key length" }
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        require(rawQuery.length in 1..MAX_QUERY_LENGTH) { "Invalid pairing query" }
        val result = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            val parts = pair.split('=', limit = 2)
            require(parts.size == 2 && parts[0].isNotBlank()) { "Invalid pairing field" }
            val key = decode(parts[0])
            val value = decode(parts[1])
            require(key !in result) { "Duplicate pairing field" }
            result[key] = value
        }
        return result
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrElse { throw IllegalArgumentException("Invalid pairing encoding", it) }
    }

    private fun normalizeAndValidateAddress(value: String): String {
        val addressText = value.trim().removePrefix("[").removeSuffix("]")
        require(addressText.length in 2..MAX_ADDRESS_LENGTH) { "Invalid companion address" }
        val isIpv4 = IPV4_PATTERN.matches(addressText) && addressText.split('.').all {
            it.toIntOrNull() in 0..255
        }
        val isIpv6 = ':' in addressText && IPV6_PATTERN.matches(addressText)
        require(isIpv4 || isIpv6) { "Use a numeric local-network address" }

        val address = runCatching { InetAddress.getByName(addressText) }
            .getOrElse { throw IllegalArgumentException("Invalid companion address", it) }
        require(address.isAllowedLocalAddress()) { "Companion must be on the local network" }
        return addressText
    }

    private fun InetAddress.isAllowedLocalAddress(): Boolean {
        if (isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress) return true
        return when (this) {
            is Inet4Address -> {
                val octets = address.map { it.toInt() and 0xff }
                octets[0] == 100 && octets[1] in 64..127
            }
            is Inet6Address -> (address.first().toInt() and 0xfe) == 0xfc
            else -> false
        }
    }

    private val REQUIRED_PARAMETERS = setOf("v", "address", "port", "id", "key")
    private val BASE64_URL_PATTERN = Regex("^[A-Za-z0-9_-]{43}=?$")
    private val IPV4_PATTERN = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
    private val IPV6_PATTERN = Regex("^[0-9A-Fa-f:]+$")
    private const val SHARED_KEY_BYTES = 32
    private const val MIN_PORT = 1024
    private const val MAX_PORT = 65535
    private const val MAX_PAIRING_URI_LENGTH = 2048
    private const val MAX_QUERY_LENGTH = 1536
    private const val MAX_ADDRESS_LENGTH = 64
}
