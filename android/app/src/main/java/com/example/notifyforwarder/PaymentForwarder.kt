package com.example.notifyforwarder

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

object PaymentForwarder {

    private const val TAG = "PaymentForwarder"
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 10000

    data class ForwardResult(
        val success: Boolean,
        val httpCode: Int,
        val statusMessage: String,
        val responseBody: String
    )

    /**
     * Checks if the configured payment server is reachable.
     */
    fun checkServerHealth(context: Context, onResult: (status: String, details: String) -> Unit) {
        val serverUrl = ConfigManager.getServerUrl(context).trim().trimEnd('/')
        if (serverUrl.isBlank()) {
            onResult("OFFLINE", "Server URL is empty")
            return
        }

        thread {
            try {
                // Try /api/config first or base URL
                val testUrlStr = if (serverUrl.endsWith("/api/config", ignoreCase = true)) {
                    serverUrl
                } else {
                    "$serverUrl/api/config"
                }
                val url = URL(testUrlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.instanceFollowRedirects = true

                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..399) {
                    onResult("ONLINE", "HTTP $code")
                } else {
                    onResult("OFFLINE", "HTTP $code")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Health check failed: ${e.message}")
                onResult("OFFLINE", e.message ?: "Connection error")
            }
        }
    }

    /**
     * Sends the notification payload to the payment server endpoint.
     */
    fun forwardNotification(
        context: Context,
        record: TransactionRecord,
        onComplete: (ForwardResult) -> Unit
    ) {
        val endpointUrl = ConfigManager.getNotifyApiUrl(context)
        val apiKey = ConfigManager.getApiKey(context)

        thread {
            val result = executePost(endpointUrl, apiKey, record)
            onComplete(result)
        }
    }

    private fun executePost(
        endpointUrl: String,
        apiKey: String,
        record: TransactionRecord
    ): ForwardResult {
        var lastCode = 0
        var lastMsg = "Failed"
        var lastBody = ""

        // Build combined text with notification content
        val combinedText = "${record.title} ${record.text}".trim()

        val jsonPayload = JSONObject().apply {
            put("text", combinedText)
            put("ts", record.timestamp)
            put("packageName", record.packageName)
            put("amount", record.amount)
            put("amountPaise", record.amountPaise)
            if (record.utr != "Not available in notification") {
                put("utr", record.utr)
            }
            if (record.sender != "Not available in notification") {
                put("sender", record.sender)
            }
        }

        val payloadBytes = jsonPayload.toString().toByteArray(Charsets.UTF_8)

        // Attempt up to 3 times for transient errors
        for (attempt in 1..3) {
            try {
                val url = URL(endpointUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.doOutput = true
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                conn.outputStream.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                }

                val code = conn.responseCode
                lastCode = code

                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseString = stream?.let { s ->
                    BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
                } ?: ""

                conn.disconnect()
                lastBody = responseString

                if (code in 200..299) {
                    Log.i(TAG, "Notification forwarded successfully (HTTP $code)")
                    return ForwardResult(
                        success = true,
                        httpCode = code,
                        statusMessage = "HTTP $code (Success)",
                        responseBody = responseString
                    )
                } else {
                    lastMsg = "HTTP $code"
                    Log.w(TAG, "Server returned HTTP $code on attempt $attempt: $responseString")
                    // If client auth error or bad request (4xx), do not retry
                    if (code in 400..499) {
                        return ForwardResult(
                            success = false,
                            httpCode = code,
                            statusMessage = "HTTP $code (Client/Auth Error)",
                            responseBody = responseString
                        )
                    }
                }
            } catch (ex: Exception) {
                lastMsg = ex.message ?: "Network error"
                Log.w(TAG, "Forward failed on attempt $attempt/3: ${ex.message}")
            }

            if (attempt < 3) {
                try {
                    Thread.sleep(1500L * attempt)
                } catch (ignored: InterruptedException) {}
            }
        }

        return ForwardResult(
            success = false,
            httpCode = lastCode,
            statusMessage = if (lastCode > 0) "HTTP $lastCode" else lastMsg,
            responseBody = lastBody
        )
    }
}
