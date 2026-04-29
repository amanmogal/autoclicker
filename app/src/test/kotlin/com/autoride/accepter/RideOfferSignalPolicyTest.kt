package com.autoride.accepter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideOfferSignalPolicyTest {
    @Test
    fun isLikelyRideOffer_acceptAndCurrency() {
        assertTrue(
            RideOfferSignalPolicy.isLikelyRideOffer(
                hasAcceptAction = true,
                hasCurrency = true,
                hasDistance = false
            )
        )
    }

    @Test
    fun isLikelyRideOffer_currencyAndDistance() {
        assertTrue(
            RideOfferSignalPolicy.isLikelyRideOffer(
                hasAcceptAction = false,
                hasCurrency = true,
                hasDistance = true
            )
        )
    }

    @Test
    fun isLikelyRideOffer_rejectsCurrencyOnly() {
        assertFalse(
            RideOfferSignalPolicy.isLikelyRideOffer(
                hasAcceptAction = false,
                hasCurrency = true,
                hasDistance = false
            )
        )
    }

    @Test
    fun isLikelyRideOffer_acceptAndDistanceWithoutFare() {
        assertTrue(
            RideOfferSignalPolicy.isLikelyRideOffer(
                hasAcceptAction = true,
                hasCurrency = false,
                hasDistance = true
            )
        )
    }

    @Test
    fun isLikelyRideOffer_rejectsAcceptOnly() {
        assertFalse(
            RideOfferSignalPolicy.isLikelyRideOffer(
                hasAcceptAction = true,
                hasCurrency = false,
                hasDistance = false
            )
        )
    }
}
