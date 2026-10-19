package com.todi.app.presentation.day

import com.todi.app.domain.FakeTaskRepository
import com.todi.app.domain.FakeTodayProvider
import com.todi.app.domain.model.Task
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.GetTasksForDateUseCase
import com.todi.app.domain.usecase.MoveTaskToDateUseCase
import com.todi.app.domain.usecase.MoveTaskToTomorrowUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayListSplittingTest {
    private val today = LocalDate.of(2026, 9, 12)

    private fun task(
        id: Long,
        completed: Boolean,
    ) = Task(
        id = id,
        title = "Дело $id",
        date = today,
        time = null,
        note = null,
        completed = completed,
        createdAt = Instant.parse("2026-09-12T08:00:00Z"),
        updatedAt = Instant.parse("2026-09-12T08:00:00Z"),
        syncId = "sync$id",
        completedAt = if (completed) Instant.parse("2026-09-12T18:00:00Z") else null,
    )

    private fun state(
        active: Int,
        done: Int,
        showCompleted: Boolean,
    ) = DayUiState(
        date = today,
        activeTasks = (1..active).map { task(it.toLong(), completed = false) },
        completedTasks = (1..done).map { task(99L + it, completed = true) },
        showCompleted = showCompleted,
    )

    @Test
    fun `an empty day is empty whichever way the setting is set`() {
        listOf(true, false).forEach { show ->
            val state = state(active = 0, done = 0, showCompleted = show)

            assertEquals(0, state.activeCount)
            assertEquals(0, state.completedCount)
            assertFalse("ничего нет — ни дел, ни выполненных", state.hasAnyTask)
            assertTrue(state.visibleTasks.isEmpty())
        }
    }

    @Test
    fun `a day with only active tasks never shows the done section`() {
        listOf(true, false).forEach { show ->
            val state = state(active = 2, done = 0, showCompleted = show)

            assertEquals(2, state.activeCount)
            assertEquals(0, state.completedCount)
            assertTrue(state.hasAnyTask)
            assertEquals(listOf(1L, 2L), state.visibleTasks.map { it.id })
        }
    }

    @Test
    fun `a day with only completed tasks is not an empty day`() {
        listOf(true, false).forEach { show ->
            val state = state(active = 0, done = 3, showCompleted = show)

            assertEquals(0, state.activeCount)
            assertEquals(3, state.completedCount)
            assertTrue("день не пустой — за ним есть выполненные дела", state.hasAnyTask)
        }
    }

    @Test
    fun `with the setting off the completed tasks are not in the visible list`() {
        val state = state(active = 0, done = 3, showCompleted = false)

        assertTrue("список дня пуст, но день не «свободен»", state.visibleTasks.isEmpty())
        assertTrue(state.hasAnyTask)
    }

    @Test
    fun `with the setting on the completed tasks follow the active ones`() {
        val state = state(active = 0, done = 3, showCompleted = true)

        assertEquals(listOf(100L, 101L, 102L), state.visibleTasks.map { it.id })
    }

    @Test
    fun `active tasks come first and keep their order`() {
        val state = state(active = 2, done = 2, showCompleted = true)

        assertEquals(listOf(1L, 2L, 100L, 101L), state.visibleTasks.map { it.id })
        assertEquals(1L, state.visibleTasks.first().id)
        assertEquals(101L, state.visibleTasks.last().id)
    }

    @Test
    fun `hiding the done section leaves the active tasks untouched`() {
        val state = state(active = 2, done = 2, showCompleted = false)

        assertEquals(2, state.activeCount)
        assertEquals(2, state.completedCount)
        assertEquals(listOf(1L, 2L), state.visibleTasks.map { it.id })
    }

    private val repository = FakeTaskRepository()
    private val createTask = CreateTaskUseCase(repository)
    private val showCompleted = MutableStateFlow(true)

    private fun TestScope.newController(): TaskListController {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return TaskListController(
            dateFlow = MutableStateFlow(today),
            showCompleted = showCompleted,
            todayProvider = FakeTodayProvider(today),
            getTasksForDate = GetTasksForDateUseCase(repository),
            createTask = createTask,
            updateTask = UpdateTaskUseCase(repository),
            deleteTask = DeleteTaskUseCase(repository),
            completeTask = CompleteTaskUseCase(repository),
            moveTaskToTomorrow = MoveTaskToTomorrowUseCase(repository, FakeTodayProvider(today)),
            moveTaskToDate = MoveTaskToDateUseCase(repository),
            restoreTask = RestoreTaskUseCase(repository),
            taskLookup = GetTaskUseCase(repository)::invoke,
            scope = scope,
        )
    }

    @Test
    fun `completing a task moves it from the active list to the done list`() =
        runTest {
            val task = createTask("Купить хлеб", today, null, null)
            val controller = newController()
            val subscription = collectState(controller)
            assertEquals(
                listOf(task.id),
                controller.uiState.value.activeTasks
                    .map { it.id },
            )

            controller.toggleCompleted(task)
            advanceUntilIdle()

            val state = controller.uiState.value
            assertTrue("из активных дело уходит", state.activeTasks.isEmpty())
            assertEquals("и появляется в выполненных", listOf(task.id), state.completedTasks.map { it.id })
            subscription.cancel()
        }

    @Test
    fun `the setting reaches the state without recreating the controller`() =
        runTest {
            createTask("Купить хлеб", today, null, null)
            val controller = newController()
            val subscription = collectState(controller)
            assertTrue(controller.uiState.value.showCompleted)

            showCompleted.value = false
            advanceUntilIdle()

            assertFalse(controller.uiState.value.showCompleted)
            assertEquals(
                "список дня теперь только активные дела",
                controller.uiState.value.activeTasks
                    .map { it.id },
                controller.uiState.value.visibleTasks
                    .map { it.id },
            )
            subscription.cancel()
        }

    @Test
    fun `tasks of another day are not part of the split`() =
        runTest {
            createTask("Завтрашнее", today.plusDays(1), null, null)
            val controller = newController()
            val subscription = collectState(controller)

            val state = controller.uiState.value
            assertEquals(0, state.activeCount)
            assertEquals(0, state.completedCount)
            assertFalse(state.hasAnyTask)
            subscription.cancel()
        }
}
