package com.todi.app.domain.repository

import com.todi.app.domain.model.BackupAccount
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BackupAccountRepository {
    val account: Flow<BackupAccount?>

    suspend fun lastUploadedDigest(): String?

    suspend fun rememberUploadedDigest(digest: String)

    suspend fun signIn(
        email: String,
        token: String,
        expiresAt: Instant?,
    )

    suspend fun signOut()
}
