package com.codexbar.android.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRefreshRetryPolicyTest {

    private val policy = TokenRefreshRetryPolicy(
        baseDelayMillis = 1_000,
        maxDelayMillis = 8_000
    )

    @Test
    fun `new or changed credential attempts immediately`() {
        assertEquals(
            TokenRefreshAttemptDecision.Attempt,
            policy.decision(state = null, credentialFingerprint = "fingerprint-a", nowMillis = 1_000)
        )

        val oldState = TokenRefreshRetryState(
            credentialFingerprint = "fingerprint-a",
            nextAttemptAtMillis = Long.MAX_VALUE,
            failureCount = 99,
            terminal = true
        )

        assertEquals(
            TokenRefreshAttemptDecision.Attempt,
            policy.decision(oldState, credentialFingerprint = "fingerprint-b", nowMillis = 1_000)
        )
    }

    @Test
    fun `successful provider is skipped until its own next due time`() {
        val state = policy.success(
            credentialFingerprint = "codex-account",
            nextAttemptAtMillis = 10_000
        )

        assertEquals(
            TokenRefreshAttemptDecision.SkipUntilDue,
            policy.decision(state, credentialFingerprint = "codex-account", nowMillis = 9_999)
        )
        assertEquals(
            TokenRefreshAttemptDecision.Attempt,
            policy.decision(state, credentialFingerprint = "codex-account", nowMillis = 10_000)
        )
    }

    @Test
    fun `failures back off per provider without becoming terminal`() {
        val firstFailure = policy.failure(
            previousState = null,
            credentialFingerprint = "gemini-account",
            nowMillis = 10_000,
            terminal = false
        )
        val secondFailure = policy.failure(
            previousState = firstFailure,
            credentialFingerprint = "gemini-account",
            nowMillis = 20_000,
            terminal = false
        )

        assertFalse(firstFailure.terminal)
        assertEquals(1, firstFailure.failureCount)
        assertEquals(11_000, firstFailure.nextAttemptAtMillis)
        assertEquals(2, secondFailure.failureCount)
        assertEquals(22_000, secondFailure.nextAttemptAtMillis)
    }

    @Test
    fun `terminal failure is skipped until the account fingerprint changes`() {
        val state = policy.failure(
            previousState = null,
            credentialFingerprint = "old-account",
            nowMillis = 10_000,
            terminal = true
        )

        assertTrue(state.terminal)
        assertEquals(
            TokenRefreshAttemptDecision.SkipTerminal,
            policy.decision(state, credentialFingerprint = "old-account", nowMillis = Long.MAX_VALUE)
        )
        assertEquals(
            TokenRefreshAttemptDecision.Attempt,
            policy.decision(state, credentialFingerprint = "new-account", nowMillis = Long.MAX_VALUE)
        )
    }
}
