package com.todi.app.notification

import com.todi.app.di.ApplicationScope
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.usecase.GetGlobalTasksUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalReminderObserver
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val getGlobalTasks: GetGlobalTasksUseCase,
        private val scheduler: GlobalReminderScheduler,
        private val notificationManager: TaskNotificationManager,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            applicationScope.launch {
                combine(
                    settingsRepository.settings,
                    getGlobalTasks(),
                    ::Pair,
                ).distinctUntilChanged { old, new ->
                    old.first.globalReminderEnabled == new.first.globalReminderEnabled &&
                        old.first.globalReminderStartMinutes == new.first.globalReminderStartMinutes &&
                        old.first.globalReminderIntervalMinutes == new.first.globalReminderIntervalMinutes &&
                        old.first.globalReminderQuietAfterMinutes == new.first.globalReminderQuietAfterMinutes &&
                        old.second.isEmpty() == new.second.isEmpty()
                }.collect { (settings, tasks) ->
                    if (!settings.globalReminderEnabled) {
                        scheduler.cancel()
                        return@collect
                    }
                    if (tasks.isEmpty()) notificationManager.cancelReminder()
                    scheduler.reschedule()
                }
            }
        }
    }
