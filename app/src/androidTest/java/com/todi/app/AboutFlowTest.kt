package com.todi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.todi.app.data.local.AppDatabase
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.navigation.DRAWER_ABOUT_TAG
import com.todi.app.presentation.navigation.DRAWER_SEARCH_TAG
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
class AboutFlowTest {
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

    private fun openDrawer() {
        composeRule.onNodeWithContentDescription(str(R.string.cd_open_menu)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun drawer_showsSearchAboveTheSections() {
        openDrawer()

        val search = composeRule.onNodeWithTag(DRAWER_SEARCH_TAG).getUnclippedBoundsInRoot()
        val today = composeRule.onNodeWithTag(drawerTag(TodiDestination.TODAY)).getUnclippedBoundsInRoot()

        check(search.top < today.top) {
            "«Поиск» должен стоять выше разделов: поиск на ${search.top}, «Сегодня» на ${today.top}"
        }
    }

    @Test
    fun about_opensFromTheDrawerAndShowsTheVersion() {
        openDrawer()
        composeRule.onNodeWithTag(DRAWER_ABOUT_TAG).performClick()
        composeRule.waitForIdle()

        val version =
            composeRule.activity.resources.getString(
                R.string.about_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            )
        composeRule.onNodeWithText(version).assertIsDisplayed()
    }

    @Test
    fun about_unfoldsABlockInPlace() {
        openDrawer()
        composeRule.onNodeWithTag(DRAWER_ABOUT_TAG).performClick()
        composeRule.waitForIdle()

        val heading = str(R.string.about_section_releases)
        composeRule.onNodeWithText(heading).assertIsDisplayed()
        check(composeRule.onAllNodesWithText(str(R.string.about_releases_text)).fetchSemanticsNodes().isEmpty()) {
            "Текст раздела не должен быть виден, пока раздел не раскрыт"
        }

        composeRule.onNodeWithText(heading).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.about_releases_text)).assertIsDisplayed()
    }

    @Test
    fun about_returnsToWhereItWasOpened() {
        openDrawer()
        composeRule.onNodeWithTag(DRAWER_ABOUT_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        composeRule.waitForIdle()

        check(composeRule.onAllNodesWithText(str(R.string.about_tagline)).fetchSemanticsNodes().isEmpty()) {
            "Страница «О приложении» должна закрыться по стрелке назад"
        }
    }
}
