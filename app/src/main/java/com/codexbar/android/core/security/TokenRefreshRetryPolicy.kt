package com.codexbar.android.core.security

data class TokenRefreshRetryState(
    val credentialFingerprint: String,
    val nextAttemptAtMillis: Long,
    val failureCount: Int,
    val terminal: Boolean
)

enum class TokenRefreshAttemptDecision {
    Attempt,
    SkipUntilDue,
    SkipTerminal
}

class TokenRefreshRetryPolicy(
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS
) {
    fun decision(
        state: TokenRefreshRetryState?,
        credentialFingerprint: String,
        nowMillis: Long
    ): TokenRefreshAttemptDecision {
        if (state == null || state.credentialFingerprint != credentialFingerprint) {
            return TokenRefreshAttemptDecision.Attempt
        }
        if (state.terminal) {
            return TokenRefreshAttemptDecision.SkipTerminal
        }
        return if (nowMillis >= state.nextAttemptAtMillis) {
            TokenRefreshAttemptDecision.Attempt
        } else {
            TokenRefreshAttemptDecision.SkipUntilDue
        }
    }

    fun success(
        credentialFingerprint: String,
        nextAttemptAtMillis: Long
    ): TokenRefreshRetryState {
        return TokenRefreshRetryState(
            credentialFingerprint = credentialFingerprint,
            nextAttemptAtMillis = nextAttemptAtMillis,
            failureCount = 0,
            terminal = false
        )
    }

    fun failure(
        previousState: TokenRefreshRetryState?,
        credentialFingerprint: String,
        nowMillis: Long,
        terminal: Boolean,
        retryAtMillis: Long? = null
    ): TokenRefreshRetryState {
        if (terminal) {
            return TokenRefreshRetryState(
                credentialFingerprint = credentialFingerprint,
                nextAttemptAtMillis = Long.MAX_VALUE,
                failureCount = (previousState?.failureCount ?: 0) + 1,
                terminal = true
            )
        }

        val nextFailureCount = (previousState?.takeIf {
            it.credentialFingerprint == credentialFingerprint
        }?.failureCount ?: 0) + 1
        val exponentialDelay = baseDelayMillis.saturatingShiftLeft(nextFailureCount - 1)
            .coerceAtMost(maxDelayMillis)
        val policyRetryAt = nowMillis.saturatingAdd(exponentialDelay)
        val boundedRetryAt = retryAtMillis
            ?.takeIf { it > nowMillis }
            ?.coerceAtMost(nowMillis.saturatingAdd(maxDelayMillis))
            ?: policyRetryAt

        return TokenRefreshRetryState(
            credentialFingerprint = credentialFingerprint,
            nextAttemptAtMillis = boundedRetryAt,
            failureCount = nextFailureCount,
            terminal = false
        )
    }

    private fun Long.saturatingShiftLeft(distance: Int): Long {
        var result = this
        repeat(distance.coerceAtLeast(0)) {
            if (result > Long.MAX_VALUE / 2) return Long.MAX_VALUE
            result *= 2
        }
        return result
    }

    private fun Long.saturatingAdd(delta: Long): Long {
        if (delta <= 0L) return this
        return if (this > Long.MAX_VALUE - delta) Long.MAX_VALUE else this + delta
    }

    companion object {
        private const val DEFAULT_BASE_DELAY_MILLIS = 60_000L
        private const val DEFAULT_MAX_DELAY_MILLIS = 6 * 60 * 60 * 1000L
    }
}
