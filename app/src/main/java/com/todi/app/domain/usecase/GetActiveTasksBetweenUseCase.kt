package com.todi.app.domain.usecase

import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetActiveTasksBetweenUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
    ) {
        operator fun invoke(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Task>> = repository.observeActiveTasksBetween(from, to)
    }
