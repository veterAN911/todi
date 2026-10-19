package com.todi.app.domain

import com.todi.app.domain.model.Task
import com.todi.app.domain.util.CompletedDayKind
import com.todi.app.domain.util.CompletedGrouper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CompletedGrouperTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private val today = LocalDate.of(2026, 9, 20)

    private fun task(
        id: Long,
        completedAt: Instant?,
        updatedAt: Instant = Instant.parse("2026-09-12T00:00:00Z"),
    ) = Task(
        id = id,
        title = "Дело $id",
        date = LocalDate.of(2026, 9, 1),
        time = null,
        note = null,
        completed = true,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        syncId = "sync$id",
        completedAt = completedAt,
    )

    @Test
    fun `groups by the completion day in the device zone, not in UTC`() {
        val justAfterMidnight = Instant.parse("2026-09-11T22:30:00Z")

        val groups = CompletedGrouper.group(listOf(task(1, justAfterMidnight)), zone, today)

        assertEquals(1, groups.size)
        assertEquals(LocalDate.of(2026, 9, 12), groups[0].date)
    }

    @Test
    fun `late evening stays on the same local day`() {
        val groups = CompletedGrouper.group(listOf(task(1, Instant.parse("2026-09-11T21:30:00Z"))), zone, today)

        assertEquals(LocalDate.of(2026, 9, 11), groups[0].date)
    }

    @Test
    fun `groups of different days are sorted newest first`() {
        val tasks =
            listOf(
                task(1, Instant.parse("2026-09-01T10:00:00Z")),
                task(2, Instant.parse("2026-09-03T10:00:00Z")),
                task(3, Instant.parse("2026-09-02T10:00:00Z")),
            )

        val dates = CompletedGrouper.group(tasks, zone, today).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 1),
            ),
            dates,
        )
    }

    @Test
    fun `tasks completed the same day stay in one group`() {
        val tasks =
            listOf(
                task(1, Instant.parse("2026-09-03T07:00:00Z")),
                task(2, Instant.parse("2026-09-03T19:00:00Z")),
            )

        val groups = CompletedGrouper.group(tasks, zone, today)

        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].tasks.map { it.id })
    }

    @Test
    fun `a task without completedAt falls back to updatedAt`() {
        val groups =
            CompletedGrouper.group(
                listOf(task(1, completedAt = null, updatedAt = Instant.parse("2026-08-30T12:00:00Z"))),
                zone,
                today,
            )

        assertEquals(LocalDate.of(2026, 8, 30), groups[0].date)
    }

    @Test
    fun `the days near today are named, the rest are not`() {
        val groups =
            CompletedGrouper.group(
                listOf(
                    task(1, completedAt = Instant.parse("2026-09-20T10:00:00Z")),
                    task(2, completedAt = Instant.parse("2026-09-19T10:00:00Z")),
                    task(3, completedAt = Instant.parse("2026-09-18T10:00:00Z")),
                ),
                zone,
                today,
            )

        assertEquals(
            listOf(CompletedDayKind.TODAY, CompletedDayKind.YESTERDAY, CompletedDayKind.EARLIER),
            groups.map { it.kind },
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 18)),
            groups.map { it.date },
        )
    }

    @Test
    fun `yesterday moves with the day the archive is read on`() {
        val completed = listOf(task(1, completedAt = Instant.parse("2026-09-19T10:00:00Z")))

        assertEquals(CompletedDayKind.YESTERDAY, CompletedGrouper.group(completed, zone, today).single().kind)
        assertEquals(
            CompletedDayKind.EARLIER,
            CompletedGrouper.group(completed, zone, LocalDate.of(2026, 9, 21)).single().kind,
        )
    }

    @Test
    fun `an empty archive has no groups`() {
        assertEquals(emptyList<Any>(), CompletedGrouper.group(emptyList(), zone, today))
    }
}
