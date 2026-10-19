package com.todi.app.presentation.voice

import com.todi.app.domain.FakeSettingsRepository
import com.todi.app.domain.FakeTaskRepository
import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.usecase.CreateTaskUseCase
import com.todi.app.domain.usecase.DeleteTaskUseCase
import com.todi.app.domain.usecase.SaveVoiceDraftsUseCase
import com.todi.app.domain.util.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputControllerTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = Clock.fixed(Instant.parse("2026-09-20T07:00:00Z"), moscow)
    private val factory = FakeSpeechEngineFactory()

    private val repository = FakeTaskRepository()

    private fun TestScope.newController(
        hasModel: Boolean = true,
        permission: Boolean = true,
        languageTag: String = "ru-RU",
        environment: FakeVoiceEnvironment = FakeVoiceEnvironment(hasModel, permission, languageTag),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): VoiceInputController {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return VoiceInputController(
            environment = environment,
            engines = factory,
            saveDrafts = SaveVoiceDraftsUseCase(createTask = CreateTaskUseCase(repository), clock = clock),
            deleteTask = DeleteTaskUseCase(repository),
            settings = settings,
            clock = clock,
            scope = scope,
        )
    }

    @Test
    fun armAndPull_trackTheFinger() =
        runTest {
            val controller = newController()

            controller.arm(targetDate = null)
            assertEquals(VoiceState.Armed(0f, hint = false), controller.state.value)

            controller.pull(0.5f)
            assertEquals(VoiceState.Armed(0.5f, hint = true), controller.state.value)

            controller.pull(4f)
            assertEquals(VoiceState.Armed(1f, hint = true), controller.state.value)
        }

    @Test
    fun voiceInputOff_refusesToArm() =
        runTest {
            val controller =
                newController(
                    settings = FakeSettingsRepository(AppSettings(voiceInputEnabled = false)),
                )
            runCurrent()

            assertFalse(controller.enabled.value)

            controller.arm(targetDate = null)
            controller.pull(1f)
            controller.retry()
            runCurrent()

            assertEquals(VoiceState.Idle, controller.state.value)
            assertEquals(0, factory.created)
        }

    @Test
    fun pullAfterTheEnd_isIgnored() =
        runTest {
            val controller = newController()

            controller.arm(targetDate = null)
            controller.pull(1f)
            controller.pull(0.2f)

            assertEquals(VoiceState.Armed(1f, hint = true), controller.state.value)
        }

    @Test
    fun withoutAModel_theDownloadIsOfferedInstead() =
        runTest {
            val controller = newController(hasModel = false)

            controller.trigger()
            runCurrent()

            assertEquals(VoiceState.NeedsModel, controller.state.value)
            assertEquals(0, factory.created)
        }

    @Test
    fun askingForTheModel_reachesTheManager() =
        runTest {
            val environment = FakeVoiceEnvironment(hasModel = false)
            val controller = newController(environment = environment)

            controller.downloadModel()

            assertEquals(1, environment.downloads)
        }

    @Test
    fun theHintIsShownWhileTheFingerIsStillHolding() =
        runTest {
            val controller = newController()

            controller.arm(targetDate = null)
            assertEquals(VoiceState.Armed(0f, hint = false), controller.state.value)

            controller.showHint()
            assertEquals(VoiceState.Armed(0f, hint = true), controller.state.value)
        }

    @Test
    fun aPullShowsTheHintItself() =
        runTest {
            val controller = newController()

            controller.arm(targetDate = null)
            controller.pull(0.2f)

            assertEquals(VoiceState.Armed(0.2f, hint = true), controller.state.value)
        }

    @Test
    fun aHintArrivingAfterTheGesture_isDropped() =
        runTest {
            val controller = newController()

            controller.arm(targetDate = null)
            controller.cancel()
            controller.showHint()

            assertEquals(VoiceState.Idle, controller.state.value)
        }

    @Test
    fun withoutPermission_theDialogIsAskedFor() =
        runTest {
            val controller = newController(permission = false)

            controller.trigger()
            runCurrent()

            assertEquals(VoiceState.NeedsPermission, controller.state.value)
            assertEquals(0, factory.created)
        }

    @Test
    fun permissionGranted_startsListening() =
        runTest {
            val environment = FakeVoiceEnvironment(permission = false)
            val controller = newController(environment = environment)

            controller.trigger()
            runCurrent()
            environment.permission = true
            controller.onPermissionResult(granted = true)
            runCurrent()

            assertEquals(
                VoiceState.Listening(VoiceLanguage.RUSSIAN, partial = null),
                controller.state.value,
            )
        }

    @Test
    fun permissionRefused_failsWithPermission() =
        runTest {
            val controller = newController(permission = false)

            controller.trigger()
            runCurrent()
            controller.onPermissionResult(granted = false)

            assertEquals(VoiceState.Failed(VoiceFailure.PERMISSION), controller.state.value)
        }

    @Test
    fun retryAfterARefusal_asksAgain() =
        runTest {
            val controller = newController(permission = false)

            controller.trigger()
            runCurrent()
            controller.onPermissionResult(granted = false)
            controller.retry()
            runCurrent()

            assertEquals(VoiceState.NeedsPermission, controller.state.value)
        }

    @Test
    fun partialResults_areShownWhileListening() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Partial("купить хлеб"))
            runCurrent()

            assertEquals(
                VoiceState.Listening(VoiceLanguage.RUSSIAN, "купить хлеб"),
                controller.state.value,
            )
        }

    @Test
    fun thePhraseIsParsedIntoTasksWithDates() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Final("купить хлеб завтра"))
            runCurrent()

            val review = controller.state.value as VoiceState.Review
            assertEquals("купить хлеб завтра", review.transcript)
            assertEquals(1, review.drafts.size)
            assertEquals("купить хлеб", review.drafts[0].title)
            assertEquals(LocalDate.of(2026, 9, 21), review.drafts[0].date)
            assertNull(review.drafts[0].time)
        }

    @Test
    fun aPhraseWithTimesIsSplitAcrossTheDraftsItNames() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Final("позвонить маме в 15:00 и купить хлеб послезавтра"))
            runCurrent()

            val drafts = (controller.state.value as VoiceState.Review).drafts
            assertEquals(2, drafts.size)
            assertEquals(LocalTime.of(15, 0), drafts[0].time)
            assertEquals(LocalDate.of(2026, 9, 22), drafts[1].date)
        }

    @Test
    fun anotherLanguage_isOneDraftWithoutADate() =
        runTest {
            val controller = newController(languageTag = "en-US")
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Final("buy bread tomorrow"))
            runCurrent()

            val review = controller.state.value as VoiceState.Review
            assertEquals(VoiceLanguage.OTHER, review.language)
            assertEquals(1, review.drafts.size)
            assertEquals("buy bread tomorrow", review.drafts[0].title)
            assertNull(review.drafts[0].date)
        }

    @Test
    fun aPartialAfterThePhrase_isIgnored() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Final("купить хлеб"))
            runCurrent()
            val settled = controller.state.value
            factory.events.emit(SpeechEvent.Partial("купить"))
            runCurrent()

            assertEquals(settled, controller.state.value)
        }

    @Test
    fun aRecorderFailureBecomesAGenericRecogniserFailure() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Failed(SpeechFailure.AUDIO))
            runCurrent()

            assertEquals(VoiceState.Failed(VoiceFailure.RECOGNIZER), controller.state.value)
        }

    @Test
    fun theModelGoingMissing_offersTheDownload() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Failed(SpeechFailure.MODEL_MISSING))
            runCurrent()

            assertEquals(VoiceState.NeedsModel, controller.state.value)
        }

    @Test
    fun hearingNothing_isItsOwnFailure() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.events.emit(SpeechEvent.Failed(SpeechFailure.NO_MATCH))
            runCurrent()

            assertEquals(VoiceState.Failed(VoiceFailure.NO_SPEECH), controller.state.value)
        }

    @Test
    fun aMinuteOfSilence_endsTheSession() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            advanceTimeBy(61_000)
            runCurrent()

            assertEquals(VoiceState.Failed(VoiceFailure.NO_SPEECH), controller.state.value)
        }

    @Test
    fun cancel_returnsToIdleAndDeafensTheSession() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            controller.cancel()
            factory.events.emit(SpeechEvent.Final("купить хлеб"))
            runCurrent()

            assertEquals(VoiceState.Idle, controller.state.value)
        }

    @Test
    fun loudnessReachesTheOrbAndDiesWithTheSession() =
        runTest {
            val controller = newController()
            controller.trigger()
            runCurrent()

            factory.level.value = 0.8f
            runCurrent()
            assertEquals(0.8f, controller.level.value, 0.001f)

            controller.cancel()
            assertEquals(0f, controller.level.value, 0.001f)
        }

    @Test
    fun theLevelOfAnIdleControllerStaysAtZero() =
        runTest {
            val controller = newController()

            assertTrue(controller.state.value is VoiceState.Idle)
            assertEquals(0f, controller.level.value, 0.001f)
        }

    private val today = LocalDate.of(2026, 9, 20)
    private val tomorrow = LocalDate.of(2026, 9, 21)

    private suspend fun TestScope.settle(
        controller: VoiceInputController,
        phrase: String,
    ): VoiceState.Review {
        controller.trigger()
        runCurrent()
        factory.events.emit(SpeechEvent.Final(phrase))
        runCurrent()
        return controller.state.value as VoiceState.Review
    }

    @Test
    fun aDraftWithNoDay_landsOnTheDayTheGestureStartedFrom() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = tomorrow)

            val review = settle(controller, "купить хлеб")

            assertEquals(tomorrow, review.targetDate)
            controller.saveAll()
            runCurrent()

            val saved = repository.observeAllTasks().first().single()
            assertEquals(tomorrow, saved.date)
            assertEquals("купить хлеб", saved.title)
        }

    @Test
    fun onTheDateLessScreen_aDraftWithNoDay_becomesATaskWithoutADate() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = null)

            settle(controller, "купить хлеб в 15:00")
            controller.saveAll()
            runCurrent()

            val saved = repository.observeGlobalTasks().first().single()
            assertTrue(saved.isGlobal)
            assertNull(saved.time)
        }

    @Test
    fun savingAll_createsEveryDraftOnItsOwnDay() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = today)

            settle(controller, "купить хлеб и позвонить маме завтра")
            controller.saveAll()
            runCurrent()

            assertEquals(listOf("купить хлеб"), repository.observeTasksForDate(today).first().map { it.title })
            assertEquals(listOf("позвонить маме"), repository.observeTasksForDate(tomorrow).first().map { it.title })
        }

    @Test
    fun savingOneBlock_leavesTheOthersOnTheFeed() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = today)

            settle(controller, "купить хлеб и позвонить маме завтра")
            controller.saveGroup(tomorrow)
            runCurrent()

            assertEquals(listOf("позвонить маме"), repository.observeTasksForDate(tomorrow).first().map { it.title })
            assertTrue(repository.observeTasksForDate(today).first().isEmpty())

            val review = controller.state.value as VoiceState.Review
            assertEquals(listOf("купить хлеб"), review.drafts.map { it.title })
        }

    @Test
    fun droppingABlock_createsNothing() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = today)

            settle(controller, "купить хлеб и позвонить маме завтра")
            controller.dropGroup(today)
            runCurrent()

            val review = controller.state.value as VoiceState.Review
            assertEquals(listOf("позвонить маме"), review.drafts.map { it.title })
            assertTrue(repository.observeAllTasks().first().isEmpty())
        }

    @Test
    fun aSave_isReported_andCanBeTakenBack() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = today)

            settle(controller, "купить хлеб")
            controller.saveAll()
            runCurrent()

            val saved = (controller.state.value as VoiceState.Review).lastSave
            assertNotNull(saved)
            assertEquals(1, saved!!.count)
            assertEquals(1, repository.observeAllTasks().first().size)

            controller.undoSave()
            runCurrent()

            val review = controller.state.value as VoiceState.Review
            assertNull(review.lastSave)
            assertEquals(listOf("купить хлеб"), review.drafts.map { it.title })

            val after = repository.observeAllTasks().first().single()
            assertNotNull(after.deletedAt)
            assertTrue(repository.observeTasksForDate(today).first().isEmpty())
        }

    @Test
    fun editingTheTranscript_reReadsTheDrafts() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = today)

            settle(controller, "купить хлеб завтра")
            controller.startEditing()
            assertTrue((controller.state.value as VoiceState.Review).editing)

            controller.editTranscript("купить хлеб")
            var review = controller.state.value as VoiceState.Review
            assertNull(review.drafts.single().date)

            controller.editTranscript("купить хлеб и позвонить маме завтра")
            runCurrent()
            review = controller.state.value as VoiceState.Review
            assertEquals(2, review.drafts.size)

            controller.finishEditing()
            assertFalse((controller.state.value as VoiceState.Review).editing)
        }

    @Test
    fun aPhraseSplitAcrossDays_takesTheTargetDayIntoAccountForTheDateLessPart() =
        runTest {
            val controller = newController()
            controller.arm(targetDate = today)

            settle(controller, "купить хлеб и позвонить маме в понедельник")
            controller.saveAll()
            runCurrent()

            val undated = repository.observeTasksForDate(today).first()
            assertEquals(listOf("купить хлеб"), undated.map { it.title })
        }
}

private class FakeVoiceEnvironment(
    private val hasModel: Boolean = true,
    var permission: Boolean = true,
    private val languageTag: String = "ru-RU",
) : VoiceEnvironment {
    override val modelState: StateFlow<VoskModelState> =
        MutableStateFlow(if (hasModel) READY else VoskModelState.Absent)

    var downloads = 0
        private set

    override suspend fun hasModel(): Boolean = hasModel

    override fun downloadModel() {
        downloads += 1
    }

    override fun hasMicrophonePermission(): Boolean = permission

    override fun languageTag(): String = languageTag

    private companion object {
        val READY = VoskModelState.Ready(bytes = 87_000_000L)
    }
}

private class FakeSpeechEngineFactory : SpeechEngineFactory {
    val events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    val level = MutableStateFlow(0f)

    var created = 0
        private set

    override fun create(): SpeechSession {
        created += 1
        return SpeechSession(events = events, level = level)
    }
}
