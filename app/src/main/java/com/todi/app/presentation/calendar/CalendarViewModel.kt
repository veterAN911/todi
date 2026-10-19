package com.todi.app.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetActiveTasksBetweenUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.GetTasksForDateUseCase
import com.todi.app.domain.usecase.MoveTaskToDateUseCase
import com.todi.app.domain.usecase.MoveTaskToTomorrowUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.domain.util.TodayProvider
import com.todi.app.presentation.day.TaskListController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val todayProvider: TodayProvider,
        private val settingsRepository: SettingsRepository,
        getTasksForDate: GetTasksForDateUseCase,
        createTask: CreateTaskUseCase,
        updateTask: UpdateTaskUseCase,
        deleteTask: DeleteTaskUseCase,
        completeTask: CompleteTaskUseCase,
        moveTaskToTomorrow: MoveTaskToTomorrowUseCase,
        moveTaskToDate: MoveTaskToDateUseCase,
        restoreTask: RestoreTaskUseCase,
        getActiveTasksBetween: GetActiveTasksBetweenUseCase,
        getTask: GetTaskUseCase,
    ) : ViewModel() {
        val today = todayProvider.today

        private val _selectedDate = MutableStateFlow(todayProvider.today.value)
        val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

        private val _month = MutableStateFlow(YearMonth.from(todayProvider.today.value))
        val month: StateFlow<YearMonth> = _month.asStateFlow()

        val controller =
            TaskListController(
                dateFlow = _selectedDate,
                showCompleted = settingsRepository.settings.map { it.showCompletedInDay },
                todayProvider = todayProvider,
                getTasksForDate = getTasksForDate,
                createTask = createTask,
                updateTask = updateTask,
                deleteTask = deleteTask,
                completeTask = completeTask,
                moveTaskToTomorrow = moveTaskToTomorrow,
                moveTaskToDate = moveTaskToDate,
                restoreTask = restoreTask,
                taskLookup = getTask::invoke,
                scope = viewModelScope,
            )

        val uiState = controller.uiState
        val actions = controller.actions

        val monthTaskCounts: StateFlow<Map<LocalDate, Int>> =
            _month
                .flatMapLatest { month ->
                    getActiveTasksBetween(month.atDay(1), month.atEndOfMonth())
                        .map { tasks -> tasks.groupingBy { it.date }.eachCount() }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        fun selectDate(date: LocalDate) {
            _selectedDate.value = date
            _month.value = YearMonth.from(date)
        }

        fun showPreviousMonth() {
            _month.value = _month.value.minusMonths(1)
        }

        fun showNextMonth() {
            _month.value = _month.value.plusMonths(1)
        }

        fun jumpToToday() {
            selectDate(todayProvider.today.value)
        }
    }
