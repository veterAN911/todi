package com.todi.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.todi.app.data.local.AppDatabase
import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.model.LockScreenScope
import com.todi.app.domain.model.NotificationScope
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.notification.NotificationSection
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.navigation.DEFAULT_BOTTOM_BAR_ROUTES
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.drawerTag
import com.todi.app.presentation.settings.COLOR_SYSTEM_TAG
import com.todi.app.presentation.settings.colorRowTag
import com.todi.app.presentation.settings.colorSwatchTag
import com.todi.app.presentation.settings.lockScreenScopeTag
import com.todi.app.presentation.settings.notificationScopeTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidTest
class NotificationSettingsFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var repository: TaskRepository

    private val swatch = 0xFFE53935.toInt()

    private fun str(id: Int): String = composeRule.activity.getString(id)

    @Before
    fun setUp() {
        hiltRule.inject()

        runBlocking {
            database.clearAllTables()
            settingsRepository.setBottomBarRoutes(DEFAULT_BOTTOM_BAR_ROUTES)
            settingsRepository.setNotificationColorToday(null)
            settingsRepository.setNotificationColorOverdue(null)
            settingsRepository.setNotificationColorDateLess(null)
            settingsRepository.setLockScreenScope(LockScreenScope.FULL)
            settingsRepository.setNotificationScope(NotificationScope.TODAY_ONLY)

            settingsRepository.setAutoCarryOver(false)
        }
        composeRule.waitForIdle()
        dismissOnboardingIfShown()
    }

    private fun dismissOnboardingIfShown() {
        val laterText = str(R.string.later)
        val todayText = str(R.string.nav_today)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(laterText).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(todayText).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText(laterText).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(laterText).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(drawerTag(TodiDestination.SETTINGS)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun createTask(
        title: String,
        date: LocalDate,
        isGlobal: Boolean = false,
    ) = runBlocking {
        repository.createTask(title = title, date = date, time = null, note = null, isGlobal = isGlobal)
        Unit
    }

    private fun openColourSheet(section: NotificationSection) {
        composeRule.onNodeWithTag(colorRowTag(section)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun storedSettings() = runBlocking { settingsRepository.settings.first() }

    private fun awaitScope(expected: NotificationScope) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            storedSettings().notificationScope == expected
        }
    }

    private fun awaitLockScope(expected: LockScreenScope) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            storedSettings().lockScreenScope == expected
        }
    }

    private fun awaitColor(
        section: NotificationSection,
        expected: Int?,
    ) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            storedSettings().colorOf(section) == expected
        }
    }

    private fun awaitShown(
        text: String,
        times: Int,
    ) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size == times
        }
        composeRule.onAllNodesWithText(text).assertCountEquals(times)
    }

    @Test
    fun aChosenColour_isStoredForItsOwnSectionOnly() {
        openSettings()
        openColourSheet(NotificationSection.OVERDUE)

        composeRule.onNodeWithTag(colorSwatchTag(swatch)).performClick()
        awaitColor(NotificationSection.OVERDUE, swatch)

        val settings = storedSettings()
        assertEquals(swatch, settings.notificationColorOverdue)
        assertNull(settings.notificationColorToday)
        assertNull(settings.notificationColorDateLess)
    }

    @Test
    fun theSystemRow_clearsTheChoice() {
        runBlocking { settingsRepository.setNotificationColorToday(swatch) }
        openSettings()
        openColourSheet(NotificationSection.TODAY)

        composeRule.onNodeWithTag(COLOR_SYSTEM_TAG).performClick()
        awaitColor(NotificationSection.TODAY, null)
    }

    @Test
    fun thePreview_showsTodaysAndOverdueTasksInBothPlaces() {
        createTask("Сегодняшнее", LocalDate.now())
        createTask("Вчерашнее", LocalDate.now().minusDays(1))
        runBlocking { settingsRepository.setNotificationScope(NotificationScope.WITH_OVERDUE) }
        openSettings()

        awaitShown("☐ Сегодняшнее", 2)
        awaitShown("☐ Вчерашнее", 2)
        awaitShown(composeRule.activity.resources.getString(R.string.notification_overdue_header) + ":", 2)
    }

    @Test
    fun theWidestScope_addsDateLessTasksToTheDailyNotification() {
        createTask("Позвонить в сервис", LocalDate.now(), isGlobal = true)
        openSettings()

        composeRule
            .onNodeWithTag(notificationScopeTag(NotificationScope.WITH_OVERDUE_AND_GLOBAL))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        awaitScope(NotificationScope.WITH_OVERDUE_AND_GLOBAL)
        awaitShown(composeRule.activity.resources.getString(R.string.notification_global_header) + ":", 2)
        awaitShown("☐ Позвонить в сервис", 2)
    }

    @Test
    fun theTitleOnlyScope_takesTheTasksOutOfTheNotificationAltogether() {
        createTask("Сегодняшнее", LocalDate.now())
        openSettings()
        awaitShown("☐ Сегодняшнее", 2)

        composeRule
            .onNodeWithTag(lockScreenScopeTag(LockScreenScope.TITLE_ONLY))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        awaitLockScope(LockScreenScope.TITLE_ONLY)
        awaitShown("☐ Сегодняшнее", 0)
    }

    @Test
    fun noActiveTasks_saysSo_insteadOfAnEmptyCard() {
        openSettings()

        composeRule.onAllNodesWithText(str(R.string.notification_preview_empty)).onFirst().assertExists()
    }

    private fun AppSettings.colorOf(section: NotificationSection): Int? =
        when (section) {
            NotificationSection.TODAY -> notificationColorToday
            NotificationSection.OVERDUE -> notificationColorOverdue
            NotificationSection.DATE_LESS -> notificationColorDateLess
        }
}
