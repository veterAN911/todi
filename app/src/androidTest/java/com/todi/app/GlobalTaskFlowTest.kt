package com.todi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import com.todi.app.presentation.editor.EDITOR_SEGMENT_WITHOUT_DATE
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.drawerTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class GlobalTaskFlowTest {
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

    private fun navigateTo(destination: TodiDestination) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(drawerTag(destination)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun assertNotShown(title: String) {
        check(composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()) {
            "«$title» не должно быть на экране"
        }
    }

    private fun addGlobalTask(title: String) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_add_task)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EDITOR_SEGMENT_WITHOUT_DATE).assertIsSelected()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(title)
        composeRule.onNodeWithText(str(R.string.add)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun globalTask_staysOutOfToday() {
        navigateTo(TodiDestination.GLOBAL)
        addGlobalTask("Позвонить в сервис")

        navigateTo(TodiDestination.TODAY)

        assertNotShown("Позвонить в сервис")
    }

    @Test
    fun globalTask_doesNotAppearInTheCalendar() {
        navigateTo(TodiDestination.GLOBAL)
        addGlobalTask("Оплатить страховку")

        navigateTo(TodiDestination.CALENDAR)

        assertNotShown("Оплатить страховку")
    }

    @Test
    fun completingGlobalTask_removesItFromTheSection() {
        navigateTo(TodiDestination.GLOBAL)
        addGlobalTask("Записаться к врачу")

        composeRule.onAllNodes(isToggleable()).onFirst().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.no_global_tasks)).assertIsDisplayed()
        assertNotShown("Записаться к врачу")
    }

    @Test
    fun globalTask_stillWorksAfterComingBackFromAnotherTab() {
        navigateTo(TodiDestination.GLOBAL)
        addGlobalTask("Разобрать документы")

        navigateTo(TodiDestination.TOMORROW)
        navigateTo(TodiDestination.GLOBAL)

        composeRule.onNodeWithText("Разобрать документы").assertIsDisplayed()
    }
}
