package com.codexbar.android.core.data

import com.codexbar.android.core.auth.resetTrackingAccountKey
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.DesktopBridgeCredential
import com.codexbar.android.core.domain.model.AcquisitionSettings
import com.codexbar.android.core.domain.model.AcquisitionMode
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaResetMonitor @Inject constructor(
    private val store: QuotaResetHistoryStore,
    private val prefsManager: EncryptedPrefsManager,
    private val notificationService: QuotaNotificationService
) {
    private val mutex = Mutex()
    private val revision = AtomicLong()
    private val _sourceChanges = MutableStateFlow(0L)
    val sourceChanges: StateFlow<Long> = _sourceChanges.asStateFlow()
    fun sourceRevision(): Long = revision.get()

    suspend fun consumeIfCurrent(expected: Long, publish: suspend () -> Unit): Boolean = mutex.withLock {
        if (expected != revision.get()) return@withLock false
        publish()
        true
    }

    suspend fun updateDesktopSource(
        credential: DesktopBridgeCredential?,
        services: Set<AiService>,
        valid: () -> Boolean = { true },
        cleanup: suspend (Set<AiService>) -> Unit
    ): Boolean = updateDesktopSettings(credential, AcquisitionSettings(
        if (services.isEmpty()) AcquisitionMode.PHONE else AcquisitionMode.WINDOWS,
        services, AiService.entries.toSet() - services
    ), valid, cleanup)

    suspend fun updateDesktopSettings(
        credential: DesktopBridgeCredential?,
        settings: AcquisitionSettings,
        valid: () -> Boolean = { true },
        cleanup: suspend (Set<AiService>) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (!valid()) return@withLock false
            withContext(NonCancellable) {
                val previous = prefsManager.acquisitionSettings()
                val pairingChanged = credential != prefsManager.desktopBridgeCredential()
                val affected = AiService.entries.filter { service ->
                    previous.routesToDesktop(service) != settings.routesToDesktop(service) ||
                        (service in previous.desktopServices) != (service in settings.desktopServices) ||
                        (pairingChanged && (previous.routesToDesktop(service) || settings.routesToDesktop(service)))
                }.toSet()
                prefsManager.saveDesktopSource(credential, settings)
                revision.incrementAndGet()
                affected.forEach { service ->
                    store.deactivate(service)
                    notificationService.cancelResetNotifications(service)
                }
                cleanup(affected)
                _sourceChanges.value = revision.get()
            }
            true
        }
    }

    suspend fun deleteAllSources(cleanup: suspend () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                prefsManager.deleteAllCredentials()
                revision.incrementAndGet()
                AiService.entries.forEach(store::deleteService)
                notificationService.cancelResetNotifications()
                cleanup()
                _sourceChanges.value = revision.get()
            }
        }
    }

    suspend fun observe(quotas: List<QuotaInfo>) = withContext(Dispatchers.IO) {
        mutex.withLock { observeLocked(quotas) }
    }

    /** Call only inside consumeIfCurrent; it already holds the source lock. */
    suspend fun observeCurrent(quotas: List<QuotaInfo>) = observeLocked(quotas)

    private suspend fun observeLocked(quotas: List<QuotaInfo>) {
        val currentQuotas = quotas.filter { quota ->
            if (prefsManager.usesDesktopBridge(quota.service)) {
                return@filter quota.resetTrackingAccountKey?.startsWith("desktop:${prefsManager.desktopBridgeCredential()?.companionId}:") == true
            }
            val credential = prefsManager.loadCredential(quota.service) ?: return@filter false
            quota.service != AiService.CODEX ||
                (credential as? Credential.CodexCredential)?.resetTrackingAccountKey() == quota.resetTrackingAccountKey
        }
        val activities = store.record(currentQuotas)
        if (prefsManager.isResetNotificationEnabled()) {
            activities.forEach { (service, events) ->
                if (currentQuotas.none { it.service == service && !it.isStale }) return@forEach
                events.forEach { notificationService.showResetActivityNotification(service, it) }
            }
        }
    }

    suspend fun forgetService(service: AiService) = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.deleteService(service)
            notificationService.cancelResetNotifications(service)
            revision.incrementAndGet()
            _sourceChanges.value = revision.get()
        }
    }

    suspend fun saveCredential(service: AiService, credential: Credential) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = prefsManager.loadCredential(service)
            val sameAccount = if (credential is Credential.CodexCredential && previous is Credential.CodexCredential) {
                credential.resetTrackingAccountKey() == previous.resetTrackingAccountKey()
            } else credential == previous
            prefsManager.saveCredential(service, credential)
            if (!sameAccount && !prefsManager.usesDesktopBridge(service)) {
                store.deleteService(service)
                notificationService.cancelResetNotifications(service)
                revision.incrementAndGet()
                _sourceChanges.value = revision.get()
            }
        }
    }
}
