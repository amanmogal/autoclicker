package com.autoride.accepter

import java.util.Locale
import kotlin.math.max

internal object ActionSignature {
    private const val POSITION_BUCKET_PX = 120

    fun build(
        windowType: String,
        label: String,
        fare: Double,
        distance: Double,
        centerX: Int,
        centerY: Int,
        viewId: String?
    ): String {
        val normalizedLabel = label
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "<no-text>" }

        val normalizedViewId = viewId
            ?.substringAfterLast('/')
            ?.ifBlank { "<no-id>" }
            ?: "<no-id>"

        val fareBucket = String.format(Locale.US, "%.0f", fare)
        val distanceBucket = String.format(Locale.US, "%.1f", distance)
        val xBucket = max(0, centerX) / POSITION_BUCKET_PX
        val yBucket = max(0, centerY) / POSITION_BUCKET_PX

        return "$windowType|$normalizedViewId|$normalizedLabel|$fareBucket|$distanceBucket|$xBucket|$yBucket"
    }
}
