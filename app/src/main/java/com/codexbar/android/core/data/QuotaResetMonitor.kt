package com.codexbar.android.core.data

import com.codexbar.android.core.auth.resetTrackingAccountKey
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaResetMonitor @Inject constructor(
    private val store: QuotaResetHistoryStore,
    private val prefsManager: EncryptedPrefsManager,
    private val notificationService: QuotaNotificationService
) {
    private val mutex = Mutex()

    suspend fun observe(quotas: List<QuotaInfo>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentQuotas = quotas.filter { quota ->
                val credential = prefsManager.loadCredential(quota.service) ?: return@filter false
                quota.service != AiService.CODEX ||
                    (credential as? Credential.CodexCredential)?.resetTrackingAccountKey() == quota.resetTrackingAccountKey
            }
            val activities = store.record(currentQuotas)
            if (prefsManager.isResetNotificationEnabled()) {
                activities.forEach { (service, events) ->
                    events.forEach { notificationService.showResetActivityNotification(service, it) }
                }
            }
        }
    }

    suspend fun forgetService(service: AiService) = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.deleteService(service)
            notificationService.cancelResetNotifications(service)
        }
    }

    suspend fun saveCredential(service: AiService, credential: Credential) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = prefsManager.loadCredential(service)
            val sameAccount = if (credential is Credential.CodexCredential && previous is Credential.CodexCredential) {
                credential.resetTrackingAccountKey() == previous.resetTrackingAccountKey()
            } else credential == previous
            prefsManager.saveCredential(service, credential)
            if (!sameAccount) {
                store.deleteService(service)
                notificationService.cancelResetNotifications(service)
            }
        }
    }
}
