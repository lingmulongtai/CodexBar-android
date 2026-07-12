package com.codexbar.android.core.network

import java.time.Instant
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `negative retry after falls back without negative sleep`() {
        val sleeps = mutableListOf<Long>()
        val client = client(sleeps)

        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "-1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals(listOf(1_000L), sleeps)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `missing and malformed retry after use exponential backoff`() {
        val sleeps = mutableListOf<Long>()
        val client = client(sleeps)

        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "bad"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals(listOf(1_000L, 2_000L), sleeps)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `max long retry after is capped`() {
        val sleeps = mutableListOf<Long>()
        val client = client(sleeps)

        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", Long.MAX_VALUE.toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals(listOf(60_000L), sleeps)
    }

    private fun client(sleeps: MutableList<Long>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                RetryInterceptor(
                    maxRetries = 3,
                    maxWaitSeconds = 60,
                    sleeper = { sleeps += it },
                    clock = { Instant.parse("2026-07-13T00:00:00Z") }
                )
            )
            .build()
    }
}
