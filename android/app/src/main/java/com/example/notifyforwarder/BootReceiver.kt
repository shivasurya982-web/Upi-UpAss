package com.example.notifyforwarder

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("BootReceiver", "Boot or package update detected. Triggering listener rebind.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    NotificationListenerService.requestRebind(
                        ComponentName(context, NotifyService::class.java)
                    )
                } catch (e: Exception) {
                    Log.w("BootReceiver", "Failed to request listener rebind: ${e.message}")
                }
            }
        }
    }
}
