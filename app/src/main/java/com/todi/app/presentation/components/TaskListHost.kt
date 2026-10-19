package com.todi.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.R
import com.todi.app.domain.model.Task
import com.todi.app.presentation.day.TaskAction
import com.todi.app.presentation.day.TaskListController
import com.todi.app.presentation.editor.TaskEditorHost
import com.todi.app.presentation.editor.TaskEditorViewModel
import com.todi.app.presentation.voice.VoiceAddFab
import com.todi.app.presentation.voice.rememberDockedAddButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TaskListHost(
    controller: TaskListController,
    editor: TaskEditorViewModel,
    today: LocalDate,
    fabDate: LocalDate,
    modifier: Modifier = Modifier,
    content: @Composable (onRequestMove: (Task) -> Unit) -> Unit,
) {
    val state by controller.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var moveTaskId by rememberSaveable { mutableLongStateOf(-1L) }
    var showMoveDatePicker by rememberSaveable { mutableStateOf(false) }

    val movedToTomorrowText = stringResource(R.string.moved_to_tomorrow)
    val movedText = stringResource(R.string.task_moved)
    val deletedText = stringResource(R.string.task_deleted)
    val undoText = stringResource(R.string.undo)

    LaunchedEffect(Unit) {
        controller.actions.collect { action ->
            val message =
                when (action) {
                    is TaskAction.MovedToTomorrow -> movedToTomorrowText
                    is TaskAction.Moved -> movedText
                    is TaskAction.Deleted -> deletedText
                }
            val result =
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = undoText,
                    withDismissAction = true,
                    duration = if (action is TaskAction.Deleted) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) controller.undoLast()
        }
    }

    val moveTask = (state.activeTasks + state.completedTasks).firstOrNull { it.id == moveTaskId }

    val docked = rememberDockedAddButton(targetDate = fabDate) { editor.openForCreate(fabDate) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!docked) {
                VoiceAddFab(targetDate = fabDate, onTap = { editor.openForCreate(fabDate) })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            content { task -> moveTaskId = task.id }
        }
    }

    moveTask?.let { task ->
        AlertDialog(
            onDismissRequest = { moveTaskId = -1L },
            title = { Text(stringResource(R.string.move_to_title)) },
            text = {
                Column {
                    MoveOptionRow(
                        label = stringResource(R.string.today),
                        onClick = {
                            controller.moveToDate(task, today)
                            moveTaskId = -1L
                        },
                    )
                    MoveOptionRow(
                        label = stringResource(R.string.tomorrow),
                        onClick = {
                            controller.moveToTomorrow(task)
                            moveTaskId = -1L
                        },
                    )
                    MoveOptionRow(
                        label = stringResource(R.string.pick_date),
                        onClick = { showMoveDatePicker = true },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveTaskId = -1L }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showMoveDatePicker && moveTask != null) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    moveTask.date
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showMoveDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            controller.moveToDate(moveTask, date)
                            moveTaskId = -1L
                        }
                        showMoveDatePicker = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    TaskEditorHost(editor = editor) { state ->
        controller.saveTask(
            title = state.title,
            date = state.date,
            time = state.time,
            note = state.note,
            editingTaskId = state.editingTaskId,
            isGlobal = state.isGlobal,
        )
        editor.clear()
    }
}

@Composable
private fun MoveOptionRow(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}
