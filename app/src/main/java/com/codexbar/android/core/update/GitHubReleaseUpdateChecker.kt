package com.codexbar.android.core.update

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    val releasePageUrl: String,
    val artifact: UpdateArtifact? = null
)

internal sealed interface UpdateCheckResult {
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object VersionNotComparable : UpdateCheckResult
    data object Unavailable : UpdateCheckResult
}

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

    internal suspend fun checkForUpdate(currentVersion: String): AvailableUpdate? =
        (checkForUpdates(currentVersion) as? UpdateCheckResult.Available)?.update

    internal suspend fun checkForUpdates(currentVersion: String): UpdateCheckResult {
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(latestReleaseApiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .header("User-Agent", "CodexBar-Android/$currentVersion")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext UpdateCheckResult.Unavailable
                    val body = response.body?.string() ?: return@withContext UpdateCheckResult.Unavailable
                    val release = json.decodeFromString<GitHubReleasePayload>(body)
                    if (release.draft || release.prerelease || stableVersionParts(release.tagName) == null)
                        return@withContext UpdateCheckResult.Unavailable
                    if (stableVersionParts(currentVersion) == null)
                        return@withContext UpdateCheckResult.VersionNotComparable
                    if (!isNewerStableVersion(release.tagName, currentVersion))
                        return@withContext UpdateCheckResult.UpToDate

                    val releasePageUrl = trustedReleaseUrl(release.htmlUrl) ?: LATEST_RELEASE_PAGE_URL
                    val selectedAsset = listOf(
                        "CodexBar-Android-v${release.tagName.removePrefix("v")}.apk",
                        "app-release.apk"
                    ).firstNotNullOfOrNull { name ->
                        release.assets.firstNotNullOfOrNull { asset ->
                            asset.takeIf { asset.name == name && trustedApkUrl(asset.browserDownloadUrl, release.tagName, name) }
                        }
                    }
                    val version = release.tagName.removePrefix("v")
                    val artifact = selectedAsset?.let { asset ->
                        UpdateArtifact(version, asset.browserDownloadUrl, asset.size,
                            asset.digest.orEmpty().removePrefix("sha256:").lowercase())
                            .takeIf { it.isTrusted() && asset.digest?.startsWith("sha256:") == true }
                    }
                    UpdateCheckResult.Available(AvailableUpdate(
                        version = version,
                        downloadUrl = selectedAsset?.browserDownloadUrl ?: releasePageUrl,
                        releasePageUrl = releasePageUrl,
                        artifact = artifact
                    ))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UpdateCheckResult.Unavailable
        }
    }

    companion object {
        private const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/lingmulongtai/CodexBar-android/releases/latest"
        private const val LATEST_RELEASE_PAGE_URL =
            "https://github.com/lingmulongtai/CodexBar-android/releases/latest"
        private const val TRUSTED_RELEASE_PATH_PREFIX =
            "/lingmulongtai/CodexBar-android/releases/"

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

        private fun trustedApkUrl(rawUrl: String, tag: String, name: String): Boolean {
            val url = rawUrl.toHttpUrlOrNull() ?: return false
            return trustedReleaseUrl(rawUrl) != null && url.port == 443 &&
                url.username.isEmpty() && url.password.isEmpty() &&
                url.encodedQuery == null && url.fragment == null &&
                url.encodedPath == "${TRUSTED_RELEASE_PATH_PREFIX}download/$tag/$name"
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
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
    val digest: String? = null
)
