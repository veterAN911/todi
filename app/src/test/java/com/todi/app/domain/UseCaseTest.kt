package com.todi.app.domain

import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetTasksForDateUseCase
import com.todi.app.domain.usecase.MoveTaskToDateUseCase
import com.todi.app.domain.usecase.MoveTaskToTomorrowUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class UseCaseTest {
    private val repository = FakeTaskRepository()
    private val todayProvider = FakeTodayProvider(LocalDate.of(2026, 8, 30))

    private val createTask = CreateTaskUseCase(repository)
    private val updateTask = UpdateTaskUseCase(repository)
    private val deleteTask = DeleteTaskUseCase(repository)
    private val completeTask = CompleteTaskUseCase(repository)
    private val moveToTomorrow = MoveTaskToTomorrowUseCase(repository, todayProvider)
    private val moveToDate = MoveTaskToDateUseCase(repository)
    private val getTasksForDate = GetTasksForDateUseCase(repository)

    @Before
    fun setUp() {
        todayProvider.setDate(LocalDate.of(2026, 8, 30))
    }

    @Test
    fun createTask_savesAllFieldsAndTrims() =
        runTest {
            val task =
                createTask(
                    title = "  Позвонить клиенту  ",
                    date = LocalDate.of(2026, 8, 30),
                    time = LocalTime.of(10, 0),
                    note = "  обсудить договор  ",
                )

            assertEquals("Позвонить клиенту", task.title)
            assertEquals(LocalDate.of(2026, 8, 30), task.date)
            assertEquals(LocalTime.of(10, 0), task.time)
            assertEquals("обсудить договор", task.note)
            assertFalse(task.completed)
            assertEquals(task, repository.getTask(task.id))
        }

    @Test
    fun createTask_blankNoteBecomesNull() =
        runTest {
            val task = createTask("Купить продукты", LocalDate.of(2026, 8, 30), null, "   ")
            assertNull(task.note)
        }

    @Test
    fun updateTask_changesFields() =
        runTest {
            val task = createTask("Старое", LocalDate.of(2026, 8, 30), null, null)

            updateTask(task.copy(title = "Новое", date = LocalDate.of(2026, 9, 1), time = LocalTime.of(9, 30), note = "детали"))

            val updated = repository.getTask(task.id)!!
            assertEquals("Новое", updated.title)
            assertEquals(LocalDate.of(2026, 9, 1), updated.date)
            assertEquals(LocalTime.of(9, 30), updated.time)
            assertEquals("детали", updated.note)
        }

    @Test
    fun deleteTask_hidesItEverywhere_andLeavesATombstone() =
        runTest {
            val task = createTask("Удалить", LocalDate.of(2026, 8, 30), null, null)

            deleteTask(task.id)

            val row = repository.getTask(task.id)
            assertNotNull(row)
            assertNotNull(row!!.deletedAt)

            assertTrue(getTasksForDate(LocalDate.of(2026, 8, 30)).first().isEmpty())
            assertFalse(repository.observeAllTasks().first().isEmpty())
        }

    @Test
    fun restoringADeletedTask_bringsBackTheSameRow() =
        runTest {
            val task = createTask("Удалить", LocalDate.of(2026, 8, 30), null, null)
            deleteTask(task.id)

            RestoreTaskUseCase(repository).invoke(task)

            assertNull(repository.getTask(task.id)!!.deletedAt)
            assertEquals(listOf(task.id), getTasksForDate(LocalDate.of(2026, 8, 30)).first().map { it.id })
        }

    @Test
    fun completeTask_togglesBothWays() =
        runTest {
            val task = createTask("Задача", LocalDate.of(2026, 8, 30), null, null)

            completeTask(task.id, completed = true)
            assertTrue(repository.getTask(task.id)!!.completed)

            completeTask(task.id, completed = false)
            assertFalse(repository.getTask(task.id)!!.completed)
        }

    @Test
    fun moveToTomorrow_movesOneDayForward() =
        runTest {
            val task = createTask("Перенос", LocalDate.of(2026, 8, 30), null, null)

            moveToTomorrow(task.id)

            assertEquals(LocalDate.of(2026, 8, 31), repository.getTask(task.id)!!.date)
        }

    @Test
    fun moveToDate_movesToArbitraryDate() =
        runTest {
            val task = createTask("Перенос", LocalDate.of(2026, 8, 30), null, null)

            moveToDate(task.id, LocalDate.of(2026, 9, 15))

            assertEquals(LocalDate.of(2026, 9, 15), repository.getTask(task.id)!!.date)
        }

    @Test
    fun getTasksForDate_returnsOnlyThatDay() =
        runTest {
            createTask("Сегодня", LocalDate.of(2026, 8, 30), null, null)
            createTask("Завтра", LocalDate.of(2026, 8, 31), null, null)
            createTask("Послезавтра", LocalDate.of(2026, 9, 1), null, null)

            val result = getTasksForDate(LocalDate.of(2026, 8, 31)).first()

            assertEquals(1, result.size)
            assertEquals("Завтра", result.single().title)
        }

    @Test
    fun createTask_givesEveryTaskItsOwnSyncId() =
        runTest {
            val first = createTask("Раз", LocalDate.of(2026, 8, 30), null, null)
            val second = createTask("Два", LocalDate.of(2026, 8, 30), null, null)

            assertTrue(first.syncId.isNotBlank())
            assertNotEquals(first.syncId, second.syncId)
        }

    @Test
    fun editingATask_keepsItsSyncId() =
        runTest {
            val task = createTask("Черновик", LocalDate.of(2026, 8, 30), null, null)

            updateTask(task.copy(title = "Готово", date = LocalDate.of(2026, 9, 2)))
            completeTask(task.id, completed = true)

            assertEquals(task.syncId, repository.getTask(task.id)!!.syncId)
        }

    @Test
    fun createGlobalTask_hasNoTimeAndStaysOutOfTheDayList() =
        runTest {
            val global =
                createTask(
                    title = "Позвонить в сервис",
                    date = LocalDate.of(2026, 8, 30),
                    time = LocalTime.of(10, 0),
                    note = null,
                    isGlobal = true,
                )

            assertTrue(global.isGlobal)

            assertNull(global.time)
            assertTrue(getTasksForDate(LocalDate.of(2026, 8, 30)).first().isEmpty())
            assertEquals(listOf(global.id), repository.observeGlobalTasks().first().map { it.id })
        }

    @Test
    fun completeTask_stampsCompletedAt_andClearsItWhenReopened() =
        runTest {
            val task = createTask("Задача", LocalDate.of(2026, 8, 30), null, null)
            assertNull(task.completedAt)

            completeTask(task.id, completed = true)
            assertNotNull(repository.getTask(task.id)!!.completedAt)

            completeTask(task.id, completed = false)
            assertNull(repository.getTask(task.id)!!.completedAt)
        }

    @Test
    fun updateTask_switchingToGlobalDropsTimeAndCarriedFrom() =
        runTest {
            val task = createTask("Дело", LocalDate.of(2026, 8, 30), LocalTime.of(9, 0), null)
            updateTask(task.copy(carriedFrom = LocalDate.of(2026, 8, 20)))

            updateTask(repository.getTask(task.id)!!.copy(isGlobal = true))

            val updated = repository.getTask(task.id)!!
            assertTrue(updated.isGlobal)
            assertNull(updated.time)
            assertNull(updated.carriedFrom)
        }

    @Test
    fun moveToDate_turnsAGlobalTaskBackIntoADatedOne() =
        runTest {
            val global = createTask("Глобальное", LocalDate.of(2026, 8, 30), null, null, isGlobal = true)

            moveToDate(global.id, LocalDate.of(2026, 9, 15))

            val moved = repository.getTask(global.id)!!
            assertFalse(moved.isGlobal)
            assertEquals(LocalDate.of(2026, 9, 15), moved.date)
        }
}
