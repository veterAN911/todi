package com.todi.app.presentation.search

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.R
import com.todi.app.domain.model.Task
import com.todi.app.domain.util.SearchGroup
import com.todi.app.domain.util.SearchSection
import com.todi.app.presentation.components.BrandMark
import com.todi.app.presentation.components.TaskCard
import com.todi.app.presentation.day.TaskAction
import com.todi.app.presentation.editor.TaskEditorHost
import com.todi.app.presentation.editor.TaskEditorViewModel
import com.todi.app.presentation.util.DateFormatters
import java.util.Locale

internal const val SEARCH_FIELD_TAG = "search_field"

internal fun searchGroupTag(section: SearchSection): String = "search_group_${section.name.lowercase()}"

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel,
    editor: TaskEditorViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

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
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                onBack = onBack,
            )
            when {
                !state.hasQuery -> {
                    SearchPromptState(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    )
                }

                state.noResults -> {
                    NoResultsState(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        state.groups.forEach { group ->
                            item(key = "header_${group.section}") {
                                SearchGroupHeader(section = group.section)
                            }
                            items(group.tasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    onToggleCompleted = { viewModel.toggleCompleted(task) },
                                    onMoveToTomorrow = {},
                                    onMoveToDate = {},
                                    onEdit = { editor.openForEdit(task) },
                                    onDelete = { viewModel.delete(task) },
                                    modifier = Modifier.animateItem(),
                                    allowMove = false,
                                    label = taskLabel(group.section, task, locale),
                                    highlight = state.query,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    TaskEditorHost(editor = editor) { editState ->
        viewModel.saveTask(
            title = editState.title,
            date = editState.date,
            time = editState.time,
            note = editState.note,
            editingTaskId = editState.editingTaskId,
            isGlobal = editState.isGlobal,
        )
        editor.clear()
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag(SEARCH_FIELD_TAG),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_clear_query),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
    }
}

@Composable
private fun SearchGroupHeader(section: SearchSection) {
    val title =
        when (section) {
            SearchSection.TODAY -> stringResource(R.string.nav_today)
            SearchSection.TOMORROW -> stringResource(R.string.nav_tomorrow)
            SearchSection.GLOBAL -> stringResource(R.string.nav_global)
            SearchSection.DATED -> stringResource(R.string.search_other_dates)
            SearchSection.COMPLETED -> stringResource(R.string.nav_completed)
        }
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                .testTag(searchGroupTag(section)),
    )
}

@Composable
private fun taskLabel(
    section: SearchSection,
    task: Task,
    locale: Locale,
): String? =
    when {
        task.isGlobal && section == SearchSection.COMPLETED -> {
            stringResource(R.string.editor_without_date)
        }

        section == SearchSection.DATED -> {
            DateFormatters.dayMonth(task.date, locale)
        }

        else -> {
            null
        }
    }

@Composable
private fun SearchPromptState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = 56.dp)
        Text(
            text = stringResource(R.string.search_prompt_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.search_prompt_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun NoResultsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.search_nothing_found),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.search_nothing_found_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
