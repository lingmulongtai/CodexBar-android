package com.codexbar.android.feature.github

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun safeGitHubAvatarUrl(value: String?): String? {
    val url = value?.toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" || url.host != "avatars.githubusercontent.com" || url.port != 443 ||
        url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) return null
    return url.newBuilder().setQueryParameter("s", "192").build().toString()
}

private val avatarClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .callTimeout(15, TimeUnit.SECONDS).build()

private fun loadAvatar(url: String): ImageBitmap? {
    return try {
        avatarClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > 512 * 1024) return null
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > 512 * 1024) return null
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }
    } catch (_: IOException) { null }
}

@Composable
internal fun GitHubAvatar(url: String?, login: String, modifier: Modifier = Modifier) {
    val safeUrl = safeGitHubAvatarUrl(url)
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = safeUrl) {
        value = if (safeUrl == null) null else withContext(Dispatchers.IO) { loadAvatar(safeUrl) }
    }
    Box(modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (image != null) Image(image!!, contentDescription = null, modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop)
        else Text(login.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall)
    }
}
