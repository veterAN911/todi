package com.todi.app.domain

import com.todi.app.domain.model.Task
import com.todi.app.domain.util.SearchSection
import com.todi.app.domain.util.TaskSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TaskSearchTest {
    private val today = LocalDate.of(2026, 9, 12)
    private val tomorrow = today.plusDays(1)
    private val base = Instant.parse("2026-09-12T08:00:00Z")

    private fun task(
        id: Long,
        title: String,
        date: LocalDate = today,
        note: String? = null,
        completed: Boolean = false,
        isGlobal: Boolean = false,
        deleted: Boolean = false,
        time: LocalTime? = null,
        completedAtOffset: Long? = null,
    ) = Task(
        id = id,
        title = title,
        date = date,
        time = time,
        note = note,
        completed = completed,
        createdAt = base.plusSeconds(id),
        updatedAt = base.plusSeconds(id),
        syncId = "sync-$id",
        isGlobal = isGlobal,
        completedAt = completedAtOffset?.let { base.plusSeconds(it) },
        deletedAt = if (deleted) base else null,
    )

    private fun sections(
        tasks: List<Task>,
        query: String = "хлеб",
    ) = TaskSearch.search(tasks, query, today).map { it.section }

    private fun titles(
        tasks: List<Task>,
        query: String = "хлеб",
    ) = TaskSearch.search(tasks, query, today).flatMap { group -> group.tasks.map { it.title } }

    @Test
    fun blankQuery_returnsNothing() {
        val tasks = listOf(task(1, "хлеб"))

        assertTrue(TaskSearch.search(tasks, "", today).isEmpty())
        assertTrue(TaskSearch.search(tasks, "   ", today).isEmpty())
    }

    @Test
    fun matchesTitle_caseInsensitiveAndCyrillic() {
        val tasks = listOf(task(1, "Купить ХЛЕБ"), task(2, "купить молоко"))

        assertEquals(listOf("Купить ХЛЕБ"), titles(tasks))
        assertEquals(listOf("Купить ХЛЕБ"), titles(tasks, "хлеб"))
    }

    @Test
    fun matchesNote() {
        val tasks =
            listOf(
                task(1, "Магазин", note = "не забыть хлеб и соль"),
                task(2, "Аптека"),
            )

        assertEquals(listOf("Магазин"), titles(tasks))
    }

    @Test
    fun matchesEmoji() {
        val tasks = listOf(task(1, "Поздравить 🎂 маму"), task(2, "Позвонить"))

        assertEquals(listOf("Поздравить 🎂 маму"), titles(tasks, "🎂"))
    }

    @Test
    fun pasteWithSpaces_matchesAndDoesNotMoveHighlightOffByOne() {
        val tasks = listOf(task(1, "Хлеб"))

        assertEquals(listOf("Хлеб"), titles(tasks, " хлеб "))
        assertEquals(listOf(0..3), TaskSearch.matchRanges("Хлеб", " хлеб "))
    }

    @Test
    fun tombstonesAreExcluded() {
        val tasks = listOf(task(1, "хлеб", deleted = true), task(2, "хлеб"))

        assertEquals(listOf("хлеб"), titles(tasks))
        assertEquals(
            listOf(2L),
            TaskSearch
                .search(tasks, "хлеб", today)
                .single()
                .tasks
                .map { it.id },
        )
    }

    @Test
    fun tasksAreGroupedByWhereTheyLive() {
        val tasks =
            listOf(
                task(1, "хлеб на завтра", date = tomorrow),
                task(2, "хлеб без даты", isGlobal = true, date = base.atZone(java.time.ZoneOffset.UTC).toLocalDate()),
                task(3, "хлеб в понедельник", date = today.plusDays(3)),
                task(4, "хлеб сегодня", time = LocalTime.of(9, 0)),
                task(5, "хлеб съеден", completed = true),
            )

        assertEquals(
            listOf(
                SearchSection.TODAY,
                SearchSection.TOMORROW,
                SearchSection.GLOBAL,
                SearchSection.DATED,
                SearchSection.COMPLETED,
            ),
            sections(tasks),
        )
        assertEquals(
            listOf("хлеб сегодня", "хлеб на завтра", "хлеб без даты", "хлеб в понедельник", "хлеб съеден"),
            titles(tasks),
        )
    }

    @Test
    fun emptySectionsAreDropped() {
        val tasks = listOf(task(1, "хлеб на завтра", date = tomorrow))

        assertEquals(listOf(SearchSection.TOMORROW), sections(tasks))
    }

    @Test
    fun otherDates_soonestFirst_andSameDayKeepsDayOrder() {
        val tasks =
            listOf(
                task(1, "хлеб позже", date = today.plusDays(10)),
                task(2, "хлеб вечером", date = today.plusDays(2), time = LocalTime.of(19, 0)),
                task(3, "хлеб до работы", date = today.plusDays(2), time = LocalTime.of(7, 30)),
                task(4, "хлеб завтра+2", date = today.plusDays(2)),
            )

        assertEquals(
            listOf("хлеб до работы", "хлеб вечером", "хлеб завтра+2", "хлеб позже"),
            titles(tasks),
        )
    }

    @Test
    fun globalTasks_areNotRepeatedUnderTheirStoredDate() {
        val tasks = listOf(task(1, "хлеб", isGlobal = true, date = today))

        val groups = TaskSearch.search(tasks, "хлеб", today)

        assertEquals(listOf(SearchSection.GLOBAL), groups.map { it.section })
        assertEquals(1, groups.sumOf { it.tasks.size })
    }

    @Test
    fun completed_areNewestFirst_evenWithoutCompletedAt() {
        val tasks =
            listOf(
                task(1, "хлеб старый", completed = true, completedAtOffset = 100),
                task(2, "хлеб новый", completed = true, completedAtOffset = 900),
                task(3, "хлеб без штампа", completed = true),
            )

        assertEquals(listOf("хлеб новый", "хлеб старый", "хлеб без штампа"), titles(tasks))
    }

    @Test
    fun matchRanges_findsEveryOccurrence_andDoesNotOverlap() {
        assertEquals(listOf(0..3, 11..14), TaskSearch.matchRanges("Хлеб и ещё хлеб", "хлеб"))

        assertEquals(listOf(0..1, 2..3, 4..5), TaskSearch.matchRanges("аааааа", "аа"))
    }

    @Test
    fun matchRanges_noMatchOrBlankQuery_isEmpty() {
        assertTrue(TaskSearch.matchRanges("Хлеб", "молоко").isEmpty())
        assertTrue(TaskSearch.matchRanges("Хлеб", "  ").isEmpty())
        assertTrue(TaskSearch.matchRanges("", "хлеб").isEmpty())
    }

    @Test
    fun matchRanges_keepsOriginalOffsetsForCyrillicAndEmoji() {
        val text = "🎂 Поздравить маму"

        val ranges = TaskSearch.matchRanges(text, "маму")

        assertEquals(1, ranges.size)
        assertEquals("маму", text.substring(ranges.single().first, ranges.single().last + 1))
    }

    @Test
    fun matches_blankQueryIsNotAMatch() {
        assertFalse(TaskSearch.matches(task(1, "хлеб"), ""))
        assertFalse(TaskSearch.matches(task(1, "хлеб"), "  "))
    }
}
