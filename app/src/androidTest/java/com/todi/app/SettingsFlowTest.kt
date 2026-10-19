package com.todi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.todi.app.data.local.AppDatabase
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.navigation.DEFAULT_BOTTOM_BAR_ROUTES
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.bottomBarTag
import com.todi.app.presentation.navigation.drawerTag
import com.todi.app.presentation.settings.bottomBarToggleTag
import com.todi.app.presentation.util.DateFormatters
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import javax.inject.Inject

@HiltAndroidTest
class SettingsFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private fun str(id: Int): String = composeRule.activity.getString(id)

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

    private fun navigateTo(destination: TodiDestination) {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()
        composeRule.onNodeWithTag(drawerTag(destination)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun untick(destination: TodiDestination) {
        composeRule.onNodeWithTag(bottomBarToggleTag(destination)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun assertBarDoesNotOffer(destination: TodiDestination) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(bottomBarTag(destination)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aSectionUnticked_isGoneFromTheBar_andStillOpensFromTheMenu() {
        navigateTo(TodiDestination.SETTINGS)
        untick(TodiDestination.CALENDAR)

        assertBarDoesNotOffer(TodiDestination.CALENDAR)

        navigateTo(TodiDestination.CALENDAR)
        val locale = composeRule.activity.resources.configuration.locales[0]
        composeRule
            .onNodeWithText(DateFormatters.monthYear(YearMonth.now(), locale))
            .assertIsDisplayed()
    }

    @Test
    fun emptyingTheBar_leavesNoBar_andTheMenuReachesEverySection() {
        navigateTo(TodiDestination.SETTINGS)
        DEFAULT_BOTTOM_BAR_ROUTES.forEach { route ->
            untick(TodiDestination.entries.first { it.route == route })
        }

        DEFAULT_BOTTOM_BAR_ROUTES.forEach { route ->
            assertBarDoesNotOffer(TodiDestination.entries.first { it.route == route })
        }

        composeRule
            .onNodeWithText(str(R.string.settings_navigation_empty))
            .performScrollTo()
            .assertIsDisplayed()

        navigateTo(TodiDestination.GLOBAL)
        composeRule.onNodeWithText(str(R.string.no_global_tasks)).assertIsDisplayed()
    }

    @Test
    fun aSectionOutsideTheDefaultBar_opensFromTheMenu() {
        assertBarDoesNotOffer(TodiDestination.SETTINGS)

        navigateTo(TodiDestination.SETTINGS)
        composeRule.onNodeWithText(str(R.string.settings_theme)).assertIsDisplayed()
    }
}
