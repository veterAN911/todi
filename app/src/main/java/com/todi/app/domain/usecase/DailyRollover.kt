package com.todi.app.domain.usecase

import com.todi.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyRollover
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val carryOverIncompleteTasks: CarryOverIncompleteTasksUseCase,
    ) {
        suspend fun perform() {
            if (!settingsRepository.settings.first().autoCarryOver) return
            carryOverIncompleteTasks()
        }
    }
