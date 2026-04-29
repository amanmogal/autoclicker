package com.autoride.accepter

import android.view.accessibility.AccessibilityEvent

object RideEventScanPolicy {
    fun shouldProcessEvent(
        eventType: Int,
        isRideAppEvent: Boolean,
        hasVisibleRideWindow: Boolean,
        eventLooksLikeRideOffer: Boolean
    ): Boolean {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return true
        }

        return isRideAppEvent || hasVisibleRideWindow || eventLooksLikeRideOffer
    }
}
