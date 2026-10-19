package com.todi.app.backup

enum class BackupFailureKind {
    NOT_SIGNED_IN,
    NETWORK,

    RATE_LIMITED,
    NOT_FOUND,
    UNREADABLE,
    TOO_NEW,

    EMPTY_LOCAL,
    FAILED,
}

data class BackupFailure(
    val kind: BackupFailureKind,
    val code: Int? = null,
    val message: String? = null,
)
