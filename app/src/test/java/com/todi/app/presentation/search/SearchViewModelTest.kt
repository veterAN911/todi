package com.todi.app.presentation.search

import com.todi.app.domain.FakeTaskRepository
import com.todi.app.domain.FakeTodayProvider
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetAllTasksUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.presentation.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeTaskRepository()
    private val today = LocalDate.of(2026, 9, 12)

    private fun newViewModel() =
        SearchViewModel(
            todayProvider = FakeTodayProvider(today),
            getAllTasks = GetAllTasksUseCase(repository),
            completeTask = CompleteTaskUseCase(repository),
            createTask = CreateTaskUseCase(repository),
            updateTask = UpdateTaskUseCase(repository),
            deleteTask = DeleteTaskUseCase(repository),
            restoreTask = RestoreTaskUseCase(repository),
            getTask = GetTaskUseCase(repository),
        )

    private fun TestScope.subscribe(viewModel: SearchViewModel): Job {
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return job
    }

    @Test
    fun theFieldShowsWhatWasTypedWhileTheResultsStillLag_andTheGapIsNotAnEmptyResult() =
        runTest {
            val task = CreateTaskUseCase(repository)("Купить хлеб", today, null, null, false)
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            viewModel.onQueryChange("хлеб")
            runCurrent()

            assertEquals("поле показывает набранное сразу", "хлеб", viewModel.uiState.value.query)
            assertFalse("фильтр ещё не догнал", viewModel.uiState.value.upToDate)
            assertFalse("отставший фильтр — не «ничего не найдено»", viewModel.uiState.value.noResults)

            advanceTimeBy(250)
            runCurrent()

            assertTrue("через дебаунс список догнал запрос", viewModel.uiState.value.upToDate)
            assertEquals(
                listOf(task.id),
                viewModel.uiState.value.groups
                    .flatMap { it.tasks }
                    .map { it.id },
            )
            subscription.cancel()
        }

    @Test
    fun clearingTheFieldDropsTheResultsWithoutWaiting() =
        runTest {
            CreateTaskUseCase(repository)("Купить хлеб", today, null, null, false)
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)
            viewModel.onQueryChange("хлеб")
            advanceTimeBy(250)
            runCurrent()
            assertTrue(viewModel.uiState.value.resultCount > 0)

            viewModel.clearQuery()
            runCurrent()

            assertEquals("", viewModel.uiState.value.query)
            assertTrue(
                "под пустым полем списка быть не должно",
                viewModel.uiState.value.groups
                    .isEmpty(),
            )
            assertFalse("пустое поле — это не «ничего не найдено»", viewModel.uiState.value.noResults)
            subscription.cancel()
        }
}
