package com.todi.app.presentation.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todi.app.R
import com.todi.app.backup.RemoteCopy
import com.todi.app.calendar.CalendarSyncManager
import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.LanguageMode
import com.todi.app.domain.model.LockScreenScope
import com.todi.app.domain.model.NotificationScope
import com.todi.app.domain.model.ThemeMode
import com.todi.app.notification.NotificationPresentation
import com.todi.app.notification.NotificationSection
import com.todi.app.presentation.components.DayHeaderBar
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.bottomBarDestinations
import com.todi.app.presentation.util.AppLanguage
import com.todi.app.presentation.util.DateFormatters
import com.todi.app.presentation.voice.VoskModelFailure
import com.todi.app.presentation.voice.VoskModelState
import com.todi.app.presentation.voice.modelMegabytes
import com.todi.app.presentation.voice.voiceModelFailureText
import java.time.LocalTime

@Composable
fun SettingsScreen(
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val notificationsAllowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()
    val calendarAllowed by viewModel.calendarAllowed.collectAsStateWithLifecycle()
    val lastSyncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
    val archiveCleared by viewModel.archiveCleared.collectAsStateWithLifecycle()
    val voiceModel by viewModel.voiceModel.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val preview by viewModel.notificationPreview.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showClearArchiveDialog by rememberSaveable { mutableStateOf(false) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showQuietPicker by rememberSaveable { mutableStateOf(false) }
    var colorSheet by rememberSaveable { mutableStateOf<NotificationSection?>(null) }
    val clearedText = stringResource(R.string.clear_completed_done)

    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val backupAccount by viewModel.backupAccount.collectAsStateWithLifecycle()

    var backupRestoreId by rememberSaveable { mutableStateOf<String?>(null) }

    val backupNotice: String? =
        when (val message = backup.message) {
            null -> {
                null
            }

            BackupMessage.Uploaded -> {
                stringResource(R.string.settings_backup_uploaded)
            }

            BackupMessage.UpToDate -> {
                stringResource(R.string.settings_backup_up_to_date)
            }

            is BackupMessage.Restored -> {
                stringResource(
                    R.string.settings_backup_restored,
                    pluralStringResource(R.plurals.settings_backup_tasks, message.taskCount, message.taskCount),
                )
            }
        }

    LaunchedEffect(archiveCleared) {
        if (!archiveCleared) return@LaunchedEffect
        snackbarHostState.showSnackbar(clearedText)
        viewModel.consumeArchiveCleared()
    }

    LaunchedEffect(backupNotice) {
        if (backupNotice == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(backupNotice)
        viewModel.consumeBackupNotice()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissionStates()
        viewModel.refreshVoiceModel()
        viewModel.refreshBackup()
        onPauseOrDispose { }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { viewModel.refreshPermissionStates() }

    val calendarPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.refreshPermissionStates()

            viewModel.requestCalendarSync()
        }

    val requestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            DayHeaderBar(
                title = stringResource(R.string.nav_settings),
                subtitle = null,
                count = 0,
                showMenuButton = showMenuButton,
                onOpenMenu = onOpenMenu,
                onSearch = onOpenSearch,
            )

            SettingsSection(title = stringResource(R.string.settings_theme)) {
                ThemeMode.entries.forEach { mode ->
                    SelectableRow(
                        label = stringResource(themeModeLabel(mode)),
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                    if (mode != ThemeMode.entries.last()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_language)) {
                LanguageMode.entries.forEach { mode ->
                    SelectableRow(
                        label = stringResource(languageModeLabel(mode)),
                        selected = settings.languageMode == mode,
                        onClick = {
                            viewModel.setLanguageMode(mode)
                            AppLanguage.apply(context, mode)

                            if (Build.VERSION.SDK_INT < 33) {
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                    )
                    if (mode != LanguageMode.entries.last()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_voice)) {
                SwitchRow(
                    title = stringResource(R.string.settings_voice_input),
                    subtitle = stringResource(R.string.settings_voice_input_desc),
                    checked = settings.voiceInputEnabled,
                    onCheckedChange = viewModel::setVoiceInputEnabled,
                    modifier = Modifier.testTag(VOICE_INPUT_SWITCH_TAG),
                )
                if (settings.voiceInputEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = stringResource(R.string.settings_voice_russian_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    VoiceModelRow(
                        state = voiceModel,
                        onDownload = viewModel::downloadVoiceModel,
                        onRemove = viewModel::removeVoiceModel,
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_navigation)) {
                val inBar = bottomBarDestinations(settings.bottomBarRoutes).toSet()
                TodiDestination.entries.forEach { destination ->
                    CheckboxRow(
                        title = stringResource(destination.labelRes),
                        icon = destination.icon,
                        checked = destination in inBar,
                        onCheckedChange = { on -> viewModel.setBottomBarRoute(destination.route, on) },
                        modifier = Modifier.testTag(bottomBarToggleTag(destination)),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_navigation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                if (inBar.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_navigation_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_carry_over)) {
                SwitchRow(
                    title = stringResource(R.string.settings_carry_over),
                    subtitle = stringResource(R.string.settings_carry_over_desc),
                    checked = settings.autoCarryOver,
                    onCheckedChange = viewModel::setAutoCarryOver,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_completed)) {
                SwitchRow(
                    title = stringResource(R.string.settings_show_completed),
                    subtitle = stringResource(R.string.settings_show_completed_desc),
                    checked = settings.showCompletedInDay,
                    onCheckedChange = viewModel::setShowCompletedInDay,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_retention)) {
                RETENTION_MONTHS.forEachIndexed { index, months ->
                    SelectableRow(
                        label =
                            if (months == 0) {
                                stringResource(R.string.retention_forever)
                            } else {
                                pluralStringResource(R.plurals.retention_months, months, months)
                            },
                        selected = settings.completedRetentionMonths == months,
                        onClick = { viewModel.setCompletedRetentionMonths(months) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                TextButton(
                    onClick = { showClearArchiveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.clear_completed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_calendar)) {
                SwitchRow(
                    title = stringResource(R.string.settings_calendar),
                    subtitle = stringResource(R.string.settings_calendar_desc),
                    checked = settings.calendarSyncEnabled && calendarAllowed,
                    onCheckedChange = { enable ->
                        if (enable) {
                            if (!calendarAllowed) {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR,
                                    ),
                                )
                            }
                            viewModel.setCalendarSyncEnabled(true)
                        } else {
                            viewModel.setCalendarSyncEnabled(false)
                        }
                    },
                )
                if (!calendarAllowed && settings.calendarSyncEnabled) {
                    Text(
                        text = stringResource(R.string.calendar_permission_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                lastSyncError?.let { error ->
                    Text(
                        text =
                            if (error == CalendarSyncManager.NO_CALENDARS_CODE) {
                                stringResource(R.string.calendar_no_calendars)
                            } else {
                                error
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_notifications)) {
                SwitchRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = settings.notificationsEnabled && notificationsAllowed,
                    onCheckedChange = { enable ->
                        if (enable) {
                            if (!notificationsAllowed) requestPermission()
                            viewModel.setNotificationsEnabled(true)
                        } else {
                            viewModel.setNotificationsEnabled(false)
                        }
                    },
                )
                if (!notificationsAllowed && settings.notificationsEnabled) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = stringResource(R.string.notifications_needs_permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                val intent =
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                context.startActivity(intent)
                            },
                        ) {
                            Text(stringResource(R.string.enable_notifications))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = stringResource(R.string.settings_lock_screen),
                    subtitle = stringResource(R.string.settings_lock_screen_desc),
                    checked = settings.showOnLockScreen,
                    onCheckedChange = viewModel::setShowOnLockScreen,
                    enabled = settings.notificationsEnabled,
                )

                if (settings.showOnLockScreen) {
                    Text(
                        text = stringResource(R.string.settings_lock_screen_scope),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                    LockScreenScope.entries.forEach { scope ->
                        SelectableRow(
                            label = stringResource(lockScreenScopeLabel(scope)),
                            selected = settings.lockScreenScope == scope,
                            onClick = { viewModel.setLockScreenScope(scope) },
                            modifier = Modifier.testTag(lockScreenScopeTag(scope)),
                        )
                    }
                    if (settings.lockScreenScope == LockScreenScope.TITLE_ONLY) {
                        Text(
                            text = stringResource(R.string.settings_lock_screen_title_only_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_scope)) {
                NotificationScope.entries.forEach { scope ->
                    SelectableRow(
                        label = stringResource(notificationScopeLabel(scope)),
                        selected = settings.notificationScope == scope,
                        onClick = { viewModel.setNotificationScope(scope) },
                        modifier = Modifier.testTag(notificationScopeTag(scope)),
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_notification_colors)) {
                NotificationSection.entries.forEach { section ->
                    NotificationColorRow(
                        section = section,
                        label = stringResource(notificationSectionLabel(section)),
                        current = settings.colorOf(section),
                        onClick = { colorSheet = section },
                    )
                }
            }

            NotificationPreviewSection(
                preview = preview,
                colors = NotificationPresentation.from(settings).colors,
                reminderEnabled = settings.globalReminderEnabled,
            )

            SettingsSection(title = stringResource(R.string.settings_global)) {
                SwitchRow(
                    title = stringResource(R.string.settings_global_reminder),
                    subtitle = stringResource(R.string.settings_global_reminder_desc),
                    checked = settings.globalReminderEnabled && notificationsAllowed,
                    onCheckedChange = { enable ->
                        if (enable && !notificationsAllowed) requestPermission()
                        viewModel.setGlobalReminderEnabled(enable)
                    },
                )
                if (settings.globalReminderEnabled) {
                    if (!notificationsAllowed) {
                        Text(
                            text = stringResource(R.string.notifications_needs_permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TimeRow(
                        label = stringResource(R.string.settings_global_reminder_start),
                        time = settings.globalReminderStart,
                        onClick = { showStartPicker = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TimeRow(
                        label = stringResource(R.string.settings_global_reminder_quiet),
                        time = settings.globalReminderQuietAfter,
                        onClick = { showQuietPicker = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.settings_global_reminder_interval),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                    REMINDER_INTERVALS.forEach { minutes ->
                        SelectableRow(
                            label = reminderIntervalLabel(minutes),
                            selected = settings.globalReminderIntervalMinutes == minutes,
                            onClick = { viewModel.setGlobalReminderInterval(minutes) },
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_backup)) {
                SwitchRow(
                    title = stringResource(R.string.settings_backup_enable),
                    subtitle = stringResource(R.string.settings_backup_desc),
                    checked = settings.backupEnabled,
                    onCheckedChange = viewModel::setBackupEnabled,
                    modifier = Modifier.testTag(BACKUP_SWITCH_TAG),
                )
                if (settings.backupEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val account = backupAccount
                    if (account == null) {
                        Text(
                            text = stringResource(R.string.settings_backup_not_signed_in),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_backup_signed_in, account.email),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = viewModel::backupNow,
                                enabled = !backup.busy,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .testTag(BACKUP_NOW_TAG),
                            ) {
                                Text(stringResource(R.string.settings_backup_now))
                            }
                            TextButton(
                                onClick = viewModel::refreshBackup,
                                enabled = !backup.busy,
                            ) {
                                Text(stringResource(R.string.settings_backup_refresh))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = stringResource(R.string.settings_backup_copies),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                        if (backup.copies.isEmpty()) {
                            if (backup.copiesLoaded) {
                                Text(
                                    text = stringResource(R.string.settings_backup_copies_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        } else {
                            backup.copies.forEach { copy ->
                                BackupCopyRow(copy = copy, onClick = { backupRestoreId = copy.id })
                            }
                        }
                        TextButton(
                            onClick = viewModel::signOutBackup,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_backup_sign_out))
                        }
                    }
                    backup.error?.let { failure ->
                        Text(
                            text = backupFailureText(failure),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Text(
                text = "${stringResource(R.string.app_name)} · v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 24.dp),
            )
        }
    }

    colorSheet?.let { section ->
        NotificationColorSheet(
            title = stringResource(notificationSectionLabel(section)),
            current = settings.colorOf(section),
            onPick = { argb ->
                viewModel.setNotificationColor(section, argb)
                colorSheet = null
            },
            onDismiss = { colorSheet = null },
        )
    }

    if (showStartPicker) {
        TimePickerDialog(
            title = stringResource(R.string.settings_global_reminder_start),
            initial = settings.globalReminderStart,
            onConfirm = {
                viewModel.setGlobalReminderStart(it.hour * 60 + it.minute)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }

    if (showQuietPicker) {
        TimePickerDialog(
            title = stringResource(R.string.settings_global_reminder_quiet),
            initial = settings.globalReminderQuietAfter,
            onConfirm = {
                viewModel.setGlobalReminderQuietAfter(it.hour * 60 + it.minute)
                showQuietPicker = false
            },
            onDismiss = { showQuietPicker = false },
        )
    }

    if (showClearArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showClearArchiveDialog = false },
            title = { Text(stringResource(R.string.clear_completed_title)) },
            text = { Text(stringResource(R.string.clear_completed_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearArchiveDialog = false
                        viewModel.clearCompleted()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.clear_completed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearArchiveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    backupRestoreId?.let { id ->
        backup.copies.firstOrNull { it.id == id }?.let { copy ->
            AlertDialog(
                onDismissRequest = { backupRestoreId = null },
                title = { Text(stringResource(R.string.backup_restore_title)) },
                text = {
                    Text(stringResource(R.string.backup_restore_text, backupInstantText(copy.savedAt)))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            backupRestoreId = null
                            viewModel.restoreBackup(copy.id)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.backup_restore_confirm),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { backupRestoreId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

private val RETENTION_MONTHS = listOf(0, 1, 3, 6, 12)

private val REMINDER_INTERVALS = listOf(30, 60, 120, 240)

@Composable
private fun reminderIntervalLabel(minutes: Int): String =
    if (minutes < 60) {
        pluralStringResource(R.plurals.reminder_minutes, minutes, minutes)
    } else {
        val hours = minutes / 60
        pluralStringResource(R.plurals.reminder_hours, hours, hours)
    }

@Composable
private fun TimeRow(
    label: String,
    time: LocalTime,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = DateFormatters.time(time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

fun bottomBarToggleTag(destination: TodiDestination): String = "settings_bottom_${destination.route}"

fun lockScreenScopeTag(scope: LockScreenScope): String = "settings_lock_scope_${scope.name.lowercase()}"

fun notificationScopeTag(scope: NotificationScope): String = "settings_notification_scope_${scope.name.lowercase()}"

const val VOICE_INPUT_SWITCH_TAG = "settings_voice_input"

const val VOICE_MODEL_DOWNLOAD_TAG = "settings_voice_model_download"
const val VOICE_MODEL_REMOVE_TAG = "settings_voice_model_remove"

const val BACKUP_SWITCH_TAG = "settings_backup_switch"
const val BACKUP_NOW_TAG = "settings_backup_now"

@Composable
private fun VoiceModelRow(
    state: VoskModelState,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_voice_model),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (state) {
            is VoskModelState.Ready -> {
                Text(
                    text = stringResource(R.string.settings_voice_model_ready, modelMegabytes(state.bytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            is VoskModelState.Downloading -> {
                Text(
                    text =
                        stringResource(
                            R.string.settings_voice_model_downloading,
                            (state.progress * 100).toInt(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
            }

            is VoskModelState.Failed -> {
                Text(
                    text = stringResource(voiceModelFailureText(state.reason)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            is VoskModelState.Absent -> {
                Text(
                    text = stringResource(R.string.settings_voice_model_absent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        when (state) {
            is VoskModelState.Downloading -> {
                Unit
            }

            is VoskModelState.Ready -> {
                TextButton(
                    onClick = onRemove,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(VOICE_MODEL_REMOVE_TAG),
                ) {
                    Text(stringResource(R.string.settings_voice_model_remove))
                }
            }

            else -> {
                TextButton(
                    onClick = onDownload,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(VOICE_MODEL_DOWNLOAD_TAG),
                ) {
                    val partial = (state as? VoskModelState.Failed)?.partial ?: 0L
                    Text(
                        if (partial > 0) {
                            stringResource(R.string.settings_voice_model_resume, modelMegabytes(partial))
                        } else {
                            stringResource(R.string.settings_voice_model_download)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun AppSettings.colorOf(section: NotificationSection): Int? =
    when (section) {
        NotificationSection.TODAY -> notificationColorToday
        NotificationSection.OVERDUE -> notificationColorOverdue
        NotificationSection.DATE_LESS -> notificationColorDateLess
    }

private fun notificationSectionLabel(section: NotificationSection): Int =
    when (section) {
        NotificationSection.TODAY -> R.string.notification_color_today
        NotificationSection.OVERDUE -> R.string.notification_overdue_header
        NotificationSection.DATE_LESS -> R.string.notification_global_header
    }

private fun notificationScopeLabel(scope: NotificationScope): Int =
    when (scope) {
        NotificationScope.TODAY_ONLY -> R.string.settings_scope_today_only
        NotificationScope.WITH_OVERDUE -> R.string.settings_scope_with_overdue
        NotificationScope.WITH_OVERDUE_AND_GLOBAL -> R.string.settings_scope_with_overdue_and_global
    }

private fun lockScreenScopeLabel(scope: LockScreenScope): Int =
    when (scope) {
        LockScreenScope.FULL -> R.string.lock_screen_scope_full
        LockScreenScope.TITLE_ONLY -> R.string.lock_screen_scope_title_only
    }

private fun themeModeLabel(mode: ThemeMode): Int =
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

private fun languageModeLabel(mode: LanguageMode): Int =
    when (mode) {
        LanguageMode.SYSTEM -> R.string.language_system
        LanguageMode.RU -> R.string.language_russian
        LanguageMode.EN -> R.string.language_english
    }

@Composable
private fun BackupCopyRow(
    copy: RemoteCopy,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = backupInstantText(copy.savedAt),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                pluralStringResource(R.plurals.settings_backup_tasks, copy.taskCount, copy.taskCount) +
                    " · " + backupSizeText(copy.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
