package com.todi.app.backup

import com.todi.app.backup.BackupFailureKind.EMPTY_LOCAL
import com.todi.app.backup.BackupFailureKind.TOO_NEW
import com.todi.app.backup.BackupFailureKind.UNREADABLE
import com.todi.app.domain.model.Task
import com.todi.app.domain.util.SnapshotDecode
import com.todi.app.domain.util.SnapshotDecode.Restored
import com.todi.app.domain.util.SnapshotDecode.TooNew
import com.todi.app.domain.util.SnapshotDecode.Unreadable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class BackupPolicyTest {
    private val now: Instant = Instant.ofEpochMilli(1_787_000_000_000)

    private fun allowed(
        enabled: Boolean = true,
        last: Instant? = null,
    ) = BackupPolicy.shouldUpload(enabled, last, now)

    @Test
    fun switchedOff_neverUploads() {
        assertFalse(allowed(enabled = false))
    }

    @Test
    fun firstEverBackup_isAllowedImmediately() {
        assertTrue(allowed(last = null))
    }

    @Test
    fun twelveHoursAfterTheLastOne_isAllowed() {
        assertTrue(allowed(last = now.minus(Duration.ofHours(12))))
    }

    @Test
    fun soonerThanTwelveHours_isNot() {
        assertFalse(allowed(last = now.minus(Duration.ofHours(11))))
        assertFalse(allowed(last = now.minusSeconds(1)))
    }

    @Test
    fun aBackupFromTheFuture_stillCountsAsRecent() {
        assertFalse(allowed(last = now.plusSeconds(3600)))
    }

    @Test
    fun aListWithTasks_isAlwaysSafeToUpload() {
        assertNull(guard(local = 3, remote = Restored(listOf(task()))))
        assertNull(guard(local = 3, remote = null))
        assertNull(guard(local = 3, remote = Unreadable))
    }

    @Test
    fun anEmptyList_doesNotEraseACopyThatHasTasks() {
        assertEquals(EMPTY_LOCAL, guard(local = 0, remote = Restored(listOf(task(), task()))))
    }

    @Test
    fun anEmptyList_updatesACopyThatIsAlsoEmpty() {
        assertNull(guard(local = 0, remote = Restored(emptyList())))
    }

    @Test
    fun onlyLiveRows_areCounted() {
        assertEquals(0, BackupPolicy.liveCount(listOf(tombstone(), tombstone())))
        assertEquals(2, BackupPolicy.liveCount(listOf(task(), tombstone(), task())))
        assertEquals(0, BackupPolicy.liveCount(emptyList()))
    }

    @Test
    fun anEmptyList_erasesACopyThatHoldsOnlyTombstones() {
        assertNull(guard(local = 0, remote = Restored(listOf(tombstone(), tombstone()))))
    }

    @Test
    fun aPhoneOfTombstones_doesNotEraseACopyThatHasTasks() {
        val tombstoned = listOf(tombstone())
        assertEquals(EMPTY_LOCAL, guard(local = BackupPolicy.liveCount(tombstoned), remote = Restored(listOf(task()))))
    }

    @Test
    fun anEmptyList_doesNotEraseACopyItCannotRead() {
        assertEquals(UNREADABLE, guard(local = 0, remote = Unreadable))
        assertEquals(TOO_NEW, guard(local = 0, remote = TooNew(version = 99)))
    }

    @Test
    fun anEmptyList_writesWhenTheServerHoldsNothing() {
        assertNull(guard(local = 0, remote = null))
    }

    private fun guard(
        local: Int,
        remote: SnapshotDecode?,
    ) = BackupPolicy.uploadGuard(local, remote)

    private fun task() =
        Task(
            id = 1,
            title = "Buy bread",
            date = LocalDate.of(2026, 9, 13),
            time = null,
            note = null,
            completed = false,
            createdAt = now,
            updatedAt = now,
            syncId = "sync1",
        )

    private fun tombstone() = task().copy(deletedAt = now)
}
