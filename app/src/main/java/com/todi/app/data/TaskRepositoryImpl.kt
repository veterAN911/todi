package com.todi.app.data

import com.todi.app.data.local.TaskDao
import com.todi.app.data.local.TaskEntity
import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.domain.util.newSyncId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl
    @Inject
    constructor(
        private val dao: TaskDao,
    ) : TaskRepository {
        override fun observeTasksForDate(date: LocalDate): Flow<List<Task>> = dao.observeTasksForDate(date.toEpochDay()).mapToDomain()

        override fun observeActiveTasksForDate(date: LocalDate): Flow<List<Task>> =
            dao.observeActiveTasksForDate(date.toEpochDay()).mapToDomain()

        override fun observeOverdueTasks(beforeDate: LocalDate): Flow<List<Task>> =
            dao.observeOverdueTasks(beforeDate.toEpochDay()).mapToDomain()

        override fun observeActiveTasksBetween(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Task>> = dao.observeActiveTasksBetween(from.toEpochDay(), to.toEpochDay()).mapToDomain()

        override fun observeGlobalTasks(): Flow<List<Task>> = dao.observeGlobalTasks().mapToDomain()

        override fun observeCompletedTasks(): Flow<List<Task>> = dao.observeCompletedTasks().mapToDomain()

        override suspend fun getTask(id: Long): Task? = dao.getById(id)?.toDomain()

        override fun observeAllTasks(): Flow<List<Task>> = dao.observeAllTasks().mapToDomain()

        override suspend fun createTask(
            title: String,
            date: LocalDate,
            time: LocalTime?,
            note: String?,
            isGlobal: Boolean,
        ): Task {
            val now = Instant.now()
            val entity =
                TaskEntity(
                    title = title,
                    dateEpochDay = date.toEpochDay(),
                    timeMinuteOfDay = if (isGlobal) null else time?.let { it.hour * 60 + it.minute },
                    note = note,
                    completed = false,
                    createdAtEpochMillis = now.toEpochMilli(),
                    updatedAtEpochMillis = now.toEpochMilli(),
                    syncId = newSyncId(),
                    isGlobal = isGlobal,
                )
            return entity.copy(id = dao.insert(entity)).toDomain()
        }

        override suspend fun updateTask(task: Task) = dao.update(task.normalized().toEntity())

        override suspend fun setCompleted(
            id: Long,
            completed: Boolean,
        ) {
            dao.getById(id)?.let {
                dao.update(
                    it.copy(
                        completed = completed,
                        completedAtEpochMillis = if (completed) nowMillis() else null,
                        updatedAtEpochMillis = nowMillis(),
                    ),
                )
            }
        }

        override suspend fun moveTask(
            id: Long,
            date: LocalDate,
        ) {
            dao.getById(id)?.let {
                dao.update(
                    it.copy(
                        dateEpochDay = date.toEpochDay(),
                        isGlobal = false,
                        updatedAtEpochMillis = nowMillis(),
                    ),
                )
            }
        }

        override suspend fun restoreCompletedTask(
            id: Long,
            date: LocalDate,
        ) {
            dao.getById(id)?.let {
                dao.update(
                    it.copy(
                        completed = false,
                        completedAtEpochMillis = null,
                        dateEpochDay = date.toEpochDay(),
                        isGlobal = false,
                        updatedAtEpochMillis = nowMillis(),
                    ),
                )
            }
        }

        override suspend fun deleteTask(id: Long) {
            dao.markDeleted(id = id, now = nowMillis())
        }

        override suspend fun restoreTask(task: Task) {
            dao.insertAll(listOf(task.normalized().toEntity()))
        }

        override suspend fun replaceAll(tasks: List<Task>) {
            dao.replaceAll(tasks.map { it.normalized().toEntity() })
        }

        override suspend fun purgeTombstones(expiresBefore: Instant): Int = dao.deleteTombstonesBefore(expiresBefore.toEpochMilli())

        override suspend fun carryOverIncompleteTasks(targetDate: LocalDate): Int =
            dao.carryOverIncomplete(
                target = targetDate.toEpochDay(),
                now = nowMillis(),
            )

        override suspend fun deleteCompleted(completedBefore: Instant?): Int = dao.deleteCompleted(completedBefore?.toEpochMilli())

        override suspend fun setCalendarEventId(
            id: Long,
            eventId: Long?,
        ) {
            dao.setCalendarEventId(id = id, eventId = eventId, now = nowMillis())
        }

        private fun Flow<List<TaskEntity>>.mapToDomain(): Flow<List<Task>> = map { entities -> entities.map(TaskEntity::toDomain) }

        private fun nowMillis(): Long = Instant.now().toEpochMilli()
    }

private fun TaskEntity.toDomain(): Task =
    Task(
        id = id,
        title = title,
        date = LocalDate.ofEpochDay(dateEpochDay),
        time = timeMinuteOfDay?.let { LocalTime.of(it / 60, it % 60) },
        note = note,
        completed = completed,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
        syncId = syncId,
        carriedFrom = carriedFromEpochDay?.let(LocalDate::ofEpochDay),
        calendarEventId = calendarEventId,
        isGlobal = isGlobal,
        completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli),
        deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    )

private fun Task.toEntity(): TaskEntity =
    TaskEntity(
        id = id,
        title = title,
        dateEpochDay = date.toEpochDay(),
        timeMinuteOfDay = time?.let { it.hour * 60 + it.minute },
        note = note,
        completed = completed,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
        syncId = syncId,
        carriedFromEpochDay = carriedFrom?.toEpochDay(),
        calendarEventId = calendarEventId,
        isGlobal = isGlobal,
        completedAtEpochMillis = completedAt?.toEpochMilli(),
        deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    )
