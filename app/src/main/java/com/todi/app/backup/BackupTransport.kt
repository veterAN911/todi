package com.todi.app.backup

import java.time.Instant

data class RemoteCopy(
    val id: String,
    val savedAt: Instant,
    val taskCount: Int,
    val sizeBytes: Long,
    val sha256: String? = null,
)

interface BackupTransport {
    suspend fun list(
        server: String,
        token: String,
    ): List<RemoteCopy>

    suspend fun upload(
        server: String,
        token: String,
        snapshot: String,
        taskCount: Int,
    ): RemoteCopy

    suspend fun download(
        server: String,
        token: String,
        id: String,
    ): String
}
