package com.codexbar.android.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

internal class ResponseSizeLimitInterceptor(
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) : Interceptor {

    init {
        require(maxBytes in 1 until Long.MAX_VALUE)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val declaredLength = body.contentLength()

        if (declaredLength > maxBytes) {
            response.close()
            throw responseTooLarge()
        }

        return response.newBuilder()
            .body(body.withSizeLimit())
            .build()
    }

    private fun ResponseBody.withSizeLimit(): ResponseBody {
        val delegate = this
        return object : ResponseBody() {
            private val limitedSource: BufferedSource by lazy {
                object : ForwardingSource(delegate.source()) {
                    private var totalBytesRead = 0L

                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (byteCount == 0L) return 0L

                        val remaining = maxBytes - totalBytesRead
                        val read = super.read(sink, minOf(byteCount, remaining + 1))
                        if (read == -1L) return -1L

                        totalBytesRead += read
                        if (totalBytesRead > maxBytes) throw responseTooLarge()
                        return read
                    }
                }.buffer()
            }

            override fun contentType(): MediaType? = delegate.contentType()

            override fun contentLength(): Long = delegate.contentLength()

            override fun source(): BufferedSource = limitedSource
        }
    }

    private fun responseTooLarge(): IOException {
        return IOException("Response body exceeds the $maxBytes byte limit")
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 2L * 1024L * 1024L
    }
}
