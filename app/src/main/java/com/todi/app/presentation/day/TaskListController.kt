package com.todi.app.presentation.day

import com.todi.app.domain.model.Task
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetTasksForDateUseCase
import com.todi.app.domain.usecase.MoveTaskToDateUseCase
import com.todi.app.domain.usecase.MoveTaskToTomorrowUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.domain.util.TodayProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class DayUiState(
    val date: LocalDate,
    val activeTasks: List<Task>,
    val completedTasks: List<Task>,
    val showCompleted: Boolean = true,
) {
    val activeCount: Int get() = activeTasks.size
    val completedCount: Int get() = completedTasks.size

    val visibleTasks: List<Task>
        get() = if (showCompleted) activeTasks + completedTasks else activeTasks

    val hasAnyTask: Boolean get() = activeCount + completedCount > 0
}

sealed interface TaskAction {
    data class MovedToTomorrow(
        val task: Task,
    ) : TaskAction

    data class Moved(
        val task: Task,
    ) : TaskAction

    data class Deleted(
        val task: Task,
    ) : TaskAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListController(
    dateFlow: Flow<LocalDate>,
    showCompleted: Flow<Boolean>,
    todayProvider: TodayProvider,
    private val getTasksForDate: GetTasksForDateUseCase,
    private val createTask: CreateTaskUseCase,
    private val updateTask: UpdateTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val moveTaskToTomorrow: MoveTaskToTomorrowUseCase,
    private val moveTaskToDate: MoveTaskToDateUseCase,
    private val restoreTask: RestoreTaskUseCase,
    private val taskLookup: suspend (Long) -> Task?,
    private val scope: CoroutineScope,
) {
    val uiState: StateFlow<DayUiState> =
        combine(
            dateFlow.flatMapLatest { date -> getTasksForDate(date).map { date to it } },
            showCompleted,
        ) { (date, tasks), show ->
            val (active, done) = tasks.partition { !it.completed }
            DayUiState(date = date, activeTasks = active, completedTasks = done, showCompleted = show)
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(5_000),
            DayUiState(todayProvider.today.value, emptyList(), emptyList()),
        )

    private val _actions =
        MutableSharedFlow<TaskAction>(
            extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )
    val actions: SharedFlow<TaskAction> = _actions.asSharedFlow()

    private var lastUndoable: TaskAction? = null

    fun toggleCompleted(task: Task) {
        scope.launch { completeTask(task.id, !task.completed) }
    }

    fun delete(task: Task) {
        scope.launch {
            deleteTask(task.id)
            emitUndoable(TaskAction.Deleted(task))
        }
    }

    fun moveToTomorrow(task: Task) {
        scope.launch {
            moveTaskToTomorrow(task.id)
            emitUndoable(TaskAction.MovedToTomorrow(task))
        }
    }

    fun moveToDate(
        task: Task,
        date: LocalDate,
    ) {
        scope.launch {
            moveTaskToDate(task.id, date)
            emitUndoable(TaskAction.Moved(task))
        }
    }

    fun saveTask(
        title: String,
        date: LocalDate,
        time: LocalTime?,
        note: String?,
        editingTaskId: Long?,
        isGlobal: Boolean = false,
    ) {
        scope.launch {
            val existing = editingTaskId?.let { taskLookup(it) }
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

    fun undoLast() {
        val action = lastUndoable ?: return
        lastUndoable = null
        scope.launch {
            when (action) {
                is TaskAction.Deleted -> restoreTask(action.task)
                is TaskAction.MovedToTomorrow -> moveTaskToDate(action.task.id, action.task.date)
                is TaskAction.Moved -> moveTaskToDate(action.task.id, action.task.date)
            }
        }
    }

    private suspend fun emitUndoable(action: TaskAction) {
        lastUndoable = action
        _actions.emit(action)
    }
}
