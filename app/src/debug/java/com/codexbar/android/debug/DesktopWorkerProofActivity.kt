package com.codexbar.android.debug

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codexbar.android.MainActivity
import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.network.desktop.DesktopBridgePairing
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.QuotaRefreshWorker
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

/** End-to-end proof on a disposable emulator; uses the public synthetic Rust fixture only. */
@AndroidEntryPoint
class DesktopWorkerProofActivity : AppCompatActivity() {
    @Inject lateinit var prefs: EncryptedPrefsManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(intent.getBooleanExtra("seed_demo", false))
        val port = intent.getIntExtra("port", 43829)
        check(port in 43828..43829) { "Only isolated synthetic proof listeners are supported" }
        lifecycleScope.launch {
            prefs.warmCache()
            val credential = DesktopBridgePairing.parse("CBWIN1|10.0.2.2|$port|11111111-1111-4111-8111-111111111111|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            prefs.saveDesktopSource(credential, AcquisitionSettings(AcquisitionMode.WINDOWS, setOf(AiService.CODEX, AiService.CLAUDE, AiService.COPILOT)))
            prefs.setResetNotificationEnabled(true)
            prefs.setPersistentNotificationEnabled(false)
            val work = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
                .setInputData(workDataOf(WorkManagerInitializer.KEY_REFRESH_SOURCE to "desktop_worker_proof")).build()
            val manager = WorkManager.getInstance(this@DesktopWorkerProofActivity)
            withContext(Dispatchers.IO) { manager.enqueue(work).result.get() }
            if (intent.getBooleanExtra("background", false)) moveTaskToBack(true)
            val result = withTimeout(60_000) {
                withContext(Dispatchers.IO) {
                    var info: WorkInfo?
                    do { delay(100); info = manager.getWorkInfoById(work.id).get() } while (info?.state?.isFinished != true)
                    checkNotNull(info)
                }
            }
            check(result.state == WorkInfo.State.SUCCEEDED) { "Windows worker failed: ${result.state}" }
            val notices = getSystemService(android.app.NotificationManager::class.java).activeNotifications.count {
                it.notification.channelId == QuotaNotificationService.RESET_CHANNEL_ID
            }
            val expected = intent.getIntExtra("minimum_alerts", 0)
            check(notices >= expected) { "Expected $expected reset alerts; received $notices" }
            android.util.Log.i("CodexBarWorkerProof", "PASS Windows source through WorkManager; reset alerts=$notices")
            if (!intent.getBooleanExtra("background", false)) startActivity(Intent(this@DesktopWorkerProofActivity, MainActivity::class.java))
            finish()
        }
    }
}
