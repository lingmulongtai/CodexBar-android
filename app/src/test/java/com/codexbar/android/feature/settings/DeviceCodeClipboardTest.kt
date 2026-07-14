package com.codexbar.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCodeClipboardTest {
    @Test
    fun `removes separators so split fields can accept one paste`() {
        assertEquals("ABCDEFGH", deviceCodeForClipboard("abcd-efgh"))
        assertEquals("12345678", deviceCodeForClipboard("1234 5678"))
    }
}
