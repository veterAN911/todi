package com.todi.app.backup

import android.util.Log
import com.todi.app.domain.repository.BackupAccountRepository
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.SnapshotDecode
import com.todi.app.domain.util.TaskSnapshotCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupOutcome {
    data class Uploaded(
        val at: Instant,
    ) : BackupOutcome

    data object UpToDate : BackupOutcome

    data class Failed(
        val failure: BackupFailure,
    ) : BackupOutcome
}

sealed interface RestoreOutcome {
    data class Restored(
        val taskCount: Int,
    ) : RestoreOutcome

    data class Failed(
        val failure: BackupFailure,
    ) : RestoreOutcome
}

sealed interface CopiesOutcome {
    data class Found(
        val copies: List<RemoteCopy>,
    ) : CopiesOutcome

    data class Failed(
        val failure: BackupFailure,
    ) : CopiesOutcome
}

@Singleton
class BackupManager
    @Inject
    constructor(
        private val transport: BackupTransport,
        private val tasks: TaskRepository,
        private val settings: SettingsRepository,
        private val accounts: BackupAccountRepository,
        private val clock: Clock,
    ) {
        private val mutex = Mutex()

        private val _lastError = MutableStateFlow<BackupFailure?>(null)

        val lastError: StateFlow<BackupFailure?> = _lastError.asStateFlow()

        private val _busy = MutableStateFlow(false)

        val busy: StateFlow<Boolean> = _busy.asStateFlow()

        suspend fun copies(): CopiesOutcome =
            mutex.withLock {
                _busy.value = true
                try {
                    val account = accounts.account.first() ?: return@withLock CopiesOutcome.Failed(record(NO_ACCOUNT))
                    val server = serverUrl
                    _lastError.value = null
                    CopiesOutcome.Found(transport.list(server, account.token))
                } catch (e: BackupException) {
                    CopiesOutcome.Failed(record(e.toBackupFailure()))
                } catch (e: Exception) {
                    CopiesOutcome.Failed(record(unexpected(e)))
                } finally {
                    _busy.value = false
                }
            }

        suspend fun backupNow(): BackupOutcome =
            mutex.withLock {
                _busy.value = true
                try {
                    val account = accounts.account.first() ?: return@withLock BackupOutcome.Failed(record(NO_ACCOUNT))
                    val server = serverUrl
                    val local = tasks.observeAllTasks().first()

                    val live = BackupPolicy.liveCount(local)

                    val refusal =
                        if (live == 0) {
                            BackupPolicy.uploadGuard(live, remoteSnapshot(server, account.token))
                        } else {
                            null
                        }
                    if (refusal != null) return@withLock BackupOutcome.Failed(record(BackupFailure(refusal)))

                    val digest = TaskSnapshotCodec.contentDigest(local)
                    if (digest == accounts.lastUploadedDigest()) {
                        _lastError.value = null
                        return@withLock BackupOutcome.UpToDate
                    }

                    transport.upload(
                        server = server,
                        token = account.token,
                        snapshot = TaskSnapshotCodec.encode(local, clock.instant()),
                        taskCount = live,
                    )
                    accounts.rememberUploadedDigest(digest)
                    val at = clock.instant()
                    settings.setBackupLastUploadAt(at.toEpochMilli())
                    _lastError.value = null
                    BackupOutcome.Uploaded(at)
                } catch (e: BackupException) {
                    BackupOutcome.Failed(record(e.toBackupFailure()))
                } catch (e: Exception) {
                    BackupOutcome.Failed(record(unexpected(e)))
                } finally {
                    _busy.value = false
                }
            }

        suspend fun restoreNow(copyId: String): RestoreOutcome =
            mutex.withLock {
                _busy.value = true
                try {
                    val account = accounts.account.first() ?: return@withLock RestoreOutcome.Failed(record(NO_ACCOUNT))
                    val server = serverUrl

                    val decoded =
                        when (val snapshot = TaskSnapshotCodec.decode(transport.download(server, account.token, copyId))) {
                            SnapshotDecode.Unreadable -> return@withLock RestoreOutcome.Failed(record(BAD_COPY))
                            is SnapshotDecode.TooNew -> return@withLock RestoreOutcome.Failed(record(TOO_NEW_COPY))
                            is SnapshotDecode.Restored -> snapshot.tasks
                        }

                    val local = tasks.observeAllTasks().first()

                    if (BackupPolicy.liveCount(local) > 0) {
                        try {
                            transport.upload(
                                server = server,
                                token = account.token,
                                snapshot = TaskSnapshotCodec.encode(local, clock.instant()),
                                taskCount = BackupPolicy.liveCount(local),
                            )
                        } catch (e: BackupException) {
                            return@withLock RestoreOutcome.Failed(record(e.toBackupFailure()))
                        }
                    }

                    tasks.replaceAll(decoded)
                    settings.setBackupLastUploadAt(clock.instant().toEpochMilli())
                    _lastError.value = null
                    RestoreOutcome.Restored(taskCount = decoded.size)
                } catch (e: BackupException) {
                    RestoreOutcome.Failed(record(e.toBackupFailure()))
                } catch (e: Exception) {
                    RestoreOutcome.Failed(record(unexpected(e)))
                } finally {
                    _busy.value = false
                }
            }

        fun clearError() {
            _lastError.value = null
        }

        private val serverUrl: String = BackupServer.url

        private suspend fun remoteSnapshot(
            server: String,
            token: String,
        ): SnapshotDecode? {
            val newest = transport.list(server, token).maxByOrNull(RemoteCopy::savedAt) ?: return null
            return TaskSnapshotCodec.decode(transport.download(server, token, newest.id))
        }

        private fun record(failure: BackupFailure): BackupFailure {
            _lastError.value = failure
            return failure
        }

        private fun unexpected(e: Exception): BackupFailure {
            Log.w(TAG, "backup call failed: ${e.javaClass.simpleName}")
            return BackupFailure(BackupFailureKind.FAILED, message = e::class.simpleName)
        }

        private companion object {
            const val TAG = "Backup"

            val NO_ACCOUNT = BackupFailure(BackupFailureKind.NOT_SIGNED_IN)
            val BAD_COPY = BackupFailure(BackupFailureKind.UNREADABLE)
            val TOO_NEW_COPY = BackupFailure(BackupFailureKind.TOO_NEW)
        }
    }
