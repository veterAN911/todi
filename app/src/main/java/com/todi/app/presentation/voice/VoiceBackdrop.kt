package com.todi.app.presentation.voice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

private val BACKDROP_BLUR = 16.dp

@Composable
fun VoiceBackdrop(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (active) Modifier.blur(BACKDROP_BLUR) else Modifier),
    ) {
        content()
    }
}
