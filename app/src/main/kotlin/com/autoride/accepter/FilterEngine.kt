package com.autoride.accepter

/**
 * FilterEngine handles the business logic for deciding whether to accept a ride.
 * It compares extracted screen data against user-defined thresholds.
 */
class FilterEngine {

    companion object {
        private const val TAG = "FilterEngine"
        private const val LOG_THROTTLE_MS = 1500L
        private val DISTANCE_TOKEN_REGEX = Regex(
            """(\d+(?:[.,]\d+)?)\s*(km|kilometer|kilometre|किमी|ಕಿ\.?ಮೀ|meter|metre|mtr|मीटर|ಮೀಟರ್)\b""",
            RegexOption.IGNORE_CASE
        )
        private val METER_DISTANCE_TOKEN_REGEX = Regex(
            """(\d+(?:[.,]\d+)?)\s*(meter|metre|mtr|मीटर|ಮೀಟರ್)\b""",
            RegexOption.IGNORE_CASE
        )
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

    fun farePerKm(fare: Double, distance: Double): Double {
        if (fare <= 0.0 || distance <= 0.0) return 0.0
        return fare / distance
    }

    /**
     * Scores rides for prioritization when multiple offers are visible.
     * Higher score means better earning potential while still rewarding absolute fare.
     */
    fun rankScore(fare: Double, distance: Double): Double {
        val farePerKmScore = farePerKm(fare, distance)
        return if (distance > 0.0) {
            (farePerKmScore * 100.0) + fare
        } else {
            // Unknown distance offers are allowed, but ranked lower by default.
            fare * 0.2
        }
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
            println("Filtering -> $snapshot")
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

    fun parseDistanceKm(text: String): Double {
        if (text.isBlank()) return 0.0

        val normalized = text.lowercase()
        val tokenMatch = DISTANCE_TOKEN_REGEX.find(normalized)
        if (tokenMatch != null) {
            val raw = tokenMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            if (raw <= 0.0) return 0.0
            val unit = tokenMatch.groupValues[2].lowercase()
            return if (unit.startsWith("m") || unit.contains("मीटर") || unit.contains("ಮೀಟರ್")) {
                raw / 1000.0
            } else {
                raw
            }
        }

        val fallback = parseAmount(normalized)
        if (fallback <= 0.0) return 0.0
        return if (METER_DISTANCE_TOKEN_REGEX.containsMatchIn(normalized)) fallback / 1000.0 else fallback
    }
}
