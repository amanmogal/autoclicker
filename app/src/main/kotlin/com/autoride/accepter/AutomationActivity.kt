package com.autoride.accepter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class AutomationActivity : AccessControlledActivity() {

    private lateinit var masterSwitch: SwitchMaterial
    private lateinit var minFareInput: TextInputEditText
    private lateinit var maxFareInput: TextInputEditText
    private lateinit var minDistanceInput: TextInputEditText
    private lateinit var maxDistanceInput: TextInputEditText
    private lateinit var minFarePerKmInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var openAccessibilityButton: Button
    private lateinit var statusLabel: TextView
    private var suppressToggleListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_automation)

        masterSwitch = findViewById(R.id.masterSwitch)
        minFareInput = findViewById(R.id.minFareInput)
        maxFareInput = findViewById(R.id.maxFareInput)
        minDistanceInput = findViewById(R.id.minDistanceInput)
        maxDistanceInput = findViewById(R.id.maxDistanceInput)
        minFarePerKmInput = findViewById(R.id.minFarePerKmInput)
        saveButton = findViewById(R.id.saveButton)
        openAccessibilityButton = findViewById(R.id.openAccessibilityButton)
        statusLabel = findViewById(R.id.statusLabel)

        loadSettings()
        refreshStatusLabel()
        refreshActionAvailability()

        saveButton.setOnClickListener { saveSettings() }
        masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggleListener) return@setOnCheckedChangeListener
            handleToggleChange(isChecked)
        }
        openAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        PrimaryTabs.bind(this, PrimaryTab.AUTOMATION)
    }

    override fun onAccessGranted(snapshot: AppAccessSnapshot) {
        refreshStatusLabel()
        refreshActionAvailability()
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
                updateToggleChecked(false)
                refreshStatusLabel()
                return
            }

            if (masterSwitch.isChecked && !authManager.hasActiveAccess()) {
                Toast.makeText(this, R.string.access_required, Toast.LENGTH_LONG).show()
                updateToggleChecked(false)
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
            editor.putBoolean("service_enabled", masterSwitch.isChecked)
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

    private fun handleToggleChange(nextChecked: Boolean) {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
            updateToggleChecked(false)
            return
        }
        if (nextChecked && !authManager.hasActiveAccess()) {
            Toast.makeText(this, R.string.access_required, Toast.LENGTH_LONG).show()
            updateToggleChecked(false)
            return
        }
        persistServiceEnabled(nextChecked)
        refreshStatusLabel()
    }

    private fun persistServiceEnabled(enabled: Boolean) {
        getSharedPreferences("RideFilters", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("serviceEnabled", enabled)
            .putBoolean("service_enabled", enabled)
            .apply()
    }

    private fun updateToggleChecked(checked: Boolean) {
        suppressToggleListener = true
        masterSwitch.isChecked = checked
        suppressToggleListener = false
        persistServiceEnabled(checked)
    }

    private fun refreshActionAvailability() {
        val loggedIn = authManager.isLoggedIn()
        val hasAccess = authManager.hasActiveAccess()

        saveButton.isEnabled = loggedIn
        masterSwitch.isEnabled = loggedIn && hasAccess
        if (!loggedIn || !hasAccess) {
            updateToggleChecked(false)
        }
    }

    private fun refreshStatusLabel() {
        val serviceEnabled = isServiceEnabled()
        val mode = if (masterSwitch.isChecked) getString(R.string.mode_on) else getString(R.string.mode_off)
        val accessibility = if (serviceEnabled) getString(R.string.accessibility_enabled) else getString(R.string.accessibility_disabled)
        statusLabel.text = getString(R.string.status_format, mode, accessibility)
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, RideAcceptService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
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
