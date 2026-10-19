package com.todi.app.presentation.voice

import kotlin.math.PI
import kotlin.math.sin

internal object VoiceHintDynamics {
    const val SPARKS = 7

    const val ARM_DOTS = 5

    const val SPARK_PERIOD = 1.5f

    const val BOB_PERIOD = 1.6f

    const val BREATH_PERIOD = 1.2f

    const val HUE_PERIOD = 3.4f

    const val HUE_DRIFT = 20f

    const val BREATH_FLOOR = 0.62f

    private const val SPARK_LIT = 0.18f

    private const val SPARK_DYING = 0.42f

    private const val SPARK_SHRINK = 0.5f

    private const val HUE_STEP = 0.29f

    private const val BREATH_STEP = 0.37f

    private const val TAU = (2 * PI).toFloat()

    fun sparkPhase(index: Int): Float = index.toFloat() / SPARKS

    fun sparkColumn(index: Int): Float = ((index % 3) - 1).toFloat()

    fun sparkRise(
        time: Float,
        index: Int,
    ): Float {
        val phase = (time / SPARK_PERIOD + sparkPhase(index)).mod(1f)

        return phase * (2f - phase)
    }

    fun sparkAlpha(rise: Float): Float =
        minOf(
            (rise / SPARK_LIT).coerceAtMost(1f),
            ((1f - rise) / SPARK_DYING).coerceAtMost(1f),
        )

    fun sparkScale(rise: Float): Float = 1f - SPARK_SHRINK * rise.coerceIn(0f, 1f)

    fun sparkSpread(rise: Float): Float = 1f - rise.coerceIn(0f, 1f)

    fun hueDrift(
        time: Float,
        index: Int,
    ): Float = HUE_DRIFT * sin(TAU * (time / HUE_PERIOD + (index * HUE_STEP).mod(1f)))

    fun breath(
        time: Float,
        index: Int,
    ): Float {
        val wave = 0.5f + 0.5f * sin(TAU * (time / BREATH_PERIOD + (index * BREATH_STEP).mod(1f)))
        return BREATH_FLOOR + (1f - BREATH_FLOOR) * wave
    }

    fun bob(time: Float): Float = sin(TAU * time / BOB_PERIOD)

    fun chevron(): FloatArray {
        val last = ARM_DOTS - 1
        val points = FloatArray((2 * last + 1) * 2)
        var at = 0
        for (step in 0 until last) {
            val out = 1f - step.toFloat() / last
            points[at++] = -out
            points[at++] = out
        }
        points[at++] = 0f
        points[at++] = 0f
        for (step in last - 1 downTo 0) {
            val out = 1f - step.toFloat() / last
            points[at++] = out
            points[at++] = out
        }
        return points
    }
}
