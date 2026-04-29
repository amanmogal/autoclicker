package com.autoride.accepter

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

abstract class AccessControlledActivity : AppCompatActivity() {
    protected val authManager: AuthManager by lazy { AuthManager(this) }

    private val accessMonitorHandler = Handler(Looper.getMainLooper())
    private val accessMonitorRunnable = object : Runnable {
        override fun run() {
            if (enforceSessionAndAccess()) {
                accessMonitorHandler.postDelayed(this, ACCESS_MONITOR_INTERVAL_MS)
            }
        }
    }

    final override fun onResume() {
        super.onResume()
        accessMonitorHandler.removeCallbacks(accessMonitorRunnable)
        if (enforceSessionAndAccess()) {
            accessMonitorHandler.postDelayed(accessMonitorRunnable, ACCESS_MONITOR_INTERVAL_MS)
        }
    }

    final override fun onPause() {
        accessMonitorHandler.removeCallbacks(accessMonitorRunnable)
        super.onPause()
    }

    private fun enforceSessionAndAccess(): Boolean {
        if (!authManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return false
        }

        val snapshot = authManager.currentAccessSnapshot()
        if (snapshot.state == AppAccessState.UNKNOWN) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return false
        }
        if (!snapshot.hasActiveAccess) {
            AppAccessController.lockAndRedirect(this, authManager, snapshot)
            return false
        }

        onAccessGranted(snapshot)
        return true
    }

    protected abstract fun onAccessGranted(snapshot: AppAccessSnapshot)

    private companion object {
        private const val ACCESS_MONITOR_INTERVAL_MS = 1000L
    }
}
