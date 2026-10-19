package com.todi.app.presentation.voice

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal fun beadColor(
    brushes: OrbBrushes,
    hue: Float,
    saturation: Float,
    alpha: Float,
    pale: Float,
): Int {
    val hsv = brushes.hsv
    hsv[0] = hue
    hsv[1] = saturation * (1f - pale)
    hsv[2] = 1f
    return android.graphics.Color.HSVToColor((alpha * 255f).toInt().coerceIn(0, 255), hsv)
}

internal fun android.graphics.Canvas.drawBead(
    x: Float,
    y: Float,
    radius: Float,
    color: Int,
    core: Float,
    brushes: OrbBrushes,
) {
    brushes.haloPaint.color = color
    drawCircle(x, y, radius, brushes.haloPaint)
    brushes.dotPaint.color = color
    drawCircle(x, y, radius * core, brushes.dotPaint)
}

internal fun DrawScope.glow(
    centre: Offset,
    radius: Float,
    base: OrbBase,
    alpha: Float,
) {
    if (alpha <= 0f) return
    drawCircle(
        brush =
            Brush.radialGradient(
                colorStops =
                    arrayOf(
                        0f to Color.Transparent,
                        GLOW_HOLLOW to Color.Transparent,
                        GLOW_CREST to base.glow(alpha),
                        1f to Color.Transparent,
                    ),
                center = centre,
                radius = radius,
            ),
        radius = radius,
        center = centre,
    )
}

internal class OrbBase(
    val hue: Float,
    val saturation: Float,
) {
    fun line(alpha: Float): Color = Color.hsv(hue, saturation, 1f, alpha)

    fun glow(alpha: Float): Color = Color.hsv(hue, saturation * GLOW_WASH, 1f, alpha)

    companion object {
        private const val MIN_SATURATION = 0.55f

        fun of(color: Color): OrbBase {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            return OrbBase(hue = hsv[0], saturation = hsv[1].coerceIn(MIN_SATURATION, 1f))
        }
    }
}

internal class OrbBrushes(
    density: Density,
) {
    val haloPaint: android.graphics.Paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            maskFilter =
                with(density) {
                    BlurMaskFilter(DOT_BLUR.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
        }

    val dotPaint: android.graphics.Paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }

    val nearPath = Path()
    val farPath = Path()
    val hsv = FloatArray(3)
}

private val DOT_BLUR = 7.dp

private const val GLOW_CREST = 0.82f

private const val GLOW_HOLLOW = 0.42f

private const val GLOW_WASH = 0.7f
