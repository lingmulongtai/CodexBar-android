package com.codexbar.android.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class AndroidUpdateVerifier @Inject constructor(@ApplicationContext private val context: Context) {
    @Suppress("DEPRECATION")
    internal fun verify(file: File, target: UpdateArtifact) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateDownloadException(UpdateFailure.PACKAGE)
        val installed = pm.getPackageInfo(context.packageName, flags)
        validateUpdateIdentity(installed.identity(), archive.identity(), target)
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.identity(): ApkIdentity {
    val certificates = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
    return ApkIdentity(packageName, versionName,
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong(),
        certificates.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet())
}
