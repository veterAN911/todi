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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoGlobalTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao

    private val today = 20_000L

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

    private fun task(
        title: String,
        date: Long = today,
        isGlobal: Boolean = false,
        completed: Boolean = false,
        completedAt: Long? = null,
        time: Int? = null,
    ) = TaskEntity(
        title = title,
        dateEpochDay = date,
        timeMinuteOfDay = time,
        note = null,
        completed = completed,
        createdAtEpochMillis = 1_000L + title.hashCode().toLong(),
        updatedAtEpochMillis = 2_000L,
        syncId = "sync-${title.hashCode()}",
        isGlobal = isGlobal,
        completedAtEpochMillis = completedAt,
    )

    private fun global(name: String = "глобальное") = task(name, isGlobal = true)

    @Test
    fun observeTasksForDate_excludesGlobalTasks() =
        runBlocking {
            dao.insert(task("сегодня"))
            dao.insert(global())

            assertEquals(listOf("сегодня"), dao.observeTasksForDate(today).first().map { it.title })
        }

    @Test
    fun observeActiveTasksForDate_excludesGlobalTasks() =
        runBlocking {
            dao.insert(task("сегодня"))
            dao.insert(global())

            assertEquals(listOf("сегодня"), dao.observeActiveTasksForDate(today).first().map { it.title })
        }

    @Test
    fun observeOverdueTasks_excludesGlobalTasks() =
        runBlocking {
            dao.insert(task("вчера", date = today - 1))

            dao.insert(global())

            assertEquals(listOf("вчера"), dao.observeOverdueTasks(today).first().map { it.title })
        }

    @Test
    fun observeActiveTasksBetween_excludesGlobalTasks() =
        runBlocking {
            dao.insert(task("сегодня"))
            dao.insert(global())

            val between = dao.observeActiveTasksBetween(today - 30, today + 30).first()
            assertEquals(listOf("сегодня"), between.map { it.title })
        }

    @Test
    fun carryOverIncomplete_skipsGlobalTasks() =
        runBlocking {
            val createdOn = today - 5
            dao.insert(task("вчера", date = today - 1))
            dao.insert(global("позвонить").copy(dateEpochDay = createdOn))

            val moved = dao.carryOverIncomplete(target = today, now = 5_000L)

            assertEquals("переносится только дело с датой", 1, moved)
            val globalRow = dao.observeGlobalTasks().first().single()
            assertEquals("дата создания глобального дела не меняется", createdOn, globalRow.dateEpochDay)
            assertNull("перенос не ставит отметку «с …»", globalRow.carriedFromEpochDay)
        }

    @Test
    fun observeGlobalTasks_returnsOnlyActiveDateLessTasks() =
        runBlocking {
            dao.insert(global("позвонить"))
            dao.insert(global("оплатить"))

            dao.insert(global("сделанное").copy(completed = true, completedAtEpochMillis = 3_000L))
            dao.insert(task("обычное"))

            val titles =
                dao
                    .observeGlobalTasks()
                    .first()
                    .map { it.title }
                    .toSet()

            assertEquals(setOf("позвонить", "оплатить"), titles)
        }

    @Test
    fun observeCompletedTasks_ordersByCompletionNewestFirst() =
        runBlocking {
            dao.insert(task("старое", completed = true, completedAt = 1_000L))
            dao.insert(task("новое", completed = true, completedAt = 3_000L))
            dao.insert(task("среднее", completed = true, completedAt = 2_000L))
            dao.insert(task("активное"))

            assertEquals(
                listOf("новое", "среднее", "старое"),
                dao.observeCompletedTasks().first().map { it.title },
            )
        }

    @Test
    fun observeCompletedTasks_includesGlobalTasks() =
        runBlocking {
            dao.insert(global("глобальное выполненное").copy(completed = true, completedAtEpochMillis = 7_000L))

            assertEquals(
                listOf("глобальное выполненное"),
                dao.observeCompletedTasks().first().map { it.title },
            )
        }

    @Test
    fun deleteCompleted_withACutoff_keepsTheRecentOnes() =
        runBlocking {
            dao.insert(task("февраль", completed = true, completedAt = 1_000L))
            dao.insert(task("май", completed = true, completedAt = 5_000L))
            dao.insert(task("активное"))

            val removed = dao.deleteCompleted(before = 2_000L)

            assertEquals(1, removed)
            assertEquals(
                setOf("май", "активное"),
                dao
                    .observeAllTasks()
                    .first()
                    .map { it.title }
                    .toSet(),
            )
        }

    @Test
    fun deleteCompleted_withoutACutoff_removesTheWholeArchive() =
        runBlocking {
            dao.insert(task("старое", completed = true, completedAt = 1_000L))
            dao.insert(task("без отметки", completed = true, completedAt = null))
            dao.insert(task("активное"))

            val removed = dao.deleteCompleted(before = null)

            assertEquals(2, removed)
            assertEquals(listOf("активное"), dao.observeAllTasks().first().map { it.title })
        }

    @Test
    fun deleteCompleted_withACutoff_neverTouchesRowsWithoutACompletionTime() =
        runBlocking {
            dao.insert(task("без отметки", completed = true, completedAt = null))

            assertEquals(0, dao.deleteCompleted(before = 9_000L))
            assertEquals(1, dao.observeAllTasks().first().size)
        }

    @Test
    fun deleteCompleted_withACutoff_keepsTasksOnTheCutoff() =
        runBlocking {
            dao.insert(task("ровно", completed = true, completedAt = 2_000L))

            assertEquals(0, dao.deleteCompleted(before = 2_000L))
        }
}
