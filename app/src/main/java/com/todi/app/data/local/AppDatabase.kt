package com.todi.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "todi.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN carriedFromEpochDay INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN calendarEventId INTEGER DEFAULT NULL")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN isGlobal INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN completedAtEpochMillis INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAtEpochMillis INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN ownerEmail TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN ownerName TEXT DEFAULT NULL")

                    db.execSQL("UPDATE tasks SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
                    db.execSQL("CREATE INDEX index_tasks_isGlobal_dateEpochDay ON tasks (isGlobal, dateEpochDay)")
                    db.execSQL(
                        "CREATE INDEX index_tasks_completed_completedAtEpochMillis " +
                            "ON tasks (completed, completedAtEpochMillis)",
                    )
                    db.execSQL("CREATE UNIQUE INDEX index_tasks_syncId ON tasks (syncId)")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE tasks_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            dateEpochDay INTEGER NOT NULL,
                            timeMinuteOfDay INTEGER,
                            note TEXT,
                            completed INTEGER NOT NULL,
                            createdAtEpochMillis INTEGER NOT NULL,
                            updatedAtEpochMillis INTEGER NOT NULL,
                            syncId TEXT NOT NULL,
                            carriedFromEpochDay INTEGER,
                            calendarEventId INTEGER,
                            isGlobal INTEGER NOT NULL DEFAULT 0,
                            completedAtEpochMillis INTEGER,
                            deletedAtEpochMillis INTEGER
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO tasks_new
                        SELECT id, title, dateEpochDay, timeMinuteOfDay, note, completed,
                               createdAtEpochMillis, updatedAtEpochMillis, syncId,
                               carriedFromEpochDay, calendarEventId, isGlobal,
                               completedAtEpochMillis, deletedAtEpochMillis
                        FROM tasks
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE tasks")
                    db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
                    db.execSQL("CREATE INDEX index_tasks_isGlobal_dateEpochDay ON tasks (isGlobal, dateEpochDay)")
                    db.execSQL(
                        "CREATE INDEX index_tasks_completed_completedAtEpochMillis " +
                            "ON tasks (completed, completedAtEpochMillis)",
                    )
                    db.execSQL("CREATE UNIQUE INDEX index_tasks_syncId ON tasks (syncId)")
                }
            }
    }
}
