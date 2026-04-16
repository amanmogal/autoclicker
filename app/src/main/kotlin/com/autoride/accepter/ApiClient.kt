package com.autoride.accepter

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    data class AuthSession(
        val userId: String,
        val token: String,
        val subscriptionActive: Boolean,
        val subscriptionExpiresAt: String?
    )

    sealed class ApiResult {
        data class Success(val session: AuthSession) : ApiResult()
        data class Error(val message: String) : ApiResult()
    }

    fun login(email: String, password: String): ApiResult {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
        return postAuth("auth/login", payload)
    }

    fun register(name: String, email: String, password: String): ApiResult {
        val payload = JSONObject()
            .put("name", name)
            .put("email", email)
            .put("password", password)
        return postAuth("auth/register", payload)
    }

    fun refreshSubscription(token: String): ApiResult {
        val connection = createConnection("subscription/status", "GET", token)
        return runCatching {
            val responseCode = connection.responseCode
            val response = readResponse(connection)
            if (responseCode in 200..299) {
                parseSession(response)
            } else {
                ApiResult.Error(parseError(response, "Unable to refresh subscription status."))
            }
        }.getOrElse { error ->
            ApiResult.Error(error.message ?: "Network request failed.")
        }.also {
            connection.disconnect()
        }
    }

    fun verifyUpiPayment(token: String, upiTxnId: String): ApiResult {
        val payload = JSONObject()
            .put("transactionId", upiTxnId)
            .put("provider", "upi")
        val connection = createConnection("subscription/verify-payment", "POST", token)
        return runCatching {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val responseCode = connection.responseCode
            val response = readResponse(connection)
            if (responseCode in 200..299) {
                parseSession(response)
            } else {
                ApiResult.Error(parseError(response, "UPI verification failed."))
            }
        }.getOrElse { error ->
            ApiResult.Error(error.message ?: "UPI verification request failed.")
        }.also {
            connection.disconnect()
        }
    }

    private fun postAuth(path: String, payload: JSONObject): ApiResult {
        val connection = createConnection(path, "POST", token = null)
        return runCatching {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val responseCode = connection.responseCode
            val response = readResponse(connection)
            if (responseCode in 200..299) {
                parseSession(response)
            } else {
                ApiResult.Error(parseError(response, "Authentication failed."))
            }
        }.getOrElse { error ->
            ApiResult.Error(error.message ?: "Network request failed.")
        }.also {
            connection.disconnect()
        }
    }

    private fun createConnection(path: String, method: String, token: String?): HttpURLConnection {
        val normalized = path.trimStart('/')
        val url = URL("${BuildConfig.API_BASE_URL}$normalized")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            doInput = true
            doOutput = method == "POST"
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        return connection
    }

    private fun parseSession(raw: String): ApiResult {
        val json = JSONObject(raw)
        val token = json.optString("token")
        val userId = json.optString("userId")
        val subscription = json.optJSONObject("subscription")
        val subscriptionActive = subscription?.optBoolean("active") ?: false
        val expiresAt = subscription?.optString("expiresAt")

        if (token.isBlank() || userId.isBlank()) {
            return ApiResult.Error("Invalid auth response from API.")
        }

        return ApiResult.Success(
            AuthSession(
                userId = userId,
                token = token,
                subscriptionActive = subscriptionActive,
                subscriptionExpiresAt = expiresAt
            )
        )
    }

    private fun parseError(raw: String, fallback: String): String {
        return runCatching {
            JSONObject(raw).optString("message").ifBlank { fallback }
        }.getOrElse { fallback }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
        if (stream == null) return ""
        return BufferedReader(stream.reader()).use { it.readText() }
    }
}
