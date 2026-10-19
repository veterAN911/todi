package com.todi.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM tasks
        WHERE dateEpochDay = :date AND isGlobal = 0 AND deletedAtEpochMillis IS NULL
        ORDER BY completed ASC,
                 timeMinuteOfDay IS NULL ASC,
                 timeMinuteOfDay ASC,
                 createdAtEpochMillis ASC,
                 id ASC
        """,
    )
    fun observeTasksForDate(date: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE completed = 0 AND isGlobal = 0 AND deletedAtEpochMillis IS NULL AND dateEpochDay = :date
        ORDER BY timeMinuteOfDay IS NULL ASC,
                 timeMinuteOfDay ASC,
                 createdAtEpochMillis ASC,
                 id ASC
        """,
    )
    fun observeActiveTasksForDate(date: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE completed = 0 AND isGlobal = 0 AND deletedAtEpochMillis IS NULL AND dateEpochDay < :beforeDate
        ORDER BY dateEpochDay ASC,
                 timeMinuteOfDay IS NULL ASC,
                 timeMinuteOfDay ASC,
                 id ASC
        """,
    )
    fun observeOverdueTasks(beforeDate: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE completed = 0 AND isGlobal = 0 AND deletedAtEpochMillis IS NULL AND dateEpochDay BETWEEN :from AND :to
        """,
    )
    fun observeActiveTasksBetween(
        from: Long,
        to: Long,
    ): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isGlobal = 1 AND completed = 0 AND deletedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis ASC, id ASC
        """,
    )
    fun observeGlobalTasks(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE completed = 1 AND deletedAtEpochMillis IS NULL
        ORDER BY completedAtEpochMillis DESC, id DESC
        """,
    )
    fun observeCompletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query(
        """
        UPDATE tasks
        SET dateEpochDay = :target,
            carriedFromEpochDay = COALESCE(carriedFromEpochDay, dateEpochDay),
            updatedAtEpochMillis = :now
        WHERE completed = 0 AND isGlobal = 0 AND deletedAtEpochMillis IS NULL AND dateEpochDay < :target
        """,
    )
    suspend fun carryOverIncomplete(
        target: Long,
        now: Long,
    ): Int

    @Query(
        """
        DELETE FROM tasks
        WHERE completed = 1 AND deletedAtEpochMillis IS NULL
          AND (:before IS NULL OR completedAtEpochMillis < :before)
        """,
    )
    suspend fun deleteCompleted(before: Long?): Int

    @Query(
        """
        UPDATE tasks
        SET deletedAtEpochMillis = :now, updatedAtEpochMillis = :now
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """,
    )
    suspend fun markDeleted(
        id: Long,
        now: Long,
    ): Int

    @Query("DELETE FROM tasks WHERE deletedAtEpochMillis IS NOT NULL AND deletedAtEpochMillis < :before")
    suspend fun deleteTombstonesBefore(before: Long): Int

    @Query("UPDATE tasks SET calendarEventId = :eventId, updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun setCalendarEventId(
        id: Long,
        eventId: Long?,
        now: Long,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(tasks: List<TaskEntity>) {
        deleteAll()
        insertAll(tasks)
    }
}
