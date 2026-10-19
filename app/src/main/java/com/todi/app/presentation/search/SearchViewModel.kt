package com.todi.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todi.app.domain.model.Task
import com.todi.app.domain.usecase.CompleteTaskUseCase
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.GetAllTasksUseCase
import com.todi.app.domain.usecase.GetTaskUseCase
import com.todi.app.domain.usecase.RestoreTaskUseCase
import com.todi.app.domain.usecase.UpdateTaskUseCase
import com.todi.app.domain.util.SearchGroup
import com.todi.app.domain.util.TaskSearch
import com.todi.app.domain.util.TodayProvider
import com.todi.app.presentation.day.TaskAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val groups: List<SearchGroup> = emptyList(),
    val upToDate: Boolean = true,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val resultCount: Int get() = groups.sumOf { it.tasks.size }

    val noResults: Boolean get() = hasQuery && upToDate && groups.isEmpty()
}

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val todayProvider: TodayProvider,
        getAllTasks: GetAllTasksUseCase,
        private val completeTask: CompleteTaskUseCase,
        private val createTask: CreateTaskUseCase,
        private val updateTask: UpdateTaskUseCase,
        private val deleteTask: DeleteTaskUseCase,
        private val restoreTask: RestoreTaskUseCase,
        private val getTask: GetTaskUseCase,
    ) : ViewModel() {
        private val query = MutableStateFlow("")

        @OptIn(FlowPreview::class)
        val uiState: StateFlow<SearchUiState> =
            combine(
                getAllTasks(),
                query,
                query.debounce { if (it.isBlank()) 0L else DEBOUNCE_MILLIS },
                todayProvider.today,
            ) { tasks, text, filtered, today ->
                SearchUiState(
                    query = text,
                    groups = TaskSearch.search(tasks, filtered, today),
                    upToDate = text == filtered,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

        private val _actions =
            MutableSharedFlow<TaskAction>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<TaskAction> = _actions.asSharedFlow()

        private var lastUndoable: TaskAction? = null

        fun onQueryChange(text: String) {
            query.value = text
        }

        fun clearQuery() {
            query.value = ""
        }

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

        private companion object {
            const val DEBOUNCE_MILLIS = 200L
        }
    }
