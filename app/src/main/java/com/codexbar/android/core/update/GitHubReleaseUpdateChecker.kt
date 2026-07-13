package com.codexbar.android.core.update

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class AvailableUpdate(
    val version: String,
    val downloadUrl: String,
    val releasePageUrl: String
)

@Singleton
class GitHubReleaseUpdateChecker internal constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val latestReleaseApiUrl: String
) {
    @Inject
    constructor(json: Json) : this(
        client = defaultClient(),
        json = json,
        latestReleaseApiUrl = LATEST_RELEASE_API_URL
    )

    internal suspend fun checkForUpdate(currentVersion: String): AvailableUpdate? {
        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(latestReleaseApiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .header("User-Agent", "CodexBar-Android/$currentVersion")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val release = json.decodeFromString<GitHubReleasePayload>(body)
                    if (release.draft || release.prerelease) return@withContext null
                    if (!isNewerStableVersion(release.tagName, currentVersion)) {
                        return@withContext null
                    }

                    val releasePageUrl = trustedReleaseUrl(release.htmlUrl)
                        ?: LATEST_RELEASE_PAGE_URL
                    val downloadUrl = release.assets
                        .sortedByDescending { it.name == RELEASE_APK_NAME }
                        .firstNotNullOfOrNull { asset ->
                            asset.browserDownloadUrl
                                .takeIf { asset.name.endsWith(".apk", ignoreCase = true) }
                                ?.let(::trustedReleaseUrl)
                        }
                        ?: releasePageUrl

                    AvailableUpdate(
                        version = release.tagName.removePrefix("v"),
                        downloadUrl = downloadUrl,
                        releasePageUrl = releasePageUrl
                    )
                }
            }
        }.getOrNull()
    }

    companion object {
        private const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/lingmulongtai/CodexBar-android/releases/latest"
        private const val LATEST_RELEASE_PAGE_URL =
            "https://github.com/lingmulongtai/CodexBar-android/releases/latest"
        private const val TRUSTED_RELEASE_PATH_PREFIX =
            "/lingmulongtai/CodexBar-android/releases/"
        private const val RELEASE_APK_NAME = "app-release.apk"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        private fun trustedReleaseUrl(rawUrl: String?): String? {
            val url = rawUrl?.toHttpUrlOrNull() ?: return null
            return rawUrl.takeIf {
                url.isHttps &&
                    url.host == "github.com" &&
                    url.encodedPath.startsWith(TRUSTED_RELEASE_PATH_PREFIX)
            }
        }
    }
}

internal fun isNewerStableVersion(candidate: String, current: String): Boolean {
    val candidateParts = stableVersionParts(candidate) ?: return false
    val currentParts = stableVersionParts(current) ?: return false
    val partCount = maxOf(candidateParts.size, currentParts.size)

    repeat(partCount) { index ->
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun stableVersionParts(version: String): List<Int>? {
    val match = STABLE_VERSION_REGEX.matchEntire(version.trim()) ?: return null
    return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
}

private val STABLE_VERSION_REGEX = Regex("^v?(\\d+(?:\\.\\d+){1,3})$")

@Serializable
private data class GitHubReleasePayload(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
private data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)
