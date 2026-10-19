package com.todi.app.domain.usecase

import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompletedArchiveCleanup
    @Inject
    constructor(
        private val repository: TaskRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) {
        suspend fun perform(): Int {
            val months = settingsRepository.settings.first().completedRetentionMonths
            if (months <= 0) return 0
            val cutoff = ZonedDateTime.now(clock).minusMonths(months.toLong()).toInstant()
            return repository.deleteCompleted(cutoff)
        }

        suspend fun clearAll(): Int = repository.deleteCompleted(null)
    }
