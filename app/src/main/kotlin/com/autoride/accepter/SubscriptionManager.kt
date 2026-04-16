package com.autoride.accepter

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SubscriptionManager {

    private const val PREFS = "RideFilters"

    fun hasValidEntitlement(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasToken = prefs.getString("auth_token", null).isNullOrBlank().not()
        val active = prefs.getBoolean("subscription_active", false)
        val expiry = prefs.getString("subscription_expires_at", null)
        return hasToken && active && isExpiryValid(expiry)
    }

    fun enforceInactiveState(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("subscription_active", false)
            .putBoolean("serviceEnabled", false)
            .putBoolean("service_enabled", false)
            .apply()
    }

    fun isExpiryValid(expiryIso: String?): Boolean {
        if (expiryIso.isNullOrBlank() || expiryIso.equals("N/A", ignoreCase = true)) return false
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val expiryDate = runCatching { parser.parse(expiryIso) }.getOrNull() ?: return false
        return Date().before(expiryDate)
    }
}
