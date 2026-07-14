package com.codexbar.android.feature.settings

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLinkFailureTest {
    @Test
    fun `recognizes direct and wrapped DNS failures`() {
        assertTrue(UnknownHostException("auth.openai.com").hasUnknownHostCause())
        assertTrue(
            IOException(
                "request failed",
                UnknownHostException("auth.openai.com")
            ).hasUnknownHostCause()
        )
        assertFalse(IOException("HTTP 500").hasUnknownHostCause())
    }
}
