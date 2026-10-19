package com.todi.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class Task(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val note: String?,
    val completed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncId: String,
    val carriedFrom: LocalDate? = null,
    val calendarEventId: Long? = null,
    val isGlobal: Boolean = false,
    val completedAt: Instant? = null,
    val deletedAt: Instant? = null,
) {
    val isTimed: Boolean get() = time != null

    fun normalized(): Task = if (isGlobal) copy(time = null, carriedFrom = null) else this
}
