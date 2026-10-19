package com.todi.app.notification

import com.todi.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class NotificationContentBuilderTest {
    private val date = LocalDate.of(2026, 8, 30)
    private val base = Instant.parse("2026-08-30T08:00:00Z")

    private fun task(
        id: Long,
        title: String,
        completed: Boolean = false,
        date: LocalDate = this.date,
        time: LocalTime? = null,
    ) = Task(
        id = id,
        title = title,
        date = date,
        time = time,
        note = null,
        completed = completed,
        createdAt = base,
        updatedAt = base,
        syncId = "sync-$id",
    )

    private fun global(
        id: Long,
        title: String,
        completed: Boolean = false,
    ) = task(id, title, completed = completed, date = date.minusDays(7)).copy(isGlobal = true)

    private fun build(
        todayTasks: List<Task> = emptyList(),
        overdueTasks: List<Task> = emptyList(),
        dateLessTasks: List<Task> = emptyList(),
        detail: NotificationDetail = NotificationDetail.FULL,
        maxLines: Int = NotificationContentBuilder.DEFAULT_MAX_LINES,
    ): NotificationContent =
        NotificationContentBuilder.build(
            todayTasks = todayTasks,
            overdueTasks = overdueTasks,
            dateLessTasks = dateLessTasks,
            overdueHeader = "Просроченные:",
            dateLessHeader = "Без даты:",
            detail = detail,
            maxLines = maxLines,
        )

    private fun NotificationContent.lineTexts(): List<String> = lines.map { it.text }

    private fun NotificationContent.previewText(): String = preview.joinToString(" · ") { it.text }

    @Test
    fun zeroTasks_hasNoContent() {
        val content = build()

        assertEquals(0, content.count)
        assertTrue(content.lines.isEmpty())
        assertTrue(content.preview.isEmpty())
        assertEquals(0, content.moreCount)
    }

    @Test
    fun oneTask_showsCountAndSingleLine() {
        val content = build(todayTasks = listOf(task(1, "Позвонить клиенту")))

        assertEquals(1, content.count)
        assertEquals(1, content.todayCount)
        assertEquals(0, content.overdueCount)
        assertEquals(listOf("☐ Позвонить клиенту"), content.lineTexts())
        assertEquals("Позвонить клиенту", content.previewText())
        assertEquals(0, content.moreCount)
    }

    @Test
    fun fiveTasks_allListed() {
        val tasks = (1..5).map { task(it.toLong(), "Задача $it") }
        val content = build(todayTasks = tasks)

        assertEquals(5, content.count)
        assertEquals(5, content.lines.size)
        assertEquals(0, content.moreCount)
    }

    @Test
    fun manyTasks_truncateLinesAndReportMore() {
        val tasks = (1..25).map { task(it.toLong(), "Задача $it") }
        val content = build(todayTasks = tasks)

        assertEquals(25, content.count)
        assertEquals(NotificationContentBuilder.DEFAULT_MAX_LINES, content.lines.size)
        assertEquals(19, content.moreCount)
    }

    @Test
    fun completedTasks_areExcluded() {
        val content =
            build(
                todayTasks =
                    listOf(
                        task(1, "Активная 1"),
                        task(2, "Выполнена", completed = true),
                        task(3, "Активная 2"),
                    ),
            )

        assertEquals(2, content.count)
        assertTrue(content.lines.none { it.text.contains("Выполнена") })
        assertTrue(content.lines.any { it.text == "☐ Активная 1" })
        assertTrue(content.lines.any { it.text == "☐ Активная 2" })
    }

    @Test
    fun overdueTasks_listedAfterTodayWithHeader() {
        val content =
            build(
                todayTasks = listOf(task(1, "Сегодняшняя")),
                overdueTasks = listOf(task(2, "Вчерашняя", date = date.minusDays(1))),
            )

        assertEquals(2, content.count)
        assertEquals(1, content.overdueCount)
        assertEquals(
            listOf("☐ Сегодняшняя", "Просроченные:", "☐ Вчерашняя"),
            content.lineTexts(),
        )
    }

    @Test
    fun completedOverdue_alsoExcluded() {
        val content =
            build(overdueTasks = listOf(task(2, "Старая выполненная", completed = true, date = date.minusDays(2))))

        assertEquals(0, content.count)
        assertFalse(content.lines.any { it.text.contains("выполненная") })
    }

    @Test
    fun timedTasks_comeBeforeUntimed() {
        val content =
            build(
                todayTasks =
                    listOf(
                        task(1, "Без времени"),
                        task(2, "С временем", time = LocalTime.of(9, 0)),
                    ),
            )

        assertEquals(listOf("☐ С временем", "☐ Без времени"), content.lineTexts())
    }

    @Test
    fun collapsedPreview_joinsFirstTwoTitles() {
        val content = build(todayTasks = listOf(task(1, "Один"), task(2, "Два"), task(3, "Три")))

        assertEquals("Один · Два", content.previewText())
    }

    @Test
    fun everyLine_carriesItsSection() {
        val content =
            build(
                todayTasks = listOf(task(1, "Сегодняшняя")),
                overdueTasks = listOf(task(2, "Вчерашняя", date = date.minusDays(1))),
            )

        assertEquals(
            listOf(
                NotificationSection.TODAY,
                NotificationSection.OVERDUE,
                NotificationSection.OVERDUE,
            ),
            content.lines.map { it.section },
        )
    }

    @Test
    fun theOverdueHeader_isAHeaderAndItsTasksAreNot() {
        val content = build(overdueTasks = listOf(task(2, "Вчерашняя", date = date.minusDays(1))))

        assertEquals(listOf(true, false), content.lines.map { it.isHeader })
    }

    @Test
    fun theCollapsedPreview_keepsEachTitleSection() {
        val content =
            build(
                todayTasks = listOf(task(1, "Сегодняшняя")),
                overdueTasks = listOf(task(2, "Вчерашняя", date = date.minusDays(1))),
            )

        assertEquals(
            listOf(NotificationSection.TODAY, NotificationSection.OVERDUE),
            content.preview.map { it.section },
        )

        assertEquals(listOf("Сегодняшняя", "Вчерашняя"), content.preview.map { it.text })
    }

    @Test
    fun dateLessTasks_areTheirOwnGroup_atTheEnd() {
        val content =
            build(
                todayTasks = listOf(task(1, "Сегодняшняя")),
                overdueTasks = listOf(task(2, "Вчерашняя", date = date.minusDays(1))),
                dateLessTasks = listOf(global(3, "Позвонить в сервис")),
            )

        assertEquals(
            listOf("☐ Сегодняшняя", "Просроченные:", "☐ Вчерашняя", "Без даты:", "☐ Позвонить в сервис"),
            content.lineTexts(),
        )
        assertEquals(
            listOf(
                NotificationSection.TODAY,
                NotificationSection.OVERDUE,
                NotificationSection.OVERDUE,
                NotificationSection.DATE_LESS,
                NotificationSection.DATE_LESS,
            ),
            content.lines.map { it.section },
        )
        assertEquals(3, content.count)
        assertEquals(1, content.globalCount)

        assertEquals(2, content.datedCount)
    }

    @Test
    fun aFullDay_stillShowsTheGroup() {
        val content =
            build(
                todayTasks = (1..8).map { task(it.toLong(), "Задача $it") },
                overdueTasks = listOf(task(99, "Вчерашняя", date = date.minusDays(1))),
            )

        assertEquals(
            listOf("☐ Задача 1", "☐ Задача 2", "☐ Задача 3", "☐ Задача 4", "Просроченные:", "☐ Вчерашняя"),
            content.lineTexts(),
        )

        assertEquals(4, content.moreCount)
    }

    @Test
    fun twoGroups_andAFullDay_fitEachOther() {
        val content =
            build(
                todayTasks = (1..9).map { task(it.toLong(), "Задача $it") },
                overdueTasks = listOf(task(90, "Вчерашняя", date = date.minusDays(1))),
                dateLessTasks = listOf(global(91, "Позвонить в сервис")),
            )

        assertEquals(
            listOf(
                "☐ Задача 1",
                "☐ Задача 2",
                "Просроченные:",
                "☐ Вчерашняя",
                "Без даты:",
                "☐ Позвонить в сервис",
            ),
            content.lineTexts(),
        )
    }

    @Test
    fun aGroupWithNoTasks_isNotDrawnAtAll() {
        val content = build(todayTasks = listOf(task(1, "Сегодняшняя")))

        assertTrue(content.lines.none { it.isHeader })
        assertEquals(0, content.globalCount)
    }

    @Test
    fun countOnly_keepsTheCountsAndDropsEveryList() {
        val content =
            build(
                todayTasks = listOf(task(1, "Сегодняшняя")),
                overdueTasks = listOf(task(2, "Вчерашняя", date = date.minusDays(1))),
                detail = NotificationDetail.COUNT_ONLY,
            )

        assertEquals(2, content.count)
        assertEquals(1, content.todayCount)
        assertEquals(1, content.overdueCount)
        assertTrue(content.lines.isEmpty())
        assertTrue(content.preview.isEmpty())
        assertEquals(0, content.moreCount)
    }

    @Test
    fun countOnly_carriesNoTaskTitleAnywhere() {
        val content =
            build(
                todayTasks = listOf(task(1, "Позвонить клиенту")),
                dateLessTasks = listOf(global(2, "Оплатить страховку")),
                detail = NotificationDetail.COUNT_ONLY,
            )

        assertFalse(content.toString().contains("Позвонить"))
        assertFalse(content.toString().contains("Оплатить"))
    }

    @Test
    fun countOnly_countsWhatTheFullBuildCounts() {
        val today = listOf(task(1, "Первая"), task(2, "Вторая"))
        val overdue = listOf(task(3, "Вчерашняя", date = date.minusDays(1)))

        val full = build(todayTasks = today, overdueTasks = overdue)
        val countOnly = build(todayTasks = today, overdueTasks = overdue, detail = NotificationDetail.COUNT_ONLY)

        assertEquals(full.count, countOnly.count)
        assertEquals(full.todayCount, countOnly.todayCount)
        assertEquals(full.overdueCount, countOnly.overdueCount)
    }

    @Test
    fun countOnlyReminder_keepsTheCountAndNoNames() {
        val content =
            NotificationContentBuilder.buildReminder(
                listOf(global(1, "Позвонить в сервис")),
                detail = NotificationDetail.COUNT_ONLY,
            )

        assertEquals(1, content.count)
        assertTrue(content.lines.isEmpty())
        assertTrue(content.preview.isEmpty())
        assertFalse(content.toString().contains("Позвонить"))
    }

    @Test
    fun reminder_listsDateLessTasksWithoutAHeader() {
        val content =
            NotificationContentBuilder.buildReminder(
                listOf(global(1, "Позвонить в сервис"), global(2, "Оплатить страховку")),
            )

        assertEquals(
            listOf("☐ Позвонить в сервис", "☐ Оплатить страховку"),
            content.lineTexts(),
        )
        assertEquals("Позвонить в сервис · Оплатить страховку", content.previewText())
        assertEquals(2, content.count)
        assertEquals(2, content.globalCount)
        assertEquals(0, content.datedCount)
    }

    @Test
    fun reminder_linesAreAllDateLess() {
        val content = NotificationContentBuilder.buildReminder(listOf(global(1, "Позвонить в сервис")))

        assertEquals(listOf(NotificationSection.DATE_LESS), content.lines.map { it.section })
        assertEquals(listOf(NotificationSection.DATE_LESS), content.preview.map { it.section })
    }

    @Test
    fun reminder_keepsTheOldestFirst() {
        val older = global(1, "Старое").copy(createdAt = base.minusSeconds(3600))
        val newer = global(2, "Новое")

        val content = NotificationContentBuilder.buildReminder(listOf(newer, older))

        assertEquals(listOf("☐ Старое", "☐ Новое"), content.lineTexts())
    }

    @Test
    fun reminder_excludesCompletedAndTruncates() {
        val content =
            NotificationContentBuilder.buildReminder(
                (1..8).map { global(it.toLong(), "Дело $it") } + global(99, "Выполненное", completed = true),
                maxLines = 6,
            )

        assertEquals(8, content.count)
        assertEquals(6, content.lines.size)
        assertEquals(2, content.moreCount)
        assertTrue(content.lines.none { it.text.contains("Выполненное") })
    }

    @Test
    fun reminder_ofNothing_isEmpty() {
        val content = NotificationContentBuilder.buildReminder(emptyList())

        assertEquals(0, content.count)
        assertTrue(content.lines.isEmpty())
        assertTrue(content.preview.isEmpty())
    }
}
