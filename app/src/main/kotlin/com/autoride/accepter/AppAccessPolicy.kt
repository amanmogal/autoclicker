package com.autoride.accepter

object AppAccessPolicy {
    const val LOCAL_USER_EMAIL = "local-driver@bhada.local"

    private const val LOCAL_ACCESS_STARTED_AT = "2026-01-01T00:00:00Z"
    private const val LOCAL_ACCESS_EXPIRES_AT = "2099-12-31T23:59:59Z"
    private const val LOCAL_ACCESS_STARTED_AT_MS = 1767225600000L
    private const val LOCAL_ACCESS_EXPIRES_AT_MS = 4102444799000L

    fun isSupabaseConfigured(url: String, anonKey: String): Boolean {
        val normalizedUrl = url.trim()
        val normalizedKey = anonKey.trim()
        if (!normalizedUrl.startsWith("http")) return false
        if (normalizedKey.isBlank()) return false
        if (normalizedUrl.startsWith("https://YOUR", ignoreCase = true)) return false
        if (normalizedKey.startsWith("YOUR_", ignoreCase = true)) return false
        return true
    }

    fun isLocalAccessAllowed(isDebug: Boolean, localAccessMode: Boolean): Boolean {
        return isDebug && localAccessMode
    }

    fun isLocalAccessMode(): Boolean {
        return isLocalAccessAllowed(BuildConfig.DEBUG, BuildConfig.LOCAL_ACCESS_MODE)
    }

    fun localAccessSnapshot(): AppAccessSnapshot {
        return AppAccessSnapshot(
            approved = true,
            approvalStatus = "approved",
            subscriptionStatus = "active",
            subscriptionStartedAt = LOCAL_ACCESS_STARTED_AT,
            subscriptionExpiresAt = LOCAL_ACCESS_EXPIRES_AT,
            subscriptionStartedAtMs = LOCAL_ACCESS_STARTED_AT_MS,
            subscriptionExpiresAtMs = LOCAL_ACCESS_EXPIRES_AT_MS,
            lastAdminActionAt = LOCAL_ACCESS_STARTED_AT,
            lastAdminActionAtMs = LOCAL_ACCESS_STARTED_AT_MS,
            state = AppAccessState.ACTIVE
        )
    }
}
