package com.todi.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.util.ReminderSlots
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val notificationManager: TaskNotificationManager,
        private val clock: Clock,
    ) {
        suspend fun reschedule() {
            val settings = settingsRepository.settings.first()
            if (!settings.globalReminderEnabled) {
                cancel()
                return
            }
            val now = ZonedDateTime.now(clock)
            val slot =
                ReminderSlots.next(
                    now = now,
                    start = settings.globalReminderStart,
                    interval = settings.globalReminderInterval,
                    quietAfter = settings.globalReminderQuietAfter,
                )
            val delay = Duration.between(now, slot).toMillis().coerceAtLeast(0)

            val request =
                OneTimeWorkRequestBuilder<GlobalReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
            notificationManager.cancelReminder()
        }

        companion object {
            const val UNIQUE_NAME = "global_reminder"
        }
    }
