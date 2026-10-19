package com.todi.app.domain

import com.todi.app.domain.util.ParsedTask
import com.todi.app.domain.util.TaskPhraseParser
import com.todi.app.domain.util.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskPhraseParserTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val sunday = ZonedDateTime.of(2026, 9, 20, 10, 0, 0, 0, zone)
    private val thursday = ZonedDateTime.of(2026, 9, 24, 10, 0, 0, 0, zone)
    private val december = ZonedDateTime.of(2026, 12, 28, 10, 0, 0, 0, zone)
    private val lateEvening = ZonedDateTime.of(2026, 9, 20, 23, 30, 0, 0, zone)

    private val today: LocalDate = sunday.toLocalDate()

    private fun parse(
        text: String,
        now: ZonedDateTime = sunday,
    ): List<ParsedTask> = TaskPhraseParser.parse(text, now)

    private fun one(
        text: String,
        now: ZonedDateTime = sunday,
    ): ParsedTask = parse(text, now).single()

    private fun titles(
        text: String,
        now: ZonedDateTime = sunday,
    ): List<String> = parse(text, now).map { it.title }

    @Test
    fun todayWord_resolvesToToday() {
        val task = one("сегодня купить хлеб")

        assertEquals(today, task.date)
        assertEquals("купить хлеб", task.title)
        assertEquals("сегодня", task.datePhrase)
    }

    @Test
    fun tomorrowAndDayAfterTomorrow() {
        assertEquals(today.plusDays(1), one("завтра купить хлеб").date)
        assertEquals(today.plusDays(2), one("послезавтра купить хлеб").date)
    }

    @Test
    fun throughOneDay() {
        assertEquals(today.plusDays(1), one("купить хлеб через день").date)
    }

    @Test
    fun throughThreeDays() {
        val task = one("купить хлеб через три дня")

        assertEquals(today.plusDays(3), task.date)
        assertEquals("купить хлеб", task.title)
        assertEquals("через три дня", task.datePhrase)
    }

    @Test
    fun throughAWeek() {
        assertEquals(today.plusDays(7), one("купить хлеб через неделю").date)
    }

    @Test
    fun throughOneAndAHalfWeeks() {
        assertEquals(today.plusDays(10), one("купить хлеб через полторы недели").date)
    }

    @Test
    fun throughAMonth() {
        assertEquals(today.plusMonths(1), one("купить хлеб через месяц").date)
    }

    @Test
    fun throughTwoMonths() {
        assertEquals(today.plusMonths(2), one("купить хлеб через два месяца").date)
    }

    @Test
    fun weekdayFromSunday_isTomorrow() {
        assertEquals(LocalDate.of(2026, 9, 21), one("в понедельник купить хлеб").date)
    }

    @Test
    fun weekdayLaterInTheWeek() {
        assertEquals(LocalDate.of(2026, 9, 24), one("в четверг купить хлеб").date)
    }

    @Test
    fun weekdayWithPrepositionFor() {
        assertEquals(LocalDate.of(2026, 9, 25), one("купить подарок на пятницу").date)
    }

    @Test
    fun thisWeekday_includesToday() {
        assertEquals(LocalDate.of(2026, 9, 24), one("в этот четверг купить хлеб").date)
    }

    @Test
    fun nextWeekday_isStrictlyAfterToday() {
        assertEquals(LocalDate.of(2026, 9, 24), one("в следующий четверг купить хлеб").date)
    }

    @Test
    fun nextWeekdayOnThatWeekday_skipsAWeek() {
        assertEquals(LocalDate.of(2026, 10, 1), one("в следующий четверг купить хлеб", thursday).date)
    }

    @Test
    fun dayOfMonthWithMonthName() {
        val task = one("купить хлеб 25 сентября")

        assertEquals(LocalDate.of(2026, 9, 25), task.date)
        assertEquals("купить хлеб", task.title)
    }

    @Test
    fun dayOfMonthWithGoSuffix() {
        assertEquals(LocalDate.of(2026, 9, 25), one("купить хлеб 25-го сентября").date)
    }

    @Test
    fun dayOfMonthSpokenAsOrdinal() {
        assertEquals(LocalDate.of(2026, 9, 25), one("купить хлеб двадцать пятого сентября").date)
    }

    @Test
    fun dottedDate() {
        assertEquals(LocalDate.of(2026, 9, 25), one("купить хлеб 25.09").date)
    }

    @Test
    fun slashedDate() {
        assertEquals(LocalDate.of(2026, 9, 25), one("купить хлеб 25/09").date)
    }

    @Test
    fun dateWithYear() {
        val task = one("купить хлеб 25 сентября 2027")

        assertEquals(LocalDate.of(2027, 9, 25), task.date)

        assertEquals("купить хлеб", task.title)
    }

    @Test
    fun dateWithYearWord() {
        assertEquals(LocalDate.of(2027, 9, 25), one("купить хлеб 25 сентября 2027 года").date)
    }

    @Test
    fun passedDateWithoutYear_rollsToNextYear() {
        assertEquals(LocalDate.of(2027, 9, 25), one("купить хлеб 25 сентября", december).date)
    }

    @Test
    fun endOfThisMonth() {
        assertEquals(LocalDate.of(2026, 9, 30), one("сдать отчёт в конце месяца").date)
    }

    @Test
    fun thisMonth_meansItsLastDay() {
        assertEquals(LocalDate.of(2026, 9, 30), one("сдать отчёт в этом месяце").date)
    }

    @Test
    fun nextMonth_meansItsFirstDay() {
        assertEquals(LocalDate.of(2026, 10, 1), one("сдать отчёт в следующем месяце").date)
    }

    @Test
    fun thisWeek_meansItsEnd() {
        assertEquals(today, one("сдать отчёт на этой неделе").date)
    }

    @Test
    fun nextWeek_meansItsMonday() {
        assertEquals(LocalDate.of(2026, 9, 21), one("сдать отчёт на следующей неделе").date)
    }

    @Test
    fun clockWithColon() {
        assertEquals(LocalTime.of(15, 0), one("позвонить маме в 15:00").time)
    }

    @Test
    fun clockWithDot() {
        assertEquals(LocalTime.of(15, 30), one("позвонить маме в 15.30").time)
    }

    @Test
    fun bareHourInTheAfternoon() {
        assertEquals(LocalTime.of(15, 0), one("позвонить маме в три").time)
    }

    @Test
    fun hourWithHourWord() {
        assertEquals(LocalTime.of(15, 0), one("позвонить маме в три часа").time)
    }

    @Test
    fun hourAndMinutes() {
        assertEquals(LocalTime.of(15, 30), one("позвонить маме в три тридцать").time)
    }

    @Test
    fun afternoonMarkerKeepsTheHour() {
        assertEquals(LocalTime.of(15, 0), one("позвонить маме в три часа дня").time)
    }

    @Test
    fun morningMarkerCancelsTheShift() {
        assertEquals(LocalTime.of(3, 0), one("позвонить маме в три часа ночи").time)
    }

    @Test
    fun eveningHourIsLeftAlone() {
        assertEquals(LocalTime.of(18, 0), one("позвонить маме в 18:00").time)
    }

    @Test
    fun halfPastTheHour() {
        assertEquals(LocalTime.of(17, 30), one("позвонить маме в половине шестого").time)
    }

    @Test
    fun minutesToTheHour() {
        assertEquals(LocalTime.of(17, 45), one("позвонить маме без пятнадцати шесть").time)
    }

    @Test
    fun partOfDayOnItsOwn() {
        val task = one("утром позвонить маме")

        assertEquals(LocalTime.of(9, 0), task.time)
        assertEquals("позвонить маме", task.title)
    }

    @Test
    fun lunchNeedsItsPreposition() {
        assertEquals(LocalTime.of(13, 0), one("позвонить маме в обед").time)
    }

    @Test
    fun throughAnHour() {
        assertEquals(LocalTime.of(11, 0), one("позвонить маме через час").time)
    }

    @Test
    fun throughAnHourAcrossMidnight_movesTheDay() {
        val task = one("позвонить маме через час", lateEvening)

        assertEquals(LocalTime.of(0, 30), task.time)
        assertEquals(1, task.relativeDayShift)
        assertNull(task.date)
    }

    @Test
    fun namedDateWinsOverACrossingRelativeTime() {
        val task = one("завтра позвонить маме через час", lateEvening)

        assertEquals(today.plusDays(1), task.date)
        assertEquals(0, task.relativeDayShift)
    }

    @Test
    fun throughHalfAnHour() {
        assertEquals(LocalTime.of(10, 30), one("позвонить маме через полчаса").time)
    }

    @Test
    fun throughTwentyMinutes() {
        assertEquals(LocalTime.of(10, 20), one("позвонить маме через двадцать минут").time)
    }

    @Test
    fun andBeforeAnObject_isOneTask() {
        assertEquals(listOf("купить хлеб и молоко"), titles("купить хлеб и молоко"))
    }

    @Test
    fun andBeforeAnInfinitive_isTwoTasks() {
        assertEquals(listOf("купить хлеб", "позвонить маме"), titles("купить хлеб и позвонить маме"))
    }

    @Test
    fun andBeforeAnImperative_isTwoTasks() {
        assertEquals(listOf("купить хлеб", "позвони маме"), titles("купить хлеб и позвони маме"))
    }

    @Test
    fun andBeforeANumeral_isOneTask() {
        assertEquals(listOf("купить хлеб и пять яблок"), titles("купить хлеб и пять яблок"))
    }

    @Test
    fun andBeforeALookalikeWord_isOneTask() {
        assertEquals(listOf("купить хлеб и почти всё"), titles("купить хлеб и почти всё"))
    }

    @Test
    fun andBeforeADateWord_isTwoTasks() {
        assertEquals(
            listOf("купить хлеб", "позвонить маме"),
            titles("купить хлеб и завтра позвонить маме"),
        )
    }

    @Test
    fun sequenceMarker_splits() {
        assertEquals(
            listOf("купить хлеб", "позвонить маме"),
            titles("купить хлеб, потом позвонить маме"),
        )
    }

    @Test
    fun afterThis_splits() {
        assertEquals(
            listOf("купить хлеб", "позвонить маме"),
            titles("купить хлеб после этого позвонить маме"),
        )
    }

    @Test
    fun fullStop_splits() {
        assertEquals(
            listOf("купить хлеб", "позвонить маме"),
            titles("купить хлеб. позвонить маме"),
        )
    }

    @Test
    fun commaBeforeAnObject_isOneTask() {
        assertEquals(listOf("купить хлеб, молоко и сыр"), titles("купить хлеб, молоко и сыр"))
    }

    @Test
    fun commaBeforeAnInfinitive_isTwoTasks() {
        assertEquals(
            listOf("купить хлеб", "позвонить маме"),
            titles("купить хлеб, позвонить маме"),
        )
    }

    @Test
    fun commaBeforeATrailingDate_keepsTheDateOnTheTask() {
        val task = one("купить хлеб, завтра")

        assertEquals(today.plusDays(1), task.date)
        assertEquals("купить хлеб", task.title)
    }

    @Test
    fun commaBeforeADateAndAVerb_splits() {
        val tasks = parse("купить хлеб, завтра позвонить маме")

        assertEquals(listOf("купить хлеб", "позвонить маме"), tasks.map { it.title })
        assertNull(tasks[0].date)
        assertEquals(today.plusDays(1), tasks[1].date)
    }

    @Test
    fun dateIsInheritedByFollowingTasks() {
        val tasks = parse("завтра купить хлеб, потом позвонить маме")

        assertEquals(listOf(today.plusDays(1), today.plusDays(1)), tasks.map { it.date })
        assertEquals(listOf("купить хлеб", "позвонить маме"), tasks.map { it.title })
    }

    @Test
    fun aLaterDateOverridesTheInheritedOne() {
        val tasks = parse("завтра купить хлеб, потом в пятницу позвонить маме")

        assertEquals(listOf(today.plusDays(1), LocalDate.of(2026, 9, 25)), tasks.map { it.date })
    }

    @Test
    fun aSliceThatIsOnlyADate_isNotATask() {
        assertTrue(parse("завтра").isEmpty())
    }

    @Test
    fun blankTranscript_isNoTasks() {
        assertTrue(parse("   ").isEmpty())
    }

    @Test
    fun pluralAfterANumber_isNotADate() {
        val task = one("купить три яблока")

        assertNull(task.date)
        assertEquals("купить три яблока", task.title)
        assertNull(task.datePhrase)
    }

    @Test
    fun digitsWithoutAMonth_areNotADate() {
        val task = one("купить 25 яблок")

        assertNull(task.date)
        assertEquals("купить 25 яблок", task.title)
    }

    @Test
    fun lastMonth_isNotADate() {
        val task = one("сдать отчёт за прошлый месяц")

        assertNull(task.date)
        assertEquals("сдать отчёт за прошлый месяц", task.title)
    }

    @Test
    fun anAmountIsNotATime() {
        val task = one("оплатить счёт в пять тысяч рублей")

        assertNull(task.time)
        assertEquals("оплатить счёт в пять тысяч рублей", task.title)
    }

    @Test
    fun aTimeWithoutADate_leavesTheDateUnnamed() {
        val task = one("позвонить маме в пять")

        assertNull(task.date)
        assertEquals(LocalTime.of(17, 0), task.time)
        assertEquals("позвонить маме", task.title)
    }

    @Test
    fun aDateInTheMiddleOfAPhrase_isCutOutOfTheTitle() {
        val task = one("написать отчёт к пятнице")

        assertEquals(LocalDate.of(2026, 9, 25), task.date)
        assertEquals("написать отчёт", task.title)
    }

    @Test
    fun bothDateAndTimeComeOutOfTheTitle() {
        val task = one("позвонить маме в пятницу в 15:00")

        assertEquals(LocalDate.of(2026, 9, 25), task.date)
        assertEquals(LocalTime.of(15, 0), task.time)
        assertEquals("позвонить маме", task.title)
        assertEquals("в пятницу", task.datePhrase)
        assertEquals("в 15:00", task.timePhrase)
    }

    @Test
    fun trailingCommaAfterADateDoesNotStayInTheTitle() {
        assertEquals("купить хлеб", one("купить хлеб, завтра").title)
    }

    @Test
    fun aDateAtTheStart_keepsTheRestOfThePhrase() {
        assertEquals("купить хлеб и молоко", one("завтра купить хлеб и молоко").title)
    }

    @Test
    fun nonRussianPhrase_staysOneTask() {
        val tasks =
            TaskPhraseParser.parse(
                "buy bread tomorrow at three",
                sunday,
                VoiceLanguage.OTHER,
            )

        assertEquals(listOf("buy bread tomorrow at three"), tasks.map { it.title })
        assertNull(tasks.single().date)
        assertNull(tasks.single().time)
    }
}
