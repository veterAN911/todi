package com.todi.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.todi.app.backup.BackupScheduleObserver
import com.todi.app.calendar.CalendarSyncObserver
import com.todi.app.di.ApplicationScope
import com.todi.app.domain.usecase.CompletedArchiveCleanup
import com.todi.app.domain.usecase.DailyRollover
import com.todi.app.notification.GlobalReminderObserver
import com.todi.app.notification.MidnightWorkScheduler
import com.todi.app.notification.NotificationUpdateObserver
import com.todi.app.notification.TaskNotificationManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TodiApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var taskNotificationManager: TaskNotificationManager

    @Inject lateinit var notificationUpdateObserver: NotificationUpdateObserver

    @Inject lateinit var midnightScheduler: MidnightWorkScheduler

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var dailyRollover: DailyRollover

    @Inject lateinit var archiveCleanup: CompletedArchiveCleanup

    @Inject lateinit var calendarSyncObserver: CalendarSyncObserver

    @Inject lateinit var globalReminderObserver: GlobalReminderObserver

    @Inject lateinit var backupScheduleObserver: BackupScheduleObserver

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        taskNotificationManager.ensureChannel()
        notificationUpdateObserver.start()
        calendarSyncObserver.start()
        globalReminderObserver.start()
        backupScheduleObserver.start()
        midnightScheduler.schedule()

        applicationScope.launch {
            dailyRollover.perform()
            archiveCleanup.perform()
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
