package com.todi.app.backup

import com.todi.app.domain.FakeSettingsRepository
import com.todi.app.domain.FakeTaskRepository
import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.BackupAccount
import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.BackupAccountRepository
import com.todi.app.domain.util.SnapshotDecode
import com.todi.app.domain.util.TaskSnapshotCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BackupManagerTest {
    private val now: Instant = Instant.ofEpochMilli(1_787_000_000_000)

    @Test
    fun restore_replacesTheList_ratherThanAddingToIt() =
        runTest {
            val tasks = FakeTaskRepository()
            val mine = tasks.createTask("Купить хлеб", day(10), null, null, false)
            val transport = FakeTransport()
            transport.seed(TaskSnapshotCodec.encode(listOf(task(99, "Из копии")), now))

            val outcome = manager(tasks, transport, FakeAccounts(account())).restoreNow("copy1")

            assertEquals(RestoreOutcome.Restored(1), outcome)
            val after = tasks.observeAllTasks().first()
            assertEquals(listOf(99L), after.map { it.id })
            assertTrue("задача, которой нет в копии, должна исчезнуть", after.none { it.id == mine.id })
        }

    @Test
    fun restore_putsASafetyCopyOfTheCurrentListOnTheServer_first() =
        runTest {
            val tasks = FakeTaskRepository()
            tasks.createTask("Своё дело", day(10), null, null, false)
            val transport = FakeTransport()
            transport.seed(TaskSnapshotCodec.encode(listOf(task(99, "Из копии")), now))

            manager(tasks, transport, FakeAccounts(account())).restoreNow("copy1")

            val safety = TaskSnapshotCodec.decode(transport.uploads.single())
            assertEquals(listOf("Своё дело"), (safety as SnapshotDecode.Restored).tasks.map { it.title })
        }

    @Test
    fun restore_ontoAnEmptyPhone_doesNotUploadTheSafetyCopy() =
        runTest {
            val tasks = FakeTaskRepository()
            val transport = FakeTransport()
            transport.seed(TaskSnapshotCodec.encode(listOf(task(99, "Из копии")), now))

            val outcome = manager(tasks, transport, FakeAccounts(account())).restoreNow("copy1")

            assertEquals(RestoreOutcome.Restored(1), outcome)
            assertTrue(transport.uploads.isEmpty())
        }

    @Test
    fun restore_ofACopyThatCannotBeRead_writesNothing() =
        runTest {
            val tasks = FakeTaskRepository()
            val mine = tasks.createTask("Своё дело", day(10), null, null, false)
            val transport = FakeTransport()
            transport.seed("это не снимок")

            val outcome = manager(tasks, transport, FakeAccounts(account())).restoreNow("copy1")

            assertEquals(RestoreOutcome.Failed(BackupFailure(BackupFailureKind.UNREADABLE)), outcome)
            assertEquals(listOf(mine.id), tasks.observeAllTasks().first().map { it.id })
            assertTrue(transport.uploads.isEmpty())
        }

    @Test
    fun restore_ofACopyFromANewerApp_refusesIt() =
        runTest {
            val tasks = FakeTaskRepository()
            val transport = FakeTransport()
            transport.seed("""{"version":99,"app":"todi","tasks":[]}""")

            val outcome = manager(tasks, transport, FakeAccounts(account())).restoreNow("copy1")

            assertEquals(RestoreOutcome.Failed(BackupFailure(BackupFailureKind.TOO_NEW)), outcome)
        }

    @Test
    fun anEmptyList_doesNotOverwriteACopyThatHasTasks() =
        runTest {
            val transport = FakeTransport()
            transport.seed(TaskSnapshotCodec.encode(listOf(task(1, "Чужое дело")), now))

            val outcome = manager(FakeTaskRepository(), transport, FakeAccounts(account())).backupNow()

            assertEquals(BackupOutcome.Failed(BackupFailure(BackupFailureKind.EMPTY_LOCAL)), outcome)
            assertTrue(transport.uploads.isEmpty())
        }

    @Test
    fun anUnchangedList_isNotSentTwice() =
        runTest {
            val tasks = FakeTaskRepository()
            tasks.createTask("Купить хлеб", day(10), null, null, false)
            val transport = FakeTransport()
            val manager = manager(tasks, transport, FakeAccounts(account()))

            assertEquals(BackupOutcome.Uploaded(now), manager.backupNow())

            assertEquals(BackupOutcome.UpToDate, manager.backupNow())
            assertEquals(1, transport.uploads.size)
        }

    @Test
    fun aChangedList_isSentAgain() =
        runTest {
            val tasks = FakeTaskRepository()
            tasks.createTask("Купить хлеб", day(10), null, null, false)
            val transport = FakeTransport()
            val manager = manager(tasks, transport, FakeAccounts(account()))

            manager.backupNow()
            tasks.createTask("Позвонить", day(10), null, null, false)

            assertEquals(BackupOutcome.Uploaded(now), manager.backupNow())
            assertEquals(2, transport.uploads.size)
        }

    @Test
    fun aPhoneOfTombstones_doesNotOverwriteACopyThatHasTasks() =
        runTest {
            val tasks = FakeTaskRepository()
            val mine = tasks.createTask("Купить хлеб", day(10), null, null, false)
            tasks.deleteTask(mine.id)
            val transport = FakeTransport()
            transport.seed(TaskSnapshotCodec.encode(listOf(task(1, "Чужое дело")), now))

            val outcome = manager(tasks, transport, FakeAccounts(account())).backupNow()

            assertEquals(BackupOutcome.Failed(BackupFailure(BackupFailureKind.EMPTY_LOCAL)), outcome)
            assertTrue(transport.uploads.isEmpty())
        }

    @Test
    fun backupNow_sendsTheList_andStampsTheTime() =
        runTest {
            val tasks = FakeTaskRepository()
            tasks.createTask("Купить хлеб", day(10), null, null, false)
            val transport = FakeTransport()
            val settings = FakeSettingsRepository(AppSettings())

            val outcome = manager(tasks, transport, FakeAccounts(account()), settings).backupNow()

            assertEquals(BackupOutcome.Uploaded(now), outcome)
            assertEquals(1, transport.uploads.size)
            assertEquals(now, settings.current.backupLastUploadAt)
        }

    @Test
    fun withoutAnAccount_nothingIsSent() =
        runTest {
            val tasks = FakeTaskRepository()
            tasks.createTask("Купить хлеб", day(10), null, null, false)
            val transport = FakeTransport()

            val outcome = manager(tasks, transport, FakeAccounts(null)).backupNow()

            assertEquals(BackupOutcome.Failed(BackupFailure(BackupFailureKind.NOT_SIGNED_IN)), outcome)
            assertTrue(transport.uploads.isEmpty())
        }

    private fun manager(
        tasks: FakeTaskRepository,
        transport: FakeTransport,
        accounts: FakeAccounts,
        settings: FakeSettingsRepository = FakeSettingsRepository(AppSettings()),
    ) = BackupManager(
        transport = transport,
        tasks = tasks,
        settings = settings,
        accounts = accounts,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun account() = BackupAccount(email = "me@example.org", token = "tok")

    private fun day(number: Int) = LocalDate.of(2026, 9, number)

    private fun task(
        id: Long,
        title: String,
    ) = Task(
        id = id,
        title = title,
        date = day(10),
        time = null,
        note = null,
        completed = false,
        createdAt = now,
        updatedAt = now,
        syncId = "sync-$id",
    )
}

private class FakeTransport : BackupTransport {
    private val copies = mutableListOf<RemoteCopy>()
    private val bodies = mutableMapOf<String, String>()

    val uploads = mutableListOf<String>()

    fun seed(snapshot: String) {
        val id = "copy${copies.size + 1}"
        bodies[id] = snapshot
        copies +=
            RemoteCopy(
                id = id,
                savedAt = Instant.ofEpochMilli(1_787_000_000_000 + copies.size),
                taskCount = 1,
                sizeBytes = snapshot.length.toLong(),
            )
    }

    override suspend fun list(
        server: String,
        token: String,
    ): List<RemoteCopy> = copies.toList()

    override suspend fun upload(
        server: String,
        token: String,
        snapshot: String,
        taskCount: Int,
    ): RemoteCopy {
        uploads += snapshot
        seed(snapshot)
        return copies.last()
    }

    override suspend fun download(
        server: String,
        token: String,
        id: String,
    ): String = bodies.getValue(id)
}

private class FakeAccounts(
    initial: BackupAccount?,
) : BackupAccountRepository {
    private val state = MutableStateFlow(initial)

    private var digest: String? = null

    override val account: Flow<BackupAccount?> = state.asStateFlow()

    override suspend fun lastUploadedDigest(): String? = digest

    override suspend fun rememberUploadedDigest(digest: String) {
        this.digest = digest
    }

    override suspend fun signIn(
        email: String,
        token: String,
        expiresAt: Instant?,
    ) {
        state.value = BackupAccount(email, token, expiresAt)

        digest = null
    }

    override suspend fun signOut() {
        state.value = null
        digest = null
    }
}
