package com.todi.app.presentation.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VoiceHintDynamicsTest {
    private val moments: List<Float> = (0..600).map { it * 0.02f }

    @Test
    fun sparks_areAStreamNotAClump() {
        for (time in moments.filter { it % 0.25f < 0.02f }) {
            val rises =
                (0 until VoiceHintDynamics.SPARKS).map {
                    VoiceHintDynamics.sparkRise(time, it)
                }
            val highest = rises.max()
            val lowest = rises.min()
            assertTrue(
                "at $time: the sparks are all at ${"%.2f".format(highest)}..${"%.2f".format(lowest)}",
                highest - lowest >= 0.5f,
            )
        }
    }

    @Test
    fun aSpark_staysBetweenTheButtonAndTheTop() {
        for (time in moments) {
            for (spark in 0 until VoiceHintDynamics.SPARKS) {
                val rise = VoiceHintDynamics.sparkRise(time, spark)
                assertTrue("at $time the spark $spark was at $rise", rise in 0f..1f)
            }
        }
    }

    @Test
    fun aSpark_isNeverLitAtEitherEnd() {
        assertEquals(0f, VoiceHintDynamics.sparkAlpha(0f), 0f)
        assertEquals(0f, VoiceHintDynamics.sparkAlpha(1f), 0f)
        assertTrue("the middle of the climb should be lit", VoiceHintDynamics.sparkAlpha(0.5f) > 0.9f)
    }

    @Test
    fun aSpark_shrinksAsItClimbs() {
        var previous = Float.MAX_VALUE
        for (rise in 0..10) {
            val scale = VoiceHintDynamics.sparkScale(rise / 10f)
            assertTrue("the spark grew at $rise", scale <= previous + 0.0001f)
            previous = scale
        }
        assertTrue(VoiceHintDynamics.sparkScale(1f) < VoiceHintDynamics.sparkScale(0f))
    }

    @Test
    fun sparks_areGatheredOntoTheLineOnTheWayUp() {
        assertEquals(1f, VoiceHintDynamics.sparkSpread(0f), 0f)
        assertEquals(0f, VoiceHintDynamics.sparkSpread(1f), 0f)
        assertTrue(
            VoiceHintDynamics.sparkSpread(0.7f) < VoiceHintDynamics.sparkSpread(0.3f),
        )
    }

    @Test
    fun sparks_climbOnBothSides_andUpTheMiddle() {
        val columns = (0 until VoiceHintDynamics.SPARKS).map { VoiceHintDynamics.sparkColumn(it) }
        assertTrue("nothing climbed on the left", columns.any { it < 0f })
        assertTrue("nothing climbed on the right", columns.any { it > 0f })
        assertTrue("nothing climbed up the middle", columns.any { it == 0f })
        assertTrue("a spark climbed right off the arrow", columns.all { abs(it) <= 1f })
    }

    @Test
    fun theColourDrifts_butNeverFarFromTheThemesOwn() {
        for (time in moments) {
            for (dot in 0 until VoiceHintDynamics.SPARKS + 2 * VoiceHintDynamics.ARM_DOTS) {
                val drift = VoiceHintDynamics.hueDrift(time, dot)
                assertTrue("the drift ran to $drift", abs(drift) <= VoiceHintDynamics.HUE_DRIFT + 0.001f)
            }
        }
    }

    @Test
    fun theColourDrift_isNotTheSameOnEveryDot() {
        val moment = 1.1f
        val drifts =
            (0 until VoiceHintDynamics.SPARKS).map {
                VoiceHintDynamics.hueDrift(moment, it)
            }
        assertTrue(
            "every dot drifted the same way at once — that is a colour change, not a shimmer",
            drifts.max() - drifts.min() > VoiceHintDynamics.HUE_DRIFT * 0.5f,
        )
    }

    @Test
    fun theDrift_goesBothWaysOverTime() {
        val drift = moments.map { VoiceHintDynamics.hueDrift(it, 0) }
        assertTrue("the colour never left the theme's hue", drift.max() > 1f)
        assertTrue("the colour never came back the other way", drift.min() < -1f)
    }

    @Test
    fun aDotBreathes_betweenTheFloorAndFull() {
        for (time in moments) {
            for (dot in 0 until 2 * VoiceHintDynamics.ARM_DOTS + 1) {
                val breath = VoiceHintDynamics.breath(time, dot)
                assertTrue("a dot went out at $time", breath >= VoiceHintDynamics.BREATH_FLOOR - 0.001f)
                assertTrue("a dot burned past full at $time", breath <= 1f)
            }
        }
    }

    @Test
    fun theArrow_breathesUpAndDown() {
        val bobs = moments.map { VoiceHintDynamics.bob(it) }
        assertTrue("the arrow never rose", bobs.max() > 0.99f)
        assertTrue("the arrow never settled", bobs.min() < -0.99f)
    }

    @Test
    fun theArrow_hasOneApexAndTwoEvenArms() {
        val points = VoiceHintDynamics.chevron()
        val count = points.size / 2
        assertEquals("an odd number of dots, the apex being one of them", 1, count % 2)

        val apex = (0 until count).minByOrNull { points[it * 2 + 1] }!!
        assertEquals("the apex is not the middle dot", count / 2, apex)
        assertEquals("the apex is not on the line", 0f, points[apex * 2], 0f)
        assertEquals("the apex is not the highest dot", 0f, points[apex * 2 + 1], 0f)
    }

    @Test
    fun theArrow_isSymmetrical() {
        val points = VoiceHintDynamics.chevron()
        val count = points.size / 2
        for (dot in 0 until count / 2) {
            val mirror = count - 1 - dot
            assertEquals(
                "the dots $dot and $mirror are not each other's mirror",
                points[dot * 2],
                -points[mirror * 2],
                0.0001f,
            )
            assertEquals(
                "the dots $dot and $mirror are at different heights",
                points[dot * 2 + 1],
                points[mirror * 2 + 1],
                0.0001f,
            )
        }
    }

    @Test
    fun theArrow_fallsAwayFromItsApex() {
        val points = VoiceHintDynamics.chevron()
        val count = points.size / 2

        for (dot in count / 2 until count - 1) {
            assertTrue(
                "the dot ${dot + 1} is not below the dot $dot",
                points[(dot + 1) * 2 + 1] > points[dot * 2 + 1],
            )
        }
        assertEquals("the tips are not the lowest dots", 1f, points[1], 0f)
        assertEquals("the left tip is not the leftmost dot", -1f, points[0], 0f)
        assertEquals("the right tip is not the rightmost dot", 1f, points[(count - 1) * 2], 0f)
    }
}
