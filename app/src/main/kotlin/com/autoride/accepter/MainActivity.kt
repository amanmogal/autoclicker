package com.autoride.accepter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.SharedPreferences
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
    private lateinit var recenterOverlayButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var runtimeStatsLabel: TextView

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
        recenterOverlayButton = findViewById(R.id.recenterOverlayButton)
        statusLabel = findViewById(R.id.statusLabel)
        runtimeStatsLabel = findViewById(R.id.runtimeStatsLabel)

        loadSettings()
        refreshStatusLabel()

        saveButton.setOnClickListener {
            saveSettings()
        }

        openAccessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        recenterOverlayButton.setOnClickListener {
            val prefs = getSharedPreferences(AppPrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(AppPrefsKeys.KEY_OVERLAY_X, 0)
                .putInt(AppPrefsKeys.KEY_OVERLAY_Y, 100)
                .apply()
            Toast.makeText(this, getString(R.string.overlay_recentered), Toast.LENGTH_SHORT).show()
            refreshStatusLabel()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatusLabel()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(AppPrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        masterSwitch.isChecked = readBooleanPref(
            prefs,
            AppPrefsKeys.KEY_SERVICE_ENABLED,
            AppPrefsKeys.KEY_SERVICE_ENABLED_LEGACY,
            true
        )
        minFareInput.setText(readFloatPref(prefs, "minFare", "min_fare", 100.0f).toString())
        maxFareInput.setText(readFloatPref(prefs, "maxFare", "max_fare", 5000.0f).toString())
        minDistanceInput.setText(readFloatPref(prefs, "minDistance", "min_pickup_km", 0.0f).toString())
        maxDistanceInput.setText(readFloatPref(prefs, "maxDistance", "max_pickup_km", 5.0f).toString())
        minFarePerKmInput.setText(readFloatPref(prefs, "minFarePerKm", "min_fare_per_km", 15.0f).toString())
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences(AppPrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
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

            editor.putBoolean(AppPrefsKeys.KEY_SERVICE_ENABLED, masterSwitch.isChecked)
            editor.putBoolean(AppPrefsKeys.KEY_SERVICE_ENABLED_LEGACY, masterSwitch.isChecked)
            editor.putFloat("minFare", minFareStr.toFloat())
            editor.putFloat("maxFare", maxFareStr.toFloat())
            editor.putFloat("minDistance", minDistanceStr.toFloat())
            editor.putFloat("maxDistance", maxDistanceStr.toFloat())
            editor.putFloat("minFarePerKm", minFarePerKmStr.toFloat())
            editor.apply()
            refreshStatusLabel()

            Toast.makeText(this, "Settings Saved & Mode Updated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid input. Please enter numbers only.", Toast.LENGTH_SHORT).show()
        }
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

        val prefs = getSharedPreferences(AppPrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val seen = prefs.getLong(AppPrefsKeys.KEY_METRIC_OFFERS_SEEN, 0L)
        val accepted = prefs.getLong(AppPrefsKeys.KEY_METRIC_OFFERS_ACCEPTED, 0L)
        val gestureSent = prefs.getLong(AppPrefsKeys.KEY_METRIC_GESTURE_SENT, 0L)
        val gestureFailed = prefs.getLong(AppPrefsKeys.KEY_METRIC_GESTURE_FAILED, 0L)
        val overlayX = prefs.getInt(AppPrefsKeys.KEY_OVERLAY_X, 0)
        val overlayY = prefs.getInt(AppPrefsKeys.KEY_OVERLAY_Y, 100)
        runtimeStatsLabel.text = getString(
            R.string.runtime_stats_format,
            seen,
            accepted,
            gestureSent,
            gestureFailed,
            overlayX,
            overlayY
        )
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
