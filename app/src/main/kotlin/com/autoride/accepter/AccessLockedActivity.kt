package com.autoride.accepter

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AccessLockedActivity : AppCompatActivity() {
    private lateinit var authManager: AuthManager
    private lateinit var supabaseAuthClient: SupabaseAuthClient

    private lateinit var lockTitleLabel: TextView
    private lateinit var lockMessageLabel: TextView
    private lateinit var lockDetailsLabel: TextView
    private lateinit var refreshButton: Button
    private lateinit var signOutButton: Button

    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_access_locked)

        authManager = AuthManager(this)
        supabaseAuthClient = SupabaseAuthClient()

        lockTitleLabel = findViewById(R.id.lockTitleLabel)
        lockMessageLabel = findViewById(R.id.lockMessageLabel)
        lockDetailsLabel = findViewById(R.id.lockDetailsLabel)
        refreshButton = findViewById(R.id.refreshAccessButton)
        signOutButton = findViewById(R.id.signOutButton)

        refreshButton.setOnClickListener { refreshAccessState() }
        signOutButton.setOnClickListener {
            authManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!authManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val snapshot = authManager.currentAccessSnapshot()
        if (snapshot.hasActiveAccess) {
            openOperations()
            return
        }
        renderLockedState(snapshot)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdownNow()
    }

    private fun renderLockedState(snapshot: AppAccessSnapshot) {
        val (titleRes, messageRes) = when (snapshot.state) {
            AppAccessState.PENDING_APPROVAL -> {
                R.string.lock_pending_title to R.string.lock_pending_message
            }
            AppAccessState.EXPIRED -> {
                R.string.lock_expired_title to R.string.lock_expired_message
            }
            AppAccessState.REVOKED -> {
                R.string.lock_revoked_title to R.string.lock_revoked_message
            }
            else -> {
                R.string.lock_unknown_title to R.string.lock_unknown_message
            }
        }

        lockTitleLabel.text = getString(titleRes)
        lockMessageLabel.text = getString(messageRes)

        val detail = when {
            snapshot.subscriptionExpiresAtMs > 0L -> {
                getString(
                    R.string.lock_detail_expires_at,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(snapshot.subscriptionExpiresAtMs))
                )
            }
            snapshot.lastAdminActionAtMs > 0L -> {
                getString(
                    R.string.lock_detail_last_admin_action,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(snapshot.lastAdminActionAtMs))
                )
            }
            else -> getString(R.string.lock_contact_admin_hint)
        }
        lockDetailsLabel.text = detail
    }

    private fun refreshAccessState() {
        val accessToken = authManager.currentAccessToken()
        if (accessToken.isNullOrBlank()) {
            authManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        refreshButton.isEnabled = false
        networkExecutor.execute {
            val result = supabaseAuthClient.refreshCurrentAccess(accessToken)
            result.accessSnapshot?.let { authManager.saveAccessSnapshot(it) }

            runOnUiThread {
                refreshButton.isEnabled = true

                val snapshot = authManager.currentAccessSnapshot()
                if (snapshot.hasActiveAccess) {
                    Toast.makeText(this, R.string.lock_refresh_granted, Toast.LENGTH_SHORT).show()
                    openOperations()
                    return@runOnUiThread
                }

                renderLockedState(snapshot)
                val message = result.errorMessage ?: getString(R.string.lock_refresh_still_blocked)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openOperations() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
