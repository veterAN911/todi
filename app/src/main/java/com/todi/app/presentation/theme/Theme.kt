package com.todi.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.todi.app.domain.model.ThemeMode

private val DarkColorScheme =
    darkColorScheme(
        primary = Iris,
        onPrimary = IrisOnDark,
        primaryContainer = IrisContainerDark,
        onPrimaryContainer = IrisOnContainerDark,
        secondary = GraySecondaryDark,
        onSecondary = GrayOnSecondaryDark,
        secondaryContainer = GraySecondaryContainerDark,
        onSecondaryContainer = GrayOnSecondaryContainerDark,
        error = SoftErrorDark,
        onError = OnSoftErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        background = GraphiteBackground,
        onBackground = OffWhite,
        surface = GraphiteSurface,
        onSurface = OffWhite,
        surfaceVariant = GraphiteOutlineVariant,
        onSurfaceVariant = MutedGrayDark,
        outline = GraphiteOutline,
        outlineVariant = GraphiteOutlineVariant,
        surfaceContainerLowest = GraphiteSurfaceLowest,
        surfaceContainerLow = GraphiteSurfaceLow,
        surfaceContainer = GraphiteSurfaceContainer,
        surfaceContainerHigh = GraphiteSurfaceContainerHigh,
        surfaceContainerHighest = GraphiteSurfaceContainerHighest,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = IrisLight,
        onPrimary = IrisOnLight,
        primaryContainer = IrisContainerLight,
        onPrimaryContainer = IrisOnContainerLight,
        secondary = GraySecondaryLight,
        onSecondary = GrayOnSecondaryLight,
        secondaryContainer = GraySecondaryContainerLight,
        onSecondaryContainer = GrayOnSecondaryContainerLight,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        background = PaperBackground,
        onBackground = Ink,
        surface = PaperSurface,
        onSurface = Ink,
        surfaceVariant = PaperOutlineVariant,
        onSurfaceVariant = MutedGrayLight,
        outline = PaperOutline,
        outlineVariant = PaperOutlineVariant,
        surfaceContainerLowest = PaperSurfaceLowest,
        surfaceContainerLow = PaperSurfaceLow,
        surfaceContainer = PaperSurfaceContainer,
        surfaceContainerHigh = PaperSurfaceContainerHigh,
        surfaceContainerHighest = PaperSurfaceContainerHighest,
    )

@Immutable
data class TodiExtraColors(
    val completed: Color,
)

private val DarkExtraColors = TodiExtraColors(completed = DoneGreenDark)
private val LightExtraColors = TodiExtraColors(completed = DoneGreenLight)

private val LocalTodiExtraColors = staticCompositionLocalOf { LightExtraColors }

object TodiColors {
    val completed: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalTodiExtraColors.current.completed
}

@Composable
fun TodiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    CompositionLocalProvider(
        LocalTodiExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = TodiTypography,
            content = content,
        )
    }
}
