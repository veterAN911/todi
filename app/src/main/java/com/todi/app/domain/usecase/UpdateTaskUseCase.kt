package com.todi.app.domain.usecase

import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import javax.inject.Inject

class UpdateTaskUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        suspend operator fun invoke(task: Task) {
            repository.updateTask(
                task.copy(
                    title = task.title.trim(),
                    note = task.note?.trim()?.ifEmpty { null },
                ),
            )
        }
    }
