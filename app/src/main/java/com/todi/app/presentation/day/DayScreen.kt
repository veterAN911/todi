package com.todi.app.presentation.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.presentation.components.DayHeaderBar
import com.todi.app.presentation.components.DayListContent
import com.todi.app.presentation.components.TaskListHost
import com.todi.app.presentation.editor.TaskEditorViewModel
import com.todi.app.presentation.util.DateFormatters
import java.time.LocalDate

@Composable
fun DayScreen(
    title: String,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    controller: TaskListController,
    editor: TaskEditorViewModel,
    actualToday: LocalDate,
) {
    val state by controller.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    TaskListHost(
        controller = controller,
        editor = editor,
        today = actualToday,
        fabDate = state.date,
    ) { onRequestMove ->
        Column(Modifier.fillMaxSize()) {
            DayHeaderBar(
                title = title,
                subtitle = DateFormatters.dayMonth(state.date, locale),
                count = state.activeCount,
                showMenuButton = showMenuButton,
                onOpenMenu = onOpenMenu,
                onSearch = onOpenSearch,
            )
            DayListContent(
                state = state,
                isToday = state.date == actualToday,
                onToggleCompleted = controller::toggleCompleted,
                onMoveToTomorrow = controller::moveToTomorrow,
                onMoveToDate = onRequestMove,
                onEdit = editor::openForEdit,
                onDelete = controller::delete,
                onAddTask = { editor.openForCreate(state.date) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
        }
    }
}
