package com.autoride.accepter

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom

private const val AUTH_PREFS = "AuthPrefs"
private const val KEY_AUTH_EMAIL = "authEmail"
private const val KEY_AUTH_TOKEN = "authToken"

class AuthManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun isLoggedIn(): Boolean {
        val email = prefs.getString(KEY_AUTH_EMAIL, null)
        val token = prefs.getString(KEY_AUTH_TOKEN, null)
        return !email.isNullOrBlank() && !token.isNullOrBlank()
    }

    fun currentUserEmail(): String {
        return prefs.getString(KEY_AUTH_EMAIL, null) ?: ""
    }

    fun login(email: String, password: String): Boolean {
        if (!isValidEmail(email) || !isValidPassword(password)) {
            return false
        }

        val sessionToken = createSessionToken()
        prefs.edit()
            .putString(KEY_AUTH_EMAIL, email.trim())
            .putString(KEY_AUTH_TOKEN, sessionToken)
            .apply()
        return true
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_AUTH_EMAIL)
            .remove(KEY_AUTH_TOKEN)
            .apply()
    }

    private fun createSessionToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }

    private fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
}
