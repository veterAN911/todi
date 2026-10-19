package com.todi.app.presentation.voice

import kotlin.math.atan2
import kotlin.math.sqrt

object AddButtonContour {
    fun hump(
        radius: Float,
        sink: Float,
        gap: Float = 0f,
        shoulder: Float = 0f,
    ): Hump? {
        if (radius <= 0f || sink < 0f) return null

        val above = radius - sink
        val capRadius = radius + gap
        val bend = shoulder.coerceAtLeast(0f)

        val reach =
            sqrt(
                ((capRadius + bend) * (capRadius + bend) - (bend - above) * (bend - above))
                    .coerceAtLeast(0f),
            )

        val touch = atan2(bend - above, reach) * DEGREES_PER_RADIAN

        return Hump(
            straightEnd = reach,
            leftShoulder =
                Arc(
                    centreX = -reach,
                    centreY = -bend,
                    radius = bend,
                    startDegrees = 90f,
                    sweepDegrees = touch - 90f,
                ),
            cap =
                Arc(
                    centreX = 0f,
                    centreY = -above,
                    radius = capRadius,
                    startDegrees = 180f + touch,
                    sweepDegrees = 180f - 2f * touch,
                ),
            rightShoulder =
                Arc(
                    centreX = reach,
                    centreY = -bend,
                    radius = bend,
                    startDegrees = 180f - touch,
                    sweepDegrees = touch - 90f,
                ),
        )
    }

    data class Hump(
        val straightEnd: Float,
        val leftShoulder: Arc,
        val cap: Arc,
        val rightShoulder: Arc,
    ) {
        val arcs: List<Arc> get() = listOf(leftShoulder, cap, rightShoulder)
    }

    data class Arc(
        val centreX: Float,
        val centreY: Float,
        val radius: Float,
        val startDegrees: Float,
        val sweepDegrees: Float,
    )

    private const val DEGREES_PER_RADIAN = 57.29577951308232f
}
