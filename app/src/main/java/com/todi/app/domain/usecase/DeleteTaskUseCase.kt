package com.todi.app.domain.usecase

import com.todi.app.domain.repository.TaskRepository
import javax.inject.Inject

class DeleteTaskUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        suspend operator fun invoke(id: Long) = repository.deleteTask(id)
    }
