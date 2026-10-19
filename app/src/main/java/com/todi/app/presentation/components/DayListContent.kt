package com.todi.app.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todi.app.R
import com.todi.app.domain.model.Task
import com.todi.app.presentation.day.DayUiState
import com.todi.app.presentation.voice.addButtonClearance

fun LazyListScope.dayListItems(
    state: DayUiState,
    isToday: Boolean,
    onToggleCompleted: (Task) -> Unit,
    onMoveToTomorrow: (Task) -> Unit,
    onMoveToDate: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onAddTask: () -> Unit,
    fillHeight: Boolean = true,
    animateChanges: Boolean = true,
) {
    if (!state.hasAnyTask) {
        item(key = "empty") {
            EmptyDayState(
                isToday = isToday,
                onAddTask = onAddTask,
                fillHeight = fillHeight,
            )
        }
        return
    }

    if (state.activeCount == 0) {
        item(key = "done_banner") { AllDoneBanner(isToday = isToday) }
    }

    items(state.activeTasks, key = { it.id }) { task ->
        TaskCard(
            task = task,
            onToggleCompleted = { onToggleCompleted(task) },
            onMoveToTomorrow = { onMoveToTomorrow(task) },
            onMoveToDate = { onMoveToDate(task) },
            onEdit = { onEdit(task) },
            onDelete = { onDelete(task) },
            modifier =
                (if (animateChanges) Modifier.animateItem() else Modifier)
                    .testTag(taskCardTag(task.id)),
        )
    }

    if (state.showCompleted && state.completedCount > 0) {
        item(key = "completed_header") { CompletedSectionHeader(count = state.completedCount) }
        items(state.completedTasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                onToggleCompleted = { onToggleCompleted(task) },
                onMoveToTomorrow = { onMoveToTomorrow(task) },
                onMoveToDate = { onMoveToDate(task) },
                onEdit = { onEdit(task) },
                onDelete = { onDelete(task) },
                modifier =
                    (if (animateChanges) Modifier.animateItem() else Modifier)
                        .testTag(taskCardTag(task.id)),
            )
        }
    }
}

@Composable
private fun CompletedSectionHeader(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.completed_section, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

const val DAY_LIST_TAG = "dayList"

@Composable
fun DayListContent(
    state: DayUiState,
    isToday: Boolean,
    onToggleCompleted: (Task) -> Unit,
    onMoveToTomorrow: (Task) -> Unit,
    onMoveToDate: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag(DAY_LIST_TAG),
        contentPadding = PaddingValues(bottom = addButtonClearance()),
    ) {
        dayListItems(
            state = state,
            isToday = isToday,
            onToggleCompleted = onToggleCompleted,
            onMoveToTomorrow = onMoveToTomorrow,
            onMoveToDate = onMoveToDate,
            onEdit = onEdit,
            onDelete = onDelete,
            onAddTask = onAddTask,
        )
    }
}
