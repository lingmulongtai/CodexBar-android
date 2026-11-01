package com.codexbar.android.core.network.github

import com.codexbar.android.core.domain.model.GitHubDashboardSession
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Serializable
internal data class GitHubAppInfo(@kotlinx.serialization.SerialName("client_id") val clientId: String,
    val slug: String, val owner: GitHubAppOwner)
@Serializable
internal data class GitHubAppOwner(val login: String)

@Serializable
internal data class GitHubDeviceCode(
    @kotlinx.serialization.SerialName("device_code") val deviceCode: String,
    @kotlinx.serialization.SerialName("user_code") val userCode: String,
    @kotlinx.serialization.SerialName("verification_uri") val verificationUri: String,
    @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long,
    val interval: Long = 5
) { override fun toString(): String = "GitHubDeviceCode(redacted)" }

@Serializable
internal data class GitHubTokenResponse(
    @kotlinx.serialization.SerialName("access_token") val accessToken: String? = null,
    @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String? = null,
    @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long? = null,
    val error: String? = null
) { override fun toString(): String = "GitHubTokenResponse(redacted)" }

@Serializable
internal data class GitHubProfile(
    val login: String,
    val name: String? = null,
    @kotlinx.serialization.SerialName("public_repos") val publicRepos: Int = 0,
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val email: String? = null,
    val followers: Int = 0,
    val following: Int = 0
)

@Serializable
internal data class GitHubRepository(val id: Long,
    @kotlinx.serialization.SerialName("full_name") val fullName: String,
    @kotlinx.serialization.SerialName("private") val isPrivate: Boolean = false,
    @kotlinx.serialization.SerialName("html_url") val htmlUrl: String,
    val description: String? = null,
    @kotlinx.serialization.SerialName("stargazers_count") val stars: Int = 0,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
    @kotlinx.serialization.SerialName("pushed_at") val pushedAt: String? = null,
    val language: String? = null)

internal data class GitHubRepositories(val items: List<GitHubRepository>, val complete: Boolean)

internal data class GitHubHttpResponse(val status: Int, val body: String)

/** Fixed GitHub endpoints, no redirects, and no credential logging. */
@Singleton
internal class GitHubDashboardApi internal constructor(
    private val json: Json,
    private val execute: suspend (Request) -> GitHubHttpResponse
) {
    @Inject constructor(json: Json) : this(json, ::executeGitHubRequest)

    /** GitHub publishes App client IDs; resolve ours without embedding a fixed identifier. */
    suspend fun clientId(): String {
        val app = json.decodeFromString<GitHubAppInfo>(successful(get("https://api.github.com/apps/codexbar-android-dashboard", null)))
        if (app.slug != "codexbar-android-dashboard" || app.owner.login != "lingmulongtai" ||
            !app.clientId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) throw IOException("Invalid GitHub App identifier")
        return app.clientId
    }

    suspend fun start(clientId: String): GitHubDeviceCode {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{1,128}")))
        val response = post("https://github.com/login/device/code", mapOf("client_id" to clientId, "scope" to "offline_access"))
        val code = json.decodeFromString<GitHubDeviceCode>(successful(response))
        if (code.verificationUri != "https://github.com/login/device" ||
            !code.userCode.matches(Regex("[A-Z0-9-]{4,32}")) ||
            code.deviceCode.isBlank() || code.expiresIn !in 1..1800 || code.interval !in 1..120) throw IOException("Invalid GitHub device response")
        return code
    }

    suspend fun poll(clientId: String, deviceCode: String): GitHubTokenResponse =
        json.decodeFromString(successful(post("https://github.com/login/oauth/access_token", mapOf(
            "client_id" to clientId, "device_code" to deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))))

    suspend fun refresh(session: GitHubDashboardSession): GitHubTokenResponse =
        json.decodeFromString(successful(post("https://github.com/login/oauth/access_token", mapOf(
            "client_id" to session.clientId, "refresh_token" to session.refreshToken, "grant_type" to "refresh_token"))))

    suspend fun profile(token: String): GitHubProfile =
        json.decodeFromString(successful(get("https://api.github.com/user", token)))

    suspend fun repositories(token: String): List<GitHubRepository> = repositoryPage(token, 1)

    /** GitHub App permissions limit results to repositories shared with the installation. */
    suspend fun allRepositories(token: String): GitHubRepositories {
        val collected = LinkedHashMap<Long, GitHubRepository>()
        var duplicate = false
        for (page in 1..20) {
            val batch = repositoryPage(token, page)
            batch.forEach { if (collected.put(it.id, it) != null) duplicate = true }
            if (batch.size < 100) return GitHubRepositories(collected.values.toList(), complete = !duplicate)
        }
        return GitHubRepositories(collected.values.toList(), complete = false)
    }

    private suspend fun repositoryPage(token: String, page: Int): List<GitHubRepository> =
        json.decodeFromString(successful(get("https://api.github.com/user/repos?per_page=100&sort=updated&page=$page", token)))

    private suspend fun post(url: String, fields: Map<String, String>): GitHubHttpResponse {
        val form = FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
        return execute(Request.Builder().url(url).header("Accept", "application/json").post(form).build())
    }

    private suspend fun get(url: String, token: String?): GitHubHttpResponse = execute(
        Request.Builder().url(url).header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { if (token != null) header("Authorization", "Bearer $token") }.build())

    private fun successful(response: GitHubHttpResponse): String {
        if (response.status !in 200..299) throw GitHubDashboardException(response.status)
        return response.body
    }
}

internal class GitHubDashboardException(val status: Int) : IOException("GitHub request failed ($status)")

private val githubClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .callTimeout(20, TimeUnit.SECONDS).build()

private suspend fun executeGitHubRequest(request: Request): GitHubHttpResponse = suspendCancellableCoroutine { continuation ->
    val call = githubClient.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                response.use {
                    val body = it.body ?: throw IOException("Missing GitHub response")
                    if (body.contentLength() > 1024 * 1024) throw IOException("GitHub response too large")
                    val bytes = body.byteStream().use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (out.size() + n > 1024 * 1024) throw IOException("GitHub response too large")
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    }
                    GitHubHttpResponse(it.code, bytes.decodeToString())
                }
            }
            if (continuation.isActive) continuation.resumeWith(result)
        }
    })
}
