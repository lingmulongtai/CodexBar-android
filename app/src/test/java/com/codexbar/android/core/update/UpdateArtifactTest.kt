package com.codexbar.android.core.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class UpdateArtifactTest {
    private val target = UpdateArtifact("0.17.0", "https://github.com/lingmulongtai/CodexBar-android/releases/download/v0.17.0/CodexBar-Android-v0.17.0.apk", 1234, "a".repeat(64))
    private val installed = ApkIdentity("com.codexbar.android", "0.16.0", 45, setOf("production"))
    private val archive = installed.copy(versionName = "0.17.0", versionCode = 46)

    @Test fun `only exact owned HTTPS APK targets and bounded digests are accepted`() {
        assertTrue(target.isTrusted())
        listOf(target.copy(bytes = 0), target.copy(bytes = MAX_UPDATE_BYTES + 1), target.copy(sha256 = ""),
            target.copy(url = target.url.replace("github.com", "github.com.evil.test")),
            target.copy(url = target.url.replace("https:", "http:")), target.copy(url = target.url + "?x=1"),
            target.copy(url = target.url.replace("v0.17.0/", "v0.16.0/")),
            target.copy(url = target.url.replace("lingmulongtai", "someone"))).forEach { assertFalse(it.toString(), it.isTrusted()) }
    }

    @Test fun `redirects cannot escape release hosts or downgrade transport`() {
        assertTrue(isTrustedUpdateHop("https://release-assets.githubusercontent.com/asset?signature=public".toHttpUrl()))
        listOf("http://github.com/a", "https://example.com/a", "https://github.com:444/a", "https://user:pass@github.com/a")
            .forEach { assertFalse(isTrustedUpdateHop(it.toHttpUrl())) }
    }

    @Test fun `install requires matching package newer selected version and exact current signer set`() {
        validateUpdateIdentity(installed, archive, target)
        listOf(archive.copy(packageName = "other"), archive.copy(versionName = "0.18.0"), archive.copy(versionCode = 45),
            archive.copy(signers = emptySet()), archive.copy(signers = setOf("other")),
            archive.copy(signers = setOf("production", "other"))).forEach {
            assertThrows(UpdateDownloadException::class.java) { validateUpdateIdentity(installed, it, target) }
        }
    }
}
