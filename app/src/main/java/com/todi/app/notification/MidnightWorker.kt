package com.todi.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.todi.app.domain.usecase.CompletedArchiveCleanup
import com.todi.app.domain.usecase.DailyRollover
import com.todi.app.domain.usecase.TombstoneCleanup
import com.todi.app.domain.util.TodayProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MidnightWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val todayProvider: TodayProvider,
        private val scheduler: MidnightWorkScheduler,
        private val dailyRollover: DailyRollover,
        private val archiveCleanup: CompletedArchiveCleanup,
        private val tombstoneCleanup: TombstoneCleanup,
        private val globalReminderScheduler: GlobalReminderScheduler,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            todayProvider.refresh()
            dailyRollover.perform()

            archiveCleanup.perform()

            tombstoneCleanup.perform()
            scheduler.schedule()

            globalReminderScheduler.reschedule()
            return Result.success()
        }
    }
