package com.todi.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todi.app.R
import com.todi.app.notification.NotificationColors
import com.todi.app.notification.NotificationContent

@Composable
fun NotificationPreviewSection(
    preview: NotificationPreviewState?,
    colors: NotificationColors,
    reminderEnabled: Boolean,
) {
    if (preview == null) return

    SettingsSection(title = stringResource(R.string.settings_notification_preview)) {
        PreviewCard(
            label = stringResource(R.string.notification_preview_shade),
            head = preview.shadeTitle,
            content = preview.shade,
            colors = colors,
        )
        PreviewCard(
            label = stringResource(R.string.notification_preview_lock_screen),
            head = preview.lockScreenTitle,
            content = preview.lockScreen,
            colors = colors,
            hiddenText = stringResource(R.string.notification_preview_hidden),
        )
        PreviewCard(
            label = stringResource(R.string.notification_preview_reminder),
            head = preview.reminderTitle,
            content = preview.reminder,
            colors = colors,
            hiddenText = stringResource(R.string.notification_preview_off),
            hidden = !reminderEnabled,
        )
        Text(
            text = stringResource(R.string.settings_notification_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PreviewCard(
    label: String,
    head: String?,
    content: NotificationContent?,
    colors: NotificationColors,
    hiddenText: String = "",
    hidden: Boolean = false,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (content != null && head != null && content.count > 0) {
                    Text(
                        text = head,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                when {
                    content == null || hidden -> {
                        PreviewText(hiddenText)
                    }

                    content.lines.isEmpty() && content.count == 0 -> {
                        PreviewText(stringResource(R.string.notification_preview_empty))
                    }

                    else -> {
                        content.lines.forEach { line ->
                            PreviewText(line.text, colors.forSection(line.section), line.isHeader)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewText(
    text: String,
    color: Int? = null,
    isHeader: Boolean = false,
) {
    Text(
        text = text,
        style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
        color =
            color?.let { Color(it) }
                ?: if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}
