package com.autoride.accepter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    @Test
    fun `distance missing can be accepted when configured`() {
        val engine = FilterEngine().apply {
            minFare = 30.0
            maxFare = 500.0
            allowFareOnlyWhenDistanceMissing = true
        }

        assertTrue(engine.shouldAccept(fare = 100.0, distance = 0.0))
    }

    @Test
    fun `distance missing is rejected when fare-only disabled`() {
        val engine = FilterEngine().apply {
            minFare = 30.0
            maxFare = 500.0
            allowFareOnlyWhenDistanceMissing = false
        }

        assertFalse(engine.shouldAccept(fare = 100.0, distance = 0.0))
    }

    @Test
    fun `accepts when fare, distance and fare-per-km constraints pass`() {
        val engine = FilterEngine().apply {
            minFare = 30.0
            maxFare = 500.0
            minDistance = 0.5
            maxDistance = 5.0
            minFarePerKm = 20.0
        }

        assertTrue(engine.shouldAccept(fare = 120.0, distance = 3.0))
    }

    @Test
    fun `rejects when distance is out of range`() {
        val engine = FilterEngine().apply {
            minFare = 30.0
            maxFare = 500.0
            minDistance = 0.5
            maxDistance = 3.0
            minFarePerKm = 10.0
        }

        assertFalse(engine.shouldAccept(fare = 120.0, distance = 3.5))
    }
}
