package com.todi.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.todi.app.data.local.AppDatabase
import com.todi.app.data.local.TaskEntity
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.components.DAY_LIST_TAG
import com.todi.app.presentation.components.taskCardTag
import com.todi.app.presentation.navigation.BOTTOM_BAR_TAG
import com.todi.app.presentation.navigation.DEFAULT_BOTTOM_BAR_ROUTES
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.bottomBarTag
import com.todi.app.presentation.navigation.drawerTag
import com.todi.app.presentation.voice.VOICE_FAB_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

@HiltAndroidTest
class AddButtonDockTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private fun str(id: Int): String = composeRule.activity.resources.getString(id)

    @Before
    fun setUp() {
        hiltRule.inject()

        runBlocking {
            database.clearAllTables()
            settingsRepository.setBottomBarRoutes(DEFAULT_BOTTOM_BAR_ROUTES)
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

    private fun addTask(title: String) {
        composeRule.onNodeWithTag(VOICE_FAB_TAG).performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(title)
        composeRule.onNodeWithText(str(R.string.add)).performClick()
        composeRule.waitForIdle()
    }

    private fun theBar(): DpRect = composeRule.onNodeWithTag(BOTTOM_BAR_TAG).getBoundsInRoot()

    private fun theButton(): DpRect = composeRule.onNodeWithTag(VOICE_FAB_TAG).getBoundsInRoot()

    private fun aTabsBottom(): Dp =
        TodiDestination.entries
            .filter { composeRule.onAllNodesWithTag(bottomBarTag(it)).fetchSemanticsNodes().isNotEmpty() }
            .maxOfOrNull { composeRule.onAllNodesWithTag(bottomBarTag(it))[0].getBoundsInRoot().bottom }
            ?: 0.dp

    @Test
    fun theButton_crossesTheBarsEdge() {
        val bar = theBar()
        val button = theButton()

        assertTrue(
            "the button is not attached to the bar: its bottom is at ${button.bottom}, " +
                "the bar starts at ${bar.top}",
            button.bottom > bar.top,
        )
        assertTrue(
            "the button is sunk into the bar rather than bulging out of it: " +
                "top ${button.top}, bar ${bar.top}",
            button.top < bar.top,
        )
        assertTrue(
            "the whole button has to be on the screen, top is ${button.top}",
            button.top > 0.dp,
        )

        val offCentre = ((button.left + button.right) - (bar.left + bar.right)) / 2
        assertTrue(
            "the button is not in the middle of the bar: it is $offCentre off",
            abs(offCentre.value) < 1f,
        )

        val sunk = button.bottom - bar.top
        assertTrue(
            "the button is swallowed by the bar rather than let into it: " +
                "it goes $sunk into a bar ${bar.bottom - bar.top} tall, " +
                "over a tab ending at ${aTabsBottom()}",
            sunk < (bar.bottom - bar.top) / 3,
        )
    }

    private fun scrollToEnd() {
        val list = composeRule.onNodeWithTag(DAY_LIST_TAG)
        repeat(12) {
            list.performTouchInput {
                swipe(
                    start = Offset(width * 0.25f, height - 4f),
                    end = Offset(width * 0.25f, height * 0.15f),
                )
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun theLastTask_isClearOfTheButton() {
        val titles = (1..15).map { "Задача $it" }
        runBlocking {
            val now = System.currentTimeMillis()
            titles.forEachIndexed { index, title ->
                database.taskDao().insert(
                    TaskEntity(
                        title = title,
                        dateEpochDay = LocalDate.now().toEpochDay(),
                        timeMinuteOfDay = null,
                        note = null,
                        createdAtEpochMillis = now + index,
                        updatedAtEpochMillis = now + index,
                        syncId = "sync-$title",
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        val lastId = runBlocking { database.taskDao().observeAllTasks().first() }.maxOf { it.id }

        scrollToEnd()
        val lastCard = composeRule.onNodeWithTag(taskCardTag(lastId))

        val card = lastCard.getBoundsInRoot()
        val button = theButton()
        assertTrue(
            "the button sits on the last task at the end of the scroll: " +
                "the card ends at ${card.bottom}, the button starts at ${button.top}",
            card.bottom < button.top,
        )
    }

    @Test
    fun theButton_comesBackWithTheScreenThatOwnsIt() {
        composeRule.onNodeWithTag(VOICE_FAB_TAG).assertIsDisplayed()

        navigateTo(TodiDestination.COMPLETED)
        composeRule.onAllNodesWithTag(VOICE_FAB_TAG).assertCountEquals(0)

        navigateTo(TodiDestination.TODAY)
        composeRule.onNodeWithTag(VOICE_FAB_TAG).assertIsDisplayed()

        addTask("Хлеб")
        composeRule.onNodeWithText("Хлеб").assertIsDisplayed()
    }

    private fun navigateTo(destination: TodiDestination) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(drawerTag(destination)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }
}
