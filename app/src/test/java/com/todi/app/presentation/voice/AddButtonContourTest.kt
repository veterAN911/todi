package com.todi.app.presentation.voice

import com.todi.app.presentation.voice.AddButtonContour.Arc
import com.todi.app.presentation.voice.AddButtonContour.Hump
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class AddButtonContourTest {
    private val radius = 28f
    private val sink = 10f
    private val gap = 8f

    private val shoulder = radius - sink

    private val hump = AddButtonContour.hump(radius, sink, gap, shoulder)!!

    private val centreY = -(radius - sink)

    private fun Arc.pointAt(degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        return (centreX + radius * cos(radians)).toFloat() to
            (centreY + radius * sin(radians)).toFloat()
    }

    private fun Arc.startPoint() = pointAt(startDegrees)

    private fun Arc.endPoint() = pointAt(startDegrees + sweepDegrees)

    private fun Arc.pointsEvery(stepDegrees: Float): List<Pair<Float, Float>> {
        val steps = (kotlin.math.abs(sweepDegrees) / stepDegrees).toInt().coerceAtLeast(1)
        return (0..steps).map { pointAt(startDegrees + sweepDegrees * it / steps) }
    }

    @Test
    fun thePieces_meet_withoutAStep() {
        assertEquals(hump.straightEnd, -hump.leftShoulder.startPoint().first, 0.01f)
        assertEquals(0f, hump.leftShoulder.startPoint().second, 0.01f)

        val shoulderToCap = hump.leftShoulder.endPoint()
        val capFrom = hump.cap.startPoint()
        assertEquals(shoulderToCap.first, capFrom.first, 0.01f)
        assertEquals(shoulderToCap.second, capFrom.second, 0.01f)

        val capTo = hump.cap.endPoint()
        val shoulderFrom = hump.rightShoulder.startPoint()
        assertEquals(capTo.first, shoulderFrom.first, 0.01f)
        assertEquals(capTo.second, shoulderFrom.second, 0.01f)

        assertEquals(hump.straightEnd, hump.rightShoulder.endPoint().first, 0.01f)
        assertEquals(0f, hump.rightShoulder.endPoint().second, 0.01f)
    }

    @Test
    fun theLine_meetsTheHump_flat() {
        assertEquals(90f, hump.leftShoulder.startDegrees, 0.01f)
        assertEquals(90f, hump.rightShoulder.startDegrees + hump.rightShoulder.sweepDegrees, 0.01f)

        val turning = hump.arcs.sumOf { it.sweepDegrees.toDouble() }
        assertEquals(0.0, turning, 0.01)
    }

    @Test
    fun theShoulders_areTangentToTheCap() {
        val shoulderCentreX = hump.leftShoulder.centreX
        val shoulderCentreY = hump.leftShoulder.centreY
        val distance =
            hypot(
                (shoulderCentreX - hump.cap.centreX).toDouble(),
                (shoulderCentreY - hump.cap.centreY).toDouble(),
            )
        assertEquals((hump.cap.radius + hump.leftShoulder.radius).toDouble(), distance, 0.01)

        assertEquals(hump.leftShoulder.radius, -hump.leftShoulder.centreY, 0.01f)
    }

    @Test
    fun theHump_keepsTheGap_allTheWayRound() {
        val nearest =
            hump.arcs
                .flatMap { it.pointsEvery(stepDegrees = 0.5f) }
                .minOf { (x, y) -> hypot(x.toDouble(), (y - centreY).toDouble()) }

        assertTrue(
            "the line runs into the button: closest approach ${nearest - radius}, gap $gap",
            nearest >= radius + gap - 0.01,
        )

        assertEquals((radius + gap).toDouble(), nearest, 0.01)
    }

    @Test
    fun theHump_isThreeRoundTurns_andNoCorners() {
        assertEquals(-90f, hump.leftShoulder.sweepDegrees, 0.01f)
        assertEquals(-90f, hump.rightShoulder.sweepDegrees, 0.01f)
        assertEquals(180f, hump.cap.sweepDegrees, 0.01f)
    }

    @Test
    fun theCap_goesOverTheTop() {
        val top = hump.cap
        val start = top.startDegrees
        val throughTop = ((270f - start) % 360f + 360f) % 360f
        assertTrue("the wrap does not reach over the button: $start + ${top.sweepDegrees}", throughTop <= top.sweepDegrees)
        assertTrue("the wrap goes round more than once: ${top.sweepDegrees}", top.sweepDegrees < 360f)
    }

    @Test
    fun theShoulders_areTheSameBend_mirrored() {
        assertEquals(-hump.leftShoulder.centreX, hump.rightShoulder.centreX, 0.01f)
        assertEquals(hump.leftShoulder.centreY, hump.rightShoulder.centreY, 0.01f)
        assertEquals(hump.leftShoulder.sweepDegrees, hump.rightShoulder.sweepDegrees, 0.01f)

        assertTrue("the shoulders turn opposite ways", hump.leftShoulder.sweepDegrees < 0f)
    }

    @Test
    fun aShoulderOfZero_isTheBareCorner() {
        val cornered: Hump = AddButtonContour.hump(radius, sink, gap, shoulder = 0f)!!

        val above = radius - sink
        val capRadius = radius + gap
        assertEquals(sqrt(capRadius * capRadius - above * above), cornered.straightEnd, 0.01f)
        assertTrue("the cap is not over the top: ${cornered.cap.sweepDegrees}", cornered.cap.sweepDegrees > 180f)
    }

    @Test
    fun aBiggerGap_pushesTheHumpOut_andKeepsItTheSameShape() {
        val wider = AddButtonContour.hump(radius, sink, gap = gap + 4f, shoulder = shoulder)!!

        assertTrue("the wrap did not widen: ${wider.straightEnd}", wider.straightEnd > hump.straightEnd)

        assertEquals(hump.cap.centreY, wider.cap.centreY, 0.01f)
        assertEquals(hump.cap.centreX, wider.cap.centreX, 0.01f)
    }

    @Test
    fun aButtonAboveTheLine_isNotWrappedAtAll() {
        assertNull(AddButtonContour.hump(radius, sink = -4f, gap = gap, shoulder = shoulder))
    }
}
