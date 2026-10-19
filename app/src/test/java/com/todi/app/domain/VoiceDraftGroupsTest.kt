package com.todi.app.domain

import com.todi.app.domain.util.ParsedTask
import com.todi.app.domain.util.VoiceDraftDay
import com.todi.app.domain.util.VoiceDraftGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class VoiceDraftGroupsTest {
    private val today = LocalDate.of(2026, 9, 20)
    private val tomorrow = today.plusDays(1)

    private fun draft(
        title: String,
        date: LocalDate? = null,
        time: LocalTime? = null,
        relativeDayShift: Int = 0,
    ) = ParsedTask(
        date = date,
        time = time,
        title = title,
        datePhrase = null,
        timePhrase = null,
        relativeDayShift = relativeDayShift,
    )

    @Test
    fun aDraftWithNoDay_landsOnTheDayTheGestureStartedFrom() {
        val groups = VoiceDraftGroups.of(listOf(draft("купить хлеб")), targetDate = today, today = today)

        assertEquals(1, groups.size)
        assertEquals(today, groups.single().date)
        assertEquals(
            "купить хлеб",
            groups
                .single()
                .drafts
                .single()
                .title,
        )
    }

    @Test
    fun aDraftWithNoDay_onTheDateLessScreen_keepsNoDay() {
        val groups = VoiceDraftGroups.of(listOf(draft("купить хлеб")), targetDate = null, today = today)

        assertNull(groups.single().date)
    }

    @Test
    fun aNamedDay_winsOverTheDayOfTheScreen() {
        val groups =
            VoiceDraftGroups.of(
                drafts = listOf(draft("позвонить маме", date = tomorrow)),
                targetDate = today,
                today = today,
            )

        assertEquals(tomorrow, groups.single().date)
    }

    @Test
    fun draftsOfTheSameDay_shareOneGroup_andKeepTheirOrder() {
        val groups =
            VoiceDraftGroups.of(
                drafts =
                    listOf(
                        draft("купить хлеб"),
                        draft("позвонить маме", date = tomorrow),
                        draft("вынести мусор"),
                    ),
                targetDate = today,
                today = today,
            )

        assertEquals(listOf(today, tomorrow), groups.map { it.date })
        assertEquals(listOf("купить хлеб", "вынести мусор"), groups.first().drafts.map { it.title })
    }

    @Test
    fun theNearestDay_isFirst_andTheDateLessGroupIsLast() {
        val inAWeek = today.plusDays(7)
        val groups =
            VoiceDraftGroups.of(
                drafts =
                    listOf(
                        draft("а", date = inAWeek),
                        draft("без даты", date = null),
                        draft("б", date = tomorrow),
                        draft("в", date = today),
                    ),
                targetDate = null,
                today = today,
            )

        assertEquals(listOf(today, tomorrow, inAWeek, null), groups.map { it.date })
    }

    @Test
    fun aRelativeTimeThatCrossesMidnight_movesTheDraftToTheNextDay() {
        val groups =
            VoiceDraftGroups.of(
                drafts = listOf(draft("позвонить", time = LocalTime.of(0, 30), relativeDayShift = 1)),
                targetDate = today,
                today = today,
            )

        assertEquals(tomorrow, groups.single().date)
        assertEquals(
            LocalTime.of(0, 30),
            groups
                .single()
                .drafts
                .single()
                .time,
        )
    }

    @Test
    fun aRelativeTimeOnTheDateLessScreen_shiftsNothing() {
        val groups =
            VoiceDraftGroups.of(
                drafts = listOf(draft("позвонить", time = LocalTime.of(0, 30), relativeDayShift = 1)),
                targetDate = null,
                today = today,
            )

        assertNull(groups.single().date)
    }

    @Test
    fun theDayOfTheScreen_isHeadedToday() {
        val groups = VoiceDraftGroups.of(listOf(draft("купить хлеб")), targetDate = today, today = today)

        assertEquals(VoiceDraftDay.TODAY, groups.single().day)
    }

    @Test
    fun theDayAfter_isHeadedTomorrow() {
        val groups =
            VoiceDraftGroups.of(
                drafts = listOf(draft("позвонить маме", date = tomorrow)),
                targetDate = null,
                today = today,
            )

        assertEquals(VoiceDraftDay.TOMORROW, groups.single().day)
    }

    @Test
    fun everyOtherDay_isHeadedByItsOwnDate() {
        val groups =
            VoiceDraftGroups.of(
                drafts =
                    listOf(
                        draft("вчера", date = today.minusDays(1)),
                        draft("через неделю", date = today.plusDays(7)),
                        draft("без даты", date = null),
                    ),
                targetDate = null,
                today = today,
            )

        assertEquals(listOf(VoiceDraftDay.OTHER, VoiceDraftDay.OTHER, VoiceDraftDay.OTHER), groups.map { it.day })
    }

    @Test
    fun noDrafts_isNoGroups() {
        assertEquals(emptyList<Any>(), VoiceDraftGroups.of(emptyList(), targetDate = today, today = today))
    }
}
