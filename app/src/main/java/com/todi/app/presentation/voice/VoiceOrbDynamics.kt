package com.todi.app.presentation.voice

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object VoiceOrbDynamics {
    const val QUIET_LEVEL = 0.12f

    const val SILENCE_MILLIS = 1_200L

    fun isSilent(
        lastLoudAtNanos: Long,
        nowNanos: Long,
    ): Boolean = nowNanos - lastLoudAtNanos >= SILENCE_MILLIS * NANOS_PER_MILLI

    fun countsAsSound(sample: Float): Boolean = sample > QUIET_LEVEL

    fun wordsHeard(text: String?): Int =
        text
            ?.trim()
            .orEmpty()
            .split(' ', '\t', '\n', '\r')
            .count { it.isNotBlank() }

    fun spherePoints(count: Int): FloatArray {
        val points = FloatArray(count * 3)
        for (index in 0 until count) {
            val y = 1f - 2f * (index + 0.5f) / count
            val ring = sqrt((1f - y * y).coerceAtLeast(0f))
            val angle = index * GOLDEN_ANGLE
            points[index * 3] = ring * cos(angle)
            points[index * 3 + 1] = y
            points[index * 3 + 2] = ring * sin(angle)
        }
        return points
    }

    fun neighbourPairs(
        points: FloatArray,
        count: Int,
        maxChord: Float,
    ): IntArray {
        val pairs = ArrayList<Int>(count * 3)
        val limit = maxChord * maxChord
        for (first in 0 until count) {
            for (second in first + 1 until count) {
                val dx = points[first * 3] - points[second * 3]
                val dy = points[first * 3 + 1] - points[second * 3 + 1]
                val dz = points[first * 3 + 2] - points[second * 3 + 2]
                if (dx * dx + dy * dy + dz * dz <= limit) {
                    pairs.add(first)
                    pairs.add(second)
                }
            }
        }
        return pairs.toIntArray()
    }

    fun neighbourChord(count: Int): Float = NEIGHBOUR_CHORD / sqrt(count.toFloat())

    fun swellOf(
        loudness: Float,
        kick: Float,
    ): Float {
        val voice = loudness.coerceIn(0f, 1f)
        val word = kick.coerceIn(0f, 1f)
        return 1f + SWELL_VOICE * voice + SWELL_WORD * word
    }

    fun spinOf(loudness: Float): Float = SPIN_BASE + SPIN_VOICE * loudness.coerceIn(0f, 1f)

    fun tiltOf(wobble: Float): Float = TILT_BASE + TILT_SWING * sin(wobble)

    const val WOBBLE_RATE = 0.16f

    fun shimmerOf(silence: Float): Float = ((silence.coerceIn(0f, 1f) - (1f - SHIMMER_FADE)) / SHIMMER_FADE).coerceIn(0f, 1f)

    fun twinkle(
        index: Int,
        phase: Float,
        shimmer: Float,
    ): Float {
        val wave = 0.5f + 0.5f * sin(index * TWINKLE_STEP + phase)
        return 1f - SHIMMER_DEPTH * shimmer.coerceIn(0f, 1f) * wave
    }

    fun depthFade(
        depth: Float,
        behind: Float,
        inFront: Float,
    ): Float = behind + (inFront - behind) * depth.coerceIn(0f, 1f)

    const val SHIMMER_FADE = 0.35f

    const val SHIMMER_DEPTH = 0.45f

    private const val NEIGHBOUR_CHORD = 4f
    private const val GOLDEN_ANGLE = 2.399963f
    private const val TWINKLE_STEP = 2.399963f
    private const val SWELL_VOICE = 0.10f
    private const val SWELL_WORD = 0.08f

    private const val SPIN_BASE = 0.02f
    private const val SPIN_VOICE = 0.05f

    private const val TILT_BASE = 0.55f
    private const val TILT_SWING = 0.35f

    private const val NANOS_PER_MILLI = 1_000_000L
}
