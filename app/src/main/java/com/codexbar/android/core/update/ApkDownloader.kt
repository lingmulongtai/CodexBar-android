package com.codexbar.android.core.update

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** No provider credentials/interceptors. A cancelled worker also closes an in-flight socket. */
internal class ApkDownloader(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build(),
    private val timeoutMillis: Long = 180_000,
    private val allowedUrl: (HttpUrl) -> Boolean = ::isTrustedUpdateHop
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    suspend fun download(target: UpdateArtifact, partial: File, onProgress: suspend (Long) -> Unit) = withContext(Dispatchers.IO) {
        require(target.bytes in 1..MAX_UPDATE_BYTES)
        var url = target.url.toHttpUrl()
        // One budget for the whole redirect chain, not 180 seconds per redirect.
        try { withTimeout(timeoutMillis) {
            try {
                repeat(6) { hop ->
                    if (!allowedUrl(url)) throw UpdateDownloadException(UpdateFailure.UNAVAILABLE)
                    val call = client.newCall(Request.Builder().url(url).header("Accept", "application/octet-stream")
                        .header("Accept-Encoding", "identity").header("User-Agent", "CodexBar-Android-Updater").build())
                    val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { call.cancel() }
                    }
                    try {
                        call.execute().use { response ->
                            if (response.code in setOf(301, 302, 303, 307, 308)) {
                                if (hop == 5) throw UpdateDownloadException(UpdateFailure.NETWORK)
                                url = response.header("Location")?.let(url::resolve)
                                    ?: throw UpdateDownloadException(UpdateFailure.NETWORK)
                            } else {
                                copyResponse(response, target, partial, onProgress)
                                return@withTimeout
                            }
                        }
                    } finally { cancellation.cancelAndJoin() }
                }
            } catch (error: Exception) {
                partial.delete()
                currentCoroutineContext().ensureActive()
                throw error
            }
        } } catch (timeout: TimeoutCancellationException) {
            partial.delete()
            currentCoroutineContext().ensureActive()
            throw UpdateDownloadException(UpdateFailure.NETWORK)
        }
    }

    private suspend fun copyResponse(response: Response, target: UpdateArtifact, partial: File, onProgress: suspend (Long) -> Unit) {
        if (response.code != 200) throw UpdateDownloadException(UpdateFailure.NETWORK)
        val body = response.body ?: throw UpdateDownloadException(UpdateFailure.NETWORK)
        if (body.contentLength() >= 0 && body.contentLength() != target.bytes)
            throw UpdateDownloadException(UpdateFailure.INTEGRITY)
        if (!partial.parentFile!!.let { it.isDirectory || it.mkdirs() } ||
            partial.parentFile!!.usableSpace < target.bytes + 10L * 1024 * 1024)
            throw UpdateDownloadException(UpdateFailure.STORAGE)
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var lastProgress = 0L
        body.byteStream().use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                    if (received > target.bytes) throw UpdateDownloadException(UpdateFailure.INTEGRITY)
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    val now = System.nanoTime()
                    if (now - lastProgress >= 250_000_000 || received == target.bytes) {
                        onProgress(received)
                        lastProgress = now
                    }
                }
                output.flush()
                output.fd.sync()
            }
        }
        if (received != target.bytes || digest.digest().hex() != target.sha256)
            throw UpdateDownloadException(UpdateFailure.INTEGRITY)
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

internal suspend fun verifyUpdateBytes(file: File, target: UpdateArtifact) = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() != target.bytes) throw UpdateDownloadException(UpdateFailure.INTEGRITY)
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    if (digest.digest().hex() != target.sha256) throw UpdateDownloadException(UpdateFailure.INTEGRITY)
}
