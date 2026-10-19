package com.todi.app.presentation.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.todi.app.domain.util.VoiceLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidVoiceEnvironment
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val models: VoskModelManager,
    ) : VoiceEnvironment {
        override val modelState: StateFlow<VoskModelState> = models.state

        override suspend fun hasModel(): Boolean {
            models.refresh()

            return models.installedPath() != null &&
                VoiceLanguage.of(languageTag()) == VoiceLanguage.RUSSIAN
        }

        override fun downloadModel() {
            models.download()
        }

        override fun hasMicrophonePermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        override fun languageTag(): String =
            context.resources.configuration.locales[0]
                .toLanguageTag()
    }
