package com.todi.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.todi.app.R
import kotlin.math.min

@Composable
fun EmptyDayState(
    isToday: Boolean,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = true,
) {
    Column(
        modifier =
            modifier
                .then(
                    if (fillHeight) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp)
                    },
                ).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = 56.dp)
        Text(
            text = stringResource(R.string.all_free),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(if (isToday) R.string.no_tasks_today else R.string.no_tasks_date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedButton(
            onClick = onAddTask,
            shape = RoundedCornerShape(14.dp),
            modifier =
                Modifier
                    .padding(top = 20.dp)
                    .height(48.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.add_task),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
fun AllDoneBanner(
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val scale = remember { Animatable(0f) }
        LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.55f)) }
        Box(Modifier.scale(scale.value)) {
            DoneMark(size = 56.dp)
        }
        Text(
            text = stringResource(if (isToday) R.string.all_done else R.string.all_done_any),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.all_done_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun TaskCountPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Text(
            text = pluralStringResource(R.plurals.task_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val dotColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barHeight = w * 0.09f
        val radius = barHeight / 2
        val dotRadius = w * 0.085f
        val barStart = w * 0.44f
        val dotX = w * 0.16f

        fun bar(
            y: Float,
            endFraction: Float,
        ) {
            drawRoundRect(
                color = barColor,
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(barStart, y),
                size =
                    androidx.compose.ui.geometry
                        .Size(w * endFraction - barStart, barHeight),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(radius, radius),
            )
        }

        val y1 = h * 0.13f
        val y2 = h * 0.40f
        val y3 = h * 0.67f
        drawCircle(
            color = dotColor,
            radius = dotRadius,
            center =
                androidx.compose.ui.geometry
                    .Offset(dotX, y1 + barHeight / 2),
        )
        bar(y1, 1f)
        bar(y2, 0.80f)
        bar(y3, 0.86f)
    }
}

@Composable
private fun DoneMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val checkColor = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(color = accent, radius = w / 2)
        val checkLength = min(w, h) * 0.42f
        val strokeWidth = checkLength * 0.22f
        val path =
            Path().apply {
                moveTo(w * 0.28f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.36f)
            }
        drawPath(
            path = path,
            color = checkColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
