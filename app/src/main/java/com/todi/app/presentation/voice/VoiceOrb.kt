package com.todi.app.presentation.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.cos
import kotlin.math.sin

const val VOICE_ORB_TAG = "voiceOrb"

@Composable
fun VoiceOrb(
    reveal: Float,
    level: State<Float>,
    listening: Boolean,
    heard: State<Int> = remember { mutableStateOf(0) },
    modifier: Modifier = Modifier,
) {
    val motion = remember { OrbMotion() }
    val spin = remember { mutableFloatStateOf(0f) }
    val wobble = remember { mutableFloatStateOf(0f) }
    val membrane = MaterialTheme.colorScheme.primary
    val base = remember(membrane) { OrbBase.of(membrane) }
    val density = LocalDensity.current
    val brushes = remember(density) { OrbBrushes(density) }
    val cage = remember { Shell(VoiceOrbDynamics.spherePoints(CAGE_DOTS)) }
    val core = remember { Shell(VoiceOrbDynamics.spherePoints(CORE_DOTS)) }

    LaunchedEffect(level, listening) {
        if (!listening) return@LaunchedEffect
        motion.beginSession()
        snapshotFlow { level.value }.collectLatest { sample ->
            if (VoiceOrbDynamics.countsAsSound(sample)) {
                motion.lastSoundNanos = System.nanoTime()
            }
            motion.loudness.animateTo(
                targetValue = sample.coerceIn(0f, 1f),
                animationSpec = tween(LOUDNESS_MILLIS, easing = LinearEasing),
            )
        }
    }

    LaunchedEffect(heard, listening) {
        if (!listening) return@LaunchedEffect
        snapshotFlow { heard.value }
            .distinctUntilChanged()
            .collectLatest { understood ->
                if (understood <= 0) return@collectLatest
                motion.word.snapTo(1f)
                motion.word.animateTo(0f, animationSpec = tween(WORD_MILLIS))
            }
    }

    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        while (true) {
            val silent = VoiceOrbDynamics.isSilent(motion.lastSoundNanos, System.nanoTime())
            if (silent != motion.wasSilent) {
                motion.wasSilent = silent
                motion.silence.animateTo(
                    targetValue = if (silent) 1f else 0f,
                    animationSpec = tween(SILENCE_FADE_MILLIS),
                )
            }
            delay(SILENCE_POLL_MILLIS)
        }
    }

    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - previous) / NANOS_PER_SECOND
            previous = now

            spin.floatValue =
                wrap(
                    spin.floatValue + seconds * VoiceOrbDynamics.spinOf(motion.loudness.value) * TWO_PI,
                )
            wobble.floatValue =
                wrap(
                    wobble.floatValue + seconds * VoiceOrbDynamics.WOBBLE_RATE,
                )
        }
    }

    Canvas(
        modifier
            .size(ORB_SIZE)
            .testTag(VOICE_ORB_TAG),
    ) {
        val loudness = motion.loudness.value.coerceIn(0f, 1f)
        val energy = (loudness + motion.word.value).coerceIn(0f, 1f)
        val swell = VoiceOrbDynamics.swellOf(loudness, motion.word.value)
        val shimmer = VoiceOrbDynamics.shimmerOf(motion.silence.value)
        val shown = reveal.coerceIn(0f, 1f)
        val fade = ORB_MIN_ALPHA + (1f - ORB_MIN_ALPHA) * shown
        val turn = spin.floatValue
        val tilt = VoiceOrbDynamics.tiltOf(wobble.floatValue)
        val centre = Offset(size.width / 2f, size.height / 2f)
        val ball = size.minDimension * BALL_FRACTION * swell
        val heart = ball * CORE_SCALE

        glow(centre, ball * GLOW_SCALE, base, (0.15f + 0.20f * energy) * fade)
        glow(centre, heart * GLOW_SCALE, base, (0.22f + 0.17f * energy) * fade)

        cage.project(ball, centre, turn, tilt)
        core.project(heart, centre, turn, tilt)

        drawShell(
            shell = cage,
            base = base,
            fade = fade,
            energy = energy,
            shimmer = shimmer,
            turn = turn,
            brushes = brushes,
            dotSize = CAGE_DOT.toPx(),
            lineWidth = CAGE_LINE.toPx(),
            pale = CAGE_PALE,
        )
        drawShell(
            shell = core,
            base = base,
            fade = fade,
            energy = energy,
            shimmer = shimmer,
            turn = turn,
            brushes = brushes,
            dotSize = CORE_DOT.toPx(),
            lineWidth = CORE_LINE.toPx(),
            pale = CORE_PALE,
        )
    }
}

private fun DrawScope.drawShell(
    shell: Shell,
    base: OrbBase,
    fade: Float,
    energy: Float,
    shimmer: Float,
    turn: Float,
    brushes: OrbBrushes,
    dotSize: Float,
    lineWidth: Float,
    pale: Float,
) {
    val near = brushes.nearPath
    val far = brushes.farPath
    near.reset()
    far.reset()
    var index = 0
    while (index < shell.edges.size) {
        val first = shell.edges[index]
        val second = shell.edges[index + 1]
        val depth = (shell.depth[first] + shell.depth[second]) * 0.5f
        val path = if (depth >= SHELL_MIDDLE) near else far
        path.moveTo(shell.screen[first * 2], shell.screen[first * 2 + 1])
        path.lineTo(shell.screen[second * 2], shell.screen[second * 2 + 1])
        index += 2
    }

    drawPath(far, base.line(LINE_ALPHA_BEHIND * fade), style = Stroke(lineWidth))
    drawPath(near, base.line(LINE_ALPHA_IN_FRONT * fade), style = Stroke(lineWidth))

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        for (dot in 0 until shell.count) {
            val depth = shell.depth[dot]
            val bright =
                VoiceOrbDynamics.depthFade(depth, DOT_ALPHA_BEHIND, DOT_ALPHA_IN_FRONT) *
                    fade * VoiceOrbDynamics.twinkle(dot, turn * TWINKLE_RATE, shimmer)
            val radius =
                dotSize *
                    VoiceOrbDynamics.depthFade(depth, DOT_SCALE_BEHIND, DOT_SCALE_IN_FRONT) *
                    (1f + DOT_SWELL * energy)

            native.drawBead(
                x = shell.screen[dot * 2],
                y = shell.screen[dot * 2 + 1],
                radius = radius,
                color = beadColor(brushes, base.hue, base.saturation, bright, pale * depth),
                core = DOT_CORE,
                brushes = brushes,
            )
        }
    }
}

private class OrbMotion {
    val loudness = Animatable(0f)

    val word = Animatable(0f)

    val silence = Animatable(0f)

    var lastSoundNanos: Long = 0L

    var wasSilent: Boolean = false

    suspend fun beginSession() {
        lastSoundNanos = System.nanoTime()
        wasSilent = false
        loudness.snapTo(0f)
        word.snapTo(0f)
        silence.snapTo(0f)
    }
}

private class Shell(
    points: FloatArray,
) {
    val count: Int = points.size / 3
    val edges: IntArray =
        VoiceOrbDynamics.neighbourPairs(
            points = points,
            count = count,
            maxChord = VoiceOrbDynamics.neighbourChord(count),
        )
    val screen: FloatArray = FloatArray(count * 2)
    val depth: FloatArray = FloatArray(count)

    private val points: FloatArray = points

    fun project(
        radius: Float,
        centre: Offset,
        angle: Float,
        tilt: Float,
    ) {
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        val cosTilt = cos(tilt)
        val sinTilt = sin(tilt)
        for (index in 0 until count) {
            val x = points[index * 3]
            val y = points[index * 3 + 1]
            val z = points[index * 3 + 2]

            val turnedX = x * cosAngle + z * sinAngle
            val turnedZ = -x * sinAngle + z * cosAngle
            val tiltedY = y * cosTilt - turnedZ * sinTilt
            val tiltedZ = y * sinTilt + turnedZ * cosTilt

            screen[index * 2] = centre.x + radius * turnedX
            screen[index * 2 + 1] = centre.y + radius * tiltedY

            depth[index] = (tiltedZ + 1f) * 0.5f
        }
    }
}

private val ORB_SIZE = 280.dp
private val CAGE_DOT = 3.6.dp
private val CORE_DOT = 2.4.dp
private val CAGE_LINE = 1.1.dp
private val CORE_LINE = 0.7.dp

private const val CAGE_DOTS = 64
private const val CORE_DOTS = 260

private const val BALL_FRACTION = 0.44f

private const val CORE_SCALE = 0.58f

private const val DOT_CORE = 0.62f

private const val CAGE_PALE = 0.85f
private const val CORE_PALE = 0.95f

private const val DOT_SWELL = 0.16f
private const val DOT_ALPHA_BEHIND = 0.45f
private const val DOT_ALPHA_IN_FRONT = 1f
private const val DOT_SCALE_BEHIND = 0.6f
private const val DOT_SCALE_IN_FRONT = 1.2f
private const val LINE_ALPHA_BEHIND = 0.32f
private const val LINE_ALPHA_IN_FRONT = 0.8f

private const val SHELL_MIDDLE = 0.5f

private const val GLOW_SCALE = 1.45f
private const val ORB_MIN_ALPHA = 0.45f

private const val TWINKLE_RATE = 0.6f

private const val TWO_PI = 6.2831855f
private const val TURN_WRAP = TWO_PI * 64f

private fun wrap(radians: Float): Float = if (radians > TURN_WRAP) radians - TURN_WRAP else radians

private const val NANOS_PER_SECOND = 1_000_000_000f

private const val LOUDNESS_MILLIS = 140
private const val SILENCE_FADE_MILLIS = 600
private const val SILENCE_POLL_MILLIS = 250L
private const val WORD_MILLIS = 420
