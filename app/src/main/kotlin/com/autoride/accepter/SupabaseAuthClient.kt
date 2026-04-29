package com.autoride.accepter

import android.util.Log
import android.util.Patterns
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class SupabaseSession(
    val email: String,
    val accessToken: String,
    val refreshToken: String?
)

data class SupabaseAuthResult(
    val session: SupabaseSession? = null,
    val accessSnapshot: AppAccessSnapshot? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = errorMessage == null
}

class SupabaseAuthClient {
    companion object {
        private const val TAG = "SupabaseAuthClient"
    }

    private val baseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    fun signIn(email: String, password: String): SupabaseAuthResult {
        val validationError = validateSignInCredentials(email, password)
        if (validationError != null) {
            return SupabaseAuthResult(errorMessage = validationError)
        }
        if (!isConfigured()) {
            return SupabaseAuthResult(errorMessage = "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in Gradle properties.")
        }

        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
        return sendAuthRequest(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            payload = payload,
            accessToken = null,
            requireSession = true
        )
    }

    fun signUp(email: String, password: String): SupabaseAuthResult {
        val validationError = validateSignUpCredentials(email, password)
        if (validationError != null) {
            return SupabaseAuthResult(errorMessage = validationError)
        }
        if (!isConfigured()) {
            return SupabaseAuthResult(errorMessage = "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in Gradle properties.")
        }

        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put(
                "data",
                JSONObject()
                    .put("approval_status", "pending")
                    .put("approved", false)
                    .put("subscription_status", "pending")
            )
        return sendAuthRequest(
            method = "POST",
            path = "/auth/v1/signup",
            payload = payload,
            accessToken = null,
            requireSession = false
        )
    }

    fun requestPasswordReset(email: String): SupabaseAuthResult {
        val normalizedEmail = email.trim()
        if (!isValidEmail(normalizedEmail)) {
            return SupabaseAuthResult(errorMessage = "Enter a valid email.")
        }
        if (!isConfigured()) {
            return SupabaseAuthResult(errorMessage = "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in Gradle properties.")
        }

        val payload = JSONObject().put("email", normalizedEmail)
        return sendAuthRequest(
            method = "POST",
            path = "/auth/v1/recover",
            payload = payload,
            accessToken = null,
            requireSession = false
        )
    }

    fun refreshCurrentAccess(accessToken: String): SupabaseAuthResult {
        if (accessToken.isBlank()) {
            return SupabaseAuthResult(errorMessage = "Missing access token.")
        }
        if (!isConfigured()) {
            return SupabaseAuthResult(errorMessage = "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in Gradle properties.")
        }

        return sendAuthRequest(
            method = "GET",
            path = "/auth/v1/user",
            payload = null,
            accessToken = accessToken,
            requireSession = false
        )
    }

    fun refreshSession(refreshToken: String): SupabaseAuthResult {
        if (refreshToken.isBlank()) {
            return SupabaseAuthResult(errorMessage = "Missing refresh token.")
        }
        if (!isConfigured()) {
            return SupabaseAuthResult(errorMessage = "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in Gradle properties.")
        }

        val payload = JSONObject().put("refresh_token", refreshToken)
        return sendAuthRequest(
            method = "POST",
            path = "/auth/v1/token?grant_type=refresh_token",
            payload = payload,
            accessToken = null,
            requireSession = false
        )
    }

    private fun isConfigured(): Boolean {
        return AppAccessPolicy.isSupabaseConfigured(baseUrl, anonKey)
    }

    private fun validateSignInCredentials(email: String, password: String): String? {
        if (!isValidEmail(email.trim())) {
            return "Enter a valid email."
        }
        if (password.isBlank()) {
            return "Password is required."
        }
        return null
    }

    private fun validateSignUpCredentials(email: String, password: String): String? {
        if (!isValidEmail(email.trim())) {
            return "Enter a valid email."
        }
        if (password.length < 6) {
            return "Password must be at least 6 characters."
        }
        return null
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun sendAuthRequest(
        method: String,
        path: String,
        payload: JSONObject?,
        accessToken: String?,
        requireSession: Boolean
    ): SupabaseAuthResult {
        Log.d(TAG, "Request start path=$path configured=${isConfigured()} baseUrl=$baseUrl")
        val url = URL("$baseUrl$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: anonKey}")
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        return try {
            if (payload != null) {
                connection.outputStream.use { stream ->
                    stream.writer(Charsets.UTF_8).use { writer ->
                        writer.write(payload.toString())
                    }
                }
            }

            val code = connection.responseCode
            val rawBody = readBody(
                if (code in 200..299) connection.inputStream else connection.errorStream
            )
            Log.d(TAG, "Request completed path=$path code=$code bodyLength=${rawBody.length}")
            val json = rawBody.toJsonObjectOrNull()

            if (code in 200..299) {
                val session = extractSession(json, payload?.optString("email").orEmpty())
                val userJson = if (path.endsWith("/user")) json else json?.optJSONObject("user")
                val accessSnapshot = extractAccessSnapshot(userJson)
                if (session != null) {
                    if (requireSession && accessSnapshot != null && !accessSnapshot.hasActiveAccess) {
                        return SupabaseAuthResult(errorMessage = accessDeniedMessage(accessSnapshot.state))
                    }
                    return SupabaseAuthResult(session = session, accessSnapshot = accessSnapshot)
                }
                if (accessSnapshot != null) {
                    return SupabaseAuthResult(accessSnapshot = accessSnapshot)
                }
                if (!requireSession) {
                    val infoMessage = if (requiresEmailConfirmation(json)) {
                        "Account created. Verify your email first, then wait for admin approval."
                    } else {
                        "Account created. Pending admin approval."
                    }
                    return SupabaseAuthResult(infoMessage = infoMessage)
                }
                return SupabaseAuthResult(errorMessage = "Sign-in failed: no session token returned.")
            }

            SupabaseAuthResult(errorMessage = extractErrorMessage(json, rawBody, code, path))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host for Supabase URL: $baseUrl", e)
            SupabaseAuthResult(errorMessage = "Supabase host not reachable. Check SUPABASE_URL and internet access.")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout talking to Supabase", e)
            SupabaseAuthResult(errorMessage = "Supabase request timed out. Check network quality and retry.")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Connect exception talking to Supabase", e)
            SupabaseAuthResult(errorMessage = "Unable to connect to Supabase. Verify network access.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Supabase client failure", e)
            val detail = e.message?.trim().orEmpty()
            val message = if (detail.isNotBlank()) {
                "Unable to reach Supabase: $detail"
            } else {
                "Unable to reach Supabase. Check project URL/key and network."
            }
            SupabaseAuthResult(errorMessage = message)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractSession(json: JSONObject?, fallbackEmail: String): SupabaseSession? {
        if (json == null) return null
        val accessToken = json.optString("access_token", "").trim()
        if (accessToken.isBlank()) {
            return null
        }

        val refreshToken = json.optString("refresh_token").trim().takeIf { it.isNotBlank() }
        val user = json.optJSONObject("user")
        val email = user?.optString("email", "").orEmpty().ifBlank { fallbackEmail.trim() }
        if (email.isBlank()) {
            return null
        }

        return SupabaseSession(
            email = email,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    private fun extractErrorMessage(json: JSONObject?, rawBody: String, code: Int, path: String): String {
        val bodyText = rawBody.lowercase()
        if (code == 401 || code == 403) {
            if (path.endsWith("/user") || path.contains("grant_type=refresh_token")) {
                return "Session expired. Sign in again."
            }
            return "Invalid Supabase key or URL for this project."
        }
        if (code == 429) {
            return "Too many auth attempts. Please wait and retry."
        }

        if (bodyText.contains("email not confirmed")) {
            return "Email not confirmed. Verify your inbox link before signing in."
        }
        if (bodyText.contains("invalid login credentials")) {
            return "Invalid email or password."
        }
        if (bodyText.contains("already registered")) {
            return "This email is already registered. Try signing in."
        }
        if (bodyText.contains("password")) {
            return "Password does not meet Supabase requirements."
        }
        if (bodyText.contains("pending")) {
            return "Account is pending admin approval."
        }

        if (json == null) {
            return "Supabase request failed (HTTP $code)."
        }

        val errorDescription = json.optString("error_description", "").trim()
        if (errorDescription.isNotBlank()) {
            return errorDescription
        }
        val message = json.optString("msg", "").trim()
        if (message.isNotBlank()) {
            return message
        }
        val error = json.optString("error", "").trim()
        if (error.isNotBlank()) {
            return error
        }
        return "Supabase request failed (HTTP $code)."
    }

    private fun requiresEmailConfirmation(responseJson: JSONObject?): Boolean {
        val userJson = responseJson?.optJSONObject("user") ?: return false
        val emailConfirmedAt = sequenceOf(
            userJson.optString("email_confirmed_at"),
            userJson.optString("confirmed_at")
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        return emailConfirmedAt.isBlank()
    }

    private fun extractAccessSnapshot(userJson: JSONObject?): AppAccessSnapshot? {
        if (userJson == null) return null

        val appMetadata = userJson.optJSONObject("app_metadata")
        val userMetadata = userJson.optJSONObject("user_metadata")
        val approved = appMetadata.readBooleanLike("approved")
            ?: userMetadata.readBooleanLike("approved")
            ?: false
        val approvalStatus = firstNonBlank(
            appMetadata?.optString("approval_status"),
            userMetadata?.optString("approval_status"),
            if (approved) "approved" else "pending"
        )
        val subscriptionStatus = firstNonBlank(
            appMetadata?.optString("subscription_status"),
            userMetadata?.optString("subscription_status"),
            if (approved) "expired" else "pending"
        )
        val subscriptionStartedAt = firstNonBlank(
            appMetadata?.optString("subscription_started_at"),
            userMetadata?.optString("subscription_started_at"),
            null
        )
        val subscriptionExpiresAt = firstNonBlank(
            appMetadata?.optString("subscription_expires_at"),
            userMetadata?.optString("subscription_expires_at"),
            null
        )
        val lastAdminActionAt = firstNonBlank(
            appMetadata?.optString("last_admin_action_at"),
            userMetadata?.optString("last_admin_action_at"),
            null
        )

        val startedAtMs = parseIsoToMillis(subscriptionStartedAt)
        val expiresAtMs = parseIsoToMillis(subscriptionExpiresAt)
        val lastAdminActionAtMs = parseIsoToMillis(lastAdminActionAt)
        val nowMs = System.currentTimeMillis()

        val normalizedApproval = approvalStatus.orEmpty().trim().lowercase(Locale.US)
        val normalizedSubscription = subscriptionStatus.orEmpty().trim().lowercase(Locale.US)
        val state = when {
            normalizedApproval.isBlank() && normalizedSubscription.isBlank() && !approved -> AppAccessState.UNKNOWN
            normalizedApproval in setOf("rejected", "revoked") || normalizedSubscription == "revoked" -> AppAccessState.REVOKED
            !approved || normalizedApproval in setOf("", "pending") -> AppAccessState.PENDING_APPROVAL
            expiresAtMs in 1..nowMs -> AppAccessState.EXPIRED
            normalizedSubscription == "active" && expiresAtMs > nowMs -> AppAccessState.ACTIVE
            approved && expiresAtMs <= 0L -> AppAccessState.EXPIRED
            approved && expiresAtMs > nowMs -> AppAccessState.ACTIVE
            else -> AppAccessState.EXPIRED
        }

        return AppAccessSnapshot(
            approved = approved,
            approvalStatus = approvalStatus.orEmpty(),
            subscriptionStatus = subscriptionStatus.orEmpty(),
            subscriptionStartedAt = subscriptionStartedAt,
            subscriptionExpiresAt = subscriptionExpiresAt,
            subscriptionStartedAtMs = startedAtMs,
            subscriptionExpiresAtMs = expiresAtMs,
            lastAdminActionAt = lastAdminActionAt,
            lastAdminActionAtMs = lastAdminActionAtMs,
            state = state
        )
    }

    private fun JSONObject?.readBooleanLike(key: String): Boolean? {
        if (this == null || !has(key)) return null
        val raw = opt(key)
        if (raw == null || raw == JSONObject.NULL) return null
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase(Locale.US)) {
                "true", "1", "yes", "approved", "active" -> true
                "false", "0", "no", "pending", "rejected", "inactive", "revoked" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun String.toJsonObjectOrNull(): JSONObject? {
        if (isBlank()) return null
        return runCatching { JSONObject(this) }.getOrNull()
    }

    private fun accessDeniedMessage(state: AppAccessState): String {
        return when (state) {
            AppAccessState.PENDING_APPROVAL -> "Account is pending admin approval."
            AppAccessState.EXPIRED -> "Manual subscription expired. Contact admin for renewal."
            AppAccessState.REVOKED -> "Access revoked. Contact admin for renewal."
            else -> "Unable to verify access. Contact admin."
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun parseIsoToMillis(rawValue: String?): Long {
        if (rawValue.isNullOrBlank()) return 0L
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        ).onEach {
            it.timeZone = TimeZone.getTimeZone("UTC")
            it.isLenient = false
        }
        formats.forEach { formatter ->
            val parsed = runCatching { formatter.parse(rawValue)?.time ?: 0L }.getOrDefault(0L)
            if (parsed > 0L) return parsed
        }
        return 0L
    }

    private fun readBody(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }
    }
}
