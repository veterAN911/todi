package com.todi.app.domain.usecase

import com.todi.app.domain.repository.TaskRepository
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TombstoneCleanup
    @Inject
    constructor(
        private val repository: TaskRepository,
        private val clock: Clock,
    ) {
        suspend fun perform(): Int = repository.purgeTombstones(clock.instant().minus(TOMBSTONE_TTL))

        private companion object {
            val TOMBSTONE_TTL: Duration = Duration.ofDays(90)
        }
    }
