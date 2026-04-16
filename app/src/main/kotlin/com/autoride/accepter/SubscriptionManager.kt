package com.autoride.accepter

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.util.UUID

private const val BILLING_PREFS = "BillingPrefs"
private const val KEY_SUBSCRIPTION_EXPIRY_MS = "subscriptionExpiryMs"
private const val KEY_SUBSCRIPTION_PLAN = "subscriptionPlan"
private const val KEY_PENDING_PLAN = "pendingPlan"
private const val KEY_PENDING_TRANSACTION_ID = "pendingTransactionId"
private const val KEY_PENDING_AT_MS = "pendingAtMs"

enum class SubscriptionPlan(val label: String, val priceInr: String, val durationDays: Int) {
    MONTHLY("Monthly Pro", "199.00", 30),
    YEARLY("Yearly Pro", "1999.00", 365)
}

data class UpiPaymentRequest(
    val plan: SubscriptionPlan,
    val transactionId: String,
    val uri: Uri
)

data class BillingState(
    val isActive: Boolean,
    val activePlanName: String?,
    val activeUntilMs: Long,
    val pendingPlanName: String?,
    val pendingTransactionId: String?
)

class SubscriptionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(BILLING_PREFS, Context.MODE_PRIVATE)

    fun isSubscriptionActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        val expiry = prefs.getLong(KEY_SUBSCRIPTION_EXPIRY_MS, 0L)
        return expiry > nowMs
    }

    fun activeUntilMs(): Long {
        return prefs.getLong(KEY_SUBSCRIPTION_EXPIRY_MS, 0L)
    }

    fun activePlanName(): String {
        return prefs.getString(KEY_SUBSCRIPTION_PLAN, null) ?: "No plan"
    }

    fun activate(plan: SubscriptionPlan, nowMs: Long = System.currentTimeMillis()) {
        val newExpiry = nowMs + (plan.durationDays * 24L * 60L * 60L * 1000L)
        prefs.edit()
            .putLong(KEY_SUBSCRIPTION_EXPIRY_MS, newExpiry)
            .putString(KEY_SUBSCRIPTION_PLAN, plan.label)
            .remove(KEY_PENDING_PLAN)
            .remove(KEY_PENDING_TRANSACTION_ID)
            .remove(KEY_PENDING_AT_MS)
            .apply()
    }

    fun createUpiRequest(plan: SubscriptionPlan): UpiPaymentRequest {
        val transactionId = "ARA-${UUID.randomUUID().toString().take(8)}"
        val note = "Auto Ride Accepter ${plan.label}"
        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", BuildConfig.UPI_PAYEE_VPA)
            .appendQueryParameter("pn", BuildConfig.UPI_PAYEE_NAME)
            .appendQueryParameter("tr", transactionId)
            .appendQueryParameter("tn", note)
            .appendQueryParameter("am", plan.priceInr)
            .appendQueryParameter("cu", "INR")
            .build()

        return UpiPaymentRequest(
            plan = plan,
            transactionId = transactionId,
            uri = uri
        )
    }

    fun markPaymentPending(plan: SubscriptionPlan, transactionId: String, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_PENDING_PLAN, plan.label)
            .putString(KEY_PENDING_TRANSACTION_ID, transactionId)
            .putLong(KEY_PENDING_AT_MS, nowMs)
            .apply()
    }

    fun billingState(nowMs: Long = System.currentTimeMillis()): BillingState {
        val isActive = isSubscriptionActive(nowMs)
        return BillingState(
            isActive = isActive,
            activePlanName = if (isActive) activePlanName() else null,
            activeUntilMs = if (isActive) activeUntilMs() else 0L,
            pendingPlanName = prefs.getString(KEY_PENDING_PLAN, null),
            pendingTransactionId = prefs.getString(KEY_PENDING_TRANSACTION_ID, null)
        )
    }

    fun isUpiSuccessResponse(response: String?): Boolean {
        if (response.isNullOrBlank()) {
            return false
        }
        return response
            .split('&')
            .any { part ->
                val tokens = part.split('=', limit = 2)
                tokens.size == 2 && tokens[0].equals("status", ignoreCase = true) &&
                    tokens[1].equals("success", ignoreCase = true)
            }
    }
}
