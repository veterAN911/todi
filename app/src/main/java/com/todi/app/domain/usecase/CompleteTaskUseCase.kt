package com.todi.app.domain.usecase

import com.todi.app.domain.repository.TaskRepository
import javax.inject.Inject

class CompleteTaskUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        suspend operator fun invoke(
            id: Long,
            completed: Boolean,
        ) = repository.setCompleted(id, completed)
    }
