package com.todi.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.todi.app.domain.util.ParsedTask
import com.todi.app.domain.util.VoiceLanguage
import com.todi.app.presentation.voice.VOICE_EDIT_FIELD_TAG
import com.todi.app.presentation.voice.VOICE_ENGINE_TAG
import com.todi.app.presentation.voice.VOICE_MESSAGE_TAG
import com.todi.app.presentation.voice.VOICE_MODEL_DOWNLOAD_TAG
import com.todi.app.presentation.voice.VOICE_OVERLAY_TAG
import com.todi.app.presentation.voice.VOICE_SAVED_TAG
import com.todi.app.presentation.voice.VoiceFailure
import com.todi.app.presentation.voice.VoiceOverlay
import com.todi.app.presentation.voice.VoiceReviewActions
import com.todi.app.presentation.voice.VoiceSave
import com.todi.app.presentation.voice.VoiceState
import com.todi.app.presentation.voice.VoskModelFailure
import com.todi.app.presentation.voice.VoskModelState
import com.todi.app.presentation.voice.voiceBlockDropTag
import com.todi.app.presentation.voice.voiceBlockSaveTag
import com.todi.app.presentation.voice.voiceBlockTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class VoiceOverlayTest {
    private val behindTag = "behindTheGlass"

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        val READY = VoskModelState.Ready(bytes = 87_000_000L)
    }

    private val level = mutableStateOf(0.5f)

    private fun str(
        id: Int,
        vararg args: Any,
    ): String = context.resources.getString(id, *args)

    private fun showVoice(
        state: VoiceState,
        modelState: VoskModelState = READY,
        onDownloadModel: () -> Unit = {},
        onCancel: () -> Unit = {},
        onRetry: () -> Unit = {},
        onOpenForm: () -> Unit = {},
        review: VoiceReviewActions = VoiceReviewActions(),
    ) {
        freezeTheBallIfItIsTurning(state)
        composeRule.setContent {
            MaterialTheme {
                VoiceOverlay(
                    state = state,
                    level = level,
                    modelState = modelState,
                    onDownloadModel = onDownloadModel,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onOpenForm = onOpenForm,
                    review = review,
                )
            }
        }
    }

    private fun freezeTheBallIfItIsTurning(state: VoiceState) {
        if (state is VoiceState.Listening) composeRule.mainClock.autoAdvance = false
    }

    private fun listening(partial: String? = null) = VoiceState.Listening(language = VoiceLanguage.RUSSIAN, partial = partial)

    private fun draft(
        title: String,
        date: LocalDate? = null,
        time: LocalTime? = null,
    ) = ParsedTask(date = date, time = time, title = title, datePhrase = null, timePhrase = null)

    @Test
    fun idle_isNoScreenAtAll() {
        showVoice(VoiceState.Idle)

        composeRule.onAllNodesWithTag(VOICE_OVERLAY_TAG).assertCountEquals(0)
    }

    @Test
    fun listening_saysTheRecognitionStaysOnThePhone() {
        showVoice(listening())

        composeRule.onNodeWithTag(VOICE_ENGINE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.voice_engine_offline)).assertIsDisplayed()
    }

    @Test
    fun listening_showsTheGuessSoFar() {
        showVoice(listening(partial = "купить хлеб"))

        composeRule.onNodeWithText("купить хлеб").assertIsDisplayed()
    }

    @Test
    fun needsPermission_explainsItself_andAsksAgain() {
        var retried = false
        showVoice(VoiceState.NeedsPermission, onRetry = { retried = true })

        composeRule.onNodeWithTag(VOICE_MESSAGE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.voice_permission_rationale)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.voice_allow_microphone)).performClick()

        assertTrue(retried)
    }

    @Test
    fun aFailureSaysWhatWentWrong() {
        showVoice(VoiceState.Failed(VoiceFailure.NO_SPEECH))

        composeRule.onNodeWithText(str(R.string.voice_error_no_speech)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.voice_error_recognizer)).assertDoesNotExist()
    }

    @Test
    fun withoutTheModel_theDownloadIsOffered() {
        var downloaded = false
        showVoice(
            VoiceState.NeedsModel,
            modelState = VoskModelState.Absent,
            onDownloadModel = { downloaded = true },
        )

        composeRule.onNodeWithText(str(R.string.voice_error_model_missing)).assertIsDisplayed()
        composeRule.onNodeWithTag(VOICE_MODEL_DOWNLOAD_TAG).performClick()

        assertTrue(downloaded)
    }

    @Test
    fun whileTheModelIsDownloading_theProgressIsShown() {
        showVoice(
            VoiceState.NeedsModel,
            modelState = VoskModelState.Downloading(progress = 0.42f),
        )

        composeRule
            .onNodeWithText(str(R.string.settings_voice_model_downloading, 42))
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(VOICE_MODEL_DOWNLOAD_TAG).assertCountEquals(0)
    }

    @Test
    fun aBrokenDownload_explainsItselfAndOffersToContinue() {
        showVoice(
            VoiceState.NeedsModel,
            modelState = VoskModelState.Failed(reason = VoskModelFailure.NETWORK, partial = 31_000_000L),
        )

        composeRule.onNodeWithText(str(R.string.settings_voice_model_failed_network)).assertIsDisplayed()

        composeRule.onNodeWithTag(VOICE_MODEL_DOWNLOAD_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_voice_model_resume, "29.6")).assertIsDisplayed()
    }

    @Test
    fun onceTheModelIsReady_theMicrophoneStaysClosed() {
        showVoice(VoiceState.NeedsModel, modelState = READY)

        composeRule.onNodeWithText(str(R.string.voice_model_ready_pull)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(VOICE_MODEL_DOWNLOAD_TAG).assertCountEquals(0)
    }

    @Test
    fun aTapOnTheAppUnderTheGlass_doesNotGetThrough() {
        var behindClicked = false
        var cancelled = false
        freezeTheBallIfItIsTurning(listening())
        composeRule.setContent {
            MaterialTheme {
                Box {
                    TextButton(
                        onClick = { behindClicked = true },
                        modifier = Modifier.testTag(behindTag),
                    ) {
                        Text("behind")
                    }
                    VoiceOverlay(
                        state = listening(),
                        level = level,
                        modelState = READY,
                        onDownloadModel = {},
                        onCancel = { cancelled = true },
                        onRetry = {},
                        onOpenForm = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(behindTag).performClick()
        composeRule.onNodeWithText(str(R.string.cancel)).performClick()

        assertFalse(behindClicked)
        assertTrue(cancelled)
    }

    @Test
    fun cancel_leavesTheVoiceScreen() {
        var cancelled = false
        showVoice(listening(), onCancel = { cancelled = true })

        composeRule.onNodeWithText(str(R.string.cancel)).performClick()

        assertTrue(cancelled)
    }

    @Test
    fun drafts_areShownWithTheDateTheyLandedOn() {
        val tomorrow = LocalDate.now().plusDays(1)
        showVoice(
            VoiceState.Review(
                language = VoiceLanguage.RUSSIAN,
                transcript = "купить хлеб завтра и позвонить маме",
                drafts =
                    listOf(
                        draft("купить хлеб", date = tomorrow, time = LocalTime.of(15, 0)),
                        draft("позвонить маме"),
                    ),
                today = today,
            ),
        )

        composeRule.onNodeWithText("купить хлеб").assertIsDisplayed()
        composeRule.onNodeWithText("позвонить маме").assertIsDisplayed()

        composeRule.onAllNodesWithText(str(R.string.tomorrow), substring = true).assertCountEquals(1)
    }

    @Test
    fun aPhraseThatBecameNothing_saysItBecameNothing() {
        showVoice(
            VoiceState.Review(
                language = VoiceLanguage.RUSSIAN,
                transcript = "гм",
                drafts = emptyList(),
                today = today,
            ),
        )

        composeRule.onNodeWithText(str(R.string.voice_no_drafts)).assertIsDisplayed()
    }

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)

    private fun review(
        drafts: List<ParsedTask>,
        targetDate: LocalDate? = null,
        editing: Boolean = false,
        lastSave: VoiceSave? = null,
    ) = VoiceState.Review(
        language = VoiceLanguage.RUSSIAN,
        transcript = "купить хлеб и позвонить маме",
        drafts = drafts,
        today = today,
        targetDate = targetDate,
        editing = editing,
        lastSave = lastSave,
    )

    @Test
    fun theFeed_hasOneBlockPerDay() {
        showVoice(
            review(
                drafts =
                    listOf(
                        draft("купить хлеб", date = tomorrow),
                        draft("позвонить маме"),
                    ),
                targetDate = today,
            ),
        )

        composeRule.onNodeWithTag(voiceBlockTag(tomorrow)).assertIsDisplayed()
        composeRule.onNodeWithTag(voiceBlockTag(today)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(voiceBlockTag(null)).assertCountEquals(0)
    }

    @Test
    fun aDraftWithNoDayOnTheDateLessSection_isItsOwnBlock() {
        showVoice(
            review(drafts = listOf(draft("купить хлеб")), targetDate = null),
        )

        composeRule.onNodeWithTag(voiceBlockTag(null)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.editor_without_date)).assertIsDisplayed()
    }

    @Test
    fun aBlocksOwnOk_savesThatDayAlone() {
        var saved: LocalDate? = today
        showVoice(
            review(
                drafts = listOf(draft("хлеб", date = tomorrow), draft("мама")),
                targetDate = today,
            ),
            review = VoiceReviewActions(onSaveBlock = { saved = it }),
        )

        composeRule.onNodeWithTag(voiceBlockSaveTag(tomorrow)).performClick()

        assertEquals(tomorrow, saved)
    }

    @Test
    fun aBlocksOwnCancel_dropsThatDayAlone() {
        var dropped: LocalDate? = null
        showVoice(
            review(
                drafts = listOf(draft("хлеб", date = tomorrow), draft("мама")),
                targetDate = today,
            ),
            review = VoiceReviewActions(onDropBlock = { dropped = it }),
        )

        composeRule.onNodeWithTag(voiceBlockDropTag(today)).performClick()

        assertEquals(today, dropped)
    }

    @Test
    fun savingEverything_isAnsweredByTheWholeFeed() {
        var savedAll = false
        showVoice(
            review(drafts = listOf(draft("хлеб", date = tomorrow), draft("мама")), targetDate = today),
            review = VoiceReviewActions(onSaveAll = { savedAll = true }),
        )

        composeRule.onNodeWithText(str(R.string.voice_save_all)).performClick()

        assertTrue(savedAll)
    }

    @Test
    fun whatWasJustSaved_isSaidAndCanBeTakenBack() {
        var undone = false
        showVoice(
            review(
                drafts = emptyList(),
                targetDate = today,
                lastSave = VoiceSave(taskIds = listOf(1L), drafts = listOf(draft("хлеб"))),
            ),
            review = VoiceReviewActions(onUndo = { undone = true }),
        )

        composeRule.onNodeWithTag(VOICE_SAVED_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.undo)).performClick()

        assertTrue(undone)
    }

    @Test
    fun onceEverythingIsSettled_theWayOutIsNotCancel() {
        showVoice(
            review(
                drafts = emptyList(),
                targetDate = today,
                lastSave = VoiceSave(taskIds = listOf(1L), drafts = listOf(draft("хлеб"))),
            ),
        )

        composeRule.onNodeWithText(str(R.string.ok)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.cancel)).assertDoesNotExist()
    }

    @Test
    fun editing_opensTheTranscriptForCorrection() {
        var edited: String? = null
        showVoice(
            review(drafts = listOf(draft("хлеб", date = tomorrow)), targetDate = today, editing = true),
            review = VoiceReviewActions(onEditTranscript = { edited = it }),
        )

        composeRule.onNodeWithTag(VOICE_EDIT_FIELD_TAG).performTextInput(" и мама")

        assertTrue(edited!!.contains("купить хлеб"))
        assertTrue(edited!!.contains("и мама"))
    }

    @Test
    fun whileEditing_theBlocksOfferNothing() {
        showVoice(
            review(drafts = listOf(draft("хлеб", date = tomorrow)), targetDate = today, editing = true),
        )

        composeRule.onAllNodesWithTag(voiceBlockSaveTag(tomorrow)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(voiceBlockDropTag(tomorrow)).assertCountEquals(0)
    }
}
