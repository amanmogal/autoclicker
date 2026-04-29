package com.autoride.accepter

import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideWindowPolicyTest {

    @Test
    fun shouldMatchInstalledDriverPackages() {
        assertTrue(RideWindowPolicy.isRideApp("com.rapido.rider"))
        assertTrue(RideWindowPolicy.isRideApp("com.olacabs.oladriver"))
    }

    @Test
    fun shouldRejectConsumerRidePackages() {
        assertFalse(RideWindowPolicy.isRideApp("com.rapido.customer"))
        assertFalse(RideWindowPolicy.isRideApp("com.uber.passenger"))
    }

    @Test
    fun shouldScanSystemHostedOfferWindow() {
        assertTrue(
            RideWindowPolicy.shouldScanWindow(
                windowType = AccessibilityWindowInfo.TYPE_SYSTEM,
                rootPackage = "android",
                appPackage = "com.autoride.accepter",
                rootHasOfferSignals = true
            )
        )
    }

    @Test
    fun shouldScanSystemHostedAcceptDistanceWindowWithoutCurrencySignal() {
        assertTrue(
            RideWindowPolicy.shouldScanWindow(
                windowType = AccessibilityWindowInfo.TYPE_SYSTEM,
                rootPackage = "android",
                appPackage = "com.autoride.accepter",
                rootHasOfferSignals = RideOfferSignalPolicy.isLikelyRideOffer(
                    hasAcceptAction = true,
                    hasCurrency = false,
                    hasDistance = true
                )
            )
        )
    }

    @Test
    fun shouldScanVendorApplicationOfferWindowEvenWithoutDriverRole() {
        assertTrue(
            RideWindowPolicy.shouldScanWindow(
                windowType = AccessibilityWindowInfo.TYPE_APPLICATION,
                rootPackage = "com.rapido.overlay",
                appPackage = "com.autoride.accepter",
                rootHasOfferSignals = true
            )
        )
    }

    @Test
    fun shouldScanNonOwnAccessibilityOverlayWithOfferSignals() {
        assertTrue(
            RideWindowPolicy.shouldScanWindow(
                windowType = AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                rootPackage = "com.rapido.rider",
                appPackage = "com.autoride.accepter",
                rootHasOfferSignals = true
            )
        )
    }

    @Test
    fun shouldNotScanOwnOverlayWindow() {
        assertFalse(
            RideWindowPolicy.shouldScanWindow(
                windowType = AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                rootPackage = "com.autoride.accepter",
                appPackage = "com.autoride.accepter",
                rootHasOfferSignals = true
            )
        )
    }
}
