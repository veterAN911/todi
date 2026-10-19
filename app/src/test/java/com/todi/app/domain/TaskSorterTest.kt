package com.todi.app.domain

import com.todi.app.domain.model.Task
import com.todi.app.domain.util.TaskSorter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TaskSorterTest {
    private val base = Instant.parse("2026-08-30T08:00:00Z")

    private fun task(
        id: Long,
        title: String,
        completed: Boolean = false,
        time: LocalTime? = null,
        createdAtOffset: Long = 0,
    ) = Task(
        id = id,
        title = title,
        date = LocalDate.of(2026, 8, 30),
        time = time,
        note = null,
        completed = completed,
        createdAt = base.plusSeconds(createdAtOffset),
        updatedAt = base.plusSeconds(createdAtOffset),
        syncId = "sync-$id",
    )

    @Test
    fun order_incompleteFirst_timedByTime_untimedAfter_completedLast() {
        val completed = task(1, "Выполнено", completed = true)
        val untimed = task(2, "Без времени")
        val evening = task(3, "Вечером", time = LocalTime.of(18, 30))
        val morning = task(4, "Утром", time = LocalTime.of(10, 0))
        val anotherUntimed = task(5, "Без времени 2", createdAtOffset = 10)

        val sorted =
            TaskSorter.sortForDay(
                listOf(untimed, completed, evening, anotherUntimed, morning),
            )

        assertEquals(listOf(4L, 3L, 2L, 5L, 1L), sorted.map { it.id })
    }

    @Test
    fun order_sameTime_sortedByCreation() {
        val later = task(1, "Позже создана", time = LocalTime.of(9, 0), createdAtOffset = 60)
        val earlier = task(2, "Раньше создана", time = LocalTime.of(9, 0), createdAtOffset = 0)

        val sorted = TaskSorter.sortForDay(listOf(later, earlier))

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun sortGlobal_keepsOldestFirst() {
        val later = task(1, "Позже", createdAtOffset = 120).copy(isGlobal = true)
        val earlier = task(2, "Раньше", createdAtOffset = 0).copy(isGlobal = true)
        val middle = task(3, "Середина", createdAtOffset = 60).copy(isGlobal = true)

        val sorted = TaskSorter.sortGlobal(listOf(later, earlier, middle))

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }
}
