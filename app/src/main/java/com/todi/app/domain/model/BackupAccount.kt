package com.todi.app.domain.model

import java.time.Instant

data class BackupAccount(
    val email: String,
    val token: String,
    val expiresAt: Instant? = null,
)
