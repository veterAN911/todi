package com.todi.app.presentation.completed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todi.app.domain.model.Task
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetCompletedTasksUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.RestoreCompletedTaskUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.domain.util.CompletedGroup
import com.todi.app.domain.util.CompletedGrouper
import com.todi.app.domain.util.TodayProvider
import com.todi.app.presentation.day.TaskAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

enum class CompletedViewMode { DAYS, CALENDAR }

@HiltViewModel
class CompletedViewModel
    @Inject
    constructor(
        private val todayProvider: TodayProvider,
        getCompletedTasks: GetCompletedTasksUseCase,
        private val createTask: CreateTaskUseCase,
        private val updateTask: UpdateTaskUseCase,
        private val deleteTask: DeleteTaskUseCase,
        private val restoreTask: RestoreTaskUseCase,
        private val restoreCompletedTask: RestoreCompletedTaskUseCase,
        private val getTask: GetTaskUseCase,
    ) : ViewModel() {
        val groups: StateFlow<List<CompletedGroup>> =
            combine(getCompletedTasks(), todayProvider.today) { tasks, today ->
                CompletedGrouper.group(tasks, ZoneId.systemDefault(), today)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val today = todayProvider.today

        val total: StateFlow<Int> =
            groups
                .map { groups -> groups.sumOf { group -> group.tasks.size } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

        private val _viewMode = MutableStateFlow(CompletedViewMode.DAYS)
        val viewMode: StateFlow<CompletedViewMode> = _viewMode.asStateFlow()

        private val _month = MutableStateFlow(YearMonth.from(todayProvider.today.value))
        val month: StateFlow<YearMonth> = _month.asStateFlow()

        private val _selectedDate = MutableStateFlow(todayProvider.today.value)
        val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

        val dayCounts: StateFlow<Map<LocalDate, Int>> =
            groups
                .map { groups -> groups.associate { it.date to it.tasks.size } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        val selectedDayTasks: StateFlow<List<Task>> =
            combine(groups, _selectedDate) { groups, date ->
                groups.firstOrNull { it.date == date }?.tasks.orEmpty()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun toggleViewMode() {
            _viewMode.update {
                if (it == CompletedViewMode.DAYS) CompletedViewMode.CALENDAR else CompletedViewMode.DAYS
            }
        }

        fun selectDate(date: LocalDate) {
            _selectedDate.value = date
            _month.value = YearMonth.from(date)
        }

        fun showPreviousMonth() {
            showMonth(_month.value.minusMonths(1))
        }

        fun showNextMonth() {
            showMonth(_month.value.plusMonths(1))
        }

        private fun showMonth(month: YearMonth) {
            _month.value = month
            _selectedDate.value = month.atDay(minOf(_selectedDate.value.dayOfMonth, month.lengthOfMonth()))
        }

        fun jumpToToday() {
            selectDate(todayProvider.today.value)
        }

        private val _actions =
            MutableSharedFlow<TaskAction>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<TaskAction> = _actions.asSharedFlow()

        private var lastUndoable: TaskAction? = null

        fun restoreToDate(
            task: Task,
            date: LocalDate,
        ) {
            viewModelScope.launch { restoreCompletedTask(task.id, date) }
        }

        fun delete(task: Task) {
            viewModelScope.launch {
                deleteTask(task.id)
                lastUndoable = TaskAction.Deleted(task)
                _actions.emit(TaskAction.Deleted(task))
            }
        }

        fun undoLast() {
            val action = lastUndoable ?: return
            lastUndoable = null
            viewModelScope.launch {
                (action as? TaskAction.Deleted)?.let { restoreTask(it.task) }
            }
        }

        fun saveTask(
            title: String,
            date: LocalDate,
            time: LocalTime?,
            note: String?,
            editingTaskId: Long?,
            isGlobal: Boolean,
        ) {
            viewModelScope.launch {
                val existing = editingTaskId?.let { getTask(it) }
                if (existing == null) {
                    createTask(title, date, time, note, isGlobal)
                } else {
                    updateTask(
                        existing
                            .copy(title = title, date = date, time = time, note = note, isGlobal = isGlobal)
                            .normalized(),
                    )
                }
            }
        }
    }
