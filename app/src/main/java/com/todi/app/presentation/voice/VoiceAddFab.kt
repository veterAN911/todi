package com.todi.app.presentation.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

const val VOICE_FAB_TAG = "voiceAddFab"

private val HINT_GAP = 8.dp

@Composable
fun VoiceAddFab(
    targetDate: LocalDate?,
    modifier: Modifier = Modifier,
    docked: Boolean = false,
    onTap: () -> Unit,
) {
    val voice = LocalVoiceInput.current
    val config = LocalVoiceGestureConfig.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { config.dragThreshold.toPx() }
    val holdMillis = config.holdMillis
    val voiceEnabled by voice.enabled.collectAsStateWithLifecycle()
    val state by voice.state.collectAsStateWithLifecycle()
    val armed = state as? VoiceState.Armed

    LaunchedEffect(armed != null) {
        if (armed == null) return@LaunchedEffect
        delay(config.hintDelayMillis)
        voice.showHint()
    }

    val currentOnTap by rememberUpdatedState(onTap)
    val description = stringResource(R.string.cd_add_task)

    Box(modifier.size(ADD_BUTTON_SIZE)) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(VOICE_FAB_TAG)
                    .semantics { contentDescription = description }
                    .pointerInput(thresholdPx, holdMillis, voiceEnabled) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            if (!voiceEnabled) {
                                if (waitForUpOrCancellation() != null) currentOnTap()
                                return@awaitEachGesture
                            }

                            val held =
                                try {
                                    withTimeout(holdMillis) { waitForUpOrCancellation() }
                                    false
                                } catch (_: PointerEventTimeoutCancellationException) {
                                    true
                                }
                            if (!held) {
                                currentOnTap()
                                return@awaitEachGesture
                            }

                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            voice.arm(targetDate)

                            var triggered = false
                            var pulled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                pulled = true
                                val dy = change.position.y - down.position.y
                                val progress = (-dy / thresholdPx).coerceIn(0f, 1f)
                                voice.pull(progress)
                                if (progress >= 1f && !triggered) {
                                    triggered = true
                                    voice.trigger()
                                }
                                change.consume()
                            }

                            if (triggered) {
                                return@awaitEachGesture
                            }

                            voice.cancel()
                            currentOnTap()
                        }
                    },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = if (docked) 0.dp else 6.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }

        if (armed?.hint == true) {
            VoiceHint(
                progress = armed.dragProgress,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -(ADD_BUTTON_SIZE + HINT_GAP)),
            )
        }
    }
}
