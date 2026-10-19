package com.todi.app.domain.util

import com.todi.app.domain.model.Task
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

sealed interface SnapshotDecode {
    data class Restored(
        val tasks: List<Task>,
    ) : SnapshotDecode

    data class TooNew(
        val version: Int,
    ) : SnapshotDecode

    data object Unreadable : SnapshotDecode
}

object TaskSnapshotCodec {
    const val CURRENT_VERSION = 1
    private const val APP = "todi"

    fun encode(
        tasks: List<Task>,
        exportedAt: Instant,
    ): String {
        val json =
            JSONObject()
                .put("version", CURRENT_VERSION)
                .put("app", APP)
                .put("exportedAtEpochMillis", exportedAt.toEpochMilli())
                .put(
                    "tasks",
                    JSONArray().apply { tasks.forEach { put(encodeTask(it)) } },
                )
        return json.toString()
    }

    fun contentDigest(tasks: List<Task>): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(encode(tasks, Instant.EPOCH).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun decode(json: String): SnapshotDecode {
        val root = runCatching { JSONObject(json) }.getOrElse { return SnapshotDecode.Unreadable }

        val version = root.optInt("version", 0)
        if (version > CURRENT_VERSION) return SnapshotDecode.TooNew(version)
        if (version <= 0) return SnapshotDecode.Unreadable

        val array = root.optJSONArray("tasks") ?: return SnapshotDecode.Restored(emptyList())
        val tasks =
            buildList {
                for (i in 0 until array.length()) {
                    val task = array.optJSONObject(i)?.let(::decodeTask)

                    if (task != null) add(task)
                }
            }
        return SnapshotDecode.Restored(tasks)
    }

    private fun encodeTask(task: Task): JSONObject =
        JSONObject()
            .put("id", task.id)
            .put("title", task.title)
            .put("dateEpochDay", task.date.toEpochDay())
            .put("timeMinuteOfDay", task.time?.let { it.hour * 60 + it.minute })
            .put("note", task.note)
            .put("completed", task.completed)
            .put("createdAtEpochMillis", task.createdAt.toEpochMilli())
            .put("updatedAtEpochMillis", task.updatedAt.toEpochMilli())
            .put("syncId", task.syncId)
            .put("carriedFromEpochDay", task.carriedFrom?.toEpochDay())
            .put("calendarEventId", task.calendarEventId)
            .put("isGlobal", task.isGlobal)
            .put("completedAtEpochMillis", task.completedAt?.toEpochMilli())
            .put("deletedAtEpochMillis", task.deletedAt?.toEpochMilli())

    private fun decodeTask(json: JSONObject): Task? {
        val id = json.optLong("id", 0L).coerceAtLeast(0L)
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        val date =
            json
                .optLong("dateEpochDay", Long.MIN_VALUE)
                .takeIf { it != Long.MIN_VALUE }
                ?.let(LocalDate::ofEpochDay)
                ?: return null

        return Task(
            id = id,
            title = title,
            date = date,
            time =
                json.optIntOrNull("timeMinuteOfDay")?.let { minutes ->
                    LocalTime.of(minutes / 60, minutes % 60)
                },
            note = json.optStringOrNull("note"),
            completed = json.optBoolean("completed", false),
            createdAt =
                json
                    .optLong("createdAtEpochMillis", 0L)
                    .takeIf { it > 0 }
                    ?.let(Instant::ofEpochMilli)
                    ?: json.optLong("updatedAtEpochMillis", 0L).let(Instant::ofEpochMilli),
            updatedAt = json.optLong("updatedAtEpochMillis", 0L).let(Instant::ofEpochMilli),
            syncId = json.optString("syncId").takeIf { it.isNotBlank() } ?: newSyncId(),
            carriedFrom = json.optLongOrNull("carriedFromEpochDay")?.let(LocalDate::ofEpochDay),
            calendarEventId = null,
            isGlobal = json.optBoolean("isGlobal", false),
            completedAt = json.optLongOrNull("completedAtEpochMillis")?.let(Instant::ofEpochMilli),
            deletedAt = json.optLongOrNull("deletedAtEpochMillis")?.let(Instant::ofEpochMilli),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
}
