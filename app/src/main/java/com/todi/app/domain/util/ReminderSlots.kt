package com.todi.app.domain.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderSlots {
    fun next(
        now: ZonedDateTime,
        start: LocalTime,
        interval: Duration,
        quietAfter: LocalTime,
    ): ZonedDateTime {
        val rawStep = interval.toMinutes().coerceAtLeast(0)

        val stepMinutes = if (rawStep >= MINUTES_PER_DAY) 0L else rawStep
        return firstSlotOn(now, now.toLocalDate(), start, stepMinutes, quietAfter)
            ?: now
                .toLocalDate()
                .plusDays(1)
                .atTime(start)
                .atZone(now.zone)
    }

    fun nextQuietMoment(
        now: ZonedDateTime,
        quietAfter: LocalTime,
    ): ZonedDateTime {
        val today = now.toLocalDate().atTime(quietAfter).atZone(now.zone)
        return if (today.isAfter(now)) {
            today
        } else {
            now
                .toLocalDate()
                .plusDays(1)
                .atTime(quietAfter)
                .atZone(now.zone)
        }
    }

    private fun firstSlotOn(
        now: ZonedDateTime,
        date: LocalDate,
        start: LocalTime,
        stepMinutes: Long,
        quietAfter: LocalTime,
    ): ZonedDateTime? {
        var slot = start
        while (slot.isBefore(quietAfter)) {
            val candidate = date.atTime(slot).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
            if (stepMinutes == 0L) return null
            val next = slot.plusMinutes(stepMinutes)

            if (next <= slot) return null
            slot = next
        }
        return null
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
