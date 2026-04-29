package com.autoride.accepter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    @Test
    fun shouldReject_whenFareOutOfRange() {
        val engine = FilterEngine().apply {
            minFare = 100.0
            maxFare = 300.0
        }

        assertFalse(engine.shouldAccept(fare = 90.0, distance = 2.0))
        assertFalse(engine.shouldAccept(fare = 301.0, distance = 2.0))
    }

    @Test
    fun shouldAccept_whenDistanceMissingAndEnabled() {
        val engine = FilterEngine().apply {
            minFare = 100.0
            maxFare = 300.0
            allowFareOnlyWhenDistanceMissing = true
        }

        assertTrue(engine.shouldAccept(fare = 150.0, distance = 0.0))
    }

    @Test
    fun shouldReject_whenFarePerKmBelowThreshold() {
        val engine = FilterEngine().apply {
            minFare = 100.0
            maxFare = 1000.0
            minDistance = 1.0
            maxDistance = 10.0
            minFarePerKm = 25.0
        }

        assertFalse(engine.shouldAccept(fare = 100.0, distance = 5.0))
    }

    @Test
    fun parseAmount_shouldHandleCurrencyAndDecimal() {
        val engine = FilterEngine()

        assertEquals(150.5, engine.parseAmount("₹150.5"), 0.001)
        assertEquals(2.75, engine.parseAmount("Distance 2,75 km"), 0.001)
    }

    @Test
    fun parseDistanceKm_shouldKeepWholeNumberAndDecimalDistances() {
        val engine = FilterEngine()

        assertEquals(2.0, engine.parseDistanceKm("Pickup 2 km"), 0.001)
        assertEquals(2.5, engine.parseDistanceKm("Pickup 2.5 km"), 0.001)
        assertEquals(0.5, engine.parseDistanceKm("Pickup 500 meter"), 0.001)
    }

    @Test
    fun rankScore_shouldPrioritizeHigherFarePerKm() {
        val engine = FilterEngine()

        val higherAbsoluteFare = engine.rankScore(fare = 250.0, distance = 10.0)
        val betterFarePerKm = engine.rankScore(fare = 180.0, distance = 4.0)

        assertTrue(betterFarePerKm > higherAbsoluteFare)
    }

    @Test
    fun rankScore_shouldRankKnownDistanceAboveSimilarFareWithoutDistance() {
        val engine = FilterEngine()

        val unknownDistance = engine.rankScore(fare = 250.0, distance = 0.0)
        val knownDistance = engine.rankScore(fare = 240.0, distance = 4.0)

        assertTrue(knownDistance > unknownDistance)
    }
}
