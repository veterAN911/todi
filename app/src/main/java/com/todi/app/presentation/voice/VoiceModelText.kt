package com.todi.app.presentation.voice

import com.todi.app.R
import java.util.Locale

fun voiceModelFailureText(reason: VoskModelFailure): Int =
    when (reason) {
        VoskModelFailure.NETWORK -> R.string.settings_voice_model_failed_network
        VoskModelFailure.STORAGE -> R.string.settings_voice_model_failed_storage
        VoskModelFailure.ARCHIVE -> R.string.settings_voice_model_failed_archive
    }

fun modelMegabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0))
