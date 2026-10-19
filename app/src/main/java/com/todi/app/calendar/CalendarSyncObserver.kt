package com.todi.app.calendar

import android.util.Log
import com.todi.app.di.ApplicationScope
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncObserver
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val taskRepository: TaskRepository,
        private val calendarSyncManager: CalendarSyncManager,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val syncTick = MutableStateFlow(0L)

        fun requestSync() {
            syncTick.update { it + 1 }
        }

        fun start() {
            Log.d(TAG, "observer start")
            applicationScope.launch {
                combine(
                    settingsRepository.settings,
                    taskRepository.observeAllTasks(),
                    syncTick,
                ) { settings, tasks, _ -> settings to tasks }
                    .debounce(DEBOUNCE_MILLIS)
                    .collect { (settings, tasks) ->
                        Log.d(TAG, "collect: enabled=${settings.calendarSyncEnabled}, tasks=${tasks.size}")
                        if (!settings.calendarSyncEnabled) return@collect

                        runCatching { calendarSyncManager.reconcile(tasks) }
                    }
            }
        }

        private companion object {
            const val TAG = "CalendarSync"
            const val DEBOUNCE_MILLIS = 400L
        }
    }
