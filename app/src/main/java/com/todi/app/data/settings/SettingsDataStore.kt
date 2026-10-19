package com.todi.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.LanguageMode
import com.todi.app.domain.model.LockScreenScope
import com.todi.app.domain.model.NotificationScope
import com.todi.app.domain.model.ThemeMode
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.util.NavBarRoutes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "todi_settings")

@Singleton
class SettingsDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            context.settingsDataStore.data.map { prefs ->
                AppSettings(
                    themeMode =
                        prefs[KEY_THEME]
                            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                            ?: ThemeMode.SYSTEM,
                    languageMode =
                        prefs[KEY_LANGUAGE]
                            ?.let { runCatching { LanguageMode.valueOf(it) }.getOrNull() }
                            ?: LanguageMode.SYSTEM,
                    notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                    showOnLockScreen = prefs[KEY_LOCK_SCREEN] ?: true,
                    notificationScope = decodeScope(prefs),
                    notificationPromptShown = prefs[KEY_PROMPT_SHOWN] ?: false,
                    autoCarryOver = prefs[KEY_AUTO_CARRY] ?: true,
                    calendarSyncEnabled = prefs[KEY_CALENDAR_SYNC] ?: false,
                    showCompletedInDay = prefs[KEY_SHOW_COMPLETED] ?: true,
                    completedRetentionMonths = prefs[KEY_RETENTION_MONTHS] ?: 0,
                    notificationColorToday = prefs[KEY_COLOR_TODAY],
                    notificationColorOverdue = prefs[KEY_COLOR_OVERDUE],
                    notificationColorDateLess = prefs[KEY_COLOR_DATE_LESS],
                    lockScreenScope =
                        prefs[KEY_LOCK_SCOPE]
                            ?.let { runCatching { LockScreenScope.valueOf(it) }.getOrNull() }
                            ?: LockScreenScope.FULL,
                    globalReminderEnabled = prefs[KEY_GLOBAL_REMINDER] ?: false,
                    globalReminderStartMinutes = prefs[KEY_GLOBAL_REMINDER_START] ?: 600,
                    globalReminderIntervalMinutes = prefs[KEY_GLOBAL_REMINDER_INTERVAL] ?: 120,
                    globalReminderQuietAfterMinutes = prefs[KEY_GLOBAL_REMINDER_QUIET_AFTER] ?: 1320,
                    bottomBarRoutes = NavBarRoutes.decode(prefs[KEY_BOTTOM_BAR]),
                    voiceInputEnabled = prefs[KEY_VOICE_INPUT] ?: true,
                    backupEnabled = prefs[KEY_BACKUP_ENABLED] ?: false,
                    backupLastUploadAt = prefs[KEY_BACKUP_LAST]?.let(Instant::ofEpochMilli),
                )
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            context.settingsDataStore.edit { it[KEY_THEME] = mode.name }
        }

        override suspend fun setLanguageMode(mode: LanguageMode) {
            context.settingsDataStore.edit { it[KEY_LANGUAGE] = mode.name }
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean) {
            context.settingsDataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
        }

        override suspend fun setShowOnLockScreen(show: Boolean) {
            context.settingsDataStore.edit { it[KEY_LOCK_SCREEN] = show }
        }

        override suspend fun setNotificationScope(scope: NotificationScope) {
            context.settingsDataStore.edit { it[KEY_NOTIFICATION_SCOPE] = scope.name }
        }

        private fun decodeScope(prefs: Preferences): NotificationScope =
            prefs[KEY_NOTIFICATION_SCOPE]
                ?.let { runCatching { NotificationScope.valueOf(it) }.getOrNull() }
                ?: if (prefs[KEY_OVERDUE] == true) NotificationScope.WITH_OVERDUE else NotificationScope.TODAY_ONLY

        override suspend fun setAutoCarryOver(enabled: Boolean) {
            context.settingsDataStore.edit { it[KEY_AUTO_CARRY] = enabled }
        }

        override suspend fun setCalendarSyncEnabled(enabled: Boolean) {
            context.settingsDataStore.edit { it[KEY_CALENDAR_SYNC] = enabled }
        }

        override suspend fun setShowCompletedInDay(show: Boolean) {
            context.settingsDataStore.edit { it[KEY_SHOW_COMPLETED] = show }
        }

        override suspend fun setCompletedRetentionMonths(months: Int) {
            context.settingsDataStore.edit { it[KEY_RETENTION_MONTHS] = months }
        }

        override suspend fun setNotificationColorToday(argb: Int?) {
            context.settingsDataStore.edit { prefs -> prefs.writeColor(KEY_COLOR_TODAY, argb) }
        }

        override suspend fun setNotificationColorOverdue(argb: Int?) {
            context.settingsDataStore.edit { prefs -> prefs.writeColor(KEY_COLOR_OVERDUE, argb) }
        }

        override suspend fun setNotificationColorDateLess(argb: Int?) {
            context.settingsDataStore.edit { prefs -> prefs.writeColor(KEY_COLOR_DATE_LESS, argb) }
        }

        override suspend fun setLockScreenScope(scope: LockScreenScope) {
            context.settingsDataStore.edit { it[KEY_LOCK_SCOPE] = scope.name }
        }

        private fun MutablePreferences.writeColor(
            key: Preferences.Key<Int>,
            argb: Int?,
        ) {
            if (argb == null) remove(key) else set(key, argb)
        }

        override suspend fun setGlobalReminderEnabled(enabled: Boolean) {
            context.settingsDataStore.edit { it[KEY_GLOBAL_REMINDER] = enabled }
        }

        override suspend fun setGlobalReminderStartMinutes(minutes: Int) {
            context.settingsDataStore.edit { it[KEY_GLOBAL_REMINDER_START] = minutes }
        }

        override suspend fun setGlobalReminderIntervalMinutes(minutes: Int) {
            context.settingsDataStore.edit { it[KEY_GLOBAL_REMINDER_INTERVAL] = minutes }
        }

        override suspend fun setGlobalReminderQuietAfterMinutes(minutes: Int) {
            context.settingsDataStore.edit { it[KEY_GLOBAL_REMINDER_QUIET_AFTER] = minutes }
        }

        override suspend fun markNotificationPromptShown() {
            context.settingsDataStore.edit { it[KEY_PROMPT_SHOWN] = true }
        }

        override suspend fun setBottomBarRoutes(routes: List<String>) {
            context.settingsDataStore.edit { it[KEY_BOTTOM_BAR] = NavBarRoutes.encode(routes) }
        }

        override suspend fun setVoiceInputEnabled(enabled: Boolean) {
            context.settingsDataStore.edit { it[KEY_VOICE_INPUT] = enabled }
        }

        override suspend fun setBackupEnabled(enabled: Boolean) {
            context.settingsDataStore.edit { it[KEY_BACKUP_ENABLED] = enabled }
        }

        override suspend fun setBackupLastUploadAt(millis: Long) {
            context.settingsDataStore.edit { it[KEY_BACKUP_LAST] = millis }
        }

        private companion object {
            val KEY_THEME = stringPreferencesKey("theme_mode")
            val KEY_LANGUAGE = stringPreferencesKey("language_mode")
            val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
            val KEY_LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
            val KEY_NOTIFICATION_SCOPE = stringPreferencesKey("notification_scope")

            val KEY_OVERDUE = booleanPreferencesKey("include_overdue")
            val KEY_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
            val KEY_AUTO_CARRY = booleanPreferencesKey("auto_carry_over")
            val KEY_CALENDAR_SYNC = booleanPreferencesKey("calendar_sync_enabled")
            val KEY_SHOW_COMPLETED = booleanPreferencesKey("show_completed_in_day")
            val KEY_RETENTION_MONTHS = intPreferencesKey("completed_retention_months")
            val KEY_COLOR_TODAY = intPreferencesKey("notification_color_today")
            val KEY_COLOR_OVERDUE = intPreferencesKey("notification_color_overdue")
            val KEY_COLOR_DATE_LESS = intPreferencesKey("notification_color_date_less")
            val KEY_LOCK_SCOPE = stringPreferencesKey("lock_screen_scope")
            val KEY_GLOBAL_REMINDER = booleanPreferencesKey("global_reminder_enabled")
            val KEY_GLOBAL_REMINDER_START = intPreferencesKey("global_reminder_start_minutes")
            val KEY_GLOBAL_REMINDER_INTERVAL = intPreferencesKey("global_reminder_interval_minutes")
            val KEY_GLOBAL_REMINDER_QUIET_AFTER = intPreferencesKey("global_reminder_quiet_after_minutes")

            val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
            val KEY_BACKUP_LAST = longPreferencesKey("backup_last_upload_at_millis")

            val KEY_BOTTOM_BAR = stringPreferencesKey("bottom_bar_routes")
            val KEY_VOICE_INPUT = booleanPreferencesKey("voice_input_enabled")
        }
    }
