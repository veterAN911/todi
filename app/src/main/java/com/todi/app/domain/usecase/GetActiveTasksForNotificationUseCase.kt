package com.todi.app.domain.usecase

import com.todi.app.domain.model.NotificationTaskSet
import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.TodayProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class GetActiveTasksForNotificationUseCase
    @Inject
    constructor(
        private val repository: TaskRepository,
        private val settingsRepository: SettingsRepository,
        private val todayProvider: TodayProvider,
    ) {
        operator fun invoke(): Flow<NotificationTaskSet> =
            combine(settingsRepository.settings, todayProvider.today, ::Pair)
                .flatMapLatest { (settings, today) ->
                    val scope = settings.notificationScope
                    val todayTasks = repository.observeActiveTasksForDate(today)

                    val overdueTasks =
                        if (scope.includesOverdue) repository.observeOverdueTasks(today) else EMPTY
                    val dateLessTasks =
                        if (scope.includesDateLess) repository.observeGlobalTasks() else EMPTY

                    combine(todayTasks, overdueTasks, dateLessTasks) { tasks, overdue, dateLess ->
                        if (scope.includesOverdue) {
                            val (carried, fresh) = tasks.partition { it.carriedFrom != null }
                            NotificationTaskSet(today = fresh, overdue = overdue + carried, dateLess = dateLess)
                        } else {
                            NotificationTaskSet(today = tasks, dateLess = dateLess)
                        }
                    }
                }

        private companion object {
            val EMPTY: Flow<List<Task>> = flowOf(emptyList())
        }
    }
