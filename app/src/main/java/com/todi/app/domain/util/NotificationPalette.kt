package com.todi.app.domain.util

import kotlin.math.pow

object NotificationPalette {
    const val MIN_CONTRAST = 3.0

    const val SWATCHES_PER_ROW = 4

    const val LIGHT_BACKGROUND = 0xFFFFFFFF.toInt()

    const val DARK_BACKGROUND = 0xFF1F1F1F.toInt()

    val swatches: List<Int> =
        listOf(
            0xFFE53935.toInt(),
            0xFFE64A19.toInt(),
            0xFFF4511E.toInt(),
            0xFF8D6E63.toInt(),
            0xFF827717.toInt(),
            0xFF2E7D32.toInt(),
            0xFF43A047.toInt(),
            0xFF00897B.toInt(),
            0xFF0097A7.toInt(),
            0xFF1E88E5.toInt(),
            0xFF448AFF.toInt(),
            0xFF5C6BC0.toInt(),
            0xFF9575CD.toInt(),
            0xFFAB47BC.toInt(),
            0xFFEC407A.toInt(),
            0xFF607D8B.toInt(),
        )

    fun isReadableOnBoth(argb: Int): Boolean =
        contrastAgainst(argb, LIGHT_BACKGROUND) >= MIN_CONTRAST &&
            contrastAgainst(argb, DARK_BACKGROUND) >= MIN_CONTRAST

    fun contrastAgainst(
        foreground: Int,
        background: Int,
    ): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
    }

    private fun relativeLuminance(argb: Int): Double =
        RED_WEIGHT * linearize(argb shr 16 and 0xFF) +
            GREEN_WEIGHT * linearize(argb shr 8 and 0xFF) +
            BLUE_WEIGHT * linearize(argb and 0xFF)

    private fun linearize(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private const val CONTRAST_OFFSET = 0.05
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
}
