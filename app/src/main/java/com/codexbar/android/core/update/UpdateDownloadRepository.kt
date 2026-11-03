package com.codexbar.android.core.update

import android.content.Context
import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import androidx.work.*
import com.codexbar.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class UpdateDownloadPhase { IDLE, QUEUED, DOWNLOADING, VERIFYING, READY, FAILED, CANCELLED }
internal data class UpdateDownloadState(
    val phase: UpdateDownloadPhase = UpdateDownloadPhase.IDLE, val id: UUID? = null,
    val version: String = "", val received: Long = 0, val total: Long = 0,
    val failure: UpdateFailure? = null
) {
    val busy get() = phase in setOf(UpdateDownloadPhase.QUEUED, UpdateDownloadPhase.DOWNLOADING, UpdateDownloadPhase.VERIFYING)
    val percent get() = if (phase == UpdateDownloadPhase.READY) 100 else
        (if (total > 0) received * 100 / total else 0).toInt().coerceIn(0, 99)
}

@Singleton
class UpdateDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context, private val verifier: AndroidUpdateVerifier
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val gate = Mutex()
    // This name is exclusively one root request with REPLACE; never append a chain.
    internal val state get() = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        infos.singleOrNull()?.let { info ->
            val version = info.tags.firstOrNull { it.startsWith(VERSION_TAG) }?.removePrefix(VERSION_TAG).orEmpty()
            if (info.state.isFinished && version.isNotEmpty() && !isNewerStableVersion(version, BuildConfig.VERSION_NAME))
                return@map UpdateDownloadState()
            val data = if (info.state.isFinished) info.outputData else info.progress
            val phase = when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UpdateDownloadPhase.QUEUED
                WorkInfo.State.RUNNING -> if (data.getString("stage") == "verify") UpdateDownloadPhase.VERIFYING else UpdateDownloadPhase.DOWNLOADING
                WorkInfo.State.SUCCEEDED -> if (readyFile(context, info.id).isFile) UpdateDownloadPhase.READY else UpdateDownloadPhase.FAILED
                WorkInfo.State.FAILED -> UpdateDownloadPhase.FAILED
                WorkInfo.State.CANCELLED -> UpdateDownloadPhase.CANCELLED
            }
            UpdateDownloadState(phase, info.id, version,
                data.getLong("received", 0), data.getLong("bytes", 0),
                data.getString("failure")?.let { name -> UpdateFailure.entries.firstOrNull { it.name == name } })
        } ?: UpdateDownloadState()
    }

    internal suspend fun start(target: UpdateArtifact) = withContext(Dispatchers.IO) {
        if (!target.isTrusted()) throw UpdateDownloadException(UpdateFailure.UNAVAILABLE)
        gate.withLock {
            if (workManager.getWorkInfosForUniqueWork(WORK_NAME).get().any { !it.state.isFinished }) return@withLock
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(target.toData()).addTag(VERSION_TAG + target.version)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request).result.get()
        }
    }

    internal suspend fun cancel() = withContext(Dispatchers.IO) {
        gate.withLock { workManager.cancelUniqueWork(WORK_NAME).result.get() }
    }

    internal suspend fun installerIntent(id: UUID): Intent = withContext(Dispatchers.IO) {
        gate.withLock {
            val info = workManager.getWorkInfosForUniqueWork(WORK_NAME).get().singleOrNull()
            if (info?.id != id || info.state != WorkInfo.State.SUCCEEDED) throw UpdateDownloadException(UpdateFailure.UNAVAILABLE)
            val target = info.outputData.artifact() ?: throw UpdateDownloadException(UpdateFailure.UNAVAILABLE)
            val file = readyFile(context, id)
            verifyUpdateBytes(file, target)
            verifier.verify(file, target)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("CodexBar update", uri) }
        }
    }

    internal companion object {
        const val WORK_NAME = "codexbar-apk-update"
        private const val VERSION_TAG = "codexbar-update-version:"
        fun readyFile(context: Context, id: UUID) = File(context.filesDir, "updates/$id.apk")
        fun partialFile(context: Context, id: UUID) = File(context.filesDir, "updates/$id.part")
    }
}

class UpdateFileProvider : FileProvider()

internal fun UpdateArtifact.toData() = workDataOf("version" to version, "url" to url, "bytes" to bytes, "sha256" to sha256)
internal fun Data.artifact(): UpdateArtifact? = UpdateArtifact(getString("version").orEmpty(), getString("url").orEmpty(),
    getLong("bytes", 0), getString("sha256").orEmpty()).takeIf { it.isTrusted() }
