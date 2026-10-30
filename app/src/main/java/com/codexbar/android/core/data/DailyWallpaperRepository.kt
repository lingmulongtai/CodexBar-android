package com.codexbar.android.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import com.codexbar.android.core.security.EncryptedPrefsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

data class WallpaperPreferences(val enabled: Boolean = true, val hasAccessKey: Boolean = false)
enum class WallpaperStatus { Ready, Updating, Offline, ApiUnavailable }
data class WallpaperState(
    val preferences: WallpaperPreferences = WallpaperPreferences(), val photo: WallpaperPhoto? = null,
    val bitmap: Bitmap? = null, val day: String? = null, val status: WallpaperStatus = WallpaperStatus.Ready
)

@Serializable
private data class SavedWallpaper(val day: String, val photo: WallpaperPhoto, val filename: String, val editorial: Boolean)

/** One app-wide daily selection. Navigation never discards a successfully loaded background. */
@Singleton
class DailyWallpaperRepository @Inject constructor(
    @ApplicationContext context: Context, private val prefs: EncryptedPrefsManager
) {
    private val directory by lazy { File(context.filesDir, "daily-wallpaper").apply { mkdirs() } }
    private val record by lazy { AtomicFile(File(directory, "selection.json")) }
    // First touched by the IO coroutine, never while Hilt creates this repository in composition.
    private val client by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .callTimeout(20, TimeUnit.SECONDS).cache(Cache(File(context.cacheDir, "wallpaper-http"), 20L * 1024 * 1024)).build()
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var saved: SavedWallpaper? = null
    private var nextAttempt = 0L
    private val _state = MutableStateFlow(WallpaperState())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    init {
        scope.launch {
            saved = runCatching { record.openRead().use { json.decodeFromString<SavedWallpaper>(it.readNBytesCompat(16 * 1024).decodeToString()) } }.getOrNull()
            runCatching {
            saved?.let { old ->
                // Filename originates here, but validate persisted data before resolving it.
                if (isWallpaperUrl(old.photo.profileUrl, "unsplash.com") && isWallpaperUrl(old.photo.photoUrl, "unsplash.com") && old.filename.matches(Regex("image-[a-zA-Z0-9_-]+\\.jpg"))) {
                    decode(File(directory, old.filename).takeIf { it.isFile && it.length() <= 8 * 1024 * 1024 }?.readBytes())?.let { bitmap ->
                        _state.update { it.copy(photo = old.photo, bitmap = bitmap, day = old.day) }
                    }
                }
            }
            }
            prefs.wallpaperPreferences.collectLatest { preferences ->
                val changed = _state.value.preferences != preferences
                _state.update { it.copy(preferences = preferences) }
                if (changed) nextAttempt = 0L
                refresh()
            }
        }
    }

    fun refreshInBackground(force: Boolean = false) { scope.launch { refresh(force) } }

    suspend fun save(enabled: Boolean, replacementKey: String?) {
        require(replacementKey == null || replacementKey.isEmpty() || replacementKey.matches(Regex("[A-Za-z0-9_-]{16,128}")))
        prefs.saveWallpaperPreferences(enabled, replacementKey)
        nextAttempt = 0L
        refresh(force = true)
    }

    private suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!_state.value.preferences.enabled) return@withLock
            val today = LocalDate.now()
            val key = prefs.loadWallpaperAccessKey()
            if (!force && saved?.day == today.toString() && saved?.editorial == (key != null) && _state.value.bitmap != null) return@withLock
            if (!force && System.currentTimeMillis() < nextAttempt) return@withLock
            nextAttempt = System.currentTimeMillis() + 30 * 60_000L
            _state.update { it.copy(status = WallpaperStatus.Updating) }
            try {
                val photo = if (key == null) curatedWallpaper(today) else editorial(today, key)
                val image = photo.imageUrl.toHttpUrl().newBuilder()
                    .setQueryParameter("w", "1600").setQueryParameter("q", "80")
                    .setQueryParameter("fit", "max").setQueryParameter("auto", "format").setQueryParameter("fm", "jpg").build()
                require(isWallpaperUrl(image.toString(), "images.unsplash.com"))
                val bytes = fetch(Request.Builder().url(image).build(), 8 * 1024 * 1024)
                val bitmap = decode(bytes) ?: throw IOException("Invalid background image")
                currentCoroutineContext().ensureActive()
                if (prefs.loadWallpaperAccessKey() != key || !prefs.wallpaperPreferences.first().enabled) return@withLock
                if (key != null) {
                    val download = photo.downloadUrl ?: throw IOException("Missing download tracking URL")
                    require(isWallpaperUrl(download, "api.unsplash.com"))
                    fetch(apiRequest(download, key), 64 * 1024)
                }
                currentCoroutineContext().ensureActive()
                // A manual refresh is independent of the preference collector. Tracking suspends,
                // so re-check settings after it too before publishing that request's result.
                if (prefs.loadWallpaperAccessKey() != key || !prefs.wallpaperPreferences.first().enabled) return@withLock
                val filename = "image-${photo.id.replace(Regex("[^a-zA-Z0-9_-]"), "")}.jpg"
                AtomicFile(File(directory, filename)).writeSafely(bytes)
                val selected = SavedWallpaper(today.toString(), photo, filename, key != null)
                record.writeSafely(json.encodeToString(selected).encodeToByteArray())
                saved = selected
                _state.update { it.copy(photo = photo, bitmap = bitmap, day = selected.day, status = WallpaperStatus.Ready) }
                directory.listFiles()?.filter { it.name.startsWith("image-") && it.name != filename }?.forEach { it.delete() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.update { it.copy(status = if (key == null) WallpaperStatus.Offline else WallpaperStatus.ApiUnavailable) }
            }
        }
    }

    private suspend fun editorial(date: LocalDate, key: String): WallpaperPhoto {
        val data = json.parseToJsonElement(fetch(apiRequest("https://api.unsplash.com/photos?per_page=30&page=1", key), 1024 * 1024).decodeToString()).jsonArray
        return selectEditorialWallpaper(data, date) ?: throw IOException("No suitable Editorial background")
    }

    private fun apiRequest(url: String, key: String): Request {
        require(isWallpaperUrl(url, "api.unsplash.com"))
        return Request.Builder().url(url).header("Authorization", "Client-ID $key").header("Accept-Version", "v1").header("Cache-Control", "no-store").build()
    }

    private suspend fun fetch(request: Request, limit: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("Background unavailable")
                        val body = it.body ?: throw IOException("Missing background")
                        if (body.contentLength() > limit) throw IOException("Background too large")
                        body.byteStream().use { input ->
                            input.readNBytesCompat(limit + 1).also { bytes ->
                                if (bytes.size > limit) throw IOException("Background too large")
                            }
                        }
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }

    private fun decode(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        val options = BitmapFactory.Options().apply {
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 2048) inSampleSize = inSampleSize.coerceAtLeast(1) * 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

private fun AtomicFile.writeSafely(bytes: ByteArray) {
    val output = startWrite()
    try { output.write(bytes); finishWrite(output) }
    catch (error: Exception) { failWrite(output); throw error }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (result.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - result.size()))
        if (count < 0) break
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}
