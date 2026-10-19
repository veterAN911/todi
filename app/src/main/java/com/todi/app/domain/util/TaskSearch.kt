package com.todi.app.domain.util

import com.todi.app.domain.model.Task
import java.time.LocalDate

enum class SearchSection { TODAY, TOMORROW, GLOBAL, DATED, COMPLETED }

data class SearchGroup(
    val section: SearchSection,
    val tasks: List<Task>,
)

object TaskSearch {
    fun search(
        tasks: List<Task>,
        query: String,
        today: LocalDate,
    ): List<SearchGroup> {
        if (query.isBlank()) return emptyList()
        val tomorrow = today.plusDays(1)

        val hits = tasks.filter { it.deletedAt == null && matches(it, query) }
        val (completed, active) = hits.partition { it.completed }

        return buildList {
            addGroup(SearchSection.TODAY, active.byDate(today))
            addGroup(SearchSection.TOMORROW, active.byDate(tomorrow))
            addGroup(SearchSection.GLOBAL, active.filter { it.isGlobal }.let(TaskSorter::sortGlobal))
            addGroup(
                SearchSection.DATED,
                active
                    .filter { !it.isGlobal && it.date != today && it.date != tomorrow }
                    .groupBy { it.date }
                    .toSortedMap()
                    .values
                    .flatMap { TaskSorter.sortForDay(it) },
            )
            addGroup(SearchSection.COMPLETED, completed.sortedWith(COMPLETED_ORDER))
        }
    }

    fun matches(
        task: Task,
        query: String,
    ): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return false
        return task.title.contains(needle, ignoreCase = true) ||
            task.note?.contains(needle, ignoreCase = true) == true
    }

    fun matchRanges(
        text: String,
        query: String,
    ): List<IntRange> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var from = 0
        while (from + needle.length <= text.length) {
            val at = text.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            ranges += at until at + needle.length
            from = at + needle.length
        }
        return ranges
    }

    private fun List<Task>.byDate(date: LocalDate): List<Task> = filter { !it.isGlobal && it.date == date }.let(TaskSorter::sortForDay)

    private fun MutableList<SearchGroup>.addGroup(
        section: SearchSection,
        tasks: List<Task>,
    ) {
        if (tasks.isNotEmpty()) add(SearchGroup(section, tasks))
    }

    private val COMPLETED_ORDER = compareByDescending<Task> { it.completedAt ?: it.updatedAt }
}
