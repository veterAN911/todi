package com.todi.app.presentation.day

import com.todi.app.domain.FakeTaskRepository
import com.todi.app.domain.FakeTodayProvider
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListControllerTest {
    private val repository = FakeTaskRepository()
    private val today = LocalDate.of(2026, 8, 30)
    private val createTask = CreateTaskUseCase(repository)

    private fun TestScope.newController(): TaskListController {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return TaskListController(
            dateFlow = MutableStateFlow(today),
            showCompleted = MutableStateFlow(true),
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
    fun saveTask_updatesGlobalTaskThatTheDayListDoesNotShow() =
        runTest {
            val global = createTask("Позвонить в сервис", LocalDate.of(2026, 8, 20), null, null, isGlobal = true)
            val controller = newController()
            val subscription = collectState(controller)

            assertTrue(
                controller.uiState.value.activeTasks
                    .isEmpty(),
            )

            controller.saveTask(
                title = "Позвонить в сервис и в банк",
                date = today,
                time = null,
                note = "после обеда",
                editingTaskId = global.id,
                isGlobal = true,
            )
            advanceUntilIdle()

            val allTasks = repository.observeAllTasks().first()
            assertEquals("должно остаться одно дело, а не появиться дубликат", 1, allTasks.size)
            assertEquals(global.id, allTasks.single().id)
            assertEquals("Позвонить в сервис и в банк", allTasks.single().title)
            assertEquals("после обеда", allTasks.single().note)
            assertTrue(allTasks.single().isGlobal)
            subscription.cancel()
        }

    @Test
    fun saveTask_withoutEditingId_createsATask() =
        runTest {
            val controller = newController()
            advanceUntilIdle()

            controller.saveTask(
                title = "Купить хлеб",
                date = today,
                time = LocalTime.of(19, 0),
                note = null,
                editingTaskId = null,
            )
            advanceUntilIdle()

            val task = repository.observeAllTasks().first().single()
            assertEquals("Купить хлеб", task.title)
            assertEquals(LocalTime.of(19, 0), task.time)
            assertFalse(task.isGlobal)
        }

    @Test
    fun saveTask_onADatedTask_updatesItInPlace() =
        runTest {
            val dated = createTask("Черновик", today, LocalTime.of(9, 0), null)
            val controller = newController()
            advanceUntilIdle()

            controller.saveTask("Черновик письма", today, LocalTime.of(10, 0), null, dated.id)
            advanceUntilIdle()

            val allTasks = repository.observeAllTasks().first()
            assertEquals(1, allTasks.size)
            assertEquals("Черновик письма", allTasks.single().title)
            assertEquals(LocalTime.of(10, 0), allTasks.single().time)
        }

    @Test
    fun saveTask_switchingADatedTaskToGlobal_dropsItsTime() =
        runTest {
            val dated = createTask("Дело", today, LocalTime.of(9, 0), null)
            val controller = newController()
            val subscription = collectState(controller)

            controller.saveTask("Дело", today, LocalTime.of(9, 0), null, dated.id, isGlobal = true)
            advanceUntilIdle()

            val task = repository.observeAllTasks().first().single()
            assertTrue(task.isGlobal)
            assertNull("у дела без даты не должно остаться времени", task.time)
            assertTrue(
                "глобальное дело уходит из списка дня",
                controller.uiState.value.activeTasks
                    .isEmpty(),
            )
            subscription.cancel()
        }
}
