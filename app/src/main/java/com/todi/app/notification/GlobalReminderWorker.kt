package com.todi.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.usecase.GetGlobalTasksUseCase
import com.todi.app.domain.util.ReminderSlots
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime

@HiltWorker
class GlobalReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val settingsRepository: SettingsRepository,
        private val getGlobalTasks: GetGlobalTasksUseCase,
        private val notificationManager: TaskNotificationManager,
        private val scheduler: GlobalReminderScheduler,
        private val clock: Clock,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val settings = settingsRepository.settings.first()
            if (!settings.globalReminderEnabled) {
                scheduler.cancel()
                return Result.success()
            }

            val tasks = getGlobalTasks().first()
            if (tasks.isEmpty()) {
                notificationManager.cancelReminder()
            } else {
                val presentation = NotificationPresentation.from(settings)

                val content = NotificationContentBuilder.buildReminder(tasks, detail = presentation.detail)
                val quietMoment =
                    ReminderSlots.nextQuietMoment(
                        now = ZonedDateTime.now(clock),
                        quietAfter = settings.globalReminderQuietAfter,
                    )
                val timeout = Duration.between(ZonedDateTime.now(clock), quietMoment).toMillis()
                notificationManager.updateReminder(
                    content = content,
                    timeoutMillis = timeout.coerceAtLeast(0),
                    presentation = presentation,
                )
            }

            scheduler.reschedule()
            return Result.success()
        }
    }
