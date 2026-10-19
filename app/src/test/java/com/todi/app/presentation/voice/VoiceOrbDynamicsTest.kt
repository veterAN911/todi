package com.todi.app.presentation.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class VoiceOrbDynamicsTest {
    private val milli = 1_000_000L

    @Test
    fun aSoundJustHeard_isNotSilence() {
        assertFalse(VoiceOrbDynamics.isSilent(lastLoudAtNanos = 0L, nowNanos = 0L))
        assertFalse(
            VoiceOrbDynamics.isSilent(
                lastLoudAtNanos = 0L,
                nowNanos = (VoiceOrbDynamics.SILENCE_MILLIS - 1) * milli,
            ),
        )
    }

    @Test
    fun silence_comesAfterTheFullInterval() {
        assertTrue(
            VoiceOrbDynamics.isSilent(
                lastLoudAtNanos = 0L,
                nowNanos = VoiceOrbDynamics.SILENCE_MILLIS * milli,
            ),
        )
        assertTrue(
            VoiceOrbDynamics.isSilent(
                lastLoudAtNanos = 1_000L * milli,
                nowNanos = (1_000L + VoiceOrbDynamics.SILENCE_MILLIS) * milli,
            ),
        )
    }

    @Test
    fun aRoomIsNotAVoice() {
        assertFalse(VoiceOrbDynamics.countsAsSound(0f))
        assertFalse(VoiceOrbDynamics.countsAsSound(VoiceOrbDynamics.QUIET_LEVEL))
        assertTrue(VoiceOrbDynamics.countsAsSound(VoiceOrbDynamics.QUIET_LEVEL + 0.01f))
        assertTrue(VoiceOrbDynamics.countsAsSound(1f))
    }

    @Test
    fun theWordsAreCounted() {
        assertEquals(0, VoiceOrbDynamics.wordsHeard(null))
        assertEquals(0, VoiceOrbDynamics.wordsHeard(""))
        assertEquals(0, VoiceOrbDynamics.wordsHeard("   "))
        assertEquals(1, VoiceOrbDynamics.wordsHeard("хлеб"))
        assertEquals(2, VoiceOrbDynamics.wordsHeard("купить хлеб"))
        assertEquals(4, VoiceOrbDynamics.wordsHeard("отвести дочку в садик"))
    }

    @Test
    fun theEmptyGaps_areNotWords() {
        assertEquals(2, VoiceOrbDynamics.wordsHeard("  купить   хлеб  "))
        assertEquals(2, VoiceOrbDynamics.wordsHeard("купить\tхлеб"))
        assertEquals(2, VoiceOrbDynamics.wordsHeard("купить\nхлеб"))
    }

    @Test
    fun everyDot_sitsOnTheSurface() {
        val points = VoiceOrbDynamics.spherePoints(130)

        assertEquals(130 * 3, points.size)
        for (index in 0 until 130) {
            val x = points[index * 3]
            val y = points[index * 3 + 1]
            val z = points[index * 3 + 2]
            assertEquals("dot $index drifted off the sphere", 1f, hypot(x, y, z), 0.0001f)
        }
    }

    @Test
    fun theDots_areSpreadEvenly() {
        val count = 130
        val points = VoiceOrbDynamics.spherePoints(count)
        var closest = Float.MAX_VALUE
        for (first in 0 until count) {
            for (second in first + 1 until count) {
                val chord = chord(points, first, second)
                if (chord < closest) closest = chord
            }
        }

        assertTrue("two dots nearly on top of each other: $closest", closest > 1.4f / sqrt(count.toFloat()))
    }

    @Test
    fun theLines_joinNeighboursOnly() {
        val count = 130
        val points = VoiceOrbDynamics.spherePoints(count)
        val chord = VoiceOrbDynamics.neighbourChord(count)
        val edges = VoiceOrbDynamics.neighbourPairs(points, count, chord)

        assertTrue("no line refused", edges.size > count)
        var index = 0
        while (index < edges.size) {
            val first = edges[index]
            val second = edges[index + 1]
            assertTrue("a dot joined to itself", first != second)
            assertTrue("a pair repeated backwards", first < second)
            assertTrue("a pair fell off the ball", second < count)
            assertTrue(
                "a line spans $chord or more, which is not a neighbour",
                chord(points, first, second) <= chord + 0.0001f,
            )
            index += 2
        }
    }

    @Test
    fun theNet_isAllInOnePiece() {
        for (count in listOf(64, 130)) {
            val points = VoiceOrbDynamics.spherePoints(count)
            val edges =
                VoiceOrbDynamics.neighbourPairs(
                    points = points,
                    count = count,
                    maxChord = VoiceOrbDynamics.neighbourChord(count),
                )

            val reached = BooleanArray(count)
            val queue = ArrayDeque<Int>()
            reached[0] = true
            queue.addLast(0)
            var visited = 1
            while (queue.isNotEmpty()) {
                val dot = queue.removeFirst()
                var index = 0
                while (index < edges.size) {
                    val neighbour =
                        when (dot) {
                            edges[index] -> edges[index + 1]
                            edges[index + 1] -> edges[index]
                            else -> -1
                        }
                    if (neighbour >= 0 && !reached[neighbour]) {
                        reached[neighbour] = true
                        visited++
                        queue.addLast(neighbour)
                    }
                    index += 2
                }
            }

            assertEquals("the net of $count dots fell into islands", count, visited)
        }
    }

    @Test
    fun theNet_isNeitherBareNorATangle() {
        for (count in listOf(64, 130)) {
            val points = VoiceOrbDynamics.spherePoints(count)
            val degrees = IntArray(count)
            val edges =
                VoiceOrbDynamics.neighbourPairs(
                    points = points,
                    count = count,
                    maxChord = VoiceOrbDynamics.neighbourChord(count),
                )
            var index = 0
            while (index < edges.size) {
                degrees[edges[index]]++
                degrees[edges[index + 1]]++
                index += 2
            }

            val average = degrees.sum().toFloat() / count
            degrees.forEach { assertTrue("a dot with $it lines", it >= 1) }
            assertTrue("$count dots average $average lines each", average in 2f..6f)
        }
    }

    @Test
    fun theDenserTheShell_theShorterItsLines() {
        assertTrue(VoiceOrbDynamics.neighbourChord(130) < VoiceOrbDynamics.neighbourChord(64))
    }

    @Test
    fun theBall_swellsWithTheVoice_andNeverPastItsBounds() {
        assertEquals(1f, VoiceOrbDynamics.swellOf(0f, 0f), 0.0001f)
        assertTrue(VoiceOrbDynamics.swellOf(1f, 0f) > VoiceOrbDynamics.swellOf(0.5f, 0f))

        assertTrue(VoiceOrbDynamics.swellOf(1f, 1f) > VoiceOrbDynamics.swellOf(1f, 0f))

        assertEquals(VoiceOrbDynamics.swellOf(1f, 1f), VoiceOrbDynamics.swellOf(1.4f, 3f), 0.0001f)
        assertEquals(VoiceOrbDynamics.swellOf(0f, 0f), VoiceOrbDynamics.swellOf(-2f, -1f), 0.0001f)
    }

    @Test
    fun theBall_turnsEvenInSilence_andFasterWithAVoice() {
        assertTrue(VoiceOrbDynamics.spinOf(0f) > 0f)
        assertTrue(VoiceOrbDynamics.spinOf(1f) > VoiceOrbDynamics.spinOf(0f))
        assertEquals(VoiceOrbDynamics.spinOf(1f), VoiceOrbDynamics.spinOf(9f), 0.0001f)

        assertTrue(VoiceOrbDynamics.spinOf(1f) < 1f)
    }

    @Test
    fun theAxis_drifts_butNeverSeesTheBallPoleOn() {
        val tilts = (0..36).map { VoiceOrbDynamics.tiltOf(it * 0.1f) }

        assertTrue("the axis never moved", (tilts.max() - tilts.min()) > 0.5f)

        tilts.forEach {
            assertTrue("the ball was seen pole-on: $it rad", it > 0.15f && it < 1.5f)
        }
    }

    @Test
    fun theBallStaysSteady_untilTheSilenceHasRunMostOfItsWay() {
        assertEquals(0f, VoiceOrbDynamics.shimmerOf(0f), 0.0001f)
        assertEquals(0f, VoiceOrbDynamics.shimmerOf(0.5f), 0.0001f)
        assertEquals(0f, VoiceOrbDynamics.shimmerOf(0.65f), 0.0001f)
        assertTrue(VoiceOrbDynamics.shimmerOf(0.8f) > 0f)
        assertEquals(1f, VoiceOrbDynamics.shimmerOf(1f), 0.0001f)
    }

    @Test
    fun moreSilence_neverMeansLessTwinkle() {
        var previous = 0f
        for (step in 0..20) {
            val shimmer = VoiceOrbDynamics.shimmerOf(step / 20f)
            assertTrue("the twinkle fell at $step", shimmer >= previous)
            previous = shimmer
        }
    }

    @Test
    fun withNoSilence_everyDotBurnsSteady() {
        for (index in 0 until 40) {
            assertEquals(1f, VoiceOrbDynamics.twinkle(index, phase = 1.7f, shimmer = 0f), 0.0001f)
        }
    }

    @Test
    fun theTwinkle_travelsAroundTheBall() {
        val phases = (0 until 20).map { VoiceOrbDynamics.twinkle(it, phase = 0f, shimmer = 1f) }

        assertNotEquals(phases[0], phases[1], 0.001f)
        assertNotEquals(phases[1], phases[2], 0.001f)
        assertTrue("no dot dimmed at all", phases.min() < 1f - VoiceOrbDynamics.SHIMMER_DEPTH * 0.9f)
        assertTrue("a dot went out entirely", phases.min() >= 1f - VoiceOrbDynamics.SHIMMER_DEPTH)
    }

    @Test
    fun theFarSideOfTheBall_isDimmerAndSmaller() {
        assertEquals(0.2f, VoiceOrbDynamics.depthFade(0f, behind = 0.2f, inFront = 0.9f), 0.0001f)
        assertEquals(0.9f, VoiceOrbDynamics.depthFade(1f, behind = 0.2f, inFront = 0.9f), 0.0001f)
        assertTrue(
            VoiceOrbDynamics.depthFade(0.75f, behind = 0.2f, inFront = 0.9f) >
                VoiceOrbDynamics.depthFade(0.25f, behind = 0.2f, inFront = 0.9f),
        )

        assertEquals(0.2f, VoiceOrbDynamics.depthFade(-1f, behind = 0.2f, inFront = 0.9f), 0.0001f)
        assertEquals(0.9f, VoiceOrbDynamics.depthFade(4f, behind = 0.2f, inFront = 0.9f), 0.0001f)
    }

    private fun hypot(
        x: Float,
        y: Float,
        z: Float,
    ): Float = sqrt(x * x + y * y + z * z)

    private fun chord(
        points: FloatArray,
        first: Int,
        second: Int,
    ): Float {
        val dx = points[first * 3] - points[second * 3]
        val dy = points[first * 3 + 1] - points[second * 3 + 1]
        val dz = points[first * 3 + 2] - points[second * 3 + 2]
        return hypot(dx, dy, dz)
    }
}
