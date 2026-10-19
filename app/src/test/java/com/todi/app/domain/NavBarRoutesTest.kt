package com.todi.app.domain

import com.todi.app.domain.util.NavBarRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavBarRoutesTest {
    private val known = listOf("today", "tomorrow", "global", "calendar", "completed", "shared", "settings")
    private val defaults = listOf("today", "tomorrow", "calendar", "completed")

    @Test
    fun nothingStored_isNotTheSameAsAnEmptyBar() {
        assertNull(NavBarRoutes.decode(null))
        assertEquals(emptyList<String>(), NavBarRoutes.decode(""))
    }

    @Test
    fun blankStoredValue_isAnEmptyBarToo() {
        assertEquals(emptyList<String>(), NavBarRoutes.decode("   "))
        assertEquals(emptyList<String>(), NavBarRoutes.decode(",,,"))
    }

    @Test
    fun storedRoutes_comeBackInOrder() {
        assertEquals(listOf("today", "tomorrow"), NavBarRoutes.decode("today,tomorrow"))
    }

    @Test
    fun straySpacesEmptyItemsAndRepeats_areDropped() {
        assertEquals(
            listOf("today", "calendar"),
            NavBarRoutes.decode(" today , ,today, calendar ,,"),
        )
    }

    @Test
    fun aStoredValue_roundTrips() {
        val routes = listOf("global", "settings")
        assertEquals(routes, NavBarRoutes.decode(NavBarRoutes.encode(routes)))
        assertEquals(emptyList<String>(), NavBarRoutes.decode(NavBarRoutes.encode(emptyList())))
    }

    @Test
    fun nothingStored_fallsBackToTheDefaults() {
        assertEquals(defaults, NavBarRoutes.resolve(null, defaults, known))
    }

    @Test
    fun anEmptyBar_staysEmpty() {
        assertEquals(emptyList<String>(), NavBarRoutes.resolve(emptyList(), defaults, known))
    }

    @Test
    fun unknownRoutes_areDroppedRatherThanShownAsHoles() {
        assertEquals(
            listOf("today", "calendar"),
            NavBarRoutes.resolve(listOf("today", "gone", "calendar"), defaults, known),
        )
    }

    @Test
    fun theOrderIsTheAppsNotTheStoredOne() {
        assertEquals(
            listOf("tomorrow", "calendar", "settings"),
            NavBarRoutes.resolve(listOf("settings", "calendar", "tomorrow"), defaults, known),
        )
    }

    @Test
    fun theDefaultsAreFilteredToo() {
        assertEquals(
            listOf("today", "calendar"),
            NavBarRoutes.resolve(null, listOf("today", "gone", "calendar"), known),
        )
    }

    @Test
    fun theFirstTick_materialisesTheDefaults() {
        assertEquals(
            listOf("today", "tomorrow", "completed"),
            NavBarRoutes.toggle(null, "calendar", on = false, defaults, known),
        )
    }

    @Test
    fun theFirstTickOnAnEmptyBar_addsJustThatRoute() {
        assertEquals(
            listOf("global"),
            NavBarRoutes.toggle(emptyList(), "global", on = true, defaults, known),
        )
    }

    @Test
    fun tickingOn_aRouteTheBarDoesNotHave() {
        assertEquals(
            listOf("today", "tomorrow", "calendar", "completed", "settings"),
            NavBarRoutes.toggle(defaults, "settings", on = true, defaults, known),
        )
    }

    @Test
    fun untickingTheLastRoute_leavesAnEmptyBar() {
        assertEquals(
            emptyList<String>(),
            NavBarRoutes.toggle(listOf("today"), "today", on = false, defaults, known),
        )
    }

    @Test
    fun tickingOn_aRouteAlreadyThere_changesNothing() {
        assertEquals(defaults, NavBarRoutes.toggle(defaults, "today", on = true, defaults, known))
    }

    @Test
    fun aRouteOutsideTheKnownList_isNotAdded() {
        assertEquals(defaults, NavBarRoutes.toggle(defaults, "gone", on = true, defaults, known))
    }
}
