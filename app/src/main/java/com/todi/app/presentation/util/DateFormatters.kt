package com.todi.app.presentation.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateFormatters {
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM")
    private val FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("LLLL yyyy")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val SHORT_DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")

    fun dayMonth(
        date: LocalDate,
        locale: Locale,
    ): String = date.format(DAY_MONTH.withLocale(locale))

    fun shortDayMonth(
        date: LocalDate,
        locale: Locale,
    ): String = date.format(SHORT_DAY_MONTH.withLocale(locale))

    fun fullDate(
        date: LocalDate,
        locale: Locale,
    ): String = date.format(FULL_DATE.withLocale(locale))

    fun monthYear(
        month: YearMonth,
        locale: Locale,
    ): String = month.format(MONTH_YEAR.withLocale(locale))

    fun time(time: LocalTime): String = time.format(TIME)

    fun weekdayNarrow(
        dayOfWeek: DayOfWeek,
        locale: Locale,
    ): String = dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
}
