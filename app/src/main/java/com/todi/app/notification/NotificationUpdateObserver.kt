package com.todi.app.notification

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import com.todi.app.di.ApplicationScope
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.usecase.GetActiveTasksForNotificationUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationUpdateObserver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val getActiveTasks: GetActiveTasksForNotificationUseCase,
        private val manager: TaskNotificationManager,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val repaint = MutableStateFlow(0)

        fun start() {
            context.registerComponentCallbacks(
                object : ComponentCallbacks {
                    override fun onConfigurationChanged(newConfig: Configuration) {
                        repaint.value += 1
                    }

                    override fun onLowMemory() = Unit
                },
            )

            applicationScope.launch {
                combine(
                    settingsRepository.settings,
                    getActiveTasks(),
                    repaint,
                ) { settings, tasks, _ -> settings to tasks }
                    .collect { (settings, tasks) ->
                        if (!settings.notificationsEnabled || !manager.canPost()) {
                            manager.cancel()
                            return@collect
                        }
                        val presentation = NotificationPresentation.from(settings)
                        val content =
                            NotificationContentBuilder.build(
                                todayTasks = tasks.today,
                                overdueTasks = tasks.overdue,
                                dateLessTasks = tasks.dateLess,
                                overdueHeader = manager.localizedOverdueHeader(),
                                dateLessHeader = manager.localizedDateLessHeader(),
                                detail = presentation.detail,
                            )
                        if (content.count == 0) {
                            manager.cancel()
                        } else {
                            manager.update(content, presentation)
                        }
                    }
            }
        }
    }
