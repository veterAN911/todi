package com.todi.app.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.todi.app.R
import com.todi.app.backup.BackupFailure
import com.todi.app.backup.BackupFailureKind
import com.todi.app.presentation.util.DateFormatters
import java.time.Instant
import java.time.ZoneId

@Composable
fun backupFailureText(failure: BackupFailure): String {
    val label =
        stringResource(
            when (failure.kind) {
                BackupFailureKind.NOT_SIGNED_IN -> R.string.backup_error_not_signed_in
                BackupFailureKind.NETWORK -> R.string.backup_error_network
                BackupFailureKind.RATE_LIMITED -> R.string.backup_error_rate_limited
                BackupFailureKind.NOT_FOUND -> R.string.backup_error_not_found
                BackupFailureKind.UNREADABLE -> R.string.backup_error_unreadable
                BackupFailureKind.TOO_NEW -> R.string.backup_error_too_new
                BackupFailureKind.EMPTY_LOCAL -> R.string.backup_error_empty_local
                BackupFailureKind.FAILED -> R.string.backup_error_failed
            },
        )
    val detail =
        listOfNotNull(
            failure.code?.let { stringResource(R.string.backup_error_code, it) },
            failure.message,
        ).joinToString(" — ")
    return if (detail.isBlank()) label else "$label — $detail"
}

@Composable
fun backupInstantText(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val moment = instant.atZone(ZoneId.systemDefault())
    return DateFormatters.dayMonth(moment.toLocalDate(), locale) + ", " + DateFormatters.time(moment.toLocalTime())
}

@Composable
fun backupSizeText(bytes: Long): String {
    val kilobytes = bytes / 1024
    return when {
        kilobytes == 0L -> {
            stringResource(R.string.settings_backup_size_under_kb)
        }

        kilobytes < 1024 -> {
            stringResource(R.string.settings_backup_size_kb, kilobytes)
        }

        else -> {
            val locale = LocalConfiguration.current.locales[0]
            stringResource(R.string.settings_backup_size_mb, String.format(locale, "%.1f", kilobytes / 1024.0))
        }
    }
}
