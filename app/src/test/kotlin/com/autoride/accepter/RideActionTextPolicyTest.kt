package com.autoride.accepter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideActionTextPolicyTest {
    @Test
    fun isAcceptText_allowsOfferCardDescriptionWithPickupAndDrop() {
        assertTrue(
            RideActionTextPolicy.isAcceptText("Accept ride, pickup 2 km, drop 5 km")
        )
    }

    @Test
    fun isAcceptText_rejectsPickupOnlyDescription() {
        assertFalse(
            RideActionTextPolicy.isAcceptText("Pickup 2 km, drop 5 km")
        )
    }

    @Test
    fun isAcceptText_rejectsBlockedNavigationActionsEvenWithNearbyAcceptText() {
        assertFalse(
            RideActionTextPolicy.isAcceptText("Start ride after accept")
        )
    }
}
