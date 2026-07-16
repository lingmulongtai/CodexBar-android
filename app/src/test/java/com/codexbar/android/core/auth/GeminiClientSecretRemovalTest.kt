package com.codexbar.android.core.auth

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClientSecretRemovalTest {

    @Test
    fun `main source does not request store or transmit gemini client secrets`() {
        val mainSource = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertFalse(mainSource.contains("oauthClientSecret"))
        assertFalse(mainSource.contains("oauth_client_secret"))
        assertFalse(mainSource.contains("client_secret"))
        assertFalse(mainSource.contains("Client Secret"))
        assertFalse(mainSource.contains("cloudcode-pa.googleapis.com"))
        assertFalse(mainSource.contains("oauth2.googleapis.com"))
        assertFalse(mainSource.contains("retrieveUserQuota"))
        assertFalse(mainSource.contains("GeminiTokenRefreshService"))
        assertFalse(mainSource.contains("Credential.GeminiCredential"))
        assertTrue(mainSource.contains("Credential.GeminiCompanionCredential"))
        assertTrue(mainSource.contains("SNAPSHOT_SOURCE = \"gemini-cli-terminal\""))
    }
}
