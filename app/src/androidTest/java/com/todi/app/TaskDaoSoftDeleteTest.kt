package com.todi.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todi.app.data.local.AppDatabase
import com.todi.app.data.local.TaskDao
import com.todi.app.data.local.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoSoftDeleteTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao

    private val day1 = 20_000L
    private val day2 = 20_001L
    private val created = 1_756_000_000_000L
    private val now = 1_756_100_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.taskDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(
        title: String,
        date: Long = day1,
        completed: Boolean = false,
        isGlobal: Boolean = false,
        completedAt: Long? = null,
    ) = dao.insert(
        TaskEntity(
            title = title,
            dateEpochDay = date,
            timeMinuteOfDay = null,
            note = null,
            completed = completed,
            createdAtEpochMillis = created,
            updatedAtEpochMillis = created,
            syncId = "sync-$title",
            isGlobal = isGlobal,
            completedAtEpochMillis = completedAt,
        ),
    )

    @Test
    fun aDeletedTask_isGoneFromEveryList() =
        runBlocking {
            val deleted = insert("Удалённое", date = day1)
            val deletedGlobal = insert("Удалённое без даты", isGlobal = true)
            val deletedDone = insert("Удалённое выполненное", completed = true, completedAt = created)
            dao.markDeleted(deleted, now)
            dao.markDeleted(deletedGlobal, now)
            dao.markDeleted(deletedDone, now)

            val live = insert("Живое", date = day1)
            val liveDone = insert("Живое выполненное", completed = true, completedAt = created)
            val liveGlobal = insert("Живое без даты", isGlobal = true)

            assertEquals(listOf(live, liveDone), dao.observeTasksForDate(day1).first().map { it.id })
            assertEquals(listOf(live), dao.observeActiveTasksForDate(day1).first().map { it.id })
            assertEquals(
                listOf(live),
                dao.observeOverdueTasks(beforeDate = day2).first().map { it.id },
            )
            assertEquals(listOf(live), dao.observeActiveTasksBetween(day1, day2).first().map { it.id })
            assertEquals(listOf(liveGlobal), dao.observeGlobalTasks().first().map { it.id })
            assertEquals(listOf(liveDone), dao.observeCompletedTasks().first().map { it.id })
        }

    @Test
    fun theTombstoneIsStillThere_forWhoeverHasToKnow() =
        runBlocking {
            val id = insert("Удалённое")
            dao.markDeleted(id, now)

            assertEquals(listOf(id), dao.observeAllTasks().first().map { it.id })
            assertNotNull(dao.getById(id)?.deletedAtEpochMillis)
        }

    @Test
    fun aDeletedTask_isNotCarriedOver() =
        runBlocking {
            val id = insert("Удалённое", date = day1)
            dao.markDeleted(id, now)

            assertEquals(0, dao.carryOverIncomplete(target = day2, now = now))
            assertEquals(day1, dao.getById(id)!!.dateEpochDay)
        }

    @Test
    fun theArchiveCleanup_leavesTombstonesAlone() =
        runBlocking {
            val kept = insert("Удалённое выполненное", completed = true, completedAt = created)
            dao.markDeleted(kept, now)
            val cleared = insert("Выполненное", completed = true, completedAt = created)

            assertEquals(1, dao.deleteCompleted(before = null))

            assertNull(dao.getById(cleared))
            assertNotNull(dao.getById(kept))
        }

    @Test
    fun deletingTwice_keepsTheFirstTimestamp() =
        runBlocking {
            val id = insert("Удалённое")
            dao.markDeleted(id, now)
            dao.markDeleted(id, now + 60_000)

            assertEquals(now, dao.getById(id)!!.deletedAtEpochMillis)
        }

    @Test
    fun deletingTwice_doesNotMoveTheVersionForward() =
        runBlocking {
            val id = insert("Удалённое")
            dao.markDeleted(id, now)

            assertEquals(0, dao.markDeleted(id, now + 60_000))
            assertEquals(now, dao.getById(id)!!.updatedAtEpochMillis)
        }

    @Test
    fun oldTombstones_arePurged_andFreshOnesStay() =
        runBlocking {
            val old = insert("Давно удалённое")
            val fresh = insert("Только что удалённое")
            dao.markDeleted(old, now - 100_000)
            dao.markDeleted(fresh, now)

            assertEquals(1, dao.deleteTombstonesBefore(now - 50_000))

            assertNull(dao.getById(old))
            assertNotNull(dao.getById(fresh))
        }

    @Test
    fun aRestoredRowWithoutAnId_arrivesUnderAFreshOne() =
        runBlocking {
            val first = insert("Своё")

            dao.insertAll(
                listOf(
                    TaskEntity(
                        id = 0,
                        title = "Из копии",
                        dateEpochDay = day1,
                        timeMinuteOfDay = null,
                        note = null,
                        completed = false,
                        createdAtEpochMillis = created,
                        updatedAtEpochMillis = created,
                        syncId = "sync-копии",
                    ),
                ),
            )

            val rows = dao.observeAllTasks().first()
            assertEquals(2, rows.size)
            assertEquals(1, rows.count { it.id != 0L && it.id != first })
        }
}
