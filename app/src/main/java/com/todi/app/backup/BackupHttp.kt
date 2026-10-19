package com.todi.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal suspend fun OkHttpClient.executeBackupCall(build: () -> Request): String =
    withContext(Dispatchers.IO) {
        val response =
            try {
                newCall(build()).execute()
            } catch (e: IOException) {
                throw BackupException(BackupException.Kind.NETWORK, cause = e)
            } catch (e: IllegalArgumentException) {
                throw BackupException(BackupException.Kind.MALFORMED, cause = e)
            }
        response.use { answer ->

            val body =
                try {
                    answer.body?.string().orEmpty()
                } catch (e: IOException) {
                    throw BackupException(BackupException.Kind.NETWORK, cause = e)
                }
            if (!answer.isSuccessful) throw failureFor(answer.code, body)
            body
        }
    }

private fun failureFor(
    code: Int,
    body: String,
): BackupException =
    BackupException(
        kind =
            when (code) {
                401, 403 -> BackupException.Kind.AUTH
                404 -> BackupException.Kind.NOT_FOUND
                409 -> BackupException.Kind.CONFLICT
                429 -> BackupException.Kind.RATE_LIMITED
                else -> BackupException.Kind.SERVER
            },
        code = code,
        detail = BackupRequests.errorMessage(body),
    )
