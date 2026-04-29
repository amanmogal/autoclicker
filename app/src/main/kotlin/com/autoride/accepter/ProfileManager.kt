package com.autoride.accepter

import android.content.Context
import android.content.SharedPreferences

private const val PROFILE_PREFS = "DriverProfilePrefs"
private const val KEY_PROFILE_NAME = "driverName"
private const val KEY_PROFILE_CITY = "driverCity"
private const val KEY_PROFILE_VEHICLE = "driverVehicle"

data class DriverProfile(
    val name: String,
    val city: String,
    val vehicle: String
)

class ProfileManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

    fun saveProfile(name: String, city: String, vehicle: String) {
        prefs.edit()
            .putString(KEY_PROFILE_NAME, name.trim())
            .putString(KEY_PROFILE_CITY, city.trim())
            .putString(KEY_PROFILE_VEHICLE, vehicle.trim())
            .apply()
    }

    fun getProfile(): DriverProfile? {
        val name = prefs.getString(KEY_PROFILE_NAME, null)?.trim().orEmpty()
        val city = prefs.getString(KEY_PROFILE_CITY, null)?.trim().orEmpty()
        val vehicle = prefs.getString(KEY_PROFILE_VEHICLE, null)?.trim().orEmpty()

        if (name.isBlank() || city.isBlank() || vehicle.isBlank()) {
            return null
        }

        return DriverProfile(name = name, city = city, vehicle = vehicle)
    }
}
