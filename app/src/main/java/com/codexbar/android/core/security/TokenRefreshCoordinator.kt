package com.codexbar.android.core.security

import com.codexbar.android.core.domain.model.AiService
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TokenRefreshCoordinator @Inject constructor() {
    private val locks = ConcurrentHashMap<AiService, Mutex>()

    suspend fun <T> withRefreshLock(service: AiService, block: suspend () -> T): T {
        return locks.getOrPut(service) { Mutex() }.withLock {
            block()
        }
    }
}
