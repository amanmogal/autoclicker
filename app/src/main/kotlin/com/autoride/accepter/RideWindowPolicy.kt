package com.autoride.accepter

import android.view.accessibility.AccessibilityWindowInfo

object RideWindowPolicy {
    private val rideVendorTokens = setOf("ola", "rapido", "uber", "namma", "yatri")
    private val driverRoleTokens = setOf("driver", "rider", "captain", "partner", "sathi", "pilot")
    private val blockedRoleTokens = setOf("customer", "passenger", "consumer", "client", "rental")

    fun isRideApp(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        val segments = normalized.split('.')
        val hasVendor = segments.any { segment ->
            rideVendorTokens.any { vendor -> segment.startsWith(vendor) }
        }
        val hasDriverRole = segments.any { segment ->
            driverRoleTokens.any { role -> segment.contains(role) }
        }
        val hasBlockedRole = segments.any { segment ->
            blockedRoleTokens.any { blocked -> segment.contains(blocked) }
        }

        return hasVendor && hasDriverRole && !hasBlockedRole
    }

    fun isSystemOverlayHostPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return normalized.isBlank() ||
            normalized == "android" ||
            normalized.contains("systemui")
    }

    fun shouldScanWindow(
        windowType: Int,
        rootPackage: String,
        appPackage: String,
        rootHasOfferSignals: Boolean
    ): Boolean {
        if (windowType != AccessibilityWindowInfo.TYPE_APPLICATION &&
            windowType != AccessibilityWindowInfo.TYPE_SYSTEM &&
            windowType != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
        ) {
            return false
        }

        if (isRideApp(rootPackage)) return true
        if (!rootHasOfferSignals) return false
        if (rootPackage == appPackage) return false

        if (windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return true

        return windowType == AccessibilityWindowInfo.TYPE_SYSTEM ||
            isSystemOverlayHostPackage(rootPackage) ||
            hasRideVendorToken(rootPackage)
    }

    private fun hasRideVendorToken(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        val segments = normalized.split('.')
        return segments.any { segment ->
            rideVendorTokens.any { vendor -> segment.startsWith(vendor) }
        }
    }
}
