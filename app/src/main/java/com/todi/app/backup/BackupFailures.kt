package com.todi.app.backup

internal fun BackupException.toBackupFailure(): BackupFailure =
    when (kind) {
        BackupException.Kind.AUTH -> BackupFailure(BackupFailureKind.NOT_SIGNED_IN, code, detail)
        BackupException.Kind.NOT_FOUND -> BackupFailure(BackupFailureKind.NOT_FOUND, code, detail)
        BackupException.Kind.NETWORK -> BackupFailure(BackupFailureKind.NETWORK, code, detail)
        BackupException.Kind.RATE_LIMITED -> BackupFailure(BackupFailureKind.RATE_LIMITED, code, detail)
        BackupException.Kind.CONFLICT -> BackupFailure(BackupFailureKind.EMPTY_LOCAL, code, detail)
        BackupException.Kind.MALFORMED -> BackupFailure(BackupFailureKind.UNREADABLE, code, detail)
        BackupException.Kind.SERVER -> BackupFailure(BackupFailureKind.FAILED, code, detail)
    }

internal val BackupFailureKind.isWorthRetrying: Boolean
    get() =
        when (this) {
            BackupFailureKind.NETWORK,
            BackupFailureKind.RATE_LIMITED,
            BackupFailureKind.FAILED,
            -> true

            BackupFailureKind.NOT_SIGNED_IN,
            BackupFailureKind.NOT_FOUND,
            BackupFailureKind.UNREADABLE,
            BackupFailureKind.TOO_NEW,
            BackupFailureKind.EMPTY_LOCAL,
            -> false
        }
