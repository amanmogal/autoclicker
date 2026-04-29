package com.autoride.accepter

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    private lateinit var loginEmailInput: TextInputEditText
    private lateinit var loginPasswordInput: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var signupButton: Button
    private lateinit var openAppButton: Button
    private lateinit var logoutButton: Button
    private lateinit var forgotPasswordLink: TextView
    private lateinit var loginStatusLabel: TextView
    private lateinit var loginSecondaryActionsRow: LinearLayout

    private lateinit var authManager: AuthManager
    private lateinit var supabaseAuthClient: SupabaseAuthClient
    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager(this)
        supabaseAuthClient = SupabaseAuthClient()

        loginEmailInput = findViewById(R.id.loginEmailInput)
        loginPasswordInput = findViewById(R.id.loginPasswordInput)
        loginButton = findViewById(R.id.loginButton)
        signupButton = findViewById(R.id.signupButton)
        openAppButton = findViewById(R.id.openAppButton)
        logoutButton = findViewById(R.id.logoutButton)
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink)
        loginStatusLabel = findViewById(R.id.loginStatusLabel)
        loginSecondaryActionsRow = findViewById(R.id.loginSecondaryActionsRow)

        refreshLoginStatus()

        loginButton.setOnClickListener { attemptSignIn() }
        signupButton.setOnClickListener { attemptSignUp() }
        forgotPasswordLink.setOnClickListener { attemptPasswordReset() }
        openAppButton.setOnClickListener { routeLoggedInUser() }
        logoutButton.setOnClickListener {
            authManager.logout()
            refreshLoginStatus()
            Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show()
        }

        if (authManager.isLoggedIn()) {
            syncExistingSessionAndRoute()
        }
    }

    override fun onResume() {
        super.onResume()
        if (authManager.isLoggedIn()) {
            routeLoggedInUser()
            return
        }
        refreshLoginStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdownNow()
    }

    private fun attemptSignIn() {
        val email = loginEmailInput.text?.toString()?.trim().orEmpty()
        val password = loginPasswordInput.text?.toString().orEmpty()
        performAuthRequest { supabaseAuthClient.signIn(email, password) }
    }

    private fun attemptSignUp() {
        val email = loginEmailInput.text?.toString()?.trim().orEmpty()
        val password = loginPasswordInput.text?.toString().orEmpty()
        performAuthRequest { supabaseAuthClient.signUp(email, password) }
    }

    private fun attemptPasswordReset() {
        val email = loginEmailInput.text?.toString()?.trim().orEmpty()
        if (!supabaseAuthClientConfigured()) {
            Toast.makeText(this, R.string.supabase_not_configured, Toast.LENGTH_LONG).show()
            return
        }

        setLoadingState(true)
        networkExecutor.execute {
            val result = supabaseAuthClient.requestPasswordReset(email)
            runOnUiThread {
                setLoadingState(false)
                if (result.isSuccess) {
                    Toast.makeText(this, R.string.password_reset_sent, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        this,
                        result.errorMessage ?: getString(R.string.password_reset_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun performAuthRequest(request: () -> SupabaseAuthResult) {
        val email = loginEmailInput.text?.toString()?.trim().orEmpty()
        if (!supabaseAuthClientConfigured() && authManager.currentAccessToken().isNullOrBlank()) {
            Toast.makeText(this, R.string.supabase_not_configured, Toast.LENGTH_LONG).show()
            return
        }

        setLoadingState(true)
        networkExecutor.execute {
            val result = request()
            runOnUiThread {
                setLoadingState(false)

                if (!result.isSuccess) {
                    Toast.makeText(this, result.errorMessage ?: getString(R.string.login_error), Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                val session = result.session
                if (session != null) {
                    authManager.saveSession(session, result.accessSnapshot)
                    loginPasswordInput.text = null
                    refreshLoginStatus()
                    Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()
                    routeLoggedInUser()
                    return@runOnUiThread
                }

                val info = result.infoMessage ?: getString(R.string.signup_success_verify_email)
                if (email.isNotBlank()) {
                    loginStatusLabel.text = getString(R.string.login_status_logged_out)
                }
                Toast.makeText(this, info, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncExistingSessionAndRoute() {
        val accessToken = authManager.currentAccessToken()
        if (accessToken.isNullOrBlank()) {
            authManager.logout()
            refreshLoginStatus()
            return
        }

        setLoadingState(true)
        networkExecutor.execute {
            val result = supabaseAuthClient.refreshCurrentAccess(accessToken)
            result.accessSnapshot?.let { authManager.saveAccessSnapshot(it) }

            runOnUiThread {
                setLoadingState(false)
                if (result.errorMessage != null && !authManager.hasStoredAccessSnapshot()) {
                    authManager.logout()
                    refreshLoginStatus()
                    Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                routeLoggedInUser()
            }
        }
    }

    private fun routeLoggedInUser() {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = authManager.currentAccessSnapshot()
        if (!snapshot.hasActiveAccess) {
            AppAccessController.lockAndRedirect(this, authManager, snapshot)
            return
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun supabaseAuthClientConfigured(): Boolean {
        return BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    }

    private fun setLoadingState(loading: Boolean) {
        loginButton.isEnabled = !loading
        signupButton.isEnabled = !loading
        openAppButton.isEnabled = !loading && authManager.isLoggedIn()
        logoutButton.isEnabled = !loading && authManager.isLoggedIn()
        forgotPasswordLink.isEnabled = !loading
    }

    private fun refreshLoginStatus() {
        if (authManager.isLoggedIn()) {
            loginStatusLabel.text = getString(R.string.login_status_logged_in, authManager.currentUserEmail())
            loginButton.isEnabled = true
            signupButton.isEnabled = true
            openAppButton.isEnabled = true
            logoutButton.isEnabled = true
            loginSecondaryActionsRow.visibility = View.VISIBLE
            return
        }
        loginStatusLabel.text = getString(R.string.login_status_logged_out)
        loginButton.isEnabled = true
        signupButton.isEnabled = true
        openAppButton.isEnabled = false
        logoutButton.isEnabled = false
        loginSecondaryActionsRow.visibility = View.GONE
    }
}
