package com.todi.app.domain

import com.todi.app.domain.model.Task
import com.todi.app.domain.util.SnapshotDecode
import com.todi.app.domain.util.TaskSnapshotCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TaskSnapshotCodecTest {
    private val now: Instant = Instant.ofEpochMilli(1_787_000_000_000)

    private fun task(
        id: Long = 1,
        title: String = "Buy bread",
        date: LocalDate = LocalDate.of(2026, 9, 13),
        time: LocalTime? = null,
        note: String? = null,
        completed: Boolean = false,
        syncId: String = "abcdef0123456789abcdef0123456789",
        carriedFrom: LocalDate? = null,
        calendarEventId: Long? = null,
        isGlobal: Boolean = false,
        completedAt: Instant? = null,
        deletedAt: Instant? = null,
    ) = Task(
        id = id,
        title = title,
        date = date,
        time = time,
        note = note,
        completed = completed,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000),
        updatedAt = Instant.ofEpochMilli(1_700_000_500_000),
        syncId = syncId,
        carriedFrom = carriedFrom,
        calendarEventId = calendarEventId,
        isGlobal = isGlobal,
        completedAt = completedAt,
        deletedAt = deletedAt,
    )

    private fun restored(json: String): List<Task> {
        val result = TaskSnapshotCodec.decode(json)
        assertTrue("ожидалось Restored, а не $result", result is SnapshotDecode.Restored)
        return (result as SnapshotDecode.Restored).tasks
    }

    private fun encoded(vararg tasks: Task): JSONObject = JSONObject(TaskSnapshotCodec.encode(tasks.toList(), now))

    @Test
    fun theDigestOfOneList_doesNotMove() {
        assertEquals(
            TaskSnapshotCodec.contentDigest(listOf(task())),
            TaskSnapshotCodec.contentDigest(listOf(task())),
        )
    }

    @Test
    fun theDigest_changesWithTheList() {
        assertNotEquals(
            TaskSnapshotCodec.contentDigest(listOf(task())),
            TaskSnapshotCodec.contentDigest(listOf(task().copy(title = "Купить молоко"))),
        )
        assertNotEquals(
            TaskSnapshotCodec.contentDigest(listOf(task())),
            TaskSnapshotCodec.contentDigest(listOf(task(), task(id = 2))),
        )
        assertNotEquals(
            TaskSnapshotCodec.contentDigest(listOf(task())),
            TaskSnapshotCodec.contentDigest(emptyList()),
        )
    }

    @Test
    fun theDigest_noticesADeletion() {
        assertNotEquals(
            TaskSnapshotCodec.contentDigest(listOf(task())),
            TaskSnapshotCodec.contentDigest(listOf(task().copy(deletedAt = now))),
        )
    }

    @Test
    fun roundTrip_returnsEveryField() {
        val original =
            task(
                id = 42,
                title = "Позвонить в страховую 🚗",
                date = LocalDate.of(2026, 9, 30),
                time = LocalTime.of(14, 5),
                note = "полис №123, спросить про франшизу",
                completed = true,
                carriedFrom = LocalDate.of(2026, 9, 29),
                isGlobal = false,
                completedAt = Instant.ofEpochMilli(1_787_000_000_000),
            )

        val back = restored(TaskSnapshotCodec.encode(listOf(original), now))

        assertEquals(listOf(original.copy(calendarEventId = null)), back)
    }

    @Test
    fun cyrillicAndEmoji_surviveTheTrip() {
        val original =
            task(
                title = "Купить хлеб 🥖",
                note = "«Бородинский», 1 буханка\nесли нет — батон",
            )

        val back = restored(TaskSnapshotCodec.encode(listOf(original), now)).single()

        assertEquals("Купить хлеб 🥖", back.title)
        assertEquals("«Бородинский», 1 буханка\nесли нет — батон", back.note)
    }

    @Test
    fun globalTask_losesNeitherFlagNorTimeRule() {
        val original = task(title = "Оплатить интернет", isGlobal = true)

        val back = restored(TaskSnapshotCodec.encode(listOf(original), now)).single()

        assertTrue(back.isGlobal)
        assertNull(back.time)
    }

    @Test
    fun taskIds_areKept_soCalendarMarkersStayValid() {
        val tasks = listOf(task(id = 7), task(id = 913, title = "Second"))

        val back = restored(TaskSnapshotCodec.encode(tasks, now))

        assertEquals(listOf(7L, 913L), back.map { it.id })
    }

    @Test
    fun calendarEventId_isWrittenButNotRestored() {
        val original = task(calendarEventId = 4711)
        val json = encoded(original)

        assertEquals(4711L, json.getJSONArray("tasks").getJSONObject(0).getLong("calendarEventId"))
        assertNull(restored(json.toString()).single().calendarEventId)
    }

    @Test
    fun tombstone_survivesTheRoundTrip() {
        val deletedAt = Instant.ofEpochMilli(1_787_000_111_000)
        val original = task(deletedAt = deletedAt)

        val back = restored(TaskSnapshotCodec.encode(listOf(original), now)).single()

        assertEquals(deletedAt, back.deletedAt)
    }

    @Test
    fun emptyList_roundTripsAsEmpty_notAsUnreadable() {
        assertEquals(emptyList<Task>(), restored(TaskSnapshotCodec.encode(emptyList(), now)))
    }

    @Test
    fun newerVersion_isRefusedBeforeAnythingIsRead() {
        val json = encoded(task()).put("version", TaskSnapshotCodec.CURRENT_VERSION + 1)

        assertEquals(SnapshotDecode.TooNew(2), TaskSnapshotCodec.decode(json.toString()))
    }

    @Test
    fun missingVersion_isUnreadable() {
        val json = encoded(task())
        json.remove("version")

        assertEquals(SnapshotDecode.Unreadable, TaskSnapshotCodec.decode(json.toString()))
    }

    @Test
    fun brokenJson_isUnreadable_notAnException() {
        listOf("", "{", "not json at all", "<html>403 Forbidden</html>", "[1,2,3]")
            .forEach { text ->
                assertEquals("вход: «$text»", SnapshotDecode.Unreadable, TaskSnapshotCodec.decode(text))
            }
    }

    @Test
    fun unknownKeys_areIgnored() {
        val json =
            encoded(task()).apply {
                put("futureField", "whatever")
                getJSONArray("tasks").getJSONObject(0).put("somethingNew", 17)
            }

        assertEquals(1, restored(json.toString()).size)
    }

    @Test
    fun missingOptionalKeys_fallBackToDefaults() {
        val json =
            JSONObject()
                .put("version", TaskSnapshotCodec.CURRENT_VERSION)
                .put(
                    "tasks",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("id", 5)
                            .put("title", "Only the essentials")
                            .put("dateEpochDay", 20_709L),
                    ),
                )

        val back = restored(json.toString()).single()

        assertEquals("Only the essentials", back.title)
        assertEquals(LocalDate.ofEpochDay(20_709), back.date)
        assertNull(back.time)
        assertNull(back.note)
        assertEquals(false, back.completed)
        assertEquals(false, back.isGlobal)
        assertNull(back.carriedFrom)
        assertNull(back.completedAt)
        assertNull(back.deletedAt)

        assertEquals(32, back.syncId.length)
    }

    @Test
    fun missingCreationStamp_fallsBackToTheLastChange_notTo1970() {
        val json =
            JSONObject()
                .put("version", TaskSnapshotCodec.CURRENT_VERSION)
                .put(
                    "tasks",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("id", 1)
                            .put("title", "Из чужого файла")
                            .put("dateEpochDay", 20_709L)
                            .put("updatedAtEpochMillis", 1_787_000_000_000L),
                    ),
                )

        val back = restored(json.toString()).single()

        assertEquals(Instant.ofEpochMilli(1_787_000_000_000), back.createdAt)
        assertEquals(back.updatedAt, back.createdAt)
    }

    @Test
    fun rowWithoutTitleOrDate_isSkipped_butTheRestOfTheBackupIsKept() {
        val tasks =
            org.json
                .JSONArray()
                .put(JSONObject().put("id", 1).put("title", "Good one").put("dateEpochDay", 20_709L))
                .put(JSONObject().put("id", 2).put("dateEpochDay", 20_709L))
                .put(JSONObject().put("id", 3).put("title", "No date"))

        val json =
            JSONObject()
                .put("version", TaskSnapshotCodec.CURRENT_VERSION)
                .put("tasks", tasks)

        assertEquals(listOf("Good one"), restored(json.toString()).map { it.title })
    }

    @Test
    fun emptyNote_isNull_notAnEmptyString() {
        val json =
            JSONObject()
                .put("version", TaskSnapshotCodec.CURRENT_VERSION)
                .put(
                    "tasks",
                    org.json.JSONArray().put(
                        JSONObject()
                            .put("id", 1)
                            .put("title", "Plain")
                            .put("dateEpochDay", 20_709L)
                            .put("note", JSONObject.NULL),
                    ),
                )

        assertNull(restored(json.toString()).single().note)
    }

    @Test
    fun midnight_isAValidTime() {
        val original = task(time = LocalTime.MIDNIGHT)

        assertEquals(LocalTime.MIDNIGHT, restored(TaskSnapshotCodec.encode(listOf(original), now)).single().time)
    }
}
