package com.todi.app.backup

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant

internal object BackupRequests {
    const val HEADER_TASK_COUNT = "X-Todi-Task-Count"
    const val GZIP = "gzip"
    const val MIME_JSON = "application/json"

    private const val API = "api"
    private const val VERSION = "v1"
    private const val BEARER = "Authorization"

    fun requestCode(
        server: String,
        email: String,
    ): Request =
        jsonRequest(
            url = url(server, "auth", "request"),
            json = JSONObject().put("email", email).toString(),
        )

    fun verifyCode(
        server: String,
        email: String,
        code: String,
    ): Request =
        jsonRequest(
            url = url(server, "auth", "verify"),
            json =
                JSONObject()
                    .put("email", email)
                    .put("code", code)
                    .toString(),
        )

    fun list(
        server: String,
        token: String,
    ): Request = withToken(get(server, "backups"), token)

    fun upload(
        server: String,
        token: String,
        gzipped: ByteArray,
        taskCount: Int,
    ): Request =
        withToken(
            Request
                .Builder()
                .url(url(server, "backups"))
                .header("Content-Encoding", GZIP)
                .header(HEADER_TASK_COUNT, taskCount.toString())
                .post(gzipped.toRequestBody(MIME_JSON.toMediaType()))
                .build(),
            token,
        )

    fun download(
        server: String,
        token: String,
        id: String,
    ): Request = withToken(get(server, "backups", id), token)

    fun withToken(
        request: Request,
        token: String,
    ): Request = request.newBuilder().header(BEARER, "Bearer $token").build()

    fun parseCopies(body: String): List<RemoteCopy> {
        val array = runCatching { JSONObject(body).optJSONArray("copies") }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let(::parseCopy)?.let(::add)
            }
        }
    }

    fun parseCopy(body: String): RemoteCopy? = runCatching { JSONObject(body) }.getOrNull()?.let(::parseCopy)

    fun parseToken(body: String): String? = runCatching { JSONObject(body).optString("token") }.getOrNull()?.takeIf { it.isNotBlank() }

    fun errorMessage(body: String): String? =
        runCatching { JSONObject(body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.take(ERROR_MESSAGE_LIMIT)

    private fun parseCopy(json: JSONObject): RemoteCopy? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return RemoteCopy(
            id = id,
            savedAt = Instant.ofEpochMilli(json.optLong("createdAtEpochMillis", 0L)),
            taskCount = json.optInt("taskCount", 0),
            sizeBytes = json.optLong("sizeBytes", 0L),
            sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
        )
    }

    private fun get(
        server: String,
        vararg segments: String,
    ): Request =
        Request
            .Builder()
            .url(url(server, *segments))
            .get()
            .build()

    private fun url(
        server: String,
        vararg segments: String,
    ): HttpUrl {
        val builder = server.trim().toHttpUrl().newBuilder()
        builder.addPathSegment(API)
        builder.addPathSegment(VERSION)
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun jsonRequest(
        url: HttpUrl,
        json: String,
    ): Request =
        Request
            .Builder()
            .url(url)
            .post(json.toRequestBody(MIME_JSON.toMediaType()))
            .build()

    private const val ERROR_MESSAGE_LIMIT = 200
}
