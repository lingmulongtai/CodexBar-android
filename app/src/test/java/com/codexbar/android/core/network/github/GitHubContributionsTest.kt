package com.codexbar.android.core.network.github

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class GitHubContributionsTest {
    @Test fun `periods use local calendar boundaries and keep commit counts separate from activity`() = runTest {
        var request: Request? = null
        val api = GitHubDashboardApi(Json { ignoreUnknownKeys = true }) { sent ->
            request = sent
            GitHubHttpResponse(200, """{"data":{"viewer":{"isDeveloperProgramMember":true,"today":{"totalCommitContributions":2},"week":{"totalCommitContributions":8},"month":{"totalCommitContributions":12},"year":{"totalCommitContributions":123,"totalIssueContributions":3,"contributionCalendar":{"totalContributions":130,"weeks":[{"contributionDays":[{"date":"2026-11-02","contributionCount":5,"contributionLevel":"SECOND_QUARTILE"}]}]}}}}}""")
        }
        val result = api.contributions("token", Instant.parse("2026-11-02T03:00:00Z"), ZoneId.of("Asia/Tokyo"))
        val sent = request!!
        assertEquals("api.github.com", sent.url.host)
        assertEquals("/graphql", sent.url.encodedPath)
        assertEquals("Bearer token", sent.header("Authorization"))
        val buffer = Buffer(); sent.body!!.writeTo(buffer)
        val vars = Json.parseToJsonElement(buffer.readUtf8()).jsonObject["variables"]!!.jsonObject
        assertEquals("2026-11-01T15:00:00Z", vars["today"]!!.jsonPrimitive.content)
        assertEquals("2026-11-01T15:00:00Z", vars["week"]!!.jsonPrimitive.content)
        assertEquals("2026-10-31T15:00:00Z", vars["month"]!!.jsonPrimitive.content)
        assertEquals("2025-12-31T15:00:00Z", vars["year"]!!.jsonPrimitive.content)
        assertEquals(2, result.viewer.today!!.totalCommitContributions)
        assertEquals(5, result.viewer.year!!.contributionCalendar!!.weeks.single().contributionDays.single().contributionCount)
        assertFalse(result.partial)
    }

    @Test fun `partial GraphQL response does not invent unavailable counts`() = runTest {
        val api = GitHubDashboardApi(Json { ignoreUnknownKeys = true }) {
            GitHubHttpResponse(200, """{"data":{"viewer":{"today":null}},"errors":[{"message":"Forbidden"}]}""")
        }
        val result = api.contributions("token", Instant.parse("2026-01-02T00:00:00Z"), ZoneId.of("UTC"))
        assertTrue(result.partial)
        assertNull(result.viewer.today)
    }
}
