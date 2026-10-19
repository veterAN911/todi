package com.todi.app.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LanguageMode { SYSTEM, RU, EN }

enum class LockScreenScope { FULL, TITLE_ONLY }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val notificationScope: NotificationScope = NotificationScope.TODAY_ONLY,
    val notificationPromptShown: Boolean = false,
    val autoCarryOver: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    val showCompletedInDay: Boolean = true,
    val completedRetentionMonths: Int = 0,
    val notificationColorToday: Int? = null,
    val notificationColorOverdue: Int? = null,
    val notificationColorDateLess: Int? = null,
    val lockScreenScope: LockScreenScope = LockScreenScope.FULL,
    val globalReminderEnabled: Boolean = false,
    val globalReminderStartMinutes: Int = 600,
    val globalReminderIntervalMinutes: Int = 120,
    val globalReminderQuietAfterMinutes: Int = 1320,
    val bottomBarRoutes: List<String>? = null,
    val voiceInputEnabled: Boolean = true,
    val backupEnabled: Boolean = false,
    val backupLastUploadAt: Instant? = null,
) {
    val globalReminderStart: LocalTime get() = minutesToTime(globalReminderStartMinutes)

    val globalReminderQuietAfter: LocalTime get() = minutesToTime(globalReminderQuietAfterMinutes)

    val globalReminderInterval: Duration
        get() = Duration.ofMinutes(globalReminderIntervalMinutes.coerceAtLeast(0).toLong())
}

private fun minutesToTime(minutes: Int): LocalTime {
    val wrapped = minutes.mod(HOURS_PER_DAY * MINUTES_PER_HOUR)
    return LocalTime.of(wrapped / MINUTES_PER_HOUR, wrapped % MINUTES_PER_HOUR)
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
