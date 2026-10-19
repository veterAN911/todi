package com.todi.app.notification

import com.todi.app.domain.model.Task
import com.todi.app.domain.util.TaskSorter

enum class NotificationSection { TODAY, OVERDUE, DATE_LESS }

enum class NotificationDetail { FULL, COUNT_ONLY }

data class NotificationLine(
    val text: String,
    val section: NotificationSection,
    val isHeader: Boolean = false,
)

data class NotificationContent(
    val count: Int,
    val todayCount: Int,
    val overdueCount: Int,
    val globalCount: Int,
    val preview: List<NotificationLine>,
    val lines: List<NotificationLine>,
    val moreCount: Int,
) {
    val datedCount: Int get() = todayCount + overdueCount
}

object NotificationContentBuilder {
    const val DEFAULT_MAX_LINES = 6
    private const val PREVIEW_TITLES = 2
    private const val LINE_PREFIX = "☐ "

    private const val MIN_GROUP_LINES = 2

    fun build(
        todayTasks: List<Task>,
        overdueTasks: List<Task>,
        dateLessTasks: List<Task>,
        overdueHeader: String,
        dateLessHeader: String,
        detail: NotificationDetail = NotificationDetail.FULL,
        maxLines: Int = DEFAULT_MAX_LINES,
    ): NotificationContent {
        val activeToday = TaskSorter.sortForDay(todayTasks.filterNot { it.completed })
        val activeOverdue = TaskSorter.sortForDay(overdueTasks.filterNot { it.completed })
        val activeDateLess = TaskSorter.sortGlobal(dateLessTasks.filterNot { it.completed })

        val count = activeToday.size + activeOverdue.size + activeDateLess.size

        if (detail == NotificationDetail.COUNT_ONLY) {
            return NotificationContent(
                count = count,
                todayCount = activeToday.size,
                overdueCount = activeOverdue.size,
                globalCount = activeDateLess.size,
                preview = emptyList(),
                lines = emptyList(),
                moreCount = 0,
            )
        }

        val groups =
            listOf(
                Group(overdueHeader, NotificationSection.OVERDUE, activeOverdue),
                Group(dateLessHeader, NotificationSection.DATE_LESS, activeDateLess),
            ).filter { it.tasks.isNotEmpty() }

        val todayBudget = (maxLines - groups.size * MIN_GROUP_LINES).coerceAtLeast(1)

        val lines =
            buildList {
                addTasks(activeToday, NotificationSection.TODAY, upTo = todayBudget)
                groups.forEachIndexed { index, group ->

                    val budget = maxLines - size - (groups.size - index - 1) * MIN_GROUP_LINES
                    addGroup(group, budget)
                }
            }

        return NotificationContent(
            count = count,
            todayCount = activeToday.size,
            overdueCount = activeOverdue.size,
            globalCount = activeDateLess.size,
            preview =
                previewOf(
                    activeToday to NotificationSection.TODAY,
                    activeOverdue to NotificationSection.OVERDUE,
                    activeDateLess to NotificationSection.DATE_LESS,
                ),
            lines = lines,
            moreCount = (count - lines.count { !it.isHeader }).coerceAtLeast(0),
        )
    }

    fun buildReminder(
        globalTasks: List<Task>,
        detail: NotificationDetail = NotificationDetail.FULL,
        maxLines: Int = DEFAULT_MAX_LINES,
    ): NotificationContent {
        val active = TaskSorter.sortGlobal(globalTasks.filterNot { it.completed })

        if (detail == NotificationDetail.COUNT_ONLY) {
            return NotificationContent(
                count = active.size,
                todayCount = 0,
                overdueCount = 0,
                globalCount = active.size,
                preview = emptyList(),
                lines = emptyList(),
                moreCount = 0,
            )
        }

        val lines =
            active
                .take(maxLines)
                .map { NotificationLine(LINE_PREFIX + it.title, NotificationSection.DATE_LESS) }

        return NotificationContent(
            count = active.size,
            todayCount = 0,
            overdueCount = 0,
            globalCount = active.size,
            preview =
                active
                    .take(PREVIEW_TITLES)
                    .map { NotificationLine(it.title, NotificationSection.DATE_LESS) },
            lines = lines,
            moreCount = (active.size - lines.size).coerceAtLeast(0),
        )
    }

    private data class Group(
        val header: String,
        val section: NotificationSection,
        val tasks: List<Task>,
    )

    private fun previewOf(vararg groups: Pair<List<Task>, NotificationSection>): List<NotificationLine> =
        groups
            .flatMap { (tasks, section) -> tasks.map { it.title to section } }
            .take(PREVIEW_TITLES)
            .map { (title, section) -> NotificationLine(title, section) }

    private fun MutableList<NotificationLine>.addGroup(
        group: Group,
        budget: Int,
    ) {
        if (budget < MIN_GROUP_LINES) return
        add(NotificationLine(group.header, group.section, isHeader = true))
        addTasks(group.tasks, group.section, upTo = size + budget - 1)
    }

    private fun MutableList<NotificationLine>.addTasks(
        tasks: List<Task>,
        section: NotificationSection,
        upTo: Int,
    ) {
        for (task in tasks) {
            if (size >= upTo) break
            add(NotificationLine(LINE_PREFIX + task.title, section))
        }
    }
}
