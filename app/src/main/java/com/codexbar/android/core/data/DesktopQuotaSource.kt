package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.network.desktop.*
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DesktopQuotaSource @Inject constructor(
    private val client: DesktopBridgeClient,
    private val prefs: EncryptedPrefsManager,
    private val sourceMonitor: QuotaResetMonitor,
    private val coordinator: DesktopSourceCoordinator
) {
    private val mutex = Mutex()
    private var cachedCredential: DesktopBridgeCredential? = null
    private var cached: DesktopBridgeSnapshot? = null
    private var cachedAtNanos = 0L
    private var cachedFailure: Exception? = null
    private var cachedRevision = -1L

    /** Fetch once before choosing repositories, so newly enabled Windows providers appear. */
    suspend fun prepareRefresh(): Long {
        val revision = sourceMonitor.sourceRevision()
        val credential = prefs.desktopBridgeCredential() ?: return revision
        val settings = prefs.acquisitionSettings()
        if (settings.mode != AcquisitionMode.WINDOWS) return revision
        return mutex.withLock {
            val snapshot = try { snapshot(credential, revision) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { return@withLock revision }
            val advertised = snapshot.providers.mapNotNull { serviceFor(it.id) }.toSet()
            if (advertised == settings.advertisedServices) return@withLock revision
            val applied = coordinator.update(credential, settings.copy(advertisedServices = advertised), valid = {
                sourceMonitor.sourceRevision() == revision && prefs.desktopBridgeCredential() == credential &&
                    prefs.acquisitionSettings() == settings
            })
            if (!applied) return@withLock revision
            sourceMonitor.sourceRevision().also { cachedRevision = it }
        }
    }

    // Caller holds mutex. Both a successful snapshot and an offline result are single-flight.
    private suspend fun snapshot(credential: DesktopBridgeCredential, revision: Long): DesktopBridgeSnapshot {
        val age = System.nanoTime() - cachedAtNanos
        if (cachedCredential == credential && cachedRevision == revision && age in 0..5_000_000_000L) {
            cachedFailure?.let { throw it }
            return checkNotNull(cached)
        }
        try {
            return client.fetchSnapshot(credential).also {
                cached = it; cachedFailure = null
                cachedCredential = credential; cachedRevision = revision; cachedAtNanos = System.nanoTime()
            }
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            cached = null; cachedFailure = error
            cachedCredential = credential; cachedRevision = revision; cachedAtNanos = System.nanoTime()
            throw error
        }
    }

    suspend fun fetchQuota(service: AiService): Result<QuotaInfo, AppError> {
        val credential = prefs.desktopBridgeCredential() ?: return Result.Failure(AppError.CredentialNotFound(service))
        val revision = sourceMonitor.sourceRevision()
        return try {
            val snapshot = mutex.withLock { snapshot(credential, revision) }
            val provider = snapshot.providers.find { serviceFor(it.id) == service }
                ?: return Result.Failure(AppError.ServiceUnavailable)
            if (provider.status == "needsAuthentication" || provider.status == "expiredSession") {
                return Result.Failure(AppError.AuthError(service, true, "Reconnect this provider in Win-CodexBar"))
            }
            if (provider.status != "ready" || provider.updatedAtEpochSeconds < Instant.now().epochSecond - 7200) {
                return Result.Failure(AppError.ServiceUnavailable)
            }
            val spend = snapshot.spend?.takeIf { it.costTrackingEnabled }?.let { summary ->
                summary.rows.find { it.providerId == provider.id }?.let { row -> DesktopSpend(
                    summary.updatedAtEpochSeconds, summary.reportingPeriod, summary.timeZone, row.currency, row.refreshing,
                    row.sevenDay, row.thirtyDay, row.periodCost, row.sevenDayTokens, row.thirtyDayTokens, row.periodTokens,
                    row.tokensLowerBound, row.daily.map { DesktopSpendDay(it.day, it.tokens, it.costUsd, it.isScanned) }) }
            }
            Result.Success(provider.toQuota(service, credential.companionId).copy(desktopSpend = spend))
        } catch (error: CancellationException) { throw error
        } catch (error: DesktopBridgeAuthenticationException) {
            Result.Failure(AppError.AuthError(service, true, "Pair with Win-CodexBar again"))
        } catch (error: DesktopBridgeProtocolException) {
            Result.Failure(AppError.ParseError("Invalid Windows usage response", error))
        } catch (error: IOException) {
            Result.Failure(AppError.NetworkError("Cannot reach Win-CodexBar", error))
        }
    }

    companion object {
        fun serviceFor(id: String): AiService? = when (id) {
            "geminiapps" -> AiService.GEMINI_APPS
            "cline" -> AiService.CLINEPASS
            "kimik2" -> AiService.MOONSHOT
            "ibmbob" -> AiService.IBM_BOB
            else -> AiService.entries.find { it.name.lowercase() == id }
        }

        internal fun DesktopProvider.toQuota(service: AiService, companionId: String, now: Instant = Instant.now()): QuotaInfo = QuotaInfo(
            service = service,
            windows = windows.map { UsageWindow(it.label, it.usedFraction, it.resetsAtEpochSeconds?.let(Instant::ofEpochSecond), it.windowDurationSeconds) },
            extraUsage = budget?.let { ExtraUsage(true, it.limit, it.used, (it.used / it.limit).coerceIn(0.0, 1.0), it.currency) },
            tier = balance?.let { "Balance: ${it.currency} ${it.amount}" },
            fetchedAt = Instant.ofEpochSecond(updatedAtEpochSeconds),
            codexResetCredits = if (service == AiService.CODEX) resetCredits?.let { CodexResetCredits(it.availableCount, it.expiresAtEpochSeconds.map(Instant::ofEpochSecond)) } else null,
            resetTrackingAccountKey = subjectId?.let { "desktop:$companionId:$it" },
            isStale = now.epochSecond - updatedAtEpochSeconds > FRESH_OBSERVATION_SECONDS,
            historyEligible = subjectId != null && now.epochSecond - updatedAtEpochSeconds <= FRESH_OBSERVATION_SECONDS,
            isDesktopSource = true,
            desktopHistoryScopeKey = historyScopeId?.let { "desktop:$companionId:history:$it" },
            desktopQuotaHistory = if (historyScopeId == null) emptyList() else history.map { series ->
                DesktopQuotaSeries(series.label, series.samples.map { DesktopQuotaSample(Instant.ofEpochSecond(it.t), it.u, it.r?.let(Instant::ofEpochSecond)) })
            },
            desktopLocalUsage = localUsage?.let { DesktopLocalUsage(it.updatedAtEpochSeconds, it.todayDate,
                it.todayTokens, it.thirtyDayTokens, it.periodTokens, it.todayCostUsd, it.thirtyDayCostUsd,
                it.periodCostUsd, it.reportingPeriod, it.topModel, it.incompleteRequestCount) }
        )
        private const val FRESH_OBSERVATION_SECONDS = 35L * 60
    }
}
