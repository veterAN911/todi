package com.todi.app.domain.util

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Month

internal object SpokenLexicon {
    val MONTHS: Map<String, Month> =
        mapOf(
            "января" to Month.JANUARY,
            "январь" to Month.JANUARY,
            "февраля" to Month.FEBRUARY,
            "февраль" to Month.FEBRUARY,
            "марта" to Month.MARCH,
            "март" to Month.MARCH,
            "апреля" to Month.APRIL,
            "апрель" to Month.APRIL,
            "мая" to Month.MAY,
            "май" to Month.MAY,
            "июня" to Month.JUNE,
            "июнь" to Month.JUNE,
            "июля" to Month.JULY,
            "июль" to Month.JULY,
            "августа" to Month.AUGUST,
            "август" to Month.AUGUST,
            "сентября" to Month.SEPTEMBER,
            "сентябрь" to Month.SEPTEMBER,
            "октября" to Month.OCTOBER,
            "октябрь" to Month.OCTOBER,
            "ноября" to Month.NOVEMBER,
            "ноябрь" to Month.NOVEMBER,
            "декабря" to Month.DECEMBER,
            "декабрь" to Month.DECEMBER,
        )

    val WEEKDAYS: Map<String, DayOfWeek> =
        mapOf(
            "понедельник" to DayOfWeek.MONDAY,
            "понедельника" to DayOfWeek.MONDAY,
            "понедельнику" to DayOfWeek.MONDAY,
            "вторник" to DayOfWeek.TUESDAY,
            "вторника" to DayOfWeek.TUESDAY,
            "вторнику" to DayOfWeek.TUESDAY,
            "среда" to DayOfWeek.WEDNESDAY,
            "среду" to DayOfWeek.WEDNESDAY,
            "среды" to DayOfWeek.WEDNESDAY,
            "среде" to DayOfWeek.WEDNESDAY,
            "четверг" to DayOfWeek.THURSDAY,
            "четверга" to DayOfWeek.THURSDAY,
            "четвергу" to DayOfWeek.THURSDAY,
            "пятница" to DayOfWeek.FRIDAY,
            "пятницу" to DayOfWeek.FRIDAY,
            "пятницы" to DayOfWeek.FRIDAY,
            "пятнице" to DayOfWeek.FRIDAY,
            "суббота" to DayOfWeek.SATURDAY,
            "субботу" to DayOfWeek.SATURDAY,
            "субботы" to DayOfWeek.SATURDAY,
            "субботе" to DayOfWeek.SATURDAY,
            "воскресенье" to DayOfWeek.SUNDAY,
            "воскресенья" to DayOfWeek.SUNDAY,
            "воскресенью" to DayOfWeek.SUNDAY,
        )

    val DAY_PARTS: Map<String, LocalTime> =
        mapOf(
            "утром" to LocalTime.of(9, 0),
            "днём" to LocalTime.of(14, 0),
            "днем" to LocalTime.of(14, 0),
            "вечером" to LocalTime.of(19, 0),
            "ночью" to LocalTime.of(22, 0),
        )

    const val LUNCH_WORD = "обед"
    val LUNCH_TIME: LocalTime = LocalTime.of(13, 0)

    val AFTERNOON_MARKERS: Set<String> = setOf("дня", "вечера", "вечером", "днём", "днем")
    val MORNING_MARKERS: Set<String> = setOf("утра", "ночи", "ночью", "утром")

    val HOUR_MARKERS: Set<String> = setOf("час", "часа", "часов", "ч")

    val MINUTE_MARKERS: Set<String> = setOf("минуту", "минуты", "минут")

    val CARDINALS: Map<String, Int> =
        mapOf(
            "ноль" to 0,
            "нуль" to 0,
            "один" to 1,
            "одна" to 1,
            "одну" to 1,
            "одно" to 1,
            "одного" to 1,
            "два" to 2,
            "две" to 2,
            "двух" to 2,
            "пара" to 2,
            "пару" to 2,
            "три" to 3,
            "трёх" to 3,
            "трех" to 3,
            "трём" to 3,
            "трем" to 3,
            "четыре" to 4,
            "четырёх" to 4,
            "четырех" to 4,
            "пять" to 5,
            "пяти" to 5,
            "шесть" to 6,
            "шести" to 6,
            "семь" to 7,
            "семи" to 7,
            "восемь" to 8,
            "восьми" to 8,
            "девять" to 9,
            "девяти" to 9,
            "десять" to 10,
            "десяти" to 10,
            "одиннадцать" to 11,
            "одиннадцати" to 11,
            "двенадцать" to 12,
            "двенадцати" to 12,
            "тринадцать" to 13,
            "тринадцати" to 13,
            "четырнадцать" to 14,
            "четырнадцати" to 14,
            "пятнадцать" to 15,
            "пятнадцати" to 15,
            "шестнадцать" to 16,
            "шестнадцати" to 16,
            "семнадцать" to 17,
            "семнадцати" to 17,
            "восемнадцать" to 18,
            "восемнадцати" to 18,
            "девятнадцать" to 19,
            "девятнадцати" to 19,
            "двадцать" to 20,
            "двадцати" to 20,
            "тридцать" to 30,
            "тридцати" to 30,
            "сорок" to 40,
            "сорока" to 40,
            "пятьдесят" to 50,
            "пятидесяти" to 50,
        )

    val ORDINALS: Map<String, Int> =
        mapOf(
            "первого" to 1,
            "второго" to 2,
            "третьего" to 3,
            "четвёртого" to 4,
            "четвертого" to 4,
            "пятого" to 5,
            "шестого" to 6,
            "седьмого" to 7,
            "восьмого" to 8,
            "девятого" to 9,
            "десятого" to 10,
            "одиннадцатого" to 11,
            "двенадцатого" to 12,
            "тринадцатого" to 13,
            "четырнадцатого" to 14,
            "пятнадцатого" to 15,
            "шестнадцатого" to 16,
            "семнадцатого" to 17,
            "восемнадцатого" to 18,
            "девятнадцатого" to 19,
            "двадцатого" to 20,
            "тридцатого" to 30,
        )

    val ORDINAL_TENS: Map<String, Int> =
        mapOf(
            "двадцать" to 20,
            "тридцать" to 30,
        )
}
