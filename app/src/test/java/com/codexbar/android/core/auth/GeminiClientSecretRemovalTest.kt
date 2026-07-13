package com.codexbar.android.core.auth

import java.io.File
import org.junit.Assert.assertFalse
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
    }
}
