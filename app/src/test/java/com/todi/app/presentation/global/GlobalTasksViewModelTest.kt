package com.todi.app.presentation.global

import com.todi.app.domain.FakeTaskRepository
import com.todi.app.domain.FakeTodayProvider
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetGlobalTasksUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.presentation.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalTasksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeTaskRepository()
    private val today = LocalDate.of(2026, 9, 12)
    private val todayProvider = FakeTodayProvider(today)

    private fun newViewModel() =
        GlobalTasksViewModel(
            todayProvider = todayProvider,
            getGlobalTasks = GetGlobalTasksUseCase(repository),
            createTask = CreateTaskUseCase(repository),
            updateTask = UpdateTaskUseCase(repository),
            deleteTask = DeleteTaskUseCase(repository),
            completeTask = CompleteTaskUseCase(repository),
            restoreTask = RestoreTaskUseCase(repository),
            getTask = GetTaskUseCase(repository),
        )

    private fun TestScope.subscribe(viewModel: GlobalTasksViewModel): Job {
        val job = launch { viewModel.tasks.collect {} }
        advanceUntilIdle()
        return job
    }

    @Test
    fun theSectionListsTheDateLessOnes_inTheOrderTheyWereCreated() =
        runTest {
            val create = CreateTaskUseCase(repository)
            create("Позвонить в сервис", today, null, null, isGlobal = true)
            create("Проверить почту", today, LocalTime.of(9, 0), null, isGlobal = true)
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            assertEquals(
                listOf("Позвонить в сервис", "Проверить почту"),
                viewModel.tasks.value.map { it.title },
            )
            subscription.cancel()
        }

    @Test
    fun savingWithoutTheDateLessFlag_graduatesTheTaskToTheChosenDay() =
        runTest {
            val task = CreateTaskUseCase(repository)("Полить цветы", today, null, null, isGlobal = true)
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            viewModel.saveTask(
                title = "Полить цветы",
                date = today,
                time = null,
                note = null,
                editingTaskId = task.id,
                isGlobal = false,
            )
            advanceUntilIdle()

            assertTrue("раздел «Без даты» его больше не показывает", viewModel.tasks.value.isEmpty())
            assertEquals(
                listOf(task.id),
                repository.observeActiveTasksForDate(today).first().map { it.id },
            )
            subscription.cancel()
        }

    @Test
    fun tickingADateLessTaskOff_takesItOutOfTheSection() =
        runTest {
            val task = CreateTaskUseCase(repository)("Разобрать бумаги", today, null, null, isGlobal = true)
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            viewModel.toggleCompleted(task)
            advanceUntilIdle()

            assertTrue(viewModel.tasks.value.isEmpty())
            subscription.cancel()
        }

    @Test
    fun deleteThenUndo_returnsTheTaskToTheSection() =
        runTest {
            val task = CreateTaskUseCase(repository)("Позвонить в банк", today, null, null, isGlobal = true)
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            viewModel.delete(task)
            advanceUntilIdle()
            assertTrue(viewModel.tasks.value.isEmpty())

            viewModel.undoLast()
            advanceUntilIdle()
            assertEquals(listOf(task.id), viewModel.tasks.value.map { it.id })
            subscription.cancel()
        }
}
