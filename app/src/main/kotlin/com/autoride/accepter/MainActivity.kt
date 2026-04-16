package com.autoride.accepter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var masterSwitch: SwitchMaterial
    private lateinit var minFareInput: TextInputEditText
    private lateinit var maxFareInput: TextInputEditText
    private lateinit var minDistanceInput: TextInputEditText
    private lateinit var maxDistanceInput: TextInputEditText
    private lateinit var minFarePerKmInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var openAccessibilityButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var accountNameInput: TextInputEditText
    private lateinit var accountEmailInput: TextInputEditText
    private lateinit var accountPasswordInput: TextInputEditText
    private lateinit var registerButton: Button
    private lateinit var loginButton: Button
    private lateinit var logoutButton: Button
    private lateinit var refreshSubscriptionButton: Button
    private lateinit var authStatusLabel: TextView
    private lateinit var subscriptionStatusLabel: TextView
    private lateinit var upiPaymentButton: Button
    private lateinit var verifyPaymentButton: Button
    private lateinit var upiTxnIdInput: TextInputEditText
    private lateinit var paymentStatusLabel: TextView

    private val upiPaymentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Toast.makeText(this, "UPI app returned. Paste transaction ID and tap Verify Payment.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        masterSwitch = findViewById(R.id.masterSwitch)
        minFareInput = findViewById(R.id.minFareInput)
        maxFareInput = findViewById(R.id.maxFareInput)
        minDistanceInput = findViewById(R.id.minDistanceInput)
        maxDistanceInput = findViewById(R.id.maxDistanceInput)
        minFarePerKmInput = findViewById(R.id.minFarePerKmInput)
        saveButton = findViewById(R.id.saveButton)
        openAccessibilityButton = findViewById(R.id.openAccessibilityButton)
        statusLabel = findViewById(R.id.statusLabel)
        accountNameInput = findViewById(R.id.accountNameInput)
        accountEmailInput = findViewById(R.id.accountEmailInput)
        accountPasswordInput = findViewById(R.id.accountPasswordInput)
        registerButton = findViewById(R.id.registerButton)
        loginButton = findViewById(R.id.loginButton)
        logoutButton = findViewById(R.id.logoutButton)
        refreshSubscriptionButton = findViewById(R.id.refreshSubscriptionButton)
        authStatusLabel = findViewById(R.id.authStatusLabel)
        subscriptionStatusLabel = findViewById(R.id.subscriptionStatusLabel)
        upiPaymentButton = findViewById(R.id.upiPaymentButton)
        verifyPaymentButton = findViewById(R.id.verifyPaymentButton)
        upiTxnIdInput = findViewById(R.id.upiTxnIdInput)
        paymentStatusLabel = findViewById(R.id.paymentStatusLabel)

        loadSettings()
        refreshStatusLabel()

        saveButton.setOnClickListener {
            saveSettings()
        }
        registerButton.setOnClickListener {
            performRegistration()
        }
        loginButton.setOnClickListener {
            performLogin()
        }
        logoutButton.setOnClickListener {
            performLogout()
        }
        refreshSubscriptionButton.setOnClickListener {
            refreshSubscription()
        }
        upiPaymentButton.setOnClickListener {
            startUpiPayment()
        }
        verifyPaymentButton.setOnClickListener {
            verifyUpiPayment()
        }

        openAccessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatusLabel()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
        masterSwitch.isChecked = readBooleanPref(prefs, "serviceEnabled", "service_enabled", true)
        minFareInput.setText(readFloatPref(prefs, "minFare", "min_fare", 100.0f).toString())
        maxFareInput.setText(readFloatPref(prefs, "maxFare", "max_fare", 5000.0f).toString())
        minDistanceInput.setText(readFloatPref(prefs, "minDistance", "min_pickup_km", 0.0f).toString())
        maxDistanceInput.setText(readFloatPref(prefs, "maxDistance", "max_pickup_km", 5.0f).toString())
        minFarePerKmInput.setText(readFloatPref(prefs, "minFarePerKm", "min_fare_per_km", 15.0f).toString())
    }

    private fun saveSettings() {
        if (!isSubscriptionActive()) {
            Toast.makeText(this, "Active subscription is required to enable automation.", Toast.LENGTH_LONG).show()
            masterSwitch.isChecked = false
            updateServiceEnabled(false)
            refreshStatusLabel()
            return
        }

        val prefs = getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        try {
            val minFareStr = minFareInput.text.toString()
            val maxFareStr = maxFareInput.text.toString()
            val minDistanceStr = minDistanceInput.text.toString()
            val maxDistanceStr = maxDistanceInput.text.toString()
            val minFarePerKmStr = minFarePerKmInput.text.toString()

            if (minFareStr.isEmpty() || maxFareStr.isEmpty() || minDistanceStr.isEmpty() || maxDistanceStr.isEmpty() || minFarePerKmStr.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }

            val minFare = minFareStr.toFloat()
            val maxFare = maxFareStr.toFloat()
            val minDistance = minDistanceStr.toFloat()
            val maxDistance = maxDistanceStr.toFloat()
            val minFarePerKm = minFarePerKmStr.toFloat()

            if (!minFare.isFinite() || !maxFare.isFinite() || !minDistance.isFinite() || !maxDistance.isFinite() || !minFarePerKm.isFinite()) {
                Toast.makeText(this, "Invalid numeric value detected.", Toast.LENGTH_SHORT).show()
                return
            }
            if (minFare < 0f || maxFare < 0f || minDistance < 0f || maxDistance < 0f || minFarePerKm < 0f) {
                Toast.makeText(this, "Values must be zero or greater.", Toast.LENGTH_SHORT).show()
                return
            }
            if (minFare > maxFare) {
                Toast.makeText(this, "Min fare must be less than or equal to max fare.", Toast.LENGTH_SHORT).show()
                return
            }
            if (minDistance > maxDistance) {
                Toast.makeText(this, "Min distance must be less than or equal to max distance.", Toast.LENGTH_SHORT).show()
                return
            }

            editor.putBoolean("serviceEnabled", masterSwitch.isChecked)
            editor.putBoolean("service_enabled", masterSwitch.isChecked)
            editor.putFloat("minFare", minFare)
            editor.putFloat("maxFare", maxFare)
            editor.putFloat("minDistance", minDistance)
            editor.putFloat("maxDistance", maxDistance)
            editor.putFloat("minFarePerKm", minFarePerKm)
            editor.apply()
            refreshStatusLabel()

            Toast.makeText(this, "Settings Saved & Mode Updated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid input. Please enter numbers only.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshStatusLabel() {
        val serviceEnabled = isServiceEnabled()
        val signedIn = isSignedIn()
        val subscriptionActive = isSubscriptionActive()
        val mode = if (masterSwitch.isChecked) {
            getString(R.string.mode_on)
        } else {
            getString(R.string.mode_off)
        }
        val accessibility = if (serviceEnabled) {
            getString(R.string.accessibility_enabled)
        } else {
            getString(R.string.accessibility_disabled)
        }
        statusLabel.text = getString(R.string.status_format, mode, accessibility)
        authStatusLabel.text = if (signedIn) {
            getString(R.string.auth_status_signed_in)
        } else {
            getString(R.string.auth_status_signed_out)
        }
        subscriptionStatusLabel.text = if (subscriptionActive) {
            getString(R.string.subscription_status_active, readSubscriptionExpiry())
        } else {
            getString(R.string.subscription_status_inactive)
        }
        paymentStatusLabel.text = if (subscriptionActive) {
            getString(R.string.payment_status_ready)
        } else {
            getString(R.string.payment_status_required)
        }
        masterSwitch.isEnabled = signedIn && subscriptionActive
        saveButton.isEnabled = signedIn && subscriptionActive
        refreshSubscriptionButton.isEnabled = signedIn
        verifyPaymentButton.isEnabled = signedIn
    }

    private fun performRegistration() {
        val name = accountNameInput.text?.toString()?.trim().orEmpty()
        val email = accountEmailInput.text?.toString()?.trim().orEmpty()
        val password = accountPasswordInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Name, email and password are required.", Toast.LENGTH_SHORT).show()
            return
        }
        runAuthRequest { ApiClient.register(name, email, password) }
    }

    private fun performLogin() {
        val email = accountEmailInput.text?.toString()?.trim().orEmpty()
        val password = accountPasswordInput.text?.toString()?.trim().orEmpty()
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Email and password are required.", Toast.LENGTH_SHORT).show()
            return
        }
        runAuthRequest { ApiClient.login(email, password) }
    }

    private fun refreshSubscription() {
        val token = readAuthToken()
        if (token.isBlank()) {
            Toast.makeText(this, "Log in first.", Toast.LENGTH_SHORT).show()
            return
        }
        runAuthRequest { ApiClient.refreshSubscription(token) }
    }

    private fun startUpiPayment() {
        val token = readAuthToken()
        if (token.isBlank()) {
            Toast.makeText(this, "Log in first before making payment.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse(
            "upi://pay?pa=${BuildConfig.UPI_VPA}" +
                "&pn=${Uri.encode(BuildConfig.UPI_PAYEE_NAME)}" +
                "&tn=${Uri.encode(BuildConfig.UPI_NOTE)}" +
                "&cu=INR"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No UPI app found on this device.", Toast.LENGTH_LONG).show()
            return
        }
        upiPaymentLauncher.launch(intent)
    }

    private fun verifyUpiPayment() {
        val token = readAuthToken()
        val txnId = upiTxnIdInput.text?.toString()?.trim().orEmpty()
        if (token.isBlank()) {
            Toast.makeText(this, "Log in first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (txnId.isBlank()) {
            Toast.makeText(this, "Enter UPI transaction ID.", Toast.LENGTH_SHORT).show()
            return
        }
        runAuthRequest { ApiClient.verifyUpiPayment(token, txnId) }
    }

    private fun performLogout() {
        getSharedPreferences("RideFilters", Context.MODE_PRIVATE).edit()
            .remove("auth_user_id")
            .remove("auth_token")
            .remove("subscription_active")
            .remove("subscription_expires_at")
            .putBoolean("serviceEnabled", false)
            .putBoolean("service_enabled", false)
            .apply()
        upiTxnIdInput.setText("")
        masterSwitch.isChecked = false
        refreshStatusLabel()
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
    }

    private fun runAuthRequest(block: () -> ApiClient.ApiResult) {
        registerButton.isEnabled = false
        loginButton.isEnabled = false
        refreshSubscriptionButton.isEnabled = false
        Thread {
            val result = block()
            runOnUiThread {
                when (result) {
                    is ApiClient.ApiResult.Success -> {
                        persistAuthSession(result.session)
                        Toast.makeText(this, "Authentication updated.", Toast.LENGTH_SHORT).show()
                    }
                    is ApiClient.ApiResult.Error -> {
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                }
                registerButton.isEnabled = true
                loginButton.isEnabled = true
                refreshStatusLabel()
            }
        }.start()
    }

    private fun persistAuthSession(session: ApiClient.AuthSession) {
        val shouldEnableService = session.subscriptionActive && masterSwitch.isChecked
        getSharedPreferences("RideFilters", Context.MODE_PRIVATE).edit()
            .putString("auth_user_id", session.userId)
            .putString("auth_token", session.token)
            .putBoolean("subscription_active", session.subscriptionActive)
            .putString("subscription_expires_at", session.subscriptionExpiresAt ?: "N/A")
            .putBoolean("serviceEnabled", shouldEnableService)
            .putBoolean("service_enabled", shouldEnableService)
            .apply()
        if (!session.subscriptionActive) {
            masterSwitch.isChecked = false
            SubscriptionManager.enforceInactiveState(this)
            Toast.makeText(this, "Subscription inactive. Auto-accept has been disabled.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isSignedIn(): Boolean {
        return readAuthToken().isNotBlank()
    }

    private fun isSubscriptionActive(): Boolean {
        return SubscriptionManager.hasValidEntitlement(this)
    }

    private fun readSubscriptionExpiry(): String {
        return getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
            .getString("subscription_expires_at", "N/A")
            .orEmpty()
    }

    private fun readAuthToken(): String {
        return getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
            .getString("auth_token", "")
            .orEmpty()
    }

    private fun updateServiceEnabled(enabled: Boolean) {
        getSharedPreferences("RideFilters", Context.MODE_PRIVATE).edit()
            .putBoolean("serviceEnabled", enabled)
            .putBoolean("service_enabled", enabled)
            .apply()
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, RideAcceptService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices
            .split(':')
            .any { it.equals(expected, ignoreCase = true) }
    }

    private fun readBooleanPref(
        prefs: SharedPreferences,
        primaryKey: String,
        legacyKey: String,
        defaultValue: Boolean
    ): Boolean {
        return if (prefs.contains(primaryKey)) {
            prefs.getBoolean(primaryKey, defaultValue)
        } else {
            prefs.getBoolean(legacyKey, defaultValue)
        }
    }

    private fun readFloatPref(
        prefs: SharedPreferences,
        primaryKey: String,
        legacyKey: String,
        defaultValue: Float
    ): Float {
        if (prefs.contains(primaryKey)) {
            return runCatching { prefs.getFloat(primaryKey, defaultValue) }
                .getOrElse {
                    prefs.getString(primaryKey, defaultValue.toString())?.toFloatOrNull() ?: defaultValue
                }
        }

        if (prefs.contains(legacyKey)) {
            return runCatching { prefs.getFloat(legacyKey, defaultValue) }
                .getOrElse {
                    prefs.getString(legacyKey, defaultValue.toString())?.toFloatOrNull() ?: defaultValue
                }
        }

        return defaultValue
    }
}
