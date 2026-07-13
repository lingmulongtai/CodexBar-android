package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.startup.Initializer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer as AndroidWorkManagerInitializer
import androidx.work.workDataOf
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WorkManagerInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        applySavedRefreshPolicyAsync(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(AndroidWorkManagerInitializer::class.java)
    }

    companion object {
        private const val QUOTA_WORK_NAME = "quota_periodic_refresh"
        private const val TOKEN_WORK_NAME = "token_periodic_refresh"
        private const val MANUAL_QUOTA_WORK_NAME = "quota_manual_refresh"
        const val KEY_REFRESH_SOURCE = "refresh_source"

        fun applyRefreshPolicy(context: Context, intervalMinutes: Long) {
            schedulePeriodicRefresh(context, intervalMinutes)
            scheduleTokenRefresh(context, intervalMinutes)
        }

        fun applySavedRefreshPolicyAsync(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val prefsManager = EncryptedPrefsManager(appContext)
                prefsManager.warmCache()
                applyRefreshPolicy(appContext, prefsManager.getRefreshInterval())
            }
        }

        fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long = 30) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(QUOTA_WORK_NAME)
                return
            }

            // WorkManager minimum is 15 minutes
            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<QuotaRefreshWorker>(
                effectiveInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag("quota_refresh")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                QUOTA_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun scheduleTokenRefresh(context: Context, intervalMinutes: Long = 30) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(TOKEN_WORK_NAME)
                return
            }

            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
                effectiveInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag("token_refresh")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TOKEN_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueManualQuotaRefresh(context: Context, source: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_REFRESH_SOURCE to source))
                .addTag("quota_refresh")
                .addTag("quota_refresh_manual")
                .addTag("quota_refresh_source_$source")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_QUOTA_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
