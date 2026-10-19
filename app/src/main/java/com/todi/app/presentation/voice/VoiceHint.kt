package com.todi.app.presentation.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.todi.app.R

const val VOICE_HINT_TAG = "voicePullHint"

@Composable
fun VoiceHint(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val membrane = MaterialTheme.colorScheme.primary
    val base = remember(membrane) { OrbBase.of(membrane) }
    val density = LocalDensity.current
    val brushes = remember(density) { OrbBrushes(density) }
    val arrow = remember { VoiceHintDynamics.chevron() }
    val motion = remember { HintMotion() }
    val label = stringResource(R.string.voice_pull_up)

    LaunchedEffect(progress > 0f) {
        if (progress > 0f) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            motion.seconds.floatValue += (now - previous) / NANOS_PER_SECOND
            previous = now
        }
    }

    Canvas(
        modifier
            .size(HINT_WIDTH, HINT_HEIGHT)
            .testTag(VOICE_HINT_TAG)
            .semantics { contentDescription = label },
    ) {
        val fade = (1f - progress).coerceIn(0f, 1f)
        if (fade <= 0f) return@Canvas
        val seconds = motion.seconds.floatValue
        val centre = size.width / 2f
        val bob = VoiceHintDynamics.bob(seconds) * BOB.toPx()
        val apexY = APEX_TOP.toPx() + bob

        glow(
            centre = Offset(centre, apexY + ARROW_DROP.toPx() * 0.5f),
            radius = ARROW_HALF.toPx() * 1.15f,
            base = base,
            alpha = BLOOM * fade,
        )

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            for (spark in 0 until VoiceHintDynamics.SPARKS) {
                val rise = VoiceHintDynamics.sparkRise(seconds, spark)
                val alpha = VoiceHintDynamics.sparkAlpha(rise) * fade
                if (alpha <= 0f) continue
                native.drawBead(
                    x =
                        centre + VoiceHintDynamics.sparkColumn(spark) *
                            SPARK_SPREAD.toPx() * VoiceHintDynamics.sparkSpread(rise),
                    y = lerp(SPARK_BOTTOM.toPx(), SPARK_TOP.toPx(), rise),
                    radius = SPARK_DOT.toPx() * VoiceHintDynamics.sparkScale(rise),
                    color =
                        beadColor(
                            brushes = brushes,
                            hue = base.hue + VoiceHintDynamics.hueDrift(seconds, spark),
                            saturation = base.saturation,
                            alpha = alpha,
                            pale = SPARK_PALE,
                        ),
                    core = SPARK_CORE,
                    brushes = brushes,
                )
            }

            for (dot in 0 until arrow.size / 2) {
                native.drawBead(
                    x = centre + arrow[dot * 2] * ARROW_HALF.toPx(),
                    y = apexY + arrow[dot * 2 + 1] * ARROW_DROP.toPx(),
                    radius = ARROW_DOT.toPx(),
                    color =
                        beadColor(
                            brushes = brushes,
                            hue = base.hue + VoiceHintDynamics.hueDrift(seconds, dot + HUE_STEP),
                            saturation = base.saturation,
                            alpha = VoiceHintDynamics.breath(seconds, dot) * fade,
                            pale = ARROW_PALE,
                        ),
                    core = ARROW_CORE,
                    brushes = brushes,
                )
            }
        }
    }
}

private class HintMotion {
    val seconds = mutableFloatStateOf(0f)
}

private val HINT_WIDTH = 96.dp

private val HINT_HEIGHT = 64.dp

private val APEX_TOP = 15.dp
private val ARROW_HALF = 15.dp
private val ARROW_DROP = 12.dp

private val SPARK_BOTTOM = 62.dp
private val SPARK_TOP = 18.dp

private val SPARK_SPREAD = 9.dp

private val BOB = 1.5.dp

private val ARROW_DOT = 3.1.dp
private val SPARK_DOT = 2.1.dp

private const val ARROW_PALE = 0.6f
private const val SPARK_PALE = 0.45f

private const val ARROW_CORE = 0.62f
private const val SPARK_CORE = 0.55f

private const val BLOOM = 0.12f

private const val HUE_STEP = 3

private const val NANOS_PER_SECOND = 1_000_000_000f
