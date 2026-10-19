package com.todi.app.domain.usecase

import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.TodayProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class GetTodayTasksUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
        private val todayProvider: TodayProvider,
    ) {
        operator fun invoke(): Flow<List<Task>> = todayProvider.today.flatMapLatest { repository.observeTasksForDate(it) }
    }
