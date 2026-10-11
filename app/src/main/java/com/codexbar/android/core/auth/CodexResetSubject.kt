package com.codexbar.android.core.auth

import com.codexbar.android.core.domain.model.Credential
import java.security.MessageDigest

internal fun Credential.CodexCredential.resetTrackingAccountKey(): String {
    val subject = accountId ?: codexAccountId(null, accessToken) ?: refreshToken
    return MessageDigest.getInstance("SHA-256").digest(subject.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
