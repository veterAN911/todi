package com.todi.app.domain.util

import com.todi.app.domain.model.Task
import java.time.LocalDate
import java.time.ZoneId

enum class CompletedDayKind { TODAY, YESTERDAY, EARLIER }

data class CompletedGroup(
    val date: LocalDate,
    val kind: CompletedDayKind,
    val tasks: List<Task>,
)

object CompletedGrouper {
    fun group(
        tasks: List<Task>,
        zone: ZoneId,
        today: LocalDate,
    ): List<CompletedGroup> =
        tasks
            .groupBy { task -> (task.completedAt ?: task.updatedAt).atZone(zone).toLocalDate() }
            .map { (date, dayTasks) -> CompletedGroup(date = date, kind = dayKind(date, today), tasks = dayTasks) }
            .sortedByDescending { it.date }

    private fun dayKind(
        date: LocalDate,
        today: LocalDate,
    ): CompletedDayKind =
        when (date) {
            today -> CompletedDayKind.TODAY
            today.minusDays(1) -> CompletedDayKind.YESTERDAY
            else -> CompletedDayKind.EARLIER
        }
}
