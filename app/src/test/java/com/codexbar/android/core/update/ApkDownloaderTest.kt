package com.codexbar.android.core.update

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun target(bytes: ByteArray) = UpdateArtifact("0.17.0", server.url("/app.apk").toString(), bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).hex())
    private fun downloader(client: OkHttpClient = OkHttpClient()) = ApkDownloader(client) { it.host == server.url("/").host && it.port == server.port }
    private fun partial() = File(temporary.root, "update.part")

    @Test fun `streamed progress reaches expected bytes only after complete response`() = runBlocking {
        val body = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(body)))
        val progress = mutableListOf<Long>()
        val file = partial()
        downloader().download(target(body), file) { progress += it }
        assertArrayEquals(body, file.readBytes())
        assertEquals(body.size.toLong(), progress.last())
        verifyUpdateBytes(file, target(body))
    }

    @Test fun `truncated oversized and wrong hash responses are discarded`() = runBlocking {
        val expected = "valid apk bytes".toByteArray()
        for (body in listOf(expected.dropLast(1).toByteArray(), expected + byteArrayOf(0), ByteArray(expected.size) { 1 })) {
            server.enqueue(MockResponse().setChunkedBody(Buffer().write(body), 3))
            val file = partial()
            try { downloader().download(target(expected), file) {}; fail("Invalid bytes accepted") }
            catch (error: UpdateDownloadException) { assertEquals(UpdateFailure.INTEGRITY, error.reason) }
            assertFalse(file.exists())
        }
    }

    @Test fun `untrusted redirects and redirect loops fail without leaving an APK`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://example.com/app.apk"))
        try { downloader().download(target(byteArrayOf(1)), partial()) {}; fail() }
        catch (error: UpdateDownloadException) { assertEquals(UpdateFailure.UNAVAILABLE, error.reason) }
        repeat(6) { server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/loop")) }
        try { downloader().download(target(byteArrayOf(1)), partial()) {}; fail() }
        catch (error: UpdateDownloadException) { assertEquals(UpdateFailure.NETWORK, error.reason) }
        assertFalse(partial().exists())
    }

    @Test fun `cancellation interrupts socket reads and removes partial data`() = runBlocking {
        val body = ByteArray(500_000) { 1 }
        server.enqueue(MockResponse().setBody(Buffer().write(body)).throttleBody(1024, 1, TimeUnit.SECONDS))
        val started = CompletableDeferred<Unit>()
        val job = launch { downloader().download(target(body), partial()) { started.complete(Unit) } }
        withTimeout(5_000) { started.await() }
        withTimeout(3_000) { job.cancelAndJoin() }
        assertFalse(partial().exists())
    }

    @Test fun `a stalled connection terminates with a network error`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS).callTimeout(500, TimeUnit.MILLISECONDS).build()
        try { downloader(client).download(target(byteArrayOf(1)), partial()) {}; fail() }
        catch (error: java.io.IOException) { assertFalse(error is UpdateDownloadException) }
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertFalse(partial().exists())
    }

    @Test fun `changed ready file fails revalidation before installer handoff`() = runBlocking {
        val body = byteArrayOf(1, 2, 3)
        val file = partial().apply { writeBytes(byteArrayOf(3, 2, 1)) }
        try { verifyUpdateBytes(file, target(body)); fail() }
        catch (error: UpdateDownloadException) { assertEquals(UpdateFailure.INTEGRITY, error.reason) }
    }

    @Test fun `whole transfer deadline becomes a retryable failure rather than cancelling the worker`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
        val downloader = ApkDownloader(client, 200) { it.host == server.url("/").host && it.port == server.port }
        try { downloader.download(target(byteArrayOf(1)), partial()) {}; fail() }
        catch (error: UpdateDownloadException) { assertEquals(UpdateFailure.NETWORK, error.reason) }
        currentCoroutineContext().ensureActive()
        assertFalse(partial().exists())
    }
}
