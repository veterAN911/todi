package com.todi.app.domain.util

import com.todi.app.domain.model.Task

object TaskSorter {
    fun sortForDay(tasks: List<Task>): List<Task> = tasks.sortedWith(DAY_ORDER)

    fun sortGlobal(tasks: List<Task>): List<Task> = tasks.sortedWith(GLOBAL_ORDER)

    private val DAY_ORDER =
        compareBy<Task>(
            { it.completed },
            { it.time == null },
            { it.time },
            { it.createdAt },
            { it.id },
        )

    private val GLOBAL_ORDER =
        compareBy<Task>(
            { it.createdAt },
            { it.id },
        )
}
