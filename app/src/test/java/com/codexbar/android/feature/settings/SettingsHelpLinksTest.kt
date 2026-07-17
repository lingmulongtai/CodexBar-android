package com.codexbar.android.feature.settings

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.providerMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHelpLinksTest {
    @Test
    fun `every provider opens its own https setup section`() {
        AiService.entries.forEach { service ->
            val url = accountGuideUrl(service)
            assertTrue(url.startsWith("https://github.com/lingmulongtai/CodexBar-android#"))
            assertEquals(service.providerMetadata.guideAnchor, url.substringAfter('#'))
        }
    }
}
