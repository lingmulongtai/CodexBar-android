package com.codexbar.android.feature.dashboard

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.data.QuotaRepositoryRegistry
import com.codexbar.android.core.data.QuotaResetHistoryStore
import com.codexbar.android.core.data.QuotaResetMonitor
import com.codexbar.android.core.domain.model.AcquisitionSettings
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.presentation.AndroidQuotaPresentationText
import com.codexbar.android.core.security.ConnectionHealthStore
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.WidgetPrefsManager
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardSourceChangeTest {
    private lateinit var color: org.mockito.MockedStatic<android.graphics.Color>

    @Before
    fun mockAndroidColors() {
        color = mockStatic(android.graphics.Color::class.java)
    }

    @After
    fun resetDispatcher() {
        Dispatchers.resetMain()
        color.close()
    }

    @Test
    fun `source change clears shared readings even while a manual refresh is pending`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        mockStatic(SystemClock::class.java).use {
            mockStatic(Log::class.java).use {
                mockConstruction(AndroidQuotaPresentationText::class.java) { text, _ ->
                    `when`(text.locale).thenReturn(Locale.ENGLISH)
                }.use {
                    val prefs = mock(EncryptedPrefsManager::class.java)
                    `when`(prefs.acquisitionSettings()).thenReturn(AcquisitionSettings())
                    val history = mock(QuotaResetHistoryStore::class.java)
                    `when`(history.history).thenReturn(MutableStateFlow(emptyMap()))
                    val notifications = mock(QuotaNotificationService::class.java)
                    val monitor = QuotaResetMonitor(history, prefs, notifications)
                    val registry = mock(QuotaRepositoryRegistry::class.java)
                    `when`(registry.prepareRefresh()).thenAnswer { monitor.sourceRevision() }
                    `when`(registry.entries()).thenReturn(emptyList())
                    val model = DashboardViewModel(
                        registry, prefs, mock(ConnectionHealthStore::class.java),
                        mock(QuotaHistoryStore::class.java), history, monitor,
                        mock(MonitoringSessionStore::class.java), notifications,
                        mock(WidgetPrefsManager::class.java), mock(DashboardOrderPrefs::class.java),
                        mock(Context::class.java)
                    )
                    try {
                        runCurrent()
                        val previous = model.uiState.value
                        assertTrue(previous is DashboardUiState.Content)

                        // A slow provider must not block tab navigation or erase ordinary refreshes.
                        val response = CompletableDeferred<Result<QuotaInfo, AppError>>()
                        val repository = mock(QuotaRepository::class.java)
                        val suspendedRepository = object : QuotaRepository by repository {
                            override suspend fun fetchQuota(): Result<QuotaInfo, AppError> = response.await()
                        }
                        `when`(registry.entries()).thenReturn(listOf(AiService.CODEX to suspendedRepository))
                        `when`(prefs.hasQuotaSource(AiService.CODEX)).thenReturn(true)
                        model.refresh()
                        runCurrent()
                        assertTrue(model.isRefreshing.value)
                        assertSame(previous, model.uiState.value)

                        // The same slow request must not keep a disconnected account's readings.
                        monitor.forgetService(AiService.CODEX)
                        runCurrent()
                        assertSame(DashboardUiState.Loading, model.uiState.value)
                        assertTrue(model.isRefreshing.value)
                    } finally {
                        model.viewModelScope.cancel()
                        runCurrent()
                    }
                }
            }
        }
    }
}
