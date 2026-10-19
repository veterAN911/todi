package com.todi.app.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.todi.app.domain.model.AppSettings
import com.todi.app.domain.usecase.DailyRollover
import com.todi.app.domain.util.TodayProvider
import com.todi.app.notification.TaskNotificationManager
import com.todi.app.presentation.navigation.EditorRequestBus
import com.todi.app.presentation.navigation.TodiApp
import com.todi.app.presentation.notifications.NotificationOnboardingScreen
import com.todi.app.presentation.settings.SettingsViewModel
import com.todi.app.presentation.theme.TodiTheme
import com.todi.app.presentation.util.AppLanguage
import com.todi.app.presentation.voice.LocalVoiceGestureConfig
import com.todi.app.presentation.voice.LocalVoiceInput
import com.todi.app.presentation.voice.VoiceBackdrop
import com.todi.app.presentation.voice.VoiceGestureConfig
import com.todi.app.presentation.voice.VoiceInputController
import com.todi.app.presentation.voice.VoiceOverlay
import com.todi.app.presentation.voice.VoiceReviewActions
import com.todi.app.presentation.voice.VoiceState
import com.todi.app.presentation.voice.isRevealed
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var todayProvider: TodayProvider

    @Inject lateinit var editorRequestBus: EditorRequestBus

    @Inject lateinit var dailyRollover: DailyRollover

    @Inject lateinit var voiceInputController: VoiceInputController

    @Inject lateinit var voiceGestureConfig: VoiceGestureConfig

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            voiceInputController.onPermissionResult(granted)
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapBaseContext(newBase) ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleExtra(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val voiceState by voiceInputController.state.collectAsStateWithLifecycle()

            val voiceLevel = voiceInputController.level.collectAsStateWithLifecycle()

            val voiceModelState by voiceInputController.modelState.collectAsStateWithLifecycle()

            LaunchedEffect(voiceState) {
                if (voiceState is VoiceState.NeedsPermission) {
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            CompositionLocalProvider(
                LocalVoiceInput provides voiceInputController,
                LocalVoiceGestureConfig provides voiceGestureConfig,
            ) {
                TodiTheme(themeMode = settings.themeMode) {
                    Box(Modifier.fillMaxSize()) {
                        VoiceBackdrop(active = voiceState.isRevealed) {
                            if (!settings.notificationPromptShown) {
                                NotificationOnboardingScreen(
                                    onAllow = {
                                        requestNotificationPermission()
                                        settingsViewModel.markNotificationPromptShown()
                                    },
                                    onLater = { settingsViewModel.markNotificationPromptShown() },
                                )
                            } else {
                                TodiApp(
                                    editorRequestBus = editorRequestBus,
                                    bottomBarRoutes = settings.bottomBarRoutes,
                                )
                            }
                        }
                        VoiceOverlay(
                            state = voiceState,
                            level = voiceLevel,
                            modelState = voiceModelState,
                            onDownloadModel = { voiceInputController.downloadModel() },
                            onCancel = { voiceInputController.cancel() },
                            onRetry = { voiceInputController.retry() },
                            onOpenForm = {
                                voiceInputController.cancel()
                                editorRequestBus.requestOpen()
                            },
                            review =
                                VoiceReviewActions(
                                    onSaveBlock = voiceInputController::saveGroup,
                                    onDropBlock = voiceInputController::dropGroup,
                                    onSaveAll = voiceInputController::saveAll,
                                    onUndo = voiceInputController::undoSave,
                                    onEdit = voiceInputController::startEditing,
                                    onEditTranscript = voiceInputController::editTranscript,
                                    onFinishEditing = voiceInputController::finishEditing,
                                ),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExtra(intent)
    }

    override fun onStart() {
        super.onStart()

        todayProvider.refresh()
        lifecycleScope.launch { dailyRollover.perform() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleExtra(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(TaskNotificationManager.EXTRA_OPEN_EDITOR, false)) {
            editorRequestBus.requestOpen()
        }
    }
}
