package com.todi.app.domain.repository

import com.todi.app.domain.model.Task
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

interface TaskRepository {
    fun observeTasksForDate(date: LocalDate): Flow<List<Task>>

    fun observeActiveTasksForDate(date: LocalDate): Flow<List<Task>>

    fun observeOverdueTasks(beforeDate: LocalDate): Flow<List<Task>>

    fun observeActiveTasksBetween(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Task>>

    fun observeGlobalTasks(): Flow<List<Task>>

    fun observeCompletedTasks(): Flow<List<Task>>

    suspend fun getTask(id: Long): Task?

    fun observeAllTasks(): Flow<List<Task>>

    suspend fun createTask(
        title: String,
        date: LocalDate,
        time: LocalTime?,
        note: String?,
        isGlobal: Boolean = false,
    ): Task

    suspend fun updateTask(task: Task)

    suspend fun setCompleted(
        id: Long,
        completed: Boolean,
    )

    suspend fun moveTask(
        id: Long,
        date: LocalDate,
    )

    suspend fun restoreCompletedTask(
        id: Long,
        date: LocalDate,
    )

    suspend fun deleteTask(id: Long)

    suspend fun restoreTask(task: Task)

    suspend fun replaceAll(tasks: List<Task>)

    suspend fun purgeTombstones(expiresBefore: Instant): Int

    suspend fun carryOverIncompleteTasks(targetDate: LocalDate): Int

    suspend fun deleteCompleted(completedBefore: Instant?): Int

    suspend fun setCalendarEventId(
        id: Long,
        eventId: Long?,
    )
}
