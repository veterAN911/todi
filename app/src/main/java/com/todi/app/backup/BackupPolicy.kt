package com.todi.app.backup

import com.todi.app.domain.model.Task
import com.todi.app.domain.util.SnapshotDecode
import java.time.Duration
import java.time.Instant

internal object BackupPolicy {
    val MIN_INTERVAL: Duration = Duration.ofHours(12)

    fun shouldUpload(
        enabled: Boolean,
        lastBackupAt: Instant?,
        now: Instant,
    ): Boolean {
        if (!enabled) return false
        if (lastBackupAt == null) return true
        return Duration.between(lastBackupAt, now) >= MIN_INTERVAL
    }

    fun liveCount(tasks: List<Task>): Int = tasks.count { it.deletedAt == null }

    fun uploadGuard(
        localTaskCount: Int,
        remote: SnapshotDecode?,
    ): BackupFailureKind? {
        if (localTaskCount > 0 || remote == null) return null
        return when (remote) {
            is SnapshotDecode.Restored -> {
                if (liveCount(remote.tasks) == 0) null else BackupFailureKind.EMPTY_LOCAL
            }

            SnapshotDecode.Unreadable -> {
                BackupFailureKind.UNREADABLE
            }

            is SnapshotDecode.TooNew -> {
                BackupFailureKind.TOO_NEW
            }
        }
    }
}
