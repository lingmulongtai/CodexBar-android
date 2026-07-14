package com.codexbar.android.core.network

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ResponseSizeLimitInterceptorTest {
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
    fun `body at the limit remains readable`() {
        server.enqueue(MockResponse().setBody("12345678"))

        client(maxBytes = 8).newCall(request()).execute().use { response ->
            assertEquals("12345678", response.body?.string())
        }
    }

    @Test
    fun `declared oversized body is rejected before decoding`() {
        server.enqueue(
            MockResponse()
                .setBody("ignored")
                .setHeader("Content-Length", "9")
        )

        assertThrows(IOException::class.java) {
            client(maxBytes = 8).newCall(request()).execute()
        }
    }

    @Test
    fun `chunked oversized body is rejected while reading`() {
        server.enqueue(MockResponse().setChunkedBody("123456789", 3))

        client(maxBytes = 8).newCall(request()).execute().use { response ->
            assertThrows(IOException::class.java) {
                response.body?.string()
            }
        }
    }

    private fun client(maxBytes: Long): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(ResponseSizeLimitInterceptor(maxBytes))
            .build()
    }

    private fun request(): Request = Request.Builder()
        .url(server.url("/quota"))
        .build()
}
