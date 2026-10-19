package com.todi.app.presentation.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ResumableDownload(
    private val client: OkHttpClient,
    private val url: String,
) {
    suspend fun into(
        file: File,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Long =
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            fetch(file, onProgress)
        }

    private suspend fun fetch(
        file: File,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        var stalled = 0
        var backoff = FIRST_BACKOFF_MILLIS
        while (true) {
            val before = file.length()

            val total =
                try {
                    attempt(file, onProgress)
                } catch (_: IOException) {
                    0L
                }
            if (total > 0 && file.length() >= total) return total
            if (file.length() > before) {
                stalled = 0
                backoff = FIRST_BACKOFF_MILLIS
            } else {
                stalled++
                if (stalled >= MAX_STALLED_ATTEMPTS) {
                    throw IOException("download stopped at ${file.length()} bytes")
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    private fun attempt(
        file: File,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        val have = file.length()
        val request =
            Request
                .Builder()
                .url(url)
                .apply { if (have > 0) header("Range", "bytes=$have-") }
                .build()
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
                return file.length()
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("no body")
            val resumed = response.code == HTTP_PARTIAL_CONTENT
            val range = response.header("Content-Range")?.let(::parseContentRange)

            val appending = resumed && range?.start == have
            if (!appending) file.delete()
            val start = if (appending) have else 0L
            val total =
                when {
                    appending -> range?.total ?: 0L
                    else -> body.contentLength().takeIf { it > 0 } ?: 0L
                }
            var written = start
            FileOutputStream(file, appending).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
            return if (total > 0) total else written
        }
    }

    private fun parseContentRange(header: String): ContentRange? {
        val start =
            header
                .substringAfter("bytes ", "")
                .substringBefore('-')
                .trim()
                .toLongOrNull()
                ?: return null
        val total = header.substringAfterLast('/', "").trim().toLongOrNull() ?: 0L
        return ContentRange(start = start, total = total)
    }

    private data class ContentRange(
        val start: Long,
        val total: Long,
    )

    private companion object {
        const val HTTP_PARTIAL_CONTENT = 206

        const val HTTP_RANGE_NOT_SATISFIABLE = 416

        const val BUFFER_BYTES = 64 * 1024

        const val MAX_STALLED_ATTEMPTS = 3

        const val FIRST_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 15_000L
    }
}
