package com.example.notifyforwarder

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {

    private const val PREFS_NAME = "upi_forwarder_config"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
    private const val KEY_CAPTURE_ALL = "capture_all_packages"

    // Default configuration matching existing deployment
    const val DEFAULT_SERVER_URL = "https://upi-notify-app.onrender.com"
    const val DEFAULT_API_KEY = "long-random-secret-for-the-android-app"

    val DEFAULT_ALLOWED_PACKAGES = setOf(
        // Popular UPI Apps
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                        // PhonePe
        "com.phonepe.app.business",               // PhonePe Business
        "net.one97.paytm",                        // Paytm
        "com.paytm.business",                     // Paytm for Business
        "in.org.npci.upiapp",                     // BHIM
        "in.amazon.mShop.android.shopping",       // Amazon Pay
        "com.bharatpe.app",                       // BharatPe
        "com.dreamplug.androidapp",               // CRED
        
        // Major Indian Banks
        "com.sbi.lotusintouch",                   // YONO SBI
        "com.sbi.upi",                            // SBI Pay
        "com.snapwork.hdfc",                      // HDFC MobileBanking
        "com.hdfcbank.payzapp",                   // HDFC PayZapp
        "com.csam.icici.bank.imobile",            // ICICI iMobile
        "com.icicibank.pockets",                  // ICICI Pockets
        "com.axis.mobile",                        // Axis Mobile
        "com.msf.kbank.mobile",                   // Kotak Mobile
        "com.bankofbaroda.mconnect",              // BOB World
        "com.canarabank.mobility",                // Canara ai1
        "com.unionbank.ecommerce.mobile.android", // Union Bank Vyom
        "com.pnb.pnbone",                         // PNB ONE
        "com.idbi.mobilebanking",                 // IDBI Mobile
        "com.fss.indus",                          // IndusInd Mobile
        
        // Testing / generic fallbacks
        "com.your.bank.app",
        "com.your.upi.business.app"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getServerUrl(context: Context): String {
        return getPrefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)?.trim() ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_SERVER_URL, url.trim()).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, DEFAULT_API_KEY)?.trim() ?: DEFAULT_API_KEY
    }

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getAllowedPackages(context: Context): Set<String> {
        val stored = getPrefs(context).getStringSet(KEY_ALLOWED_PACKAGES, null)
        return stored ?: DEFAULT_ALLOWED_PACKAGES
    }

    fun setAllowedPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_ALLOWED_PACKAGES, packages).apply()
    }

    fun isCaptureAllPackages(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CAPTURE_ALL, false)
    }

    fun setCaptureAllPackages(context: Context, captureAll: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CAPTURE_ALL, captureAll).apply()
    }

    /**
     * Resolves the full `/api/notify` endpoint URL correctly regardless of trailing slashes.
     */
    fun getNotifyApiUrl(context: Context): String {
        val baseUrl = getServerUrl(context).trim().trimEnd('/')
        return if (baseUrl.endsWith("/api/notify", ignoreCase = true)) {
            baseUrl
        } else {
            "$baseUrl/api/notify"
        }
    }
}
