package com.todi.app

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.todi.app.data.local.AppDatabase
import com.todi.app.presentation.MainActivity
import com.todi.app.presentation.editor.EDITOR_SEGMENT_WITH_DATE
import com.todi.app.presentation.voice.VOICE_FAB_TAG
import com.todi.app.presentation.voice.VOICE_HINT_TAG
import com.todi.app.presentation.voice.VOICE_OVERLAY_TAG
import com.todi.app.presentation.voice.VoiceGestureConfig
import com.todi.app.presentation.voice.VoiceInputController
import com.todi.app.presentation.voice.VoiceState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class VoiceFabGestureTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var voiceInputController: VoiceInputController

    @Inject
    lateinit var gestureConfig: VoiceGestureConfig

    @Before
    fun setUp() {
        hiltRule.inject()
        grantMicrophone()

        voiceInputController.cancel()
        runBlocking { database.clearAllTables() }
        composeRule.waitForIdle()
        dismissOnboardingIfShown()
    }

    private fun grantMicrophone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = "pm grant ${instrumentation.targetContext.packageName} ${Manifest.permission.RECORD_AUDIO}"
        val output =
            instrumentation.uiAutomation.executeShellCommand(command).let {
                ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes().decodeToString() }
            }

        check(output.isBlank()) { "pm grant failed: $output" }
    }

    private fun dismissOnboardingIfShown() {
        val laterText = composeRule.activity.getString(R.string.later)
        val todayText = composeRule.activity.getString(R.string.nav_today)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(laterText).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(todayText).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText(laterText).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(laterText).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun holdButton() {
        composeRule.onNodeWithTag(VOICE_FAB_TAG).performTouchInput { down(center) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            voiceInputController.state.value is VoiceState.Armed
        }
    }

    private fun pullUp(by: Dp) {
        composeRule.onNodeWithTag(VOICE_FAB_TAG).performTouchInput {
            moveBy(Offset(0f, -by.toPx()))
        }
        composeRule.waitForIdle()
    }

    private fun releaseFinger() {
        composeRule.onNodeWithTag(VOICE_FAB_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun assertEditorShown() {
        composeRule.onNodeWithTag(EDITOR_SEGMENT_WITH_DATE).assertIsDisplayed()
    }

    private fun assertEditorNotShown() {
        composeRule.onAllNodesWithTag(EDITOR_SEGMENT_WITH_DATE).assertCountEquals(0)
    }

    private fun assertVoiceOverlayShown() {
        composeRule.onNodeWithTag(VOICE_OVERLAY_TAG).assertIsDisplayed()
    }

    private fun assertVoiceOverlayNotShown() {
        composeRule.onAllNodesWithTag(VOICE_OVERLAY_TAG).assertCountEquals(0)
    }

    @Test
    fun tap_opensTheEditor() {
        composeRule.onNodeWithTag(VOICE_FAB_TAG).performClick()
        composeRule.waitForIdle()

        assertEditorShown()
        assertVoiceOverlayNotShown()
    }

    @Test
    fun holdWithoutPulling_opensTheEditor() {
        holdButton()
        releaseFinger()

        assertEditorShown()
        assertVoiceOverlayNotShown()
    }

    @Test
    fun shortPull_opensTheEditor() {
        holdButton()
        pullUp(20.dp)
        releaseFinger()

        assertEditorShown()
        assertVoiceOverlayNotShown()
    }

    @Test
    fun holding_still_showsTheHintAtTheButton() {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(VOICE_FAB_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(gestureConfig.holdMillis + PAST_IT)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            voiceInputController.state.value is VoiceState.Armed
        }

        composeRule.mainClock.advanceTimeBy(gestureConfig.hintDelayMillis + PAST_IT)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            (voiceInputController.state.value as? VoiceState.Armed)?.hint == true
        }

        composeRule.mainClock.advanceTimeByFrame()

        assertVoiceOverlayNotShown()
        composeRule.onNodeWithTag(VOICE_HINT_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(VOICE_HINT_TAG).assertContentDescriptionEquals(
            composeRule.activity.getString(R.string.voice_pull_up),
        )

        val hint = composeRule.onNodeWithTag(VOICE_HINT_TAG).fetchSemanticsNode().boundsInRoot
        val button = composeRule.onNodeWithTag(VOICE_FAB_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the hint should sit above the button", hint.bottom <= button.top)
        assertTrue("the hint should be on the screen", hint.top >= 0f)
    }

    @Test
    fun pullPastThreshold_showsTheVoiceOverlay() {
        holdButton()
        pullUp(300.dp)

        assertVoiceOverlayShown()
        assertEditorNotShown()
    }

    @Test
    fun releaseAfterFullPull_doesNotOpenTheEditor() {
        holdButton()
        pullUp(300.dp)
        releaseFinger()

        assertVoiceOverlayShown()
        assertEditorNotShown()
    }

    private companion object {
        const val PAST_IT = 150L
    }
}
