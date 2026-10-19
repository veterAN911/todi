package com.todi.app.backup

import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpBackupTransport
    @Inject
    constructor(
        private val client: OkHttpClient,
    ) : BackupTransport {
        override suspend fun list(
            server: String,
            token: String,
        ): List<RemoteCopy> = BackupRequests.parseCopies(client.executeBackupCall { BackupRequests.list(server, token) })

        override suspend fun upload(
            server: String,
            token: String,
            snapshot: String,
            taskCount: Int,
        ): RemoteCopy =
            BackupRequests.parseCopy(
                client.executeBackupCall { BackupRequests.upload(server, token, gzip(snapshot), taskCount) },
            ) ?: throw BackupException(BackupException.Kind.MALFORMED)

        override suspend fun download(
            server: String,
            token: String,
            id: String,
        ): String = client.executeBackupCall { BackupRequests.download(server, token, id) }

        private fun gzip(json: String): ByteArray {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            return out.toByteArray()
        }
    }
