package com.todi.app.presentation.voice

import android.content.Context
import com.todi.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoskModelState {
    data object Absent : VoskModelState

    data class Downloading(
        val progress: Float,
    ) : VoskModelState

    data class Ready(
        val bytes: Long,
    ) : VoskModelState

    data class Failed(
        val reason: VoskModelFailure,
        val partial: Long = 0L,
    ) : VoskModelState
}

enum class VoskModelFailure {
    NETWORK,

    STORAGE,

    ARCHIVE,
}

@Singleton
class VoskModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow<VoskModelState>(VoskModelState.Absent)
        val state: StateFlow<VoskModelState> = _state.asStateFlow()

        private val guard = Mutex()

        @Volatile
        private var installed: File? = null

        private var reported = 0f

        private var totalBytes = 0L

        private val download = ResumableDownload(client = client, url = MODEL_URL)

        fun installedPath(): String? = installed?.absolutePath

        suspend fun refresh() {
            val found = withContext(Dispatchers.IO) { completeModel() }
            installed = found
            _state.value =
                if (found == null) {
                    VoskModelState.Absent
                } else {
                    VoskModelState.Ready(bytes = withContext(Dispatchers.IO) { found.sizeOnDisk() })
                }
        }

        fun download() {
            if (_state.value is VoskModelState.Downloading) return
            scope.launch {
                guard.withLock {
                    val target = modelDir
                    val staging = stagingDir
                    reported = 0f
                    totalBytes = 0L
                    _state.value = VoskModelState.Downloading(0f)
                    try {
                        withContext(Dispatchers.IO) {
                            staging.deleteRecursively()
                            staging.mkdirs()
                            fetch(archive = archiveFile, into = staging)
                            if (completeModel(staging) == null) throw ZipFailure()

                            target.deleteRecursively()
                            if (!staging.renameTo(target)) throw IOException("rename failed")

                            archiveFile.delete()
                        }
                        refresh()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: ZonedOut) {
                        fail(VoskModelFailure.STORAGE, staging, keepArchive = false)
                    } catch (_: ZipFailure) {
                        fail(VoskModelFailure.ARCHIVE, staging, keepArchive = false)
                    } catch (_: IOException) {
                        fail(VoskModelFailure.NETWORK, staging, keepArchive = true)
                    } catch (_: SecurityException) {
                        fail(VoskModelFailure.STORAGE, staging, keepArchive = false)
                    }
                }
            }
        }

        suspend fun remove() {
            withContext(Dispatchers.IO) {
                modelDir.deleteRecursively()
                stagingDir.deleteRecursively()
                archiveFile.delete()
            }
            refresh()
        }

        private suspend fun fail(
            reason: VoskModelFailure,
            staging: File,
            keepArchive: Boolean,
        ) {
            val partial =
                withContext(Dispatchers.IO) {
                    staging.deleteRecursively()
                    if (!keepArchive) archiveFile.delete()
                    archiveFile.length().coerceAtLeast(0L)
                }
            _state.value = VoskModelState.Failed(reason, partial = partial)
        }

        private suspend fun fetch(
            archive: File,
            into: File,
        ) {
            if (into.usableSpace < MIN_FREE_BYTES) throw ZonedOut()
            download.into(archive) { written, total -> report(written, total) }

            val expected = totalBytes
            if (expected > 0 && archive.length() < expected) {
                throw IOException("archive is ${archive.length()} of $expected bytes")
            }
            unpack(archive, into)
        }

        private fun report(
            written: Long,
            total: Long,
        ) {
            if (total > 0) totalBytes = total
            val known = totalBytes
            if (known <= 0) return
            val progress = (written.toFloat() / known).coerceIn(0f, 1f)

            if (progress - reported >= PROGRESS_STEP || progress >= 1f) {
                reported = progress
                _state.value = VoskModelState.Downloading(progress)
            }
        }

        private fun unpack(
            archive: File,
            into: File,
        ) {
            val root = into.canonicalFile
            ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    val name = entry.name.substringAfter('/', "")
                    if (name.isEmpty()) continue
                    val file = File(root, name)

                    if (!file.canonicalPath.startsWith(root.path + File.separator)) {
                        throw ZipFailure()
                    }
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zip.copyTo(it, COPY_BUFFER) }
                    }
                    zip.closeEntry()
                }
            }
        }

        private fun completeModel(dir: File = modelDir): File? {
            val required = REQUIRED_ENTRIES.all { File(dir, it).isFile }
            return if (required) dir else null
        }

        private fun File.sizeOnDisk(): Long = walkBottomUp().filter { it.isFile }.sumOf { it.length() }

        private val modelDir: File get() = File(context.filesDir, MODEL_FOLDER)
        private val stagingDir: File get() = File(context.filesDir, STAGING_FOLDER)

        private val archiveFile: File get() = File(context.filesDir, ARCHIVE_FILE)

        private class ZonedOut : IOException()

        private class ZipFailure : IOException()

        private companion object {
            const val MODEL_URL = "https://app-todi.ru/vosk-model-small-ru-0.22.zip"

            const val MODEL_FOLDER = "vosk-model"
            const val STAGING_FOLDER = "vosk-model-incoming"

            const val ARCHIVE_FILE = "vosk-model-incoming.zip.part"

            val REQUIRED_ENTRIES =
                listOf(
                    "am/final.mdl",
                    "conf/mfcc.conf",
                    "graph/HCLr.fst",
                    "ivector/final.ie",
                )

            const val MIN_FREE_BYTES = 250L * 1024 * 1024

            const val COPY_BUFFER = 64 * 1024

            const val PROGRESS_STEP = 0.01f
        }
    }
