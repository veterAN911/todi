package com.todi.app.presentation.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.GetTasksForDateUseCase
import com.todi.app.domain.usecase.MoveTaskToDateUseCase
import com.todi.app.domain.usecase.MoveTaskToTomorrowUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.domain.util.TodayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class TomorrowViewModel
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
        getTask: GetTaskUseCase,
    ) : ViewModel() {
        val controller =
            TaskListController(
                dateFlow = todayProvider.today.map { it.plusDays(1) },
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

        val today = todayProvider.today
    }
