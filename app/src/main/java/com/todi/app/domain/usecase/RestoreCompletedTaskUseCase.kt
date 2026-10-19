package com.todi.app.domain.usecase

import com.todi.app.domain.repository.TaskRepository
import java.time.LocalDate
import javax.inject.Inject

class RestoreCompletedTaskUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        suspend operator fun invoke(
            id: Long,
            date: LocalDate,
        ) = repository.restoreCompletedTask(id, date)
    }
