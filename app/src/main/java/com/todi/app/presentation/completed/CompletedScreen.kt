package com.todi.app.presentation.completed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.R
import com.todi.app.domain.model.Task
import com.todi.app.domain.util.CompletedDayKind
import com.todi.app.domain.util.CompletedGroup
import com.todi.app.presentation.calendar.CalendarPanel
import com.todi.app.presentation.components.BrandMark
import com.todi.app.presentation.components.DayHeaderBar
import com.todi.app.presentation.components.TaskCard
import com.todi.app.presentation.components.taskCardTag
import com.todi.app.presentation.day.TaskAction
import com.todi.app.presentation.editor.TaskEditorHost
import com.todi.app.presentation.editor.TaskEditorViewModel
import com.todi.app.presentation.util.DateFormatters

private const val ARCHIVE_DAY_FIRST_ITEM = 1

@Composable
fun CompletedScreen(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: CompletedViewModel,
    editor: TaskEditorViewModel,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    var pendingRestore by remember { mutableStateOf<Task?>(null) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DayHeaderBar(
                title = stringResource(R.string.nav_completed),
                subtitle = null,
                count = total,
                showMenuButton = showMenuButton,
                onOpenMenu = onOpenMenu,
                onSearch = onOpenSearch,
            )
            ArchiveViewToggle(mode = viewMode, onToggle = viewModel::toggleViewMode)
            when (viewMode) {
                CompletedViewMode.DAYS -> {
                    if (groups.isEmpty()) {
                        EmptyArchiveState(
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
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            groups.forEach { group ->
                                item(key = "header_${group.date}") {
                                    CompletedGroupHeader(group = group)
                                }
                                items(group.tasks, key = { it.id }) { task ->
                                    ArchiveTaskCard(
                                        task = task,
                                        onEdit = { editor.openForEdit(task) },
                                        onDelete = { viewModel.delete(task) },
                                        onRestore = { pendingRestore = task },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }

                CompletedViewMode.CALENDAR -> {
                    ArchiveCalendar(
                        viewModel = viewModel,
                        onEdit = editor::openForEdit,
                        onRestore = { pendingRestore = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    )
                }
            }
        }
    }

    RestoreCompletedTaskFlow(
        task = pendingRestore,
        today = today,
        onRestore = { date ->
            pendingRestore?.let { viewModel.restoreToDate(it, date) }
            pendingRestore = null
        },
        onDismiss = { pendingRestore = null },
    )

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
private fun CompletedGroupHeader(group: CompletedGroup) {
    val locale = LocalConfiguration.current.locales[0]
    val title =
        when (group.kind) {
            CompletedDayKind.TODAY -> stringResource(R.string.completed_today)
            CompletedDayKind.YESTERDAY -> stringResource(R.string.completed_yesterday)
            CompletedDayKind.EARLIER -> DateFormatters.dayMonth(group.date, locale)
        }
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyArchiveState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = 56.dp)
        Text(
            text = stringResource(R.string.no_completed_tasks),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.completed_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ArchiveViewToggle(
    mode: CompletedViewMode,
    onToggle: () -> Unit,
) {
    val asCalendar = mode == CompletedViewMode.DAYS
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector =
                    if (asCalendar) {
                        Icons.Outlined.CalendarMonth
                    } else {
                        Icons.AutoMirrored.Outlined.ViewList
                    },
                contentDescription =
                    stringResource(
                        if (asCalendar) R.string.cd_archive_as_calendar else R.string.cd_archive_as_list,
                    ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArchiveTaskCard(
    task: Task,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TaskCard(
        task = task,
        onToggleCompleted = onRestore,
        onMoveToTomorrow = {},
        onMoveToDate = {},
        onEdit = onEdit,
        onDelete = onDelete,
        modifier = modifier.testTag(taskCardTag(task.id)),
        allowMove = false,
        label = if (task.isGlobal) stringResource(R.string.editor_without_date) else null,
    )
}

@Composable
private fun ArchiveCalendar(
    viewModel: CompletedViewModel,
    onEdit: (Task) -> Unit,
    onRestore: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val counts by viewModel.dayCounts.collectAsStateWithLifecycle()
    val tasks by viewModel.selectedDayTasks.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var lastAutoScrolledEpochDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    LaunchedEffect(selectedDate) {
        val epochDay = selectedDate.toEpochDay()
        if (lastAutoScrolledEpochDay != Long.MIN_VALUE && lastAutoScrolledEpochDay != epochDay) {
            listState.animateScrollToItem(ARCHIVE_DAY_FIRST_ITEM)
        }
        lastAutoScrolledEpochDay = epochDay
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "grid") {
            CalendarPanel(
                month = month,
                selectedDate = selectedDate,
                today = today,
                counts = counts,
                onSelectDate = viewModel::selectDate,
                onPreviousMonth = viewModel::showPreviousMonth,
                onNextMonth = viewModel::showNextMonth,
                onJumpToToday = viewModel::jumpToToday,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
            )
        }
        if (tasks.isEmpty()) {
            item(key = "empty_day") {
                Text(
                    text = stringResource(R.string.completed_nothing_that_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                )
            }
        } else {
            items(tasks, key = { it.id }) { task ->

                ArchiveTaskCard(
                    task = task,
                    onEdit = { onEdit(task) },
                    onDelete = { viewModel.delete(task) },
                    onRestore = { onRestore(task) },
                )
            }
        }
    }
}
