package com.todi.app.presentation.editor

import androidx.lifecycle.ViewModel
import com.todi.app.domain.model.Task
import com.todi.app.domain.util.TodayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TaskEditorUiState(
    val visible: Boolean = false,
    val editingTaskId: Long? = null,
    val title: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val note: String = "",
    val isGlobal: Boolean = false,
) {
    val isEdit: Boolean get() = editingTaskId != null
    val canSave: Boolean get() = title.isNotBlank()
}

@HiltViewModel
class TaskEditorViewModel
    @Inject
    constructor(
        todayProvider: TodayProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TaskEditorUiState(date = todayProvider.today.value))
        val state: StateFlow<TaskEditorUiState> = _state.asStateFlow()

        fun openForCreate(
            date: LocalDate,
            isGlobal: Boolean = false,
        ) {
            _state.update {
                it.copy(
                    visible = true,
                    editingTaskId = null,
                    title = "",
                    date = date,
                    time = null,
                    note = "",
                    isGlobal = isGlobal,
                )
            }
        }

        fun openForEdit(task: Task) {
            _state.update {
                it.copy(
                    visible = true,
                    editingTaskId = task.id,
                    title = task.title,
                    date = task.date,
                    time = task.time,
                    note = task.note.orEmpty(),
                    isGlobal = task.isGlobal,
                )
            }
        }

        fun setTitle(value: String) = _state.update { it.copy(title = value) }

        fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }

        fun setTime(value: LocalTime?) = _state.update { it.copy(time = value) }

        fun setNote(value: String) = _state.update { it.copy(note = value) }

        fun setGlobal(value: Boolean) =
            _state.update {
                it.copy(isGlobal = value, time = if (value) null else it.time)
            }

        fun clear() = _state.update { it.copy(visible = false) }
    }
