package com.todi.app.domain.usecase

import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.TodayProvider
import javax.inject.Inject

class MoveTaskToTomorrowUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
        private val todayProvider: TodayProvider,
    ) {
        suspend operator fun invoke(id: Long) {
            repository.moveTask(id, todayProvider.today.value.plusDays(1))
        }
    }
