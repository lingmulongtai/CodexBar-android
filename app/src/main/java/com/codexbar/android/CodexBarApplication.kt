package com.codexbar.android

import android.app.Application
import com.codexbar.android.ui.theme.DeveloperSettingsRuntime
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CodexBarApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var developerSettingsRuntime: DeveloperSettingsRuntime

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        developerSettingsRuntime.install()
        WorkManagerInitializer.applySavedRefreshPolicyAsync(this)
    }
}
