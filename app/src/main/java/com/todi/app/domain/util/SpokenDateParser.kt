package com.todi.app.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZonedDateTime

data class SpokenMatch<T>(
    val value: T,
    val range: IntRange,
)

data class SpokenTime(
    val time: LocalTime,
    val dayShift: Int = 0,
)

object SpokenDateParser {
    private const val AFTERNOON_FROM = 12

    private val CLOCK = Regex("""(\d{1,2}):(\d{2})""")
    private val DOTTED_DATE = Regex("""(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?""")
    private val SLASHED_DATE = Regex("""(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?""")

    private val DIGITS = Regex("""(\d{1,2})(?:-?го)?""")

    private val QUANTITY_WORDS =
        setOf(
            "тысяч",
            "тысячи",
            "тысяча",
            "рублей",
            "рубля",
            "рубль",
            "раз",
            "раза",
            "процентов",
            "процента",
            "штук",
            "штуки",
            "грамм",
            "граммов",
            "килограмм",
            "килограмма",
            "метров",
            "километров",
            "литров",
            "страниц",
            "страницы",
            "капель",
            "баллов",
            "очков",
        )

    private val DATE_PREPOSITIONS = setOf("в", "во", "на", "к", "ко", "до")
    private val TIME_PREPOSITIONS = setOf("в", "во", "к", "ко", "около")
    private const val HALF_WORD = "половине"
    private const val WITHOUT_WORD = "без"

    private val DAY_UNITS = setOf("день", "дня", "дней", "дню", "сутки", "суток")
    private val WEEK_UNITS = setOf("неделю", "недели", "недель", "неделя")
    private val MONTH_UNITS = setOf("месяц", "месяца", "месяцев", "месяцу")

    private val NEXT_WORDS =
        setOf("следующий", "следующую", "следующее", "следующей", "следующем", "следующих")
    private val THIS_WORDS = setOf("этот", "эту", "это", "этой", "этом", "этих")
    private val WEEK_WORDS = setOf("неделе", "неделю", "недели")
    private val MONTH_WORDS = setOf("месяце", "месяца", "месяц")
    private val END_WORDS = setOf("конце", "конец", "конца")

    fun findDate(
        text: String,
        now: ZonedDateTime,
    ): SpokenMatch<LocalDate>? {
        val tokens = tokenizeSpoken(text)
        val today = now.toLocalDate()
        for (i in tokens.indices) {
            if (tokens[i].core.isEmpty()) continue
            dateAt(tokens, i, today)?.let { return it }
        }
        return null
    }

    fun findTime(
        text: String,
        now: ZonedDateTime,
    ): SpokenMatch<SpokenTime>? {
        val tokens = tokenizeSpoken(text)
        val nowTime = now.toLocalTime()
        for (i in tokens.indices) {
            if (tokens[i].core.isEmpty()) continue
            timeAt(tokens, i, nowTime)?.let { return it }
        }
        return null
    }

    private fun dateAt(
        tokens: List<SpokenToken>,
        i: Int,
        today: LocalDate,
    ): SpokenMatch<LocalDate>? {
        when (val core = tokens[i].core) {
            "сегодня" -> return tokens.match(i, i, today)
            "завтра" -> return tokens.match(i, i, today.plusDays(1))
            "послезавтра" -> return tokens.match(i, i, today.plusDays(2))
            "через" -> return relativeDate(tokens, i, today)
        }

        if (tokens[i].core.isIn(DATE_PREPOSITIONS)) {
            dateAfterPreposition(tokens, i, today)?.let { return it }
        }

        SpokenLexicon.WEEKDAYS[tokens[i].core]?.let { day ->
            return weekday(tokens, i, i, today, day, strictlyAfterToday = false)
        }

        return calendarDate(tokens, dayIndex = i, from = i, today = today)
    }

    private fun dateAfterPreposition(
        tokens: List<SpokenToken>,
        i: Int,
        today: LocalDate,
    ): SpokenMatch<LocalDate>? {
        val next = tokens.coreAt(i + 1) ?: return null
        val after = tokens.coreAt(i + 2)

        if (next.isIn(END_WORDS)) {
            val monthAt = if (after.isIn(THIS_WORDS) || after.isIn(NEXT_WORDS)) i + 3 else i + 2
            if (tokens.coreAt(monthAt).isIn(MONTH_WORDS)) {
                val shift = if (after.isIn(NEXT_WORDS)) 1 else 0
                val end = YearMonth.from(today).plusMonths(shift.toLong()).atEndOfMonth()
                return tokens.match(i, monthAt, end)
            }
        }

        if (next.isIn(THIS_WORDS)) {
            if (after.isIn(WEEK_WORDS)) return tokens.match(i, i + 2, endOfWeek(today))
            if (after.isIn(MONTH_WORDS)) {
                return tokens.match(i, i + 2, YearMonth.from(today).atEndOfMonth())
            }
            SpokenLexicon.WEEKDAYS[after]?.let { day ->
                return weekday(tokens, i, i + 2, today, day, strictlyAfterToday = false)
            }
        }

        if (next.isIn(NEXT_WORDS)) {
            if (after.isIn(WEEK_WORDS)) {
                return tokens.match(i, i + 2, endOfWeek(today).plusDays(1))
            }
            if (after.isIn(MONTH_WORDS)) {
                return tokens.match(i, i + 2, YearMonth.from(today).plusMonths(1).atDay(1))
            }
            SpokenLexicon.WEEKDAYS[after]?.let { day ->
                return weekday(tokens, i, i + 2, today, day, strictlyAfterToday = true)
            }
        }

        SpokenLexicon.WEEKDAYS[next]?.let { day ->
            return weekday(tokens, i, i + 1, today, day, strictlyAfterToday = false)
        }

        when (next) {
            "сегодня" -> return tokens.match(i, i + 1, today)
            "завтра" -> return tokens.match(i, i + 1, today.plusDays(1))
            "послезавтра" -> return tokens.match(i, i + 1, today.plusDays(2))
        }

        if (tokens[i].core.isIn(TIME_PREPOSITIONS) && DOTTED_DATE.matches(next)) return null

        return calendarDate(tokens, dayIndex = i + 1, from = i, today = today)
    }

    private fun relativeDate(
        tokens: List<SpokenToken>,
        i: Int,
        today: LocalDate,
    ): SpokenMatch<LocalDate>? {
        val next = tokens.coreAt(i + 1) ?: return null

        if (next == "полторы" || next == "полтора") {
            val unit = tokens.coreAt(i + 2)
            return when {
                unit.isIn(WEEK_UNITS) -> tokens.match(i, i + 2, today.plusDays(10))
                unit.isIn(MONTH_UNITS) -> tokens.match(i, i + 2, today.plusMonths(1).plusDays(15))
                else -> null
            }
        }

        if (next.isIn(WEEK_UNITS)) return tokens.match(i, i + 1, today.plusWeeks(1))
        if (next.isIn(MONTH_UNITS)) return tokens.match(i, i + 1, today.plusMonths(1))
        if (next.isIn(DAY_UNITS)) return tokens.match(i, i + 1, today.plusDays(1))

        val (amount, used) = readNumber(tokens, i + 1) ?: return null
        val unitIndex = i + 1 + used
        val unit = tokens.coreAt(unitIndex) ?: return null
        val date =
            when {
                unit.isIn(DAY_UNITS) -> today.plusDays(amount.toLong())
                unit.isIn(WEEK_UNITS) -> today.plusWeeks(amount.toLong())
                unit.isIn(MONTH_UNITS) -> today.plusMonths(amount.toLong())
                else -> return null
            }
        return tokens.match(i, unitIndex, date)
    }

    private fun calendarDate(
        tokens: List<SpokenToken>,
        dayIndex: Int,
        from: Int,
        today: LocalDate,
    ): SpokenMatch<LocalDate>? {
        val core = tokens.coreAt(dayIndex) ?: return null

        DOTTED_DATE.matchEntire(core)?.let { m ->
            val full = m.groupValues[3].isNotEmpty()
            val nextCore = tokens.coreAt(dayIndex + 1)

            if (!full && nextCore.isIn(SpokenLexicon.AFTERNOON_MARKERS)) return null
            return buildDate(
                day = m.groupValues[1].toInt(),
                month = m.groupValues[2].toInt(),
                year = m.groupValues[3].toIntOrNull()?.let(::expandYear),
                tokens = tokens,
                from = from,
                to = dayIndex,
                today = today,
            )
        }

        SLASHED_DATE.matchEntire(core)?.let { m ->
            return buildDate(
                day = m.groupValues[1].toInt(),
                month = m.groupValues[2].toInt(),
                year = m.groupValues[3].toIntOrNull()?.let(::expandYear),
                tokens = tokens,
                from = from,
                to = dayIndex,
                today = today,
            )
        }

        DIGITS.matchEntire(core)?.let { m ->
            val month = tokens.monthAt(dayIndex + 1) ?: return null
            val day = m.groupValues[1].toIntOrNull() ?: return null

            var end = dayIndex + 1
            var year: Int? = null
            val yearToken = tokens.coreAt(dayIndex + 2)
            if (yearToken != null) {
                val digitsYear = yearToken.toIntOrNull()?.takeIf { it in 1000..9999 }
                when {
                    digitsYear != null -> {
                        year = digitsYear
                        end = dayIndex + 2
                        if (tokens.coreAt(dayIndex + 3).isIn(YEAR_WORDS_IGNORED)) end = dayIndex + 3
                    }

                    yearToken.isIn(YEAR_WORDS_IGNORED) -> {
                        end = dayIndex + 2
                    }
                }
            }

            return buildDate(
                day = day,
                month = month.value,
                year = year,
                tokens = tokens,
                from = from,
                to = end,
                today = today,
            )
        }

        val (ordinal, used) = readOrdinal(tokens, dayIndex) ?: return null
        val month = tokens.monthAt(dayIndex + used) ?: return null
        return buildDate(
            day = ordinal,
            month = month.value,
            year = null,
            tokens = tokens,
            from = from,
            to = dayIndex + used,
            today = today,
        )
    }

    private fun buildDate(
        day: Int,
        month: Int,
        year: Int?,
        tokens: List<SpokenToken>,
        from: Int,
        to: Int,
        today: LocalDate,
    ): SpokenMatch<LocalDate>? {
        if (month !in 1..12) return null
        val monthValue = Month.of(month)
        val date =
            if (year != null) {
                safeDate(year, monthValue, day)
            } else {
                upcomingDate(day, monthValue, today)
            } ?: return null
        return tokens.match(from, to, date)
    }

    private fun weekday(
        tokens: List<SpokenToken>,
        from: Int,
        to: Int,
        today: LocalDate,
        day: DayOfWeek,
        strictlyAfterToday: Boolean,
    ): SpokenMatch<LocalDate> {
        val ahead = (day.value - today.dayOfWeek.value + 7) % 7
        val shift = if (strictlyAfterToday && ahead == 0) 7 else ahead
        return tokens.match(from, to, today.plusDays(shift.toLong()))
    }

    private fun endOfWeek(date: LocalDate): LocalDate {
        val ahead = (DayOfWeek.SUNDAY.value - date.dayOfWeek.value + 7) % 7
        return date.plusDays(ahead.toLong())
    }

    private fun safeDate(
        year: Int,
        month: Month,
        day: Int,
    ): LocalDate? {
        if (day < 1 || day > YearMonth.of(year, month).lengthOfMonth()) return null
        return LocalDate.of(year, month, day)
    }

    private fun upcomingDate(
        day: Int,
        month: Month,
        today: LocalDate,
    ): LocalDate? {
        for (offset in 0..8) {
            val candidate = safeDate(today.year + offset, month, day) ?: continue
            if (!candidate.isBefore(today)) return candidate
        }
        return null
    }

    private fun expandYear(year: Int): Int = if (year < 100) 2000 + year else year

    private fun timeAt(
        tokens: List<SpokenToken>,
        i: Int,
        now: LocalTime,
    ): SpokenMatch<SpokenTime>? {
        val core = tokens[i].core

        CLOCK.matchEntire(core)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour > 23 || minute > 59) return null
            return tokens.match(i, i, SpokenTime(LocalTime.of(hour, minute)))
        }

        DOTTED_DATE.matchEntire(core)?.let { m ->
            if (!tokens.coreAt(i + 1).isIn(SpokenLexicon.AFTERNOON_MARKERS)) return null
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour > 23 || minute > 59) return null
            return tokens.match(i, i, SpokenTime(LocalTime.of(hour, minute)))
        }

        if (core == "через") return relativeTime(tokens, i, now)

        if (core == WITHOUT_WORD) return withoutMinutes(tokens, i)

        SpokenLexicon.DAY_PARTS[core]?.let { return tokens.match(i, i, SpokenTime(it)) }

        if (core.isIn(TIME_PREPOSITIONS)) {
            timeAfterPreposition(tokens, i)?.let { return it }
        }

        return null
    }

    private fun timeAfterPreposition(
        tokens: List<SpokenToken>,
        i: Int,
    ): SpokenMatch<SpokenTime>? {
        val next = tokens.coreAt(i + 1) ?: return null

        if (next == SpokenLexicon.LUNCH_WORD) {
            return tokens.match(i, i + 1, SpokenTime(SpokenLexicon.LUNCH_TIME))
        }

        if (next == WITHOUT_WORD) return withoutMinutes(tokens, i + 1, from = i)

        if (next == HALF_WORD) {
            val (ordinal, used) = readOrdinal(tokens, i + 2) ?: return null
            val (markers, end) = readDayPartMarkers(tokens, i + 2 + used)
            val hour = (resolveHour(ordinal, markers) - 1 + 24) % 24
            return tokens.match(i, end - 1, SpokenTime(LocalTime.of(hour, 30)))
        }

        CLOCK.matchEntire(next)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour > 23 || minute > 59) return null
            return tokens.match(i, i + 1, SpokenTime(LocalTime.of(hour, minute)))
        }
        DOTTED_DATE.matchEntire(next)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour > 23 || minute > 59) return null
            return tokens.match(i, i + 1, SpokenTime(LocalTime.of(hour, minute)))
        }

        return clockAfterPreposition(tokens, i)
    }

    private fun clockAfterPreposition(
        tokens: List<SpokenToken>,
        i: Int,
    ): SpokenMatch<SpokenTime>? {
        var cursor = i + 1

        val (hourValue, hourTokens) = readNumber(tokens, cursor) ?: return null
        cursor += hourTokens
        if (tokens.coreAt(cursor).isIn(SpokenLexicon.HOUR_MARKERS)) cursor += 1

        var minute = 0
        readNumber(tokens, cursor)?.let { (value, used) ->
            if (value in 0..59) {
                minute = value
                cursor += used
            }
        }

        val (markers, end) = readDayPartMarkers(tokens, cursor)

        if (tokens.coreAt(end).isIn(QUANTITY_WORDS)) return null

        val hour = resolveHour(hourValue, markers)
        if (hour !in 0..23) return null
        return tokens.match(i, end - 1, SpokenTime(LocalTime.of(hour, minute)))
    }

    private fun withoutMinutes(
        tokens: List<SpokenToken>,
        withoutIndex: Int,
        from: Int = withoutIndex,
    ): SpokenMatch<SpokenTime>? {
        val (back, backUsed) = readNumber(tokens, withoutIndex + 1) ?: return null
        if (back !in 1..59) return null
        var cursor = withoutIndex + 1 + backUsed
        if (tokens.coreAt(cursor).isIn(SpokenLexicon.MINUTE_MARKERS)) cursor += 1

        val (hourValue, hourUsed) = readNumber(tokens, cursor) ?: return null
        cursor += hourUsed

        val (markers, end) = readDayPartMarkers(tokens, cursor)
        val hour = (resolveHour(hourValue, markers) - 1 + 24) % 24
        return tokens.match(from, end - 1, SpokenTime(LocalTime.of(hour, 60 - back)))
    }

    private fun readDayPartMarkers(
        tokens: List<SpokenToken>,
        from: Int,
    ): Pair<Set<String>, Int> {
        val markers = mutableSetOf<String>()
        var cursor = from
        while (tokens.coreAt(cursor).isDayPart()) {
            markers += tokens[cursor].core
            cursor += 1
        }
        return markers to cursor
    }

    private fun relativeTime(
        tokens: List<SpokenToken>,
        i: Int,
        now: LocalTime,
    ): SpokenMatch<SpokenTime>? {
        val next = tokens.coreAt(i + 1) ?: return null

        if (next == "полчаса") return tokens.match(i, i + 1, shift(now, 30))
        if (next == "полтора" || next == "полторы") {
            if (tokens.coreAt(i + 2).isIn(SpokenLexicon.HOUR_MARKERS)) {
                return tokens.match(i, i + 2, shift(now, 90))
            }
            return null
        }

        var cursor = i + 1
        var amount = 1
        readNumber(tokens, cursor)?.let { (value, used) ->
            amount = value
            cursor += used
        }

        val unit = tokens.coreAt(cursor) ?: return null
        val minutes =
            when {
                unit.isIn(SpokenLexicon.HOUR_MARKERS) -> amount * 60
                unit.isIn(SpokenLexicon.MINUTE_MARKERS) -> amount
                else -> return null
            }
        return tokens.match(i, cursor, shift(now, minutes))
    }

    private fun shift(
        now: LocalTime,
        minutes: Int,
    ): SpokenTime {
        val total = now.hour * 60 + now.minute + minutes
        return SpokenTime(LocalTime.of((total / 60) % 24, total % 60), total / MINUTES_PER_DAY)
    }

    private const val MINUTES_PER_DAY = 24 * 60

    private fun resolveHour(
        hour: Int,
        markers: Set<String>,
    ): Int =
        when {
            markers.any { it in SpokenLexicon.MORNING_MARKERS } -> {
                if (hour == 12) 0 else hour
            }

            markers.any { it in SpokenLexicon.AFTERNOON_MARKERS } -> {
                if (hour in 1..11) hour + AFTERNOON_FROM else hour
            }

            hour in 1..7 -> {
                hour + AFTERNOON_FROM
            }

            else -> {
                hour
            }
        }

    private fun readNumber(
        tokens: List<SpokenToken>,
        from: Int,
    ): Pair<Int, Int>? {
        val token = tokens.coreAt(from) ?: return null
        token.toIntOrNull()?.let { return it to 1 }

        val first = SpokenLexicon.CARDINALS[token] ?: return null
        val second = tokens.coreAt(from + 1)?.let { SpokenLexicon.CARDINALS[it] }
        return if (first >= 20 && second != null && second in 1..9) {
            (first + second) to 2
        } else {
            first to 1
        }
    }

    private fun readOrdinal(
        tokens: List<SpokenToken>,
        from: Int,
    ): Pair<Int, Int>? {
        val token = tokens.coreAt(from) ?: return null
        SpokenLexicon.ORDINALS[token]?.let { return it to 1 }

        val tens = SpokenLexicon.ORDINAL_TENS[token] ?: return null
        val units = tokens.coreAt(from + 1)?.let { SpokenLexicon.ORDINALS[it] }
        return if (units != null && units in 1..9) (tens + units) to 2 else null
    }

    private fun List<SpokenToken>.coreAt(index: Int): String? = getOrNull(index)?.core

    private fun List<SpokenToken>.monthAt(index: Int): Month? = coreAt(index)?.let { SpokenLexicon.MONTHS[it] }

    private fun String?.isIn(set: Set<String>): Boolean = this != null && this in set

    private fun String?.isDayPart(): Boolean = isIn(SpokenLexicon.AFTERNOON_MARKERS) || isIn(SpokenLexicon.MORNING_MARKERS)

    private fun <T> List<SpokenToken>.match(
        from: Int,
        to: Int,
        value: T,
    ): SpokenMatch<T> = SpokenMatch(value = value, range = this[from].range.first..this[to].range.last)

    private val YEAR_WORDS_IGNORED = setOf("года", "году", "г")
}

internal data class SpokenToken(
    val raw: String,
    val core: String,
    val range: IntRange,
)

private const val EDGE_PUNCTUATION = ".,;:!?()«»\"'—-–—"

internal fun tokenizeSpoken(text: String): List<SpokenToken> {
    val tokens = mutableListOf<SpokenToken>()
    var index = 0
    while (index < text.length) {
        if (text[index].isWhitespace()) {
            index++
            continue
        }
        val start = index
        while (index < text.length && !text[index].isWhitespace()) index++
        val raw = text.substring(start, index)

        var coreStart = 0
        var coreEnd = raw.length
        while (coreStart < coreEnd && raw[coreStart] in EDGE_PUNCTUATION) coreStart++
        while (coreEnd > coreStart && raw[coreEnd - 1] in EDGE_PUNCTUATION) coreEnd--

        tokens +=
            SpokenToken(
                raw = raw,
                core = raw.substring(coreStart, coreEnd).lowercase(),
                range = (start + coreStart) until (start + coreEnd),
            )
    }
    return tokens
}
