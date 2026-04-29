package com.autoride.accepter

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val AUTH_PREFS = "AuthPrefs"
private const val KEY_AUTH_EMAIL = "authEmail"
private const val KEY_AUTH_ACCESS_TOKEN = "authAccessToken"
private const val KEY_AUTH_REFRESH_TOKEN = "authRefreshToken"
private const val KEY_AUTH_TOKEN_LEGACY = "authToken"
private const val KEY_APPROVED = "approved"
private const val KEY_APPROVAL_STATUS = "approvalStatus"
private const val KEY_SUBSCRIPTION_STATUS = "subscriptionStatus"
private const val KEY_SUBSCRIPTION_STARTED_AT = "subscriptionStartedAt"
private const val KEY_SUBSCRIPTION_EXPIRES_AT = "subscriptionExpiresAt"
private const val KEY_LAST_ADMIN_ACTION_AT = "lastAdminActionAt"

class AuthManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean {
        val email = prefs.getString(KEY_AUTH_EMAIL, null)
        val accessToken = prefs.getString(KEY_AUTH_ACCESS_TOKEN, null)
            ?: prefs.getString(KEY_AUTH_TOKEN_LEGACY, null)
        return !email.isNullOrBlank() && !accessToken.isNullOrBlank()
    }

    fun currentUserEmail(): String {
        return prefs.getString(KEY_AUTH_EMAIL, null) ?: ""
    }

    fun currentAccessToken(): String? {
        return prefs.getString(KEY_AUTH_ACCESS_TOKEN, null)
            ?: prefs.getString(KEY_AUTH_TOKEN_LEGACY, null)
    }

    fun saveSession(session: SupabaseSession, accessSnapshot: AppAccessSnapshot? = null) {
        val editor = prefs.edit()
            .putString(KEY_AUTH_EMAIL, session.email.trim())
            .putString(KEY_AUTH_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_AUTH_REFRESH_TOKEN, session.refreshToken)
            .remove(KEY_AUTH_TOKEN_LEGACY)
        if (accessSnapshot != null) {
            writeAccessSnapshot(editor, accessSnapshot)
        }
        editor.apply()
    }

    fun saveAccessSnapshot(snapshot: AppAccessSnapshot) {
        prefs.edit()
            .also { writeAccessSnapshot(it, snapshot) }
            .apply()
    }

    fun hasStoredAccessSnapshot(): Boolean {
        return prefs.contains(KEY_APPROVAL_STATUS) ||
            prefs.contains(KEY_SUBSCRIPTION_STATUS) ||
            prefs.contains(KEY_SUBSCRIPTION_EXPIRES_AT)
    }

    fun hasActiveAccess(nowMs: Long = System.currentTimeMillis()): Boolean {
        return currentAccessSnapshot(nowMs).hasActiveAccess
    }

    fun currentAccessSnapshot(nowMs: Long = System.currentTimeMillis()): AppAccessSnapshot {
        val approved = prefs.getBoolean(KEY_APPROVED, false)
        val approvalStatus = prefs.getString(KEY_APPROVAL_STATUS, null).orEmpty()
        val subscriptionStatus = prefs.getString(KEY_SUBSCRIPTION_STATUS, null).orEmpty()
        val subscriptionStartedAt = prefs.getString(KEY_SUBSCRIPTION_STARTED_AT, null)
        val subscriptionExpiresAt = prefs.getString(KEY_SUBSCRIPTION_EXPIRES_AT, null)
        val lastAdminActionAt = prefs.getString(KEY_LAST_ADMIN_ACTION_AT, null)

        val snapshot = AppAccessSnapshot(
            approved = approved,
            approvalStatus = approvalStatus,
            subscriptionStatus = subscriptionStatus,
            subscriptionStartedAt = subscriptionStartedAt,
            subscriptionExpiresAt = subscriptionExpiresAt,
            subscriptionStartedAtMs = parseIsoToMillis(subscriptionStartedAt),
            subscriptionExpiresAtMs = parseIsoToMillis(subscriptionExpiresAt),
            lastAdminActionAt = lastAdminActionAt,
            lastAdminActionAtMs = parseIsoToMillis(lastAdminActionAt),
            state = AppAccessState.UNKNOWN
        )

        val normalizedApproval = snapshot.approvalStatus.trim().lowercase(Locale.US)
        val normalizedSubscription = snapshot.subscriptionStatus.trim().lowercase(Locale.US)
        val state = when {
            normalizedApproval.isBlank() && normalizedSubscription.isBlank() && !snapshot.approved -> AppAccessState.UNKNOWN
            normalizedApproval in setOf("rejected", "revoked") || normalizedSubscription == "revoked" -> AppAccessState.REVOKED
            !snapshot.approved || normalizedApproval in setOf("", "pending") -> AppAccessState.PENDING_APPROVAL
            snapshot.subscriptionExpiresAtMs in 1..nowMs -> AppAccessState.EXPIRED
            normalizedSubscription == "active" && snapshot.subscriptionExpiresAtMs > nowMs -> AppAccessState.ACTIVE
            snapshot.approved && snapshot.subscriptionExpiresAtMs <= 0L -> AppAccessState.EXPIRED
            snapshot.approved && snapshot.subscriptionExpiresAtMs > nowMs -> AppAccessState.ACTIVE
            else -> AppAccessState.EXPIRED
        }

        return snapshot.copy(state = state)
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_AUTH_EMAIL)
            .remove(KEY_AUTH_ACCESS_TOKEN)
            .remove(KEY_AUTH_REFRESH_TOKEN)
            .remove(KEY_AUTH_TOKEN_LEGACY)
            .remove(KEY_APPROVED)
            .remove(KEY_APPROVAL_STATUS)
            .remove(KEY_SUBSCRIPTION_STATUS)
            .remove(KEY_SUBSCRIPTION_STARTED_AT)
            .remove(KEY_SUBSCRIPTION_EXPIRES_AT)
            .remove(KEY_LAST_ADMIN_ACTION_AT)
            .apply()
    }

    private fun writeAccessSnapshot(
        editor: SharedPreferences.Editor,
        snapshot: AppAccessSnapshot
    ) {
        editor.putBoolean(KEY_APPROVED, snapshot.approved)
            .putString(KEY_APPROVAL_STATUS, snapshot.approvalStatus)
            .putString(KEY_SUBSCRIPTION_STATUS, snapshot.subscriptionStatus)
            .putString(KEY_SUBSCRIPTION_STARTED_AT, snapshot.subscriptionStartedAt)
            .putString(KEY_SUBSCRIPTION_EXPIRES_AT, snapshot.subscriptionExpiresAt)
            .putString(KEY_LAST_ADMIN_ACTION_AT, snapshot.lastAdminActionAt)
    }

    private fun parseIsoToMillis(rawValue: String?): Long {
        if (rawValue.isNullOrBlank()) return 0L

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val parsed = runCatching { formatter.parse(rawValue)?.time ?: 0L }.getOrDefault(0L)
            if (parsed > 0L) {
                return parsed
            }
        }
        return 0L
    }
}
