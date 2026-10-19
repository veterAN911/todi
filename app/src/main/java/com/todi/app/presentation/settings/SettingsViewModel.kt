package com.todi.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todi.app.backup.BackupFailure
import com.todi.app.backup.BackupManager
import com.todi.app.backup.BackupOutcome
import com.todi.app.backup.CopiesOutcome
import com.todi.app.backup.RemoteCopy
import com.todi.app.backup.RestoreOutcome
import com.todi.app.calendar.CalendarSyncManager
import com.todi.app.calendar.CalendarSyncObserver
import com.todi.app.domain.model.BackupAccount
import com.todi.app.domain.model.LanguageMode
import com.todi.app.domain.model.LockScreenScope
import com.todi.app.domain.model.NotificationScope
import com.todi.app.domain.model.ThemeMode
import com.todi.app.domain.repository.BackupAccountRepository
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.usecase.CompletedArchiveCleanup
import com.todi.app.domain.usecase.GetActiveTasksForNotificationUseCase
import com.todi.app.domain.usecase.GetGlobalTasksUseCase
import com.todi.app.domain.util.NavBarRoutes
import com.todi.app.notification.NotificationContent
import com.todi.app.notification.NotificationContentBuilder
import com.todi.app.notification.NotificationPresentation
import com.todi.app.notification.NotificationSection
import com.todi.app.notification.TaskNotificationManager
import com.todi.app.presentation.navigation.ALL_ROUTES
import com.todi.app.presentation.navigation.DEFAULT_BOTTOM_BAR_ROUTES
import com.todi.app.presentation.voice.VoskModelManager
import com.todi.app.presentation.voice.VoskModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationPreviewState(
    val shadeTitle: String,
    val shade: NotificationContent,
    val lockScreenTitle: String?,
    val lockScreen: NotificationContent?,
    val reminderTitle: String,
    val reminder: NotificationContent,
)

data class BackupState(
    val signedInEmail: String? = null,
    val busy: Boolean = false,
    val copies: List<RemoteCopy> = emptyList(),
    val copiesLoaded: Boolean = false,
    val error: BackupFailure? = null,
    val message: BackupMessage? = null,
)

sealed interface BackupMessage {
    data object Uploaded : BackupMessage

    data object UpToDate : BackupMessage

    data class Restored(
        val taskCount: Int,
    ) : BackupMessage
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val notificationManager: TaskNotificationManager,
        private val getActiveTasks: GetActiveTasksForNotificationUseCase,
        private val getGlobalTasks: GetGlobalTasksUseCase,
        private val calendarSyncManager: CalendarSyncManager,
        private val calendarSyncObserver: CalendarSyncObserver,
        private val archiveCleanup: CompletedArchiveCleanup,
        private val voiceModels: VoskModelManager,
        private val backupManager: BackupManager,
        private val backupAccounts: BackupAccountRepository,
    ) : ViewModel() {
        val settings = settingsRepository.settings

        val backupAccount: StateFlow<BackupAccount?> =
            backupAccounts.account.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ACCOUNT_SUBSCRIBE_MILLIS),
                initialValue = null,
            )

        private val _backup = MutableStateFlow(BackupState())

        val backup: StateFlow<BackupState> = _backup.asStateFlow()

        val notificationPreview: StateFlow<NotificationPreviewState?> =
            combine(
                settingsRepository.settings,
                getActiveTasks(),
                getGlobalTasks(),
            ) { settings, tasks, globalTasks ->
                val presentation = NotificationPresentation.from(settings)

                val shade =
                    NotificationContentBuilder.build(
                        todayTasks = tasks.today,
                        overdueTasks = tasks.overdue,
                        dateLessTasks = tasks.dateLess,
                        overdueHeader = notificationManager.localizedOverdueHeader(),
                        dateLessHeader = notificationManager.localizedDateLessHeader(),
                        detail = presentation.detail,
                    )
                val reminder = NotificationContentBuilder.buildReminder(globalTasks, detail = presentation.detail)
                val lockScreen = presentation.lockScreenContent(shade)
                NotificationPreviewState(
                    shadeTitle = notificationManager.titleFor(shade),
                    shade = shade,
                    lockScreenTitle = lockScreen?.let(notificationManager::titleFor),
                    lockScreen = lockScreen,
                    reminderTitle = notificationManager.titleFor(reminder),
                    reminder = reminder,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(PREVIEW_SUBSCRIBE_MILLIS),
                initialValue = null,
            )

        val lastSyncError = calendarSyncManager.lastSyncError

        private val _notificationsAllowed = MutableStateFlow(false)
        val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed.asStateFlow()

        private val _calendarAllowed = MutableStateFlow(false)
        val calendarAllowed: StateFlow<Boolean> = _calendarAllowed.asStateFlow()

        private val _archiveCleared = MutableStateFlow(false)
        val archiveCleared: StateFlow<Boolean> = _archiveCleared.asStateFlow()

        fun refreshPermissionStates() {
            _notificationsAllowed.value = notificationManager.canPost()
            _calendarAllowed.value = calendarSyncManager.hasPermission()
        }

        val voiceModel: StateFlow<VoskModelState> = voiceModels.state

        fun refreshVoiceModel() {
            viewModelScope.launch { voiceModels.refresh() }
        }

        fun downloadVoiceModel() {
            voiceModels.download()
        }

        fun removeVoiceModel() {
            viewModelScope.launch { voiceModels.remove() }
        }

        fun setVoiceInputEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setVoiceInputEnabled(enabled) }
        }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { settingsRepository.setThemeMode(mode) }
        }

        fun setLanguageMode(mode: LanguageMode) {
            viewModelScope.launch { settingsRepository.setLanguageMode(mode) }
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
        }

        fun setShowOnLockScreen(show: Boolean) {
            viewModelScope.launch { settingsRepository.setShowOnLockScreen(show) }
        }

        fun setNotificationScope(scope: NotificationScope) {
            viewModelScope.launch { settingsRepository.setNotificationScope(scope) }
        }

        fun setAutoCarryOver(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setAutoCarryOver(enabled) }
        }

        fun setCalendarSyncEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setCalendarSyncEnabled(enabled) }
        }

        fun requestCalendarSync() {
            calendarSyncObserver.requestSync()
        }

        fun markNotificationPromptShown() {
            viewModelScope.launch { settingsRepository.markNotificationPromptShown() }
        }

        fun setShowCompletedInDay(show: Boolean) {
            viewModelScope.launch { settingsRepository.setShowCompletedInDay(show) }
        }

        fun setBottomBarRoute(
            route: String,
            on: Boolean,
        ) {
            viewModelScope.launch {
                val stored = settingsRepository.settings.first().bottomBarRoutes
                settingsRepository.setBottomBarRoutes(
                    NavBarRoutes.toggle(stored, route, on, DEFAULT_BOTTOM_BAR_ROUTES, ALL_ROUTES),
                )
            }
        }

        fun setCompletedRetentionMonths(months: Int) {
            viewModelScope.launch {
                settingsRepository.setCompletedRetentionMonths(months)
                archiveCleanup.perform()
            }
        }

        fun setNotificationColor(
            section: NotificationSection,
            argb: Int?,
        ) {
            viewModelScope.launch {
                when (section) {
                    NotificationSection.TODAY -> settingsRepository.setNotificationColorToday(argb)
                    NotificationSection.OVERDUE -> settingsRepository.setNotificationColorOverdue(argb)
                    NotificationSection.DATE_LESS -> settingsRepository.setNotificationColorDateLess(argb)
                }
            }
        }

        fun setLockScreenScope(scope: LockScreenScope) {
            viewModelScope.launch { settingsRepository.setLockScreenScope(scope) }
        }

        fun setGlobalReminderEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setGlobalReminderEnabled(enabled) }
        }

        fun setGlobalReminderStart(minutes: Int) {
            viewModelScope.launch { settingsRepository.setGlobalReminderStartMinutes(minutes) }
        }

        fun setGlobalReminderInterval(minutes: Int) {
            viewModelScope.launch { settingsRepository.setGlobalReminderIntervalMinutes(minutes) }
        }

        fun setGlobalReminderQuietAfter(minutes: Int) {
            viewModelScope.launch { settingsRepository.setGlobalReminderQuietAfterMinutes(minutes) }
        }

        fun clearCompleted() {
            viewModelScope.launch {
                archiveCleanup.clearAll()
                _archiveCleared.value = true
            }
        }

        fun consumeArchiveCleared() {
            _archiveCleared.value = false
        }

        fun setBackupEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setBackupEnabled(enabled)

                if (enabled) refreshBackup()
            }
        }

        fun signOutBackup() {
            viewModelScope.launch {
                backupAccounts.signOut()
                _backup.update { it.copy(copies = emptyList(), copiesLoaded = false, error = null, message = null) }
            }
        }

        fun backupNow() {
            viewModelScope.launch {
                _backup.update { it.copy(busy = true, error = null, message = null) }
                when (val outcome = backupManager.backupNow()) {
                    is BackupOutcome.Uploaded -> {
                        _backup.update { it.copy(busy = false, message = BackupMessage.Uploaded) }
                        refreshBackup()
                    }

                    BackupOutcome.UpToDate -> {
                        _backup.update { it.copy(busy = false, message = BackupMessage.UpToDate) }
                    }

                    is BackupOutcome.Failed -> {
                        _backup.update { it.copy(busy = false, error = outcome.failure) }
                    }
                }
            }
        }

        fun refreshBackup() {
            viewModelScope.launch {
                if (backupAccounts.account.first() == null) {
                    _backup.update { it.copy(copies = emptyList(), copiesLoaded = true) }
                    return@launch
                }
                _backup.update { it.copy(busy = true, error = null) }
                when (val outcome = backupManager.copies()) {
                    is CopiesOutcome.Found -> {
                        _backup.update { it.copy(busy = false, copies = outcome.copies, copiesLoaded = true) }
                    }

                    is CopiesOutcome.Failed -> {
                        _backup.update { it.copy(busy = false, error = outcome.failure) }
                    }
                }
            }
        }

        fun restoreBackup(copyId: String) {
            viewModelScope.launch {
                _backup.update { it.copy(busy = true, error = null, message = null) }
                when (val outcome = backupManager.restoreNow(copyId)) {
                    is RestoreOutcome.Restored -> {
                        _backup.update { it.copy(busy = false, message = BackupMessage.Restored(outcome.taskCount)) }
                        refreshBackup()
                    }

                    is RestoreOutcome.Failed -> {
                        _backup.update { it.copy(busy = false, error = outcome.failure) }
                    }
                }
            }
        }

        fun consumeBackupNotice() {
            _backup.update { it.copy(error = null, message = null) }
        }

        private companion object {
            const val PREVIEW_SUBSCRIBE_MILLIS = 5_000L

            const val ACCOUNT_SUBSCRIBE_MILLIS = 5_000L
        }
    }
