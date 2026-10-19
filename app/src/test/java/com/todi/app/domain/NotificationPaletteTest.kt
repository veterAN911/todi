package com.todi.app.domain

import com.todi.app.domain.util.NotificationPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPaletteTest {
    @Test
    fun everySwatch_isLegibleOnBothBackgrounds() {
        val unreadable = NotificationPalette.swatches.filterNot(NotificationPalette::isReadableOnBoth)

        assertEquals(emptyList<Int>(), unreadable)
    }

    @Test
    fun theSwatches_areDistinct() {
        assertEquals(
            NotificationPalette.swatches.size,
            NotificationPalette.swatches.toSet().size,
        )
    }

    @Test
    fun thePalette_holdsSixteen() {
        assertEquals(16, NotificationPalette.swatches.size)
    }

    @Test
    fun thePalette_isWholeRows() {
        assertEquals(0, NotificationPalette.swatches.size % NotificationPalette.SWATCHES_PER_ROW)
    }

    @Test
    fun contrast_isOneForAColourAgainstItself() {
        assertEquals(1.0, NotificationPalette.contrastAgainst(0xFF123456.toInt(), 0xFF123456.toInt()), 0.001)
    }

    @Test
    fun contrast_ofWhiteOnBlack_isTheKnownMaximum() {
        assertEquals(
            21.0,
            NotificationPalette.contrastAgainst(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
            0.01,
        )
    }

    @Test
    fun aPaleColour_failsOnTheLightBackground() {
        assertFalse(NotificationPalette.isReadableOnBoth(0xFFFFFB00.toInt()))
    }

    @Test
    fun aDeepColour_failsOnTheDarkBackground() {
        assertFalse(NotificationPalette.isReadableOnBoth(0xFF000080.toInt()))
    }
}
