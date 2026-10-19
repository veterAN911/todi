package com.todi.app.domain.repository

import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.LanguageMode
import com.todi.app.domain.model.LockScreenScope
import com.todi.app.domain.model.NotificationScope
import com.todi.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLanguageMode(mode: LanguageMode)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setShowOnLockScreen(show: Boolean)

    suspend fun setNotificationScope(scope: NotificationScope)

    suspend fun setAutoCarryOver(enabled: Boolean)

    suspend fun setCalendarSyncEnabled(enabled: Boolean)

    suspend fun setShowCompletedInDay(show: Boolean)

    suspend fun setCompletedRetentionMonths(months: Int)

    suspend fun setNotificationColorToday(argb: Int?)

    suspend fun setNotificationColorOverdue(argb: Int?)

    suspend fun setNotificationColorDateLess(argb: Int?)

    suspend fun setLockScreenScope(scope: LockScreenScope)

    suspend fun setGlobalReminderEnabled(enabled: Boolean)

    suspend fun setGlobalReminderStartMinutes(minutes: Int)

    suspend fun setGlobalReminderIntervalMinutes(minutes: Int)

    suspend fun setGlobalReminderQuietAfterMinutes(minutes: Int)

    suspend fun setBottomBarRoutes(routes: List<String>)

    suspend fun setVoiceInputEnabled(enabled: Boolean)

    suspend fun setBackupEnabled(enabled: Boolean)

    suspend fun setBackupLastUploadAt(millis: Long)

    suspend fun markNotificationPromptShown()
}
