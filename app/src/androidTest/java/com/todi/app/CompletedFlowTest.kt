package com.todi.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.todi.app.data.local.AppDatabase
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.domain.repository.TaskRepository
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.completed.RESTORE_DAY_CANCEL_TAG
import com.todi.app.presentation.completed.RESTORE_DAY_OTHER_TAG
import com.todi.app.presentation.completed.RESTORE_DAY_TODAY_TAG
import com.todi.app.presentation.components.taskCardTag
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.drawerTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import javax.inject.Inject

@HiltAndroidTest
class CompletedFlowTest {
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

    private fun str(id: Int): String = composeRule.activity.getString(id)

    private fun completedHeader(count: Int): String = composeRule.activity.resources.getString(R.string.completed_section, count)

    @Before
    fun setUp() {
        hiltRule.inject()

        runBlocking {
            database.clearAllTables()
            settingsRepository.setShowCompletedInDay(true)
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

    private fun navigateTo(destination: TodiDestination) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(drawerTag(destination)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun assertNotShown(text: String) {
        check(composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            "«$text» не должно быть на экране"
        }
    }

    private fun addTask(title: String) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_add_task)).performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(title)
        composeRule.onNodeWithText(str(R.string.add)).performClick()
        composeRule.waitForIdle()
    }

    private fun toggleFirstTask() {
        composeRule.onAllNodes(isToggleable()).onFirst().performClick()
        composeRule.waitForIdle()
    }

    private fun taskIdOf(title: String): Long =
        runBlocking {
            repository
                .observeAllTasks()
                .first()
                .single { it.title == title }
                .id
        }

    @Test
    fun completedTask_staysUnderTheDoneDividerWithItsCount() {
        addTask("Купить хлеб")

        toggleFirstTask()

        composeRule.onNodeWithText(completedHeader(1)).assertIsDisplayed()
        composeRule.onNodeWithText("Купить хлеб").assertIsDisplayed()
    }

    @Test
    fun completedTask_appearsInTheArchive() {
        addTask("Позвонить в банк")
        toggleFirstTask()

        navigateTo(TodiDestination.COMPLETED)

        composeRule.onNodeWithText("Позвонить в банк").assertIsDisplayed()
        assertNotShown(str(R.string.no_completed_tasks))
    }

    @Test
    fun archive_groupsByTheCompletionDay() {
        addTask("Старое дело")
        toggleFirstTask()
        runBlocking {
            val task = repository.observeAllTasks().first().single()
            val yesterday =
                task.completedAt!!
                    .atZone(ZoneId.systemDefault())
                    .minusDays(1)
                    .toInstant()
            repository.updateTask(task.copy(completedAt = yesterday))
        }
        composeRule.waitForIdle()

        navigateTo(TodiDestination.COMPLETED)

        composeRule.onNodeWithText(str(R.string.completed_yesterday)).assertIsDisplayed()
    }

    @Test
    fun settingOff_hidesTheDoneSectionButKeepsTheArchive() {
        addTask("Разобрать почту")
        toggleFirstTask()
        runBlocking { settingsRepository.setShowCompletedInDay(false) }
        composeRule.waitForIdle()

        assertNotShown(completedHeader(1))
        assertNotShown("Разобрать почту")

        navigateTo(TodiDestination.COMPLETED)
        composeRule.onNodeWithText("Разобрать почту").assertIsDisplayed()
    }

    @Test
    fun archive_returnsATaskToTheDay_itWasTold() {
        addTask("Записаться к врачу")
        toggleFirstTask()
        navigateTo(TodiDestination.COMPLETED)

        toggleFirstTask()

        composeRule.onNodeWithText(str(R.string.restore_day_hint)).assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORE_DAY_TODAY_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.no_completed_tasks)).assertIsDisplayed()
        navigateTo(TodiDestination.TODAY)
        assertNotShown(completedHeader(1))
        composeRule.onNodeWithText("Записаться к врачу").assertIsDisplayed()
    }

    @Test
    fun archive_restoreToAnotherDay_goesThroughTheDatePicker() {
        addTask("Сдать книгу")
        toggleFirstTask()
        navigateTo(TodiDestination.COMPLETED)

        toggleFirstTask()
        composeRule.onNodeWithTag(RESTORE_DAY_OTHER_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.ok)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.no_completed_tasks)).assertIsDisplayed()
        navigateTo(TodiDestination.TODAY)
        composeRule.onNodeWithText("Сдать книгу").assertIsDisplayed()
    }

    @Test
    fun archive_swipeRight_asksWhichDay() {
        addTask("Полить цветы")
        toggleFirstTask()
        navigateTo(TodiDestination.COMPLETED)

        val card = composeRule.onNodeWithTag(taskCardTag(taskIdOf("Полить цветы")))
        card.performTouchInput {
            swipe(start = Offset(width * 0.15f, height / 2f), end = Offset(width * 0.95f, height / 2f))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.restore_day_hint)).assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORE_DAY_TODAY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORE_DAY_OTHER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORE_DAY_CANCEL_TAG).assertIsDisplayed()
    }

    @Test
    fun archive_togglesBetweenTheListAndTheCalendar() {
        addTask("Вынести мусор")
        toggleFirstTask()
        navigateTo(TodiDestination.COMPLETED)

        composeRule.onNodeWithContentDescription(str(R.string.cd_archive_as_calendar)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(str(R.string.cd_archive_as_list)).assertIsDisplayed()
        composeRule.onNodeWithText("Вынести мусор").assertIsDisplayed()
    }

    @Test
    fun archive_signsADateLessTask() {
        navigateTo(TodiDestination.GLOBAL)
        composeRule.onNodeWithContentDescription(str(R.string.cd_add_task)).performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Оплатить страховку")
        composeRule.onNodeWithText(str(R.string.add)).performClick()
        composeRule.waitForIdle()
        toggleFirstTask()

        navigateTo(TodiDestination.COMPLETED)

        composeRule.onNodeWithText("Оплатить страховку").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.editor_without_date)).assertIsDisplayed()
    }
}
