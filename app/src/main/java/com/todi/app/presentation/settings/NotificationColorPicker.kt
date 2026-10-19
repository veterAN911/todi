package com.todi.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todi.app.R
import com.todi.app.domain.util.NotificationPalette
import com.todi.app.notification.NotificationSection
import android.graphics.Color as AndroidColor

fun colorRowTag(section: NotificationSection): String = "settings_color_${section.name.lowercase()}"

fun colorSwatchTag(argb: Int): String = "settings_swatch_$argb"

const val COLOR_SYSTEM_TAG = "settings_color_system"
const val COLOR_CUSTOM_TAG = "settings_color_custom"

private const val DEFAULT_CUSTOM_COLOR = 0xFFE53935.toInt()

private val SWATCH_SIZE = 44.dp

@Composable
fun NotificationColorRow(
    section: NotificationSection,
    label: String,
    current: Int?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(colorRowTag(section)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (current == null) {
            Text(
                text = stringResource(R.string.notification_color_system),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(
                Modifier
                    .size(24.dp)
                    .background(Color(current), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationColorSheet(
    title: String,
    current: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val start = remember { hsvOf(current ?: DEFAULT_CUSTOM_COLOR) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    val custom = Color.hsv(hue, saturation, brightness)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            SystemColorRow(selected = current == null) { onPick(null) }

            NotificationPalette.swatches.chunked(NotificationPalette.SWATCHES_PER_ROW).forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { argb ->
                        Swatch(argb = argb, selected = current == argb) { onPick(argb) }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { customOpen = !customOpen }
                    .padding(vertical = 12.dp)
                    .testTag(COLOR_CUSTOM_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.notification_color_custom),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (customOpen) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(custom, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
            }

            if (customOpen) {
                ColorSlider(
                    label = stringResource(R.string.notification_color_hue),
                    value = hue,
                    range = 0f..360f,
                    thumb = Color.hsv(hue, 1f, 1f),
                ) { hue = it }
                ColorSlider(
                    label = stringResource(R.string.notification_color_saturation),
                    value = saturation,
                    range = 0f..1f,
                    thumb = custom,
                ) { saturation = it }
                ColorSlider(
                    label = stringResource(R.string.notification_color_brightness),
                    value = brightness,
                    range = 0f..1f,
                    thumb = custom,
                ) { brightness = it }

                if (!NotificationPalette.isReadableOnBoth(custom.toArgb())) {
                    Text(
                        text = stringResource(R.string.notification_color_low_contrast),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                TextButton(
                    onClick = { onPick(custom.toArgb()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun SystemColorRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(COLOR_SYSTEM_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Block,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.notification_color_system),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Swatch(
    argb: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(argb)
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .testTag(colorSwatchTag(argb)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            val onSwatch =
                if (
                    NotificationPalette.contrastAgainst(argb, WHITE) >=
                    NotificationPalette.contrastAgainst(argb, BLACK)
                ) {
                    Color.White
                } else {
                    Color.Black
                }
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = onSwatch,
            )
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    thumb: Color,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = thumb, activeTrackColor = thumb),
        )
    }
}

private fun hsvOf(argb: Int): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    return hsv
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
