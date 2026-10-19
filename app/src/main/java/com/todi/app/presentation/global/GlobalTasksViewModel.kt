package com.todi.app.presentation.global

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todi.app.domain.model.Task
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetGlobalTasksUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.domain.util.TaskSorter
import com.todi.app.domain.util.TodayProvider
import com.todi.app.presentation.day.TaskAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class GlobalTasksViewModel
    @Inject
    constructor(
        private val todayProvider: TodayProvider,
        getGlobalTasks: GetGlobalTasksUseCase,
        private val createTask: CreateTaskUseCase,
        private val updateTask: UpdateTaskUseCase,
        private val deleteTask: DeleteTaskUseCase,
        private val completeTask: CompleteTaskUseCase,
        private val restoreTask: RestoreTaskUseCase,
        private val getTask: GetTaskUseCase,
    ) : ViewModel() {
        val tasks: StateFlow<List<Task>> =
            getGlobalTasks()
                .map(TaskSorter::sortGlobal)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val today = todayProvider.today

        private val _actions =
            MutableSharedFlow<TaskAction>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<TaskAction> = _actions.asSharedFlow()

        private var lastUndoable: TaskAction? = null

        fun toggleCompleted(task: Task) {
            viewModelScope.launch { completeTask(task.id, !task.completed) }
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
