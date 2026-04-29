package com.autoride.accepter

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText

class ProfileActivity : AccessControlledActivity() {

    private lateinit var profileNameInput: TextInputEditText
    private lateinit var profileCityInput: TextInputEditText
    private lateinit var profileVehicleInput: TextInputEditText
    private lateinit var saveProfileButton: Button
    private lateinit var profileStatusLabel: TextView

    private lateinit var profileManager: ProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profileManager = ProfileManager(this)

        profileNameInput = findViewById(R.id.profileNameInput)
        profileCityInput = findViewById(R.id.profileCityInput)
        profileVehicleInput = findViewById(R.id.profileVehicleInput)
        saveProfileButton = findViewById(R.id.saveProfileButton)
        profileStatusLabel = findViewById(R.id.profileStatusLabel)

        loadProfile()
        PrimaryTabs.bind(this, PrimaryTab.PROFILE)

        saveProfileButton.setOnClickListener { saveProfile() }
    }

    override fun onAccessGranted(snapshot: AppAccessSnapshot) {
        loadProfile()
    }

    private fun loadProfile() {
        val profile = profileManager.getProfile()
        if (profile == null) {
            profileStatusLabel.text = getString(R.string.profile_status_empty)
            return
        }

        profileNameInput.setText(profile.name)
        profileCityInput.setText(profile.city)
        profileVehicleInput.setText(profile.vehicle)
        profileStatusLabel.text = getString(
            R.string.profile_status_saved,
            profile.name,
            profile.city,
            profile.vehicle
        )
    }

    private fun saveProfile() {
        val name = profileNameInput.text?.toString()?.trim().orEmpty()
        val city = profileCityInput.text?.toString()?.trim().orEmpty()
        val vehicle = profileVehicleInput.text?.toString()?.trim().orEmpty()

        if (name.isBlank() || city.isBlank() || vehicle.isBlank()) {
            Toast.makeText(this, R.string.profile_error_empty, Toast.LENGTH_SHORT).show()
            return
        }

        if (!authManager.isLoggedIn()) {
            Toast.makeText(this, R.string.profile_requires_login, Toast.LENGTH_LONG).show()
            return
        }

        profileManager.saveProfile(name, city, vehicle)
        profileStatusLabel.text = getString(R.string.profile_status_saved, name, city, vehicle)
        Toast.makeText(this, R.string.profile_saved_toast, Toast.LENGTH_SHORT).show()
    }
}
