package com.todi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.todi.app.data.local.AppDatabase
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.drawerTag
import com.todi.app.presentation.util.DateFormatters
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltAndroidTest
class TaskFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    private fun str(id: Int): String = composeRule.activity.getString(id)

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { database.clearAllTables() }
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

    private fun addTaskViaSheet(title: String) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_add_task)).performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(title)
        composeRule.onNodeWithText(str(R.string.add)).performClick()
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    private fun navigateTo(destination: TodiDestination) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(drawerTag(destination)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun addTask_taskAppearsInTodayList() {
        addTaskViaSheet("Позвонить клиенту")
    }

    @Test
    fun toggleCheckbox_taskBecomesCompleted() {
        addTaskViaSheet("Купить продукты")

        composeRule.onAllNodes(isToggleable()).onFirst().performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodes(isToggleable()).onFirst().assertIsOn()
    }

    @Test
    fun moveToTomorrow_taskShownOnTomorrowScreen() {
        addTaskViaSheet("Переезд на дачу")

        composeRule.onNodeWithContentDescription(str(R.string.cd_more_options)).performClick()
        composeRule.onNodeWithText(str(R.string.move_to_tomorrow)).performClick()

        composeRule.onNodeWithText(str(R.string.moved_to_tomorrow)).assertIsDisplayed()

        navigateTo(TodiDestination.TOMORROW)

        composeRule.onNodeWithText("Переезд на дачу").assertIsDisplayed()
    }

    @Test
    fun navigateToCalendar_monthAndSelectedDayShown() {
        navigateTo(TodiDestination.CALENDAR)

        val locale = composeRule.activity.resources.configuration.locales[0]
        val currentMonth = YearMonth.now()
        composeRule.onNodeWithText(DateFormatters.monthYear(currentMonth, locale)).assertIsDisplayed()

        composeRule.onAllNodesWithText("1").onFirst().performClick()

        val firstOfMonth = LocalDate.of(currentMonth.year, currentMonth.monthValue, 1)
        composeRule.onNodeWithText(DateFormatters.fullDate(firstOfMonth, locale)).assertIsDisplayed()
    }
}
