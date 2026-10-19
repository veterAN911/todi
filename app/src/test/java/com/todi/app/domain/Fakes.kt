package com.todi.app.domain

import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.LanguageMode
import com.todi.app.domain.model.LockScreenScope
import com.todi.app.domain.model.NotificationScope
import com.todi.app.domain.model.Task
import com.todi.app.domain.model.ThemeMode
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.TaskSorter
import com.todi.app.domain.util.TodayProvider
import com.todi.app.domain.util.newSyncId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class FakeTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow<Map<Long, Task>>(emptyMap())
    private var nextId = 1L

    override fun observeTasksForDate(date: LocalDate): Flow<List<Task>> =
        tasks.map { map ->
            TaskSorter.sortForDay(map.values.filter { it.isVisible && !it.isGlobal && it.date == date })
        }

    override fun observeActiveTasksForDate(date: LocalDate): Flow<List<Task>> =
        tasks.map { map ->
            map.values.filter { it.isVisible && !it.isGlobal && it.date == date && !it.completed }
        }

    override fun observeOverdueTasks(beforeDate: LocalDate): Flow<List<Task>> =
        tasks.map { map ->
            map.values.filter { it.isVisible && !it.isGlobal && it.date < beforeDate && !it.completed }
        }

    override fun observeActiveTasksBetween(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Task>> =
        tasks.map { map ->
            map.values.filter { it.isVisible && !it.isGlobal && it.date in from..to && !it.completed }
        }

    override fun observeGlobalTasks(): Flow<List<Task>> =
        tasks.map { map -> TaskSorter.sortGlobal(map.values.filter { it.isVisible && it.isGlobal && !it.completed }) }

    override fun observeCompletedTasks(): Flow<List<Task>> =
        tasks.map { map ->
            map.values
                .filter { it.isVisible && it.completed }
                .sortedWith(
                    compareByDescending<Task> { it.completedAt ?: Instant.EPOCH }
                        .thenByDescending { it.id },
                )
        }

    private val Task.isVisible: Boolean get() = deletedAt == null

    override suspend fun getTask(id: Long): Task? = tasks.value[id]

    override fun observeAllTasks(): Flow<List<Task>> = tasks.map { it.values.toList() }

    override suspend fun createTask(
        title: String,
        date: LocalDate,
        time: LocalTime?,
        note: String?,
        isGlobal: Boolean,
    ): Task {
        val now = Instant.now()
        val task =
            Task(
                id = nextId++,
                title = title,
                date = date,
                time = if (isGlobal) null else time,
                note = note,
                completed = false,
                createdAt = now,
                updatedAt = now,
                syncId = newSyncId(),
                isGlobal = isGlobal,
            )
        tasks.update { it + (task.id to task) }
        return task
    }

    override suspend fun updateTask(task: Task) {
        tasks.update { it + (task.id to task.normalized()) }
    }

    override suspend fun setCompleted(
        id: Long,
        completed: Boolean,
    ) {
        tasks.update { map ->
            map[id]?.let {
                map + (id to it.copy(completed = completed, completedAt = if (completed) Instant.now() else null))
            } ?: map
        }
    }

    override suspend fun moveTask(
        id: Long,
        date: LocalDate,
    ) {
        tasks.update { map ->
            map[id]?.let { map + (id to it.copy(date = date, isGlobal = false)) } ?: map
        }
    }

    override suspend fun deleteTask(id: Long) {
        tasks.update { map ->
            map[id]
                ?.takeIf { it.deletedAt == null }
                ?.let { map + (id to it.copy(deletedAt = Instant.now())) }
                ?: map
        }
    }

    override suspend fun restoreCompletedTask(
        id: Long,
        date: LocalDate,
    ) {
        tasks.update { map ->
            map[id]?.let { task ->
                val restored =
                    task.copy(
                        completed = false,
                        completedAt = null,
                        date = date,
                        isGlobal = false,
                    )
                map + (id to restored)
            } ?: map
        }
    }

    override suspend fun restoreTask(task: Task) {
        tasks.update { it + (task.id to task.normalized()) }
    }

    override suspend fun replaceAll(tasks: List<Task>) {
        this.tasks.value =
            tasks
                .associateBy { it.id }
                .mapValues { (_, task) -> task.normalized() }
        nextId = (tasks.maxOfOrNull { it.id } ?: 0L) + 1
    }

    override suspend fun purgeTombstones(expiresBefore: Instant): Int {
        var removed = 0
        tasks.update { map ->
            map.filterValues { task ->
                val expired = task.deletedAt != null && task.deletedAt.isBefore(expiresBefore)
                if (expired) removed++
                !expired
            }
        }
        return removed
    }

    override suspend fun carryOverIncompleteTasks(targetDate: LocalDate): Int {
        var moved = 0
        tasks.update { map ->
            map.mapValues { (_, task) ->
                if (!task.completed && !task.isGlobal && task.isVisible && task.date < targetDate) {
                    moved++

                    task.copy(date = targetDate, carriedFrom = task.carriedFrom ?: task.date)
                } else {
                    task
                }
            }
        }
        return moved
    }

    override suspend fun deleteCompleted(completedBefore: Instant?): Int {
        var removed = 0
        tasks.update { map ->
            map.filterValues { task ->

                val expired =
                    completedBefore == null || (
                        task.completedAt != null && task.completedAt.isBefore(completedBefore)
                    )
                if (task.completed && task.isVisible && expired) {
                    removed++
                    false
                } else {
                    true
                }
            }
        }
        return removed
    }

    override suspend fun setCalendarEventId(
        id: Long,
        eventId: Long?,
    ) {
        tasks.update { map ->
            map[id]?.let { map + (id to it.copy(calendarEventId = eventId)) } ?: map
        }
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    override suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    override suspend fun setLanguageMode(mode: LanguageMode) = update { it.copy(languageMode = mode) }

    override suspend fun setNotificationsEnabled(enabled: Boolean) = update { it.copy(notificationsEnabled = enabled) }

    override suspend fun setShowOnLockScreen(show: Boolean) = update { it.copy(showOnLockScreen = show) }

    override suspend fun setNotificationScope(scope: NotificationScope) = update { it.copy(notificationScope = scope) }

    override suspend fun setAutoCarryOver(enabled: Boolean) = update { it.copy(autoCarryOver = enabled) }

    override suspend fun setCalendarSyncEnabled(enabled: Boolean) = update { it.copy(calendarSyncEnabled = enabled) }

    override suspend fun setShowCompletedInDay(show: Boolean) = update { it.copy(showCompletedInDay = show) }

    override suspend fun setCompletedRetentionMonths(months: Int) = update { it.copy(completedRetentionMonths = months) }

    override suspend fun setNotificationColorToday(argb: Int?) = update { it.copy(notificationColorToday = argb) }

    override suspend fun setNotificationColorOverdue(argb: Int?) = update { it.copy(notificationColorOverdue = argb) }

    override suspend fun setNotificationColorDateLess(argb: Int?) = update { it.copy(notificationColorDateLess = argb) }

    override suspend fun setLockScreenScope(scope: LockScreenScope) = update { it.copy(lockScreenScope = scope) }

    override suspend fun setGlobalReminderEnabled(enabled: Boolean) = update { it.copy(globalReminderEnabled = enabled) }

    override suspend fun setGlobalReminderStartMinutes(minutes: Int) = update { it.copy(globalReminderStartMinutes = minutes) }

    override suspend fun setGlobalReminderIntervalMinutes(minutes: Int) = update { it.copy(globalReminderIntervalMinutes = minutes) }

    override suspend fun setGlobalReminderQuietAfterMinutes(minutes: Int) = update { it.copy(globalReminderQuietAfterMinutes = minutes) }

    override suspend fun markNotificationPromptShown() = update { it.copy(notificationPromptShown = true) }

    override suspend fun setBottomBarRoutes(routes: List<String>) = update { it.copy(bottomBarRoutes = routes) }

    override suspend fun setVoiceInputEnabled(enabled: Boolean) = update { it.copy(voiceInputEnabled = enabled) }

    override suspend fun setBackupEnabled(enabled: Boolean) = update { it.copy(backupEnabled = enabled) }

    override suspend fun setBackupLastUploadAt(millis: Long) = update { it.copy(backupLastUploadAt = Instant.ofEpochMilli(millis)) }

    private inline fun update(block: (AppSettings) -> AppSettings) {
        state.update(block)
    }
}

class FakeTodayProvider(
    initial: LocalDate,
) : TodayProvider {
    private val _today = MutableStateFlow(initial)
    override val today: StateFlow<LocalDate> = _today.asStateFlow()

    override fun refresh() {
    }

    fun setDate(date: LocalDate) {
        _today.value = date
    }
}
