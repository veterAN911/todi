package com.todi.app.notification

import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.LockScreenScope
import com.todi.app.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class NotificationPresentationTest {
    private val date = LocalDate.of(2026, 10, 11)
    private val stamp = Instant.parse("2026-10-11T08:00:00Z")

    private fun task(
        id: Long,
        title: String,
        date: LocalDate = this.date,
        global: Boolean = false,
    ) = Task(
        id = id,
        title = title,
        date = date,
        time = null,
        note = null,
        completed = false,
        createdAt = stamp,
        updatedAt = stamp,
        syncId = "sync-$id",
    ).copy(isGlobal = global)

    private fun presentation(
        showOnLockScreen: Boolean = true,
        scope: LockScreenScope = LockScreenScope.FULL,
    ) = NotificationPresentation(showOnLockScreen = showOnLockScreen, lockScreenScope = scope)

    private fun contentOf(answer: NotificationPresentation): NotificationContent =
        NotificationContentBuilder.build(
            todayTasks = listOf(task(1, "Сегодняшнее")),
            overdueTasks = listOf(task(2, "Вчерашнее", date = date.minusDays(1))),
            dateLessTasks = listOf(task(3, "Позвонить в сервис", date = date.minusDays(7), global = true)),
            overdueHeader = "Просроченные:",
            dateLessHeader = "Без даты:",
            detail = answer.detail,
        )

    @Test
    fun theSwitchOff_keepsTheNotificationOffTheLockScreen() {
        val content = contentOf(presentation())

        LockScreenScope.entries.forEach { scope ->
            val hidden = presentation(showOnLockScreen = false, scope = scope).lockScreenContent(content)

            assertNull(scope.name, hidden)
        }
    }

    @Test
    fun theSwitchOff_leavesTheShadeAlone() {
        LockScreenScope.entries.forEach { scope ->
            val detail = presentation(showOnLockScreen = false, scope = scope).detail

            assertEquals(scope.name, NotificationDetail.FULL, detail)
        }
    }

    @Test
    fun theWholeListScope_handsTheTasksOver() {
        val content = contentOf(presentation(scope = LockScreenScope.FULL))

        assertEquals(NotificationDetail.FULL, presentation(scope = LockScreenScope.FULL).detail)
        assertEquals(
            listOf("☐ Сегодняшнее", "Просроченные:", "☐ Вчерашнее", "Без даты:", "☐ Позвонить в сервис"),
            content.lines.map { it.text },
        )
    }

    @Test
    fun theTitleOnlyScope_buildsTheNotificationWithoutTheTasks() {
        val answer = presentation(scope = LockScreenScope.TITLE_ONLY)
        val content = contentOf(answer)

        assertEquals(NotificationDetail.COUNT_ONLY, answer.detail)
        assertEquals(3, content.count)
        assertTrue(content.lines.isEmpty())
        assertTrue(content.preview.isEmpty())
        assertFalse(content.toString().contains("Сегодняшнее"))
        assertFalse(content.toString().contains("Позвонить"))
    }

    @Test
    fun theLockScreenGetsTheVeryNotificationTheShadeGets() {
        val answer = presentation(scope = LockScreenScope.TITLE_ONLY)
        val content = contentOf(answer)

        assertEquals(content, answer.lockScreenContent(content))
    }

    @Test
    fun from_readsTheSettings() {
        val answer =
            NotificationPresentation.from(
                AppSettings(
                    showOnLockScreen = false,
                    lockScreenScope = LockScreenScope.TITLE_ONLY,
                    notificationColorToday = 0xFF1E88E5.toInt(),
                ),
            )

        assertFalse(answer.showOnLockScreen)
        assertEquals(LockScreenScope.TITLE_ONLY, answer.lockScreenScope)
        assertEquals(0xFF1E88E5.toInt(), answer.colors.today)
        assertNull(answer.colors.overdue)
    }

    @Test
    fun from_answersTheDetailFromTheSwitchAndTheScope() {
        val onAndTitleOnly =
            NotificationPresentation.from(
                AppSettings(showOnLockScreen = true, lockScreenScope = LockScreenScope.TITLE_ONLY),
            )
        val offAndTitleOnly =
            NotificationPresentation.from(
                AppSettings(showOnLockScreen = false, lockScreenScope = LockScreenScope.TITLE_ONLY),
            )

        assertEquals(NotificationDetail.COUNT_ONLY, onAndTitleOnly.detail)
        assertEquals(NotificationDetail.FULL, offAndTitleOnly.detail)
    }
}
