package com.todi.app.presentation.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class VoskSpeechEngineFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val models: VoskModelManager,
    ) : SpeechEngineFactory {
        override fun create(): SpeechSession {
            val level = MutableStateFlow(0f)

            val events =
                channelFlow {
                    val outgoing = this

                    val path = models.installedPath()
                    if (path == null) {
                        outgoing.send(SpeechEvent.Failed(SpeechFailure.MODEL_MISSING))
                        return@channelFlow
                    }

                    val session =
                        try {
                            withContext(Dispatchers.IO) { open(path) }
                        } catch (_: PermissionMissing) {
                            outgoing.send(SpeechEvent.Failed(SpeechFailure.PERMISSION))
                            return@channelFlow
                        } catch (_: IOException) {
                            null
                        } catch (_: UnsatisfiedLinkError) {
                            null
                        }
                    if (session == null) {
                        outgoing.send(SpeechEvent.Failed(SpeechFailure.UNKNOWN))
                        return@channelFlow
                    }

                    try {
                        withContext(Dispatchers.IO) { readIncoming(session, level, outgoing) }
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) { session.close() }
                    }
                }

            return SpeechSession(events = events, level = level.asStateFlow())
        }

        private suspend fun readIncoming(
            session: Session,
            level: MutableStateFlow<Float>,
            outgoing: ProducerScope<SpeechEvent>,
        ) {
            val buffer = ShortArray(READ_SAMPLES)
            var spokenSoFar = ""

            while (outgoing.isActive) {
                val read = session.recorder.read(buffer, 0, buffer.size)
                if (read < 0) {
                    outgoing.send(SpeechEvent.Failed(SpeechFailure.AUDIO))
                    return
                }
                if (read == 0) continue

                level.value = loudness(buffer, read)
                val ended = session.recognizer.acceptWaveForm(buffer, read)

                val partial = text(session.recognizer.partialResult, PARTIAL)
                if (partial.isNotBlank() && partial != spokenSoFar) {
                    spokenSoFar = partial
                    outgoing.send(SpeechEvent.Partial(partial))
                }

                if (ended) {
                    val phrase = text(session.recognizer.result, TEXT)
                    outgoing.send(
                        if (phrase.isBlank()) {
                            SpeechEvent.Failed(SpeechFailure.NO_MATCH)
                        } else {
                            SpeechEvent.Final(phrase)
                        },
                    )
                    return
                }
            }
        }

        private fun open(path: String): Session {
            LibVosk.setLogLevel(LogLevel.WARNINGS)

            val model = Model(path)
            val recorder =
                try {
                    openRecorder()
                } catch (failure: Throwable) {
                    model.close()
                    throw failure
                }
            return try {
                Session(
                    model,
                    recorder,
                    Recognizer(model, SAMPLE_RATE.toFloat()).apply {
                        setEndpointerMode(Recognizer.EndpointerMode.LONG)
                    },
                )
            } catch (failure: Throwable) {
                recorder.release()
                model.close()
                throw failure
            }
        }

        private fun openRecorder(): AudioRecord {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                throw PermissionMissing()
            }

            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            val recorder =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL,
                    ENCODING,
                    maxOf(minimum, READ_SAMPLES * BYTES_PER_SAMPLE * 2),
                )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                throw IOException("the microphone did not open")
            }
            recorder.startRecording()
            return recorder
        }

        private class PermissionMissing : IOException("RECORD_AUDIO is not granted")

        private class Session(
            private val model: Model,
            val recorder: AudioRecord,
            val recognizer: Recognizer,
        ) : AutoCloseable {
            override fun close() {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { recognizer.close() }
                runCatching { model.close() }
            }
        }

        private companion object {
            fun text(
                json: String,
                field: String,
            ): String = runCatching { JSONObject(json).optString(field) }.getOrDefault("").trim()

            fun loudness(
                buffer: ShortArray,
                read: Int,
            ): Float {
                var power = 0.0
                for (index in 0 until read) {
                    val sample = buffer[index].toDouble()
                    power += sample * sample
                }
                val rms = sqrt(power / read)
                if (rms <= 0.0) return 0f
                val decibels = 20.0 * log10(rms / FULL_SCALE)
                return ((decibels - LOUDNESS_FLOOR_DB) / LOUDNESS_RANGE_DB).toFloat().coerceIn(0f, 1f)
            }

            const val PARTIAL = "partial"
            const val TEXT = "text"

            const val SAMPLE_RATE = 16_000
            const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
            const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
            const val BYTES_PER_SAMPLE = 2

            const val READ_SAMPLES = SAMPLE_RATE / 10

            const val FULL_SCALE = 32768.0

            const val LOUDNESS_FLOOR_DB = -62.0
            const val LOUDNESS_RANGE_DB = 45.0
        }
    }
