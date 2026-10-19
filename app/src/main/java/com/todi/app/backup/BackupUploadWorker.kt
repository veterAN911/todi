package com.todi.app.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.todi.app.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock

@HiltWorker
class BackupUploadWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val manager: BackupManager,
        private val settings: SettingsRepository,
        private val clock: Clock,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val current = settings.settings.first()

            if (!BackupPolicy.shouldUpload(current.backupEnabled, current.backupLastUploadAt, clock.instant())) {
                return Result.success()
            }

            return when (val outcome = manager.backupNow()) {
                is BackupOutcome.Uploaded, BackupOutcome.UpToDate -> {
                    Result.success()
                }

                is BackupOutcome.Failed -> {
                    if (outcome.failure.kind.isWorthRetrying) Result.retry() else Result.success()
                }
            }
        }
    }
