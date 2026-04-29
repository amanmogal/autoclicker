package com.autoride.accepter

object RideOfferSignalPolicy {
    fun isLikelyRideOffer(
        hasAcceptAction: Boolean,
        hasCurrency: Boolean,
        hasDistance: Boolean
    ): Boolean {
        if (hasCurrency && (hasAcceptAction || hasDistance)) return true
        return hasAcceptAction && hasDistance
    }
}
