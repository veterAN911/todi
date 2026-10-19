package com.todi.app.presentation.voice

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.todi.app.di.ApplicationScope
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.SaveVoiceDraftsUseCase
import com.todi.app.domain.util.ParsedTask
import com.todi.app.domain.util.TaskPhraseParser
import com.todi.app.domain.util.VoiceDraftGroup
import com.todi.app.domain.util.VoiceDraftGroups
import com.todi.app.domain.util.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceState {
    data object Idle : VoiceState

    data class Armed(
        val dragProgress: Float,
        val hint: Boolean = false,
    ) : VoiceState

    data object NeedsPermission : VoiceState

    data object NeedsModel : VoiceState

    data class Listening(
        val language: VoiceLanguage,
        val partial: String?,
    ) : VoiceState

    data class Review(
        val language: VoiceLanguage,
        val transcript: String,
        val drafts: List<ParsedTask>,
        val today: LocalDate,
        val targetDate: LocalDate? = null,
        val editing: Boolean = false,
        val lastSave: VoiceSave? = null,
    ) : VoiceState

    data class Failed(
        val reason: VoiceFailure,
    ) : VoiceState
}

val VoiceState.isRevealed: Boolean
    get() =
        when (this) {
            is VoiceState.Armed -> dragProgress > 0f
            VoiceState.Idle -> false
            else -> true
        }

data class VoiceSave(
    val taskIds: List<Long>,
    val drafts: List<ParsedTask>,
) {
    val count: Int get() = taskIds.size
}

enum class VoiceFailure {
    PERMISSION,
    NO_SPEECH,
    RECOGNIZER,
}

@Singleton
class VoiceInputController
    @Inject
    constructor(
        private val environment: VoiceEnvironment,
        private val engines: SpeechEngineFactory,
        private val saveDrafts: SaveVoiceDraftsUseCase,
        private val deleteTask: DeleteTaskUseCase,
        private val settings: SettingsRepository,
        private val clock: Clock,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
        val state: StateFlow<VoiceState> = _state.asStateFlow()

        private val _level = MutableStateFlow(0f)
        val level: StateFlow<Float> = _level.asStateFlow()

        val enabled: StateFlow<Boolean> =
            settings.settings
                .map { it.voiceInputEnabled }
                .stateIn(scope, SharingStarted.Eagerly, true)

        val modelState: StateFlow<VoskModelState> = environment.modelState

        private var session: Job? = null

        private var sessionTargetDate: LocalDate? = null

        fun arm(targetDate: LocalDate?) {
            if (!enabled.value) return
            sessionTargetDate = targetDate
            _state.value = VoiceState.Armed(dragProgress = 0f)
        }

        fun showHint() {
            val current = _state.value as? VoiceState.Armed ?: return
            _state.value = current.copy(hint = true)
        }

        fun pull(progress: Float) {
            val current = _state.value as? VoiceState.Armed ?: return

            if (current.dragProgress >= 1f) return
            val moved = progress.coerceIn(0f, 1f)
            _state.value =
                current.copy(
                    dragProgress = moved,
                    hint = current.hint || moved > 0f,
                )
        }

        fun downloadModel() {
            environment.downloadModel()
        }

        fun trigger() {
            _state.value = VoiceState.Armed(dragProgress = 1f)
            start()
        }

        fun cancel() {
            session?.cancel()
            session = null
            _level.value = 0f
            _state.value = VoiceState.Idle
        }

        fun onPermissionResult(granted: Boolean) {
            if (granted) {
                start()
            } else {
                _state.value = VoiceState.Failed(VoiceFailure.PERMISSION)
            }
        }

        fun retry() {
            start()
        }

        fun saveGroup(date: LocalDate?) {
            val review = _state.value as? VoiceState.Review ?: return
            val group = review.group(date) ?: return
            persist(listOf(group))
        }

        fun saveAll() {
            val review = _state.value as? VoiceState.Review ?: return
            val groups = VoiceDraftGroups.of(review.drafts, review.targetDate, review.today)
            if (groups.isEmpty()) return
            persist(groups)
        }

        fun dropGroup(date: LocalDate?) {
            val review = _state.value as? VoiceState.Review ?: return
            val group = review.group(date) ?: return
            _state.value =
                review.copy(
                    drafts = review.drafts - group.drafts.toSet(),
                    lastSave = null,
                )
        }

        fun undoSave() {
            val review = _state.value as? VoiceState.Review ?: return
            val save = review.lastSave ?: return
            _state.value =
                review.copy(
                    drafts = review.drafts + save.drafts,
                    lastSave = null,
                )
            scope.launch { save.taskIds.forEach { deleteTask(it) } }
        }

        fun startEditing() {
            val review = _state.value as? VoiceState.Review ?: return
            _state.value = review.copy(editing = true, lastSave = null)
        }

        fun editTranscript(text: String) {
            val review = _state.value as? VoiceState.Review ?: return
            _state.value =
                review.copy(
                    transcript = text,
                    drafts = TaskPhraseParser.parse(text, ZonedDateTime.now(clock), review.language),
                    lastSave = null,
                )
        }

        fun finishEditing() {
            val review = _state.value as? VoiceState.Review ?: return
            _state.value = review.copy(editing = false)
        }

        private fun VoiceState.Review.group(date: LocalDate?): VoiceDraftGroup? =
            VoiceDraftGroups.of(drafts, targetDate, today).firstOrNull { it.date == date }

        private fun persist(groups: List<VoiceDraftGroup>) {
            val saved = groups.flatMap { it.drafts }
            scope.launch {
                val created = groups.flatMap { group -> saveDrafts(group.drafts, group.date) }
                val current = _state.value as? VoiceState.Review ?: return@launch
                _state.value =
                    current.copy(
                        drafts = current.drafts - saved.toSet(),
                        editing = false,
                        lastSave = VoiceSave(taskIds = created.map { it.id }, drafts = saved),
                    )
            }
        }

        private fun start() {
            if (!enabled.value) return
            session?.cancel()
            session = scope.launch { runSession() }
        }

        private suspend fun CoroutineScope.runSession() {
            if (!environment.hasModel()) {
                _state.value = VoiceState.NeedsModel
                return
            }
            if (!environment.hasMicrophonePermission()) {
                _state.value = VoiceState.NeedsPermission
                return
            }

            val language = VoiceLanguage.of(environment.languageTag())
            val speech = engines.create()
            _state.value = VoiceState.Listening(language = language, partial = null)

            val levels = launch { speech.level.collect { _level.value = it } }
            try {
                withTimeout(SESSION_TIMEOUT_MILLIS) {
                    speech.events.collect { event -> handle(event, language) }
                }
            } catch (_: TimeoutCancellationException) {
                if (_state.value is VoiceState.Listening) {
                    _state.value = VoiceState.Failed(VoiceFailure.NO_SPEECH)
                }
            } finally {
                levels.cancel()
                _level.value = 0f
            }
        }

        private fun handle(
            event: SpeechEvent,
            language: VoiceLanguage,
        ) {
            when (event) {
                is SpeechEvent.Partial -> {
                    val current = _state.value
                    if (current is VoiceState.Listening) {
                        _state.value = current.copy(partial = event.text)
                    }
                }

                is SpeechEvent.Final -> {
                    val now = ZonedDateTime.now(clock)
                    _state.value =
                        VoiceState.Review(
                            language = language,
                            transcript = event.text,
                            drafts = TaskPhraseParser.parse(event.text, now, language),
                            targetDate = sessionTargetDate,
                            today = now.toLocalDate(),
                        )
                }

                is SpeechEvent.Failed -> {
                    _state.value = event.reason.toVoiceState()
                }
            }
        }

        private fun SpeechFailure.toVoiceState(): VoiceState =
            when (this) {
                SpeechFailure.MODEL_MISSING -> VoiceState.NeedsModel
                else -> VoiceState.Failed(toVoiceFailure())
            }

        private fun SpeechFailure.toVoiceFailure(): VoiceFailure =
            when (this) {
                SpeechFailure.NO_MATCH -> VoiceFailure.NO_SPEECH
                SpeechFailure.PERMISSION -> VoiceFailure.PERMISSION
                else -> VoiceFailure.RECOGNIZER
            }

        private companion object {
            const val SESSION_TIMEOUT_MILLIS = 60_000L
        }
    }

@Singleton
class VoiceGestureConfig
    @Inject
    constructor() {
        val holdMillis: Long = 1_000L
        val dragThreshold: Dp = 96.dp
        val hintDelayMillis: Long = 1_000L
    }

val LocalVoiceInput =
    staticCompositionLocalOf<VoiceInputController> {
        error("VoiceInputController is provided in MainActivity")
    }

val LocalVoiceGestureConfig = staticCompositionLocalOf { VoiceGestureConfig() }
