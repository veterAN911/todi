package com.todi.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.todi.app.R
import com.todi.app.domain.model.Task
import com.todi.app.presentation.theme.TodiColors
import com.todi.app.presentation.util.DateFormatters
import com.todi.app.presentation.util.highlightMatches

private val CardShape = RoundedCornerShape(16.dp)

fun taskCardTag(taskId: Long): String = "task_card_$taskId"

@Composable
fun TaskCard(
    task: Task,
    onToggleCompleted: () -> Unit,
    onMoveToTomorrow: () -> Unit,
    onMoveToDate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    allowMove: Boolean = true,
    label: String? = null,
    highlight: String? = null,
) {
    val moveState = rememberSwipeToDismissBoxState()
    val completeState = rememberSwipeToDismissBoxState()

    LaunchedEffect(moveState.currentValue) {
        if (moveState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onMoveToTomorrow()
            moveState.reset()
        }
    }
    LaunchedEffect(completeState.currentValue) {
        if (completeState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            onToggleCompleted()
            completeState.reset()
        }
    }

    SwipeToDismissBox(
        state = moveState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = allowMove && !task.completed,
        backgroundContent = {
            SwipeBackground(isStartSide = false)
        },
        modifier = modifier,
    ) {
        SwipeToDismissBox(
            state = completeState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = false,
            backgroundContent = {
                SwipeBackground(isStartSide = true, completed = task.completed)
            },
        ) {
            TaskCardContent(
                task = task,
                onToggleCompleted = onToggleCompleted,
                onMoveToTomorrow = onMoveToTomorrow,
                onMoveToDate = onMoveToDate,
                onEdit = onEdit,
                onDelete = onDelete,
                allowMove = allowMove,
                label = label,
                highlight = highlight,
            )
        }
    }
}

@Composable
private fun SwipeBackground(
    isStartSide: Boolean,
    completed: Boolean = false,
) {
    val color =
        if (isStartSide) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val contentColor =
        if (isStartSide) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Box(
        Modifier
            .fillMaxSize()
            .background(color, CardShape),
        contentAlignment = if (isStartSide) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isStartSide) {
                Icon(
                    imageVector = if (completed) Icons.AutoMirrored.Outlined.Undo else Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                )
                Text(
                    stringResource(if (completed) R.string.uncomplete_task else R.string.complete_task),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            } else {
                Text(
                    stringResource(R.string.nav_tomorrow),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = contentColor)
            }
        }
    }
}

@Composable
private fun TaskCardContent(
    task: Task,
    onToggleCompleted: () -> Unit,
    onMoveToTomorrow: () -> Unit,
    onMoveToDate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    allowMove: Boolean,
    label: String?,
    highlight: String?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val completed = task.completed

    val titleStyle =
        MaterialTheme.typography.bodyLarge.copy(
            color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (completed) TextDecoration.LineThrough else null,
        )

    Surface(
        shape = CardShape,
        color =
            if (completed) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (completed) {
                Box(
                    Modifier
                        .toggleable(
                            value = true,
                            role = Role.Checkbox,
                            onValueChange = { onToggleCompleted() },
                        ).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.cd_completed_task),
                        tint = TodiColors.completed,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Checkbox(
                    checked = false,
                    onCheckedChange = { onToggleCompleted() },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                label?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = highlightMatches(task.title, highlight, MaterialTheme.colorScheme.primary),
                    style = titleStyle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                task.note?.let { note ->
                    Text(
                        text = highlightMatches(note, highlight, MaterialTheme.colorScheme.primary),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                task.time?.let { time ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = DateFormatters.time(time),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (completed) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                task.carriedFrom?.let { from ->
                    val locale = LocalConfiguration.current.locales[0]
                    Text(
                        text = stringResource(R.string.carried_from, DateFormatters.shortDayMonth(from, locale)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (completed) R.string.uncomplete_task else R.string.complete_task))
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onToggleCompleted()
                        },
                    )
                    if (allowMove) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_tomorrow)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                            enabled = !completed,
                            onClick = {
                                menuOpen = false
                                onMoveToTomorrow()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to)) },
                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMoveToDate()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_task)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_task), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
