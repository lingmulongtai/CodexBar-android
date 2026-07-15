package com.codexbar.android.core.network.oauth

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class GitHubDeviceAuthServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: GitHubDeviceAuthService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(JSON.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubDeviceAuthService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `device-code request asks GitHub for JSON and decodes it`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "device_code": "device-code",
                  "user_code": "E390-AA82",
                  "verification_uri": "https://github.com/login/device",
                  "expires_in": 899,
                  "interval": 5
                }
                """.trimIndent()
            )
        )

        val response = service.requestDeviceCode()

        assertTrue(response.isSuccessful)
        assertEquals("device-code", response.body()?.deviceCode)
        assertEquals("E390-AA82", response.body()?.userCode)
        val request = server.takeRequest()
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("/login/device/code", request.path)
        assertTrue(request.body.readUtf8().contains("scope=read%3Auser"))
    }

    @Test
    fun `access-token poll asks GitHub for JSON and decodes it`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "access_token": "github-token",
                  "token_type": "bearer",
                  "scope": "read:user"
                }
                """.trimIndent()
            )
        )

        val response = service.pollForAccessToken(deviceCode = "device-code")

        assertTrue(response.isSuccessful)
        assertEquals("github-token", response.body()?.accessToken)
        val request = server.takeRequest()
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("/login/oauth/access_token", request.path)
        assertTrue(request.body.readUtf8().contains("device_code=device-code"))
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
