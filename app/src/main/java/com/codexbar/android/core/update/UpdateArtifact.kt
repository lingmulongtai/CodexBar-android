package com.codexbar.android.core.update

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MAX_UPDATE_BYTES = 150L * 1024 * 1024
private const val RELEASE_DOWNLOAD_PATH = "/lingmulongtai/CodexBar-android/releases/download/"

internal data class UpdateArtifact(val version: String, val url: String, val bytes: Long, val sha256: String) {
    fun isTrusted(): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val name = parsed.pathSegments.lastOrNull() ?: return false
        return isNewerStableVersion(version, "0.0.0") && bytes in 1..MAX_UPDATE_BYTES &&
            sha256.matches(Regex("[0-9a-f]{64}")) && parsed.isHttps && parsed.host == "github.com" &&
            parsed.port == 443 && parsed.username.isEmpty() && parsed.password.isEmpty() &&
            parsed.encodedQuery == null && parsed.fragment == null &&
            name in setOf("CodexBar-Android-v$version.apk", "app-release.apk") &&
            parsed.encodedPath in setOf("${RELEASE_DOWNLOAD_PATH}v$version/$name", "$RELEASE_DOWNLOAD_PATH$version/$name")
    }
}

internal fun isTrustedUpdateHop(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
    url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
    url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

internal enum class UpdateFailure { NETWORK, INTEGRITY, PACKAGE, VERSION, SIGNER, STORAGE, UNAVAILABLE }
internal class UpdateDownloadException(val reason: UpdateFailure) : IOException(reason.name)

internal data class ApkIdentity(val packageName: String, val versionName: String?, val versionCode: Long, val signers: Set<String>)

internal fun validateUpdateIdentity(installed: ApkIdentity, archive: ApkIdentity, target: UpdateArtifact) {
    if (archive.packageName != installed.packageName) throw UpdateDownloadException(UpdateFailure.PACKAGE)
    if (archive.versionCode <= installed.versionCode || archive.versionName != target.version ||
        !isNewerStableVersion(target.version, installed.versionName.orEmpty()))
        throw UpdateDownloadException(UpdateFailure.VERSION)
    if (installed.signers.isEmpty() || archive.signers != installed.signers)
        throw UpdateDownloadException(UpdateFailure.SIGNER)
}
