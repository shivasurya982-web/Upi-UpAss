package com.example.notifyforwarder

import org.json.JSONObject

data class TransactionRecord(
    val id: String,
    val packageName: String,
    var appName: String = "",
    val title: String,
    val text: String,
    val amount: String,
    val amountPaise: Long = 0L,
    val utr: String,
    val sender: String,
    val timestamp: Long,
    val classification: String, // INCOMING, OUTGOING, UNRELATED
    val isPayment: Boolean,
    var matchedPattern: String = "",
    var forwardingStatus: String, // YES, NO, IGNORED, PENDING_RETRY
    var serverStatus: String, // e.g. "HTTP 200", "Failed (HTTP 500)", "Pending Retry"
    var serverResponseBody: String = "",
    var retryCount: Int = 0
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("packageName", packageName)
            put("appName", appName)
            put("title", title)
            put("text", text)
            put("amount", amount)
            put("amountPaise", amountPaise)
            put("utr", utr)
            put("sender", sender)
            put("timestamp", timestamp)
            put("classification", classification)
            put("isPayment", isPayment)
            put("matchedPattern", matchedPattern)
            put("forwardingStatus", forwardingStatus)
            put("serverStatus", serverStatus)
            put("serverResponseBody", serverResponseBody)
            put("retryCount", retryCount)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): TransactionRecord {
            return TransactionRecord(
                id = json.optString("id", ""),
                packageName = json.optString("packageName", "Unknown App"),
                appName = json.optString("appName", ""),
                title = json.optString("title", ""),
                text = json.optString("text", ""),
                amount = json.optString("amount", "Not available in notification"),
                amountPaise = json.optLong("amountPaise", 0L),
                utr = json.optString("utr", "Not available in notification"),
                sender = json.optString("sender", "Not available in notification"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                classification = json.optString("classification", "UNRELATED"),
                isPayment = json.optBoolean("isPayment", false),
                matchedPattern = json.optString("matchedPattern", ""),
                forwardingStatus = json.optString("forwardingStatus", "NO"),
                serverStatus = json.optString("serverStatus", "Unknown"),
                serverResponseBody = json.optString("serverResponseBody", ""),
                retryCount = json.optInt("retryCount", 0)
            )
        }
    }
}
