package com.todi.app.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodiDestinationsTest {
    @Test
    fun anUntouchedSetting_showsTheDefaultFour() {
        assertEquals(
            listOf(
                TodiDestination.TODAY,
                TodiDestination.TOMORROW,
                TodiDestination.CALENDAR,
                TodiDestination.COMPLETED,
            ),
            bottomBarDestinations(stored = null),
        )
    }

    @Test
    fun theDefaults_areRealSectionsInAppOrder() {
        val order = TodiDestination.entries.map { it.route }
        assertEquals(DEFAULT_BOTTOM_BAR_ROUTES.sortedBy { order.indexOf(it) }, DEFAULT_BOTTOM_BAR_ROUTES)
        assertTrue(DEFAULT_BOTTOM_BAR_ROUTES.all { it in ALL_ROUTES })
    }

    @Test
    fun anEmptiedBar_showsNothing() {
        assertEquals(emptyList<TodiDestination>(), bottomBarDestinations(stored = emptyList()))
    }

    @Test
    fun aStoredRoute_mapsToItsSection() {
        assertEquals(
            listOf(TodiDestination.GLOBAL, TodiDestination.SETTINGS),
            bottomBarDestinations(stored = listOf("settings", "global")),
        )
    }
}
