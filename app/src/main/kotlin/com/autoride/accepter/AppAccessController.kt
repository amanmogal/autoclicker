package com.autoride.accepter

import android.app.Activity
import android.content.Context
import android.content.Intent

object AppAccessController {
    fun disableAutomation(context: Context) {
        context.getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("serviceEnabled", false)
            .putBoolean("service_enabled", false)
            .apply()
    }

    fun lockAndRedirect(
        activity: Activity,
        authManager: AuthManager,
        snapshot: AppAccessSnapshot = authManager.currentAccessSnapshot()
    ) {
        disableAutomation(activity)
        authManager.saveAccessSnapshot(snapshot)
        activity.startActivity(
            Intent(activity, AccessLockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        activity.finish()
    }
}
