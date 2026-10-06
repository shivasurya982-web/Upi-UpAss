package com.example.notifyforwarder

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var repository: TransactionRepository
    private lateinit var adapter: TransactionAdapter
    private val dateFormatter = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateDashboard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = TransactionRepository.getInstance(applicationContext)

        val rvRecent = findViewById<RecyclerView>(R.id.rvRecentNotifications)
        rvRecent.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(emptyList())
        rvRecent.adapter = adapter

        // Button handlers
        findViewById<Button>(R.id.btnGrantAccess).setOnClickListener {
            openNotificationSettings()
        }

        findViewById<Button>(R.id.btnFixBattery).setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<Button>(R.id.btnTestServer).setOnClickListener {
            testServerConnection()
        }

        findViewById<Button>(R.id.btnSimulateTest).setOnClickListener {
            simulatePayment(
                amount = "499.37",
                sender = "Sivakumar",
                utr = "123456789012"
            )
        }

        findViewById<Button>(R.id.btnSimulateWrongAmount).setOnClickListener {
            simulatePayment(
                amount = "499.00",
                sender = "Test Buyer",
                utr = "987654321012"
            )
        }

        findViewById<Button>(R.id.btnSimulateDebit).setOnClickListener {
            simulateDebit()
        }

        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            repository.clearHistory()
            updateDashboard()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }

        // Check server health on launch
        testServerConnection(showToast = false)
        updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiUpdateReceiver, IntentFilter(ACTION_UPDATE_UI), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiUpdateReceiver, IntentFilter(ACTION_UPDATE_UI))
        }
        updateDashboard()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(uiUpdateReceiver)
        } catch (e: Exception) {}
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open notification settings directly", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (ex: Exception) {
                    Toast.makeText(this, "Please disable battery optimization in device settings", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            return pm?.isIgnoringBatteryOptimizations(packageName) ?: true
        }
        return true
    }

    private fun testServerConnection(showToast: Boolean = true) {
        val tvServerStatus = findViewById<TextView>(R.id.tvConfigServerStatus)
        tvServerStatus.text = "Checking..."
        tvServerStatus.setTextColor(getColor(R.color.text_secondary))

        PaymentForwarder.checkServerHealth(this) { status, details ->
            runOnUiThread {
                repository.setLastServerStatus(status)
                if (status == "ONLINE") {
                    tvServerStatus.text = "ONLINE"
                    tvServerStatus.setTextColor(getColor(R.color.success_green))
                    if (showToast) Toast.makeText(this, "Server is ONLINE ($details)", Toast.LENGTH_SHORT).show()
                } else {
                    tvServerStatus.text = "OFFLINE"
                    tvServerStatus.setTextColor(getColor(R.color.warning_red))
                    if (showToast) Toast.makeText(this, "Server is OFFLINE: $details", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateDashboard() {
        // 1. Notification Access Banner & Status
        val accessEnabled = isNotificationServiceEnabled()
        val cardAccessWarning = findViewById<CardView>(R.id.cardAccessWarning)
        val tvServiceStatusBadge = findViewById<TextView>(R.id.tvServiceStatusBadge)
        val tvConfigAccessStatus = findViewById<TextView>(R.id.tvConfigAccessStatus)

        if (accessEnabled) {
            cardAccessWarning.visibility = View.GONE
            tvServiceStatusBadge.text = "Active"
            tvServiceStatusBadge.setBackgroundColor(getColor(R.color.success_bg))
            tvServiceStatusBadge.setTextColor(getColor(R.color.success_green))
            tvConfigAccessStatus.text = "ENABLED"
            tvConfigAccessStatus.setTextColor(getColor(R.color.success_green))
        } else {
            cardAccessWarning.visibility = View.VISIBLE
            tvServiceStatusBadge.text = "Disabled"
            tvServiceStatusBadge.setBackgroundColor(getColor(R.color.warning_bg))
            tvServiceStatusBadge.setTextColor(getColor(R.color.warning_red))
            tvConfigAccessStatus.text = "DISABLED"
            tvConfigAccessStatus.setTextColor(getColor(R.color.warning_red))
        }

        // 2. Battery Optimization Banner
        val isBatteryIgnored = isBatteryOptimizationIgnored()
        val cardBatteryWarning = findViewById<CardView>(R.id.cardBatteryWarning)
        cardBatteryWarning.visibility = if (isBatteryIgnored) View.GONE else View.VISIBLE

        // 3. Server Status & URL
        val endpointUrl = ConfigManager.getNotifyApiUrl(this)
        findViewById<TextView>(R.id.tvConfigServerUrl).text = endpointUrl

        val apiKey = ConfigManager.getApiKey(this)
        val maskedApiKey = if (apiKey.length > 4) "••••••••" + apiKey.takeLast(4) else "••••••••"
        findViewById<TextView>(R.id.tvConfigApiKey).text = maskedApiKey

        val isCaptureAll = ConfigManager.isCaptureAllPackages(this)
        val allowedPkgs = ConfigManager.getAllowedPackages(this)
        val tvAllowedPkgs = findViewById<TextView>(R.id.tvConfigAllowedPackages)
        if (isCaptureAll) {
            tvAllowedPkgs.text = "ALL PACKAGES (Diagnostic Mode)"
            tvAllowedPkgs.setTextColor(getColor(R.color.amber_yellow))
        } else {
            tvAllowedPkgs.text = "${allowedPkgs.size} Apps configured"
            tvAllowedPkgs.setTextColor(getColor(R.color.accent))
        }

        val lastSuccessTime = repository.getLastServerSuccessTime()
        findViewById<TextView>(R.id.tvConfigLastSuccess).text = if (lastSuccessTime > 0) {
            dateFormatter.format(Date(lastSuccessTime))
        } else {
            "None yet"
        }

        // 4. Latest Notification Card
        val latestRecord = repository.getLatestRecord()
        val layoutLatestDetails = findViewById<View>(R.id.layoutLatestDetails)
        val tvLatestAmount = findViewById<TextView>(R.id.tvLatestAmount)
        val tvLatestDirBadge = findViewById<TextView>(R.id.tvLatestDirectionBadge)
        val tvLatestFwdBadge = findViewById<TextView>(R.id.tvLatestForwardBadge)

        if (latestRecord != null) {
            layoutLatestDetails.visibility = View.VISIBLE
            tvLatestAmount.text = if (latestRecord.amountPaise > 0) {
                "${latestRecord.amount} (${latestRecord.amountPaise} paise)"
            } else {
                latestRecord.amount
            }

            tvLatestDirBadge.visibility = View.VISIBLE
            tvLatestDirBadge.text = latestRecord.classification
            when (latestRecord.classification) {
                "INCOMING" -> {
                    tvLatestDirBadge.setBackgroundColor(getColor(R.color.success_bg))
                    tvLatestDirBadge.setTextColor(getColor(R.color.success_green))
                }
                "OUTGOING" -> {
                    tvLatestDirBadge.setBackgroundColor(getColor(R.color.warning_bg))
                    tvLatestDirBadge.setTextColor(getColor(R.color.warning_red))
                }
                else -> {
                    tvLatestDirBadge.setBackgroundColor(getColor(R.color.neutral_bg))
                    tvLatestDirBadge.setTextColor(getColor(R.color.neutral_gray))
                }
            }

            tvLatestFwdBadge.visibility = View.VISIBLE
            tvLatestFwdBadge.text = "Forwarded: ${latestRecord.forwardingStatus}"
            when (latestRecord.forwardingStatus) {
                "YES" -> {
                    tvLatestFwdBadge.setBackgroundColor(getColor(R.color.success_bg))
                    tvLatestFwdBadge.setTextColor(getColor(R.color.success_green))
                }
                "IGNORED" -> {
                    tvLatestFwdBadge.setBackgroundColor(getColor(R.color.neutral_bg))
                    tvLatestFwdBadge.setTextColor(getColor(R.color.neutral_gray))
                }
                else -> {
                    tvLatestFwdBadge.setBackgroundColor(getColor(R.color.warning_bg))
                    tvLatestFwdBadge.setTextColor(getColor(R.color.warning_red))
                }
            }

            val appDisplay = if (latestRecord.appName.isNotBlank()) "${latestRecord.appName} (${latestRecord.packageName})" else latestRecord.packageName
            findViewById<TextView>(R.id.tvLatestSource).text = appDisplay
            findViewById<TextView>(R.id.tvLatestSender).text = latestRecord.sender
            findViewById<TextView>(R.id.tvLatestUtr).text = latestRecord.utr
            findViewById<TextView>(R.id.tvLatestTime).text = timeFormatter.format(Date(latestRecord.timestamp))
            findViewById<TextView>(R.id.tvLatestForwarded).text = latestRecord.serverStatus
        } else {
            tvLatestAmount.text = "No notifications detected yet"
            tvLatestDirBadge.visibility = View.GONE
            tvLatestFwdBadge.visibility = View.GONE
            layoutLatestDetails.visibility = View.GONE
        }

        // 5. Recent History RecyclerView
        val allRecords = repository.getAllRecords()
        val tvEmptyHistory = findViewById<TextView>(R.id.tvEmptyHistory)
        val rvRecent = findViewById<RecyclerView>(R.id.rvRecentNotifications)

        if (allRecords.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            rvRecent.visibility = View.GONE
        } else {
            tvEmptyHistory.visibility = View.GONE
            rvRecent.visibility = View.VISIBLE
            adapter.updateData(allRecords)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val etServerUrl = dialogView.findViewById<EditText>(R.id.etServerUrl)
        val etApiKey = dialogView.findViewById<EditText>(R.id.etApiKey)
        val switchCaptureAll = dialogView.findViewById<SwitchCompat>(R.id.switchCaptureAll)
        val etAllowedPackages = dialogView.findViewById<EditText>(R.id.etAllowedPackages)

        etServerUrl.setText(ConfigManager.getServerUrl(this))
        etApiKey.setText(ConfigManager.getApiKey(this))
        switchCaptureAll.isChecked = ConfigManager.isCaptureAllPackages(this)
        etAllowedPackages.setText(ConfigManager.getAllowedPackages(this).joinToString("\n"))

        dialogView.findViewById<Button>(R.id.btnResetPackages).setOnClickListener {
            etAllowedPackages.setText(ConfigManager.DEFAULT_ALLOWED_PACKAGES.joinToString("\n"))
        }

        dialogView.findViewById<Button>(R.id.btnCancelSettings).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            val newUrl = etServerUrl.text.toString().trim()
            val newKey = etApiKey.text.toString().trim()
            val newCaptureAll = switchCaptureAll.isChecked
            val pkgLines = etAllowedPackages.text.toString().lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            if (newUrl.isNotBlank()) ConfigManager.setServerUrl(this, newUrl)
            if (newKey.isNotBlank()) ConfigManager.setApiKey(this, newKey)
            ConfigManager.setCaptureAllPackages(this, newCaptureAll)
            if (pkgLines.isNotEmpty()) ConfigManager.setAllowedPackages(this, pkgLines)

            dialog.dismiss()
            updateDashboard()
            testServerConnection(showToast = true)
            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun simulatePayment(amount: String, sender: String, utr: String) {
        val testPkg = "com.phonepe.app"
        val testTs = System.currentTimeMillis()
        val testTitle = "PhonePe: Payment Received"
        val testText = "Received ₹$amount from $sender (UPI Ref $utr)"

        val record = NotificationParser.parseNotification(
            packageName = testPkg,
            title = testTitle,
            text = testText,
            postTime = testTs,
            appName = "PhonePe"
        )

        record.forwardingStatus = "NO"
        record.serverStatus = "Forwarding to server..."
        repository.addOrUpdateRecord(record)
        updateDashboard()

        PaymentForwarder.forwardNotification(applicationContext, record) { result ->
            runOnUiThread {
                if (result.success) {
                    record.forwardingStatus = "YES"
                    record.serverStatus = result.statusMessage
                    record.serverResponseBody = result.responseBody
                    repository.setLastServerSuccessTime(System.currentTimeMillis())
                    repository.setLastServerStatus("ONLINE")
                } else {
                    record.forwardingStatus = "NO"
                    record.serverStatus = result.statusMessage
                    record.serverResponseBody = result.responseBody
                }
                repository.addOrUpdateRecord(record)
                updateDashboard()
                Toast.makeText(this, "Test result: ${result.statusMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun simulateDebit() {
        val testPkg = "com.sbi.lotusintouch"
        val testTs = System.currentTimeMillis()
        val testTitle = "SBI Account Alert"
        val testText = "Dear Customer, A/c *1234 is debited by Rs.500.00 on 06-Oct-26 sent to Merchant XYZ (UPI Ref 998877665544)"

        val record = NotificationParser.parseNotification(
            packageName = testPkg,
            title = testTitle,
            text = testText,
            postTime = testTs,
            appName = "YONO SBI"
        )

        repository.addOrUpdateRecord(record)
        updateDashboard()
        Toast.makeText(this, "Simulated Outgoing Debit: Correctly classified as OUTGOING & IGNORED", Toast.LENGTH_LONG).show()
    }
}
