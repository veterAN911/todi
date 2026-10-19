package com.todi.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todi.app.data.TaskRepositoryImpl
import com.todi.app.data.local.AppDatabase
import com.todi.app.data.local.TaskDao
import com.todi.app.data.local.TaskEntity
import com.todi.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RestoreCompletedTaskDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao
    private lateinit var repository: TaskRepository

    private val plannedFor = 20_000L
    private val target = 20_030L

    private val stamp = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.taskDao()
        repository = TaskRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun archived(
        title: String = "Купить хлеб",
        isGlobal: Boolean = false,
        deleted: Boolean = false,
    ) = dao.insert(
        TaskEntity(
            title = title,
            dateEpochDay = plannedFor,
            timeMinuteOfDay = null,
            note = "заметка",
            completed = true,
            createdAtEpochMillis = stamp,
            updatedAtEpochMillis = stamp,
            syncId = "sync-$title",
            isGlobal = isGlobal,
            completedAtEpochMillis = stamp,
            deletedAtEpochMillis = if (deleted) stamp else null,
        ),
    )

    @Test
    fun restore_writesEveryFieldTheArchiveDependsOn() =
        runBlocking {
            val id = archived()

            repository.restoreCompletedTask(id, LocalDate.ofEpochDay(target))

            val row = dao.getById(id)!!
            assertFalse("отметка снимается", row.completed)
            assertNull("момент выполнения не переживает возврат", row.completedAtEpochMillis)
            assertEquals("день — тот, который назвали", target, row.dateEpochDay)
            assertFalse("дело с днём больше не бездатное", row.isGlobal)
        }

    @Test
    fun restore_movesUpdatedAt() =
        runBlocking {
            val id = archived()

            repository.restoreCompletedTask(id, LocalDate.ofEpochDay(target))

            assertTrue(
                "отметка времени правки должна сдвинуться вперёд",
                dao.getById(id)!!.updatedAtEpochMillis > stamp,
            )
        }

    @Test
    fun restoredTask_landsInTheTargetDayAndLeavesTheArchive() =
        runBlocking {
            val id = archived("Сдать книгу")

            repository.restoreCompletedTask(id, LocalDate.ofEpochDay(target))

            assertEquals(
                listOf("Сдать книгу"),
                dao.observeActiveTasksForDate(target).first().map { it.title },
            )
            assertTrue("в архиве его быть не должно", dao.observeCompletedTasks().first().isEmpty())
        }

    @Test
    fun restore_ofADateLessTask_givesItTheChosenDay() =
        runBlocking {
            val id = archived("Полить цветы", isGlobal = true)

            repository.restoreCompletedTask(id, LocalDate.ofEpochDay(target))

            assertEquals(target, dao.getById(id)!!.dateEpochDay)
            assertEquals(
                listOf("Полить цветы"),
                dao.observeActiveTasksForDate(target).first().map { it.title },
            )
        }

    @Test
    fun restore_ofADeletedTask_leavesItDeleted() =
        runBlocking {
            val id = archived(deleted = true)

            repository.restoreCompletedTask(id, LocalDate.ofEpochDay(target))

            assertNotNull("надгробие остаётся надгробием", dao.getById(id)!!.deletedAtEpochMillis)
            assertTrue(dao.observeActiveTasksForDate(target).first().isEmpty())
            assertTrue(dao.observeCompletedTasks().first().isEmpty())
        }

    @Test
    fun restore_ofAnUnknownId_writesNothing() =
        runBlocking {
            val id = archived()

            repository.restoreCompletedTask(id + 999, LocalDate.ofEpochDay(target))

            assertEquals(1, dao.observeAllTasks().first().size)
            assertEquals(plannedFor, dao.getById(id)!!.dateEpochDay)
            assertTrue("ничего не должно было вернуться", dao.getById(id)!!.completed)
        }
}
