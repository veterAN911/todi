package com.todi.app.backup

import com.todi.app.di.ApplicationScope
import com.todi.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupScheduleObserver
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val scheduler: BackupWorkScheduler,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            applicationScope.launch {
                settingsRepository.settings
                    .map { it.backupEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) scheduler.schedule() else scheduler.cancel()
                    }
            }
        }
    }
