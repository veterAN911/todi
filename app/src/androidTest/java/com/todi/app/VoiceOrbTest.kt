package com.todi.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.todi.app.domain.model.ThemeMode
import com.todi.app.presentation.theme.TodiTheme
import com.todi.app.presentation.voice.VOICE_ORB_TAG
import com.todi.app.presentation.voice.VoiceOrb
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class VoiceOrbTest {
    private val backdrop = Color(0xFF141824)

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val level = mutableStateOf(VOICE_QUIET)
    private val heard = mutableStateOf(0)

    private fun showOrb() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TodiTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.background(backdrop)) {
                    VoiceOrb(reveal = 1f, level = level, listening = true, heard = heard)
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        advance(SETTLE_MILLIS)
    }

    private fun advance(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
    }

    @Test
    fun theBall_isANetNotADisc() {
        showOrb()
        val frame = capture("net")

        assertTrue(
            "the ball painted almost nothing: ${frame.dotCoverage()} of the canvas",
            frame.dotCoverage() > MIN_COVERAGE,
        )
        assertTrue(
            "the ball is a solid disc: ${frame.dotCoverage()} of the canvas is painted",
            frame.dotCoverage() < MAX_COVERAGE,
        )
        assertTrue(
            "the outer cage is filled in: ${frame.cageCoverage()} of the ring is painted",
            frame.cageCoverage() < MAX_CAGE_COVERAGE,
        )
    }

    @Test
    fun theBall_isRound_andTheCageReachesItsEdge() {
        showOrb()
        val frame = capture("round")

        val reach = frame.reach()
        val widest = reach.max()
        val narrowest = reach.min()

        assertTrue(
            "the cage stops at ${widest / frame.size} of the canvas, not at its edge",
            widest / frame.size > MIN_REACH,
        )
        assertTrue(
            "the ball spills over the canvas at ${widest / frame.size}",
            widest / frame.size < MAX_REACH,
        )
        assertTrue(
            "the ball is lopsided: $widest px one way, $narrowest px the other",
            widest / narrowest < MAX_LOPSIDED,
        )
    }

    @Test
    fun theBall_turns() {
        level.value = VOICE_QUIET
        showOrb()
        val first = capture("turn-1")

        advance(TURN_MILLIS)
        val second = capture("turn-2")

        assertTrue(
            "the ball did not move in ${TURN_MILLIS}ms: ${first.changed(second)} of the canvas",
            first.changed(second) > MIN_TURNED,
        )
        val grew = second.dotReach() / first.dotReach()
        assertTrue("the ball changed size instead of turning: ${grew}x", grew in 0.9f..1.1f)
    }

    @Test
    fun aVoice_swellsTheOrb() {
        level.value = VOICE_QUIET
        showOrb()
        val quiet = capture("quiet")

        level.value = VOICE_LOUD
        advance(SETTLE_MILLIS)
        val loud = capture("loud")

        assertTrue(
            "the ball did not grow: ${quiet.dotReach()} px at $VOICE_QUIET, " +
                "${loud.dotReach()} px at $VOICE_LOUD",
            loud.dotReach() > quiet.dotReach(),
        )
        assertTrue(
            "no more of the canvas was painted when the voice rose",
            loud.dotPixels() > quiet.dotPixels(),
        )
    }

    @Test
    fun aWord_swellsTheOrb() {
        level.value = VOICE_QUIET
        showOrb()
        val before = capture("before-word")

        heard.value = 3

        advance(WORD_MILLIS / 3)
        val after = capture("after-word")

        assertTrue(
            "a word landed and the ball did not grow: ${before.dotPixels()} px, " +
                "${after.dotPixels()} px",
            after.dotPixels() > before.dotPixels(),
        )
    }

    private inner class Frame(
        val bitmap: Bitmap,
    ) {
        val size = bitmap.width
        private val pixels =
            IntArray(size * size).also {
                bitmap.getPixels(it, 0, size, 0, 0, size, size)
            }

        fun dotCoverage(): Float = dotPixels().toFloat() / (size * size)

        fun cageCoverage(): Float {
            var painted = 0
            var area = 0
            forEachPixel { x, y ->
                val from = distance(x, y) / ballRadius
                if (from in CAGE_INNER..CAGE_OUTER) {
                    area++
                    if (dot(x, y)) painted++
                }
            }
            return painted.toFloat() / area
        }

        fun dotPixels(): Int {
            var painted = 0
            forEachPixel { x, y -> if (dot(x, y)) painted++ }
            return painted
        }

        fun dotReach(): Float {
            var furthest = 0f
            forEachPixel { x, y -> if (dot(x, y)) furthest = max(furthest, distance(x, y)) }
            return furthest
        }

        fun reach(): FloatArray {
            val reach = floatArrayOf(0f, 0f, 0f, 0f)
            forEachPixel { x, y ->
                if (dot(x, y)) {
                    val out = x - size / 2
                    val down = y - size / 2
                    if (abs(out) >= abs(down)) {
                        val arm = if (out >= 0) 0 else 1
                        reach[arm] = max(reach[arm], abs(out).toFloat())
                    } else {
                        val arm = if (down >= 0) 2 else 3
                        reach[arm] = max(reach[arm], abs(down).toFloat())
                    }
                }
            }
            return reach
        }

        fun changed(other: Frame): Float {
            var moved = 0
            for (index in pixels.indices) {
                if (abs(difference(pixels[index]) - difference(other.pixels[index])) > CHANGE_MARGIN) {
                    moved++
                }
            }
            return moved.toFloat() / pixels.size
        }

        private fun dot(
            x: Int,
            y: Int,
        ): Boolean = difference(pixels[y * size + x]) > DOT_MARGIN

        private fun distance(
            x: Int,
            y: Int,
        ): Float = hypot((x - size / 2).toFloat(), (y - size / 2).toFloat())

        private val ballRadius: Float = size * BALL_FRACTION

        private inline fun forEachPixel(action: (Int, Int) -> Unit) {
            for (y in 0 until size) {
                for (x in 0 until size) action(x, y)
            }
        }
    }

    private fun difference(pixel: Int): Int =
        abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(backdrop.toArgb())) +
            abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(backdrop.toArgb())) +
            abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(backdrop.toArgb()))

    private fun capture(name: String): Frame {
        val picture =
            composeRule
                .onNodeWithTag(VOICE_ORB_TAG)
                .captureToImage()
                .asAndroidBitmap()
        dump(picture, name)
        return Frame(picture)
    }

    private fun dump(
        picture: Bitmap,
        name: String,
    ) {
        val file = File(context.getExternalFilesDir(null), "orb-$name.png")
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val VOICE_QUIET = 0.2f
        const val VOICE_LOUD = 1f

        const val SETTLE_MILLIS = 400L
        const val WORD_MILLIS = 420L

        const val TURN_MILLIS = 3_000L
        const val MIN_TURNED = 0.05f

        const val CHANGE_MARGIN = 60

        const val DOT_MARGIN = 250

        const val MIN_COVERAGE = 0.005f
        const val MAX_COVERAGE = 0.45f
        const val MAX_CAGE_COVERAGE = 0.35f

        const val BALL_FRACTION = 0.44f

        const val CAGE_INNER = 0.62f
        const val CAGE_OUTER = 0.95f

        const val MIN_REACH = 0.36f
        const val MAX_REACH = 0.55f
        const val MAX_LOPSIDED = 1.25f
    }
}
