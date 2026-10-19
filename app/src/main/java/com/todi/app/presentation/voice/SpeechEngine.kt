package com.todi.app.presentation.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface SpeechEvent {
    data class Partial(
        val text: String,
    ) : SpeechEvent

    data class Final(
        val text: String,
    ) : SpeechEvent

    data class Failed(
        val reason: SpeechFailure,
    ) : SpeechEvent
}

enum class SpeechFailure {
    NO_MATCH,

    AUDIO,

    PERMISSION,
    BUSY,

    MODEL_MISSING,
    UNKNOWN,
}

class SpeechSession(
    val events: Flow<SpeechEvent>,
    val level: StateFlow<Float>,
)

fun interface SpeechEngineFactory {
    fun create(): SpeechSession
}

interface VoiceEnvironment {
    val modelState: StateFlow<VoskModelState>

    suspend fun hasModel(): Boolean

    fun downloadModel()

    fun hasMicrophonePermission(): Boolean

    fun languageTag(): String
}
