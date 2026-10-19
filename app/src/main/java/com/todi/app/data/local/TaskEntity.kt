package com.todi.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["isGlobal", "dateEpochDay"]),
        Index(value = ["completed", "completedAtEpochMillis"]),
        Index(value = ["syncId"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val note: String?,
    val completed: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val syncId: String,
    val carriedFromEpochDay: Long? = null,
    val calendarEventId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val isGlobal: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val deletedAtEpochMillis: Long? = null,
)
