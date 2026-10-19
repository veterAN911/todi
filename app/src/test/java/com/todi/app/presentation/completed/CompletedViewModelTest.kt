package com.todi.app.presentation.completed

import com.todi.app.domain.FakeTaskRepository
import com.todi.app.domain.FakeTodayProvider
import com.todi.app.domain.model.Task
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetCompletedTasksUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.RestoreCompletedTaskUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.domain.util.CompletedDayKind
import com.todi.app.presentation.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CompletedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeTaskRepository()
    private val today = LocalDate.of(2026, 9, 10)
    private val todayProvider = FakeTodayProvider(today)

    private fun newViewModel() =
        CompletedViewModel(
            todayProvider = todayProvider,
            getCompletedTasks = GetCompletedTasksUseCase(repository),
            createTask = CreateTaskUseCase(repository),
            updateTask = UpdateTaskUseCase(repository),
            deleteTask = DeleteTaskUseCase(repository),
            restoreTask = RestoreTaskUseCase(repository),
            restoreCompletedTask = RestoreCompletedTaskUseCase(repository),
            getTask = GetTaskUseCase(repository),
        )

    private fun TestScope.subscribe(viewModel: CompletedViewModel): Job {
        val job =
            launch {
                coroutineScope {
                    launch { viewModel.groups.collect {} }
                    launch { viewModel.total.collect {} }
                    launch { viewModel.dayCounts.collect {} }
                    launch { viewModel.selectedDayTasks.collect {} }
                }
            }
        advanceUntilIdle()
        return job
    }

    private suspend fun archive(vararg tasks: Task) {
        tasks.forEach { repository.restoreTask(it) }
    }

    private fun finished(
        id: Long,
        title: String,
        finishedAt: String,
    ): Task {
        val moment = Instant.parse(finishedAt)
        return Task(
            id = id,
            title = title,
            date = today.minusDays(3),
            time = null,
            note = null,
            completed = true,
            createdAt = moment,
            updatedAt = moment,
            syncId = "sync-$id",
            completedAt = moment,
        )
    }

    @Test
    fun groups_areNamedAgainstTheTodayTheProviderGives_andComeNewestFirst() =
        runTest {
            archive(
                finished(1, "Понедельник", "2026-09-07T10:00:00Z"),
                finished(2, "Вчера", "2026-09-09T10:00:00Z"),
                finished(3, "Сегодня", "2026-09-10T10:00:00Z"),
            )
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            val groups = viewModel.groups.value
            assertEquals(
                "дни идут от свежего к старому",
                listOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 7)),
                groups.map { it.date },
            )
            assertEquals(
                "день называется по дню, который показан как «Сегодня»",
                listOf(CompletedDayKind.TODAY, CompletedDayKind.YESTERDAY, CompletedDayKind.EARLIER),
                groups.map { it.kind },
            )
            subscription.cancel()
        }

    @Test
    fun theHeaderCountsEveryDay_andTheGridCountsEach() =
        runTest {
            archive(
                finished(1, "Понедельник", "2026-09-07T10:00:00Z"),
                finished(2, "Вчера", "2026-09-09T10:00:00Z"),
                finished(3, "Сегодня", "2026-09-10T10:00:00Z"),
            )
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            assertEquals(3, viewModel.total.value)
            assertEquals(
                mapOf(
                    LocalDate.of(2026, 9, 7) to 1,
                    LocalDate.of(2026, 9, 9) to 1,
                    LocalDate.of(2026, 9, 10) to 1,
                ),
                viewModel.dayCounts.value,
            )
            subscription.cancel()
        }

    @Test
    fun theListUnderTheGridIsTheSelectedDay_andNothingElse() =
        runTest {
            archive(
                finished(1, "Понедельник", "2026-09-07T10:00:00Z"),
                finished(2, "Сегодня", "2026-09-10T10:00:00Z"),
            )
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            assertEquals(
                "по умолчанию выбран сегодняшний день",
                listOf("Сегодня"),
                viewModel.selectedDayTasks.value.map { it.title },
            )

            viewModel.selectDate(LocalDate.of(2026, 9, 7))
            advanceUntilIdle()

            assertEquals(listOf("Понедельник"), viewModel.selectedDayTasks.value.map { it.title })
            subscription.cancel()
        }

    @Test
    fun pagingTheMonth_carriesTheSelectedDayAndClampsIt() =
        runTest {
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            viewModel.selectDate(LocalDate.of(2026, 1, 31))
            advanceUntilIdle()
            viewModel.showNextMonth()
            advanceUntilIdle()

            assertEquals(YearMonth.of(2026, 2), viewModel.month.value)
            assertEquals(
                "31 февраля не бывает — день обрезается по длине месяца",
                LocalDate.of(2026, 2, 28),
                viewModel.selectedDate.value,
            )

            viewModel.showPreviousMonth()
            advanceUntilIdle()

            assertEquals(YearMonth.of(2026, 1), viewModel.month.value)
            assertEquals(LocalDate.of(2026, 1, 28), viewModel.selectedDate.value)
            subscription.cancel()
        }

    @Test
    fun selectingADay_pullsItsMonthWithIt_andTodayIsHowYouComeBack() =
        runTest {
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            viewModel.selectDate(LocalDate.of(2026, 5, 4))
            advanceUntilIdle()
            assertEquals(YearMonth.of(2026, 5), viewModel.month.value)

            viewModel.jumpToToday()
            advanceUntilIdle()

            assertEquals(today, viewModel.selectedDate.value)
            assertEquals(YearMonth.of(2026, 9), viewModel.month.value)
            subscription.cancel()
        }

    @Test
    fun theIconFlipsBetweenTheListAndTheGrid() =
        runTest {
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            assertEquals(CompletedViewMode.DAYS, viewModel.viewMode.value)
            viewModel.toggleViewMode()
            assertEquals(CompletedViewMode.CALENDAR, viewModel.viewMode.value)
            viewModel.toggleViewMode()
            assertEquals(CompletedViewMode.DAYS, viewModel.viewMode.value)
            subscription.cancel()
        }

    @Test
    fun restoreToDate_writesTheDayItWasGiven_andTakesTheTaskOutOfTheArchive() =
        runTest {
            archive(finished(1, "Сдать книгу", "2026-09-09T10:00:00Z"))
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)
            val task = repository.getTask(1)!!

            viewModel.restoreToDate(task, LocalDate.of(2026, 9, 2))
            advanceUntilIdle()

            val restored = repository.getTask(1)!!
            assertFalse("возврат снимает отметку", restored.completed)
            assertNull("момент выполнения не переживает возврат", restored.completedAt)
            assertEquals("день — тот, который назвали, а не сегодняшний", LocalDate.of(2026, 9, 2), restored.date)
            assertTrue("в архиве его больше нет", viewModel.groups.value.isEmpty())
            subscription.cancel()
        }

    @Test
    fun deleteThenUndo_returnsTheTaskToTheArchive() =
        runTest {
            archive(finished(1, "Разобрать почту", "2026-09-09T10:00:00Z"))
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)
            val task = repository.getTask(1)!!

            viewModel.delete(task)
            advanceUntilIdle()

            assertTrue("удалённое дело уходит из архива", viewModel.groups.value.isEmpty())

            viewModel.undoLast()
            advanceUntilIdle()

            assertEquals(
                listOf("Разобрать почту"),
                viewModel.groups.value
                    .single()
                    .tasks
                    .map { it.title },
            )
            subscription.cancel()
        }

    @Test
    fun editingAnArchivedTask_keepsItsTickAndItsCompletionDay() =
        runTest {
            archive(finished(1, "Черновик", "2026-09-09T10:00:00Z"))
            val viewModel = newViewModel()
            val subscription = subscribe(viewModel)

            viewModel.saveTask(
                title = "Черновик письма",
                date = today,
                time = LocalTime.of(10, 0),
                note = "к обеду",
                editingTaskId = 1,
                isGlobal = false,
            )
            advanceUntilIdle()

            val edited = repository.getTask(1)!!
            assertEquals("Черновик письма", edited.title)
            assertEquals("к обеду", edited.note)
            assertTrue("архивное дело остаётся отмеченным", edited.completed)
            assertEquals(
                LocalDate.of(2026, 9, 9),
                viewModel.groups.value
                    .single()
                    .date,
            )
            subscription.cancel()
        }
}
