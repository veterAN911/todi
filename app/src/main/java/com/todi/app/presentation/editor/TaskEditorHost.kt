package com.todi.app.presentation.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TaskEditorHost(
    editor: TaskEditorViewModel,
    onSave: (TaskEditorUiState) -> Unit,
) {
    val state by editor.state.collectAsStateWithLifecycle()
    if (!state.visible) return

    TaskEditorSheet(
        state = state,
        onTitleChange = editor::setTitle,
        onGlobalChange = editor::setGlobal,
        onDateChange = editor::setDate,
        onTimeChange = editor::setTime,
        onNoteChange = editor::setNote,
        onSave = { onSave(state) },
        onDismiss = editor::clear,
    )
}
