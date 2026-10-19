package com.todi.app.domain.usecase

import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import javax.inject.Inject

class RestoreTaskUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        suspend operator fun invoke(task: Task) = repository.restoreTask(task)
    }
