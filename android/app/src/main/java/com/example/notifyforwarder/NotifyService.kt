package com.example.notifyforwarder

import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

const val ACTION_UPDATE_UI = "com.example.notifyforwarder.ACTION_UPDATE_UI"

class NotifyService : NotificationListenerService() {

    private val TAG = "NotifyService"

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListenerService connected and active.")
        sendBroadcast(Intent(ACTION_UPDATE_UI))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationListenerService disconnected. Requesting rebind...")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            requestRebind(android.content.ComponentName(this, NotifyService::class.java))
        }
        sendBroadcast(Intent(ACTION_UPDATE_UI))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // Resolve App Label
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        val captureAll = ConfigManager.isCaptureAllPackages(this)
        val allowedPackages = ConfigManager.getAllowedPackages(this)

        // Filter package unless diagnostic "Capture All" mode is enabled
        val isAllowedPackage = allowedPackages.contains(packageName)
        if (!captureAll && !isAllowedPackage) {
            return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        
        // Extract all text fields from notification extras
        val textCandidates = listOf(
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.subText")?.toString(),
            extras.getCharSequence("android.summaryText")?.toString(),
            extras.getCharSequence("android.infoText")?.toString()
        ).filterNotNull().filter { it.isNotBlank() }

        val bodyText = if (textCandidates.isNotEmpty()) {
            textCandidates.distinct().joinToString(" ")
        } else {
            ""
        }

        if (title.isBlank() && bodyText.isBlank()) return

        Log.d(TAG, "Notification received from [$appName] ($packageName): title='$title'")

        val repository = TransactionRepository.getInstance(applicationContext)

        // Parse notification
        val record = NotificationParser.parseNotification(
            packageName = packageName,
            title = title,
            text = bodyText,
            postTime = sbn.postTime,
            appName = appName
        )

        // Deduplication check
        if (repository.isDuplicate(record.id)) {
            Log.d(TAG, "Duplicate notification ignored: id=${record.id}")
            return
        }

        // If not a valid incoming payment notification
        if (!record.isPayment) {
            Log.i(TAG, "Notification is non-incoming payment (class=${record.classification}, pkg=$packageName)")
            record.forwardingStatus = "IGNORED"
            record.serverStatus = if (record.classification == "OUTGOING") {
                "Ignored: Outgoing transaction"
            } else {
                "Ignored: Non-payment notification"
            }
            repository.addOrUpdateRecord(record)
            sendBroadcast(Intent(ACTION_UPDATE_UI))
            return
        }

        // If package was captured via debug mode but is not in allowed packages and not forwarded
        if (!isAllowedPackage && captureAll) {
            Log.i(TAG, "Incoming payment detected from unallowed package ($packageName) in diagnostic mode")
            record.forwardingStatus = "NO"
            record.serverStatus = "Diagnostic Only (Package not in allowed list)"
            repository.addOrUpdateRecord(record)
            sendBroadcast(Intent(ACTION_UPDATE_UI))
            return
        }

        // Save initially as pending
        record.forwardingStatus = "NO"
        record.serverStatus = "Forwarding to server..."
        repository.addOrUpdateRecord(record)
        sendBroadcast(Intent(ACTION_UPDATE_UI))

        // Forward to payment server
        PaymentForwarder.forwardNotification(applicationContext, record) { result ->
            if (result.success) {
                record.forwardingStatus = "YES"
                record.serverStatus = result.statusMessage
                record.serverResponseBody = result.responseBody
                repository.setLastServerSuccessTime(System.currentTimeMillis())
                repository.setLastServerStatus("ONLINE")
            } else {
                record.retryCount++
                record.forwardingStatus = if (result.httpCode in 400..499) "NO" else "PENDING_RETRY"
                record.serverStatus = result.statusMessage
                record.serverResponseBody = result.responseBody
                if (result.httpCode == 0) {
                    repository.setLastServerStatus("OFFLINE")
                }
            }

            repository.addOrUpdateRecord(record)
            sendBroadcast(Intent(ACTION_UPDATE_UI))
        }
    }
}
