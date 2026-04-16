package com.autoride.accepter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var loginEmailInput: TextInputEditText
    private lateinit var loginPasswordInput: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var logoutButton: Button
    private lateinit var loginStatusLabel: TextView

    private lateinit var masterSwitch: SwitchMaterial
    private lateinit var minFareInput: TextInputEditText
    private lateinit var maxFareInput: TextInputEditText
    private lateinit var minDistanceInput: TextInputEditText
    private lateinit var maxDistanceInput: TextInputEditText
    private lateinit var minFarePerKmInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var openAccessibilityButton: Button
    private lateinit var monthlyPlanButton: Button
    private lateinit var yearlyPlanButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var subscriptionStatusLabel: TextView

    private lateinit var authManager: AuthManager
    private lateinit var subscriptionManager: SubscriptionManager
    private var pendingPaymentRequest: UpiPaymentRequest? = null

    private val upiLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val paymentRequest = pendingPaymentRequest ?: return@registerForActivityResult
        val response = result.data?.getStringExtra("response")

        if (result.resultCode == RESULT_OK && subscriptionManager.isUpiSuccessResponse(response)) {
            subscriptionManager.markPaymentPending(paymentRequest.plan, paymentRequest.transactionId)
            if (BuildConfig.ALLOW_LOCAL_SUBSCRIPTION_ACTIVATION) {
                subscriptionManager.activate(paymentRequest.plan)
                Toast.makeText(this, getString(R.string.payment_success, paymentRequest.plan.label), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.payment_verification_required, paymentRequest.transactionId), Toast.LENGTH_LONG).show()
            }
            refreshSubscriptionStatus()
            refreshActionAvailability()
            return@registerForActivityResult
        }

        Toast.makeText(this, R.string.payment_pending_or_failed, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authManager = AuthManager(this)
        subscriptionManager = SubscriptionManager(this)

        loginEmailInput = findViewById(R.id.loginEmailInput)
        loginPasswordInput = findViewById(R.id.loginPasswordInput)
        loginButton = findViewById(R.id.loginButton)
        logoutButton = findViewById(R.id.logoutButton)
        loginStatusLabel = findViewById(R.id.loginStatusLabel)

        masterSwitch = findViewById(R.id.masterSwitch)
        minFareInput = findViewById(R.id.minFareInput)
        maxFareInput = findViewById(R.id.maxFareInput)
        minDistanceInput = findViewById(R.id.minDistanceInput)
        maxDistanceInput = findViewById(R.id.maxDistanceInput)
        minFarePerKmInput = findViewById(R.id.minFarePerKmInput)
        saveButton = findViewById(R.id.saveButton)
        openAccessibilityButton = findViewById(R.id.openAccessibilityButton)
        monthlyPlanButton = findViewById(R.id.monthlyPlanButton)
        yearlyPlanButton = findViewById(R.id.yearlyPlanButton)
        subscriptionStatusLabel = findViewById(R.id.subscriptionStatusLabel)
        statusLabel = findViewById(R.id.statusLabel)

        loadSettings()
        refreshLoginStatus()
        refreshStatusLabel()
        refreshSubscriptionStatus()
        refreshActionAvailability()

        loginButton.setOnClickListener {
            attemptLogin()
        }

        logoutButton.setOnClickListener {
            authManager.logout()
            masterSwitch.isChecked = false
            refreshLoginStatus()
            refreshActionAvailability()
            refreshStatusLabel()
            Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            saveSettings()
        }

        monthlyPlanButton.setOnClickListener {
            launchUpiPayment(SubscriptionPlan.MONTHLY)
        }

        yearlyPlanButton.setOnClickListener {
            launchUpiPayment(SubscriptionPlan.YEARLY)
        }

        openAccessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLoginStatus()
        refreshStatusLabel()
        refreshSubscriptionStatus()
        refreshActionAvailability()
    }

    private fun attemptLogin() {
        val email = loginEmailInput.text?.toString()?.trim().orEmpty()
        val password = loginPasswordInput.text?.toString().orEmpty()

        if (!authManager.login(email, password)) {
            Toast.makeText(this, R.string.login_error, Toast.LENGTH_LONG).show()
            return
        }

        loginPasswordInput.text = null
        refreshLoginStatus()
        refreshActionAvailability()
        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()
    }

    private fun launchUpiPayment(plan: SubscriptionPlan) {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required_for_payment, Toast.LENGTH_LONG).show()
            return
        }

        val upiRequest = subscriptionManager.createUpiRequest(plan)
        pendingPaymentRequest = upiRequest

        val upiIntent = Intent(Intent.ACTION_VIEW).apply {
            data = upiRequest.uri
        }

        if (upiIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, R.string.upi_not_available, Toast.LENGTH_LONG).show()
            return
        }

        upiLauncher.launch(Intent.createChooser(upiIntent, getString(R.string.choose_upi_app)))
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

            if (!authManager.isLoggedIn()) {
                Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
                masterSwitch.isChecked = false
                refreshStatusLabel()
                return
            }

            if (masterSwitch.isChecked && !subscriptionManager.isSubscriptionActive()) {
                Toast.makeText(this, R.string.subscription_required, Toast.LENGTH_LONG).show()
                masterSwitch.isChecked = false
                refreshStatusLabel()
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
            editor.putFloat("minFare", minFare)
            editor.putFloat("maxFare", maxFare)
            editor.putFloat("minDistance", minDistance)
            editor.putFloat("maxDistance", maxDistance)
            editor.putFloat("minFarePerKm", minFarePerKm)
            editor.apply()
            refreshStatusLabel()

            Toast.makeText(this, "Settings Saved & Mode Updated", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Invalid input. Please enter numbers only.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshLoginStatus() {
        if (authManager.isLoggedIn()) {
            loginStatusLabel.text = getString(R.string.login_status_logged_in, authManager.currentUserEmail())
            return
        }

        loginStatusLabel.text = getString(R.string.login_status_logged_out)
    }

    private fun refreshActionAvailability() {
        val loggedIn = authManager.isLoggedIn()
        val hasSubscription = subscriptionManager.isSubscriptionActive()

        logoutButton.isEnabled = loggedIn
        loginButton.isEnabled = !loggedIn

        monthlyPlanButton.isEnabled = loggedIn
        yearlyPlanButton.isEnabled = loggedIn

        saveButton.isEnabled = loggedIn
        masterSwitch.isEnabled = loggedIn && hasSubscription

        if (!loggedIn || !hasSubscription) {
            masterSwitch.isChecked = false
        }
    }

    private fun refreshSubscriptionStatus() {
        val billingState = subscriptionManager.billingState()
        if (billingState.isActive) {
            val expiry = DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(Date(billingState.activeUntilMs))
            subscriptionStatusLabel.text = getString(
                R.string.subscription_status_active,
                billingState.activePlanName ?: getString(R.string.unknown_plan),
                expiry
            )
            return
        }

        if (!billingState.pendingTransactionId.isNullOrBlank()) {
            subscriptionStatusLabel.text = getString(
                R.string.subscription_status_pending,
                billingState.pendingPlanName ?: getString(R.string.unknown_plan),
                billingState.pendingTransactionId
            )
            return
        }

        subscriptionStatusLabel.text = getString(R.string.subscription_status_inactive)
    }

    private fun refreshStatusLabel() {
        val serviceEnabled = isServiceEnabled()
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
        prefs: android.content.SharedPreferences,
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
        prefs: android.content.SharedPreferences,
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
