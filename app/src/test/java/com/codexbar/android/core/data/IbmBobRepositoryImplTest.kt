package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.ibmbob.IbmBobApiService
import com.codexbar.android.core.network.ibmbob.IbmBobDto
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Response

class IbmBobRepositoryImplTest {
    private val prefsManager = mock(EncryptedPrefsManager::class.java)
    private val credential = Credential.ProviderSecretCredential(
        service = AiService.IBM_BOB,
        kind = ProviderSecretKind.API_KEY,
        accessToken = "bob-api-key"
    )

    @Test
    fun `decodes live profile field names and numeric reset`() {
        val profile = Json { ignoreUnknownKeys = true }.decodeFromString<IbmBobDto.ProfileResponse>(
            """
            {
              "instances": [{
                "instance_id": "instance-one",
                "instance_name": "Personal",
                "user_id": "user-one",
                "plan_name": "Pro+",
                "refresh_at": 1788220800,
                "region_domain": "us-east.bob.ibm.com",
                "teams": [{"id": "team-one", "budget_limit": 40, "usage": 10}]
              }]
            }
            """.trimIndent()
        )

        val instance = profile.instances.single()
        assertEquals("Personal", instance.instanceName)
        assertEquals("1788220800", instance.refreshAt?.content)
        assertEquals(40.0, instance.teams.single().budgetLimit ?: -1.0, 0.0001)
    }

    @Test
    fun `aggregates regional team budgets and uses API key authorization`() = runTest {
        val api = FakeIbmBobApiService(
            profileResponse = Response.success(
                IbmBobDto.ProfileResponse(
                    instances = listOf(
                        instance(
                            id = "personal",
                            userId = "user-one",
                            region = "us-east.bob.ibm.com",
                            plan = "Pro+",
                            reset = JsonPrimitive("2026-09-01T00:00:00Z"),
                            teamId = "solo",
                            limit = 40.0
                        ),
                        instance(
                            id = "work",
                            userId = "user-two",
                            region = "api.eu-de.bob.ibm.com",
                            plan = "Enterprise",
                            reset = JsonPrimitive(1_788_220_800),
                            teamId = "platform",
                            limit = 160.0
                        )
                    )
                )
            ),
            budgets = mapOf(
                "solo" to IbmBobDto.TeamBudgetResponse(usage = 10.0),
                "platform" to IbmBobDto.TeamBudgetResponse(usage = 25.0)
            )
        )
        val repository = IbmBobRepositoryImpl(api, prefsManager)
        `when`(prefsManager.loadCredential(AiService.IBM_BOB)).thenReturn(credential)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quota = (result as Result.Success).value
        assertEquals(AiService.IBM_BOB, quota.service)
        assertEquals(0.175, quota.windows.single().utilization, 0.0001)
        assertEquals(35.0, quota.extraUsage?.usedCredits ?: -1.0, 0.0001)
        assertEquals(200.0, quota.extraUsage?.monthlyLimit ?: -1.0, 0.0001)
        assertTrue(quota.tier.orEmpty().contains("Enterprise"))
        assertEquals(listOf("api.us-east.bob.ibm.com", "api.eu-de.bob.ibm.com"), api.teamUrls.map { it.host })
        assertTrue(api.authorizations.all { it == "Apikey bob-api-key" })
        assertEquals(listOf("personal", "work"), api.instanceHeaders)
        assertEquals(listOf("solo", "platform"), api.teamHeaders)
    }

    @Test
    fun `uses bearer authorization only for a structurally valid JWT`() = runTest {
        val api = FakeIbmBobApiService(
            profileResponse = Response.success(
                IbmBobDto.ProfileResponse(
                    instances = listOf(
                        instance("personal", "user-one", null, null, null, "solo", 40.0)
                    )
                )
            ),
            budgets = mapOf("solo" to IbmBobDto.TeamBudgetResponse(usage = 4.0))
        )
        val repository = IbmBobRepositoryImpl(api, prefsManager)
        val jwt = credential.copy(accessToken = "header.eyJzdWIiOiJ1c2VyIn0.signature")

        val result = repository.validateCredential(jwt)

        assertTrue(result is Result.Success)
        assertTrue(api.authorizations.all { it == "Bearer ${jwt.accessToken}" })
        assertTrue(isIbmBobJwt(jwt.accessToken))
        assertFalse(isIbmBobJwt("header.not-json.signature"))
    }

    @Test
    fun `rejects regional URL bypasses before sending a team credential`() = runTest {
        val unsafeRegions = listOf(
            "evil.example",
            "evil.example/x.bob.ibm.com",
            "bob.ibm.com.evil.example",
            "x@evil.example",
            "evil.example?next=.bob.ibm.com",
            "evil.example#.bob.ibm.com",
            "us-east.bob.ibm.com:443"
        )

        unsafeRegions.forEach { region ->
            val api = FakeIbmBobApiService(
                profileResponse = Response.success(
                    IbmBobDto.ProfileResponse(
                        instances = listOf(
                            instance("personal", "user-one", region, null, null, "solo", 40.0)
                        )
                    )
                ),
                budgets = emptyMap()
            )
            val result = IbmBobRepositoryImpl(api, prefsManager).validateCredential(credential)

            assertTrue("Expected failure for $region", result is Result.Failure)
            assertTrue((result as Result.Failure).error is AppError.ParseError)
            assertEquals(0, api.teamUrls.size)
        }
    }

    @Test
    fun `maps rejected key to terminal authentication error`() = runTest {
        val api = FakeIbmBobApiService(
            profileResponse = Response.error(401, "unauthorized".toResponseBody()),
            budgets = emptyMap()
        )

        val result = IbmBobRepositoryImpl(api, prefsManager).validateCredential(credential)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError && error.isTerminal)
        assertEquals(0, api.teamUrls.size)
    }

    private fun instance(
        id: String,
        userId: String,
        region: String?,
        plan: String?,
        reset: JsonPrimitive?,
        teamId: String,
        limit: Double?
    ) = IbmBobDto.Instance(
        instanceId = id,
        legacyName = id,
        userId = userId,
        planName = plan,
        refreshAt = reset,
        regionDomain = region,
        teams = listOf(IbmBobDto.Team(id = teamId, name = teamId, budgetLimit = limit))
    )
}

private class FakeIbmBobApiService(
    private val profileResponse: Response<IbmBobDto.ProfileResponse>,
    private val budgets: Map<String, IbmBobDto.TeamBudgetResponse>
) : IbmBobApiService {
    val authorizations = mutableListOf<String>()
    val teamUrls = mutableListOf<HttpUrl>()
    val instanceHeaders = mutableListOf<String>()
    val teamHeaders = mutableListOf<String>()

    override suspend fun getProfile(
        authorization: String
    ): Response<IbmBobDto.ProfileResponse> {
        authorizations += authorization
        return profileResponse
    }

    override suspend fun getTeamBudget(
        url: HttpUrl,
        authorization: String,
        instanceId: String,
        teamId: String
    ): Response<IbmBobDto.TeamBudgetResponse> {
        authorizations += authorization
        teamUrls += url
        instanceHeaders += instanceId
        teamHeaders += teamId
        return Response.success(checkNotNull(budgets[teamId]))
    }

}
