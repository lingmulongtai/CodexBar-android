package com.codexbar.android.debug

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.codexbar.android.core.update.*
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Opt-in fixtures on a disposable emulator. No production download/install policy is changed. */
@AndroidEntryPoint
class UpdateProofActivity : AppCompatActivity() {
    @Inject lateinit var checker: GitHubReleaseUpdateChecker
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(intent.getBooleanExtra("seed_demo", false))
        val mode = intent.getStringExtra("mode") ?: "progress"
        lifecycleScope.launch {
            try {
                if (mode == "network" || mode == "worker") {
                    val target = (checker.checkForUpdates("0.0.0") as UpdateCheckResult.Available).update.artifact!!
                    if (mode == "worker") {
                        val manager = WorkManager.getInstance(this@UpdateProofActivity)
                        val work = OneTimeWorkRequestBuilder<UpdateDownloadWorker>().setInputData(target.toData())
                            .addTag("codexbar-update-version:${target.version}")
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
                        withContext(Dispatchers.IO) { manager.enqueueUniqueWork(UpdateDownloadRepository.WORK_NAME, ExistingWorkPolicy.REPLACE, work).result.get() }
                        val result = withTimeout(200_000) { manager.getWorkInfoByIdFlow(work.id).first { it?.state?.isFinished == true }!! }
                        check(result.state == WorkInfo.State.FAILED && result.outputData.getString("failure") == UpdateFailure.VERSION.name)
                        Log.i("CodexBarUpdateProof", "PASS production foreground worker downloaded and verified the live APK; correctly rejected non-newer version")
                        finish()
                        return@launch
                    }
                    val file = File(cacheDir, "update-network-proof.part")
                    try {
                        ApkDownloader().download(target, file) { Log.i("CodexBarUpdateProof", "network bytes=$it/${target.bytes}") }
                        verifyUpdateBytes(file, target)
                        @Suppress("DEPRECATION")
                        val flags = if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
                        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)!!
                        check(archive.packageName == packageName && archive.versionName == target.version)
                        @Suppress("DEPRECATION")
                        val signers = if (android.os.Build.VERSION.SDK_INT >= 28) archive.signingInfo!!.apkContentsSigners else archive.signatures
                        check(signers!!.isNotEmpty())
                        Log.i("CodexBarUpdateProof", "PASS real HTTPS APK ${target.version} bytes=${target.bytes} hash=${target.sha256}")
                    } finally { file.delete() }
                } else if (mode != "restore") {
                    val fixture = File(filesDir, "update-proof.apk")
                    val target = if (mode == "ready") UpdateArtifact("0.17.0",
                        "https://github.com/lingmulongtai/CodexBar-android/releases/download/v0.17.0/CodexBar-Android-v0.17.0.apk",
                        fixture.length(), MessageDigest.getInstance("SHA-256").digest(fixture.readBytes()).hex())
                    else UpdateArtifact("0.17.0", "https://github.com/lingmulongtai/CodexBar-android/releases/download/v0.17.0/CodexBar-Android-v0.17.0.apk", 8_000_000, "a".repeat(64))
                    val work = OneTimeWorkRequestBuilder<UpdateScenarioWorker>()
                        .setInputData(Data.Builder().putAll(target.toData()).putString("mode", mode).putBoolean("seed_demo", true).build())
                        .addTag("codexbar-update-version:0.17.0").build()
                    withContext(Dispatchers.IO) { WorkManager.getInstance(this@UpdateProofActivity)
                        .enqueueUniqueWork(UpdateDownloadRepository.WORK_NAME, ExistingWorkPolicy.REPLACE, work).result.get() }
                }
                startActivity(Intent(this@UpdateProofActivity, ScreenshotActivity::class.java)
                    .putExtra("destination", "settings").putExtra("dark_theme", true).putExtra("seed_demo", true))
                finish()
            } catch (error: Exception) { Log.e("CodexBarUpdateProof", "FAIL $mode", error); finish() }
        }
    }
}

class UpdateScenarioWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        check(inputData.getBoolean("seed_demo", false))
        val target = inputData.artifact()!!
        when (inputData.getString("mode")) {
            "ready" -> {
                val file = UpdateDownloadRepository.readyFile(applicationContext, id)
                file.parentFile!!.mkdirs()
                File(applicationContext.filesDir, "update-proof.apk").copyTo(file, overwrite = true)
                verifyUpdateBytes(file, target)
                AndroidUpdateVerifier(applicationContext).verify(file, target)
                Log.i("CodexBarUpdateProof", "PASS ready fixture $id")
                Result.success(target.toData())
            }
            "progress" -> {
                for (percent in listOf(10, 30, 55, 75, 92)) {
                    setProgress(Data.Builder().putAll(target.toData()).putLong("received", target.bytes * percent / 100).build())
                    delay(4000)
                }
                setProgress(Data.Builder().putAll(target.toData()).putString("stage", "verify").build())
                delay(5000)
                Result.failure(workDataOf("failure" to UpdateFailure.NETWORK.name))
            }
            else -> Result.failure(workDataOf("failure" to UpdateFailure.NETWORK.name))
        }
    }
}
