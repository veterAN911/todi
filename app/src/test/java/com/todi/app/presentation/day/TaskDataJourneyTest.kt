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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDataJourneyTest {
    private val repository = FakeTaskRepository()
    private val today = LocalDate.of(2026, 9, 12)
    private val tomorrow = today.plusDays(1)

    private fun TestScope.newController(): TaskListController {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return TaskListController(
            dateFlow = MutableStateFlow(today),
            showCompleted = MutableStateFlow(true),
            todayProvider = FakeTodayProvider(today),
            getTasksForDate = GetTasksForDateUseCase(repository),
            createTask = CreateTaskUseCase(repository),
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
    fun aNewTask_arrivesAsScalarsAndIsStoredWithEveryOneOfThem() =
        runTest {
            val controller = newController()
            val subscription = collectState(controller)

            controller.saveTask(
                title = "Купить хлеб",
                date = today,
                time = LocalTime.of(19, 0),
                note = "в пекарне",
                editingTaskId = null,
                isGlobal = false,
            )
            advanceUntilIdle()

            val stored = repository.observeAllTasks().first().single()

            assertEquals("Купить хлеб", stored.title)
            assertEquals(today, stored.date)
            assertEquals(LocalTime.of(19, 0), stored.time)
            assertEquals("в пекарне", stored.note)
            assertFalse("новое дело не выполнено", stored.completed)
            assertFalse("новое дело не без даты", stored.isGlobal)
            assertTrue("без syncId дело не переживёт слияние", stored.syncId.isNotBlank())

            assertEquals(
                listOf(stored.id),
                controller.uiState.value.activeTasks
                    .map { it.id },
            )
            subscription.cancel()
        }

    @Test
    fun whitespaceIsTrimmedAtTheUseCaseBoundary_andAnEmptyNoteBecomesNothing() =
        runTest {
            CreateTaskUseCase(repository)("  Позвонить  ", today, null, "   ", false)

            val stored = repository.observeAllTasks().first().single()
            assertEquals("Позвонить", stored.title)
            assertNull("заметка из одних пробелов — это отсутствие заметки", stored.note)
        }

    @Test
    fun editingTrimsAtTheSameBoundary_andADateLessTaskLosesItsTimeInTheModel() =
        runTest {
            val created = CreateTaskUseCase(repository)("Дело", today, LocalTime.of(9, 0), "в банк", false)
            val controller = newController()
            val subscription = collectState(controller)

            controller.saveTask("  Дело  ", today, LocalTime.of(9, 0), "  в банк  ", created.id, isGlobal = true)
            advanceUntilIdle()

            val stored = repository.getTask(created.id)!!
            assertEquals("правка обрезает пробелы там же, где и создание", "Дело", stored.title)
            assertEquals("в банк", stored.note)
            assertTrue("дело стало бездатным", stored.isGlobal)
            assertNull("время у бездатного дела снимает normalized(), а не экран", stored.time)
            subscription.cancel()
        }

    @Test
    fun tickingATaskOff_carriesTheIdAlone_andTheListSplitsAroundIt() =
        runTest {
            val created = CreateTaskUseCase(repository)("Сдать книгу", today, LocalTime.of(9, 0), null, false)
            val controller = newController()
            val subscription = collectState(controller)

            val snapshot =
                controller.uiState.value.activeTasks
                    .single()
                    .copy(title = "УСТАРЕВШИЙ СНИМОК")

            controller.toggleCompleted(snapshot)
            advanceUntilIdle()

            val stored = repository.getTask(created.id)!!
            assertEquals("пишем по id: поля снимка в базу не переезжают", "Сдать книгу", stored.title)
            assertEquals(LocalTime.of(9, 0), stored.time)
            assertTrue("отметка поставлена", stored.completed)
            assertNotNull("момент выполнения записан — по нему архив строит день", stored.completedAt)

            assertTrue(
                "выполненное уходит из активных",
                controller.uiState.value.activeTasks
                    .isEmpty(),
            )
            assertEquals(
                listOf(created.id),
                controller.uiState.value.completedTasks
                    .map { it.id },
            )
            subscription.cancel()
        }

    @Test
    fun movingATask_writesTheDayFromTheArgument_andUndoRestoresTheDayFromTheSnapshot() =
        runTest {
            val created = CreateTaskUseCase(repository)("Позвонить", today, null, null, false)
            val controller = newController()
            val subscription = collectState(controller)
            val snapshot =
                controller.uiState.value.activeTasks
                    .single()

            controller.moveToDate(snapshot, tomorrow)
            advanceUntilIdle()

            val moved = repository.getTask(created.id)!!
            assertEquals("день — тот, что передали аргументом", tomorrow, moved.date)
            assertEquals("а всё остальное дело осталось при нём", "Позвонить", moved.title)
            assertTrue(
                "перенесённое уходит из списка прежнего дня",
                controller.uiState.value.activeTasks
                    .isEmpty(),
            )

            controller.undoLast()
            advanceUntilIdle()

            assertEquals(
                "откат возвращает день, который контроллер запомнил в снимке",
                today,
                repository.getTask(created.id)!!.date,
            )
            subscription.cancel()
        }
}
