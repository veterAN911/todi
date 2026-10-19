package com.todi.app

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todi.app.domain.model.Task
import com.todi.app.notification.NotificationColors
import com.todi.app.notification.NotificationContent
import com.todi.app.notification.NotificationContentBuilder
import com.todi.app.notification.NotificationSection
import com.todi.app.notification.TaskNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class NotificationViewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val manager = TaskNotificationManager(context)

    private val day = LocalDate.of(2026, 9, 13)
    private val stamp = Instant.parse("2026-09-13T08:00:00Z")

    private val red = 0xFFE53935.toInt()

    private fun task(
        id: Long,
        title: String,
        date: LocalDate = day,
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
    )

    private fun content(): NotificationContent =
        NotificationContentBuilder.build(
            todayTasks = listOf(task(1, "Сегодняшнее")),
            overdueTasks = listOf(task(2, "Вчерашнее", day.minusDays(1))),
            dateLessTasks = emptyList(),
            overdueHeader = "Просроченные:",
            dateLessHeader = "Без даты:",
        )

    private fun fullContent(): NotificationContent =
        NotificationContentBuilder.build(
            todayTasks = listOf(task(1, "Сегодняшнее")),
            overdueTasks = listOf(task(2, "Вчерашнее", day.minusDays(1))),
            dateLessTasks = listOf(task(3, "Позвонить в сервис", date = day.minusDays(7)).copy(isGlobal = true)),
            overdueHeader = "Просроченные:",
            dateLessHeader = "Без даты:",
        )

    private fun inflate(
        content: NotificationContent,
        colors: NotificationColors,
        moreCount: Int = 0,
    ): View =
        manager
            .contentViews(content, colors, content.lines, moreCount)
            .apply(context, FrameLayout(context))

    private fun View.line(index: Int): TextView = findViewById(TaskNotificationManager.LINE_IDS[index])

    private fun themeColor(id: Int): Int = ContextCompat.getColor(context, id)

    @Test
    fun everyLineTheBuilderCanDraw_findsItsView() {
        val content = content()
        val view = inflate(content, NotificationColors.SYSTEM)

        assertEquals(NotificationContentBuilder.DEFAULT_MAX_LINES, TaskNotificationManager.LINE_IDS.size)
        content.lines.forEachIndexed { index, line ->
            assertEquals(line.text, view.line(index).text.toString())
        }
    }

    @Test
    fun theChosenSectionColour_reachesItsOwnLinesOnly() {
        val view = inflate(content(), NotificationColors(overdue = red))

        assertEquals(themeColor(R.color.notification_text_primary), view.line(0).currentTextColor)

        assertEquals(red, view.line(1).currentTextColor)
        assertEquals(red, view.line(2).currentTextColor)
    }

    @Test
    fun eachLine_takesTheColourOfItsOwnSection() {
        val content = content()
        val colors = NotificationColors(today = 0xFF1E88E5.toInt(), overdue = red, dateLess = 0xFF43A047.toInt())
        val view = inflate(content, colors)

        content.lines.forEachIndexed { index, line ->
            assertEquals(
                colors.forSection(line.section),
                view.line(index).currentTextColor,
            )
        }
    }

    @Test
    fun theDateLessGroup_inTheDailyNotification_keepsItsColour() {
        val content = fullContent()
        val colors = NotificationColors(today = 0xFF1E88E5.toInt(), overdue = red, dateLess = 0xFF43A047.toInt())
        val view = inflate(content, colors)

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
        content.lines.forEachIndexed { index, line ->
            assertEquals(colors.forSection(line.section), view.line(index).currentTextColor)
        }
    }

    @Test
    fun anUnchosenSection_keepsTheThemeColour() {
        val view = inflate(content(), NotificationColors.SYSTEM)

        assertEquals(themeColor(R.color.notification_text_secondary), view.line(1).currentTextColor)
        assertEquals(themeColor(R.color.notification_text_primary), view.line(2).currentTextColor)
    }

    @Test
    fun theLinesWithoutTasks_areHidden() {
        val content = content()
        val view = inflate(content, NotificationColors.SYSTEM)

        assertEquals(View.VISIBLE, view.line(content.lines.size - 1).visibility)
        assertEquals(View.GONE, view.line(content.lines.size).visibility)
    }

    @Test
    fun theMoreLine_showsTheCount_andIsHiddenWhenThereIsNone() {
        val content = content()
        val withMore = inflate(content, NotificationColors.SYSTEM, moreCount = 19)
        val without = inflate(content, NotificationColors.SYSTEM)

        val more = withMore.findViewById<TextView>(R.id.notification_more)
        assertEquals(View.VISIBLE, more.visibility)
        assertEquals(context.resources.getString(R.string.notification_more, 19), more.text.toString())
        assertEquals(View.GONE, without.findViewById<TextView>(R.id.notification_more).visibility)
    }

    @Test
    fun theTitle_isTheManagersOwn() {
        val content = content()
        val view = inflate(content, NotificationColors.SYSTEM)

        assertEquals(
            manager.titleFor(content),
            view.findViewById<TextView>(R.id.notification_title).text.toString(),
        )
    }

    @Test
    fun theReminder_paintsItsOwnSection() {
        val content =
            NotificationContentBuilder.buildReminder(
                globalTasks = listOf(task(3, "Без даты")),
            )
        val view = inflate(content, NotificationColors(dateLess = 0xFF43A047.toInt()))

        assertEquals(NotificationSection.DATE_LESS, content.lines.single().section)
        assertEquals(0xFF43A047.toInt(), view.line(0).currentTextColor)
    }
}
