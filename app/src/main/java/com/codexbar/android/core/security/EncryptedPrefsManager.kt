package com.codexbar.android.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private val prefs: SharedPreferences
        get() = cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createPrefs().also { cachedPrefs = it }
        }

    private fun createPrefs(): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            if (!e.isSecureStoreFailure()) throw e
            purgeSecureStore()
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private inline fun <T> readSecureStore(defaultValue: T, block: (SharedPreferences) -> T): T {
        return try {
            block(prefs)
        } catch (e: Exception) {
            if (!e.isSecureStoreFailure()) throw e
            purgeSecureStore()
            defaultValue
        }
    }

    private inline fun writeSecureStore(block: (SharedPreferences) -> Unit) {
        try {
            block(prefs)
        } catch (e: Exception) {
            if (!e.isSecureStoreFailure()) throw e
            purgeSecureStore()
            throw e
        }
    }

    private fun purgeSecureStore() {
        cachedPrefs = null
        context.deleteSharedPreferences(SECURE_PREFS_NAME)
    }

    private fun Exception.isSecureStoreFailure(): Boolean {
        return this is SecurityException ||
            this is GeneralSecurityException ||
            this is IOException ||
            this is IllegalStateException
    }

    fun saveCredential(service: AiService, credential: Credential) {
        writeSecureStore { prefs ->
            val editor = prefs.edit()
            val prefix = service.name

            editor.putString("${prefix}_access_token", credential.accessToken)
            editor.putString("${prefix}_refresh_token", credential.refreshToken)

            when (credential) {
                is Credential.ClaudeCredential -> {
                    credential.expiresAt?.let {
                        editor.putLong("${prefix}_expires_at", it.epochSecond)
                    }
                    credential.scopes?.let {
                        editor.putString("${prefix}_scopes", it)
                    }
                    credential.rateLimitTier?.let {
                        editor.putString("${prefix}_rate_limit_tier", it)
                    }
                }
                is Credential.CodexCredential -> {
                    credential.accountId?.let {
                        editor.putString("${prefix}_account_id", it)
                    }
                }
                is Credential.GeminiCredential -> {
                    editor.putLong("${prefix}_expires_at_ms", credential.expiresAtMs)
                    editor.putString("${prefix}_oauth_client_id", credential.oauthClientId)
                    editor.putString("${prefix}_oauth_client_secret", credential.oauthClientSecret)
                }
            }

            editor.apply() // atomic write via SharedPreferences commit semantics
        }
    }

    fun loadCredential(service: AiService): Credential? {
        return readSecureStore(null) { prefs ->
            val prefix = service.name
            val accessToken = prefs.getString("${prefix}_access_token", null) ?: return@readSecureStore null

            when (service) {
                AiService.CLAUDE -> {
                    val refreshToken = prefs.getString("${prefix}_refresh_token", null)
                    val expiresAt = prefs.getLong("${prefix}_expires_at", -1L)
                        .takeIf { it > 0 }
                        ?.let { Instant.ofEpochSecond(it) }
                    val scopes = prefs.getString("${prefix}_scopes", null)
                    val rateLimitTier = prefs.getString("${prefix}_rate_limit_tier", null)
                    Credential.ClaudeCredential(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt,
                        scopes = scopes,
                        rateLimitTier = rateLimitTier
                    )
                }
                AiService.CODEX -> {
                    val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: return@readSecureStore null
                    val accountId = prefs.getString("${prefix}_account_id", null)
                    Credential.CodexCredential(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        accountId = accountId
                    )
                }
                AiService.GEMINI -> {
                    val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: return@readSecureStore null
                    val expiresAtMs = prefs.getLong("${prefix}_expires_at_ms", -1L)
                        .takeIf { it > 0 } ?: return@readSecureStore null
                    val clientId = prefs.getString("${prefix}_oauth_client_id", null) ?: return@readSecureStore null
                    val clientSecret = prefs.getString("${prefix}_oauth_client_secret", null) ?: return@readSecureStore null
                    Credential.GeminiCredential(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAtMs = expiresAtMs,
                        oauthClientId = clientId,
                        oauthClientSecret = clientSecret
                    )
                }
            }
        }
    }

    fun deleteCredential(service: AiService) {
        writeSecureStore { prefs ->
            val prefix = service.name
            val editor = prefs.edit()

            val keys = prefs.all.keys.filter { it.startsWith(prefix) }
            keys.forEach { editor.remove(it) }

            editor.apply()
        }
    }

    fun deleteAllCredentials() {
        writeSecureStore { prefs ->
            prefs.edit().clear().apply()
        }
    }

    fun hasCredential(service: AiService): Boolean {
        return readSecureStore(false) { prefs ->
            prefs.getString("${service.name}_access_token", null) != null
        }
    }

    fun getRefreshInterval(): Long {
        return readSecureStore(30L) { prefs ->
            prefs.getLong("refresh_interval_minutes", 30L)
        }
    }

    fun setRefreshInterval(minutes: Long) {
        writeSecureStore { prefs ->
            prefs.edit().putLong("refresh_interval_minutes", minutes).apply()
        }
    }

    fun isNotificationsEnabled(): Boolean {
        return readSecureStore(true) { prefs ->
            prefs.getBoolean("notifications_enabled", true)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        writeSecureStore { prefs ->
            prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        }
    }

    fun saveResetTimes(service: AiService, windows: List<Pair<String, Instant?>>) {
        writeSecureStore { prefs ->
            val editor = prefs.edit()
            windows.forEach { (label, resetsAt) ->
                val key = "${service.name}_${label}_resets_at"
                if (resetsAt != null) {
                    editor.putLong(key, resetsAt.epochSecond)
                } else {
                    editor.remove(key)
                }
            }
            editor.apply()
        }
    }

    fun loadResetTimes(service: AiService): Map<String, Instant> {
        return readSecureStore(emptyMap()) { prefs ->
            val prefix = "${service.name}_"
            val suffix = "_resets_at"
            prefs.all
                .filter { it.key.startsWith(prefix) && it.key.endsWith(suffix) }
                .mapNotNull { (key, value) ->
                    val label = key.removePrefix(prefix).removeSuffix(suffix)
                    val epochSecond = (value as? Long)?.takeIf { it > 0 } ?: return@mapNotNull null
                    label to Instant.ofEpochSecond(epochSecond)
                }
                .toMap()
        }
    }

    companion object {
        const val SECURE_PREFS_NAME = "codexbar_secure_prefs"
    }
}
