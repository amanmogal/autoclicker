package com.autoride.accepter

enum class AppAccessState {
    ACTIVE,
    PENDING_APPROVAL,
    EXPIRED,
    REVOKED,
    UNKNOWN
}

data class AppAccessSnapshot(
    val approved: Boolean,
    val approvalStatus: String,
    val subscriptionStatus: String,
    val subscriptionStartedAt: String?,
    val subscriptionExpiresAt: String?,
    val subscriptionStartedAtMs: Long,
    val subscriptionExpiresAtMs: Long,
    val lastAdminActionAt: String?,
    val lastAdminActionAtMs: Long,
    val state: AppAccessState
) {
    val hasActiveAccess: Boolean
        get() = state == AppAccessState.ACTIVE
}
