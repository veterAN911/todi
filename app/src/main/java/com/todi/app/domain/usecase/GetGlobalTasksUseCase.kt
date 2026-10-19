package com.todi.app.domain.usecase

import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGlobalTasksUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        operator fun invoke(): Flow<List<Task>> = repository.observeGlobalTasks()
    }
