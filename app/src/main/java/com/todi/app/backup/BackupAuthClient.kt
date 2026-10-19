package com.todi.app.backup

import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupAuthClient
    @Inject
    constructor(
        private val client: OkHttpClient,
    ) {
        suspend fun requestCode(
            server: String,
            email: String,
        ) {
            client.executeBackupCall { BackupRequests.requestCode(server, email) }
        }

        suspend fun verifyCode(
            server: String,
            email: String,
            code: String,
        ): String =
            BackupRequests.parseToken(client.executeBackupCall { BackupRequests.verifyCode(server, email, code) })
                ?: throw BackupException(BackupException.Kind.MALFORMED)
    }
