package com.todi.app.presentation.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.todi.app.R
import com.todi.app.domain.util.ParsedTask
import com.todi.app.domain.util.VoiceDraftDay
import com.todi.app.domain.util.VoiceDraftGroup
import com.todi.app.domain.util.VoiceDraftGroups
import com.todi.app.presentation.util.DateFormatters
import java.time.LocalDate

const val VOICE_OVERLAY_TAG = "voiceOverlay"

const val VOICE_ENGINE_TAG = "voiceEngineBadge"

const val VOICE_MODEL_DOWNLOAD_TAG = "voiceModelDownload"

const val VOICE_MESSAGE_TAG = "voiceMessage"

const val VOICE_SAVED_TAG = "voiceSaved"

const val VOICE_EDIT_FIELD_TAG = "voiceEditField"

fun voiceBlockTag(date: LocalDate?): String = "voiceBlock_${date ?: "none"}"

fun voiceBlockSaveTag(date: LocalDate?): String = "voiceBlockSave_${date ?: "none"}"

fun voiceBlockDropTag(date: LocalDate?): String = "voiceBlockDrop_${date ?: "none"}"

@Composable
fun VoiceOverlay(
    state: VoiceState,
    level: State<Float>,
    modelState: VoskModelState,
    onDownloadModel: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenForm: () -> Unit,
    review: VoiceReviewActions = VoiceReviewActions(),
) {
    if (!state.isRevealed) return

    val pulled = (state as? VoiceState.Armed)?.dragProgress ?: 1f
    val revealed by animateFloatAsState(
        targetValue = pulled,
        animationSpec = spring(stiffness = 400f),
        label = "voiceReveal",
    )

    Box(
        Modifier
            .fillMaxSize()
            .testTag(VOICE_OVERLAY_TAG)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.10f + 0.28f * revealed))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        when (state) {
            is VoiceState.Idle -> {
                Unit
            }

            is VoiceState.Armed -> {
                RevealScreen(revealed = revealed, level = level)
            }

            VoiceState.NeedsModel -> {
                NeedsModelScreen(
                    state = modelState,
                    onDownload = onDownloadModel,
                    onCancel = onCancel,
                )
            }

            VoiceState.NeedsPermission -> {
                MessageScreen(
                    message = stringResource(R.string.voice_permission_rationale),
                    primary = stringResource(R.string.voice_allow_microphone),
                    onPrimary = onRetry,
                    onCancel = onCancel,
                )
            }

            is VoiceState.Listening -> {
                ListeningScreen(state = state, level = level, onCancel = onCancel)
            }

            is VoiceState.Review -> {
                ReviewScreen(state = state, onCancel = onCancel, actions = review)
            }

            is VoiceState.Failed -> {
                MessageScreen(
                    message = stringResource(state.reason.messageRes),
                    primary = stringResource(R.string.voice_enter_manually),
                    onPrimary = onOpenForm,
                    onCancel = onCancel,
                )
            }
        }
    }
}

private val VoiceFailure.messageRes: Int
    get() =
        when (this) {
            VoiceFailure.PERMISSION -> R.string.voice_error_permission
            VoiceFailure.NO_SPEECH -> R.string.voice_error_no_speech
            VoiceFailure.RECOGNIZER -> R.string.voice_error_recognizer
        }

@Composable
private fun BoxScope.RevealScreen(
    revealed: Float,
    level: State<Float>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(reveal = revealed, level = level, listening = false)
    }
}

@Composable
private fun BoxScope.NeedsModelScreen(
    state: VoskModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.voice_error_model_missing),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(VOICE_MESSAGE_TAG),
        )
        when (state) {
            is VoskModelState.Downloading -> {
                Text(
                    text =
                        stringResource(
                            R.string.settings_voice_model_downloading,
                            (state.progress * 100).toInt(),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 16.dp),
                )
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                )
            }

            is VoskModelState.Ready -> {
                Text(
                    text = stringResource(R.string.voice_model_ready_pull),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            is VoskModelState.Failed -> {
                Text(
                    text = stringResource(voiceModelFailureText(state.reason)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            is VoskModelState.Absent -> {
                Unit
            }
        }
    }

    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state !is VoskModelState.Downloading && state !is VoskModelState.Ready) {
            val partial = (state as? VoskModelState.Failed)?.partial ?: 0L
            TextButton(
                onClick = onDownload,
                modifier = Modifier.testTag(VOICE_MODEL_DOWNLOAD_TAG),
            ) {
                Text(
                    text =
                        if (partial > 0) {
                            stringResource(R.string.settings_voice_model_resume, modelMegabytes(partial))
                        } else {
                            stringResource(R.string.settings_voice_model_download)
                        },
                    color = Color.White,
                )
            }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = Color.White) }
    }
}

@Composable
private fun BoxScope.ListeningScreen(
    state: VoiceState.Listening,
    level: State<Float>,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EngineBadge()
        Spacer(Modifier.height(24.dp))

        VoiceOrb(
            reveal = 1f,
            level = level,
            listening = true,
            heard = rememberUpdatedState(VoiceOrbDynamics.wordsHeard(state.partial)),
        )
        Text(
            text = state.partial.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = 28.dp)
                    .fillMaxWidth(),
        )
    }
    CancelButton(onCancel = onCancel)
}

data class VoiceReviewActions(
    val onSaveBlock: (LocalDate?) -> Unit = {},
    val onDropBlock: (LocalDate?) -> Unit = {},
    val onSaveAll: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onEditTranscript: (String) -> Unit = {},
    val onFinishEditing: () -> Unit = {},
)

@Composable
private fun BoxScope.ReviewScreen(
    state: VoiceState.Review,
    onCancel: () -> Unit,
    actions: VoiceReviewActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val groups =
        remember(state.drafts, state.targetDate, state.today) {
            VoiceDraftGroups.of(state.drafts, state.targetDate, state.today)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 168.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EngineBadge()
        Spacer(Modifier.height(16.dp))
        if (state.editing) {
            TranscriptField(
                text = state.transcript,
                onTextChange = actions.onEditTranscript,
            )
        } else {
            Text(
                text = state.transcript,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.voice_no_drafts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            groups.forEach { group ->
                DraftBlock(
                    group = group,
                    locale = locale,
                    actions = if (state.editing) null else actions,
                )
            }
        }
    }

    ReviewBar(state = state, onCancel = onCancel, actions = actions)
}

@Composable
private fun DraftBlock(
    group: VoiceDraftGroup,
    locale: java.util.Locale,
    actions: VoiceReviewActions?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag(voiceBlockTag(group.date)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dayLabel(group, locale),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f),
            )
            if (actions != null) {
                IconButton(
                    onClick = { actions.onSaveBlock(group.date) },
                    modifier = Modifier.testTag(voiceBlockSaveTag(group.date)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.voice_block_save),
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = { actions.onDropBlock(group.date) },
                    modifier = Modifier.testTag(voiceBlockDropTag(group.date)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.voice_block_drop),
                        tint = Color.White,
                    )
                }
            }
        }
        group.drafts.forEach { draft -> DraftRow(draft = draft) }
    }
}

@Composable
private fun DraftRow(draft: ParsedTask) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = draft.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )

        draft.time?.let { time ->
            Text(
                text = DateFormatters.time(time),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun TranscriptField(
    text: String,
    onTextChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(R.string.voice_edit_hint)) },
        singleLine = false,
        maxLines = 3,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(VOICE_EDIT_FIELD_TAG),
    )
}

@Composable
private fun BoxScope.ReviewBar(
    state: VoiceState.Review,
    onCancel: () -> Unit,
    actions: VoiceReviewActions,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.lastSave?.let { save ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag(VOICE_SAVED_TAG),
            ) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.voice_saved,
                            save.count,
                            save.count,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                TextButton(onClick = actions.onUndo) {
                    Text(stringResource(R.string.undo), color = Color.White)
                }
            }
        }

        if (state.editing) {
            TextButton(onClick = actions.onFinishEditing) {
                Text(stringResource(R.string.ok), color = Color.White)
            }
            return@Column
        }

        if (state.drafts.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = actions.onEdit) {
                    Text(stringResource(R.string.voice_edit), color = Color.White)
                }
                Button(onClick = actions.onSaveAll) {
                    Text(stringResource(R.string.voice_save_all))
                }
            }
        }

        val closing = state.lastSave != null || state.drafts.isEmpty()
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(if (closing) R.string.ok else R.string.cancel),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun dayLabel(
    group: VoiceDraftGroup,
    locale: java.util.Locale,
): String =
    when (group.day) {
        VoiceDraftDay.TODAY -> {
            stringResource(R.string.today)
        }

        VoiceDraftDay.TOMORROW -> {
            stringResource(R.string.tomorrow)
        }

        VoiceDraftDay.OTHER -> {
            group.date?.let { DateFormatters.dayMonth(it, locale) }
                ?: stringResource(R.string.editor_without_date)
        }
    }

@Composable
private fun BoxScope.MessageScreen(
    message: String,
    primary: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .testTag(VOICE_MESSAGE_TAG),
    )
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onPrimary) { Text(primary, color = Color.White) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = Color.White) }
    }
}

@Composable
private fun EngineBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag(VOICE_ENGINE_TAG),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Text(
            text = stringResource(R.string.voice_engine_offline),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun BoxScope.CancelButton(onCancel: () -> Unit) {
    TextButton(
        onClick = onCancel,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
    ) {
        Text(stringResource(R.string.cancel), color = Color.White)
    }
}
