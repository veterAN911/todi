package com.todi.app.domain.usecase

import com.todi.app.domain.repository.TaskRepository
import java.time.LocalDate
import javax.inject.Inject

class MoveTaskToDateUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        suspend operator fun invoke(
            id: Long,
            date: LocalDate,
        ) {
            repository.moveTask(id, date)
        }
    }
