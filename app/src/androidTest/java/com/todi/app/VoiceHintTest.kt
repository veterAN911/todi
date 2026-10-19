package com.todi.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.todi.app.domain.model.ThemeMode
import com.todi.app.presentation.theme.TodiTheme
import com.todi.app.presentation.voice.VOICE_HINT_TAG
import com.todi.app.presentation.voice.VoiceHint
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VoiceHintTest {
    private val backdrop = Color(0xFF141824)

    @get:Rule
    val composeRule = createComposeRule()

    private fun showHint() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TodiTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.background(backdrop)) {
                    VoiceHint(progress = 0f)
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theHint_isDrawn() {
        showHint()
        val frame = capture("drawn")

        assertTrue("nothing was drawn at all", frame.painted() > 300)
    }

    @Test
    fun theHint_isSmall() {
        showHint()
        val frame = capture("small")

        assertTrue(
            "the hint covers ${frame.coverage()} of its canvas — that is a panel, not a hint",
            frame.coverage() < 0.25f,
        )
    }

    @Test
    fun theArrow_pointsUp() {
        showHint()
        val frame = capture("arrow")

        val top = frame.firstPaintedRow(minimum = ARROW_ROW)
        assertTrue("the arrow's apex was not found", top >= 0)
        val widthAtTop = frame.paintedWidth(top)
        val widthBelow = frame.paintedWidth(top + ARROW_SPREAD_ROWS)
        assertTrue(
            "the picture is ${widthAtTop}px wide at its top and ${widthBelow}px " +
                "${ARROW_SPREAD_ROWS}px lower — that is not an arrow pointing up",
            widthBelow > widthAtTop * 1.5f,
        )
    }

    @Test
    fun theHint_shimmers() {
        showHint()
        val first = capture("shimmer-a")
        composeRule.mainClock.advanceTimeBy(SHIMMER_MILLIS)
        val second = capture("shimmer-b")

        assertTrue(
            "the hint did not move in ${SHIMMER_MILLIS}ms — it is not shimmering",
            first.changed(second) > 0.005f,
        )
    }

    private inner class Frame(
        val bitmap: Bitmap,
    ) {
        val width = bitmap.width
        val height = bitmap.height
        private val pixels =
            IntArray(width * height).also {
                bitmap.getPixels(it, 0, width, 0, 0, width, height)
            }

        fun painted(): Int {
            var painted = 0
            forEachPixel { x, y -> if (bead(x, y)) painted++ }
            return painted
        }

        fun coverage(): Float = painted().toFloat() / (width * height)

        fun firstPaintedRow(minimum: Int): Int {
            for (y in 0 until height) {
                var painted = 0
                for (x in 0 until width) if (bead(x, y)) painted++
                if (painted >= minimum) return y
            }
            return -1
        }

        fun paintedWidth(row: Int): Int {
            var left = width
            var right = -1
            for (x in 0 until width) {
                if (bead(x, row)) {
                    left = min(left, x)
                    right = max(right, x)
                }
            }
            return if (right < 0) 0 else right - left + 1
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

        private fun bead(
            x: Int,
            y: Int,
        ): Boolean = difference(pixels[y * width + x]) > BEAD_MARGIN

        private inline fun forEachPixel(action: (Int, Int) -> Unit) {
            for (y in 0 until height) {
                for (x in 0 until width) action(x, y)
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
                .onNodeWithTag(VOICE_HINT_TAG)
                .captureToImage()
                .asAndroidBitmap()
        dump(picture, name)
        return Frame(picture)
    }

    private fun dump(
        bitmap: Bitmap,
        name: String,
    ) {
        val directory =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getExternalFilesDir(null) ?: return
        val file = File(directory, "hint-$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val ARROW_ROW = 12

        const val ARROW_SPREAD_ROWS = 14

        const val SHIMMER_MILLIS = 400L

        const val BEAD_MARGIN = 180

        const val CHANGE_MARGIN = 40
    }
}
