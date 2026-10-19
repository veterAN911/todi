package com.todi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.todi.app.data.local.AppDatabase
import com.todi.app.domain.repository.BackupAccountRepository
import com.todi.app.domain.repository.SettingsRepository
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.navigation.DRAWER_SEARCH_TAG
import com.todi.app.presentation.navigation.DRAWER_SIGN_IN_TAG
import com.todi.app.presentation.navigation.TodiDestination
import com.todi.app.presentation.navigation.drawerTag
import com.todi.app.presentation.settings.BACKUP_SWITCH_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.math.abs

@HiltAndroidTest
class BackupAccountFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var backupAccounts: BackupAccountRepository

    private fun str(id: Int): String = composeRule.activity.getString(id)

    @Before
    fun setUp() {
        hiltRule.inject()

        runBlocking {
            database.clearAllTables()
            settingsRepository.setBackupEnabled(false)
            backupAccounts.signOut()
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
    fun signIn_sitsInTheHeaderOppositeTheTitle() {
        openDrawer()

        val signIn = composeRule.onNodeWithTag(DRAWER_SIGN_IN_TAG).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText(str(R.string.app_name)).getUnclippedBoundsInRoot()
        val search = composeRule.onNodeWithTag(DRAWER_SEARCH_TAG).getUnclippedBoundsInRoot()

        check(signIn.top < search.top) {
            "Вход должен стоять в шапке, выше поиска: вход на ${signIn.top}, поиск на ${search.top}"
        }
        check(signIn.left > title.right) {
            "Вход должен стоять справа от названия: вход от ${signIn.left}, название до ${title.right}"
        }

        val signInMiddle = (signIn.top + signIn.bottom) / 2
        val titleMiddle = (title.top + title.bottom) / 2
        val drift = abs((signInMiddle - titleMiddle).value)
        check(drift < 24f) {
            "Вход должен стоять напротив названия, а не под ним: расхождение $drift"
        }
    }

    @Test
    fun signIn_opensTheEmailDialog() {
        openDrawer()
        composeRule.onNodeWithTag(DRAWER_SIGN_IN_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.backup_login_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.backup_login_email)).assertIsDisplayed()
    }

    @Test
    fun settings_offersNoServerAddressField() {
        openDrawer()
        composeRule.onNodeWithTag(drawerTag(TodiDestination.SETTINGS)).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule
            .onNode(hasAnyAncestor(hasTestTag(BACKUP_SWITCH_TAG)) and isToggleable())
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule
                .onAllNodesWithText(str(R.string.settings_backup_not_signed_in))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule
            .onNodeWithText(str(R.string.settings_backup_not_signed_in))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
