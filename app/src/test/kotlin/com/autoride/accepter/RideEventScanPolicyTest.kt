package com.autoride.accepter

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideEventScanPolicyTest {
    @Test
    fun shouldProcessEvent_allowsSparseWindowChangesForImmediateOverlayScan() {
        assertTrue(
            RideEventScanPolicy.shouldProcessEvent(
                eventType = AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                isRideAppEvent = false,
                hasVisibleRideWindow = false,
                eventLooksLikeRideOffer = false
            )
        )
    }

    @Test
    fun shouldProcessEvent_rejectsUnrelatedContentChangesWithoutRideSignals() {
        assertFalse(
            RideEventScanPolicy.shouldProcessEvent(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                isRideAppEvent = false,
                hasVisibleRideWindow = false,
                eventLooksLikeRideOffer = false
            )
        )
    }
}
