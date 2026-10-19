package com.todi.app.presentation.global

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.R
import com.todi.app.presentation.components.BrandMark
import com.todi.app.presentation.components.DayHeaderBar
import com.todi.app.presentation.components.TaskCard
import com.todi.app.presentation.day.TaskAction
import com.todi.app.presentation.editor.TaskEditorHost
import com.todi.app.presentation.editor.TaskEditorViewModel
import com.todi.app.presentation.voice.VoiceAddFab
import com.todi.app.presentation.voice.addButtonClearance
import com.todi.app.presentation.voice.rememberDockedAddButton

@Composable
fun GlobalTasksScreen(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: GlobalTasksViewModel,
    editor: TaskEditorViewModel,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.task_deleted)
    val undoText = stringResource(R.string.undo)

    LaunchedEffect(Unit) {
        viewModel.actions.collect { action ->
            if (action !is TaskAction.Deleted) return@collect
            val result =
                snackbarHostState.showSnackbar(
                    message = deletedText,
                    actionLabel = undoText,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    val docked = rememberDockedAddButton(targetDate = null) { editor.openForCreate(today, isGlobal = true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!docked) {
                VoiceAddFab(targetDate = null, onTap = { editor.openForCreate(today, isGlobal = true) })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DayHeaderBar(
                title = stringResource(R.string.nav_global),
                subtitle = null,
                count = tasks.size,
                showMenuButton = showMenuButton,
                onOpenMenu = onOpenMenu,
                onSearch = onOpenSearch,
            )
            if (tasks.isEmpty()) {
                EmptyGlobalState(
                    onAddTask = { editor.openForCreate(today, isGlobal = true) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentPadding = PaddingValues(bottom = addButtonClearance()),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleCompleted = { viewModel.toggleCompleted(task) },
                            onMoveToTomorrow = {},
                            onMoveToDate = {},
                            onEdit = { editor.openForEdit(task) },
                            onDelete = { viewModel.delete(task) },
                            modifier = Modifier.animateItem(),
                            allowMove = false,
                        )
                    }
                }
            }
        }
    }

    TaskEditorHost(editor = editor) { state ->
        viewModel.saveTask(
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
private fun EmptyGlobalState(
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = 56.dp)
        Text(
            text = stringResource(R.string.no_global_tasks),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.global_tasks_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedButton(
            onClick = onAddTask,
            shape = RoundedCornerShape(14.dp),
            modifier =
                Modifier
                    .padding(top = 20.dp)
                    .size(height = 48.dp, width = 200.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.add_task),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
