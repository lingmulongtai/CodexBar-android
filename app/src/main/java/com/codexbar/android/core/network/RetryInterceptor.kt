package com.codexbar.android.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val maxWaitSeconds: Long = 60,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val clock: () -> Instant = { Instant.now() }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0

        while (response.code == 429 && attempt < maxRetries) {
            val retryAfter = response.header("Retry-After")
            response.close()
            attempt++

            val waitMs = RetryAfter.parseDelayMillis(
                value = retryAfter,
                now = clock(),
                maxDelay = Duration.ofSeconds(maxWaitSeconds.coerceAtLeast(0))
            ) ?: exponentialBackoffMillis(attempt)

            try {
                sleeper(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Retry interrupted")
            }

            response = chain.proceed(request)
        }

        return response
    }

    private fun exponentialBackoffMillis(attempt: Int): Long {
        val maxWaitMillis = maxWaitSeconds.coerceAtLeast(0) * 1000
        val backoff = 2.0.pow(attempt - 1).toLong() * 1000
        return min(backoff, maxWaitMillis)
    }
}
