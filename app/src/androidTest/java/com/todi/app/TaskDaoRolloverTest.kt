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
class TaskDaoRolloverTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao

    private val day1 = 20_000L
    private val day2 = 20_001L
    private val day3 = 20_002L

    private val created = 1_756_000_000_000L

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
        date: Long,
        completed: Boolean = false,
        isGlobal: Boolean = false,
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
        ),
    )

    @Test
    fun carryOver_twice_keepsTheFirstDateItSlippedFrom() =
        runBlocking {
            val id = insert("Дело", day1)

            assertEquals(1, dao.carryOverIncomplete(target = day2, now = 1L))
            dao.carryOverIncomplete(target = day3, now = 2L)

            val row = dao.getById(id)!!
            assertEquals(day3, row.dateEpochDay)
            assertEquals(day1, row.carriedFromEpochDay)
        }

    @Test
    fun carryOver_neverRewritesTheCreationDate() =
        runBlocking {
            val id = insert("Дело", day1)

            dao.carryOverIncomplete(target = day2, now = 1L)
            dao.carryOverIncomplete(target = day3, now = 2L)

            val row = dao.getById(id)!!
            assertEquals(created, row.createdAtEpochMillis)
            assertEquals(2L, row.updatedAtEpochMillis)
        }

    @Test
    fun carryOver_onlyMovesWhatIsBehind() =
        runBlocking {
            val behind = insert("Просроченное", day1)
            val ahead = insert("Будущее", day3)
            val onTarget = insert("Сегодняшнее", day2)

            assertEquals(1, dao.carryOverIncomplete(target = day2, now = 1L))

            assertEquals(day2, dao.getById(behind)!!.dateEpochDay)
            assertEquals(day1, dao.getById(behind)!!.carriedFromEpochDay)
            assertEquals(day3, dao.getById(ahead)!!.dateEpochDay)
            assertNull(dao.getById(ahead)!!.carriedFromEpochDay)
            assertEquals(day2, dao.getById(onTarget)!!.dateEpochDay)
            assertNull(dao.getById(onTarget)!!.carriedFromEpochDay)
        }

    @Test
    fun carryOver_leavesCompletedAndGlobalRowsAlone() =
        runBlocking {
            val done = insert("Выполнено", day1, completed = true)
            val global = insert("Без даты", day1, isGlobal = true)

            assertEquals(0, dao.carryOverIncomplete(target = day3, now = 1L))

            assertEquals(day1, dao.getById(done)!!.dateEpochDay)
            assertEquals(day1, dao.getById(global)!!.dateEpochDay)
            assertNull(dao.getById(global)!!.carriedFromEpochDay)
        }

    @Test
    fun theCarriedTaskStillShowsUpInItsNewDay() =
        runBlocking {
            insert("Дело", day1)
            dao.carryOverIncomplete(target = day3, now = 1L)

            assertEquals(listOf("Дело"), dao.observeTasksForDate(day3).first().map { it.title })
        }
}
