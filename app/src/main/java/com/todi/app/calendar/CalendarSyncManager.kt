package com.todi.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import androidx.core.content.ContextCompat
import com.todi.app.domain.model.Task
import com.todi.app.domain.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val taskRepository: TaskRepository,
    ) {
        private val _lastSyncError = MutableStateFlow<String?>(null)

        val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

        fun hasPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

        suspend fun reconcile(tasks: List<Task>) =
            withContext(Dispatchers.IO) {
                try {
                    reconcileInternal(tasks)
                    _lastSyncError.value = null
                } catch (e: Exception) {
                    _lastSyncError.value = "${e::class.simpleName}: ${e.message}"
                }
            }

        private suspend fun reconcileInternal(tasks: List<Task>) {
            if (!hasPermission()) {
                Log.d(TAG, "reconcile skipped: no calendar permission")
                return
            }
            val calendarId = findOrCreateCalendar()
            if (calendarId == null) {
                Log.d(TAG, "reconcile skipped: no usable calendar")
                _lastSyncError.value = NO_CALENDARS_CODE
                return
            }
            Log.d(TAG, "reconcile: calendar=$calendarId, tasks=${tasks.size}")
            val existing = readTodiEvents(calendarId)
            val activeIds = tasks.mapTo(mutableSetOf()) { it.id }

            for (task in tasks) {
                val eventId = task.calendarEventId ?: existing[task.id]?.first

                if (task.completed || task.isGlobal || task.deletedAt != null) {
                    if (eventId != null) {
                        deleteEvent(eventId)
                        if (task.calendarEventId != null) taskRepository.setCalendarEventId(task.id, null)
                    }
                    continue
                }
                if (eventId != null) {
                    updateEvent(calendarId, eventId, task)
                } else {
                    val created = insertEvent(calendarId, task)
                    if (created != null) taskRepository.setCalendarEventId(task.id, created)
                }
            }

            for ((taskId, event) in existing) {
                if (taskId !in activeIds) deleteEvent(event.first)
            }
        }

        private fun findOrCreateCalendar(): Long? {
            val projection =
                arrayOf(
                    Calendars._ID,
                    Calendars.ACCOUNT_TYPE,
                    Calendars.CALENDAR_DISPLAY_NAME,
                    Calendars.IS_PRIMARY,
                )
            var localCalendarId: Long? = null
            var anyGoogleCalendar: Long? = null

            val cursor =
                runCatching {
                    context.contentResolver.query(
                        Calendars.CONTENT_URI,
                        projection,
                        "${Calendars.VISIBLE} = 1",
                        null,
                        null,
                    )
                }.onFailure { Log.d(TAG, "calendar query failed: ${it::class.simpleName} ${it.message}") }
                    .getOrNull() ?: return null

            cursor.use {
                val idIdx = cursor.getColumnIndexOrThrow(Calendars._ID)
                val typeIdx = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE)
                val nameIdx = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
                val primaryIdx = cursor.getColumnIndexOrThrow(Calendars.IS_PRIMARY)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val isGoogle = cursor.getString(typeIdx) == "com.google"

                    if (isGoogle && cursor.getInt(primaryIdx) == 1) return id
                    if (isGoogle && anyGoogleCalendar == null) anyGoogleCalendar = id
                    if (cursor.getString(nameIdx) == CALENDAR_NAME) localCalendarId = id
                }
            }

            anyGoogleCalendar?.let { return it }
            localCalendarId?.let { return it }

            val values =
                ContentValues().apply {
                    put(Calendars.ACCOUNT_NAME, "Todi")
                    put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                    put(Calendars.NAME, CALENDAR_NAME)
                    put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
                    put(Calendars.CALENDAR_COLOR, 0xFF8E9BFF.toInt())
                    put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                    put(Calendars.OWNER_ACCOUNT, "todi")
                    put(Calendars.VISIBLE, 1)
                    put(Calendars.SYNC_EVENTS, 1)
                }
            val uri = runCatching { context.contentResolver.insert(Calendars.CONTENT_URI, values) }.getOrNull()
            return uri?.let(ContentUris::parseId)
        }

        private fun readTodiEvents(calendarId: Long): Map<Long, Pair<Long, String>> {
            val result = mutableMapOf<Long, Pair<Long, String>>()
            context.contentResolver
                .query(
                    Events.CONTENT_URI,
                    arrayOf(Events._ID, Events.DESCRIPTION),
                    "${Events.CALENDAR_ID} = ? AND ${Events.DESCRIPTION} LIKE '$MARKER_PREFIX%'",
                    arrayOf(calendarId.toString()),
                    null,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(Events._ID)
                    val descIdx = cursor.getColumnIndexOrThrow(Events.DESCRIPTION)
                    while (cursor.moveToNext()) {
                        val description = cursor.getString(descIdx) ?: continue
                        taskIdFromMarker(description)?.let { taskId ->
                            result[taskId] = cursor.getLong(idIdx) to description
                        }
                    }
                }
            return result
        }

        private fun insertEvent(
            calendarId: Long,
            task: Task,
        ): Long? {
            val uri =
                runCatching { context.contentResolver.insert(Events.CONTENT_URI, eventValues(calendarId, task)) }
                    .onFailure { Log.d(TAG, "insert failed: ${it::class.simpleName} ${it.message}") }
                    .getOrNull()
            return uri?.let(ContentUris::parseId)
        }

        private fun updateEvent(
            calendarId: Long,
            eventId: Long,
            task: Task,
        ) {
            runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                    eventValues(calendarId, task),
                    null,
                    null,
                )
            }
        }

        private fun deleteEvent(eventId: Long) {
            runCatching {
                context.contentResolver.delete(
                    ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                    null,
                    null,
                )
            }
        }

        private fun eventValues(
            calendarId: Long,
            task: Task,
        ): ContentValues {
            val zone = ZoneId.systemDefault()
            return ContentValues().apply {
                put(Events.CALENDAR_ID, calendarId)
                put(Events.TITLE, task.title)
                put(Events.DESCRIPTION, buildMarkerDescription(task))
                put(Events.EVENT_TIMEZONE, zone.id)
                if (task.time != null) {
                    val start =
                        task.date
                            .atTime(task.time)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                    put(Events.DTSTART, start)
                    put(Events.DTEND, start + EVENT_DURATION_MILLIS)
                } else {
                    val start =
                        task.date
                            .atStartOfDay(java.time.ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli()
                    put(Events.ALL_DAY, 1)
                    put(Events.DTSTART, start)
                    put(Events.DTEND, start + DAY_MILLIS)
                }
            }
        }

        private fun buildMarkerDescription(task: Task): String {
            val marker = "$MARKER_PREFIX${task.id}"
            return if (task.note.isNullOrBlank()) marker else task.note + "\n\n" + marker
        }

        private fun taskIdFromMarker(description: String): Long? {
            val idx = description.lastIndexOf("$MARKER_PREFIX")
            if (idx < 0) return null
            return description.substring(idx + MARKER_PREFIX.length).trim().toLongOrNull()
        }

        companion object {
            const val NO_CALENDARS_CODE = "no_calendars"

            const val TAG = "CalendarSync"
            const val CALENDAR_NAME = "Todi"
            const val MARKER_PREFIX = "Todi:"
            const val EVENT_DURATION_MILLIS = 60 * 60 * 1000L
            const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        }
    }
