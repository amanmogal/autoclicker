package com.autoride.accepter

import android.util.Log

/**
 * FilterEngine handles the business logic for deciding whether to accept a ride.
 * It compares extracted screen data against user-defined thresholds.
 */
class FilterEngine {

    companion object {
        private const val TAG = "FilterEngine"
        private const val LOG_THROTTLE_MS = 1500L
    }

    // User settings
    var minFare: Double = 0.0
    var maxFare: Double = Double.MAX_VALUE
    var minDistance: Double = 0.0
    var maxDistance: Double = Double.MAX_VALUE
    var minFarePerKm: Double = 0.0
    var allowFareOnlyWhenDistanceMissing: Boolean = true
    private var lastDecisionLogAtMs: Long = 0L
    private var lastDecisionSnapshot: String? = null

    /**
     * Decides whether to accept a ride based on fare and distance.
     */
    fun shouldAccept(fare: Double, distance: Double): Boolean {
        val inFareRange = fare in minFare..maxFare
        if (!inFareRange) {
            maybeLogDecision(fare, distance, 0.0, false, "fare-range")
            return false
        }

        if (distance <= 0.0) {
            val decision = allowFareOnlyWhenDistanceMissing
            maybeLogDecision(fare, distance, 0.0, decision, "distance-missing")
            return decision
        }

        val farePerKm = fare / distance
        val inDistanceRange = distance in minDistance..maxDistance
        val meetsFarePerKm = farePerKm >= minFarePerKm
        val decision = inDistanceRange && meetsFarePerKm
        maybeLogDecision(fare, distance, farePerKm, decision, if (inDistanceRange) "fare-per-km" else "distance-range")
        return decision
    }

    private fun maybeLogDecision(
        fare: Double,
        distance: Double,
        farePerKm: Double,
        decision: Boolean,
        reason: String
    ) {
        val now = System.currentTimeMillis()
        val snapshot = "fare=$fare|distance=$distance|fpk=$farePerKm|decision=$decision|reason=$reason"
        if (snapshot != lastDecisionSnapshot || now - lastDecisionLogAtMs >= LOG_THROTTLE_MS) {
            lastDecisionSnapshot = snapshot
            lastDecisionLogAtMs = now
            Log.d(TAG, "Filtering -> $snapshot")
        }
    }

    /**
     * Extracts numerical value from a string (e.g., "₹150.50" -> 150.50)
     */
    fun parseAmount(text: String): Double {
        if (text.isBlank()) return 0.0
        return try {
            // Standard regex to find prices and distances
            val match = Regex("[0-9]+([.,][0-9]+)?").find(text)
            match?.value?.replace(",", ".")?.toDouble() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }
}
