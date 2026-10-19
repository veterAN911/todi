package com.todi.app.presentation.calendar

import java.time.LocalDate
import java.time.YearMonth

object MonthGrid {
    const val CELLS = 42

    fun days(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1)
        val mondayOffset = first.dayOfWeek.value - 1
        val start = first.minusDays(mondayOffset.toLong())
        return (0 until CELLS).map { start.plusDays(it.toLong()) }
    }

    fun weekdayHeaders(): List<Int> = (1..7).toList()
}
