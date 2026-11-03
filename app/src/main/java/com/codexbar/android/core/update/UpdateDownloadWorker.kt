package com.codexbar.android.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.*

@HiltWorker
class UpdateDownloadWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted parameters: WorkerParameters,
    private val verifier: AndroidUpdateVerifier
) : CoroutineWorker(context, parameters) {
    override suspend fun getForegroundInfo() = foreground(0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val target = inputData.artifact() ?: return@withContext Result.failure(workDataOf("failure" to UpdateFailure.UNAVAILABLE.name))
        val partial = UpdateDownloadRepository.partialFile(applicationContext, id)
        val ready = UpdateDownloadRepository.readyFile(applicationContext, id)
        try {
            setForeground(foreground(0))
            // Prune only this app's expired update artifacts, never the current request or a fresh installer file.
            partial.parentFile?.listFiles()?.filter { it != partial && it != ready &&
                it.name.matches(Regex("[0-9a-f-]{36}\\.(apk|part)")) &&
                it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60_000L }?.forEach { it.delete() }
            setProgress(target.toData())
            ApkDownloader().download(target, partial) { received ->
                val progress = Data.Builder().putAll(target.toData()).putLong("received", received).build()
                setProgress(progress)
                setForeground(foreground((received * 100 / target.bytes).toInt().coerceIn(0, 99)))
            }
            setProgress(Data.Builder().putAll(target.toData()).putString("stage", "verify").putLong("received", target.bytes).build())
            setForeground(foreground(null))
            verifier.verify(partial, target)
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(ready)) throw UpdateDownloadException(UpdateFailure.STORAGE)
            Result.success(Data.Builder().putAll(target.toData()).putLong("received", target.bytes).build())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ready.delete()
            val failure = (error as? UpdateDownloadException)?.reason ?: if (error is IOException) UpdateFailure.NETWORK else UpdateFailure.UNAVAILABLE
            Result.failure(workDataOf("failure" to failure.name))
        } finally { partial.delete() }
    }

    private fun foreground(percent: Int?): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_download_title), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 4931,
            Intent(context, MainActivity::class.java).setData(Uri.parse("codexbar://settings")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.update_download_title))
            .setContentText(context.getString(if (percent == null) R.string.update_verifying else R.string.update_downloading))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, percent ?: 0, percent == null)
            .addAction(0, context.getString(R.string.action_cancel), WorkManager.getInstance(context).createCancelPendingIntent(id)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(4931, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(4931, notification)
    }

    private companion object { const val CHANNEL = "codexbar_updates" }
}
