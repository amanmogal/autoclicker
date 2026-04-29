package com.autoride.accepter

object RideOfferSignalPolicy {
    fun isLikelyRideOffer(
        hasAcceptAction: Boolean,
        hasCurrency: Boolean,
        hasDistance: Boolean
    ): Boolean {
        return hasCurrency && (hasAcceptAction || hasDistance)
    }
}
