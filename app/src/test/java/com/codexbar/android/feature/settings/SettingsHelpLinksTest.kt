package com.codexbar.android.feature.settings

import com.codexbar.android.core.domain.model.AiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHelpLinksTest {
    @Test
    fun `every provider opens its own https setup section`() {
        val expectedAnchors = mapOf(
            AiService.CLAUDE to "claude-anthropic",
            AiService.CODEX to "codex-openai--chatgpt",
            AiService.GEMINI to "gemini-google",
            AiService.COPILOT to "github-copilot",
            AiService.CURSOR to "cursor",
            AiService.ZENMUX to "zenmux"
        )

        expectedAnchors.forEach { (service, anchor) ->
            val url = accountGuideUrl(service)
            assertTrue(url.startsWith("https://github.com/lingmulongtai/CodexBar-android#"))
            assertEquals(anchor, url.substringAfter('#'))
        }
    }
}
