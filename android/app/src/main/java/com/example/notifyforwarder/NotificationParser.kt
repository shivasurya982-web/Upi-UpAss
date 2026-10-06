package com.example.notifyforwarder

import java.security.MessageDigest
import java.util.Locale

object NotificationParser {

    // Regex for amounts with currency indicators: ₹, Rs, Rs., INR, INR.
    private val amountRegex = Regex(
        """(?:₹|rs\.?|inr\.?)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Secondary fallback regex for amounts where currency symbol might follow or be spaced (e.g. "499.37 INR" or "credited with 499.37")
    private val amountSuffixRegex = Regex(
        """([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:₹|rs\.?|inr\.?|rupees?)""",
        RegexOption.IGNORE_CASE
    )

    // UTR / Reference parsing
    private val utrRegex = Regex(
        """(?:upi\s*ref(?:erence)?(?:\s*no\.?|\s*num(?:ber)?)?|utr(?:\s*no\.?)?|ref(?:\s*no\.?)?|txn\s*id|transaction\s*(?:id|ref|no\.?)|rrn)[\s:-]*([a-z0-9]{8,24})""",
        RegexOption.IGNORE_CASE
    )
    private val upiSlashRegex = Regex(
        """(?:upi|upi/p2a|upi/p2m|rrn|imps)/([0-9]{12})""",
        RegexOption.IGNORE_CASE
    )
    private val standalone12DigitUtrRegex = Regex("""\b(\d{12})\b""")

    // Sender parsing
    private val senderFromRegex = Regex(
        """from\s+([A-Za-z0-9\s.]{2,35}?)(?=\s*(?:\(|via|by|upi|ref|utr|a\/c|account|on|\.|\,|$))""",
        RegexOption.IGNORE_CASE
    )
    private val senderByRegex = Regex(
        """by\s+([A-Za-z0-9\s.]{2,35}?)(?=\s*(?:\(|via|upi|ref|utr|a\/c|account|on|\.|\,|$))""",
        RegexOption.IGNORE_CASE
    )

    // Outgoing regex patterns indicating money paid / sent / debited
    private val outgoingRegex = Regex(
        """\b(?:paid|sent|transferred)\s+(?:₹|rs\.?|inr\.?)?[\d,.]*\s*to\b|\bdebited\b|\bdebit\b|\bpaid\s+at\b|\bpayment\s+to\b|\bpayment\s+made\b|\bmoney\s+sent\b|\bspent\b|\bwithdrawn\b|\bdeducted\b|\byou\s+(?:paid|sent|transferred)\b""",
        RegexOption.IGNORE_CASE
    )

    // Incoming regex patterns indicating money received / credited
    private val incomingRegex = Regex(
        """\b(?:received|credited|deposit|deposited)\b|\b(?:sent|transferred|paid)\s+you\b|\b(?:sent|transferred|paid)\s+(?:₹|rs\.?|inr\.?)?[\d,.]*\s*to\s+(?:you|your)\b|\badded\s+to\s+your\b|\bpayment\s+received\b|\bmoney\s+received\b|\bupi\s+payment\s+received\b|\bhas\s+received\b|\breceived\s+on\b|\bhas\s+been\s+credited\b|\bcredited\s+with\b|\bcredited\s+by\b|\bcredited\s+to\b|\breceived\s+from\b""",
        RegexOption.IGNORE_CASE
    )

    // Noise / security / non-payment / promo / reward keywords to filter out
    private val nonPaymentIndicators = listOf(
        "otp",
        "one time password",
        "verification code",
        "e-statement",
        "statement is ready",
        "login alert",
        "logged in",
        "password changed",
        "due date",
        "bill generated",
        "pre-approved",
        "loan offer",
        "apply now",
        "reward earned",
        "tap to reveal",
        "cashback earned",
        "scratch card",
        "win up to",
        "invite friends",
        "earn up to",
        "spin the wheel",
        "discount voucher"
    )

    /**
     * Converts a raw currency amount string (e.g. "499.37", "1,299.50", "500") to integer paise.
     * Guaranteed to use pure integer arithmetic to prevent floating point inaccuracy.
     */
    fun parseAmountToPaise(cleanAmountStr: String): Long {
        val normalized = cleanAmountStr.replace(",", "").trim()
        if (normalized.isEmpty()) return 0L

        return try {
            val parts = normalized.split(".")
            val rupees = parts[0].toLongOrNull() ?: 0L
            val paise = if (parts.size > 1) {
                val frac = parts[1].padEnd(2, '0').take(2)
                frac.toLongOrNull() ?: 0L
            } else {
                0L
            }
            (rupees * 100L) + paise
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Generates a unique deduplication ID based on package, normalized text, and timestamp window.
     */
    fun generateNotificationId(packageName: String, text: String, postTime: Long): String {
        // Bucket timestamp into 10-second intervals to absorb minor system re-delivery timestamp shifts
        val timeBucket = postTime / 10000L
        val normalizedText = text.replace(Regex("""\s+"""), " ").trim().lowercase(Locale.ROOT)
        val input = "$packageName:$normalizedText:$timeBucket"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun parseNotification(
        packageName: String,
        title: String,
        text: String,
        postTime: Long,
        appName: String = ""
    ): TransactionRecord {
        val combinedText = "$title $text".trim()
        val textLower = combinedText.lowercase(Locale.ROOT)

        // 1. Check for non-payment noise (OTP, login, statement)
        val isNonPaymentNoise = nonPaymentIndicators.any { textLower.contains(it) }

        // 2. Check for outgoing indicators (sent to, paid to, debited)
        val isOutgoing = outgoingRegex.containsMatchIn(combinedText)

        // 3. Check for incoming indicators (received, credited, deposited, sent you)
        val isIncoming = incomingRegex.containsMatchIn(combinedText)

        // 4. Direction classification (Outgoing checks take priority over incidental incoming words)
        val classification = when {
            isNonPaymentNoise -> "UNRELATED"
            isOutgoing -> "OUTGOING"
            isIncoming -> "INCOMING"
            else -> "UNRELATED"
        }

        // 5. Amount extraction
        var extractedRawAmount = ""
        var matchedPattern = ""

        val prefixMatch = amountRegex.find(combinedText)
        if (prefixMatch != null) {
            extractedRawAmount = prefixMatch.groupValues[1]
            matchedPattern = prefixMatch.value
        } else {
            val suffixMatch = amountSuffixRegex.find(combinedText)
            if (suffixMatch != null) {
                extractedRawAmount = suffixMatch.groupValues[1]
                matchedPattern = suffixMatch.value
            }
        }

        val amountPaise = if (extractedRawAmount.isNotBlank()) {
            parseAmountToPaise(extractedRawAmount)
        } else {
            0L
        }

        val formattedAmount = if (amountPaise > 0) {
            val rupeesPart = amountPaise / 100L
            val paisePart = amountPaise % 100L
            if (paisePart == 0L) "₹$rupeesPart" else "₹$rupeesPart.%02d".format(paisePart)
        } else {
            "Not available in notification"
        }

        // 6. Payment condition:
        // Must be INCOMING, NOT outgoing, NOT noise, and have valid positive amount
        val isPayment = (classification == "INCOMING") && (amountPaise > 0L)

        // 7. UTR / UPI Reference extraction
        var utr = "Not available in notification"
        val utrMatch = utrRegex.find(combinedText)
        if (utrMatch != null && utrMatch.groupValues[1].isNotBlank()) {
            utr = utrMatch.groupValues[1].trim()
        } else {
            val slashMatch = upiSlashRegex.find(combinedText)
            if (slashMatch != null && slashMatch.groupValues[1].isNotBlank()) {
                utr = slashMatch.groupValues[1].trim()
            } else {
                val standaloneMatch = standalone12DigitUtrRegex.find(combinedText)
                if (standaloneMatch != null && standaloneMatch.groupValues[1].isNotBlank()) {
                    utr = standaloneMatch.groupValues[1].trim()
                }
            }
        }

        // 8. Sender extraction
        var sender = "Not available in notification"
        val fromMatch = senderFromRegex.find(combinedText)
        if (fromMatch != null && fromMatch.groupValues[1].isNotBlank()) {
            val candidate = fromMatch.groupValues[1].trim()
            if (!candidate.contains("bank", ignoreCase = true) && !candidate.contains("account", ignoreCase = true)) {
                sender = candidate
            }
        } else {
            val byMatch = senderByRegex.find(combinedText)
            if (byMatch != null && byMatch.groupValues[1].isNotBlank()) {
                val candidate = byMatch.groupValues[1].trim()
                if (!candidate.contains("bank", ignoreCase = true) && !candidate.contains("account", ignoreCase = true)) {
                    sender = candidate
                }
            }
        }

        val id = generateNotificationId(packageName, combinedText, postTime)

        val forwardStatus = when {
            isPayment -> "NO"
            classification == "OUTGOING" -> "IGNORED"
            else -> "IGNORED"
        }

        val serverStatus = when {
            isPayment -> "Pending"
            classification == "OUTGOING" -> "Ignored: Outgoing (Debit/Sent)"
            else -> "Ignored: Not incoming payment"
        }

        return TransactionRecord(
            id = id,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            amount = formattedAmount,
            amountPaise = amountPaise,
            utr = utr,
            sender = sender,
            timestamp = postTime,
            classification = classification,
            isPayment = isPayment,
            matchedPattern = matchedPattern,
            forwardingStatus = forwardStatus,
            serverStatus = serverStatus,
            serverResponseBody = ""
        )
    }
}
