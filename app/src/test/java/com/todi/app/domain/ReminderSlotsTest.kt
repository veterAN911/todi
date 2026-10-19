package com.todi.app.domain

import com.todi.app.domain.util.ReminderSlots
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSlotsTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(
        text: String,
        zone: ZoneId = berlin,
    ): ZonedDateTime = ZonedDateTime.parse(text).withZoneSameInstant(zone)

    private fun next(
        now: ZonedDateTime,
        start: LocalTime = LocalTime.of(10, 0),
        interval: Duration = Duration.ofHours(2),
        quietAfter: LocalTime = LocalTime.of(22, 0),
    ) = ReminderSlots.next(now, start, interval, quietAfter)

    @Test
    fun `before the start time the first slot is today`() {
        val now = at("2026-09-12T08:00:00+02:00")

        assertEquals(at("2026-09-12T10:00:00+02:00"), next(now))
    }

    @Test
    fun `early morning still has todays slots ahead`() {
        val now = at("2026-09-12T00:30:00+02:00")

        assertEquals(at("2026-09-12T10:00:00+02:00"), next(now))
    }

    @Test
    fun `between slots the next one wins`() {
        val now = at("2026-09-12T13:15:00+02:00")

        assertEquals(at("2026-09-12T14:00:00+02:00"), next(now))
    }

    @Test
    fun `exactly on a slot the following one is returned`() {
        val now = at("2026-09-12T14:00:00+02:00")

        assertEquals(at("2026-09-12T16:00:00+02:00"), next(now))
    }

    @Test
    fun `a custom interval is honoured`() {
        val now = at("2026-09-12T10:20:00+02:00")

        assertEquals(
            at("2026-09-12T10:30:00+02:00"),
            next(now, interval = Duration.ofMinutes(30)),
        )
    }

    @Test
    fun `after the last slot before the quiet hour the next morning is used`() {
        val now = at("2026-09-12T20:30:00+02:00")

        assertEquals(at("2026-09-13T10:00:00+02:00"), next(now))
    }

    @Test
    fun `late at night the reminder waits for the morning`() {
        val now = at("2026-09-12T23:40:00+02:00")

        assertEquals(at("2026-09-13T10:00:00+02:00"), next(now))
    }

    @Test
    fun `a quiet hour equal to the start means one slot a day`() {
        val now = at("2026-09-12T11:00:00+02:00")

        assertEquals(
            at("2026-09-13T10:00:00+02:00"),
            next(now, quietAfter = LocalTime.of(10, 0)),
        )
    }

    @Test
    fun `a zero interval gives the start slot only`() {
        assertEquals(
            at("2026-09-12T10:00:00+02:00"),
            next(at("2026-09-12T08:00:00+02:00"), interval = Duration.ZERO),
        )
        assertEquals(
            at("2026-09-13T10:00:00+02:00"),
            next(at("2026-09-12T08:00:00+02:00").plusHours(3), interval = Duration.ZERO),
        )
    }

    @Test
    fun `an interval longer than a day falls back to the next morning`() {
        val now = at("2026-09-12T11:00:00+02:00")

        assertEquals(
            at("2026-09-13T10:00:00+02:00"),
            next(now, interval = Duration.ofHours(25)),
        )
    }

    @Test
    fun `a spring-forward night keeps the wall clock slots`() {
        val now = at("2026-03-29T01:30:00+01:00[Europe/Berlin]")

        assertEquals(
            at("2026-03-29T03:00:00+02:00[Europe/Berlin]"),
            next(now, start = LocalTime.of(1, 0)),
        )
    }

    @Test
    fun `a half-hour zone keeps its offset`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val now = at("2026-09-12T08:00:00+05:30[Asia/Kolkata]", kolkata)

        val slot = next(now, start = LocalTime.of(9, 0))

        assertEquals(LocalTime.of(9, 0), slot.toLocalTime())
        assertEquals("+05:30", slot.offset.id)
    }

    @Test
    fun `the quiet moment is today when it is still ahead`() {
        val now = at("2026-09-12T20:30:00+02:00")

        assertEquals(
            at("2026-09-12T22:00:00+02:00"),
            ReminderSlots.nextQuietMoment(now, LocalTime.of(22, 0)),
        )
    }

    @Test
    fun `the quiet moment moves to tomorrow once it has passed`() {
        val now = at("2026-09-12T23:30:00+02:00")

        assertEquals(
            at("2026-09-13T22:00:00+02:00"),
            ReminderSlots.nextQuietMoment(now, LocalTime.of(22, 0)),
        )
    }
}
