package com.todi.app.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.R
import com.todi.app.presentation.components.DayHeaderBar
import com.todi.app.presentation.components.DayListContent
import com.todi.app.presentation.components.TaskListHost
import com.todi.app.presentation.components.dayListItems
import com.todi.app.presentation.editor.TaskEditorViewModel
import com.todi.app.presentation.util.DateFormatters
import com.todi.app.presentation.voice.addButtonClearance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private const val SELECTED_DAY_ITEM = 2

@Composable
fun CalendarScreen(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: CalendarViewModel,
    editor: TaskEditorViewModel,
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val counts by viewModel.monthTaskCounts.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val dayState by viewModel.controller.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= 840.dp
        if (sideBySide) {
            TaskListHost(
                controller = viewModel.controller,
                editor = editor,
                today = today,
                fabDate = selectedDate,
            ) { onRequestMove ->
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(0.45f)
                            .fillMaxSize()
                            .padding(start = 16.dp, top = 12.dp, end = 8.dp),
                    ) {
                        CalendarPanel(
                            month = month,
                            selectedDate = selectedDate,
                            today = today,
                            counts = counts,
                            onSelectDate = viewModel::selectDate,
                            onPreviousMonth = viewModel::showPreviousMonth,
                            onNextMonth = viewModel::showNextMonth,
                            onJumpToToday = viewModel::jumpToToday,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .padding(vertical = 12.dp),
                    )
                    Column(
                        Modifier
                            .weight(0.55f)
                            .fillMaxSize()
                            .padding(start = 8.dp, top = 12.dp, end = 16.dp),
                    ) {
                        SelectedDayHeaderBar(
                            date = selectedDate,
                            count = dayState.activeCount,
                            showMenuButton = showMenuButton,
                            onOpenMenu = onOpenMenu,
                            onSearch = onOpenSearch,
                        )
                        DayListContent(
                            state = dayState,
                            isToday = selectedDate == today,
                            onToggleCompleted = viewModel.controller::toggleCompleted,
                            onMoveToTomorrow = viewModel.controller::moveToTomorrow,
                            onMoveToDate = onRequestMove,
                            onEdit = editor::openForEdit,
                            onDelete = viewModel.controller::delete,
                            onAddTask = { editor.openForCreate(selectedDate) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                        )
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()

            var lastAutoScrolledEpochDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
            LaunchedEffect(selectedDate) {
                val epochDay = selectedDate.toEpochDay()
                if (lastAutoScrolledEpochDay != Long.MIN_VALUE && lastAutoScrolledEpochDay != epochDay) {
                    listState.animateScrollToItem(SELECTED_DAY_ITEM)
                }
                lastAutoScrolledEpochDay = epochDay
            }

            TaskListHost(
                controller = viewModel.controller,
                editor = editor,
                today = today,
                fabDate = selectedDate,
            ) { onRequestMove ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = addButtonClearance()),
                ) {
                    item(key = "title") {
                        DayHeaderBar(
                            title = stringResource(R.string.nav_calendar),
                            subtitle = null,
                            count = 0,
                            showMenuButton = showMenuButton,
                            onOpenMenu = onOpenMenu,
                            onSearch = onOpenSearch,
                        )
                    }
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
                    stickyHeader(key = "selected_day") {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            SelectedDayHeaderBar(
                                date = selectedDate,
                                count = dayState.activeCount,
                                showMenuButton = false,
                                onOpenMenu = {},
                            )
                        }
                    }
                    dayListItems(
                        state = dayState,
                        isToday = selectedDate == today,
                        onToggleCompleted = viewModel.controller::toggleCompleted,
                        onMoveToTomorrow = viewModel.controller::moveToTomorrow,
                        onMoveToDate = onRequestMove,
                        onEdit = editor::openForEdit,
                        onDelete = viewModel.controller::delete,
                        onAddTask = { editor.openForCreate(selectedDate) },
                        fillHeight = false,
                        animateChanges = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayHeaderBar(
    date: LocalDate,
    count: Int,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onSearch: (() -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    DayHeaderBar(
        title = DateFormatters.fullDate(date, locale),
        subtitle = null,
        count = count,
        showMenuButton = showMenuButton,
        onOpenMenu = onOpenMenu,
        titleStyle = MaterialTheme.typography.titleLarge,
        onSearch = onSearch,
    )
}

@Composable
internal fun CalendarPanel(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    counts: Map<LocalDate, Int>,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DateFormatters.monthYear(month, locale),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
            )
            if (today !in month.atDay(1)..month.atEndOfMonth()) {
                TextButton(onClick = onJumpToToday) { Text(stringResource(R.string.today)) }
            }
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.cd_previous_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNextMonth, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.cd_next_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            MonthGrid.weekdayHeaders().forEach { dayOfWeekValue ->
                Text(
                    text = DateFormatters.weekdayNarrow(DayOfWeek.of(dayOfWeekValue), locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                )
            }
        }

        val days = MonthGrid.days(month)
        Column(Modifier.fillMaxWidth()) {
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            inMonth = day.month == month.month,
                            selected = day == selectedDate,
                            isToday = day == today,
                            taskCount = counts[day] ?: 0,
                            onClick = { onSelectDate(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    taskCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberColor =
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            !inMonth -> MaterialTheme.colorScheme.outline
            isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        ).border(
                            width = if (isToday && !selected) 1.5.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
            ) {
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = numberColor,
                )
            }

            Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                when {
                    taskCount == 1 -> {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }

                    taskCount > 1 -> {
                        Text(
                            text = taskCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
