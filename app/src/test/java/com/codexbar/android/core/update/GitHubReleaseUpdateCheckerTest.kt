package com.codexbar.android.core.update

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubReleaseUpdateCheckerTest {
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
    fun `newer stable release selects signed apk asset`() = runTest {
        server.enqueue(
            releaseResponse(
                tag = "v0.3.0",
                assetUrl = TRUSTED_APK_URL
            )
        )

        val update = checker().checkForUpdate("0.2.1")

        assertEquals("0.3.0", update?.version)
        assertEquals(TRUSTED_APK_URL, update?.downloadUrl)
        assertEquals(TRUSTED_RELEASE_URL, update?.releasePageUrl)
    }

    @Test
    fun `untrusted asset url falls back to trusted release page`() = runTest {
        server.enqueue(
            releaseResponse(
                tag = "v1.0.0",
                assetUrl = "https://example.com/app-release.apk"
            )
        )

        val update = checker().checkForUpdate("0.2.1")

        assertEquals(TRUSTED_RELEASE_URL, update?.downloadUrl)
    }

    @Test
    fun `unexpected apk asset name falls back to trusted release page`() = runTest {
        server.enqueue(
            releaseResponse(
                tag = "v1.0.0",
                assetUrl = TRUSTED_APK_URL.replace("app-release.apk", "debug.apk"),
                assetName = "debug.apk"
            )
        )

        val update = checker().checkForUpdate("0.2.1")

        assertEquals(TRUSTED_RELEASE_URL, update?.downloadUrl)
    }

    @Test
    fun `same version and prerelease tags do not prompt`() = runTest {
        server.enqueue(releaseResponse(tag = "v0.2.1", assetUrl = TRUSTED_APK_URL))
        server.enqueue(releaseResponse(tag = "v0.3.0-beta", assetUrl = TRUSTED_APK_URL))

        assertNull(checker().checkForUpdate("0.2.1"))
        assertNull(checker().checkForUpdate("0.2.1"))
    }

    @Test
    fun `version comparison is numeric and stable only`() {
        assertTrue(isNewerStableVersion("v1.10.0", "1.9.9"))
        assertTrue(isNewerStableVersion("2.0", "1.99.99"))
        assertFalse(isNewerStableVersion("1.2.3", "1.2.3"))
        assertFalse(isNewerStableVersion("1.2.2", "1.2.3"))
        assertFalse(isNewerStableVersion("1.3.0-beta", "1.2.3"))
    }

    private fun checker(): GitHubReleaseUpdateChecker {
        return GitHubReleaseUpdateChecker(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            latestReleaseApiUrl = server.url("/repos/project/releases/latest").toString()
        )
    }

    private fun releaseResponse(
        tag: String,
        assetUrl: String,
        assetName: String = "app-release.apk"
    ): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "tag_name": "$tag",
                  "html_url": "$TRUSTED_RELEASE_URL",
                  "draft": false,
                  "prerelease": false,
                  "assets": [
                    {
                      "name": "$assetName",
                      "browser_download_url": "$assetUrl"
                    }
                  ]
                }
                """.trimIndent()
            )
    }

    private companion object {
        const val TRUSTED_RELEASE_URL =
            "https://github.com/lingmulongtai/CodexBar-android/releases/tag/v0.3.0"
        const val TRUSTED_APK_URL =
            "https://github.com/lingmulongtai/CodexBar-android/releases/download/v0.3.0/app-release.apk"
    }
}
